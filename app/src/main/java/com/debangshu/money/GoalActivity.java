package com.debangshu.money;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One goal: how far, on track or not, a graph of where it's heading, what-if sliders, and where the money could sit. */
public class GoalActivity extends Screen {

    private static final int REQ_PHOTO = 81;

    static void open(Context c, String id) { c.startActivity(new Intent(c, GoalActivity.class).putExtra("id", id)); }

    private JSONObject g;
    private int extraPerMonth;              // what-if: rupees more each month
    private String cutCat;                  // what-if: spend less on this
    private int cutPct;                     // … by this much (%)

    @Override
    String title() {
        JSONObject x = Goals.byId(this, getIntent().getStringExtra("id"));
        return x == null ? "Goal" : x.optString("name");
    }

    @Override
    void build() {
        g = Goals.byId(this, getIntent().getStringExtra("id"));
        if (g == null) { finish(); return; }
        Goals.check(this, false);
        g = Goals.byId(this, g.optString("id"));
        if (getIntent().getBooleanExtra("fresh", false)) {
            getIntent().removeExtra("fresh");
            Ui.toast(this, "Goal created 🎯");
        }
        GoalMath.Status s = Goals.status(this, g);
        hero(s);
        if (s.kind != GoalMath.REACHED) {
            numbers(s);
            graphAndWhatIf(s);
            whereToKeep(s);
        } else celebrateCard();
        actions(s);
        history();
        GrowPage.showCelebrations(this);
    }

    // ------------------------------------------------------------------ top

