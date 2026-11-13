package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.List;

/** "200 cash for chai" — say a cash spend (from the widget, the quick-settings tile, Activity's + or the cash wallet). */
public class VoiceActivity extends Activity {

    private static final int REQ = 61;

    public static void start(Activity a) { a.startActivity(new Intent(a, VoiceActivity.class)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Lang.speech())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a payment — “200 for chai” or “500 to Ramesh on Google Pay”");
        try { startActivityForResult(i, REQ); }
        catch (Exception e) {
            Ui.toast(this, "Voice typing isn't available on this phone — type it instead");
            TxnSheetHost.typeInstead(this);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ) return;
        if (res != RESULT_OK || data == null) { finish(); return; }
        ArrayList<String> said = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        List<Voice.Item> items = new ArrayList<>();
        String heard = "";
        if (said != null) for (String s : said) { items = Voice.parse(s); heard = s; if (!items.isEmpty()) break; }
        if (items.isEmpty()) {
            String h = heard;
            new Ui.Popup(this).setTitle("Didn't catch an amount")
                    .setMessage((h.isEmpty() ? "" : "Heard: “" + h + "”\n\n") + "Try “200 for chai” or “auto 50 and samosa 30”.")
                    .setPositiveButton("Try again", (d, w) -> { finish(); startActivity(new Intent(this, VoiceActivity.class)); })
                    .setNegativeButton("Close", (d, w) -> finish())
                    .setOnCancelListener(d -> finish()).show();
            return;
        }
        confirm(items, heard);
    }

    /** Show what was heard and ask "Paid from" — Cash or the exact bank account. Nothing is saved until you tap Save. */
    private void confirm(List<Voice.Item> items, String heard) {
        // which money: what you said wins ("in cash", "from Federal ending 1234"), then UPI words, then last time's choice
        String[] from = {null};
        String ask = "Paid from";
        String bank = "", last4 = "";
        boolean cash = false, upi = false;
        for (Voice.Item it : items) {
            if (it.cash) cash = true;
            if (it.upi) upi = true;
            if (bank.isEmpty() && !it.bank.isEmpty()) bank = it.bank;
            if (last4.isEmpty() && !it.last4.isEmpty()) last4 = it.last4;
        }
        List<String> banks = Noted.bankAccounts(this);
        if (cash) from[0] = Cash.ACCOUNT;
        else if (!bank.isEmpty() || !last4.isEmpty()) {
            List<String> m = Noted.match(this, bank, last4);
            if (m.size() == 1) from[0] = m.get(0);
            else if (m.size() > 1) ask = "Which " + (bank.isEmpty() ? "" : bank + " ") + "account?";
            else ask = "No " + (bank.isEmpty() ? "" : bank + " ") + "account" + (last4.isEmpty() ? "" : " ending " + last4) + " yet — pick one";
        } else if (upi) {
            String d = Noted.defaultAccount(this);
            from[0] = banks.contains(d) ? d : null;
        } else {
            String l = Noted.lastFrom(this);
            from[0] = Cash.ACCOUNT.equals(l) || banks.contains(l) ? l : Cash.ACCOUNT;
        }

        android.widget.LinearLayout box = Ui.vbox(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 4), p, 0);
        long total = 0;
        StringBuilder list = new StringBuilder();
        for (Voice.Item it : items) {
            total += it.paise;
            list.append(Cats.emoji(it.category)).append("  ").append(Fmt.plain(it.paise)).append(" · ").append(it.what).append('\n');
        }
        if (!heard.isEmpty()) box.addView(Ui.sub(this, "Heard: “" + heard + "”"));
        android.widget.TextView li = Ui.text(this, list.toString().trim(), 16, Ui.col(this, R.color.text), Ui.BOLD);
        box.addView(li, Ui.margins(Ui.lp(-1, -2), this, 0, 8, 0, 14));
        android.widget.TextView fl = Ui.text(this, ask, 13, Ui.col(this, from[0] == null ? R.color.accent : R.color.text2), Ui.BOLD);
        box.addView(fl, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 8));
        android.app.AlertDialog[] dlg = {null};
        box.addView(Noted.fromPicker(this, from, true, () -> {
            if (dlg[0] != null) dlg[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }));
        android.widget.TextView tip = Ui.sub(this, "The balance of that account changes right away. Say “from Federal ending 1234” or “in cash” next time to skip this.");
        box.addView(tip, Ui.margins(Ui.lp(-1, -2), this, 0, 12, 0, 0));

        long tot = total;
        dlg[0] = new Ui.Popup(this).setTitle(items.size() == 1 ? "Add this?" : "Add " + items.size() + "?  (" + Fmt.plain(tot) + ")")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> save(items, from[0]))
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish()).create();
        dlg[0].show();
        if (from[0] == null) dlg[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private void save(List<Voice.Item> items, String from) {
        List<Txn> saved = new ArrayList<>();
        for (Voice.Item it : items) {
            String nm = it.what.equals("Cash spend") ? (Cash.ACCOUNT.equals(from) ? it.what : "") : it.what;
            saved.add(Noted.save(this, from, it.paise, nm, it.category, ""));
        }
        String where = Noted.fullLabel(this, from);
        String left = Cash.ACCOUNT.equals(from)
                ? (Cash.on(this) ? Fmt.plain(Cash.balance(this)) + " left in cash" : "")
                : balanceText(from);
        new Ui.Popup(this).setTitle("Saved ✓")
                .setMessage("From " + where + (left.isEmpty() ? "" : " · " + left)
                        + (Cash.ACCOUNT.equals(from) ? "" : "\n\nIf the bank's SMS comes later, it joins this — no double entry."))
                .setPositiveButton("OK", (d, w) -> finish())
                .setNeutralButton("Undo", (d, w) -> {
                    Store s = Store.get(this);
                    for (Txn t : saved) {
                        TxnSheet.gone(this, t, true);
                        t.kind = Txn.IGNORED;
                        t.note = "Undone";
                        s.update(t);
                    }
                    Ui.toast(this, "Undone — balance is back");
                    finish();
                })
                .setOnCancelListener(d -> finish()).show();
    }

    private String balanceText(String key) {
        long[] lv = Noted.liveOf(this, key);
        return lv != null && lv[0] >= 0 ? Fmt.plain(lv[0]) + " left" : "";
    }
}
