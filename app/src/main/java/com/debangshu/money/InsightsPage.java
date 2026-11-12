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

        // ---- Ask bar
        LinearLayout ask = Ui.hbox(a);
        ask.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 24));
        ask.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        android.widget.ImageView ai = new android.widget.ImageView(a);
        ai.setImageResource(R.drawable.ic_chat);
        ai.setColorFilter(Ui.col(a, R.color.accent));
        ask.addView(ai, Ui.lp(Ui.dp(a, 20), Ui.dp(a, 20)));
        TextView at = Ui.text(a, "Ask: “food this month”, “biggest expenses”…", 14.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
        at.setPadding(Ui.dp(a, 10), 0, 0, 0);
        ask.addView(Ui.oneLine(at), Ui.weight1());
        ask.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, AskActivity.class)));
        body.addView(ask, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

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
        long perDay = 0;
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

        // ---- where you spend (places, from saved locations)
        View places = places(all, month, end);
        if (places != null) body.addView(places, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
    }

    /** Groups this month's payments by the area they were made in (needs “Save where you paid”). */
    private View places(List<Txn> all, long from, long to) {
        Map<String, long[]> byPlace = new java.util.HashMap<>();
        Map<String, Txn> sample = new java.util.HashMap<>();
        int located = 0, total = 0;
        for (Txn t : all) {
            if (!t.isSpend() || t.time < from || t.time >= to) continue;
            total++;
            if (!t.hasLoc()) continue;
            located++;
            String k = t.place.isEmpty() ? String.format(java.util.Locale.ROOT, "Pin %.3f, %.3f", t.lat, t.lng) : t.place.replaceFirst("^near ", "");
            String[] parts = k.split(", ");
            if (parts.length > 1) k = parts[parts.length - 1];   // group by area, not by shop
            long[] v = byPlace.get(k);
            if (v == null) byPlace.put(k, v = new long[2]);
            v[0] += t.amount;
            v[1]++;
            sample.put(k, t);
        }
        LinearLayout c = Ui.card(a);
        c.addView(Ui.sectionTitle(a, "Where you spend"));
        if (located == 0) {
            TextView t = Ui.sub(a, Store.get(a).flag(Store.S_LOCATION)
                    ? "Places appear here as you pay (only payments made after turning on “Save where you paid”)."
                    : "Turn on Settings → Remember every payment → Save where you paid, to see the areas where your money goes.");
            c.addView(t);
            return c;
        }
        Map<String, Long> sums = new java.util.HashMap<>();
        for (Map.Entry<String, long[]> e : byPlace.entrySet()) sums.put(e.getKey(), e.getValue()[0]);
        long top = 1;
        for (long v : sums.values()) top = Math.max(top, v);
        int i = 0;
        for (Map.Entry<String, Long> e : Ledger.sorted(sums).entrySet()) {
            if (i++ >= 6) break;
            long n = byPlace.get(e.getKey())[1];
            View row = Rows.amountRow(a, "📍", 0xFFEF4444, e.getKey(), n + (n == 1 ? " payment" : " payments") + " · tap for map", e.getValue(), e.getValue() / (float) top);
            Txn s0 = sample.get(e.getKey());
            row.setOnClickListener(v -> Locator.openMap(a, s0));
            c.addView(row);
        }
        if (located < total) {
            TextView t = Ui.text(a, located + " of " + total + " payments have a place", 11.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
            t.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), 0, 0);
            c.addView(t);
        }
        return c;
    }

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

}
