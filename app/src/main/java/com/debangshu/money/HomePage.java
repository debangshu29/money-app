package com.debangshu.money;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Home, top to bottom: Needs you (only when something does) · This month · quick actions · Money you have · Recent.
 * Every section has its own eye to hide just that section's amounts.
 */
final class HomePage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;

    static final String H_BUDGET = "budget", H_SPEND = "month", H_BAL = "balances", H_LATEST = "latest";
    static final String[] SECTIONS = {H_BUDGET, H_SPEND, H_BAL, H_LATEST};

    HomePage(MainActivity a) { this.a = a; }

    View view() {
        if (scroll == null) {
            scroll = new ScrollView(a);
            box = Ui.vbox(a);
            box.setPadding(0, 0, 0, Ui.dp(a, 28));
            scroll.addView(box);
        }
        return scroll;
    }

    static boolean hidden(android.content.Context c, String section) {
        return Store.get(c).prefs().getBoolean("hide_" + section, false);
    }

    /** "Hide amounts when opening": every section starts hidden. */
    static void hideAll(android.content.Context c) {
        android.content.SharedPreferences.Editor e = Store.get(c).prefs().edit();
        for (String sct : SECTIONS) e.putBoolean("hide_" + sct, true);
        e.apply();
    }

    void refresh() {
        view();
        int y = scroll.getScrollY();
        box.removeAllViews();
        Store s = Store.get(a);
        long now = System.currentTimeMillis();
        List<Txn> all = s.txns();
        box.addView(a.topBar("Money", null));
        TextView date = Ui.text(a, Fmt.DAY[Fmt.cal(now).get(java.util.Calendar.DAY_OF_WEEK) - 1] + ", " + Fmt.date(now), 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
        date.setPadding(Ui.dp(a, 20), 0, 0, Ui.dp(a, 10));
        box.addView(date);
        LinearLayout body = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        body.setPadding(p, 0, p, 0);
        box.addView(body);

        // ---- only when something needs you: one tidy card on top
        List<View> need = new ArrayList<>();
        if (!Setup.smsOn(a) || !Setup.listenerOn(a)) need.add(Ui.navRow(a, "🛡️", 0xFFDC2626,
                !Setup.smsOn(a) ? "Reading bank SMS is off" : "Payment notifications are off", "Tap to fix — Money can't see payments",
                v -> a.startActivity(new Intent(a, SetupActivity.class))));
        List<Txn> look = Resolve.pending(a);
        if (!look.isEmpty()) need.add(Ui.navRow(a, "✅", 0xFF0B6F86, look.size() + (look.size() == 1 ? " payment needs a look" : " payments need a look"),
                "Sort them out in a minute", v -> Resolve.start(a)));
        String due = dueSoon(s, all, now);
        if (due != null) need.add(Ui.navRow(a, "📅", 0xFF3B82F6, due, "Bills & subscriptions", v -> a.startActivity(new Intent(a, BillsActivity.class))));
        int late = Refunds.overdue(a);
        if (late > 0) need.add(Ui.navRow(a, "↩️", 0xFFF59E0B, late == 1 ? "A refund is late" : late + " refunds are late", "See what to do",
                v -> a.startActivity(new Intent(a, Refunds.class))));
        java.util.List<org.json.JSONObject> soon = Reminders.upcoming(a, 2);
        if (!soon.isEmpty()) need.add(Ui.navRow(a, Reminders.emoji(soon.get(0).optString("kind")), Reminders.color(soon.get(0).optString("kind")),
                soon.get(0).optString("name") + " " + Reminders.when(soon.get(0).optLong("until")), soon.size() > 1 ? "+" + (soon.size() - 1) + " more" : "Reminder",
                v -> a.startActivity(new Intent(a, Reminders.class))));
        if (!need.isEmpty()) {
            LinearLayout nc = Ui.card(a);
            nc.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
            for (View v : need) nc.addView(v);
            body.addView(nc, gap());
        }

        if (s.visible().isEmpty()) {
            LinearLayout e = Ui.card(a);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(Ui.dp(a, 20), Ui.dp(a, 24), Ui.dp(a, 20), Ui.dp(a, 20));
            e.addView(Ui.iconBadge(a, "📥", 0xFF0B6F86, 56));
            TextView t1 = Ui.text(a, "Waiting for your first payment", 17, Ui.BOLD);
            t1.setGravity(Gravity.CENTER);
            t1.setPadding(0, Ui.dp(a, 12), 0, 0);
            e.addView(t1);
            TextView t2 = Ui.sub(a, "Pay as usual — it shows up here by itself.");
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 14));
            e.addView(t2);
            TextView imp = Ui.button(a, "Bring in past SMS", true);
            imp.setOnClickListener(v -> SetupActivity.importPast(a, a::refresh));
            e.addView(imp);
            body.addView(e, gap());
        }

        body.addView(hero(s, all, now), gap());
        // quick actions, Google Pay style — straight on the background
        body.addView(Ui.grid(a,
                Ui.action(a, "➕", 0xFF8B5CF6, "Add", v -> TxnSheet.addManual(a)),
                Ui.action(a, "🎤", 0xFFEC4899, "Say it", v -> VoiceActivity.start(a)),
                Ui.action(a, "🏦", 0xFF0B6F86, "Accounts", v -> a.startActivity(new Intent(a, AccountsActivity.class))),
                Ui.action(a, "💬", 0xFF0EA5E9, "Ask", v -> a.startActivity(new Intent(a, AskActivity.class)))), gap());
        View bal = balances(s);
        if (bal != null) body.addView(bal, gap());
        View latest = latest(s);
        if (latest != null) body.addView(latest, gap());
        Fmt.hide = false;
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private LinearLayout.LayoutParams gap() { return Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12); }

    /** Card title with this section's own eye. */
    private LinearLayout header(LinearLayout card, String title, String section) {
        boolean h = section != null && hidden(a, section);
        Fmt.hide = h;
        LinearLayout row = Ui.hbox(a);
        TextView t = Ui.text(a, title, 16, Ui.col(a, R.color.text2), Ui.BOLD);
        row.addView(t, Ui.weight1());
        if (section != null) {
            ImageView eye = Ui.icon(a, R.drawable.ic_eye, h ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.hint), 20);
            eye.setContentDescription(h ? "Show amounts" : "Hide amounts");
            eye.setLayoutParams(Ui.lp(Ui.dp(a, 36), Ui.dp(a, 36)));
            eye.setOnClickListener(v -> {
                Store.get(a).prefs().edit().putBoolean("hide_" + section, !h).apply();
                a.refresh();
            });
            row.addView(eye);
        }
        card.addView(row);
        return row;
    }

    // ------------------------------------------------------------------ 1. this month, in one card

    private View hero(Store s, List<Txn> all, long now) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 20), Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 18));
        long ms = Fmt.startOfMonth(now), me = Fmt.addMonths(ms, 1);
        header(c, "Spent in " + Fmt.MONTH[Fmt.cal(now).get(java.util.Calendar.MONTH)], H_BUDGET);
        long spent = Ledger.spent(all, ms, me);
        TextView big = Ui.text(a, Fmt.rs(spent), 38, Ui.EXTRABOLD);
        big.setOnClickListener(v -> Flow.show(a, ms, me, false, "in " + Fmt.month(now)));
        c.addView(big);

        Plan p = s.plan();
        boolean active = p != null && p.started(now) && !p.finished(now);
        if (active) {
            long ps = p.spent(all);
            float f = p.amount <= 0 ? 0 : ps / (float) p.amount;
            Charts.Progress bar = new Charts.Progress(a);
            bar.budget(f, 10);
            c.addView(bar, Ui.margins(Ui.matchWrap(), a, 0, 12, 8, 6));
            long left = p.amount - ps;
            LinearLayout nums = Ui.hbox(a);
            nums.addView(Ui.text(a, left >= 0 ? Fmt.rs(left) + " left" : Fmt.rs(-left) + " over", 13, Ui.budgetColor(f), Ui.BOLD), Ui.weight1());
            TextView of = Ui.text(a, "of " + Fmt.rs(p.amount) + " · " + p.daysLeft(now) + (p.daysLeft(now) == 1 ? " day" : " days"), 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
            of.setPadding(0, 0, Ui.dp(a, 8), 0);
            nums.addView(of);
            c.addView(nums);
        } else {
            long last = Ledger.spent(all, Fmt.addMonths(ms, -1), Fmt.addMonths(now, -1) + 1);
            if (last > 0) {
                long d = spent - last;
                c.addView(Ui.sub(a, (d <= 0 ? "▼ " + Fmt.rs(-d) + " less" : "▲ " + Fmt.rs(d) + " more") + " than this time last month"));
            }
        }

        // three small numbers: safe today (or set a budget) · today · came in
        LinearLayout row = Ui.hbox(a);
        row.setBaselineAligned(false);
        long dayStart = Fmt.startOfDay(now), dayEnd = Fmt.addDays(dayStart, 1);
        if (active) {
            List<Ledger.Recurring> rec = Ledger.recurring(all, now);
            long safe = p.safeToday(all, rec, now);
            row.addView(mini(safe >= 0 ? "Safe today" : "Over today", Fmt.rs(Math.abs(safe)),
                    safe >= 0 ? Ui.col(a, R.color.success) : Ui.col(a, R.color.danger), v -> openBudget()), mlp(true));
        } else {
            row.addView(mini("Budget", "Set one", Ui.col(a, R.color.accent), v -> openBudget()), mlp(true));
        }
        row.addView(mini("Today", Fmt.rs(Ledger.spent(all, dayStart, dayEnd)), Ui.col(a, R.color.text2),
                v -> Flow.show(a, dayStart, dayEnd, false, "today")), mlp(false));
        row.addView(mini("Came in", Fmt.rs(Ledger.income(all, ms, me) + Ledger.refunds(all, ms, me)), Ui.col(a, R.color.success),
                v -> Flow.show(a, ms, me, true, "in " + Fmt.month(now))), mlp(false));
        c.addView(row, Ui.margins(Ui.matchWrap(), a, 0, 14, 8, 0));
        return c;
    }

    private LinearLayout.LayoutParams mlp(boolean first) {
        return Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, first ? 0 : 8, 0, 0, 0);
    }

    private View mini(String label, String value, int color, View.OnClickListener l) {
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        t.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 16));
        t.addView(Ui.oneLine(Ui.text(a, label, 12, color, Ui.BOLD)));
        t.addView(Ui.oneLine(Ui.text(a, value, 16, Ui.EXTRABOLD)));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ 3. balances (compact)

    private View balances(Store s) {
        List<Account> accs = new ArrayList<>();
        for (Account ac : s.accounts()) if (ac.balance >= 0 || ac.limit >= 0) accs.add(ac);
        if (accs.isEmpty() && !Cash.on(a)) return null;
        LinearLayout c = Ui.card(a);
        LinearLayout h = header(c, "Money you have", H_BAL);
        long total = 0;
        for (Account ac : accs) if (!ac.card && !ac.hidden && ac.balance >= 0) total += s.liveBalance(ac);
        if (Cash.on(a)) total += Cash.balance(a);
        TextView tt = Ui.text(a, Fmt.rs(total), 17, Ui.EXTRABOLD);
        h.addView(tt, 1);
        int shown = 0;
        for (Account ac : accs) {
            if (shown++ >= 3) break;
            boolean card = ac.card && ac.limit >= 0;
            LinearLayout r = Ui.hbox(a);
            r.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
            r.setBackground(Ui.rippleRound(a, 0x00000000, 12));
            r.addView(Ui.iconBadge(a, ac.card ? "💳" : "🏦", ac.card ? 0xFF6D28D9 : 0xFF0B6F86, 32));
            TextView nm = Ui.oneLine(Ui.text(a, Noted.fullLabel(a, ac.key) + (card ? "  · limit left" : ""), 14,
                    ac.hidden ? Ui.col(a, R.color.text2) : Ui.col(a, R.color.text), Ui.SEMIBOLD));
            nm.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
            r.addView(nm, Ui.weight1());
            r.addView(Ui.text(a, Fmt.rs(card ? ac.limit : s.liveBalance(ac)), 14, Ui.BOLD));
            r.setOnClickListener(v -> AccountMenu.show(a, ac));
            r.setOnLongClickListener(v -> { AccountMenu.show(a, ac); return true; });
            c.addView(r);
        }
        if (Cash.on(a)) {
            LinearLayout r = Ui.hbox(a);
            r.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
            r.addView(Ui.iconBadge(a, "💵", 0xFF65A30D, 32));
            TextView nm = Ui.text(a, "Cash wallet", 14, Ui.SEMIBOLD);
            nm.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
            r.addView(nm, Ui.weight1());
            r.addView(Ui.text(a, Fmt.rs(Cash.balance(a)), 14, Ui.BOLD));
            r.setOnClickListener(v -> Cash.sheet(a));
            c.addView(r);
        }
        c.addView(link(accs.size() > 3 ? "All " + accs.size() + " accounts" : "Manage accounts", v -> a.startActivity(new Intent(a, AccountsActivity.class))));
        return c;
    }

    // ------------------------------------------------------------------ 4. latest

    private View latest(Store s) {
        List<Txn> vis = s.visible();
        if (vis.isEmpty()) return null;
        LinearLayout c = Ui.card(a);
        header(c, "Recent", H_LATEST);
        for (int i = 0; i < Math.min(4, vis.size()); i++) c.addView(Rows.txn(a, vis.get(i), true));
        c.addView(link("See all", v -> a.show(MainActivity.ACTIVITY)));
        return c;
    }

    private String dueSoon(Store s, List<Txn> all, long now) {
        for (Store.Bill b : s.bills()) {
            if (b.paid || b.dueDate < now - 86400000 || b.dueDate > now + 3L * 86400000 || Alerts.billPaid(s, b)) continue;
            return (b.account.isEmpty() ? "Card" : b.account) + " bill " + Fmt.plain(Math.max(0, b.due)) + " due " + Fmt.rel(b.dueDate);
        }
        for (Ledger.Recurring r : Ledger.recurring(all, now)) {
            if (r.next >= Fmt.startOfDay(now) && r.next <= now + 2L * 86400000) return r.name + " " + Fmt.plain(r.amount) + " expected " + Fmt.rel(r.next);
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private void openBudget() { a.startActivity(new Intent(a, BudgetActivity.class)); }

    private View link(String label, View.OnClickListener l) {
        TextView t = Ui.text(a, label, 14, Ui.col(a, R.color.accent), Ui.BOLD);
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

    private void accountMenu(Account ac) { AccountMenu.show(a, ac); }
}
