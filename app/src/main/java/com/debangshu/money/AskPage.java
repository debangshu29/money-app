package com.debangshu.money;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;

/** Ask your money in plain words. Answered on the phone — the question never leaves it. */
final class AskPage {

    private final android.app.Activity a;
    private LinearLayout root, chat;
    private ScrollView scroll;
    private EditText input;

    AskPage(android.app.Activity a) { this.a = a; }

    View view() {
        if (root != null) return root;
        root = Ui.vbox(a);
        root.addView(Screens.bar(a, "Ask your money"));

        scroll = new ScrollView(a);
        chat = Ui.vbox(a);
        chat.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 12));
        scroll.addView(chat);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hello = bubble("Ask anything about your money in plain words — or tap an example below. "
                + "Everything is worked out on your phone.", false);
        chat.addView(hello);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.hbox(a);
        chips.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 6));
        for (String ex : Ask.EXAMPLES) {
            TextView c = Ui.chip(a, ex, false);
            c.setOnClickListener(v -> send(ex));
            chips.addView(c, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        }
        hs.addView(chips);
        root.addView(hs);

        LinearLayout bar = Ui.hbox(a);
        bar.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 10));
        input = new EditText(a);
        input.setHint("Ask about your spending…");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setTypeface(Ui.face(a, Ui.REGULAR));
        input.setTextSize(15);
        input.setBackground(Ui.round(Ui.col(a, R.color.surface), Ui.dp(a, 24)));
        input.setPadding(Ui.dp(a, 18), Ui.dp(a, 12), Ui.dp(a, 18), Ui.dp(a, 12));
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                send(input.getText().toString());
                return true;
            }
            return false;
        });
        bar.addView(input, Ui.weight1());
        ImageView go = Ui.icon(a, R.drawable.ic_send, Ui.col(a, R.color.on_accent), 20);
        go.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.accent), 22));
        go.setOnClickListener(v -> send(input.getText().toString()));
        bar.addView(go, Ui.margins(Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)), a, 8, 0, 0, 0));
        root.addView(bar);
        return root;
    }

    void ask(String q) { view(); send(q); }

    private void send(String q) {
        q = q.trim();
        if (q.isEmpty()) return;
        input.setText("");
        InputMethodManager im = a.getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        chat.addView(bubble(q, true));
        Store s = Store.get(a);
        Ask.plan = s.plan();
        Ask.Answer ans = Ask.answer(q, s.txns(), s.accounts(), System.currentTimeMillis());
        LinearLayout card = Ui.card(a);
        card.addView(Ui.text(a, ans.text, 15, Ui.SEMIBOLD));
        if (ans.table != null && !ans.table.isEmpty()) {
            long top = 1;
            for (long v : ans.table.values()) top = Math.max(top, v);
            for (Map.Entry<String, Long> e : ans.table.entrySet()) {
                card.addView(Rows.amountRow(a, null, Ui.col(a, R.color.accent), e.getKey(), null, e.getValue(), e.getValue() / (float) top));
            }
        }
        int shown = 0;
        for (Txn t : ans.items) {
            if (shown++ >= 8) break;
            card.addView(Rows.txn(a, t, true));
        }
        if (ans.items.size() > 8) card.addView(Ui.sub(a, "+ " + (ans.items.size() - 8) + " more"));
        chat.addView(card, Ui.margins(Ui.matchWrap(), a, 0, 6, 32, 6));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView bubble(String text, boolean mine) {
        TextView t = Ui.text(a, text, 15, mine ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text), mine ? Ui.SEMIBOLD : Ui.REGULAR);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        t.setBackground(Ui.round(mine ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface), Ui.dp(a, 18)));
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.setMargins(mine ? Ui.dp(a, 48) : 0, Ui.dp(a, 6), mine ? 0 : Ui.dp(a, 48), Ui.dp(a, 6));
        t.setLayoutParams(lp);
        return t;
    }
}
