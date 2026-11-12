package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/** The look of the app: Plus Jakarta Sans, rounded cards, pill buttons, sheets from the bottom. */
public final class Ui {

    private Ui() {}

    public static final int REGULAR = 0, SEMIBOLD = 1, BOLD = 2, EXTRABOLD = 3;
    private static final String[] FILES = {"ui/Regular.ttf", "ui/SemiBold.ttf", "ui/Bold.ttf", "ui/ExtraBold.ttf"};
    private static final Typeface[] FACES = new Typeface[4];

    public static Typeface face(Context ctx, int weight) {
        weight = Math.max(0, Math.min(3, weight));
        if (FACES[weight] == null) {
            try { FACES[weight] = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), FILES[weight]); }
            catch (Exception e) { FACES[weight] = weight >= BOLD ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT; }
        }
        return FACES[weight];
    }

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static int col(Context c, int res) { return c.getColor(res); }

    public static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x297A7F99), content, content == null ? new ColorDrawable(0xFFFFFFFF) : null);
    }

    public static Drawable rippleRound(Context c, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x297A7F99), round(color, dp(c, radiusDp)), null);
    }

    // ------------------------------------------------------------------ building blocks

    public static TextView text(Context c, CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face(c, weight));
        t.setIncludeFontPadding(true);
        return t;
    }

    public static TextView text(Context c, CharSequence s, float sp, int weight) {
        return text(c, s, sp, col(c, R.color.text), weight);
    }

    public static TextView sub(Context c, CharSequence s) {
        return text(c, s, 13, col(c, R.color.text2), REGULAR);
    }

    public static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** A white rounded card with padding. */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(round(col(c, R.color.surface), dp(c, 22)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams matchWrap() { return lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    public static LinearLayout.LayoutParams weight1() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams l, Context c, int left, int top, int right, int bottom) {
        l.setMargins(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
        return l;
    }

    public static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 17, EXTRABOLD);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    /** Pill button: filled accent (main) or soft grey. */
    public static TextView button(Context c, String label, boolean main) {
        TextView b = text(c, label, 14, main ? col(c, R.color.on_accent) : col(c, R.color.text), BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 42));
        b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
        b.setBackground(rippleRound(c, main ? col(c, R.color.accent) : col(c, R.color.surface2), 21));
        return b;
    }

    /** Small rounded chip. */
    public static TextView chip(Context c, String label, boolean on) {
        TextView t = text(c, label, 13, on ? col(c, R.color.on_accent) : col(c, R.color.text), SEMIBOLD);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setBackground(rippleRound(c, on ? col(c, R.color.accent) : col(c, R.color.surface), 18));
        return t;
    }

    public static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(tint);
        i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(c, (44 - sizeDp) / 2f);
        i.setPadding(p, p, p, p);
        i.setBackground(rippleRound(c, 0x00000000, 22));
        i.setLayoutParams(lp(dp(c, 44), dp(c, 44)));
        return i;
    }

    /** Round emoji badge for a category. */
    public static TextView badge(Context c, String emoji, int color, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.45f);
        t.setBackground(round((color & 0x00FFFFFF) | (dark(c) ? 0x44000000 : 0x2A000000), dp(c, sizeDp / 2f)));
        t.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.divider));
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2)));
        return v;
    }

    public static View space(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(c, dpH)));
        return v;
    }

    /** Status and navigation bar colours that match the screen. */
    public static void bars(Activity a) {
        Window w = a.getWindow();
        int color = col(a, R.color.bg);
        boolean light = !dark(a);
        w.setStatusBarColor(color);
        w.setNavigationBarColor(color);
        int flags = w.getDecorView().getSystemUiVisibility();
        if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
        if (Store.get(a).flag(Store.S_SECURE)) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    // ------------------------------------------------------------------ popups

    /** Like AlertDialog.Builder, with rounded cards, pill buttons and lists as bottom sheets. */
    public static class Popup extends AlertDialog.Builder {
        private CharSequence title;
        private boolean items, view, message, forceSheet;

        public Popup(Context c) { super(c); }

        public Popup sheet() { forceSheet = true; return this; }

        @Override public AlertDialog.Builder setTitle(CharSequence t) { title = t; return this; }
        @Override public AlertDialog.Builder setTitle(int id) { title = getContext().getText(id); return this; }
        @Override public AlertDialog.Builder setMessage(CharSequence m) { message = m != null; return super.setMessage(m); }
        @Override public AlertDialog.Builder setView(View v) { view = true; return super.setView(v); }
        @Override public AlertDialog.Builder setItems(CharSequence[] i, DialogInterface.OnClickListener l) { items = true; return super.setItems(i, l); }
        @Override public AlertDialog.Builder setAdapter(ListAdapter a, DialogInterface.OnClickListener l) { items = true; return super.setAdapter(a, l); }
        @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] i, int c, DialogInterface.OnClickListener l) {
            items = true; return super.setSingleChoiceItems(i, c, l);
        }
        @Override public AlertDialog.Builder setMultiChoiceItems(CharSequence[] i, boolean[] c, DialogInterface.OnMultiChoiceClickListener l) {
            items = true; return super.setMultiChoiceItems(i, c, l);
        }

        @Override
        public AlertDialog create() {
            final boolean sheet = forceSheet || (items && !view && !message);
            Context c = getContext();
            if (title != null || sheet) super.setCustomTitle(titleView(c, title, sheet));
            AlertDialog d = super.create();
            Window w = d.getWindow();
            if (w != null && sheet) {
                w.setGravity(Gravity.BOTTOM);
                w.setBackgroundDrawable(c.getDrawable(R.drawable.bg_sheet));
                w.setWindowAnimations(R.style.SheetAnim);
            }
            d.setOnShowListener(x -> style(d, sheet));
            return d;
        }
    }

    private static View titleView(Context c, CharSequence title, boolean sheet) {
        LinearLayout box = vbox(c);
        if (sheet) {
            View handle = new View(c);
            handle.setBackground(round(col(c, R.color.divider), dp(c, 3)));
            LinearLayout.LayoutParams hl = lp(dp(c, 40), dp(c, 5));
            hl.gravity = Gravity.CENTER_HORIZONTAL;
            hl.topMargin = dp(c, 10);
            hl.bottomMargin = dp(c, title == null ? 8 : 2);
            box.addView(handle, hl);
        }
        if (title != null) {
            TextView t = text(c, title, sheet ? 18 : 20, EXTRABOLD);
            t.setPadding(dp(c, 24), dp(c, sheet ? 12 : 24), dp(c, 24), dp(c, sheet ? 8 : 6));
            box.addView(t);
        }
        return box;
    }

    static void fonts(View root) {
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            Typeface tf = t.getTypeface();
            boolean ours = false;
            for (Typeface f : FACES) if (f != null && f.equals(tf)) ours = true;
            if (!ours) t.setTypeface(face(t.getContext(), tf != null && tf.isBold() ? BOLD : REGULAR));
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) fonts(g.getChildAt(i));
        }
    }

    public static void style(AlertDialog d, boolean sheet) {
        Window w = d.getWindow();
        if (w == null) return;
        Context c = d.getContext();
        if (sheet) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.getDecorView().setPadding(0, 0, 0, 0);
        }
        fonts(w.getDecorView());
        TextView msg = d.findViewById(android.R.id.message);
        if (msg != null) {
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            msg.setLineSpacing(dp(c, 3), 1f);
            msg.setTextColor(col(c, R.color.text2));
        }
        ListView lv = d.getListView();
        if (lv != null) {
            lv.setDivider(null);
            lv.setSelector(new ColorDrawable(0));
            lv.setPadding(dp(c, 8), 0, dp(c, 8), dp(c, sheet ? 18 : 6));
            lv.setClipToPadding(false);
        }
        Button pos = d.getButton(DialogInterface.BUTTON_POSITIVE);
        Button neg = d.getButton(DialogInterface.BUTTON_NEGATIVE);
        Button neu = d.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (pos != null) {
            String label = pos.getText() == null ? "" : pos.getText().toString().toLowerCase(java.util.Locale.ROOT);
            boolean danger = label.contains("delete") || label.contains("erase") || label.contains("remove");
            pill(pos, danger ? col(c, R.color.danger) : col(c, R.color.accent), col(c, R.color.on_accent));
        }
        if (neg != null) pill(neg, col(c, R.color.surface2), col(c, R.color.text));
        if (neu != null) pill(neu, 0, col(c, R.color.accent));
        View panel = pos != null ? (View) pos.getParent() : null;
        if (panel != null) panel.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, sheet ? 18 : 16));
    }

    private static void pill(Button b, int fill, int text) {
        Context c = b.getContext();
        b.setAllCaps(false);
        b.setTypeface(face(c, BOLD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(text);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 42));
        b.setMinimumHeight(dp(c, 42));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setBackground(ripple(round(fill, dp(c, 21))));
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
            b.setLayoutParams(lp);
        }
    }

    /** Wraps a scrolling view so a popup shows it at most {@code fraction} of the screen tall. */
    public static View capHeight(View content, float fraction) {
        Context c = content.getContext();
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        int h = (int) (c.getResources().getDisplayMetrics().heightPixels * fraction);
        f.addView(content, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return f;
    }

    public static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show();
    }
}
