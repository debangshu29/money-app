package com.debangshu.money;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Two small hand-drawn charts: a ring of categories and bars for each day of the month. */
public final class Charts {

    private Charts() {}

    /** Ring chart: each slice is a category's share. */
    public static class Donut extends View {
        private long[] values = new long[0];
        private int[] colors = new int[0];
        private String center = "", centerSub = "";
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Donut(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.BUTT);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        public void set(long[] v, int[] c, String center, String sub) {
            values = v; colors = c; this.center = center; centerSub = sub;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int size = Math.min(MeasureSpec.getSize(w), Ui.dp(getContext(), 200));
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float stroke = Ui.dp(getContext(), 22);
            float pad = stroke / 2 + 2;
            r.set(pad, pad, w - pad, h - pad);
            p.setStrokeWidth(stroke);
            long total = 0;
            for (long v : values) total += v;
            if (total <= 0) {
                p.setColor(Ui.col(getContext(), R.color.surface2));
                c.drawArc(r, 0, 360, false, p);
            } else {
                float start = -90;
                float gap = values.length > 1 ? 1.5f : 0;
                for (int i = 0; i < values.length; i++) {
                    float sweep = 360f * values[i] / total;
                    if (sweep <= 0) continue;
                    p.setColor(colors[i]);
                    c.drawArc(r, start + gap / 2, Math.max(0.5f, sweep - gap), false, p);
                    start += sweep;
                }
            }
            tp.setColor(Ui.col(getContext(), R.color.text));
            tp.setTypeface(Ui.face(getContext(), Ui.EXTRABOLD));
            tp.setTextSize(Ui.dp(getContext(), 22));
            c.drawText(center, w / 2, h / 2 + Ui.dp(getContext(), 4), tp);
            tp.setColor(Ui.col(getContext(), R.color.text2));
            tp.setTypeface(Ui.face(getContext(), Ui.REGULAR));
            tp.setTextSize(Ui.dp(getContext(), 12));
            c.drawText(centerSub, w / 2, h / 2 + Ui.dp(getContext(), 22), tp);
        }
    }

    /** Bars for each day; tap a bar to see that day's amount. */
    public static class Bars extends View {
        private long[] values = new long[0];
        private int today = -1, picked = -1;
        private long budgetPerDay = 0;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        public interface OnPick { void pick(int day, long value); }
        private OnPick onPick;

        public Bars(Context c) {
            super(c);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        public void set(long[] v, int todayIndex, long perDay, OnPick l) {
            values = v; today = todayIndex; budgetPerDay = perDay; onPick = l; picked = -1;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 150));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (values.length == 0) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int i = (int) (e.getX() / (getWidth() / (float) values.length));
                i = Math.max(0, Math.min(values.length - 1, i));
                if (i != picked) {
                    picked = i;
                    invalidate();
                    if (onPick != null) onPick.pick(i, values[i]);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (values.length == 0) return;
            float w = getWidth(), h = getHeight() - Ui.dp(getContext(), 18);
            long max = Math.max(1, budgetPerDay);
            for (long v : values) max = Math.max(max, v);
            float slot = w / values.length;
            float bw = Math.max(2, slot * 0.62f);
            float rad = Math.min(bw / 2, Ui.dp(getContext(), 4));
            for (int i = 0; i < values.length; i++) {
                float x = i * slot + (slot - bw) / 2;
                float bh = values[i] <= 0 ? Ui.dp(getContext(), 2) : Math.max(Ui.dp(getContext(), 3), h * values[i] / (float) max);
                r.set(x, h - bh, x + bw, h);
                int col;
                if (i == picked) col = Ui.col(getContext(), R.color.text);
                else if (today >= 0 && i > today) col = Ui.col(getContext(), R.color.surface2);
                else if (budgetPerDay > 0 && values[i] > budgetPerDay) col = Ui.col(getContext(), R.color.warn);
                else col = Ui.col(getContext(), R.color.accent);
                p.setColor(col);
                c.drawRoundRect(r, rad, rad, p);
            }
            if (budgetPerDay > 0) {
                float y = h - h * budgetPerDay / (float) max;
                p.setColor(Ui.col(getContext(), R.color.text2));
                p.setStrokeWidth(Ui.dp(getContext(), 1));
                for (float x = 0; x < w; x += Ui.dp(getContext(), 8)) c.drawLine(x, y, x + Ui.dp(getContext(), 4), y, p);
            }
            tp.setColor(Ui.col(getContext(), R.color.text2));
            tp.setTypeface(Ui.face(getContext(), Ui.REGULAR));
            tp.setTextSize(Ui.dp(getContext(), 10));
            int[] marks = {0, 9, 19, values.length - 1};
            for (int m : marks) {
                if (m < 0 || m >= values.length) continue;
                c.drawText(String.valueOf(m + 1), m * slot + slot / 2, getHeight() - Ui.dp(getContext(), 3), tp);
            }
        }
    }

