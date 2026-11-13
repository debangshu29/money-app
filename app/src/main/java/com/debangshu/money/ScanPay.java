package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scan & Pay: scan the shop's QR in Money → Money already knows WHO (name + UPI ID), you pick the category
 * → your UPI app (Google Pay, PhonePe…) opens with everything filled in → when the bank's SMS / app
 * notification arrives, that payment gets the name, category and note by itself.
 *
 * Scanning uses Google's code scanner (no camera permission needed — Google Play services shows the camera).
 */
public final class ScanPay {

    private ScanPay() {}

    public static final int REQ_PAY = 81;

    /** What a UPI QR says. */
    static final class Upi {
        String pa = "", pn = "", am = "", tn = "", mc = "", tr = "", raw = "";
        boolean fixedAmount;
        /** A person's QR (no merchant code) — UPI apps often block these when the payment starts in another app. */
        boolean person() { return mc.isEmpty() || mc.equals("0000"); }
    }

    // ------------------------------------------------------------------ scan

    public static void start(Activity a) {
        try {
            GmsBarcodeScannerOptions opts = new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();
            GmsBarcodeScanner sc = GmsBarcodeScanning.getClient(a, opts);
            sc.startScan()
                    .addOnSuccessListener(b -> {
                        Upi u = parse(b.getRawValue());
                        if (u == null) noUpi(a);
                        else sheet(a, u);
                    })
                    .addOnFailureListener(e -> fallback(a, "The scanner couldn't start (" + e.getMessage() + ")."));
        } catch (Throwable t) {
            fallback(a, "The scanner isn't available on this phone.");
        }
    }

    private static void noUpi(Activity a) {
        new Ui.Popup(a).setTitle("Not a payment QR")
                .setMessage("That QR code isn't a UPI payment code. Try the shop's UPI / Paytm / PhonePe / GPay QR.")
                .setPositiveButton("Scan again", (d, w) -> start(a))
                .setNegativeButton("Close", null).show();
    }

    private static void fallback(Activity a, String why) {
        new Ui.Popup(a).setTitle("Scan & Pay")
                .setMessage(why + "\n\nYou can type the UPI ID instead, or open your UPI app — Money will still remember who you paid.")
                .setPositiveButton("Type UPI ID", (d, w) -> typeUpi(a))
                .setNegativeButton("Close", null).show();
    }

