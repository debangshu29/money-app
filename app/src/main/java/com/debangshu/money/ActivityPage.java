package com.debangshu.money;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every transaction, newest first, grouped by day, with search and quick filters. */
final class ActivityPage {

    private final MainActivity a;
    private LinearLayout root;
    private LinearLayout chips;
    private TextView filterBar;
    private EditText search;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private int quick = 0;   // 0 all, 1 spent, 2 money in, 3 needs a look, 4 not spending / hidden
    private int mode = 0;    // 0 list, 1 calendar
    private LinearLayout listPart, seg;
    private android.widget.ScrollView calPart;
    private LinearLayout calBox;
    private long calMonth = Fmt.startOfMonth(System.currentTimeMillis()), calDay = Fmt.startOfDay(System.currentTimeMillis());
    private static final String[] QUICK = {"All", "Spent", "Money in", "Needs a look", "Not counted"};

    ActivityPage(MainActivity a) { this.a = a; }

    void setQuick(int q) { quick = Math.max(0, Math.min(QUICK.length - 1, q)); mode = 0; }

    View view() {
        if (root != null) return root;
        root = Ui.vbox(a);
        ImageView add = Ui.icon(a, R.drawable.ic_add, Ui.col(a, R.color.text2), 22);
        add.setContentDescription("Add cash spend");
        add.setOnClickListener(v -> TxnSheet.addManual(a));
        root.addView(a.topBar("Activity", add));

        // List ⇄ Calendar
        seg = Ui.hbox(a);
        seg.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 20)));
        seg.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        root.addView(seg, Ui.margins(Ui.matchWrap(), a, 16, 0, 16, 10));

        listPart = Ui.vbox(a);
        calPart = new android.widget.ScrollView(a);
        calBox = Ui.vbox(a);
        calBox.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 24));
        calPart.addView(calBox);

        search = new EditText(a);
        search.setHint(Lang.t("Search name, amount, note…"));
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTypeface(Ui.face(a, Ui.REGULAR));
        search.setTextSize(15);
        search.setBackground(Ui.round(Ui.col(a, R.color.surface), Ui.dp(a, 22)));
        search.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 16), Ui.dp(a, 10));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        listPart.addView(search, Ui.margins(Ui.matchWrap(), a, 16, 0, 16, 8));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.hbox(a);
        chips.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 6));
        hs.addView(chips);
        listPart.addView(hs);

        filterBar = Ui.text(a, "", 13, Ui.col(a, R.color.accent), Ui.BOLD);
        filterBar.setPadding(Ui.dp(a, 20), Ui.dp(a, 2), Ui.dp(a, 20), Ui.dp(a, 4));
        filterBar.setOnClickListener(v -> { a.filterCategory = null; a.filterLabel = null; a.filterFrom = 0; a.filterTo = 0; quick = 0; refresh(); });
        listPart.addView(filterBar);

        list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 24));
        list.setClipToPadding(false);
        list.setAdapter(adapter);
        listPart.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(listPart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(calPart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void buildChips() {
        chips.removeAllViews();
        for (int i = 0; i < QUICK.length; i++) {
            TextView c = Ui.chip(a, QUICK[i], i == quick);
            final int idx = i;
            c.setOnClickListener(v -> { quick = idx; refresh(); });
            chips.addView(c, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        }
    }

    private void buildSeg() {
        seg.removeAllViews();
        String[] names = {"☰  List", "📅  Calendar"};
        for (int i = 0; i < 2; i++) {
            boolean on = i == mode;
            TextView t = Ui.text(a, names[i], 14, on ? Ui.col(a, R.color.text) : Ui.col(a, R.color.text2), on ? Ui.BOLD : Ui.SEMIBOLD);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            t.setBackground(on ? Ui.round(Ui.col(a, R.color.surface), Ui.dp(a, 16)) : null);
            if (on) t.setElevation(Ui.dp(a, 1));
            final int m = i;
            t.setOnClickListener(v -> { mode = m; refresh(); });
            seg.addView(t, Ui.weight1());
        }
        listPart.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        calPart.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ spending calendar

    private void buildCalendar() {
        calBox.removeAllViews();
        Store s = Store.get(a);
        java.util.List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        long end = Fmt.addMonths(calMonth, 1);
        boolean thisMonth = calMonth == Fmt.startOfMonth(now);

        LinearLayout nav = Ui.hbox(a);
        nav.setGravity(android.view.Gravity.CENTER);
        TextView prev = Ui.chip(a, "‹", false), next = Ui.chip(a, "›", false);
        TextView name = Ui.text(a, Fmt.month(calMonth), 17, Ui.BOLD);
        name.setGravity(android.view.Gravity.CENTER);
        prev.setOnClickListener(v -> { calMonth = Fmt.addMonths(calMonth, -1); calDay = calMonth; refresh(); });
        next.setOnClickListener(v -> { if (!thisMonth) { calMonth = Fmt.addMonths(calMonth, 1); calDay = calMonth; refresh(); } });
        next.setAlpha(thisMonth ? 0.3f : 1f);
        nav.addView(prev);
        nav.addView(name, Ui.weight1());
        nav.addView(next);
        calBox.addView(nav, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 10));

        long[] days = Ledger.byDay(all, calMonth);
        long max = 1, total = 0;
        for (long v : days) { max = Math.max(max, v); total += v; }
        Plan plan = s.plan();
        long share = plan != null && plan.start < end && plan.end > calMonth ? plan.amount / plan.days() : 0;

        LinearLayout card = Ui.card(a);
        card.setPadding(Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12));
        LinearLayout head = Ui.hbox(a);
        for (String d : new String[]{"M", "T", "W", "T", "F", "S", "S"}) {
            TextView t = Ui.text(a, d, 12, Ui.col(a, R.color.hint), Ui.BOLD);
            t.setGravity(android.view.Gravity.CENTER);
            head.addView(t, Ui.weight1());
        }
        card.addView(head, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 6));
        java.util.Calendar k = Fmt.cal(calMonth);
        int lead = (k.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday first
        int cells = lead + days.length;
        int rows = (cells + 6) / 7;
        long today = Fmt.startOfDay(now);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = Ui.hbox(a);
            for (int c = 0; c < 7; c++) {
                int idx = r * 7 + c - lead;
                LinearLayout cell = Ui.vbox(a);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                if (idx >= 0 && idx < days.length) {
                    long day = Fmt.addDays(calMonth, idx);
                    long v = days[idx];
                    boolean future = day > today;
                    int bg;
                    if (v <= 0 || future) bg = 0x00000000;
                    else if (share > 0) bg = (Ui.budgetColor(v / (float) share) & 0x00FFFFFF) | (Ui.dark(a) ? 0x66000000 : 0x40000000);
                    else bg = (Ui.mix(0xFFB7EBD3, 0xFF0E9F6E, (float) Math.sqrt(v / (double) max)) & 0x00FFFFFF) | (Ui.dark(a) ? 0x88000000 : 0x99000000);
                    android.graphics.drawable.GradientDrawable g = Ui.round(bg, Ui.dp(a, 12));
                    if (day == calDay) g.setStroke(Ui.dp(a, 2), Ui.col(a, R.color.text));
                    else if (day == today) g.setStroke(Ui.dp(a, 1), Ui.col(a, R.color.accent));
                    cell.setBackground(g);
                    TextView dn = Ui.text(a, String.valueOf(idx + 1), 14, future ? Ui.col(a, R.color.hint) : Ui.col(a, R.color.text), Ui.BOLD);
                    dn.setGravity(android.view.Gravity.CENTER);
                    cell.addView(dn);
                    TextView amt = Ui.text(a, v > 0 ? Fmt.shortRs(v) : " ", 9.5f, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
                    amt.setGravity(android.view.Gravity.CENTER);
                    cell.addView(amt);
                    if (!future) cell.setOnClickListener(x -> { calDay = day; refresh(); });
                }
                row.addView(cell, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 2, 2, 2, 2));
            }
            card.addView(row);
        }
        calBox.addView(card, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 8));
        TextView legend = Ui.text(a, (share > 0 ? "Green = within your daily budget share (" + Fmt.rs(share) + "), red = over · "
                : "Darker = more spent · ") + "month total " + Fmt.rs(total), 11.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
        legend.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 12));
        calBox.addView(legend);

        // the chosen day
        if (calDay >= calMonth && calDay < end) {
            long dEnd = Fmt.addDays(calDay, 1);
            java.util.List<Txn> list = new java.util.ArrayList<>();
            long spent = 0, in = 0;
            for (Txn t : s.visible()) {
                if (t.time < calDay || t.time >= dEnd || t.kind == Txn.IGNORED) continue;
                list.add(t);
                if (t.isSpend()) spent += t.amount;
                if (t.isIncome()) in += t.amount;
            }
            LinearLayout dc = Ui.card(a);
            LinearLayout dh = Ui.hbox(a);
            dh.addView(Ui.sectionTitle(a, Fmt.day(calDay)), Ui.weight1());
            TextView sum = Ui.text(a, (spent > 0 ? "−" + Fmt.rs(spent) : "") + (in > 0 ? "  +" + Fmt.rs(in) : ""), 14, Ui.col(a, R.color.text2), Ui.BOLD);
            sum.setPadding(0, 0, 0, Ui.dp(a, 8));
            dh.addView(sum);
            dc.addView(dh);
            if (list.isEmpty()) dc.addView(Ui.sub(a, "No payments this day 🎉"));
            for (Txn t : list) dc.addView(Rows.txn(a, t, false));
            calBox.addView(dc);
        }
    }

    void refresh() {
        view();
        buildSeg();
        if (mode == 1) { buildCalendar(); return; }
        buildChips();
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        Store s = Store.get(a);
        List<Txn> src = new ArrayList<>();
        for (Txn t : s.txns()) if (t.visible()) src.add(t);
        TxnSheet.sortedNewest(src);
        boolean filtered = a.filterLabel != null;
        filterBar.setVisibility(filtered ? View.VISIBLE : View.GONE);
        if (filtered) filterBar.setText("Showing: " + a.filterLabel + "   ✕");

        List<Object> rows = new ArrayList<>();
        long day = -1, daySpent = 0, dayIn = 0;
        int headerIdx = -1;
        for (Txn t : src) {
            if (quick == 1 && !t.isSpend()) continue;
            if (quick == 2 && !t.isIncome()) continue;
            if (quick == 3 && !t.review) continue;
            if (quick == 4 && !(t.kind == Txn.TRANSFER || t.kind == Txn.IGNORED || t.kind == Txn.REFUND)) continue;
            if (quick != 4 && t.kind == Txn.IGNORED) continue;
            if (filtered) {
                if (a.filterCategory != null && a.filterCategory.startsWith("acc:")) {
                    if (!t.account.equals(a.filterCategory.substring(4))) continue;
                } else if (a.filterCategory != null && !a.filterCategory.equals(t.category)) continue;
                if (a.filterTo > 0 && (t.time < a.filterFrom || t.time >= a.filterTo)) continue;
            }
            if (!q.isEmpty() && !matches(t, q)) continue;
            long d = Fmt.startOfDay(t.time);
            if (d != day) {
                if (headerIdx >= 0) rows.set(headerIdx, new long[]{day, daySpent, dayIn});
                day = d; daySpent = 0; dayIn = 0;
                headerIdx = rows.size();
                rows.add(new long[]{d, 0, 0});
            }
            if (t.isSpend()) daySpent += t.amount;
            if (t.isIncome()) dayIn += t.amount;
            rows.add(t);
        }
        if (headerIdx >= 0) rows.set(headerIdx, new long[]{day, daySpent, dayIn});
        adapter.rows = rows;
        adapter.notifyDataSetChanged();
        if (rows.isEmpty()) {
            TextView e = Ui.sub(a, q.isEmpty() ? "Nothing here yet." : "Nothing matches “" + q + "”.");
            e.setPadding(Ui.dp(a, 12), Ui.dp(a, 30), 0, 0);
            list.setEmptyView(null);
            adapter.empty = e;
        } else adapter.empty = null;
    }

    private static boolean matches(Txn t, String q) {
        if (t.merchant.toLowerCase(Locale.ROOT).contains(q) || t.category.toLowerCase(Locale.ROOT).contains(q)
                || t.note.toLowerCase(Locale.ROOT).contains(q) || t.account.toLowerCase(Locale.ROOT).contains(q)
                || t.vpa.toLowerCase(Locale.ROOT).contains(q)) return true;
        String digits = q.replaceAll("[₹,\\s]", "");
        if (digits.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            long p = SmsParser.paise(digits);
            return t.amount == p || String.valueOf(t.amount / 100).startsWith(digits);
        }
        return false;
    }

    private class Adapter extends BaseAdapter {
        List<Object> rows = new ArrayList<>();
        View empty;
        @Override public int getCount() { return rows.isEmpty() && empty != null ? 1 : rows.size(); }
        @Override public Object getItem(int i) { return rows.isEmpty() ? null : rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public boolean isEnabled(int i) { return false; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (rows.isEmpty()) return empty;
            Object o = rows.get(i);
            if (o instanceof long[]) {
                long[] h = (long[]) o;
                return Rows.dayHeader(a, h[0], h[1], h[2]);
            }
            return Rows.txn(a, (Txn) o, false);
        }
    }
}
