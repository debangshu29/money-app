package com.debangshu.money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A budget you start yourself: an amount, a start date, how long it runs,
 * and (optionally) categories that don't count. Only payments from the start date count.
 * Plain Java (tested on a computer).
 */
public final class Plan {

    public static final int MONTH = 0, TWO_WEEKS = 1, WEEK = 2, CUSTOM = 3;
    public static final String[] LENGTH_NAMES = {"1 month", "2 weeks", "1 week", "Pick end date"};

    public String id = "";
    public long amount;          // paise
    public long start;           // start of the first day
    public long end;             // start of the day AFTER the last day
    public int length = MONTH;
    public boolean repeat = true;
    public List<String> skip = new ArrayList<>();   // categories that don't count
    public long closedAt;        // when it ended (past budgets only)

    public static long endFor(long start, int length, long customEnd) {
        switch (length) {
            case TWO_WEEKS: return Fmt.addDays(start, 14);
            case WEEK: return Fmt.addDays(start, 7);
            case CUSTOM: return Fmt.addDays(Fmt.startOfDay(customEnd), 1);
            default: return Fmt.addMonths(start, 1);
        }
    }

    /** The next budget with the same settings, starting the day this one ends. */
    public Plan next() {
        Plan p = new Plan();
        p.id = java.util.UUID.randomUUID().toString();
        p.amount = amount;
        p.start = end;
        p.length = length == CUSTOM ? CUSTOM : length;
        p.end = length == CUSTOM ? end + (end - start) : endFor(end, length, 0);
        p.repeat = repeat;
        p.skip = new ArrayList<>(skip);
        return p;
    }

    public boolean counts(Txn t) {
        return t.isSpend() && t.time >= start && t.time < end && !skip.contains(t.category.isEmpty() ? Cats.OTHER : t.category);
    }

    public long spent(List<Txn> all) {
        long s = 0;
        for (Txn t : all) if (counts(t)) s += t.amount;
        return s;
    }

    /** Spending in the categories you left out (shown separately). */
    public long skipped(List<Txn> all) {
        long s = 0;
        for (Txn t : all) if (t.isSpend() && t.time >= start && t.time < end && skip.contains(t.category)) s += t.amount;
        return s;
    }

