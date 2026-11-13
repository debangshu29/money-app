package com.debangshu.money;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Goal maths (plain Java, tested on the desktop): how far along a goal is, whether it's on track,
 * when it will be reached, how to split a monthly saving across goals, festivals, and where goal money could sit.
 * All amounts are paise.
 */
public final class GoalMath {

    private GoalMath() {}

    public static final long DAY = 86400000L;
    public static final double MONTH_DAYS = 30.44;

    public static final int REACHED = 0, PAUSED = 1, AHEAD = 2, ON_TRACK = 3, BEHIND = 4, NOT_MOVING = 5, NO_DATE = 6;

    /** Everything a goal screen needs to say. */
    public static final class Status {
        public int kind;
        public long current, target, left;
        /** needed per month to finish by the date (0 without a date) */
        public long needPerMonth;
        /** how fast it has really been growing per month (-1 = not known yet) */
        public long pace = -1;
        /** pace used for the forecast (real pace, else your plan, else your usual saving share) */
        public long usedPace;
        /** when it will be reached at usedPace (0 = never at this pace) */
        public long finish;
        /** behind (+) or ahead (−) of a straight-line plan from start to the date */
        public long gap;
        /** extra per month needed to catch up (BEHIND only) */
        public long catchUp;
        public int percent;
    }

    /** Months between two times (can be fractional). */
    public static double months(long from, long to) { return (to - from) / (DAY * MONTH_DAYS); }

    /** Whole months left until the date, at least 1. */
    public static int monthsLeft(long now, long by) {
        if (by <= now) return 1;
        return (int) Math.max(1, Math.ceil(months(now, by) - 0.05));
    }

    /**
     * @param current   money in the goal now
     * @param target    goal amount
     * @param start     when the goal started
     * @param startValue money in the goal when it started
     * @param by        the date (0 = no date)
     * @param pace      real growth per month (-1 = unknown)
     * @param fallback  plan / usual saving to use when the real pace is unknown (0 = none)
     */
    public static Status status(long now, long current, long target, long start, long startValue, long by,
                                long pace, long fallback, boolean paused) {
        Status s = new Status();
        s.current = Math.max(0, current);
        s.target = Math.max(1, target);
        s.left = Math.max(0, target - s.current);
        s.percent = (int) Math.min(100, Math.floor(s.current * 100.0 / s.target));
        s.pace = pace;
        s.usedPace = pace > 0 ? pace : fallback > 0 ? fallback : 0;
        if (by > 0 && s.left > 0) s.needPerMonth = (long) Math.ceil(s.left / (double) monthsLeft(now, by));
        if (s.left == 0) { s.kind = REACHED; s.percent = 100; return s; }
        s.finish = finish(now, s.left, s.usedPace);
        if (paused) { s.kind = PAUSED; return s; }
        if (by <= 0) { s.kind = s.usedPace > 0 ? NO_DATE : NOT_MOVING; return s; }
        // a straight line from the start to the date: where should it be by now?
        double total = Math.max(1, by - start);
        double done = Math.max(0, Math.min(1, (now - start) / total));
        long expected = startValue + Math.round((target - startValue) * done);
        s.gap = expected - s.current;
        if (s.usedPace <= 0) { s.kind = NOT_MOVING; s.catchUp = s.needPerMonth; return s; }
        if (s.finish > 0 && s.finish <= by) {
            s.kind = s.finish <= by - 30 * DAY ? AHEAD : ON_TRACK;
        } else {
            s.kind = BEHIND;
            s.catchUp = Math.max(0, s.needPerMonth - s.usedPace);
        }
        return s;
    }

    /** When `left` is reached growing `perMonth` (0 = never). */
    public static long finish(long now, long left, long perMonth) {
        if (left <= 0) return now;
        if (perMonth <= 0) return 0;
        double m = left / (double) perMonth;
        if (m > 1200) return 0;
        return now + Math.round(m * MONTH_DAYS * DAY);
    }

    /** Real growth per month from dated snapshots {time, value} (oldest first), looking back up to ~3 months. */
    public static long pace(long[][] snaps, long now) {
        if (snaps == null || snaps.length < 2) return -1;
        long[] last = snaps[snaps.length - 1];
        long[] first = snaps[0];
        for (long[] s : snaps) if (s[0] >= now - 92 * DAY) { first = s; break; }
        double m = months(first[0], last[0]);
        if (m < 0.5) {
            // under two weeks of history: not enough to call it a pace
            return -1;
        }
        return Math.round((last[1] - first[1]) / m);
    }

