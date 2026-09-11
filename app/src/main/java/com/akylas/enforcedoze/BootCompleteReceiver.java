package com.akylas.enforcedoze;
import android.content.*;
public class BootCompleteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) Utils.applyForceDozeSchedule(context);
    }
}
