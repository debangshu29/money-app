package com.debangshu.money;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** First-run checklist: everything needed so you never type an amount. Also opened from Settings. */
public class SetupActivity extends Activity {

    private static final int REQ_SMS = 11, REQ_NOTIF = 12, REQ_STORAGE = 13;
    private LinearLayout list;
    private boolean askedSms;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = Ui.vbox(this);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 28), p, p);
        sv.addView(root);

        TextView h = Ui.text(this, "Let's make it automatic", 26, Ui.EXTRABOLD);
        root.addView(h);
        TextView s = Ui.sub(this, "Money reads your bank SMS and payment notifications on this phone and fills everything in by "
                + "itself — amounts, shops, balances. Nothing leaves your phone.");
        s.setTextSize(15);
        s.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 16));
        root.addView(s);

        list = Ui.vbox(this);
        root.addView(list);

        TextView done = Ui.button(this, "Done", true);
        root.addView(done, Ui.margins(Ui.matchWrap(), this, 0, 16, 0, 8));
        done.setOnClickListener(v -> finishSetup());
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void finishSetup() {
        Store.get(this).setFlag(Store.S_SETUP, true);
        if (!Setup.smsOn(this) && !Setup.listenerOn(this)) {
            new Ui.Popup(this).setTitle("Nothing is turned on yet")
                    .setMessage("Without SMS or notification access the app can't see any payments. You can turn them on later in Settings → Automatic capture.")
                    .setPositiveButton("Continue anyway", (d, w) -> goHome())
                    .setNegativeButton("Go back", null).show();
            return;
        }
        goHome();
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void build() {
        list.removeAllViews();
        int n = 1;
        step(n++, "📩", "Read bank SMS", "Every debit, credit, card spend and ATM withdrawal your bank texts you — with the balance. "
                        + "This is the main source.", Setup.smsOn(this), "Allow", v -> askSms());
        step(n++, "🔔", "Read payment notifications", "Google Pay, PhonePe, Paytm, CRED and bank apps — catches the payment the moment it "
                        + "happens and gives better names. Also a backup when an SMS is late. Only payment and Messages apps are read; "
                        + "every other notification is ignored.", Setup.listenerOn(this), "Turn on", v -> {
                    Setup.openListenerSettings(this);
                    Ui.toast(this, "Find “Money” and switch it on");
                });
        boolean notif = Setup.notifyOn(this);
        step(n++, "⚠️", "Alerts", "Charged twice, unusual payments, budget warnings, bills due, renewals and a short evening summary.",
                notif, "Allow", v -> {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    else Setup.openNotificationSettings(this);
                });
        step(n++, "🔋", "Keep working in the background", "So your phone's battery saver doesn't stop the app from getting SMS."
                        + (Setup.hasAutostart() ? " On this phone also switch on Autostart for Money." : ""),
                Setup.batteryOn(this), Setup.hasAutostart() && Setup.batteryOn(this) ? "Autostart" : "Allow", v -> {
                    if (!Setup.batteryOn(this)) Setup.askBattery(this);
                    else Setup.openAutostart(this);
                });
        long scanned = Store.get(this).prefs().getLong("lastScan", 0);
        int count = Store.get(this).visible().size();
        step(n++, "🗂️", "Bring in your past messages", scanned == 0 ? "Reads the bank SMS already on your phone, so you start with months of history."
                        : count + " transactions so far. You can read older messages again any time.",
                scanned != 0, scanned == 0 ? "Choose" : "Again", v -> importPast(this, this::build));
        boolean pw = Backup.hasPassword(this);
        step(n++, "🔒", "Private backups", pw ? "On — a locked backup goes to Documents/Money/Backups on this phone every day (newest 7 kept)."
                        : "Daily backup to this phone's storage (Documents/Money/Backups), locked with a password you choose. No Google Drive.",
                pw, "Set password", v -> askPassword(this, this::build));

        LinearLayout tip = Ui.card(this);
        tip.setBackground(Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 22)));
        tip.addView(Ui.text(this, "💡 Make sure messages keep coming", 15, Ui.BOLD));
        TextView t = Ui.sub(this, "• Banks send an SMS for every debit and credit by default. If one bank's payments never show up, "
                + "turn on SMS alerts for that account in its app or netbanking.\n"
                + "• Keep notifications ON inside Google Pay / PhonePe / Paytm (their own settings).\n"
                + "• If a message is ever missed anyway, Money spots it: the balance in the next SMS won't add up, and an "
                + "“Unrecorded” entry appears under Needs a look.");
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        tip.addView(t);
        list.addView(tip, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 4));
    }

    private void step(int n, String emoji, String title, String desc, boolean on, String action, View.OnClickListener click) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.TOP);
        TextView e = Ui.badge(this, emoji, on ? Ui.col(this, R.color.accent) : 0xFF94A3B8, 42);
        row.addView(e);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        col.addView(Ui.text(this, title, 16, Ui.BOLD));
        TextView d = Ui.sub(this, desc);
        d.setLineSpacing(Ui.dp(this, 2), 1f);
        d.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 10));
        col.addView(d);
        LinearLayout actions = Ui.hbox(this);
        if (on) {
            TextView ok = Ui.text(this, "✓ On", 14, Ui.col(this, R.color.success), Ui.BOLD);
            actions.addView(ok);
            if (action.equals("Again") || action.equals("Autostart")) {
                TextView again = Ui.chip(this, action.equals("Again") ? "Read again" : "Autostart settings", false);
                again.setOnClickListener(click);
                actions.addView(again, Ui.margins(Ui.lp(-2, -2), this, 12, 0, 0, 0));
            }
        } else {
            TextView btn = Ui.button(this, action, true);
            btn.setOnClickListener(click);
            actions.addView(btn);
        }
        col.addView(actions);
        row.addView(col, Ui.weight1());
        card.addView(row);
        list.addView(card, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
    }

    // ------------------------------------------------------------------ SMS permission

    private void askSms() {
        askedSms = true;
        requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        build();
        if (req == REQ_SMS) {
            if (Setup.smsOn(this)) {
                if (Store.get(this).prefs().getLong("lastScan", 0) == 0) importPast(this, this::build);
            } else {
                restrictedHelp(this, "SMS");
            }
        }
    }

    /** Android 13+ blocks SMS / notification access for apps installed from a file, until you allow it once. */
    public static void restrictedHelp(Activity a, String what) {
        new Ui.Popup(a).setTitle("Blocked by Android?")
                .setMessage("If no permission box appeared (or it says “Restricted setting”), Android is being careful because "
                        + "Money was installed from a file, not the Play Store. Allow it once:\n\n"
                        + "1. Tap “Open app info” below.\n"
                        + "2. Tap ⋮ (top right) → “Allow restricted settings” and confirm.\n"
                        + "3. Come back and tap Allow / Turn on again.\n\n"
                        + "If you don't see ⋮, try step 3 first, then look again.")
                .setPositiveButton("Open app info", (d, w) -> Setup.openAppInfo(a))
                .setNegativeButton("Later", null).show();
    }

    // ------------------------------------------------------------------ past messages

    public static void importPast(Activity a, Runnable after) {
        if (!Setup.smsOn(a)) { Ui.toast(a, "Allow SMS first"); return; }
        String[] opts = {"Last 3 months", "Last 6 months", "Last 12 months", "Everything on the phone"};
        int[] months = {3, 6, 12, 600};
        new Ui.Popup(a).setTitle("Read older bank messages").setItems(opts, (d, w) -> {
            long since = months[w] >= 600 ? 0 : Fmt.addMonths(System.currentTimeMillis(), -months[w]);
            runImport(a, since, after);
        }).show();
    }

    static void runImport(Activity a, long since, Runnable after) {
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        TextView label = Ui.sub(a, "Reading messages…");
        label.setPadding(0, Ui.dp(a, 8), 0, 0);
        box.addView(bar);
        box.addView(label);
        AlertDialog dlg = new Ui.Popup(a).setTitle("Bringing in your history").setView(box).setCancelable(false).create();
        dlg.show();
        new Thread(() -> {
            int found = Engine.scanInbox(a.getApplicationContext(), since, (done, total) -> a.runOnUiThread(() -> {
                bar.setIndeterminate(false);
                bar.setMax(Math.max(1, total));
                bar.setProgress(done);
                label.setText("Read " + done + " of " + total + " messages");
            }));
            Engine.recheckGaps(a.getApplicationContext());
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                dlg.dismiss();
                new Ui.Popup(a).setTitle(found == 0 ? "All caught up" : "Done!")
                        .setMessage(found == 0 ? "No new bank transactions in those messages."
                                : "Found " + found + " transactions. From now on, new ones appear by themselves.")
                        .setPositiveButton("OK", null).show();
                if (after != null) after.run();
            });
        }).start();
    }

    // ------------------------------------------------------------------ backup password

    public static void askPassword(Activity a, Runnable after) {
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        box.setPadding(p, 0, p, 0);
        TextView info = Ui.sub(a, "You'll need this password to restore a backup (for example on a new phone). "
                + "It can't be recovered — write it down somewhere safe.");
        info.setPadding(0, 0, 0, Ui.dp(a, 10));
        box.addView(info);
        EditText p1 = field(a, "Password (at least 6 characters)");
        EditText p2 = field(a, "Type it again");
        box.addView(p1);
        box.addView(p2, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        AlertDialog d = new Ui.Popup(a).setTitle(Backup.hasPassword(a) ? "Change backup password" : "Backup password")
                .setView(box).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(x -> {
            Ui.style(d, false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String s1 = p1.getText().toString(), s2 = p2.getText().toString();
                if (s1.length() < 6) { p1.setError("At least 6 characters"); return; }
                if (!s1.equals(s2)) { p2.setError("Doesn't match"); return; }
                try {
                    Backup.setPassword(a, s1.toCharArray());
                    Store.get(a).setFlag(Store.S_AUTO_BACKUP, true);
                    d.dismiss();
                    new Thread(() -> {
                        try {
                            String where = Backup.backupNow(a.getApplicationContext());
                            a.runOnUiThread(() -> Ui.toast(a, "First backup saved: " + where));
                        } catch (Exception e) {
                            a.runOnUiThread(() -> Ui.toast(a, "Backup failed: " + e.getMessage()));
                        }
                        if (after != null) a.runOnUiThread(after);
                    }).start();
                } catch (Exception e) {
                    Ui.toast(a, "Couldn't save: " + e.getMessage());
                }
            });
        });
        d.show();
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTypeface(Ui.face(c, Ui.REGULAR));
        return e;
    }
}
