package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Net worth = what you have − what you owe. Most of it fills itself: bank balances, cash, investments
 * (what you put in), money people owe you, unpaid card bills. Add anything else once (gold, FD, a car loan…).
 */
public class NetWorthActivity extends Screen {

    static final String KEY = "assets", HISTORY = "nwHistory";

    static final class Line {
        String emoji, name, sub, id;
        long amt;
        Line(String e, String n, String s, long a, String id) { emoji = e; name = n; sub = s; amt = a; this.id = id; }
    }

    /** [0] = have, [1] = owe; lines filled when the lists are given. */
    static long[] compute(Context c, List<Line> have, List<Line> owe) {
        Store s = Store.get(c);
        long h = 0, o = 0;
        long bank = 0;
        int nb = 0;
        for (Account a : s.accounts()) {
            if (a.card || a.hidden || a.balance < 0) continue;
            bank += s.liveBalance(a);
            nb++;
        }
        if (nb > 0) { h += bank; if (have != null) have.add(new Line("🏦", "Bank balances", nb + (nb == 1 ? " account" : " accounts"), bank, null)); }
        if (Cash.on(c) && Cash.balance(c) > 0) { h += Cash.balance(c); if (have != null) have.add(new Line("💵", "Cash wallet", null, Cash.balance(c), null)); }
        long inv = 0;
        for (Txn t : s.txns()) if (t.kind == Txn.NORMAL && Cats.INVEST.equals(t.category)) inv += t.credit ? -t.amount : t.amount;
        if (inv > 0) { h += inv; if (have != null) have.add(new Line("📈", "Investments", "What you put in (from payments marked Investments)", inv, null)); }
        long[] pp = People.totals(c);
        if (pp[0] > 0) { h += pp[0]; if (have != null) have.add(new Line("👥", "People owe you", null, pp[0], null)); }
        if (pp[1] > 0) { o += pp[1]; if (owe != null) owe.add(new Line("👥", "You owe people", null, pp[1], null)); }

        // unpaid card bills (latest per card)
        java.util.Map<String, Store.Bill> latest = new java.util.HashMap<>();
        for (Store.Bill b : s.bills()) {
            Store.Bill x = latest.get(b.account);
            if (x == null || b.seen > x.seen) latest.put(b.account, b);
        }
        long cards = 0;
        int nc = 0;
        for (Store.Bill b : latest.values()) if (!b.paid && b.due > 0) { cards += b.due; nc++; }
        if (cards > 0) { o += cards; if (owe != null) owe.add(new Line("💳", "Card bills due", nc + (nc == 1 ? " card" : " cards"), cards, null)); }

        long loans = Loans.totalLeft(c);
        if (loans > 0) { o += loans; if (owe != null) owe.add(new Line("🏦", "Loans (from EMIs)", "Plan → Loans & EMIs", loans, null)); }

        JSONArray a = s.list(KEY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            boolean loan = "loan".equals(x.optString("kind"));
            long v = x.optLong("amt");
            if (loan) { o += v; if (owe != null) owe.add(new Line(x.optString("emoji", "🏠"), x.optString("name"), "Tap to update", v, x.optString("id"))); }
            else { h += v; if (have != null) have.add(new Line(x.optString("emoji", "🪙"), x.optString("name"), "Tap to update", v, x.optString("id"))); }
        }
        return new long[]{h, o};
    }

    static long value(Context c) { long[] v = compute(c, null, null); return v[0] - v[1]; }

    static String summary(Context c) {
        long[] v = compute(c, null, null);
        return v[0] == 0 && v[1] == 0 ? "What you have − what you owe" : Fmt.rs(v[0] - v[1]);
    }

