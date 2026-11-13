package com.debangshu.money;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The numbers the "Save more" tools share: income, savings, 50/30/20 buckets, health, forecast, jar, wait-list. */
final class MoneyFacts {

    private MoneyFacts() {}

    // ------------------------------------------------------------------ income & savings

    /** Your monthly income: what you set, else the average money in over the last 3 full months. */
    static long income(Context c) {
        long set = Store.get(c).prefs().getLong("myIncome", 0);
        if (set > 0) return set;
        return detectedIncome(c);
    }

    static long detectedIncome(Context c) {
        List<Txn> all = Store.get(c).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long sum = 0;
        int n = 0;
        for (int k = 1; k <= 3; k++) {
            long in = Ledger.income(all, Fmt.addMonths(ms, -k), Fmt.addMonths(ms, -k + 1));
            if (in <= 0) continue;
            sum += in;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Money you have right now: bank balances (not cards, not hidden) + the cash wallet. */
    static long savings(Context c) {
        Store s = Store.get(c);
        long t = 0;
        for (Account a : s.accounts()) if (!a.card && !a.hidden && a.balance >= 0) t += Math.max(0, s.liveBalance(a));
        if (Cash.on(c)) t += Math.max(0, Cash.balance(c));
        return t;
    }

    static long emiMonthly(Context c) {
        List<Txn> all = Store.get(c).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long sum = 0;
        for (int k = 1; k <= 3; k++) {
            Long v = Ledger.byCategory(all, Fmt.addMonths(ms, -k), Fmt.addMonths(ms, -k + 1)).get(Cats.EMI);
            if (v != null) sum += v;
        }
        return sum / 3;
    }

    // ------------------------------------------------------------------ 50 / 30 / 20

    static int rule(Context c) { return Store.get(c).prefs().getInt("rule", 0); }

    static void setRule(Context c, int i) { Store.get(c).prefs().edit().putInt("rule", i).apply(); }

    static Map<String, Integer> moved(Context c) {
        JSONObject o = Store.get(c).obj("ruleMoved");
        Map<String, Integer> m = new HashMap<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, o.optInt(k)); }
        return m;
    }

    static void move(Context c, String cat, int bucket) {
        JSONObject o = Store.get(c).obj("ruleMoved");
        try { o.put(cat, bucket); } catch (Exception ignored) { }
        Store.get(c).putObj("ruleMoved", o);
    }

    /** Spending between two times split into buckets: {needs, wants, invested}, plus each category's amount and bucket. */
    static long[] buckets(Context c, long from, long to, LinkedHashMap<String, Long> cats) {
        Map<String, Integer> mv = moved(c);
        long[] b = new long[3];
        LinkedHashMap<String, Long> by = Ledger.byCategory(Store.get(c).txns(), from, to);
        for (Map.Entry<String, Long> e : by.entrySet()) {
            int k = SaveMath.bucket(e.getKey(), mv);
            if (k == SaveMath.SKIP) continue;
            b[k] += e.getValue();
            if (cats != null) cats.put(e.getKey(), e.getValue());
        }
        return b;
    }

    // ------------------------------------------------------------------ health

    /** {saving, runway, emi, budget, goals} each 0–20. */
    static int[] health(Context c, double[] facts) {
        long inc = income(c), spend = Goals.monthlySpend(c), save = Goals.monthlySaving(c);
        double rate = inc > 0 ? save / (double) inc : 0;
        double run = SaveMath.runway(savings(c), spend);
        double emi = inc > 0 ? emiMonthly(c) / (double) inc : 0;
        int budget = budgetState(c);
        int on = 0, behind = 0;
        for (JSONObject g : Goals.active(c)) {
            GoalMath.Status s = Goals.status(c, g);
            if (s.kind == GoalMath.AHEAD || s.kind == GoalMath.ON_TRACK || s.kind == GoalMath.NO_DATE) on++;
            else if (s.kind == GoalMath.BEHIND || s.kind == GoalMath.NOT_MOVING) behind++;
        }
        if (facts != null && facts.length >= 6) { facts[0] = rate; facts[1] = run; facts[2] = emi; facts[3] = budget; facts[4] = on; facts[5] = behind; }
        return new int[]{SaveMath.scoreSaving(rate), SaveMath.scoreRunway(run), SaveMath.scoreEmi(emi), SaveMath.scoreBudget(budget), SaveMath.scoreGoals(on, behind)};
    }

    /** 0 kept, 1 a little over pace, 2 well over, 3 no budget. */
    static int budgetState(Context c) {
        Plan p = Store.get(c).plan();
        long now = System.currentTimeMillis();
        if (p == null || p.finished(now) || !p.started(now) || p.amount <= 0) return 3;
        double done = Math.max(0.05, Math.min(1, (now - p.start) / (double) Math.max(1, p.end - p.start)));
        double projected = p.spent(Store.get(c).txns()) / done;
        return projected <= p.amount ? 0 : projected <= p.amount * 1.15 ? 1 : 2;
    }

    // ------------------------------------------------------------------ month-end forecast

