package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Payments you note yourself (UPI / bank) — kept until the bank's SMS or the app's notification joins them. */
final class Noted {

    private Noted() {}

    /** The account your UPI payments usually come from (the latest UPI payment's, else the busiest bank account). */
    static String defaultAccount(Context c) {
        String p = Store.get(c).prefs().getString("noteAccount", null);
        List<String> ok = bankAccounts(c);
        if (p != null && ok.contains(p)) return p;
        long cut = System.currentTimeMillis() - 60L * 86400000;
        Txn latest = null;
        Map<String, Integer> count = new HashMap<>();
        for (Txn t : Store.get(c).txns()) {
            if (t.credit || t.account.isEmpty() || !ok.contains(t.account) || t.time < cut || t.kind == Txn.SNAPSHOT) continue;
            count.put(t.account, (count.containsKey(t.account) ? count.get(t.account) : 0) + 1);
            if (!t.vpa.isEmpty() && (latest == null || t.time > latest.time)) latest = t;
        }
        if (latest != null) return latest.account;
        String best = null;
        int n = 0;
        for (Map.Entry<String, Integer> e : count.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        if (best != null) return best;
        return ok.isEmpty() ? "" : ok.get(0);
    }

    static List<String> bankAccounts(Context c) {
        List<String> out = new ArrayList<>();
        for (Account a : Store.get(c).accounts()) if (!a.card && !a.hidden && !Cash.ACCOUNT.equals(a.key)) out.add(a.key);
        return out;
    }

    static String label(Context c, String acc) {
        if (acc == null || acc.isEmpty()) return "your bank account";
        if (Cash.ACCOUNT.equals(acc)) return "Cash";
        List<Account> all = Store.get(c).accounts();
        for (Account a : all) if (a.key.equals(acc)) {
            String d = a.display();
            // two accounts with the same name (e.g. both renamed “Federal”) → add the last 4 digits
            int same = 0;
            for (Account o : all) if (o.display().equalsIgnoreCase(d)) same++;
            if (same > 1 && !d.matches(".*[0-9]{4}.*") && a.key.contains("••")) d += " ••" + a.key.substring(a.key.indexOf("••") + 2);
            return d;
        }
        return acc;
    }

    /** The choice you made last time ("Cash" or an account key). */
    static String lastFrom(Context c) { return Store.get(c).prefs().getString("noteFrom", Cash.ACCOUNT); }

    static void rememberFrom(Context c, String acc) { Store.get(c).prefs().edit().putString("noteFrom", acc).apply(); }

    /** "Union Bank ••1234" — always with the account number, like the Balance page. */
    static String fullLabel(Context c, String acc) {
        if (acc == null || acc.isEmpty() || Cash.ACCOUNT.equals(acc)) return label(c, acc);
        String d = label(c, acc), n = number(acc);
        return n.isEmpty() || d.contains(n) ? d : d + " ••" + n;
    }

    /** The account number part of a key ("HDFC ••1234" → "1234"). */
    static String number(String key) {
        int k = key == null ? -1 : key.indexOf("••");
        return k < 0 ? "" : key.substring(k + 2).trim();
    }

    /** Name without the number ("Union Bank ••1234" → "Union Bank"). */
    static String bankName(Context c, String key) {
        String d = label(c, key);
        int k = d.indexOf("••");
        return k < 0 ? d : d.substring(0, k).trim();
    }

    /**
     * "Paid from": your accounts as tap-able cards, two per row — 💵 Cash (optional) and every bank account
     * with its name, account number and balance. sel[0] holds the choice ("Cash", an account key, or null = not chosen yet).
     */
    static android.view.View fromPicker(Activity a, String[] sel, boolean withCash, Runnable onChange) {
        android.widget.LinearLayout grid = Ui.vbox(a);
        List<String> keys = new ArrayList<>();
        if (withCash) keys.add(Cash.ACCOUNT);
        keys.addAll(bankAccounts(a));
        List<android.widget.LinearLayout> cards = new ArrayList<>();
        List<TextView[]> texts = new ArrayList<>();
        Runnable paint = () -> {
            for (int i = 0; i < cards.size(); i++) {
                boolean on = keys.get(i).equals(sel[0]);
                cards.get(i).setBackground(Ui.rippleRound(a, on ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 16));
                texts.get(i)[0].setTextColor(on ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text));
                texts.get(i)[1].setTextColor(on ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text2));
            }
        };
        android.widget.LinearLayout row = null;
        for (int n = 0; n < keys.size(); n++) {
            String k = keys.get(n);
            boolean cash = Cash.ACCOUNT.equals(k);
            String top, bottom;
            if (cash) {
                top = "💵  Cash";
                bottom = Cash.on(a) ? Fmt.plain(Cash.balance(a)) + " in wallet" : "Cash spending";
            } else {
                long[] lv = liveOf(a, k);
                String num = number(k);
                top = "🏦  " + bankName(a, k);
                bottom = (num.isEmpty() ? "" : "••" + num) + (lv != null && lv[0] >= 0 ? (num.isEmpty() ? "" : "  ·  ") + Fmt.plain(lv[0]) : "");
                if (bottom.isEmpty()) bottom = "Bank account";
            }
            android.widget.LinearLayout card = Ui.vbox(a);
            int p = Ui.dp(a, 12);
            card.setPadding(p, Ui.dp(a, 10), p, Ui.dp(a, 10));
            card.setMinimumHeight(Ui.dp(a, 60));
            TextView t1 = Ui.text(a, top, 14, Ui.col(a, R.color.text), Ui.BOLD);
            t1.setSingleLine(true);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView t2 = Ui.text(a, bottom, 12, Ui.col(a, R.color.text2), Ui.REGULAR);
            t2.setSingleLine(true);
            card.addView(t1);
            card.addView(t2);
            card.setClickable(true);
            card.setOnClickListener(v -> { sel[0] = k; paint.run(); if (onChange != null) onChange.run(); });
            cards.add(card);
            texts.add(new TextView[]{t1, t2});
            if (n % 2 == 0) {
                row = Ui.hbox(a);
                grid.addView(row, Ui.margins(Ui.lp(-1, -2), a, 0, n == 0 ? 0 : 8, 0, 0));
            }
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(card, Ui.margins(lp, a, n % 2 == 0 ? 0 : 8, 0, 0, 0));
        }
        if (keys.size() % 2 == 1 && row != null) row.addView(new android.view.View(a), new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        if (keys.size() == (withCash ? 1 : 0)) {
            grid.addView(Ui.sub(a, "Bank accounts appear here after the first bank SMS"), Ui.margins(Ui.lp(-1, -2), a, 0, 8, 0, 0));
        }
        paint.run();
        return grid;
    }

