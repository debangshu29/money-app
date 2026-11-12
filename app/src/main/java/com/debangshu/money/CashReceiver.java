package com.debangshu.money;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** The amount typed into "how much cash is left?". */
public class CashReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        int nid = intent.getIntExtra("nid", 444);
        Bundle r = RemoteInput.getResultsFromIntent(intent);
        CharSequence cs = r == null ? null : r.getCharSequence("left");
        long v = cs == null ? -1 : SmsParser.paise(cs.toString().replaceAll("[^0-9.]", ""));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (v < 0) { if (nm != null) nm.cancel(nid); return; }
        long before = Cash.balance(c);
        Cash.setLeft(c, v);
        Alerts.post(c, Alerts.CH_ASK, nid, "Saved ✓ " + Fmt.plain(v) + " in your wallet",
                v < before ? Fmt.plain(before - v) + " counted as cash spent" : "Wallet updated", null);
        if (nm != null) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> nm.cancel(nid), 5000);
    }
}
