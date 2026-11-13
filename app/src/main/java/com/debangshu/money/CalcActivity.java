package com.debangshu.money;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One calculator / play tool: a simple explanation, the result (numbers, graph, bars), the sliders, and "share as picture". */
public class CalcActivity extends Screen {

    static void open(Context c, String id) { c.startActivity(new Intent(c, CalcActivity.class).putExtra("id", id)); }

    private Calcs.Calc calc;
    private final Map<String, Long> v = new HashMap<>();
    private LinearLayout result;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    String title() {
        calc = Calcs.byId(getIntent().getStringExtra("id"));
        return calc.title;
    }

    @Override
    void build() {
        if (calc == null) calc = Calcs.byId(getIntent().getStringExtra("id"));
        if (v.isEmpty()) {
            for (Calcs.P p : calc.params) v.put(p.key, p.def);
            personalise();
        }
        add(Explain.box(this, "c_" + calc.id));
        result = Ui.card(this);
        add(result);
        add(inputs());
        TextView share = Ui.button(this, "Share as picture", false);
        share.setGravity(Gravity.CENTER);
        share.setOnClickListener(x -> share());
        add(share);
        TextView more = Ui.text(this, "All calculators & play tools →", 14, Ui.col(this, R.color.accent), Ui.BOLD);
        more.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
        more.setOnClickListener(x -> startActivity(new Intent(this, CalcHub.class)));
        add(more);
        render();
    }

    /** Start from your own numbers where Money knows them (monthly saving, age). */
    private void personalise() {
        long save = Goals.monthlySaving(this) / 100;
        int age = Store.get(this).prefs().getInt("age", -1);
        for (Calcs.P p : calc.params) {
            if (p.key.equals("m") && save >= p.min && "sip wait snowball crash".contains(calc.id)) v.put(p.key, Math.min(p.max, save / p.step * p.step));
            if (p.key.equals("age") && age >= p.min && age <= p.max) v.put(p.key, (long) age);
        }
    }

    // ------------------------------------------------------------------ inputs

