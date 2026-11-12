package com.debangshu.money;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Notifications: unusual / double charges, budgets, bills due, renewals, the evening summary. */
public final class Alerts {

    private Alerts() {}

    static final String CH_ALERTS = "alerts", CH_BILLS = "bills", CH_DAILY = "daily", CH_EACH = "each";

    public static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(ch(CH_ALERTS, "Unusual payments & budgets", NotificationManager.IMPORTANCE_HIGH,
                "Double charges, payments much bigger than usual, budget warnings, missing messages"));
        nm.createNotificationChannel(ch(CH_BILLS, "Bills & renewals", NotificationManager.IMPORTANCE_DEFAULT,
                "Card bills due and subscriptions about to renew"));
        nm.createNotificationChannel(ch(CH_DAILY, "Evening summary", NotificationManager.IMPORTANCE_LOW, "What you spent today"));
        nm.createNotificationChannel(ch(CH_EACH, "Every payment", NotificationManager.IMPORTANCE_LOW,
                "A quiet note for each payment the app catches (off by default)"));
    }

    private static NotificationChannel ch(String id, String name, int imp, String desc) {
        NotificationChannel ch = new NotificationChannel(id, name, imp);
        ch.setDescription(desc);
        return ch;
    }

    public static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    static void post(Context c, String channel, int id, String title, String text, String txnId) {
        if (!canNotify(c)) return;
        channels(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (txnId != null) open.putExtra("txn", txnId);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(c.getColor(R.color.accent));
        if (Store.get(c).flag(Store.S_HIDE) || Store.get(c).flag(Store.S_LOCK)) b.setVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id, b.build());
    }

    // ------------------------------------------------------------------ right after a new payment

    public static void afterNew(Context c, Txn t) {
        Store s = Store.get(c);
        List<Txn> all = s.txns();
        boolean recent = System.currentTimeMillis() - t.time < 2L * 3600000;
        if (!recent) return;
        String who = t.merchant.isEmpty() ? "" : " · " + t.merchant;

        if ((t.sources & Txn.SRC_GAP) == 0 && t.isSpend()) {
            Txn dbl = Ledger.possibleDouble(all, t);
            if (dbl != null) {
                t.review = true;
                dbl.review = true;
                s.update(t);
                if (s.flag(Store.S_UNUSUAL)) post(c, CH_ALERTS, id(t), "Charged twice?",
                        Fmt.plain(t.amount) + who + " was paid twice within a few minutes. Check if both were meant.", t.id);
            } else if (s.flag(Store.S_UNUSUAL)) {
                String why = Ledger.unusual(all, t);
                if (why != null) {
                    t.review = true;
                    s.update(t);
                    post(c, CH_ALERTS, id(t), "Unusual payment: " + Fmt.plain(t.amount) + who, why + ". Tap if it wasn't you.", t.id);
                }
            }
            if (s.flag(Store.S_BUDGET)) budget(c, t);
        }
        if (s.flag(Store.S_EACH) && t.kind != Txn.SNAPSHOT) {
            String title = (t.credit ? "+" : "−") + Fmt.plain(t.amount) + (t.merchant.isEmpty() ? "" : (t.credit ? " from " : " to ") + t.merchant);
            String text = Cats.emoji(t.category) + " " + t.category + (t.account.isEmpty() ? "" : " · " + t.account)
                    + " · tap to change";
            post(c, CH_EACH, id(t), title, text, t.id);
        }
    }

    private static int id(Txn t) { return 1000 + (t.id.hashCode() & 0xFFFFF); }

    /** Your budget at 50%, 80% and 100% (once each), and category limits at 80% / 100% per month. */
    static void budget(Context c, Txn t) {
        Store s = Store.get(c);
        SharedPreferences p = s.prefs();
        List<Txn> all = s.txns();
        long now = System.currentTimeMillis();
        Plan plan = s.plan();
        if (plan != null && plan.counts(t) && plan.amount > 0) {
            long spent = plan.spent(all);
            int pct = (int) (spent * 100 / plan.amount);
            int level = pct >= 100 ? 100 : pct >= 80 ? 80 : pct >= 50 ? 50 : 0;
            String key = "plan:" + plan.id + ":" + level;
            if (level > 0 && !p.getBoolean(key, false)) {
                p.edit().putBoolean(key, true).apply();
                String title = level >= 100 ? "Over budget by " + Fmt.plain(spent - plan.amount)
                        : pct + "% of your budget used";
                String text = Fmt.plain(spent) + " of " + Fmt.plain(plan.amount) + " spent (" + plan.range() + ")"
                        + (level >= 100 ? ". " + plan.overReasons(all) : " · " + plan.status(all, now));
                post(c, CH_ALERTS, 490, title, text, null);
            }
        }
        Map<String, Long> budgets = s.budgets();
        long ms = Fmt.startOfMonth(now);
        String month = Fmt.month(now);
        Long b = budgets.get(t.category);
        if (b == null || b <= 0 || t.category.isEmpty()) return;
        long spent = 0;
        for (Txn x : all) if (x.isSpend() && x.time >= ms && t.category.equals(x.category)) spent += x.amount;
        int pct = (int) (spent * 100 / b);
        int level = pct >= 100 ? 100 : pct >= 80 ? 80 : 0;
        if (level == 0) return;
        String key = "budget:" + month + ":" + t.category + ":" + level;
        if (p.getBoolean(key, false)) return;
        p.edit().putBoolean(key, true).apply();
        post(c, CH_ALERTS, 500 + Math.abs(t.category.hashCode() % 400),
                level >= 100 ? "Over your " + t.category + " limit" : pct + "% of your " + t.category + " limit used",
                Fmt.plain(spent) + " of " + Fmt.plain(b) + " spent in " + Fmt.MONTH[Fmt.cal(now).get(Calendar.MONTH)]
                        + (level >= 100 ? "." : " — " + Fmt.plain(Math.max(0, b - spent)) + " left."), null);
    }

    /** When the budget's last day is over: a summary, and the next one starts if you chose "repeat". */
    public static void rollPlan(Context c) {
        Store s = Store.get(c);
        long now = System.currentTimeMillis();
        for (int guard = 0; guard < 24; guard++) {
            Plan p = s.plan();
            if (p == null || !p.finished(now)) return;
            List<Txn> all = s.txns();
            long spent = p.spent(all);
            long diff = p.amount - spent;
            String key = "planDone:" + p.id;
            if (!s.prefs().getBoolean(key, false) && now - p.end < 3L * 86400000) {
                s.prefs().edit().putBoolean(key, true).apply();
                String reasons = diff < 0 ? p.overReasons(all) : "";
                post(c, CH_ALERTS, 491, diff >= 0 ? "Budget done: " + Fmt.plain(diff) + " under 🎉" : "Budget done: " + Fmt.plain(-diff) + " over",
                        Fmt.plain(spent) + " spent of " + Fmt.plain(p.amount) + " (" + p.range() + ")"
                                + (reasons.isEmpty() ? "" : ". " + reasons) + (p.repeat ? ". Your next budget has started." : ""), null);
            }
            Plan next = p.repeat ? p.next() : null;
            s.closePlan(p.end);
            if (next != null) s.setPlan(next);
        }
    }

    // ------------------------------------------------------------------ evening summary + reminders (once a day)

    /** The evening summary and next day's alarm. */
    public static void dailySummary(Context c) {
        Store s = Store.get(c);
        schedule(c);
        if (!s.flag(Store.S_DAILY)) return;
        long now = System.currentTimeMillis();
        long today = Fmt.startOfDay(now);
        List<Txn> all = s.txns();
        long spent = Ledger.spent(all, today, now + 1);
        int n = 0;
        for (Txn t : all) if (t.isSpend() && t.time >= today) n++;
        long month = Ledger.spent(all, Fmt.startOfMonth(now), now + 1);
        rollPlan(c);
        Plan plan = s.plan();
        String title = n == 0 ? "No spending today 🎉" : "Today: " + Fmt.plain(spent) + " in " + n + (n == 1 ? " payment" : " payments");
        String text;
        if (plan != null && plan.started(now)) {
            long ps = plan.spent(all);
            text = "Budget: " + Fmt.plain(ps) + " of " + Fmt.plain(plan.amount) + " spent · "
                    + (ps > plan.amount ? Fmt.plain(ps - plan.amount) + " over" : Fmt.plain(plan.amount - ps) + " left, "
                    + Fmt.plain((plan.amount - ps) / Math.max(1, plan.daysLeft(now))) + "/day");
        } else {
            text = "This month: " + Fmt.plain(month) + " · heading for " + Fmt.plain(Ledger.forecast(all, now));
        }
        if (s.flag(Store.S_HIDE)) { title = n + (n == 1 ? " payment today" : " payments today"); text = "Open Money to see today's summary"; }
        post(c, CH_DAILY, 77, title, text, null);
    }

    /** Card bills due within 3 days and subscriptions renewing within 2 days (each reminded once). */
    public static void reminders(Context c) {
        Store s = Store.get(c);
        if (!s.flag(Store.S_BILLS)) return;
        long now = System.currentTimeMillis();
        SharedPreferences p = s.prefs();
        for (Store.Bill b : s.bills()) {
            if (b.paid || b.dueDate < now - 86400000 || b.dueDate > now + 3L * 86400000) continue;
            if (billPaid(s, b)) { s.setBillPaid(b, true); continue; }
            String key = "billR:" + b.account + ":" + b.dueDate;
            if (p.getBoolean(key, false)) continue;
            p.edit().putBoolean(key, true).apply();
            post(c, CH_BILLS, 300 + Math.abs(key.hashCode() % 100), (b.account.isEmpty() ? "Card" : b.account) + " bill due " + Fmt.rel(b.dueDate),
                    "Total due " + Fmt.plain(Math.max(0, b.due)) + (b.minDue > 0 ? " (minimum " + Fmt.plain(b.minDue) + ")" : "")
                            + " · pay by " + Fmt.date(b.dueDate), null);
        }
        for (Ledger.Recurring r : Ledger.recurring(s.txns(), now)) {
            if (r.next < now || r.next > now + 2L * 86400000) continue;
            String key = "subR:" + r.key + ":" + Fmt.startOfDay(r.next);
            if (p.getBoolean(key, false)) continue;
            p.edit().putBoolean(key, true).apply();
            post(c, CH_BILLS, 400 + Math.abs(key.hashCode() % 100), r.name + " renews " + Fmt.rel(r.next),
                    "Usually " + Fmt.plain(r.amount) + " · " + r.periodName().toLowerCase(java.util.Locale.ROOT)
                            + (r.account == null || r.account.isEmpty() ? "" : " · " + r.account), null);
        }
    }

    /** A card bill counts as paid when a payment to that card (≥ the minimum due) came in after the statement. */
    static boolean billPaid(Store s, Store.Bill b) {
        long paid = 0;
        for (Txn t : s.txns()) {
            if (t.credit && t.time > b.seen && Ledger.sameAccount(t.account, b.account) && !t.account.isEmpty()
                    && t.kind == Txn.TRANSFER) paid += t.amount;
        }
        long need = b.minDue > 0 ? b.minDue : b.due;
        return need > 0 && paid >= need;
    }

    // ------------------------------------------------------------------ the evening alarm

    public static final String P_DAILY_HOUR = "daily_hour";

    public static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, DailyReceiver.class).setAction(DailyReceiver.ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int hour = Store.get(c).prefs().getInt(P_DAILY_HOUR, 21);
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, hour);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        if (k.getTimeInMillis() <= System.currentTimeMillis() + 60000) k.add(Calendar.DAY_OF_MONTH, 1);
        // a window instead of an exact time: no special permission needed, and it's kind to the battery
        am.setWindow(AlarmManager.RTC_WAKEUP, k.getTimeInMillis(), 10L * 60000, pi);
    }
}
