package com.debangshu.money;

import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** True price of a habit: pick something you buy often, slide "cut to", see a year — and 10 years invested. */
public class HabitActivity extends Screen {

    private long each = 150, times = 12, cut = 4, years = 10, rate = 12;
    private boolean picked;

    @Override String title() { return "True price of a habit"; }

    /** Repeat "wants" in the last 60 days: {name, average each (paise), times a month}. */
    private List<Object[]> habits() {
        long now = System.currentTimeMillis(), from = now - 60 * GoalMath.DAY;
        Map<String, long[]> m = new HashMap<>();
        Map<String, Integer> mv = MoneyFacts.moved(this);
        for (Txn t : Store.get(this).txns()) {
            if (!t.isSpend() || t.time < from || t.unnamed()) continue;
            if (SaveMath.bucket(t.category, mv) != SaveMath.WANTS) continue;
            long[] v = m.get(t.merchant);
            if (v == null) m.put(t.merchant, v = new long[2]);
            v[0] += t.amount;
            v[1]++;
        }
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            if (e.getValue()[1] < 3) continue;
            out.add(new Object[]{e.getKey(), e.getValue()[0] / e.getValue()[1], Math.max(1, Math.round(e.getValue()[1] / 2.0))});
        }
        java.util.Collections.sort(out, (x, y) -> Long.compare((Long) y[1] * (Long) y[2], (Long) x[1] * (Long) x[2]));
        return out;
    }

    @Override
    void build() {
        List<Object[]> hs = habits();
        if (!picked && !hs.isEmpty()) {
            each = Math.max(10, (Long) hs.get(0)[1] / 100);
            times = Math.max(1, (Long) hs.get(0)[2]);
            cut = Math.max(1, times / 3);
            picked = true;
        }
        if (!hs.isEmpty()) {
            add(Ui.label(this, "Your repeat spends — tap one"));
            HorizontalScrollView sv = new HorizontalScrollView(this);
            sv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = Ui.hbox(this);
            sv.addView(row);
            for (int i = 0; i < Math.min(8, hs.size()); i++) {
                Object[] h = hs.get(i);
                TextView ch = Ui.chip(this, h[0] + " · " + Fmt.rs((Long) h[1]) + " × " + h[2] + "/mo", false);
                ch.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface), 18));
                ch.setOnClickListener(v -> {
                    each = Math.max(10, (Long) h[1] / 100);
                    times = Math.max(1, (Long) h[2]);
                    cut = Math.max(1, times / 3);
                    rebuild();
                });
                row.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
            }
            add(sv);
        }

        LinearLayout res = Ui.card(this);
        TextView perMonth = Ui.text(this, "", 13.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        TextView big = Ui.text(this, "", 30, Ui.col(this, R.color.success), Ui.EXTRABOLD);
        TextView under = Ui.text(this, "", 13.5f, Ui.SEMIBOLD);
        res.addView(perMonth);
        TextView inYears = Ui.text(this, "", 13, Ui.col(this, R.color.text2), Ui.BOLD);
        res.addView(inYears);
        res.addView(big);
        res.addView(under);
        Charts.Line chart = new Charts.Line(this);
        chart.height(170);
        res.addView(chart, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        LinearLayout lg = Ui.hbox(this);
        lg.addView(legend(0xFF0E9F6E, "Invested"));
        lg.addView(legend(0xFF94A3B8, "Just kept aside"));
        res.addView(lg, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 0));
        add(res);

        LinearLayout c = Ui.card(this);
        Slider[] s = new Slider[5];
        Runnable upd = () -> {
            double[] h = SaveMath.habit(each, cut, (int) years, rate / 100.0);
            perMonth.setText("Now: " + Slider.rs(each * times) + " a month · " + Slider.rs(each * times * 12) + " a year"
                    + (MoneyFacts.income(this) > 0 ? " · " + SaveMath.hoursText(SaveMath.hours(each * times * 1200, MoneyFacts.income(this), MoneyFacts.hoursPerWeek(this))) + " a year" : ""));
            big.setText(Fmt.rs(Math.round(h[2]) * 100));
            inYears.setText("Cut it down and in " + years + (years == 1 ? " year" : " years") + " you could have");
            under.setText("Saving " + Slider.rs(Math.round(h[0])) + " a month (" + Slider.rs(Math.round(h[1])) + " a year) by cutting " + cut + " of " + times + " times");
            int n = (int) years;
            float[] inv = new float[n + 1], kept = new float[n + 1];
            String[] x = new String[n + 1];
            for (int y = 0; y <= n; y++) {
                inv[y] = (float) SaveMath.sip(h[0], y * 12, rate / 100.0);
                kept[y] = (float) (h[0] * 12 * y);
                x[y] = y == 0 ? "Now" : y + " yr";
            }
            chart.set(new float[][]{inv, kept}, new int[]{0xFF0E9F6E, 0xFF94A3B8}, new boolean[]{false, true}, new boolean[]{true, false}, x, -1);
        };
        s[0] = new Slider(this, "Each time", 10, 3000, 10, each, Slider::rs, v -> { each = v; upd.run(); });
        s[1] = new Slider(this, "Times a month", 1, 60, 1, times, v -> v + "×", v -> {
            times = v;
            if (cut > times) { cut = times; s[2].set(cut); }
            upd.run();
        });
        s[2] = new Slider(this, "Cut by", 0, 60, 1, cut, v -> v + (v == 1 ? " time" : " times"), v -> {
            cut = Math.min(v, times);
            if (cut != v) s[2].set(cut);
            upd.run();
        });
        s[3] = new Slider(this, "For years", 1, 30, 1, years, v -> v + " yr", v -> { years = v; upd.run(); });
        s[4] = new Slider(this, "Yearly return (example)", 4, 15, 1, rate, v -> v + "%", v -> { rate = v; upd.run(); });
        for (Slider x : s) c.addView(x.view);
        TextView d = Ui.text(this, "For learning — returns are examples, not promises.", 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        c.addView(d);
        add(c);
        upd.run();
    }

    private LinearLayout legend(int color, String label) {
        LinearLayout r = Ui.hbox(this);
        android.view.View d = new android.view.View(this);
        d.setBackground(Ui.round(color, Ui.dp(this, 3)));
        r.addView(d, Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10)));
        TextView t = Ui.text(this, label, 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        t.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 14), 0);
        r.addView(t);
        return r;
    }
}
