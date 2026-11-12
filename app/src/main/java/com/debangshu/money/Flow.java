package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tap "Money in" or "Money out": where it came from / where it went, then every payment. */
public final class Flow {

    private Flow() {}

    public static void show(Activity a, long from, long to, boolean in, String when) {
        List<Txn> all = Store.get(a).txns();
        List<Txn> hits = new ArrayList<>();
        long total = 0, transfers = 0;
        for (Txn t : all) {
            if (t.time < from || t.time >= to || t.kind == Txn.IGNORED || t.kind == Txn.SNAPSHOT) continue;
            if (t.credit != in) continue;
            if (t.kind == Txn.TRANSFER) { transfers += t.amount; continue; }
            if (in ? !(t.isIncome() || t.kind == Txn.REFUND) : !t.isSpend()) continue;
            hits.add(t);
            total += t.amount;
        }
        TxnSheet.sortedNewest(hits);

        LinearLayout box = Ui.vbox(a);
        box.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 8));
        TextView big = Ui.text(a, Fmt.plain(total), 30, in ? Ui.col(a, R.color.success) : Ui.col(a, R.color.text), Ui.EXTRABOLD);
        box.addView(big);
        box.addView(Ui.sub(a, hits.size() + (hits.size() == 1 ? " payment " : " payments ") + when));

        // by category
        Map<String, Long> byCat = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Txn t : hits) {
            String c = t.kind == Txn.REFUND ? Cats.REFUND : t.category.isEmpty() ? Cats.OTHER : t.category;
            Long v = byCat.get(c);
            byCat.put(c, (v == null ? 0 : v) + t.amount);
            Integer n = count.get(c);
            count.put(c, n == null ? 1 : n + 1);
        }
        LinkedHashMap<String, Long> cats = Ledger.sorted(byCat);
        if (!cats.isEmpty()) {
            box.addView(head(a, in ? "Where it came from" : "Where it went"));
            for (Map.Entry<String, Long> e : cats.entrySet()) {
                String c = e.getKey();
                int n = count.get(c);
                int pct = (int) Math.round(e.getValue() * 100.0 / Math.max(1, total));
                View row = Rows.amountRow(a, Cats.emoji(c), Cats.color(c), c, pct + "% · " + n + (n == 1 ? " payment" : " payments"),
                        e.getValue(), e.getValue() / (float) Math.max(1, total));
                row.setOnClickListener(v -> category(a, hits, c, when));
                box.addView(row);
            }
        }

        // by place / person
        Map<String, Long> byWho = new HashMap<>();
        for (Txn t : hits) {
            String k = t.merchant.isEmpty() ? (in ? "Unknown sender" : "Unknown") : t.merchant;
            Long v = byWho.get(k);
            byWho.put(k, (v == null ? 0 : v) + t.amount);
        }
        LinkedHashMap<String, Long> who = Ledger.sorted(byWho);
        if (who.size() > 1) {
            box.addView(head(a, in ? "From" : "Top places"));
            int i = 0;
            long top = who.values().iterator().next();
            for (Map.Entry<String, Long> e : who.entrySet()) {
                if (i++ >= 6) break;
                box.addView(Rows.amountRow(a, null, in ? Ui.col(a, R.color.success) : Ui.col(a, R.color.accent), e.getKey(), null,
                        e.getValue(), e.getValue() / (float) top));
            }
        }

        // every payment
        if (!hits.isEmpty()) {
            box.addView(head(a, "Payments"));
            for (int i = 0; i < Math.min(25, hits.size()); i++) box.addView(Rows.txn(a, hits.get(i), true));
            if (hits.size() > 25) box.addView(Ui.sub(a, "+ " + (hits.size() - 25) + " more — tap “See all”"));
        }
        if (transfers > 0) {
            TextView tr = Ui.sub(a, "Not counted: " + Fmt.plain(transfers) + " moved between your own accounts / card bills.");
            tr.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
            box.addView(tr);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        new Ui.Popup(a).sheet().setTitle((in ? "Money in · " : "Money out · ") + when).setView(Ui.capHeight(sv, 0.75f))
                .setPositiveButton("See all", (d, w) -> {
                    Intent i = new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("filterFrom", from).putExtra("filterTo", to).putExtra("filterLabel", (in ? "Money in · " : "Money out · ") + when)
                            .putExtra("filterQuick", in ? 2 : 1);
                    a.startActivity(i);
                })
                .setNegativeButton("Close", null).show();
    }

    private static void category(Activity a, List<Txn> hits, String cat, String when) {
        LinearLayout b = Ui.vbox(a);
        b.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        long total = 0;
        for (Txn t : hits) {
            String c = t.kind == Txn.REFUND ? Cats.REFUND : t.category.isEmpty() ? Cats.OTHER : t.category;
            if (!c.equals(cat)) continue;
            b.addView(Rows.txn(a, t, true));
            total += t.amount;
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        new Ui.Popup(a).sheet().setTitle(Cats.emoji(cat) + "  " + cat + " · " + Fmt.plain(total)).setView(Ui.capHeight(sv, 0.6f))
                .setPositiveButton("Close", null).show();
    }

    private static TextView head(Activity a, String s) {
        TextView t = Ui.text(a, s, 14, Ui.col(a, R.color.text2), Ui.BOLD);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 16), 0, Ui.dp(a, 2));
        return t;
    }
}
