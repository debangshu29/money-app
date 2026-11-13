package com.debangshu.money;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the "Payment successful" screen of UPI apps (Google Pay, PhonePe, Paytm, super.money…) — so even a ₹1 payment
 * that sends no SMS and no notification is saved with its name. Only those apps' screens are looked at (set in
 * res/xml/screen_watch.xml); typed text and PIN fields are never read; nothing but the payment itself is kept.
 */
public class ScreenWatch extends AccessibilityService {

    private final Handler h = new Handler(Looper.getMainLooper());
    private String pendingPkg;
    private final Runnable read = this::readNow;
    private static final Map<String, Long> seen = new LinkedHashMap<String, Long>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 80; }
    };
    private static long lastUnread;

    public static boolean enabled(Context c) {
        try {
            String flat = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (flat == null) return false;
            String me = new ComponentName(c, ScreenWatch.class).flattenToString();
            String meShort = new ComponentName(c, ScreenWatch.class).flattenToShortString();
            for (String s : flat.split(":")) if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (NotifParser.appName(pkg) == null) return;
        if (!Store.get(this).flag(Store.S_READ_SCREENS)) return;
        // wait until the screen settles (success screens animate in)
        pendingPkg = pkg;
        h.removeCallbacks(read);
        h.postDelayed(read, 700);
    }

    private void readNow() {
        String pkg = pendingPkg;
        if (pkg == null) return;
        AccessibilityNodeInfo root;
        try { root = getRootInActiveWindow(); } catch (Exception ex) { return; }
        if (root == null || root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return;
        List<String> lines = new ArrayList<>();
        collect(root, lines, 0);
        if (lines.isEmpty() || lines.size() > 120) return;
        long now = System.currentTimeMillis();
        SmsParser.Result r = ScreenParser.parse(pkg, lines, now);
        if (r == null) { maybeUnread(pkg, lines, now); return; }
        String key = pkg + "|" + (r.ref.isEmpty() ? r.amount + "|" + r.merchant + "|" + (r.time / 300000) : r.ref);
        synchronized (seen) {
            Long w = seen.get(key);
            if (w != null && now - w < 6L * 3600000) return;
            seen.put(key, now);
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 600) break;
            sb.append(sb.length() == 0 ? "" : " · ").append(l);
        }
        final String text = sb.toString();
        final long when = r.time > 0 ? r.time : now;
        new Thread(() -> { try { Engine.screen(getApplicationContext(), pkg, r, text, when); } catch (Exception ignored) { } }).start();
    }

    /** Looked like a success screen but the layout is new → keep it in "Couldn't read" (at most once a minute). */
    private void maybeUnread(String pkg, List<String> lines, long now) {
        if (now - lastUnread < 60000) return;
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(sb.length() == 0 ? "" : " · ").append(l);
        String all = sb.toString();
        String lo = all.toLowerCase(java.util.Locale.ROOT);
        if (!lo.matches("(?s).*(successful|paid to|sent to|completed).*") || !all.matches("(?s).*(₹|(?i:rs\\.?))\\s*[0-9].*")) return;
        if (lo.matches("(?s).*(upi pin|enter pin).*")) return;
        lastUnread = now;
        String name = NotifParser.appName(pkg);
        Store.get(this).addUnread(now, name + " (screen)", all.length() > 700 ? all.substring(0, 700) : all);
    }

    private static void collect(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || depth > 25 || out.size() > 150) return;
        try {
            if (n.isPassword() || n.isEditable()) return;   // never typed text, never PINs
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            if (t != null && t.length() > 0 && t.length() < 200) out.add(t.toString());
            else if (d != null && d.length() > 0 && d.length() < 200) out.add(d.toString());
            for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
        } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }
}
