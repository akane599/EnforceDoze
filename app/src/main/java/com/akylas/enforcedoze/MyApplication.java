package com.akylas.enforcedoze;

import android.content.Context;

public class MyApplication extends android.app.Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains("executionMode")) prefs.edit().putString("executionMode", prefs.getBoolean("isSuAvailable", false) ? "root" : "shizuku").apply();
        if (!prefs.contains("respectHotspot")) prefs.edit().putBoolean("respectHotspot", prefs.contains("ignoreIfHotspot") ? !prefs.getBoolean("ignoreIfHotspot", true) : true).apply();
    }

    public static Context getAppContext() {
        return MyApplication.context;
    }
}
