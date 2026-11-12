package com.debangshu.money;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tax helper (old regime deductions), for the April–March year: finds payments that usually count —
 * 80C (ELSS, PPF, LIC, tuition), 80D (health insurance), NPS, rent, donations — and keeps a total
 * against each limit. Not tax advice; a starting list for you or your CA.
 */
public class TaxActivity extends Screen {

    static final String EXCLUDE = "taxExclude", MANUAL = "taxManual";

    static final class Sec {
        final String code, name, emoji, help;
        final long limit;   // paise, 0 = no fixed limit
        final Pattern words;
        final String[] cats;
        Sec(String code, String name, String emoji, long limitRs, String help, String words, String... cats) {
            this.code = code; this.name = name; this.emoji = emoji; this.limit = limitRs * 100; this.help = help;
            this.words = Pattern.compile(words, Pattern.CASE_INSENSITIVE); this.cats = cats;
        }
        boolean match(Txn t) {
            boolean catOk = cats.length == 0;
            for (String c : cats) if (c.equals(t.category)) catOk = true;
            if (!catOk) return false;
            String s = t.merchant + " " + t.raw;
            return words.matcher(s).find();
        }
    }

    static final Sec[] SECS = {
            new Sec("80C", "80C · Savings & insurance", "🛡️", 150000,
                    "ELSS / tax-saver funds, PPF, EPF (voluntary), LIC & life insurance, Sukanya, NSC, 5-year tax-saver FD, children's tuition fees, home-loan principal.",
                    "elss|tax ?saver|ppf|public provident|sukanya|\\bnsc\\b|\\blic\\b|life insurance|life ins|term (?:plan|insurance)|tuition|school fee|\\bvpf\\b|ulip",
                    Cats.INVEST, Cats.INSURE, Cats.EDU, Cats.EMI, Cats.OTHER, Cats.BILLS),
            new Sec("80D", "80D · Health insurance", "💊", 25000,
                    "Health insurance for you, your spouse and children (₹25,000; more for parents / seniors) and preventive check-ups.",
                    "health|medi ?claim|star health|care health|niva|bupa|hdfc ergo|icici lombard|aditya birla health|preventive|health check",
                    Cats.INSURE, Cats.HEALTH),
            new Sec("NPS", "80CCD(1B) · NPS", "👴", 50000,
                    "Your own extra NPS contribution — up to ₹50,000 on top of 80C.",
                    "\\bnps\\b|national pension|protean|nsdl.*pension|pfrda",
                    Cats.INVEST, Cats.OTHER),
            new Sec("RENT", "Rent paid (HRA / 80GG)", "🏠", 0,
                    "Rent you pay — useful for HRA from your employer, or 80GG if you don't get HRA. Keep rent receipts.",
                    "rent|landlord|nobroker|housing",
                    Cats.HOME, Cats.PEOPLE, Cats.OTHER),
            new Sec("80G", "80G · Donations", "🎁", 0,
                    "Donations to registered charities / relief funds (keep the 80G receipt).",
                    "donat|charit|relief fund|pm cares|ngo|foundation|trust|temple|gurudwara|church|mosque|giveindia|ketto|cry\\b|akshaya",
                    Cats.GIFTS, Cats.OTHER),
    };

    static Set<String> excluded(Context c) {
        Set<String> s = new HashSet<>();
        JSONArray a = Store.get(c).list(EXCLUDE);
        for (int i = 0; i < a.length(); i++) s.add(a.optString(i));
        return s;
    }

    static List<Txn> found(Context c, Sec sec, long from, long to, Set<String> ex) {
        List<Txn> out = new ArrayList<>();
        for (Txn t : Store.get(c).txns()) {
            if (t.credit || t.kind != Txn.NORMAL && t.kind != Txn.TRANSFER || t.time < from || t.time >= to || ex.contains(t.id)) continue;
            if (sec.match(t)) out.add(t);
        }
        return out;
    }

