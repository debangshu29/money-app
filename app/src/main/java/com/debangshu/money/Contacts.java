package com.debangshu.money;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Names from your contacts for UPI IDs that are just a phone number (9830012345@ybl). Looked up on the phone, never stored or sent. */
final class Contacts {

    private Contacts() {}

    static final int REQ = 91;
    private static final Map<String, String> cache = new HashMap<>();
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+?91[ -]?)?([6-9][0-9]{4}[ -]?[0-9]{5})(?![0-9])");

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    static void ask(Activity a) { a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ); }

    /** The phone number a payment points at (its UPI ID, or a name that is only a number). */
    static String number(Txn t) {
        String local = t.vpa.contains("@") ? t.vpa.substring(0, t.vpa.indexOf('@')) : "";
        Matcher m = PHONE.matcher(local);
        if (m.find()) return m.group(1).replaceAll("[ -]", "");
        m = PHONE.matcher(t.merchant);
        if (m.find() && t.merchant.replaceAll("[^A-Za-z]", "").length() < 2) return m.group(1).replaceAll("[ -]", "");
        return null;
    }

    static String lookup(Context c, String num) {
        if (num == null || !allowed(c)) return null;
        synchronized (cache) { if (cache.containsKey(num)) return cache.get(num); }
        String name = null;
        try (Cursor cur = c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) { }
        synchronized (cache) { cache.put(num, name); }
        return name;
    }

    /** A new payment: if it's to / from a phone number in your contacts, use the contact's name. */
    static boolean name(Context c, Txn t) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_CONTACTS) || t.userMerchant || !allowed(c)) return false;
        String num = number(t);
        if (num == null) return false;
        if (!t.unnamed() && !t.merchant.equals(SmsParser.nameFromVpa(t.vpa)) && !t.merchant.matches(".*[0-9]{5}.*")) return false;
        String n = lookup(c, num);
        if (n == null || n.trim().isEmpty()) return false;
        t.merchant = n.trim();
        if (!t.userCategory && (t.category.isEmpty() || Cats.OTHER.equals(t.category))) t.category = Cats.PEOPLE;
        s.update(t);
        return true;
    }

    /** Turned on: name the payments already there. */
    static int nameAll(Context c) {
        int n = 0;
        Store s = Store.get(c);
        s.begin();
        try { for (Txn t : s.txns()) if (name(c, t)) n++; } finally { s.end(); }
        return n;
    }
}
