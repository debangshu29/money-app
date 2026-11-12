package com.debangshu.money;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Small home-screen widget: Scan & Pay, and a mic to say a cash spend. */
public class PayWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    public static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, PayWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_pay);
            Intent scan = new Intent(c, MainActivity.class).putExtra("scan", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            v.setOnClickPendingIntent(R.id.wp_scan, PendingIntent.getActivity(c, 31, scan, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            Intent voice = new Intent(c, VoiceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.wp_voice, PendingIntent.getActivity(c, 32, voice, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            m.updateAppWidget(ids, v);
        } catch (Exception ignored) { }
    }
}