    /** {left at month end, balance now, usual daily spend, days left, bills still due}. */
    static long[] forecast(Context c) {
        List<Txn> all = Store.get(c).txns();
        long now = System.currentTimeMillis();
        long monthEnd = Fmt.addMonths(Fmt.startOfMonth(now), 1);
        int daysLeft = (int) Math.max(0, Math.ceil((monthEnd - now) / (double) GoalMath.DAY) - 1);
        List<Ledger.Recurring> rec = Ledger.recurring(all, now);
        long billsDue = 0, billsPaid = 0;
        for (Ledger.Recurring r : rec) {
            if (r.next > now && r.next < monthEnd) billsDue += r.amount;
            if (r.last >= now - 30 * GoalMath.DAY) billsPaid += r.lastAmount > 0 ? r.lastAmount : r.amount;
        }
        long spent30 = Ledger.spent(all, now - 30 * GoalMath.DAY, now);
        long daily = Math.max(0, (spent30 - billsPaid) / 30);
        long bal = savings(c);
        return new long[]{SaveMath.forecast(bal, daily, daysLeft, billsDue, 0), bal, daily, daysLeft, billsDue};
    }

    // ------------------------------------------------------------------ round-up jar

    static JSONObject jar(Context c) { return Store.get(c).obj("jar"); }

    /** What's in the jar: round-ups of every payment since you started it, minus what you moved out. */
    static long jarAmount(Context c) {
        JSONObject j = jar(c);
        if (!j.optBoolean("on")) return 0;
        long step = j.optLong("step", 1000), since = j.optLong("since");
        long sum = 0;
        for (Txn t : Store.get(c).txns()) {
            if (!t.isSpend() || t.time < since) continue;
            if (Cats.EMI.equals(t.category) || Cats.HOME.equals(t.category) || Cats.INVEST.equals(t.category)) continue;
            sum += SaveMath.roundUp(t.amount, step);
        }
        return Math.max(0, sum - j.optLong("moved"));
    }

    // ------------------------------------------------------------------ hours of work

    static double hoursPerWeek(Context c) { return Store.get(c).prefs().getFloat("hoursWeek", 48f); }

    static boolean showHours(Context c) { return Store.get(c).prefs().getBoolean("showHours", false); }

    /** "≈ 3 hours of work" for a payment, or "" if it's off or income isn't known. */
    static String hoursFor(Context c, long paise) {
        if (!showHours(c)) return "";
        return SaveMath.hoursText(SaveMath.hours(paise, income(c), hoursPerWeek(c)));
    }

    // ------------------------------------------------------------------ goal impact on a big payment

    /** "= 13% of your Laptop goal" for a big payment (₹2,000+), using the goal it hurts most. */
    static String goalImpact(Context c, long paise) {
        if (paise < 200000) return "";
        JSONObject best = null;
        long bestTarget = 0;
        for (JSONObject g : Goals.active(c)) {
            long t = g.optLong("target");
            if (best == null || t < bestTarget) { best = g; bestTarget = t; }
        }
        if (best == null || bestTarget <= 0) return "";
        long pct = Math.round(paise * 100.0 / bestTarget);
        if (pct < 1) return "";
        return "= " + pct + "% of your " + best.optString("name") + " goal";
    }

    // ------------------------------------------------------------------ wait-list

    static final String WAIT = "waitlist";

    static JSONArray waitList(Context c) { return Store.get(c).list(WAIT); }

    /** Background: tell you when an item's waiting time is over. */
    static void checkWaitList(Context c) {
        JSONArray a = waitList(c);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject w = a.optJSONObject(i);
            if (!"wait".equals(w.optString("state", "wait")) || w.optBoolean("told")) continue;
            if (now < w.optLong("added") + w.optInt("days") * GoalMath.DAY) continue;
            try { w.put("told", true); } catch (Exception ignored) { }
            changed = true;
            Alerts.postIntent(c, Alerts.CH_ALERTS, 33000 + (w.optString("id").hashCode() & 0x3FF),
                    "Still want " + w.optString("name") + "?",
                    w.optInt("days") + " days are up. If you still want it, buy it happily. If not — " + Fmt.rs(w.optLong("price")) + " saved.",
                    new Intent(c, WaitListActivity.class));
        }
        if (changed) Store.get(c).putList(WAIT, a);
    }

    // ------------------------------------------------------------------ salary day

    /** A salary came in (live): offer to split it, once a month. */
    static void maybeSalary(Context c, Txn t) {
        if (!t.credit || t.kind != Txn.NORMAL || System.currentTimeMillis() - t.time > 6 * 3600000L) return;
        if (!SaveMath.isSalary(t.raw + " " + t.merchant + " " + t.category, t.amount, detectedIncome(c)) && !Cats.SALARY.equals(t.category)) return;
        String key = "salarySplit_" + Fmt.cal(t.time).get(java.util.Calendar.YEAR) + "_" + Fmt.cal(t.time).get(java.util.Calendar.MONTH);
        if (Store.get(c).prefs().getBoolean(key, false)) return;
        Store.get(c).prefs().edit().putBoolean(key, true).apply();
        Alerts.postIntent(c, Alerts.CH_ALERTS, 34000, "Salary " + Fmt.rs(t.amount) + " came in 🎉",
                "Split it now in one tap — goals first, then needs and wants. That's how saving becomes automatic.",
                new Intent(c, SalarySplitActivity.class).putExtra("amount", t.amount));
    }
}
