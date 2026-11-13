package com.debangshu.money;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Wait-list: park something you want to buy for 7 or 30 days. If you still want it, buy it happily; if not, that's money saved. */
public class WaitListActivity extends Screen {

    @Override String title() { return "Wait-list"; }

    @Override
    void build() {
        JSONArray a = MoneyFacts.waitList(this);
        long now = System.currentTimeMillis();
        long savedTotal = 0;
        int skipped = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject w = a.optJSONObject(i);
            if ("skip".equals(w.optString("state"))) { savedTotal += w.optLong("price"); skipped++; }
        }
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, "Saved by waiting", 13, Ui.col(this, R.color.text2), Ui.BOLD));
        hero.addView(Ui.text(this, Fmt.rs(savedTotal), 30, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, skipped == 0 ? "Most “must-haves” stop feeling urgent after a week. Try it with the next thing you want."
                : skipped + (skipped == 1 ? " thing" : " things") + " you decided you didn't need after all."));
        TextView addB = Ui.button(this, "＋  Add something I want", true);
        addB.setOnClickListener(v -> addItem());
        hero.addView(addB, Ui.margins(Ui.lp(-2, -2), this, 0, 14, 0, 0));
        add(hero);

        boolean any = false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject w = a.optJSONObject(i);
                boolean waiting = "wait".equals(w.optString("state", "wait"));
                if ((pass == 0) != waiting) continue;
                if (pass == 1 && !any) add(Ui.label(this, "Decided"));
                any = true;
                add(card(w, now));
            }
            if (pass == 0 && any) any = false;
        }
    }

    private LinearLayout card(JSONObject w, long now) {
        LinearLayout c = Ui.card(this);
        String state = w.optString("state", "wait");
        long due = w.optLong("added") + w.optInt("days") * GoalMath.DAY;
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconBadge(this, "wait".equals(state) ? "⏳" : "skip".equals(state) ? "🐷" : "🛍️",
                "wait".equals(state) ? 0xFF6366F1 : "skip".equals(state) ? 0xFF0E9F6E : 0xFF94A3B8, 40));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        col.addView(Ui.oneLine(Ui.text(this, w.optString("name"), 15.5f, Ui.BOLD)));
        String sub;
        if ("wait".equals(state)) {
            long days = (long) Math.ceil((due - now) / (double) GoalMath.DAY);
            sub = days > 0 ? days + (days == 1 ? " day" : " days") + " to wait" : "Time's up — still want it?";
        } else sub = "skip".equals(state) ? "Skipped — saved ✓" : "Bought";
        col.addView(Ui.text(this, sub, 12.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        String hrs = MoneyFacts.hoursFor(this, w.optLong("price"));
        if (!hrs.isEmpty()) col.addView(Ui.text(this, "≈ " + hrs, 12, Ui.col(this, R.color.hint), Ui.SEMIBOLD));
        h.addView(col, Ui.weight1());
        h.addView(Ui.text(this, Fmt.rs(w.optLong("price")), 15.5f, Ui.EXTRABOLD));
        c.addView(h);
        if ("wait".equals(state)) {
            float f = Math.min(1f, (now - w.optLong("added")) / (float) Math.max(1, due - w.optLong("added")));
            Charts.Progress pr = new Charts.Progress(this);
            pr.set(f, 0xFF6366F1);
            c.addView(pr, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 10));
            LinearLayout r = Ui.hbox(this);
            TextView skip = Ui.chip(this, "Don't need it", true);
            TextView buy = Ui.chip(this, now >= due ? "Buying it" : "Bought anyway", false);
            buy.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
            skip.setOnClickListener(v -> decide(w, "skip"));
            buy.setOnClickListener(v -> decide(w, "buy"));
            r.addView(skip, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
            r.addView(buy);
            c.addView(r);
        } else {
            c.setOnLongClickListener(v -> {
                new Ui.Popup(this).setTitle("Remove “" + w.optString("name") + "”?").setPositiveButton("Remove", (d, x) -> remove(w))
                        .setNegativeButton("Keep", null).show();
                return true;
            });
        }
        return c;
    }

    private void decide(JSONObject w, String state) {
        JSONArray a = MoneyFacts.waitList(this);
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optString("id").equals(w.optString("id"))) {
            try { a.optJSONObject(i).put("state", state).put("decided", System.currentTimeMillis()); } catch (Exception ignored) { }
        }
        Store.get(this).putList(MoneyFacts.WAIT, a);
        if ("skip".equals(state)) {
            if (Goals.award(this, "patient")) GrowPage.showCelebrations(this);
            Ui.toast(this, Fmt.rs(w.optLong("price")) + " saved 🐷");
        } else Ui.toast(this, "Enjoy it — you thought it through");
        rebuild();
    }

    private void remove(JSONObject w) {
        JSONArray a = MoneyFacts.waitList(this), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optString("id").equals(w.optString("id"))) out.put(a.optJSONObject(i));
        Store.get(this).putList(MoneyFacts.WAIT, out);
        rebuild();
    }

    private void addItem() {
        LinearLayout b = form();
        EditText name = field("What do you want to buy?", false);
        EditText price = field("₹ price", true);
        b.addView(name);
        b.addView(price);
        TextView hrs = Ui.text(this, "", 13, Ui.col(this, R.color.accent), Ui.BOLD);
        price.addTextChangedListener(GoalSetup.watcher(s -> {
            String h = MoneyFacts.hoursFor(this, SmsParser.paise(s.trim()));
            if (h.isEmpty() && MoneyFacts.income(this) > 0) h = SaveMath.hoursText(SaveMath.hours(SmsParser.paise(s.trim()), MoneyFacts.income(this), MoneyFacts.hoursPerWeek(this)));
            hrs.setText(h.isEmpty() ? "" : "That's " + h);
        }));
        b.addView(hrs);
        TextView wl = Ui.text(this, "Wait for", 13, Ui.col(this, R.color.text2), Ui.BOLD);
        wl.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        b.addView(wl);
        int[] days = {7};
        LinearLayout r = Ui.hbox(this);
        TextView d7 = Ui.chip(this, "7 days", true), d30 = Ui.chip(this, "30 days", false);
        d30.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        d7.setOnClickListener(v -> { days[0] = 7; paint(d7, d30); });
        d30.setOnClickListener(v -> { days[0] = 30; paint(d30, d7); });
        r.addView(d7, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
        r.addView(d30);
        b.addView(r);
        TextView tip = Ui.sub(this, "Rule of thumb: 7 days for small things, 30 days for anything over a week's pay.");
        tip.setPadding(0, Ui.dp(this, 10), 0, 0);
        b.addView(tip);
        new Ui.Popup(this).setTitle("I want…").setView(b)
                .setPositiveButton("Start waiting", (d, w) -> {
                    String n = name.getText().toString().trim();
                    long p = SmsParser.paise(price.getText().toString().trim());
                    if (n.isEmpty() || p <= 0) { Ui.toast(this, "Add what and how much"); return; }
                    JSONArray a = MoneyFacts.waitList(this);
                    try {
                        a.put(new JSONObject().put("id", id()).put("name", n).put("price", p).put("days", days[0])
                                .put("added", System.currentTimeMillis()).put("state", "wait"));
                    } catch (Exception ignored) { }
                    Store.get(this).putList(MoneyFacts.WAIT, a);
                    Ui.toast(this, "Money will ask you again in " + days[0] + " days");
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
    }

    private void paint(TextView on, TextView off) {
        on.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.accent), 18));
        on.setTextColor(Ui.col(this, R.color.on_accent));
        off.setBackground(Ui.rippleRound(this, Ui.col(this, R.color.surface2), 18));
        off.setTextColor(Ui.col(this, R.color.text));
    }
}
