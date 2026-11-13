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
 * Home, top to bottom: Budget · Balances · This month (in / out) · Today (in / out) · Latest · Coming up · Needs a look.
 * Every section has its own eye to hide just that section's amounts.
 */
final class HomePage {

    private final MainActivity a;
    private ScrollView scroll;
    private LinearLayout box;

    static final String H_BUDGET = "budget", H_SPEND = "month", H_BAL = "balances", H_LATEST = "latest";
    static final String[] SECTIONS = {H_BUDGET, H_SPEND, H_BAL, H_LATEST};
    private boolean showMonth = true;   // Today ⇄ This month

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

        if (!Setup.smsOn(a) || !Setup.listenerOn(a)) {
            body.addView(strip("⚠️", !Setup.smsOn(a) ? "Reading bank SMS is off — fix it" : "Payment notifications are off — fix it",
                    Ui.col(a, R.color.warn_soft), v -> a.startActivity(new Intent(a, SetupActivity.class))), gap());
        }
        if (!s.prefs().getBoolean("seen_intro", false)) body.addView(intro(), gap());

        if (s.visible().isEmpty()) {
            LinearLayout e = Ui.card(a);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.addView(Ui.text(a, "📭", 40, Ui.REGULAR));
            TextView t1 = Ui.text(a, "Waiting for your first payment", 17, Ui.BOLD);
            t1.setGravity(Gravity.CENTER);
            e.addView(t1);
            TextView t2 = Ui.sub(a, "Pay as usual — it appears here by itself. Or bring in the bank SMS already on your phone.");
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
            e.addView(t2);
            TextView imp = Ui.button(a, "Bring in past messages", true);
            imp.setOnClickListener(v -> SetupActivity.importPast(a, a::refresh));
            e.addView(imp);
            body.addView(e, gap());
        }

        body.addView(hero(s, all, now), gap());
        // quick actions, Google Pay style
        LinearLayout qa = Ui.card(a);
        qa.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
        qa.addView(Ui.grid(a,
                Ui.action(a, "✏️", 0xFF8B5CF6, "Add", v -> TxnSheet.addManual(a)),
                Ui.action(a, "🎤", 0xFFEC4899, "Say it", v -> VoiceActivity.start(a)),
                Ui.action(a, "🏦", 0xFF0B6F86, "Accounts", v -> a.startActivity(new Intent(a, AccountsActivity.class))),
                Ui.action(a, "💬", 0xFF0EA5E9, "Ask", v -> a.startActivity(new Intent(a, AskActivity.class)))));
        body.addView(qa, gap());
        body.addView(spending(all, now), gap());
        View bal = balances(s);
        if (bal != null) body.addView(bal, gap());
        View latest = latest(s);
        if (latest != null) body.addView(latest, gap());

        // slim strips, only when something needs you
        Fmt.hide = false;
        List<Txn> look = Resolve.pending(a);
        if (!look.isEmpty()) body.addView(strip("🔍", look.size() + (look.size() == 1 ? " payment needs" : " payments need") + " a look — sort them out",
                Ui.col(a, R.color.accent_soft), v -> Resolve.start(a)), gap());
        String due = dueSoon(s, all, now);
        if (due != null) body.addView(strip("📅", due, Ui.col(a, R.color.surface), v -> a.startActivity(new Intent(a, BillsActivity.class))), gap());
        int late = Refunds.overdue(a);
        if (late > 0) body.addView(strip("↩️", late == 1 ? "A failed payment hasn't come back yet — see what to do" : late + " failed payments haven't come back yet",
                Ui.col(a, R.color.surface), v -> a.startActivity(new Intent(a, Refunds.class))), gap());
        java.util.List<org.json.JSONObject> soon = Reminders.upcoming(a, 2);
        if (!soon.isEmpty()) body.addView(strip(Reminders.emoji(soon.get(0).optString("kind")), soon.get(0).optString("name") + " " + Reminders.when(soon.get(0).optLong("until"))
                + (soon.size() > 1 ? " · +" + (soon.size() - 1) + " more" : ""), Ui.col(a, R.color.surface), v -> a.startActivity(new Intent(a, Reminders.class))), gap());
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

    // ------------------------------------------------------------------ first-time card

