package com.debangshu.money;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;

/** App lock: fingerprint / face / phone PIN when you open the app (after 30 seconds away). */
public final class Lock {

    private Lock() {}

    private static int started = 0;
    private static long leftAt = 0;
    static boolean unlocked = false;

    public static void track(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { started++; }
            @Override public void onActivityStopped(Activity a) {
                started = Math.max(0, started - 1);
                if (started == 0) leftAt = System.currentTimeMillis();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    public static boolean deviceSecure(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    public static boolean needed(Context c) {
        if (!Store.get(c).flag(Store.S_LOCK) || !deviceSecure(c)) return false;
        if (unlocked && (leftAt == 0 || System.currentTimeMillis() - leftAt < 30000)) return false;
        unlocked = false;
        return true;
    }

    static void done() { unlocked = true; leftAt = 0; }
}
