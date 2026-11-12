package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Cashback & rewards: every cashback that came in (by itself, from your messages) and each card's reward points. */
public class Rewards extends Screen {

    static final String KEY = "points";
    private static final Pattern CASHBACK = Pattern.compile("cash ?back|reward|scratch ?card|cred coins|supercoin|neu ?coins|gpay rewards|bonus credit", Pattern.CASE_INSENSITIVE);

    static void onPoints(Context c, Notices.Points p, long time) {
        JSONObject all = Store.get(c).obj(KEY);
        JSONObject o = all.optJSONObject(p.account);
        if (o == null) o = new JSONObject();
        if (o.optLong("t") > time) return;
        try {
            o.put("t", time);
            if (p.balance >= 0) o.put("bal", p.balance);
            if (p.earned > 0) o.put("earned", p.earned).put("earnedT", time);
            all.put(p.account, o);
        } catch (Exception ignored) { }
        Store.get(c).putObj(KEY, all);
    }

    static boolean isCashback(Txn t) {
        return t.credit && t.kind != Txn.IGNORED && t.kind != Txn.SNAPSHOT
                && (CASHBACK.matcher(t.raw).find() || CASHBACK.matcher(t.merchant).find() || CASHBACK.matcher(t.note).find());
    }

    static List<Txn> cashback(List<Txn> all, long from) {
        List<Txn> out = new ArrayList<>();
        for (Txn t : all) if (t.time >= from && isCashback(t)) out.add(t);
        java.util.Collections.sort(out, (x, y) -> Long.compare(y.time, x.time));
        return out;
    }

    static String summary(Context c) {
        long fy = Smart.fyStart(System.currentTimeMillis());
        long tot = 0;
        for (Txn t : cashback(Store.get(c).txns(), fy)) tot += t.amount;
        long pts = 0;
        JSONObject all = Store.get(c).obj(KEY);
        for (Iterator<String> it = all.keys(); it.hasNext(); ) pts += Math.max(0, all.optJSONObject(it.next()).optLong("bal"));
        if (tot == 0 && pts == 0) return "Cashback & card points add up here";
        return (tot > 0 ? Fmt.rs(tot) + " cashback" : "") + (tot > 0 && pts > 0 ? " · " : "") + (pts > 0 ? String.format(java.util.Locale.ROOT, "%,d", pts) + " points" : "");
    }

    @Override
    String title() { return "Cashback & rewards"; }

    @Override
    void build() {
        long now = System.currentTimeMillis();
        long fy = Smart.fyStart(now), month = Fmt.startOfMonth(now);
        List<Txn> cb = cashback(Store.get(this).txns(), fy);
        long tot = 0, mon = 0;
        Map<String, Long> by = new LinkedHashMap<>();
        for (Txn t : cb) {
            tot += t.amount;
            if (t.time >= month) mon += t.amount;
            String k = t.merchant.isEmpty() ? (t.sender == null || t.sender.isEmpty() ? "Cashback" : t.sender) : t.merchant;
            by.put(k, (by.containsKey(k) ? by.get(k) : 0) + t.amount);
        }
        LinearLayout hero = Ui.card(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFFF59E0B, 0xFFEA580C});
        g.setCornerRadius(Ui.dp(this, 22));
        hero.setBackground(g);
        hero.addView(Ui.text(this, "Cashback in " + Smart.fyName(fy), 13, 0xD9FFFFFF, Ui.SEMIBOLD));
        hero.addView(Ui.text(this, Fmt.rs(tot), 30, 0xFFFFFFFF, Ui.EXTRABOLD));
        hero.addView(Ui.text(this, cb.isEmpty() ? "Cashback credits are picked up from your messages by themselves." : Fmt.rs(mon) + " this month · " + cb.size() + (cb.size() == 1 ? " time" : " times"),
                13, 0xD9FFFFFF, Ui.REGULAR));
        add(hero);

        // card points
        JSONObject pts = Store.get(this).obj(KEY);
        LinearLayout pc = Ui.card(this);
        pc.addView(Ui.sectionTitle(this, "Reward points"));
        if (pts.length() == 0) pc.addView(Ui.sub(this, "Points appear when your bank sends a message with the points balance (often with the card statement)."));
        for (Iterator<String> it = pts.keys(); it.hasNext(); ) {
            String acc = it.next();
            JSONObject o = pts.optJSONObject(acc);
            LinearLayout r = Ui.hbox(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            r.addView(Ui.badge(this, "⭐", 0xFFF59E0B, 36));
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
            col.addView(Ui.oneLine(Ui.text(this, acc, 14.5f, Ui.SEMIBOLD)));
            col.addView(Ui.oneLine(Ui.sub(this, "As of " + Fmt.date(o.optLong("t")) + (o.optLong("earned") > 0 ? " · last +" + o.optLong("earned") : ""))));
            r.addView(col, Ui.weight1());
            long bal = o.optLong("bal", -1);
            r.addView(Ui.text(this, bal >= 0 ? String.format(java.util.Locale.ROOT, "%,d", bal) : "—", 16, Ui.EXTRABOLD));
            r.setOnLongClickListener(v -> { JSONObject a = Store.get(this).obj(KEY); a.remove(acc); Store.get(this).putObj(KEY, a); rebuild(); return true; });
            pc.addView(r);
        }
        if (pts.length() > 0) {
            TextView tip = Ui.text(this, "Points often expire in 2–3 years — redeem them in your card app before they do.", 12, Ui.col(this, R.color.text2), Ui.REGULAR);
            tip.setPadding(0, Ui.dp(this, 6), 0, 0);
            pc.addView(tip);
        }
        add(pc);

        if (!by.isEmpty()) {
            LinearLayout sc = Ui.card(this);
            sc.addView(Ui.sectionTitle(this, "Where it came from"));
            long top = 1;
            for (long v : by.values()) top = Math.max(top, v);
            int i = 0;
            for (Map.Entry<String, Long> e : Ledger.sorted(by).entrySet()) {
                if (i++ >= 6) break;
                sc.addView(Rows.amountRow(this, "💸", 0xFFF59E0B, e.getKey(), null, e.getValue(), e.getValue() / (float) top));
            }
            add(sc);
            LinearLayout lc = Ui.card(this);
            lc.addView(Ui.sectionTitle(this, "Latest"));
            for (int k = 0; k < Math.min(10, cb.size()); k++) lc.addView(Rows.txn(this, cb.get(k), true));
            add(lc);
        }
    }
}