    /** A progress bar. In budget mode it fills with a smooth green → yellow → orange → red → maroon gradient. */
    public static class Progress extends View {
        private float frac;
        private int color;
        private boolean budget;
        private int heightDp = 8;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Progress(Context c) { super(c); }

        public void set(float f, int col) { frac = f; color = col; budget = false; invalidate(); }

        /** Budget bar: how much of the budget is used (can be over 1). */
        public void budget(float f, int heightDp) { frac = f; budget = true; this.heightDp = heightDp; requestLayout(); invalidate(); }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), budget ? heightDp : 8)); }

        @Override
        protected void onDraw(Canvas c) {
            float rad = getHeight() / 2f;
            r.set(0, 0, getWidth(), getHeight());
            p.setShader(null);
            p.setColor(budget ? (Ui.dark(getContext()) ? 0x33FFFFFF : 0x1F000000) : Ui.col(getContext(), R.color.surface2));
            c.drawRoundRect(r, rad, rad, p);
            float f = Math.max(0, Math.min(1, frac));
            r.set(0, 0, Math.max(getHeight(), getWidth() * f), getHeight());
            if (frac <= 0) return;
            if (budget) {
                int end = Ui.budgetColor(frac);
                int mid = Ui.budgetColor(frac / 2);
                p.setShader(new android.graphics.LinearGradient(0, 0, r.right, 0, new int[]{Ui.budgetColor(0), mid, end},
                        new float[]{0f, 0.5f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            } else {
                p.setColor(color);
            }
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
        }
    }

    /**
     * A simple line chart: one or more series over the same x points, an optional dashed target line,
     * money labels on the left, first/last x labels, and touch to see the values at a point.
     */
    public static class Line extends View {
        private float[][] series = new float[0][];
        private int[] colors = new int[0];
        private boolean[] dashed = new boolean[0], filled = new boolean[0];
        private String[] xLabels = new String[0];
        private float target = -1;
        private int touch = -1;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private int heightDp = 200;
        private int reveal = -1;          // draw only the first N points (for the race animation); -1 = all
        public interface OnTouchPoint { void at(int index); }

        public void reveal(int n) { reveal = n; invalidate(); }
        private OnTouchPoint listener;

        public Line(Context c) { super(c); }

        /** values are rupees (floats), all series the same length. */
        public void set(float[][] s, int[] col, boolean[] dash, boolean[] fill, String[] x, float targetLine) {
            series = s; colors = col; dashed = dash; filled = fill; xLabels = x; target = targetLine; touch = -1; reveal = -1;
            invalidate();
        }

        public void height(int dp) { heightDp = dp; requestLayout(); }

        public void onTouchPoint(OnTouchPoint l) { listener = l; }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), heightDp)); }

        static String money(float v) {
            float a = Math.abs(v);
            String s = a >= 1e7f ? String.format(java.util.Locale.ROOT, "%.1fCr", a / 1e7f)
                    : a >= 1e5f ? String.format(java.util.Locale.ROOT, "%.1fL", a / 1e5f)
                    : a >= 1e3f ? Math.round(a / 1e3f) + "k" : String.valueOf(Math.round(a));
            return (v < 0 ? "-₹" : "₹") + s.replace(".0Cr", "Cr").replace(".0L", "L");
        }

        private float max() {
            float m = Math.max(1, target);
            for (float[] s : series) for (float v : s) m = Math.max(m, v);
            return m * 1.08f;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (series.length == 0 || series[0].length < 2) return false;
            float left = Ui.dp(getContext(), 44), right = getWidth() - Ui.dp(getContext(), 8);
            int n = series[0].length;
            int i = Math.round((e.getX() - left) / Math.max(1, right - left) * (n - 1));
            i = Math.max(0, Math.min(n - 1, i));
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            } else {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (i != touch) { touch = i; if (listener != null) listener.at(i); invalidate(); }
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (series.length == 0 || series[0].length < 2) return;
            Context ctx = getContext();
            float left = Ui.dp(ctx, 44), right = getWidth() - Ui.dp(ctx, 8), top = Ui.dp(ctx, 8), bottom = getHeight() - Ui.dp(ctx, 22);
            float mx = max();
            int n = series[0].length;
            tp.setTextSize(Ui.dp(ctx, 10.5f));
            tp.setColor(Ui.col(ctx, R.color.text2));
            tp.setTypeface(Ui.face(ctx, Ui.SEMIBOLD));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(ctx, 1));
            p.setPathEffect(null);
            p.setColor(Ui.dark(ctx) ? 0x22FFFFFF : 0x14000000);
            for (int g = 0; g <= 3; g++) {
                float y = bottom - (bottom - top) * g / 3f;
                c.drawLine(left, y, right, y, p);
                tp.setTextAlign(Paint.Align.RIGHT);
                c.drawText(money(mx * g / 3f), left - Ui.dp(ctx, 6), y + Ui.dp(ctx, 4), tp);
            }
            if (xLabels.length > 0) {
                tp.setTextAlign(Paint.Align.LEFT);
                c.drawText(xLabels[0], left, getHeight() - Ui.dp(ctx, 4), tp);
                tp.setTextAlign(Paint.Align.RIGHT);
                c.drawText(xLabels[xLabels.length - 1], right, getHeight() - Ui.dp(ctx, 4), tp);
            }
            if (target > 0) {
                float y = bottom - (bottom - top) * target / mx;
                p.setColor(Ui.col(ctx, R.color.text2));
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(ctx, 4), Ui.dp(ctx, 4)}, 0));
                c.drawLine(left, y, right, y, p);
                p.setPathEffect(null);
            }
            int upto = reveal < 0 ? n : Math.max(1, Math.min(n, reveal));
            for (int k = 0; k < series.length; k++) {
                path.reset();
                for (int i = 0; i < upto; i++) {
                    float x = left + (right - left) * i / (float) (n - 1);
                    float y = bottom - (bottom - top) * Math.max(0, series[k][i]) / mx;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                if (filled.length > k && filled[k]) {
                    android.graphics.Path f = new android.graphics.Path(path);
                    f.lineTo(left + (right - left) * (upto - 1) / (float) (n - 1), bottom);
                    f.lineTo(left, bottom);
                    f.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor((colors[k] & 0x00FFFFFF) | 0x22000000);
                    c.drawPath(f, p);
                    p.setStyle(Paint.Style.STROKE);
                }
                p.setColor(colors[k]);
                p.setStrokeWidth(Ui.dp(ctx, 2.2f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setPathEffect(dashed.length > k && dashed[k] ? new android.graphics.DashPathEffect(new float[]{Ui.dp(ctx, 6), Ui.dp(ctx, 4)}, 0) : null);
                c.drawPath(path, p);
                p.setPathEffect(null);
            }
            if (touch >= 0 && touch < n) {
                float x = left + (right - left) * touch / (float) (n - 1);
                p.setStrokeWidth(Ui.dp(ctx, 1));
                p.setColor(Ui.col(ctx, R.color.text2));
                c.drawLine(x, top, x, bottom, p);
                p.setStyle(Paint.Style.FILL);
                for (int k = 0; k < series.length; k++) {
                    float y = bottom - (bottom - top) * Math.max(0, series[k][touch]) / mx;
                    p.setColor(colors[k]);
                    c.drawCircle(x, y, Ui.dp(ctx, 4), p);
                }
                p.setStyle(Paint.Style.STROKE);
            }
        }
    }
}
