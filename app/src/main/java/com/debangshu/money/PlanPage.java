package com.debangshu.money;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Plan: the things you organise, as a calm grid of tiles. Each tile shows one live line and opens its own screen.
 * New features from the roadmap get a tile here (Goals, People, Challenges, Net worth, Tax, Cards) —
 * so Home never fills up.
 */
final class PlanPage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;

    PlanPage(MainActivity a) { this.a = a; }

    View view() {
        if (scroll == null) {
            scroll = new ScrollView(a);
            box = Ui.vbox(a);
            box.setPadding(0, 0, 0, Ui.dp(a, 24));
            scroll.addView(box);
        }
        return scroll;
    }

    void refresh() {
        view();
        box.removeAllViews();
        box.addView(a.topBar("Plan", null));
        LinearLayout body = Ui.vbox(a);
        body.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
        box.addView(body);
        TextView intro = Ui.sub(a, "Set things up once — Money keeps them up to date by itself.");
        intro.setPadding(Ui.dp(a, 6), 0, 0, Ui.dp(a, 4));
        body.addView(intro);

        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();

        // Budget
        Plan p = s.plan();
        String bLine;
        float bFrac = -1;
        if (p == null) bLine = "Not set — tap to start one";
        else {
            long spent = p.spent(all);
            bFrac = spent / (float) Math.max(1, p.amount);
            bLine = spent <= p.amount ? Fmt.rs(p.amount - spent) + " left · " + p.daysLeft(now) + " days" : Fmt.rs(spent - p.amount) + " over";
        }
        View budget = tile("🎯", 0xFF10B981, "Budget", bLine, bFrac, v -> a.startActivity(new Intent(a, BudgetActivity.class)));

        // Bills & subscriptions
        List<Ledger.Recurring> rec = Ledger.recurring(all, now);
        int dueSoon = 0;
        for (Ledger.Recurring r : rec) if (r.next >= Fmt.startOfDay(now) && r.next < now + 7L * 86400000) dueSoon++;
        for (Store.Bill b : s.bills()) if (!b.paid && b.dueDate >= now - 86400000 && b.dueDate < now + 7L * 86400000 && !Alerts.billPaid(s, b)) dueSoon++;
        long monthly = 0;
        for (Ledger.Recurring r : rec) monthly += r.amount * 30 / Math.max(1, r.period);
        String billLine = rec.isEmpty() && dueSoon == 0 ? "Found by themselves as they repeat"
                : (dueSoon > 0 ? dueSoon + " due this week · " : "") + "≈ " + Fmt.rs(monthly) + "/month";
        View bills = tile("📅", 0xFF3B82F6, "Bills & subscriptions", billLine, -1, v -> a.startActivity(new Intent(a, BillsActivity.class)));

        // Accounts
        long total = 0;
        int n = 0;
        for (Account ac : s.accounts()) if (!ac.card && !ac.hidden && ac.balance >= 0) { total += ac.balance; n++; }
        View accounts = tile("🏦", 0xFF0B6F86, "Accounts", n == 0 ? "Appear after a bank SMS with a balance" : Fmt.rs(total) + " in " + n + (n == 1 ? " account" : " accounts"),
                -1, v -> a.startActivity(new Intent(a, AccountsActivity.class)));

        // Cash
        View cash = tile("💵", 0xFF65A30D, "Cash wallet", Cash.on(a) ? Fmt.rs(Cash.balance(a)) + " in your wallet" : "Track cash without typing every spend",
                -1, v -> Cash.sheet(a));

        // Goals · Challenges · Net worth
        float gp = Goals.progress(a);
        View goals = tile("🏆", 0xFFF59E0B, "Goals", Goals.summary(a), gp >= 0 ? Math.min(1f, gp) : -1, v -> a.startActivity(new Intent(a, Goals.class)), false);
        View chal = tile("🔥", 0xFFEF4444, "Challenges", Challenges.summary(a), -1, v -> a.startActivity(new Intent(a, Challenges.class)));
        View nw = tile("💎", 0xFF0EA5E9, "Net worth", NetWorthActivity.summary(a), -1, v -> a.startActivity(new Intent(a, NetWorthActivity.class)));

        // People
        long[] pp = People.totals(a);
        String pl = pp[0] == 0 && pp[1] == 0 ? "Split bills, lend & borrow — settled automatically"
                : (pp[0] > 0 ? "You'll get " + Fmt.rs(pp[0]) : "") + (pp[0] > 0 && pp[1] > 0 ? " · " : "") + (pp[1] > 0 ? "you owe " + Fmt.rs(pp[1]) : "");
        View people = tile("👥", 0xFFA855F7, "People", pl, -1, v -> a.startActivity(new Intent(a, People.class)));

        // Tools
        View tax = tile("🧾", 0xFF6366F1, "Tax helper", TaxActivity.summary(a), -1, v -> a.startActivity(new Intent(a, TaxActivity.class)));
        float cu = CardsActivity.usedFrac(a);
        View cards = tile("💳", 0xFF6D28D9, "Credit cards", CardsActivity.summary(a), cu, v -> a.startActivity(new Intent(a, CardsActivity.class)));

        body.addView(label("Everyday"));
        body.addView(row(budget, bills));
        body.addView(row(accounts, cash));
        body.addView(label("Grow"));
        body.addView(row(goals, chal));
        body.addView(label("Together"));
        body.addView(row(people, nw));
        body.addView(label("Tools"));
        body.addView(row(tax, cards));
    }

    private TextView label(String s) {
        TextView t = Ui.text(a, s.toUpperCase(java.util.Locale.ROOT), 11.5f, Ui.col(a, R.color.text2), Ui.BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), 0, Ui.dp(a, 10));
        return t;
    }

    private View row(View l, View r) {
        LinearLayout row = Ui.hbox(a);
        row.setBaselineAligned(false);
        row.addView(l, Ui.margins(new LinearLayout.LayoutParams(0, -1, 1), a, 0, 0, 6, 12));
        if (r != null) row.addView(r, Ui.margins(new LinearLayout.LayoutParams(0, -1, 1), a, 6, 0, 0, 12));
        else row.addView(new View(a), Ui.margins(new LinearLayout.LayoutParams(0, -1, 1), a, 6, 0, 0, 12));
        return row;
    }

    private View tile(String emoji, int color, String title, String line, float frac, View.OnClickListener l) {
        return tile(emoji, color, title, line, frac, l, true);
    }

    private View tile(String emoji, int color, String title, String line, float frac, View.OnClickListener l, boolean budgetBar) {
        LinearLayout t = Ui.card(a);
        t.setMinimumHeight(Ui.dp(a, 132));
        t.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        t.addView(Ui.badge(a, emoji, color, 44));
        TextView tt = Ui.text(a, title, 16, Ui.BOLD);
        tt.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 2));
        t.addView(tt);
        TextView ln = Ui.text(a, line, 12.5f, Ui.col(a, R.color.text2), Ui.REGULAR);
        ln.setMaxLines(2);
        t.addView(ln);
        if (frac >= 0) {
            Charts.Progress pr = new Charts.Progress(a);
            if (budgetBar) pr.budget(frac, 6);
            else pr.set(frac, color);
            t.addView(pr, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        }
        t.setOnClickListener(l);
        return t;
    }
}
