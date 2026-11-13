package com.debangshu.money;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 20 calculators and play tools in Grow. Each one is: a few inputs (sliders, switches, choices) and a function that
 * turns them into big numbers, a graph, bars and a few simple lines. CalcActivity draws them all the same way.
 * Percentages are stored in tenths (71 = 7.1%). Money inputs are whole rupees.
 */
final class Calcs {

    private Calcs() {}

    static final int RS = 0, PCT = 1, YEARS = 2, MONTHS = 3, AGE = 4, SWITCH = 5, CHOICE = 6;

    /** One input. */
    static final class P {
        final String key, label;
        final int kind;
        final long min, max, step, def;
        final String[] choices;     // CHOICE: labels; value = index
        P(String key, String label, int kind, long min, long max, long step, long def) { this(key, label, kind, min, max, step, def, null); }
        P(String key, String label, int kind, long min, long max, long step, long def, String[] choices) {
            this.key = key; this.label = label; this.kind = kind; this.min = min; this.max = max; this.step = step; this.def = def; this.choices = choices;
        }
        String fmt(long v) {
            switch (kind) {
                case RS: return Fmt.rs(v * 100);
                case PCT: return (v % 10 == 0 ? String.valueOf(v / 10) : (v / 10) + "." + (v % 10)) + "%";
                case YEARS: return v + (v == 1 ? " year" : " years");
                case MONTHS: return v + (v == 1 ? " month" : " months");
                case AGE: return String.valueOf(v);
                case CHOICE: return choices[(int) v];
                default: return v == 1 ? "On" : "Off";
            }
        }
    }

    static final class Bar {
        final String label, value, sub;
        final float frac;
        final int color;
        Bar(String label, String value, String sub, float frac, int color) { this.label = label; this.value = value; this.sub = sub; this.frac = frac; this.color = color; }
    }

    /** What a calculator shows. */
    static final class Out {
        String headLabel = "", head = "";
        int headColor;
        final List<String[]> tiles = new ArrayList<>();      // {label, value}
        final List<Bar> bars = new ArrayList<>();
        float[][] series;
        int[] colors;
        boolean[] dashed, filled;
        String[] x;
        String[] legend;
        float target = -1;
        final List<String> lines = new ArrayList<>();       // "what this means", simply
        boolean race;                                        // animate the graph
        String note;                                         // small grey line under everything
    }

    interface Fn { Out run(Context c, Map<String, Long> v); }

    static final class Calc {
        final String id, emoji, title, sub;
        final int color, group;
        final P[] params;
        final Fn fn;
        Calc(String id, String emoji, int color, int group, String title, String sub, P[] params, Fn fn) {
            this.id = id; this.emoji = emoji; this.color = color; this.group = group; this.title = title; this.sub = sub; this.params = params; this.fn = fn;
        }
    }

    static final String[] GROUPS = {"Grow your money", "See how money works", "Loans & big buys"};

    // ------------------------------------------------------------------ helpers

    static final int GREEN = 0xFF0E9F6E, BLUE = 0xFF3B82F6, AMBER = 0xFFF59E0B, RED = 0xFFEF4444, PURPLE = 0xFF8B5CF6, GREY = 0xFF94A3B8, TEAL = 0xFF0EA5E9, PINK = 0xFFEC4899;

    static String rs(double rupees) { return Fmt.rs(Math.round(rupees) * 100); }

    static String big(double rupees) { return Charts.Line.money((float) rupees); }

    static double pct(Map<String, Long> v, String k) { return v.get(k) / 1000.0; }

    static float[] f(double[] d) { float[] o = new float[d.length]; for (int i = 0; i < d.length; i++) o[i] = (float) d[i]; return o; }

    static String[] yearsX(int n) { String[] x = new String[n + 1]; for (int i = 0; i <= n; i++) x[i] = i == 0 ? "Now" : i + " yr"; return x; }

    static void chart(Out o, String[] legend, int[] colors, boolean[] dashed, boolean[] filled, String[] x, double[]... s) {
        o.series = new float[s.length][];
        for (int i = 0; i < s.length; i++) o.series[i] = f(s[i]);
        o.colors = colors; o.dashed = dashed; o.filled = filled; o.x = x; o.legend = legend;
    }

    static final String EXAMPLE = "Rates are examples, not promises — for learning, not financial advice.";

    // ------------------------------------------------------------------ the tools

