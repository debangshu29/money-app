package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Failed payments: "UPI failed but money debited". Money notices it, keeps it out of your spending, watches for the
 * money to come back (and links the refund), and reminds you with the RBI rule if it doesn't.
 */
public class Refunds extends Screen {

    static final String KEY = "refunds";
    private static final long DAY = 86400000L;
    private static final Pattern BACK = Pattern.compile("refund|revers|failed|credited back|returned|chargeback|\\brev\\b", Pattern.CASE_INSENSITIVE);

    static List<JSONObject> all(Context c) {
        JSONArray a = Store.get(c).list(KEY);
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optJSONObject(i));
        return out;
    }

    private static void saveAll(Context c, List<JSONObject> l) {
        JSONArray a = new JSONArray();
        for (JSONObject o : l) a.put(o);
        Store.get(c).putList(KEY, a);
    }

    static boolean waiting(JSONObject w) { return "waiting".equals(w.optString("status")); }

    static List<JSONObject> waiting(Context c) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject w : all(c)) if (waiting(w)) out.add(w);
        return out;
    }

    /** Days the bank is allowed: next day for UPI / IMPS transfers, 5 days for shops, cards and ATMs. */
    static int allowed(JSONObject w) { return w.optBoolean("merchant") ? 5 : 1; }

    static long dueBy(JSONObject w) { return Fmt.startOfDay(w.optLong("t")) + (allowed(w) + 1) * DAY - 1; }

    // ------------------------------------------------------------------ from messages

    /** A debit that might be a failed payment (its own message says so, or a "failed" message came within 2 hours). */
    static void onTxn(Context c, Txn t, String sender, String body) {
        if (t.kind == Txn.SNAPSHOT || t.kind == Txn.IGNORED) return;
        if (t.credit) { resolve(c, t); return; }
        Notices.Failed f = Notices.failed(sender, body);
        if (f != null && f.amount == t.amount) { watch(c, t, f.merchantPay, f.ref, false); return; }
        // a "failed" notice seen just before this debit
        JSONObject pend = Store.get(c).obj("failPending");
        for (java.util.Iterator<String> it = pend.keys(); it.hasNext(); ) {
            JSONObject p = pend.optJSONObject(it.next());
            if (p != null && p.optLong("amt") == t.amount && Math.abs(p.optLong("t") - t.time) < 2 * 3600000L
                    && (p.optString("acc").isEmpty() || Ledger.sameAccount(p.optString("acc"), t.account))) {
                watch(c, t, p.optBoolean("merchant"), p.optString("ref"), false);
                return;
            }
        }
    }

    /** A "payment failed" message (SMS or app) without its own debit: match it to a debit that already came, or remember it. */
    static boolean onFailedNotice(Context c, Notices.Failed f, long time) {
        Txn hit = null;
        for (Txn t : Store.get(c).txns()) {
            if (t.credit || t.amount != f.amount || Math.abs(t.time - time) > 2 * 3600000L || t.kind == Txn.SNAPSHOT) continue;
            if (!f.account.isEmpty() && !t.account.isEmpty() && !Ledger.sameAccount(f.account, t.account)) continue;
            hit = t;
            break;
        }
        if (hit != null) { watch(c, hit, f.merchantPay, f.ref, false); return true; }
        JSONObject pend = Store.get(c).obj("failPending");
        try {
            pend.put(Long.toString(time) + f.amount, new JSONObject().put("amt", f.amount).put("t", time).put("acc", f.account)
                    .put("merchant", f.merchantPay).put("ref", f.ref));
            // keep it small
            List<String> old = new ArrayList<>();
            for (java.util.Iterator<String> it = pend.keys(); it.hasNext(); ) {
                String k = it.next();
                if (System.currentTimeMillis() - pend.optJSONObject(k).optLong("t") > 3 * DAY) old.add(k);
            }
            for (String k : old) pend.remove(k);
        } catch (Exception ignored) { }
        Store.get(c).putObj("failPending", pend);
        return false;
    }

    /** Start watching a payment (also from "Payment failed?" on a payment). */
    static void watch(Context c, Txn t, boolean merchant, String ref, boolean manual) {
        Store s = Store.get(c);
        List<JSONObject> l = all(c);
        for (JSONObject w : l) if (t.id.equals(w.optString("txn"))) return;
        try {
            l.add(new JSONObject().put("id", Screen.id()).put("txn", t.id).put("amt", t.amount).put("acc", t.account).put("t", t.time)
                    .put("who", t.merchant).put("ref", ref == null ? "" : ref).put("merchant", merchant).put("status", "waiting").put("manual", manual));
        } catch (Exception ignored) { }
        saveAll(c, l);
        if (t.kind == Txn.NORMAL) t.kind = Txn.TRANSFER;
        t.note = "Failed payment — waiting for the money to come back";
        t.review = false;
        s.update(t);
        // the refund may already be here (inbox catch-up)
        for (Txn x : s.txns()) if (x.credit && x.time >= t.time && x.amount == t.amount) { if (resolve(c, x)) break; }
        if (!s.inBatch() && !manual && System.currentTimeMillis() - t.time < 6 * 3600000L && s.flag(Store.S_REMIND)) {
            Alerts.postTo(c, Alerts.CH_ALERTS, 700 + (t.id.hashCode() & 0xFF), "↩️ Failed payment: " + Fmt.plain(t.amount) + (t.merchant.isEmpty() ? "" : " · " + t.merchant),
                    "Not counted as spending. Money is watching for it to come back — the bank should return it "
                            + (merchant ? "within 5 days." : "by tomorrow."), Refunds.class);
        }
    }

    /** A credit: is it a failed payment coming back? */
    static boolean resolve(Context c, Txn cr) {
        if (!cr.credit) return false;
        Store s = Store.get(c);
        List<JSONObject> l = all(c);
        for (JSONObject w : l) {
            if (!waiting(w) || w.optLong("amt") != cr.amount) continue;
            long t0 = w.optLong("t");
            if (cr.time < t0 - 60000 || cr.time > t0 + 45 * DAY) continue;
            String acc = w.optString("acc");
            if (!acc.isEmpty() && !cr.account.isEmpty() && !Ledger.sameAccount(acc, cr.account)) continue;
            String ref = w.optString("ref");
            boolean looks = cr.kind == Txn.REFUND || BACK.matcher(cr.raw).find() || BACK.matcher(cr.merchant).find()
                    || (!ref.isEmpty() && cr.raw.contains(ref));
            if (!looks) continue;
            try { w.put("status", "back").put("back", cr.id).put("backT", cr.time); } catch (Exception ignored) { }
            saveAll(c, l);
            cr.kind = Txn.TRANSFER;
            cr.category = Cats.REFUND;
            cr.userCategory = true;
            cr.note = "Money back for the failed payment of " + Fmt.date(t0);
            s.update(cr);
            Txn d = s.byId(w.optString("txn"));
            if (d != null) { d.note = "Failed payment — the money came back on " + Fmt.date(cr.time) + " ✓"; s.update(d); }
            if (!s.inBatch() && System.currentTimeMillis() - cr.time < 6 * 3600000L && s.flag(Store.S_REMIND))
                Alerts.postTo(c, Alerts.CH_ALERTS, 760 + (cr.id.hashCode() & 0xFF), "✓ " + Fmt.plain(cr.amount) + " is back",
                        "The failed payment of " + Fmt.date(t0) + " came back to " + (cr.account.isEmpty() ? "your account" : cr.account) + ".", Refunds.class);
            return true;
        }
        return false;
    }

    /** Background: remind at day 5 and day 10 if nothing came back. */
    static void check(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_REMIND)) return;
        long now = System.currentTimeMillis();
        List<JSONObject> l = all(c);
        boolean changed = false;
        for (JSONObject w : l) {
            if (!waiting(w)) continue;
            long days = (now - w.optLong("t")) / DAY;
            String stage = days >= 10 ? "n10" : days >= 5 ? "n5" : null;
            if (stage == null || w.optBoolean(stage)) continue;
            try { w.put(stage, true); if (stage.equals("n10")) w.put("n5", true); } catch (Exception ignored) { }
            changed = true;
            Alerts.postTo(c, Alerts.CH_ALERTS, 800 + (w.optString("id").hashCode() & 0xFF),
                    "↩️ " + Fmt.plain(w.optLong("amt")) + " still hasn't come back",
                    "Failed on " + Fmt.date(w.optLong("t")) + ". Banks must return it " + (w.optBoolean("merchant") ? "within 5 days" : "by the next day")
                            + " and pay ₹100 a day after that (RBI rule). Raise a complaint in your UPI app (Help → Report issue) or call the bank.", Refunds.class);
        }
        if (changed) saveAll(c, l);
    }

    static long waitingTotal(Context c) {
        long t = 0;
        for (JSONObject w : waiting(c)) t += w.optLong("amt");
        return t;
    }

    /** Overdue ones (past the bank's deadline), for the Home strip. */
    static int overdue(Context c) {
        int n = 0;
        long now = System.currentTimeMillis();
        for (JSONObject w : waiting(c)) if (now > dueBy(w)) n++;
        return n;
    }

    static String summary(Context c) {
        List<JSONObject> w = waiting(c);
        if (!w.isEmpty()) return Fmt.rs(waitingTotal(c)) + " on the way back" + (overdue(c) > 0 ? " · " + overdue(c) + " late" : "");
        int back = 0;
        for (JSONObject x : all(c)) if ("back".equals(x.optString("status"))) back++;
        return back > 0 ? back + " failed " + (back == 1 ? "payment" : "payments") + " came back ✓" : "Failed payments are watched by themselves";
    }

    // ------------------------------------------------------------------ screen

    @Override
    String title() { return "Refunds"; }

    @Override
    void build() {
        List<JSONObject> l = all(this);
        java.util.Collections.sort(l, (x, y) -> Long.compare(y.optLong("t"), x.optLong("t")));
        long wait = waitingTotal(this);
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "On the way back", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(wait), 28, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, wait == 0 ? "Nothing waiting. When a payment fails but money leaves your account, it shows up here and is kept out of your spending."
                : "Failed payments aren't counted as spending. When the money comes back, Money links it by itself."));
        add(hero);

        List<JSONObject> w = new ArrayList<>(), done = new ArrayList<>();
        for (JSONObject x : l) (waiting(x) ? w : done).add(x);
        if (!w.isEmpty()) { box.addView(label("Waiting")); for (JSONObject x : w) add(card(x)); }
        if (!done.isEmpty()) { box.addView(label("Done")); for (int i = 0; i < Math.min(15, done.size()); i++) add(card(done.get(i))); }

        LinearLayout rule = Ui.card(this);
        rule.addView(Ui.text(this, "ⓘ  The RBI rule", 14.5f, Ui.BOLD));
        TextView rt = Ui.sub(this, "Failed UPI / IMPS transfer: the bank must return the money by the next day (T+1). Shop, card or ATM: within 5 days (T+5). "
                + "After that the bank owes you ₹100 for every day of delay.\n\nStill not back? Raise it in your UPI app (Help → Report an issue), "
                + "then call the bank. If it's not solved in 30 days, you can complain at cms.rbi.org.in.\n\nTo track a payment yourself: open it → “❌ Payment failed?”.");
        rt.setPadding(0, Ui.dp(this, 6), 0, 0);
        rule.addView(rt);
        add(rule);
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s.toUpperCase(Locale.ROOT), 11.5f, Ui.col(this, R.color.text2), Ui.BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), 0, Ui.dp(this, 8));
        return t;
    }

    private View card(JSONObject w) {
        LinearLayout c = Ui.card(this);
        boolean wt = waiting(w);
        long now = System.currentTimeMillis();
        boolean late = wt && now > dueBy(w);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        String st = w.optString("status");
        h.addView(Ui.badge(this, wt ? (late ? "⏰" : "↩️") : "back".equals(st) ? "✅" : "✖️",
                wt ? (late ? 0xFFEF4444 : 0xFFF59E0B) : "back".equals(st) ? 0xFF10B981 : 0xFF94A3B8, 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        String who = w.optString("who");
        col.addView(Ui.oneLine(Ui.text(this, Fmt.rs(w.optLong("amt")) + (who.isEmpty() ? "" : " · " + who), 15, Ui.BOLD)));
        long days = (now - w.optLong("t")) / DAY;
        String line = "Failed " + Fmt.date(w.optLong("t")) + (w.optString("acc").isEmpty() ? "" : " · " + w.optString("acc"));
        if (wt) line += late ? "\nLate by " + Math.max(1, (now - dueBy(w)) / DAY) + (((now - dueBy(w)) / DAY) <= 1 ? " day" : " days") + " — the bank owes ₹100 a day"
                : "\nShould be back by " + Fmt.date(dueBy(w)) + " · day " + (days + 1);
        else if ("back".equals(st)) line += "\nCame back " + Fmt.date(w.optLong("backT")) + " ✓";
        else line += "\nCounted as spent";
        TextView lt = Ui.text(this, line, 12.5f, late ? Ui.col(this, R.color.danger) : Ui.col(this, R.color.text2), Ui.REGULAR);
        col.addView(lt);
        h.addView(col, Ui.weight1());
        c.addView(h);
        c.setOnClickListener(v -> menu(w));
        return c;
    }

    private void menu(JSONObject w) {
        boolean wt = waiting(w);
        List<String> opts = new ArrayList<>();
        if (wt) { opts.add("✓ It came back"); opts.add("✖ It won't come back — count it as spent"); }
        opts.add("Open the payment");
        opts.add("Stop tracking");
        new Ui.Popup(this).setTitle(Fmt.rs(w.optLong("amt")) + (w.optString("who").isEmpty() ? "" : " · " + w.optString("who")))
                .setItems(opts.toArray(new String[0]), (d, i) -> {
                    String o = opts.get(i);
                    Store s = Store.get(this);
                    Txn t = s.byId(w.optString("txn"));
                    List<JSONObject> l = all(this);
                    JSONObject me = null;
                    for (JSONObject x : l) if (x.optString("id").equals(w.optString("id"))) me = x;
                    if (me == null) return;
                    try {
                        if (o.startsWith("✓")) {
                            me.put("status", "back").put("backT", System.currentTimeMillis());
                            if (t != null) { t.note = "Failed payment — came back ✓"; s.update(t); }
                        } else if (o.startsWith("✖")) {
                            me.put("status", "lost");
                            if (t != null) { t.kind = Txn.NORMAL; t.note = "Failed payment that didn't come back"; s.update(t); }
                        } else if (o.startsWith("Open")) {
                            if (t != null) startActivity(new android.content.Intent(this, MainActivity.class).putExtra("txn", t.id)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            return;
                        } else {
                            l.remove(me);
                            if (t != null && t.kind == Txn.TRANSFER && "waiting".equals(w.optString("status"))) { t.kind = Txn.NORMAL; t.note = ""; s.update(t); }
                        }
                    } catch (Exception ignored) { }
                    saveAll(this, l);
                    rebuild();
                }).show();
    }
}
