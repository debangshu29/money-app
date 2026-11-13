package com.debangshu.money;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * "Save more" maths (plain Java, tested on the desktop): 50/30/20 buckets, money health score, runway,
 * month-end forecast, how money grows (SIP / lump sum), freedom number, true price of a habit, round-ups,
 * hours of work, salary detection. All amounts are paise unless said otherwise.
 */
public final class SaveMath {

    private SaveMath() {}

    // ------------------------------------------------------------------ 50 / 30 / 20

    public static final int NEEDS = 0, WANTS = 1, SAVED = 2, SKIP = 3;

    /** name, needs %, wants %, save %, one line. "Pay yourself first" keeps needs+wants together (wants = -1). */
    public static final Object[][] RULES = {
            {"50/30/20", 50, 30, 20, "Half on needs, a third on wants, a fifth saved — the classic"},
            {"60/20/20", 60, 20, 20, "For big-city rent: more room for needs, still 20% saved"},
            {"70/20/10", 70, 20, 10, "For tight months or a first job: start with 10% saved"},
            {"Pay yourself first", 80, -1, 20, "Save 20% the day money comes in — spend the rest freely"},
    };

    /** Which bucket a category goes in, unless you moved it. */
    public static int bucket(String cat, Map<String, Integer> moved) {
        if (cat == null || cat.isEmpty()) cat = Cats.OTHER;
        if (moved != null && moved.containsKey(cat)) return moved.get(cat);
        switch (cat) {
            case Cats.GROCERY: case Cats.TRANSPORT: case Cats.FUEL: case Cats.BILLS: case Cats.RECHARGE: case Cats.HEALTH:
            case Cats.EDU: case Cats.HOME: case Cats.EMI: case Cats.INSURE: case Cats.FEES:
                return NEEDS;
            case Cats.INVEST:
                return SAVED;
            case Cats.TRANSFER: case Cats.CARDBILL: case Cats.INCOME: case Cats.SALARY: case Cats.REFUND:
                return SKIP;
            default:
                return WANTS;     // food, shopping, fun, subscriptions, travel, personal care, gifts, cash, people, other
        }
    }

    /** {needs, wants, saved} as whole percents of income (saved = income − needs − wants, never below 0). */
    public static int[] percents(long income, long needs, long wants) {
        if (income <= 0) return new int[]{0, 0, 0};
        long saved = Math.max(0, income - needs - wants);
        return new int[]{(int) Math.round(needs * 100.0 / income), (int) Math.round(wants * 100.0 / income), (int) Math.round(saved * 100.0 / income)};
    }

    // ------------------------------------------------------------------ money health (0–100, five parts of 20)

    /** Saving rate: 20% or more of income saved = full marks. */
    public static int scoreSaving(double rate) { return clamp((int) Math.round(Math.max(0, rate) / 0.20 * 20)); }

    /** Emergency fund: 6 months of spending = full marks. */
    public static int scoreRunway(double months) { return clamp((int) Math.round(Math.max(0, months) / 6.0 * 20)); }

    /** EMIs: 10% of income or less = full marks, 40% or more = none. */
    public static int scoreEmi(double share) {
        if (share <= 0.10) return 20;
        if (share >= 0.40) return 0;
        return clamp((int) Math.round((0.40 - share) / 0.30 * 20));
    }

    /** Budget: kept = 20, a little over = 12, well over = 4, no budget = 8. */
    public static int scoreBudget(int state) { return state == 0 ? 20 : state == 1 ? 12 : state == 2 ? 4 : 8; }

