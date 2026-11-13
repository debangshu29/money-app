package com.debangshu.money;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads payment notifications (Google Pay, PhonePe, Paytm, bank apps) and bank SMS shown by the Messages app.
 * Only those apps are looked at; every other notification is ignored straight away and never stored.
 */
public class PayListener extends NotificationListenerService {

    // the same notification is often posted several times (updates): remember what was already read
    private static final Map<String, Long> seen = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 300; }
    };

    public static volatile boolean connected = false;
    private static volatile PayListener instance;

    @Override public void onListenerConnected() {
        connected = true;
        instance = this;
        readShowing();   // anything posted while Money wasn't listening (phone restart, battery saver…)
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        instance = null;
    }

    /** App opened: re-read payment notifications still in the shade, or ask Android to reconnect the listener. */
    public static void refresh(android.content.Context c) {
        PayListener l = instance;
        if (l != null && connected) { l.readShowing(); return; }
        try {
            if (Setup.listenerOn(c) && android.os.Build.VERSION.SDK_INT >= 24)
                requestRebind(new android.content.ComponentName(c, PayListener.class));
        } catch (Exception ignored) { }
    }

    private void readShowing() {
        new Thread(() -> {
            try {
                StatusBarNotification[] all = getActiveNotifications();
                if (all == null) return;
                for (StatusBarNotification n : all) {
                    try { onNotificationPosted(n); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }).start();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        boolean payApp = NotifParser.appName(pkg) != null;
        boolean smsApp = NotifParser.isSmsApp(pkg);
        if (!payApp && !smsApp) return;
        Store s = Store.get(this);
        if (payApp && !s.flag(Store.S_READ_APPS)) return;
        if (smsApp && !s.flag(Store.S_READ_SMS_NOTIF)) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle x = n.extras;
        if (x == null) return;
        String title = str(x.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = str(x.getCharSequence(Notification.EXTRA_TEXT));
        if (smsApp) {
            // conversation style: take the newest message
            Parcelable[] msgs = x.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
                CharSequence t = ((Bundle) msgs[msgs.length - 1]).getCharSequence("text");
                if (t != null && t.length() > 0) text = t.toString();
            }
            String conv = str(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            if (!conv.isEmpty()) title = conv;
        }
        if (text.isEmpty()) return;
        String key = pkg + "|" + title + "|" + text;
        synchronized (seen) {
            Long when = seen.get(key);
            if (when != null && System.currentTimeMillis() - when < 86400000L) return;
            seen.put(key, System.currentTimeMillis());
        }
        final String fTitle = title, fText = text;
        final long time = sbn.getPostTime();
        new Thread(() -> {
            try {
                if (payApp) Engine.app(getApplicationContext(), pkg, fTitle, fText, time);
                else Engine.sms(getApplicationContext(), fTitle, fText, time, Engine.FROM_SMS_NOTIF);
            } catch (Exception ignored) { }
        }).start();
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString().trim(); }
}
