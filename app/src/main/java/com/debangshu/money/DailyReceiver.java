package com.debangshu.money;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The evening summary alarm; also sets everything up again after a restart or an update. */
public class DailyReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.debangshu.money.DAILY";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        String a = intent.getAction();
        if (ACTION.equals(a)) {
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try { Alerts.dailySummary(app); } catch (Exception ignored) { } finally { pr.finish(); }
            }).start();
        } else {
            Alerts.schedule(app);
            BgJob.schedule(app);
            Updater.scheduleBackgroundChecks(app);
        }
    }
}
