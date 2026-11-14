package com.debangshu.money;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2.8 Motivation — the maths, in plain Java (tested on the desktop by T20).
 * No-spend days and streaks, and the numbers behind Money Wrapped (a year's story).
 */
public final class MotiMath {

    private MotiMath() {}

    // ------------------------------------------------------------------ no-spend days

    /** Planned money: these never break a no-spend day (same rule as the no-spend challenge). */
    public static boolean isNeed(String cat) {
        return Cats.BILLS.equals(cat) || Cats.HOME.equals(cat) || Cats.EMI.equals(cat) || Cats.INVEST.equals(cat)
                || Cats.INSURE.equals(cat) || Cats.SUBS.equals(cat) || Cats.RECHARGE.equals(cat);
    }

    /** A balance gap ("Unrecorded"): money left at some unknown time, so it can't be pinned to a day. */
    public static boolean unknownDay(Txn x) {
        return (x.sources & Txn.SRC_GAP) != 0 && !x.userCategory;
    }

    /** Spending on wants that breaks a no-spend day. */
    public static boolean want(Txn x) {
        return x.isSpend() && !unknownDay(x) && !isNeed(x.category);
    }

    /** Day (start, local time) → money spent on wants that day, in paise. */
    public static Map<Long, Long> wantsByDay(List<Txn> all) {
        Map<Long, Long> m = new HashMap<>();
        for (Txn x : all) {
            if (!want(x)) continue;
            long d = Fmt.startOfDay(x.time);
            Long v = m.get(d);
            m.put(d, (v == null ? 0 : v) + x.amount);
        }
        return m;
    }

    /** The first day Money has anything for (0 = nothing yet). */
    public static long trackStart(List<Txn> all) {
        long min = Long.MAX_VALUE;
        for (Txn x : all) if (x.visible() && x.time > 0 && x.time < min) min = x.time;
        return min == Long.MAX_VALUE ? 0 : Fmt.startOfDay(min);
    }

    public static final int NONE = -1, SPENT = 0, FREE = 1, TODAY_FREE = 2;

    /** NONE before tracking started or in the future; TODAY_FREE = nothing spent on wants yet today. */
    public static int state(Map<Long, Long> wants, long day, long start, long today) {
        if (start == 0 || day < start || day > today) return NONE;
        boolean free = !wants.containsKey(day);
        if (day == today) return free ? TODAY_FREE : SPENT;
        return free ? FREE : SPENT;
    }

    /**
     * {current streak, best streak, no-spend days in all} from the first day up to {@code last}.
     * Today counts while it's still free (a spend later today ends the streak).
     */
    public static int[] streaks(Map<Long, Long> wants, long start, long last) {
        int cur = 0, best = 0, total = 0;
        if (start == 0) return new int[]{0, 0, 0};
        for (long d = start; d <= last; d = Fmt.addDays(d, 1)) {
            if (!wants.containsKey(d)) { cur++; total++; if (cur > best) best = cur; }
            else cur = 0;
        }
        return new int[]{cur, best, total};
    }

    /** No-spend days in a month (finished days, plus today if still free). */
    public static int monthFree(Map<Long, Long> wants, long monthStart, long start, long today) {
        int n = 0;
        long end = Fmt.addMonths(monthStart, 1);
        for (long d = monthStart; d < end; d = Fmt.addDays(d, 1)) {
            int s = state(wants, d, start, today);
            if (s == FREE || s == TODAY_FREE) n++;
        }
        return n;
    }

    /** On a day you do spend on wants, how much (average over the days in [from, to) that had spending). */
    public static long avgWantDay(Map<Long, Long> wants, long from, long to) {
        long sum = 0;
        int n = 0;
        for (Map.Entry<Long, Long> e : wants.entrySet()) {
            if (e.getKey() < from || e.getKey() >= to) continue;
            sum += e.getValue();
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    // ------------------------------------------------------------------ Money Wrapped

    public static final class Wrap {
        public long from, to;                  // the period told (from = start of year, or the day Money started)
        public boolean partYear;               // Money started during the year
        public int payments;                   // spends + money in
        public long in, out, kept;             // paise
        public int ratePct = -1;               // kept / in (−1 when no income was seen)
        public LinkedHashMap<String, Long> cats = new LinkedHashMap<>();   // biggest first
        public String topCat = "";
        public long topCatAmt;
        public int topCatPct;
        public String topWant = "";            // biggest category you could choose to spend less on
        public long topWantAmt;
        public String fav = "";                // the name you paid most often
        public int favCount;
        public long favAmt;
        public long bigAmt;                    // biggest single payment
        public String bigName = "";
        public long bigTime;
        public long[] months = new long[12];   // spending per month
        public int bigMonth = -1, lowMonth = -1;
        public int busyDow = -1;               // Calendar.SUNDAY..SATURDAY minus 1 (0 = Sunday)
        public long busyDowAvg;
        public int smallCount;                 // payments under ₹100
        public long smallAmt;
        public int freeDays, bestStreak;
        public long prevOut = -1;              // same months last year (−1 = Money wasn't there yet)
        public int needsPct;                   // share of spending that was bills, rent, EMIs…
        public String persona = "steady";
        public long cutMonthly, cutYearly;     // spend 10% less on topWant
        public long cutGrow10;                 // that, put in a SIP for 10 years at 12%
    }

    static final long SMALL = 10000;   // ₹100 in paise

    /**
     * The year's story. {@code to} is the end of the year or now (whichever is first); {@code start} is when Money
     * started (from {@link #trackStart}); the period is [max(yearStart, start), to).
     */
    public static Wrap wrap(List<Txn> all, int year, long now) {
        Wrap w = new Wrap();
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Calendar.JANUARY, 1);
        long ys = c.getTimeInMillis();
        long ye = Fmt.addMonths(ys, 12);
        long start = trackStart(all);
        w.from = Math.max(ys, start);
        w.partYear = start > ys;
        w.to = Math.min(ye, now);
        if (start == 0 || w.from >= w.to) return w;

        Map<String, Long> by = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        Map<String, Long> spentAt = new HashMap<>();
        long[] dowSum = new long[7];
        Set<Long> seenDays = new HashSet<>();
        long needs = 0;
        for (Txn x : all) {
            if (x.time < w.from || x.time >= w.to || x.kind != Txn.NORMAL) continue;
            if (x.credit) { w.in += x.amount; w.payments++; continue; }
            w.out += x.amount;
            w.payments++;
            String cat = x.category == null || x.category.isEmpty() ? Cats.OTHER : x.category;
            Long v = by.get(cat);
            by.put(cat, (v == null ? 0 : v) + x.amount);
            if (isNeed(cat)) needs += x.amount;
            Calendar t = Fmt.cal(x.time);
            w.months[t.get(Calendar.MONTH)] += x.amount;
            if (unknownDay(x)) continue;          // the rest needs a real day and a real name
            dowSum[t.get(Calendar.DAY_OF_WEEK) - 1] += x.amount;
            seenDays.add(Fmt.startOfDay(x.time));
            if (x.amount < SMALL) { w.smallCount++; w.smallAmt += x.amount; }
            if (x.amount > w.bigAmt) { w.bigAmt = x.amount; w.bigName = x.merchant; w.bigTime = x.time; }
            if (!x.unnamed()) {
                String k = x.merchant.trim();
                Integer n = count.get(k);
                count.put(k, (n == null ? 0 : n) + 1);
                Long s = spentAt.get(k);
                spentAt.put(k, (s == null ? 0 : s) + x.amount);
            }
        }
        w.kept = w.in - w.out;
        if (w.in > 0) w.ratePct = (int) Math.round(w.kept * 100.0 / w.in);
        w.needsPct = w.out > 0 ? (int) Math.round(needs * 100.0 / w.out) : 0;

        // categories, biggest first
        List<Map.Entry<String, Long>> l = new ArrayList<>(by.entrySet());
        Collections.sort(l, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> e : l) w.cats.put(e.getKey(), e.getValue());
        if (!l.isEmpty()) {
            w.topCat = l.get(0).getKey();
            w.topCatAmt = l.get(0).getValue();
            w.topCatPct = (int) Math.round(w.topCatAmt * 100.0 / Math.max(1, w.out));
        }
        for (Map.Entry<String, Long> e : l) {
            String k = e.getKey();
            if (isNeed(k) || Cats.UNRECORDED.equals(k) || Cats.OTHER.equals(k) || Cats.CASH.equals(k) || Cats.HEALTH.equals(k)
                    || Cats.EDU.equals(k) || Cats.FEES.equals(k)) continue;
            w.topWant = k;
            w.topWantAmt = e.getValue();
            break;
        }

        // most-visited name (ties: more money)
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            long amt = spentAt.get(e.getKey());
            if (e.getValue() > w.favCount || (e.getValue() == w.favCount && amt > w.favAmt)) {
                w.fav = e.getKey();
                w.favCount = e.getValue();
                w.favAmt = amt;
            }
        }

        // biggest and lightest month (only months fully or partly inside the period with spending)
        for (int m = 0; m < 12; m++) {
            if (w.months[m] <= 0) continue;
            if (w.bigMonth < 0 || w.months[m] > w.months[w.bigMonth]) w.bigMonth = m;
            if (w.lowMonth < 0 || w.months[m] < w.months[w.lowMonth]) w.lowMonth = m;
        }

        // busiest weekday: average per such day in the period
        int[] dows = new int[7];
        for (long d = Fmt.startOfDay(w.from); d < w.to; d = Fmt.addDays(d, 1)) dows[Fmt.cal(d).get(Calendar.DAY_OF_WEEK) - 1]++;
        for (int i = 0; i < 7; i++) {
            if (dows[i] == 0 || dowSum[i] == 0) continue;
            long avg = dowSum[i] / dows[i];
            if (avg > w.busyDowAvg) { w.busyDowAvg = avg; w.busyDow = i; }
        }

        // no-spend days and the longest run, inside the period (today only once it's over)
        Map<Long, Long> wants = wantsByDay(all);
        long last = Math.min(Fmt.addDays(Fmt.startOfDay(w.to), w.to == Fmt.startOfDay(w.to) ? -1 : 0), Fmt.addDays(Fmt.startOfDay(now), -1));
        int[] st = streaks(wants, Fmt.startOfDay(w.from), last);
        w.freeDays = st[2];
        w.bestStreak = st[1];

        // the same months last year — only if Money was already there then
        long pf = Fmt.addMonths(w.from, -12), pt = Fmt.addMonths(w.to, -12);
        if (start <= pf) w.prevOut = Ledger.spent(all, pf, pt);

        // one idea: 10% less on the biggest want, as a SIP for 10 years at 12%
        double days = Math.max(1, (w.to - w.from) / 86400000.0);
        if (w.topWantAmt > 0 && days >= 90) {   // shorter than 3 months is too little to guess a whole year from
            long perYear = Math.round(w.topWantAmt * 365 / days);
            w.cutYearly = perYear / 10;
            w.cutMonthly = w.cutYearly / 12;
            w.cutGrow10 = Math.round(SaveMath.sip(w.cutMonthly / 100.0, 120, 0.12) * 100);
        }
        w.persona = persona(w);
        return w;
    }

    /** A fun "money style", always from a real number. */
    public static String persona(Wrap w) {
        if (w.ratePct >= 20) return "saver";
        if (w.needsPct >= 70) return "planner";
        String t = w.topWant;
        if (Cats.FOOD.equals(t)) return "foodie";
        if (Cats.SHOP.equals(t)) return "shopper";
        if (Cats.TRAVEL.equals(t) || Cats.TRANSPORT.equals(t) || Cats.FUEL.equals(t)) return "explorer";
        if (Cats.PEOPLE.equals(t) || Cats.GIFTS.equals(t)) return "giver";
        if (Cats.FUN.equals(t)) return "fun";
        return "steady";
    }

    /** Years that have at least one payment, newest first. */
    public static List<Integer> years(List<Txn> all) {
        Set<Integer> s = new HashSet<>();
        for (Txn x : all) if (x.kind == Txn.NORMAL && x.time > 0) s.add(Fmt.cal(x.time).get(Calendar.YEAR));
        List<Integer> l = new ArrayList<>(s);
        Collections.sort(l, Collections.reverseOrder());
        return l;
    }

    // ------------------------------------------------------------------ month checks for levels

    /** {money in, money out} for a calendar month. */
    public static long[] month(List<Txn> all, long monthStart) {
        long e = Fmt.addMonths(monthStart, 1);
        return new long[]{Ledger.income(all, monthStart, e), Ledger.spent(all, monthStart, e)};
    }

    /** Months (of the last {@code back} full ones) where you kept at least {@code pct}% of what came in; also the run ending last month. */
    public static int[] savedMonths(List<Txn> all, long now, int back, int pct) {
        long ms = Fmt.startOfMonth(now);
        int n = 0, run = 0;
        boolean running = true;
        for (int k = 1; k <= back; k++) {
            long[] io = month(all, Fmt.addMonths(ms, -k));
            boolean ok = io[0] > 0 && (io[0] - io[1]) * 100 >= (long) pct * io[0] && io[0] > io[1];
            if (ok) n++;
            if (running && ok) run++;
            else running = false;
        }
        return new int[]{n, run};
    }

    /** Different months (this one and the {@code back} before it) with a payment to Investments. */
    public static int investMonths(List<Txn> all, long now, int back) {
        long from = Fmt.addMonths(Fmt.startOfMonth(now), -back);
        Set<Long> m = new HashSet<>();
        for (Txn x : all) if (x.isSpend() && Cats.INVEST.equals(x.category) && x.time >= from && x.time <= now) m.add(Fmt.startOfMonth(x.time));
        return m.size();
    }
}
