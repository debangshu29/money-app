package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/** Learn: the 7-day course, every lesson by topic, quiz, flashcards, myth busters, money words and scam alerts. */
public class LearnActivity extends Screen {

    static void open(Activity a, String mode) { a.startActivity(new Intent(a, LearnActivity.class).putExtra("mode", mode)); }

    private String mode() { String m = getIntent().getStringExtra("mode"); return m == null ? "" : m; }

    @Override String title() {
        switch (mode()) {
            case "myths": return "Myth busters";
            case "scams": return "Scam alerts";
            case "words": return "Money words";
            default: return "Learn";
        }
    }

    @Override
    void build() {
        switch (mode()) {
            case "myths": myths(); return;
            case "scams": scams(); return;
            case "words": words(); return;
            default: hub();
        }
    }

    // ------------------------------------------------------------------ the hub

    private void hub() {
        List<JSONObject> all = Learn.lessons(this);
        int done = Learn.doneCount(this);

        // progress + lesson to read now
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, done + " / " + all.size(), 26, Ui.col(this, R.color.text), Ui.EXTRABOLD));
        hero.addView(Ui.text(this, "1-minute lessons learned", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(meter(this, all.isEmpty() ? 0 : done / (float) all.size(), 0xFF0E9F6E), Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 12));
        JSONObject next = Learn.next(this);
        if (next != null) {
            TextView b = Ui.button(this, "▶  " + Lang.t(done == 0 ? "Start:" : "Next:") + " " + Learn.s(next, "title"), true);
            b.setOnClickListener(v -> lesson(this, next.optString("id")));
            hero.addView(b);
        }
        add(hero);

        add(course(this));

        // check yourself
        add(Ui.label(this, "Check yourself"));
        LinearLayout g = Ui.card(this);
        g.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        g.addView(Ui.grid(this,
                Ui.action(this, "🧠", 0xFF8B5CF6, "Quiz", v -> QuizActivity.open(this, false)),
                Ui.action(this, "🃏", 0xFF0EA5E9, "Flashcards", v -> QuizActivity.open(this, true)),
                Ui.action(this, "💥", 0xFFF97316, "Myth busters", v -> open(this, "myths")),
                Ui.action(this, "🚨", 0xFFDC2626, "Scam alerts", v -> open(this, "scams"))));
        add(g);
        int best = Learn.prog(this).optInt("quizBest", -1);
        if (best >= 0) {
            TextView q = Ui.text(this, Lang.t("Best quiz score:") + " " + best + " / 10", 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
            q.setPadding(Ui.dp(this, 8), 0, 0, Ui.dp(this, 4));
            add(q);
        }

        // every topic
        for (JSONObject grp : Learn.list(this, "groups")) {
            List<JSONObject> ls = Learn.lessons(this, grp.optString("id"));
            if (ls.isEmpty()) continue;
            int n = 0;
            for (JSONObject l : ls) if (Learn.done(this, l.optString("id"))) n++;
            add(Ui.label(this, Learn.s(grp, "title") + "  ·  " + n + "/" + ls.size()));
            LinearLayout c = Ui.card(this);
            c.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
            int color = color(grp.optString("color"));
            for (JSONObject l : ls) {
                String id = l.optString("id");
                boolean d = Learn.done(this, id);
                c.addView(Ui.navRow(this, l.optString("emoji", "📘"), d ? 0xFF16A34A : color, Learn.s(l, "title"),
                        d ? "✓ " + Lang.t("Learned") : Learn.s(l, "tip"), v -> lesson(this, id)));
            }
            add(c);
        }

        // money words
        LinearLayout wc = Ui.card(this);
        wc.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        wc.addView(Ui.navRow(this, "📖", 0xFF6366F1, "Money words", "SIP, NAV, lock-in, annuity… in one line each", v -> open(this, "words")));
        add(Ui.label(this, "Look up a word"));
        add(wc);

        // official places to learn more
        add(Ui.label(this, "Learn more from the regulators"));
        LinearLayout oc = Ui.card(this);
        oc.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        oc.addView(Ui.navRow(this, "🏛️", 0xFF0B6F86, "RBI Kehta Hai", "Reserve Bank's own safety and money tips", v -> Learn.openUrl(this, "https://rbikehtahai.rbi.org.in")));
        oc.addView(Ui.navRow(this, "📈", 0xFF0E9F6E, "SEBI investor website", "Investing basics from the market regulator", v -> Learn.openUrl(this, "https://investor.sebi.gov.in")));
        oc.addView(Ui.navRow(this, "🛡️", 0xFF14B8A6, "Bima Bharosa (IRDAI)", "Insurance complaints and help", v -> Learn.openUrl(this, "https://bimabharosa.irdai.gov.in")));
        oc.addView(Ui.navRow(this, "🚨", 0xFFDC2626, "Cyber crime: call 1930", "Report fraud fast at cybercrime.gov.in", v -> Learn.openUrl(this, "https://cybercrime.gov.in")));
        add(oc);

        TextView d = Ui.text(this, "For learning, not financial advice. Every lesson shows where its facts come from.", 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
        add(d);
    }

    static void lesson(Activity a, String id) { a.startActivity(new Intent(a, LessonActivity.class).putExtra("id", id)); }

    /** The 7-day "money basics" course card (also used on the Grow tab). */
    static View course(Activity a) {
        LinearLayout c = Ui.card(a);
        int day = Learn.courseDay(a);
        LinearLayout h = Ui.hbox(a);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.iconBadge(a, "🎓", 0xFF8B5CF6, 40));
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 12), 0, 0, 0);
        t.addView(Ui.text(a, "7-day money basics", 16, Ui.EXTRABOLD));
        String sub = day == 0 ? "Two 1-minute lessons a day, with a gentle reminder"
                : day >= 8 ? "Finished 🎉 — you know the basics"
                : Lang.t("Day " + day + " of 7");
        t.addView(Ui.text(a, sub, 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        h.addView(t, Ui.weight1());
        c.addView(h);
        if (day == 0) {
            TextView b = Ui.button(a, "Start the course", true);
            b.setOnClickListener(v -> {
                Learn.startCourse(a);
                String[] ids = Learn.dayLessons(a, 1);
                if (ids.length > 0) lesson(a, ids[0]);
            });
            c.addView(b, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        } else if (day <= 7) {
            c.addView(meter(a, (day - 1) / 7f + 0.01f, 0xFF8B5CF6), Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 4));
            for (String id : Learn.dayLessons(a, day)) {
                JSONObject l = Learn.lesson(a, id);
                if (l == null) continue;
                boolean d = Learn.done(a, id);
                TextView r = Ui.text(a, (d ? "✓  " : "○  ") + Learn.s(l, "title"), 14.5f,
                        Ui.col(a, d ? R.color.success : R.color.text), Ui.SEMIBOLD);
                r.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, Ui.dp(a, 4));
                r.setOnClickListener(v -> lesson(a, id));
                c.addView(r);
            }
        }
        return c;
    }

    static View meter(Activity a, float f, int color) {
        LinearLayout track = Ui.hbox(a);
        track.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 5)));
        View fill = new View(a);
        fill.setBackground(Ui.round(color, Ui.dp(a, 5)));
        float k = Math.max(0, Math.min(1, f));
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(a, 10), k));
        View rest = new View(a);
        track.addView(rest, new LinearLayout.LayoutParams(0, Ui.dp(a, 10), 1 - k));
        return track;
    }

    private static int color(String hex) {
        try { return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16); } catch (Exception e) { return 0xFF0B6F86; }
    }

    // ------------------------------------------------------------------ myths

    private void myths() {
        TextView intro = Ui.sub(this, "Things many people believe about money — and what the facts say. Tap a myth to see the truth.");
        intro.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        add(intro);
        for (JSONObject m : Learn.list(this, "myths")) {
            LinearLayout c = Ui.card(this);
            TextView my = Learn.body(this, "💭  " + Learn.s(m, "myth"), 15.5f, Ui.col(this, R.color.text), Ui.BOLD);
            c.addView(my);
            LinearLayout truth = Ui.vbox(this);
            truth.setVisibility(View.GONE);
            TextView tl = Ui.text(this, "✅  The truth", 13, Ui.col(this, R.color.success), Ui.BOLD);
            tl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
            truth.addView(tl);
            truth.addView(Learn.body(this, Learn.s(m, "truth"), 14.5f, Ui.col(this, R.color.text), Ui.REGULAR));
            proofs(truth, m.optJSONObject("proof"));
            String lid = m.optString("lesson");
            if (!lid.isEmpty() && Learn.lesson(this, lid) != null) {
                TextView lk = Ui.text(this, "Open the lesson  →", 13.5f, Ui.col(this, R.color.accent), Ui.BOLD);
                lk.setPadding(0, Ui.dp(this, 10), 0, 0);
                lk.setOnClickListener(v -> lesson(this, lid));
                truth.addView(lk);
            }
            c.addView(truth);
            TextView tap = Ui.text(this, "Tap to see the truth", 12.5f, Ui.col(this, R.color.accent), Ui.SEMIBOLD);
            tap.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(tap);
            c.setOnClickListener(v -> {
                boolean show = truth.getVisibility() != View.VISIBLE;
                truth.setVisibility(show ? View.VISIBLE : View.GONE);
                tap.setVisibility(show ? View.GONE : View.VISIBLE);
            });
            add(c);
        }
    }

    private void proofs(LinearLayout into, JSONObject p) {
        if (p == null) return;
        TextView pt = Learn.body(this, "📌  " + Learn.s(p, "t"), 13.5f, Ui.col(this, R.color.text2), Ui.REGULAR);
        pt.setPadding(0, Ui.dp(this, 8), 0, 0);
        into.addView(pt);
        String src = p.optString("src"), url = p.optString("url");
        if (!src.isEmpty()) {
            TextView s = Ui.text(this, Lang.t("Source:") + " " + src + (url.isEmpty() ? "" : "  ↗"), 12.5f,
                    Ui.col(this, url.isEmpty() ? R.color.text2 : R.color.accent), Ui.BOLD);
            s.setPadding(0, Ui.dp(this, 4), 0, 0);
            if (!url.isEmpty()) s.setOnClickListener(v -> Learn.openUrl(this, url));
            into.addView(s);
        }
    }

    // ------------------------------------------------------------------ scams

    private void scams() {
        LinearLayout help = Ui.vbox(this);
        help.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        help.setBackground(Ui.round(0x1FDC2626, Ui.dp(this, 20)));
        help.addView(Ui.text(this, "🚨  Cheated? Act in the first hour", 16, Ui.col(this, R.color.text), Ui.EXTRABOLD));
        TextView ht = Ui.text(this, "Call 1930 (national cyber crime helpline) or report at cybercrime.gov.in, then call your bank to block the card / UPI. The sooner you report, the better the chance to freeze the money.",
                14, Ui.col(this, R.color.text), Ui.REGULAR);
        ht.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        help.addView(ht);
        LinearLayout hb = Ui.hbox(this);
        TextView call = Ui.button(this, "📞  1930", true);
        call.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:1930"))); } catch (Exception ignored) { } });
        hb.addView(call, Ui.weight1());
        TextView site = Ui.button(this, "cybercrime.gov.in", false);
        site.setOnClickListener(v -> Learn.openUrl(this, "https://cybercrime.gov.in"));
        hb.addView(site, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, 10, 0, 0, 0));
        help.addView(hb);
        add(help);

        for (JSONObject s : Learn.list(this, "scams")) {
            LinearLayout c = Ui.card(this);
            LinearLayout h = Ui.hbox(this);
            h.setGravity(Gravity.CENTER_VERTICAL);
            h.addView(Ui.iconBadge(this, s.optString("emoji", "⚠️"), 0xFFDC2626, 36));
            TextView t = Learn.body(this, Learn.s(s, "title"), 16, Ui.col(this, R.color.text), Ui.EXTRABOLD);
            t.setPadding(Ui.dp(this, 12), 0, 0, 0);
            h.addView(t, Ui.weight1());
            c.addView(h);
            LinearLayout more = Ui.vbox(this);
            more.setVisibility(View.GONE);
            more.addView(part(Lang.t("How it works"), Learn.s(s, "how")));
            more.addView(part(Lang.t("Stay safe"), Learn.s(s, "do")));
            proofs(more, s.optJSONObject("proof"));
            c.addView(more);
            TextView tap = Ui.text(this, "Tap to see how it works", 12.5f, Ui.col(this, R.color.accent), Ui.SEMIBOLD);
            tap.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(tap);
            c.setOnClickListener(v -> {
                boolean show = more.getVisibility() != View.VISIBLE;
                more.setVisibility(show ? View.VISIBLE : View.GONE);
                tap.setVisibility(show ? View.GONE : View.VISIBLE);
            });
            add(c);
        }
    }

    private View part(String label, String text) {
        LinearLayout p = Ui.vbox(this);
        TextView l = Ui.text(this, label, 13, Ui.col(this, R.color.accent), Ui.BOLD);
        l.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        p.addView(l);
        p.addView(Learn.body(this, text, 14.5f, Ui.col(this, R.color.text), Ui.REGULAR));
        return p;
    }

    // ------------------------------------------------------------------ words

    private void words() {
        TextView intro = Ui.sub(this, "Every money word in one simple line. These words are also underlined in lessons — tap them anywhere.");
        intro.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        add(intro);
        LinearLayout c = Ui.card(this);
        boolean first = true;
        for (JSONObject w : Learn.list(this, "words")) {
            if (!first) c.addView(Ui.divider(this), Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 8));
            first = false;
            org.json.JSONArray forms = w.optJSONObject("w") == null ? null : w.optJSONObject("w").optJSONArray(Lang.code());
            if (forms == null && w.optJSONObject("w") != null) forms = w.optJSONObject("w").optJSONArray("en");
            String name = forms == null || forms.length() == 0 ? w.optString("id") : forms.optString(0);
            c.addView(Ui.text(this, new android.text.SpannableString(name), 15.5f, Ui.col(this, R.color.text), Ui.EXTRABOLD));
            TextView m = Learn.body(this, Learn.s(w, "m"), 14, Ui.col(this, R.color.text2), Ui.REGULAR);
            m.setPadding(0, Ui.dp(this, 2), 0, 0);
            c.addView(m);
            String lid = w.optString("lesson");
            if (!lid.isEmpty() && Learn.lesson(this, lid) != null) {
                TextView lk = Ui.text(this, "Lesson  →", 12.5f, Ui.col(this, R.color.accent), Ui.BOLD);
                lk.setPadding(0, Ui.dp(this, 4), 0, 0);
                lk.setOnClickListener(v -> lesson(this, lid));
                c.addView(lk);
            }
        }
        add(c);
    }
}
