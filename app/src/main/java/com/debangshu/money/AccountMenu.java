package com.debangshu.money;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Long-press an account: rename · correct the balance · merge · count in total · show payments · delete. */
public final class AccountMenu {

    private AccountMenu() {}

    public static void show(Activity a, Account ac) {
        String[] opts = {"✏️  Rename", "🔧  Correct the balance", "🔗  Same as another account (merge)",
                ac.hidden ? "➕  Count in total balance" : "➖  Don't count in total balance", "📄  Show its payments", "🗑️  Delete account"};
        new Ui.Popup(a).setTitle(ac.display()).setItems(opts, (d, w) -> {
            switch (w) {
                case 0: rename(a, ac); break;
                case 1: correct(a, ac); break;
                case 2: merge(a, ac); break;
                case 3: Store.get(a).setAccountHidden(ac, !ac.hidden); break;
                case 4:
                    a.startActivity(new android.content.Intent(a, MainActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("filterCat", "acc:" + ac.key).putExtra("filterLabel", ac.display()));
                    break;
                default: delete(a, ac); break;
            }
        }).show();
    }

    private static void rename(Activity a, Account ac) {
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
    }

    /** The balance shown is wrong: type the right one (from your bank app). */
    private static void correct(Activity a, Account ac) {
        boolean limit = ac.card && ac.limit >= 0;
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        TextView info = Ui.sub(a, (limit ? "Type the card's available limit" : "Type the balance") + " shown in your bank app right now. "
                + "Money uses it from now on, and the missed-message check starts fresh from here.");
        info.setPadding(0, 0, 0, Ui.dp(a, 8));
        b.addView(info);
        android.widget.EditText e = new android.widget.EditText(a);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setHint("₹");
        e.setTypeface(Ui.face(a, Ui.BOLD));
        e.setTextSize(22);
        b.addView(e);
        new Ui.Popup(a).setTitle("Correct " + ac.display()).setView(b)
                .setPositiveButton("Save", (x, y) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v < 0) { Ui.toast(a, "Enter the amount"); return; }
                    Store.get(a).correctBalance(ac.key, v, limit);
                    new Thread(() -> Engine.recheckGaps(a.getApplicationContext())).start();
                    Ui.toast(a, "Balance corrected");
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Two entries that are really one account (e.g. a debit card shown separately from its bank account). */
    private static void merge(Activity a, Account ac) {
        List<Account> others = new ArrayList<>();
        for (Account o : Store.get(a).accounts()) if (!o.key.equals(ac.key)) others.add(o);
        if (others.isEmpty()) { Ui.toast(a, "There's no other account to merge with"); return; }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) {
            Account o = others.get(i);
            names[i] = o.display() + (o.balance >= 0 ? "  ·  " + Fmt.plain(o.balance) : "");
        }
        new Ui.Popup(a).setTitle("“" + ac.display() + "” is the same as…").setItems(names, (d, w) -> {
            Account to = others.get(w);
            new Ui.Popup(a).setTitle("Merge into " + to.display() + "?")
                    .setMessage("All payments of " + ac.display() + " move to " + to.display() + ", and new messages for "
                            + ac.display() + " go there too.")
                    .setPositiveButton("Merge", (x, y) -> {
                        Store.get(a).mergeAccount(ac.key, to.key);
                        new Thread(() -> Engine.recheckGaps(a.getApplicationContext())).start();
                        Ui.toast(a, "Merged");
                    })
                    .setNegativeButton("Cancel", null).show();
        }).show();
    }

    private static void delete(Activity a, Account ac) {
        int n = 0;
        for (Txn t : Store.get(a).txns()) if (ac.key.equals(t.account) && t.visible() && t.kind != Txn.IGNORED && (t.sources & Txn.SRC_GAP) == 0) n++;
        final int count = n;
        new Ui.Popup(a).setTitle("Delete " + ac.display() + "?")
                .setMessage("The account disappears from Balances and won't come back from new messages "
                        + "(you can bring it back in Settings → Deleted accounts).\n\n"
                        + "It has " + count + (count == 1 ? " payment" : " payments") + ". Keep them in your history, or delete them too?")
                .setPositiveButton("Delete account", (x, y) -> {
                    Store.get(a).deleteAccount(ac.key, false);
                    Ui.toast(a, "Account deleted — its payments are kept");
                })
                .setNeutralButton("Delete with payments", (x, y) -> new Ui.Popup(a).setTitle("Delete " + count + " payments too?")
                        .setMessage("They're removed from all totals. Use this when the account itself was wrong (not a real account of yours).")
                        .setPositiveButton("Delete", (p1, p2) -> {
                            Store.get(a).deleteAccount(ac.key, true);
                            Ui.toast(a, "Account and its payments deleted");
                        })
                        .setNegativeButton("Cancel", null).show())
                .setNegativeButton("Cancel", null).show();
    }
}
