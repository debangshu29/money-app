package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Credit cards: how much of each limit you're using (keep it under 30% for a good credit score),
 * the latest bill and due date, and which card gives the longest interest-free time if you buy today.
 */
public class CardsActivity extends Screen {

    static final String LIMITS = "cardLimits";
    private static final long DAY = 86400000L;

    static final class Card {
        Account acc;
        long limit = -1, used = -1, sinceStatement;
        Store.Bill bill;
        int statementDay = -1, graceDays = -1, freeDays = -1;
        float util() { return limit > 0 && used >= 0 ? used / (float) limit : -1; }
    }

    static List<Card> cards(Context c) {
        Store s = Store.get(c);
        JSONObject lim = s.obj(LIMITS);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        List<Card> out = new ArrayList<>();
        for (Account a : s.accounts()) {
            if (!a.card) continue;
            Card k = new Card();
            k.acc = a;
            k.limit = lim.optLong(a.key, -1);
            for (Store.Bill b : s.bills()) if (b.account.equals(a.key) && (k.bill == null || b.seen > k.bill.seen)) k.bill = b;
            if (k.bill != null && k.bill.seen > 0) {
                k.statementDay = Fmt.cal(k.bill.seen).get(Calendar.DAY_OF_MONTH);
                if (k.bill.dueDate > k.bill.seen) k.graceDays = (int) Math.round((k.bill.dueDate - k.bill.seen) / (double) DAY);
            }
            long since = k.bill != null ? k.bill.seen : Fmt.startOfMonth(now);
            for (Txn t : all) if (t.account.equals(a.key) && t.time >= since && !t.credit && t.kind != Txn.IGNORED && t.kind != Txn.SNAPSHOT) k.sinceStatement += t.amount;
            if (k.limit > 0 && a.limit >= 0 && now - a.limitTime < 45 * DAY) k.used = Math.max(0, k.limit - a.limit);
            else k.used = (k.bill != null && !k.bill.paid && k.bill.due > 0 ? k.bill.due : 0) + k.sinceStatement;
            if (k.statementDay > 0) {
                Calendar c0 = Fmt.cal(Fmt.startOfDay(now));
                Calendar st = Fmt.cal(Fmt.startOfDay(now));
                st.set(Calendar.DAY_OF_MONTH, Math.min(k.statementDay, st.getActualMaximum(Calendar.DAY_OF_MONTH)));
                if (!st.after(c0)) {
                    st.add(Calendar.MONTH, 1);
                    st.set(Calendar.DAY_OF_MONTH, Math.min(k.statementDay, st.getActualMaximum(Calendar.DAY_OF_MONTH)));
                }
                int toStatement = (int) Math.round((st.getTimeInMillis() - c0.getTimeInMillis()) / (double) DAY);
                k.freeDays = toStatement + (k.graceDays > 0 ? k.graceDays : 18);
            }
            out.add(k);
        }
        return out;
    }

    static Card best(List<Card> cs) {
        Card b = null;
        for (Card k : cs) if (k.freeDays > 0 && (k.util() < 0 || k.util() < 0.9f) && (b == null || k.freeDays > b.freeDays)) b = k;
        return b;
    }

    static String summary(Context c) {
        List<Card> cs = cards(c);
        if (cs.isEmpty()) return "Appears when a card is used";
        Card b = best(cs);
        if (b != null && cs.size() > 1) return "Today: use " + b.acc.display();
        long used = 0, lim = 0;
        for (Card k : cs) if (k.limit > 0) { used += Math.max(0, k.used); lim += k.limit; }
        return lim > 0 ? Math.round(used * 100.0 / lim) + "% of limit used" : cs.size() + (cs.size() == 1 ? " card" : " cards");
    }

    static float usedFrac(Context c) {
        long used = 0, lim = 0;
        for (Card k : cards(c)) if (k.limit > 0) { used += Math.max(0, k.used); lim += k.limit; }
        return lim > 0 ? used / (float) lim : -1;
    }

    @Override
    String title() { return "Credit cards"; }