    static long manual(Context c, String code, long from, long to, List<JSONObject> out) {
        long sum = 0;
        JSONArray a = Store.get(c).list(MANUAL);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (!code.equals(o.optString("sec")) || o.optLong("t") < from || o.optLong("t") >= to) continue;
            sum += o.optLong("amt");
            if (out != null) out.add(o);
        }
        return sum;
    }

    /** Total that counts (each capped at its limit) for the Plan tile. */
    static String summary(Context c) {
        long fy = Smart.fyStart(System.currentTimeMillis()), to = Fmt.addMonths(fy, 12);
        Set<String> ex = excluded(c);
        long tot = 0;
        for (Sec s : SECS) {
            long v = manual(c, s.code, fy, to, null);
            for (Txn t : found(c, s, fy, to, ex)) v += t.amount;
            tot += s.limit > 0 ? Math.min(v, s.limit) : v;
        }
        return tot == 0 ? Smart.fyName(fy) + " · 80C, 80D, NPS, rent" : Fmt.rs(tot) + " found · " + Smart.fyName(fy);
    }

    private long fy = Smart.fyStart(System.currentTimeMillis());

    @Override
    String title() { return "Tax helper"; }

    @Override
    void build() {
        long to = Fmt.addMonths(fy, 12);
        long now = System.currentTimeMillis();
        Set<String> ex = excluded(this);

        // year switcher
        LinearLayout nav = Ui.hbox(this);
        nav.setGravity(Gravity.CENTER);
        TextView prev = Ui.chip(this, "‹", false), next = Ui.chip(this, "›", false);
        TextView name = Ui.text(this, Smart.fyName(fy), 17, Ui.BOLD);
        name.setGravity(Gravity.CENTER);
        boolean current = fy == Smart.fyStart(now);
        prev.setOnClickListener(v -> { fy = Fmt.addMonths(fy, -12); rebuild(); });
        next.setOnClickListener(v -> { if (!current) { fy = Fmt.addMonths(fy, 12); rebuild(); } });
        next.setAlpha(current ? 0.3f : 1f);
        nav.addView(prev);
        nav.addView(name, Ui.weight1());
        nav.addView(next);
        add(nav);

        long total = 0;
        List<View> cards = new ArrayList<>();
        for (Sec s : SECS) {
            List<Txn> list = found(this, s, fy, to, ex);
            List<JSONObject> man = new ArrayList<>();
            long v = manual(this, s.code, fy, to, man);
            for (Txn t : list) v += t.amount;
            total += s.limit > 0 ? Math.min(v, s.limit) : v;
            cards.add(card(s, list, man, v));
        }

        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "Deductions found", 13, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(total), 28, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, "From your payments this year (counted up to each limit). Tap a payment to leave it out, or add one paid another way (salary deduction, cash, cheque)."));
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView csv = Ui.chip(this, "⬇ Save as CSV", true);
        csv.setOnClickListener(v -> exportCsv());
        TextView info = Ui.chip(this, "ⓘ Old vs new regime", false);
        info.setOnClickListener(v -> new Ui.Popup(this).setTitle("Which tax regime?")
                .setMessage("These deductions count only in the OLD tax regime. The NEW regime (the default now) has lower rates but almost no deductions — "
                        + "only your employer's NPS share and the standard deduction.\n\nIf your deductions are small, the new regime is usually better. "
                        + "Compare both on the income-tax portal or ask your CA.\n\nMoney only finds likely payments from your messages — it isn't tax advice, "
                        + "and limits / rules can change in the Budget each year.")
                .setPositiveButton("OK", null).show());
        r.addView(csv, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        r.addView(info);
        hero.addView(r);
        add(hero);
        for (View c : cards) add(c);

        TextView d = Ui.text(this, "Not tax advice — a starting list to check with your documents or CA.", 11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setGravity(Gravity.CENTER);
        box.addView(d);
    }

    private View card(Sec s, List<Txn> list, List<JSONObject> man, long v) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.badge(this, s.emoji, 0xFF6366F1, 38));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, s.name, 15, Ui.BOLD));
        col.addView(Ui.sub(this, s.limit > 0 ? Fmt.rs(v) + " of " + Fmt.rs(s.limit)
                + (v >= s.limit ? " · limit reached ✓" : " · " + Fmt.rs(s.limit - v) + " room left") : Fmt.rs(v) + " this year"));
        h.addView(col, Ui.weight1());
        c.addView(h);
        if (s.limit > 0) {
            Charts.Progress p = new Charts.Progress(this);
            p.set(Math.min(1f, v / (float) s.limit), v >= s.limit ? Ui.col(this, R.color.success) : 0xFF6366F1);
            c.addView(p, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 4));
        }
        TextView help = Ui.text(this, s.help, 12, Ui.col(this, R.color.text2), Ui.REGULAR);
        help.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        c.addView(help);
        for (Txn t : list) {
            View row = Rows.txn(this, t, true);
            row.setOnClickListener(x -> new Ui.Popup(this).setTitle("Leave this out?")
                    .setMessage(Fmt.rs(t.amount) + " · " + (t.merchant.isEmpty() ? "Payment" : t.merchant) + " won't count for " + s.code + ".")
                    .setPositiveButton("Leave out", (dd, w) -> {
                        JSONArray a = Store.get(this).list(EXCLUDE);
                        a.put(t.id);
                        Store.get(this).putList(EXCLUDE, a);
                        rebuild();
                    })
                    .setNeutralButton("Open payment", (dd, w) -> TxnSheet.show(this, t))
                    .setNegativeButton("Keep", null).show());
            c.addView(row);
        }
        for (JSONObject o : man) {
            View row = Rows.amountRow(this, "✍️", 0xFF6366F1, o.optString("name"), Fmt.date(o.optLong("t")) + " · added by you · tap to remove", o.optLong("amt"), 0);
            row.setOnClickListener(x -> new Ui.Popup(this).setTitle("Remove “" + o.optString("name") + "”?")
                    .setPositiveButton("Remove", (dd, w) -> {
                        JSONArray a = Store.get(this).list(MANUAL), out = new JSONArray();
                        for (int i = 0; i < a.length(); i++) if (!o.optString("id").equals(a.optJSONObject(i).optString("id"))) out.put(a.optJSONObject(i));
                        Store.get(this).putList(MANUAL, out);
                        rebuild();
                    }).setNegativeButton("Cancel", null).show());
            c.addView(row);
        }
        TextView addB = Ui.chip(this, "＋ Add", false);
        addB.setOnClickListener(x -> addManual(s));
        c.addView(addB, Ui.margins(Ui.lp(-2, -2), this, 0, 8, 0, 0));
        return c;
    }

    private void addManual(Sec s) {
        LinearLayout b = form();
        EditText n = field("What (e.g. PPF, LIC premium, EPF)", false);
        EditText amt = field("Amount ₹", true);
        b.addView(n);
        b.addView(amt);
        new Ui.Popup(this).setTitle("Add to " + s.code).setView(b)
                .setPositiveButton("Add", (d, w) -> {
                    long v = SmsParser.paise(amt.getText().toString().trim());
                    if (v <= 0) return;
                    long now = System.currentTimeMillis();
                    long t = now >= fy && now < Fmt.addMonths(fy, 12) ? now : Fmt.addMonths(fy, 12) - 86400000L;
                    JSONArray a = Store.get(this).list(MANUAL);
                    try {
                        a.put(new JSONObject().put("id", id()).put("sec", s.code).put("name", n.getText().toString().trim().isEmpty() ? s.code : n.getText().toString().trim())
                                .put("amt", v).put("t", t));
                    } catch (Exception ignored) { }
                    Store.get(this).putList(MANUAL, a);
                    rebuild();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void exportCsv() {
        long to = Fmt.addMonths(fy, 12);
        Set<String> ex = excluded(this);
        StringBuilder b = new StringBuilder("Section,Date,Paid to,Amount (Rs),Account,Source\n");
        for (Sec s : SECS) {
            for (Txn t : found(this, s, fy, to, ex))
                b.append(s.code).append(',').append(Fmt.date(t.time).replace(",", "")).append(',').append(csv(t.merchant)).append(',')
                        .append(String.format(Locale.ROOT, "%.2f", t.amount / 100.0)).append(',').append(csv(t.account)).append(",message\n");
            List<JSONObject> man = new ArrayList<>();
            manual(this, s.code, fy, to, man);
            for (JSONObject o : man)
                b.append(s.code).append(',').append(Fmt.date(o.optLong("t")).replace(",", "")).append(',').append(csv(o.optString("name"))).append(',')
                        .append(String.format(Locale.ROOT, "%.2f", o.optLong("amt") / 100.0)).append(",,added by you\n");
        }
        String file = "Tax-" + Smart.fyName(fy).replace("FY ", "").replace("–", "-") + ".csv";
        try {
            android.net.Uri u = Report.save(this, "Tax", file, "text/csv", b.toString().getBytes("UTF-8"));
            if (u == null) { Ui.toast(this, "Saved " + file + " in the app's folder"); return; }
            new Ui.Popup(this).setTitle("Saved ✓").setMessage("Documents/Money/Tax/" + file)
                    .setPositiveButton("Share", (d, w) -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share")))
                    .setNegativeButton("Close", null).show();
        } catch (Exception e) {
            Ui.toast(this, "Couldn't save: " + e.getMessage());
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
