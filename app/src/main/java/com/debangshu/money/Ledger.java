package com.debangshu.money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The smart bits that work on the list of transactions (plain Java, tested on a computer):
 *  - same payment seen twice (bank SMS + Google Pay notification) → counted once
 *  - missed SMS: the balance in the next SMS doesn't add up → an "Unrecorded" entry fills the gap
 *  - repeating payments (subscriptions, EMIs, rent) and their next date
 *  - totals by category / merchant / day
 */
public final class Ledger {

    private Ledger() {}

    // ------------------------------------------------------------------ same payment twice

    static String last4(String account) {
        if (account == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{3,4})$").matcher(account.trim());
        return m.find() ? m.group(1) : "";
    }

    static boolean sameAccount(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return true;
        if (a.equals(b)) return true;
        String x = last4(a), y = last4(b);
        if (!x.isEmpty() && !y.isEmpty()) return x.endsWith(y) || y.endsWith(x);
        return false;
    }

    /** The transaction already saved that is really the same payment as {@code n}, or null. */
    public static Txn findTwin(List<Txn> all, Txn n) {
        Txn best = null;
        long bestDt = Long.MAX_VALUE;
        int nClass = n.sources & (Txn.SRC_SMS | Txn.SRC_APP);
        if (nClass == 0) return null;   // added by hand / found by the balance check: never merged
        for (Txn e : all) {
            if (e.kind == Txn.SNAPSHOT || (e.sources & (Txn.SRC_GAP | Txn.SRC_MANUAL)) != 0) continue;
            if (e.credit != n.credit || e.amount != n.amount) continue;
            long dt = Math.abs(e.time - n.time);
            if (!n.srcKey.isEmpty() && n.srcKey.equals(e.srcKey) && dt <= 6L * 3600000) return e;
            if (!e.ref.isEmpty() && !n.ref.isEmpty()) {
                if (e.ref.equals(n.ref) && dt < 3L * 86400000) return e;
                continue;
            }
            if (!sameAccount(e.account, n.account)) continue;
            boolean sameClass = (e.sources & nClass) != 0;
            if (sameClass) {
                if (!n.raw.isEmpty() && n.raw.equals(e.raw) && dt <= 6L * 3600000) return e;
                if (dt > 10L * 60000) continue;
            } else if (dt > 30L * 60000) {
                continue;
            }
            if (dt < bestDt) { bestDt = dt; best = e; }
        }
        return best;
    }

    /** Puts what {@code from} knows into {@code into} (the saved one). */
    public static void merge(Txn into, Txn from) {
        boolean fromSms = (from.sources & Txn.SRC_SMS) != 0;
        boolean fromApp = (from.sources & Txn.SRC_APP) != 0;
        into.sources |= from.sources;
        if (into.account.isEmpty()) into.account = from.account;
        if (from.balance >= 0 && (into.balance < 0 || fromSms)) into.balance = from.balance;
        if (from.limit >= 0 && into.limit < 0) into.limit = from.limit;
        if (into.ref.isEmpty()) into.ref = from.ref;
        if (into.vpa.isEmpty()) into.vpa = from.vpa;
        if (!into.userMerchant && !from.merchant.isEmpty()
                && (into.merchant.isEmpty() || (fromApp && !into.merchant.equals(from.merchant)) || into.merchant.startsWith("Shop (QR)")
                    || into.merchant.matches("[0-9]{5} [0-9]{5}"))) {
            into.merchant = from.merchant;
            if (!into.userCategory) into.category = "";   // guess again with the better name
        }
        if (!from.raw.isEmpty() && !into.raw.contains(from.raw)) into.raw = into.raw.isEmpty() ? from.raw : into.raw + "\n\n" + from.raw;
        if (into.sender.isEmpty()) into.sender = from.sender;
        else if (!from.sender.isEmpty() && !into.sender.contains(from.sender)) into.sender = into.sender + " + " + from.sender;
        if (from.kind != Txn.NORMAL && into.kind == Txn.NORMAL) into.kind = from.kind;
        if (from.time < into.time) into.time = from.time;
        if (!into.hasLoc() && from.hasLoc()) { into.lat = from.lat; into.lng = from.lng; into.locAcc = from.locAcc; into.place = from.place; }
        if (into.photo.isEmpty()) into.photo = from.photo;
    }

    // ------------------------------------------------------------------ missed SMS (balance doesn't add up)

    public static final String GAP_OUT = "Unrecorded spending", GAP_IN = "Unrecorded money in";