    /**
     * Split a monthly saving across goals. needs[i] = what goal i needs per month (0 if it has no date),
     * lefts[i] = what is left. Goals with a date get what they need first (shared in proportion if not enough);
     * whatever remains is shared among the rest in proportion to what's left.
     */
    public static long[] split(long amount, long[] needs, long[] lefts) {
        int n = needs.length;
        long[] out = new long[n];
        if (amount <= 0 || n == 0) return out;
        long needSum = 0;
        for (int i = 0; i < n; i++) if (lefts[i] > 0) needSum += needs[i];
        long rest = amount;
        if (needSum > 0) {
            double f = Math.min(1.0, amount / (double) needSum);
            for (int i = 0; i < n; i++) if (lefts[i] > 0 && needs[i] > 0) { out[i] = Math.min(lefts[i], Math.round(needs[i] * f)); rest -= out[i]; }
        }
        if (rest > 0) {
            long leftSum = 0;
            for (int i = 0; i < n; i++) leftSum += Math.max(0, lefts[i] - out[i]);
            if (leftSum > 0) {
                long given = 0;
                for (int i = 0; i < n; i++) {
                    long room = Math.max(0, lefts[i] - out[i]);
                    long add = Math.min(room, Math.round(rest * (room / (double) leftSum)));
                    out[i] += add;
                    given += add;
                }
                rest -= given;
            }
        }
        return out;
    }

    /** Which milestones (25, 50, 75, 100) were crossed going from one percent to another. */
    public static List<Integer> crossed(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int m : new int[]{25, 50, 75, 100}) if (from < m && to >= m) out.add(m);
        return out;
    }

    // ------------------------------------------------------------------ where goal money could sit (for learning)

    /** {title, what, why} for how far away the goal is. */
    public static String[] whereToKeep(int monthsLeft) {
        if (monthsLeft <= 12) return new String[]{"Under 1 year",
                "Savings account, or an FD that ends before your date",
                "A short trip 🚲 — money you need soon should sit somewhere safe, like a locker. It won't grow much, but it will surely be there on the day."};
        if (monthsLeft <= 36) return new String[]{"1 to 3 years",
                "RD or FD, or a debt fund",
                "A longer trip 🚌 — the bank pays you a little extra for keeping money with it (that's interest). Small growth, tiny wobbles. Shares are too bumpy for money you need this soon."};
        if (monthsLeft <= 60) return new String[]{"3 to 5 years",
                "Mostly FD or debt funds, a small part in a balanced (hybrid) fund",
                "A long train ride 🚆 — a small part can take the faster, bumpier road. In the last year, move everything back to safe places."};
        return new String[]{"5 years or more",
                "Equity mutual funds or an index fund through a monthly SIP; PPF for very long goals",
                "A long flight ✈️ — shares go up and down like a roller-coaster, but over many years they have usually grown more than FD. Time smooths out the bumps."};
    }

    // ------------------------------------------------------------------ festivals (start dates; Islamic dates are approximate)

    /** name, emoji, then yyyy-mm-dd dates (oldest first). */
    public static final String[][] FESTIVALS = {
            {"Durga Puja", "🪔", "2025-09-29", "2026-10-17", "2027-10-06", "2028-09-25"},
            {"Diwali & Kali Puja", "🪔", "2025-10-20", "2026-11-08", "2027-10-29", "2028-10-17"},
            {"Christmas & New Year", "🎄", "2025-12-25", "2026-12-25", "2027-12-25", "2028-12-25"},
            {"Holi", "🎨", "2025-03-14", "2026-03-04", "2027-03-22", "2028-03-11"},
            {"Eid", "🌙", "2025-03-31", "2026-03-20", "2027-03-10", "2028-02-27"},
            {"Poila Baisakh", "🎉", "2025-04-15", "2026-04-15", "2027-04-15", "2028-04-14"},
    };

    public static long parse(String ymd) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        c.clear();
        String[] p = ymd.split("-");
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
        return c.getTimeInMillis();
    }

    /** The next date of festival i after now (0 = none in the table), and the one before it (for "last time"). */
    public static long[] nextAndLast(int i, long now) {
        String[] f = FESTIVALS[i];
        long last = 0;
        for (int k = 2; k < f.length; k++) {
            long t = parse(f[k]);
            if (t > now) return new long[]{t, last};
            last = t;
        }
        return new long[]{0, last};
    }

    /**
     * Extra spent around a festival: what went out in the 3 weeks before it (and 3 days after),
     * minus a normal 24 days at your usual daily spend (the 60 days before that window).
     * daySpend[k] = spend on day k counted from dayZero. Returns -1 if there's no data that far back.
     */
    public static long festivalExtra(long[] daySpend, long dayZero, long festival) {
        int start = (int) Math.floor((festival - 21 * DAY - dayZero) / (double) DAY);
        int end = start + 24;
        int base = start - 60;
        if (base < 0 || end > daySpend.length) return -1;
        long window = 0, normal = 0;
        for (int k = start; k < end; k++) window += daySpend[k];
        for (int k = base; k < start; k++) normal += daySpend[k];
        if (normal == 0 && window == 0) return -1;
        long usual = Math.round(normal / 60.0 * 24);
        return Math.max(0, window - usual);
    }
}
