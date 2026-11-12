package com.debangshu.money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Smart insights (plain Java): your personal inflation, hidden charges, savings score. */
public final class Smart {

    private Smart() {}

    public static final class PriceChange {
        public String name, category;
        public long before, now;   // average payment, paise
        public int pct, count;
    }

    /** Places where your usual payment went up (or down): last 3 months vs the 3 months before. */
    public static List<PriceChange> inflation(List<Txn> all, long now) {
        long cut = Fmt.addMonths(Fmt.startOfMonth(now), -3), old = Fmt.addMonths(cut, -3);
        Map<String, long[]> recent = new HashMap<>(), earlier = new HashMap<>();
        Map<String, String> names = new HashMap<>(), cats = new HashMap<>();
        for (Txn t : all) {
            if (!t.isSpend() || t.unnamed() || t.time < old || Cats.PEOPLE.equals(t.category) || Cats.CASH.equals(t.category)) continue;
            String k = Cats.key(t);
            if (k.isEmpty()) continue;
            Map<String, long[]> m = t.time >= cut ? recent : earlier;
            long[] v = m.get(k);
            if (v == null) m.put(k, v = new long[2]);
            v[0] += t.amount;
            v[1]++;
            names.put(k, t.merchant);
            cats.put(k, t.category);
        }
        List<PriceChange> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : recent.entrySet()) {
            long[] r = e.getValue(), o = earlier.get(e.getKey());
            if (o == null || r[1] < 3 || o[1] < 3) continue;
            long a = o[0] / o[1], b = r[0] / r[1];
            if (a < 5000) continue;
            int pct = (int) Math.round((b - a) * 100.0 / a);
            if (Math.abs(pct) < 10) continue;
            PriceChange p = new PriceChange();
            p.name = names.get(e.getKey());
            p.category = cats.get(e.getKey());
            p.before = a;
            p.now = b;
            p.pct = pct;
            p.count = (int) (r[1] + o[1]);
            out.add(p);
        }
        Collections.sort(out, (x, y) -> Integer.compare(y.pct, x.pct));
        return out;
    }

    private static final Pattern FEE = Pattern.compile(
            "sms (?:alert )?charges?|annual fee|joining fee|late (?:payment )?fee|late charges?|penalty|penal|min(?:imum)? bal(?:ance)? charges?|"
            + "non[- ]maintenance|amc|atm (?:withdrawal )?charges?|cash withdrawal fee|forex markup|markup fee|gst on|service charges?|"
            + "convenience fee|processing fee|finance charges?|interest on card|overlimit|cheque bounce|ecs return|nach return|debit card fee",
            Pattern.CASE_INSENSITIVE);

    /** Bank fees, SMS charges, card annual / late fees, penalties… since {@code from}. */
    public static List<Txn> charges(List<Txn> all, long from) {
        List<Txn> out = new ArrayList<>();
        for (Txn t : all) {
            if (t.credit || t.kind == Txn.IGNORED || t.kind == Txn.SNAPSHOT || t.time < from) continue;
            boolean fee = Cats.FEES.equals(t.category) || FEE.matcher(t.raw).find() || FEE.matcher(t.merchant).find();
            if (fee) out.add(t);
        }
        Collections.sort(out, (x, y) -> Long.compare(y.time, x.time));
        return out;
    }

    /** 0–100: how much of what came in you kept (with a small bonus for staying within budget). */
    public static int score(long in, long out, boolean withinBudget) {
        if (in <= 0) return out == 0 ? 50 : 20;
        double rate = (in - out) / (double) in;          // saved share
        int s = (int) Math.round(Math.max(0, Math.min(1, (rate + 0.1) / 0.5)) * 85);
        if (withinBudget) s += 15;
        return Math.max(0, Math.min(100, s));
    }

    public static String scoreWord(int s) {
        return s >= 80 ? "Excellent" : s >= 60 ? "Good" : s >= 40 ? "Okay" : "Needs care";
    }

    /** April–March financial year start for a time. */
    public static long fyStart(long t) {
        java.util.Calendar c = Fmt.cal(Fmt.startOfDay(t));
        int y = c.get(java.util.Calendar.YEAR);
        if (c.get(java.util.Calendar.MONTH) < 3) y--;
        c.set(y, 3, 1, 0, 0, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static String fyName(long fy) {
        int y = Fmt.cal(fy).get(java.util.Calendar.YEAR);
        return "FY " + y + "–" + String.format(Locale.ROOT, "%02d", (y + 1) % 100);
    }
}