    /** Remembers this month's figure (for "since last month"). */
    static void record(Context c, long nw) {
        JSONObject h = Store.get(c).obj(HISTORY);
        Calendar k = Fmt.cal(System.currentTimeMillis());
        String key = String.format(Locale.ROOT, "%04d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1);
        if (h.optLong(key, Long.MIN_VALUE) == nw) return;
        try { h.put(key, nw); } catch (Exception ignored) { }
        Store.get(c).putObj(HISTORY, h);
    }

    @Override
    String title() { return "Net worth"; }

    @Override
    void build() {
        List<Line> have = new ArrayList<>(), owe = new ArrayList<>();
        long[] v = compute(this, have, owe);
        long nw = v[0] - v[1];
        record(this, nw);

        // hero
        LinearLayout hero = Ui.card(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFF0E9F6E, 0xFF0B6F86});
        g.setCornerRadius(Ui.dp(this, 22));
        hero.setBackground(g);
        hero.addView(Ui.text(this, "Your net worth", 13.5f, 0xD9FFFFFF, Ui.SEMIBOLD));
        hero.addView(Ui.text(this, (nw < 0 ? "−" : "") + Fmt.rs(Math.abs(nw)), 32, 0xFFFFFFFF, Ui.EXTRABOLD));
        String change = change(nw);
        hero.addView(Ui.text(this, change != null ? change : "What you have − what you owe", 13, 0xD9FFFFFF, Ui.REGULAR));
        add(hero);

        // trend
        View trend = trend();
        if (trend != null) add(trend);

        add(section("What you have", Fmt.rs(v[0]), have, false));
        add(section("What you owe", Fmt.rs(v[1]), owe, true));

        LinearLayout r = Ui.hbox(this);
        TextView a1 = Ui.button(this, "＋ Something you own", true);
        a1.setOnClickListener(x -> edit(null, false));
        TextView a2 = Ui.button(this, "＋ A loan", false);
        a2.setOnClickListener(x -> edit(null, true));
        r.addView(a1, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, 0, 0, 6, 0));
        r.addView(a2, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, 6, 0, 0, 0));
        add(r);
        TextView n = Ui.sub(this, "Gold, FDs, PF, a house — or a home / car / education loan. Update them now and then; everything else updates itself.");
        n.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        box.addView(n);
    }

    private String change(long nw) {
        JSONObject h = Store.get(this).obj(HISTORY);
        TreeMap<String, Long> m = new TreeMap<>();
        for (Iterator<String> it = h.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, h.optLong(k)); }
        if (m.size() < 2) return null;
        String last = m.lastKey();
        String prev = m.lowerKey(last);
        long d = nw - m.get(prev);
        return (d >= 0 ? "▲ " : "▼ ") + Fmt.rs(Math.abs(d)) + " since " + Fmt.MON[Integer.parseInt(prev.substring(5)) - 1];
    }

    private View trend() {
        JSONObject h = Store.get(this).obj(HISTORY);
        TreeMap<String, Long> m = new TreeMap<>();
        for (Iterator<String> it = h.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, h.optLong(k)); }
        if (m.size() < 2) return null;
        while (m.size() > 12) m.remove(m.firstKey());
        long[] vals = new long[m.size()];
        int i = 0;
        long min = 0;
        for (long x : m.values()) min = Math.min(min, x);
        for (long x : m.values()) vals[i++] = x - min;
        LinearLayout c = Ui.card(this);
        c.addView(Ui.sectionTitle(this, "Month by month"));
        TextView picked = Ui.sub(this, "Tap a bar");
        List<String> keys = new ArrayList<>(m.keySet());
        Charts.Bars bars = new Charts.Bars(this);
        bars.set(vals, vals.length - 1, 0, (idx, val) -> picked.setText(Fmt.MON[Integer.parseInt(keys.get(idx).substring(5)) - 1] + " "
                + keys.get(idx).substring(0, 4) + ": " + Fmt.rs(m.get(keys.get(idx)))));
        c.addView(bars);
        c.addView(picked);
        return c;
    }

    private View section(String title, String total, List<Line> lines, boolean owe) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.sectionTitle(this, title);
        h.addView(t, Ui.weight1());
        h.addView(Ui.text(this, total, 15, owe ? Ui.col(this, R.color.danger) : Ui.col(this, R.color.accent), Ui.EXTRABOLD));
        c.addView(h);
        if (lines.isEmpty()) c.addView(Ui.sub(this, owe ? "Nothing — nice." : "Balances appear as your bank messages arrive."));
        long top = 1;
        for (Line l : lines) top = Math.max(top, l.amt);
        for (Line l : lines) {
            View row = Rows.amountRow(this, l.emoji, owe ? 0xFFEF4444 : 0xFF10B981, l.name, l.sub, l.amt, l.amt / (float) top);
            if (l.id != null) {
                String id = l.id;
                row.setOnClickListener(x -> edit(find(id), owe));
            }
            c.addView(row);
        }
        return c;
    }

    private JSONObject find(String id) {
        JSONArray a = Store.get(this).list(KEY);
        for (int i = 0; i < a.length(); i++) if (id.equals(a.optJSONObject(i).optString("id"))) return a.optJSONObject(i);
        return null;
    }

    private void edit(JSONObject x, boolean loan) {
        LinearLayout b = form();
        EditText name = field(loan ? "Name (e.g. Home loan — amount left)" : "Name (e.g. Gold, FD, PF)", false);
        EditText amt = field(loan ? "Amount still to pay ₹" : "Worth today ₹", true);
        if (x != null) {
            name.setText(x.optString("name"));
            amt.setText(Fmt.plain(x.optLong("amt")).replace("₹", "").replace(",", ""));
        }
        b.addView(name);
        b.addView(amt);
        Ui.Popup p = new Ui.Popup(this);
        p.setTitle(x == null ? (loan ? "Add a loan" : "Add something you own") : "Update").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    long v = SmsParser.paise(amt.getText().toString().trim());
                    if (n.isEmpty() || v < 0) return;
                    JSONArray a = Store.get(this).list(KEY);
                    JSONObject o = null;
                    if (x != null) for (int i = 0; i < a.length(); i++) if (x.optString("id").equals(a.optJSONObject(i).optString("id"))) o = a.optJSONObject(i);
                    try {
                        if (o == null) { o = new JSONObject().put("id", id()); a.put(o); }
                        o.put("name", n).put("amt", v).put("kind", loan ? "loan" : "asset").put("emoji", emojiFor(n, loan));
                    } catch (Exception ignored) { }
                    Store.get(this).putList(KEY, a);
                    rebuild();
                })
                .setNegativeButton("Cancel", null);
        if (x != null) p.setNeutralButton("Delete", (d, w) -> {
            JSONArray a = Store.get(this).list(KEY), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (!x.optString("id").equals(a.optJSONObject(i).optString("id"))) out.put(a.optJSONObject(i));
            Store.get(this).putList(KEY, out);
            rebuild();
        });
        p.show();
    }

    static String emojiFor(String n, boolean loan) {
        String l = n.toLowerCase(Locale.ROOT);
        if (l.contains("gold") || l.contains("silver")) return "🪙";
        if (l.contains("house") || l.contains("home") || l.contains("flat") || l.contains("land")) return "🏠";
        if (l.contains("car") || l.contains("bike")) return "🚗";
        if (l.contains("edu") || l.contains("study")) return "🎓";
        if (l.contains("fd") || l.contains("deposit") || l.contains("pf") || l.contains("ppf") || l.contains("rd")) return "🏦";
        if (l.contains("stock") || l.contains("share") || l.contains("mutual") || l.contains("sip") || l.contains("crypto")) return "📈";
        return loan ? "📄" : "💎";
    }
}
