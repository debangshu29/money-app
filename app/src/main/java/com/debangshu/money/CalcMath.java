package com.debangshu.money;

/**
 * Calculator maths (plain Java, tested on the desktop). Everything is in rupees (double), rates are yearly
 * fractions (0.07 = 7%) unless the name says otherwise. These are learning tools with example rates, not advice.
 */
public final class CalcMath {

    private CalcMath() {}

    // ------------------------------------------------------------------ growth

    /** Monthly SIP with a yearly step-up: value at the end of each year (index 0 = start). */
    public static double[] sipYears(double monthly, int years, double rate, double stepUp) {
        double[] out = new double[years + 1];
        double v = 0, m = monthly, r = rate / 12;
        for (int y = 1; y <= years; y++) {
            for (int k = 0; k < 12; k++) v = (v + m) * (1 + r);
            out[y] = v;
            m *= 1 + stepUp;
        }
        return out;
    }

    /** Money put in by a step-up SIP, at the end of each year. */
    public static double[] sipInvested(double monthly, int years, double stepUp) {
        double[] out = new double[years + 1];
        double t = 0, m = monthly;
        for (int y = 1; y <= years; y++) { t += m * 12; out[y] = t; m *= 1 + stepUp; }
        return out;
    }

    /** Same SIP but interest never earns interest (simple interest) — for the snowball switch. */
    public static double[] sipSimple(double monthly, int years, double rate) {
        double[] out = new double[years + 1];
        double principal = 0, interest = 0;
        for (int y = 1; y <= years; y++) {
            for (int k = 0; k < 12; k++) { principal += monthly; interest += principal * rate / 12; }
            out[y] = principal + interest;
        }
        return out;
    }

    public static double[] lumpYears(double amount, int years, double rate) {
        double[] out = new double[years + 1];
        for (int y = 0; y <= years; y++) out[y] = amount * Math.pow(1 + rate, y);
        return out;
    }

    /** Bank FD, interest added every 3 months (how most Indian banks do it). */
    public static double fd(double principal, double rate, double years) { return principal * Math.pow(1 + rate / 4, 4 * years); }

    /** Bank RD: each monthly deposit grows with quarterly compounding until maturity. */
    public static double rd(double monthly, double rate, int months) {
        double v = 0;
        for (int k = 1; k <= months; k++) v += monthly * Math.pow(1 + rate / 4, (months - k + 1) / 3.0);
        return v;
    }

    /** PPF: a yearly deposit at the start of each year, interest once a year. Value at the end of each year. */
    public static double[] ppf(double yearly, int years, double rate) {
        double[] out = new double[years + 1];
        double v = 0;
        for (int y = 1; y <= years; y++) { v = (v + yearly) * (1 + rate); out[y] = v; }
        return out;
    }

    /** NPS at retirement: {corpus, lump sum, monthly pension from the annuity part}. */
    public static double[] nps(double monthly, int years, double rate, double lumpShare, double annuityRate) {
        double corpus = years <= 0 ? 0 : sipYears(monthly, years, rate, 0)[years];
        double lump = corpus * lumpShare, annuity = corpus - lump;
        return new double[]{corpus, lump, annuity * annuityRate / 12};
    }

    /** The real return after tax on the gains and after inflation. */
    public static double realReturn(double rate, double taxSlab, double inflation) {
        double afterTax = rate * (1 - taxSlab);
        return (1 + afterTax) / (1 + inflation) - 1;
    }

    /** Value of a SIP after `years` when a yearly fee is taken from the return. */
    public static double withFee(double monthly, int years, double rate, double fee) {
        return sipYears(monthly, years, rate - fee, 0)[years];
    }

    public static double futurePrice(double today, double years, double inflation) { return today * Math.pow(1 + inflation, years); }

    /** {value at 60 starting now, value at 60 starting after `delay` years} for a monthly SIP. */
    public static double[] costOfWaiting(double monthly, double rate, int age, int delay, int until) {
        int n = Math.max(0, until - age);
        double now = n == 0 ? 0 : sipYears(monthly, n, rate, 0)[n];
        int m = Math.max(0, n - delay);
        double later = m == 0 ? 0 : sipYears(monthly, m, rate, 0)[m];
        return new double[]{now, later};
    }

    // ------------------------------------------------------------------ borrowing

    public static double emi(double principal, double rate, int months) {
        if (months <= 0) return principal;
        double r = rate / 12;
        if (r == 0) return principal / months;
        double f = Math.pow(1 + r, months);
        return principal * r * f / (f - 1);
    }

    /**
     * Paying only the minimum due on a credit card (no new spends): {months to clear, interest paid incl. 18% GST}.
     * monthlyRate e.g. 0.036 (3.6% a month), minShare e.g. 0.05 (5%), minAmount e.g. 200. Gives up after 50 years.
     */
    public static double[] cardTrap(double balance, double monthlyRate, double minShare, double minAmount) {
        double b = balance, paidInterest = 0;
        int m = 0;
        while (b > 0.5 && m < 600) {
            double interest = b * monthlyRate * 1.18;
            b += interest;
            paidInterest += interest;
            double pay = Math.min(b, Math.max(minAmount, b * minShare));
            b -= pay;
            m++;
        }
        return new double[]{m, paidInterest};
    }

