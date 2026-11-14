package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Money Wrapped: your year in money as a short story — tap the right side for the next card, the left to go back.
 * Every number comes from your own payments on this phone. Share any card as a picture.
 */
public class WrappedActivity extends Activity {

    static void open(Activity a, int year) { a.startActivity(new Intent(a, WrappedActivity.class).putExtra("year", year)); }

    private static final int[][] BG = {
            {0xFF0B6F86, 0xFF0E9F6E}, {0xFF4338CA, 0xFF7C3AED}, {0xFFBE185D, 0xFFF97316}, {0xFF0F766E, 0xFF22C55E},
            {0xFF7C2D12, 0xFFEA580C}, {0xFF1E3A8A, 0xFF0EA5E9}, {0xFF6D28D9, 0xFFDB2777}, {0xFF166534, 0xFF65A30D},
            {0xFF9A3412, 0xFFF59E0B}, {0xFF0369A1, 0xFF6366F1}, {0xFF831843, 0xFF8B5CF6}, {0xFF064E3B, 0xFF0D9488},
    };
    static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    /** id, emoji, name, why (with {} for the number) */
    static final String[][] PERSONAS = {
            {"saver", "🐷", "The Super Saver", "You kept {}% of what came in — more than the 20% the 50/30/20 rule asks for."},
            {"planner", "📋", "The Planner", "{}% of your spending went to planned things: bills, rent, EMIs, insurance and investing."},
            {"foodie", "🍔", "The Foodie", "Food & Dining was your biggest want: {}."},
            {"shopper", "🛍️", "The Shopper", "Shopping was your biggest want: {}."},
            {"explorer", "✈️", "The Explorer", "Going places was your biggest want: {}."},
            {"giver", "🎁", "The Giver", "Money for people and gifts was your biggest want: {}."},
            {"fun", "🎬", "The Fun Lover", "Fun and movies were your biggest want: {}."},
            {"steady", "🌱", "The Steady One", "No single want ran away with your money. Nice balance."},
    };

