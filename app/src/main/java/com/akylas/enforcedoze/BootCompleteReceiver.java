package com.akylas.enforcedoze;

import android.content.*;
import android.preference.PreferenceManager;

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) recover(c);
    }

    static void recover(Context c) {
        if (PreferenceManager.getDefaultSharedPreferences(c).getBoolean("serviceEnabled", false)
                || new RecoveryStore(c).pending()) Utils.startForceDozeService(c);
        Utils.scheduleNextCustomDozePeriodBoundary(c);
    }
}
