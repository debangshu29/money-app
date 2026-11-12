package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Plan → Accounts: every account Money found, the total, and (tap / long-press) rename · correct · merge · delete. */
public class AccountsActivity extends Activity {

    private LinearLayout box;
    private final Runnable onChange = this::build;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "Accounts"));
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        Store.get(this).listen(onChange);
    }

    @Override
    protected void onDestroy() { Store.get(this).unlisten(onChange); super.onDestroy(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        build();
    }

    private void build() {
        box.removeAllViews();
        Store s = Store.get(this);
        List<Account> accs = s.accounts();
        long total = 0;
        for (Account ac : accs) if (!ac.card && !ac.hidden && ac.balance >= 0) total += ac.balance;
        if (Cash.on(this)) total += Cash.balance(this);

        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "Total balance", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(total), 32, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, "Bank accounts" + (Cash.on(this) ? " + cash" : "") + ", from the last message that showed a balance"));
        box.addView(hero, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));

        LinearLayout c = Ui.card(this);
        if (accs.isEmpty() && !Cash.on(this)) c.addView(Ui.sub(this, "No accounts yet — they appear after a bank SMS that shows a balance."));
        for (Account ac : accs) {
            boolean card = ac.card && ac.limit >= 0;
            long v = card ? ac.limit : ac.balance;
            long when = card ? ac.limitTime : ac.balanceTime;
            String sub = (card ? "Card limit left · " : "") + (ac.hidden ? "not in total · " : "")
                    + (v < 0 ? "no balance seen yet" : "updated " + HomePage.ago(when));
            View row = Rows.amountRow(this, ac.card ? "💳" : ac.wallet ? "👛" : "🏦", ac.card ? 0xFF8B5CF6 : 0xFF0E9F6E, ac.display(), sub, Math.max(0, v), -1);
            row.setOnClickListener(x -> AccountMenu.show(this, ac));
            row.setOnLongClickListener(x -> { AccountMenu.show(this, ac); return true; });
            c.addView(row);
        }
        if (Cash.on(this)) {
            View row = Rows.amountRow(this, "💵", 0xFF65A30D, "Cash wallet", "tap to count or note a cash spend", Cash.balance(this), -1);
            row.setOnClickListener(x -> Cash.sheet(this));
            c.addView(row);
        }
        box.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        TextView tip = Ui.sub(this, "Tap an account to rename it, correct a wrong balance, merge two that are the same, or delete it.");
        tip.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 8));
        box.addView(tip);
        List<String> del = s.deletedAccounts();
        if (!del.isEmpty()) {
            TextView d = Ui.text(this, "Deleted accounts (" + del.size() + ")  →", 14, Ui.col(this, R.color.accent), Ui.BOLD);
            d.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
            d.setOnClickListener(v -> new Ui.Popup(this).setTitle("Bring back").setItems(del.toArray(new String[0]), (x, w) -> {
                Store.get(this).restoreAccount(del.get(w));
                new Thread(() -> Engine.recheckGaps(getApplicationContext())).start();
            }).show());
            box.addView(d);
        }
    }
}
