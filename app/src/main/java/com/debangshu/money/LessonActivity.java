package com.debangshu.money;

import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/** One 1-minute lesson: the story, your own numbers, why it's true (with sources), Try it, videos. */
public class LessonActivity extends Screen {

    private JSONObject l;

    @Override String title() {
        l = Learn.lesson(this, getIntent().getStringExtra("id") == null ? "" : getIntent().getStringExtra("id"));
        JSONObject g = l == null ? null : Learn.group(this, l.optString("g"));
        return g == null ? "Learn" : Learn.s(g, "title");
    }

    @Override
    void build() {
        if (l == null) { finish(); return; }
        String id = l.optString("id");
        JSONObject grp = Learn.group(this, l.optString("g"));
        int color = col(l.optString("color", grp == null ? "#0B6F86" : grp.optString("color", "#0B6F86")));

        // ---- title
        LinearLayout head = Ui.hbox(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, Ui.dp(this, 4));
        head.addView(Ui.iconBadge(this, l.optString("emoji", "📘"), color, 48));
        LinearLayout ht = Ui.vbox(this);
        ht.setPadding(Ui.dp(this, 14), 0, 0, 0);
        ht.addView(Learn.body(this, Learn.s(l, "title"), 21, Ui.col(this, R.color.text), Ui.EXTRABOLD));
        ht.addView(Ui.text(this, Learn.done(this, id) ? "✓ Learned  ·  1 minute" : "1 minute", 12.5f,
                Ui.col(this, Learn.done(this, id) ? R.color.success : R.color.text2), Ui.SEMIBOLD));
        head.addView(ht, Ui.weight1());
        add(head);

        // ---- the story
        LinearLayout story = Ui.card(this);
        story.addView(Learn.body(this, Learn.s(l, "story"), 16, Ui.col(this, R.color.text), Ui.REGULAR));
        add(story);

        // ---- with your numbers
        String ex = Learn.s(l, "ex");
        if (!ex.isEmpty()) {
            Map<String, String> v = Learn.vars(this);
            boolean yours = Learn.yours(this);
            LinearLayout c = Ui.vbox(this);
            c.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            c.setBackground(Ui.round(0x1A0B6F86, Ui.dp(this, 20)));
            c.addView(Ui.text(this, yours ? "🧮  With your numbers" : "🧮  An example", 14, Ui.col(this, R.color.accent), Ui.BOLD));
            TextView t = Learn.body(this, Learn.fill(ex, v), 15.5f, Ui.col(this, R.color.text), Ui.SEMIBOLD);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(t);
            TextView note = Ui.text(this, yours ? "From your own salary and spending in Money. Rates are examples, not promises."
                    : "Someone earning ₹30,000 and spending ₹20,000 a month. Money uses your own numbers once it has seen your salary.",
                    11.5f, Ui.col(this, R.color.text2), Ui.REGULAR);
            note.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(note);
            add(c);
        }

        // ---- why this is true
        JSONArray proof = l.optJSONArray("proof");
        if (proof != null && proof.length() > 0) {
            add(Ui.label(this, "✅  Why this is true"));
            LinearLayout pc = Ui.card(this);
            for (int i = 0; i < proof.length(); i++) {
                JSONObject p = proof.optJSONObject(i);
                if (p == null) continue;
                if (i > 0) pc.addView(Ui.divider(this), Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 10));
                pc.addView(Learn.body(this, Learn.s(p, "t"), 14.5f, Ui.col(this, R.color.text), Ui.REGULAR));
                String src = p.optString("src"), url = p.optString("url");
                if (!src.isEmpty()) {
                    TextView s = Ui.text(this, Lang.t("Source:") + " " + src + (url.isEmpty() ? "" : "  ↗"), 12.5f,
                            Ui.col(this, url.isEmpty() ? R.color.text2 : R.color.accent), Ui.BOLD);
                    s.setPadding(0, Ui.dp(this, 6), 0, 0);
                    if (!url.isEmpty()) s.setOnClickListener(v -> Learn.openUrl(this, url));
                    pc.addView(s);
                }
            }
            add(pc);
        }

        // ---- remember
        String tip = Learn.s(l, "tip");
        if (!tip.isEmpty()) {
            LinearLayout tc = Ui.hbox(this);
            tc.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            tc.setBackground(Ui.round(0x1FF59E0B, Ui.dp(this, 18)));
            tc.addView(Ui.text(this, "💡", 18, Ui.REGULAR));
            TextView tt = Learn.body(this, tip, 15, Ui.col(this, R.color.text), Ui.BOLD);
            tt.setPadding(Ui.dp(this, 10), 0, 0, 0);
            tc.addView(tt, Ui.weight1());
            add(tc);
        }

        // ---- actions
        String calc = l.optString("calc");
        String tl = Learn.tryLabel(calc);
        if (tl != null) {
            TextView tb = Ui.button(this, "▶  " + Lang.t("Try it:") + " " + Lang.t(tl), true);
            tb.setOnClickListener(v -> { Learn.markDone(this, id); Learn.tryIt(this, calc); });
            add(tb);
        }
        TextView vb = Ui.button(this, "📺  " + Lang.t("Watch videos in your language"), false);
        vb.setOnClickListener(v -> Learn.videos(this, l));
        add(vb);

        JSONObject nx = Learn.after(this, id);
        TextView done = Ui.button(this, nx == null ? "✓  Got it" : "✓  Got it — next lesson", true);
        done.setOnClickListener(v -> {
            Learn.markDone(this, id);
            if (nx != null) startActivity(new Intent(this, LessonActivity.class).putExtra("id", nx.optString("id")));
            else Ui.toast(this, "All lessons done 🎓");
            finish();
        });
        add(done);

        TextView d = Ui.text(this, "For learning, not financial advice. Facts checked in November 2026 — rules and rates change, so tap a source to see the latest.",
                11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), 0);
        add(d);
    }

    private static int col(String hex) {
        try { return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16); } catch (Exception e) { return 0xFF0B6F86; }
    }
}
