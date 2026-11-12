package com.debangshu.money;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Insights for any month: where the money went, day by day, top places, repeating payments, budgets. */
final class InsightsPage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;
    private long month = Fmt.startOfMonth(System.currentTimeMillis());

    InsightsPage(MainActivity a) { this.a = a; }

    View view() {
        if (scroll == null) {
            scroll = new ScrollView(a);
            box = Ui.vbox(a);
            box.setPadding(0, 0, 0, Ui.dp(a, 24));
            scroll.addView(box);
        }
        return scroll;
    }

    void refresh() {
        view();
        box.removeAllViews();
        box.addView(a.topBar("Insights", null));
        LinearLayout body = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        body.setPadding(p, 0, p, 0);
        box.addView(body);

        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        long end = Fmt.addMonths(month, 1);
        boolean thisMonth = month == Fmt.startOfMonth(now);

        // ---- month switcher
        LinearLayout nav = Ui.hbox(a);
        nav.setGravity(Gravity.CENTER);
        TextView prev = Ui.chip(a, "‹", false);
        TextView next = Ui.chip(a, "›", false);
        TextView name = Ui.text(a, Fmt.month(month), 17, Ui.BOLD);
        name.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> { month = Fmt.addMonths(month, -1); refresh(); });
        next.setOnClickListener(v -> { if (!thisMonth) { month = Fmt.addMonths(month, 1); refresh(); } });
        next.setAlpha(thisMonth ? 0.3f : 1f);
        nav.addView(prev);
        nav.addView(name, Ui.weight1());
        nav.addView(next);
        body.addView(nav, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 10));

        long spent = Ledger.spent(all, month, end);
        long income = Ledger.income(all, month, end);
        long refunds = Ledger.refunds(all, month, end);

        // ---- summary
        LinearLayout sum = Ui.hbox(a);
        sum.addView(stat("Spent", Fmt.rs(spent), Ui.col(a, R.color.text)), Ui.weight1());
        sum.addView(stat("Money in", Fmt.rs(income), Ui.col(a, R.color.success)), Ui.weight1());
        long saved = income - spent;
        String rate = income > 0 ? " (" + Math.round(saved * 100.0 / income) + "%)" : "";
        sum.addView(stat(saved >= 0 ? "Saved" : "Overspent", Fmt.rs(Math.abs(saved)) + rate,
                saved >= 0 ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.danger)), Ui.weight1());
        LinearLayout sc = Ui.card(a);
        sc.addView(sum);
        if (refunds > 0) {
            TextView r = Ui.sub(a, "Refunds & cashback: " + Fmt.rs(refunds));
            r.setPadding(0, Ui.dp(a, 8), 0, 0);
            sc.addView(r);
        }
        body.addView(sc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        // ---- where it went
        LinkedHashMap<String, Long> cats = Ledger.byCategory(all, month, end);
        LinearLayout cc = Ui.card(a);
        cc.addView(Ui.sectionTitle(a, "Where it went"));
        if (cats.isEmpty()) {
            cc.addView(Ui.sub(a, "Nothing spent this month yet."));
        } else {
            long[] vals = new long[Math.min(8, cats.size()) + (cats.size() > 8 ? 1 : 0)];
            int[] cols = new int[vals.length];
            int i = 0;
            long rest = 0;
            for (Map.Entry<String, Long> e : cats.entrySet()) {
                if (i < 8) { vals[i] = e.getValue(); cols[i] = Cats.color(e.getKey()); i++; }
                else rest += e.getValue();
            }
            if (cats.size() > 8) { vals[8] = rest; cols[8] = 0xFF94A3B8; }
            Charts.Donut d = new Charts.Donut(a);
            d.set(vals, cols, Fmt.shortRs(spent), cats.size() + (cats.size() == 1 ? " category" : " categories"));
            LinearLayout dc = Ui.hbox(a);
            dc.setGravity(Gravity.CENTER);
            dc.addView(d);
            cc.addView(dc, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 12));
            Map<String, Long> budgets = s.budgets();
            for (Map.Entry<String, Long> e : cats.entrySet()) {
                String c = e.getKey();
                Long b = budgets.get(c);
                int pct = (int) Math.round(e.getValue() * 100.0 / Math.max(1, spent));
                String sub = pct + "% of spending" + (b != null ? " · budget " + Fmt.rs(b) : "");
                float frac = b != null ? e.getValue() / (float) b : e.getValue() / (float) Math.max(1, cats.values().iterator().next());
                View row = Rows.amountRow(a, Cats.emoji(c), b != null && e.getValue() > b ? Ui.col(a, R.color.danger) : Cats.color(c), c, sub, e.getValue(), frac);
                row.setOnClickListener(v -> categorySheet(c));
                cc.addView(row);
            }
        }
        body.addView(cc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        // ---- day by day
        long[] days = Ledger.byDay(all, month);
        LinearLayout dd = Ui.card(a);
        dd.addView(Ui.sectionTitle(a, "Day by day"));
        TextView picked = Ui.sub(a, "Tap a bar to see that day");
        Charts.Bars bars = new Charts.Bars(a);
        Long monthBudget = budgets(s).get("");
        long perDay = monthBudget == null ? 0 : monthBudget / Fmt.daysInMonth(month);
        int todayIdx = thisMonth ? Fmt.cal(now).get(java.util.Calendar.DAY_OF_MONTH) - 1 : -1;
        bars.set(days, todayIdx, perDay, (day, v) -> picked.setText((day + 1) + " " + Fmt.monthShort(month) + ": " + Fmt.rs(v)
                + (perDay > 0 ? (v > perDay ? " · over the daily budget" : " · within the daily budget") : "")));
        dd.addView(bars);
        dd.addView(picked);
        int countDays = thisMonth ? todayIdx + 1 : days.length;
        TextView avg = Ui.sub(a, "Average " + Fmt.rs(spent / Math.max(1, countDays)) + " a day" + (thisMonth && spent > 0
                ? " · heading for " + Fmt.rs(Ledger.forecast(all, now)) : ""));
        avg.setPadding(0, Ui.dp(a, 4), 0, 0);
        dd.addView(avg);
        body.addView(dd, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        // ---- top places
        LinkedHashMap<String, Long> shops = Ledger.byMerchant(all, month, end, null);
        if (!shops.isEmpty()) {
            LinearLayout tc = Ui.card(a);
            tc.addView(Ui.sectionTitle(a, "Top places"));
            int i = 0;
            long top = shops.values().iterator().next();
            for (Map.Entry<String, Long> e : shops.entrySet()) {
                if (i++ >= 6) break;
                int n = 0;
                String cat = Cats.OTHER;
                for (Txn t : all) if (t.isSpend() && t.time >= month && t.time < end && e.getKey().equals(t.merchant.isEmpty() ? "Unknown" : t.merchant)) { n++; cat = t.category; }
                tc.addView(Rows.amountRow(a, Cats.emoji(cat), Cats.color(cat), e.getKey(), n + (n == 1 ? " payment" : " payments"),
                        e.getValue(), e.getValue() / (float) top));
            }
            body.addView(tc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
        }

        // ---- repeating payments
        List<Ledger.Recurring> rec = Ledger.recurring(all, now);
        LinearLayout rcard = Ui.card(a);
        long monthly = 0;
        for (Ledger.Recurring r : rec) monthly += r.amount * 30 / Math.max(1, r.period);
        LinearLayout rh = Ui.hbox(a);
        rh.addView(Ui.sectionTitle(a, "Repeating payments"), Ui.weight1());
        if (!rec.isEmpty()) {
            TextView m = Ui.text(a, "≈ " + Fmt.rs(monthly) + "/mo", 14, Ui.col(a, R.color.text2), Ui.BOLD);
            m.setPadding(0, 0, 0, Ui.dp(a, 8));
            rh.addView(m);
        }
        rcard.addView(rh);
        if (rec.isEmpty()) rcard.addView(Ui.sub(a, "Subscriptions, EMIs, rent and bills show up here by themselves after they repeat."));
        for (Ledger.Recurring r : rec) {
            String sub = r.periodName() + " · next " + Fmt.day(r.next) + " · seen " + r.count + "×";
            if (r.lastAmount > r.prevAmount * 1.05 && r.prevAmount > 0)
                sub += "\n▲ Price went up: " + Fmt.rs(r.prevAmount) + " → " + Fmt.rs(r.lastAmount);
            rcard.addView(Rows.amountRow(a, Cats.emoji(r.category), Cats.color(r.category), r.name, sub, r.lastAmount, -1));
        }
        body.addView(rcard, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        // ---- budgets
        LinearLayout bc = Ui.card(a);
        bc.addView(Ui.sectionTitle(a, "Budgets"));
        Map<String, Long> budgets = s.budgets();
        if (budgets.isEmpty()) {
            bc.addView(Ui.sub(a, "Set limits and get a warning at 80% and 100%. Money can suggest them from your last 3 months."));
        } else {
            for (Map.Entry<String, Long> e : budgets.entrySet()) {
                String c = e.getKey();
                long used = 0;
                for (Txn t : all) if (t.isSpend() && t.time >= month && t.time < end && (c.isEmpty() || c.equals(t.category))) used += t.amount;
                float f = used / (float) e.getValue();
                String label = c.isEmpty() ? "Whole month" : c;
                bc.addView(Rows.amountRow(a, c.isEmpty() ? "🎯" : Cats.emoji(c), f >= 1 ? Ui.col(a, R.color.danger) : f >= 0.8 ? Ui.col(a, R.color.warn) : Ui.col(a, R.color.accent),
                        label, Fmt.rs(used) + " of " + Fmt.rs(e.getValue()) + (f >= 1 ? " · over" : " · " + Fmt.rs(e.getValue() - used) + " left"), used, f));
            }
        }
        TextView setB = Ui.button(a, budgets.isEmpty() ? "Suggest budgets" : "Edit budgets", budgets.isEmpty());
        setB.setOnClickListener(v -> editBudgets());
        LinearLayout br = Ui.hbox(a);
        br.setPadding(0, Ui.dp(a, 10), 0, 0);
        br.addView(setB);
        bc.addView(br);
        body.addView(bc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
    }

    private static Map<String, Long> budgets(Store s) { return s.budgets(); }

    private View stat(String label, String value, int color) {
        LinearLayout c = Ui.vbox(a);
        c.addView(Ui.text(a, label, 12.5f, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        c.addView(Ui.oneLine(Ui.text(a, value, 16, color, Ui.EXTRABOLD)));
        return c;
    }

    /** Tap a category: its shops this month, and a way to see every payment. */
    private void categorySheet(String cat) {
        long end = Fmt.addMonths(month, 1);
        List<Txn> all = Store.get(a).txns();
        LinkedHashMap<String, Long> shops = Ledger.byMerchant(all, month, end, cat);
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        b.setPadding(p, 0, p, 0);
        int i = 0;
        long top = shops.isEmpty() ? 1 : shops.values().iterator().next();
        for (Map.Entry<String, Long> e : shops.entrySet()) {
            if (i++ >= 12) break;
            b.addView(Rows.amountRow(a, null, Cats.color(cat), e.getKey(), null, e.getValue(), e.getValue() / (float) top));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        new Ui.Popup(a).sheet().setTitle(Cats.emoji(cat) + "  " + cat + " · " + Fmt.month(month)).setView(Ui.capHeight(sv, 0.5f))
                .setPositiveButton("See payments", (d, w) -> a.showFiltered(cat, month, end, cat + " · " + Fmt.month(month)))
                .setNegativeButton("Close", null).show();
    }

    /** Budgets: suggested from the average of the last 3 full months (rounded up to ₹500). */
    private void editBudgets() {
        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        long ms = Fmt.startOfMonth(now);
        long from = Fmt.addMonths(ms, -3);
        Map<String, Long> cur = s.budgets();
        LinkedHashMap<String, Long> avg = Ledger.byCategory(all, from, ms);
        List<String> cats = new ArrayList<>();
        cats.add("");
        for (String c : avg.keySet()) if (!cats.contains(c) && !Cats.CASH.equals(c) && !Cats.UNRECORDED.equals(c)) cats.add(c);
        for (String c : cur.keySet()) if (!cats.contains(c)) cats.add(c);
        int months = 3;
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        b.setPadding(p, 0, p, 0);
        TextView info = Ui.sub(a, "Filled in from your average over the last 3 months. Clear a box for no budget.");
        info.setPadding(0, 0, 0, Ui.dp(a, 8));
        b.addView(info);
        List<EditText> fields = new ArrayList<>();
        long total = 0;
        for (Long v : avg.values()) total += v;
        for (String c : cats) {
            LinearLayout r = Ui.hbox(a);
            r.addView(Ui.oneLine(Ui.text(a, c.isEmpty() ? "🎯  Whole month" : Cats.emoji(c) + "  " + c, 14.5f, Ui.SEMIBOLD)), Ui.weight1());
            EditText e = new EditText(a);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setTypeface(Ui.face(a, Ui.REGULAR));
            e.setHint("—");
            e.setGravity(Gravity.END);
            Long have = cur.get(c);
            long suggest = c.isEmpty() ? total / months : (avg.containsKey(c) ? avg.get(c) / months : 0);
            suggest = ((suggest / 100 + 499) / 500) * 500;   // rupees, rounded up to 500
            if (have != null) e.setText(String.valueOf(have / 100));
            else if (cur.isEmpty() && suggest >= 500) e.setText(String.valueOf(suggest));
            r.addView(e, Ui.lp(Ui.dp(a, 110), -2));
            b.addView(r);
            fields.add(e);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        new Ui.Popup(a).setTitle("Monthly budgets (₹)").setView(Ui.capHeight(sv, 0.55f))
                .setPositiveButton("Save", (d, w) -> {
                    for (int i = 0; i < cats.size(); i++) {
                        String txt = fields.get(i).getText().toString().trim();
                        long v = txt.isEmpty() ? 0 : SmsParser.paise(txt);
                        s.setBudget(cats.get(i), Math.max(0, v));
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
