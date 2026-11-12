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
    private static final String[] QUICK = {"All", "Spent", "Money in", "Needs a look", "Not counted"};

    ActivityPage(MainActivity a) { this.a = a; }

    void setQuick(int q) { quick = Math.max(0, Math.min(QUICK.length - 1, q)); }

    View view() {
        if (root != null) return root;
        root = Ui.vbox(a);
        ImageView add = Ui.icon(a, R.drawable.ic_add, Ui.col(a, R.color.text2), 22);
        add.setContentDescription("Add cash spend");
        add.setOnClickListener(v -> TxnSheet.addManual(a));
        root.addView(a.topBar("Activity", add));

        search = new EditText(a);
        search.setHint("Search name, amount, note…");
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
        root.addView(search, Ui.margins(Ui.matchWrap(), a, 16, 0, 16, 8));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.hbox(a);
        chips.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 6));
        hs.addView(chips);
        root.addView(hs);

        filterBar = Ui.text(a, "", 13, Ui.col(a, R.color.accent), Ui.BOLD);
        filterBar.setPadding(Ui.dp(a, 20), Ui.dp(a, 2), Ui.dp(a, 20), Ui.dp(a, 4));
        filterBar.setOnClickListener(v -> { a.filterCategory = null; a.filterLabel = null; a.filterFrom = 0; a.filterTo = 0; quick = 0; refresh(); });
        root.addView(filterBar);

        list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 24));
        list.setClipToPadding(false);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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

    void refresh() {
        view();
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
