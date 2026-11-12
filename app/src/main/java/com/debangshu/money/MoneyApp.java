package com.debangshu.money;

import android.app.Application;

public class MoneyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fmt.hide = Store.get(this).flag(Store.S_HIDE);
        Lock.track(this);
        Alerts.channels(this);
        Alerts.schedule(this);
        BgJob.schedule(this);
        Updater.scheduleBackgroundChecks(this);
    }
}
