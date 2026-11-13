package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * People: split a bill, lend, borrow. "Rahul owes you ₹300 (dinner)".
 * When Rahul's UPI payment comes in, it's ticked off by itself (and doesn't count as income).
 * A friendly WhatsApp reminder is one tap.
 */
public class People extends Activity {

    static final String KEY = "debts";

    // ------------------------------------------------------------------ data

    /** dir "owed" = they owe you, "owe" = you owe them. Amounts in paise. */
    static JSONObject add(Context c, String who, long amt, String dir, String note, String txnId, String vpa) {
        Store s = Store.get(c);
        JSONArray a = s.list(KEY);
        JSONObject d = new JSONObject();
        try {
            d.put("id", java.util.UUID.randomUUID().toString()).put("who", who.trim()).put("amt", amt).put("dir", dir)
                    .put("note", note == null ? "" : note).put("t", System.currentTimeMillis()).put("txn", txnId == null ? "" : txnId)
                    .put("paid", 0L).put("vpa", vpa == null ? "" : vpa);
            a.put(d);
        } catch (Exception ignored) { }
        s.putList(KEY, a);
        return d;
    }

    static List<JSONObject> all(Context c) {
        List<JSONObject> l = new ArrayList<>();
        JSONArray a = Store.get(c).list(KEY);
        for (int i = 0; i < a.length(); i++) l.add(a.optJSONObject(i));
        return l;
    }

    static long left(JSONObject d) { return Math.max(0, d.optLong("amt") - d.optLong("paid")); }

    static boolean open(JSONObject d) { return left(d) > 0; }

    /** person → net (+ they owe you, − you owe them) for open items. */
    static LinkedHashMap<String, Long> balances(Context c) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (JSONObject d : all(c)) {
            if (!open(d)) continue;
            String w = d.optString("who");
            long v = left(d) * ("owed".equals(d.optString("dir")) ? 1 : -1);
            m.put(w, (m.containsKey(w) ? m.get(w) : 0) + v);
        }
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : m.entrySet()) if (e.getValue() != 0) out.put(e.getKey(), e.getValue());
        return out;
    }

    static long[] totals(Context c) {
        long get = 0, give = 0;
        for (long v : balances(c).values()) { if (v > 0) get += v; else give -= v; }
        return new long[]{get, give};
    }

    static void pay(Context c, String id, long amount) {
        Store s = Store.get(c);
        JSONArray a = s.list(KEY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            if (d != null && id.equals(d.optString("id"))) {
                try { d.put("paid", Math.min(d.optLong("amt"), d.optLong("paid") + amount)); } catch (Exception ignored) { }
            }
        }
        s.putList(KEY, a);
    }

    static void remove(Context c, String id) {
        Store s = Store.get(c);
        JSONArray a = s.list(KEY), b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.optJSONObject(i).optString("id"))) b.put(a.optJSONObject(i));
        s.putList(KEY, b);
    }

    static boolean sameWho(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase(Locale.ROOT).trim(), y = b.toLowerCase(Locale.ROOT).trim();
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y) || x.startsWith(y + " ") || y.startsWith(x + " ")) return true;
        String fx = x.split(" ")[0], fy = y.split(" ")[0];
        return fx.length() >= 3 && fx.equals(fy);
    }

    /**
     * A new payment came in: is it someone paying you back (or you paying them back)?
     * Ticks the matching items off and marks the payment as "paid back" (not income / spending). Returns true if it matched.
     */
    static boolean autoMatch(Context c, Txn t) {
        if (t.kind != Txn.NORMAL) return false;
        String want = t.credit ? "owed" : "owe";
        Store s = Store.get(c);
        JSONArray a = s.list(KEY);
        long amount = t.amount;
        List<String> who = new ArrayList<>();
        for (int i = 0; i < a.length() && amount > 0; i++) {
            JSONObject d = a.optJSONObject(i);
            if (d == null || !open(d) || !want.equals(d.optString("dir"))) continue;
            boolean match = (!d.optString("vpa").isEmpty() && d.optString("vpa").equalsIgnoreCase(t.vpa)) || sameWho(d.optString("who"), t.merchant);
            if (!match || d.optLong("t") > t.time + 60000) continue;
            long l = left(d);
            if (amount + 100 < l && who.isEmpty() && amount < l / 4) continue;   // tiny unrelated payment
            long use = Math.min(l, amount);
            try { d.put("paid", d.optLong("paid") + use); } catch (Exception ignored) { }
            amount -= use;
            if (!who.contains(d.optString("who"))) who.add(d.optString("who"));
        }
        if (who.isEmpty()) return false;
        s.putList(KEY, a);
        t.kind = Txn.TRANSFER;
        t.category = t.credit ? Cats.REFUND : Cats.PEOPLE;
        t.userCategory = true;
        if (t.note.isEmpty()) t.note = t.credit ? "Paid back by " + who.get(0) : "You paid " + who.get(0) + " back";
        s.update(t);
        Alerts.post(c, Alerts.CH_ALERTS, 600 + Math.abs(who.get(0).hashCode() % 90),
                t.credit ? who.get(0) + " paid you back ✓" : "You paid " + who.get(0) + " back ✓",
                Fmt.plain(t.amount) + " · ticked off in People" + (amount > 0 ? " (" + Fmt.plain(amount) + " extra)" : ""), t.id);
        return true;
    }

    // ------------------------------------------------------------------ from a payment: split / lend / borrow

    static void fromPayment(Activity a, Txn t) {
        if (t.credit) {
            new Ui.Popup(a).setTitle("Money from " + (t.merchant.isEmpty() ? "someone" : t.merchant))
                    .setItems(new String[]{"🤝  They lent it to me (I owe them)", "↩️  They paid me back"}, (d, w) -> {
                        if (w == 0) {
                            add(a, t.merchant.isEmpty() ? "Someone" : t.merchant, t.amount, "owe", "Borrowed", t.id, t.vpa);
                            t.kind = Txn.TRANSFER; t.category = Cats.PEOPLE; t.userCategory = true;
                            Store.get(a).update(t);
                            Ui.toast(a, "Added to People: you owe " + Fmt.plain(t.amount));
                        } else {
                            if (!autoMatch(a, t)) Ui.toast(a, "No open amount from " + t.merchant + " in People");
                        }
                    }).show();
            return;
        }
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        TextView info = Ui.sub(a, "Who shares this " + Fmt.plain(t.amount) + "? Separate names with commas.");
        info.setPadding(0, 0, 0, Ui.dp(a, 6));
        b.addView(info);
        EditText names = new EditText(a);
        names.setHint(Lang.t("e.g. Rahul, Amit, Priya"));
        names.setTypeface(Ui.face(a, Ui.REGULAR));
        names.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (Cats.PEOPLE.equals(t.category) && !t.unnamed()) names.setText(t.merchant);
        b.addView(names);
        CheckBox me = new CheckBox(a);
        me.setText("Include me (split equally)");
        me.setChecked(!Cats.PEOPLE.equals(t.category));
        me.setTypeface(Ui.face(a, Ui.REGULAR));
        b.addView(me);
        EditText note = new EditText(a);
        note.setHint(Lang.t("What for? e.g. dinner"));
        note.setTypeface(Ui.face(a, Ui.REGULAR));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        b.addView(note);
        new Ui.Popup(a).setTitle("Split or lend").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    List<String> list = new ArrayList<>();
                    for (String n : names.getText().toString().split(",")) if (!n.trim().isEmpty()) list.add(n.trim());
                    if (list.isEmpty()) { Ui.toast(a, "Add at least one name"); return; }
                    int shares = list.size() + (me.isChecked() ? 1 : 0);
                    long each = t.amount / shares;
                    String nt = note.getText().toString().trim();
                    for (String n : list) add(a, n, each, "owed", nt.isEmpty() ? (t.merchant.isEmpty() ? "Shared payment" : t.merchant) : nt, t.id,
                            list.size() == 1 && !me.isChecked() ? t.vpa : "");
                    if (t.note.isEmpty()) t.note = (me.isChecked() ? "Split with " : "Lent to ") + BudgetActivity.join(list);
                    if (!me.isChecked() && list.size() == 1) { t.kind = Txn.TRANSFER; t.category = Cats.PEOPLE; t.userCategory = true; }
                    Store.get(a).update(t);
                    Ui.toast(a, list.size() == 1 ? list.get(0) + " owes you " + Fmt.plain(each) : "Each owes you " + Fmt.plain(each));
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ the screen

    private LinearLayout box;
    private final Runnable onChange = this::build;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, "People"));
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
        long[] tot = totals(this);
        LinearLayout hero = Ui.card(this);
        LinearLayout two = Ui.hbox(this);
        two.addView(stat("You'll get", tot[0], Ui.col(this, R.color.success)), Ui.weight1());
        two.addView(stat("You owe", tot[1], Ui.col(this, R.color.danger)), Ui.weight1());
        hero.addView(two);
        TextView how = Ui.sub(this, "Split or lend from any payment (tap it → 👥 Split / lend). When they pay you back by UPI, it's ticked off here by itself.");
        how.setPadding(0, Ui.dp(this, 10), 0, 0);
        hero.addView(how);
        box.addView(hero, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));

        LinkedHashMap<String, Long> bal = balances(this);
        LinearLayout c = Ui.card(this);
        c.addView(Ui.sectionTitle(this, "Still to settle"));
        if (bal.isEmpty()) c.addView(Ui.sub(this, "All settled 🎉"));
        for (Map.Entry<String, Long> e : bal.entrySet()) {
            long v = e.getValue();
            View row = Rows.amountRow(this, "👤", v > 0 ? 0xFF16A34A : 0xFFEF4444, e.getKey(), v > 0 ? "owes you" : "you owe", Math.abs(v), -1);
            row.setOnClickListener(x -> person(e.getKey()));
            c.addView(row);
        }
        TextView add = Ui.button(this, "＋  Add", false);
        add.setOnClickListener(v -> addManual());
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 10), 0, 0);
        r.addView(add);
        c.addView(r);
        box.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        List<JSONObject> done = new ArrayList<>();
        for (JSONObject d : all(this)) if (!open(d)) done.add(d);
        if (!done.isEmpty()) {
            LinearLayout h = Ui.card(this);
            h.addView(Ui.sectionTitle(this, "Settled"));
            for (int i = done.size() - 1, n = 0; i >= 0 && n < 10; i--, n++) {
                JSONObject d = done.get(i);
                h.addView(Rows.amountRow(this, "✓", 0xFF94A3B8, d.optString("who") + " · " + d.optString("note"),
                        Fmt.date(d.optLong("t")), d.optLong("amt"), -1));
            }
            box.addView(h);
        }
    }

    private View stat(String label, long v, int color) {
        LinearLayout c = Ui.vbox(this);
        c.addView(Ui.text(this, label, 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        c.addView(Ui.text(this, Fmt.rs(v), 24, v > 0 ? color : Ui.col(this, R.color.text), Ui.EXTRABOLD));
        return c;
    }

    private void person(String who) {
        LinearLayout b = Ui.vbox(this);
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        long net = 0;
        for (JSONObject d : all(this)) {
            if (!open(d) || !who.equals(d.optString("who"))) continue;
            boolean owed = "owed".equals(d.optString("dir"));
            net += owed ? left(d) : -left(d);
            LinearLayout row = Ui.hbox(this);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            LinearLayout col = Ui.vbox(this);
            col.addView(Ui.text(this, (owed ? "Owes you " : "You owe ") + Fmt.plain(left(d)), 15, Ui.BOLD));
            col.addView(Ui.sub(this, d.optString("note") + " · " + Fmt.date(d.optLong("t"))
                    + (d.optLong("paid") > 0 ? " · " + Fmt.plain(d.optLong("paid")) + " paid" : "")));
            row.addView(col, Ui.weight1());
            TextView paid = Ui.chip(this, "Paid ✓", false);
            paid.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
            String id = d.optString("id");
            long l = left(d);
            paid.setOnClickListener(v -> { pay(this, id, l); Ui.toast(this, "Marked as paid"); });
            row.addView(paid);
            row.setOnLongClickListener(v -> {
                new Ui.Popup(this).setTitle("Remove this?").setPositiveButton("Remove", (x, y) -> remove(this, id))
                        .setNegativeButton("Cancel", null).show();
                return true;
            });
            b.addView(row);
        }
        final long fnet = net;
        ScrollView sv = new ScrollView(this);
        sv.addView(b);
        Ui.Popup pp = new Ui.Popup(this);
        pp.sheet().setTitle("👤 " + who).setView(Ui.capHeight(sv, 0.5f)).setNegativeButton("Close", null);
        if (fnet > 0) pp.setPositiveButton("Remind on WhatsApp", (d, w) -> remind(who, fnet));
        pp.show();
    }

    private void remind(String who, long amount) {
        String upi = Store.get(this).prefs().getString("my_upi", "");
        if (upi.isEmpty()) {
            LinearLayout b = Ui.vbox(this);
            int p = Ui.dp(this, 24);
            b.setPadding(p, 0, p, 0);
            TextView t = Ui.sub(this, "Optional: your UPI ID, so they can pay you in one tap (saved for next time).");
            t.setPadding(0, 0, 0, Ui.dp(this, 6));
            b.addView(t);
            EditText e = new EditText(this);
            e.setHint(Lang.t("e.g. debangshu@okaxis"));
            e.setTypeface(Ui.face(this, Ui.REGULAR));
            b.addView(e);
            new Ui.Popup(this).setTitle("Your UPI ID").setView(b)
                    .setPositiveButton("Continue", (d, w) -> {
                        String v = e.getText().toString().trim();
                        Store.get(this).prefs().edit().putString("my_upi", v.isEmpty() ? " " : v).apply();
                        remind(who, amount);
                    })
                    .setNegativeButton("Skip", (d, w) -> { Store.get(this).prefs().edit().putString("my_upi", " ").apply(); remind(who, amount); })
                    .show();
            return;
        }
        String msg = "Hi " + who.split(" ")[0] + "! Just a gentle reminder about " + Fmt.plain(amount) + " 🙂"
                + (upi.trim().isEmpty() ? "" : "\nUPI: " + upi.trim()) + "\nThanks!";
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg);
        i.setPackage("com.whatsapp");
        try { startActivity(i); }
        catch (Exception ex) { i.setPackage(null); startActivity(Intent.createChooser(i, "Send reminder")); }
    }

    private void addManual() {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        EditText who = new EditText(this);
        who.setHint(Lang.t("Name"));
        who.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText amt = new EditText(this);
        amt.setHint(Lang.t("Amount (₹)"));
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = new EditText(this);
        note.setHint(Lang.t("What for? (optional)"));
        for (EditText e : new EditText[]{who, amt, note}) { e.setTypeface(Ui.face(this, Ui.REGULAR)); b.addView(e); }
        new Ui.Popup(this).setTitle("Add").setView(b)
                .setPositiveButton("They owe me", (d, w) -> manual(who, amt, note, "owed"))
                .setNeutralButton("I owe them", (d, w) -> manual(who, amt, note, "owe"))
                .setNegativeButton("Cancel", null).show();
    }

    private void manual(EditText who, EditText amt, EditText note, String dir) {
        long v = SmsParser.paise(amt.getText().toString().trim());
        String w = who.getText().toString().trim();
        if (v <= 0 || w.isEmpty()) { Ui.toast(this, "Add a name and an amount"); return; }
        add(this, w, v, dir, note.getText().toString().trim(), "", "");
    }
}
