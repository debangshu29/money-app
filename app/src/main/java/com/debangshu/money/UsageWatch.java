package com.debangshu.money;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * "Paid anyone on Google Pay?" — with Usage access, Money sees WHEN a UPI app was open (never what was on screen).
 * About a minute after you leave it, if no payment came in (SMS / notification / e-mail / noted), it asks. Visits
 * under 12 seconds (a quick balance check) are ignored. Runs from the notification reader, so it's near-instant.
 */
final class UsageWatch {

    private UsageWatch() {}

    private static final String STATE = "usageWatch";
    private static final long GAP = 4000, SETTLE = 60000, MIN_VISIT = 12000;

    static boolean allowed(Context c) {
        try {
            AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            @SuppressWarnings("deprecation")
            int m = ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    static void openSettings(Context c) {
        try { c.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Ui.toast(c, "Open Settings → Apps → Special access → Usage access → Money"); }
    }

    /** Called every ~30 s while the notification reader runs, and when Money opens. */
    static synchronized void tick(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_USAGE) || !allowed(c)) return;
        long now = System.currentTimeMillis();
        JSONObject st;
        try { st = new JSONObject(s.prefs().getString(STATE, "{}")); } catch (Exception e) { st = new JSONObject(); }
        long since = Math.max(st.optLong("last", now - 10 * 60000), now - 30 * 60000);
        JSONObject open = st.optJSONObject("open");          // pkg → {start, end}
        if (open == null) open = new JSONObject();
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents ev = um.queryEvents(since, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev != null && ev.hasNextEvent()) {
                ev.getNextEvent(e);
                String pkg = e.getPackageName();
                if (pkg == null || NotifParser.appName(pkg) == null || pkg.equals(c.getPackageName())) continue;
                int type = e.getEventType();
                long ts = e.getTimeStamp();
                JSONObject v = open.optJSONObject(pkg);
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {          // = ACTIVITY_RESUMED
                    if (v == null || (v.optLong("end", 0) > 0 && ts - v.optLong("end") > GAP)) {
                        if (v != null && v.optLong("end", 0) > 0) finish(c, pkg, v.optLong("start"), v.optLong("end"));
                        v = new JSONObject().put("start", ts).put("end", 0);
                    } else v.put("end", 0);                                   // just moving between the app's own screens
                    open.put(pkg, v);
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND && v != null) {   // = ACTIVITY_PAUSED
                    v.put("end", ts);
                    open.put(pkg, v);
                }
            }
            // visits that ended a minute ago → check now
            java.util.List<String> drop = new java.util.ArrayList<>();
            for (Iterator<String> it = open.keys(); it.hasNext(); ) {
                String pkg = it.next();
                JSONObject v = open.optJSONObject(pkg);
                long end = v.optLong("end", 0);
                if (end > 0 && now - end >= SETTLE) { finish(c, pkg, v.optLong("start"), end); drop.add(pkg); }
                else if (end == 0 && now - v.optLong("start") > 3 * 3600000L) drop.add(pkg);
            }
            for (String k : drop) open.remove(k);
            st.put("open", open).put("last", now);
        } catch (Exception ignored) { }
        s.prefs().edit().putString(STATE, st.toString()).apply();
    }

    private static void finish(Context c, String pkg, long start, long end) {
        if (end - start < MIN_VISIT) return;
        if (System.currentTimeMillis() - end > 20 * 60000) return;              // too long ago to ask now
        if (PayNote.startedHere(c, pkg, start)) return;
        for (Txn t : Store.get(c).txns()) {
            if (t.credit || t.kind == Txn.SNAPSHOT || (t.sources & Txn.SRC_GAP) != 0) continue;
            if (t.time >= start - 60000 && t.time <= end + 3 * 60000) return;   // a payment came in → nothing to ask ("what for?" handles it)
            if ((t.sources & Txn.SRC_NOTE) != 0 && t.time >= start - 60000 && t.time <= System.currentTimeMillis()) return;
        }
        Reasons.askPaidAnyone(c, pkg, start, end);
    }
}
