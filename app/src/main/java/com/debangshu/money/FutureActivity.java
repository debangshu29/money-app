package com.debangshu.money;

import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Future you: what steady monthly investing becomes at 30, 40, 50, 60 — in today's rupees — and when you reach
 * your freedom number (25 × a year of spending: the point where work becomes a choice).
 */
public class FutureActivity extends Screen {

    private long age, monthly, saved, spend, rate = 10, infl = 6;
    private boolean init;

    @Override String title() { return "Future you"; }

    @Override
    void build() {
        if (!init) {
            age = Store.get(this).prefs().getInt("age", 25);
            long u = Goals.monthlySaving(this) / 100;
            monthly = Math.max(1000, Math.min(100000, (u / 500) * 500));
            saved = Math.min(5000000, MoneyFacts.savings(this) / 100 / 1000 * 1000);
            spend = Math.max(5000, Math.min(300000, Goals.monthlySpend(this) / 100 / 1000 * 1000));
            if (spend == 5000 && Goals.monthlySpend(this) == 0) spend = 25000;
            init = true;
        }
        add(Explain.box(this, "future"));
        LinearLayout hero = Ui.card(this);
        TextView fl = Ui.text(this, "", 13, Ui.col(this, R.color.text2), Ui.BOLD);
        TextView fn = Ui.text(this, "", 28, Ui.EXTRABOLD);
        TextView when = Ui.text(this, "", 15, Ui.col(this, R.color.success), Ui.BOLD);
        TextView sooner = Ui.sub(this, "");
        hero.addView(fl);
        hero.addView(fn);
        hero.addView(when);
        hero.addView(sooner);
        LinearLayout ages = Ui.hbox(this);
        ages.setBaselineAligned(false);
        hero.addView(ages, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        Charts.Line chart = new Charts.Line(this);
        chart.height(180);
        hero.addView(chart, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        TextView tl = Ui.text(this, "Green line: your money (in today's rupees) · dashed line: your freedom number", 11.5f, Ui.col(this, R.color.hint), Ui.SEMIBOLD);
        tl.setPadding(0, Ui.dp(this, 4), 0, 0);
        hero.addView(tl);
        add(hero);

        LinearLayout c = Ui.card(this);
        Runnable upd = () -> {
            double real = (1 + rate / 100.0) / (1 + infl / 100.0) - 1;
            double target = SaveMath.freedomNumber(spend);
            fl.setText("Your freedom number (25 years of spending)");
            fn.setText(Fmt.rs(Math.round(target) * 100));
            double y = SaveMath.yearsTo(saved, monthly, real, target);
            when.setText(y < 0 ? "Not reached at this pace — try saving more" : y == 0 ? "You're already there 🎉"
                    : "You'd get there at about " + Math.round(age + y) + " (in " + String.format(Locale.ROOT, "%.0f", Math.ceil(y)) + " years)");
            double y2 = SaveMath.yearsTo(saved, monthly + 2000, real, target);
            sooner.setText(y > 0 && y2 > 0 && y - y2 >= 0.5 ? "Invest ₹2,000 more a month and it's " + Math.round(y - y2) + " years sooner." : "");
            ages.removeAllViews();
            int shown = 0;
            for (int a : new int[]{30, 40, 50, 60}) {
                if (a <= age || shown >= 3) continue;
                double v = SaveMath.sip(monthly, (int) ((a - age) * 12), real) + SaveMath.lump(saved, a - age, real);
                LinearLayout t = Ui.vbox(this);
                t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
                t.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 14)));
                t.addView(Ui.text(this, "At " + a, 12, Ui.col(this, R.color.text2), Ui.BOLD));
                t.addView(Ui.oneLine(Ui.text(this, Charts.Line.money((float) v), 16, Ui.EXTRABOLD)));
                ages.addView(t, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, shown == 0 ? 0 : 8, 0, 0, 0));
                shown++;
            }
            int n = (int) Math.max(5, Math.min(45, 60 - age));
            float[] v = new float[n + 1];
            String[] x = new String[n + 1];
            for (int k = 0; k <= n; k++) {
                v[k] = (float) (SaveMath.sip(monthly, k * 12, real) + SaveMath.lump(saved, k, real));
                x[k] = "Age " + (age + k);
            }
            chart.set(new float[][]{v}, new int[]{0xFF0E9F6E}, new boolean[]{false}, new boolean[]{true}, x, (float) target);
        };
        c.addView(new Slider(this, "Your age", 16, 60, 1, age, v -> v + "", v -> {
            age = v;
            Store.get(this).prefs().edit().putInt("age", (int) v).apply();
            upd.run();
        }).view);
        c.addView(new Slider(this, "Invest every month", 0, 100000, 500, monthly, Slider::rs, v -> { monthly = v; upd.run(); }).view);
        c.addView(new Slider(this, "Already saved", 0, 5000000, 5000, saved, Slider::rs, v -> { saved = v; upd.run(); }).view);
        c.addView(new Slider(this, "You spend a month", 5000, 300000, 1000, spend, Slider::rs, v -> { spend = v; upd.run(); }).view);
        c.addView(new Slider(this, "Yearly return (example)", 4, 14, 1, rate, v -> v + "%", v -> { rate = v; upd.run(); }).view);
        c.addView(new Slider(this, "Prices rise (inflation)", 3, 9, 1, infl, v -> v + "%", v -> { infl = v; upd.run(); }).view);
        TextView d = Ui.text(this, "All in today's rupees (after inflation). For learning — returns are examples, not promises.", 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        c.addView(d);
        add(c);

        add(Explain.box(this, "freedom"));
        upd.run();
    }
}
