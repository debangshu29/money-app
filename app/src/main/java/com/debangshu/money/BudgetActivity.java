package com.debangshu.money;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Your budget: enter an amount, pick the start date, tap Start — then see how much is left,
 * where it's going, every payment since the start, and how past budgets went.
 */
public class BudgetActivity extends Activity {

    private LinearLayout box;
    private final Runnable onChange = this::refreshSoon;
    private boolean pending, editing;

    // the form
    private long fStart, fCustomEnd;
    private int fLength = Plan.MONTH;
    private boolean fRepeat = true;
    private final List<String> fSkip = new ArrayList<>();
    private String fAmount = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        LinearLayout bar = Ui.hbox(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        ImageView back = Ui.icon(this, R.drawable.ic_back, Ui.col(this, R.color.text), 22);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back);
        bar.addView(Ui.text(this, "Budget", 24, Ui.EXTRABOLD));
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        Store.get(this).listen(onChange);
        resetForm(null);
    }

    @Override
    protected void onDestroy() {
        Store.get(this).unlisten(onChange);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        Alerts.rollPlan(this);
        build();
    }

    @Override
    public void onBackPressed() {
        if (editing && Store.get(this).plan() != null) { editing = false; build(); return; }
        super.onBackPressed();
    }

    private void refreshSoon() {
        if (pending || editing) return;
        pending = true;
        box.postDelayed(() -> { pending = false; build(); }, 250);
    }

    private void build() {
        box.removeAllViews();
        Plan p = Store.get(this).plan();
        if (p == null || editing) form(p);
        else dashboard(p);
        history();
    }

    // ------------------------------------------------------------------ the running budget

    private void dashboard(Plan p) {
        Store s = Store.get(this);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        long spent = p.spent(all);
        long left = p.amount - spent;
        float frac = p.amount <= 0 ? 0 : spent / (float) p.amount;

        LinearLayout hero = Ui.card(this);
        int col = Ui.budgetColor(frac);
        hero.addView(Ui.text(this, "Budget · " + p.range(), 14, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        LinearLayout big = Ui.hbox(this);
        big.setGravity(android.view.Gravity.BOTTOM);
        big.addView(Ui.text(this, Fmt.rs(Math.abs(left)), 36, col, Ui.EXTRABOLD));
        TextView lw = Ui.text(this, left >= 0 ? "  left" : "  over budget", 16, col, Ui.BOLD);
        lw.setPadding(0, 0, 0, Ui.dp(this, 7));
        big.addView(lw);
        hero.addView(big);
        Charts.Progress pr = new Charts.Progress(this);
        pr.budget(frac, 16);
        hero.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 6));
        LinearLayout nums = Ui.hbox(this);
        nums.addView(Ui.text(this, Fmt.rs(spent) + " spent", 14, Ui.SEMIBOLD), Ui.weight1());
        nums.addView(Ui.text(this, "of " + Fmt.rs(p.amount), 14, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(nums);
        TextView st = Ui.text(this, p.status(all, now), 14, col, Ui.BOLD);
        st.setPadding(0, Ui.dp(this, 8), 0, 0);
        hero.addView(st);
        if (p.started(now) && !p.finished(now) && spent > 0) {
            long proj = p.projected(all, now);
            hero.addView(Ui.sub(this, "At this pace: about " + Fmt.rs(proj) + " by " + Fmt.date(Fmt.addDays(p.end, -1))
                    + (proj > p.amount ? " (" + Fmt.rs(proj - p.amount) + " over)" : "")));
        }
        box.addView(hero, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 10));

        LinearLayout actions = Ui.hbox(this);
        TextView edit = Ui.button(this, "Edit", false);
        edit.setOnClickListener(v -> { resetForm(p); editing = true; build(); });
        TextView end = Ui.button(this, "End budget", false);
        end.setOnClickListener(v -> new Ui.Popup(this).setTitle("End this budget now?")
                .setMessage("It moves to Past budgets with what was spent until today. You can start a new one any time.")
                .setPositiveButton("End it", (d, w) -> { Store.get(this).closePlan(System.currentTimeMillis()); resetForm(p); build(); })
                .setNegativeButton("Cancel", null).show());
        actions.addView(edit, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        actions.addView(end);
        box.addView(actions, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        if (left < 0) {
            String why = p.overReasons(all);
            if (!why.isEmpty()) {
                LinearLayout w = Ui.card(this);
                w.setBackground(Ui.round(Ui.col(this, R.color.danger_soft), Ui.dp(this, 22)));
                w.addView(Ui.text(this, "Where the extra went", 15, Ui.BOLD));
                TextView t = Ui.sub(this, why + ". Tap a category below to see its payments.");
                t.setPadding(0, Ui.dp(this, 4), 0, 0);
                w.addView(t);
                box.addView(w, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
            }
        }

        // where it's going
        LinkedHashMap<String, Long> cats = p.byCategory(all);
        LinearLayout cc = Ui.card(this);
        cc.addView(Ui.sectionTitle(this, "Where it's going"));
        if (cats.isEmpty()) cc.addView(Ui.sub(this, p.started(now) ? "No spending since " + Fmt.date(p.start) + " yet." : "Nothing yet — it starts " + Fmt.rel(p.start) + "."));
        for (Map.Entry<String, Long> e : cats.entrySet()) {
            int pct = (int) Math.round(e.getValue() * 100.0 / Math.max(1, p.amount));
            int n = 0;
            for (Txn t : all) if (p.counts(t) && e.getKey().equals(t.category.isEmpty() ? Cats.OTHER : t.category)) n++;
            View row = Rows.amountRow(this, Cats.emoji(e.getKey()), Cats.color(e.getKey()), e.getKey(),
                    pct + "% of budget · " + n + (n == 1 ? " payment" : " payments"), e.getValue(), e.getValue() / (float) Math.max(1, p.amount));
            String cat = e.getKey();
            row.setOnClickListener(v -> categoryPayments(p, cat));
            cc.addView(row);
        }
        long skipped = p.skipped(all);
        if (!p.skip.isEmpty()) {
            TextView sk = Ui.sub(this, "Not counted: " + join(p.skip) + (skipped > 0 ? " (" + Fmt.rs(skipped) + " spent)" : ""));
            sk.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
            cc.addView(sk);
        }
        box.addView(cc, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        // day by day
        if (p.started(now)) {
            LinearLayout dd = Ui.card(this);
            dd.addView(Ui.sectionTitle(this, "Day by day"));
            long[] days = p.byDay(all);
            TextView picked = Ui.sub(this, "Tap a bar to see that day · the dotted line is your daily share");
            Charts.Bars bars = new Charts.Bars(this);
            bars.set(days, p.finished(now) ? -1 : p.dayIndex(now), p.amount / p.days(),
                    (i, v) -> picked.setText(Fmt.date(Fmt.addDays(p.start, i)) + ": " + Fmt.rs(v)));
            dd.addView(bars);
            dd.addView(picked);
            box.addView(dd, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        }

        // every payment since the start
        List<Txn> pays = p.payments(all);
        LinearLayout pc = Ui.card(this);
        LinearLayout ph = Ui.hbox(this);
        ph.addView(Ui.sectionTitle(this, "Payments in this budget"), Ui.weight1());
        TextView cnt = Ui.text(this, String.valueOf(pays.size()), 14, Ui.col(this, R.color.text2), Ui.BOLD);
        cnt.setPadding(0, 0, 0, Ui.dp(this, 8));
        ph.addView(cnt);
        pc.addView(ph);
        if (pays.isEmpty()) pc.addView(Ui.sub(this, "Payments from " + Fmt.date(p.start) + " show up here by themselves."));
        for (int i = 0; i < Math.min(15, pays.size()); i++) pc.addView(Rows.txn(this, pays.get(i), true));
        if (pays.size() > 15) {
            TextView more = Ui.text(this, "See all " + pays.size() + " payments  →", 14, Ui.col(this, R.color.accent), Ui.BOLD);
            more.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 2));
            more.setOnClickListener(v -> openInActivity(p, null));
            pc.addView(more);
        }
        box.addView(pc, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
    }

    private void categoryPayments(Plan p, String cat) {
        List<Txn> all = Store.get(this).txns();
        LinearLayout b = Ui.vbox(this);
        b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        long total = 0;
        for (Txn t : p.payments(all)) {
            if (!cat.equals(t.category.isEmpty() ? Cats.OTHER : t.category)) continue;
            b.addView(Rows.txn(this, t, true));
            total += t.amount;
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(b);
        new Ui.Popup(this).sheet().setTitle(Cats.emoji(cat) + "  " + cat + " · " + Fmt.rs(total)).setView(Ui.capHeight(sv, 0.6f))
                .setPositiveButton("Close", null).show();
    }

    private void openInActivity(Plan p, String cat) {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("filterFrom", p.start).putExtra("filterTo", p.end).putExtra("filterLabel", "Budget " + p.range());
        if (cat != null) i.putExtra("filterCat", cat);
        startActivity(i);
    }

    // ------------------------------------------------------------------ start / edit form

    private void resetForm(Plan p) {
        if (p != null) {
            fAmount = String.valueOf(p.amount / 100);
            fStart = p.start;
            fLength = p.length;
            fCustomEnd = Fmt.addDays(p.end, -1);
            fRepeat = p.repeat;
            fSkip.clear();
            fSkip.addAll(p.skip);
        } else {
            fAmount = "";
            fStart = Fmt.startOfDay(System.currentTimeMillis());
            fLength = Plan.MONTH;
            fCustomEnd = Fmt.addDays(Fmt.addMonths(fStart, 1), -1);
            fRepeat = true;
            fSkip.clear();
        }
    }

    private void form(Plan running) {
        boolean edit = running != null;
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, edit ? "Change your budget" : "Start a budget", 20, Ui.EXTRABOLD));
        TextView intro = Ui.sub(this, "Enter how much you want to spend and from which day. Every payment from that day counts against it — automatically.");
        intro.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 14));
        c.addView(intro);

        // amount
        c.addView(label("Amount"));
        LinearLayout ar = Ui.hbox(this);
        TextView rupee = Ui.text(this, "₹", 26, Ui.EXTRABOLD);
        ar.addView(rupee);
        EditText amt = new EditText(this);
        amt.setInputType(InputType.TYPE_CLASS_NUMBER);
        amt.setHint("30000");
        amt.setText(fAmount);
        amt.setTextSize(26);
        amt.setTypeface(Ui.face(this, Ui.EXTRABOLD));
        amt.setBackground(null);
        amt.setPadding(Ui.dp(this, 6), 0, 0, 0);
        amt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { fAmount = s.toString(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        ar.addView(amt, Ui.weight1());
        c.addView(ar);
        c.addView(Ui.divider(this));
        long usual = usualSpend();
        if (usual > 0) {
            TextView u = Ui.text(this, "You usually spend about " + Fmt.rs(usual) + " a month" + (fSkip.isEmpty() ? "" : " (without the left-out categories)")
                    + " — tap to use it", 12.5f, Ui.col(this, R.color.accent), Ui.SEMIBOLD);
            u.setPadding(0, Ui.dp(this, 6), 0, 0);
            long rounded = ((usual / 100 + 999) / 1000) * 1000;
            u.setOnClickListener(v -> { fAmount = String.valueOf(rounded); amt.setText(fAmount); });
            c.addView(u);
        }

        // start date
        c.addView(label("Starts on"));
        LinearLayout sr = Ui.hbox(this);
        long today = Fmt.startOfDay(System.currentTimeMillis());
        long first = Fmt.startOfMonth(today);
        TextView tToday = Ui.chip(this, "Today", fStart == today);
        TextView tFirst = Ui.chip(this, "1st of this month", fStart == first && first != today);
        TextView tPick = Ui.chip(this, fStart != today && fStart != first ? "📅 " + Fmt.date(fStart) : "📅 Pick a date", fStart != today && fStart != first);
        tToday.setOnClickListener(v -> { fStart = today; build(); });
        tFirst.setOnClickListener(v -> { fStart = first; build(); });
        tPick.setOnClickListener(v -> pickDate(fStart, d -> { fStart = d; build(); }));
        sr.addView(tToday, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        if (first != today) sr.addView(tFirst, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        sr.addView(tPick);
        c.addView(wrapH(sr));

        // length
        c.addView(label("Runs for"));
        LinearLayout lr = Ui.hbox(this);
        for (int i = 0; i < Plan.LENGTH_NAMES.length; i++) {
            String name = i == Plan.CUSTOM && fLength == Plan.CUSTOM ? "📅 until " + Fmt.date(fCustomEnd) : Plan.LENGTH_NAMES[i];
            TextView ch = Ui.chip(this, name, fLength == i);
            final int idx = i;
            ch.setOnClickListener(v -> {
                if (idx == Plan.CUSTOM) {
                    pickDate(Math.max(fCustomEnd, fStart), d -> {
                        if (d < fStart) { Ui.toast(this, "The end date must be after the start"); return; }
                        fCustomEnd = d; fLength = Plan.CUSTOM; build();
                    });
                } else { fLength = idx; build(); }
            });
            lr.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        }
        c.addView(wrapH(lr));
        long endPreview = Plan.endFor(fStart, fLength, fCustomEnd);
        TextView range = Ui.sub(this, "From " + Fmt.date(fStart) + " to " + Fmt.date(Fmt.addDays(endPreview, -1))
                + " (" + Math.round((endPreview - fStart) / 86400000.0) + " days)");
        range.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(range);

        // left out
        c.addView(label("Don't count"));
        TextView skip = Ui.chip(this, fSkip.isEmpty() ? "Everything counts  ▾" : join(fSkip) + "  ▾", false);
        skip.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        skip.setOnClickListener(v -> pickSkip());
        LinearLayout kr = Ui.hbox(this);
        kr.addView(skip);
        c.addView(kr);
        TextView kh = Ui.text(this, "e.g. leave out Rent, EMI and Investments to budget only day-to-day spending", 12, Ui.col(this, R.color.hint), Ui.REGULAR);
        kh.setPadding(0, Ui.dp(this, 4), 0, 0);
        c.addView(kh);

        // repeat
        Switch rep = new Switch(this);
        rep.setText("Start the next one by itself when this ends");
        rep.setChecked(fRepeat);
        rep.setTypeface(Ui.face(this, Ui.SEMIBOLD));
        rep.setTextColor(Ui.col(this, R.color.text));
        rep.setTextSize(14);
        rep.setOnCheckedChangeListener((b, on) -> fRepeat = on);
        c.addView(rep, Ui.margins(Ui.matchWrap(), this, 0, 16, 0, 0));

        TextView go = Ui.button(this, edit ? "Save changes" : "Start budget", true);
        go.setOnClickListener(v -> start(running));
        c.addView(go, Ui.margins(Ui.matchWrap(), this, 0, 18, 0, 0));
        if (edit) {
            TextView cancel = Ui.button(this, "Cancel", false);
            cancel.setOnClickListener(v -> { editing = false; build(); });
            c.addView(cancel, Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        }
        box.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));
    }

    private void start(Plan running) {
        long amount = SmsParser.paise(fAmount.trim());
        if (amount <= 0) { Ui.toast(this, "Enter the amount first"); return; }
        long end = Plan.endFor(fStart, fLength, fCustomEnd);
        if (end <= System.currentTimeMillis()) { Ui.toast(this, "That budget would already be over — pick a later start or end date"); return; }
        Plan p = new Plan();
        p.id = running != null ? running.id : java.util.UUID.randomUUID().toString();
        p.amount = amount;
        p.start = fStart;
        p.end = end;
        p.length = fLength;
        p.repeat = fRepeat;
        p.skip = new ArrayList<>(fSkip);
        Store.get(this).setPlan(p);
        editing = false;
        Ui.toast(this, running != null ? "Budget updated" : "Budget started — payments from " + Fmt.date(fStart) + " count");
        build();
    }

    private long usualSpend() {
        List<Txn> all = Store.get(this).txns();
        long ms = Fmt.startOfMonth(System.currentTimeMillis());
        long total = 0;
        int months = 0;
        for (int k = 1; k <= 3; k++) {
            long s = Fmt.addMonths(ms, -k), e = Fmt.addMonths(ms, -k + 1);
            long m = 0;
            boolean any = false;
            for (Txn t : all) {
                if (t.time < s || t.time >= e) continue;
                any = true;
                if (t.isSpend() && !fSkip.contains(t.category)) m += t.amount;
            }
            if (any) { total += m; months++; }
        }
        return months == 0 ? 0 : total / months;
    }

    private void pickSkip() {
        String[] names = Cats.spendNames();
        boolean[] on = new boolean[names.length];
        CharSequence[] items = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) { on[i] = fSkip.contains(names[i]); items[i] = Cats.emoji(names[i]) + "  " + names[i]; }
        new Ui.Popup(this).setTitle("Don't count these").setMultiChoiceItems(items, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Done", (d, w) -> {
                    fSkip.clear();
                    for (int i = 0; i < names.length; i++) if (on[i]) fSkip.add(names[i]);
                    build();
                })
                .setNeutralButton("Rent, EMI, Investments", (d, w) -> {
                    fSkip.clear();
                    fSkip.add(Cats.HOME); fSkip.add(Cats.EMI); fSkip.add(Cats.INVEST); fSkip.add(Cats.INSURE);
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }

    interface OnDate { void picked(long day); }

    private void pickDate(long initial, OnDate cb) {
        Calendar k = Fmt.cal(initial);
        DatePickerDialog d = new DatePickerDialog(this, (v, y, m, day) -> {
            Calendar c = Calendar.getInstance();
            c.set(y, m, day, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            cb.picked(c.getTimeInMillis());
        }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    // ------------------------------------------------------------------ past budgets

    private void history() {
        List<Plan> past = Store.get(this).pastPlans();
        if (past.isEmpty()) return;
        List<Txn> all = Store.get(this).txns();
        LinearLayout c = Ui.card(this);
        c.addView(Ui.sectionTitle(this, "Past budgets"));
        for (Plan p : past) {
            long spent = p.spent(all);
            long diff = p.amount - spent;
            String sub = Fmt.rs(spent) + " of " + Fmt.rs(p.amount) + " · " + (diff >= 0 ? Fmt.rs(diff) + " under ✓" : Fmt.rs(-diff) + " over");
            View row = Rows.amountRow(this, diff >= 0 ? "✅" : "🔺", diff >= 0 ? Ui.col(this, R.color.success) : Ui.col(this, R.color.danger),
                    p.range(), sub, spent, spent / (float) Math.max(1, p.amount));
            row.setOnClickListener(v -> pastDetail(p));
            c.addView(row);
        }
        box.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
    }

    private void pastDetail(Plan p) {
        List<Txn> all = Store.get(this).txns();
        LinearLayout b = Ui.vbox(this);
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        long spent = p.spent(all);
        TextView top = Ui.text(this, Fmt.rs(spent) + " spent of " + Fmt.rs(p.amount), 16, Ui.BOLD);
        b.addView(top);
        String why = spent > p.amount ? p.overReasons(all) : "";
        if (!why.isEmpty()) { TextView w = Ui.sub(this, why); w.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6)); b.addView(w); }
        for (Map.Entry<String, Long> e : p.byCategory(all).entrySet()) {
            b.addView(Rows.amountRow(this, Cats.emoji(e.getKey()), Cats.color(e.getKey()), e.getKey(),
                    Math.round(e.getValue() * 100.0 / Math.max(1, p.amount)) + "% of budget", e.getValue(), e.getValue() / (float) Math.max(1, p.amount)));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(b);
        new Ui.Popup(this).sheet().setTitle("Budget " + p.range()).setView(Ui.capHeight(sv, 0.6f))
                .setPositiveButton("See payments", (d, w) -> openInActivity(p, null))
                .setNeutralButton("Remove", (d, w) -> Store.get(this).deletePastPlan(p))
                .setNegativeButton("Close", null).show();
    }

    // ------------------------------------------------------------------ small helpers

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 13, Ui.col(this, R.color.text2), Ui.BOLD);
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        return t;
    }

    private View wrapH(View v) {
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }

    static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) b.append(i == 0 ? "" : ", ").append(l.get(i));
        return b.toString();
    }
}
