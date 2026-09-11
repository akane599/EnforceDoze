package com.akylas.enforcedoze;
import android.content.*;
public class AutoRestartOnUpdate extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) Utils.applyForceDozeSchedule(context);
    }
}