    /**
     * Walks one account's transactions in time order. Whenever a message tells the balance (or card limit),
     * it checks: previous balance ± everything in between = this balance? If it doesn't add up, it first tries
     * to EXPLAIN the difference before calling it "Unrecorded":
     *  1. payments seen only in Google Pay / PhonePe notifications (no account on them) in that time
     *     → they're linked to this account (that was the missing SMS)
     *  2. two messages that arrived in the wrong order → a +X and −X pair that cancels out
     *  3. the same payment counted twice → the extra copy is hidden
     *  4. small round-ups at quarter end (interest) or small debits (bank / SMS charges) → labelled as "probably …"
     * Only what's left becomes an "Unrecorded" entry, and it disappears by itself if the missing message turns up.
     * Note: this may update transactions in {@code all} (links app payments to the account, hides double counts).
     */
    public static List<Txn> gaps(List<Txn> all, String account) {
        List<Txn> out = new ArrayList<>();
        if (account == null || account.isEmpty()) return out;
        List<Txn> list = new ArrayList<>();
        for (Txn t : all) {
            if ((t.sources & Txn.SRC_GAP) != 0 || t.kind == Txn.IGNORED) continue;
            if (account.equals(t.account)) list.add(t);
        }
        Collections.sort(list, (a, b) -> Long.compare(a.time, b.time));
        fixOrder(list);
        java.util.Set<Txn> used = new java.util.HashSet<>();
        for (int mode = 0; mode < 2; mode++) {          // 0 = bank balance, 1 = card limit
            List<Txn> found = new ArrayList<>();
            Txn prev = null;
            long moved = 0;
            List<Txn> between = new ArrayList<>();
            for (Txn t : list) {
                long val = mode == 0 ? t.balance : t.limit;
                if (prev == null) {
                    if (val >= 0) { prev = t; moved = 0; between.clear(); }
                    continue;
                }
                if (t.kind != Txn.SNAPSHOT) { moved += t.signed(); between.add(t); }
                if (val < 0) continue;
                long prevVal = mode == 0 ? prev.balance : prev.limit;
                long diff = val - (prevVal + moved);
                if (Math.abs(diff) >= 100 && t.time - prev.time < 120L * 86400000) {
                    // 1. app-only payments (no account) in this window explain it?
                    List<Txn> link = explain(all, diff, prev.time, t.time, used);
                    if (link != null) {
                        for (Txn x : link) {
                            x.account = account;
                            if (x.time > t.time) x.time = t.time - 1000;
                            used.add(x);
                        }
                        diff = 0;
                    }
                    // 3. counted twice? (money "came in" = a duplicate debit inside this window)
                    if (diff > 0) {
                        List<Txn> win = new ArrayList<>();
                        win.add(prev);
                        win.addAll(between);
                        Txn dup = duplicateIn(win, diff);
                        if (dup != null) {
                            dup.kind = Txn.IGNORED;
                            dup.note = dup.note.isEmpty() ? "Hidden automatically: the same payment was counted twice (your balance shows it only once)." : dup.note;
                            diff = 0;
                        }
                    }
                }
                if (Math.abs(diff) >= 100 && t.time - prev.time < 120L * 86400000) {
                    Txn g = new Txn();
                    g.id = "gap:" + (mode == 0 ? "b:" : "l:") + t.id;
                    g.time = t.time - 1000;
                    g.gapFrom = prev.time;
                    g.amount = Math.abs(diff);
                    g.credit = diff > 0;
                    g.account = account;
                    g.merchant = diff > 0 ? GAP_IN : GAP_OUT;
                    g.category = diff > 0 ? Cats.INCOME : Cats.UNRECORDED;
                    g.sources = Txn.SRC_GAP;
                    g.review = true;
                    g.raw = "No SMS or notification was received for this. The balance went from "
                            + Fmt.plain(prevVal) + " (" + Fmt.dayTime(prev.time) + ") to " + Fmt.plain(val) + " ("
                            + Fmt.dayTime(t.time) + "), but the messages in between only explain "
                            + Fmt.signed(moved) + ".";
                    guessSmall(g, prev.time, t.time);
                    found.add(g);
                }
                prev = t;
                moved = 0;
                between.clear();
            }
            // 2. messages that arrived out of order: +X then −X (or −X then +X) within 3 days cancel out
            for (int i = 0; i + 1 < found.size(); i++) {
                Txn a = found.get(i), b = found.get(i + 1);
                if (a.amount == b.amount && a.credit != b.credit && Math.abs(b.time - a.time) < 3L * 86400000) {
                    found.remove(i + 1);
                    found.remove(i);
                    i = Math.max(-1, i - 2);
                }
            }
            out.addAll(found);
        }
        return out;
    }

