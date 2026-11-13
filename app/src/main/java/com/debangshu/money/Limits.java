package com.debangshu.money;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Category limits ("Food ₹6,000 a month"): a card for the Budget screen, and the editor. */
public final class Limits {

    private Limits() {}

    public static LinearLayout card(Activity a) {
        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        long month = Fmt.startOfMonth(now), end = Fmt.addMonths(month, 1);
        LinearLayout bc = Ui.card(a);
        bc.addView(Ui.sectionTitle(a, "Category limits"));
        Map<String, Long> budgets = s.budgets();
        if (budgets.isEmpty()) {
            TextView t = Ui.sub(a, "Optional: a monthly limit for one category (e.g. Food ₹6,000). You get a warning at 80% and 100%.");
            bc.addView(t);
        } else {
            for (Map.Entry<String, Long> e : budgets.entrySet()) {
                String c = e.getKey();
                long used = 0;
                for (Txn t : all) if (t.isSpend() && t.time >= month && t.time < end && c.equals(t.category)) used += t.amount;
                float f = used / (float) e.getValue();
                bc.addView(Rows.amountRow(a, Cats.emoji(c), Ui.budgetColor(f), c,
                        Fmt.rs(used) + " of " + Fmt.rs(e.getValue()) + (f >= 1 ? " · over" : " · " + Fmt.rs(e.getValue() - used) + " left"),
                        used, f));
            }
        }
        TextView setB = Ui.button(a, budgets.isEmpty() ? "Set category limits" : "Edit limits", false);
        setB.setOnClickListener(v -> edit(a));
        LinearLayout br = Ui.hbox(a);
        br.setPadding(0, Ui.dp(a, 10), 0, 0);
        br.addView(setB);
        bc.addView(br);
        return bc;
    }

    /** Suggested from the average of the last 3 full months (rounded up to ₹500). */
    public static void edit(Activity a) {
        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        Map<String, Long> cur = s.budgets();
        LinkedHashMap<String, Long> avg = Ledger.byCategory(all, Fmt.addMonths(ms, -3), ms);
        List<String> cats = new ArrayList<>();
        for (String c : avg.keySet()) if (!cats.contains(c) && !Cats.CASH.equals(c) && !Cats.UNRECORDED.equals(c)) cats.add(c);
        for (String c : cur.keySet()) if (!cats.contains(c)) cats.add(c);
        for (String c : Cats.spendNames()) if (!cats.contains(c) && !Cats.CASH.equals(c)) cats.add(c);
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        b.setPadding(p, 0, p, 0);
        TextView info = Ui.sub(a, "Filled in from your average over the last 3 months. Leave a box empty for no limit.");
        info.setPadding(0, 0, 0, Ui.dp(a, 8));
        b.addView(info);
        List<EditText> fields = new ArrayList<>();
        for (String c : cats) {
            LinearLayout r = Ui.hbox(a);
            r.addView(Ui.oneLine(Ui.text(a, Cats.emoji(c) + "  " + c, 14.5f, Ui.SEMIBOLD)), Ui.weight1());
            EditText e = new EditText(a);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setTypeface(Ui.face(a, Ui.REGULAR));
            e.setHint(Lang.t("—"));
            e.setGravity(Gravity.END);
            Long have = cur.get(c);
            long suggest = avg.containsKey(c) ? avg.get(c) / 3 : 0;
            suggest = ((suggest / 100 + 499) / 500) * 500;
            if (have != null) e.setText(String.valueOf(have / 100));
            else if (cur.isEmpty() && suggest >= 1000) e.setText(String.valueOf(suggest));
            r.addView(e, Ui.lp(Ui.dp(a, 110), -2));
            b.addView(r);
            fields.add(e);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        new Ui.Popup(a).setTitle("Monthly limits (₹)").setView(Ui.capHeight(sv, 0.55f))
                .setPositiveButton("Save", (d, w) -> {
                    for (int i = 0; i < cats.size(); i++) {
                        String txt = fields.get(i).getText().toString().trim();
                        s.setBudget(cats.get(i), txt.isEmpty() ? 0 : Math.max(0, SmsParser.paise(txt)));
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
