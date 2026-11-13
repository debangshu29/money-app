package com.debangshu.money;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/** Home-screen widget: spent today, this month, and the total balance. */
public class Widget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    public static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids == null || ids.length == 0) return;
            Store s = Store.get(c);
            List<Txn> all = s.txns();
            long now = System.currentTimeMillis();
            long today = Ledger.spent(all, Fmt.startOfDay(now), now + 1);
            long month = Ledger.spent(all, Fmt.startOfMonth(now), now + 1);
            long bal = 0;
            boolean any = false;
            for (Account a : s.accounts()) if (!a.card && !a.hidden && a.balance >= 0) { bal += s.liveBalance(a); any = true; }
            boolean hide = s.flag(Store.S_HIDE) || s.flag(Store.S_LOCK);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.w_today, hide ? "₹ ••••" : Fmt.plain(today));
            v.setTextViewText(R.id.w_month, hide ? "₹ ••••" : Fmt.plain(month));
            v.setTextViewText(R.id.w_month_label, Lang.t("Spent in " + Fmt.monthShort(now)));
            Plan plan = s.plan();
            if (plan != null && plan.started(now) && !plan.finished(now)) {
                long safe = plan.safeToday(all, Ledger.recurring(all, now), now);
                v.setTextViewText(R.id.w_bal_label, Lang.t(safe >= 0 ? "Safe to spend today" : "Over today's share by"));
                v.setTextViewText(R.id.w_bal, hide ? "₹ ••••" : Fmt.plain(Math.abs(safe)));
            } else {
                v.setTextViewText(R.id.w_bal_label, Lang.t("Balance (all accounts)"));
                v.setTextViewText(R.id.w_bal, !any ? "—" : hide ? "₹ ••••" : Fmt.plain(bal));
            }
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 9, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            m.updateAppWidget(ids, v);
        } catch (Exception ignored) { }
    }
}