    private int year;
    private final List<View> slides = new ArrayList<>();
    private int at;
    private FrameLayout stage;
    private LinearLayout bars;
    private TextView share;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        getWindow().setStatusBarColor(0xFF0B1220);
        getWindow().setNavigationBarColor(0xFF0B1220);
        getWindow().getDecorView().setSystemUiVisibility(0);   // light icons on the dark story
        long now = System.currentTimeMillis();
        List<Integer> years = MotiMath.years(Store.get(this).txns());
        year = getIntent().getIntExtra("year", Motivation.wrapYear(now));
        if (!years.isEmpty() && !years.contains(year)) year = years.get(0);
        if (b != null) at = b.getInt("at", 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1220);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Ui.vbox(this);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), 0);
        bars = Ui.hbox(this);
        top.addView(bars, Ui.matchWrap());
        LinearLayout tr = Ui.hbox(this);
        tr.addView(Ui.text(this, "Money Wrapped", 13, 0xCCFFFFFF, Ui.BOLD), Ui.weight1());
        TextView close = Ui.text(this, "✕", 20, 0xFFFFFFFF, Ui.BOLD);
        close.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        tr.addView(close);
        top.addView(tr);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        share = Ui.text(this, "↗  Share this card", 14, 0xFF111827, Ui.BOLD);
        share.setGravity(Gravity.CENTER);
        share.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 12));
        share.setBackground(Ui.rippleRound(this, 0xFFFFFFFF, 24));
        share.setOnClickListener(v -> share());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = Ui.dp(this, 26);
        root.addView(share, sp);

        stage.setOnTouchListener((v, e) -> {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            if (e.getX() < v.getWidth() / 3f) go(at - 1);
            else go(at + 1);
            return true;
        });
        setContentView(root);
        build(years);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("at", at);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
    }

    private void build(List<Integer> years) {
        Fmt.hide = false;
        slides.clear();
        List<Txn> all = Store.get(this).txns();
        long now = System.currentTimeMillis();
        MotiMath.Wrap w = MotiMath.wrap(all, year, now);
        boolean done = year < Fmt.cal(now).get(Calendar.YEAR);
        String yr = String.valueOf(year);

        // 1. hello
        View intro = slide("🎁", done ? "Your year in money" : "Your year so far", Lang.t("Your " + yr + " in money"),
                w.payments == 0 ? "Nothing here yet — Money shows your story once it has seen some payments."
                        : (w.partYear ? Lang.t("Since you started on " + Fmt.date(w.from)) + "\n" : "")
                        + Lang.t(w.payments + " payments, and every one stayed on this phone.") + "\n" + Lang.t("Tap to see more →"));
        if (years.size() > 1) ((LinearLayout) ((ViewGroup) intro).getChildAt(0)).addView(yearChips(years));
        slides.add(intro);
        if (w.payments == 0) { show(); return; }

        // 2. in and out
        if (w.in > 0) {
            String big = w.kept >= 0 ? Lang.t("You kept " + Fmt.rs(w.kept)) : Lang.t(Fmt.rs(-w.kept) + " more went out");
            String sub = Lang.t("Came in: " + Fmt.rs(w.in)) + "\n" + Lang.t("Went out: " + Fmt.rs(w.out)) + "\n"
                    + (w.kept >= 0 ? Lang.t("Kept: " + w.ratePct + "% of everything that came in.")
                    : Lang.t("It happens in big years. One goal for next year can turn it around."));
            slides.add(slide("💰", "Money in and out", big, sub));
        } else {
            slides.add(slide("💰", "You spent", Fmt.rs(w.out), "Money didn't see your income yet — so it can't tell what you kept."));
        }

        // 3. where it went
        if (!w.topCat.isEmpty()) {
            View s = slide(Cats.emoji(w.topCat), "Most of your money went to", Lang.t(w.topCat),
                    Fmt.rs(w.topCatAmt) + "\n" + Lang.t("Share of all spending: " + w.topCatPct + "%"));
            ((LinearLayout) ((ViewGroup) s).getChildAt(0)).addView(catBars(w));
            slides.add(s);
        }

        // 4. most-paid name
        if (w.favCount >= 3) slides.add(slide("❤️", "The name you paid most often", w.fav,
                Lang.t("Paid " + w.favCount + " times · " + Fmt.rs(w.favAmt))));

        // 5. months
        int monthsWith = 0;
        for (long m : w.months) if (m > 0) monthsWith++;
        if (monthsWith >= 2) {
            View s = slide("📅", "Your biggest month", Lang.t(Fmt.MONTH[w.bigMonth]),
                    Lang.t(Fmt.rs(w.months[w.bigMonth]) + " spent") + "\n"
                            + Lang.t("Lightest: " + Lang.t(Fmt.MONTH[w.lowMonth]) + " (" + Fmt.rs(w.months[w.lowMonth]) + ")"));
            ((LinearLayout) ((ViewGroup) s).getChildAt(0)).addView(monthBars(w));
            slides.add(s);
        }

        // 6. biggest payment
        if (w.bigAmt > 0) slides.add(slide("🧾", "Your biggest single payment", Fmt.rs(w.bigAmt),
                (w.bigName.isEmpty() ? "" : w.bigName + " · ") + Fmt.date(w.bigTime)));

        // 7. the day you spend most
        if (w.busyDow >= 0) slides.add(slide("📆", "Your spending day", Lang.t(DAYS[w.busyDow]),
                Lang.t("On a usual " + Lang.t(DAYS[w.busyDow]) + " you spend about " + Fmt.rs(w.busyDowAvg) + " — more than on any other day.")));

        // 8. small things
        if (w.smallCount >= 10) slides.add(slide("☕", "Small things add up", Fmt.rs(w.smallAmt),
                Lang.t(w.smallCount + " payments under ₹100. Small each time — real money together.")));

        // 9. no-spend days
        if (w.freeDays > 0) slides.add(slide("🌱", "No-spend days", String.valueOf(w.freeDays),
                w.bestStreak == 1 ? Lang.t("Longest run: 1 day") : Lang.t("Longest run: " + w.bestStreak + " days in a row")));

        // 10. you grew
        int badges = 0;
        JSONObject bj = Store.get(this).obj(Goals.BADGES);
        for (Iterator<String> it = bj.keys(); it.hasNext(); ) {
            long t = bj.optLong(it.next());
            if (t >= w.from && t < Math.max(w.to, now)) badges++;
        }
        int lessons = Learn.doneCount(this);
        if (badges > 0 || lessons > 0) slides.add(slide("🎓", "You grew too", badges == 1 ? Lang.t("1 badge") : Lang.t(badges + " badges"),
                Lang.t(lessons + " lessons learned") + "\n" + Lang.t("Level:") + " " + Lang.t(Motivation.LEVELS[Motivation.level(this)][2])));

        // 11. compared with last year
        if (w.prevOut > 0 && w.out > 0) {
            int pct = (int) Math.round((w.out - w.prevOut) * 100.0 / w.prevOut);
            slides.add(slide(pct <= 0 ? "📉" : "📈", Lang.t("Compared with " + (year - 1)),
                    pct <= 0 ? Lang.t("Spent " + (-pct) + "% less") : Lang.t("Spent " + pct + "% more"),
                    Lang.t(Fmt.rs(w.out) + " vs " + Fmt.rs(w.prevOut) + " in the same months.")));
        }

        // 12. your money style
        String[] p = PERSONAS[PERSONAS.length - 1];
        for (String[] x : PERSONAS) if (x[0].equals(w.persona)) p = x;
        String num = "saver".equals(p[0]) ? String.valueOf(w.ratePct) : "planner".equals(p[0]) ? String.valueOf(w.needsPct) : Fmt.rs(w.topWantAmt);
        slides.add(slide(p[1], "Your money style", Lang.t(p[2]), Lang.t(p[3].replace("{}", num))));

        // 13. one idea for next year
        if (w.cutMonthly >= 10000) {
            slides.add(slide("🚀", Lang.t("One idea for " + (year + 1)), Lang.t(Fmt.rs(w.cutYearly) + " a year"),
                    Lang.t("Spend 10% less on " + Lang.t(w.topWant) + " and save " + Fmt.rs(w.cutMonthly) + " a month.") + "\n"
                            + Lang.t("In a SIP for 10 years at 12% a year, it could grow to about " + Fmt.rs(w.cutGrow10) + ".") + "\n"
                            + Lang.t("12% is close to what the Nifty 50 gave over the last 20 years — not a promise.")));
        }

        // 14. the end
        View end = slide("🎉", done ? "That was your year" : "That was your year so far", Lang.t("Thank you, " + yr + "!"),
                "Everything here was worked out on this phone. Nothing was uploaded.");
        TextView again = Ui.text(this, "Done", 15, 0xFF111827, Ui.BOLD);
        again.setGravity(Gravity.CENTER);
        again.setPadding(Ui.dp(this, 30), Ui.dp(this, 12), Ui.dp(this, 30), Ui.dp(this, 12));
        again.setBackground(Ui.rippleRound(this, 0xFFFFFFFF, 24));
        again.setOnClickListener(v -> finish());
        LinearLayout ec = (LinearLayout) ((ViewGroup) end).getChildAt(0);
        ec.addView(again, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 26, 0, 0));
        slides.add(end);
        show();
    }

    // ------------------------------------------------------------------ one card

    private View slide(String emoji, String label, String big, String sub) {
        int i = slides.size() % BG.length;
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, BG[i]);
        f.setBackground(g);
        LinearLayout c = Ui.vbox(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 30);
        c.setPadding(p, Ui.dp(this, 90), p, Ui.dp(this, 110));
        TextView e = new TextView(this);
        e.setText(emoji);
        e.setTextSize(54);
        e.setGravity(Gravity.CENTER);
        c.addView(e);
        TextView l = Ui.text(this, label, 16, 0xDDFFFFFF, Ui.BOLD);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        c.addView(l);
        TextView b = Ui.text(this, big, big.length() > 18 ? 30 : 40, 0xFFFFFFFF, Ui.EXTRABOLD);
        b.setGravity(Gravity.CENTER);
        c.addView(b);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(this, sub, 16, 0xEEFFFFFF, Ui.SEMIBOLD);
            s.setGravity(Gravity.CENTER);
            s.setLineSpacing(Ui.dp(this, 4), 1f);
            s.setPadding(0, Ui.dp(this, 14), 0, 0);
            c.addView(s);
        }
        f.addView(c, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        return f;
    }

    private View catBars(MotiMath.Wrap w) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(0, Ui.dp(this, 22), 0, 0);
        long max = 1;
        for (long v : w.cats.values()) max = Math.max(max, v);
        int n = 0;
        for (Map.Entry<String, Long> e : w.cats.entrySet()) {
            if (n++ >= 4) break;
            LinearLayout r = Ui.hbox(this);
            r.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            TextView name = Ui.text(this, Cats.emoji(e.getKey()) + " " + Lang.t(e.getKey()), 13, 0xFFFFFFFF, Ui.SEMIBOLD);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(name, Ui.lp(Ui.dp(this, 128), -2));
            LinearLayout track = Ui.hbox(this);
            View fill = new View(this);
            fill.setBackground(Ui.round(0xDDFFFFFF, Ui.dp(this, 5)));
            float k = Math.max(0.04f, e.getValue() / (float) max);
            track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 10), k));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 10), 1 - k));
            r.addView(track, Ui.weight1());
            TextView amt = Ui.text(this, Fmt.shortRs(e.getValue()), 12.5f, 0xFFFFFFFF, Ui.BOLD);
            amt.setPadding(Ui.dp(this, 8), 0, 0, 0);
            r.addView(amt);
            box.addView(r, Ui.matchWrap());
        }
        return box;
    }

    private View monthBars(MotiMath.Wrap w) {
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, Ui.dp(this, 24), 0, 0);
        long max = 1;
        for (long v : w.months) max = Math.max(max, v);
        int h = Ui.dp(this, 90);
        for (int m = 0; m < 12; m++) {
            LinearLayout col = Ui.vbox(this);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            View bar = new View(this);
            bar.setBackground(Ui.round(m == w.bigMonth ? 0xFFFFFFFF : 0x88FFFFFF, Ui.dp(this, 4)));
            int bh = w.months[m] <= 0 ? Ui.dp(this, 2) : Math.max(Ui.dp(this, 4), (int) (h * (w.months[m] / (double) max)));
            col.addView(bar, Ui.lp(Ui.dp(this, 12), bh));
            TextView l = Ui.text(this, Lang.t(Fmt.MON[m]).substring(0, 1), 10.5f, 0xCCFFFFFF, Ui.BOLD);
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, Ui.dp(this, 4), 0, 0);
            col.addView(l);
            row.addView(col, new LinearLayout.LayoutParams(0, h + Ui.dp(this, 22), 1));
        }
        return row;
    }

    private View yearChips(List<Integer> years) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, Ui.dp(this, 22), 0, 0);
        for (int y : years) {
            boolean on = y == year;
            TextView t = Ui.text(this, String.valueOf(y), 14, on ? 0xFF111827 : 0xFFFFFFFF, Ui.BOLD);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
            t.setBackground(Ui.rippleRound(this, on ? 0xFFFFFFFF : 0x33FFFFFF, 18));
            t.setOnClickListener(v -> {
                if (y == year) return;
                year = y;
                at = 0;
                build(years);
            });
            row.addView(t, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 4, 0, 4, 0));
        }
        hs.addView(row);
        return hs;
    }

    // ------------------------------------------------------------------ moving through

    private void go(int i) {
        if (i < 0 || i >= slides.size()) return;
        at = i;
        show();
    }

    private void show() {
        at = Math.max(0, Math.min(at, slides.size() - 1));
        stage.removeAllViews();
        View v = slides.get(at);
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        stage.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(220).start();
        bars.removeAllViews();
        for (int i = 0; i < slides.size(); i++) {
            View s = new View(this);
            s.setBackground(Ui.round(i <= at ? 0xFFFFFFFF : 0x55FFFFFF, Ui.dp(this, 2)));
            bars.addView(s, Ui.margins(new LinearLayout.LayoutParams(0, Ui.dp(this, 3), 1), this, 2, 0, 2, 0));
        }
        share.setVisibility(slides.size() > 1 && at < slides.size() - 1 ? View.VISIBLE : View.GONE);
        if (at == slides.size() - 1 && slides.size() > 2 && Goals.award(this, "wrapped")) Ui.toast(this, "New badge: Looked back 🎁");
    }

    // ------------------------------------------------------------------ share as picture

    private void share() {
        View v = stage.getChildCount() > 0 ? stage.getChildAt(0) : null;
        if (v == null || v.getWidth() == 0) return;
        try {
            Bitmap bm = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            v.draw(cv);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xCCFFFFFF);
            p.setTextSize(Ui.dp(this, 13));
            p.setTypeface(Ui.face(this, Ui.BOLD));
            String foot = Lang.t("Money Wrapped") + " " + year + " · " + Lang.t("made with Money");
            cv.drawText(foot, (bm.getWidth() - p.measureText(foot)) / 2f, bm.getHeight() - Ui.dp(this, 36), p);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            Uri u = Report.save(this, "Shared", "wrapped-" + year + "-" + (at + 1) + ".png", "image/png", out.toByteArray());
            if (u == null) { Ui.toast(this, "Saved in Money's folder"); return; }
            Intent i = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, Lang.t("Share")));
        } catch (Exception e) {
            Ui.toast(this, "Couldn't make the picture");
        }
    }
}