    static void typeUpi(Activity a) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        b.setPadding(p, 0, p, 0);
        EditText e = new EditText(a);
        e.setHint("e.g. sharmakirana@ybl or 9830012345@ybl");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setTypeface(Ui.face(a, Ui.REGULAR));
        b.addView(e);
        new Ui.Popup(a).setTitle("Pay a UPI ID").setView(b)
                .setPositiveButton("Next", (d, w) -> {
                    String v = e.getText().toString().trim();
                    if (!v.matches("[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9]{1,30}")) { Ui.toast(a, "That doesn't look like a UPI ID"); return; }
                    Upi u = new Upi();
                    u.pa = v;
                    sheet(a, u);
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** upi://pay?pa=…&pn=…&am=… — and Bharat QR (EMV) codes that carry a UPI ID inside. */
    static Upi parse(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        Upi u = new Upi();
        u.raw = raw;
        if (raw.toLowerCase(Locale.ROOT).startsWith("upi://")) {
            Uri uri = Uri.parse(raw.replace(" ", "%20"));
            u.pa = nz(uri.getQueryParameter("pa"));
            u.pn = nz(uri.getQueryParameter("pn"));
            u.am = nz(uri.getQueryParameter("am"));
            u.tn = nz(uri.getQueryParameter("tn"));
            u.mc = nz(uri.getQueryParameter("mc"));
            u.tr = nz(uri.getQueryParameter("tr"));
            u.fixedAmount = !u.am.isEmpty() && SmsParser.paise(u.am) > 0;
            return u.pa.contains("@") ? u : null;
        }
        if (raw.startsWith("000201")) {           // EMV / Bharat QR
            Map<String, String> top = tlv(raw);
            for (int t = 26; t <= 51; t++) {
                String v = top.get(String.format(Locale.ROOT, "%02d", t));
                if (v == null) continue;
                for (String s : tlv(v).values()) if (s.matches("[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9]{1,30}")) { u.pa = s; break; }
                if (!u.pa.isEmpty()) break;
            }
            u.pn = nz(top.get("59"));
            u.am = nz(top.get("54"));
            u.mc = nz(top.get("52"));
            u.fixedAmount = !u.am.isEmpty();
            if (!u.pa.isEmpty()) { u.raw = ""; return u; }
        }
        return null;
    }

    private static Map<String, String> tlv(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        int i = 0;
        try {
            while (i + 4 <= s.length()) {
                String tag = s.substring(i, i + 2);
                int len = Integer.parseInt(s.substring(i + 2, i + 4));
                if (i + 4 + len > s.length()) break;
                m.put(tag, s.substring(i + 4, i + 4 + len));
                i += 4 + len;
            }
        } catch (Exception ignored) { }
        return m;
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    // ------------------------------------------------------------------ the pay sheet

    static void sheet(Activity a, Upi u) {
        Store s = Store.get(a);
        Txn probe = new Txn();
        probe.vpa = u.pa;
        probe.merchant = u.pn.isEmpty() ? SmsParser.nameFromVpa(u.pa) : SmsParser.cleanName(u.pn);
        String known = s.nameFor(probe);
        String name0 = known != null ? known : probe.merchant;
        probe.merchant = name0;
        final String[] cat = {s.categoryFor(probe)};
        if (Cats.PEOPLE.equals(cat[0]) && !Cats.looksLikePerson(probe)) cat[0] = Cats.OTHER;

        ScrollView sv = new ScrollView(a);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, 0, p, Ui.dp(a, 6));
        sv.addView(box);

        box.addView(label(a, "Paying"));
        EditText name = new EditText(a);
        name.setText(name0.startsWith("Shop (QR)") ? "" : name0);
        name.setHint("Shop or person's name");
        name.setTextSize(20);
        name.setTypeface(Ui.face(a, Ui.BOLD));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name);
        TextView id = Ui.sub(a, "UPI ID: " + u.pa + (known != null ? "  ·  🧠 you named it before" : ""));
        box.addView(id);

        box.addView(label(a, "Amount (optional)"));
        LinearLayout ar = Ui.hbox(a);
        ar.addView(Ui.text(a, "₹", 26, Ui.EXTRABOLD));
        EditText amt = new EditText(a);
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amt.setHint("0");
        amt.setTextSize(26);
        amt.setTypeface(Ui.face(a, Ui.EXTRABOLD));
        amt.setBackground(null);
        if (u.fixedAmount) { amt.setText(u.am); amt.setEnabled(false); }
        ar.addView(amt, Ui.weight1());
        box.addView(ar);
        if (u.fixedAmount) box.addView(Ui.sub(a, "Amount set by the shop's QR"));
        else box.addView(Ui.sub(a, "Leave it empty if you'll type it in your UPI app — Money asks when you're back"));

        box.addView(label(a, "Category"));
        LinearLayout chips = Ui.hbox(a);
        List<TextView> chipViews = new ArrayList<>();
        String[] picks = {Cats.GROCERY, Cats.FOOD, Cats.SHOP, Cats.TRANSPORT, Cats.FUEL, Cats.PEOPLE, Cats.BILLS, Cats.HEALTH,
                Cats.RECHARGE, Cats.HOME, Cats.PERSONAL, Cats.OTHER};
        List<String> order = new ArrayList<>();
        order.add(cat[0]);
        for (String c : picks) if (!order.contains(c)) order.add(c);
        for (String c : order) {
            TextView ch = Ui.chip(a, Cats.emoji(c) + " " + c, c.equals(cat[0]));
            ch.setTag(c);
            ch.setOnClickListener(v -> {
                cat[0] = c;
                for (TextView x : chipViews) {
                    boolean on = x.getTag().equals(c);
                    x.setTextColor(on ? Ui.col(a, R.color.on_accent) : Ui.col(a, R.color.text));
                    x.setBackground(Ui.rippleRound(a, on ? Ui.col(a, R.color.accent) : Ui.col(a, R.color.surface2), 18));
                }
            });
            if (!c.equals(cat[0])) ch.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            chipViews.add(ch);
            chips.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        box.addView(hs);

        box.addView(label(a, "Note (optional)"));
        EditText note = new EditText(a);
        note.setHint("e.g. vegetables, haircut");
        note.setText(u.tn);
        note.setTypeface(Ui.face(a, Ui.REGULAR));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(note);

        String app = appLabel(a, preferred(a));
        TextView with = Ui.chip(a, "Pay in: " + (app == null ? "choose your UPI app" : app) + "  ▾", false);
        box.addView(with, Ui.margins(Ui.lp(-2, -2), a, 0, 6, 0, 0));

        TextView how = Ui.text(a, "Money opens your UPI app — scan the same QR there and pay as usual (UPI apps only fully trust payments "
                + "that start inside them). When you come back, Money asks “Paid?” and saves it with this name — or picks it up by "
                + "itself from the bank SMS or the app's notification. Money never sees your PIN.",
                12, Ui.col(a, R.color.hint), Ui.REGULAR);
        how.setPadding(0, Ui.dp(a, 12), 0, 0);
        box.addView(how);

        AlertDialog d = new Ui.Popup(a).sheet().setTitle("Scan & Pay").setView(Ui.capHeight(sv, 0.72f))
                .setPositiveButton(app == null ? "Open my UPI app" : "Open " + app, null)
                .setNegativeButton("Cancel", null).create();
        with.setOnClickListener(v -> pickApp(a, pkg -> {
            with.setText("Pay in: " + appLabel(a, pkg) + "  ▾");
            android.widget.Button b = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (b != null) b.setText("Open " + appLabel(a, pkg));
        }));
        d.setOnShowListener(x -> {
            Ui.style(d, true);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long paise = Math.max(0, SmsParser.paise(amt.getText().toString().trim()));
                String nm = name.getText().toString().trim();
                remember(a, u, nm, cat[0], paise, note.getText().toString().trim());
                d.dismiss();
                if (preferred(a) == null) pickApp(a, pkg -> openApp(a));
                else openApp(a);
            });
        });
        d.show();
    }

