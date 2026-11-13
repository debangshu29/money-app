package com.debangshu.money;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Leak finder: subscriptions, bank charges, food delivery and small daily spends — what they cost in a year and in 10 years. */
public class LeaksActivity extends Screen {

    @Override String title() { return "Leak finder"; }

    @Override
    void build() {
        List<Txn> all = Store.get(this).txns();
        long now = System.currentTimeMillis(), m30 = now - 30 * GoalMath.DAY;
        long yearly = 0;

        add(Explain.box(this, "leaks"));

        // ---- subscriptions
        List<Ledger.Recurring> subs = new ArrayList<>();
        for (Ledger.Recurring r : Ledger.recurring(all, now))
            if (Cats.SUBS.equals(r.category) || Cats.RECHARGE.equals(r.category) && r.period > 40) subs.add(r);
        JSONObject cancel = Store.get(this).obj("leakCancel");
        long subYear = 0, cancelYear = 0;
        for (Ledger.Recurring r : subs) {
            long y = r.amount * 365 / Math.max(7, r.period);
            subYear += y;
            if (cancel.optBoolean(r.key)) cancelYear += y;
        }
        if (!subs.isEmpty()) {
            LinearLayout c = leakCard("📺", 0xFF8B5CF6, "Subscriptions", subs.size() + (subs.size() == 1 ? " running" : " running") + " · " + Fmt.rs(subYear) + " a year", subYear);
            for (Ledger.Recurring r : subs) {
                boolean off = cancel.optBoolean(r.key);
                LinearLayout row = Ui.hbox(this);
                row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
                LinearLayout col = Ui.vbox(this);
                col.addView(Ui.oneLine(Ui.text(this, r.name, 14, Ui.SEMIBOLD)));
                col.addView(Ui.text(this, Fmt.rs(r.amount) + " · " + r.periodName().toLowerCase(Locale.ROOT) + " · " + Fmt.rs(r.amount * 365 / Math.max(7, r.period)) + "/yr",
                        12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
                row.addView(col, Ui.weight1());
                TextView t = Ui.chip(this, off ? "Will cancel" : "Still using", off);
                if (!off) t.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
                t.setOnClickListener(v -> {
                    JSONObject o = Store.get(this).obj("leakCancel");
                    try { if (off) o.remove(r.key); else o.put(r.key, true); } catch (Exception ignored) { }
                    Store.get(this).putObj("leakCancel", o);
                    rebuild();
                });
                row.addView(t);
                c.addView(row);
            }
            if (cancelYear > 0) c.addView(note("Cancelling those saves " + Fmt.rs(cancelYear) + " a year. Money only marks them — cancel inside each app."));
            add(c);
            yearly += cancelYear;
        }

        // ---- bank charges
        List<Txn> fees = Smart.charges(all, Smart.fyStart(now));
        long feeSum = 0;
        Map<String, Long> feeBy = new HashMap<>();
        for (Txn t : fees) { feeSum += t.amount; String k = t.merchant.isEmpty() ? "Charge" : t.merchant; feeBy.put(k, (feeBy.containsKey(k) ? feeBy.get(k) : 0) + t.amount); }
        if (feeSum > 0) {
            LinearLayout c = leakCard("🧾", 0xFF78716C, "Bank charges & fees", Fmt.rs(feeSum) + " since April", feeSum);
            int n = 0;
            for (Map.Entry<String, Long> e : feeBy.entrySet()) {
                if (n++ >= 4) break;
                c.addView(line(e.getKey(), Fmt.rs(e.getValue())));
            }
            c.addView(note("Most charges can be avoided: keep the minimum balance, pay card bills on time, and switch off paid SMS alerts if your bank charges for them."));
            add(c);
            yearly += feeSum;
        }

        // ---- food delivery
        long del = 0;
        int delN = 0;
        for (Txn t : all) {
            if (!t.isSpend() || t.time < m30) continue;
            if (SaveMath.isDelivery(t.merchant + " " + t.vpa)) { del += t.amount; delN++; }
        }
        if (delN > 0) {
            LinearLayout c = leakCard("🛵", 0xFFF97316, "Food delivery", delN + " orders in 30 days · " + Fmt.rs(del), del * 12);
            c.addView(note("Delivery fee + packing + higher menu prices = the same food costs much more at home. Ordering half as often saves about "
                    + Fmt.rs(del * 6) + " a year."));
            TextView go = Ui.chip(this, "🔥  Start a No-delivery challenge", false);
            go.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.accent_soft), 18));
            go.setOnClickListener(v -> {
                boolean ok = Challenges.begin(this, "delivery", 0);
                Ui.toast(this, ok ? "Challenge started 🔥" : "It's already running");
                startActivity(new Intent(this, Challenges.class));
            });
            c.addView(go, Ui.margins(Ui.lp(-2, -2), this, 0, 10, 0, 0));
            add(c);
            yearly += del * 6;
        }

