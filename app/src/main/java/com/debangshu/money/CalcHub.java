package com.debangshu.money;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** All calculators & play tools, in three groups. */
public class CalcHub extends Screen {

    @Override String title() { return "Calculators & play"; }

    @Override
    void build() {
        TextView intro = Ui.sub(this, "Move the sliders and watch your money change. Every tool explains itself simply — and you can share any result as a picture.");
        intro.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        add(intro);
        for (int g = 0; g < Calcs.GROUPS.length; g++) {
            add(Ui.label(this, Calcs.GROUPS[g]));
            LinearLayout c = Ui.card(this);
            c.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
            for (Calcs.Calc k : Calcs.ALL) {
                if (k.group != g) continue;
                c.addView(Ui.navRow(this, k.emoji, k.color, k.title, k.sub, v -> CalcActivity.open(this, k.id)));
            }
            add(c);
        }
        TextView d = Ui.text(this, "Everything is worked out on your phone. Rates are examples, not promises — for learning, not financial advice.",
                11.5f, Ui.col(this, R.color.hint), Ui.REGULAR);
        d.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
        add(d);
    }

    /** Four favourites as round icons + "All 20 tools", for the Grow tab. */
    static View featured(android.app.Activity a) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 8));
        String[] ids = {"sip", "compare", "crash", "time", "race", "wait", "card", "nocost"};
        List<View> items = new ArrayList<>();
        for (String id : ids) {
            Calcs.Calc k = Calcs.byId(id);
            String short1 = id.equals("sip") ? "SIP" : id.equals("compare") ? "Compare all" : id.equals("crash") ? "Crash test"
                    : id.equals("time") ? "Time machine" : id.equals("race") ? "Race" : id.equals("wait") ? "Cost of waiting"
                    : id.equals("card") ? "Card trap" : "No-cost EMI";
            items.add(Ui.action(a, k.emoji, k.color, short1, v -> CalcActivity.open(a, id)));
        }
        c.addView(Ui.grid(a, items.toArray(new View[0])));
        TextView all = Ui.text(a, "All " + Calcs.ALL.length + " calculators & play tools  →", 14, Ui.col(a, R.color.accent), Ui.BOLD);
        all.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), 0, Ui.dp(a, 4));
        all.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, CalcHub.class)));
        c.addView(all);
        return c;
    }
}
