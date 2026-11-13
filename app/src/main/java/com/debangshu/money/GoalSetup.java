package com.debangshu.money;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Calendar;

/** New goal in 3 taps: what for → how much and by when → where the money will be. */
public class GoalSetup extends Activity {

    static void start(Context c) { c.startActivity(new Intent(c, GoalSetup.class)); }

    /** Festival planner: start with the festival already filled in. */
    static void festival(Context c, String name, long by, long suggest) {
        c.startActivity(new Intent(c, GoalSetup.class).putExtra("kind", "festival").putExtra("name", name)
                .putExtra("by", by).putExtra("suggest", suggest));
    }

    private int step;
    private String kind = "other", emoji = "🎯", name = "";
    private long target, by;
    private final String[] account = {null};
    private boolean manual, existing = true;
    private LinearLayout body;
    private TextView next, stepLabel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "New goal"));
        stepLabel = Ui.text(this, "", 13, Ui.col(this, R.color.text2), Ui.BOLD);
        stepLabel.setPadding(Ui.dp(this, 20), 0, 0, Ui.dp(this, 6));
        root.addView(stepLabel);
        ScrollView sv = new ScrollView(this);
        body = Ui.vbox(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        next = Ui.button(this, "Next", true);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> next());
        root.addView(next, Ui.margins(Ui.matchWrap(), this, 16, 4, 16, 16));
        setContentView(root);

        Intent i = getIntent();
        if (i.hasExtra("kind")) {
            kind = i.getStringExtra("kind");
            for (String[] t : Goals.TEMPLATES) if (t[0].equals(kind)) emoji = t[1];
            name = i.getStringExtra("name") == null ? "" : i.getStringExtra("name");
            by = i.getLongExtra("by", 0);
            target = i.getLongExtra("suggest", 0);
            step = 1;
        }
        show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
    }

    @Override
    public void onBackPressed() {
        if (step > 0 && !getIntent().hasExtra("kind")) { step--; show(); }
        else super.onBackPressed();
    }

    private void show() {
        body.removeAllViews();
        stepLabel.setText("Step " + (step + 1) + " of 3");
        if (step == 0) what();
        else if (step == 1) howMuch();
        else where();
        next.setText(step == 2 ? "Create goal" : "Next");
    }

    private void next() {
        if (step == 0) {
            if (name.trim().isEmpty()) { Ui.toast(this, "Give it a name"); return; }
        } else if (step == 1) {
            if (target <= 0) { Ui.toast(this, "How much do you need?"); return; }
        } else {
            if (!manual && account[0] == null) { Ui.toast(this, "Pick an account, or “I'll add it myself”"); return; }
            JSONObject g = Goals.create(this, kind, emoji, name.trim(), target, by, manual ? "" : account[0], existing);
            startActivity(new Intent(this, GoalActivity.class).putExtra("id", g.optString("id")).putExtra("fresh", true));
            finish();
            return;
        }
        step++;
        show();
    }

    private TextView heading(String s) {
        TextView t = Ui.text(this, s, 22, Ui.EXTRABOLD);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), 0, Ui.dp(this, 14));
        body.addView(t);
        return t;
    }

    // ------------------------------------------------------------------ 1. what for

    private void what() {
        heading("What are you saving for?");
        EditText nm = new EditText(this);
        View[] items = new View[Goals.TEMPLATES.length];
        for (int k = 0; k < Goals.TEMPLATES.length; k++) {
            String[] t = Goals.TEMPLATES[k];
            items[k] = Ui.action(this, t[1], Goals.COLORS[k % Goals.COLORS.length], t[2], v -> {
                kind = t[0];
                emoji = t[1];
                name = "other".equals(t[0]) ? "" : t[2];
                nm.setText(name);
                nm.setSelection(nm.getText().length());
                if (!"other".equals(t[0])) next();
                else nm.requestFocus();
            });
        }
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        c.addView(Ui.grid(this, items));
        body.addView(c);
        TextView or = Ui.text(this, "Or give it your own name", 13, Ui.col(this, R.color.text2), Ui.BOLD);
        or.setPadding(Ui.dp(this, 6), Ui.dp(this, 18), 0, Ui.dp(this, 4));
        body.addView(or);
        nm.setHint("e.g. Puja shopping, Goa trip, new guitar");
        nm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nm.setTypeface(Ui.face(this, Ui.SEMIBOLD));
        nm.setText(name);
        nm.addTextChangedListener(watcher(s -> name = s));
        body.addView(nm);
    }

    // ------------------------------------------------------------------ 2. how much, by when

    private void howMuch() {
        heading(emoji.isEmpty() ? name : name + " — how much?");
        EditText amt = new EditText(this);
        amt.setHint("₹ amount");
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amt.setTextSize(28);
        amt.setTypeface(Ui.face(this, Ui.EXTRABOLD));
        if (target > 0) amt.setText(String.valueOf(target / 100));
        body.addView(amt);

        // ideas for the amount
        LinearLayout ideas = Ui.hbox(this);
        long spend = Goals.monthlySpend(this);
        if ("emergency".equals(kind) && spend > 0) {
            ideas.addView(suggest("3 months of spending · " + Fmt.rs(spend * 3), spend * 3, amt));
            ideas.addView(suggest("6 months · " + Fmt.rs(spend * 6), spend * 6, amt));
        } else if (getIntent().getLongExtra("suggest", 0) > 0) {
            ideas.addView(suggest("Last time ~" + Fmt.rs(getIntent().getLongExtra("suggest", 0)), getIntent().getLongExtra("suggest", 0), amt));
        }
        if (ideas.getChildCount() > 0) {
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(ideas);
            body.addView(hs, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 4));
        }
        if ("emergency".equals(kind)) {
            TextView w = Ui.sub(this, "An emergency fund is money kept aside for surprises — a job loss, a hospital bill. "
                    + "Most people aim for 3 to 6 months of what they spend.");
            w.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
            body.addView(w);
        }

        TextView bl = Ui.text(this, "By when?", 15, Ui.BOLD);
        bl.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 8));
        body.addView(bl);
        TextView perMonth = Ui.text(this, "", 15, Ui.col(this, R.color.accent), Ui.BOLD);
        Runnable upd = () -> {
            if (target > 0 && by > 0) perMonth.setText("That's " + Fmt.rs((long) Math.ceil(target / (double) GoalMath.monthsLeft(System.currentTimeMillis(), by)))
                    + " a month until " + Fmt.date(by));
            else if (target > 0) perMonth.setText("No date — Money shows when you'll get there at your pace");
            else perMonth.setText("");
        };
        amt.addTextChangedListener(watcher(s -> { target = SmsParser.paise(s.trim()); upd.run(); }));

        String[] labels = {"3 months", "6 months", "1 year", "2 years", "Pick a date", "No date"};
        int[] months = {3, 6, 12, 24, -1, 0};
        LinearLayout row = Ui.vbox(this);
        java.util.List<TextView> chips = new java.util.ArrayList<>();
        LinearLayout line = null;
        for (int k = 0; k < labels.length; k++) {
            if (k % 3 == 0) { line = Ui.hbox(this); row.addView(line, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8)); }
            TextView ch = Ui.chip(this, labels[k], false);
            ch.setGravity(Gravity.CENTER);
            final int m = months[k];
            ch.setOnClickListener(v -> {
                if (m == -1) {
                    Calendar cal = Calendar.getInstance();
                    if (by > 0) cal.setTimeInMillis(by); else cal.add(Calendar.MONTH, 6);
                    DatePickerDialog dp = new DatePickerDialog(this, (d, y, mo, day) -> {
                        Calendar k2 = Calendar.getInstance();
                        k2.set(y, mo, day, 12, 0, 0);
                        by = k2.getTimeInMillis();
                        ((TextView) v).setText(Fmt.date(by));
                        paintChips(chips, (TextView) v);
                        upd.run();
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                    dp.getDatePicker().setMinDate(System.currentTimeMillis() + GoalMath.DAY);
                    dp.show();
                    return;
                }
                if (m == 0) by = 0;
                else {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.MONTH, m);
                    by = cal.getTimeInMillis();
                }
                paintChips(chips, (TextView) v);
                upd.run();
            });
            chips.add(ch);
            line.addView(ch, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, k % 3 == 0 ? 0 : 8, 0, 0, 0));
        }
        body.addView(row);
        if (by > 0) { chips.get(4).setText(Fmt.date(by)); paintChips(chips, chips.get(4)); }
        perMonth.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, 0);
        body.addView(perMonth);
        upd.run();
        amt.requestFocus();
    }

    private void paintChips(java.util.List<TextView> chips, TextView on) {
        for (TextView t : chips) {
            boolean o = t == on;
            t.setBackground(Ui.rippleRound(this, o ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.surface2), 18));
            t.setTextColor(o ? Ui.col(this, R.color.on_accent) : Ui.col(this, R.color.text));
        }
    }

    private TextView suggest(String label, long paise, EditText amt) {
        TextView t = Ui.chip(this, label, false);
        t.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.accent_soft), 18));
        t.setOnClickListener(v -> { amt.setText(String.valueOf(paise / 100)); amt.setSelection(amt.getText().length()); });
        LinearLayout.LayoutParams lp = Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ 3. where the money will be

    private void where() {
        heading("Where will the money be?");
        TextView why = Ui.sub(this, "Pick the bank account you'll keep it in. Money then checks that account's balance and tells you "
                + "if you're ahead, on track or behind — by itself.");
        why.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
        body.addView(why);
        Switch ex = new Switch(this);
        TextView manualCard = Ui.text(this, "", 15, Ui.SEMIBOLD);
        Runnable paintManual = () -> {
            manualCard.setBackground(Ui.rippleRound(this, manual ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.surface2), 16));
            manualCard.setTextColor(manual ? Ui.col(this, R.color.on_accent) : Ui.col(this, R.color.text));
        };
        View picker = Noted.fromPicker(this, account, false, () -> {
            manual = false;
            paintManual.run();
            long[] lv = Noted.liveOf(this, account[0]);
            long bal = lv == null ? -1 : lv[0];
            ex.setVisibility(View.VISIBLE);
            ex.setText(bal > 0 ? "Count the " + Fmt.rs(bal) + " already in it" : "Count what's already in it");
        });
        body.addView(picker);
        manualCard.setText("✍️   I'll add the money myself");
        manualCard.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16));
        manualCard.setOnClickListener(v -> {
            manual = true;
            account[0] = null;
            show();   // repaint the account buttons as not chosen
        });
        paintManual.run();
        body.addView(manualCard, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));

        ex.setChecked(existing);
        ex.setTypeface(Ui.face(this, Ui.SEMIBOLD));
        ex.setTextSize(14);
        ex.setVisibility(account[0] == null ? View.GONE : View.VISIBLE);
        ex.setOnCheckedChangeListener((b, on) -> existing = on);
        body.addView(ex, Ui.margins(Ui.matchWrap(), this, 4, 14, 4, 0));
        TextView tip = Ui.sub(this, "Tip: a separate account just for the goal (like your second Federal account) works best — "
                + "money you don't see, you don't spend.");
        tip.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, 0);
        body.addView(tip);
    }

    // ------------------------------------------------------------------ helper

    interface OnText { void on(String s); }

    static TextWatcher watcher(OnText o) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { o.on(s.toString()); }
        };
    }
}
