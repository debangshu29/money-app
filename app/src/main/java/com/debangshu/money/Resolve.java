package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Sort them out": goes through the entries that need a look one by one.
 * For an "Unrecorded" amount it shows clues — bank SMS from that time that Money couldn't read
 * (one tap adds it as the real payment) and app payments nearby — or lets you say what it was in one tap.
 */
public final class Resolve {

    private Resolve() {}

    public static List<Txn> pending(Context c) {
        List<Txn> l = new ArrayList<>();
        for (Txn t : Store.get(c).visible()) if (t.review) l.add(t);
        // unrecorded first, biggest first
        java.util.Collections.sort(l, (a, b) -> {
            boolean ga = (a.sources & Txn.SRC_GAP) != 0, gb = (b.sources & Txn.SRC_GAP) != 0;
            if (ga != gb) return ga ? -1 : 1;
            return Long.compare(b.amount, a.amount);
        });
        return l;
    }

    /** Starts the one-by-one flow. */
    public static void start(Activity a) {
        List<Txn> l = pending(a);
        if (l.isEmpty()) { Ui.toast(a, "Nothing needs a look 🎉"); return; }
        step(a, l, 0);
    }

    /** Just one entry (from its own sheet). */
    public static void one(Activity a, Txn t) {
        List<Txn> l = new ArrayList<>();
        l.add(t);
        step(a, l, 0);
    }

    private static void step(Activity a, List<Txn> list, int i) {
        if (i >= list.size()) {
            int left = pending(a).size();
            Ui.toast(a, left == 0 ? "All sorted 🎉" : left + " still to look at");
            return;
        }
        Txn t = list.get(i);
        if (!t.review || t.kind == Txn.IGNORED || Store.get(a).byId(t.id) == null) { step(a, list, i + 1); return; }
        boolean gap = (t.sources & Txn.SRC_GAP) != 0;
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, 0, p, Ui.dp(a, 6));
        final AlertDialog[] dlg = {null};

        TextView amt = Ui.text(a, (t.credit ? "+" : "−") + Fmt.plain(t.amount), 30, t.credit ? Ui.col(a, R.color.success) : Ui.col(a, R.color.text), Ui.EXTRABOLD);
        box.addView(amt);
        String sub = gap ? (t.credit ? "Came into " : "Went out of ") + t.account + "\nsomewhere between " + (t.gapFrom > 0 ? Fmt.dayTime(t.gapFrom) : "the previous message")
                + " and " + Fmt.dayTime(t.time + 1000) : (t.merchant.isEmpty() ? "Payment" : t.merchant) + " · " + Fmt.dayTime(t.time);
        TextView s = Ui.sub(a, sub);
        s.setLineSpacing(Ui.dp(a, 2), 1f);
        box.addView(s);
        if (!gap) {
            TextView why = Ui.sub(a, "This looked unusual (much bigger than normal, or the same amount twice in a few minutes).");
            why.setPadding(0, Ui.dp(a, 6), 0, 0);
            box.addView(why);
        }