        // ---- small daily spends
        Map<String, long[]> small = new HashMap<>();
        long smallSum = 0;
        int smallN = 0;
        for (Txn t : all) {
            if (!t.isSpend() || t.time < m30 || t.amount >= 20000) continue;
            int b = SaveMath.bucket(t.category, MoneyFacts.moved(this));
            if (b != SaveMath.WANTS) continue;
            smallSum += t.amount;
            smallN++;
            String k = t.unnamed() ? "Unnamed UPI" : t.merchant;
            long[] v = small.get(k);
            if (v == null) small.put(k, v = new long[2]);
            v[0] += t.amount;
            v[1]++;
        }
        if (smallN >= 5) {
            LinearLayout c = leakCard("☕", 0xFF0EA5E9, "Small daily spends", smallN + " payments under ₹200 in 30 days · " + Fmt.rs(smallSum), smallSum * 12);
            List<Map.Entry<String, long[]>> l = new ArrayList<>(small.entrySet());
            java.util.Collections.sort(l, (x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
            for (int i = 0; i < Math.min(4, l.size()); i++) {
                long[] v = l.get(i).getValue();
                c.addView(line(l.get(i).getKey(), Fmt.rs(v[0] / Math.max(1, v[1])) + " × " + v[1]));
            }
            c.addView(note("Each one feels like nothing — like a drop from a tap. Together: " + Fmt.rs(smallSum * 12) + " a year."));
            TextView go = Ui.chip(this, "💸  See the true price of a habit", false);
            go.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.accent_soft), 18));
            go.setOnClickListener(v -> startActivity(new Intent(this, HabitActivity.class)));
            c.addView(go, Ui.margins(Ui.lp(-2, -2), this, 0, 10, 0, 0));
            add(c);
            yearly += smallSum * 12 / 3;
        }

        if (getChildCountOfBox() <= 1) {   // only the explanation box
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "No leaks found 👍", 16, Ui.BOLD));
            c.addView(Ui.sub(this, "Nothing obvious in the last 30 days. Check again in a few weeks."));
            add(c);
            return;
        }
        if (yearly > 0) {
            double ten = SaveMath.sip(yearly / 1200.0, 120, 0.12);
            LinearLayout c = Ui.card(this);
            c.setBackground(Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 22)));
            c.addView(Ui.text(this, "Fix the easy ones and save about", 13, Ui.col(this, R.color.text2), Ui.BOLD));
            c.addView(Ui.text(this, Fmt.rs(yearly) + " a year", 26, Ui.EXTRABOLD));
            c.addView(Ui.sub(this, "Invested every month for 10 years at 12%: about " + Fmt.rs(Math.round(ten) * 100) + ". (An example rate, not a promise.)"));
            box.addView(c, 1, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        }
    }

    private int getChildCountOfBox() { return box.getChildCount(); }

    private LinearLayout leakCard(String emoji, int color, String title, String sub, long perYear) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, emoji, color, 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, title, 16, Ui.BOLD));
        col.addView(Ui.sub(this, sub));
        h.addView(col, Ui.weight1());
        c.addView(h);
        View d = Ui.divider(this);
        c.addView(d, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 4));
        return c;
    }

    private View line(String l, String r) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.oneLine(Ui.text(this, l, 14, Ui.SEMIBOLD)), Ui.weight1());
        row.addView(Ui.text(this, r, 14, Ui.BOLD));
        return row;
    }

    private TextView note(String s) {
        TextView t = Ui.sub(this, s);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        return t;
    }
}