    private View intro() {
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.round(Ui.col(a, R.color.accent_soft), Ui.dp(a, 22)));
        c.addView(Ui.text(a, "👋  How Money works", 16, Ui.BOLD));
        TextView t = Ui.text(a, "1.  Pay as usual — every payment shows up here by itself.\n"
                + "2.  Tap Money out / Money in to see where it went or came from.\n"
                + "3.  Use the round button to Scan & Pay — Money remembers who you paid.\n"
                + "4.  Plan: set a budget, see bills, fix accounts, track cash.", 13.5f, Ui.col(a, R.color.text), Ui.REGULAR);
        t.setLineSpacing(Ui.dp(a, 4), 1f);
        t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 10));
        c.addView(t);
        TextView ok = Ui.button(a, "Got it", true);
        ok.setOnClickListener(v -> { Store.get(a).prefs().edit().putBoolean("seen_intro", true).apply(); a.refresh(); });
        LinearLayout r = Ui.hbox(a);
        r.addView(ok);
        c.addView(r);
        return c;
    }

    // ------------------------------------------------------------------ 1. the one big number

    private View hero(Store s, List<Txn> all, long now) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 20));
        Plan p = s.plan();
        if (p == null || p.finished(now)) {
            header(c, "Spent in " + Fmt.MONTH[Fmt.cal(now).get(java.util.Calendar.MONTH)], H_BUDGET);
            long ms = Fmt.startOfMonth(now);
            long spent = Ledger.spent(all, ms, now + 1);
            c.addView(Ui.text(a, Fmt.rs(spent), 38, Ui.EXTRABOLD));
            long last = Ledger.spent(all, Fmt.addMonths(ms, -1), Fmt.addMonths(now, -1) + 1);
            if (last > 0) {
                long d = spent - last;
                c.addView(Ui.sub(a, (d <= 0 ? "▼ " + Fmt.rs(-d) + " less" : "▲ " + Fmt.rs(d) + " more") + " than this time last month"));
            }
            TextView set = Ui.chip(a, "🎯  Set a budget to see what's safe to spend each day  ›", false);
            set.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.accent_soft), 18));
            set.setTextColor(Ui.col(a, R.color.text));
            set.setOnClickListener(v -> openBudget());
            LinearLayout r = Ui.hbox(a);
            r.addView(set);
            c.addView(r, Ui.margins(Ui.matchWrap(), a, 0, 14, 8, 0));
            return c;
        }
        long spent = p.spent(all);
        float f = p.amount <= 0 ? 0 : spent / (float) p.amount;
        if (!p.started(now)) {
            header(c, "Budget", H_BUDGET);
            c.addView(Ui.text(a, Fmt.rs(p.amount), 36, Ui.EXTRABOLD));
            c.addView(Ui.sub(a, "Starts " + Fmt.rel(p.start) + " · " + p.range()));
        } else {
            header(c, "Safe to spend today", H_BUDGET);
            List<Ledger.Recurring> rec = Ledger.recurring(all, now);
            long safe = p.safeToday(all, rec, now);
            int col = safe >= 0 ? Ui.budgetColor(Math.min(0.99f, f)) : Ui.budgetColor(1.1f);
            LinearLayout big = Ui.hbox(a);
            big.setGravity(Gravity.BOTTOM);
            big.addView(Ui.text(a, Fmt.rs(Math.abs(safe)), 40, safe >= 0 ? Ui.col(a, R.color.text) : col, Ui.EXTRABOLD));
            if (safe < 0) {
                TextView o = Ui.text(a, "  over today", 15, col, Ui.BOLD);
                o.setPadding(0, 0, 0, Ui.dp(a, 8));
                big.addView(o);
            }
            c.addView(big);
            long upcoming = p.upcoming(rec, now);
            c.addView(Ui.sub(a, upcoming > 0 ? "after " + Fmt.rs(upcoming) + " of bills still to come" : "keeps you within your budget"));
            Charts.Progress bar = new Charts.Progress(a);
            bar.budget(f, 12);
            c.addView(bar, Ui.margins(Ui.matchWrap(), a, 0, 16, 8, 6));
            LinearLayout nums = Ui.hbox(a);
            long left = p.amount - spent;
            nums.addView(Ui.text(a, left >= 0 ? Fmt.rs(left) + " left" : Fmt.rs(-left) + " over", 13, Ui.budgetColor(f), Ui.BOLD), Ui.weight1());
            TextView of = Ui.text(a, "of " + Fmt.rs(p.amount) + " · " + p.daysLeft(now) + (p.daysLeft(now) == 1 ? " day" : " days"), 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
            of.setPadding(0, 0, Ui.dp(a, 8), 0);
            nums.addView(of);
            c.addView(nums);
        }
        c.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface), 22));
        c.setOnClickListener(v -> openBudget());
        return c;
    }

    // ------------------------------------------------------------------ 2. today / this month: money out, money in

    private View spending(List<Txn> all, long now) {
        LinearLayout c = Ui.card(a);
        LinearLayout h = header(c, "", H_SPEND);
        h.removeViewAt(0);
        LinearLayout seg = Ui.hbox(a);
        seg.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 18)));
        seg.setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
        String[] names = {"Today", Fmt.MONTH[Fmt.cal(now).get(java.util.Calendar.MONTH)]};
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == showMonth;
            TextView t = Ui.text(a, names[i], 13.5f, on ? Ui.col(a, R.color.text) : Ui.col(a, R.color.text2), on ? Ui.BOLD : Ui.SEMIBOLD);
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6));
            t.setBackground(on ? Ui.round(Ui.col(a, R.color.surface), Ui.dp(a, 15)) : null);
            final boolean m = i == 1;
            t.setOnClickListener(v -> { showMonth = m; a.refresh(); });
            seg.addView(t);
        }
        h.addView(seg, 0);
        h.addView(new View(a), 1, Ui.weight1());

        long from = showMonth ? Fmt.startOfMonth(now) : Fmt.startOfDay(now);
        long to = showMonth ? Fmt.addMonths(from, 1) : Fmt.addDays(from, 1);
        String when = showMonth ? "in " + Fmt.month(now) : "today";
        long out = Ledger.spent(all, from, to);
        long in = Ledger.income(all, from, to) + Ledger.refunds(all, from, to);
        int nOut = 0, nIn = 0;
        for (Txn t : all) {
            if (t.time < from || t.time >= to) continue;
            if (t.isSpend()) nOut++;
            else if (t.isIncome() || (t.kind == Txn.REFUND && t.credit)) nIn++;
        }
        LinearLayout tiles = Ui.hbox(a);
        tiles.addView(tile("↑  Money out", out, nOut, Ui.col(a, R.color.danger), v -> Flow.show(a, from, to, false, when)),
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 0, 10, 6, 0));
        tiles.addView(tile("↓  Money in", in, nIn, Ui.col(a, R.color.success), v -> Flow.show(a, from, to, true, when)),
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 6, 10, 0, 0));
        c.addView(tiles);
        long cmpFrom = showMonth ? Fmt.addMonths(from, -1) : Fmt.addDays(from, -1);
        long cmpTo = (showMonth ? Fmt.addMonths(now, -1) : Fmt.addDays(now, -1)) + 1;
        long before = Ledger.spent(all, cmpFrom, cmpTo);
        if (before > 0) {
            long d = out - before;
            TextView ct = Ui.sub(a, (d <= 0 ? "▼ " + Fmt.rs(-d) + " less" : "▲ " + Fmt.rs(d) + " more") + " spent than "
                    + (showMonth ? "this time last month" : "yesterday by now"));
            ct.setPadding(Ui.dp(a, 2), Ui.dp(a, 10), 0, 0);
            c.addView(ct);
        }
        return c;
    }

    private View tile(String label, long amount, int n, int color, View.OnClickListener l) {
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        t.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
        t.addView(Ui.text(a, label, 13, color, Ui.BOLD));
        t.addView(Ui.oneLine(Ui.text(a, Fmt.rs(amount), 21, Ui.EXTRABOLD)));
        t.addView(Ui.text(a, n + (n == 1 ? " payment" : " payments") + "  ›", 12, Ui.col(a, R.color.text2), Ui.REGULAR));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ 3. balances (compact)

    private View balances(Store s) {
        List<Account> accs = new ArrayList<>();
        for (Account ac : s.accounts()) if (ac.balance >= 0 || ac.limit >= 0) accs.add(ac);
        if (accs.isEmpty() && !Cash.on(a)) return null;
        LinearLayout c = Ui.card(a);
        LinearLayout h = header(c, "Balances", H_BAL);
        long total = 0;
        for (Account ac : accs) if (!ac.card && !ac.hidden && ac.balance >= 0) total += ac.balance;
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
            r.addView(Ui.oneLine(Ui.text(a, (ac.card ? "💳  " : "🏦  ") + ac.display() + (card ? "  (limit left)" : ""), 14,
                    ac.hidden ? Ui.col(a, R.color.text2) : Ui.col(a, R.color.text), Ui.SEMIBOLD)), Ui.weight1());
            r.addView(Ui.text(a, Fmt.rs(card ? ac.limit : ac.balance), 14, Ui.BOLD));
            r.setOnClickListener(v -> AccountMenu.show(a, ac));
            r.setOnLongClickListener(v -> { AccountMenu.show(a, ac); return true; });
            c.addView(r);
        }
        if (Cash.on(a)) {
            LinearLayout r = Ui.hbox(a);
            r.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
            r.addView(Ui.text(a, "💵  Cash wallet", 14, Ui.SEMIBOLD), Ui.weight1());
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
        header(c, "Latest", H_LATEST);
        for (int i = 0; i < Math.min(5, vis.size()); i++) c.addView(Rows.txn(a, vis.get(i), true));
        c.addView(link("See all", v -> a.show(MainActivity.ACTIVITY)));
        return c;
    }

    // ------------------------------------------------------------------ slim strips

    private View strip(String emoji, String text, int bg, View.OnClickListener l) {
        LinearLayout r = Ui.hbox(a);
        r.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        r.setBackground(Ui.rippleRound(a, bg, 18));
        r.addView(Ui.text(a, emoji, 16, Ui.REGULAR));
        TextView t = Ui.text(a, text, 14, Ui.SEMIBOLD);
        t.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
        r.addView(t, Ui.weight1());
        r.addView(Ui.text(a, "›", 18, Ui.col(a, R.color.text2), Ui.BOLD));
        r.setOnClickListener(l);
        return r;
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

    private void accountMenu(Account ac) { AccountMenu.show(a, ac); }
}
