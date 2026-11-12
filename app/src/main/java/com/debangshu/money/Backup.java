package com.debangshu.money;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Backups go to the phone's own storage — Documents/Money/Backups — never to any cloud.
 * Every backup is locked with your backup password (AES-256); without it the file can't be read.
 * Automatic backup once a day; the newest 7 are kept.
 */
public final class Backup {

    private Backup() {}

    public static final String FOLDER = "Documents/Money/Backups";
    private static final String P_KEY = "bk_key", P_SALT = "bk_salt", P_LAST = "bk_last", P_WHERE = "bk_where";
    private static final int KEEP = 7;

    public static boolean hasPassword(Context c) { return prefs(c).contains(P_KEY); }

    private static SharedPreferences prefs(Context c) { return Store.get(c).prefs(); }

    /** Remembers a key made from the password (not the password itself), so daily backups need no typing. */
    public static void setPassword(Context c, char[] pw) throws Exception {
        byte[] salt = BackupCrypt.newSalt();
        byte[] key = BackupCrypt.key(pw, salt, BackupCrypt.ROUNDS);
        prefs(c).edit().putString(P_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
                .putString(P_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)).apply();
    }

    public static long lastBackup(Context c) { return prefs(c).getLong(P_LAST, 0); }

    public static String lastWhere(Context c) { return prefs(c).getString(P_WHERE, ""); }

    /** Daily, from the background job. */
    public static void auto(Context c) throws Exception {
        Store s = Store.get(c);
        if (!s.flag(Store.S_AUTO_BACKUP) || !hasPassword(c) || s.txns().isEmpty()) return;
        if (System.currentTimeMillis() - lastBackup(c) < 20L * 3600000) return;
        backupNow(c);
    }

    /** Writes an encrypted backup now. Returns where it went. */
    public static String backupNow(Context c) throws Exception {
        if (!hasPassword(c)) throw new Exception("Set a backup password first");
        byte[] key = Base64.decode(prefs(c).getString(P_KEY, ""), Base64.NO_WRAP);
        byte[] salt = Base64.decode(prefs(c).getString(P_SALT, ""), Base64.NO_WRAP);
        byte[] plain = gzip(Store.get(c).toJson().toString().getBytes(StandardCharsets.UTF_8));
        byte[] file = BackupCrypt.encrypt(plain, key, salt, BackupCrypt.ROUNDS);
        java.util.Calendar k = Fmt.cal(System.currentTimeMillis());
        String name = String.format(Locale.ROOT, "Money-backup-%04d-%02d-%02d_%02d%02d.mbak", k.get(java.util.Calendar.YEAR),
                k.get(java.util.Calendar.MONTH) + 1, k.get(java.util.Calendar.DAY_OF_MONTH),
                k.get(java.util.Calendar.HOUR_OF_DAY), k.get(java.util.Calendar.MINUTE));
        String where = write(c, name, "application/octet-stream", file, true);
        prefs(c).edit().putLong(P_LAST, System.currentTimeMillis()).putString(P_WHERE, where).apply();
        return where;
    }

    /** Opens a backup file: password → everything back. Throws BackupCrypt.WrongPassword on a wrong password. */
    public static JSONObject open(byte[] file, char[] pw) throws Exception {
        byte[] plain = BackupCrypt.decrypt(file, pw);
        return new JSONObject(new String(gunzip(plain), StandardCharsets.UTF_8));
    }

    /** Saves a spreadsheet (CSV) of all transactions to Documents/Money. Not encrypted — you asked for it. */
    public static String exportCsv(Context c) throws Exception {
        StringBuilder b = new StringBuilder("Date,Time,Amount,In/Out,Merchant,Category,Account,Note,Source\n");
        for (Txn t : Store.get(c).visible()) {
            b.append(Fmt.date(t.time)).append(',').append(Fmt.time(t.time)).append(',')
             .append(String.format(Locale.ROOT, "%.2f", t.amount / 100.0)).append(',').append(t.credit ? "In" : "Out").append(',')
             .append(csv(t.merchant)).append(',').append(csv(t.category)).append(',').append(csv(t.account)).append(',')
             .append(csv(t.note)).append(',').append(csv(t.sender)).append('\n');
        }
        java.util.Calendar k = Fmt.cal(System.currentTimeMillis());
        String name = String.format(Locale.ROOT, "Money-%04d-%02d-%02d.csv", k.get(java.util.Calendar.YEAR),
                k.get(java.util.Calendar.MONTH) + 1, k.get(java.util.Calendar.DAY_OF_MONTH));
        return write(c, name, "text/csv", b.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    // ------------------------------------------------------------------ files

    private static String write(Context c, String name, String mime, byte[] data, boolean backup) throws Exception {
        String rel = backup ? FOLDER : "Documents/Money";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel + "/");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri coll = MediaStore.Files.getContentUri("external");
            Uri u = cr.insert(coll, v);
            if (u == null) throw new Exception("Couldn't create the file");
            try (OutputStream out = cr.openOutputStream(u)) {
                if (out == null) throw new Exception("Couldn't write the file");
                out.write(data);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(u, v, null, null);
            if (backup) pruneQ(c);
            return rel + "/" + name;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), rel);
        boolean ok = c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED && (dir.isDirectory() || dir.mkdirs());
        if (!ok) {
            dir = new File(c.getExternalFilesDir(null), backup ? "Backups" : "Exports");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("Storage not available");
        }
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
        if (backup) pruneOld(dir);
        return f.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath() + "/", "");
    }

    /** Keeps the newest 7 backups this app made (Android 10+). */
    private static void pruneQ(Context c) {
        if (Build.VERSION.SDK_INT < 29) return;
        ContentResolver cr = c.getContentResolver();
        Uri coll = MediaStore.Files.getContentUri("external");
        List<Long> ids = new ArrayList<>();
        try (Cursor cur = cr.query(coll, new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'Money-backup-%'",
                new String[]{FOLDER + "/"}, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cur == null) return;
            while (cur.moveToNext()) ids.add(cur.getLong(0));
        } catch (Exception e) {
            return;
        }
        for (int i = KEEP; i < ids.size(); i++) {
            try { cr.delete(android.content.ContentUris.withAppendedId(coll, ids.get(i)), null, null); } catch (Exception ignored) { }
        }
    }

    private static void pruneOld(File dir) {
        File[] fs = dir.listFiles((d, n) -> n.startsWith("Money-backup-") && n.endsWith(".mbak"));
        if (fs == null || fs.length <= KEEP) return;
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < fs.length; i++) fs[i].delete();
    }

    public static byte[] read(Context c, Uri u) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) throw new Exception("Couldn't open the file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    static byte[] gzip(byte[] d) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(bo)) { z.write(d); }
        return bo.toByteArray();
    }

    static byte[] gunzip(byte[] d) throws Exception {
        try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(d))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = z.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }
}
