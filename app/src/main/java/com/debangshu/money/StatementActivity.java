package com.debangshu.money;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Monthly bank statement check: open the statement (PDF — password ok, CSV or Excel) → Money compares it with what it
 * recorded → adds what it missed (with name / UPI ID / your note from the statement), fills in details, and lists anything
 * Money has that the bank doesn't. Everything happens on the phone; the file isn't kept.
 */
public class StatementActivity extends Screen {

    private static final int REQ_PICK = 71;
    private byte[] data;
    private String fileName = "", mime = "";
    private StatementParser.Result parsed;
    private String status;          // a message to show (reading… / couldn't read …)
    private Reconcile.Plan done;    // after applying
    private String doneAccount;
    private int doneFilled, doneHidden;

    @Override
    String title() { return "Bank statement"; }

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        Uri u = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        if (u != null) load(u);
    }

    @Override
    void build() {
        if (done != null) { result(); return; }
        if (parsed != null) { summary(); return; }
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "📄", 30, Ui.REGULAR));
        c.addView(Ui.text(this, "Check a month against your bank", 17, Ui.BOLD));
        c.addView(Ui.sub(this, "Download last month's statement from your bank app, net banking or the e-mail the bank sends "
                + "(PDF — even password-protected — CSV or Excel). Money compares it with what it recorded:\n\n"
                + "• adds payments it missed (with the name, UPI ID and the note you typed in the UPI app, when the bank shows it)\n"
                + "• fills in missing names and UPI IDs\n"
                + "• shows anything Money has that the bank doesn't (a double entry?)\n\n"
                + "It all happens on this phone; the file isn't kept."));
        TextView pick = Ui.button(this, "Choose the statement file", true);
        pick.setOnClickListener(v -> pick());
        c.addView(pick, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        TextView tip = Ui.sub(this, "Tip: you can also share the PDF to Money from your files or mail app.");
        tip.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(tip);
        add(c);
        if (status != null) {
            LinearLayout s = Ui.card(this);
            s.addView(Ui.text(this, status, 14, Ui.SEMIBOLD));
            add(s);
        }
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "text/csv", "text/comma-separated-values", "text/plain",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream"});
        try { startActivityForResult(i, REQ_PICK); } catch (Exception e) { Ui.toast(this, "No file picker found"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == REQ_PICK && res == RESULT_OK && d != null && d.getData() != null) load(d.getData());
    }

    // ------------------------------------------------------------------ reading

    private void load(Uri u) {
        status = "Reading the statement…";
        parsed = null;
        done = null;
        if (box != null) rebuild();
        new Thread(() -> {
            try {
                mime = getContentResolver().getType(u);
                if (mime == null) mime = "";
                fileName = name(u);
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 25 * 1024 * 1024) break; }
                    data = bo.toByteArray();
                }
                read(null);
            } catch (Exception e) {
                ui("Couldn't open the file: " + e.getMessage());
            }
        }).start();
    }

    private String name(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String p = u.getLastPathSegment();
        return p == null ? "" : p;
    }

    private void read(String password) {
        String lname = fileName.toLowerCase(Locale.ROOT);
        try {
            StatementParser.Result r;
            boolean pdf = mime.contains("pdf") || lname.endsWith(".pdf") || (data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F');
            boolean xlsx = lname.endsWith(".xlsx") || mime.contains("spreadsheetml") || (data.length > 2 && data[0] == 'P' && data[1] == 'K');
            if (pdf) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
                com.tom_roush.pdfbox.pdmodel.PDDocument doc;
                try {
                    doc = password == null ? com.tom_roush.pdfbox.pdmodel.PDDocument.load(new ByteArrayInputStream(data))
                            : com.tom_roush.pdfbox.pdmodel.PDDocument.load(new ByteArrayInputStream(data), password);
                } catch (java.io.IOException e) {
                    if (e.getClass().getSimpleName().contains("Password") || String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("password")) {
                        runOnUiThread(() -> askPassword(password != null));
                        return;
                    }
                    throw e;
                }
                try {
                    doc.setAllSecurityToBeRemoved(true);
                    com.tom_roush.pdfbox.text.PDFTextStripper ts = new com.tom_roush.pdfbox.text.PDFTextStripper();
                    ts.setSortByPosition(true);
                    r = StatementParser.fromText(ts.getText(doc));
                } finally {
                    doc.close();
                }
            } else if (xlsx) {
                r = fromXlsx(data);
            } else if (lname.endsWith(".xls")) {
                ui("Old Excel files (.xls) can't be read — download the statement as PDF or CSV instead.");
                return;
            } else {
                r = fromCsv(new String(data, "UTF-8"));
            }
            if (r.rows.isEmpty()) {
                ui("Couldn't find any payments in “" + fileName + "”. Which bank is it? Tell Claude the bank and the format (PDF / CSV), "
                        + "and the reader will be taught that layout.");
                return;
            }
            parsed = r;
            status = null;
            runOnUiThread(this::rebuild);
        } catch (Throwable e) {
            ui("Couldn't read this statement (" + e.getClass().getSimpleName() + "). Try the CSV / Excel download from net banking, "
                    + "or tell Claude which bank it's from.");
        }
    }

    private void ui(String msg) {
        status = msg;
        runOnUiThread(this::rebuild);
    }

    private void askPassword(boolean wrong) {
        LinearLayout b = form();
        EditText e = field("PDF password", false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        b.addView(e);
        TextView n = Ui.sub(this, (wrong ? "That password didn't work. " : "")
                + "Banks usually use a mix of your name, date of birth or customer ID — the e-mail with the statement says which. Money doesn't save it.");
        n.setPadding(0, Ui.dp(this, 8), 0, 0);
        b.addView(n);
        new Ui.Popup(this).setTitle("This statement is locked").setView(b)
                .setPositiveButton("Open", (d, w) -> {
                    String pw = e.getText().toString();
                    status = "Reading the statement…";
                    rebuild();
                    new Thread(() -> read(pw)).start();
                })
                .setNegativeButton("Cancel", (d, w) -> { status = null; rebuild(); }).show();
    }

    static StatementParser.Result fromCsv(String text) {
        String[] lines = text.split("\\r?\\n");
        char sep = ',';
        int best = 0;
        for (char c : new char[]{',', ';', '\t', '|'}) {
            int n = 0;
            for (int i = 0; i < Math.min(lines.length, 40); i++) n += lines[i].length() - lines[i].replace(String.valueOf(c), "").length();
            if (n > best) { best = n; sep = c; }
        }
        List<List<String>> table = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            List<String> row = splitCsv(lines[i], sep);
            table.add(row);
            if (i < 25) head.append(lines[i]).append('\n');
        }
        return StatementParser.fromTable(table, head.toString());
    }

    private static List<String> splitCsv(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else q = !q; }
            else if (ch == sep && !q) { out.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString().trim());
        return out;
    }

    /** .xlsx = a zip of XML files: shared strings + the first sheet's cells. */
    private static StatementParser.Result fromXlsx(byte[] data) throws Exception {
        List<String> shared = new ArrayList<>();
        byte[] sheet = null;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            Map<String, byte[]> files = new HashMap<>();
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("xl/sharedStrings.xml") || n.matches("xl/worksheets/sheet[0-9]+\\.xml")) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int k;
                    while ((k = z.read(buf)) > 0) bo.write(buf, 0, k);
                    files.put(n, bo.toByteArray());
                }
            }
            byte[] ss = files.get("xl/sharedStrings.xml");
            if (ss != null) {
                XmlPullParser p = android.util.Xml.newPullParser();
                p.setInput(new ByteArrayInputStream(ss), "UTF-8");
                StringBuilder cur = null;
                for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                    if (ev == XmlPullParser.START_TAG && p.getName().equals("si")) cur = new StringBuilder();
                    else if (ev == XmlPullParser.TEXT && cur != null) cur.append(p.getText());
                    else if (ev == XmlPullParser.END_TAG && p.getName().equals("si") && cur != null) { shared.add(cur.toString()); cur = null; }
                }
            }
            sheet = files.get("xl/worksheets/sheet1.xml");
            if (sheet == null) for (Map.Entry<String, byte[]> f : files.entrySet()) if (f.getKey().startsWith("xl/worksheets/")) { sheet = f.getValue(); break; }
        }
        if (sheet == null) return new StatementParser.Result();
        List<List<String>> table = new ArrayList<>();
        XmlPullParser p = android.util.Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(sheet), "UTF-8");
        List<String> row = null;
        String type = null;
        int col = 0;
        StringBuilder val = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("row")) row = new ArrayList<>();
                else if (n.equals("c")) {
                    type = p.getAttributeValue(null, "t");
                    String ref = p.getAttributeValue(null, "r");
                    col = 0;
                    if (ref != null) for (char ch : ref.toCharArray()) { if (Character.isLetter(ch)) col = col * 26 + (ch - 'A' + 1); else break; }
                    col = Math.max(0, col - 1);
                    val = new StringBuilder();
                }
            } else if (ev == XmlPullParser.TEXT && val != null) val.append(p.getText());
            else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("c") && row != null && val != null) {
                    String v = val.toString();
                    if ("s".equals(type)) { try { v = shared.get(Integer.parseInt(v.trim())); } catch (Exception ignored) { } }
                    while (row.size() < col) row.add("");
                    row.add(v);
                    val = null;
                } else if (n.equals("row") && row != null) { table.add(row); row = null; }
            }
        }
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < Math.min(25, table.size()); i++) { for (String c : table.get(i)) head.append(c).append(' '); head.append('\n'); }
        return StatementParser.fromTable(table, head.toString());
    }

    // ------------------------------------------------------------------ summary → compare

    private String chosenAccount;

    private void summary() {
        StatementParser.Result r = parsed;
        long in = 0, out = 0;
        for (StatementParser.Row x : r.rows) { if (x.credit) in += x.amount; else out += x.amount; }
        if (chosenAccount == null) chosenAccount = guessAccount(r);
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, (r.bank.isEmpty() ? "Statement" : r.bank) + (r.last4.isEmpty() ? "" : " ••" + r.last4), 17, Ui.BOLD));
        c.addView(Ui.sub(this, Fmt.date(r.from) + " – " + Fmt.date(r.to) + " · " + r.rows.size() + " entries"));
        LinearLayout st = Ui.hbox(this);
        st.setPadding(0, Ui.dp(this, 12), 0, 0);
        st.addView(stat("Money in", Fmt.rs(in)), Ui.weight1());
        st.addView(stat("Money out", Fmt.rs(out)), Ui.weight1());
        c.addView(st);
        add(c);

        LinearLayout a = Ui.card(this);
        a.addView(Ui.text(this, "Which account is this?", 14.5f, Ui.BOLD));
        List<String> accs = Noted.bankAccounts(this);
        String newKey = (r.bank.isEmpty() ? "Bank" : r.bank) + (r.last4.isEmpty() ? "" : " ••" + r.last4);
        if (!accs.contains(newKey) && !r.last4.isEmpty()) accs.add(newKey);
        if (chosenAccount == null && !accs.isEmpty()) chosenAccount = accs.get(0);
        for (String k : accs) {
            TextView ch = Ui.chip(this, (k.equals(chosenAccount) ? "● " : "○ ") + Noted.label(this, k) + (Noted.bankAccounts(this).contains(k) ? "" : "  (new)"), k.equals(chosenAccount));
            ch.setOnClickListener(v -> { chosenAccount = k; rebuild(); });
            a.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 8, 0, 0));
        }
        if (accs.isEmpty()) a.addView(Ui.sub(this, "No bank account in Money yet — it will be added as " + newKey + "."));
        add(a);
        if (chosenAccount == null) chosenAccount = newKey;

        TextView go = Ui.button(this, "Compare with Money", true);
        go.setOnClickListener(v -> compare());
        add(go);
        TextView other = Ui.text(this, "Choose another file", 14, Ui.col(this, R.color.accent), Ui.BOLD);
        other.setGravity(Gravity.CENTER);
        other.setPadding(0, Ui.dp(this, 6), 0, 0);
        other.setOnClickListener(v -> { parsed = null; chosenAccount = null; pick(); });
        box.addView(other);
    }

    private String guessAccount(StatementParser.Result r) {
        if (r.last4.isEmpty()) return null;
        for (String k : Noted.bankAccounts(this)) if (k.endsWith(r.last4)) return k;
        return null;
    }

    private View stat(String label, String value) {
        LinearLayout v = Ui.vbox(this);
        v.addView(Ui.text(this, label, 11.5f, Ui.col(this, R.color.text2), Ui.SEMIBOLD));
        v.addView(Ui.text(this, value, 16, Ui.BOLD));
        return v;
    }

    private void compare() {
        String acc = chosenAccount;
        Store s = Store.get(this);
        Reconcile.Plan p = Reconcile.plan(s.txns(), acc, parsed, parsed.bank);
        int filled = 0;
        s.begin();
        try {
            for (int i = 0; i < p.matched.size(); i++) if (Reconcile.fill(p.matched.get(i), p.matchedRows.get(i), acc)) { s.update(p.matched.get(i)); filled++; }
            for (Txn t : p.added) s.addOrMerge(t, null);
        } finally {
            s.end();
        }
        new Thread(() -> Engine.regaps(getApplicationContext(), acc)).start();
        s.prefs().edit().putLong("stmtLast", System.currentTimeMillis()).apply();
        done = p;
        doneAccount = acc;
        doneFilled = filled;
        parsed = null;
        data = null;
        rebuild();
    }

    // ------------------------------------------------------------------ result

    private void result() {
        Reconcile.Plan p = done;
        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, p.added.isEmpty() && p.notInStatement.isEmpty() ? "All matched ✓" : "Checked ✓", 20, Ui.EXTRABOLD));
        hero.addView(Ui.sub(this, Noted.label(this, doneAccount) + " · " + Fmt.date(p.from) + " – " + Fmt.date(p.to - 1)));
        LinearLayout st = Ui.hbox(this);
        st.setPadding(0, Ui.dp(this, 12), 0, 0);
        st.addView(stat("Matched", String.valueOf(p.matched.size())), Ui.weight1());
        st.addView(stat("Added", String.valueOf(p.added.size())), Ui.weight1());
        st.addView(stat("Details filled", String.valueOf(doneFilled)), Ui.weight1());
        hero.addView(st);
        add(hero);
        if (!p.added.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.sectionTitle(this, "Money had missed these"));
            c.addView(Ui.sub(this, "Now added. Tap one to add a reason while the statement jogs your memory."));
            for (int i = 0; i < Math.min(40, p.added.size()); i++) c.addView(Rows.txn(this, p.added.get(i), true));
            add(c);
        }
        if (!p.notInStatement.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.sectionTitle(this, "In Money, not in the statement"));
            c.addView(Ui.sub(this, "Usually the same payment counted twice, or a payment that failed. Tap Hide if it never happened."));
            for (Txn t : p.notInStatement) {
                LinearLayout r = Ui.hbox(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.addView(Rows.txn(this, t, true), Ui.weight1());
                TextView hide = Ui.chip(this, t.kind == Txn.IGNORED ? "Hidden" : "Hide", false);
                hide.setOnClickListener(v -> {
                    t.kind = Txn.IGNORED;
                    t.note = t.note.isEmpty() ? "Hidden: not in the bank statement" : t.note;
                    Store.get(this).update(t);
                    doneHidden++;
                    hide.setText("Hidden");
                    hide.setEnabled(false);
                });
                r.addView(hide);
                c.addView(r);
            }
            add(c);
        }
        TextView again = Ui.button(this, "Check another statement", false);
        again.setOnClickListener(v -> { done = null; status = null; rebuild(); pick(); });
        add(again);
    }

    // ------------------------------------------------------------------ monthly reminder

    /** Background: early each month, a gentle nudge to check last month (once). */
    static void maybeRemind(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_STMT) || !Alerts.canNotify(c) || Noted.bankAccounts(c).isEmpty()) return;
        java.util.Calendar k = Fmt.cal(System.currentTimeMillis());
        int d = k.get(java.util.Calendar.DAY_OF_MONTH), h = k.get(java.util.Calendar.HOUR_OF_DAY);
        if (d < 3 || d > 10 || h < 10 || h > 20) return;
        String key = "stmtAsked:" + k.get(java.util.Calendar.YEAR) + "-" + k.get(java.util.Calendar.MONTH);
        if (s.prefs().getBoolean(key, false)) return;
        if (System.currentTimeMillis() - s.prefs().getLong("stmtLast", 0) < 20L * 86400000) return;
        s.prefs().edit().putBoolean(key, true).apply();
        Alerts.channels(c);
        long last = Fmt.addMonths(Fmt.startOfMonth(System.currentTimeMillis()), -1);
        Intent open = new Intent(c, StatementActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 446, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, Alerts.CH_ASK) : new android.app.Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle("📄 Check " + Fmt.month(last) + " with your bank statement")
                .setContentText("Open it in Money — missed payments get added, names filled in.")
                .setContentIntent(pi).setAutoCancel(true).setColor(c.getColor(R.color.accent));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(446, b.build());
    }
}
