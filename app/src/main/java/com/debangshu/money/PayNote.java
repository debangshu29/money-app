package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pay & note — the middle button. Pick one of your UPI apps → pay there as usual → back in Money:
 * “Paid? ₹__ to __ for __” with one-tap reasons. If the bank's SMS / the app's notification already came, Money only asks
 * what for; if it comes later, it joins what you saved.
 */
public final class PayNote {

    private PayNote() {}

    private static final String PENDING = "payNote";

    // ------------------------------------------------------------------ the sheet

    public static void sheet(Activity a) {
        List<ResolveInfo> apps = upiApps(a);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, 0, p, Ui.dp(a, 6));
        TextView hint = Ui.sub(a, "Pay in your app as usual. When you're back, Money asks who and what for — so you still know in a month.");
        hint.setPadding(0, 0, 0, Ui.dp(a, 12));
        box.addView(hint);
        AlertDialog[] dlg = new AlertDialog[1];
        if (apps.isEmpty()) box.addView(Ui.sub(a, "No UPI app found on this phone."));
        LinearLayout row = null;
        String last = a.getSharedPreferences("scanpay", 0).getString("app", null);
        // the one you used last first
        if (last != null) for (int i = 0; i < apps.size(); i++) if (apps.get(i).activityInfo.packageName.equals(last)) { apps.add(0, apps.remove(i)); break; }
        PackageManager pm = a.getPackageManager();
        for (int i = 0; i < apps.size(); i++) {
            if (i % 4 == 0) {
                row = Ui.hbox(a);
                box.addView(row, Ui.margins(Ui.matchWrap(), a, 0, 0, 0, 10));
            }
            ResolveInfo r = apps.get(i);
            String pkg = r.activityInfo.packageName;
            LinearLayout cell = Ui.vbox(a);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            cell.setBackground(Ui.rippleRound(a, 0, 16));
            ImageView ic = new ImageView(a);
            try { Drawable d = r.loadIcon(pm); ic.setImageDrawable(d); } catch (Exception ignored) { }
            cell.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
            TextView l = Ui.text(a, label(a, pkg), 11.5f, Ui.SEMIBOLD);
            l.setGravity(Gravity.CENTER);
            l.setMaxLines(2);
            l.setPadding(0, Ui.dp(a, 6), 0, 0);
            cell.addView(l);
            cell.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); open(a, pkg); });
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (row != null) while (row.getChildCount() < 4) row.addView(new LinearLayout(a), new LinearLayout.LayoutParams(0, 1, 1));

        LinearLayout more = Ui.hbox(a);
        more.setPadding(0, Ui.dp(a, 8), 0, 0);
        TextView add = Ui.chip(a, "✏️  Add without paying", false);
        add.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); TxnSheet.addManual(a); });
        TextView say = Ui.chip(a, "🎤  Say it", false);
        say.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); VoiceActivity.start(a); });
        more.addView(add, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
        more.addView(say);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(more);
        box.addView(hs);
        dlg[0] = new Ui.Popup(a).sheet().setTitle("Pay & note").setView(box).setNegativeButton("Close", null).show();
    }

    static List<ResolveInfo> upiApps(Context c) {
        List<ResolveInfo> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        try {
            for (ResolveInfo r : c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=a@b")), 0)) {
                if (r.activityInfo == null || seen.contains(r.activityInfo.packageName) || r.activityInfo.packageName.equals(c.getPackageName())) continue;
                if (c.getPackageManager().getLaunchIntentForPackage(r.activityInfo.packageName) == null) continue;
                seen.add(r.activityInfo.packageName);
                out.add(r);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String label(Context c, String pkg) {
        String n = NotifParser.appName(pkg);
        if (n != null) return n;
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private static void open(Activity a, String pkg) {
        Intent l = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (l == null) { Ui.toast(a, "Couldn't open " + label(a, pkg)); return; }
        a.getSharedPreferences("scanpay", 0).edit().putString("app", pkg).apply();
        try {
            Store.get(a).prefs().edit().putString(PENDING, new JSONObject().put("pkg", pkg).put("t", System.currentTimeMillis()).toString()).apply();
        } catch (Exception ignored) { }
        a.startActivity(l);
    }

    static void clear(Context c) { Store.get(c).prefs().edit().remove(PENDING).apply(); }

    /** The usage watch shouldn't ask about a visit you started from Money (you get asked in the app). */
    static boolean startedHere(Context c, String pkg, long start) {
        String js = Store.get(c).prefs().getString(PENDING, null);
        if (js == null) return false;
        try {
            JSONObject j = new JSONObject(js);
            return pkg.equals(j.optString("pkg")) && Math.abs(j.optLong("t") - start) < 2 * 60000;
        } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------------ back in Money

    /** Called when Money comes back to the front. */
    public static void onReturn(Activity a) {
        SharedPreferences p = Store.get(a).prefs();
        String js = p.getString(PENDING, null);
        if (js == null) return;
        JSONObject j;
        try { j = new JSONObject(js); } catch (Exception e) { clear(a); return; }
        long t0 = j.optLong("t"), now = System.currentTimeMillis();
        if (now - t0 < 5000) return;                          // didn't really leave
        clear(a);
        if (now - t0 > 45L * 60000) return;
        String pkg = j.optString("pkg");
        // did a payment already come in (SMS / notification / e-mail)?
        Txn caught = null;
        for (Txn t : Store.get(a).txns()) {
            if (t.credit || t.kind != Txn.NORMAL || (t.sources & (Txn.SRC_SMS | Txn.SRC_APP)) == 0) continue;
            if (t.time < t0 - 60000 || t.time > now + 60000) continue;
            if (caught == null || t.time > caught.time) caught = t;
        }
        if (caught != null && !caught.note.isEmpty()) {         // a regular — already has its usual note
            Ui.toast(a, "Saved ✓ " + Fmt.plain(caught.amount) + " · " + caught.merchant + " · " + caught.note);
            return;
        }
        ask(a, caught, pkg, t0);
    }

    /** "Paid? ₹__ to __ for __" — or, when the payment is already here, just "what for?". Also used by the "Paid anyone?" notification. */
    public static void ask(Activity a, Txn caught, String pkg, long t0) {
        Store s = Store.get(a);
        List<Txn> all = s.txns();
        LinearLayout b = Ui.vbox(a);
        int pd = Ui.dp(a, 22);
        b.setPadding(pd, 0, pd, 0);
        EditText amt = new EditText(a), who = new EditText(a), why = new EditText(a);
        String[] acc = {Noted.defaultAccount(a)};
        String[] cat = {""};
        if (caught != null) {
            b.addView(Ui.text(a, Fmt.plain(caught.amount) + (caught.unnamed() ? "" : " to " + caught.merchant), 20, Ui.EXTRABOLD));
            b.addView(Ui.sub(a, "Already caught from " + caught.sender + " · " + Fmt.time(caught.time)));
            if (caught.unnamed()) {
                who.setHint("Who was it?");
                who.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                who.setTypeface(Ui.face(a, Ui.REGULAR));
                b.addView(who);
            }
        } else {
            LinearLayout ar = Ui.hbox(a);
            ar.addView(Ui.text(a, "₹", 26, Ui.EXTRABOLD));
            amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amt.setHint("amount");
            amt.setTextSize(26);
            amt.setTypeface(Ui.face(a, Ui.EXTRABOLD));
            ar.addView(amt, Ui.weight1());
            b.addView(ar);
            who.setHint("To whom (e.g. Sharma Kirana, Ramesh)");
            who.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            who.setTypeface(Ui.face(a, Ui.REGULAR));
            b.addView(who);
            // people / places you pay often
            LinearLayout recent = Ui.hbox(a);
            List<String> names = new ArrayList<>();
            List<Txn> sorted = new ArrayList<>(all);
            java.util.Collections.sort(sorted, (x, y) -> Long.compare(y.time, x.time));
            for (Txn t : sorted) {
                if (t.credit || t.unnamed() || t.kind != Txn.NORMAL || names.contains(t.merchant)) continue;
                names.add(t.merchant);
                if (names.size() >= 8) break;
            }
            for (String n : names) {
                TextView ch = Ui.chip(a, n, false);
                ch.setOnClickListener(v -> who.setText(n));
                recent.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 6, 0));
            }
            if (!names.isEmpty()) {
                HorizontalScrollView hs = new HorizontalScrollView(a);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(recent);
                b.addView(hs, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 4));
            }
        }
        TextView wl = Ui.text(a, "What for?", 13, Ui.col(a, R.color.text2), Ui.BOLD);
        wl.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
        b.addView(wl);
        // one-tap reasons
        Txn probe = caught;
        if (probe == null) { probe = new Txn(); probe.time = System.currentTimeMillis(); probe.amount = 1; }
        LinearLayout chips = Ui.hbox(a);
        List<TextView> cv = new ArrayList<>();
        for (String[] r : ReasonLogic.suggest(all, probe, 6)) {
            TextView ch = Ui.chip(a, r[0], false);
            ch.setOnClickListener(v -> {
                if (!r[1].isEmpty()) why.setText(r[1]);
                cat[0] = r[2];
                for (TextView x : cv) x.setBackground(Ui.rippleRound(a, x == ch ? Ui.col(a, R.color.accent_soft) : Ui.col(a, R.color.surface2), 18));
            });
            ch.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            cv.add(ch);
            chips.addView(ch, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 6, 0));
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        b.addView(hs);
        why.setHint("Or type: e.g. test, Puja chanda, Rahul's birthday");
        why.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        why.setTypeface(Ui.face(a, Ui.REGULAR));
        if (caught != null && !caught.note.isEmpty()) why.setText(caught.note);
        b.addView(why);
        if (caught == null) {
            if (!Noted.bankAccounts(a).contains(acc[0])) acc[0] = null;
            b.addView(Ui.text(a, "Paid from", 13, Ui.col(a, R.color.text2), Ui.BOLD), Ui.margins(Ui.lp(-1, -2), a, 0, 12, 0, 6));
            b.addView(Noted.fromPicker(a, acc, false, null));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(b);
        String app = pkg == null ? null : label(a, pkg);
        AlertDialog d = new Ui.Popup(a).setTitle(caught != null ? "What was it for?" : "Paid" + (app == null ? "" : " with " + app) + "?").setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton(caught != null ? "Skip" : "Didn't pay", null).create();
        d.setOnShowListener(x -> {
            Ui.style(d, false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String note = why.getText().toString().trim(), name = who.getText().toString().trim();
                if (caught != null) {
                    if (!name.isEmpty()) Alerts.applyName(a, caught, name, cat[0].isEmpty() ? null : cat[0]);
                    if (!note.isEmpty()) caught.note = note;
                    if (!cat[0].isEmpty() && !caught.userCategory) { caught.category = cat[0]; caught.userCategory = true; }
                    s.update(caught);
                    cancelNotif(a, caught);
                    d.dismiss();
                    Ui.toast(a, "Saved ✓");
                    return;
                }
                long paise = SmsParser.paise(amt.getText().toString().trim());
                if (paise <= 0) { amt.setError("How much?"); return; }
                if (acc[0] == null && !Noted.bankAccounts(a).isEmpty()) { Ui.toast(a, "Pick the account it was paid from"); return; }
                Noted.add(a, paise, name, cat[0].isEmpty() ? null : cat[0], note, "", acc[0]);
                if (acc[0] != null) Noted.rememberFrom(a, acc[0]);
                d.dismiss();
                Ui.toast(a, "Saved ✓ " + Fmt.plain(paise) + (name.isEmpty() ? "" : " to " + name) + (acc[0] == null ? "" : " · from " + Noted.label(a, acc[0])));
            });
        });
        d.show();
    }

    private static void cancelNotif(Context c, Txn t) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        if (nm != null) { nm.cancel(Reasons.nid(t.id)); nm.cancel(20000 + (t.id.hashCode() & 0xFFFF)); }
        Store.get(c).prefs().edit().putBoolean("why:" + t.id, true).apply();
    }
}
