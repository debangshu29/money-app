package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/** Settings: capture, alerts, privacy, backups (on this phone only), data, updates. */
public class SettingsActivity extends Activity {

    private static final int REQ_RESTORE = 41;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        LinearLayout bar = Ui.hbox(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        ImageView back = Ui.icon(this, R.drawable.ic_back, Ui.col(this, R.color.text), 22);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Settings", 24, Ui.EXTRABOLD));
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        build();
    }

    private void build() {
        box.removeAllViews();
        Store s = Store.get(this);

        LinearLayout cap = section("Automatic capture");
        boolean all = Setup.smsOn(this) && Setup.listenerOn(this);
        row(cap, "Permissions & setup", all ? "✓ SMS and notifications are on" : "⚠️ Something is off — tap to fix",
                v -> startActivity(new Intent(this, SetupActivity.class)));
        sw(cap, "Read payment app notifications", "Google Pay, PhonePe, Paytm, CRED, bank apps", Store.S_READ_APPS, null);
        sw(cap, "Read bank SMS from the Messages app too", "A backup in case Android doesn't pass an SMS to the app",
                Store.S_READ_SMS_NOTIF, null);
        sw(cap, "Read bank alert e-mails", "From Gmail's notifications — only e-mails from banks (some banks e-mail small UPI payments they don't text)",
                Store.S_READ_MAIL, null);
        sw(cap, "Ask “what for?” right after a payment", "Only for new people / places or odd amounts — regulars get their usual note by themselves",
                Store.S_ASK_REASON, null);
        boolean uw = UsageWatch.allowed(this);
        sw(cap, "“Paid anyone?” after using a UPI app", !uw ? "⚠️ Needs Usage access — tap the row below"
                        : "✓ A minute after you leave Google Pay / PhonePe… if no payment message came",
                Store.S_USAGE, on -> { if (on && !UsageWatch.allowed(this)) usageSetup(); });
        if (s.flag(Store.S_USAGE) && !uw) row(cap, "Allow Usage access", "Lets Money see WHEN a UPI app was open — never what's on the screen", v -> usageSetup());
        boolean ca = Contacts.allowed(this);
        sw(cap, "Names from your contacts", !ca ? "For UPI IDs that are just a phone number — needs Contacts permission"
                        : "✓ 9830012345@ybl → the name in your contacts (looked up on the phone, never stored)",
                Store.S_CONTACTS, on -> { if (on && !Contacts.allowed(this)) Contacts.ask(this); });
        row(cap, "Check with a bank statement", "Adds what Money missed, fills in names — PDF (even locked), CSV or Excel",
                v -> startActivity(new Intent(this, StatementActivity.class)));
        sw(cap, "Monthly statement reminder", "Early each month: “Check last month with your bank statement”", Store.S_STMT, null);
        sw(cap, "Weekly balance check", "Once a week: type your balance → any payment that sent nothing shows up while you remember it",
                Store.S_BAL_CHECK, null);
        sw(cap, "Find missed messages", "If the balance in a new SMS doesn't add up, an “Unrecorded” entry shows the difference",
                Store.S_GAPS, on -> new Thread(() -> Engine.recheckGaps(getApplicationContext())).start());
        sw(cap, "Count ATM cash as spending", Cash.on(this) ? "Not used while the cash wallet is on (Plan → Cash wallet)"
                : "Or turn on the cash wallet in Plan, which counts cash as you spend it", Store.S_ATM_SPEND,
                on -> atmChanged(on));
        row(cap, "Read older messages again", "Bring in bank SMS already on your phone", v -> SetupActivity.importPast(this, this::build));
        int unread = s.unread().size();
        row(cap, "Messages Money couldn't read" + (unread > 0 ? " (" + unread + ")" : ""),
                unread == 0 ? "None so far 👍" : "Send them to get them fixed — or add one as a payment",
                unread == 0 ? null : v -> showUnread());

        LinearLayout rm = section("Remember every payment");
        boolean locOk = Locator.allowed(this), bgOk = Locator.backgroundAllowed(this);
        sw(rm, "Save where you paid", !s.flag(Store.S_LOCATION) ? "Notes the spot when a payment is caught — see it on a map later"
                        : !locOk ? "⚠️ Location permission is off — tap here to allow"
                        : !bgOk ? "⚠️ Choose “Allow all the time” so it works when the app is closed"
                        : "✓ On — one quick location check per payment",
                Store.S_LOCATION, on -> { if (on) Locator.ask(this); build(); });
        if (s.flag(Store.S_LOCATION) && (!locOk || !bgOk)) row(rm, "Allow location", "Needed for “Save where you paid”", v -> Locator.ask(this));
        sw(rm, "Show area names", "“near Garia Station Road” — asks Google's address lookup. Off = map pin only, nothing sent",
                Store.S_PLACE_NAMES, null);
        sw(rm, "Ask “Who was it?”", "When a payment comes without a name, type it right in the notification (with a guess when Money has one)",
                Store.S_ASK_NAME, null);

        LinearLayout al = section("Alerts");
        int hour = s.prefs().getInt(Alerts.P_DAILY_HOUR, 21);
        sw(al, "Evening summary", "What you spent today, at " + hourName(hour), Store.S_DAILY, on -> Alerts.schedule(this));
        row(al, "Summary time", hourName(hour), v -> pickHour());
        sw(al, "Unusual payments & double charges", "Much bigger than normal, or the same amount twice in minutes", Store.S_UNUSUAL, null);
        sw(al, "Refunds, renewals & EMIs", "Failed payments not back yet, plan / insurance / PUC expiry, FASTag low, return & warranty dates, EMI due", Store.S_REMIND, null);
        sw(al, "Fraud watch", "Late-night, foreign, or a big first payment to someone new — asks “Was this you?”", Store.S_FRAUD, null);
        sw(al, "Budget warnings", "At 80% and 100% of a budget", Store.S_BUDGET, null);
        sw(al, "Bills due & renewals", "Card bills 3 days before, subscriptions 2 days before", Store.S_BILLS, null);
        sw(al, "A note for every payment", "Quiet notification each time a payment is caught", Store.S_EACH, null);
        row(al, "Notification settings", "Sound, vibration and more for each kind", v -> Setup.openNotificationSettings(this));

        LinearLayout pv = section("Privacy");
        sw(pv, "App lock", Lock.deviceSecure(this) ? "Fingerprint, face or screen lock to open" : "Set a screen lock on your phone first",
                Store.S_LOCK, on -> { if (on) Lock.done(); Widget.refresh(this); });
        sw(pv, "Hide amounts when opening", "Every Home section starts as ₹ •••• — tap a section's eye to show it", Store.S_HIDE,
                on -> { if (on) HomePage.hideAll(this); Widget.refresh(this); });
        sw(pv, "Block screenshots", "Also hides the app's content in recent apps", Store.S_SECURE, on -> Ui.bars(this));
        TextView info = Ui.sub(this, "🔒 Everything stays on this phone. Your messages and money data are never uploaded. "
                + "The internet is used only to check for new versions of the app.");
        info.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4));
        pv.addView(info);

        LinearLayout bk = section("Backups (this phone only)");
        boolean pw = Backup.hasPassword(this);
        row(bk, pw ? "Change backup password" : "Set backup password", pw ? "Backups are locked with it" : "Needed for backups — no Google Drive",
                v -> SetupActivity.askPassword(this, this::build));
        sw(bk, "Daily automatic backup", Backup.FOLDER + " · newest 7 kept", Store.S_AUTO_BACKUP, null);
        long last = Backup.lastBackup(this);
        row(bk, "Back up now", last == 0 ? "No backup yet" : "Last: " + Fmt.dayTime(last) + "\n" + Backup.lastWhere(this), v -> backupNow());
        row(bk, "Restore from a backup", "Pick a .mbak file (from this phone or copied from your old one)", v -> pickRestore());
        row(bk, "Export spreadsheet (CSV)", "All transactions to Documents/Money — opens in Excel / Sheets", v -> exportCsv());
        TextView tip = Ui.sub(this, "Tip: backups live on this phone. Copy the Documents/Money/Backups folder to your PC now and then, "
                + "so a lost phone doesn't mean lost history.");
        tip.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4));
        bk.addView(tip);

        LinearLayout dt = section("Your data");
        row(dt, "Accounts", s.accounts().size() + " found automatically · long-press one on Home to rename, correct, merge or delete", null);
        List<String> del = s.deletedAccounts();
        if (!del.isEmpty()) row(dt, "Deleted accounts (" + del.size() + ")", "Tap to bring one back", v -> {
            new Ui.Popup(this).setTitle("Bring back").setItems(del.toArray(new String[0]), (d, w) -> {
                Store.get(this).restoreAccount(del.get(w));
                new Thread(() -> Engine.recheckGaps(getApplicationContext())).start();
                Ui.toast(this, del.get(w) + " is back");
                build();
            }).show();
        });
        row(dt, "Delete all data", "Erase every transaction, account, budget and rule", v -> deleteAll());

        LinearLayout up = section("App updates");
        sw(up, "Check for updates automatically", "Shows a popup when a new version is ready", Store.S_AUTO_UPDATE,
                on -> Updater.scheduleBackgroundChecks(this));
        row(up, "Check now", "You have version " + Updater.currentVersionName(this), v -> Updater.maybePrompt(this, true));
        String with = Updater.installWithLabel(this);
        row(up, "Open updates with", with == null ? "Ask next time" : with + " (tap to ask next time)", v -> {
            Updater.forgetInstallApp(this);
            build();
        });
        row(up, "Delete old update files", "Money.apk files your browser downloaded", v -> Updater.pickOldFilesToDelete(this));
        row(up, "Share Money with friends & family", "Sends the download link", v -> share());
    }

    // ------------------------------------------------------------------ building blocks

    private LinearLayout section(String title) {
        TextView t = Ui.text(this, title, 13, Ui.col(this, R.color.accent), Ui.BOLD);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 18), 0, Ui.dp(this, 6));
        box.addView(t);
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        box.addView(c);
        return c;
    }

    private void row(LinearLayout c, String title, String sub, View.OnClickListener l) {
        LinearLayout r = Ui.vbox(this);
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        r.addView(Ui.text(this, title, 15, Ui.SEMIBOLD));
        if (sub != null && !sub.isEmpty()) r.addView(Ui.text(this, sub, 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
        if (l != null) {
            r.setBackground(Ui.rippleRound(this, 0x00000000, 14));
            r.setOnClickListener(l);
        }
        c.addView(r);
    }

    interface OnSwitch { void on(boolean on); }

    private void sw(LinearLayout c, String title, String sub, String key, OnSwitch after) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8));
        LinearLayout col = Ui.vbox(this);
        col.addView(Ui.text(this, title, 15, Ui.SEMIBOLD));
        col.addView(Ui.text(this, sub, 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
        r.addView(col, Ui.weight1());
        Switch s = new Switch(this);
        s.setChecked(Store.get(this).flag(key));
        s.setOnCheckedChangeListener((b, on) -> {
            if (key.equals(Store.S_LOCK) && on && !Lock.deviceSecure(this)) {
                b.setChecked(false);
                Ui.toast(this, "Set a screen lock (PIN, pattern or fingerprint) in your phone's settings first");
                return;
            }
            Store.get(this).setFlag(key, on);
            if (after != null) after.on(on);
        });
        r.addView(s);
        r.setOnClickListener(v -> s.toggle());
        c.addView(r);
    }

    private static String hourName(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (h < 12 ? " am" : " pm");
    }

    private void pickHour() {
        int[] hours = {18, 19, 20, 21, 22, 23};
        String[] names = new String[hours.length];
        for (int i = 0; i < hours.length; i++) names[i] = hourName(hours[i]);
        new Ui.Popup(this).setTitle("Evening summary at").setItems(names, (d, w) -> {
            Store.get(this).prefs().edit().putInt(Alerts.P_DAILY_HOUR, hours[w]).apply();
            Alerts.schedule(this);
            build();
        }).show();
    }

    private void atmChanged(boolean spend) {
        new Thread(() -> {
            Store s = Store.get(getApplicationContext());
            s.begin();
            try {
                for (Txn t : s.txns()) {
                    if (!Cats.CASH.equals(t.category) || t.credit || t.kind == Txn.IGNORED) continue;
                    if (!t.raw.toLowerCase(java.util.Locale.ROOT).contains("atm")) continue;
                    t.kind = spend ? Txn.NORMAL : Txn.TRANSFER;
                    s.update(t);
                }
            } finally {
                s.end();
            }
        }).start();
    }

    private void usageSetup() {
        new Ui.Popup(this).setTitle("Allow Usage access")
                .setMessage("Money only learns WHEN Google Pay, PhonePe or another UPI app was open — not what you did there.\n\n"
                        + "If you leave the app and no payment message arrives within a minute, Money asks “Paid anyone?”. "
                        + "Quick visits (under 12 seconds) are ignored.\n\nNext screen: find Money → turn on “Permit usage access”.")
                .setPositiveButton("Open settings", (d, w) -> UsageWatch.openSettings(this))
                .setNegativeButton("Not now", null).show();
    }

    private void contactsResult(int req) {
        if (req == Contacts.REQ && Contacts.allowed(this)) {
            new Thread(() -> {
                int n = Contacts.nameAll(getApplicationContext());
                runOnUiThread(() -> { if (n > 0) Ui.toast(this, n + (n == 1 ? " payment" : " payments") + " named from your contacts"); build(); });
            }).start();
        }
    }

    private void showUnread() {
        List<String[]> list = Store.get(this).unread();
        LinearLayout b = Ui.vbox(this);
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        TextView info = Ui.sub(this, "These look like bank payments, but their wording is new to Money. Share them (to Claude, for example) "
                + "so the reader can learn them — the next version then picks them up by itself. They stay on this phone unless you share.");
        info.setPadding(0, 0, 0, Ui.dp(this, 8));
        b.addView(info);
        StringBuilder all = new StringBuilder();
        for (String[] u : list) {
            LinearLayout c = Ui.vbox(this);
            c.setBackground(Ui.round(Ui.col(this, R.color.surface2), Ui.dp(this, 14)));
            c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            c.addView(Ui.text(this, u[1] + " · " + Fmt.dayTime(Long.parseLong(u[0])), 12, Ui.col(this, R.color.text2), Ui.BOLD));
            TextView body = Ui.text(this, u[2], 13, Ui.REGULAR);
            body.setTextIsSelectable(true);
            c.addView(body);
            b.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 4));
            all.append(u[1]).append(" | ").append(u[2].replace("\n", " / ")).append("\n");
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(b);
        new Ui.Popup(this).sheet().setTitle("Couldn't read (" + list.size() + ")").setView(Ui.capHeight(sv, 0.7f))
                .setPositiveButton("Share all", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, "Bank SMS that Money couldn't read:\n\n" + all);
                    startActivity(Intent.createChooser(i, "Share messages"));
                })
                .setNeutralButton("Clear list", (d, w) -> { Store.get(this).clearUnread(); build(); })
                .setNegativeButton("Close", null).show();
    }

    // ------------------------------------------------------------------ backups

    private void backupNow() {
        if (!Backup.hasPassword(this)) { SetupActivity.askPassword(this, this::build); return; }
        Ui.toast(this, "Backing up…");
        new Thread(() -> {
            try {
                String where = Backup.backupNow(getApplicationContext());
                runOnUiThread(() -> { Ui.toast(this, "Saved: " + where); build(); });
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Backup failed: " + e.getMessage()));
            }
        }).start();
    }

    private void exportCsv() {
        new Ui.Popup(this).setTitle("Export spreadsheet?")
                .setMessage("The CSV file is NOT password-protected — anyone with the file can read it. Delete it when you're done.")
                .setPositiveButton("Export", (d, w) -> new Thread(() -> {
                    try {
                        String where = Backup.exportCsv(getApplicationContext());
                        runOnUiThread(() -> Ui.toast(this, "Saved: " + where));
                    } catch (Exception e) {
                        runOnUiThread(() -> Ui.toast(this, "Export failed: " + e.getMessage()));
                    }
                }).start())
                .setNegativeButton("Cancel", null).show();
    }

    private void pickRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (Build.VERSION.SDK_INT >= 26) {
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:" + Backup.FOLDER));
        }
        try {
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            Ui.toast(this, "No file picker found");
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == Locator.REQ_FG && Locator.allowed(this)) Locator.ask(this);
        contactsResult(req);
        build();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Updater.onCleanupResult(this, req, res, data);
        if (req != REQ_RESTORE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        byte[] file;
        try {
            file = Backup.read(this, u);
        } catch (Exception e) {
            Ui.toast(this, "Couldn't read the file");
            return;
        }
        if (!BackupCrypt.isEncrypted(file)) { Ui.toast(this, "That isn't a Money backup (.mbak)"); return; }
        askRestorePassword(file);
    }

    private void askRestorePassword(byte[] file) {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        EditText e = SetupActivity.field(this, "Backup password");
        b.addView(e);
        new Ui.Popup(this).setTitle("Unlock backup").setView(b)
                .setPositiveButton("Open", (d, w) -> {
                    char[] pw = e.getText().toString().toCharArray();
                    Ui.toast(this, "Opening…");
                    new Thread(() -> {
                        try {
                            JSONObject o = Backup.open(file, pw);
                            int n = o.optJSONArray("txns") == null ? 0 : o.optJSONArray("txns").length();
                            runOnUiThread(() -> confirmRestore(o, n, pw));
                        } catch (BackupCrypt.WrongPassword wp) {
                            runOnUiThread(() -> { Ui.toast(this, "Wrong password"); askRestorePassword(file); });
                        } catch (Exception ex) {
                            runOnUiThread(() -> Ui.toast(this, "Couldn't open: " + ex.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmRestore(JSONObject o, int n, char[] pw) {
        new Ui.Popup(this).setTitle("Restore " + n + " transactions?")
                .setMessage("Everything in the app now is replaced by this backup. New SMS that arrived after the backup are read again automatically.")
                .setPositiveButton("Restore", (d, w) -> {
                    try {
                        Store.get(this).replaceAll(o);
                        if (!Backup.hasPassword(this)) Backup.setPassword(this, pw);
                        Store.get(this).setFlag(Store.S_SETUP, true);
                        new Thread(() -> {
                            Engine.catchUp(getApplicationContext());
                            Engine.recheckGaps(getApplicationContext());
                        }).start();
                        Ui.toast(this, "Restored");
                        build();
                    } catch (Exception e) {
                        Ui.toast(this, "Restore failed: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ data

    private void deleteAll() {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        TextView t = Ui.sub(this, "This erases everything in the app (backup files in Documents/Money stay). Type DELETE to confirm.");
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        b.addView(t);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.addView(e);
        AlertDialog d = new Ui.Popup(this).setTitle("Delete all data?").setView(b)
                .setPositiveButton("Delete", (x, w) -> {
                    if (!e.getText().toString().trim().equalsIgnoreCase("DELETE")) { Ui.toast(this, "Not deleted"); return; }
                    Store.get(this).wipe();
                    Store.get(this).prefs().edit().remove("lastScan").apply();
                    Ui.toast(this, "All data deleted");
                    build();
                })
                .setNegativeButton("Cancel", null).create();
        d.show();
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Money — tracks your spending automatically from bank SMS & UPI notifications. "
                + "Everything stays on your phone.\nDownload: " + Updater.APK_URL);
        startActivity(Intent.createChooser(i, "Share Money"));
    }
}
