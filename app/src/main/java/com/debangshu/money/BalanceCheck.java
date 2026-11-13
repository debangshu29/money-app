package com.debangshu.money;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly balance check: "What's your HDFC ••1234 balance now?" — type it in the notification. Whatever your payments
 * don't explain becomes an Unrecorded entry while you still remember what it was (small UPI payments often send no SMS).
 */
public class BalanceCheck extends BroadcastReceiver {

    private static final long DAY = 86400000L;
    private static final int NID = 445;

    /** The account you use most (last 30 days). */
    static String mainAccount(Context c) {
        List<String> ok = Noted.bankAccounts(c);
        Map<String, Integer> n = new HashMap<>();
        long cut = System.currentTimeMillis() - 30 * DAY;
        for (Txn t : Store.get(c).txns()) if (t.time > cut && ok.contains(t.account) && t.kind != Txn.SNAPSHOT) n.put(t.account, (n.containsKey(t.account) ? n.get(t.account) : 0) + 1);
        String best = null;
        int m = 0;
        for (Map.Entry<String, Integer> e : n.entrySet()) if (e.getValue() > m) { m = e.getValue(); best = e.getKey(); }
        return best;
    }

    /** Background: once a week, in the evening. */
    static void maybeAsk(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_BAL_CHECK) || !Alerts.canNotify(c)) return;
        long now = System.currentTimeMillis();
        int h = Fmt.cal(now).get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 18 || h > 21) return;
        long last = s.prefs().getLong("balCheckAsked", 0);
        if (last == 0) { s.prefs().edit().putLong("balCheckAsked", now - 4 * DAY).apply(); return; }   // first one a few days after install
        if (now - last < 7 * DAY) return;
        String acc = mainAccount(c);
        if (acc == null) return;
        s.prefs().edit().putLong("balCheckAsked", now).apply();
        Alerts.channels(c);
        Intent ri = new Intent(c, BalanceCheck.class).putExtra("acc", acc);
        PendingIntent pr = PendingIntent.getBroadcast(c, NID, ri, PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        RemoteInput in = new RemoteInput.Builder("bal").setLabel("Balance now, e.g. 12450").build();
        Notification.Action act = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notify), "Type balance", pr).addRemoteInput(in).build();
        Intent open = new Intent(c, MainActivity.class).putExtra("balCheck", acc).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(c, NID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, Alerts.CH_ASK) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle("🏦 Weekly check: " + Noted.label(c, acc) + " balance?")
                .setContentText("Open your bank or UPI app, type the balance here — Money finds any payment that sent no message.")
                .setStyle(new Notification.BigTextStyle().bigText("Open your bank or UPI app and type the balance here. Any small payment that sent no SMS or notification shows up as “Unrecorded” — while you still remember what it was."))
                .setContentIntent(po).setAutoCancel(true).addAction(act).setColor(c.getColor(R.color.accent));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NID, b.build());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        Bundle r = RemoteInput.getResultsFromIntent(intent);
        CharSequence cs = r == null ? null : r.getCharSequence("bal");
        String acc = intent.getStringExtra("acc");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        long v = cs == null ? -1 : SmsParser.paise(cs.toString().replaceAll("[^0-9.]", ""));
        if (v < 0 || acc == null) { if (nm != null) nm.cancel(NID); return; }
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                String res = apply(c, acc, v);
                Alerts.post(c, Alerts.CH_ASK, NID, res.startsWith("✓") ? res : "🔍 " + res,
                        res.startsWith("✓") ? "Nothing missing since the last check." : "Tap to name it while you remember.", null);
            } finally { pr.finish(); }
        }).start();
    }

    /** Saves the balance, re-checks, and says what it found. */
    static String apply(Context c, String acc, long paise) {
        Store s = Store.get(c);
        long before = s.txns().size();
        long t0 = System.currentTimeMillis();
        s.checkBalance(acc, paise);
        Engine.regaps(c, acc);
        long missing = 0;
        int n = 0;
        for (Txn g : s.txns()) {
            if ((g.sources & Txn.SRC_GAP) == 0 || !acc.equals(g.account) || g.time < t0 - 60000) continue;
            missing += g.credit ? -g.amount : g.amount;
            n++;
        }
        if (n == 0) return "✓ " + Noted.label(c, acc) + " matches — " + Fmt.plain(paise);
        return (missing > 0 ? Fmt.plain(missing) + " went out" : Fmt.plain(-missing) + " came in") + " without a message — see Activity";
    }

    /** In the app: Account menu → "Check balance". */
    static void ask(Activity a, String acc) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        EditText e = new EditText(a);
        e.setHint(Lang.t("Balance now ₹ (from your bank / UPI app)"));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTypeface(Ui.face(a, Ui.BOLD));
        e.setTextSize(20);
        b.addView(e);
        b.addView(Ui.sub(a, "Anything your payments don't explain shows as “Unrecorded” — name it while you remember. "
                + "(Different from “Correct balance”, which starts afresh and hides the difference.)"));
        new Ui.Popup(a).setTitle("Check " + Noted.label(a, acc)).setView(b)
                .setPositiveButton("Check", (d, w) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v < 0) return;
                    new Thread(() -> {
                        String res = apply(a, acc, v);
                        a.runOnUiThread(() -> new Ui.Popup(a).setTitle(res.startsWith("✓") ? "All good" : "Found something")
                                .setMessage(res).setPositiveButton("OK", null).show());
                    }).start();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
