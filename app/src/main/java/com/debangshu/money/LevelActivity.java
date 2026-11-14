package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Your level: where you are on the ladder (Starter → Saver → Builder → Investor → Money tree), the three steps to the next
 * level with your own progress, and why the ladder goes in that order (with sources).
 */
public class LevelActivity extends Screen {

    static final int[] COLORS = {0xFF0E9F6E, 0xFFEC4899, 0xFFF97316, 0xFF6366F1, 0xFF15803D};

    @Override String title() { return "Your level"; }

    @Override
    void build() {
        Motivation.Facts f = Motivation.check(this, false);
        int lv = Motivation.level(this);
        String[] cur = Motivation.LEVELS[lv];
        boolean top = lv >= Motivation.STEPS.length;

        // ---- where you are
        LinearLayout hero = Ui.card(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(Ui.iconBadge(this, cur[1], COLORS[lv], 76));
        TextView ln = Ui.text(this, Lang.t("Level " + (lv + 1) + " of 5"), 13, Ui.col(this, R.color.text2), Ui.BOLD);
        ln.setPadding(0, Ui.dp(this, 10), 0, 0);
        hero.addView(ln);
        TextView name = Ui.text(this, cur[2], 26, Ui.EXTRABOLD);
        name.setGravity(Gravity.CENTER);
        hero.addView(name);
        TextView mean = Ui.text(this, cur[3], 14, Ui.col(this, R.color.text2), Ui.REGULAR);
        mean.setGravity(Gravity.CENTER);
        hero.addView(mean);
        if (!top) {
            int done = Motivation.stepsDone(this, lv + 1);
            hero.addView(LearnActivity.meter(this, done / 3f, COLORS[lv + 1]), Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 6));
            hero.addView(Ui.text(this, Lang.t(done + " of 3 steps to " + Lang.t(Motivation.LEVELS[lv + 1][2])), 12.5f,
                    Ui.col(this, R.color.text2), Ui.BOLD));
        }
        add(hero);

        // ---- the steps to the next level
        if (!top) {
            String[] nx = Motivation.LEVELS[lv + 1];
            add(Ui.label(this, Lang.t("To become a " + Lang.t(nx[2]))));
            LinearLayout sc = Ui.card(this);
            sc.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            for (String[] st : Motivation.STEPS[lv]) sc.addView(stepRow(f, st));
            add(sc);
        } else {
            add(NoSpendActivity.kid(this, "You've reached the top level. Keep the habits going: invest every month, keep your "
                    + "safety net full, and enjoy no-spend days. Trees keep growing without anyone pulling them."));
        }

        // ---- the ladder
        add(Ui.label(this, "The 5 levels"));
        LinearLayout lc = Ui.card(this);
        lc.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        for (int i = 0; i < Motivation.LEVELS.length; i++) {
            String[] l = Motivation.LEVELS[i];
            LinearLayout r = Ui.hbox(this);
            r.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
            View ic = Ui.iconBadge(this, l[1], COLORS[i], 42);
            ic.setAlpha(i <= lv ? 1f : 0.4f);
            r.addView(ic);
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 14), 0, 0, 0);
            col.addView(Ui.text(this, l[2], 15.5f, i <= lv ? Ui.col(this, R.color.text) : Ui.col(this, R.color.hint), Ui.BOLD));
            col.addView(Ui.text(this, l[3], 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
            r.addView(col, Ui.weight1());
            if (i == lv) {
                TextView here = Ui.text(this, "You", 11.5f, Ui.col(this, R.color.on_accent), Ui.BOLD);
                here.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
                here.setBackground(Ui.round(COLORS[i], Ui.dp(this, 10)));
                r.addView(here);
            } else if (i < lv) r.addView(Ui.text(this, "✓", 16, Ui.col(this, R.color.success), Ui.BOLD));
            lc.addView(r);
        }
        add(lc);

        // ---- why this order
        add(Ui.label(this, "Why this order?"));
        add(NoSpendActivity.kid(this, "It's like building a house. First you look at the land (see where money goes), then you lay "
                + "bricks (keep some each month), then build strong walls (a safety net), and only then the roof garden (investing). "
                + "A garden on a house without walls falls down at the first storm."));
        LinearLayout pc = Ui.card(this);
        NoSpendActivity.proof(this, pc, "Safety net before investing: if an emergency comes and you borrow on a credit card, banks charge "
                + "up to 3.75% a month (45% a year) plus 18% GST on that interest. Savings in the bank cost you nothing.",
                "SBI Card / ICICI Bank card charges", "https://www.sbicard.com", false);
        NoSpendActivity.proof(this, pc, "Markets can fall a lot for a while: the Nifty 50 fell about 38% in early 2020. Money you may need "
                + "soon should not be there — or you may have to sell at the bottom.", "NSE index history", "https://www.nseindia.com", true);
        NoSpendActivity.proof(this, pc, "Given time, it usually recovers: since 1999 the Nifty 50 Total Return Index has had no 7-year "
                + "period with a loss. That's why investing comes after the safety net — so you can wait.",
                "FundsIndia research on NSE data", "https://www.nseindia.com", true);
        NoSpendActivity.proof(this, pc, "Levels never go down. A bad month doesn't take away what you built — steps you've reached stay done.",
                "", "", true);
        add(pc);

        TextView d = Ui.text(this, "For learning, not financial advice. Steps are checked from your payments on this phone.",
                11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        add(d);
    }

    private View stepRow(Motivation.Facts f, String[] st) {
        boolean done = Motivation.stepDone(this, st[0]);
        double[] p = Motivation.progress(f, st[0]);
        LinearLayout r = Ui.hbox(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        TextView tick = Ui.text(this, done ? "✓" : "○", 20, Ui.col(this, done ? R.color.success : R.color.hint), Ui.BOLD);
        tick.setGravity(Gravity.CENTER);
        r.addView(tick, Ui.lp(Ui.dp(this, 30), -2));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 8), 0, 0, 0);
        col.addView(Ui.text(this, st[1], 15, done ? Ui.col(this, R.color.text2) : Ui.col(this, R.color.text), Ui.BOLD));
        col.addView(Ui.text(this, st[2], 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
        if (!done) {
            String prog = progressText(f, st[0], p);
            if (prog != null) {
                col.addView(LearnActivity.meter(this, (float) (p[0] / Math.max(0.0001, p[1])), 0xFF0E9F6E), Ui.margins(Ui.matchWrap(), this, 0, 8, 24, 4));
                col.addView(Ui.text(this, prog, 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
            }
        }
        if (!st[3].isEmpty()) {
            TextView why = Ui.text(this, "📖  " + Lang.t("Why this matters") + "  ›", 12.5f, Ui.col(this, R.color.accent), Ui.BOLD);
            why.setPadding(0, Ui.dp(this, 6), 0, 0);
            why.setOnClickListener(v -> LearnActivity.lesson(this, st[3]));
            col.addView(why);
        }
        r.addView(col, Ui.weight1());
        View.OnClickListener go = action(this, st[0]);
        if (go != null && !done) {
            r.addView(Ui.chevron(this));
            r.setBackground(Ui.rippleRound(this, 0x00000000, 16));
            r.setOnClickListener(go);
        }
        return r;
    }

    private static String progressText(Motivation.Facts f, String id, double[] p) {
        String n = trim(p[0]) + " / " + trim(p[1]);
        switch (id) {
            case "track30": return Lang.t(n + " days");
            case "free5": case "streak3": return Lang.t(n + " days");
            case "learn3": return Lang.t(n + " lessons");
            case "save10x3": case "invest3": return Lang.t(n + " months");
            case "runway3": case "runway6":
                return f.measured ? Lang.t(n + " months") : Lang.t("Needs one full month of payments first");
            case "health65": return f.measured ? Lang.t("Score:") + " " + n : Lang.t("Needs one full month of payments first");
            default: return null;
        }
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Where a step's row takes you. */
    static View.OnClickListener action(Activity a, String id) {
        switch (id) {
            case "free5": case "streak3": return v -> a.startActivity(new Intent(a, NoSpendActivity.class));
            case "learn3": case "course": return v -> a.startActivity(new Intent(a, LearnActivity.class));
            case "goal": return v -> GoalSetup.start(a);
            case "runway3": case "runway6": case "health65": case "green": case "save10x3":
                return v -> a.startActivity(new Intent(a, HealthActivity.class));
            case "invest3": return v -> a.startActivity(new Intent(a, CalcActivity.class).putExtra("id", "sip"));
            default: return null;
        }
    }
}
