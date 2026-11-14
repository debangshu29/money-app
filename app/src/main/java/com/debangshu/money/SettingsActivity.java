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
    private TextView titleView;

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
        titleView = Ui.text(this, "Settings", 24, Ui.EXTRABOLD);
        bar.addView(titleView);
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

    /** Which page: null = the main list; "capture", "ask", "names", "privacy", "data", "about". */
    private String page;

    private static final String[][] PAGES = {
            // id, emoji, colour, title, short line
            {"capture", "📥", "#0E9F6E", "Payment capture", "SMS, payment apps, e-mails"},
            {"ask", "🔔", "#F59E0B", "Questions & reminders", "“What for?”, bills, summaries"},
            {"names", "📍", "#0EA5E9", "Names & places", "Who you paid, and where"},
            {"privacy", "🔒", "#6366F1", "Privacy & security", "App lock, hide amounts"},
            {"data", "💾", "#8B5CF6", "Backup & data", "Backups, export, bank statements"},
            {"learn", "🎓", "#0B6F86", "Tutorials", "Quick tour and how-tos"},
            {"about", "⬆️", "#64748B", "Updates & about", "Version, share with family"},
    };

    private void build() {
        box.removeAllViews();
        if (page == null) page = getIntent().getStringExtra("page");
        String title = "Settings";
        for (String[] p : PAGES) if (p[0].equals(page)) title = p[3];
        if (titleView != null) titleView.setText(title);
        if (page == null) main();
        else switch (page) {
            case "capture": capture(); break;
            case "ask": ask(); break;
            case "names": names(); break;
            case "privacy": privacy(); break;
            case "data": data(); break;
            case "learn": TutorialActivity.fill(this, box); break;
            default: about(); break;
        }
    }

    private void open(String id) { startActivity(new Intent(this, SettingsActivity.class).putExtra("page", id)); }

    // ---- the main page: a status card + six groups
    private void main() {
        Store s = Store.get(this);
        List<String> fix = new java.util.ArrayList<>();
        if (!Setup.smsOn(this)) fix.add("Reading bank SMS is off");
        if (!Setup.listenerOn(this)) fix.add("Payment notifications are off");
        if (s.flag(Store.S_USAGE) && !UsageWatch.allowed(this)) fix.add("“Paid anyone?” needs Usage access");
        if (s.flag(Store.S_LOCATION) && (!Locator.allowed(this) || !Locator.backgroundAllowed(this))) fix.add("Location needs “Allow all the time”");
        if (!Backup.hasPassword(this)) fix.add("No backup password yet");

        LinearLayout st = Ui.card(this);
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(android.view.Gravity.CENTER_VERTICAL);
        boolean ok = fix.isEmpty();
        st.setBackground(Ui.round(Ui.col(this, ok ? R.color.accent_soft : R.color.warn_soft), Ui.dp(this, 22)));
        st.addView(Ui.iconBadge(this, ok ? "🛡️" : "⚡", ok ? 0xFF0E9F6E : 0xFFD97706, 44));
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        col.addView(Ui.text(this, ok ? "All set" : fix.size() == 1 ? "1 thing to fix" : fix.size() + " things to fix", 16, Ui.BOLD));
        col.addView(Ui.text(this, ok ? "Money is catching your payments by itself" : fix.get(0), 13, Ui.col(this, R.color.text2), Ui.REGULAR));
        st.addView(col, Ui.weight1());
        if (!ok) {
            st.setOnClickListener(v -> {
                if (!Setup.smsOn(this) || !Setup.listenerOn(this)) startActivity(new Intent(this, SetupActivity.class));
                else if (s.flag(Store.S_USAGE) && !UsageWatch.allowed(this)) usageSetup();
                else if (s.flag(Store.S_LOCATION) && (!Locator.allowed(this) || !Locator.backgroundAllowed(this))) Locator.ask(this);
                else SetupActivity.askPassword(this, this::build);
            });
        }
        box.addView(st, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 14));

        LinearLayout lc = Ui.card(this);
        lc.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        String cur = Lang.NAMES[java.util.Arrays.asList(Lang.CODES).indexOf(Lang.code())];
        lc.addView(Ui.navRow(this, "🌐", 0xFF0EA5E9, "Language · भाषा · ভাষা", cur, v -> Lang.choose(this)));
        sw(lc, "Simple mode", "Bigger text, just 3 tabs — easy for parents", Store.S_SIMPLE, on -> {
            Ui.applySimple(this);
            recreate();
        });
        box.addView(lc, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        for (String[] p : PAGES) c.addView(Ui.navRow(this, p[1], android.graphics.Color.parseColor(p[2]), p[3], p[4], v -> open(p[0])));
        box.addView(c);

        TextView info = Ui.sub(this, "Everything stays on this phone. Nothing is uploaded — the internet is used only to check for app updates.");
        info.setGravity(android.view.Gravity.CENTER);
        info.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), 0);
        box.addView(info);
        TextView ver = Ui.text(this, "Money " + Updater.currentVersionName(this), 12, Ui.col(this, R.color.hint), Ui.SEMIBOLD);
        ver.setGravity(android.view.Gravity.CENTER);
        ver.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(ver);
    }

    // ---- Payment capture
    private void capture() {
        Store s = Store.get(this);
        LinearLayout c = section(null);
        boolean all = Setup.smsOn(this) && Setup.listenerOn(this);
        row(c, "Permissions", all ? "✓ SMS and notifications on" : "⚠️ Something is off — tap to fix", v -> startActivity(new Intent(this, SetupActivity.class)));
        sw(c, "Payment app notifications", "Google Pay, PhonePe, bank apps", Store.S_READ_APPS, null);
        sw(c, "Bank SMS via the Messages app", "A backup if an SMS is missed", Store.S_READ_SMS_NOTIF, null);
        sw(c, "Bank alert e-mails", "From Gmail notifications", Store.S_READ_MAIL, null);

        LinearLayout m = section("Missed payments");
        sw(m, "Find missed payments", "Balance gaps show as “Unrecorded”", Store.S_GAPS,
                on -> new Thread(() -> Engine.recheckGaps(getApplicationContext())).start());
        sw(m, "Count ATM cash as spending", Cash.on(this) ? "Off while the cash wallet is on" : "Or use the cash wallet in Plan", Store.S_ATM_SPEND, this::atmChanged);
        row(m, "Read older SMS again", "Bring in messages already on the phone", v -> SetupActivity.importPast(this, this::build));
        int unread = s.unread().size();
        row(m, "Messages Money couldn't read" + (unread > 0 ? " (" + unread + ")" : ""), unread == 0 ? "None so far 👍" : "Share them to get them fixed",
                unread == 0 ? null : v -> showUnread());
    }

    // ---- Questions & reminders
    private void ask() {
        Store s = Store.get(this);
        LinearLayout a = section("Right after a payment");
        sw(a, "Ask “what for?”", "Only for new or unusual payments", Store.S_ASK_REASON, null);
        sw(a, "Ask “Who was it?”", "When a payment has no name", Store.S_ASK_NAME, null);
        boolean uw = UsageWatch.allowed(this);
        sw(a, "“Paid anyone?”", uw ? "After you leave a UPI app" : "⚠️ Needs Usage access", Store.S_USAGE,
                on -> { if (on && !UsageWatch.allowed(this)) usageSetup(); });
        if (s.flag(Store.S_USAGE) && !uw) row(a, "Allow Usage access", "Only WHEN an app was open", v -> usageSetup());

        LinearLayout r = section("Reminders");
        int hour = s.prefs().getInt(Alerts.P_DAILY_HOUR, 21);
        sw(r, "Evening summary", "Today's spending at " + hourName(hour), Store.S_DAILY, on -> Alerts.schedule(this));
        if (s.flag(Store.S_DAILY)) row(r, "Summary time", hourName(hour), v -> pickHour());
        sw(r, "Bills & subscriptions", "A few days before they're due", Store.S_BILLS, null);
        sw(r, "Renewals, refunds & EMIs", "Plan expiry, insurance, PUC, EMI", Store.S_REMIND, null);
        sw(r, "Budget warnings", "At 80% and 100%", Store.S_BUDGET, null);
        sw(r, "Weekly balance check", "Catches payments that sent nothing", Store.S_BAL_CHECK, null);
        sw(r, "Monthly statement reminder", "Check last month with the bank", Store.S_STMT, null);

        LinearLayout f = section("Safety");
        sw(f, "Unusual payments", "Much bigger than usual, or twice", Store.S_UNUSUAL, null);
        sw(f, "Fraud watch", "Asks “Was this you?”", Store.S_FRAUD, null);
        sw(f, "A note for every payment", "A quiet notification each time", Store.S_EACH, null);
        row(f, "Sounds & vibration", "Android notification settings", v -> Setup.openNotificationSettings(this));
    }

    // ---- Names & places
    private void names() {
        Store s = Store.get(this);
        LinearLayout c = section(null);
        boolean locOk = Locator.allowed(this), bgOk = Locator.backgroundAllowed(this);
        sw(c, "Save where you paid", !s.flag(Store.S_LOCATION) ? "See payments on a map" : !locOk || !bgOk ? "⚠️ Allow location “all the time”" : "✓ On",
                Store.S_LOCATION, on -> { if (on) Locator.ask(this); build(); });
        if (s.flag(Store.S_LOCATION) && (!locOk || !bgOk)) row(c, "Allow location", "Needed for saving places", v -> Locator.ask(this));
        sw(c, "Show area names", "“near Garia Station Road”", Store.S_PLACE_NAMES, null);
        boolean ca = Contacts.allowed(this);
        sw(c, "Names from contacts", ca ? "✓ For phone-number UPI IDs" : "For phone-number UPI IDs", Store.S_CONTACTS,
                on -> { if (on && !Contacts.allowed(this)) Contacts.ask(this); });
    }

    // ---- Privacy & security
    private void privacy() {
        LinearLayout c = section(null);
        sw(c, "App lock", Lock.deviceSecure(this) ? "Fingerprint, face or PIN" : "Set a phone screen lock first", Store.S_LOCK,
                on -> { if (on) Lock.done(); Widget.refresh(this); });
        sw(c, "Hide amounts when opening", "Shows ₹ •••• until you tap", Store.S_HIDE, on -> { if (on) HomePage.hideAll(this); Widget.refresh(this); });
        sw(c, "Block screenshots", "Also hides Money in recent apps", Store.S_SECURE, on -> Ui.bars(this));
        TextView info = Ui.sub(this, "🔒 Your messages and money data never leave this phone.");
        info.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), 0);
        box.addView(info);
    }

    // ---- Backup & data
    private void data() {
        Store s = Store.get(this);
        LinearLayout b = section("Backup");
        boolean pw = Backup.hasPassword(this);
        long last = Backup.lastBackup(this);
        row(b, "Back up now", last == 0 ? "No backup yet" : "Last: " + Fmt.dayTime(last), v -> backupNow());
        sw(b, "Daily automatic backup", "Newest 7 kept, on this phone", Store.S_AUTO_BACKUP, null);
        row(b, pw ? "Backup password" : "Set a backup password", pw ? "Backups are locked with it" : "Needed for backups", v -> SetupActivity.askPassword(this, this::build));
        row(b, "Restore from a backup", "Pick a .mbak file", v -> pickRestore());

        LinearLayout d = section("Your data");
        row(d, "Check with a bank statement", "PDF, CSV or Excel", v -> startActivity(new Intent(this, StatementActivity.class)));
        row(d, "Export to a spreadsheet", "CSV in Documents/Money", v -> exportCsv());
        List<String> del = s.deletedAccounts();
        if (!del.isEmpty()) row(d, "Deleted accounts (" + del.size() + ")", "Tap to bring one back", v ->
                new Ui.Popup(this).setTitle("Bring back").setItems(del.toArray(new String[0]), (x, w) -> {
                    Store.get(this).restoreAccount(del.get(w));
                    new Thread(() -> Engine.recheckGaps(getApplicationContext())).start();
                    Ui.toast(this, del.get(w) + " is back");
                    build();
                }).show());
        LinearLayout z = section(null);
        TextView wipe = Ui.text(this, "Delete all data", 15, Ui.col(this, R.color.danger), Ui.SEMIBOLD);
        wipe.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
        wipe.setBackground(Ui.rippleRound(this, 0x00000000, 14));
        wipe.setOnClickListener(v -> deleteAll());
        z.addView(wipe);
        TextView tip = Ui.sub(this, "Tip: copy Documents/Money/Backups to your PC now and then.");
        tip.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), 0);
        box.addView(tip);
    }

    // ---- Updates & about
    private void about() {
        LinearLayout u = section(null);
        row(u, "Check for updates", "You have version " + Updater.currentVersionName(this), v -> Updater.maybePrompt(this, true));
        sw(u, "Check automatically", "Shows a popup when ready", Store.S_AUTO_UPDATE, on -> Updater.scheduleBackgroundChecks(this));
        String with = Updater.installWithLabel(this);
        if (with != null) row(u, "Updates open with", with + " · tap to ask next time", v -> { Updater.forgetInstallApp(this); build(); });
        row(u, "Delete old update files", "Money.apk files in Downloads", v -> Updater.pickOldFilesToDelete(this));
        LinearLayout f = section(null);
        row(f, "Share Money with family", "Sends the download link", v -> share());
    }

    // ------------------------------------------------------------------ building blocks

    private LinearLayout section(String title) {
        if (title != null) box.addView(Ui.label(this, title));
        else box.addView(new View(this), Ui.lp(1, Ui.dp(this, 10)));
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        box.addView(c);
        return c;
    }

    private void row(LinearLayout c, String title, String sub, View.OnClickListener l) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
        LinearLayout col = Ui.vbox(this);
        col.addView(Ui.text(this, title, 15, Ui.SEMIBOLD));
        if (sub != null && !sub.isEmpty()) col.addView(Ui.text(this, sub, 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
        r.addView(col, Ui.weight1());
        if (l != null) {
            r.addView(Ui.chevron(this));
            r.setBackground(Ui.rippleRound(this, 0x00000000, 14));
            r.setOnClickListener(l);
        }
        c.addView(r);
    }

    interface OnSwitch { void on(boolean on); }

    private void sw(LinearLayout c, String title, String sub, String key, OnSwitch after) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10));
        LinearLayout col = Ui.vbox(this);
        col.addView(Ui.text(this, title, 15, Ui.SEMIBOLD));
        col.addView(Ui.text(this, sub, 12.5f, Ui.col(this, R.color.text2), Ui.REGULAR));
        r.addView(col, Ui.weight1());
        Switch s = new Ui.LSwitch(this);
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
        TextView info = Ui.sub(this, "These look like payments, but their wording is new to Money. Share them with whoever set up Money for you "
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
