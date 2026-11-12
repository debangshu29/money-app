package com.debangshu.money;

import android.app.Activity;
import android.app.DatePickerDialog;
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
import java.util.List;
import java.util.Locale;

/**
 * Renewals & reminders: mobile plan expiry, insurance renewal, PUC, FASTag balance (read from SMS by themselves),
 * plus return-by and warranty dates for things you bought, and anything you add.
 */
public class Reminders extends Screen {

    static final String KEY = "renewals";
    private static final long DAY = 86400000L;

    static JSONObject items(Context c) { return Store.get(c).obj(KEY); }

    static void put(Context c, String key, JSONObject o) {
        JSONObject all = items(c);
        try { all.put(key, o); } catch (Exception ignored) { }
        Store.get(c).putObj(KEY, all);
    }

    static void remove(Context c, String key) {
        JSONObject all = items(c);
        all.remove(key);
        Store.get(c).putObj(KEY, all);
    }

    static int[] stages(String kind) {
        switch (kind) {
            case Notices.RECHARGE: return new int[]{3, 1, 0};
            case Notices.INSURANCE: return new int[]{30, 7, 1};
            case Notices.PUC: return new int[]{15, 3, 0};
            case Notices.RETURN: return new int[]{2, 0};
            case Notices.WARRANTY: return new int[]{30, 7};
            default: return new int[]{7, 1, 0};
        }
    }

    static String emoji(String kind) {
        switch (kind) {
            case Notices.RECHARGE: return "📱";
            case Notices.INSURANCE: return "🛡️";
            case Notices.PUC: return "🚗";
            case Notices.FASTAG: return "🛣️";
            case Notices.RETURN: return "📦";
            case Notices.WARRANTY: return "🧰";
            default: return "🔔";
        }
    }

    static int color(String kind) {
        switch (kind) {
            case Notices.RECHARGE: return 0xFF6366F1;
            case Notices.INSURANCE: return 0xFF14B8A6;
            case Notices.PUC: return 0xFF84CC16;
            case Notices.FASTAG: return 0xFF0EA5E9;
            case Notices.RETURN: return 0xFFF97316;
            case Notices.WARRANTY: return 0xFF8B5CF6;
            default: return 0xFF64748B;
        }
    }

    static int daysLeft(long until) { return (int) Math.floor((Fmt.startOfDay(until) - Fmt.startOfDay(System.currentTimeMillis())) / (double) DAY); }

    static String when(long until) {
        int d = daysLeft(until);
        return d == 0 ? "today" : d == 1 ? "tomorrow" : d > 1 ? "in " + d + " days" : d == -1 ? "yesterday" : (-d) + " days ago";
    }

    // ------------------------------------------------------------------ from messages

    static void onNotice(Context c, Notices.Renewal r, long time, boolean live) {
        String key = r.kind + ":" + r.name.toLowerCase(Locale.ROOT);
        JSONObject old = items(c).optJSONObject(key);
        if (old != null && old.optLong("seen") > time) return;   // an older message
        JSONObject o = old != null ? old : new JSONObject();
        try {
            o.put("kind", r.kind).put("name", r.name).put("seen", time);
            if (Notices.FASTAG.equals(r.kind)) {
                boolean wasLow = o.optBoolean("low");
                if (r.balance >= 0) o.put("bal", r.balance);
                o.put("low", r.low);
                if (r.low && !wasLow && live && Store.get(c).flag(Store.S_REMIND))
                    Alerts.postTo(c, Alerts.CH_BILLS, 900 + (key.hashCode() & 0xFF), "🛣️ " + r.name + " balance is low",
                            (r.balance >= 0 ? "Only " + Fmt.plain(r.balance) + " left. " : "") + "Recharge before your next trip to avoid paying double toll.", Reminders.class);
            } else {
                if (o.optLong("until") != r.until) {
                    o.put("until", r.until);
                    for (int s : new int[]{30, 15, 7, 3, 2, 1, 0}) o.remove("s" + s);
                    o.remove("done");
                }
            }
        } catch (Exception ignored) { }
        put(c, key, o);
    }