    public List<Txn> payments(List<Txn> all) {
        List<Txn> out = new ArrayList<>();
        for (Txn t : all) if (counts(t)) out.add(t);
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    public LinkedHashMap<String, Long> byCategory(List<Txn> all) {
        Map<String, Long> m = new HashMap<>();
        for (Txn t : all) {
            if (!counts(t)) continue;
            String c = t.category.isEmpty() ? Cats.OTHER : t.category;
            Long v = m.get(c);
            m.put(c, (v == null ? 0 : v) + t.amount);
        }
        return Ledger.sorted(m);
    }

    public long[] byDay(List<Txn> all) {
        int n = days();
        long[] d = new long[Math.max(1, n)];
        for (Txn t : all) {
            if (!counts(t)) continue;
            int i = (int) Math.floor((Fmt.startOfDay(t.time) - start) / 86400000.0 + 0.5);
            if (i >= 0 && i < d.length) d[i] += t.amount;
        }
        return d;
    }

    /** Number of days in the budget. */
    public int days() { return (int) Math.max(1, Math.round((end - start) / 86400000.0)); }

    /** Days left including today (0 when finished). */
    public int daysLeft(long now) {
        if (now >= end) return 0;
        long today = Fmt.startOfDay(Math.max(now, start));
        return (int) Math.max(1, Math.round((end - today) / 86400000.0));
    }

    /** Index of today inside the budget (-1 before it starts). */
    public int dayIndex(long now) {
        if (now < start) return -1;
        return (int) Math.min(days() - 1, Math.round((Fmt.startOfDay(now) - start) / 86400000.0));
    }

    public boolean started(long now) { return now >= start; }

    public boolean finished(long now) { return now >= end; }

    /** "10 Nov – 9 Dec" */
    public String range() {
        long last = Fmt.addDays(end, -1);
        java.util.Calendar a = Fmt.cal(start), b = Fmt.cal(last);
        String s = a.get(java.util.Calendar.DAY_OF_MONTH) + " " + Fmt.MON[a.get(java.util.Calendar.MONTH)];
        String e = b.get(java.util.Calendar.DAY_OF_MONTH) + " " + Fmt.MON[b.get(java.util.Calendar.MONTH)];
        if (a.get(java.util.Calendar.YEAR) != b.get(java.util.Calendar.YEAR)) {
            s += " " + a.get(java.util.Calendar.YEAR);
            e += " " + b.get(java.util.Calendar.YEAR);
        }
        return s + " – " + e;
    }

    /** Where the month is heading, at the usual pace so far. */
    public long projected(List<Txn> all, long now) {
        long spent = spent(all);
        if (now < start) return 0;
        if (now >= end) return spent;
        double daysGone = Math.max(1, (Fmt.startOfDay(now) - start) / 86400000.0 + 1);
        return Math.round(spent / daysGone * days() / 100.0) * 100;
    }

    /** One line about how it's going. */
    public String status(List<Txn> all, long now) {
        long spent = spent(all);
        long left = amount - spent;
        if (now < start) return "Starts " + Fmt.rel(start) + " (" + Fmt.date(start) + ")";
        if (now >= end) return left >= 0 ? "Finished " + Fmt.rs(left) + " under budget 🎉" : "Finished " + Fmt.rs(-left) + " over budget";
        if (left < 0) return Fmt.rs(-left) + " over budget";
        int d = daysLeft(now);
        return Fmt.rs(left / d) + " a day for " + d + (d == 1 ? " day" : " days");
    }

    /** "Mostly Shopping (+₹6,000) and Food (+₹3,000)": categories above what you usually spend in a period this long. */
    public String overReasons(List<Txn> all) {
        LinkedHashMap<String, Long> now = byCategory(all);
        long len = end - start;
        Map<String, Long> usual = new HashMap<>();
        for (int k = 1; k <= 3; k++) {
            long s = start - k * len, e = s + len;
            for (Txn t : all) {
                if (!t.isSpend() || t.time < s || t.time >= e || skip.contains(t.category)) continue;
                Long v = usual.get(t.category);
                usual.put(t.category, (v == null ? 0 : v) + t.amount);
            }
        }
        List<String[]> parts = new ArrayList<>();
        Map<String, Long> extra = new HashMap<>();
        for (Map.Entry<String, Long> e : now.entrySet()) {
            Long u = usual.get(e.getKey());
            long avg = u == null ? 0 : u / 3;
            long x = e.getValue() - avg;
            if (x > 0) extra.put(e.getKey(), x);
        }
        int i = 0;
        for (Map.Entry<String, Long> e : Ledger.sorted(extra).entrySet()) {
            if (i++ >= 3 || e.getValue() < 50000) break;
            parts.add(new String[]{e.getKey(), Fmt.rs(e.getValue())});
        }
        if (parts.isEmpty()) {
            i = 0;
            for (Map.Entry<String, Long> e : now.entrySet()) {
                if (i++ >= 2) break;
                parts.add(new String[]{e.getKey(), Fmt.rs(e.getValue())});
            }
            if (parts.isEmpty()) return "";
            StringBuilder b = new StringBuilder("Biggest: ");
            for (int j = 0; j < parts.size(); j++) b.append(j > 0 ? ", " : "").append(parts.get(j)[0]).append(" ").append(parts.get(j)[1]);
            return b.toString();
        }
        StringBuilder b = new StringBuilder("More than usual on ");
        for (int j = 0; j < parts.size(); j++) {
            b.append(j == 0 ? "" : j == parts.size() - 1 ? " and " : ", ").append(parts.get(j)[0]).append(" (+").append(parts.get(j)[1]).append(")");
        }
        return b.toString();
    }
}
