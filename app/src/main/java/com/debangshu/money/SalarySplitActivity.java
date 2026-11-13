package com.debangshu.money;

import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Salary-day split: the moment your salary lands, decide where it goes — goals first (pay yourself first),
 * then needs and wants. One tap sets this month's budget and fills your goals.
 */
public class SalarySplitActivity extends Screen {

    private long salary, savePct = -1, needPct = -1;

    @Override String title() { return "Split my salary"; }

    @Override
    void build() {
        if (salary <= 0) salary = getIntent().getLongExtra("amount", 0);
        if (salary <= 0) salary = MoneyFacts.income(this);
        if (salary <= 0) salary = 3000000;
        Object[] rule = SaveMath.RULES[MoneyFacts.rule(this)];
        if (savePct < 0) { savePct = (Integer) rule[3]; needPct = (Integer) rule[1] > 70 ? 50 : (Integer) rule[1]; }

        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "Salary", 13, Ui.col(this, R.color.text2), Ui.BOLD));
        TextView amt = Ui.text(this, Fmt.rs(salary), 28, Ui.EXTRABOLD);
        amt.setOnClickListener(v -> askAmount());
        hero.addView(amt);
        TextView ch = Ui.text(this, "Not this amount? Tap it to change", 12, Ui.col(this, R.color.hint), Ui.SEMIBOLD);
        hero.addView(ch);
        TextView barTxt = Ui.text(this, "", 13, Ui.SEMIBOLD);
        LinearLayout barBox = Ui.vbox(this);
        hero.addView(barBox, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 6));
        hero.addView(barTxt);
        add(hero);

        LinearLayout sl = Ui.card(this);
        LinearLayout goalsBox = Ui.vbox(this);
        Runnable[] upd = {null};
        Slider save = new Slider(this, "Save first (goals)", 0, 60, 5, savePct, v -> v + "%", v -> {
            savePct = v;
            if (savePct + needPct > 100) needPct = 100 - savePct;
            upd[0].run();
        });
        Slider need = new Slider(this, "Needs (rent, bills, groceries…)", 10, 90, 5, needPct, v -> v + "%", v -> {
            needPct = Math.min(v, 100 - savePct);
            upd[0].run();
        });
        sl.addView(save.view);
        sl.addView(need.view);
        TextView wantsTxt = Ui.text(this, "", 13.5f, Ui.SEMIBOLD);
        sl.addView(wantsTxt);
        add(sl);
        add(Ui.label(this, "Your saving goes to"));
        add(goalsBox);

        List<JSONObject> goals = Goals.active(this);
        long[][] shares = {new long[0]};
        upd[0] = () -> {
            need.set(needPct);
            long s = salary * savePct / 100, n = salary * needPct / 100, w = Math.max(0, salary - s - n);
            barBox.removeAllViews();
            barBox.addView(RuleActivity.bar(this, new int[]{(int) needPct, (int) (100 - savePct - needPct), (int) savePct}, 14));
            barTxt.setText("Needs " + Fmt.rs(n) + " · Wants " + Fmt.rs(w) + " · Save " + Fmt.rs(s));
            wantsTxt.setText("Wants (fun, food out, shopping): " + (100 - savePct - needPct) + "% = " + Fmt.rs(w));
            goalsBox.removeAllViews();
            LinearLayout gc = Ui.card(this);
            if (goals.isEmpty()) {
                gc.addView(Ui.sub(this, "No goals yet — " + Fmt.rs(s) + " can simply go to savings. Add a goal in Grow to give it a job."));
            } else {
                long[] needs = new long[goals.size()], lefts = new long[goals.size()];
                for (int i = 0; i < goals.size(); i++) {
                    GoalMath.Status st = Goals.status(this, goals.get(i));
                    needs[i] = st.needPerMonth;
                    lefts[i] = st.left;
                }
                shares[0] = GoalMath.split(s, needs, lefts);
                long rest = s;
                for (int i = 0; i < goals.size(); i++) {
                    JSONObject g = goals.get(i);
                    rest -= shares[0][i];
                    LinearLayout r = Ui.hbox(this);
                    r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
                    r.addView(Ui.iconBadge(this, g.optString("emoji", "🎯"), Goals.color(g), 34));
                    LinearLayout col = Ui.vbox(this);
                    col.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
                    col.addView(Ui.oneLine(Ui.text(this, g.optString("name"), 14.5f, Ui.BOLD)));
                    col.addView(Ui.oneLine(Ui.text(this, g.optString("account").isEmpty() ? "Added for you when you tap below"
                            : "Move it to " + Noted.fullLabel(this, g.optString("account")), 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD)));
                    r.addView(col, Ui.weight1());
                    r.addView(Ui.text(this, Fmt.rs(shares[0][i]), 14.5f, Ui.EXTRABOLD));
                    gc.addView(r);
                }
                if (rest > 0) gc.addView(Ui.sub(this, Fmt.rs(rest) + " more than your goals need — keep it as savings."));
            }
            goalsBox.addView(gc);
        };

        TextView go = Ui.button(this, "Do it — set budget and fill goals", true);
        go.setOnClickListener(v -> apply(goals, shares[0]));
        add(go);
        TextView note = Ui.sub(this, "Your budget for the next month becomes Needs + Wants. Goals you fill yourself get their share added; "
                + "for goals in a bank account, move the money in your bank app — Money sees it from the SMS.");
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), 0, 0);
        add(note);
        upd[0].run();
    }

    private void apply(List<JSONObject> goals, long[] shares) {
        long budget = salary * (100 - savePct) / 100;
        Store s = Store.get(this);
        Plan running = s.plan();
        long now = System.currentTimeMillis();
        Plan p = new Plan();
        boolean keep = running != null && running.started(now) && !running.finished(now);
        p.id = keep ? running.id : java.util.UUID.randomUUID().toString();
        p.amount = budget;
        p.start = keep ? running.start : Fmt.startOfDay(now);
        p.length = keep ? running.length : Plan.MONTH;
        p.end = keep ? running.end : Plan.endFor(p.start, Plan.MONTH, 0);
        p.repeat = keep ? running.repeat : true;
        p.skip = keep ? new ArrayList<>(running.skip) : new ArrayList<>();
        s.setPlan(p);
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < goals.size() && i < shares.length; i++) {
            JSONObject g = goals.get(i);
            if (shares[i] <= 0) continue;
            try { g.put("plan", shares[i]); } catch (Exception ignored) { }
            if (g.optString("account").isEmpty()) {
                try {
                    g.put("saved", g.optLong("saved") + shares[i]);
                    JSONArray log = g.optJSONArray("log");
                    if (log == null) { log = new JSONArray(); g.put("log", log); }
                    log.put(new JSONArray().put(now).put(shares[i]).put("From salary"));
                } catch (Exception ignored) { }
            } else moves.add(Fmt.rs(shares[i]) + " → " + Noted.fullLabel(this, g.optString("account")) + " (" + g.optString("name") + ")");
            Goals.put(this, g);
        }
        Goals.check(this, false);
        Goals.award(this, "payday");
        StringBuilder m = new StringBuilder("Budget set to " + Fmt.rs(budget) + (keep ? " for this budget period." : " for the next month."));
        if (!moves.isEmpty()) {
            m.append("\n\nNow move these in your bank or UPI app:");
            for (String x : moves) m.append("\n• ").append(x);
        }
        new Ui.Popup(this).setTitle("Done ✓").setMessage(m.toString()).setPositiveButton("OK", (d, w) -> finish()).show();
    }

    private void askAmount() {
        LinearLayout b = form();
        android.widget.EditText e = field("₹ salary", true);
        e.setText(String.valueOf(salary / 100));
        b.addView(e);
        new Ui.Popup(this).setTitle("Salary amount").setView(b)
                .setPositiveButton("OK", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v > 0) { salary = v; rebuild(); }
                }).setNegativeButton("Cancel", null).show();
    }
}
