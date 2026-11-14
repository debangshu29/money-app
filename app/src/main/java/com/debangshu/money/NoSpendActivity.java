package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * The no-spend calendar: every day green when nothing was spent on wants (bills, rent, EMIs, insurance, investments,
 * subscriptions and recharges don't count). Streaks, a monthly target, what it kept with your numbers, and why it works.
 */
public class NoSpendActivity extends Screen {

    private long month;   // start of the month shown

    @Override String title() { return "No-spend days"; }

    @Override
    void build() {
        long now = System.currentTimeMillis();
        if (month == 0) month = Fmt.startOfMonth(now);
        List<Txn> all = Store.get(this).txns();
        Motivation.Facts f = Motivation.check(this, false);
        long today = f.today, start = f.start;
        Map<Long, Long> wants = f.wants;
        boolean thisMonth = month == Fmt.startOfMonth(now);
        int free = MotiMath.monthFree(wants, month, start, today);
        int target = Motivation.target(this);

        // ---- the count, streaks, target
        LinearLayout hero = Ui.card(this);
        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.iconBadge(this, "🌱", 0xFF0E9F6E, 52));
        LinearLayout tc = Ui.vbox(this);
        tc.setPadding(Ui.dp(this, 14), 0, 0, 0);
        tc.addView(Ui.text(this, String.valueOf(free), 30, Ui.EXTRABOLD));
        tc.addView(Ui.text(this, Lang.t("no-spend days in " + Lang.t(Fmt.MONTH[Fmt.cal(month).get(Calendar.MONTH)])), 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        top.addView(tc, Ui.weight1());
        hero.addView(top);
        if (thisMonth) {
            LinearLayout chips = Ui.hbox(this);
            chips.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
            chips.addView(pill("🔥", f.cur == 1 ? "1 day streak" : f.cur + " day streak"), Ui.weight1());
            chips.addView(pill("🏆", "Best: " + f.best), Ui.margins(Ui.weight1(), this, 8, 0, 0, 0));
            hero.addView(chips);
            hero.addView(LearnActivity.meter(this, free / (float) Math.max(1, target), 0xFF0E9F6E), Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 6));
            LinearLayout tr = Ui.hbox(this);
            tr.addView(Ui.text(this, free >= target ? "Target reached 🎉" : (target - free) + " more to reach your target",
                    12.5f, Ui.col(this, free >= target ? R.color.success : R.color.text2), Ui.BOLD), Ui.weight1());
            TextView ch = Ui.text(this, Lang.t("Target:") + " " + target + "  ✎", 12.5f, Ui.col(this, R.color.accent), Ui.BOLD);
            ch.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), 0, Ui.dp(this, 4));
            ch.setOnClickListener(v -> pickTarget());
            tr.addView(ch);
            hero.addView(tr);
        }
        add(hero);

        // ---- the calendar
        add(calendar(all, wants, start, today, now));

        // ---- what counts
        add(kid(this, "A no-spend day is a day with no spending on wants — no snacks, no shopping, no food delivery. "
                + "Bills, rent, EMIs, insurance, investments, subscriptions and recharges don't count: they're planned. "
                + "Money adds the green days by itself from your payments."));

        // ---- with your numbers
        long avg = MotiMath.avgWantDay(wants, Fmt.addDays(today, -90), today);
        if (avg > 0) {
            LinearLayout c = numbers(this);
            long kept = avg * free;
            String t = "On days you spend on wants, you spend about " + Fmt.rs(avg) + " (last 90 days)."
                    + "\n" + Lang.t("So these " + free + " green days kept about " + Fmt.rs(kept) + " with you.")
                    + "\n" + Lang.t(target + " no-spend days every month ≈ " + Fmt.rs(avg * target * 12) + " a year.");
            TextView tv = Ui.text(this, t, 15, Ui.col(this, R.color.text), Ui.SEMIBOLD);
            tv.setLineSpacing(Ui.dp(this, 3), 1f);
            tv.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(tv);
            TextView n = Ui.text(this, "About — your real wants spending changes day to day.", 11.5f, Ui.col(this, R.color.text2), Ui.REGULAR);
            n.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(n);
            add(c);
        }

        // ---- why it works
        add(Ui.label(this, "✅  Why this works"));
        LinearLayout pc = Ui.card(this);
        proof(this, pc, "In a study at University College London, 96 people each built one new daily habit. On average it took 66 days "
                + "(between 18 and 254) before it felt automatic — so keep going past the first few weeks.",
                "Lally et al., European Journal of Social Psychology, 2010", "https://doi.org/10.1002/ejsp.674", false);
        proof(this, pc, "The same study found that missing one day did not undo the habit. A spending day breaks the streak, "
                + "not your progress — just start again tomorrow.", "", "", true);
        proof(this, pc, "Simple maths: a rupee you don't spend today is a rupee still with you tonight. "
                + "Your green days above show how much that added up to.", "", "", true);
        add(pc);

        TextView go = Ui.button(this, "🔥  Try a no-spend challenge", false);
        go.setOnClickListener(v -> startActivity(new Intent(this, Challenges.class)));
        add(go);
    }

    private TextView pill(String emoji, String text) {
        TextView t = Ui.text(this, emoji + "  " + Lang.t(text), 13.5f, Ui.col(this, R.color.text), Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 9), Ui.dp(this, 10), Ui.dp(this, 9));
        t.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 16)));
        return t;
    }

    private void pickTarget() {
        int[] opts = {4, 8, 12, 15, 20};
        String[] names = new String[opts.length];
        for (int i = 0; i < opts.length; i++) names[i] = Lang.t(opts[i] + " days");
        new Ui.Popup(this).setTitle("No-spend days a month").setItems(names, (d, w) -> {
            Motivation.setTarget(this, opts[w]);
            rebuild();
        }).show();
    }

    // ------------------------------------------------------------------ the month grid

    private View calendar(List<Txn> all, Map<Long, Long> wants, long start, long today, long now) {
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 14));
        LinearLayout head = Ui.hbox(this);
        TextView prev = Ui.text(this, "‹", 26, Ui.col(this, R.color.text), Ui.BOLD);
        prev.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        boolean canPrev = start > 0 && Fmt.addMonths(month, -1) >= Fmt.startOfMonth(start);
        prev.setAlpha(canPrev ? 1f : 0.25f);
        if (canPrev) prev.setOnClickListener(v -> { month = Fmt.addMonths(month, -1); rebuild(); });
        head.addView(prev);
        TextView mt = Ui.text(this, Fmt.month(month), 16, Ui.BOLD);
        mt.setGravity(Gravity.CENTER);
        head.addView(mt, Ui.weight1());
        TextView next = Ui.text(this, "›", 26, Ui.col(this, R.color.text), Ui.BOLD);
        next.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        boolean canNext = month < Fmt.startOfMonth(now);
        next.setAlpha(canNext ? 1f : 0.25f);
        if (canNext) next.setOnClickListener(v -> { month = Fmt.addMonths(month, 1); rebuild(); });
        head.addView(next);
        c.addView(head);

        LinearLayout wk = Ui.hbox(this);
        wk.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        for (int i = 0; i < 7; i++) {   // Monday first, as on Indian calendars
            TextView d = Ui.text(this, Fmt.DAY[(i + 1) % 7], 11.5f, Ui.col(this, R.color.hint), Ui.BOLD);
            d.setGravity(Gravity.CENTER);
            wk.addView(d, Ui.weight1());
        }
        c.addView(wk);

        int first = (Fmt.cal(month).get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        int days = Fmt.daysInMonth(month);
        LinearLayout row = null;
        for (int cell = 0; cell < first + days; cell++) {
            if (cell % 7 == 0) {
                row = Ui.hbox(this);
                c.addView(row, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 0));
            }
            if (cell < first) { row.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1)); continue; }
            long day = Fmt.addDays(month, cell - first);
            row.addView(dayCell(all, wants, day, start, today), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
        }
        if (row != null) while (row.getChildCount() < 7) row.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));

        LinearLayout key = Ui.hbox(this);
        key.setGravity(Gravity.CENTER);
        key.setPadding(0, Ui.dp(this, 12), 0, 0);
        key.addView(dot(0xFF0E9F6E, true));
        key.addView(Ui.text(this, "No spending", 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        View sp = dot(Ui.col(this, R.color.surface2), true);
        key.addView(sp, Ui.margins(Ui.lp(Ui.dp(this, 12), Ui.dp(this, 12)), this, 16, 0, 6, 0));
        key.addView(Ui.text(this, "Spent on wants", 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        c.addView(key);
        return c;
    }

    private View dot(int color, boolean fill) {
        View v = new View(this);
        v.setBackground(Ui.round(fill ? color : 0, Ui.dp(this, 6)));
        v.setLayoutParams(Ui.margins(Ui.lp(Ui.dp(this, 12), Ui.dp(this, 12)), this, 0, 0, 6, 0));
        return v;
    }

    private View dayCell(List<Txn> all, Map<Long, Long> wants, long day, long start, long today) {
        int s = MotiMath.state(wants, day, start, today);
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        TextView t = Ui.text(this, String.valueOf(Fmt.cal(day).get(Calendar.DAY_OF_MONTH)), 13.5f, Ui.SEMIBOLD);
        t.setGravity(Gravity.CENTER);
        int green = 0xFF0E9F6E;
        if (s == MotiMath.FREE) {
            t.setTextColor(0xFFFFFFFF);
            t.setTypeface(Ui.face(this, Ui.BOLD));
            t.setBackground(Ui.round(green, Ui.dp(this, 18)));
        } else if (s == MotiMath.TODAY_FREE) {
            android.graphics.drawable.GradientDrawable g = Ui.round(0x220E9F6E, Ui.dp(this, 18));
            g.setStroke(Ui.dp(this, 2), green);
            t.setBackground(g);
            t.setTextColor(green);
            t.setTypeface(Ui.face(this, Ui.BOLD));
        } else if (s == MotiMath.SPENT) {
            android.graphics.drawable.GradientDrawable g = Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 18));
            if (day == today) g.setStroke(Ui.dp(this, 2), Ui.col(this, R.color.text2));
            t.setBackground(g);
            t.setTextColor(Ui.col(this, R.color.text2));
        } else {
            t.setTextColor(Ui.col(this, R.color.hint));
        }
        f.addView(t, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36), Gravity.CENTER));
        if (s != MotiMath.NONE) f.setOnClickListener(v -> showDay(all, day, s));
        return f;
    }

    private void showDay(List<Txn> all, long day, int s) {
        long end = Fmt.addDays(day, 1);
        List<Txn> wants = new ArrayList<>(), needs = new ArrayList<>();
        for (Txn x : all) {
            if (!x.isSpend() || x.time < day || x.time >= end || MotiMath.unknownDay(x)) continue;
            (MotiMath.want(x) ? wants : needs).add(x);
        }
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 22);
        b.setPadding(p, 0, p, Ui.dp(this, 8));
        boolean free = s != MotiMath.SPENT;
        TextView head = Ui.text(this, free ? (s == MotiMath.TODAY_FREE ? "🌱 Nothing spent on wants so far today" : "🌱 A no-spend day")
                : "Spent on wants", 15, free ? 0xFF0E9F6E : Ui.col(this, R.color.text), Ui.BOLD);
        head.setPadding(0, 0, 0, Ui.dp(this, 6));
        b.addView(head);
        long sum = 0;
        for (Txn x : wants) { b.addView(line(x)); sum += x.amount; }
        if (!wants.isEmpty() && wants.size() > 1) {
            TextView tt = Ui.text(this, Lang.t("Total:") + " " + Fmt.rs(sum), 14, Ui.col(this, R.color.text), Ui.BOLD);
            tt.setPadding(0, Ui.dp(this, 6), 0, 0);
            b.addView(tt);
        }
        if (!needs.isEmpty()) {
            TextView nl = Ui.text(this, "Planned — doesn't count", 13, Ui.col(this, R.color.text2), Ui.BOLD);
            nl.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            b.addView(nl);
            for (Txn x : needs) b.addView(line(x));
        }
        new Ui.Popup(this).setTitle(Fmt.DAY[Fmt.cal(day).get(Calendar.DAY_OF_WEEK) - 1] + ", " + Fmt.date(day))
                .setView(b).setPositiveButton("OK", null).show();
    }

    private View line(Txn x) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        r.addView(Ui.text(this, Cats.emoji(x.category) + "  " + (x.merchant.isEmpty() ? Lang.t(x.category) : x.merchant), 14,
                Ui.col(this, R.color.text), Ui.REGULAR), Ui.weight1());
        r.addView(Ui.text(this, Fmt.rs(x.amount), 14, Ui.col(this, R.color.text), Ui.BOLD));
        return r;
    }

    // ------------------------------------------------------------------ shared look (also used by Levels)

    /** The simple explanation, in a soft yellow box. */
    static View kid(Activity a, String text) {
        LinearLayout card = Ui.vbox(a);
        card.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        card.setBackground(Ui.round(0x1FF59E0B, Ui.dp(a, 18)));
        TextView body = Ui.text(a, text, 15, Ui.col(a, R.color.text), Ui.REGULAR);
        body.setLineSpacing(Ui.dp(a, 3), 1f);
        card.addView(body);
        return card;
    }

    /** "🧮 With your numbers" box (add the lines yourself). */
    static LinearLayout numbers(Activity a) {
        LinearLayout c = Ui.vbox(a);
        c.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        c.setBackground(Ui.round(0x1A0B6F86, Ui.dp(a, 20)));
        c.addView(Ui.text(a, "🧮  With your numbers", 14, Ui.col(a, R.color.accent), Ui.BOLD));
        return c;
    }

    /** One proof in a "Why this works" card: the fact, then its source (tap to open). */
    static void proof(Activity a, LinearLayout card, String text, String src, String url, boolean divider) {
        if (divider) card.addView(Ui.divider(a), Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 10));
        TextView t = Ui.text(a, text, 14.5f, Ui.col(a, R.color.text), Ui.REGULAR);
        t.setLineSpacing(Ui.dp(a, 2), 1f);
        card.addView(t);
        if (src.isEmpty()) return;
        TextView s = Ui.text(a, Lang.t("Source:") + " " + src + (url.isEmpty() ? "" : "  ↗"), 12.5f,
                Ui.col(a, url.isEmpty() ? R.color.text2 : R.color.accent), Ui.BOLD);
        s.setPadding(0, Ui.dp(a, 6), 0, 0);
        if (!url.isEmpty()) s.setOnClickListener(v -> Learn.openUrl(a, url));
        card.addView(s);
    }
}