    /** Move a payment you added to another account (or cash) — balances and the cash wallet follow. */
    static void move(Context c, Txn t, String to) {
        String was = t.account;
        if (to == null || to.equals(was)) return;
        if (t.kind != Txn.IGNORED && !t.credit && Cash.on(c)) {
            if (Cash.ACCOUNT.equals(was)) Cash.refund(c, t.amount);
            if (Cash.ACCOUNT.equals(to)) Cash.refund(c, -t.amount);
        }
        t.account = to;
        t.sources = Cash.ACCOUNT.equals(to) ? Txn.SRC_MANUAL : Txn.SRC_NOTE;
        Store.get(c).update(t);
        rememberFrom(c, to);
    }

    static long[] liveOf(Context c, String key) {
        for (Account x : Store.get(c).accounts()) if (x.key.equals(key)) return Store.get(c).live(x);
        return null;
    }

    /** Accounts matching what you said / typed: a bank ("federal", "union") and maybe the last digits. */
    static List<String> match(Context c, String bank, String last4) {
        List<String> out = new ArrayList<>();
        for (String k : bankAccounts(c)) {
            boolean okBank = bank == null || bank.isEmpty() || k.toLowerCase(java.util.Locale.ROOT).startsWith(bank.toLowerCase(java.util.Locale.ROOT))
                    || label(c, k).toLowerCase(java.util.Locale.ROOT).contains(bank.toLowerCase(java.util.Locale.ROOT));
            boolean okDigits = last4 == null || last4.isEmpty() || k.endsWith(last4);
            if (okBank && okDigits) out.add(k);
        }
        return out;
    }

    /** Save from the "Paid from" choice: cash goes to the cash wallet, anything else to that bank account. */
    static Txn save(Context c, String from, long paise, String name, String category, String note) {
        rememberFrom(c, from);
        if (Cash.ACCOUNT.equals(from)) {
            Txn t = Cash.spend(c, paise, name, category);
            if (note != null && !note.isEmpty()) { t.note = note; Store.get(c).update(t); }
            return t;
        }
        return add(c, paise, name, category, note, "", from);
    }

    /** A "From: HDFC ••1234 ▾" chip that lets you pick another account. */
    static TextView accountChip(Activity a, String[] acc) {
        TextView ch = Ui.chip(a, "From: " + label(a, acc[0]) + "  ▾", false);
        ch.setOnClickListener(v -> {
            List<String> ks = bankAccounts(a);
            if (ks.isEmpty()) { Ui.toast(a, "No bank account yet — it appears after the first bank SMS"); return; }
            String[] names = new String[ks.size()];
            for (int i = 0; i < ks.size(); i++) names[i] = label(a, ks.get(i));
            new Ui.Popup(a).setTitle("Paid from").setItems(names, (d, w) -> {
                acc[0] = ks.get(w);
                Store.get(a).prefs().edit().putString("noteAccount", acc[0]).apply();
                ch.setText("From: " + names[w] + "  ▾");
            }).show();
        });
        return ch;
    }

    /** Save a UPI / bank payment you noted. */
    static Txn add(Context c, long paise, String name, String category, String note, String vpa, String account) {
        Txn t = new Txn();
        t.time = System.currentTimeMillis();
        t.amount = paise;
        t.merchant = name == null ? "" : name.trim();
        t.userMerchant = !t.merchant.isEmpty();
        t.category = category == null ? "" : category;
        t.userCategory = !t.category.isEmpty();
        t.note = note == null ? "" : note.trim();
        t.vpa = vpa == null ? "" : vpa;
        t.account = account == null ? "" : account;
        t.raw = "";
        return Engine.note(c, t);
    }
}
