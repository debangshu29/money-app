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

    /**
     * "Paid from": a row of chips — 💵 Cash (optional) and every bank account by name and last digits.
     * sel[0] holds the choice ("Cash", an account key, or null = not chosen yet).
     */
    static android.view.View fromPicker(Activity a, String[] sel, boolean withCash, Runnable onChange) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout row = Ui.hbox(a);
        hs.addView(row);
        List<String> keys = new ArrayList<>();
        if (withCash) keys.add(Cash.ACCOUNT);
        keys.addAll(bankAccounts(a));
        List<TextView> chips = new ArrayList<>();
        Runnable paint = () -> {
            for (int i = 0; i < chips.size(); i++) {
                boolean on = keys.get(i).equals(sel[0]);
                chips.get(i).setBackground(Ui.rippleRound(a, on ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 18));
                chips.get(i).setTextColor(on ? 0xFFFFFFFF : Ui.col(a, R.color.text));
            }
        };
        for (String k : keys) {
            long[] lv = Cash.ACCOUNT.equals(k) ? null : liveOf(a, k);
            String txt = (Cash.ACCOUNT.equals(k) ? "💵 Cash" : "🏦 " + label(a, k))
                    + (Cash.ACCOUNT.equals(k) ? (Cash.on(a) ? " · " + Fmt.plain(Cash.balance(a)) : "") : lv != null && lv[0] >= 0 ? " · " + Fmt.plain(lv[0]) : "");
            TextView ch = Ui.chip(a, txt, false);
            ch.setOnClickListener(v -> { sel[0] = k; paint.run(); if (onChange != null) onChange.run(); });
            chips.add(ch);
            row.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        }
        if (keys.size() == (withCash ? 1 : 0)) {
            TextView none = Ui.sub(a, "Bank accounts appear here after the first bank SMS");
            row.addView(none);
        }
        paint.run();
        return hs;
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
