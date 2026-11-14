package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Grow: your saving, your goals (each linked to a real account or filled by hand), split my saving,
 * last month's leftover, festivals coming up, badges, and challenges.
 */
final class GrowPage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;

    GrowPage(MainActivity a) { this.a = a; }

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
        int y = scroll.getScrollY();
        box.removeAllViews();
        box.addView(a.topBar("Grow", null));
        LinearLayout body = Ui.vbox(a);
        body.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
        box.addView(body);

        Goals.check(a, false);
        List<JSONObject> goals = Goals.all(a);
        long usual = Goals.monthlySaving(a);

        body.addView(hero(goals, usual), gap());
        View left = leftover(goals);
        if (left != null) body.addView(left, gap());

        // ---- 2.8: your level, no-spend days, Money Wrapped
        Motivation.Facts mf = Motivation.check(a, false);
        body.addView(levelCard(), gap());
        LinearLayout mt = Ui.hbox(a);
        mt.setBaselineAligned(false);
        long nowM = System.currentTimeMillis();
        int free = MotiMath.monthFree(mf.wants, Fmt.startOfMonth(nowM), mf.start, mf.today);
        mt.addView(tile("🗓️", 0xFF0E9F6E, "No-spend days", Lang.t("This month: " + free) + (mf.cur > 0 ? "  🔥" + mf.cur : ""),
                v -> a.startActivity(new Intent(a, NoSpendActivity.class))), new LinearLayout.LayoutParams(0, -2, 1));
        int wy = Motivation.wrapYear(nowM);
        mt.addView(tile("🎁", 0xFFDB2777, "Money Wrapped", Motivation.wrapSeason(nowM) ? Lang.t("Your " + wy + " is ready") : Lang.t(wy + " so far"),
                v -> WrappedActivity.open(a, wy)), Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 10, 0, 0, 0));
        body.addView(mt, gap());

        // ---- your money at a glance
        body.addView(Ui.label(a, "Your money"));
        body.addView(ruleCard(), gap());
        LinearLayout tiles = Ui.hbox(a);
        tiles.setBaselineAligned(false);
        double[] f = new double[6];
        int score = 0;
        for (int p : MoneyFacts.health(a, f)) score += p;
        tiles.addView(tile("❤️", HealthActivity.color(score), "Money health", score + " · " + SaveMath.grade(score),
                v -> a.startActivity(new Intent(a, HealthActivity.class))), new LinearLayout.LayoutParams(0, -2, 1));
        long[] fc = MoneyFacts.forecast(a);
        tiles.addView(tile("📆", fc[0] >= 0 ? 0xFF0B6F86 : 0xFFDC2626, "Month end",
                (fc[0] >= 0 ? "~" + Fmt.rs(fc[0]) : Fmt.rs(-fc[0]) + " short") + " left",
                v -> a.startActivity(new Intent(a, HealthActivity.class))), Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 10, 0, 0, 0));
        body.addView(tiles, gap());
        LinearLayout tiles2 = Ui.hbox(a);
        tiles2.setBaselineAligned(false);
        String run = f[1] >= 24 ? "2+ years" : String.format(java.util.Locale.ROOT, "%.1f", f[1]).replace(".0", "") + " months";
        tiles2.addView(tile("☂️", 0xFF6366F1, "If income stopped", "Lasts " + run,
                v -> a.startActivity(new Intent(a, HealthActivity.class))), new LinearLayout.LayoutParams(0, -2, 1));
        boolean jarOn = MoneyFacts.jar(a).optBoolean("on");
        tiles2.addView(tile("🫙", 0xFF0E9F6E, "Round-up jar", jarOn ? Fmt.rs(MoneyFacts.jarAmount(a)) + " inside" : "Start saving spare change",
                v -> jarSheet(a)), Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 10, 0, 0, 0));
        body.addView(tiles2, gap());

        // ---- goals
        LinearLayout gh = Ui.hbox(a);
        gh.addView(Ui.label(a, "My goals"), Ui.weight1());
        if (!goals.isEmpty()) {
            TextView add = Ui.text(a, "+ New goal", 14, Ui.col(a, R.color.accent), Ui.BOLD);
            add.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 8));
            add.setOnClickListener(v -> GoalSetup.start(a));
            gh.addView(add);
        }
        body.addView(gh);
        if (goals.isEmpty()) body.addView(emptyGoals(), gap());
        else {
            for (JSONObject g : goals) body.addView(goalCard(g), gap());
            TextView wk = Ui.chip(a, "💡  Where should goal money sit?", false);
            wk.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 18));
            wk.setOnClickListener(v -> whereSheet(a));
            LinearLayout wr = Ui.hbox(a);
            wr.addView(wk);
            body.addView(wr, gap());
        }

        View fest = festivals(goals);
        if (fest != null) { body.addView(Ui.label(a, "Festivals coming up")); body.addView(fest, gap()); }

        body.addView(Ui.label(a, "Badges"));
        body.addView(badges(), gap());

        body.addView(Ui.label(a, "Save more"));
        LinearLayout gc = Ui.card(a);
        gc.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
        gc.addView(Ui.grid(a,
                Ui.action(a, "🧭", 0xFFEF4444, "Leak finder", v -> a.startActivity(new Intent(a, LeaksActivity.class))),
                Ui.action(a, "⏳", 0xFF6366F1, "Wait-list", v -> a.startActivity(new Intent(a, WaitListActivity.class))),
                Ui.action(a, "☕", 0xFFF97316, "Habit price", v -> a.startActivity(new Intent(a, HabitActivity.class))),
                Ui.action(a, "🕒", 0xFF0EA5E9, "Hours of work", v -> hoursSheet(a)),
                Ui.action(a, "🌅", 0xFF0E9F6E, "Future you", v -> a.startActivity(new Intent(a, FutureActivity.class))),
                Ui.action(a, "💼", 0xFF8B5CF6, "Salary split", v -> a.startActivity(new Intent(a, SalarySplitActivity.class))),
                Ui.action(a, "🔀", 0xFF3B82F6, "Split saving", v -> split(a)),
                Ui.action(a, "🔥", 0xFFF59E0B, "Challenges", v -> a.startActivity(new Intent(a, Challenges.class)))));
        body.addView(gc, gap());
        body.addView(Ui.label(a, "Calculators & play"));
        body.addView(CalcHub.featured(a), gap());
        body.addView(Ui.label(a, "Learn in 1 minute"));
        body.addView(learnCard(), gap());
        body.addView(LearnActivity.course(a), gap());

        scroll.post(() -> scroll.scrollTo(0, y));
        showCelebrations(a);
    }

    /** Your level, how far to the next one (tap for the steps). */
    private View levelCard() {
        int lv = Motivation.level(a);
        String[] cur = Motivation.LEVELS[lv];
        boolean top = lv >= Motivation.STEPS.length;
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        LinearLayout r = Ui.hbox(a);
        r.addView(Ui.iconBadge(a, cur[1], LevelActivity.COLORS[lv], 48));
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 6), 0);
        col.addView(Ui.text(a, Lang.t("Level " + (lv + 1) + " of 5"), 12, Ui.col(a, R.color.text2), Ui.BOLD));
        col.addView(Ui.text(a, cur[2], 18, Ui.EXTRABOLD));
        int done = top ? 3 : Motivation.stepsDone(a, lv + 1);
        col.addView(Ui.text(a, top ? "Top level — keep growing" : Lang.t(done + " of 3 steps to " + Lang.t(Motivation.LEVELS[lv + 1][2])),
                12.5f, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        r.addView(col, Ui.weight1());
        r.addView(Ui.chevron(a));
        c.addView(r);
        if (!top) c.addView(LearnActivity.meter(a, done / 3f, LevelActivity.COLORS[lv + 1]), Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        c.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        c.setOnClickListener(v -> a.startActivity(new Intent(a, LevelActivity.class)));
        return c;
    }

    /** Lesson to read now + quiz, myths, scam alerts and all lessons. */
    private View learnCard() {
        LinearLayout c = Ui.card(a);
        org.json.JSONObject next = Learn.next(a);
        if (next != null) {
            LinearLayout r = Ui.hbox(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(Ui.iconBadge(a, next.optString("emoji", "📘"), 0xFF0E9F6E, 44));
            LinearLayout t = Ui.vbox(a);
            t.setPadding(Ui.dp(a, 12), 0, 0, 0);
            t.addView(Ui.text(a, Learn.doneCount(a) == 0 ? "Your first lesson" : "Next lesson", 12.5f, Ui.col(a, R.color.text2), Ui.BOLD));
            t.addView(Ui.text(a, new android.text.SpannableString(Learn.s(next, "title")), 16, Ui.col(a, R.color.text), Ui.EXTRABOLD));
            r.addView(t, Ui.weight1());
            r.addView(Ui.chevron(a));
            r.setOnClickListener(v -> LearnActivity.lesson(a, next.optString("id")));
            c.addView(r);
        }
        LinearLayout g = Ui.vbox(a);
        g.setPadding(0, Ui.dp(a, 10), 0, 0);
        g.addView(Ui.grid(a,
                Ui.action(a, "📚", 0xFF0B6F86, "All lessons", v -> a.startActivity(new Intent(a, LearnActivity.class))),
                Ui.action(a, "🧠", 0xFF8B5CF6, "Quiz", v -> QuizActivity.open(a, false)),
                Ui.action(a, "💥", 0xFFF97316, "Myth busters", v -> LearnActivity.open(a, "myths")),
                Ui.action(a, "🚨", 0xFFDC2626, "Scam alerts", v -> LearnActivity.open(a, "scams"))));
        c.addView(g);
        return c;
    }

    private LinearLayout.LayoutParams gap() { return Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12); }

    // ------------------------------------------------------------------ hero

    private View hero(List<JSONObject> goals, long usual) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 18));
        c.addView(Ui.text(a, "You usually save", 13, Ui.col(a, R.color.text2), Ui.BOLD));
        c.addView(Ui.text(a, Fmt.rs(Math.max(0, usual)) + " a month", 30, Ui.EXTRABOLD));
        long need = 0;
        int dated = 0;
        for (JSONObject g : goals) {
            if (g.optBoolean("paused")) continue;
            GoalMath.Status s = Goals.status(a, g);
            if (s.needPerMonth > 0) { need += s.needPerMonth; dated++; }
        }
        String line;
        int color = Ui.col(a, R.color.text2);
        if (usual <= 0) line = "Lately more went out than came in. Goals need a little room — the tools below help.";
        else if (dated == 0) line = "Money in − money out, the last 3 months. Set a goal and see how soon you'd get there.";
        else if (need <= usual) { line = "✓ Enough for your goals — they need " + Fmt.rs(need) + " a month"; color = Ui.col(a, R.color.success); }
        else { line = "Your goals need " + Fmt.rs(need) + " a month — " + Fmt.rs(need - usual) + " more than you usually save"; color = 0xFFD97706; }
        TextView l = Ui.text(a, line, 13.5f, color, Ui.SEMIBOLD);
        l.setPadding(0, Ui.dp(a, 6), 0, 0);
        c.addView(l);
        if (goals.size() > 1) {
            TextView sp = Ui.chip(a, "🔀  Split my saving across goals", false);
            sp.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.accent_soft), 18));
            sp.setOnClickListener(v -> split(a));
            LinearLayout r = Ui.hbox(a);
            r.addView(sp);
            c.addView(r, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        }
        return c;
    }

    // ------------------------------------------------------------------ 50/30/20 card and small tiles

    private View ruleCard() {
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        long[] m = RuleActivity.month(a, 0, null);
        Object[] rule = SaveMath.RULES[MoneyFacts.rule(a)];
        LinearLayout h = Ui.hbox(a);
        h.addView(Ui.text(a, "This month · " + rule[0], 13, Ui.col(a, R.color.text2), Ui.BOLD), Ui.weight1());
        h.addView(Ui.chevron(a));
        c.addView(h);
        if (m[0] <= 0) {
            c.addView(Ui.text(a, "See how your money splits into needs, wants and saving", 14.5f, Ui.SEMIBOLD));
        } else {
            int[] pct = SaveMath.percents(m[0], m[1], m[2]);
            LinearLayout nums = Ui.hbox(a);
            nums.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 8));
            for (int k = 0; k < 3; k++) {
                TextView t = Ui.text(a, RuleActivity.NAMES[k] + " " + pct[k] + "%", 14.5f, RuleActivity.COL[k], Ui.EXTRABOLD);
                nums.addView(t, Ui.weight1());
            }
            c.addView(nums);
            c.addView(RuleActivity.bar(a, pct, 10));
            int[] t = {(Integer) rule[1], (Integer) rule[2], (Integer) rule[3]};
            boolean ok = pct[2] >= t[2] && (t[1] < 0 ? pct[0] + pct[1] <= t[0] : pct[0] <= t[0] && pct[1] <= t[1]);
            TextView line = Ui.text(a, ok ? "✓ Within your rule so far" : pct[2] < t[2] ? "Saving " + pct[2] + "% — aim for " + t[2] + "%" : "A bit over on spending — tap to see where",
                    12.5f, ok ? Ui.col(a, R.color.success) : 0xFFD97706, Ui.SEMIBOLD);
            line.setPadding(0, Ui.dp(a, 8), 0, 0);
            c.addView(line);
        }
        c.setOnClickListener(v -> a.startActivity(new Intent(a, RuleActivity.class)));
        if (!Goals.has(a, "rule")) {   // last month within the rule → badge
            long[] lm = RuleActivity.month(a, 1, null);
            if (lm[0] > 0 && lm[1] + lm[2] > 0) {
                int[] lp = SaveMath.percents(lm[0], lm[1], lm[2]);
                int[] t = {(Integer) rule[1], (Integer) rule[2], (Integer) rule[3]};
                boolean ok = lp[2] >= t[2] && (t[1] < 0 ? lp[0] + lp[1] <= t[0] : lp[0] <= t[0] && lp[1] <= t[1]);
                if (ok) Goals.award(a, "rule");
            }
        }
        return c;
    }

    private View tile(String emoji, int color, String label, String value, View.OnClickListener l) {
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        t.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 20));
        t.addView(Ui.iconBadge(a, emoji, color, 34));
        TextView lt = Ui.oneLine(Ui.text(a, label, 12.5f, Ui.col(a, R.color.text2), Ui.BOLD));
        lt.setPadding(0, Ui.dp(a, 8), 0, 0);
        t.addView(lt);
        t.addView(Ui.oneLine(Ui.text(a, value, 15.5f, Ui.EXTRABOLD)));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ hours of work

    static void hoursSheet(Activity a) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, 0, p, Ui.dp(a, 8));
        b.addView(kid(a, "hours"));
        long inc = MoneyFacts.income(a);
        TextView incT = Ui.text(a, inc > 0 ? "Income: " + Fmt.rs(inc) + " a month — change" : "Set your monthly income", 14, Ui.col(a, R.color.accent), Ui.BOLD);
        b.addView(incT);
        TextView res = Ui.text(a, "", 24, Ui.EXTRABOLD);
        TextView perHour = Ui.sub(a, "");
        long[] price = {3000};
        Runnable upd = () -> {
            long i2 = MoneyFacts.income(a);
            double h = SaveMath.hours(price[0] * 100, i2, MoneyFacts.hoursPerWeek(a));
            res.setText(i2 <= 0 ? "—" : Slider.rs(price[0]) + " = " + SaveMath.hoursText(h));
            perHour.setText(i2 <= 0 ? "Money needs your income for this." : "You earn about " + Fmt.rs(Math.round(i2 / (MoneyFacts.hoursPerWeek(a) * 52 / 12.0))) + " an hour.");
        };
        incT.setOnClickListener(v -> RuleActivity.askIncome(a, () -> {
            incT.setText("Income: " + Fmt.rs(MoneyFacts.income(a)) + " a month — change");
            upd.run();
        }));
        b.addView(new Slider(a, "Hours you work a week", 10, 80, 1, (long) MoneyFacts.hoursPerWeek(a), v -> v + " h", v -> {
            Store.get(a).prefs().edit().putFloat("hoursWeek", v).apply();
            upd.run();
        }).view, Ui.margins(Ui.matchWrap(), a, 0, 14, 0, 0));
        b.addView(new Slider(a, "Try a price", 100, 100000, 100, price[0], Slider::rs, v -> { price[0] = v; upd.run(); }).view);
        b.addView(res);
        b.addView(perHour);
        android.widget.Switch sw = new Ui.LSwitch(a);
        sw.setText("Show hours on payments and the wait-list");
        sw.setTypeface(Ui.face(a, Ui.SEMIBOLD));
        sw.setChecked(MoneyFacts.showHours(a));
        sw.setOnCheckedChangeListener((x, on) -> Store.get(a).prefs().edit().putBoolean("showHours", on).apply());
        b.addView(sw, Ui.margins(Ui.matchWrap(), a, 0, 16, 0, 0));
        upd.run();
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle("Hours of work");
        pop.setView(sv).setPositiveButton("Done", null);
        pop.sheet().show();
    }

    // ------------------------------------------------------------------ round-up jar

    static void jarSheet(Activity a) {
        JSONObject j = MoneyFacts.jar(a);
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, 0, p, Ui.dp(a, 8));
        b.addView(kid(a, "jar"));
        boolean on = j.optBoolean("on");
        long amount = MoneyFacts.jarAmount(a);
        if (on) {
            TextView big = Ui.text(a, Fmt.rs(amount), 30, Ui.EXTRABOLD);
            big.setPadding(0, Ui.dp(a, 12), 0, 0);
            b.addView(big);
            b.addView(Ui.sub(a, "in the jar since " + Fmt.date(j.optLong("since")) + " · rounding to " + Fmt.rs(j.optLong("step", 1000))));
        }
        TextView sl = Ui.text(a, "Round up to", 13, Ui.col(a, R.color.text2), Ui.BOLD);
        sl.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 6));
        b.addView(sl);
        LinearLayout r = Ui.hbox(a);
        long[] steps = {1000, 5000, 10000};
        List<TextView> chips = new ArrayList<>();
        long cur = j.optLong("step", 1000);
        for (long st : steps) {
            TextView ch = Ui.chip(a, "Next " + Fmt.rs(st), st == cur);
            if (st != cur) ch.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            ch.setOnClickListener(v -> {
                try { JSONObject o = MoneyFacts.jar(a); o.put("step", st); Store.get(a).putObj("jar", o); } catch (Exception ignored) { }
                for (TextView t : chips) {
                    boolean x = t == v;
                    t.setBackground(Ui.rippleRound(a, x ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 18));
                    t.setTextColor(x ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text));
                }
            });
            chips.add(ch);
            r.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        }
        b.addView(r);
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle("Round-up jar");
        pop.setView(sv);
        if (!on) pop.setPositiveButton("Start the jar", (d, w) -> {
            try {
                JSONObject o = MoneyFacts.jar(a);
                o.put("on", true).put("since", System.currentTimeMillis()).put("moved", 0L);
                if (!o.has("step")) o.put("step", 1000L);
                Store.get(a).putObj("jar", o);
            } catch (Exception ignored) { }
            Goals.award(a, "jar");
            Ui.toast(a, "Jar started — round-ups from now on");
            if (a instanceof MainActivity) ((MainActivity) a).refresh();
            showCelebrations(a);
        });
        else {
            pop.setPositiveButton(amount > 0 ? "Move to a goal" : "OK", (d, w) -> { if (amount > 0) moveJar(a, amount); });
            pop.setNeutralButton("Stop jar", (d, w) -> {
                try { JSONObject o = MoneyFacts.jar(a); o.put("on", false); Store.get(a).putObj("jar", o); } catch (Exception ignored) { }
                if (a instanceof MainActivity) ((MainActivity) a).refresh();
            });
        }
        pop.sheet().show();
    }

    private static void moveJar(Activity a, long amount) {
        List<JSONObject> open = Goals.active(a);
        if (open.isEmpty()) { Ui.toast(a, "Add a goal first"); return; }
        String[] names = new String[open.size()];
        for (int i = 0; i < open.size(); i++) names[i] = open.get(i).optString("name");
        new Ui.Popup(a).setTitle("Move " + Fmt.rs(amount) + " to…").setItems(names, (d, w) -> {
            JSONObject g = open.get(w);
            try {
                JSONObject o = MoneyFacts.jar(a);
                o.put("moved", o.optLong("moved") + amount);
                Store.get(a).putObj("jar", o);
                if (g.optString("account").isEmpty()) {
                    g.put("saved", g.optLong("saved") + amount);
                    org.json.JSONArray log = g.optJSONArray("log");
                    if (log == null) { log = new org.json.JSONArray(); g.put("log", log); }
                    log.put(new org.json.JSONArray().put(System.currentTimeMillis()).put(amount).put("Round-up jar"));
                    Goals.put(a, g);
                    Ui.toast(a, "Moved to " + g.optString("name"));
                } else {
                    new Ui.Popup(a).setTitle("Move it in your bank app")
                            .setMessage("Send " + Fmt.rs(amount) + " to " + Noted.fullLabel(a, g.optString("account")) + ". Money sees it from the SMS.")
                            .setPositiveButton("OK", null).show();
                }
            } catch (Exception ignored) { }
            if (a instanceof MainActivity) ((MainActivity) a).refresh();
        }).show();
    }

    // ------------------------------------------------------------------ goal cards

    private View goalCard(JSONObject g) {
        GoalMath.Status s = Goals.status(a, g);
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        LinearLayout h = Ui.hbox(a);
        File ph = Goals.photo(a, g.optString("id"));
        if (ph.exists()) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(BitmapFactory.decodeFile(ph.getAbsolutePath()));
            iv.setClipToOutline(true);
            iv.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 22)));
            h.addView(iv, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)));
        } else h.addView(Ui.iconBadge(a, g.optString("emoji", "🎯"), Goals.color(g), 44));
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
        col.addView(Ui.oneLine(Ui.text(a, g.optString("name"), 16, Ui.BOLD)));
        col.addView(Ui.oneLine(Ui.sub(a, Fmt.rs(s.current) + " of " + Fmt.rs(s.target)
                + (g.optString("account").isEmpty() ? "" : " · " + Noted.fullLabel(a, g.optString("account"))))));
        h.addView(col, Ui.weight1());
        h.addView(Ui.text(a, s.percent + "%", 16, Goals.color(g), Ui.EXTRABOLD));
        c.addView(h);
        Charts.Progress pr = new Charts.Progress(a);
        pr.set(s.current / (float) s.target, Goals.color(g));
        c.addView(pr, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 8));
        c.addView(Ui.oneLine(Ui.text(a, Goals.statusLine(s), 13, Goals.statusColor(a, s), Ui.BOLD)));
        c.setOnClickListener(v -> GoalActivity.open(a, g.optString("id")));
        return c;
    }

    private View emptyGoals() {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Save for something that matters", 16, Ui.BOLD));
        TextView t = Ui.sub(a, "A goal is a jar with a name on it — “Laptop”, “Puja”, “Trip”. Pick one, tap the bank account the money sits in, and Money tells you if you're on track — by itself.");
        t.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
        c.addView(t);
        View[] items = new View[4];
        int[] pick = {0, 1, 3, 10};
        for (int k = 0; k < 4; k++) {
            String[] tp = Goals.TEMPLATES[pick[k]];
            items[k] = Ui.action(a, tp[1], Goals.COLORS[k], tp[2], v -> GoalSetup.start(a));
        }
        c.addView(Ui.grid(a, items));
        return c;
    }

    // ------------------------------------------------------------------ last month's leftover

    private View leftover(List<JSONObject> goals) {
        List<JSONObject> open = new ArrayList<>();
        for (JSONObject g : goals) if (!g.optBoolean("paused") && Goals.current(a, g) < g.optLong("target")) open.add(g);
        if (open.isEmpty()) return null;
        long now = System.currentTimeMillis();
        long ms = Fmt.startOfMonth(now), last = Fmt.addMonths(ms, -1);
        String key = "leftDone_" + Fmt.cal(last).get(Calendar.YEAR) + "_" + Fmt.cal(last).get(Calendar.MONTH);
        if (Store.get(a).prefs().getBoolean(key, false)) return null;
        List<Txn> all = Store.get(a).txns();
        long left = Ledger.income(all, last, ms) - Ledger.spent(all, last, ms);
        if (left < 50000) return null;            // under ₹500 isn't worth a card
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.round(Ui.col(a, R.color.accent_soft), Ui.dp(a, 22)));
        LinearLayout h = Ui.hbox(a);
        h.addView(Ui.iconBadge(a, "🐷", 0xFF0E9F6E, 40));
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 12), 0, 0, 0);
        col.addView(Ui.text(a, Fmt.rs(left) + " was left in " + Fmt.MONTH[Fmt.cal(last).get(Calendar.MONTH)], 15.5f, Ui.BOLD));
        col.addView(Ui.sub(a, "Put it towards a goal before it gets spent?"));
        h.addView(col, Ui.weight1());
        c.addView(h);
        LinearLayout r = Ui.hbox(a);
        TextView yes = Ui.chip(a, "Put it in a goal", true);
        TextView no = Ui.chip(a, "Not now", false);
        no.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 18));
        r.addView(yes, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        r.addView(no);
        c.addView(r, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        no.setOnClickListener(v -> { Store.get(a).prefs().edit().putBoolean(key, true).apply(); refresh(); });
        yes.setOnClickListener(v -> {
            String[] names = new String[open.size()];
            for (int i = 0; i < open.size(); i++) names[i] = open.get(i).optString("name");
            new Ui.Popup(a).setTitle("Which goal?").setItems(names, (d, w) -> {
                JSONObject g = open.get(w);
                Store.get(a).prefs().edit().putBoolean(key, true).apply();
                Goals.award(a, "saver");
                if (g.optString("account").isEmpty()) {
                    try {
                        g.put("saved", g.optLong("saved") + left);
                        org.json.JSONArray log = g.optJSONArray("log");
                        if (log == null) { log = new org.json.JSONArray(); g.put("log", log); }
                        log.put(new org.json.JSONArray().put(System.currentTimeMillis()).put(left).put("Last month's leftover"));
                    } catch (Exception ignored) { }
                    Goals.put(a, g);
                    Ui.toast(a, "Added " + Fmt.rs(left) + " to " + g.optString("name"));
                    refresh();
                } else {
                    new Ui.Popup(a).setTitle("Move it in your bank app")
                            .setMessage("Send " + Fmt.rs(left) + " to " + Noted.fullLabel(a, g.optString("account"))
                                    + " from your bank or UPI app. Money sees it from the SMS and " + g.optString("name") + " moves up by itself.")
                            .setPositiveButton("OK", (x, z) -> refresh()).show();
                }
            }).show();
        });
        return c;
    }

    // ------------------------------------------------------------------ split my saving

    static void split(Activity a) {
        List<JSONObject> goals = Goals.active(a);
        if (goals.isEmpty()) { Ui.toast(a, "Add a goal first"); return; }
        long usual = Math.max(0, Goals.monthlySaving(a));
        long start = Store.get(a).prefs().getLong("splitAmount", usual > 0 ? usual : 500000);
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, 0, p, Ui.dp(a, 8));
        TextView amt = Ui.text(a, "", 26, Ui.EXTRABOLD);
        b.addView(kid(a, "split"));
        TextView cs = Ui.text(a, "I can save every month", 13, Ui.col(a, R.color.text2), Ui.BOLD);
        cs.setPadding(0, Ui.dp(a, 14), 0, 0);
        b.addView(cs);
        b.addView(amt);
        SeekBar sb = new SeekBar(a);
        long max = Math.max(5000000, ((Math.max(usual, start) * 2) / 50000 + 1) * 50000);
        sb.setMax((int) (max / 50000));                 // steps of ₹500
        sb.setProgress((int) (start / 50000));
        b.addView(sb, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 10));
        if (usual > 0) b.addView(Ui.sub(a, "You usually save " + Fmt.rs(usual) + " a month."));
        LinearLayout list = Ui.vbox(a);
        b.addView(list, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        long[] chosen = {start};
        long[][] out = {new long[0]};
        Runnable upd = () -> {
            amt.setText(Fmt.rs(chosen[0]));
            long now = System.currentTimeMillis();
            long[] needs = new long[goals.size()], lefts = new long[goals.size()];
            for (int i = 0; i < goals.size(); i++) {
                GoalMath.Status s = Goals.status(a, goals.get(i));
                needs[i] = s.needPerMonth;
                lefts[i] = s.left;
            }
            out[0] = GoalMath.split(chosen[0], needs, lefts);
            list.removeAllViews();
            for (int i = 0; i < goals.size(); i++) {
                JSONObject g = goals.get(i);
                LinearLayout r = Ui.hbox(a);
                r.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
                r.addView(Ui.iconBadge(a, g.optString("emoji", "🎯"), Goals.color(g), 34));
                LinearLayout col = Ui.vbox(a);
                col.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
                col.addView(Ui.oneLine(Ui.text(a, g.optString("name"), 14.5f, Ui.BOLD)));
                long fin = GoalMath.finish(now, lefts[i], out[0][i]);
                boolean late = g.optLong("by") > 0 && (fin == 0 || fin > g.optLong("by"));
                col.addView(Ui.oneLine(Ui.text(a, fin > 0 ? "Done by " + Fmt.date(fin) + (late ? " — after your date" : "") : "Not enough for this one",
                        12.5f, late || fin == 0 ? 0xFFD97706 : Ui.col(a, R.color.text2), Ui.SEMIBOLD)));
                r.addView(col, Ui.weight1());
                r.addView(Ui.text(a, Fmt.rs(out[0][i]) + "/mo", 14.5f, Ui.EXTRABOLD));
                list.addView(r);
            }
        };
        sb.setOnSeekBarChangeListener(GoalActivity.seek(v -> { chosen[0] = v * 50000L; upd.run(); }));
        upd.run();
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle("Split my saving");
        pop.setView(sv).setPositiveButton("Use this plan", (d, w) -> {
            for (int i = 0; i < goals.size(); i++) {
                try { goals.get(i).put("plan", out[0][i]); } catch (Exception ignored) { }
                Goals.put(a, goals.get(i));
            }
            Store.get(a).prefs().edit().putLong("splitAmount", chosen[0]).apply();
            Goals.award(a, "planner");
            Ui.toast(a, "Plan saved — each goal now expects its share");
            if (a instanceof MainActivity) ((MainActivity) a).refresh();
            showCelebrations(a);
        }).setNegativeButton("Close", null);
        pop.sheet().show();
    }

    // ------------------------------------------------------------------ festivals

    private View festivals(List<JSONObject> goals) {
        long now = System.currentTimeMillis();
        List<long[]> next = new ArrayList<>();          // {index, date, last}
        for (int i = 0; i < GoalMath.FESTIVALS.length; i++) {
            long[] nl = GoalMath.nextAndLast(i, now);
            if (nl[0] > 0 && nl[0] - now < 330 * GoalMath.DAY) next.add(new long[]{i, nl[0], nl[1]});
        }
        if (next.isEmpty()) return null;
        java.util.Collections.sort(next, (x, y) -> Long.compare(x[1], y[1]));
        // daily spending for the last ~15 months, for "last time you spent"
        long dayZero = Fmt.startOfDay(now - 460 * GoalMath.DAY);
        long[] days = new long[462];
        for (Txn t : Store.get(a).txns()) {
            if (!t.isSpend() || t.time < dayZero) continue;
            int k = (int) ((t.time - dayZero) / GoalMath.DAY);
            if (k >= 0 && k < days.length) days[k] += t.amount;
        }
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        int shown = 0;
        for (long[] f : next) {
            if (shown++ >= 3) break;
            String[] fe = GoalMath.FESTIVALS[(int) f[0]];
            long extra = f[2] > 0 ? GoalMath.festivalExtra(days, dayZero, f[2]) : -1;
            boolean planned = false;
            for (JSONObject g : goals) if ("festival".equals(g.optString("kind")) && g.optString("name").contains(fe[0])) planned = true;
            long months = Math.max(1, Math.round(GoalMath.months(now, f[1])));
            String sub = planned ? "Planned ✓" : extra > 50000 ? "Last time you spent ~" + Fmt.rs(extra) + " extra · " + Fmt.rs(extra / months) + " a month from now"
                    : "Plan ahead — save a little each month";
            long by = f[1] - 3 * GoalMath.DAY;
            long suggest = extra > 50000 ? (extra / 50000) * 50000 : 0;
            String title = fe[0] + " · " + Fmt.date(f[1]) + " (in " + months + (months == 1 ? " month)" : " months)");
            c.addView(Ui.navRow(a, fe[1], 0xFFF59E0B, title, sub, planned ? null : v -> GoalSetup.festival(a, fe[0] + " " + Fmt.cal(f[1]).get(Calendar.YEAR), by, suggest)));
        }
        return c;
    }

    // ------------------------------------------------------------------ badges

    private View badges() {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(a);
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        hs.addView(row);
        int earned = 0;
        for (String[] b : Goals.BADGE_LIST) if (Goals.has(a, b[0])) earned++;
        for (int pass = 0; pass < 2; pass++) {
            for (String[] b : Goals.BADGE_LIST) {
                boolean has = Goals.has(a, b[0]);
                if ((pass == 0) != has) continue;          // earned first
                LinearLayout v = Ui.vbox(a);
                v.setGravity(Gravity.CENTER_HORIZONTAL);
                View ic = Ui.iconBadge(a, b[1], has ? 0xFFF59E0B : 0xFF94A3B8, 52);
                ic.setAlpha(has ? 1f : 0.45f);
                v.addView(ic);
                TextView t = Ui.text(a, b[2], 11.5f, has ? Ui.col(a, R.color.text) : Ui.col(a, R.color.hint), Ui.SEMIBOLD);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(2);
                t.setPadding(0, Ui.dp(a, 6), 0, 0);
                v.addView(t, Ui.lp(Ui.dp(a, 76), -2));
                v.setOnClickListener(x -> new Ui.Popup(a).setTitle(b[2])
                        .setMessage((has ? "Earned ✓ — " : "How to earn it: ") + b[3] + ".").setPositiveButton("OK", null).show());
                row.addView(v, Ui.margins(Ui.lp(Ui.dp(a, 76), -2), a, 0, 0, 6, 0));
            }
        }
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12));
        TextView n = Ui.text(a, earned + " of " + Goals.BADGE_LIST.length + " earned", 12.5f, Ui.col(a, R.color.text2), Ui.BOLD);
        n.setPadding(Ui.dp(a, 6), 0, 0, Ui.dp(a, 8));
        c.addView(n);
        c.addView(hs);
        return c;
    }

    // ------------------------------------------------------------------ where to keep goal money (learning)

    static void whereSheet(Activity a) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, 0, p, Ui.dp(a, 8));
        b.addView(kid(a, "where"));
        int[] at = {6, 24, 48, 80};
        String[] icon = {"🏦", "📅", "📊", "📈"};
        for (int k = 0; k < at.length; k++) {
            String[] w = GoalMath.whereToKeep(at[k]);
            LinearLayout r = Ui.hbox(a);
            r.setGravity(Gravity.TOP);
            r.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
            r.addView(Ui.iconBadge(a, icon[k], 0xFF0B6F86, 38));
            LinearLayout col = Ui.vbox(a);
            col.setPadding(Ui.dp(a, 12), 0, 0, 0);
            col.addView(Ui.text(a, w[0], 15, Ui.BOLD));
            col.addView(Ui.text(a, w[1], 13.5f, Ui.SEMIBOLD));
            TextView why = Ui.sub(a, w[2]);
            why.setPadding(0, Ui.dp(a, 2), 0, 0);
            col.addView(why);
            r.addView(col, Ui.weight1());
            b.addView(r);
        }
        TextView d = Ui.text(a, "For learning — not financial advice.", 11.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
        d.setPadding(0, Ui.dp(a, 12), 0, 0);
        b.addView(d);
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle("Where to keep goal money");
        pop.setView(sv).setPositiveButton("Got it", null);
        pop.sheet().show();
    }

    /** The simple explanation for a sheet (always open). */
    static View kid(Activity a, String key) {
        String[] t = Explain.topic(key);
        LinearLayout card = Ui.vbox(a);
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        card.setBackground(Ui.round(0x1FF59E0B, Ui.dp(a, 18)));
        TextView body = Ui.text(a, t == null ? "" : t[2], 14, Ui.col(a, R.color.text), Ui.REGULAR);
        body.setLineSpacing(Ui.dp(a, 3), 1f);
        card.addView(body);
        return card;
    }

    // ------------------------------------------------------------------ celebrations

    private static boolean showing;

    /** A milestone reached or a new badge: celebrate once. */
    static void showCelebrations(Activity a) {
        if (showing || a.isFinishing()) return;
        String title = null, text = null, icon = "🎉";
        int lvNew = Motivation.takeLevelNew(a);
        if (lvNew > 0) {
            String[] l = Motivation.LEVELS[lvNew];
            icon = l[1];
            title = Lang.t("You're now a " + Lang.t(l[2]) + "!");
            text = Lang.t(l[3]) + ". " + (lvNew < Motivation.STEPS.length
                    ? Lang.t("Next: " + Lang.t(Motivation.LEVELS[lvNew + 1][2]) + " — see the steps in Your level.")
                    : Lang.t("That's the top level. Well done!"));
        }
        if (title == null) for (JSONObject g : Goals.all(a)) {
            int m = g.optInt("celebrate", 0);
            if (m <= 0) continue;
            g.remove("celebrate");
            Goals.put(a, g);
            icon = m == 100 ? "🏆" : "🎉";
            title = m == 100 ? g.optString("name") + " — done!" : g.optString("name") + " is " + m + "% there";
            text = m == 100 ? "You reached your goal. That takes real discipline — well done."
                    : m == 25 ? "A great start. Small steps add up." : m == 50 ? "Halfway! The second half always feels faster."
                    : "Almost there — keep going!";
            break;
        }
        JSONObject nb = Store.get(a).obj("badgeNew");
        List<String> news = new ArrayList<>();
        for (Iterator<String> it = nb.keys(); it.hasNext(); ) news.add(it.next());
        if (title == null && news.isEmpty()) return;
        LinearLayout b = Ui.vbox(a);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(a, 24);
        b.setPadding(p, Ui.dp(a, 20), p, Ui.dp(a, 4));
        if (title != null) {
            b.addView(Ui.iconBadge(a, icon, 0xFFF59E0B, 72));
            TextView t = Ui.text(a, title, 20, Ui.EXTRABOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
            b.addView(t);
            TextView s = Ui.sub(a, text);
            s.setGravity(Gravity.CENTER);
            b.addView(s);
        }
        if (!news.isEmpty()) {
            TextView nl = Ui.text(a, news.size() == 1 ? "New badge" : news.size() + " new badges", 13, Ui.col(a, R.color.text2), Ui.BOLD);
            nl.setPadding(0, Ui.dp(a, title == null ? 0 : 18), 0, Ui.dp(a, 8));
            b.addView(nl);
            LinearLayout row = null;
            int k = 0;
            for (String id : news) {
                String[] bd = Goals.badge(id);
                if (bd == null) continue;
                if (k++ % 3 == 0) {   // three to a row, so many at once still fit
                    row = Ui.hbox(a);
                    row.setGravity(Gravity.CENTER);
                    row.setPadding(0, 0, 0, Ui.dp(a, 10));
                    b.addView(row);
                }
                LinearLayout v = Ui.vbox(a);
                v.setGravity(Gravity.CENTER_HORIZONTAL);
                v.addView(Ui.iconBadge(a, bd[1], 0xFFF59E0B, 52));
                TextView t = Ui.text(a, bd[2], 12, Ui.SEMIBOLD);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, Ui.dp(a, 4), 0, 0);
                v.addView(t);
                row.addView(v, Ui.margins(Ui.lp(Ui.dp(a, 84), -2), a, 4, 0, 4, 0));
            }
            Store.get(a).putObj("badgeNew", new JSONObject());
        }
        showing = true;
        new Ui.Popup(a).setView(b).setPositiveButton("Yay!", null).setOnDismissListener(d -> showing = false).show();
    }
}