    /**
     * "No-cost" EMI: the shop's discount pays the interest, but the bank still charges 18% GST on that interest, plus a
     * processing fee (+18% GST). Returns {total you really pay, interest hidden inside, GST on it, fee with GST}.
     */
    public static double[] noCostEmi(double price, int months, double rate, double fee) {
        double perRupee = emi(1, rate, months) * months;      // ₹ paid back per ₹1 borrowed
        double principal = price / perRupee;                   // what the bank really lends (price − interest)
        double interest = price - principal;
        double gst = interest * 0.18, feeAll = fee * 1.18;
        return new double[]{price + gst + feeAll, interest, gst, feeAll};
    }

    /**
     * Prepay a loan or invest the extra? Both end on the loan's original last month.
     * Returns {months saved by prepaying, interest saved, value if you prepay then invest the freed EMI, value if you just invest the extra}.
     */
    public static double[] prepayOrInvest(double outstanding, double rate, int monthsLeft, double extra, double investRate) {
        double e = emi(outstanding, rate, monthsLeft), r = rate / 12, ri = investRate / 12;
        // A: prepay
        double b = outstanding, interestA = 0, inv = 0;
        int m = 0;
        for (; m < monthsLeft && b > 0.5; m++) {
            double i = b * r;
            interestA += i;
            b = b + i - e - extra;
        }
        int closed = m;
        for (; m < monthsLeft; m++) inv = (inv + e + extra) * (1 + ri);
        // B: invest the extra
        double interestB = e * monthsLeft - outstanding, invB = 0;
        for (int k = 0; k < monthsLeft; k++) invB = (invB + extra) * (1 + ri);
        return new double[]{monthsLeft - closed, interestB - interestA, inv, invB};
    }

    /**
     * Rent or buy, after `years`: {what the buyer owns (house − loan left), what the renter owns (investments)}.
     * The renter invests the down payment + buying costs, and every month whatever buying would have cost more than rent.
     */
    public static double[] rentVsBuy(double price, double downShare, double loanRate, int tenureYears, double rent, double rentRise,
                                     double priceRise, double investRate, int years) {
        double down = price * downShare, loan = price - down, costs = price * 0.07;    // stamp duty + registration ≈ 7%
        double emi = emi(loan, loanRate, tenureYears * 12);
        double maint = price * 0.005 / 12;
        double bal = loan, inv = down + costs, rentNow = rent, r = loanRate / 12, ri = investRate / 12;
        for (int m = 1; m <= years * 12; m++) {
            double owner = (bal > 0 ? emi : 0) + maint;
            if (bal > 0) bal = Math.max(0, bal + bal * r - emi);
            inv = inv * (1 + ri) + (owner - rentNow);
            if (m % 12 == 0) rentNow *= 1 + rentRise;
        }
        double house = price * Math.pow(1 + priceRise, years);
        return new double[]{house - bal, inv};
    }

    // ------------------------------------------------------------------ illustrations (made-up but realistic shapes)

    /** A market index for `months`: ~12% a year, a 30% fall from month `crashAt` over 3 months, back to the old trend 2 years later. */
    public static double[] crashIndex(int months, int crashAt) {
        double[] idx = new double[months + 1];
        double g = Math.pow(1.12, 1 / 12.0);
        for (int m = 0; m <= months; m++) {
            double trend = Math.pow(g, m);
            double f = 1;
            if (m >= crashAt && m < crashAt + 3) f = 1 - 0.30 * (m - crashAt + 1) / 3.0;
            else if (m >= crashAt + 3 && m < crashAt + 27) f = 0.70 + 0.30 * (m - crashAt - 2) / 24.0;
            idx[m] = trend * f;
        }
        return idx;
    }

    /**
     * SIP in the crash index — everyone sets aside the same money every month:
     * kind 0 = kept investing; 1 = panicked, sold everything at the bottom and kept saving in cash;
     * 2 = paused the SIP at the bottom (kept what they had, saved in cash) and restarted once the market was back.
     */
    public static double[] crashSip(double monthly, int months, int crashAt, int kind) {
        double[] idx = crashIndex(months, crashAt), out = new double[months + 1];
        double units = 0, cash = 0;
        int bottom = crashAt + 2, back = crashAt + 27;
        for (int m = 0; m <= months; m++) {
            if (m > 0) {
                boolean invest = kind == 0 || m <= bottom || (kind == 2 && m >= back);
                if (invest) units += monthly / idx[m]; else cash += monthly;
            }
            if (kind == 1 && m == bottom) { cash += units * idx[m]; units = 0; }
            out[m] = units * idx[m] + cash;
        }
        return out;
    }

    /** Yearly returns for the race's bumpy runners (made-up but realistic: average ≈ the long-run figure). */
    public static final double[] EQUITY_YEARS = {0.24, -0.09, 0.16, 0.31, 0.04, -0.21, 0.38, 0.12, 0.08, 0.19, -0.04, 0.27, 0.10, 0.15, -0.12, 0.22, 0.09, 0.18, 0.06, 0.14};
    public static final double[] GOLD_YEARS = {0.12, 0.21, -0.05, 0.08, 0.25, -0.03, 0.07, 0.18, 0.02, 0.11, 0.15, -0.08, 0.20, 0.06, 0.09, 0.13, -0.02, 0.17, 0.05, 0.10};

    /** Value of a lump sum over `years` following yearly returns (repeating the list if needed). */
    public static double[] path(double amount, int years, double[] yearly) {
        double[] out = new double[years + 1];
        out[0] = amount;
        for (int y = 1; y <= years; y++) out[y] = out[y - 1] * (1 + yearly[(y - 1) % yearly.length]);
        return out;
    }
}
