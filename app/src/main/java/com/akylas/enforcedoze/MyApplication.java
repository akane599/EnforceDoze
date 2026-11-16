package com.akylas.enforcedoze;

import android.content.Context;

import io.github.akane599.enforcedoze.app.automation.AutomationReceiver;

public class MyApplication extends android.app.Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
        // The automation receiver follows its switch again after a backup restore (ADR 0033); off the main thread, so
        // the preference read and the package manager call stay out of the cold start.
        new Thread(() -> AutomationReceiver.sync(this), "automation-sync").start();
    }

    public static Context getAppContext() {
        return MyApplication.context;
    }
}