    private static TextView label(Activity a, String s) {
        TextView t = Ui.text(a, s, 13, Ui.col(a, R.color.text2), Ui.BOLD);
        t.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
        return t;
    }

    // ------------------------------------------------------------------ handing over to the UPI app

    /**
     * The QR exactly as the shop printed it (signature, merchant code and all — rebuilding it is what makes UPI apps refuse);
     * only the amount is added when the QR has none and you typed one.
     */
    static String payUri(Upi u, long paise) {
        String am = String.format(Locale.ROOT, "%d.%02d", paise / 100, paise % 100);
        if (!u.raw.isEmpty()) {
            String r = u.raw.replace(" ", "%20");
            if (!u.fixedAmount && paise > 0) {
                r = r.replaceAll("(?i)([?&])am=[^&]*&?", "$1").replaceAll("[?&]$", "");
                r += (r.contains("?") ? "&" : "?") + "am=" + am;
                if (!r.toLowerCase(Locale.ROOT).contains("cu=")) r += "&cu=INR";
            }
            return r;
        }
        // Bharat QR (EMV) — no upi:// text to pass on, so the minimum the UPI app needs
        Uri.Builder b = new Uri.Builder().scheme("upi").authority("pay").appendQueryParameter("pa", u.pa);
        if (!u.pn.isEmpty()) b.appendQueryParameter("pn", u.pn);
        if (!u.mc.isEmpty()) b.appendQueryParameter("mc", u.mc);
        if (paise > 0) b.appendQueryParameter("am", am);
        b.appendQueryParameter("cu", "INR");
        return b.build().toString();
    }

