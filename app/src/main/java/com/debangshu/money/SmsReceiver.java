package com.debangshu.money;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Android wakes the app for every incoming SMS; bank messages become transactions within a second. */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts;
        try {
            parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            return;
        }
        if (parts == null || parts.length == 0) return;
        // long messages come in several parts: join them per sender
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        Map<String, Long> times = new LinkedHashMap<>();
        for (SmsMessage m : parts) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder b = bodies.get(from);
            if (b == null) { bodies.put(from, b = new StringBuilder()); times.put(from, m.getTimestampMillis()); }
            b.append(m.getDisplayMessageBody());
        }
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                for (Map.Entry<String, StringBuilder> e : bodies.entrySet()) {
                    Long t = times.get(e.getKey());
                    long when = t == null || t <= 0 || Math.abs(t - System.currentTimeMillis()) > 86400000L ? System.currentTimeMillis() : t;
                    Engine.sms(app, e.getKey(), e.getValue().toString(), when, Engine.FROM_SMS);
                }
            } catch (Exception ignored) {
            } finally {
                pr.finish();
            }
        }).start();
    }
}
