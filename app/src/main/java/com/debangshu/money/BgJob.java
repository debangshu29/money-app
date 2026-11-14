package com.debangshu.money;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;

/**
 * Runs every few hours in the background (Android picks the moment):
 *  - catches up on any bank SMS that was missed
 *  - bill and renewal reminders
 *  - the automatic encrypted backup (once a day)
 */
public class BgJob extends JobService {

    private static final int JOB_ID = 5151;

    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, BgJob.class))
                .setPeriodic(3L * 60 * 60 * 1000)
                .setPersisted(true)
                .build();
        try { js.schedule(job); } catch (Exception ignored) { }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Context c = getApplicationContext();
            try { Engine.catchUp(c); } catch (Exception ignored) { }
            try { Alerts.reminders(c); } catch (Exception ignored) { }
            try { Alerts.rollPlan(c); } catch (Exception ignored) { }
            try { Cash.maybeCheckIn(c); } catch (Exception ignored) { }
            try { Refunds.check(c); } catch (Exception ignored) { }
            try { Reminders.check(c); } catch (Exception ignored) { }
            try { Loans.check(c); } catch (Exception ignored) { }
            try { BalanceCheck.maybeAsk(c); } catch (Exception ignored) { }
            try { StatementActivity.maybeRemind(c); } catch (Exception ignored) { }
            try { UsageWatch.tick(c); } catch (Exception ignored) { }
            try { MoneyFacts.checkWaitList(c); } catch (Exception ignored) { }
            try { Learn.nudge(c); } catch (Exception ignored) { }
            try { Motivation.check(c, true); } catch (Exception ignored) { }
            try { Backup.auto(c); } catch (Exception ignored) { }
            try {
                if (!PayListener.connected && Setup.listenerOn(c)) {
                    NotificationListenerService.requestRebind(new ComponentName(c, PayListener.class));
                }
            } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
