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
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a cash spend — e.g. “200 for chai”");
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
        List<Txn> saved = new ArrayList<>();
        StringBuilder msg = new StringBuilder();
        long total = 0;
        for (Voice.Item it : items) {
            Txn t = Cash.spend(this, it.paise, it.what, it.category);
            saved.add(t);
            total += it.paise;
            msg.append(Cats.emoji(it.category)).append("  ").append(Fmt.plain(it.paise)).append(" · ").append(it.what).append('\n');
        }
        msg.append(Cash.on(this) ? "\nTaken from your cash wallet (" + Fmt.plain(Cash.balance(this)) + " left)." : "\nSaved as cash spending.");
        long tot = total;
        new Ui.Popup(this).setTitle(items.size() == 1 ? "Saved ✓" : "Saved " + items.size() + " ✓  (" + Fmt.plain(tot) + ")")
                .setMessage(msg.toString().trim())
                .setPositiveButton("OK", (d, w) -> finish())
                .setNeutralButton("Undo", (d, w) -> {
                    Store s = Store.get(this);
                    for (Txn t : saved) { t.kind = Txn.IGNORED; t.note = "Undone"; s.update(t); }
                    if (Cash.on(this)) Cash.refund(this, tot);
                    Ui.toast(this, "Undone");
                    finish();
                })
                .setOnCancelListener(d -> finish()).show();
    }
}
