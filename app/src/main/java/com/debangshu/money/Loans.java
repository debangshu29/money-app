package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loans & EMIs: found from EMI payments and lender messages. Add what's left + the interest rate once, and Money shows
 * months left, interest still to pay, and what a prepayment would save.
 */
public class Loans extends Screen {

    static final String KEY = "loans";
    private static final long DAY = 86400000L;

    static final class Loan {
        String key, name = "";
        long emi, last, first, due = -1, os = -1, osT;
        int count;
        double rate = -1;
        boolean hidden;
        /** What's left today (estimated from the last known figure and the EMIs paid since). */
        long now(List<Txn> all) {
            if (os <= 0) return -1;
            if (rate <= 0) return os;
            int k = 0;
            for (Txn t : all) if (key.startsWith("t:") && isEmi(t) && ("t:" + Cats.key(t)).equals(key) && t.time > osT + DAY) k++;
            return Notices.after(os, rate, emi, k);
        }
    }

    static boolean isEmi(Txn t) { return !t.credit && t.kind == Txn.NORMAL && Cats.EMI.equals(t.category); }

    static JSONObject data(Context c) { return Store.get(c).obj(KEY); }

    static void put(Context c, String key, JSONObject o) {
        JSONObject d = data(c);
        try { d.put(key, o); } catch (Exception ignored) { }
        Store.get(c).putObj(KEY, d);
    }

    static List<Loan> list(Context c, boolean withHidden) {
        List<Txn> all = Store.get(c).txns();
        long from = System.currentTimeMillis() - 400 * DAY;
        Map<String, Loan> m = new LinkedHashMap<>();
        for (Txn t : all) {
            if (!isEmi(t) || t.time < from) continue;
            String k = "t:" + Cats.key(t);
            Loan l = m.get(k);
            if (l == null) { l = new Loan(); l.key = k; l.first = t.time; m.put(k, l); }
            l.count++;
            if (t.time >= l.last) { l.last = t.time; l.emi = t.amount; l.name = t.merchant.isEmpty() ? "Loan" : t.merchant; }
            l.first = Math.min(l.first, t.time);
        }
        JSONObject d = data(c);
        for (Iterator<String> it = d.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject o = d.optJSONObject(k);
            if (o == null) continue;
            Loan l = m.get(k);
            if (l == null && !k.startsWith("t:")) {
                long e = o.optLong("emi");
                for (Loan x : m.values()) if (e > 0 && Math.abs(x.emi - e) <= Math.max(100, e / 100)) { l = x; break; }
            }
            if (l == null) {
                if (k.startsWith("t:")) continue;    // payments no longer in range
                l = new Loan();
                l.key = k;
                m.put(k, l);
            }
            if (!o.optString("name").isEmpty() && (o.optBoolean("named") || l.name.isEmpty() || l.name.equals("Loan"))) l.name = o.optString("name");
            if (o.optLong("emi") > 0 && l.emi <= 0) l.emi = o.optLong("emi");
            if (o.optLong("os", -1) > 0 && o.optLong("osT") >= l.osT) { l.os = o.optLong("os"); l.osT = o.optLong("osT"); }
            if (o.optDouble("rate", -1) > 0) l.rate = o.optDouble("rate");
            if (o.optLong("due", -1) > 0) l.due = Math.max(l.due, o.optLong("due"));
            if (o.optBoolean("hidden")) l.hidden = true;
            if (l.last == 0) l.last = o.optLong("last");
        }
        List<Loan> out = new ArrayList<>();
        for (Loan l : m.values()) if (withHidden || !l.hidden) out.add(l);
        java.util.Collections.sort(out, (x, y) -> Long.compare(y.emi, x.emi));
        return out;
    }

    /** From an EMI / loan message (paid, due, outstanding). */
    static void onEmi(Context c, Notices.Emi e, Txn saved, long time) {
        Store s = Store.get(c);
        String key;
        if (saved != null && !saved.credit && saved.kind == Txn.NORMAL) {
            if (!saved.userCategory && !Cats.EMI.equals(saved.category)) saved.category = Cats.EMI;
            if (saved.merchant.isEmpty() || saved.unnamed()) saved.merchant = e.name();
            s.update(saved);
            key = "t:" + Cats.key(saved);
        } else {
            key = null;
            for (Loan l : list(c, true)) {
                boolean digits = !e.loan.isEmpty() && l.name.contains(e.loan);
                boolean amt = e.amount > 0 && Math.abs(l.emi - e.amount) <= Math.max(100, e.amount / 100);
                if (digits || amt) { key = l.key; break; }
            }
            if (key == null) key = "n:" + e.name().toLowerCase(Locale.ROOT);
        }
        JSONObject o = data(c).optJSONObject(key);
        if (o == null) o = new JSONObject();
        try {
            if (o.optString("name").isEmpty()) o.put("name", e.name());
            if (e.amount > 0 && (e.paid || o.optLong("emi") <= 0)) o.put("emi", e.amount);
            if (e.outstanding > 0 && time >= o.optLong("osT")) o.put("os", e.outstanding).put("osT", time);
            if (e.due > 0 && e.due != o.optLong("due")) { o.put("due", e.due); o.remove("dueN"); }
            if (e.paid) o.put("last", Math.max(time, o.optLong("last")));
        } catch (Exception ignored) { }
        put(c, key, o);
    }

