package com.debangshu.money;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * Goals: "Laptop ₹60,000 by March". Shows how much to put aside each month,
 * and whether you're on track from what you actually saved lately (money in − money out).
 */
public class Goals extends Activity {

    static final String KEY = "goals";

    static JSONArray list(Context c) { return Store.get(c).list(KEY); }

    static void save(Context c, JSONArray a) { Store.get(c).putList(KEY, a); }

    /** Average saved per month over the last 3 full months (money in − money out). */
    static long monthlySaving(Context c) {
        List<Txn> all = Store.get(c).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long sum = 0;
        int n = 0;
        for (int k = 1; k <= 3; k++) {
            long s = Fmt.addMonths(ms, -k), e = Fmt.addMonths(ms, -k + 1);
            long in = Ledger.income(all, s, e), out = Ledger.spent(all, s, e);
            if (in == 0 && out == 0) continue;
            sum += in - out;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    static int monthsLeft(long by) {
        if (by <= 0) return 0;
        Calendar a = Fmt.cal(System.currentTimeMillis()), b = Fmt.cal(by);
        int m = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + b.get(Calendar.MONTH) - a.get(Calendar.MONTH);
        return Math.max(1, m);
    }

    /** One line for the Plan tile. */
    static String summary(Context c) {
        JSONArray a = list(c);
        if (a.length() == 0) return "Save for something — a laptop, a trip";
        long saved = 0, target = 0;
        for (int i = 0; i < a.length(); i++) { saved += a.optJSONObject(i).optLong("saved"); target += a.optJSONObject(i).optLong("target"); }
        return a.length() + (a.length() == 1 ? " goal · " : " goals · ") + Math.round(saved * 100.0 / Math.max(1, target)) + "% saved";
    }

    static float progress(Context c) {
        JSONArray a = list(c);
        long saved = 0, target = 0;
        for (int i = 0; i < a.length(); i++) { saved += a.optJSONObject(i).optLong("saved"); target += a.optJSONObject(i).optLong("target"); }
        return target == 0 ? -1 : saved / (float) target;
    }

    // ------------------------------------------------------------------ screen

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "Goals"));
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        build();
    }

    private void build() {
        box.removeAllViews();
        JSONArray a = list(this);
        long saving = monthlySaving(this);
        long needAll = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.optJSONObject(i);
            long left = Math.max(0, g.optLong("target") - g.optLong("saved"));
            if (left > 0 && g.optLong("by") > 0) needAll += left / monthsLeft(g.optLong("by"));
        }
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "You usually save", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(Math.max(0, saving)) + " a month", 26, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, saving <= 0 ? "Lately more went out than came in — goals need a little room."
                : needAll == 0 ? "Money in − money out, average of the last 3 months."
                : needAll <= saving ? "✓ Enough for all your goals (" + Fmt.rs(needAll) + " a month needed)"
                : "Goals need " + Fmt.rs(needAll) + " a month — " + Fmt.rs(needAll - saving) + " more than you usually save"));
        box.addView(hero, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));

        for (int i = 0; i < a.length(); i++) box.addView(goalCard(a.optJSONObject(i), saving), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        if (a.length() == 0) {
            TextView e = Ui.sub(this, "No goals yet. A goal is something you're saving for — Money shows how much to put aside each month.");
            e.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
            box.addView(e);
        }
        TextView add = Ui.button(this, "＋  New goal", true);
        add.setOnClickListener(v -> edit(null));
        box.addView(add);
    }

    private View goalCard(JSONObject g, long saving) {
        LinearLayout c = Ui.card(this);
        long target = g.optLong("target"), saved = g.optLong("saved"), by = g.optLong("by");
        float f = saved / (float) Math.max(1, target);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.badge(this, g.optString("emoji", "🎯"), 0xFF10B981, 44));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, g.optString("name"), 16, Ui.BOLD));
        col.addView(Ui.sub(this, Fmt.rs(saved) + " of " + Fmt.rs(target) + (by > 0 ? " · by " + Fmt.MON[Fmt.cal(by).get(Calendar.MONTH)] + " " + Fmt.cal(by).get(Calendar.YEAR) : "")));
        h.addView(col, Ui.weight1());
        h.addView(Ui.text(this, Math.round(f * 100) + "%", 16, Ui.col(this, R.color.accent), Ui.EXTRABOLD));
        c.addView(h);
        Charts.Progress pr = new Charts.Progress(this);
        pr.set(f, Ui.col(this, R.color.accent));
        c.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 8));
        long left = Math.max(0, target - saved);
        String line;
        if (left == 0) line = "🎉 Reached!";
        else if (by > 0) {
            long per = left / monthsLeft(by);
            line = "Put aside " + Fmt.rs(per) + " a month · " + (saving >= per ? "on track ✓" : "a stretch — you usually save " + Fmt.rs(Math.max(0, saving)));
        } else line = saving > 0 ? "At your usual saving: about " + (left / saving + 1) + " months" : Fmt.rs(left) + " to go";
        c.addView(Ui.text(this, line, 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView addM = Ui.chip(this, "＋ Add money", true);
        TextView take = Ui.chip(this, "− Take out", false);
        take.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        TextView more = Ui.chip(this, "Edit", false);
        more.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        String id = g.optString("id");
        addM.setOnClickListener(v -> money(id, 1));
        take.setOnClickListener(v -> money(id, -1));
        more.setOnClickListener(v -> edit(g));
        r.addView(addM, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        r.addView(take, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        r.addView(more);
        c.addView(r);
        return c;
    }

    private void money(String id, int sign) {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setHint("₹");
        e.setTextSize(22);
        e.setTypeface(Ui.face(this, Ui.BOLD));
        b.addView(e);
        new Ui.Popup(this).setTitle(sign > 0 ? "Put aside" : "Take out").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v <= 0) return;
                    JSONArray a = list(this);
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject g = a.optJSONObject(i);
                        if (!id.equals(g.optString("id"))) continue;
                        try { g.put("saved", Math.max(0, g.optLong("saved") + sign * v)); } catch (Exception ignored) { }
                        if (sign > 0 && g.optLong("saved") >= g.optLong("target")) Ui.toast(this, "🎉 Goal reached: " + g.optString("name"));
                    }
                    save(this, a);
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void edit(JSONObject g) {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        EditText name = new EditText(this);
        name.setHint("What for? e.g. Laptop");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText emoji = new EditText(this);
        emoji.setHint("Emoji (optional) e.g. 💻");
        EditText target = new EditText(this);
        target.setHint("Target amount (₹)");
        target.setInputType(InputType.TYPE_CLASS_NUMBER);
        for (EditText e : new EditText[]{name, emoji, target}) { e.setTypeface(Ui.face(this, Ui.REGULAR)); b.addView(e); }
        final long[] by = {g == null ? 0 : g.optLong("by")};
        TextView when = Ui.chip(this, by[0] > 0 ? "📅 By " + Fmt.date(by[0]) : "📅 Pick a date (optional)", false);
        when.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        when.setOnClickListener(v -> {
            Calendar k = Calendar.getInstance();
            if (by[0] > 0) k.setTimeInMillis(by[0]); else k.add(Calendar.MONTH, 6);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar c = Calendar.getInstance();
                c.set(y, m, d, 12, 0, 0);
                by[0] = c.getTimeInMillis();
                when.setText("📅 By " + Fmt.date(by[0]));
            }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
        });
        LinearLayout wr = Ui.hbox(this);
        wr.setPadding(0, Ui.dp(this, 10), 0, 0);
        wr.addView(when);
        b.addView(wr);
        if (g != null) {
            name.setText(g.optString("name"));
            emoji.setText(g.optString("emoji"));
            target.setText(String.valueOf(g.optLong("target") / 100));
        }
        Ui.Popup pp = new Ui.Popup(this);
        pp.setTitle(g == null ? "New goal" : "Edit goal").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long t = SmsParser.paise(target.getText().toString().trim());
                    String n = name.getText().toString().trim();
                    if (t <= 0 || n.isEmpty()) { Ui.toast(this, "Add a name and a target"); return; }
                    JSONArray a = list(this);
                    try {
                        JSONObject x = null;
                        if (g != null) for (int i = 0; i < a.length(); i++) if (g.optString("id").equals(a.optJSONObject(i).optString("id"))) x = a.optJSONObject(i);
                        if (x == null) { x = new JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("saved", 0L); a.put(x); }
                        String em = emoji.getText().toString().trim();
                        x.put("name", n).put("emoji", em.isEmpty() ? "🎯" : em).put("target", t).put("by", by[0]);
                    } catch (Exception ignored) { }
                    save(this, a);
                    build();
                })
                .setNegativeButton("Cancel", null);
        if (g != null) pp.setNeutralButton("Delete", (d, w) -> {
            JSONArray a = list(this), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (!g.optString("id").equals(a.optJSONObject(i).optString("id"))) out.put(a.optJSONObject(i));
            save(this, out);
            build();
        });
        pp.show();
    }
}