    /** Goals: on track = 20, behind = 10, none = 6. */
    public static int scoreGoals(int onTrack, int behind) {
        if (onTrack + behind == 0) return 6;
        return clamp((int) Math.round((onTrack * 20 + behind * 10) / (double) (onTrack + behind)));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(20, v)); }

    public static String grade(int score) {
        return score >= 80 ? "Excellent" : score >= 65 ? "Good" : score >= 45 ? "Getting there" : "Needs care";
    }

    /** How many months your money lasts if income stopped (savings ÷ monthly spending). */
    public static double runway(long savings, long monthlySpend) {
        if (monthlySpend <= 0) return savings > 0 ? 99 : 0;
        return Math.max(0, savings) / (double) monthlySpend;
    }

    // ------------------------------------------------------------------ month-end forecast

    /**
     * What's left at month end: balance now − the usual daily spending for the days left − bills still due
     * + income you usually get that hasn't come yet.
     */
    public static long forecast(long balance, long dailySpend, int daysLeft, long billsDue, long incomeToCome) {
        return balance - dailySpend * Math.max(0, daysLeft) - billsDue + incomeToCome;
    }

    // ------------------------------------------------------------------ growth (rupees, doubles)

    /** Value after `months` of investing `monthly` at the start of each month, at `yearlyRate` (0.12 = 12%). */
    public static double sip(double monthly, int months, double yearlyRate) {
        double r = yearlyRate / 12, v = 0;
        for (int i = 0; i < months; i++) v = (v + monthly) * (1 + r);
        return v;
    }

    public static double lump(double amount, double years, double yearlyRate) { return amount * Math.pow(1 + yearlyRate, years); }

    /** Freedom number: 25 years of your yearly spending (the "4% rule"). */
    public static double freedomNumber(double monthlySpend) { return monthlySpend * 12 * 25; }

    /** Years until savings reach `target` (today's rupees) with a real (after-inflation) return; -1 if never within 70 years. */
    public static double yearsTo(double now, double monthly, double realRate, double target) {
        if (now >= target) return 0;
        double v = now, r = realRate / 12;
        for (int m = 1; m <= 840; m++) {
            v = (v + monthly) * (1 + r);
            if (v >= target) return m / 12.0;
        }
        return -1;
    }

    /** True price of a habit: {per month, per year, in N years if invested at rate} for cutting `cutPerMonth` times of `each`. */
    public static double[] habit(double each, double cutPerMonth, int years, double rate) {
        double m = each * cutPerMonth;
        return new double[]{m, m * 12, sip(m, years * 12, rate)};
    }

    // ------------------------------------------------------------------ round-up jar, hours of work

    /** Round a payment up to the next step (₹10, ₹50, ₹100) — the difference goes in the jar. Paise. */
    public static long roundUp(long paise, long stepPaise) {
        if (stepPaise <= 0) return 0;
        long r = paise % stepPaise;
        return r == 0 ? 0 : stepPaise - r;
    }

    /** Hours of work a payment costs, given monthly income and working hours a week. */
    public static double hours(long paise, long monthlyIncome, double hoursPerWeek) {
        if (monthlyIncome <= 0 || hoursPerWeek <= 0) return -1;
        double perHour = monthlyIncome / (hoursPerWeek * 52 / 12.0);
        return paise / perHour;
    }

    public static String hoursText(double h) {
        if (h < 0) return "";
        if (h < 1) return Math.max(1, Math.round(h * 60)) + " min of work";
        if (h < 10) return String.format(Locale.ROOT, "%.1f", h).replace(".0", "") + " hours of work";
        double days = h / 8;
        if (h < 40) return Math.round(h) + " hours of work";
        return String.format(Locale.ROOT, "%.1f", days).replace(".0", "") + " working days";
    }

    // ------------------------------------------------------------------ salary, delivery, small spends

    private static final Pattern SALARY = Pattern.compile("\\bsalary\\b|\\bsal\\b|\\bsal(?:ary)? cr|payroll|\\bpay ?out\\b.*employer|stipend", Pattern.CASE_INSENSITIVE);
    static final Pattern DELIVERY = Pattern.compile("swiggy|zomato|eatsure|domino|pizza hut|faasos|box8|behrouz|magicpin|zepto cafe|blinkit|zepto|instamart|bigbasket now", Pattern.CASE_INSENSITIVE);

    /** A credit that looks like salary: says so, or is at least 60% of your usual monthly income (and over ₹5,000). */
    public static boolean isSalary(String text, long amount, long usualMonthlyIncome) {
        if (text != null && SALARY.matcher(text).find()) return true;
        return usualMonthlyIncome > 0 && amount >= 500000 && amount >= usualMonthlyIncome * 0.6;
    }

    public static boolean isDelivery(String text) { return text != null && DELIVERY.matcher(text).find(); }
}
