package com.debangshu.money;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns messages into transactions, all on the phone:
 * bank SMS (as they arrive, plus a catch-up scan of the inbox for any that were missed),
 * payment-app notifications, and SMS shown by the Messages app.
 */
public final class Engine {

    private Engine() {}

    public static final int FROM_SMS = 0, FROM_INBOX = 1, FROM_SMS_NOTIF = 2;

    /** A bank SMS. Returns the saved transaction (or null when it wasn't about money). */
    public static Txn sms(Context ctx, String sender, String body, long time, int from) {
        SmsParser.Result r = SmsParser.parse(sender, body);
        Store s = Store.get(ctx);
        if (r == null) {
            if (from != FROM_SMS_NOTIF && SmsParser.looksLikeBankTxn(sender, body)) s.addUnread(time, SmsParser.cleanSender(sender), body.trim());
            return null;
        }
        if (r.bill) {
            Store.Bill b = new Store.Bill();
            b.account = r.account;
            b.due = r.due;
            b.minDue = r.minDue;
            b.dueDate = r.dueDate;
            b.seen = time;
            b.raw = body;
            s.addBill(b);
            return null;
        }
        Txn t = new Txn();
        t.time = time;
        t.raw = body.trim();
        t.sender = SmsParser.cleanSender(sender);
        t.srcKey = "sms:" + Integer.toHexString((t.sender + "|" + t.raw).hashCode());
        t.sources = Txn.SRC_SMS;
        t.account = r.account;
        t.balance = r.balance;
        t.limit = r.limit;
        // a debit-card message: put it on the bank account behind the card (so balances add up)
        if (r.viaCard && !r.card && !t.account.isEmpty()) {
            String acc = s.alias(t.account);
            if (acc == null && r.balance >= 0 && !r.bank.isEmpty() && r.amount > 0) {
                for (Account a : s.accounts()) {
                    if (a.card || a.key.equals(t.account) || !a.key.startsWith(r.bank + " ••") || a.balance < 0 || a.balanceTime > time) continue;
                    long expect = a.balance + (r.credit ? r.amount : -r.amount);
                    if (Math.abs(expect - r.balance) <= 100) { acc = a.key; s.setAlias(t.account, acc); break; }
                }
            }
            if (acc != null) t.account = acc;
        }
        if (r.balanceOnly) {
            t.kind = Txn.SNAPSHOT;
            t.amount = 0;
        } else {
            fill(ctx, t, r);
        }
        return save(ctx, t, r.card, r.wallet);
    }

    /** A notification from Google Pay / PhonePe / Paytm / a bank app. */
    public static Txn app(Context ctx, String pkg, String title, String text, long time) {
        SmsParser.Result r = NotifParser.parseApp(pkg, title, text);
        if (r == null) return null;
        Txn t = new Txn();
        t.time = time;
        t.raw = ((title == null ? "" : title.trim() + "\n") + (text == null ? "" : text.trim())).trim();
        t.sender = NotifParser.appName(pkg);
        t.srcKey = "app:" + Integer.toHexString((pkg + "|" + t.raw).hashCode());
        t.sources = Txn.SRC_APP;
        t.account = r.account;
        fill(ctx, t, r);
        return save(ctx, t, false, false);
    }

    private static void fill(Context ctx, Txn t, SmsParser.Result r) {
        t.amount = r.amount;
        t.credit = r.credit;
        t.merchant = r.merchant;
        t.vpa = r.vpa;
        t.ref = r.ref;
        if (r.cardPayment || r.selfTransfer) t.kind = Txn.TRANSFER;
        else if (r.refund) t.kind = Txn.REFUND;
        else if (r.atm && !Store.get(ctx).flag(Store.S_ATM_SPEND)) t.kind = Txn.TRANSFER;
        if (r.atm) t.category = Cats.CASH;
    }

    private static Txn save(Context ctx, Txn t, boolean card, boolean wallet) {
        Store s = Store.get(ctx);
        boolean[] isNew = {false};
        Txn saved;
        synchronized (s) {
            saved = s.addOrMerge(t, isNew);
            s.touchAccount(saved, card, wallet);
            if (!saved.account.isEmpty()) s.setGaps(saved.account, Ledger.gaps(s.txns(), saved.account));
        }
        if (isNew[0] && !s.inBatch() && saved.kind != Txn.SNAPSHOT) Alerts.afterNew(ctx, saved);
        return saved;
    }

    // ------------------------------------------------------------------ inbox catch-up

    public static boolean canReadSms(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reads the SMS inbox from {@code since} and adds anything not seen yet. This is the safety net for
     * messages that arrived while the phone was off, the app was stopped, or Android didn't wake it.
     * Returns how many new transactions were found.
     */
    public static int scanInbox(Context ctx, long since, Progress progress) {
        if (!canReadSms(ctx)) return 0;
        Store s = Store.get(ctx);
        int before = s.txns().size();
        s.begin();
        Set<String> accountsTouched = new HashSet<>();
        try (Cursor c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"),
                new String[]{"address", "body", "date"}, "date > ?", new String[]{String.valueOf(since)}, "date ASC")) {
            if (c == null) return 0;
            int total = c.getCount(), i = 0;
            while (c.moveToNext()) {
                i++;
                String addr = c.getString(0), body = c.getString(1);
                long date = c.getLong(2);
                if (body == null || addr == null) continue;
                Txn t = sms(ctx, addr, body, date, FROM_INBOX);
                if (t != null && !t.account.isEmpty()) accountsTouched.add(t.account);
                if (progress != null && (i % 25 == 0 || i == total)) progress.on(i, total);
            }
        } catch (Exception ignored) {
        } finally {
            s.end();
        }
        s.prefs().edit().putLong("lastScan", System.currentTimeMillis()).apply();
        return Math.max(0, s.txns().size() - before);
    }

    public interface Progress { void on(int done, int total); }

    /** Regular catch-up: the last 3 days again (the duplicate check skips what's already there). */
    public static int catchUp(Context ctx) {
        long last = Store.get(ctx).prefs().getLong("lastScan", 0);
        long since = last == 0 ? System.currentTimeMillis() - 3L * 86400000 : Math.min(last, System.currentTimeMillis()) - 3L * 86400000;
        return scanInbox(ctx, since, null);
    }

    /** Recomputes every account's "Unrecorded" entries (after a restore or a settings change). */
    public static void recheckGaps(Context ctx) {
        Store s = Store.get(ctx);
        s.begin();
        try {
            for (Account a : s.accounts()) s.setGaps(a.key, Ledger.gaps(s.txns(), a.key));
        } finally {
            s.end();
        }
    }

    public static List<Txn> needsLook(Context ctx) {
        List<Txn> out = new java.util.ArrayList<>();
        for (Txn t : Store.get(ctx).visible()) if (t.review) out.add(t);
        return out;
    }
}
