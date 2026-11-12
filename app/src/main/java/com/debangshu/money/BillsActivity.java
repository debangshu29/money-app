package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plan → Bills & subscriptions: what's due soon, and every repeating payment Money found (with price rises). */
public class BillsActivity extends Activity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "Bills & subscriptions"));
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

    /** "Still using it?" — keep (asked again in 3 months) or mark to cancel. */
    private void subSheet(Ledger.Recurring r) {
        long year = r.amount * 365 / Math.max(1, r.period);
        new Ui.Popup(this).setTitle(r.name)
                .setMessage(Fmt.plain(r.amount) + " " + r.periodName().toLowerCase(java.util.Locale.ROOT) + " = " + Fmt.plain(year)
                        + " a year.\nStill using it?")
                .setPositiveButton("Yes, keep it", (d, w) -> setSub(r.key, false))
                .setNeutralButton("Remind me to cancel", (d, w) -> setSub(r.key, true))
                .setNegativeButton("Close", null).show();
    }

    private void setSub(String key, boolean cancel) {
        Store s = Store.get(this);
        org.json.JSONObject subs = s.obj("subs");
        try { subs.put(key, new org.json.JSONObject().put("cancel", cancel).put("keep", cancel ? 0 : System.currentTimeMillis() + 90L * 86400000)); }
        catch (Exception ignored) { }
        s.putObj("subs", subs);
        build();
    }

    private void build() {
        box.removeAllViews();
        Store s = Store.get(this);
        long now = System.currentTimeMillis();
        List<Ledger.Recurring> rec = Ledger.recurring(s.txns(), now);

        // ---- due soon (next 30 days), in date order
        List<Object[]> due = new ArrayList<>();
        for (Store.Bill b : s.bills()) {
            if (b.paid || b.dueDate < now - 86400000 || b.dueDate > now + 30L * 86400000 || Alerts.billPaid(s, b)) continue;
            due.add(new Object[]{b.dueDate, "💳", 0xFF3B82F6, (b.account.isEmpty() ? "Card" : b.account) + " bill",
                    "Due " + Fmt.rel(b.dueDate) + " · " + Fmt.date(b.dueDate) + (b.minDue > 0 ? " · min " + Fmt.rs(b.minDue) : ""), Math.max(0, b.due)});
        }
        for (Ledger.Recurring r : rec) {
            if (r.next < Fmt.startOfDay(now) || r.next > now + 30L * 86400000) continue;
            due.add(new Object[]{r.next, Cats.emoji(r.category), Cats.color(r.category), r.name,
                    "Expected " + Fmt.rel(r.next) + " · " + Fmt.date(r.next), r.amount});
        }
        Collections.sort(due, (x, y) -> Long.compare((Long) x[0], (Long) y[0]));
        LinearLayout dc = Ui.card(this);
        dc.addView(Ui.sectionTitle(this, "Due in the next 30 days"));
        if (due.isEmpty()) dc.addView(Ui.sub(this, "Nothing expected. Card bills come from your card's statement SMS; the rest from payments that repeat."));
        for (Object[] d : due) dc.addView(Rows.amountRow(this, (String) d[1], (Integer) d[2], (String) d[3], (String) d[4], (Long) d[5], -1));
        box.addView(dc, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));

        // ---- repeating payments
        LinearLayout rc = Ui.card(this);
        long monthly = 0;
        for (Ledger.Recurring r : rec) monthly += r.amount * 30 / Math.max(1, r.period);
        LinearLayout rh = Ui.hbox(this);
        rh.addView(Ui.sectionTitle(this, "Repeating payments"), Ui.weight1());
        if (!rec.isEmpty()) {
            TextView m = Ui.text(this, "≈ " + Fmt.rs(monthly) + "/mo", 14, Ui.col(this, R.color.text2), Ui.BOLD);
            m.setPadding(0, 0, 0, Ui.dp(this, 8));
            rh.addView(m);
        }
        rc.addView(rh);
        if (rec.isEmpty()) rc.addView(Ui.sub(this, "Subscriptions, EMIs, rent and bills show up here by themselves after they repeat."));
        org.json.JSONObject subs = s.obj("subs");
        long cancelSum = 0;
        int ask = 0;
        for (Ledger.Recurring r : rec) {
            org.json.JSONObject st = subs.optJSONObject(r.key);
            boolean cancel = st != null && st.optBoolean("cancel");
            boolean kept = st != null && st.optLong("keep") > now;
            if (cancel) cancelSum += r.amount * 30 / Math.max(1, r.period);
            boolean isSub = Cats.SUBS.equals(r.category) || Cats.FUN.equals(r.category);
            String sub = r.periodName() + " · next " + Fmt.day(r.next) + " · " + Fmt.rs(r.amount * 365 / Math.max(1, r.period)) + "/year";
            if (r.lastAmount > r.prevAmount * 1.05 && r.prevAmount > 0) sub += "\n▲ Price went up: " + Fmt.rs(r.prevAmount) + " → " + Fmt.rs(r.lastAmount);
            if (cancel) sub += "\n🗑️ You want to cancel this";
            else if (isSub && !kept) { sub += "\n🤔 Still using it? Tap"; ask++; }
            android.view.View row = Rows.amountRow(this, Cats.emoji(r.category), Cats.color(r.category), r.name, sub, r.lastAmount, -1);
            row.setOnClickListener(v -> subSheet(r));
            rc.addView(row);
        }
        box.addView(rc, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        if (cancelSum > 0 || ask > 0) {
            LinearLayout tip = Ui.card(this);
            tip.setBackground(Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 22)));
            tip.addView(Ui.text(this, "🧹 Subscription cleaner", 15, Ui.BOLD));
            TextView tt = Ui.sub(this, (cancelSum > 0 ? "Cancelling what you marked saves about " + Fmt.rs(cancelSum) + " a month ("
                    + Fmt.rs(cancelSum * 12) + " a year). Cancel inside each app (Play Store → Payments & subscriptions for most). " : "")
                    + (ask > 0 ? ask + (ask == 1 ? " subscription is" : " subscriptions are") + " waiting for “still using it?”." : ""));
            tt.setPadding(0, Ui.dp(this, 4), 0, 0);
            tip.addView(tt);
            box.addView(tip, 0, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));
        }
    }
}
