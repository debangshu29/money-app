package com.debangshu.money;

import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Money health: one score out of 100 from five parts, each with what it's based on and one thing to do. Plus runway and month-end. */
public class HealthActivity extends Screen {

    @Override String title() { return "Money health"; }

    static int color(int score) { return score >= 80 ? 0xFF0E9F6E : score >= 65 ? 0xFF22C55E : score >= 45 ? 0xFFF59E0B : 0xFFEF4444; }

    @Override
    void build() {
        double[] f = new double[6];
        int[] parts = MoneyFacts.health(this, f);
        int score = 0;
        for (int p : parts) score += p;
        if (score >= 65 && Goals.award(this, "healthy")) GrowPage.showCelebrations(this);

        LinearLayout hero = Ui.card(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        Charts.Donut d = new Charts.Donut(this);
        d.set(new long[]{score, 100 - score}, new int[]{color(score), Ui.col(this, R.color.surface2)}, String.valueOf(score), SaveMath.grade(score));
        hero.addView(d, Ui.lp(Ui.dp(this, 170), Ui.dp(this, 170)));
        TextView t = Ui.sub(this, "Out of 100. Tap any part to see why.");
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        hero.addView(t);
        add(hero);

        add(Explain.box(this, "health"));
        long inc = MoneyFacts.income(this);
        String[][] info = {
                {"🐷", "Saving", inc > 0 ? Math.round(f[0] * 100) + "% of income saved (last 3 months)" : "Income not known yet",
                        "On salary day, move some money to a goal first. Money you don't see, you don't spend.",
                        "Out of every ₹100 that comes in, how much do you keep? Keeping ₹20 or more = full marks. (Money looks at the last 3 months.)"},
                {"☂️", "Emergency fund", String.format(Locale.ROOT, "%.1f", f[1]) + " months of spending in the bank",
                        "Start an Emergency fund goal in Grow — even ₹500 a month is a start.",
                        "If your income stopped today, how many months could you live on the money you have? 6 months = full marks."},
                {"🏦", "EMIs", inc > 0 ? Math.round(f[2] * 100) + "% of income goes to EMIs" : "Income not known yet",
                        "Try not to take new loans, and pay off the costliest one first.",
                        "Out of every ₹100 you earn, how much goes to loan EMIs? ₹10 or less = full marks. ₹40 or more = zero."},
                {"📊", "Budget", f[3] == 0 ? "On pace this month" : f[3] == 1 ? "A little over pace" : f[3] == 2 ? "Well over pace" : "No budget set",
                        f[3] == 3 ? "Set a monthly budget in Plan — it tells you how much is safe to spend each day." : "Look at “Safe today” on Home before bigger buys.",
                        "At the speed you're spending, will this month stay inside your budget? Inside = full marks."},
                {"🎯", "Goals", f[4] + f[5] == 0 ? "No goals yet" : (int) f[4] + " on track · " + (int) f[5] + " behind",
                        f[4] + f[5] == 0 ? "Set one small goal — having a reason to save changes how you spend." : "Open a goal that's behind and play with the what-if sliders.",
                        "Are your goals moving on time? On track = full marks. Behind = half."},
        };
        add(Ui.label(this, "The five parts"));
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        for (int i = 0; i < 5; i++) {
            String[] in = info[i];
            int p = parts[i];
            LinearLayout r = Ui.vbox(this);
            r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            r.setBackground(Ui.rippleRound(this, 0x00000000, 12));
            LinearLayout h = Ui.hbox(this);
            h.addView(Ui.iconBadge(this, in[0], color(p * 5), 34));
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
            col.addView(Ui.text(this, in[1], 15, Ui.BOLD));
            col.addView(Ui.text(this, in[2], 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
            h.addView(col, Ui.weight1());
            h.addView(Ui.text(this, p + "/20", 15, color(p * 5), Ui.EXTRABOLD));
            r.addView(h);
            Charts.Progress pr = new Charts.Progress(this);
            pr.set(p / 20f, color(p * 5));
            r.addView(pr, Ui.margins(Ui.matchWrap(), this, 46, 8, 0, 4));
            TextView tip = Ui.text(this, "→ " + in[3], 12.5f, Ui.SEMIBOLD);
            tip.setPadding(Ui.dp(this, 46), Ui.dp(this, 2), 0, 0);
            r.addView(tip);
            r.setOnClickListener(v -> new Ui.Popup(this).setTitle(in[1]).setMessage(in[4] + "\n\nYou: " + in[2] + ".").setPositiveButton("OK", null).show());
            c.addView(r);
        }
        add(c);
        if (inc <= 0 || Store.get(this).prefs().getLong("myIncome", 0) <= 0) {
            TextView set = Ui.text(this, inc > 0 ? "Income used: " + Fmt.rs(inc) + " a month (seen from your credits) — set it yourself" : "Set your monthly income for a fair score",
                    13, Ui.col(this, R.color.accent), Ui.BOLD);
            set.setPadding(Ui.dp(this, 6), 0, 0, Ui.dp(this, 6));
            set.setOnClickListener(v -> RuleActivity.askIncome(this, this::rebuild));
            add(set);
        }

        // ---- runway
        add(Ui.label(this, "If your income stopped today"));
        add(Explain.box(this, "runway"));
        LinearLayout rc = Ui.card(this);
        double run = f[1];
        rc.addView(Ui.text(this, run >= 24 ? "Your money lasts 2+ years" : "Your money lasts " + String.format(Locale.ROOT, "%.1f", run).replace(".0", "") + " months", 20, Ui.EXTRABOLD));
        rc.addView(Ui.sub(this, Fmt.rs(MoneyFacts.savings(this)) + " in the bank and cash ÷ " + Fmt.rs(Goals.monthlySpend(this)) + " usual spending a month"));
        Charts.Progress rp = new Charts.Progress(this);
        rp.set((float) Math.min(1, run / 6), color((int) Math.min(100, run / 6 * 100)));
        rc.addView(rp, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 4));
        LinearLayout marks = Ui.hbox(this);
        marks.addView(Ui.text(this, "0", 11, Ui.col(this, R.color.hint), Ui.SEMIBOLD), Ui.weight1());
        marks.addView(Ui.text(this, "3 months", 11, Ui.col(this, R.color.hint), Ui.SEMIBOLD), Ui.weight1());
        marks.addView(Ui.text(this, "6 months ✓", 11, Ui.col(this, R.color.hint), Ui.SEMIBOLD));
        rc.addView(marks);
        add(rc);

        // ---- month end
        long[] fc = MoneyFacts.forecast(this);
        add(Ui.label(this, "At month end"));
        add(Explain.box(this, "monthend"));
        LinearLayout mc = Ui.card(this);
        mc.addView(Ui.text(this, (fc[0] >= 0 ? "About " + Fmt.rs(fc[0]) : Fmt.rs(-fc[0]) + " short") + " left", 20, fc[0] >= 0 ? Ui.col(this, R.color.text) : Ui.col(this, R.color.danger), Ui.EXTRABOLD));
        mc.addView(Ui.sub(this, Fmt.rs(fc[1]) + " now − " + Fmt.rs(fc[2]) + " a day × " + fc[3] + " days − " + Fmt.rs(fc[4]) + " bills still due. Any income still to come is extra."));
        add(mc);
        TextView more = Ui.text(this, "See your 50/30/20 split →", 14, Ui.col(this, R.color.accent), Ui.BOLD);
        more.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
        more.setOnClickListener(v -> startActivity(new Intent(this, RuleActivity.class)));
        add(more);
    }
}
