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

    /** A thin progress bar (budget used). */
    public static class Progress extends View {
        private float frac;
        private int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Progress(Context c) { super(c); }

        public void set(float f, int col) { frac = f; color = col; invalidate(); }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 8)); }

        @Override
        protected void onDraw(Canvas c) {
            float rad = getHeight() / 2f;
            r.set(0, 0, getWidth(), getHeight());
            p.setColor(Ui.col(getContext(), R.color.surface2));
            c.drawRoundRect(r, rad, rad, p);
            r.set(0, 0, getWidth() * Math.max(0, Math.min(1, frac)), getHeight());
            p.setColor(color);
            if (r.width() > 0) c.drawRoundRect(r, rad, rad, p);
        }
    }
}
