package com.debangshu.money;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Answers typed straight into the "Who was it?" notification. */
public class NameReceiver extends BroadcastReceiver {

    public static final String NAME = "com.debangshu.money.NAME", YES = "com.debangshu.money.YES", SKIP = "com.debangshu.money.SKIP";
    public static final String KEY = "name";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        String id = intent.getStringExtra("txn");
        int nid = intent.getIntExtra("nid", 0);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Store s = Store.get(c);
        Txn t = id == null ? null : s.byId(id);
        if (t == null || SKIP.equals(intent.getAction())) {
            if (nm != null) nm.cancel(nid);
            if (t != null) s.prefs().edit().putBoolean("asked:" + t.id, false).apply();
            return;
        }
        String name = null, category = null;
        if (NAME.equals(intent.getAction())) {
            Bundle r = RemoteInput.getResultsFromIntent(intent);
            CharSequence cs = r == null ? null : r.getCharSequence(KEY);
            name = cs == null ? null : cs.toString().trim();
        } else if (YES.equals(intent.getAction())) {
            name = intent.getStringExtra("guess");
            category = intent.getStringExtra("guessCat");
        }
        if (name == null || name.isEmpty()) { if (nm != null) nm.cancel(nid); return; }
        if (NAME.equals(intent.getAction())) {          // "Ramesh - test" → name + what for
            String[] nr = ReasonLogic.nameAndReason(name);
            name = nr[0];
            if (!nr[1].isEmpty() && t.note.isEmpty()) { t.note = nr[1]; s.update(t); }
            if (name.isEmpty()) { if (nm != null) nm.cancel(nid); return; }
        }
        Alerts.applyName(c, t, name, category);
        Alerts.post(c, Alerts.CH_ASK, nid, "Saved ✓ " + name,
                Fmt.plain(t.amount) + " · " + Cats.emoji(t.category) + " " + t.category
                        + (t.vpa.isEmpty() ? "" : " · future payments to this UPI ID get this name too") + " · tap to change", t.id);
        if (nm != null) {
            final NotificationManager fnm = nm;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> fnm.cancel(nid), 6000);
        }
    }
}
