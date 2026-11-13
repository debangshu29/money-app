package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.text.SpannableString;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The money quiz (10 questions, each answer explained) and flashcards of money words. */
public class QuizActivity extends Screen {

    static void open(Activity a, boolean cards) { a.startActivity(new Intent(a, QuizActivity.class).putExtra("cards", cards)); }

    private boolean cards;
    private List<JSONObject> items;
    private int pos, picked = -1, score;
    private boolean flipped;

    @Override String title() {
        cards = getIntent().getBooleanExtra("cards", false);
        return cards ? "Flashcards" : "Money quiz";
    }

    @Override
    void build() {
        if (items == null) start();
        if (items.isEmpty()) { finish(); return; }
        if (cards) card(); else question();
    }

    private void start() {
        items = new ArrayList<>(Learn.list(this, cards ? "words" : "quiz"));
        Collections.shuffle(items);
        if (!cards && items.size() > 10) items = new ArrayList<>(items.subList(0, 10));
        pos = 0; picked = -1; score = 0; flipped = false;
    }

    // ------------------------------------------------------------------ quiz

    private void question() {
        if (pos >= items.size()) { result(); return; }
        JSONObject q = items.get(pos);
        add(Ui.text(this, Lang.t("Question " + (pos + 1) + " of " + items.size()), 13, Ui.col(this, R.color.text2), Ui.BOLD));
        add(LearnActivity.meter(this, pos / (float) items.size(), 0xFF8B5CF6));
        LinearLayout qc = Ui.card(this);
        qc.addView(Learn.body(this, Learn.s(q, "q"), 18, Ui.col(this, R.color.text), Ui.EXTRABOLD));
        add(qc);
        JSONArray o = q.optJSONArray("o");
        int right = q.optInt("a");
        for (int i = 0; o != null && i < o.length(); i++) {
            int k = i;
            String txt = Learn.s(o.optJSONObject(i));
            TextView b = Ui.text(this, new SpannableString((picked < 0 ? "" : i == right ? "✓  " : i == picked ? "✗  " : "") + txt), 15.5f,
                    Ui.col(this, R.color.text), Ui.SEMIBOLD);
            b.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            int bg = picked < 0 ? Ui.col(this, R.color.surface) : i == right ? 0x3316A34A : i == picked ? 0x33DC2626 : Ui.col(this, R.color.surface);
            b.setBackground(Ui.rippleRound(this, bg, 16));
            if (picked < 0) b.setOnClickListener(v -> { picked = k; if (k == right) score++; rebuild(); });
            add(b);
        }
        if (picked >= 0) {
            LinearLayout w = Ui.vbox(this);
            w.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            w.setBackground(Ui.round(picked == right ? 0x1F16A34A : 0x1FF59E0B, Ui.dp(this, 18)));
            w.addView(Ui.text(this, picked == right ? "🎉  Right!" : "💡  Not quite", 15, Ui.col(this, R.color.text), Ui.BOLD));
            TextView why = Learn.body(this, Learn.s(q, "why"), 14.5f, Ui.col(this, R.color.text), Ui.REGULAR);
            why.setPadding(0, Ui.dp(this, 6), 0, 0);
            w.addView(why);
            String lid = q.optString("lesson");
            if (!lid.isEmpty() && Learn.lesson(this, lid) != null) {
                TextView lk = Ui.text(this, "Open the lesson  →", 13.5f, Ui.col(this, R.color.accent), Ui.BOLD);
                lk.setPadding(0, Ui.dp(this, 8), 0, 0);
                lk.setOnClickListener(v -> LearnActivity.lesson(this, lid));
                w.addView(lk);
            }
            add(w);
            TextView next = Ui.button(this, pos + 1 < items.size() ? "Next question" : "See my score", true);
            next.setOnClickListener(v -> { pos++; picked = -1; rebuild(); });
            add(next);
        }
    }

    private void result() {
        Learn.quizScore(this, score);
        LinearLayout c = Ui.card(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(Ui.text(this, score >= 8 ? "🏆" : score >= 5 ? "👏" : "🌱", 44, Ui.REGULAR));
        TextView s = Ui.text(this, score + " / " + items.size(), 32, Ui.col(this, R.color.text), Ui.EXTRABOLD);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        TextView m = Ui.text(this, score >= 8 ? "Excellent — you really know your money!" : score >= 5 ? "Good going! A few lessons will make it perfect."
                : "Every expert started here. Read a few lessons and try again.", 14.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(m);
        add(c);
        TextView again = Ui.button(this, "Play again (new questions)", true);
        again.setOnClickListener(v -> { items = null; rebuild(); });
        add(again);
        TextView back = Ui.button(this, "Back to Learn", false);
        back.setOnClickListener(v -> finish());
        add(back);
    }

    // ------------------------------------------------------------------ flashcards

    private void card() {
        JSONObject w = items.get(pos % items.size());
        add(Ui.text(this, (pos % items.size() + 1) + " / " + items.size(), 13, Ui.col(this, R.color.text2), Ui.BOLD));
        LinearLayout c = Ui.vbox(this);
        c.setGravity(Gravity.CENTER);
        c.setMinimumHeight(Ui.dp(this, 260));
        c.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22));
        c.setBackground(Ui.rippleRound(this, flipped ? 0x1F0E9F6E : Ui.col(this, R.color.surface), 24));
        JSONArray forms = w.optJSONObject("w") == null ? null : w.optJSONObject("w").optJSONArray(Lang.code());
        if (forms == null && w.optJSONObject("w") != null) forms = w.optJSONObject("w").optJSONArray("en");
        String name = forms == null || forms.length() == 0 ? w.optString("id") : forms.optString(0);
        TextView front = Ui.text(this, new SpannableString(name), flipped ? 18 : 30, Ui.col(this, R.color.text), Ui.EXTRABOLD);
        front.setGravity(Gravity.CENTER);
        c.addView(front);
        if (flipped) {
            TextView m = Ui.text(this, new SpannableString(Learn.s(w, "m")), 17, Ui.col(this, R.color.text), Ui.SEMIBOLD);
            m.setGravity(Gravity.CENTER);
            m.setLineSpacing(Ui.dp(this, 3), 1f);
            m.setPadding(0, Ui.dp(this, 14), 0, 0);
            c.addView(m);
        } else {
            TextView hint = Ui.text(this, "Tap to see the meaning", 13, Ui.col(this, R.color.accent), Ui.SEMIBOLD);
            hint.setPadding(0, Ui.dp(this, 16), 0, 0);
            c.addView(hint);
        }
        c.setOnClickListener(v -> { flipped = !flipped; rebuild(); });
        add(c);
        LinearLayout nav = Ui.hbox(this);
        TextView prev = Ui.button(this, "←  Back", false);
        prev.setOnClickListener(v -> { pos = (pos - 1 + items.size()) % items.size(); flipped = false; rebuild(); });
        nav.addView(prev, Ui.weight1());
        TextView next = Ui.button(this, "Next  →", true);
        next.setOnClickListener(v -> { pos++; flipped = false; rebuild(); });
        nav.addView(next, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, 10, 0, 0, 0));
        add(nav);
    }
}
