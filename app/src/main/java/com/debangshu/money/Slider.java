package com.debangshu.money;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** A labelled slider: "Every month ……… ₹5,000" with a SeekBar under it. Values are whole steps. */
final class Slider {

    interface Format { String text(long v); }

    interface Change { void on(long v); }

    final LinearLayout view;
    private final SeekBar bar;
    private final TextView value;
    private final long min, step;
    private final Format fmt;
    private long v;

    Slider(Context c, String label, long min, long max, long step, long start, Format fmt, Change change) {
        this.min = min;
        this.step = step;
        this.fmt = fmt;
        view = Ui.vbox(c);
        LinearLayout top = Ui.hbox(c);
        top.addView(Ui.text(c, label, 14, Ui.col(c, R.color.text2), Ui.SEMIBOLD), Ui.weight1());
        value = Ui.text(c, "", 15, Ui.BOLD);
        top.addView(value);
        view.addView(top);
        bar = new SeekBar(c);
        bar.setMax((int) Math.max(1, (max - min) / step));
        view.addView(bar, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 6));
        set(start);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                v = Slider.this.min + p * Slider.this.step;
                value.setText(Slider.this.fmt.text(v));
                if (user) change.on(v);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
    }

    void set(long val) {
        long clamped = Math.max(min, Math.min(min + bar.getMax() * step, val));
        bar.setProgress((int) ((clamped - min) / step));
        v = min + bar.getProgress() * step;
        value.setText(fmt.text(v));
    }

    long get() { return v; }

    static String rs(long rupees) { return Fmt.rs(rupees * 100); }
}
