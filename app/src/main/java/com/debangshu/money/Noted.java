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
        for (Account a : Store.get(c).accounts()) if (a.key.equals(acc)) return a.display();
        return acc;
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
