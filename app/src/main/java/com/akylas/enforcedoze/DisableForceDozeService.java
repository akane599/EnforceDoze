package com.akylas.enforcedoze;


import static com.akylas.enforcedoze.Utils.logToLogcat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class DisableForceDozeService extends BroadcastReceiver {
    public static String TAG = "EnforceDoze";

    private static void log(String message) {
        logToLogcat(TAG, message);
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!android.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowExternalAutomation", false)) return;
        if (!"com.akylas.enforcedoze.DISABLE_FORCEDOZE".equals(intent.getAction())) return;
        log("com.akylas.enforcedoze.DISABLE_FORCEDOZE broadcast intent received");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("serviceEnabled", false).apply();
        Utils.stopForceDozeService(context);
    }
}
