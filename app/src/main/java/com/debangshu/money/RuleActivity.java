package com.debangshu.money;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/** 50/30/20: your month split into Needs · Wants · Saved, against the rule you pick. Tap a category to move it. */
public class RuleActivity extends Screen {

    static final int[] COL = {0xFF3B82F6, 0xFFF59E0B, 0xFF0E9F6E};
    static final String[] NAMES = {"Needs", "Wants", "Saved"};
    private int monthBack;      // 0 = this month, 1 = last month

    @Override String title() { return "50 / 30 / 20"; }

    /** {income, needs, wants} for this month (or last). */
    static long[] month(android.content.Context c, int back, LinkedHashMap<String, Long> cats) {
        long now = System.currentTimeMillis();
        long from = Fmt.addMonths(Fmt.startOfMonth(now), -back), to = Fmt.addMonths(from, 1);
        long[] b = MoneyFacts.buckets(c, from, to, cats);
        long inc = Ledger.income(Store.get(c).txns(), from, to);
        long set = Store.get(c).prefs().getLong("myIncome", 0);
        if (set > 0) inc = set;
        else if (back == 0 || inc <= 0) inc = Math.max(inc, MoneyFacts.income(c));
        return new long[]{inc, b[0], b[1]};
    }

    /** A thin bar in three colours: needs · wants · saved. */
    static View bar(android.content.Context c, int[] pct, int heightDp) {
        LinearLayout r = Ui.hbox(c);
        r.setClipToOutline(true);
        r.setBackground(Ui.round(Ui.col(c, R.color.surface2), Ui.dp(c, heightDp / 2f)));
        int sum = Math.max(1, pct[0] + pct[1] + pct[2]);
        for (int k = 0; k < 3; k++) {
            if (pct[k] <= 0) continue;
            View v = new View(c);
            v.setBackgroundColor(COL[k]);
            r.addView(v, new LinearLayout.LayoutParams(0, Ui.dp(c, heightDp), pct[k] / (float) sum));
        }
        return r;
    }

