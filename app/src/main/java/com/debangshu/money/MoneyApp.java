package com.debangshu.money;

import android.app.Application;

public class MoneyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fmt.hide = false;
        if (Store.get(this).flag(Store.S_HIDE)) HomePage.hideAll(this);
        Lock.track(this);
        Alerts.channels(this);
        // 1.2: the missed-SMS check got much smarter — work the "Unrecorded" entries out again once
        if (Store.get(this).prefs().getInt("gapsVersion", 0) < 2) {
            Store.get(this).prefs().edit().putInt("gapsVersion", 2).apply();
            new Thread(() -> {
                try {
                    Store.get(this).dropUntouchedGaps();
                    Engine.recheckGaps(this);
                } catch (Exception ignored) { }
            }).start();
        }
        Alerts.schedule(this);
        BgJob.schedule(this);
        Updater.scheduleBackgroundChecks(this);
    }
}
