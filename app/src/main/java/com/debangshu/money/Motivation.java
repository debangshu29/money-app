package com.debangshu.money;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 2.8 Motivation: levels (Starter → Saver → Builder → Investor → Money tree), the steps to each, the badges they give,
 * and a nudge when your year's Money Wrapped is ready.
 *
 * Stored in obj "moti": level (0–4), steps {id: time done}, levelNew (level to celebrate), target (no-spend days a month),
 * wrapNote (year whose Wrapped was announced).
 * A step stays done once reached, and a level never goes down — a bad month doesn't take away what you built.
 */
final class Motivation {

    private Motivation() {}

    static final String KEY = "moti";

    /** id, emoji, name, what it means */
    static final String[][] LEVELS = {
            {"starter", "🌱", "Starter", "You've started watching your money"},
            {"saver", "🐷", "Saver", "You spend less than you earn"},
            {"builder", "🧱", "Builder", "You've built a safety net"},
            {"investor", "📈", "Investor", "Your money works for you"},
            {"tree", "🌳", "Money tree", "Strong habits that keep growing"},
    };

    /** Steps to reach level i (index = level − 1): id, title, what to do, lesson that explains why (or "" ) */
    static final String[][][] STEPS = {
            {   // → Saver
                    {"track30", "Use Money for 30 days", "It catches payments by itself — just keep it on", "budget"},
                    {"free5", "Have 5 no-spend days", "Days with no spending on wants", ""},
                    {"learn3", "Learn 3 lessons", "1 minute each, in Grow → Learn", ""},
            },
            {   // → Builder
                    {"green", "Spend less than you earn in a month", "Any full month, from your payments", "payfirst"},
                    {"goal", "Set up a goal", "Something you're saving for", "goals"},
                    {"streak3", "Keep a 3-day no-spend streak", "3 days in a row without wants", ""},
            },
            {   // → Investor
                    {"runway3", "Savings that cover 3 months of spending", "Your emergency fund", "emergency"},
                    {"save10x3", "Save 10% of what comes in, 3 months in a row", "Pay yourself first", "payfirst"},
                    {"course", "Finish the 7-day money course", "2 short lessons a day", ""},
            },
            {   // → Money tree
                    {"invest3", "Invest in 3 different months", "A SIP every month does it", "sip"},
                    {"runway6", "Savings that cover 6 months of spending", "A full safety net", "emergency"},
                    {"health65", "Money health score of 65 or more", "See what lifts it in Money health", ""},
            },
    };

    static JSONObject data(Context c) { return Store.get(c).obj(KEY); }

    static void save(Context c, JSONObject o) { Store.get(c).putObj(KEY, o); }

    static int level(Context c) { return Math.max(0, Math.min(LEVELS.length - 1, data(c).optInt("level", 0))); }

    static boolean stepDone(Context c, String id) {
        JSONObject s = data(c).optJSONObject("steps");
        return s != null && s.has(id);
    }

    static int stepsDone(Context c, int toLevel) {
        if (toLevel < 1 || toLevel > STEPS.length) return 0;
        int n = 0;
        for (String[] st : STEPS[toLevel - 1]) if (stepDone(c, st[0])) n++;
        return n;
    }

    static int target(Context c) { return data(c).optInt("target", 8); }

    static void setTarget(Context c, int t) {
        JSONObject o = data(c);
        try { o.put("target", t); } catch (Exception ignored) { }
        save(c, o);
    }

    // ------------------------------------------------------------------ the facts every step and badge reads

    static final class Facts {
        long start, today;
        Map<Long, Long> wants;
        int cur, best, total;         // streaks up to yesterday; cur includes today while still free
        int monthFree, lastMonthFree;
        int trackedDays, lessons, goals, savedAny, saved10Run, investMonths, health;
        double runway;
        boolean course, measured;     // measured = a full month of spending is known (runway and health need it)
    }

    static Facts facts(Context c) {
        Facts f = new Facts();
        List<Txn> all = Store.get(c).txns();
        long now = System.currentTimeMillis();
        f.today = Fmt.startOfDay(now);
        f.start = MotiMath.trackStart(all);
        f.wants = MotiMath.wantsByDay(all);
        int[] done = MotiMath.streaks(f.wants, f.start, Fmt.addDays(f.today, -1));   // finished days only
        f.best = done[1];
        f.total = done[2];
        f.cur = MotiMath.streaks(f.wants, f.start, f.today)[0];
        long ms = Fmt.startOfMonth(now);
        f.monthFree = MotiMath.monthFree(f.wants, ms, f.start, Fmt.addDays(f.today, -1));
        f.lastMonthFree = MotiMath.monthFree(f.wants, Fmt.addMonths(ms, -1), f.start, Fmt.addDays(f.today, -1));
        f.trackedDays = f.start == 0 ? 0 : (int) Math.round((f.today - f.start) / 86400000.0) + 1;
        f.lessons = Learn.doneCount(c);
        f.course = Goals.has(c, "learner");
        f.goals = Goals.all(c).size();
        int[] sm = MotiMath.savedMonths(all, now, 3, 0);
        f.savedAny = sm[0];
        f.saved10Run = MotiMath.savedMonths(all, now, 3, 10)[1];
        f.investMonths = MotiMath.investMonths(all, now, 5);
        double[] hf = new double[6];
        int score = 0;
        try { for (int p : MoneyFacts.health(c, hf)) score += p; } catch (Exception ignored) { }
        f.measured = Goals.monthlySpend(c) > 0;
        f.health = f.measured ? score : 0;
        f.runway = f.measured ? hf[1] : 0;
        return f;
    }