    private static void pay(Activity a, Upi u, long paise) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(payUri(u, paise)));
        String pkg = preferred(a);
        if (pkg != null) i.setPackage(pkg);
        try {
            lastPay = u;
            a.startActivityForResult(i, REQ_PAY);
        } catch (Exception e) {
            a.getSharedPreferences("scanpay", 0).edit().remove("app").apply();
            try { a.startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(payUri(u, paise))), "Pay with"), REQ_PAY); }
            catch (Exception e2) { openApp(a); }
        }
    }

    private static Upi lastPay;

    /** UPI apps on this phone (anything that opens upi://pay). */
    static List<ResolveInfo> upiApps(Context c) {
        List<ResolveInfo> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        try {
            for (ResolveInfo r : c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=a@b")), 0)) {
                if (r.activityInfo == null || seen.contains(r.activityInfo.packageName)) continue;
                seen.add(r.activityInfo.packageName);
                out.add(r);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** The UPI app you picked last time (if it's still installed). */
    static String preferred(Context c) {
        String p = c.getSharedPreferences("scanpay", 0).getString("app", null);
        if (p == null) return null;
        for (ResolveInfo r : upiApps(c)) if (r.activityInfo.packageName.equals(p)) return p;
        return null;
    }

    static String appLabel(Context c, String pkg) {
        if (pkg == null) return null;
        String n = NotifParser.appName(pkg);
        if (n != null) return n;
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    interface Picked { void on(String pkg); }

    static void pickApp(Activity a, Picked then) {
        List<ResolveInfo> apps = upiApps(a);
        if (apps.isEmpty()) { Ui.toast(a, "No UPI app found — install Google Pay, PhonePe or Paytm"); return; }
        String[] names = new String[apps.size()];
        for (int k = 0; k < apps.size(); k++) names[k] = appLabel(a, apps.get(k).activityInfo.packageName);
        new Ui.Popup(a).setTitle("Pay with").setItems(names, (d, w) -> {
            String pkg = apps.get(w).activityInfo.packageName;
            a.getSharedPreferences("scanpay", 0).edit().putString("app", pkg).apply();
            then.on(pkg);
        }).show();
    }

    /** "Scan in my app": opens your UPI app — scan there; Money still knows who it was. */
    static void openApp(Activity a) {
        PackageManager pm = a.getPackageManager();
        String pref = preferred(a);
        if (pref != null) {
            Intent l = pm.getLaunchIntentForPackage(pref);
            if (l != null) { a.startActivity(l); return; }
        }
        List<String> names = new ArrayList<>(), pkgs = new ArrayList<>();
        for (ResolveInfo r : upiApps(a)) {
            String p = r.activityInfo.packageName;
            if (pm.getLaunchIntentForPackage(p) != null) { pkgs.add(p); names.add(appLabel(a, p)); }
        }
        if (pkgs.isEmpty()) { Ui.toast(a, "Open your UPI app and pay — Money will match it"); return; }
        if (pkgs.size() == 1) { a.startActivity(pm.getLaunchIntentForPackage(pkgs.get(0))); return; }
        new Ui.Popup(a).setTitle("Open").setItems(names.toArray(new String[0]), (d, w) -> {
            a.getSharedPreferences("scanpay", 0).edit().putString("app", pkgs.get(w)).apply();
            a.startActivity(pm.getLaunchIntentForPackage(pkgs.get(w)));
        }).show();
    }

    public static void onResult(Activity a, int req, int res, Intent data) {
        if (req != REQ_PAY) return;
        String resp = data == null ? null : data.getStringExtra("response");
        if (resp == null && data != null) resp = data.getStringExtra("Status");
        String lo = resp == null ? "" : resp.toLowerCase(Locale.ROOT);
        if (lo.contains("status=success") || lo.equals("success")) {
            Ui.toast(a, "Paid ✓ — it appears when your bank's message or the app's notification arrives");
            return;
        }
        // failed, refused ("limit", "security reasons") or just closed: offer the way that always works
        String app = appLabel(a, preferred(a));
        boolean failed = lo.contains("status=failure") || lo.equals("failure");
        new Ui.Popup(a).setTitle(failed ? "The payment didn't go through" : "Did the payment go through?")
                .setMessage((failed ? "Nothing was paid. " : "")
                        + "Some UPI apps refuse payments that start in another app, even small ones. Scanning the same QR inside "
                        + (app == null ? "your UPI app" : app) + " always works — and Money will still give the payment the name and category you chose.")
                .setPositiveButton("Scan in " + (app == null ? "my app" : app), (d, w) -> openApp(a))
                .setNeutralButton(failed ? "Try another app" : "Yes, it's paid", (d, w) -> {
                    if (!failed) { Ui.toast(a, "It appears when your bank's message or the app's notification arrives"); return; }
                    Upi u = lastPay;
                    if (u == null) return;
                    long amt = 0;
                    try { amt = new JSONObject(Store.get(a).prefs().getString("pendingPay", "{}")).optLong("amt"); } catch (Exception ignored) { }
                    long fa = amt;
                    pickApp(a, pkg -> pay(a, u, fa));
                })
                .setNegativeButton("Cancel payment", (d, w) -> clearPending(a)).show();
    }

    // ------------------------------------------------------------------ "Paid?" when you come back

    /** Back in Money after "Open Google Pay": did you pay? Saves it now (the bank SMS / notification joins it later). */
    public static void askIfPaid(Activity a) {
        SharedPreferences p = Store.get(a).prefs();
        String js = p.getString("pendingPay", null);
        if (js == null) return;
        JSONObject j;
        try { j = new JSONObject(js); } catch (Exception e) { clearPending(a); return; }
        long when = j.optLong("t"), now = System.currentTimeMillis();
        if (j.optBoolean("asked") || now - when < 6000) return;
        if (now - when > 30L * 60000) { clearPending(a); return; }
        try { p.edit().putString("pendingPay", j.put("asked", true).toString()).apply(); } catch (Exception ignored) { }

        String nm = j.optString("name");
        String who = nm.isEmpty() ? j.optString("vpa") : nm;
        LinearLayout b = Ui.vbox(a);
        int pd = Ui.dp(a, 24);
        b.setPadding(pd, 0, pd, 0);
        LinearLayout ar = Ui.hbox(a);
        ar.addView(Ui.text(a, "₹", 26, Ui.EXTRABOLD));
        EditText amt = new EditText(a);
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amt.setHint("amount");
        amt.setTextSize(26);
        amt.setTypeface(Ui.face(a, Ui.EXTRABOLD));
        if (j.optLong("amt") > 0) amt.setText(Fmt.plain(j.optLong("amt")).replace("₹", "").replace(",", ""));
        ar.addView(amt, Ui.weight1());
        b.addView(ar);
        String[] acc = {Noted.defaultAccount(a)};
        b.addView(Noted.accountChip(a, acc), Ui.margins(Ui.lp(-2, -2), a, 0, 6, 0, 0));
        TextView t = Ui.sub(a, "Saved now with the name, category and note you chose. If the bank's SMS or the app's notification comes later, it joins this — no double entry.");
        t.setPadding(0, Ui.dp(a, 10), 0, 0);
        b.addView(t);
        AlertDialog d = new Ui.Popup(a).setTitle("Paid " + who + "?").setView(b)
                .setPositiveButton("Yes, save it", null)
                .setNeutralButton("Not paid", (x, w) -> clearPending(a))
                .setNegativeButton("Later", null).create();
        d.setOnShowListener(x -> {
            Ui.style(d, false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long paise = SmsParser.paise(amt.getText().toString().trim());
                if (paise <= 0) { amt.setError("How much?"); return; }
                // already picked up by itself (SMS / notification / screen) while you were away?
                String cur = Store.get(a).prefs().getString("pendingPay", null);
                if (cur == null) { d.dismiss(); Ui.toast(a, "Already saved ✓"); return; }
                clearPending(a);
                Noted.add(a, paise, j.optString("name"), j.optString("cat"), j.optString("note"), j.optString("vpa"), acc[0]);
                d.dismiss();
                Ui.toast(a, "Saved ✓ " + Fmt.plain(paise) + (nm.isEmpty() ? "" : " to " + nm));
            });
        });
        d.show();
    }

    // ------------------------------------------------------------------ remembering who it was

    private static void remember(Context c, Upi u, String name, String cat, long paise, String note) {
        Store s = Store.get(c);
        if (!name.isEmpty()) s.learnVpa(u.pa, name, cat);
        try {
            JSONObject j = new JSONObject().put("vpa", u.pa).put("name", name).put("cat", cat).put("amt", paise)
                    .put("note", note).put("t", System.currentTimeMillis());
            s.prefs().edit().putString("pendingPay", j.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void clearPending(Context c) { Store.get(c).prefs().edit().remove("pendingPay").apply(); }

    /**
     * A new payment just arrived: is it the one started from Scan & Pay? Then it gets the name, category and note.
     * Returns true when it matched.
     */
    public static boolean match(Context c, Txn t) {
        if (t.credit || t.kind != Txn.NORMAL) return false;
        SharedPreferences p = Store.get(c).prefs();
        String js = p.getString("pendingPay", null);
        if (js == null) return false;
        try {
            JSONObject j = new JSONObject(js);
            long when = j.optLong("t");
            if (t.time < when - 60000 || t.time > when + 30L * 60000) {
                if (System.currentTimeMillis() - when > 60L * 60000) clearPending(c);
                return false;
            }
            long amt = j.optLong("amt");
            String vpa = j.optString("vpa");
            if (amt > 0 && amt != t.amount) return false;
            if (!t.vpa.isEmpty() && !t.vpa.equalsIgnoreCase(vpa)) return false;
            Store s = Store.get(c);
            if (t.vpa.isEmpty()) t.vpa = vpa;
            String name = j.optString("name");
            if (!name.isEmpty() && !t.userMerchant) { t.merchant = name; t.userMerchant = true; }
            String cat = j.optString("cat");
            if (!cat.isEmpty() && !t.userCategory) { t.category = cat; t.userCategory = true; }
            String note = j.optString("note");
            if (!note.isEmpty() && t.note.isEmpty()) t.note = note;
            if (!t.sender.contains("Scan & Pay")) t.sender = t.sender.isEmpty() ? "Scan & Pay" : t.sender + " · Scan & Pay";
            s.update(t);
            clearPending(c);
            return true;
        } catch (Exception e) {
            clearPending(c);
            return false;
        }
    }
}
