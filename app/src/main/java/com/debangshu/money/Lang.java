package com.debangshu.money;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app's languages: English, हिंदी, বাংলা.
 *
 * Every text on screen passes through {@link #t}. Translations live in assets/lang/strings.tsv:
 * {@code English<TAB>Hindi<TAB>Bengali}, one per line, "\n" for new lines. "{}" in the English marks a changing part
 * (an amount, a number, a name); in a translation "{}" fills them in order, or "{0}", "{1}" pick them by position.
 * A text with no translation yet simply stays in English.
 */
final class Lang {

    private Lang() {}

    static final String[] CODES = {"en", "hi", "bn"};
    static final String[] NAMES = {"English", "हिंदी", "বাংলা"};

    private static String code = "en";
    private static Map<String, String> exact = new HashMap<>();
    private static final List<Object[]> templates = new ArrayList<>();          // {Pattern, translation}
    private static final Map<String, List<Object[]>> byStart = new HashMap<>(), byEnd = new HashMap<>();
    private static final Map<String, String> cache = new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 1500; }
    };

    static String code() { return code; }

    static boolean english() { return "en".equals(code); }

    /** Speech-recognition language for this app language. */
    static String speech() { return "hi".equals(code) ? "hi-IN" : "bn".equals(code) ? "bn-IN" : "en-IN"; }

    /** Read the chosen language and load its words (call once at start-up). */
    static synchronized void init(Context c) {
        String want = Store.get(c).prefs().getString("lang", "en");
        if (!want.equals(code) || (exact.isEmpty() && !"en".equals(want))) load(c, want);
    }

    static synchronized void set(Context c, String lang) {
        Store.get(c).prefs().edit().putString("lang", lang).apply();
        load(c, lang);
    }

    private static void load(Context c, String lang) {
        code = lang;
        exact = new HashMap<>();
        templates.clear();
        byStart.clear();
        byEnd.clear();
        cache.clear();
        if ("en".equals(lang)) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("lang/strings.tsv"), "UTF-8"))) {
            read(r, lang);
        } catch (Exception ignored) { }
    }

    /** Load words from a reader (also used by the desktop tests). */
    static synchronized void read(BufferedReader r, String lang) throws java.io.IOException {
        code = lang;
        int col = "hi".equals(lang) ? 1 : 2;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            if (p.length <= col || p[col].trim().isEmpty()) continue;
            add(p[0].replace("\\n", "\n"), p[col].replace("\\n", "\n"));
        }
    }

    private static void add(String en, String tr) {
        if (!en.contains("{}")) {
            exact.put(en, tr);
            exact.put(en.trim(), tr.trim());
            // pieces chosen by a ternary in code (" payments need a look") usually follow a number
            if (en.startsWith(" ") && !en.trim().isEmpty()) template("{}" + en, "{}" + (tr.startsWith(" ") ? tr : " " + tr));
            if (en.endsWith(" ") && !en.trim().isEmpty()) template(en + "{}", (tr.endsWith(" ") ? tr : tr + " ") + "{}");
            return;
        }
        template(en, tr);
    }

    private static void template(String en, String tr) {
        String[] parts = en.split("\\{\\}", -1);
        StringBuilder re = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) re.append("(.+?)");
            re.append(Pattern.quote(parts[i]));
        }
        re.append("$");
        Object[] t = {Pattern.compile(re.toString(), Pattern.DOTALL), tr, parts.length - 1};
        templates.add(t);
        String head = parts[0].trim(), tail = parts[parts.length - 1].trim();
        if (head.length() >= 2) index(byStart, head.substring(0, Math.min(3, head.length())), t);
        else if (tail.length() >= 2) index(byEnd, tail.substring(Math.max(0, tail.length() - 3)), t);
        else index(byStart, "", t);
    }

    private static void index(Map<String, List<Object[]>> m, String k, Object[] t) {
        List<Object[]> l = m.get(k);
        if (l == null) m.put(k, l = new ArrayList<>());
        l.add(t);
    }

    /** Translate a text shown on screen. */
    static CharSequence t(CharSequence s) {
        if (s == null || english() || !(s instanceof String)) return s;
        return t((String) s);
    }

    static synchronized String t(String s) {
        if (s == null || english() || s.isEmpty()) return s;
        String c = cache.get(s);
        if (c != null) return c;
        String out = translate(s, 0);
        out = dates(out);
        cache.put(s, out);
        return out;
    }

    private static String translate(String s, int depth) {
        String e = exact.get(s);
        if (e != null) return e;
        String trimmed = s.trim();
        if (!trimmed.equals(s)) {
            e = exact.get(trimmed);
            if (e != null) return s.substring(0, s.indexOf(trimmed)) + e + s.substring(s.indexOf(trimmed) + trimmed.length());
        }
        String m = match(s, depth);
        if (m != null) return m;
        if (depth > 2) return s;
        // split into lines, then into " · " / " — " pieces, and translate each
        for (String sep : new String[]{"\n", "  ·  ", " · ", " — "}) {
            if (!s.contains(sep)) continue;
            String[] parts = s.split(Pattern.quote(sep), -1);
            StringBuilder b = new StringBuilder();
            boolean changed = false;
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) b.append(sep);
                String tp = parts[i].isEmpty() ? parts[i] : translate(parts[i], depth + 1);
                if (!tp.equals(parts[i])) changed = true;
                b.append(tp);
            }
            if (changed) return b.toString();
        }
        return s;
    }

    private static String match(String s, int depth) {
        String t = s.trim();
        List<Object[]> cand = new ArrayList<>();
        if (t.length() >= 2) {
            List<Object[]> a = byStart.get(t.substring(0, Math.min(3, t.length())));
            if (a != null) cand.addAll(a);
            List<Object[]> b = byEnd.get(t.substring(Math.max(0, t.length() - 3)));
            if (b != null) cand.addAll(b);
        }
        List<Object[]> any = byStart.get("");
        if (any != null) cand.addAll(any);
        for (Object[] tp : cand) {
            Matcher m = ((Pattern) tp[0]).matcher(s);
            if (!m.matches()) { m = ((Pattern) tp[0]).matcher(t); if (!m.matches()) continue; }
            String[] g = new String[m.groupCount()];
            for (int i = 0; i < g.length; i++) {
                String x = m.group(i + 1);
                g[i] = depth < 2 && x.matches(".*[A-Za-z]{3,}.*") ? translate(x, depth + 1) : x;
            }
            return fill((String) tp[1], g);
        }
        return null;
    }

    private static String fill(String tr, String[] g) {
        StringBuilder b = new StringBuilder();
        int next = 0;
        for (int i = 0; i < tr.length(); i++) {
            char ch = tr.charAt(i);
            if (ch == '{') {
                int j = tr.indexOf('}', i);
                if (j > i) {
                    String in = tr.substring(i + 1, j);
                    int idx = in.isEmpty() ? next++ : parse(in);
                    if (idx >= 0 && idx < g.length) { b.append(g[idx]); i = j; continue; }
                }
            }
            b.append(ch);
        }
        return b.toString();
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }

    /** "Language" chooser: English / हिंदी / বাংলা. Restarts the app in the new language. */
    static void choose(android.app.Activity a) {
        int cur = 0;
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) cur = i;
        String[] items = {"English", "हिंदी (Hindi)", "বাংলা (Bengali)"};
        new android.app.AlertDialog.Builder(a).setTitle("Language · भाषा · ভাষা").setSingleChoiceItems(items, cur, (d, w) -> {
            d.dismiss();
            if (CODES[w].equals(code)) return;
            set(a, CODES[w]);
            restart(a);
        }).show();
    }

    static void restart(android.app.Activity a) {
        android.content.Intent i = new android.content.Intent(a, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    /** Small row of language buttons (for the welcome tour). */
    static android.view.View chips(android.app.Activity a, Runnable after) {
        android.widget.LinearLayout r = Ui.hbox(a);
        r.setGravity(android.view.Gravity.CENTER);
        for (int i = 0; i < CODES.length; i++) {
            boolean on = CODES[i].equals(code);
            android.widget.TextView t = new android.widget.TextView(a);
            t.setText(NAMES[i]);
            t.setTextSize(14);
            t.setTypeface(Ui.face(a, Ui.SEMIBOLD));
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 7), Ui.dp(a, 14), Ui.dp(a, 7));
            t.setTextColor(on ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text));
            t.setBackground(Ui.rippleRound(a, on ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 18));
            final String c = CODES[i];
            t.setOnClickListener(v -> { if (!c.equals(code)) { set(a, c); if (after != null) after.run(); } });
            r.addView(t, Ui.margins(Ui.lp(-2, -2), a, 4, 0, 4, 0));
        }
        return r;
    }

    // ------------------------------------------------------------------ months and days

    private static final String[] EN_MON = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    private static final String[][] MON = {
            {"जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"},
            {"জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"}};
    private static final String[] EN_DAY = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[][] DAY = {{"रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि"}, {"রবি", "সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি"}};
    private static Pattern monthWord;

    /** "14 Nov 2026" → "14 नवंबर 2026"; "Fri, 13 Nov" → "शुक्र, 13 नवंबर". */
    private static String dates(String s) {
        int L = "hi".equals(code) ? 0 : 1;
        if (monthWord == null) {
            StringBuilder b = new StringBuilder("\\b(");
            for (int i = 0; i < 12; i++) { if (i > 0) b.append('|'); b.append(EN_MON[i]).append('|').append(EN_MON[i], 0, 3); }
            b.append(")\\b");
            monthWord = Pattern.compile(b.toString());
        }
        Matcher m = monthWord.matcher(s);
        StringBuffer out = new StringBuffer();
        boolean any = false;
        while (m.find()) {
            String w = m.group(1);
            int i = 0;
            for (; i < 12; i++) if (EN_MON[i].equals(w) || EN_MON[i].substring(0, 3).equals(w)) break;
            // only when it looks like a date: next to a number or a year, or alone
            int st = m.start(), en = m.end();
            boolean dateLike = (st > 0 && Character.isDigit(s.charAt(Math.max(0, st - 2)))) || (en + 1 < s.length() && Character.isDigit(s.charAt(en + 1)))
                    || s.trim().equals(w) || (en < s.length() && s.charAt(en) == '\'');
            m.appendReplacement(out, Matcher.quoteReplacement(i < 12 && dateLike ? MON[L][i] : w));
            any |= dateLike;
        }
        m.appendTail(out);
        String r = any ? out.toString() : s;
        for (int i = 0; i < 7; i++) if (r.startsWith(EN_DAY[i] + ", ")) r = DAY[L][i] + r.substring(3);
        return r;
    }
}