    /** Background: "EMI due in 2 days — keep the balance". */
    static void check(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_REMIND)) return;
        long now = System.currentTimeMillis();
        for (Loan l : list(c, false)) {
            long due = l.due;
            if (due < now - DAY && l.last > 0 && l.count >= 2) {   // guess from the usual day
                Calendar k = Fmt.cal(l.last);
                k.add(Calendar.MONTH, 1);
                due = k.getTimeInMillis();
            }
            if (due <= 0 || due < Fmt.startOfDay(now) || due > now + 2 * DAY) continue;
            if (l.last > due - 20 * DAY) continue;   // already paid this month
            String pk = "emiR:" + l.key + ":" + Fmt.startOfDay(due);
            if (s.prefs().getBoolean(pk, false)) continue;
            s.prefs().edit().putBoolean(pk, true).apply();
            Alerts.postTo(c, Alerts.CH_BILLS, 950 + (l.key.hashCode() & 0xFF), "🏦 EMI " + Fmt.plain(l.emi) + " " + Reminders.when(due),
                    l.name + (l.due > 0 ? "" : " (usually around the " + CardsActivity.ord(Fmt.cal(due).get(Calendar.DAY_OF_MONTH)) + ")")
                            + " · keep enough in the account — a bounced EMI brings charges and hurts your credit score.", Loans.class);
        }
    }

    static long totalLeft(Context c) {
        List<Txn> all = Store.get(c).txns();
        long t = 0;
        for (Loan l : list(c, false)) { long v = l.now(all); if (v > 0) t += v; }
        return t;
    }

    static String summary(Context c) {
        List<Loan> l = list(c, false);
        if (l.isEmpty()) return "Found by themselves from EMI messages";
        long emi = 0;
        for (Loan x : l) emi += x.emi;
        return Fmt.rs(emi) + "/month · " + l.size() + (l.size() == 1 ? " loan" : " loans");
    }

    // ------------------------------------------------------------------ screen

    @Override
    String title() { return "Loans & EMIs"; }

    @Override
    void build() {
        List<Loan> ls = list(this, false);
        List<Txn> all = Store.get(this).txns();
        long emi = 0, left = 0;
        for (Loan l : ls) { emi += l.emi; long v = l.now(all); if (v > 0) left += v; }
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "EMIs every month", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(emi), 28, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, ls.isEmpty() ? "No loans found. EMI payments and lender messages show up here by themselves."
                : left > 0 ? Fmt.rs(left) + " still to pay (loans with details added)" : "Tap a loan and add what's left to see months left and interest."));
        add(hero);
        for (Loan l : ls) add(card(l, all));
        TextView addB = Ui.button(this, "＋  Add a loan", false);
        addB.setOnClickListener(v -> edit(null));
        add(addB);
    }

    private View card(Loan l, List<Txn> all) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.badge(this, "🏦", 0xFF64748B, 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.oneLine(Ui.text(this, l.name, 15.5f, Ui.BOLD)));
        String sub = Fmt.rs(l.emi) + " a month" + (l.count > 0 ? " · " + l.count + " paid since " + Fmt.monthShort(l.first) + " " + Fmt.cal(l.first).get(Calendar.YEAR) : "");
        col.addView(Ui.oneLine(Ui.sub(this, sub)));
        h.addView(col, Ui.weight1());
        c.addView(h);
        long left = l.now(all);
        if (left > 0 && l.rate > 0) {
            long[] r = Notices.repay(left, l.rate, l.emi);
            LinearLayout st = Ui.hbox(this);
            st.setPadding(0, Ui.dp(this, 12), 0, 0);
            st.addView(stat("Left to pay", Fmt.rs(left)), Ui.weight1());
            if (r[0] >= 0) {
                Calendar end = Fmt.cal(System.currentTimeMillis());
                end.add(Calendar.MONTH, (int) r[0]);
                st.addView(stat("Ends", Fmt.MON[end.get(Calendar.MONTH)] + " " + end.get(Calendar.YEAR) + " (" + r[0] + " mo)"), Ui.weight1());
                st.addView(stat("Interest left", Fmt.rs(r[1])), Ui.weight1());
            } else st.addView(stat("Ends", "EMI too low?"), Ui.weight1());
            c.addView(st);
            if (r[0] > 0) c.addView(prepay(l, left));
        } else {
            TextView t = Ui.text(this, left > 0 ? Fmt.rs(left) + " left (add the interest rate for months left & interest)" : "Add what's left + interest rate → months left, interest, prepay savings",
                    12.5f, Ui.col(this, R.color.accent), Ui.SEMIBOLD);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            c.addView(t);
        }
        if (l.due > System.currentTimeMillis() - DAY) {
            TextView d = Ui.text(this, "Next EMI due " + Fmt.date(l.due), 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR);
            d.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(d);
        }
        c.setOnClickListener(v -> edit(l));
        return c;
    }

    private View stat(String label, String value) {
        LinearLayout v = Ui.vbox(this);
        v.addView(Ui.text(this, label, 11.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        v.addView(Ui.oneLine(Ui.text(this, value, 14, Ui.BOLD)));
        return v;
    }

    private View prepay(Loan l, long left) {
        LinearLayout p = Ui.vbox(this);
        p.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        p.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 14)));
        TextView out = Ui.text(this, "Pay extra once — how much would you save?", 13, Ui.SEMIBOLD);
        p.addView(out);
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 8), 0, 0);
        long[] amounts = {1000000, 5000000, 10000000};
        String[] labels = {"₹10k", "₹50k", "₹1L", "Other"};
        for (int i = 0; i < labels.length; i++) {
            TextView ch = Ui.chip(this, labels[i], false);
            int idx = i;
            ch.setOnClickListener(v -> {
                if (idx < 3) show(out, l, left, amounts[idx]);
                else {
                    LinearLayout b = form();
                    EditText e = field("Extra payment ₹", true);
                    b.addView(e);
                    new Ui.Popup(this).setTitle("Prepay").setView(b).setPositiveButton("Work it out", (d, w) -> {
                        long x = SmsParser.paise(e.getText().toString().trim());
                        if (x > 0) show(out, l, left, x);
                    }).setNegativeButton("Cancel", null).show();
                }
            });
            r.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        }
        p.addView(r);
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0);
        p.setLayoutParams(lp);
        return p;
    }

    private void show(TextView out, Loan l, long left, long x) {
        if (x >= left) { out.setText("That pays it off completely 🎉 — you'd save " + Fmt.rs(Notices.repay(left, l.rate, l.emi)[1]) + " in interest."); return; }
        long[] s = Notices.prepay(left, l.rate, l.emi, x);
        out.setText("Pay " + Fmt.rs(x) + " extra now → finish " + s[0] + (s[0] == 1 ? " month" : " months") + " sooner and save " + Fmt.rs(s[1]) + " in interest.");
        out.setTextColor(Ui.col(this, R.color.accent));
    }

    private void edit(Loan l) {
        LinearLayout b = form();
        EditText name = field("Name (e.g. Home loan, Bike loan)", false);
        EditText emi = field("EMI ₹", true);
        EditText left = field("Amount still to pay ₹ (from your loan app / statement)", true);
        EditText months = field("…or months left", true);
        EditText rate = field("Interest rate % a year", true);
        months.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (l != null) {
            name.setText(l.name);
            if (l.emi > 0) emi.setText(plain(l.emi));
            long now = l.now(Store.get(this).txns());
            if (now > 0) left.setText(plain(now));
            if (l.rate > 0) rate.setText(String.valueOf(l.rate).replaceAll("\\.0$", ""));
        }
        for (EditText e : new EditText[]{name, emi, left, months, rate}) b.addView(e);
        TextView n = Ui.sub(this, "Stored only on this phone. Months left = worked out from what's left, the EMI and the rate.");
        n.setPadding(0, Ui.dp(this, 8), 0, 0);
        b.addView(n);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(b);
        Ui.Popup p = new Ui.Popup(this);
        p.setTitle(l == null ? "Add a loan" : "Loan details").setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    String key = l != null ? l.key : "m:" + id();
                    JSONObject o = data(this).optJSONObject(key);
                    if (o == null) o = new JSONObject();
                    long e = SmsParser.paise(emi.getText().toString().trim());
                    double r = -1;
                    try { r = Double.parseDouble(rate.getText().toString().trim()); } catch (Exception ignored) { }
                    long os = SmsParser.paise(left.getText().toString().trim());
                    int mo = -1;
                    try { mo = Integer.parseInt(months.getText().toString().trim()); } catch (Exception ignored) { }
                    if (os <= 0 && mo > 0 && e > 0 && r >= 0) os = Notices.outstanding(e, r, mo);
                    try {
                        String nm = name.getText().toString().trim();
                        if (!nm.isEmpty()) o.put("name", nm).put("named", true);
                        if (e > 0) o.put("emi", e);
                        if (r > 0) o.put("rate", r);
                        if (os > 0) o.put("os", os).put("osT", System.currentTimeMillis());
                    } catch (Exception ignored) { }
                    put(this, key, o);
                    rebuild();
                })
                .setNegativeButton("Cancel", null);
        if (l != null) p.setNeutralButton(l.key.startsWith("m:") ? "Delete" : "Not a loan", (d, w) -> {
            JSONObject all = data(this);
            if (l.key.startsWith("m:")) all.remove(l.key);
            else {
                JSONObject o = all.optJSONObject(l.key);
                if (o == null) o = new JSONObject();
                try { all.put(l.key, o.put("hidden", true)); } catch (Exception ignored) { }
            }
            Store.get(this).putObj(KEY, all);
            rebuild();
        });
        p.show();
    }

    private static String plain(long paise) { return Long.toString(paise / 100); }
}