    private void hero(GoalMath.Status s) {
        LinearLayout c = Ui.card(this);
        File ph = Goals.photo(this, g.optString("id"));
        if (ph.exists()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(BitmapFactory.decodeFile(ph.getAbsolutePath()));
            iv.setClipToOutline(true);
            iv.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 16)));
            c.addView(iv, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(this, 150)), this, 0, 0, 0, 14));
        }
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, g.optString("emoji", "🎯"), Goals.color(g), 44));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, Fmt.rs(s.current), 28, Ui.EXTRABOLD));
        col.addView(Ui.sub(this, "of " + Fmt.rs(s.target) + (g.optLong("by") > 0 ? " · by " + Fmt.date(g.optLong("by")) : "")));
        h.addView(col, Ui.weight1());
        h.addView(Ui.text(this, s.percent + "%", 20, Goals.color(g), Ui.EXTRABOLD));
        c.addView(h);
        Charts.Progress pr = new Charts.Progress(this);
        pr.set(s.current / (float) s.target, Goals.color(g));
        c.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 10));
        TextView st = Ui.text(this, Goals.statusLine(s), 15, Goals.statusColor(this, s), Ui.BOLD);
        c.addView(st);
        String more = null;
        if (s.kind == GoalMath.BEHIND && s.catchUp > 0) more = "Save " + Fmt.rs(s.catchUp) + " more a month to catch up — or try the sliders below.";
        else if (s.kind == GoalMath.AHEAD && g.optLong("by") > 0)
            more = "About " + Math.max(1, Math.round(GoalMath.months(s.finish, g.optLong("by")))) + " month(s) before your date. Nice!";
        else if (s.kind == GoalMath.NOT_MOVING) more = g.optString("account").isEmpty() ? "Tap “Add money” when you put some aside."
                : "Money watches " + Noted.fullLabel(this, g.optString("account")) + " — it moves when that balance grows.";
        if (more != null) { TextView m = Ui.sub(this, more); m.setPadding(0, Ui.dp(this, 4), 0, 0); c.addView(m); }
        String acc = g.optString("account");
        TextView src = Ui.text(this, acc.isEmpty() ? "✍️  You add the money yourself" : "🏦  Money is in " + Noted.fullLabel(this, acc),
                12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        src.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(src);
        add(c);
    }

    private void numbers(GoalMath.Status s) {
        LinearLayout row = Ui.hbox(this);
        row.setBaselineAligned(false);
        row.addView(tile("Needed / month", s.needPerMonth > 0 ? Fmt.rs(s.needPerMonth) : "—"), tlp(true));
        row.addView(tile(s.pace >= 0 ? "Your pace" : "Expected pace", s.usedPace > 0 ? Fmt.rs(s.usedPace) : "—"), tlp(false));
        row.addView(tile("Reach by", s.finish > 0 ? Fmt.MON[Fmt.cal(s.finish).get(Calendar.MONTH)] + " " + Fmt.cal(s.finish).get(Calendar.YEAR) : "—"), tlp(false));
        add(row);
        if (s.pace < 0 && s.usedPace > 0) {
            TextView n = Ui.sub(this, g.optLong("plan") > 0 ? "“Expected” is your split-saving plan, until Money sees your real pace (about 2 weeks)."
                    : "“Expected” is your usual saving, shared across goals, until Money sees your real pace (about 2 weeks).");
            n.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
            add(n);
        }
    }

    private LinearLayout.LayoutParams tlp(boolean first) { return Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, first ? 0 : 8, 0, 0, 0); }

    private View tile(String label, String value) {
        LinearLayout t = Ui.vbox(this);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        t.setBackground(Ui.round(Ui.col(this, R.color.surface), Ui.dp(this, 16)));
        t.addView(Ui.oneLine(Ui.text(this, label, 12, Ui.col(this, R.color.text2), Ui.BOLD)));
        t.addView(Ui.oneLine(Ui.text(this, value, 16, Ui.EXTRABOLD)));
        return t;
    }

    // ------------------------------------------------------------------ graph + what if

    private void graphAndWhatIf(GoalMath.Status s) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Where it's heading", 16, Ui.BOLD));
        LinearLayout legend = Ui.hbox(this);
        legend.addView(dot(Goals.color(g), "At your pace"));
        legend.addView(dot(0xFFEB6834, "With changes"));
        legend.addView(dot(Ui.col(this, R.color.text2), "Goal"));
        c.addView(legend, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 6));
        Charts.Line chart = new Charts.Line(this);
        chart.height(190);
        c.addView(chart);
        TextView touch = Ui.text(this, "Touch the graph to see any month", 12, Ui.col(this, R.color.hint), Ui.SEMIBOLD);
        touch.setPadding(0, Ui.dp(this, 4), 0, 0);
        c.addView(touch);

        // ---- what if
        TextView wh = Ui.text(this, "What if…", 16, Ui.BOLD);
        wh.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 2));
        c.addView(wh);
        TextView result = Ui.text(this, "", 15, Ui.col(this, R.color.accent), Ui.BOLD);

        long maxExtra = Math.max(2000, (Math.max(s.needPerMonth, s.usedPace) / 100) * 2);
        maxExtra = ((maxExtra + 499) / 500) * 500;
        TextView exLabel = Ui.text(this, "", 14, Ui.SEMIBOLD);
        exLabel.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(exLabel);
        SeekBar ex = new SeekBar(this);
        ex.setMax((int) (maxExtra / 100));        // steps of ₹100
        ex.setProgress(extraPerMonth / 100);
        c.addView(ex);

        LinkedHashMap<String, Long> wants = Goals.wants(this);
        TextView cutLabel = Ui.text(this, "", 14, Ui.SEMIBOLD);
        SeekBar cut = new SeekBar(this);
        List<TextView> catChips = new ArrayList<>();
        if (!wants.isEmpty()) {
            TextView cl = Ui.text(this, "Spend less on…", 14, Ui.SEMIBOLD);
            cl.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
            c.addView(cl);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = Ui.hbox(this);
            hs.addView(chips);
            int k = 0;
            for (Map.Entry<String, Long> e : wants.entrySet()) {
                if (k++ >= 5) break;
                TextView ch = Ui.chip(this, Cats.emoji(e.getKey()) + " " + e.getKey() + " · " + Fmt.rs(e.getValue()) + "/mo", false);
                ch.setTag(e.getKey());
                ch.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
                catChips.add(ch);
                chips.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
            }
            if (cutCat == null) cutCat = wants.keySet().iterator().next();
            c.addView(hs);
            cutLabel.setPadding(0, Ui.dp(this, 10), 0, 0);
            c.addView(cutLabel);
            cut.setMax(20);                        // steps of 5%
            cut.setProgress(cutPct / 5);
            c.addView(cut);
        }
        result.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(result);
        TextView reset = Ui.text(this, "Reset", 13, Ui.col(this, R.color.accent), Ui.BOLD);
        reset.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(reset);
        add(c);

        Runnable update = () -> {
            long now = System.currentTimeMillis();
            long freed = 0;
            if (cutCat != null && wants.containsKey(cutCat)) freed = wants.get(cutCat) * cutPct / 100;
            long extra = extraPerMonth * 100L + freed;
            exLabel.setText("Save " + Fmt.rs(extraPerMonth * 100L) + " more each month");
            if (cutCat != null) cutLabel.setText("Spend " + cutPct + "% less on " + cutCat + "  (" + Fmt.rs(freed) + " a month)");
            for (TextView ch : catChips) {
                boolean on = ch.getTag().equals(cutCat);
                ch.setBackground(Ui.rippleRound(this, on ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.surface2), 18));
                ch.setTextColor(on ? Ui.col(this, R.color.on_accent) : Ui.col(this, R.color.text));
            }
            long pace = s.usedPace, newPace = pace + extra;
            long f0 = GoalMath.finish(now, s.left, pace), f1 = GoalMath.finish(now, s.left, newPace);
            if (extra <= 0) result.setText(f0 > 0 ? "Move a slider to see how much sooner you could get there." : "Move a slider to see when you could get there.");
            else if (f1 <= 0) result.setText("Still a long way — try a bigger change.");
            else if (f0 <= 0) result.setText("You'd reach it by " + Fmt.date(f1) + ".");
            else {
                long sooner = Math.round(GoalMath.months(f1, f0));
                result.setText("You'd reach it by " + Fmt.date(f1) + (sooner >= 1 ? " — " + sooner + (sooner == 1 ? " month" : " months") + " sooner 🎉"
                        : " — " + Math.max(1, Math.round((f0 - f1) / (double) GoalMath.DAY)) + " days sooner"));
            }
            drawChart(chart, s, pace, newPace, extra > 0);
        };
        ex.setOnSeekBarChangeListener(seek(p -> { extraPerMonth = p * 100; update.run(); }));
        cut.setOnSeekBarChangeListener(seek(p -> { cutPct = p * 5; update.run(); }));
        for (TextView ch : catChips) ch.setOnClickListener(v -> { cutCat = (String) v.getTag(); update.run(); });
        reset.setOnClickListener(v -> { extraPerMonth = 0; cutPct = 0; ex.setProgress(0); cut.setProgress(0); update.run(); });
        chart.onTouchPoint(i -> touch.setText(pointText(s, i, s.usedPace, s.usedPace + extraPerMonth * 100L
                + (cutCat != null && wants.containsKey(cutCat) ? wants.get(cutCat) * cutPct / 100 : 0))));
        update.run();
    }

    private int chartMonths(GoalMath.Status s, long pace) {
        long now = System.currentTimeMillis();
        int m = g.optLong("by") > 0 ? GoalMath.monthsLeft(now, g.optLong("by")) : 12;
        if (pace > 0) m = Math.max(m, (int) Math.ceil(s.left / (double) pace) + 1);
        return Math.max(3, Math.min(m, 120));
    }

    private void drawChart(Charts.Line chart, GoalMath.Status s, long pace, long newPace, boolean what) {
        int m = chartMonths(s, pace);
        float[] a = new float[m + 1], b = new float[m + 1];
        String[] x = new String[m + 1];
        Calendar cal = Fmt.cal(System.currentTimeMillis());
        for (int i = 0; i <= m; i++) {
            a[i] = Math.min(s.target, s.current + pace * i) / 100f;
            b[i] = Math.min(s.target, s.current + newPace * i) / 100f;
            x[i] = i == 0 ? "Now" : Fmt.MON[cal.get(Calendar.MONTH)] + " '" + (cal.get(Calendar.YEAR) % 100);
            cal.add(Calendar.MONTH, 1);
        }
        if (what) chart.set(new float[][]{a, b}, new int[]{Goals.color(g), 0xFFEB6834}, new boolean[]{false, true}, new boolean[]{true, false}, x, s.target / 100f);
        else chart.set(new float[][]{a}, new int[]{Goals.color(g)}, new boolean[]{false}, new boolean[]{true}, x, s.target / 100f);
    }

    private String pointText(GoalMath.Status s, int i, long pace, long newPace) {
        Calendar cal = Fmt.cal(System.currentTimeMillis());
        cal.add(Calendar.MONTH, i);
        String when = i == 0 ? "Now" : Fmt.MONTH[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR);
        long a = Math.min(s.target, s.current + pace * i), b = Math.min(s.target, s.current + newPace * i);
        return when + ": " + Fmt.rs(a) + (b != a ? "  ·  with changes " + Fmt.rs(b) : "");
    }

    private View dot(int color, String label) {
        LinearLayout r = Ui.hbox(this);
        View d = new View(this);
        d.setBackground(Ui.round(color, Ui.dp(this, 3)));
        r.addView(d, Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10)));
        TextView t = Ui.text(this, label, 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        t.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 14), 0);
        r.addView(t);
        return r;
    }

    interface OnProgress { void on(int p); }

    static SeekBar.OnSeekBarChangeListener seek(OnProgress o) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { if (user) o.on(p); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        };
    }

    // ------------------------------------------------------------------ where the money could sit

    private void whereToKeep(GoalMath.Status s) {
        int months = g.optLong("by") > 0 ? GoalMath.monthsLeft(System.currentTimeMillis(), g.optLong("by"))
                : s.finish > 0 ? (int) Math.ceil(GoalMath.months(System.currentTimeMillis(), s.finish)) : 12;
        String[] w = GoalMath.whereToKeep(months);
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, "💡", 0xFFF59E0B, 36));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, "Where this money could sit", 15, Ui.BOLD));
        col.addView(Ui.text(this, w[0] + " away", 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        h.addView(col, Ui.weight1());
        c.addView(h);
        TextView what = Ui.text(this, w[1], 14.5f, Ui.SEMIBOLD);
        what.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        c.addView(what);
        c.addView(Ui.sub(this, w[2]));
        TextView d = Ui.text(this, "For learning — not financial advice. Check rates and rules before you decide.", 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(d);
        add(c);
    }

    private void celebrateCard() {
        LinearLayout c = Ui.card(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 22)));
        c.addView(Ui.iconBadge(this, "🏆", 0xFFF59E0B, 64));
        TextView t = Ui.text(this, "You did it!", 20, Ui.EXTRABOLD);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        c.addView(t);
        TextView s = Ui.sub(this, "Enjoy it — and if you like, start the next goal. Saving gets easier every time.");
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        TextView n = Ui.button(this, "Start a new goal", true);
        n.setOnClickListener(v -> GoalSetup.start(this));
        c.addView(n, Ui.margins(Ui.lp(-2, -2), this, 0, 12, 0, 0));
        add(c);
    }

    // ------------------------------------------------------------------ actions

    private void actions(GoalMath.Status s) {
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        String id = g.optString("id");
        boolean manual = g.optString("account").isEmpty();
        if (manual) {
            c.addView(Ui.navRow(this, "➕", 0xFF0E9F6E, "Add money", "When you put some aside", v -> money(1)));
            if (g.optLong("saved") > 0) c.addView(Ui.navRow(this, "↩️", 0xFF64748B, "Take out", "If you used some of it", v -> money(-1)));
        }
        c.addView(Ui.navRow(this, "🏦", 0xFF0B6F86, manual ? "Keep it in a bank account" : "Money is in " + Noted.fullLabel(this, g.optString("account")),
                manual ? "Then Money tracks it by itself" : "Tap to change", v -> pickAccount()));
        c.addView(Ui.navRow(this, "📅", 0xFF3B82F6, "Change amount or date", Fmt.rs(g.optLong("target")) + (g.optLong("by") > 0 ? " by " + Fmt.date(g.optLong("by")) : " · no date"), v -> edit()));
        c.addView(Ui.navRow(this, "📷", 0xFFEC4899, Goals.photo(this, id).exists() ? "Change photo" : "Add a photo", "A picture of what you're saving for keeps you going", v -> pickPhoto()));
        boolean paused = g.optBoolean("paused");
        if (s.kind != GoalMath.REACHED) c.addView(Ui.navRow(this, paused ? "▶️" : "⏸️", 0xFF8B5CF6, paused ? "Resume" : "Pause this goal",
                paused ? "Back on the plan" : "For a tough month — Money re-plans it", v -> { set("paused", !paused); rebuild(); }));
        c.addView(Ui.navRow(this, "⚠️", 0xFFDC2626, "Delete goal", "The money stays where it is", v ->
                new Ui.Popup(this).setTitle("Delete “" + g.optString("name") + "”?")
                        .setMessage("Only the goal is removed. Your money and payments stay as they are.")
                        .setPositiveButton("Delete", (d, w) -> { Goals.delete(this, id); finish(); })
                        .setNegativeButton("Cancel", null).show()));
        add(c);
    }

    private void set(String k, Object v) {
        try { g.put(k, v); } catch (Exception ignored) { }
        Goals.put(this, g);
    }

    private void money(int sign) {
        LinearLayout b = form();
        EditText e = field("₹ amount", true);
        b.addView(e);
        EditText note = field("Note (optional)", false);
        b.addView(note);
        new Ui.Popup(this).setTitle(sign > 0 ? "Add money" : "Take out").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v <= 0) return;
                    try {
                        long saved = Math.max(0, g.optLong("saved") + sign * v);
                        g.put("saved", saved);
                        JSONArray log = g.optJSONArray("log");
                        if (log == null) { log = new JSONArray(); g.put("log", log); }
                        log.put(new JSONArray().put(System.currentTimeMillis()).put(sign * v).put(note.getText().toString().trim()));
                        while (log.length() > 100) log.remove(0);
                    } catch (Exception ignored) { }
                    Goals.put(this, g);
                    Goals.check(this, false);
                    rebuild();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void pickAccount() {
        List<String> keys = Noted.bankAccounts(this);
        String[] names = new String[keys.size() + 1];
        for (int i = 0; i < keys.size(); i++) {
            long[] lv = Noted.liveOf(this, keys.get(i));
            names[i] = Noted.fullLabel(this, keys.get(i)) + (lv != null && lv[0] >= 0 ? "  ·  " + Fmt.rs(lv[0]) : "");
        }
        names[keys.size()] = "✍️  I'll add the money myself";
        new Ui.Popup(this).setTitle("Where is the money?").setItems(names, (d, w) -> {
            try {
                if (w == keys.size()) {
                    g.put("saved", Goals.current(this, g)).put("account", "");
                } else {
                    String k = keys.get(w);
                    long[] lv = Noted.liveOf(this, k);
                    g.put("account", k).put("base", lv == null || lv[0] < 0 ? 0 : lv[0]).put("existing", true);
                    Goals.award(this, "real_home");
                }
                g.remove("snaps");      // a new source → measure the pace afresh
                g.put("created", System.currentTimeMillis()).put("startValue", Goals.current(this, g));
            } catch (Exception ignored) { }
            Goals.put(this, g);
            rebuild();
        }).show();
    }

    private void edit() {
        LinearLayout b = form();
        EditText name = field("Name", false);
        name.setText(g.optString("name"));
        EditText amt = field("₹ target", true);
        amt.setText(String.valueOf(g.optLong("target") / 100));
        b.addView(name);
        b.addView(amt);
        long[] by = {g.optLong("by")};
        TextView when = Ui.chip(this, by[0] > 0 ? "📅  " + Fmt.date(by[0]) : "📅  Pick a date (optional)", false);
        when.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        when.setOnClickListener(v -> {
            Calendar k = Calendar.getInstance();
            if (by[0] > 0) k.setTimeInMillis(by[0]); else k.add(Calendar.MONTH, 6);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar cc = Calendar.getInstance();
                cc.set(y, m, d, 12, 0, 0);
                by[0] = cc.getTimeInMillis();
                when.setText("📅  " + Fmt.date(by[0]));
            }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
        });
        b.addView(when, Ui.margins(Ui.lp(-2, -2), this, 0, 12, 0, 0));
        TextView clear = Ui.text(this, "No date", 13, Ui.col(this, R.color.accent), Ui.BOLD);
        clear.setPadding(0, Ui.dp(this, 10), 0, 0);
        clear.setOnClickListener(v -> { by[0] = 0; when.setText("📅  Pick a date (optional)"); });
        b.addView(clear);
        new Ui.Popup(this).setTitle("Change goal").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long t = SmsParser.paise(amt.getText().toString().trim());
                    String n = name.getText().toString().trim();
                    if (t <= 0 || n.isEmpty()) { Ui.toast(this, "Add a name and an amount"); return; }
                    try { g.put("name", n).put("target", t).put("by", by[0]); } catch (Exception ignored) { }
                    Goals.put(this, g);
                    Goals.check(this, false);
                    recreate();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ photo

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "Pick a photo"), REQ_PHOTO); }
        catch (Exception e) { Ui.toast(this, "No photo app found"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PHOTO || res != RESULT_OK || data == null || data.getData() == null || g == null) return;
        Uri u = data.getData();
        new Thread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap bm;
                try (InputStream in = getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in, null, o2); }
                if (bm == null) throw new Exception("no image");
                File f = Goals.photo(this, g.optString("id"));
                //noinspection ResultOfMethodCallIgnored
                f.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(f)) { bm.compress(Bitmap.CompressFormat.JPEG, 85, out); }
                runOnUiThread(this::rebuild);
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Couldn't use that photo"));
            }
        }).start();
    }

    // ------------------------------------------------------------------ history

    private void history() {
        JSONArray log = g.optJSONArray("log");
        if (log == null || log.length() == 0) return;
        add(Ui.label(this, "Added and taken out"));
        LinearLayout c = Ui.card(this);
        for (int i = log.length() - 1; i >= Math.max(0, log.length() - 8); i--) {
            JSONArray e = log.optJSONArray(i);
            LinearLayout r = Ui.hbox(this);
            r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            LinearLayout col = Ui.vbox(this);
            col.addView(Ui.text(this, Fmt.date(e.optLong(0)), 14, Ui.SEMIBOLD));
            if (!e.optString(2).isEmpty()) col.addView(Ui.sub(this, e.optString(2)));
            r.addView(col, Ui.weight1());
            long v = e.optLong(1);
            r.addView(Ui.text(this, (v >= 0 ? "+" : "−") + Fmt.rs(Math.abs(v)), 14, v >= 0 ? Ui.col(this, R.color.success) : Ui.col(this, R.color.text2), Ui.BOLD));
            c.addView(r);
        }
        add(c);
    }
}
