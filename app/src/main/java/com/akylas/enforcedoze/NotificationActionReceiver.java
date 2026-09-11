package com.akylas.enforcedoze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

/** Private target for notification PendingIntents; independent of external automation. */
public final class NotificationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!"com.akylas.enforcedoze.ENABLE_FORCEDOZE".equals(action)
                && !"com.akylas.enforcedoze.DISABLE_FORCEDOZE".equals(action)) return;
        boolean enabled = "com.akylas.enforcedoze.ENABLE_FORCEDOZE".equals(action);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("serviceEnabled", enabled).apply();
        if (enabled) Utils.applyForceDozeSchedule(context); else Utils.stopForceDozeService(context);
    }
}