    /**
     * Bank SMS sometimes arrive in the wrong order (two payments a minute apart). If swapping two neighbours
     * makes the balances add up, swap them — so a late SMS doesn't look like missing money.
     */
    static void fixOrder(List<Txn> list) {
        for (int pass = 0; pass < 3; pass++) {
            long bal = -1;
            for (int i = 0; i + 1 < list.size(); i++) {
                Txn a = list.get(i), b = list.get(i + 1);
                if (bal >= 0 && a.balance >= 0 && b.balance >= 0 && b.time - a.time < 6L * 3600000) {
                    boolean fitsNow = bal + a.signed() == a.balance;
                    boolean fitsSwapped = bal + b.signed() == b.balance && b.balance + a.signed() == a.balance;
                    if (!fitsNow && fitsSwapped) { list.set(i, b); list.set(i + 1, a); }
                }
                Txn cur = list.get(i);
                if (cur.balance >= 0) bal = cur.balance;
                else if (bal >= 0 && cur.kind != Txn.SNAPSHOT) bal += cur.signed();
            }
        }
    }

    /** Payments with no account (from app notifications) between from and to whose total is exactly −diff. */
    static List<Txn> explain(List<Txn> all, long diff, long from, long to, java.util.Set<Txn> used) {
        List<Txn> c = new ArrayList<>();
        for (Txn x : all) {
            if (!x.account.isEmpty() || used.contains(x) || x.kind == Txn.IGNORED || x.kind == Txn.SNAPSHOT) continue;
            if ((x.sources & Txn.SRC_APP) == 0) continue;
            if (x.time <= from - 5L * 60000 || x.time > to + 5L * 60000) continue;
            c.add(x);
        }
        if (c.isEmpty()) return null;
        if (c.size() > 16) {
            final long mid = (from + to) / 2;
            Collections.sort(c, (a, b) -> Long.compare(Math.abs(a.time - mid), Math.abs(b.time - mid)));
            c = new ArrayList<>(c.subList(0, 16));
        }
        long need = diff;   // the balance changed by diff more than explained → these payments' signed total = diff
        int n = c.size();
        int best = -1, bestCount = 99;
        for (int mask = 1; mask < (1 << n); mask++) {
            long sum = 0;
            int cnt = 0;
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) { sum += c.get(i).signed(); cnt++; }
            if (sum == need && cnt < bestCount) { best = mask; bestCount = cnt; }
        }
        if (best < 0) return null;
        List<Txn> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if ((best & (1 << i)) != 0) out.add(c.get(i));
        return out;
    }

    /** Two debits of the same amount minutes apart where the balance shows only one → the later one. */
    static Txn duplicateIn(List<Txn> window, long amount) {
        for (int i = 0; i < window.size(); i++) {
            Txn a = window.get(i);
            if (a.credit || a.amount != amount) continue;
            for (int j = i + 1; j < window.size(); j++) {
                Txn b = window.get(j);
                if (b.credit || b.amount != amount || Math.abs(b.time - a.time) > 30L * 60000) continue;
                if (!a.ref.isEmpty() && !b.ref.isEmpty() && !a.ref.equals(b.ref)) continue;
                if (b.userCategory || b.userMerchant || !b.note.isEmpty()) continue;
                return b;
            }
        }
        return null;
    }

    /** Small unexplained amounts are almost always bank charges or interest: label them, don't nag. */
    static void guessSmall(Txn g, long from, long to) {
        java.util.Calendar k = Fmt.cal(to);
        int m = k.get(java.util.Calendar.MONTH), d = k.get(java.util.Calendar.DAY_OF_MONTH);
        boolean quarterEnd = (m == 2 || m == 5 || m == 8 || m == 11) && d >= 20 || (m == 3 || m == 6 || m == 9 || m == 0) && d <= 10;
        if (!g.credit && g.amount <= 30000) {
            g.merchant = "Bank / SMS charges (probably)";
            g.category = Cats.FEES;
            g.review = false;
        } else if (g.credit && g.amount <= 300000 && quarterEnd) {
            g.merchant = "Interest from the bank (probably)";
            g.category = Cats.INCOME;
            g.review = false;
        }
    }

    // ------------------------------------------------------------------ repeating payments

    public static final class Recurring {
        public String key, name, category, account;
        public long amount;        // usual amount
        public long lastAmount, prevAmount;
        public int period;         // days
        public long last, next;
        public int count;
        public String periodName() {
            return period <= 8 ? "Weekly" : period <= 40 ? "Monthly" : period <= 100 ? "Every 3 months" : "Yearly";
        }
    }

    public static List<Recurring> recurring(List<Txn> all, long now) {
        Map<String, List<Txn>> groups = new LinkedHashMap<>();
        for (Txn t : all) {
            if (!t.isSpend() || (t.sources & Txn.SRC_GAP) != 0 || t.time < now - 420L * 86400000) continue;
            String k = Cats.key(t);
            if (k.isEmpty() || Cats.CASH.equals(t.category) || Cats.PEOPLE.equals(t.category) && t.amount < 50000) continue;
            List<Txn> g = groups.get(k);
            if (g == null) groups.put(k, g = new ArrayList<>());
            g.add(t);
        }
        List<Recurring> out = new ArrayList<>();
        for (Map.Entry<String, List<Txn>> e : groups.entrySet()) {
            List<Txn> g = e.getValue();
            if (g.size() < 2) continue;
            Collections.sort(g, (a, b) -> Long.compare(a.time, b.time));
            // one per day at most (two payments the same day are not a "repeat")
            List<Txn> d = new ArrayList<>();
            for (Txn t : g) if (d.isEmpty() || t.time - d.get(d.size() - 1).time > 2L * 86400000) d.add(t);
            if (d.size() < 2) continue;
            List<Long> gapsDays = new ArrayList<>();
            for (int i = 1; i < d.size(); i++) gapsDays.add(Math.round((d.get(i).time - d.get(i - 1).time) / 86400000.0));
            long med = median(gapsDays);
            int[][] windows = {{6, 8}, {26, 35}, {84, 97}, {350, 380}};
            int[] win = null;
            for (int[] w : windows) if (med >= w[0] && med <= w[1]) win = w;
            if (win == null) continue;
            int ok = 0;
            for (long x : gapsDays) if (x >= win[0] - 2 && x <= win[1] + 2) ok++;
            if (ok < gapsDays.size() - (gapsDays.size() >= 4 ? 1 : 0)) continue;
            List<Long> amts = new ArrayList<>();
            for (Txn t : d) amts.add(t.amount);
            long medAmt = median(amts);
            String cat = d.get(d.size() - 1).category;
            boolean variable = Cats.BILLS.equals(cat) || Cats.RECHARGE.equals(cat) || Cats.HOME.equals(cat);
            int close = 0;
            for (long a : amts) if (Math.abs(a - medAmt) <= medAmt * (variable ? 0.5 : 0.2)) close++;
            if (close < amts.size() - (amts.size() >= 4 ? 1 : 0)) continue;
            if (d.size() == 2 && !(Cats.SUBS.equals(cat) || Cats.RECHARGE.equals(cat) || Cats.EMI.equals(cat)
                    || Cats.INSURE.equals(cat) || Cats.BILLS.equals(cat) || Cats.HOME.equals(cat) || Cats.INVEST.equals(cat)
                    || amts.get(0).equals(amts.get(1)))) continue;
            Txn last = d.get(d.size() - 1);
            Recurring r = new Recurring();
            r.key = e.getKey();
            r.name = last.merchant;
            r.category = cat;
            r.account = last.account;
            r.amount = medAmt;
            r.lastAmount = last.amount;
            r.prevAmount = d.get(d.size() - 2).amount;
            r.period = (int) med;
            r.last = last.time;
            r.next = Fmt.addDays(last.time, (int) med);
            if (win[0] == 26) r.next = Fmt.addMonths(last.time, 1);
            r.count = d.size();
            if (r.next < now - Math.max(7, med / 2) * 86400000L) continue;   // stopped
            out.add(r);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.next, b.next));
        return out;
    }

    static long median(List<Long> v) {
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        if (s.isEmpty()) return 0;
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    // ------------------------------------------------------------------ totals

    public static long spent(List<Txn> all, long from, long to) {
        long s = 0;
        for (Txn t : all) if (t.isSpend() && t.time >= from && t.time < to) s += t.amount;
        return s;
    }

    public static long income(List<Txn> all, long from, long to) {
        long s = 0;
        for (Txn t : all) if (t.isIncome() && t.time >= from && t.time < to) s += t.amount;
        return s;
    }

    public static long refunds(List<Txn> all, long from, long to) {
        long s = 0;
        for (Txn t : all) if (t.kind == Txn.REFUND && t.credit && t.time >= from && t.time < to) s += t.amount;
        return s;
    }

    /** category → spent, biggest first */
    public static LinkedHashMap<String, Long> byCategory(List<Txn> all, long from, long to) {
        Map<String, Long> m = new HashMap<>();
        for (Txn t : all) {
            if (!t.isSpend() || t.time < from || t.time >= to) continue;
            String c = t.category.isEmpty() ? Cats.OTHER : t.category;
            Long v = m.get(c);
            m.put(c, (v == null ? 0 : v) + t.amount);
        }
        return sorted(m);
    }

    public static LinkedHashMap<String, Long> byMerchant(List<Txn> all, long from, long to, String category) {
        Map<String, Long> m = new HashMap<>();
        for (Txn t : all) {
            if (!t.isSpend() || t.time < from || t.time >= to) continue;
            if (category != null && !category.equals(t.category)) continue;
            String k = t.merchant.isEmpty() ? "Unknown" : t.merchant;
            Long v = m.get(k);
            m.put(k, (v == null ? 0 : v) + t.amount);
        }
        return sorted(m);
    }

    /** spent per day of the month (index 0 = day 1) */
    public static long[] byDay(List<Txn> all, long monthStart) {
        int n = Fmt.daysInMonth(monthStart);
        long[] d = new long[n];
        long end = Fmt.addMonths(monthStart, 1);
        for (Txn t : all) {
            if (!t.isSpend() || t.time < monthStart || t.time >= end) continue;
            int i = Fmt.cal(t.time).get(java.util.Calendar.DAY_OF_MONTH) - 1;
            if (i >= 0 && i < n) d[i] += t.amount;
        }
        return d;
    }

    static LinkedHashMap<String, Long> sorted(Map<String, Long> m) {
        List<Map.Entry<String, Long>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : l) out.put(e.getKey(), e.getValue());
        return out;
    }

    /**
     * Where the month is heading: spent so far + the usual daily spend for the days left
     * (the usual day = median of the last 60 days, so one big purchase doesn't blow the forecast up).
     */
    public static long forecast(List<Txn> all, long now) {
        long ms = Fmt.startOfMonth(now);
        long spent = spent(all, ms, now + 1);
        int dayOfMonth = Fmt.cal(now).get(java.util.Calendar.DAY_OF_MONTH);
        int left = Fmt.daysInMonth(now) - dayOfMonth;
        List<Long> days = new ArrayList<>();
        long today = Fmt.startOfDay(now);
        for (int i = 1; i <= 60; i++) {
            long s = Fmt.addDays(today, -i);
            days.add(spent(all, s, Fmt.addDays(s, 1)));
        }
        long typical = (median(days) + average(days)) / 2;
        return spent + typical * left;
    }

    static long average(List<Long> v) {
        if (v.isEmpty()) return 0;
        long s = 0;
        for (long x : v) s += x;
        return s / v.size();
    }

    /** A payment much bigger than usual (overall or for that shop). */
    public static String unusual(List<Txn> all, Txn t) {
        if (!t.isSpend() || t.amount < 100000) return null;   // under ₹1,000 is never "unusual"
        List<Long> mine = new ArrayList<>(), general = new ArrayList<>();
        String k = Cats.key(t);
        for (Txn x : all) {
            if (x == t || !x.isSpend() || x.time < t.time - 120L * 86400000 || x.time > t.time) continue;
            general.add(x.amount);
            if (!k.isEmpty() && k.equals(Cats.key(x))) mine.add(x.amount);
        }
        if (mine.size() >= 3 && t.amount > 2.5 * median(mine) && t.amount - median(mine) > 50000)
            return "Much more than you usually pay " + (t.merchant.isEmpty() ? "here" : t.merchant)
                    + " (usually " + Fmt.plain(median(mine)) + ")";
        if (general.size() >= 15 && t.amount >= 500000 && t.amount > 8 * median(general) && t.amount > percentile(general, 0.97))
            return "One of your biggest payments in 4 months";
        return null;
    }

    static long percentile(List<Long> v, double p) {
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        if (s.isEmpty()) return 0;
        return s.get(Math.min(s.size() - 1, (int) Math.floor(p * s.size())));
    }

    /** Same shop, same amount, minutes apart — charged twice? */
    public static Txn possibleDouble(List<Txn> all, Txn t) {
        if (!t.isSpend() || t.merchant.isEmpty()) return null;
        for (Txn x : all) {
            if (x == t || !x.isSpend() || x.amount != t.amount) continue;
            if (!x.merchant.equalsIgnoreCase(t.merchant)) continue;
            if (Math.abs(x.time - t.time) > 10L * 60000) continue;
            if (!x.ref.isEmpty() && x.ref.equals(t.ref)) continue;
            return x;
        }
        return null;
    }
}
