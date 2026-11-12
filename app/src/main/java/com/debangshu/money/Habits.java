package com.debangshu.money;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Probably Sharma Kirana?" — guesses who an unnamed payment was from your own past payments:
 * same spot (location), similar amount, similar time of day. Only suggests; you confirm.
 */
public final class Habits {

    private Habits() {}

    public static final class Guess {
        public String name, category;
        public int times;
    }

    public static Guess guess(List<Txn> all, Txn t) {
        if (!t.isSpend()) return null;
        int minute = minuteOfDay(t.time);
        Map<String, Double> score = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        Map<String, String> cat = new HashMap<>();
        for (Txn x : all) {
            if (x == t || !x.isSpend() || x.unnamed() || x.time > t.time || t.time - x.time > 180L * 86400000) continue;
            double sc = 0;
            boolean near = false;
            if (t.hasLoc() && x.hasLoc()) {
                double d = Locator.distance(t.lat, t.lng, x.lat, x.lng);
                double limit = Math.max(80, Math.min(250, Math.max(t.locAcc, x.locAcc) * 1.5));
                if (d <= limit) { sc += 3; near = true; }
                else if (d > 1500) continue;            // clearly somewhere else
            }
            double ratio = x.amount / (double) Math.max(1, t.amount);
            if (x.amount == t.amount) sc += 2;
            else if (ratio > 0.75 && ratio < 1.33) sc += 1;
            int dm = Math.abs(minuteOfDay(x.time) - minute);
            dm = Math.min(dm, 1440 - dm);
            if (dm <= 60) sc += 1;
            else if (dm <= 120) sc += 0.5;
            boolean strong = near ? sc >= 4 : sc >= 3;   // without location: same amount + same time of day
            if (!strong) continue;
            String k = x.merchant;
            score.put(k, (score.containsKey(k) ? score.get(k) : 0) + sc);
            count.put(k, (count.containsKey(k) ? count.get(k) : 0) + 1);
            cat.put(k, x.category);
        }
        Guess best = null;
        double bestScore = 0;
        for (Map.Entry<String, Double> e : score.entrySet()) {
            int n = count.get(e.getKey());
            boolean hadLoc = t.hasLoc();
            if (n < (hadLoc ? 1 : 3)) continue;
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = new Guess();
                best.name = e.getKey();
                best.category = cat.get(e.getKey());
                best.times = n;
            }
        }
        return best;
    }

    static int minuteOfDay(long t) {
        java.util.Calendar c = Fmt.cal(t);
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }
}
