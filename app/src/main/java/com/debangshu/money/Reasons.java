package com.debangshu.money;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

/**
 * "₹1 to Ramesh — what for?" the moment the payment is caught, with one-tap reasons — only for unusual payments
 * (new person / place, odd amount). Regulars get their usual note by themselves. Also answers "Paid anyone on Google Pay?".
 */
public class Reasons extends BroadcastReceiver {

    static final String REASON = "com.debangshu.money.REASON", TYPED = "com.debangshu.money.REASON_TYPED",
            NOPAY = "com.debangshu.money.NOPAY", PAID = "com.debangshu.money.PAID_TYPED", MOVE = "com.debangshu.money.MOVE_ACCOUNT";
    static final String KEY = "reason";

    static int nid(String txnId) { return 30000 + (txnId.hashCode() & 0x7FFF); }

    /** A new payment just arrived (live). */
    static void maybeAsk(Context c, Txn t) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_ASK_REASON) || t.credit || t.kind != Txn.NORMAL || !t.note.isEmpty()) return;
        if ((t.sources & (Txn.SRC_GAP | Txn.SRC_NOTE | Txn.SRC_MANUAL | Txn.SRC_STMT)) != 0 || Cash.ACCOUNT.equals(t.account)) return;
        if (System.currentTimeMillis() - t.time > 30L * 60000 || t.unnamed()) return;      // unnamed → "Who was it?" asks (name + reason)
        String key = "why:" + t.id;
        if (s.prefs().getBoolean(key, false)) return;
        List<Txn> all = s.txns();
        String auto = ReasonLogic.autoNote(all, t);
        if (auto != null) { t.note = auto; s.update(t); return; }      // you always write this for them
        if (!ReasonLogic.unusual(all, t) || !Alerts.canNotify(c)) return;
        s.prefs().edit().putBoolean(key, true).apply();
        Alerts.channels(c);
        int nid = nid(t.id);
        String title = Fmt.plain(t.amount) + " to " + t.merchant + " — what for?";
        String impact = MoneyFacts.goalImpact(c, t.amount);     // "= 13% of your Laptop goal"
        String text = (impact.isEmpty() ? "" : "That's " + impact.substring(2) + ". ") + "Tap a reason or type one — so you still know in a month.";
        Notification.Builder b = builder(c, title, text, openTxn(c, t, nid));
        int k = 0;
        for (String[] r : ReasonLogic.suggest(all, t, 2)) {
            Intent i = new Intent(c, Reasons.class).setAction(REASON).putExtra("txn", t.id).putExtra("nid", nid)
                    .putExtra("note", r[1]).putExtra("cat", r[2]).putExtra("label", r[0]);
            PendingIntent pi = PendingIntent.getBroadcast(c, nid * 4 + (k++), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(icon(c), r[0], pi).build());
        }
        b.addAction(typeAction(c, new Intent(c, Reasons.class).setAction(TYPED).putExtra("txn", t.id).putExtra("nid", nid), nid * 4 + 3,
                "✏️ Type", "What for? e.g. test, Puja chanda"));
        if (s.flag(Store.S_LOCK)) b.setVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(nid, b.build());
    }

    /** Usage watch: a UPI app was open but no payment came in. */
    static void askPaidAnyone(Context c, String pkg, long start, long end) {
        if (!Alerts.canNotify(c)) return;
        Alerts.channels(c);
        String app = NotifParser.appName(pkg);
        if (app == null) app = "a UPI app";
        int nid = 31000 + (int) ((start / 1000) & 0x3FF);
        Intent open = new Intent(c, MainActivity.class).putExtra("paidOn", pkg).putExtra("paidAt", start)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = builder(c, "Paid anyone on " + app + "?",
                "You had " + app + " open at " + Fmt.time(start) + " and no payment message came. If you paid, type it — e.g. “50 Ramesh for test”.", po);
        Intent no = new Intent(c, Reasons.class).setAction(NOPAY).putExtra("nid", nid);
        b.addAction(new Notification.Action.Builder(icon(c), "No, just checking",
                PendingIntent.getBroadcast(c, nid * 4, no, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        b.addAction(typeAction(c, new Intent(c, Reasons.class).setAction(PAID).putExtra("nid", nid).putExtra("pkg", pkg).putExtra("t", start),
                nid * 4 + 1, "✏️ Yes — type it", "₹ who for what — e.g. 50 Ramesh for test"));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(nid, b.build());
    }

    private static PendingIntent openTxn(Context c, Txn t, int nid) {
        Intent open = new Intent(c, MainActivity.class).putExtra("txn", t.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context c, String title, String text, PendingIntent open) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, Alerts.CH_ASK) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
                .setOnlyAlertOnce(true).setColor(c.getColor(R.color.accent));
        return b;
    }

    private static android.graphics.drawable.Icon icon(Context c) { return android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notify); }

    private static Notification.Action typeAction(Context c, Intent i, int req, String label, String hint) {
        PendingIntent pi = PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        RemoteInput ri = new RemoteInput.Builder(KEY).setLabel(hint).build();
        return new Notification.Action.Builder(icon(c), label, pi).addRemoteInput(ri).build();
    }

    // ------------------------------------------------------------------ answers

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        int nid = intent.getIntExtra("nid", 0);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        String a = intent.getAction();
        if (NOPAY.equals(a)) { if (nm != null) nm.cancel(nid); return; }
        String typed = null;
        Bundle r = RemoteInput.getResultsFromIntent(intent);
        if (r != null && r.getCharSequence(KEY) != null) typed = r.getCharSequence(KEY).toString().trim();
        Store s = Store.get(c);
        if (PAID.equals(a)) {
            // "50 Ramesh for test from federal ending 1234" → that exact account
            String[] h = Voice.hint(typed);
            String acc = Noted.defaultAccount(c);
            if (!h[0].isEmpty() || !h[1].isEmpty()) {
                List<String> m = Noted.match(c, h[0], h[1]);
                if (m.size() == 1) acc = m.get(0);
                else if (m.size() > 1 && !m.contains(acc)) acc = m.get(0);
                typed = h[2];
            }
            Object[] p = ReasonLogic.paidText(typed);
            if (p == null) {
                Alerts.post(c, Alerts.CH_ASK, nid, "Didn't catch the amount", "Type it like “50 Ramesh for test”.", null);
                return;
            }
            Txn t = Noted.add(c, (Long) p[0], (String) p[1], null, (String) p[2], "", acc);
            if (h[0].isEmpty() && h[1].isEmpty() && Noted.bankAccounts(c).size() > 1) { whichAccount(c, nm, nid, t); return; }
            done(c, nm, nid, "Saved ✓ " + Fmt.plain(t.amount) + (t.merchant.isEmpty() ? "" : " to " + t.merchant) + " · " + Noted.fullLabel(c, acc),
                    (t.note.isEmpty() ? "" : t.note + " · ") + "the bank's message joins it if one comes", t.id);
            return;
        }
        Txn t = s.byId(intent.getStringExtra("txn"));
        if (t == null) { if (nm != null) nm.cancel(nid); return; }
        if (MOVE.equals(a)) {
            Noted.move(c, t, intent.getStringExtra("to"));
            done(c, nm, nid, "Saved ✓ " + Fmt.plain(t.amount) + " · from " + Noted.fullLabel(c, t.account), "Balance updated", t.id);
            return;
        }
        if (REASON.equals(a)) {
            String note = intent.getStringExtra("note"), cat = intent.getStringExtra("cat");
            if (note != null && !note.isEmpty()) t.note = note;
            if (cat != null && !cat.isEmpty() && !t.userCategory) { t.category = cat; t.userCategory = true; }
            s.update(t);
            done(c, nm, nid, "Saved ✓ " + intent.getStringExtra("label"), Fmt.plain(t.amount) + " to " + t.merchant, t.id);
        } else if (TYPED.equals(a)) {
            if (typed == null || typed.isEmpty()) { if (nm != null) nm.cancel(nid); return; }
            t.note = typed;
            s.update(t);
            done(c, nm, nid, "Saved ✓ " + typed, Fmt.plain(t.amount) + " to " + t.merchant, t.id);
        }
    }

    /** Saved from your usual account — tap another account's button if it came from somewhere else (no typing bank names). */
    private static void whichAccount(Context c, NotificationManager nm, int nid, Txn t) {
        if (nm == null) return;
        List<String> others = new java.util.ArrayList<>();
        for (String k : Noted.bankAccounts(c)) if (!k.equals(t.account)) others.add(k);
        // most used first
        java.util.Map<String, Long> used = new java.util.HashMap<>();
        for (Account x : Store.get(c).accounts()) used.put(x.key, x.lastUsed);
        java.util.Collections.sort(others, (x, y) -> Long.compare(used.containsKey(y) ? used.get(y) : 0, used.containsKey(x) ? used.get(x) : 0));
        boolean more = others.size() > 3;
        Notification.Builder b = builder(c, "Saved ✓ " + Fmt.plain(t.amount) + (t.merchant.isEmpty() ? "" : " to " + t.merchant),
                "Paid from " + Noted.fullLabel(c, t.account) + ". Another account? Tap it below" + (more ? " (or More… for the rest)." : "."), openTxn(c, t, nid));
        int shown = more ? 2 : others.size();
        for (int i = 0; i < shown; i++) {
            Intent mv = new Intent(c, Reasons.class).setAction(MOVE).putExtra("txn", t.id).putExtra("nid", nid).putExtra("to", others.get(i));
            b.addAction(new Notification.Action.Builder(icon(c), Noted.fullLabel(c, others.get(i)),
                    PendingIntent.getBroadcast(c, nid * 4 + i, mv, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        }
        if (more) b.addAction(new Notification.Action.Builder(icon(c), "More…", openTxn(c, t, nid)).build());
        nm.notify(nid, b.build());
    }

    private static void done(Context c, NotificationManager nm, int nid, String title, String text, String txnId) {
        Alerts.post(c, Alerts.CH_ASK, nid, title, text, txnId);
        if (nm != null) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> nm.cancel(nid), 4000);
    }
}