    /** Background: the right notification at 30 / 7 / 1 day(s) before (depending on the kind). */
    static void check(Context c) {
        if (!Store.get(c).flag(Store.S_REMIND)) return;
        JSONObject all = items(c);
        boolean changed = false;
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null || o.optBoolean("done") || o.optLong("until") <= 0) continue;
            String kind = o.optString("kind");
            int d = daysLeft(o.optLong("until"));
            if (d < 0) continue;
            int hit = -1;
            for (int s : stages(kind)) if (d <= s && !o.optBoolean("s" + s)) hit = s;
            if (hit < 0) continue;
            try { for (int s : stages(kind)) if (s >= hit) o.put("s" + s, true); } catch (Exception ignored) { }
            changed = true;
            String name = o.optString("name"), w = when(o.optLong("until")), date = Fmt.date(o.optLong("until"));
            String title, text;
            switch (kind) {
                case Notices.RECHARGE: title = "📱 " + name + " ends " + w; text = "Recharge before " + date + " to keep calls and data."; break;
                case Notices.INSURANCE: title = "🛡️ " + name + " renewal " + w; text = "Renew by " + date + " so the cover doesn't lapse."; break;
                case Notices.PUC: title = "🚗 " + name + " expires " + w; text = "Get a fresh PUC before " + date + " — there's a fine without it."; break;
                case Notices.RETURN: title = "📦 Last day to return " + name + (d == 0 ? " is today" : " — " + w); text = "Return window ends " + date + ". Keep it? Ignore this."; break;
                case Notices.WARRANTY: title = "🧰 " + name + " warranty ends " + w; text = "Get anything fixed for free before " + date + "."; break;
                default: title = "🔔 " + name + " " + w; text = "Due " + date + ".";
            }
            Alerts.postTo(c, Alerts.CH_BILLS, 920 + (k.hashCode() & 0xFF), title, text, Reminders.class);
        }
        if (changed) Store.get(c).putObj(KEY, all);
    }

    static List<JSONObject> upcoming(Context c, int withinDays) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject all = items(c);
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null || o.optBoolean("done") || o.optLong("until") <= 0) continue;
            int d = daysLeft(o.optLong("until"));
            if (d >= 0 && d <= withinDays) { try { o.put("key", k); } catch (Exception ignored) { } out.add(o); }
        }
        java.util.Collections.sort(out, (x, y) -> Long.compare(x.optLong("until"), y.optLong("until")));
        return out;
    }

    static String summary(Context c) {
        List<JSONObject> u = upcoming(c, 30);
        JSONObject all = items(c);
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            JSONObject o = all.optJSONObject(it.next());
            if (o != null && Notices.FASTAG.equals(o.optString("kind")) && o.optBoolean("low")) return "FASTag balance is low";
        }
        if (!u.isEmpty()) return u.get(0).optString("name") + " " + when(u.get(0).optLong("until"));
        return all.length() == 0 ? "Recharge, insurance, PUC, returns, warranty" : all.length() + " being watched · nothing soon";
    }

    // ------------------------------------------------------------------ returns & warranty (from a payment / bill photo)

    /** Worth asking after a bill photo: shopping, electronics, or anything ₹1,000+. */
    static boolean worthAsking(Context c, Txn t) {
        if (!t.isSpend()) return false;
        JSONObject all = items(c);
        if (all.has(Notices.RETURN + ":" + t.id) || all.has(Notices.WARRANTY + ":" + t.id)) return false;
        return Cats.SHOP.equals(t.category) || t.amount >= 100000;
    }

    static void askReturn(Activity a, Txn t, Runnable after) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        EditText name = new EditText(a);
        name.setHint("What did you buy? (e.g. headphones)");
        name.setTypeface(Ui.face(a, Ui.REGULAR));
        JSONObject all = items(a);
        JSONObject r0 = all.optJSONObject(Notices.RETURN + ":" + t.id), w0 = all.optJSONObject(Notices.WARRANTY + ":" + t.id);
        name.setText(r0 != null ? r0.optString("name") : w0 != null ? w0.optString("name") : t.merchant);
        b.addView(name);
        long[] ret = {r0 != null ? r0.optLong("until") : -1}, war = {w0 != null ? w0.optLong("until") : -1};
        TextView rl = Ui.text(a, "Return by", 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
        rl.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 6));
        b.addView(rl);
        b.addView(choices(a, new String[]{"None", "7 days", "10 days", "30 days", "Pick"}, new int[]{0, 7, 10, 30, -1}, t.time, ret, Calendar.DAY_OF_MONTH));
        TextView wl = Ui.text(a, "Warranty till", 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD);
        wl.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 6));
        b.addView(wl);
        b.addView(choices(a, new String[]{"None", "6 months", "1 year", "2 years", "Pick"}, new int[]{0, 6, 12, 24, -1}, t.time, war, Calendar.MONTH));
        TextView n = Ui.sub(a, "Counted from the payment date (" + Fmt.date(t.time) + "). Money reminds you before the last day.");
        n.setPadding(0, Ui.dp(a, 10), 0, 0);
        b.addView(n);
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        new Ui.Popup(a).setTitle("⏰ Return & warranty").setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    String nm = name.getText().toString().trim();
                    if (nm.isEmpty()) nm = t.merchant.isEmpty() ? "your purchase" : t.merchant;
                    save(a, Notices.RETURN, t, nm, ret[0]);
                    save(a, Notices.WARRANTY, t, nm, war[0]);
                    if (ret[0] > 0 || war[0] > 0) Ui.toast(a, "Saved — Money will remind you");
                    if (after != null) after.run();
                })
                .setNegativeButton("Not now", (d, w) -> { if (after != null) after.run(); }).show();
    }

    private static void save(Context c, String kind, Txn t, String name, long until) {
        String key = kind + ":" + t.id;
        if (until <= 0) { remove(c, key); return; }
        try {
            put(c, key, new JSONObject().put("kind", kind).put("name", name).put("until", until).put("txn", t.id).put("seen", t.time).put("manual", true));
        } catch (Exception ignored) { }
    }

    /** A row of one-choice chips; picks a date into out[0] (≤0 = none). */
    private static View choices(Activity a, String[] labels, int[] amounts, long from, long[] out, int field) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(a);
        hs.addView(row);
        List<TextView> chips = new ArrayList<>();
        Runnable[] paint = new Runnable[1];
        int[] sel = {0};
        if (out[0] > 0) {
            sel[0] = labels.length - 1;
            for (int i = 1; i < amounts.length - 1; i++) if (add(from, field, amounts[i]) == out[0]) sel[0] = i;
        }
        for (int i = 0; i < labels.length; i++) {
            TextView ch = Ui.chip(a, labels[i], false);
            int idx = i;
            ch.setOnClickListener(v -> {
                if (amounts[idx] == 0) { out[0] = -1; sel[0] = idx; paint[0].run(); }
                else if (amounts[idx] > 0) { out[0] = add(from, field, amounts[idx]); sel[0] = idx; paint[0].run(); }
                else {
                    Calendar k = Fmt.cal(out[0] > 0 ? out[0] : add(from, field, field == Calendar.MONTH ? 12 : 7));
                    new DatePickerDialog(a, (dp, y, m, d) -> {
                        Calendar x = Calendar.getInstance();
                        x.set(y, m, d, 9, 0, 0);
                        x.set(Calendar.MILLISECOND, 0);
                        out[0] = x.getTimeInMillis();
                        sel[0] = idx;
                        labelPick(chips.get(idx), out[0]);
                        paint[0].run();
                    }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
                }
            });
            chips.add(ch);
            row.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 6, 0));
        }
        if (sel[0] == labels.length - 1 && out[0] > 0) labelPick(chips.get(sel[0]), out[0]);
        paint[0] = () -> {
            for (int i = 0; i < chips.size(); i++) {
                boolean on = i == sel[0];
                chips.get(i).setBackground(Ui.rippleRound(a, on ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 18));
                chips.get(i).setTextColor(on ? 0xFFFFFFFF : Ui.col(a, R.color.text));
            }
        };
        paint[0].run();
        return hs;
    }

    private static void labelPick(TextView t, long d) { t.setText(Fmt.cal(d).get(Calendar.DAY_OF_MONTH) + " " + Fmt.monthShort(d) + " " + Fmt.cal(d).get(Calendar.YEAR)); }

    private static long add(long from, int field, int n) {
        Calendar k = Fmt.cal(Fmt.startOfDay(from));
        k.add(field, n);
        k.set(Calendar.HOUR_OF_DAY, 9);
        return k.getTimeInMillis();
    }

    // ------------------------------------------------------------------ screen

    @Override
    String title() { return "Renewals & reminders"; }

    @Override
    void build() {
        JSONObject all = items(this);
        List<JSONObject> soon = new ArrayList<>(), later = new ArrayList<>(), past = new ArrayList<>(), tags = new ArrayList<>();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null) continue;
            try { o.put("key", k); } catch (Exception ignored) { }
            if (Notices.FASTAG.equals(o.optString("kind"))) { tags.add(o); continue; }
            int d = daysLeft(o.optLong("until"));
            if (o.optBoolean("done") || d < 0) past.add(o);
            else if (d <= 30) soon.add(o);
            else later.add(o);
        }
        java.util.Comparator<JSONObject> byDate = (x, y) -> Long.compare(x.optLong("until"), y.optLong("until"));
        java.util.Collections.sort(soon, byDate);
        java.util.Collections.sort(later, byDate);
        java.util.Collections.sort(past, (x, y) -> Long.compare(y.optLong("until"), x.optLong("until")));

        if (all.length() == 0) {
            LinearLayout e = Ui.card(this);
            e.addView(Ui.text(this, "🔔", 32, Ui.REGULAR));
            e.addView(Ui.text(this, "Nothing to remember yet", 16, Ui.BOLD));
            e.addView(Ui.sub(this, "Mobile plan expiry, insurance renewals, PUC and FASTag balance are picked up from your SMS by themselves. "
                    + "For things you buy, open the payment → “⏰ Return / warranty” (or add a bill photo)."));
            add(e);
        }
        for (JSONObject t : tags) add(fastag(t));
        if (!soon.isEmpty()) { box.addView(label("Next 30 days")); add(list(soon)); }
        if (!later.isEmpty()) { box.addView(label("Later")); add(list(later)); }
        TextView addB = Ui.button(this, "＋  Add a renewal", true);
        addB.setOnClickListener(v -> edit(null, null));
        add(addB);
        if (!past.isEmpty()) {
            box.addView(label("Past"));
            add(list(past.subList(0, Math.min(8, past.size()))));
        }
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s.toUpperCase(Locale.ROOT), 11.5f, Ui.col(this, R.color.text2), Ui.BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), 0, Ui.dp(this, 8));
        return t;
    }

    private View fastag(JSONObject o) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.badge(this, "🛣️", color(Notices.FASTAG), 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, o.optString("name"), 15, Ui.BOLD));
        col.addView(Ui.sub(this, "As of " + Fmt.date(o.optLong("seen")) + (o.optBoolean("low") ? " · low — recharge soon" : "")));
        h.addView(col, Ui.weight1());
        long bal = o.optLong("bal", -1);
        h.addView(Ui.text(this, bal >= 0 ? Fmt.rs(bal) : "—", 16, o.optBoolean("low") ? Ui.col(this, R.color.danger) : Ui.col(this, R.color.text), Ui.EXTRABOLD));
        c.addView(h);
        c.setOnLongClickListener(v -> { remove(this, o.optString("key")); rebuild(); return true; });
        return c;
    }

    private View list(List<JSONObject> l) {
        LinearLayout c = Ui.card(this);
        for (JSONObject o : l) {
            String kind = o.optString("kind");
            LinearLayout r = Ui.hbox(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            r.setBackground(Ui.rippleRound(this, 0, 14));
            r.addView(Ui.badge(this, emoji(kind), color(kind), 38));
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
            String nm = kind.equals(Notices.RETURN) ? "Return " + o.optString("name") : kind.equals(Notices.WARRANTY) ? o.optString("name") + " warranty" : o.optString("name");
            col.addView(Ui.oneLine(Ui.text(this, nm, 14.5f, Ui.SEMIBOLD)));
            long u = o.optLong("until");
            String sub = (o.optBoolean("done") ? "Done ✓ · " : "") + Fmt.date(u) + (o.optInt("repeat") > 0 ? " · every " + (o.optInt("repeat") == 12 ? "year" : o.optInt("repeat") == 1 ? "month" : o.optInt("repeat") + " months") : "");
            col.addView(Ui.oneLine(Ui.sub(this, sub)));
            r.addView(col, Ui.weight1());
            int d = daysLeft(u);
            if (!o.optBoolean("done") && d >= 0)
                r.addView(Ui.text(this, d == 0 ? "Today" : d == 1 ? "Tomorrow" : d + " days", 13, d <= 3 ? Ui.col(this, R.color.danger) : Ui.col(this, R.color.text2), Ui.BOLD));
            r.setOnClickListener(v -> menu(o));
            c.addView(r);
        }
        return c;
    }

    private void menu(JSONObject o) {
        String key = o.optString("key");
        int rep = o.optInt("repeat");
        List<String> opts = new ArrayList<>();
        opts.add(rep > 0 ? "✓ Renewed — move to the next date" : "✓ Done");
        opts.add("Change");
        if (!o.optString("txn").isEmpty()) opts.add("Open the payment");
        opts.add("Delete");
        new Ui.Popup(this).setTitle(o.optString("name")).setItems(opts.toArray(new String[0]), (d, i) -> {
            String s = opts.get(i);
            JSONObject x = items(this).optJSONObject(key);
            if (x == null) return;
            try {
                if (s.startsWith("✓")) {
                    if (rep > 0) {
                        Calendar k = Fmt.cal(x.optLong("until"));
                        k.add(Calendar.MONTH, rep);
                        x.put("until", k.getTimeInMillis());
                        for (int st : new int[]{30, 15, 7, 3, 2, 1, 0}) x.remove("s" + st);
                    } else x.put("done", true);
                    put(this, key, x);
                } else if (s.equals("Change")) {
                    if (Notices.RETURN.equals(x.optString("kind")) || Notices.WARRANTY.equals(x.optString("kind"))) {
                        Txn t = Store.get(this).byId(x.optString("txn"));
                        if (t != null) { askReturn(this, t, this::rebuild); return; }
                    }
                    edit(key, x);
                    return;
                } else if (s.startsWith("Open")) {
                    startActivity(new android.content.Intent(this, MainActivity.class).putExtra("txn", x.optString("txn"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    return;
                } else remove(this, key);
            } catch (Exception ignored) { }
            rebuild();
        }).show();
    }

    private void edit(String key, JSONObject x) {
        LinearLayout b = form();
        EditText name = field("What (e.g. Car insurance, Netflix, Gas cylinder)", false);
        if (x != null) name.setText(x.optString("name"));
        b.addView(name);
        long[] when = {x != null ? x.optLong("until") : -1};
        TextView date = Ui.button(this, when[0] > 0 ? "📅  " + Fmt.date(when[0]) : "📅  Pick the date", false);
        date.setOnClickListener(v -> {
            Calendar k = Fmt.cal(when[0] > 0 ? when[0] : System.currentTimeMillis() + 30 * DAY);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar c = Calendar.getInstance();
                c.set(y, m, d, 9, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                when[0] = c.getTimeInMillis();
                date.setText("📅  " + Fmt.date(when[0]));
            }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
        });
        b.addView(date, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 8));
        int[] rep = {x != null ? x.optInt("repeat") : 12};
        String[] rl = {"Once", "Every month", "Every 3 months", "Every year"};
        int[] rv = {0, 1, 3, 12};
        TextView repB = Ui.chip(this, "↻ " + rl[java.util.Arrays.binarySearch(rv, rep[0]) >= 0 ? java.util.Arrays.binarySearch(rv, rep[0]) : 0], false);
        repB.setOnClickListener(v -> new Ui.Popup(this).setTitle("Repeats").setItems(rl, (d, i) -> { rep[0] = rv[i]; repB.setText("↻ " + rl[i]); }).show());
        b.addView(repB);
        new Ui.Popup(this).setTitle(x == null ? "Add a renewal" : "Change").setView(b)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty() || when[0] <= 0) { Ui.toast(this, "Add a name and a date"); return; }
                    String l = n.toLowerCase(Locale.ROOT);
                    String kind = x != null ? x.optString("kind") : l.contains("insurance") || l.contains("policy") ? Notices.INSURANCE
                            : l.contains("puc") ? Notices.PUC : l.contains("recharge") || l.contains("jio") || l.contains("airtel") || l.contains("plan") ? Notices.RECHARGE : Notices.OTHER;
                    String k = key != null ? key : kind + ":" + l;
                    try {
                        JSONObject o = new JSONObject().put("kind", kind).put("name", n).put("until", when[0]).put("repeat", rep[0])
                                .put("seen", System.currentTimeMillis()).put("manual", true);
                        if (x != null && !x.optString("txn").isEmpty()) o.put("txn", x.optString("txn"));
                        put(this, k, o);
                    } catch (Exception ignored) { }
                    rebuild();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