    /** {done so far, needed} for a step (for the little progress text). */
    static double[] progress(Facts f, String id) {
        switch (id) {
            case "track30": return new double[]{Math.min(30, f.trackedDays), 30};
            case "free5": return new double[]{Math.min(5, f.total), 5};
            case "learn3": return new double[]{Math.min(3, f.lessons), 3};
            case "green": return new double[]{Math.min(1, f.savedAny), 1};
            case "goal": return new double[]{Math.min(1, f.goals), 1};
            case "streak3": return new double[]{Math.min(3, f.best), 3};
            case "runway3": return new double[]{Math.min(3, Math.floor(f.runway * 10) / 10), 3};
            case "save10x3": return new double[]{Math.min(3, f.saved10Run), 3};
            case "course": return new double[]{f.course ? 1 : 0, 1};
            case "invest3": return new double[]{Math.min(3, f.investMonths), 3};
            case "runway6": return new double[]{Math.min(6, Math.floor(f.runway * 10) / 10), 6};
            case "health65": return new double[]{Math.min(65, f.health), 65};
            default: return new double[]{0, 1};
        }
    }

    // ------------------------------------------------------------------ check (Grow tab and the background job)

    /** Mark steps reached, give badges, move up a level. With notify, a new level also posts a notification. */
    static Facts check(Context c, boolean notify) {
        Facts f = facts(c);
        JSONObject o = data(c);
        String before = o.toString();
        JSONObject steps = o.optJSONObject("steps");
        if (steps == null) steps = new JSONObject();
        long now = System.currentTimeMillis();
        try {
            for (String[][] lv : STEPS)
                for (String[] st : lv) {
                    double[] p = progress(f, st[0]);
                    if (!steps.has(st[0]) && p[0] >= p[1]) steps.put(st[0], now);
                }
            o.put("steps", steps);
        } catch (Exception ignored) { }
        int level = o.optInt("level", 0), was = level;
        while (level < STEPS.length) {
            boolean all = true;
            for (String[] st : STEPS[level]) if (!steps.has(st[0])) all = false;
            if (!all) break;
            level++;
        }
        try {
            o.put("level", level);
            if (level > was) o.put("levelNew", level);
        } catch (Exception ignored) { }
        if (!o.toString().equals(before)) save(c, o);   // saving wakes every screen up, so only when something changed

        // badges
        if (f.total >= 1) Goals.award(c, "nospend1");
        if (f.best >= 3) Goals.award(c, "streak3");
        if (f.best >= 7) Goals.award(c, "streak7");
        if (f.monthFree >= 10 || f.lastMonthFree >= 10) Goals.award(c, "nospend10");
        if (f.total >= 30) Goals.award(c, "nospend30");
        if (f.savedAny >= 1) Goals.award(c, "in_green");
        if (f.saved10Run >= 3) Goals.award(c, "steady");
        if (f.investMonths >= 3) Goals.award(c, "sip_habit");

        if (notify && level > was && Alerts.canNotify(c)) {
            String[] l = LEVELS[level];
            Alerts.postIntent(c, Alerts.CH_ALERTS, 4050, "🎉 You're now a " + l[2], l[3] + " — see what's next",
                    new Intent(c, LevelActivity.class));
        }
        if (notify) wrappedNudge(c);
        return f;
    }

    /** The level just reached, once (for the celebration), else 0. */
    static int takeLevelNew(Context c) {
        JSONObject o = data(c);
        int n = o.optInt("levelNew", 0);
        if (n > 0) { o.remove("levelNew"); save(c, o); }
        return n;
    }

    // ------------------------------------------------------------------ Money Wrapped

    /** Which year to tell: last year in early January, else this year (so far). */
    static int wrapYear(long now) {
        Calendar k = Fmt.cal(now);
        int y = k.get(Calendar.YEAR);
        return k.get(Calendar.MONTH) == Calendar.JANUARY && k.get(Calendar.DAY_OF_MONTH) <= 15 ? y - 1 : y;
    }

    /** True from 1 December to 15 January — the year is (almost) complete. */
    static boolean wrapSeason(long now) {
        Calendar k = Fmt.cal(now);
        return k.get(Calendar.MONTH) == Calendar.DECEMBER || (k.get(Calendar.MONTH) == Calendar.JANUARY && k.get(Calendar.DAY_OF_MONTH) <= 15);
    }

    /** On 31 December (from 10 am) or in early January: one notification that the year's story is ready. */
    static void wrappedNudge(Context c) {
        Calendar k = Calendar.getInstance();
        boolean last = k.get(Calendar.MONTH) == Calendar.DECEMBER && k.get(Calendar.DAY_OF_MONTH) == 31 && k.get(Calendar.HOUR_OF_DAY) >= 10;
        boolean early = k.get(Calendar.MONTH) == Calendar.JANUARY && k.get(Calendar.DAY_OF_MONTH) <= 7 && k.get(Calendar.HOUR_OF_DAY) >= 10;
        if (!last && !early) return;
        int year = last ? k.get(Calendar.YEAR) : k.get(Calendar.YEAR) - 1;
        JSONObject o = data(c);
        if (o.optInt("wrapNote", 0) >= year) return;
        if (!MotiMath.years(Store.get(c).txns()).contains(year)) return;
        try { o.put("wrapNote", year); } catch (Exception ignored) { }
        save(c, o);
        Alerts.postIntent(c, Alerts.CH_ALERTS, 4060, "🎁 Your " + year + " Money Wrapped is ready", "Your year in money, in one minute",
                new Intent(c, WrappedActivity.class).putExtra("year", year));
    }
}
