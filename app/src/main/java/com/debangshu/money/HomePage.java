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

    static final String H_BUDGET = "budget", H_BAL = "balances", H_MONTH = "month", H_TODAY = "today", H_LATEST = "latest";
    static final String[] SECTIONS = {H_BUDGET, H_BAL, H_MONTH, H_TODAY, H_LATEST};

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

        if (s.visible().isEmpty()) {
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
        }

        body.addView(budget(s, all, now), gap());
        View bal = balances(s);
        if (bal != null) body.addView(bal, gap());
        long ms = Fmt.startOfMonth(now);
        body.addView(period(H_MONTH, Fmt.MONTH[Fmt.cal(now).get(java.util.Calendar.MONTH)], "in " + Fmt.month(now), all, ms, now + 1,
                Fmt.addMonths(ms, -1), Fmt.addMonths(now, -1) + 1, "this time last month"), gap());
        long today = Fmt.startOfDay(now);
        body.addView(period(H_TODAY, "Today", "today", all, today, Fmt.addDays(today, 1),
                Fmt.addDays(today, -1), Fmt.addDays(now, -1) + 1, "this time yesterday"), gap());
        View latest = latest(s);
        if (latest != null) body.addView(latest, gap());
        View up = comingUp(s, all, now);
        if (up != null) body.addView(up, gap());
        View look = needsLook(s);
        if (look != null) body.addView(look, gap());
        Fmt.hide = false;
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private LinearLayout.LayoutParams gap() { return Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 12); }

    /** Card title with this section's own eye. */
    private LinearLayout header(LinearLayout card, String title, String section) {
        boolean h = section != null && hidden(a, section);
        Fmt.hide = h;
        LinearLayout row = Ui.hbox(a);
        TextView t = Ui.text(a, title, 17, Ui.EXTRABOLD);
        row.addView(t, Ui.weight1());
        if (section != null) {
            ImageView eye = Ui.icon(a, R.drawable.ic_eye, h ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.hint), 20);
            eye.setContentDescription(h ? "Show amounts" : "Hide amounts");
            eye.setLayoutParams(Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
            eye.setOnClickListener(v -> {
                Store.get(a).prefs().edit().putBoolean("hide_" + section, !h).apply();
                a.refresh();
            });
            row.addView(eye);
        }
        card.addView(row);
        return row;
    }

    // ------------------------------------------------------------------ 1. budget

    private View budget(Store s, List<Txn> all, long now) {
        LinearLayout c = Ui.card(a);
        Plan p = s.plan();
        if (p == null) {
            header(c, "Budget", null);
            TextView d = Ui.sub(a, "Set how much you want to spend and from which day — every payment counts against it by itself.");
            d.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 12));
            c.addView(d);
            TextView go = Ui.button(a, "＋  Set a budget", true);
            go.setOnClickListener(v -> openBudget());
            LinearLayout r = Ui.hbox(a);
            r.addView(go);
            c.addView(r);
            return c;
        }
        header(c, "Budget", H_BUDGET);
        long spent = p.spent(all);
        long left = p.amount - spent;
        float f = p.amount <= 0 ? 0 : spent / (float) p.amount;
        int col = Ui.budgetColor(f);
        c.addView(Ui.text(a, p.range() + (p.started(now) ? "" : " · starts " + Fmt.rel(p.start)), 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        LinearLayout big = Ui.hbox(a);
        big.setGravity(Gravity.BOTTOM);
        TextView l = Ui.text(a, left >= 0 ? Fmt.rs(left) : Fmt.rs(-left), 32, col, Ui.EXTRABOLD);
        big.addView(l);
        TextView lw = Ui.text(a, left >= 0 ? "  left" : "  over budget", 15, col, Ui.BOLD);
        lw.setPadding(0, 0, 0, Ui.dp(a, 6));
        big.addView(lw);
        c.addView(big, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 0));
        Charts.Progress bar = new Charts.Progress(a);
        bar.budget(f, 14);
        c.addView(bar, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 6));
        LinearLayout nums = Ui.hbox(a);
        nums.addView(Ui.text(a, Fmt.rs(spent) + " spent", 13, Ui.SEMIBOLD), Ui.weight1());
        nums.addView(Ui.text(a, "of " + Fmt.rs(p.amount), 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        c.addView(nums);
        TextView st = Ui.text(a, p.status(all, now), 13.5f, col, Ui.BOLD);
        st.setPadding(0, Ui.dp(a, 8), 0, 0);
        c.addView(st);
        if (p.started(now) && !p.finished(now) && spent > 0) {
            long proj = p.projected(all, now);
            if (proj > p.amount) c.addView(Ui.sub(a, "At this pace you'll spend about " + Fmt.rs(proj) + " by " + Fmt.date(Fmt.addDays(p.end, -1))));
        }
        LinkedHashMap<String, Long> cats = p.byCategory(all);
        int i = 0;
        for (Map.Entry<String, Long> e : cats.entrySet()) {
            if (i++ >= 3) break;
            int pct = (int) Math.round(e.getValue() * 100.0 / Math.max(1, p.amount));
            View row = Rows.amountRow(a, Cats.emoji(e.getKey()), Cats.color(e.getKey()), e.getKey(), pct + "% of budget",
                    e.getValue(), e.getValue() / (float) Math.max(1, p.amount));
            row.setOnClickListener(v -> openBudget());
            c.addView(row);
        }
        c.addView(link("Open budget", v -> openBudget()));
        return c;
    }

    // ------------------------------------------------------------------ 2. balances

    private View balances(Store s) {
        List<Account> accs = new ArrayList<>();
        for (Account ac : s.accounts()) if (ac.balance >= 0 || ac.limit >= 0) accs.add(ac);
        if (accs.isEmpty()) return null;
        LinearLayout c = Ui.card(a);
        LinearLayout h = header(c, "Balances", H_BAL);
        long total = 0;
        for (Account ac : accs) if (!ac.card && !ac.hidden && ac.balance >= 0) total += ac.balance;
        TextView tt = Ui.text(a, Fmt.rs(total), 17, Ui.EXTRABOLD);
        h.addView(tt, 1);
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
        return c;
    }

    // ------------------------------------------------------------------ 3 + 4. this month / today: money in, money out

    private View period(String section, String title, String when, List<Txn> all, long from, long to,
                        long cmpFrom, long cmpTo, String cmpName) {
        LinearLayout c = Ui.card(a);
        header(c, title, section);
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
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 0, 6, 6, 0));
        tiles.addView(tile("↓  Money in", in, nIn, Ui.col(a, R.color.success), v -> Flow.show(a, from, to, true, when)),
                Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), a, 6, 6, 0, 0));
        c.addView(tiles);
        long net = in - out;
        LinearLayout r = Ui.hbox(a);
        r.setPadding(Ui.dp(a, 2), Ui.dp(a, 10), Ui.dp(a, 2), 0);
        r.addView(Ui.text(a, net >= 0 ? "Saved" : "Spent more than came in", 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD), Ui.weight1());
        r.addView(Ui.text(a, (net >= 0 ? "+" : "−") + Fmt.rs(Math.abs(net)), 14, net >= 0 ? Ui.col(a, R.color.success) : Ui.col(a, R.color.danger), Ui.BOLD));
        c.addView(r);
        long before = Ledger.spent(all, cmpFrom, cmpTo);
        if (before > 0 || out > 0) {
            String cmp;
            if (before == 0) cmp = "Nothing spent by " + cmpName;
            else {
                long d = out - before;
                int pct = (int) Math.round(Math.abs(d) * 100.0 / before);
                cmp = (d <= 0 ? "▼ " + Fmt.rs(-d) + " (" + pct + "%) less" : "▲ " + Fmt.rs(d) + " (" + pct + "%) more") + " spent than " + cmpName;
            }
            TextView ct = Ui.sub(a, cmp);
            ct.setPadding(Ui.dp(a, 2), Ui.dp(a, 4), 0, 0);
            c.addView(ct);
        }
        if (section.equals(H_MONTH) && out > 0) {
            TextView fc = Ui.sub(a, "Heading for about " + Fmt.rs(Ledger.forecast(all, System.currentTimeMillis())) + " spent by the end of the month");
            fc.setPadding(Ui.dp(a, 2), Ui.dp(a, 2), 0, 0);
            c.addView(fc);
            c.addView(link("Full breakdown in Insights", v -> a.show(MainActivity.INSIGHTS)));
        }
        return c;
    }

    private View tile(String label, long amount, int n, int color, View.OnClickListener l) {
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        t.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
        t.addView(Ui.text(a, label, 13, color, Ui.BOLD));
        t.addView(Ui.oneLine(Ui.text(a, Fmt.rs(amount), 20, Ui.EXTRABOLD)));
        t.addView(Ui.text(a, n + (n == 1 ? " payment" : " payments") + "  ›", 12, Ui.col(a, R.color.text2), Ui.REGULAR));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ 5. latest

    private View latest(Store s) {
        List<Txn> vis = s.visible();
        if (vis.isEmpty()) return null;
        LinearLayout c = Ui.card(a);
        header(c, "Latest", H_LATEST);
        for (int i = 0; i < Math.min(8, vis.size()); i++) c.addView(Rows.txn(a, vis.get(i), true));
        c.addView(link("See all activity", v -> a.show(MainActivity.ACTIVITY)));
        return c;
    }

    // ------------------------------------------------------------------ 6. coming up

    private View comingUp(Store s, List<Txn> all, long now) {
        Fmt.hide = false;
        LinearLayout up = Ui.card(a);
        header(up, "Coming up", null);
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
        return items > 0 ? up : null;
    }

    // ------------------------------------------------------------------ 7. needs a look

    private View needsLook(Store s) {
        Fmt.hide = false;
        List<Txn> look = Resolve.pending(a);
        if (look.isEmpty()) return null;
        LinearLayout c = Ui.card(a);
        header(c, "Needs a look (" + look.size() + ")", null);
        int gaps = 0;
        long gapTotal = 0;
        for (Txn t : look) if ((t.sources & Txn.SRC_GAP) != 0) { gaps++; gapTotal += t.amount; }
        TextView d = Ui.sub(a, gaps > 0 ? gaps + (gaps == 1 ? " amount" : " amounts") + " (" + Fmt.rs(gapTotal) + ") moved with no message. "
                + "Tap “Sort them out” — Money shows clues and you answer in one tap." : "Payments that looked unusual. Check they were you.");
        d.setPadding(0, 0, 0, Ui.dp(a, 8));
        c.addView(d);
        for (int i = 0; i < Math.min(4, look.size()); i++) {
            Txn t = look.get(i);
            View row = Rows.txn(a, t, true);
            row.setOnClickListener(v -> Resolve.one(a, t));
            c.addView(row);
        }
        TextView go = Ui.button(a, "Sort them out", true);
        go.setOnClickListener(v -> Resolve.start(a));
        LinearLayout r = Ui.hbox(a);
        r.setPadding(0, Ui.dp(a, 8), 0, 0);
        r.addView(go);
        c.addView(r);
        return c;
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