    private View inputs() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Play with the numbers", 15, Ui.BOLD), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        for (Calcs.P p : calc.params) {
            if (p.kind == Calcs.SWITCH) {
                Switch sw = new Switch(this);
                sw.setText(p.label);
                sw.setTypeface(Ui.face(this, Ui.SEMIBOLD));
                sw.setTextSize(14.5f);
                sw.setChecked(v.get(p.key) == 1);
                sw.setOnCheckedChangeListener((b, on) -> { v.put(p.key, on ? 1L : 0L); render(); });
                c.addView(sw, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 10));
            } else if (p.kind == Calcs.CHOICE) {
                c.addView(Ui.text(this, p.label, 14, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
                hs.setHorizontalScrollBarEnabled(false);
                LinearLayout row = Ui.hbox(this);
                hs.addView(row);
                List<TextView> chips = new ArrayList<>();
                for (int i = 0; i < p.choices.length; i++) {
                    final long idx = i;
                    TextView ch = Ui.chip(this, p.choices[i], false);
                    ch.setOnClickListener(x -> {
                        v.put(p.key, idx);
                        paint(chips, (int) idx);
                        render();
                    });
                    chips.add(ch);
                    row.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
                }
                paint(chips, v.get(p.key).intValue());
                c.addView(hs, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 12));
            } else {
                Slider s = new Slider(this, p.label, p.min, p.max, p.step, v.get(p.key), p::fmt, val -> { v.put(p.key, val); render(); });
                c.addView(s.view);
            }
        }
        return c;
    }

    private void paint(List<TextView> chips, int on) {
        for (int i = 0; i < chips.size(); i++) {
            boolean o = i == on;
            chips.get(i).setBackground(Ui.rippleRound(this, o ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.surface2), 18));
            chips.get(i).setTextColor(o ? Ui.col(this, R.color.on_accent) : Ui.col(this, R.color.text));
        }
    }

    // ------------------------------------------------------------------ result

    private void render() {
        if (result == null) return;
        h.removeCallbacksAndMessages(null);
        Calcs.Out o;
        try { o = calc.fn.run(this, v); }
        catch (Exception e) { return; }
        result.removeAllViews();
        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.iconBadge(this, calc.emoji, calc.color, 30));
        TextView tt = Ui.text(this, calc.title, 13, Ui.col(this, R.color.text2), Ui.BOLD);
        tt.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(tt);
        result.addView(top, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        result.addView(Ui.text(this, o.headLabel, 13.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        TextView head = Ui.text(this, o.head, 27, o.headColor == 0 ? Ui.col(this, R.color.text) : o.headColor, Ui.EXTRABOLD);
        result.addView(head);

        if (!o.tiles.isEmpty()) {
            LinearLayout row = Ui.hbox(this);
            row.setBaselineAligned(false);
            for (int i = 0; i < o.tiles.size(); i++) {
                String[] t = o.tiles.get(i);
                LinearLayout tl = Ui.vbox(this);
                tl.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
                tl.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 14)));
                tl.addView(Ui.oneLine(Ui.text(this, t[0], 11.5f, Ui.col(this, R.color.text2), Ui.BOLD)));
                tl.addView(Ui.oneLine(Ui.text(this, t[1], 14.5f, Ui.EXTRABOLD)));
                row.addView(tl, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, i == 0 ? 0 : 8, 0, 0, 0));
            }
            result.addView(row, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        }

        if (o.series != null) {
            Charts.Line chart = new Charts.Line(this);
            chart.height(190);
            chart.set(o.series, o.colors, o.dashed, o.filled, o.x, o.target);
            result.addView(legend(o), Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 4));
            result.addView(chart);
            TextView touch = Ui.text(this, "Touch the graph to see any point", 11.5f, Ui.col(this, R.color.hint), Ui.SEMIBOLD);
            touch.setPadding(0, Ui.dp(this, 2), 0, 0);
            result.addView(touch);
            chart.onTouchPoint(i -> {
                StringBuilder b = new StringBuilder(o.x[i]).append(":");
                for (int k = 0; k < o.series.length; k++)
                    b.append("  ").append(o.legend != null && k < o.legend.length ? o.legend[k] + " " : "").append(Charts.Line.money(o.series[k][i]));
                touch.setText(b.toString());
            });
            if (o.race) {
                TextView play = Ui.chip(this, "▶  Play the race", true);
                play.setOnClickListener(x -> race(chart, o.series[0].length));
                LinearLayout pr = Ui.hbox(this);
                pr.addView(play);
                result.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
            }
        }

        if (!o.bars.isEmpty()) {
            LinearLayout bars = Ui.vbox(this);
            for (Calcs.Bar b : o.bars) {
                LinearLayout r = Ui.vbox(this);
                r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
                LinearLayout l = Ui.hbox(this);
                l.addView(Ui.oneLine(Ui.text(this, b.label, 14, Ui.BOLD)), Ui.weight1());
                l.addView(Ui.text(this, b.value, 14, b.color, Ui.EXTRABOLD));
                r.addView(l);
                Charts.Progress pg = new Charts.Progress(this);
                pg.set(b.frac, b.color);
                r.addView(pg, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 2));
                if (b.sub != null) r.addView(Ui.text(this, b.sub, 12, Ui.col(this, R.color.text2), Ui.REGULAR));
                bars.addView(r);
            }
            result.addView(bars, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        }

        if (!o.lines.isEmpty()) {
            LinearLayout box = Ui.vbox(this);
            box.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            box.setBackground(Ui.round(0x1FF59E0B, Ui.dp(this, 16)));
            box.addView(Ui.text(this, "What this means", 13.5f, Ui.BOLD));
            for (String line : o.lines) {
                TextView t = Ui.text(this, line, 14, Ui.REGULAR);
                t.setLineSpacing(Ui.dp(this, 2), 1f);
                t.setPadding(0, Ui.dp(this, 8), 0, 0);
                box.addView(t);
            }
            result.addView(box, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        }
        if (o.note != null) {
            TextView n = Ui.text(this, o.note, 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
            n.setPadding(0, Ui.dp(this, 10), 0, 0);
            result.addView(n);
        }
    }

    private View legend(Calcs.Out o) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = Ui.hbox(this);
        hs.addView(r);
        if (o.legend == null) return hs;
        for (int k = 0; k < o.legend.length && k < o.colors.length; k++) {
            View d = new View(this);
            boolean dash = o.dashed != null && k < o.dashed.length && o.dashed[k];
            d.setBackground(Ui.round(o.colors[k], Ui.dp(this, dash ? 1 : 3)));
            r.addView(d, Ui.lp(Ui.dp(this, dash ? 14 : 10), Ui.dp(this, dash ? 3 : 10)));
            TextView t = Ui.text(this, o.legend[k], 12, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
            t.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 14), 0);
            r.addView(t);
        }
        return hs;
    }

    /** Draw the race point by point. */
    private void race(Charts.Line chart, int n) {
        h.removeCallbacksAndMessages(null);
        for (int k = 1; k <= n; k++) {
            final int step = k;
            h.postDelayed(() -> chart.reveal(step), (long) k * Math.max(90, 2400 / n));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
    }

    // ------------------------------------------------------------------ share as picture

    private void share() {
        if (result == null || result.getWidth() == 0) return;
        try {
            int pad = Ui.dp(this, 16);
            Bitmap bm = Bitmap.createBitmap(result.getWidth() + pad * 2, result.getHeight() + pad * 3, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            cv.drawColor(Ui.col(this, R.color.bg));
            cv.save();
            cv.translate(pad, pad);
            result.draw(cv);
            cv.restore();
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(Ui.col(this, R.color.text2));
            p.setTextSize(Ui.dp(this, 12));
            p.setTypeface(Ui.face(this, Ui.SEMIBOLD));
            cv.drawText("Made with Money · for learning, not financial advice", pad, bm.getHeight() - pad, p);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            Uri u = Report.save(this, "Shared", calc.id + "-" + System.currentTimeMillis() + ".png", "image/png", out.toByteArray());
            if (u == null) { Ui.toast(this, "Saved in Money's folder"); return; }
            Intent i = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) {
            Ui.toast(this, "Couldn't make the picture");
        }
    }
}
