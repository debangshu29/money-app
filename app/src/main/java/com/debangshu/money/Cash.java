package com.debangshu.money;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Cash wallet: money taken out at an ATM goes INTO the wallet (not counted as spending yet).
 * Every few days Money asks "how much cash is left?" — the difference becomes "Cash spent".
 * No item-by-item typing (but you can note a cash spend when you want).
 */
public final class Cash {

    private Cash() {}

    static final String ON = "cash_on", BAL = "cash_bal", SINCE = "cash_since", CHECKED = "cash_checked", LAST_ATM = "cash_last_atm";
    public static final String ACCOUNT = "Cash";

    private static SharedPreferences p(Context c) { return Store.get(c).prefs(); }

    public static boolean on(Context c) { return p(c).getBoolean(ON, false); }

    public static long balance(Context c) { return p(c).getLong(BAL, 0); }

    public static void setOn(Context c, boolean on) {
        SharedPreferences.Editor e = p(c).edit().putBoolean(ON, on);
        if (on && p(c).getLong(SINCE, 0) == 0) e.putLong(SINCE, System.currentTimeMillis()).putLong(CHECKED, System.currentTimeMillis());
        e.apply();
    }

    /** An ATM withdrawal just came in: the money is now in your wallet. */
    static void atm(Context c, Txn t) {
        if (!on(c) || t.time < p(c).getLong(SINCE, 0)) return;
        p(c).edit().putLong(BAL, balance(c) + t.amount).putLong(LAST_ATM, t.time).apply();
    }

    /** You spent cash (noted by you). */
    public static Txn spend(Context c, long paise, String what, String category) {
        Txn t = new Txn();
        t.time = System.currentTimeMillis();
        t.amount = paise;
        t.merchant = what == null ? "" : what.trim();
        t.category = category == null ? "" : category;
        t.userCategory = category != null && !category.isEmpty();
        t.account = ACCOUNT;
        t.sources = Txn.SRC_MANUAL;
        t.raw = "";
        Store.get(c).addOrMerge(t, null);
        if (on(c)) p(c).edit().putLong(BAL, Math.max(0, balance(c) - paise)).apply();
        return t;
    }

    /** Put money back in the wallet (an undone spend). */
    public static void refund(Context c, long paise) {
        if (on(c)) p(c).edit().putLong(BAL, balance(c) + paise).apply();
    }

    /** "I have ₹X now": whatever is missing since the last count was spent. */
    public static void setLeft(Context c, long left) {
        long bal = balance(c);
        if (left < bal) {
            Txn t = new Txn();
            t.time = System.currentTimeMillis();
            t.amount = bal - left;
            t.merchant = "Cash spent";
            t.category = Cats.CASH;
            t.account = ACCOUNT;
            t.sources = Txn.SRC_MANUAL;
            t.note = "Worked out from your cash count (" + Fmt.plain(bal) + " → " + Fmt.plain(left) + ")";
            t.raw = "";
            Store.get(c).addOrMerge(t, null);
        }
        p(c).edit().putLong(BAL, Math.max(0, left)).putLong(CHECKED, System.currentTimeMillis()).apply();
    }

    // ------------------------------------------------------------------ the sheet (Plan → Cash wallet)

