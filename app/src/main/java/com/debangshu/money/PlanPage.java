package com.debangshu.money;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.List;

/**
 * Plan: your budget on top, a short "Needs you" list (only when something does), then every tool as a round icon,
 * four to a row, in four groups — Money · Life · Grow · Tools (like Google Pay's home).
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

        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();

        // ---- your budget, front and centre
        Plan p = s.plan();
        LinearLayout bc = Ui.card(a);
        bc.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        LinearLayout bh = Ui.hbox(a);
        bh.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout bcol = Ui.vbox(a);
        bcol.addView(Ui.text(a, "Budget", 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        if (p == null) {
            bcol.addView(Ui.text(a, "Set a monthly budget", 18, Ui.BOLD));
            bcol.addView(Ui.text(a, "See what's safe to spend each day", 12.5f, Ui.col(a, R.color.text2), Ui.REGULAR));
            bh.addView(bcol, Ui.weight1());
            bh.addView(Ui.text(a, "›", 26, Ui.col(a, R.color.hint), Ui.REGULAR));
            bc.addView(bh);
        } else {
            long spent = p.spent(all);
            float frac = spent / (float) Math.max(1, p.amount);
            bcol.addView(Ui.text(a, spent <= p.amount ? Fmt.rs(p.amount - spent) + " left" : Fmt.rs(spent - p.amount) + " over", 22, Ui.EXTRABOLD));
            bcol.addView(Ui.text(a, Fmt.rs(spent) + " of " + Fmt.rs(p.amount) + " · " + p.daysLeft(now) + " days to go", 12.5f, Ui.col(a, R.color.text2), Ui.REGULAR));
            bh.addView(bcol, Ui.weight1());
            bh.addView(Ui.text(a, "›", 26, Ui.col(a, R.color.hint), Ui.REGULAR));
            bc.addView(bh);
            Charts.Progress pr = new Charts.Progress(a);
            pr.budget(frac, 8);
            bc.addView(pr, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        }
        bc.setOnClickListener(v -> a.startActivity(new Intent(a, BudgetActivity.class)));
        body.addView(bc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 4));

        // ---- only what needs you now
        List<View> need = new java.util.ArrayList<>();
        int late = Refunds.overdue(a);
        if (late > 0) need.add(Ui.navRow(a, "↩️", 0xFFF59E0B, late + (late == 1 ? " refund is late" : " refunds are late"), "See what to do",
                v -> a.startActivity(new Intent(a, Refunds.class))));
        List<org.json.JSONObject> soon = Reminders.upcoming(a, 3);
        if (!soon.isEmpty()) need.add(Ui.navRow(a, Reminders.emoji(soon.get(0).optString("kind")), Reminders.color(soon.get(0).optString("kind")),
                soon.get(0).optString("name") + " " + Reminders.when(soon.get(0).optLong("until")), soon.size() > 1 ? "+" + (soon.size() - 1) + " more" : "Renewals & reminders",
                v -> a.startActivity(new Intent(a, Reminders.class))));
        int dueSoon = 0;
        for (Store.Bill b : s.bills()) if (!b.paid && b.dueDate >= now - 86400000 && b.dueDate < now + 5L * 86400000 && !Alerts.billPaid(s, b)) dueSoon++;
        if (dueSoon > 0) need.add(Ui.navRow(a, "📅", 0xFF3B82F6, dueSoon + (dueSoon == 1 ? " card bill due soon" : " card bills due soon"), "Bills & subscriptions",
                v -> a.startActivity(new Intent(a, BillsActivity.class))));
        if (!need.isEmpty()) {
            body.addView(Ui.label(a, "Needs you"));
            LinearLayout nc = Ui.card(a);
            nc.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
            for (View v : need) nc.addView(v);
            body.addView(nc);
        }

        // ---- everything else: round icons, 4 to a row (like Google Pay)
        body.addView(Ui.label(a, "Money"));
        body.addView(gridCard(
                Ui.action(a, "🏦", 0xFF0B6F86, "Accounts", v -> a.startActivity(new Intent(a, AccountsActivity.class))),
                Ui.action(a, "💵", 0xFF65A30D, "Cash", v -> Cash.sheet(a)),
                Ui.action(a, "📅", 0xFF3B82F6, "Bills", v -> a.startActivity(new Intent(a, BillsActivity.class))),
                Ui.action(a, "💳", 0xFF6D28D9, "Cards", v -> a.startActivity(new Intent(a, CardsActivity.class)))));
        body.addView(Ui.label(a, "Life"));
        body.addView(gridCard(
                Ui.action(a, "👥", 0xFFA855F7, "People", v -> a.startActivity(new Intent(a, People.class))),
                Ui.action(a, "🔔", 0xFF0EA5E9, "Reminders", v -> a.startActivity(new Intent(a, Reminders.class))),
                Ui.action(a, "↩️", 0xFFF59E0B, "Refunds", v -> a.startActivity(new Intent(a, Refunds.class))),
                Ui.action(a, "🏠", 0xFF64748B, "Loans", v -> a.startActivity(new Intent(a, Loans.class)))));
        body.addView(Ui.label(a, "Grow"));
        body.addView(gridCard(
                Ui.action(a, "🏆", 0xFFF59E0B, "Goals", v -> a.startActivity(new Intent(a, Goals.class))),
                Ui.action(a, "🔥", 0xFFEF4444, "Challenges", v -> a.startActivity(new Intent(a, Challenges.class))),
                Ui.action(a, "💎", 0xFF0EA5E9, "Net worth", v -> a.startActivity(new Intent(a, NetWorthActivity.class))),
                Ui.action(a, "⭐", 0xFFEA580C, "Rewards", v -> a.startActivity(new Intent(a, Rewards.class)))));
        body.addView(Ui.label(a, "Tools"));
        body.addView(gridCard(
                Ui.action(a, "🧾", 0xFF6366F1, "Tax", v -> a.startActivity(new Intent(a, TaxActivity.class))),
                Ui.action(a, "📄", 0xFF0E9F6E, "Statement", v -> a.startActivity(new Intent(a, StatementActivity.class))),
                Ui.action(a, "✏️", 0xFF8B5CF6, "Add payment", v -> TxnSheet.addManual(a)),
                Ui.action(a, "⚙️", 0xFF94A3B8, "Settings", v -> a.startActivity(new Intent(a, SettingsActivity.class)))));
    }

    private View gridCard(View... items) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
        c.addView(Ui.grid(a, items));
        return c;
    }
}
