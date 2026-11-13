package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Learn: one-minute lessons, myth busters, quiz, money words, scam alerts and a 7-day course.
 * All the words live in assets/learn/learn.json in English, Hindi and Bengali; every lesson carries
 * "why this is true" facts with their official source.
 */
final class Learn {

    private Learn() {}

    private static JSONObject data;

    static synchronized JSONObject data(Context c) {
        if (data != null) return data;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("learn/learn.json"), "UTF-8"))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
            data = new JSONObject(b.toString());
        } catch (Exception e) {
            data = new JSONObject();
        }
        return data;
    }

    /** The text of a {"en":…, "hi":…, "bn":…} object in the app's language. */
    static String s(JSONObject t) {
        if (t == null) return "";
        String v = t.optString(Lang.code(), "");
        return v.isEmpty() ? t.optString("en", "") : v;
    }

    static String s(JSONObject o, String key) { return s(o.optJSONObject(key)); }

    static List<JSONObject> list(Context c, String key) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = data(c).optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null) out.add(o); }
        return out;
    }

    static List<JSONObject> lessons(Context c) { return list(c, "lessons"); }

    static List<JSONObject> lessons(Context c, String group) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject l : lessons(c)) if (group.equals(l.optString("g"))) out.add(l);
        return out;
    }

    static JSONObject lesson(Context c, String id) {
        for (JSONObject l : lessons(c)) if (id.equals(l.optString("id"))) return l;
        return null;
    }

    static JSONObject group(Context c, String id) {
        for (JSONObject g : list(c, "groups")) if (id.equals(g.optString("id"))) return g;
        return null;
    }

    static JSONObject word(Context c, String id) {
        for (JSONObject w : list(c, "words")) if (id.equals(w.optString("id"))) return w;
        return null;
    }

    // ------------------------------------------------------------------ progress

    private static final String KEY = "learn";

    static JSONObject prog(Context c) { return Store.get(c).obj(KEY); }

    private static void save(Context c, JSONObject p) { Store.get(c).putObj(KEY, p); }

    static boolean done(Context c, String id) {
        JSONObject d = prog(c).optJSONObject("done");
        return d != null && d.has(id);
    }

    static int doneCount(Context c) {
        JSONObject d = prog(c).optJSONObject("done");
        return d == null ? 0 : d.length();
    }

    static void markDone(Context c, String id) {
        JSONObject p = prog(c);
        try {
            JSONObject d = p.optJSONObject("done");
            if (d == null) p.put("done", d = new JSONObject());
            if (!d.has(id)) d.put(id, System.currentTimeMillis());
        } catch (Exception ignored) { }
        save(c, p);
        if (doneCount(c) >= 20) Goals.award(c, "reader");
        if (courseDay(c) >= 1 && courseLeft(c) == 0) Goals.award(c, "learner");
    }

    static void quizScore(Context c, int score) {
        JSONObject p = prog(c);
        try {
            p.put("quizLast", score);
            if (score > p.optInt("quizBest", -1)) p.put("quizBest", score);
        } catch (Exception ignored) { }
        save(c, p);
        if (score >= 8) Goals.award(c, "quiz");
    }

    /** The next lesson worth reading: today's course lesson, else the first one not done yet. */
    static JSONObject next(Context c) {
        int day = courseDay(c);
        if (day >= 1 && day <= 7) for (String id : dayLessons(c, day)) if (!done(c, id)) return lesson(c, id);
        for (JSONObject l : lessons(c)) if (!done(c, l.optString("id"))) return l;
        List<JSONObject> all = lessons(c);
        return all.isEmpty() ? null : all.get((int) (System.currentTimeMillis() / 86400000L % all.size()));
    }

    /** The lesson after this one in the same topic, or the first of the next topic. */
    static JSONObject after(Context c, String id) {
        List<JSONObject> all = lessons(c);
        for (int i = 0; i < all.size() - 1; i++) if (id.equals(all.get(i).optString("id"))) return all.get(i + 1);
        return null;
    }

    // ------------------------------------------------------------------ the 7-day course

    static String[] dayLessons(Context c, int day) {
        JSONArray days = data(c).optJSONArray("course");
        if (days == null || day < 1 || day > days.length()) return new String[0];
        JSONArray d = days.optJSONArray(day - 1);
        String[] out = new String[d == null ? 0 : d.length()];
        for (int i = 0; i < out.length; i++) out[i] = d.optString(i);
        return out;
    }

    /** 0 = not started, 1–7 = that day, 8 = finished. */
    static int courseDay(Context c) {
        long start = prog(c).optLong("courseStart", 0);
        if (start == 0) return 0;
        int day = (int) ((Fmt.startOfDay(System.currentTimeMillis()) - Fmt.startOfDay(start)) / 86400000L) + 1;
        // never run ahead of the lessons: stay on the first day that still has something to read
        for (int d = 1; d < Math.min(day, 8); d++) for (String id : dayLessons(c, d)) if (!done(c, id)) return d;
        if (day > 7) return courseLeft(c) == 0 ? 8 : 7;
        return Math.max(1, day);
    }

    static int courseLeft(Context c) {
        int n = 0;
        for (int d = 1; d <= 7; d++) for (String id : dayLessons(c, d)) if (!done(c, id)) n++;
        return n;
    }

    static void startCourse(Context c) {
        JSONObject p = prog(c);
        try { p.put("courseStart", System.currentTimeMillis()); } catch (Exception ignored) { }
        save(c, p);
    }

    /** From the background job: one gentle nudge a day while the course runs. */
    static void nudge(Context c) {
        int day = courseDay(c);
        if (day < 1 || day > 7) return;
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) < 10 || now.get(Calendar.HOUR_OF_DAY) > 20) return;
        long today = Fmt.startOfDay(System.currentTimeMillis());
        JSONObject p = prog(c);
        if (p.optLong("nudged", 0) >= today) return;
        String[] ids = dayLessons(c, day);
        String first = null;
        for (String id : ids) if (!done(c, id)) { first = id; break; }
        if (first == null) return;
        try { p.put("nudged", today); } catch (Exception ignored) { }
        save(c, p);
        JSONObject l = lesson(c, first);
        String title = Lang.t("Day " + day + " of 7 · money basics");
        Alerts.postIntent(c, Alerts.CH_DAILY, 4040, title, l == null ? Lang.t("Today's 1-minute lesson is ready") : s(l, "title"),
                new Intent(c, LessonActivity.class).putExtra("id", first));
    }

    // ------------------------------------------------------------------ your numbers

    /** True when Money knows your income and spending (else the example uses a sample salary). */
    static boolean yours(Context c) { return MoneyFacts.income(c) > 0 && Goals.monthlySpend(c) > 0; }

    static Map<String, String> vars(Context c) {
        long inc = MoneyFacts.income(c), spend = Goals.monthlySpend(c);
        if (inc <= 0 || spend <= 0) { inc = 3000000; spend = 2000000; }
        double incR = inc / 100.0, spendR = spend / 100.0;
        double save10 = round(incR * 0.10, 100);
        if (save10 < 500) save10 = 500;
        double year = save10 * 12;
        Map<String, String> v = new HashMap<>();
        v.put("inc", rs(incR));
        v.put("spend", rs(spendR));
        v.put("daily", rs(round(spendR / 30, 10)));
        v.put("save10", rs(save10));
        v.put("save20", rs(round(incR * 0.20, 100)));
        v.put("year10", rs(year));
        v.put("year20", rs(round(incR * 0.20, 100) * 12));
        v.put("put10", rs(save10 * 120));
        v.put("put20", rs(save10 * 240));
        v.put("sip10", rs(round(SaveMath.sip(save10, 120, 0.12), 1000)));
        v.put("sip20", rs(round(SaveMath.sip(save10, 240, 0.12), 1000)));
        v.put("sip25", rs(round(SaveMath.sip(save10, 300, 0.12), 1000)));
        v.put("wait5", rs(round(SaveMath.sip(save10, 300, 0.12) - SaveMath.sip(save10, 240, 0.12), 1000)));
        v.put("fee20", rs(round(CalcMath.withFee(save10, 20, 0.12, 0.0) - CalcMath.withFee(save10, 20, 0.12, 0.01), 1000)));
        v.put("fd5", rs(round(CalcMath.fd(year, 0.07, 5), 100)));
        v.put("em3", rs(round(spendR * 3, 1000)));
        v.put("em6", rs(round(spendR * 6, 1000)));
        v.put("spend10", rs(round(spendR * Math.pow(1.06, 10), 100)));
        v.put("spend20", rs(round(spendR * Math.pow(1.06, 20), 100)));
        double ppfYear = Math.min(150000, Math.max(500, year));
        double ppf = 0;
        for (int y = 0; y < 15; y++) ppf = (ppf + ppfYear) * 1.071;
        v.put("ppfYear", rs(ppfYear));
        v.put("ppf15", rs(round(ppf, 1000)));
        v.put("ppfPut", rs(ppfYear * 15));
        double[] trap = CalcMath.cardTrap(spendR, 0.0375, 0.05, 200);
        v.put("card", rs(spendR));
        v.put("cardY", String.valueOf((int) trap[0] / 12));
        v.put("cardM", String.valueOf((int) trap[0] % 12));
        v.put("cardInt", rs(round(trap[1], 100)));
        v.put("cover", rs(round(spendR * 12 * 15, 100000)));
        v.put("cover10", rs(round(incR * 12 * 10, 100000)));
        v.put("spendYear", rs(round(spendR * 12, 1000)));
        return v;
    }

    private static double round(double v, double step) { return Math.round(v / step) * step; }

    private static String rs(double rupees) { return Fmt.rs(Math.round(rupees) * 100); }

    static String fill(String tpl, Map<String, String> v) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < tpl.length()) {
            int a = tpl.indexOf('{', i);
            if (a < 0) { b.append(tpl, i, tpl.length()); break; }
            int z = tpl.indexOf('}', a);
            if (z < 0) { b.append(tpl, i, tpl.length()); break; }
            String key = tpl.substring(a + 1, z);
            String val = v.get(key);
            b.append(tpl, i, a).append(val == null ? tpl.substring(a, z + 1) : val);
            i = z + 1;
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ actions

    static void openUrl(Activity a, String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Ui.toast(a, "No browser found"); }
    }

    /** A YouTube search in the app's language. */
    static void videos(Activity a, JSONObject lesson) {
        String q = s(lesson, "yt");
        if (q.isEmpty()) q = s(lesson, "title");
        openUrl(a, "https://www.youtube.com/results?search_query=" + Uri.encode(q));
    }

    /** The label and action of a lesson's "Try it" button. */
    static String tryLabel(String calc) {
        if (calc == null || calc.isEmpty()) return null;
        switch (calc) {
            case "@rule": return "50 / 30 / 20";
            case "@health": return "Money health";
            case "@leaks": return "Leak finder";
            case "@split": return "Salary split";
            case "@goals": return "New goal";
            case "@future": return "Future you";
            case "@habit": return "Habit price";
            case "@tax": return "Tax helper";
            case "@cards": return "Credit cards";
            case "@loans": return "Loans & EMIs";
            default:
                Calcs.Calc k = Calcs.byId(calc);
                return k == null ? null : k.title;
        }
    }

    static void tryIt(Activity a, String calc) {
        Class<?> screen = null;
        switch (calc) {
            case "@rule": screen = RuleActivity.class; break;
            case "@health": screen = HealthActivity.class; break;
            case "@leaks": screen = LeaksActivity.class; break;
            case "@split": screen = SalarySplitActivity.class; break;
            case "@future": screen = FutureActivity.class; break;
            case "@habit": screen = HabitActivity.class; break;
            case "@tax": screen = TaxActivity.class; break;
            case "@cards": screen = CardsActivity.class; break;
            case "@loans": screen = Loans.class; break;
            case "@goals": GoalSetup.start(a); return;
            default: CalcActivity.open(a, calc); return;
        }
        a.startActivity(new Intent(a, screen));
    }

    // ------------------------------------------------------------------ tap a word

    /** Underlines money words (SIP, FD, inflation…) in a text; tapping one shows what it means. */
    static void link(Activity a, TextView tv) {
        CharSequence cs = tv.getText();
        if (cs == null || cs.length() < 3) return;
        String text = cs.toString();
        SpannableString sp = new SpannableString(cs);
        boolean any = false;
        List<int[]> used = new ArrayList<>();
        for (JSONObject w : list(a, "words")) {
            JSONArray forms = w.optJSONObject("w") == null ? null : w.optJSONObject("w").optJSONArray(Lang.code());
            if (forms == null) forms = w.optJSONObject("w") == null ? null : w.optJSONObject("w").optJSONArray("en");
            if (forms == null) continue;
            for (int f = 0; f < forms.length(); f++) {
                String form = forms.optString(f);
                if (form.length() < 2) continue;
                int at = find(text, form);
                if (at < 0 || overlaps(used, at, at + form.length())) continue;
                used.add(new int[]{at, at + form.length()});
                sp.setSpan(new WordSpan(a, w), at, at + form.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                any = true;
                break;
            }
        }
        if (!any) return;
        tv.setText(sp);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(0x00000000);
    }

    private static boolean overlaps(List<int[]> used, int s, int e) {
        for (int[] u : used) if (s < u[1] && e > u[0]) return true;
        return false;
    }

    /** First whole-word match (letters, digits and Indian vowel signs count as part of a word). */
    static int find(String text, String form) {
        boolean caseless = !form.equals(form.toUpperCase(Locale.ROOT));
        String hay = caseless ? text.toLowerCase(Locale.ROOT) : text;
        String needle = caseless ? form.toLowerCase(Locale.ROOT) : form;
        int from = 0;
        while (true) {
            int i = hay.indexOf(needle, from);
            if (i < 0) return -1;
            int e = i + needle.length();
            boolean startOk = i == 0 || !wordChar(hay.charAt(i - 1));
            boolean endOk = e >= hay.length() || !wordChar(hay.charAt(e)) || suffixOk(hay, e);
            if (startOk && endOk) return i;
            from = i + 1;
        }
    }

    private static boolean wordChar(char ch) {
        int t = Character.getType(ch);
        return Character.isLetterOrDigit(ch) || t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
    }

    /** "SIPs", "FD-তে", "EMI-র" still count as the word. */
    private static boolean suffixOk(String hay, int e) {
        char ch = hay.charAt(e);
        return ch == 's' && (e + 1 >= hay.length() || !wordChar(hay.charAt(e + 1)));
    }

    private static final class WordSpan extends ClickableSpan {
        private final Activity a;
        private final JSONObject w;

        WordSpan(Activity a, JSONObject w) { this.a = a; this.w = w; }

        @Override public void onClick(View v) { showWord(a, w); }

        @Override public void updateDrawState(TextPaint ds) {
            ds.setColor(Ui.col(a, R.color.accent));
            ds.setUnderlineText(true);
            ds.setTypeface(Typeface.create(ds.getTypeface(), Typeface.BOLD));
        }
    }

    static void showWord(Activity a, JSONObject w) {
        JSONArray forms = w.optJSONObject("w") == null ? null : w.optJSONObject("w").optJSONArray(Lang.code());
        if (forms == null && w.optJSONObject("w") != null) forms = w.optJSONObject("w").optJSONArray("en");
        String title = forms == null || forms.length() == 0 ? w.optString("id") : forms.optString(0);
        Ui.Popup p = new Ui.Popup(a);
        p.setTitle(title);
        p.setMessage(s(w, "m"));
        String lid = w.optString("lesson");
        if (!lid.isEmpty() && lesson(a, lid) != null)
            p.setNeutralButton("Open the lesson", (d, x) -> a.startActivity(new Intent(a, LessonActivity.class).putExtra("id", lid)));
        p.setPositiveButton("Got it", null);
        p.show();
    }

    // ------------------------------------------------------------------ small views

    /** Text that is already in the right language (no second translation), with money words linked. */
    static TextView body(Activity a, String s, float sp, int color, int weight) {
        TextView t = Ui.text(a, new SpannableString(s), sp, color, weight);
        t.setLineSpacing(Ui.dp(a, 3), 1f);
        link(a, t);
        return t;
    }
}
