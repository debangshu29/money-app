package com.debangshu.money;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Home: this month at a glance, what needs a look, what's coming, balances, latest payments. */
final class HomePage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;

    HomePage(MainActivity a) { this.a = a; }

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
        Store s = Store.get(a);
        long now = System.currentTimeMillis();
        List<Txn> all = s.txns();
        box.addView(a.topBar("Money", null));
        LinearLayout body = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        body.setPadding(p, 0, p, 0);
        box.addView(body);

        // ---- capture switched off?
        if (!Setup.smsOn(a) || !Setup.listenerOn(a)) {
            LinearLayout w = Ui.card(a);
            w.setBackground(Ui.round(Ui.col(a, R.color.warn_soft), Ui.dp(a, 22)));
            String what = !Setup.smsOn(a) && !Setup.listenerOn(a) ? "SMS and notification reading are off"
                    : !Setup.smsOn(a) ? "Reading bank SMS is off" : "Reading payment notifications is off";
            w.addView(Ui.text(a, "⚠️ " + what, 15, Ui.BOLD));
            TextView d = Ui.sub(a, "Some payments won't be added by themselves. It takes a minute to fix.");
            d.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
            w.addView(d);
            TextView fix = Ui.button(a, "Fix it", true);
            fix.setOnClickListener(v -> a.startActivity(new Intent(a, SetupActivity.class)));
            LinearLayout r = Ui.hbox(a);
            r.addView(fix);
            w.addView(r);
            body.addView(w, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 12));
        }

        // ---- hero: your budget (when one is running), otherwise this month
        long ms = Fmt.startOfMonth(now);
        Plan plan = s.plan();
        if (plan != null) body.addView(budgetHero(plan, all, now), Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 12));
        else body.addView(monthHero(all, now, ms), Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 12));

        // ---- today / money in
        long today = Fmt.startOfDay(now);
        int nToday = 0;
        for (Txn t : all) if (t.isSpend() && t.time >= today) nToday++;
        LinearLayout two = Ui.hbox(a);
        two.addView(mini("Today", Fmt.rs(Ledger.spent(all, today, now + 1)), nToday == 0 ? "no payments yet" : nToday + (nToday == 1 ? " payment" : " payments")),
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 0, 0, 6, 0));
        two.addView(mini("Money in", Fmt.rs(Ledger.income(all, ms, now + 1)), "this month"),
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 6, 0, 0, 0));
        body.addView(two, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        if (plan != null && plan.started(now)) {
            LinkedHashMap<String, Long> cats = plan.byCategory(all);
            if (!cats.isEmpty()) {
                LinearLayout c = Ui.card(a);
                c.addView(Ui.sectionTitle(a, "Where your budget is going"));
                int i = 0;
                for (Map.Entry<String, Long> e : cats.entrySet()) {
                    if (i++ >= 5) break;
                    int pct = (int) Math.round(e.getValue() * 100.0 / Math.max(1, plan.amount));
                    View row = Rows.amountRow(a, Cats.emoji(e.getKey()), Cats.color(e.getKey()), e.getKey(), pct + "% of budget",
                            e.getValue(), e.getValue() / (float) Math.max(1, plan.amount));
                    row.setOnClickListener(v -> openBudget());
                    c.addView(row);
                }
                c.addView(link("Open budget", v -> openBudget()));
                body.addView(c, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
            }
        }

        List<Txn> vis = s.visible();
        if (vis.isEmpty()) {
            LinearLayout e = Ui.card(a);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.addView(Ui.text(a, "📭", 40, Ui.REGULAR));
            TextView t1 = Ui.text(a, "Waiting for your first payment", 17, Ui.BOLD);
            t1.setGravity(Gravity.CENTER);
            e.addView(t1);
            TextView t2 = Ui.sub(a, "Pay with UPI or card as usual — it appears here by itself. Or bring in the bank SMS already on your phone.");
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
            e.addView(t2);
            TextView imp = Ui.button(a, "Bring in past messages", true);
            imp.setOnClickListener(v -> SetupActivity.importPast(a, a::refresh));
            e.addView(imp);
            body.addView(e, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
            return;
        }

        // ---- needs a look
        List<Txn> look = new ArrayList<>();
        for (Txn t : vis) if (t.review) look.add(t);
        if (!look.isEmpty()) {
            LinearLayout c = Ui.card(a);
            c.addView(Ui.sectionTitle(a, "Needs a look (" + look.size() + ")"));
            for (int i = 0; i < Math.min(5, look.size()); i++) c.addView(Rows.txn(a, look.get(i), true));
            if (look.size() > 5) c.addView(link("See all", v -> a.show(MainActivity.ACTIVITY)));
            body.addView(c, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
        }

        // ---- coming up
        LinearLayout up = Ui.card(a);
        up.addView(Ui.sectionTitle(a, "Coming up"));
        int items = 0;
        for (Store.Bill b : s.bills()) {
            if (b.paid || b.dueDate < now - 86400000 || b.dueDate > now + 20L * 86400000) continue;
            if (Alerts.billPaid(s, b)) continue;
            up.addView(Rows.amountRow(a, "💳", 0xFF3B82F6, (b.account.isEmpty() ? "Card" : b.account) + " bill",
                    "Due " + Fmt.rel(b.dueDate) + " (" + Fmt.date(b.dueDate) + ")" + (b.minDue > 0 ? " · min " + Fmt.rs(b.minDue) : ""), Math.max(0, b.due), -1));
            items++;
        }
        for (Ledger.Recurring r : Ledger.recurring(all, now)) {
            if (r.next < now - 86400000 || r.next > now + 12L * 86400000) continue;
            up.addView(Rows.amountRow(a, Cats.emoji(r.category), Cats.color(r.category), r.name,
                    r.periodName() + " · expected " + Fmt.rel(r.next), r.amount, -1));
            items++;
        }
        if (items > 0) body.addView(up, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));

        // ---- accounts
        List<Account> accs = new ArrayList<>();
        for (Account ac : s.accounts()) if (ac.balance >= 0 || ac.limit >= 0) accs.add(ac);
        if (!accs.isEmpty()) {
            LinearLayout c = Ui.card(a);
            long total = 0;
            for (Account ac : accs) if (!ac.card && !ac.hidden && ac.balance >= 0) total += ac.balance;
            LinearLayout h = Ui.hbox(a);
            h.addView(Ui.sectionTitle(a, "Balances"), Ui.weight1());
            TextView tt = Ui.text(a, Fmt.rs(total), 17, Ui.EXTRABOLD);
            tt.setPadding(0, 0, 0, Ui.dp(a, 8));
            h.addView(tt);
            c.addView(h);
            for (Account ac : accs) {
                boolean card = ac.card && ac.limit >= 0;
                long v = card ? ac.limit : ac.balance;
                long when = card ? ac.limitTime : ac.balanceTime;
                View row = Rows.amountRow(a, ac.card ? "💳" : ac.wallet ? "👛" : "🏦", ac.card ? 0xFF8B5CF6 : 0xFF0E9F6E, ac.display(),
                        (card ? "Card limit left · " : "") + (ac.hidden ? "not in total · " : "") + "updated " + ago(when), v, -1);
                row.setOnClickListener(x -> accountMenu(ac));
                c.addView(row);
            }
            TextView note = Ui.text(a, "From the last bank SMS that showed a balance.", 11.5f, Ui.col(a, R.color.hint), Ui.REGULAR);
            note.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, 0);
            c.addView(note);
            body.addView(c, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
        }

        // ---- recent
        LinearLayout rc = Ui.card(a);
        rc.addView(Ui.sectionTitle(a, "Latest"));
        for (int i = 0; i < Math.min(7, vis.size()); i++) rc.addView(Rows.txn(a, vis.get(i), true));
        rc.addView(link("See all activity", v -> a.show(MainActivity.ACTIVITY)));
        body.addView(rc, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12));
    }

    private void openBudget() { a.startActivity(new Intent(a, BudgetActivity.class)); }

    private View budgetHero(Plan p, List<Txn> all, long now) {
        long spent = p.spent(all);
        long left = p.amount - spent;
        float f = p.amount <= 0 ? 0 : spent / (float) p.amount;
        LinearLayout hero = Ui.vbox(a);
        int c1 = f >= 1 ? 0xFFD14343 : f >= 0.8f ? 0xFFE08A00 : Ui.col(a, R.color.hero1);
        int c2 = f >= 1 ? 0xFF9F2A4B : f >= 0.8f ? 0xFFB4561A : Ui.col(a, R.color.hero2);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(Ui.dp(a, 26));
        hero.setBackground(g);
        hero.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 20));
        hero.addView(Ui.text(a, "Budget · " + p.range(), 14, 0xD9FFFFFF, Ui.SEMIBOLD));
        if (!p.started(now)) {
            hero.addView(Ui.text(a, Fmt.rs(p.amount), 38, 0xFFFFFFFF, Ui.EXTRABOLD));
            hero.addView(Ui.text(a, p.status(all, now), 13.5f, 0xFFFFFFFF, Ui.SEMIBOLD));
        } else {
            hero.addView(Ui.text(a, left >= 0 ? Fmt.rs(left) + " left" : Fmt.rs(-left) + " over", 36, 0xFFFFFFFF, Ui.EXTRABOLD));
            hero.addView(Ui.text(a, Fmt.rs(spent) + " spent of " + Fmt.rs(p.amount), 14, 0xF2FFFFFF, Ui.SEMIBOLD));
            Charts.Progress pr = new Charts.Progress(a);
            pr.set(f, 0xFFFFFFFF);
            hero.addView(pr, Ui.margins(Ui.matchWrap(), a, 0, 14, 0, 8));
            hero.addView(Ui.text(a, p.status(all, now), 13.5f, 0xFFFFFFFF, Ui.SEMIBOLD));
            if (spent > 0 && !p.finished(now)) {
                long proj = p.projected(all, now);
                if (proj > p.amount) hero.addView(Ui.text(a, "At this pace: about " + Fmt.rs(proj) + " by the end", 13, 0xE6FFFFFF, Ui.REGULAR));
            }
        }
        hero.setOnClickListener(v -> openBudget());
        return hero;
    }

    private View monthHero(List<Txn> all, long now, long ms) {
        long spent = Ledger.spent(all, ms, now + 1);
        long lastSame = Ledger.spent(all, Fmt.addMonths(ms, -1), Fmt.addMonths(now, -1) + 1);
        long forecast = Ledger.forecast(all, now);
        LinearLayout hero = Ui.vbox(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.col(a, R.color.hero1), Ui.col(a, R.color.hero2)});
        g.setCornerRadius(Ui.dp(a, 26));
        hero.setBackground(g);
        hero.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 20));
        hero.addView(Ui.text(a, "Spent in " + Fmt.MONTH[Fmt.cal(now).get(java.util.Calendar.MONTH)], 14, 0xD9FFFFFF, Ui.SEMIBOLD));
        hero.addView(Ui.text(a, Fmt.rs(spent), 38, 0xFFFFFFFF, Ui.EXTRABOLD));
        String cmp;
        if (lastSame == 0) cmp = "Tracking automatically from your messages";
        else {
            long diff = spent - lastSame;
            int pct = (int) Math.round(Math.abs(diff) * 100.0 / lastSame);
            cmp = (diff <= 0 ? "▼ " + Fmt.rs(-diff) + " (" + pct + "%) less" : "▲ " + Fmt.rs(diff) + " (" + pct + "%) more")
                    + " than this time last month";
        }
        hero.addView(Ui.text(a, cmp, 13, 0xE6FFFFFF, Ui.REGULAR));
        if (spent > 0) hero.addView(Ui.text(a, "Heading for about " + Fmt.rs(forecast) + " by the end of the month", 13, 0xE6FFFFFF, Ui.REGULAR));
        TextView set = Ui.text(a, "＋  Set a budget", 14, 0xFFFFFFFF, Ui.BOLD);
        set.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        set.setBackground(Ui.rippleRound(a, 0x33FFFFFF, 20));
        set.setOnClickListener(v -> openBudget());
        LinearLayout r = Ui.hbox(a);
        r.addView(set);
        hero.addView(r, Ui.margins(Ui.matchWrap(), a, 0, 14, 0, 0));
        hero.setOnClickListener(v -> a.show(MainActivity.INSIGHTS));
        return hero;
    }

    private View mini(String label, String value, String sub) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, label, 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        c.addView(Ui.oneLine(Ui.text(a, value, 21, Ui.EXTRABOLD)));
        c.addView(Ui.text(a, sub, 12, Ui.col(a, R.color.text2), Ui.REGULAR));
        return c;
    }

    private View link(String label, View.OnClickListener l) {
        TextView t = Ui.text(a, label + "  →", 14, Ui.col(a, R.color.accent), Ui.BOLD);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 2));
        t.setOnClickListener(l);
        return t;
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 24 * 60) return (m / 60) + " h ago";
        return Fmt.day(t);
    }

    private void accountMenu(Account ac) {
        String[] opts = {"Rename", "Don't count in total balance", "Show its payments"};
        if (ac.hidden) opts[1] = "Count in total balance";
        new Ui.Popup(a).setTitle(ac.display()).setItems(opts, (d, w) -> {
            if (w == 0) {
                android.widget.EditText e = new android.widget.EditText(a);
                e.setText(ac.display());
                e.setTypeface(Ui.face(a, Ui.REGULAR));
                LinearLayout b = Ui.vbox(a);
                int p = Ui.dp(a, 24);
                b.setPadding(p, 0, p, 0);
                b.addView(e);
                new Ui.Popup(a).setTitle("Name").setView(b)
                        .setPositiveButton("Save", (x, y) -> Store.get(a).renameAccount(ac, e.getText().toString().trim()))
                        .setNegativeButton("Cancel", null).show();
            } else if (w == 1) {
                Store.get(a).setAccountHidden(ac, !ac.hidden);
            } else {
                a.filterLabel = ac.display();
                a.filterCategory = "acc:" + ac.key;
                a.filterFrom = 0;
                a.filterTo = 0;
                a.show(MainActivity.ACTIVITY);
            }
        }).show();
    }
}