        // ---- clues
        if (gap) {
            long from = t.gapFrom > 0 ? t.gapFrom : t.time - 3L * 86400000;
            List<String[]> msgs = unreadBetween(a, from - 3600000, t.time + 3600000, t.account);
            if (!msgs.isEmpty()) {
                box.addView(head(a, "Bank messages from that time Money couldn't read"));
                for (String[] m : msgs) {
                    LinearLayout c = Ui.vbox(a);
                    c.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 14)));
                    c.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
                    c.addView(Ui.text(a, m[1] + " · " + Fmt.dayTime(Long.parseLong(m[0])), 12, Ui.col(a, R.color.text2), Ui.BOLD));
                    TextView body = Ui.text(a, m[2], 13, Ui.REGULAR);
                    body.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 8));
                    c.addView(body);
                    TextView use = Ui.chip(a, "✓ This is it — add it", true);
                    use.setOnClickListener(v -> {
                        if (addFromMessage(a, m, t.account)) {
                            if (dlg[0] != null) dlg[0].dismiss();
                            Ui.toast(a, "Added from the message");
                            step(a, list, i + 1);
                        } else Ui.toast(a, "Couldn't find an amount in it — pick what it was below");
                    });
                    LinearLayout r = Ui.hbox(a);
                    r.addView(use);
                    c.addView(r);
                    box.addView(c, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 4));
                }
            }
            List<Txn> near = new ArrayList<>();
            for (Txn x : Store.get(a).visible()) {
                if (x == t || (x.sources & Txn.SRC_GAP) != 0 || x.time < from - 3600000 || x.time > t.time + 3600000) continue;
                if (!x.account.isEmpty() && x.account.equals(t.account)) continue;
                near.add(x);
            }
            if (!near.isEmpty()) {
                box.addView(head(a, "Other payments around then (for a clue)"));
                for (int k = 0; k < Math.min(4, near.size()); k++) box.addView(Rows.txn(a, near.get(k), true));
            }
        }

        // ---- one-tap answers
        box.addView(head(a, gap ? "What was it?" : "Was it you?"));
        EditText name = new EditText(a);
        name.setHint(gap ? "Name (optional) — e.g. Rent, Ramesh, Petrol" : "Name (optional)");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setTypeface(Ui.face(a, Ui.REGULAR));
        name.setTextSize(15);
        if (!gap || !t.merchant.startsWith("Unrecorded")) name.setText(t.merchant.startsWith("Unrecorded") ? "" : t.merchant);
        box.addView(name);
        LinearLayout chips1 = Ui.hbox(a), chips2 = Ui.hbox(a);
        String[] quick = t.credit
                ? new String[]{"💼 Salary", "👥 From someone", "↩️ Refund", "🔁 From my other account", "💰 Interest / other"}
                : new String[]{"💵 Cash (ATM)", "🏠 Rent", "👥 Paid someone", "🔁 To my other account", "💡 Bills", "🛒 Shopping",
                "🍔 Food", "🧾 Bank charges", "📈 Investment", "🏦 EMI / loan", "More…"};
        for (int k = 0; k < quick.length; k++) {
            String q = quick[k];
            TextView c = Ui.chip(a, q, false);
            c.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            c.setOnClickListener(v -> {
                if (q.equals("More…")) {
                    TxnSheet.pickCategory(a, t, () -> {
                        finish(a, t, null, name.getText().toString(), false);
                        if (dlg[0] != null) dlg[0].dismiss();
                        step(a, list, i + 1);
                    });
                    return;
                }
                finish(a, t, q, name.getText().toString(), false);
                if (dlg[0] != null) dlg[0].dismiss();
                step(a, list, i + 1);
            });
            (k < (quick.length + 1) / 2 ? chips1 : chips2).addView(c, Ui.margins(Ui.lp(-2, -2), a, 0, 4, 8, 4));
        }
        box.addView(scrollRow(a, chips1));
        box.addView(scrollRow(a, chips2));

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        String title = list.size() > 1 ? "Needs a look · " + (i + 1) + " of " + list.size() : "Needs a look";
        AlertDialog d = new Ui.Popup(a).sheet().setTitle(title).setView(Ui.capHeight(sv, 0.75f))
                .setPositiveButton(gap ? "I don't know" : "Yes, it was me", (x, w) -> {
                    finish(a, t, null, name.getText().toString(), true);
                    step(a, list, i + 1);
                })
                .setNeutralButton(list.size() > i + 1 ? "Skip" : "Later", (x, w) -> step(a, list, i + 1))
                .create();
        dlg[0] = d;
        d.show();
    }

    /** Saves the answer and stops asking about it. */
    private static void finish(Activity a, Txn t, String quick, String name, boolean unknown) {
        Store s = Store.get(a);
        name = name == null ? "" : name.trim();
        if (quick != null) {
            String q = quick.substring(quick.indexOf(' ') + 1);
            String cat;
            switch (q) {
                case "Cash (ATM)": cat = Cats.CASH; break;
                case "Rent": cat = Cats.HOME; break;
                case "Paid someone": case "From someone": cat = Cats.PEOPLE; break;
                case "To my other account": case "From my other account": cat = Cats.TRANSFER; t.kind = Txn.TRANSFER; break;
                case "Bills": cat = Cats.BILLS; break;
                case "Shopping": cat = Cats.SHOP; break;
                case "Food": cat = Cats.FOOD; break;
                case "Bank charges": cat = Cats.FEES; break;
                case "Investment": cat = Cats.INVEST; break;
                case "EMI / loan": cat = Cats.EMI; break;
                case "Salary": cat = Cats.SALARY; break;
                case "Refund": cat = Cats.REFUND; t.kind = Txn.REFUND; break;
                default: cat = Cats.INCOME; break;
            }
            t.category = cat;
            t.userCategory = true;
            if (name.isEmpty() && (t.sources & Txn.SRC_GAP) != 0) name = q;
        }
        if (!name.isEmpty()) { t.merchant = name; t.userMerchant = true; }
        else if (unknown && (t.sources & Txn.SRC_GAP) != 0) t.merchant = t.credit ? "Money in (unknown)" : "Spent (unknown)";
        t.review = false;
        s.update(t);
    }

    // ------------------------------------------------------------------ clues

    /** Bank SMS between from and to that didn't become a transaction (the kept list + the inbox itself). */
    static List<String[]> unreadBetween(Context c, long from, long to, String account) {
        List<String[]> out = new ArrayList<>();
        Store s = Store.get(c);
        String bank = account == null ? "" : account.replaceAll(" (card )?••.*$", "").replace(" wallet", "").trim();
        List<String> known = new ArrayList<>();
        for (Txn t : s.txns()) if (t.time >= from - 86400000 && t.time <= to + 86400000 && !t.raw.isEmpty()) known.add(t.raw);
        for (String[] u : s.unread()) {
            long t = Long.parseLong(u[0]);
            if (t >= from && t <= to) out.add(u);
        }
        if (Engine.canReadSms(c)) {
            try (Cursor cur = c.getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"address", "body", "date"},
                    "date >= ? AND date <= ?", new String[]{String.valueOf(from), String.valueOf(to)}, "date ASC")) {
                while (cur != null && cur.moveToNext()) {
                    String addr = cur.getString(0), body = cur.getString(1);
                    long date = cur.getLong(2);
                    if (addr == null || body == null) continue;
                    String lo = body.toLowerCase(Locale.ROOT);
                    if (!bank.isEmpty() && !SmsParser.bankOf(addr, lo).equals(bank)) continue;
                    if (!SmsParser.looksLikeBankTxn(addr, body) && !lo.contains("bal")) continue;
                    boolean seen = false;
                    for (String k : known) if (k.contains(body.trim())) { seen = true; break; }
                    for (String[] o : out) if (o[2].equals(body.trim())) seen = true;
                    if (!seen) out.add(new String[]{String.valueOf(date), SmsParser.cleanSender(addr), body.trim()});
                }
            } catch (Exception ignored) { }
        }
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    private static final Pattern AMOUNT = Pattern.compile("(?:\\brs\\b\\.?|\\binr\\b|₹|\\brs(?=[0-9.:]))\\s*[:.]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    /** "This is it": turns a message Money couldn't read into the real payment. */
    static boolean addFromMessage(Activity a, String[] m, String account) {
        String body = m[2];
        SmsParser.Result r = SmsParser.parse(m[1], body);
        long amount;
        boolean credit;
        String who;
        if (r != null && r.txn) { amount = r.amount; credit = r.credit; who = r.merchant; }
        else {
            Matcher am = AMOUNT.matcher(body);
            amount = -1;
            while (am.find()) {
                int st = am.start();
                String before = body.toLowerCase(Locale.ROOT).substring(Math.max(0, st - 20), st);
                if (before.contains("bal") || before.contains("limit")) continue;
                amount = SmsParser.paise(am.group(1));
                break;
            }
            String lo = body.toLowerCase(Locale.ROOT);
            int ci = indexOfAny(lo, "credited", "received", "deposited", " cr ", "cr.");
            int di = indexOfAny(lo, "debited", "spent", "sent", "paid", "withdrawn", " dr ", "dr.", "purchase");
            credit = ci >= 0 && (di < 0 || ci < di);
            who = SmsParser.merchant(body, credit);
        }
        if (amount <= 0) return false;
        Txn t = new Txn();
        t.time = Long.parseLong(m[0]);
        t.amount = amount;
        t.credit = credit;
        t.merchant = who == null ? "" : who;
        t.account = account == null ? "" : account;
        t.raw = body;
        t.sender = m[1];
        t.sources = Txn.SRC_SMS;
        t.srcKey = "sms:" + Integer.toHexString((m[1] + "|" + body).hashCode());
        SmsParser.Result rb = new SmsParser.Result();
        SmsParser.balanceAndLimit(rb, body.toLowerCase(Locale.ROOT));
        t.balance = rb.balance;
        Store s = Store.get(a);
        s.addOrMerge(t, null);
        s.removeUnread(body);
        if (!t.account.isEmpty()) s.setGaps(t.account, Ledger.gaps(s.txns(), t.account));
        return true;
    }

    private static int indexOfAny(String s, String... words) {
        int best = -1;
        for (String w : words) { int i = s.indexOf(w); if (i >= 0 && (best < 0 || i < best)) best = i; }
        return best;
    }

    private static TextView head(Activity a, String s) {
        TextView t = Ui.text(a, s, 13.5f, Ui.col(a, R.color.text2), Ui.BOLD);
        t.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 6));
        return t;
    }

    private static View scrollRow(Activity a, LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(row);
        return h;
    }
}
