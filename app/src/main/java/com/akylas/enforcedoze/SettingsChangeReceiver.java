package com.akylas.enforcedoze;

import static com.akylas.enforcedoze.Utils.logToLogcat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SettingsChangeReceiver extends BroadcastReceiver {

    public static String TAG = "EnforceDoze";private static void log(String message) {
        logToLogcat(TAG, message);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!android.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allowExternalAutomation", false)) return;
        if (!"com.akylas.enforcedoze.CHANGE_SETTING".equals(intent.getAction())) return;
        log("com.akylas.enforcedoze.CHANGE_SETTING broadcast intent received");
        String settingName = intent.getStringExtra("settingName");
        boolean invert = "enableSensors".equals(settingName) || "ignoreIfHotspot".equals(settingName);
        if ("enableSensors".equals(settingName)) settingName = "disableMotionSensors";
        if ("ignoreIfHotspot".equals(settingName)) settingName = "respectHotspot";
        if ("useAutoRotateAndBrightnessFix".equals(settingName)) settingName = "autoRotateAndBrightnessFix";
        final String settingValue = intent.getStringExtra("settingValue");

        if (settingName != null && settingValue != null) {
            if (Utils.doesSettingExist(settingName)) {
                if (Utils.isSettingBool(settingName)) {
                    if (!"true".equalsIgnoreCase(settingValue) && !"false".equalsIgnoreCase(settingValue)) return;
                    Utils.updateSettingBool(context, settingName, invert != Boolean.parseBoolean(settingValue));
                    if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
                        Intent i = new Intent("reload-settings");
                        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                    }
                } else {
                    int delay;
                    try { delay = Integer.parseInt(settingValue); } catch (NumberFormatException e) { return; }
                    if (delay < 0 || delay > 1800) return;
                    Utils.updateSettingInt(context, settingName, delay);
                    if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
                        Intent i = new Intent("reload-settings");
                        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                    }
                }
            } else {
                log("Setting does not exist or not updatable");
            }
        } else {
            log("settingName and/or settingValue null");
        }
    }
}
