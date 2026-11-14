package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** No-spend challenges: streaks of days without food delivery, online shopping, or any spending. Checked from your payments. */
public class Challenges extends Activity {

    static final String KEY = "challenges";
    static final String[][] TYPES = {
            {"nospend", "🌱", "No-spend days", "Days with no spending at all (bills, rent and EMIs don't count)"},
            {"delivery", "🛵", "No food delivery", "No Swiggy, Zomato, EatSure, Domino's…"},
            {"shopping", "🛍️", "No online shopping", "No Amazon, Flipkart, Myntra, Ajio, Meesho, Nykaa…"},
            {"daily", "🎯", "Under ₹… a day", "Keep each day's spending under an amount you choose"},
            {"weekend", "🌴", "No-spend weekends", "Saturdays and Sundays without spending (bills don't count)"},
            {"week52", "📈", "52-week saving", "Week 1 save ₹10, week 2 ₹20 … week 52 ₹520 — ₹13,780 in a year"},
    };
    private static final Pattern DELIVERY = Pattern.compile("swiggy|zomato|eatsure|domino|pizza hut|faasos|box8|behrouz|magicpin|zepto cafe");
    private static final Pattern SHOPPING = Pattern.compile("amazon|flipkart|myntra|ajio|meesho|nykaa|tata cliq|snapdeal|shopsy|firstcry|purplle");

    static JSONArray list(Context c) { return Store.get(c).list(KEY); }

    static String[] type(String id) {
        for (String[] t : TYPES) if (t[0].equals(id)) return t;
        return TYPES[0];
    }

    /** Did this day pass the challenge? */
    static boolean dayOk(JSONObject ch, List<Txn> all, long day) {
        long end = Fmt.addDays(day, 1);
        String t = ch.optString("type");
        if ("week52".equals(t)) return true;
        if ("weekend".equals(t)) {
            int dow = Fmt.cal(day).get(java.util.Calendar.DAY_OF_WEEK);
            if (dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY) return true;
            t = "nospend";
        }
        long sum = 0;
        for (Txn x : all) {
            if (!x.isSpend() || x.time < day || x.time >= end) continue;
            String m = (x.merchant + " " + x.vpa).toLowerCase(Locale.ROOT);
            switch (t) {
                case "delivery": if (DELIVERY.matcher(m).find()) return false; break;
                case "shopping": if (SHOPPING.matcher(m).find()) return false; break;
                case "daily": sum += x.amount; break;
                default:   // same rule as the no-spend calendar
                    if (MotiMath.want(x)) return false;
            }
        }
        return !"daily".equals(t) || sum <= ch.optLong("limit");
    }

    /** {current streak, best streak} — today counts once it's over or still clean. */
    static int[] streak(JSONObject ch, List<Txn> all) {
        long start = Fmt.startOfDay(ch.optLong("start"));
        long today = Fmt.startOfDay(System.currentTimeMillis());
        int cur = 0, best = 0;
        for (long d = start; d <= today; d = Fmt.addDays(d, 1)) {
            if (dayOk(ch, all, d)) { cur++; best = Math.max(best, cur); }
            else if (d != today) cur = 0;
            else cur = 0;
        }
        return new int[]{cur, Math.max(best, ch.optInt("best"))};
    }

    static String summary(Context c) {
        JSONArray a = list(c);
        if (a.length() == 0) return "Streaks: no delivery, no shopping…";
        List<Txn> all = Store.get(c).txns();
        int best = 0;
        for (int i = 0; i < a.length(); i++) best = Math.max(best, streak(a.optJSONObject(i), all)[0]);
        return a.length() + (a.length() == 1 ? " challenge" : " challenges") + " · 🔥 " + best + (best == 1 ? " day" : " days");
    }