    @Override
    void build() {
        List<Card> cs = cards(this);
        if (cs.isEmpty()) {
            LinearLayout e = Ui.card(this);
            e.addView(Ui.text(this, "💳", 34, Ui.REGULAR));
            e.addView(Ui.text(this, "No credit cards yet", 16, Ui.BOLD));
            e.addView(Ui.sub(this, "Cards appear here by themselves after the first card payment or bill message."));
            add(e);
            return;
        }
        Card b = best(cs);
        if (b != null) {
            LinearLayout hero = Ui.card(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFF312E81, 0xFF6D28D9});
            g.setCornerRadius(Ui.dp(this, 22));
            hero.setBackground(g);
            hero.addView(Ui.text(this, "Best card to use today", 13, 0xD9FFFFFF, Ui.SEMIBOLD));
            hero.addView(Ui.text(this, b.acc.display(), 24, 0xFFFFFFFF, Ui.EXTRABOLD));
            hero.addView(Ui.text(this, "About " + b.freeDays + " days before you pay interest on what you buy today"
                    + (cs.size() > 1 ? " — the longest of your cards." : "."), 13, 0xD9FFFFFF, Ui.REGULAR));
            add(hero);
        }
        long used = 0, lim = 0;
        for (Card k : cs) if (k.limit > 0) { used += Math.max(0, k.used); lim += k.limit; }
        if (lim > 0) {
            LinearLayout u = Ui.card(this);
            float f = used / (float) lim;
            u.addView(Ui.text(this, "Using " + Math.round(f * 100) + "% of " + Fmt.rs(lim), 16, Ui.BOLD));
            Charts.Progress p = new Charts.Progress(this);
            p.set(Math.min(1f, f), f <= 0.3f ? Ui.col(this, R.color.success) : f <= 0.5f ? 0xFFF59E0B : Ui.col(this, R.color.danger));
            u.addView(p, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 6));
            u.addView(Ui.sub(this, f <= 0.3f ? "Under 30% — good for your credit score ✓" : "Try to stay under 30% of the total limit — it helps your credit score."));
            add(u);
        }
        for (Card k : cs) add(card(k));
        TextView n = Ui.sub(this, "Tap a card to set its total limit (once). Statement day comes from your bill messages.");
        n.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        box.addView(n);
    }

    private View card(Card k) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.badge(this, "💳", 0xFF6D28D9, 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.oneLine(Ui.text(this, k.acc.display(), 15.5f, Ui.BOLD)));
        String sub = k.limit > 0 ? Fmt.rs(Math.max(0, k.used)) + " used of " + Fmt.rs(k.limit) : "Spent since last bill " + Fmt.rs(k.sinceStatement) + " · tap to add limit";
        col.addView(Ui.oneLine(Ui.sub(this, sub)));
        h.addView(col, Ui.weight1());
        float f = k.util();
        if (f >= 0) h.addView(Ui.text(this, Math.round(f * 100) + "%", 15, f <= 0.3f ? Ui.col(this, R.color.success) : f <= 0.5f ? 0xFFF59E0B : Ui.col(this, R.color.danger), Ui.EXTRABOLD));
        c.addView(h);
        if (f >= 0) {
            Charts.Progress p = new Charts.Progress(this);
            p.set(Math.min(1f, f), f <= 0.3f ? Ui.col(this, R.color.success) : f <= 0.5f ? 0xFFF59E0B : Ui.col(this, R.color.danger));
            c.addView(p, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 4));
        }
        List<String> lines = new ArrayList<>();
        if (k.bill != null) {
            String bl = "Last bill " + (k.bill.due > 0 ? Fmt.rs(k.bill.due) : "");
            if (k.bill.dueDate > 0) bl += " · due " + Fmt.date(k.bill.dueDate);
            bl += k.bill.paid ? " · paid ✓" : k.bill.dueDate > 0 && k.bill.dueDate < System.currentTimeMillis() ? " · overdue!" : "";
            lines.add(bl);
        }
        if (k.statementDay > 0) lines.add("Bill made around the " + ord(k.statementDay) + (k.graceDays > 0 ? " · " + k.graceDays + " days to pay" : ""));
        if (k.freeDays > 0) lines.add("Buy today → ~" + k.freeDays + " interest-free days");
        if (k.acc.limit >= 0) lines.add("Available limit (last message): " + Fmt.rs(k.acc.limit));
        for (String l : lines) {
            TextView t = Ui.text(this, l, 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR);
            t.setPadding(0, Ui.dp(this, 4), 0, 0);
            c.addView(t);
        }
        c.setOnClickListener(v -> setLimit(k));
        return c;
    }

    static String ord(int d) {
        String s = (d % 100 >= 11 && d % 100 <= 13) ? "th" : d % 10 == 1 ? "st" : d % 10 == 2 ? "nd" : d % 10 == 3 ? "rd" : "th";
        return d + s;
    }

    private void setLimit(Card k) {
        LinearLayout b = form();
        EditText e = field("Total credit limit ₹", true);
        if (k.limit > 0) e.setText(Long.toString(k.limit / 100));
        b.addView(e);
        TextView n = Ui.sub(this, "Find it in your card app or statement. It's stored only on this phone.");
        b.addView(n);
        new Ui.Popup(this).setTitle(k.acc.display()).setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    JSONObject o = Store.get(this).obj(LIMITS);
                    try { if (v > 0) o.put(k.acc.key, v); else o.remove(k.acc.key); } catch (Exception ignored) { }
                    Store.get(this).putObj(LIMITS, o);
                    rebuild();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