    static final Calc[] ALL = {
            // ===== Grow your money
            new Calc("sip", "🌱", GREEN, 0, "SIP calculator", "A little every month, for years", new P[]{
                    new P("m", "Every month", RS, 500, 100000, 500, 5000),
                    new P("y", "For", YEARS, 1, 40, 1, 15),
                    new P("r", "Yearly return (example)", PCT, 40, 150, 5, 120),
                    new P("s", "Increase it every year by", PCT, 0, 200, 10, 0)}, (c, v) -> {
                int y = v.get("y").intValue();
                double[] val = CalcMath.sipYears(v.get("m"), y, pct(v, "r"), pct(v, "s")), inv = CalcMath.sipInvested(v.get("m"), y, pct(v, "s"));
                Out o = new Out();
                o.headLabel = "After " + y + " years you'd have";
                o.head = rs(val[y]);
                o.headColor = GREEN;
                o.tiles.add(new String[]{"You put in", rs(inv[y])});
                o.tiles.add(new String[]{"Growth", rs(val[y] - inv[y])});
                o.tiles.add(new String[]{"Grew", String.format(java.util.Locale.ROOT, "%.1f×", val[y] / Math.max(1, inv[y]))});
                chart(o, new String[]{"Your money", "What you put in"}, new int[]{GREEN, GREY}, new boolean[]{false, true}, new boolean[]{true, false}, yearsX(y), val, inv);
                o.lines.add("🌱 Of every ₹100 you'd have, ₹" + Math.round((val[y] - inv[y]) * 100 / Math.max(1, val[y])) + " came from growth — money your money made.");
                if (v.get("s") > 0) o.lines.add("📈 Increasing it a little every year (like when your salary grows) makes a big difference at the end.");
                else o.lines.add("💡 Try “Increase it every year” — even 10% a year, like a salary hike, changes the end a lot.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("lump", "💰", GREEN, 0, "One-time investment", "Put money once, let it grow", new P[]{
                    new P("a", "Amount", RS, 1000, 5000000, 1000, 100000),
                    new P("y", "For", YEARS, 1, 40, 1, 10),
                    new P("r", "Yearly return (example)", PCT, 30, 150, 5, 100)}, (c, v) -> {
                int y = v.get("y").intValue();
                double[] val = CalcMath.lumpYears(v.get("a"), y, pct(v, "r"));
                Out o = new Out();
                o.headLabel = "After " + y + " years";
                o.head = rs(val[y]);
                o.headColor = GREEN;
                o.tiles.add(new String[]{"You put in", rs(v.get("a"))});
                o.tiles.add(new String[]{"Growth", rs(val[y] - v.get("a"))});
                double dbl = 72.0 / Math.max(0.1, pct(v, "r") * 100);
                o.tiles.add(new String[]{"Doubles every", String.format(java.util.Locale.ROOT, "%.0f yrs", dbl)});
                double[] flat = new double[y + 1];
                java.util.Arrays.fill(flat, v.get("a"));
                chart(o, new String[]{"Your money", "What you put in"}, new int[]{GREEN, GREY}, new boolean[]{false, true}, new boolean[]{true, false}, yearsX(y), val, flat);
                o.lines.add("✌️ Rule of 72: divide 72 by the return to see how many years it takes to double. At " + v.get("r") / 10 + "%, about " + Math.round(dbl) + " years.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("fd", "🏦", BLUE, 0, "FD calculator", "Fixed deposit in a bank", new P[]{
                    new P("a", "Amount", RS, 1000, 5000000, 1000, 100000),
                    new P("y", "For", YEARS, 1, 10, 1, 3),
                    new P("r", "Bank's rate", PCT, 30, 90, 5, 70),
                    new P("t", "Your tax slab", CHOICE, 0, 4, 1, 0, new String[]{"No tax", "5%", "10%", "20%", "30%"})}, (c, v) -> {
                int y = v.get("y").intValue();
                double a = v.get("a"), m = CalcMath.fd(a, pct(v, "r"), y);
                double tax = new double[]{0, .05, .10, .20, .30}[v.get("t").intValue()] * (m - a);
                Out o = new Out();
                o.headLabel = "At the end of " + y + (y == 1 ? " year" : " years");
                o.head = rs(m);
                o.headColor = BLUE;
                o.tiles.add(new String[]{"Interest", rs(m - a)});
                o.tiles.add(new String[]{"Tax on it", rs(tax)});
                o.tiles.add(new String[]{"In hand", rs(m - tax)});
                double[] val = new double[y + 1];
                for (int i = 0; i <= y; i++) val[i] = CalcMath.fd(a, pct(v, "r"), i);
                chart(o, new String[]{"FD value"}, new int[]{BLUE}, new boolean[]{false}, new boolean[]{true}, yearsX(y), val);
                o.lines.add("🏦 The bank adds interest every 3 months, and that interest earns more interest next time.");
                o.lines.add("🧾 FD interest is added to your income and taxed at your slab — that's why “in hand” is less.");
                o.note = "Rates differ by bank and change often. " + EXAMPLE;
                return o;
            }),
            new Calc("rd", "📅", BLUE, 0, "RD calculator", "Same amount into the bank every month", new P[]{
                    new P("m", "Every month", RS, 100, 100000, 100, 3000),
                    new P("n", "For", MONTHS, 6, 120, 6, 24),
                    new P("r", "Bank's rate", PCT, 30, 90, 5, 68)}, (c, v) -> {
                int n = v.get("n").intValue();
                double m = CalcMath.rd(v.get("m"), pct(v, "r"), n), put = v.get("m") * n;
                Out o = new Out();
                o.headLabel = "After " + n + " months you get";
                o.head = rs(m);
                o.headColor = BLUE;
                o.tiles.add(new String[]{"You put in", rs(put)});
                o.tiles.add(new String[]{"Interest", rs(m - put)});
                int yrs = Math.max(1, (n + 11) / 12);
                double[] val = new double[yrs + 1], inv = new double[yrs + 1];
                for (int i = 1; i <= yrs; i++) { int mm = Math.min(n, i * 12); val[i] = CalcMath.rd(v.get("m"), pct(v, "r"), mm) * 1; inv[i] = v.get("m") * mm; }
                chart(o, new String[]{"RD value", "What you put in"}, new int[]{BLUE, GREY}, new boolean[]{false, true}, new boolean[]{true, false}, yearsX(yrs), val, inv);
                o.lines.add("🐷 An RD is a piggy bank at the bank: you drop the same amount every month, and the bank adds interest.");
                o.lines.add("✅ Great for short goals (6 months to 2 years) like a phone, a trip or Puja shopping.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("ppf", "🛡️", PURPLE, 0, "PPF calculator", "Safe, tax-free, 15 years", new P[]{
                    new P("a", "Every year", RS, 500, 150000, 500, 50000),
                    new P("y", "For", CHOICE, 0, 3, 1, 0, new String[]{"15 years", "20 years", "25 years", "30 years"}),
                    new P("r", "PPF rate", PCT, 60, 90, 1, 71)}, (c, v) -> {
                int y = 15 + v.get("y").intValue() * 5;
                double[] val = CalcMath.ppf(v.get("a"), y, pct(v, "r"));
                double put = v.get("a") * y;
                double[] inv = new double[y + 1];
                for (int i = 1; i <= y; i++) inv[i] = v.get("a") * i;
                Out o = new Out();
                o.headLabel = "After " + y + " years — tax-free";
                o.head = rs(val[y]);
                o.headColor = PURPLE;
                o.tiles.add(new String[]{"You put in", rs(put)});
                o.tiles.add(new String[]{"Interest", rs(val[y] - put)});
                chart(o, new String[]{"PPF value", "What you put in"}, new int[]{PURPLE, GREY}, new boolean[]{false, true}, new boolean[]{true, false}, yearsX(y), val, inv);
                o.lines.add("🛡️ PPF is a government savings box: very safe, and the interest is not taxed. You can put ₹500 to ₹1.5 lakh a year.");
                o.lines.add("🔒 The catch: the money is locked for 15 years (some can be taken out from year 7). After 15 years you can extend it in 5-year steps.");
                o.note = "PPF rate is 7.1% for Oct–Dec 2026; the government reviews it every 3 months. For learning, not advice.";
                return o;
            }),
            new Calc("nps", "🧓", PURPLE, 0, "NPS calculator", "Your own pension", new P[]{
                    new P("m", "Every month", RS, 500, 100000, 500, 5000),
                    new P("age", "Your age", AGE, 18, 59, 1, 25),
                    new P("r", "Yearly return (example)", PCT, 60, 140, 5, 100),
                    new P("l", "Take as lump sum at 60", PCT, 0, 800, 50, 800),
                    new P("a", "Pension rate (annuity)", PCT, 40, 80, 5, 60)}, (c, v) -> {
                int years = 60 - v.get("age").intValue();
                double[] n = CalcMath.nps(v.get("m"), years, pct(v, "r"), pct(v, "l"), pct(v, "a"));
                Out o = new Out();
                o.headLabel = "Pension every month from 60";
                o.head = rs(n[2]);
                o.headColor = PURPLE;
                o.tiles.add(new String[]{"Saved by 60", big(n[0])});
                o.tiles.add(new String[]{"Lump sum", big(n[1])});
                o.tiles.add(new String[]{"You put in", big(v.get("m") * 12.0 * years)});
                double[] val = CalcMath.sipYears(v.get("m"), years, pct(v, "r"), 0);
                String[] x = new String[years + 1];
                for (int i = 0; i <= years; i++) x[i] = "Age " + (v.get("age") + i);
                chart(o, new String[]{"NPS money"}, new int[]{PURPLE}, new boolean[]{false}, new boolean[]{true}, x, val);
                o.lines.add("🧓 NPS is a pension box: you fill it while you work; at 60 you take most of it out, and the rest buys a monthly pension for life.");
                o.lines.add("📜 New rules (Dec 2025): if you're not a government employee, you can take up to 80% at 60 — at least 20% must buy the pension. Leaving before 60 means 80% must buy a pension.");
                o.note = "Rules and tax benefits change — check before you decide. " + EXAMPLE;
                return o;
            }),
            new Calc("gold", "🪙", AMBER, 0, "Gold calculator", "Coins, jewellery or gold funds", new P[]{
                    new P("a", "Amount", RS, 1000, 2000000, 1000, 100000),
                    new P("y", "For", YEARS, 1, 20, 1, 10),
                    new P("r", "Gold's yearly rise (example)", PCT, 40, 150, 5, 100),
                    new P("j", "Buy it as jewellery", SWITCH, 0, 1, 1, 0)}, (c, v) -> {
                int y = v.get("y").intValue();
                double a = v.get("a");
                double start = v.get("j") == 1 ? a * 0.87 : a;     // ~10% making charges + 3% GST don't come back
                double[] val = CalcMath.lumpYears(start, y, pct(v, "r"));
                Out o = new Out();
                o.headLabel = "Gold worth after " + y + " years";
                o.head = rs(val[y]);
                o.headColor = AMBER;
                o.tiles.add(new String[]{"You paid", rs(a)});
                o.tiles.add(new String[]{v.get("j") == 1 ? "Lost on day 1" : "Gain", v.get("j") == 1 ? rs(a - start) : rs(val[y] - a)});
                chart(o, new String[]{"Gold value"}, new int[]{AMBER}, new boolean[]{false}, new boolean[]{true}, yearsX(y), val);
                if (v.get("j") == 1) o.lines.add("💍 Jewellery is lovely to wear, but making charges (often 8–25%) and GST don't come back when you sell — the shop pays only for the gold.");
                else o.lines.add("🪙 For saving, gold coins/bars, a gold ETF or a gold mutual fund avoid making charges.");
                o.lines.add("📉📈 Gold jumps up and down for years at a time. Many people keep just a small part (5–10%) of their savings in gold, as a safety cushion.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("compare", "⚖️", TEAL, 0, "Compare all", "FD, PPF, gold, funds… side by side", new P[]{
                    new P("a", "Amount", RS, 1000, 5000000, 1000, 100000),
                    new P("y", "For", YEARS, 1, 30, 1, 10)}, (c, v) -> {
                int y = v.get("y").intValue();
                double a = v.get("a");
                // name, rate, colour, risk, lock-in, take out, tax
                Object[][] opts = {
                        {"Savings account", 0.03, GREY, "Very safe", "None", "Anytime", "Interest taxed"},
                        {"FD", 0.07, BLUE, "Very safe", "Your choice (penalty if early)", "Easy", "Interest taxed at your slab"},
                        {"RD", 0.068, BLUE, "Very safe", "Your choice", "Easy", "Interest taxed at your slab"},
                        {"PPF", 0.071, PURPLE, "Very safe (government)", "15 years", "Hard", "Tax-free"},
                        {"Debt fund", 0.07, TEAL, "Low", "None", "1–3 days", "Taxed at your slab"},
                        {"Gold", 0.10, AMBER, "Medium — moves a lot", "None", "Easy", "Taxed when sold"},
                        {"Equity mutual fund", 0.12, GREEN, "High in short run", "None (ELSS: 3 yrs)", "2–3 days", "Some gains taxed when sold"},
                        {"Single stocks", 0.12, RED, "Very high — can drop a lot", "None", "Easy", "Some gains taxed when sold"},
                };
                double best = 0;
                double[] fin = new double[opts.length];
                for (int i = 0; i < opts.length; i++) { fin[i] = a * Math.pow(1 + (Double) opts[i][1], y); best = Math.max(best, fin[i]); }
                Out o = new Out();
                o.headLabel = rs(a) + " for " + y + (y == 1 ? " year" : " years");
                o.head = "From " + big(fin[0]) + " to " + big(best);
                o.headColor = TEAL;
                for (int i = 0; i < opts.length; i++) {
                    Object[] op = opts[i];
                    o.bars.add(new Bar((String) op[0] + " · ~" + Math.round((Double) op[1] * 1000) / 10.0 + "%", big(fin[i]),
                            "Risk: " + op[3] + " · Locked: " + op[4] + " · Take out: " + op[5] + " · " + op[6], (float) (fin[i] / best), (Integer) op[2]));
                }
                o.lines.add("⚖️ Higher growth usually comes with bigger bumps. Safe options grow slowly; shares grow faster over many years but can fall a lot in a bad year.");
                o.lines.add("🧺 Most people mix them: safe things for money needed soon, growth things for money needed after 5+ years.");
                o.note = "Rates are long-run examples, not promises; tax rules change. For learning, not financial advice.";
                return o;
            }),

            // ===== See how money works
            new Calc("snowball", "⛄", PINK, 1, "Snowball switch", "Interest that earns interest", new P[]{
                    new P("m", "Every month", RS, 500, 50000, 500, 5000),
                    new P("y", "For", YEARS, 5, 40, 1, 25),
                    new P("r", "Yearly return (example)", PCT, 40, 150, 5, 120),
                    new P("on", "Interest earns interest", SWITCH, 0, 1, 1, 1)}, (c, v) -> {
                int y = v.get("y").intValue();
                double[] comp = CalcMath.sipYears(v.get("m"), y, pct(v, "r"), 0), simp = CalcMath.sipSimple(v.get("m"), y, pct(v, "r"));
                boolean on = v.get("on") == 1;
                Out o = new Out();
                o.headLabel = on ? "With the snowball ON" : "With the snowball OFF";
                o.head = rs(on ? comp[y] : simp[y]);
                o.headColor = on ? PINK : GREY;
                o.tiles.add(new String[]{"Snowball ON", big(comp[y])});
                o.tiles.add(new String[]{"Snowball OFF", big(simp[y])});
                o.tiles.add(new String[]{"Extra from it", big(comp[y] - simp[y])});
                chart(o, new String[]{"Interest on interest", "Plain interest"}, new int[]{PINK, GREY}, new boolean[]{false, true}, new boolean[]{on, !on}, yearsX(y), comp, simp);
                o.lines.add("⛄ A snowball rolling downhill picks up more snow — and the bigger it gets, the more snow it picks up. Interest on interest works exactly like that.");
                o.lines.add("⏳ Look at the end of the graph: the lines split apart more every year. That's why starting early matters so much.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("race", "🏁", GREEN, 1, "Investment race", "Who wins over the years?", new P[]{
                    new P("a", "Starting amount", RS, 1000, 1000000, 1000, 100000),
                    new P("y", "Race length", YEARS, 1, 20, 1, 15)}, (c, v) -> {
                int y = v.get("y").intValue();
                double a = v.get("a");
                double[] sav = CalcMath.lumpYears(a, y, 0.03), fd = CalcMath.lumpYears(a, y, 0.07), gold = CalcMath.path(a, y, CalcMath.GOLD_YEARS),
                        eq = CalcMath.path(a, y, CalcMath.EQUITY_YEARS);
                Out o = new Out();
                o.race = true;
                double[][] all = {eq, gold, fd, sav};
                String[] names = {"Shares (equity fund)", "Gold", "FD", "Savings account"};
                int[] cols = {GREEN, AMBER, BLUE, GREY};
                int win = 0;
                for (int i = 1; i < 4; i++) if (all[i][y] > all[win][y]) win = i;
                o.headLabel = "After " + y + (y == 1 ? " year, the leader is" : " years, the winner is");
                o.head = names[win];
                o.headColor = cols[win];
                double best = 0;
                for (double[] s : all) best = Math.max(best, s[y]);
                for (int i = 0; i < 4; i++) o.bars.add(new Bar(names[i], big(all[i][y]), null, (float) (all[i][y] / best), cols[i]));
                chart(o, names, cols, new boolean[]{false, false, false, true}, new boolean[]{false, false, false, false}, yearsX(y), eq, gold, fd, sav);
                o.lines.add("🐢🐇 FD is the tortoise: slow, steady, never falls. Shares are the rabbit: they trip and fall some years, but over a long race they usually finish first.");
                o.lines.add("🔁 Try a short race (1–3 years) — the rabbit often loses! That's why money needed soon shouldn't be in shares.");
                o.note = "The ups and downs are made up to look like real markets — the real future will be different. For learning only.";
                return o;
            }),
            new Calc("crash", "📉", RED, 1, "Market crash test", "What to do when markets fall", new P[]{
                    new P("m", "Every month", RS, 500, 50000, 500, 5000),
                    new P("y", "Watch for", YEARS, 4, 10, 1, 6)}, (c, v) -> {
                int months = v.get("y").intValue() * 12;
                double[] kept = CalcMath.crashSip(v.get("m"), months, 24, 0), sold = CalcMath.crashSip(v.get("m"), months, 24, 1),
                        paused = CalcMath.crashSip(v.get("m"), months, 24, 2);
                int yrs = months / 12;
                double[] k = new double[yrs + 1], s = new double[yrs + 1], p = new double[yrs + 1];
                for (int i = 0; i <= yrs; i++) { k[i] = kept[i * 12]; s[i] = sold[i * 12]; p[i] = paused[i * 12]; }
                double put = v.get("m") * (double) months;
                Out o = new Out();
                o.headLabel = "Everyone put in " + rs(put) + ". The calm one ends with";
                o.head = rs(kept[months]);
                o.headColor = GREEN;
                o.tiles.add(new String[]{"Kept going", big(kept[months])});
                o.tiles.add(new String[]{"Paused", big(paused[months])});
                o.tiles.add(new String[]{"Panic-sold", big(sold[months])});
                chart(o, new String[]{"Kept investing", "Paused, restarted later", "Sold at the bottom"}, new int[]{GREEN, AMBER, RED},
                        new boolean[]{false, true, true}, new boolean[]{true, false, false}, yearsX(yrs), k, p, s);
                o.lines.add("📉 In year 3 the market falls 30%. Scary! Three friends react differently.");
                o.lines.add("😌 The one who kept going bought more at cheap prices — like buying mangoes on sale — and won when prices came back.");
                o.lines.add("😱 The one who sold at the bottom locked in the loss and missed the recovery.");
                o.note = "A made-up but realistic market. Real falls and recoveries take different times. For learning only.";
                return o;
            }),
            new Calc("time", "⏰", AMBER, 1, "Money time machine", "What prices will be in the future", new P[]{
                    new P("y", "Travel ahead", YEARS, 1, 30, 1, 20),
                    new P("i", "Prices rise every year", PCT, 30, 100, 5, 60)}, (c, v) -> {
                int y = v.get("y").intValue();
                double inf = pct(v, "i");
                int year = Fmt.cal(System.currentTimeMillis()).get(java.util.Calendar.YEAR) + y;
                Out o = new Out();
                o.headLabel = "In " + year + ", what costs ₹100 today will cost";
                o.head = rs(CalcMath.futurePrice(100, y, inf));
                o.headColor = AMBER;
                List<Object[]> items = itemsFor(c);
                double maxF = 0;
                for (Object[] it : items) maxF = Math.max(maxF, CalcMath.futurePrice((Double) it[1], y, inf));
                for (Object[] it : items) {
                    double fp = CalcMath.futurePrice((Double) it[1], y, inf);
                    o.bars.add(new Bar((String) it[0], rs(fp), "Today " + rs((Double) it[1]), (float) (fp / maxF), AMBER));
                }
                o.lines.add("⏰ Prices go up a little every year — that's inflation. So money kept under the bed slowly buys less and less.");
                o.lines.add("🌱 That's why money for far-away goals should grow faster than prices rise.");
                o.note = "Inflation in India has often been 4–7% a year. For learning only.";
                return o;
            }),
            new Calc("wait", "⌛", RED, 1, "Cost of waiting", "Start now vs start later", new P[]{
                    new P("m", "Every month", RS, 500, 50000, 500, 5000),
                    new P("age", "Your age", AGE, 18, 50, 1, 25),
                    new P("d", "If you wait", YEARS, 1, 15, 1, 5),
                    new P("r", "Yearly return (example)", PCT, 40, 150, 5, 120)}, (c, v) -> {
                int age = v.get("age").intValue(), d = v.get("d").intValue();
                double[] w = CalcMath.costOfWaiting(v.get("m"), pct(v, "r"), age, d, 60);
                Out o = new Out();
                o.headLabel = "Waiting " + d + (d == 1 ? " year" : " years") + " costs you";
                o.head = big(w[0] - w[1]);
                o.headColor = RED;
                o.tiles.add(new String[]{"Start now → at 60", big(w[0])});
                o.tiles.add(new String[]{"Start at " + (age + d), big(w[1])});
                double extra = v.get("m") * 12.0 * d;
                o.tiles.add(new String[]{"Saved by waiting", big(extra)});
                int n = 60 - age;
                double[] now = CalcMath.sipYears(v.get("m"), n, pct(v, "r"), 0), later = new double[n + 1];
                double[] l2 = CalcMath.sipYears(v.get("m"), Math.max(0, n - d), pct(v, "r"), 0);
                for (int i = 0; i <= n; i++) later[i] = i <= d ? 0 : l2[i - d];
                String[] x = new String[n + 1];
                for (int i = 0; i <= n; i++) x[i] = "Age " + (age + i);
                chart(o, new String[]{"Start now", "Start later"}, new int[]{GREEN, RED}, new boolean[]{false, true}, new boolean[]{true, false}, x, now, later);
                o.lines.add("⌛ Waiting " + d + " years “saves” only " + rs(extra) + " of spending — but the money you'd have at 60 drops by " + big(w[0] - w[1]) + ".");
                o.lines.add("🌳 The best time to plant a tree was years ago. The second-best time is today.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("taxinf", "🧾", RED, 1, "What tax & inflation eat", "The real growth of an FD", new P[]{
                    new P("a", "Amount in FD", RS, 1000, 5000000, 1000, 100000),
                    new P("r", "FD rate", PCT, 30, 90, 5, 70),
                    new P("t", "Your tax slab", CHOICE, 0, 4, 1, 3, new String[]{"No tax", "5%", "10%", "20%", "30%"}),
                    new P("i", "Prices rise every year", PCT, 30, 100, 5, 60)}, (c, v) -> {
                double a = v.get("a"), r = pct(v, "r"), t = new double[]{0, .05, .10, .20, .30}[v.get("t").intValue()], i = pct(v, "i");
                double interest = a * r, tax = interest * t, infl = a * i, real = interest - tax - infl;
                Out o = new Out();
                o.headLabel = "Real growth in one year";
                o.head = (real >= 0 ? "" : "−") + rs(Math.abs(real)) + "  (" + String.format(java.util.Locale.ROOT, "%.1f", CalcMath.realReturn(r, t, i) * 100) + "%)";
                o.headColor = real >= 0 ? GREEN : RED;
                double mx = Math.max(1, interest);
                o.bars.add(new Bar("Interest the bank pays", "+" + rs(interest), null, 1f, BLUE));
                o.bars.add(new Bar("Tax takes", "−" + rs(tax), null, (float) (tax / mx), RED));
                o.bars.add(new Bar("Higher prices eat", "−" + rs(infl), null, (float) Math.min(1, infl / mx), AMBER));
                o.bars.add(new Bar("What you really gained", (real >= 0 ? "+" : "−") + rs(Math.abs(real)), null, (float) Math.min(1, Math.abs(real) / mx), real >= 0 ? GREEN : RED));
                o.lines.add("🍕 The bank gives you a pizza of interest. Tax eats a slice. Rising prices eat a few more. What's left is your real growth.");
                o.lines.add(real < 0 ? "😮 Here your money actually buys a little less after a year — FD keeps money safe, but doesn't always make it grow."
                        : "✅ Your money grows a little faster than prices. Safe, but slow.");
                o.note = EXAMPLE;
                return o;
            }),
            new Calc("fees", "✂️", PURPLE, 1, "Fees matter", "A small yearly fee over many years", new P[]{
                    new P("m", "Every month", RS, 500, 100000, 500, 10000),
                    new P("y", "For", YEARS, 5, 40, 1, 20),
                    new P("r", "Yearly return (example)", PCT, 60, 150, 5, 120),
                    new P("f1", "Fee A (e.g. regular plan)", PCT, 0, 25, 1, 15),
                    new P("f2", "Fee B (e.g. direct plan)", PCT, 0, 25, 1, 5)}, (c, v) -> {
                int y = v.get("y").intValue();
                double[] a = CalcMath.sipYears(v.get("m"), y, pct(v, "r") - pct(v, "f1"), 0), b = CalcMath.sipYears(v.get("m"), y, pct(v, "r") - pct(v, "f2"), 0);
                Out o = new Out();
                o.headLabel = "The small fee difference costs";
                o.head = big(Math.abs(b[y] - a[y]));
                o.headColor = PURPLE;
                o.tiles.add(new String[]{"With fee A", big(a[y])});
                o.tiles.add(new String[]{"With fee B", big(b[y])});
                chart(o, new String[]{"Fee B", "Fee A"}, new int[]{GREEN, RED}, new boolean[]{false, true}, new boolean[]{true, false}, yearsX(y), b, a);
                o.lines.add("✂️ A fee of 1% sounds tiny — but it's taken every single year, from all your money, and it stops that money from snowballing.");
                o.lines.add("💡 Mutual funds have “direct” plans with lower fees than “regular” plans. Same fund, same manager — less cut.");
                o.note = EXAMPLE;
                return o;
            }),

            // ===== Loans & big buys
            new Calc("card", "💳", RED, 2, "Credit card trap", "Paying only the minimum due", new P[]{
                    new P("b", "Card bill", RS, 1000, 300000, 1000, 20000),
                    new P("r", "Interest a month", PCT, 20, 45, 1, 36),
                    new P("p", "Minimum due", PCT, 20, 100, 5, 50)}, (c, v) -> {
                double[] t = CalcMath.cardTrap(v.get("b"), pct(v, "r"), pct(v, "p"), 200);
                Out o = new Out();
                int months = (int) t[0];
                o.headLabel = "Paying only the minimum, it takes";
                o.head = months >= 600 ? "50+ years" : (months >= 24 ? (months / 12) + " years " + (months % 12) + " months" : months + " months");
                o.headColor = RED;
                o.tiles.add(new String[]{"Interest you pay", rs(t[1])});
                o.tiles.add(new String[]{"Pay full bill", "₹0 interest"});
                o.tiles.add(new String[]{"Yearly rate", "~" + Math.round((Math.pow(1 + pct(v, "r"), 12) - 1) * 100) + "%"});
                o.lines.add("💳 The “minimum due” is a trap: the card company charges about " + Math.round((Math.pow(1 + pct(v, "r"), 12) - 1) * 100) + "% a year on what's left — plus 18% GST on that interest.");
                o.lines.add("✅ Always pay the FULL bill by the due date. Then a credit card is free — and even gives you rewards.");
                o.lines.add("🆘 If you can't, pay as much as you can and stop using the card until it's clear.");
                o.note = "Assumes no new spending on the card. Real charges differ by card.";
                return o;
            }),
            new Calc("nocost", "🏷️", AMBER, 2, "“No-cost” EMI check", "Is it really free?", new P[]{
                    new P("p", "Price", RS, 1000, 300000, 500, 30000),
                    new P("n", "EMI for", CHOICE, 0, 3, 1, 1, new String[]{"3 months", "6 months", "9 months", "12 months"}),
                    new P("r", "Card's interest rate (yearly)", PCT, 100, 240, 5, 150),
                    new P("f", "Processing fee", RS, 0, 999, 1, 199),
                    new P("d", "Discount if you pay in full", PCT, 0, 150, 5, 0)}, (c, v) -> {
                int n = new int[]{3, 6, 9, 12}[v.get("n").intValue()];
                double price = v.get("p");
                double[] e = CalcMath.noCostEmi(price, n, pct(v, "r"), v.get("f"));
                double payNow = price * (1 - pct(v, "d"));
                double saveFirst = payNow - (CalcMath.rd(payNow / n, 0.068, n) - payNow);
                Out o = new Out();
                o.headLabel = "“No-cost” EMI really costs you";
                o.head = rs(e[0]);
                o.headColor = e[0] > payNow ? RED : GREEN;
                double mx = Math.max(e[0], payNow);
                o.bars.add(new Bar("No-cost EMI", rs(e[0]), "GST on hidden interest " + rs(e[2]) + " + fee " + rs(e[3]), (float) (e[0] / mx), RED));
                o.bars.add(new Bar("Pay now", rs(payNow), v.get("d") > 0 ? "With the cash discount" : "Full price", (float) (payNow / mx), BLUE));
                o.bars.add(new Bar("Save first in an RD, then buy", rs(saveFirst), "The bank pays you interest while you wait", (float) (saveFirst / mx), GREEN));
                o.lines.add("🏷️ “No-cost” means the shop gives a discount equal to the bank's interest. But the bank still adds 18% GST on that interest — plus a processing fee.");
                o.lines.add("🤔 And if the shop gives a discount for paying in full, the EMI quietly loses you that discount too.");
                o.lines.add("✅ If you have the money, pay now. If you don't, waiting and saving first is the cheapest of all.");
                o.note = "Each bank's fee and rate differ — read the EMI terms. For learning only.";
                return o;
            }),
            new Calc("prepay", "🏠", BLUE, 2, "Prepay loan or invest?", "Where should extra money go?", new P[]{
                    new P("o", "Loan left", RS, 50000, 10000000, 50000, 2000000),
                    new P("r", "Loan interest", PCT, 60, 180, 5, 90),
                    new P("y", "Years left", YEARS, 1, 30, 1, 15),
                    new P("e", "Extra every month", RS, 500, 100000, 500, 5000),
                    new P("i", "Investment return (example)", PCT, 40, 150, 5, 100)}, (c, v) -> {
                double[] p = CalcMath.prepayOrInvest(v.get("o"), pct(v, "r"), v.get("y").intValue() * 12, v.get("e"), pct(v, "i"));
                boolean prepay = p[2] >= p[3];
                Out o = new Out();
                o.headLabel = "At these numbers, the better choice";
                o.head = prepay ? "Prepay the loan" : "Invest the extra";
                o.headColor = prepay ? BLUE : GREEN;
                o.tiles.add(new String[]{"Loan ends sooner", Math.round(p[0] / 12 * 10) / 10.0 + " yrs"});
                o.tiles.add(new String[]{"Interest saved", big(p[1])});
                double mx = Math.max(p[2], p[3]);
                o.bars.add(new Bar("Prepay, then invest the freed EMI", big(p[2]), "Money you'd have when the loan would have ended", (float) (p[2] / mx), BLUE));
                o.bars.add(new Bar("Invest the extra instead", big(p[3]), "Same day, loan paid normally", (float) (p[3] / mx), GREEN));
                o.lines.add("⚖️ Paying off a loan “earns” you exactly the loan's interest rate — for sure. Investing might earn more, but it's not sure.");
                o.lines.add(pct(v, "r") >= pct(v, "i") ? "✅ When the loan costs more than the investment usually earns, prepaying wins — and feels great."
                        : "🌱 Here the investment is expected to earn more than the loan costs. But prepaying is still a safe, sure choice if loans worry you.");
                o.note = "Ignores taxes and prepayment charges. " + EXAMPLE;
                return o;
            }),
            new Calc("rentbuy", "🔑", TEAL, 2, "Rent or buy a home?", "Who has more after N years", new P[]{
                    new P("p", "House price", RS, 500000, 20000000, 100000, 5000000),
                    new P("d", "Down payment", PCT, 100, 500, 50, 200),
                    new P("r", "Home loan rate", PCT, 60, 120, 5, 85),
                    new P("rent", "Rent for a similar home", RS, 2000, 200000, 500, 15000),
                    new P("y", "Compare after", YEARS, 5, 30, 1, 20),
                    new P("g", "House price rise a year", PCT, 0, 100, 5, 50),
                    new P("i", "Investment return (example)", PCT, 40, 140, 5, 100)}, (c, v) -> {
                int y = v.get("y").intValue();
                double[] r = CalcMath.rentVsBuy(v.get("p"), pct(v, "d"), pct(v, "r"), 20, v.get("rent"), 0.05, pct(v, "g"), pct(v, "i"), y);
                double emi = CalcMath.emi(v.get("p") * (1 - pct(v, "d")), pct(v, "r"), 240);
                boolean buy = r[0] >= r[1];
                Out o = new Out();
                o.headLabel = "After " + y + " years, more money with";
                o.head = buy ? "Buying" : "Renting + investing";
                o.headColor = buy ? TEAL : GREEN;
                o.tiles.add(new String[]{"Home loan EMI", rs(emi)});
                o.tiles.add(new String[]{"Rent now", rs(v.get("rent"))});
                double mx = Math.max(Math.abs(r[0]), Math.abs(r[1]));
                o.bars.add(new Bar("Buyer owns", big(r[0]), "House value minus loan still left", (float) (Math.max(0, r[0]) / mx), TEAL));
                o.bars.add(new Bar("Renter owns", big(r[1]), "Down payment + every month's difference, invested", (float) (Math.max(0, r[1]) / mx), GREEN));
                o.lines.add("🏠 Buying: you pay EMI and own the house slowly. Renting: you pay less each month and invest the rest.");
                o.lines.add("💭 Money isn't everything — a home of your own brings peace; renting brings freedom to move. Use this only as one part of the decision.");
                o.note = "Assumes 20-year loan, ~7% buying costs, 0.5% yearly upkeep, rent rising 5% a year. For learning only.";
                return o;
            }),
            new Calc("lockin", "🔒", GREY, 2, "Lock-in timeline", "When can you take your money out?", new P[]{
                    new P("age", "Your age", AGE, 18, 59, 1, 25)}, (c, v) -> {
                int toSixty = 60 - v.get("age").intValue();
                Object[][] rows = {
                        {"Savings account", 0.0, "Anytime", GREEN},
                        {"Liquid / debt fund", 0.01, "In 1–3 working days", GREEN},
                        {"Equity mutual fund", 0.01, "Anytime (small exit fee in year 1 for many funds)", GREEN},
                        {"Gold ETF / gold fund", 0.01, "Anytime on market days", GREEN},
                        {"FD / RD", 0.05, "Anytime — small penalty if you break it early", AMBER},
                        {"ELSS (tax-saver fund)", 3.0, "Locked 3 years", AMBER},
                        {"Tax-saver FD", 5.0, "Locked 5 years", AMBER},
                        {"NSC", 5.0, "Locked 5 years", AMBER},
                        {"PPF", 15.0, "15 years (some can come out from year 7)", RED},
                        {"NPS", (double) toSixty, "Till 60 — in " + toSixty + " years (leaving early: 80% must buy a pension)", RED},
                };
                Out o = new Out();
                o.headLabel = "Before you put money in, ask";
                o.head = "When will I need it?";
                o.headColor = GREY;
                for (Object[] r : rows) {
                    double yrs = (Double) r[1];
                    o.bars.add(new Bar((String) r[0], yrs < 0.5 ? "Free" : Math.round(yrs) + " yrs", (String) r[2], (float) Math.max(0.03, Math.min(1, yrs / 40)), (Integer) r[3]));
                }
                o.lines.add("🔒 Some boxes lock your money so you can't spend it — that's good for far-away goals, bad for money you might need soon.");
                o.lines.add("☂️ Keep your emergency fund only in boxes you can open anytime.");
                o.note = "Rules change — check the latest before you invest.";
                return o;
            }),
    };

    static Calc byId(String id) {
        for (Calc c : ALL) if (c.id.equals(id)) return c;
        return ALL[0];
    }

    /** Things to show in the time machine: your own repeat spends (if Money knows them), plus everyday examples. */
    static List<Object[]> itemsFor(Context c) {
        List<Object[]> out = new ArrayList<>();
        long spend = Goals.monthlySpend(c);
        if (spend > 0) out.add(new Object[]{"Your monthly spending", spend / 100.0});
        long now = System.currentTimeMillis(), from = now - 60 * GoalMath.DAY;
        Map<String, long[]> m = new HashMap<>();
        for (Txn t : Store.get(c).txns()) {
            if (!t.isSpend() || t.time < from || t.unnamed() || t.amount > 500000) continue;
            long[] x = m.get(t.merchant);
            if (x == null) m.put(t.merchant, x = new long[2]);
            x[0] += t.amount;
            x[1]++;
        }
        List<Map.Entry<String, long[]>> l = new ArrayList<>(m.entrySet());
        java.util.Collections.sort(l, (a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        for (int i = 0; i < l.size() && out.size() < 4; i++) {
            if (l.get(i).getValue()[1] < 3) break;
            out.add(new Object[]{l.get(i).getKey() + " (each time)", l.get(i).getValue()[0] / 100.0 / l.get(i).getValue()[1]});
        }
        out.add(new Object[]{"A cup of tea", 15.0});
        out.add(new Object[]{"A movie ticket", 250.0});
        out.add(new Object[]{"A new phone", 20000.0});
        return out;
    }
}
