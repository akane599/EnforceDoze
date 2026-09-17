package com.akylas.enforcedoze;

import android.content.Context;

public class MyApplication extends android.app.Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
        applyTheme(this);
    }

    public static void applyTheme(Context context) {
        String theme = android.preference.PreferenceManager.getDefaultSharedPreferences(context).getString("theme", "system");
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(theme.equals("dark")
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : theme.equals("light")
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    public static Context getAppContext() {
        return MyApplication.context;
    }
}
