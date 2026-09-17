package com.akylas.enforcedoze;

import android.content.*;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CustomDozePeriodReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if ("screen-delay".equals(i.getAction()))
            LocalBroadcastManager.getInstance(c).sendBroadcast(new Intent("reenter-doze"));
        else Utils.applyForceDozeSchedule(c);
    }
}