    // ------------------------------------------------------------------ screen

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "Challenges"));
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        build();
    }

    private void build() {
        box.removeAllViews();
        JSONArray a = list(this);
        List<Txn> all = Store.get(this).txns();
        TextView intro = Ui.sub(this, "Pick a small habit. Money checks your payments every day and keeps the streak — nothing to tick.");
        intro.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
        box.addView(intro);
        for (int i = 0; i < a.length(); i++) box.addView(card(a.optJSONObject(i), all), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        TextView add = Ui.button(this, "＋  Start a challenge", true);
        add.setOnClickListener(v -> pick());
        box.addView(add);
    }

    /** 52-week challenge: week n puts aside ₹10 × n. */
    private View week52(JSONObject ch) {
        int done = ch.optInt("ticks");
        long start = Fmt.startOfDay(ch.optLong("start"));
        int week = (int) Math.min(52, (System.currentTimeMillis() - start) / (7 * GoalMath.DAY) + 1);
        long saved = 1000L * done * (done + 1) / 2;
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, "📈", 0xFF0E9F6E, 44));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, "52-week saving", 16, Ui.BOLD));
        col.addView(Ui.sub(this, "Week " + week + " of 52 · " + done + " done · " + Fmt.rs(saved) + " saved"));
        h.addView(col, Ui.weight1());
        c.addView(h);
        Charts.Progress pr = new Charts.Progress(this);
        pr.set(done / 52f, 0xFF0E9F6E);
        c.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 10));
        if (done < 52) {
            long next = 1000L * (done + 1);
            boolean behind = done + 1 < week;
            TextView b = Ui.chip(this, "I put aside " + Fmt.rs(next) + " (week " + (done + 1) + ")", true);
            b.setOnClickListener(v -> {
                JSONArray a = list(this);
                for (int i = 0; i < a.length(); i++) if (ch.optString("id").equals(a.optJSONObject(i).optString("id"))) {
                    try { a.optJSONObject(i).put("ticks", done + 1); } catch (Exception ignored) { }
                }
                Store.get(this).putList(KEY, a);
                Ui.toast(this, done + 1 == 52 ? "52 weeks done — " + Fmt.rs(1378000) + " saved! 🏆" : "Week " + (done + 1) + " done ✓");
                build();
            });
            LinearLayout r = Ui.hbox(this);
            r.addView(b);
            c.addView(r);
            if (behind) { TextView n = Ui.sub(this, "You're a few weeks behind — catch up any time."); n.setPadding(0, Ui.dp(this, 6), 0, 0); c.addView(n); }
        } else c.addView(Ui.text(this, "🏆 Done — ₹13,780 saved", 15, Ui.col(this, R.color.success), Ui.BOLD));
        c.setOnLongClickListener(v -> {
            new Ui.Popup(this).setTitle("Stop the 52-week challenge?").setPositiveButton("Remove", (d, w) -> {
                JSONArray a = list(this), out = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (!ch.optString("id").equals(a.optJSONObject(i).optString("id"))) out.put(a.optJSONObject(i));
                Store.get(this).putList(KEY, out);
                build();
            }).setNegativeButton("Keep going", null).show();
            return true;
        });
        return c;
    }

    private View card(JSONObject ch, List<Txn> all) {
        if ("week52".equals(ch.optString("type"))) return week52(ch);
        String[] t = type(ch.optString("type"));
        int[] st = streak(ch, all);
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, t[1], 0xFFF97316, 44));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        String title = "daily".equals(ch.optString("type")) ? "Under " + Fmt.plain(ch.optLong("limit")) + " a day" : t[2];
        col.addView(Ui.text(this, title, 16, Ui.BOLD));
        col.addView(Ui.sub(this, "Best: " + st[1] + (st[1] == 1 ? " day" : " days") + " · since " + Fmt.date(ch.optLong("start"))));
        h.addView(col, Ui.weight1());
        LinearLayout fire = Ui.vbox(this);
        fire.setGravity(Gravity.CENTER);
        fire.addView(Ui.text(this, "🔥 " + st[0], 22, Ui.EXTRABOLD));
        fire.addView(Ui.text(this, st[0] == 1 ? "day" : "days", 11, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        h.addView(fire);
        c.addView(h);
        // last 14 days
        LinearLayout dots = Ui.hbox(this);
        dots.setPadding(0, Ui.dp(this, 12), 0, 0);
        long today = Fmt.startOfDay(System.currentTimeMillis());
        long start = Fmt.startOfDay(ch.optLong("start"));
        for (int i = 13; i >= 0; i--) {
            long d = Fmt.addDays(today, -i);
            View dot = new View(this);
            int col2 = d < start ? Ui.col(this, R.color.surface2) : dayOk(ch, all, d) ? 0xFF22C55E : 0xFFEF4444;
            if (d == today && dayOk(ch, all, d)) col2 = 0xFF86EFAC;
            dot.setBackground(Ui.round(col2, Ui.dp(this, 4)));
            dots.addView(dot, Ui.margins(new LinearLayout.LayoutParams(0, Ui.dp(this, 12), 1), this, 2, 0, 2, 0));
        }
        c.addView(dots);
        TextView lbl = Ui.text(this, "Last 14 days", 11, Ui.col(this, R.color.hint), Ui.REGULAR);
        lbl.setPadding(Ui.dp(this, 2), Ui.dp(this, 4), 0, 0);
        c.addView(lbl);
        // keep best
        if (st[1] > ch.optInt("best")) {
            JSONArray a = list(this);
            for (int i = 0; i < a.length(); i++) if (ch.optString("id").equals(a.optJSONObject(i).optString("id"))) {
                try { a.optJSONObject(i).put("best", st[1]); } catch (Exception ignored) { }
            }
            Store.get(this).putList(KEY, a);
        }
        c.setOnLongClickListener(v -> {
            new Ui.Popup(this).setTitle("Stop “" + title + "”?").setPositiveButton("Remove", (d, w) -> {
                JSONArray a = list(this), out = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (!ch.optString("id").equals(a.optJSONObject(i).optString("id"))) out.put(a.optJSONObject(i));
                Store.get(this).putList(KEY, out);
                build();
            }).setNegativeButton("Keep going", null).show();
            return true;
        });
        return c;
    }

    private void pick() {
        String[] names = new String[TYPES.length];
        for (int i = 0; i < TYPES.length; i++) names[i] = TYPES[i][1] + "  " + TYPES[i][2] + "\n" + TYPES[i][3];
        new Ui.Popup(this).setTitle("Start a challenge").setItems(names, (d, w) -> {
            if ("daily".equals(TYPES[w][0])) {
                LinearLayout b = Ui.vbox(this);
                int p = Ui.dp(this, 24);
                b.setPadding(p, 0, p, 0);
                EditText e = new EditText(this);
                e.setInputType(InputType.TYPE_CLASS_NUMBER);
                e.setHint(Lang.t("e.g. 500"));
                e.setTypeface(Ui.face(this, Ui.BOLD));
                b.addView(e);
                new Ui.Popup(this).setTitle("Under how much a day? (₹)").setView(b)
                        .setPositiveButton("Start", (x, y) -> {
                            long v = SmsParser.paise(e.getText().toString().trim());
                            if (v > 0) start("daily", v);
                        }).setNegativeButton("Cancel", null).show();
            } else start(TYPES[w][0], 0);
        }).show();
    }

    private void start(String type, long limit) {
        begin(this, type, limit);
        Ui.toast(this, "Started — good luck! 🔥");
        build();
    }

    /** Start a challenge from anywhere (e.g. the Leak finder). Returns false if it's already running. */
    static boolean begin(Context c, String type, long limit) {
        JSONArray a = list(c);
        for (int i = 0; i < a.length(); i++) if (type.equals(a.optJSONObject(i).optString("type")) && !"daily".equals(type)) return false;
        try {
            a.put(new JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("type", type).put("limit", limit)
                    .put("start", Fmt.startOfDay(System.currentTimeMillis())).put("best", 0));
        } catch (Exception ignored) { }
        Store.get(c).putList(KEY, a);
        return true;
    }
}
