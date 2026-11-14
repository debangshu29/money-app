package com.debangshu.money;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Goals 2.0 — the data. A goal is "Laptop ₹60,000 by March". Its money either sits in a bank account you picked
 * (progress = that account's live balance) or you add it yourself. Snapshots give the real pace; milestones give badges.
 *
 * Stored as a list "goals" of JSON objects:
 * id, name, emoji, kind, target, by, created, startValue, saved (added yourself), account ("" = added yourself),
 * existing (count what was already in the account), base (account balance when linked), paused, photo, plan (₹/month),
 * pct (last percent seen), celebrate (milestone to show), snaps [[time, value]], log [[time, amount, note]].
 */
final class Goals {

    private Goals() {}

    static final String KEY = "goals", BADGES = "badges";

    /** kind, emoji, name */
    static final String[][] TEMPLATES = {
            {"emergency", "🛡️", "Emergency fund"}, {"phone", "📱", "New phone"}, {"laptop", "💻", "Laptop"},
            {"trip", "✈️", "Trip"}, {"bike", "🏍️", "Bike"}, {"gift", "🎁", "Gift"}, {"festival", "🪔", "Festival"},
            {"study", "🎓", "Studies"}, {"wedding", "💍", "Wedding"}, {"home", "🏠", "A home"}, {"other", "🎯", "Something else"},
    };

    static final int[] COLORS = {0xFF0E9F6E, 0xFF6366F1, 0xFFF59E0B, 0xFFEC4899, 0xFF0EA5E9, 0xFF8B5CF6, 0xFFEF4444};

    static int color(JSONObject g) { return COLORS[(g.optString("id").hashCode() & 0x7FFFFFFF) % COLORS.length]; }

    // ------------------------------------------------------------------ list

    static List<JSONObject> all(Context c) {
        JSONArray a = Store.get(c).list(KEY);
        List<JSONObject> out = new ArrayList<>();
        boolean fixed = false;
        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.optJSONObject(i);
            if (g == null) continue;
            try {   // goals from before 2.3
                if (g.optLong("created") == 0) { g.put("created", now).put("startValue", g.optLong("saved")); fixed = true; }
                if (!g.has("account")) { g.put("account", ""); fixed = true; }
            } catch (Exception ignored) { }
            out.add(g);
        }
        if (fixed) saveAll(c, out);
        return out;
    }

    static List<JSONObject> active(Context c) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject g : all(c)) if (!g.optBoolean("paused") && current(c, g) < g.optLong("target")) out.add(g);
        return out;
    }

    static JSONObject byId(Context c, String id) {
        for (JSONObject g : all(c)) if (g.optString("id").equals(id)) return g;
        return null;
    }

    static void saveAll(Context c, List<JSONObject> l) {
        JSONArray a = new JSONArray();
        for (JSONObject g : l) a.put(g);
        Store.get(c).putList(KEY, a);
    }

    static void put(Context c, JSONObject g) {
        List<JSONObject> l = all(c);
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).optString("id").equals(g.optString("id"))) { l.set(i, g); found = true; }
        if (!found) l.add(g);
        saveAll(c, l);
    }

    static void delete(Context c, String id) {
        List<JSONObject> l = all(c), out = new ArrayList<>();
        for (JSONObject g : l) if (!g.optString("id").equals(id)) out.add(g);
        saveAll(c, out);
        File f = photo(c, id);
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** Make a new goal. account "" = you add the money yourself. */
    static JSONObject create(Context c, String kind, String emoji, String name, long target, long by, String account, boolean existing) {
        JSONObject g = new JSONObject();
        long now = System.currentTimeMillis();
        try {
            g.put("id", Screen.id()).put("kind", kind).put("emoji", emoji).put("name", name).put("target", target).put("by", by)
                    .put("created", now).put("saved", 0L).put("account", account == null ? "" : account).put("existing", existing);
            if (account != null && !account.isEmpty()) {
                long[] lv = Noted.liveOf(c, account);
                g.put("base", lv == null || lv[0] < 0 ? 0 : lv[0]);
            }
            g.put("startValue", current(c, g));
            g.put("pct", percent(c, g));
        } catch (Exception ignored) { }
        put(c, g);
        award(c, "first_goal");
        if (all(c).size() >= 3) award(c, "dreamer");
        if (account != null && !account.isEmpty()) award(c, "real_home");
        if ("emergency".equals(kind)) award(c, "safety_net");
        if ("festival".equals(kind)) award(c, "festival_ready");
        return g;
    }

    static File photo(Context c, String id) { return new File(new File(c.getFilesDir(), "goals"), id + ".jpg"); }

    // ------------------------------------------------------------------ numbers

    /** Money in the goal now. */
    static long current(Context c, JSONObject g) {
        String acc = g.optString("account");
        if (!acc.isEmpty()) {
            long[] lv = Noted.liveOf(c, acc);
            if (lv != null && lv[0] >= 0) return Math.max(0, lv[0] - (g.optBoolean("existing", true) ? 0 : g.optLong("base")));
        }
        return Math.max(0, g.optLong("saved"));
    }

    static int percent(Context c, JSONObject g) {
        long t = Math.max(1, g.optLong("target"));
        return (int) Math.min(100, current(c, g) * 100 / t);
    }

    static long[][] snaps(JSONObject g) {
        JSONArray a = g.optJSONArray("snaps");
        if (a == null) return new long[0][];
        long[][] out = new long[a.length()][];
        for (int i = 0; i < a.length(); i++) { JSONArray p = a.optJSONArray(i); out[i] = new long[]{p.optLong(0), p.optLong(1)}; }
        return out;
    }

    /** Keep one value per day (up to ~8 months) — that's how the real pace is measured. */
    static boolean snapshot(JSONObject g, long value) {
        try {
            JSONArray a = g.optJSONArray("snaps");
            if (a == null) { a = new JSONArray(); g.put("snaps", a); }
            long now = System.currentTimeMillis();
            if (a.length() > 0) {
                JSONArray last = a.optJSONArray(a.length() - 1);
                if (Fmt.startOfDay(last.optLong(0)) == Fmt.startOfDay(now)) {
                    if (last.optLong(1) == value) return false;
                    a.put(a.length() - 1, new JSONArray().put(now).put(value));
                    return true;
                }
            }
            a.put(new JSONArray().put(now).put(value));
            while (a.length() > 240) a.remove(0);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Average money in − money out per month over the last 3 full months. */
    static long monthlySaving(Context c) {
        List<Txn> all = Store.get(c).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long sum = 0;
        int n = 0;
        for (int k = 1; k <= 3; k++) {
            long s = Fmt.addMonths(ms, -k), e = Fmt.addMonths(ms, -k + 1);
            long in = Ledger.income(all, s, e), out = Ledger.spent(all, s, e);
            if (in == 0 && out == 0) continue;
            sum += in - out;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Average spending per month over the last 3 full months. */
    static long monthlySpend(Context c) {
        List<Txn> all = Store.get(c).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long sum = 0;
        int n = 0;
        for (int k = 1; k <= 3; k++) {
            long out = Ledger.spent(all, Fmt.addMonths(ms, -k), Fmt.addMonths(ms, -k + 1));
            if (out == 0) continue;
            sum += out;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    /** "Wants" you could spend less on: category → average per month (last 3 months), biggest first. */
    static LinkedHashMap<String, Long> wants(Context c) {
        List<Txn> all = Store.get(c).txns();
        long now = System.currentTimeMillis();
        long from = Fmt.addMonths(Fmt.startOfMonth(now), -3), to = Fmt.startOfMonth(now);
        double months = 3;
        if (Ledger.spent(all, from, to) == 0) { from = Fmt.startOfMonth(now); to = now; months = Math.max(0.3, GoalMath.months(from, now)); }
        String[] want = {Cats.FOOD, Cats.SHOP, Cats.FUN, Cats.SUBS, Cats.TRAVEL, Cats.PERSONAL, Cats.GIFTS};
        Map<String, Long> by = Ledger.byCategory(all, from, to);
        List<Map.Entry<String, Long>> l = new ArrayList<>();
        for (String w : want) if (by.containsKey(w) && by.get(w) > 0) l.add(new java.util.AbstractMap.SimpleEntry<>(w, Math.round(by.get(w) / months)));
        java.util.Collections.sort(l, (x, y) -> Long.compare(y.getValue(), x.getValue()));
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : l) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** Status of one goal. */
    static GoalMath.Status status(Context c, JSONObject g) {
        long now = System.currentTimeMillis();
        long cur = current(c, g);
        long pace = GoalMath.pace(snaps(g), now);
        if (g.optString("account").isEmpty() && pace < 0) pace = manualPace(g, now);
        long fallback = g.optLong("plan");
        if (fallback <= 0) {
            int n = Math.max(1, active(c).size());
            fallback = Math.max(0, monthlySaving(c) / n);
        }
        return GoalMath.status(now, cur, g.optLong("target"), g.optLong("created"), g.optLong("startValue"), g.optLong("by"),
                pace, fallback, g.optBoolean("paused"));
    }

    /** For goals you fill yourself: what you've added per month since you started (after the first 2 weeks). */
    private static long manualPace(JSONObject g, long now) {
        double m = GoalMath.months(g.optLong("created"), now);
        if (m < 0.5) return -1;
        return Math.round((g.optLong("saved") - g.optLong("startValue")) / Math.max(1.0, m));
    }

    static String statusLine(GoalMath.Status s) {
        switch (s.kind) {
            case GoalMath.REACHED: return "Reached";
            case GoalMath.PAUSED: return "Paused";
            case GoalMath.AHEAD: return "Ahead — done by " + Fmt.date(s.finish);
            case GoalMath.ON_TRACK: return "On track — done by " + Fmt.date(s.finish);
            case GoalMath.BEHIND: return s.gap > 0 ? "Behind by " + Fmt.rs(s.gap) : "A bit behind — save " + Fmt.rs(s.catchUp) + " more a month";
            case GoalMath.NO_DATE: return s.finish > 0 ? "At this pace: " + Fmt.date(s.finish) : "No date set";
            default: return "Not moving yet — " + (s.needPerMonth > 0 ? Fmt.rs(s.needPerMonth) + " a month needed" : "add some money");
        }
    }

    static int statusColor(Context c, GoalMath.Status s) {
        switch (s.kind) {
            case GoalMath.REACHED: case GoalMath.AHEAD: case GoalMath.ON_TRACK: return Ui.col(c, R.color.success);
            case GoalMath.BEHIND: return 0xFFD97706;
            default: return Ui.col(c, R.color.text2);
        }
    }

    // ------------------------------------------------------------------ milestones & badges

    /**
     * Look at every goal: save today's value, notice milestones crossed (25 / 50 / 75 / 100 %).
     * With notify, a milestone also posts a notification right away (e.g. after the bank's SMS).
     */
    static void check(Context c, boolean notify) {
        List<JSONObject> l = all(c);
        boolean changed = false;
        for (JSONObject g : l) {
            long cur = current(c, g);
            if (snapshot(g, cur)) changed = true;
            int was = g.optInt("pct", 0);
            int now = (int) Math.min(100, cur * 100 / Math.max(1, g.optLong("target")));
            if (now != was) {
                try {
                    g.put("pct", now);
                    List<Integer> hit = GoalMath.crossed(was, now);
                    if (!hit.isEmpty()) {
                        int m = hit.get(hit.size() - 1);
                        g.put("celebrate", m);
                        award(c, m == 25 ? "quarter" : m == 50 ? "halfway" : m == 75 ? "almost" : "reached");
                        if (notify && Alerts.canNotify(c)) {
                            Alerts.channels(c);
                            Alerts.post(c, Alerts.CH_ALERTS, 32000 + (g.optString("id").hashCode() & 0x3FF),
                                    m == 100 ? "🏆 Goal reached: " + g.optString("name") : "🎉 " + g.optString("name") + " is " + m + "% there",
                                    Fmt.rs(cur) + " of " + Fmt.rs(g.optLong("target")) + (m == 100 ? " — well done!" : " — keep going"), null);
                        }
                    }
                } catch (Exception ignored) { }
                changed = true;
            }
        }
        if (changed) saveAll(c, l);
    }

    /** id, emoji, title, how to earn it */
    static final String[][] BADGE_LIST = {
            {"first_goal", "🎯", "First goal", "Set up your first goal"},
            {"real_home", "🏦", "Real home", "Link a goal to a bank account"},
            {"quarter", "⭐", "Quarter way", "Reach 25% of a goal"},
            {"halfway", "🏅", "Halfway", "Reach 50% of a goal"},
            {"almost", "🚀", "Almost there", "Reach 75% of a goal"},
            {"reached", "🏆", "Goal reached", "Finish a goal"},
            {"planner", "🔀", "Planner", "Split your saving across goals"},
            {"safety_net", "☂️", "Safety net", "Start an emergency fund"},
            {"festival_ready", "🪔", "Festival ready", "Plan ahead for a festival"},
            {"saver", "🐷", "Saver", "Put last month's leftover into a goal"},
            {"dreamer", "✨", "Dreamer", "Have 3 goals going"},
            {"patient", "⏳", "Patient", "Skip something on your wait-list"},
            {"healthy", "❤️", "In shape", "Reach a money health score of 65+"},
            {"jar", "🫙", "Spare change", "Start the round-up jar"},
            {"payday", "💼", "Payday pro", "Split a salary the day it comes"},
            {"rule", "📊", "Balanced", "Keep needs, wants and saving within your rule for a month"},
            {"learner", "🎓", "Money basics", "Finish the 7-day money basics course"},
            {"quiz", "🧠", "Quiz whiz", "Score 8 or more in the money quiz"},
            {"reader", "📚", "Bookworm", "Learn 20 lessons"},
            {"nospend1", "🌱", "First no-spend day", "Have a whole day with no spending on wants"},
            {"streak3", "🔥", "On a roll", "3 no-spend days in a row"},
            {"streak7", "⚡", "Week of will", "7 no-spend days in a row"},
            {"nospend10", "🗓️", "Ten-day month", "10 no-spend days in one month"},
            {"nospend30", "👑", "Mindful", "30 no-spend days in all"},
            {"in_green", "💰", "In the green", "Spend less than you earn in a month"},
            {"steady", "📈", "Steady saver", "Save 10% of what comes in, 3 months in a row"},
            {"sip_habit", "🌳", "SIP habit", "Invest in 3 different months"},
            {"wrapped", "🎁", "Looked back", "Watch your Money Wrapped"},
    };

    static boolean has(Context c, String id) { return Store.get(c).obj(BADGES).has(id); }

    /** Returns true the first time. */
    static boolean award(Context c, String id) {
        JSONObject b = Store.get(c).obj(BADGES);
        if (b.has(id)) return false;
        try { b.put(id, System.currentTimeMillis()); } catch (Exception ignored) { }
        Store.get(c).putObj(BADGES, b);
        try {   // remember to show it once
            JSONObject p = Store.get(c).obj("badgeNew");
            p.put(id, true);
            Store.get(c).putObj("badgeNew", p);
        } catch (Exception ignored) { }
        return true;
    }

    static String[] badge(String id) {
        for (String[] b : BADGE_LIST) if (b[0].equals(id)) return b;
        return null;
    }
}