    public static void sheet(Activity a) {
        LinearLayout box = Ui.vbox(a);
        int pd = Ui.dp(a, 20);
        box.setPadding(pd, 0, pd, Ui.dp(a, 8));
        boolean on = on(a);
        if (on) {
            box.addView(Ui.text(a, "In your wallet", 13, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
            box.addView(Ui.text(a, Fmt.plain(balance(a)), 34, Ui.EXTRABOLD));
            long checked = p(a).getLong(CHECKED, 0);
            box.addView(Ui.sub(a, "Last counted " + (checked == 0 ? "never" : HomePage.ago(checked))));
            LinearLayout r = Ui.hbox(a);
            r.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
            TextView count = Ui.button(a, "I have ₹… now", true);
            TextView spent = Ui.button(a, "Spent cash", false);
            r.addView(count, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
            r.addView(spent);
            box.addView(r);
            count.setOnClickListener(v -> askLeft(a));
            spent.setOnClickListener(v -> TxnSheet.addManual(a));
            TextView say = Ui.chip(a, "🎤  Say a cash spend", false);
            say.setOnClickListener(v -> VoiceActivity.start(a));
            box.addView(say, Ui.margins(Ui.lp(-2, -2), a, 0, 6, 0, 0));
            List<Txn> recent = new ArrayList<>();
            for (Txn t : Store.get(a).visible()) if (ACCOUNT.equals(t.account) || (Cats.CASH.equals(t.category) && t.raw.toLowerCase().contains("atm"))) recent.add(t);
            if (!recent.isEmpty()) {
                TextView h = Ui.text(a, "Recent", 13.5f, Ui.col(a, R.color.text2), Ui.BOLD);
                h.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
                box.addView(h);
                for (int i = 0; i < Math.min(8, recent.size()); i++) box.addView(Rows.txn(a, recent.get(i), true));
            }
        } else {
            box.addView(Ui.text(a, "💵  Track your cash", 18, Ui.EXTRABOLD));
            TextView d = Ui.sub(a, "Cash taken out at an ATM goes into your wallet here instead of counting as spending straight away. "
                    + "Every few days Money asks “how much cash is left?” — the difference is counted as cash spent. "
                    + "No typing every chai or vegetable.");
            d.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
            d.setLineSpacing(Ui.dp(a, 2), 1f);
            box.addView(d);
        }
        Switch sw = new Ui.LSwitch(a);
        sw.setText("Track cash in a wallet");
        sw.setChecked(on);
        sw.setTypeface(Ui.face(a, Ui.SEMIBOLD));
        sw.setTextColor(Ui.col(a, R.color.text));
        sw.setTextSize(14);
        box.addView(sw, Ui.margins(Ui.matchWrap(), a, 0, 12, 0, 0));
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        android.app.AlertDialog d = new Ui.Popup(a).sheet().setTitle("Cash wallet").setView(sv).setPositiveButton("Done", null).create();
        sw.setOnCheckedChangeListener((b, v) -> {
            setOn(a, v);
            d.dismiss();
            if (v) askLeft(a); else sheet(a);
        });
        d.show();
    }

    static void askLeft(Activity a) {
        LinearLayout b = Ui.vbox(a);
        int pd = Ui.dp(a, 24);
        b.setPadding(pd, 0, pd, 0);
        TextView info = Ui.sub(a, "Count the cash in your wallet (roughly is fine).");
        info.setPadding(0, 0, 0, Ui.dp(a, 6));
        b.addView(info);
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(Lang.t("₹"));
        e.setTextSize(22);
        e.setTypeface(Ui.face(a, Ui.BOLD));
        b.addView(e);
        new Ui.Popup(a).setTitle("How much cash do you have?").setView(b)
                .setPositiveButton("Save", (x, y) -> {
                    long v = SmsParser.paise(e.getText().toString().trim());
                    if (v < 0) return;
                    long before = balance(a);
                    setLeft(a, v);
                    Ui.toast(a, v < before ? Fmt.plain(before - v) + " counted as cash spent" : "Wallet set to " + Fmt.plain(v));
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ "how much is left?" every few days

    public static void maybeCheckIn(Context c) {
        if (!on(c) || balance(c) < 10000 || !Alerts.canNotify(c)) return;
        long now = System.currentTimeMillis();
        if (now - p(c).getLong(CHECKED, 0) < 3L * 86400000) return;
        if (now - p(c).getLong(LAST_ATM, 0) < 86400000L) return;
        p(c).edit().putLong(CHECKED, now).apply();
        Alerts.channels(c);
        int nid = 444;
        Intent ri = new Intent(c, CashReceiver.class).putExtra("nid", nid);
        PendingIntent pr = PendingIntent.getBroadcast(c, nid, ri, PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        RemoteInput in = new RemoteInput.Builder("left").setLabel("Cash left now, e.g. 800").build();
        Notification.Action act = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notify), "Type amount", pr).addRemoteInput(in).build();
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, Alerts.CH_ASK) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle("💵 " + Fmt.plain(balance(c)) + " cash — how much is left?")
                .setContentText("Type what's in your wallet; the difference counts as cash spent.")
                .setContentIntent(po).setAutoCancel(true).addAction(act).setColor(c.getColor(R.color.accent));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(nid, b.build());
    }
}
