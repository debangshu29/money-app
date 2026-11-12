package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One transaction as a row: category badge · name + details · amount. */
public final class Rows {

    private Rows() {}

    public static View txn(Activity a, Txn t, boolean showDay) {
        Context c = a;
        LinearLayout row = Ui.hbox(c);
        int ph = Ui.dp(c, 4), pv = Ui.dp(c, 10);
        row.setPadding(ph, pv, ph, pv);
        row.setBackground(Ui.rippleRound(c, 0x00000000, 14));

        row.addView(Ui.badge(c, Cats.emoji(t.category), Cats.color(t.category), 42));

        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        String name = t.merchant.isEmpty() ? (t.credit ? "Money in" : "Payment") : t.merchant;
        TextView n = Ui.oneLine(Ui.text(c, (t.review ? "⚠️ " : "") + name, 15, Ui.SEMIBOLD));
        col.addView(n);
        StringBuilder sb = new StringBuilder(t.category.isEmpty() ? Cats.OTHER : t.category);
        if (t.kind == Txn.TRANSFER) sb.append(" · not spending");
        if (t.kind == Txn.IGNORED) sb.append(" · hidden");
        sb.append(" · ").append(showDay ? Fmt.day(t.time) + ", " + Fmt.time(t.time) : Fmt.time(t.time));
        if (!t.account.isEmpty()) sb.append(" · ").append(t.account);
        col.addView(Ui.oneLine(Ui.text(c, sb.toString(), 12.5f, Ui.col(c, R.color.text2), Ui.REGULAR)));
        row.addView(col, Ui.weight1());

        int color = t.kind == Txn.TRANSFER || t.kind == Txn.IGNORED ? Ui.col(c, R.color.text2)
                : t.credit ? Ui.col(c, R.color.success) : Ui.col(c, R.color.text);
        TextView amt = Ui.text(c, (t.credit ? "+" : "−") + Fmt.rs(t.amount).replace("₹ ", "₹"), 15, color, Ui.BOLD);
        amt.setGravity(Gravity.END);
        row.addView(amt);

        row.setOnClickListener(v -> TxnSheet.show(a, t));
        return row;
    }

    /** "Today · −₹1,240" day header. */
    public static View dayHeader(Context c, long day, long spent, long in) {
        LinearLayout h = Ui.hbox(c);
        h.setPadding(Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 4));
        TextView d = Ui.text(c, Fmt.day(day), 13, Ui.col(c, R.color.text2), Ui.BOLD);
        h.addView(d, Ui.weight1());
        String s = (spent > 0 ? "−" + Fmt.rs(spent) : "") + (in > 0 ? (spent > 0 ? "   " : "") + "+" + Fmt.rs(in) : "");
        h.addView(Ui.text(c, s, 12.5f, Ui.col(c, R.color.text2), Ui.SEMIBOLD));
        return h;
    }

    /** Name · amount row with a coloured dot (category tables). */
    public static View amountRow(Context c, String emoji, int color, String name, String sub, long amount, float frac) {
        LinearLayout row = Ui.hbox(c);
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        row.setBackground(Ui.rippleRound(c, 0x00000000, 14));
        if (emoji != null) row.addView(Ui.badge(c, emoji, color, 36));
        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, emoji != null ? 12 : 0), 0, Ui.dp(c, 8), 0);
        LinearLayout top = Ui.hbox(c);
        top.addView(Ui.oneLine(Ui.text(c, name, 14.5f, Ui.SEMIBOLD)), Ui.weight1());
        top.addView(Ui.text(c, Fmt.rs(amount), 14.5f, Ui.BOLD));
        col.addView(top);
        if (frac >= 0) {
            Charts.Progress p = new Charts.Progress(c);
            p.set(frac, color);
            col.addView(p, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        }
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 12, Ui.col(c, R.color.text2), Ui.REGULAR);
            s.setPadding(0, Ui.dp(c, 3), 0, 0);
            col.addView(s);
        }
        row.addView(col, Ui.weight1());
        return row;
    }
}