    @Override
    void build() {
        int ri = MoneyFacts.rule(this);
        Object[] rule = SaveMath.RULES[ri];
        LinkedHashMap<String, Long> cats = new LinkedHashMap<>();
        long[] m = month(this, monthBack, cats);
        long inc = m[0];

        // month switch
        LinearLayout seg = Ui.hbox(this);
        seg.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 18)));
        seg.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
        long now = System.currentTimeMillis();
        for (int i = 0; i < 2; i++) {
            boolean on = i == monthBack;
            TextView t = Ui.text(this, Fmt.MONTH[Fmt.cal(Fmt.addMonths(Fmt.startOfMonth(now), -i)).get(java.util.Calendar.MONTH)] + (i == 0 ? " (so far)" : ""),
                    13.5f, on ? Ui.col(this, R.color.text) : Ui.col(this, R.color.text2), on ? Ui.BOLD : Ui.SEMIBOLD);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            t.setBackground(on ? Ui.round(Ui.col(this, R.color.surface), Ui.dp(this, 15)) : null);
            final int b = i;
            t.setOnClickListener(v -> { monthBack = b; rebuild(); });
            seg.addView(t);
        }
        add(Explain.box(this, "rule"));
        LinearLayout sr = Ui.hbox(this);
        sr.addView(seg);
        add(sr);

        if (inc <= 0) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "What's your monthly income?", 16, Ui.BOLD));
            c.addView(Ui.sub(this, "Money hasn't seen your income yet. Tell it once to see your split."));
            TextView set = Ui.button(this, "Set my income", true);
            set.setOnClickListener(v -> askIncome());
            c.addView(set, Ui.margins(Ui.lp(-2, -2), this, 0, 12, 0, 0));
            add(c);
            return;
        }
        int[] pct = SaveMath.percents(inc, m[1], m[2]);

        // ---- the split
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Out of " + Fmt.rs(inc) + " income", 13, Ui.col(this, R.color.text2), Ui.BOLD));
        LinearLayout big = Ui.hbox(this);
        big.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        for (int k = 0; k < 3; k++) {
            LinearLayout col = Ui.vbox(this);
            col.addView(Ui.text(this, pct[k] + "%", 26, COL[k], Ui.EXTRABOLD));
            col.addView(Ui.text(this, NAMES[k], 13, Ui.col(this, R.color.text2), Ui.BOLD));
            big.addView(col, Ui.weight1());
        }
        c.addView(big);
        c.addView(bar(this, pct, 14));
        TextView rl = Ui.text(this, "Rule: " + rule[0], 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD);
        rl.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(rl);
        add(c);

        // ---- each bucket against the rule
        long[] amounts = {m[1], m[2], Math.max(0, inc - m[1] - m[2])};
        int[] target = {(Integer) rule[1], (Integer) rule[2], (Integer) rule[3]};
        boolean together = target[1] < 0;
        LinearLayout rows = Ui.card(this);
        rows.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        for (int k = 0; k < 3; k++) {
            if (together && k == 1) continue;
            long amt = together && k == 0 ? amounts[0] + amounts[1] : amounts[k];
            int p = together && k == 0 ? pct[0] + pct[1] : pct[k];
            long goal = inc * target[k] / 100;
            boolean good = k == 2 ? amt >= goal : amt <= goal;
            LinearLayout r = Ui.hbox(this);
            r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            View dot = new View(this);
            dot.setBackground(Ui.round(COL[k], Ui.dp(this, 5)));
            r.addView(dot, Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10)));
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 12), 0, 0, 0);
            col.addView(Ui.text(this, (together && k == 0 ? "Needs + wants" : NAMES[k]) + " · " + Fmt.rs(amt), 15, Ui.BOLD));
            long diff = Math.abs(amt - goal);
            String line = k == 2 ? (good ? "✓ " + p + "% saved — target " + target[k] + "%" : p + "% saved — " + Fmt.rs(diff) + " short of " + target[k] + "%")
                    : (good ? "✓ " + p + "% — within " + target[k] + "%" : p + "% — " + Fmt.rs(diff) + " over " + target[k] + "%");
            col.addView(Ui.text(this, line, 13, good ? Ui.col(this, R.color.success) : 0xFFD97706, Ui.SEMIBOLD));
            r.addView(col, Ui.weight1());
            rows.addView(r);
        }
        add(rows);

        // ---- tip
        String tip;
        if (pct[2] >= target[2]) tip = "You're saving like a pro. Put the saved part to work — a goal or a monthly SIP.";
        else if (!together && pct[1] > target[1]) tip = "Wants are the easiest to trim. Try the Leak finder, or a No-delivery challenge for a week.";
        else if (pct[0] > target[0]) tip = "Needs are high. Rent and EMIs are hard to change quickly — try the 60/20/20 or 70/20/10 rule for now.";
        else tip = "Close! Move a little more on salary day — Pay yourself first makes it automatic.";
        LinearLayout tc = Ui.card(this);
        tc.setOrientation(LinearLayout.HORIZONTAL);
        tc.setGravity(Gravity.CENTER_VERTICAL);
        tc.setBackground(Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 22)));
        tc.addView(Ui.iconBadge(this, "💡", 0xFFF59E0B, 36));
        TextView tt = Ui.text(this, tip, 13.5f, Ui.SEMIBOLD);
        tt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        tc.addView(tt, Ui.weight1());
        add(tc);

        // ---- categories (tap to move)
        add(Ui.label(this, "What counts where — tap to move"));
        LinearLayout cl = Ui.card(this);
        cl.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        java.util.Map<String, Integer> mv = MoneyFacts.moved(this);
        for (Map.Entry<String, Long> e : cats.entrySet()) {
            int k = SaveMath.bucket(e.getKey(), mv);
            LinearLayout r = Ui.hbox(this);
            r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            r.setBackground(Ui.rippleRound(this, 0x00000000, 12));
            r.addView(Ui.text(this, Cats.emoji(e.getKey()), 18, Ui.REGULAR));
            TextView n = Ui.oneLine(Ui.text(this, e.getKey(), 14, Ui.SEMIBOLD));
            n.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
            r.addView(n, Ui.weight1());
            r.addView(Ui.text(this, Fmt.rs(e.getValue()), 14, Ui.BOLD));
            TextView tag = Ui.chip(this, NAMES[k], false);
            tag.setTextColor(COL[k]);
            tag.setBackground(Ui.round((COL[k] & 0x00FFFFFF) | 0x22000000, Ui.dp(this, 14)));
            r.addView(tag, Ui.margins(Ui.lp(-2, -2), this, 10, 0, 0, 0));
            r.setOnClickListener(v -> new Ui.Popup(this).setTitle(e.getKey() + " is a…")
                    .setItems(new String[]{"Need — have to pay", "Want — nice to have", "Saving / investing"}, (d, w) -> { MoneyFacts.move(this, e.getKey(), w); rebuild(); })
                    .show());
            cl.addView(r);
        }
        if (cats.isEmpty()) cl.addView(Ui.sub(this, "No spending yet this month."));
        add(cl);

        // ---- pick a rule: tap to learn it first, with your own numbers
        int sug = suggested(this);
        add(Ui.label(this, "Pick your rule — tap one to learn it first"));
        LinearLayout pr = Ui.card(this);
        pr.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        for (int i = 0; i < SaveMath.RULES.length; i++) {
            Object[] r = SaveMath.RULES[i];
            final int idx = i;
            String sub = (i == sug ? "⭐ Suggested for you · " : "") + r[4];
            View row = Ui.navRow(this, i == ri ? "✅" : "📊", i == ri ? 0xFF0E9F6E : 0xFF94A3B8, (String) r[0] + (i == ri ? "  · yours" : ""), sub,
                    v -> explainRule(this, idx, this::rebuild));
            pr.addView(row);
        }
        add(pr);
        TextView inc2 = Ui.text(this, "Income: " + Fmt.rs(MoneyFacts.income(this)) + " a month — change", 13, Ui.col(this, R.color.accent), Ui.BOLD);
        inc2.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
        inc2.setOnClickListener(v -> askIncome());
        add(inc2);
    }

    /** {income, needs, wants} to compare with: last full month if it has data, else this month so far. */
    static long[] now(android.content.Context c) {
        long[] last = month(c, 1, null);
        if (last[0] > 0 && last[1] + last[2] > 0) return last;
        return month(c, 0, null);
    }

    /** The rule that fits how you spend today. */
    static int suggested(android.content.Context c) {
        long[] m = now(c);
        if (m[0] <= 0) return 0;
        int[] p = SaveMath.percents(m[0], m[1], m[2]);
        if (p[0] <= 50) return 0;
        if (p[0] <= 60) return 1;
        return 2;
    }

    /** Explain one rule simply, with "now vs with this rule" for your own money, then let you choose it. */
    static void explainRule(android.app.Activity a, int i, Runnable chosen) {
        Object[] rule = SaveMath.RULES[i];
        String[] story = Explain.RULE_STORY[i];
        long[] m = now(a);
        long inc = m[0];
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, 0, p, Ui.dp(a, 8));
        if (i == suggested(a) && inc > 0) {
            TextView sg = Ui.text(a, "⭐ Suggested for you — it fits how you spend today", 13, 0xFFD97706, Ui.BOLD);
            sg.setPadding(0, 0, 0, Ui.dp(a, 10));
            b.addView(sg);
        }
        TextView st = Ui.text(a, story[0], 15, Ui.col(a, R.color.text), Ui.REGULAR);
        st.setLineSpacing(Ui.dp(a, 3), 1f);
        b.addView(st);

        int[] tgt = {(Integer) rule[1], (Integer) rule[2], (Integer) rule[3]};
        boolean together = tgt[1] < 0;
        if (inc > 0) {
            // ---- with your money
            b.addView(head(a, "With your " + Fmt.rs(inc) + " a month"));
            LinearLayout boxes = Ui.hbox(a);
            boxes.setBaselineAligned(false);
            String[] lbl = together ? new String[]{"Spend freely", "", "Save first"} : NAMES;
            int shown = 0;
            for (int k = 0; k < 3; k++) {
                if (together && k == 1) continue;
                LinearLayout t = Ui.vbox(a);
                t.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
                t.setBackground(Ui.round((COL[k] & 0x00FFFFFF) | 0x22000000, Ui.dp(a, 14)));
                t.addView(Ui.text(a, lbl[k] + " · " + tgt[k] + "%", 12, COL[k], Ui.BOLD));
                t.addView(Ui.oneLine(Ui.text(a, Fmt.rs(inc * tgt[k] / 100), 15.5f, Ui.EXTRABOLD)));
                boxes.addView(t, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, shown++ == 0 ? 0 : 8, 0, 0, 0));
            }
            b.addView(boxes);

            // ---- now vs with this rule
            int[] nowPct = SaveMath.percents(inc, m[1], m[2]);
            b.addView(head(a, "You now  vs  with this rule"));
            b.addView(Ui.text(a, "Now", 12, Ui.col(a, R.color.text2), Ui.BOLD));
            b.addView(bar(a, nowPct, 12), Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 8));
            b.addView(Ui.text(a, "With " + rule[0], 12, Ui.col(a, R.color.text2), Ui.BOLD));
            int[] rulePct = together ? new int[]{80, 0, 20} : tgt;
            b.addView(bar(a, rulePct, 12), Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 4));

            // ---- what changes for you
            b.addView(head(a, "What changes for you"));
            long saveNow = Math.max(0, inc - m[1] - m[2]), saveRule = inc * tgt[2] / 100;
            StringBuilder ch = new StringBuilder();
            if (saveRule > saveNow) {
                long more = saveRule - saveNow;
                ch.append("🐷 You'd save ").append(Fmt.rs(more)).append(" more every month (").append(Fmt.rs(saveNow)).append(" → ").append(Fmt.rs(saveRule)).append(").\n\n");
                ch.append("📅 In 1 year: ").append(Fmt.rs(saveRule * 12)).append(" saved.\n");
                ch.append("🏦 Kept in an FD for 5 years (about 7%): ").append(Fmt.rs(Math.round(SaveMath.sip(saveRule / 100.0, 60, 0.07)) * 100)).append(".\n");
                ch.append("🌱 Invested for 10 years (about 12%): ").append(Fmt.rs(Math.round(SaveMath.sip(saveRule / 100.0, 120, 0.12)) * 100)).append(".");
            } else {
                ch.append("👏 You already save ").append(Fmt.rs(saveNow)).append(" a month — more than this rule asks (").append(Fmt.rs(saveRule)).append(").\n\n");
                ch.append("Following it, you could even enjoy ").append(Fmt.rs(saveNow - saveRule)).append(" more a month and still be on track. Or keep saving more — future you will love it.");
            }
            if (!together) {
                long wantRule = inc * tgt[1] / 100, needRule = inc * tgt[0] / 100;
                if (m[2] > wantRule) {
                    long less = m[2] - wantRule;
                    long orders = Math.max(1, less / 30000);
                    ch.append("\n\n🎉 Wants would go down by ").append(Fmt.rs(less)).append(" a month — about ").append(orders)
                            .append(orders == 1 ? " food order or outing" : " food orders or outings").append(" fewer.");
                }
                if (m[1] > needRule) {
                    ch.append("\n\n🏠 Your needs are ").append(Fmt.rs(m[1] - needRule)).append(" more than this rule allows. Needs like rent and EMIs are hard to change quickly")
                            .append(i < 2 ? " — a rule with more room for needs may fit you better." : ".");
                }
            }
            TextView chT = Ui.text(a, ch.toString(), 14.5f, Ui.col(a, R.color.text), Ui.REGULAR);
            chT.setLineSpacing(Ui.dp(a, 3), 1f);
            b.addView(chT);
        } else {
            TextView ni = Ui.sub(a, "Tell Money your monthly income to see this rule with your own numbers.");
            ni.setPadding(0, Ui.dp(a, 12), 0, 0);
            b.addView(ni);
        }

        b.addView(head(a, "👍 Good for"));
        b.addView(Ui.text(a, story[1], 14.5f, Ui.REGULAR));
        b.addView(head(a, "⚠️ The hard part"));
        b.addView(Ui.text(a, story[2], 14.5f, Ui.REGULAR));
        TextView d = Ui.text(a, "Growth figures use example rates — for learning, not a promise.", 11.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
        d.setPadding(0, Ui.dp(a, 12), 0, 0);
        b.addView(d);

        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        boolean mine = MoneyFacts.rule(a) == i;
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle((String) rule[0]);
        pop.setView(sv);
        if (mine) pop.setPositiveButton("This is my rule ✓", null);
        else pop.setPositiveButton("Use this rule", (x, y) -> {
            MoneyFacts.setRule(a, i);
            Ui.toast(a, rule[0] + " is your rule now");
            if (chosen != null) chosen.run();
        });
        pop.setNegativeButton("Close", null);
        if (inc <= 0) pop.setNeutralButton("Set income", (x, y) -> askIncome(a, () -> explainRule(a, i, chosen)));
        pop.sheet().show();
    }

    private static TextView head(android.content.Context c, String s) {
        TextView t = Ui.text(c, s, 15, Ui.BOLD);
        t.setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 8));
        return t;
    }

    private void askIncome() { askIncome(this, this::rebuild); }

    static void askIncome(android.app.Activity a, Runnable done) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        EditText e = new EditText(a);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setHint("₹ a month, after tax");
        e.setTypeface(Ui.face(a, Ui.BOLD));
        long cur = Store.get(a).prefs().getLong("myIncome", 0);
        if (cur > 0) e.setText(String.valueOf(cur / 100));
        b.addView(e);
        long det = MoneyFacts.detectedIncome(a);
        if (det > 0) b.addView(Ui.sub(a, "Money saw about " + Fmt.rs(det) + " a month come in. Leave empty to use that."));
        new Ui.Popup(a).setTitle("Your monthly income").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    Store.get(a).prefs().edit().putLong("myIncome", Math.max(0, v)).apply();
                    if (done != null) done.run();
                }).setNegativeButton("Cancel", null).show();
    }
}
