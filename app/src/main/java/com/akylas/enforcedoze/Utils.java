package com.akylas.enforcedoze;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.content.ComponentName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static android.content.Context.BATTERY_SERVICE;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class Utils {

    private static final int DISABLED_NOTIFICATION_ID = 9876;
    private static final String CHANNEL_DISABLED = "CHANNEL_DISABLED";
    public static final String ACTION_CUSTOM_DOZE_PERIOD_BOUNDARY = "com.akylas.enforcedoze.ACTION_CUSTOM_DOZE_PERIOD_BOUNDARY";
    private static final int CUSTOM_DOZE_PERIOD_REQUEST_CODE = 9012;

    public static void startForceDozeService(Context context) {
        if (isMyServiceRunning(ForceDozeService.class, context)) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("reload-settings"));
            return;
        }
        try {
            Intent intent = new Intent(context, ForceDozeService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            hideDisabledNotification(context);
        } catch (RuntimeException e) {
            // Android 12+ can deny background starts. Keep the user's enabled preference.
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString("runtimeStatus", "NEEDS_START").putString("lastError", e.toString()).apply();
            showStartRequiredNotification(context);
        }
        updateTileState(context);
    }

    private static void showStartRequiredNotification(Context context) {
        if (!isPostNotificationPermissionGranted(context)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("start_required", "Service setup", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 91, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(9191, new NotificationCompat.Builder(context, "start_required")
                .setSmallIcon(R.drawable.ic_power_settings_new_white_24dp).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.status_needs_start)).setContentIntent(open).setAutoCancel(true).build());
    }

    public static void stopForceDozeService(Context context) {
        cancelCustomDozePeriodAlarm(context);
        if (isMyServiceRunning(ForceDozeService.class, context)) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ForceDozeService.ACTION_STOP));
        }
        showDisabledNotification(context);
        updateTileState(context);
    }

    public static void applyForceDozeSchedule(Context context) {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false);
        if (!enabled) { stopForceDozeService(context); return; }
        scheduleNextCustomDozePeriodBoundary(context);
        // Keep the foreground monitor alive outside periods. Background alarms cannot reliably restart it.
        startForceDozeService(context);
    }

    public static void scheduleNextCustomDozePeriodBoundary(Context context) {
        cancelCustomDozePeriodAlarm(context);
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false) || !hasCustomDozePeriods(context)) {
            return;
        }

        long delay = getMillisUntilNextCustomDozePeriodBoundary(context);
        if (delay < 0) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getCustomDozePeriodPendingIntent(context);
        long triggerAtMillis = System.currentTimeMillis() + delay;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            logToLogcat("EnforceDoze", "Exact custom period alarm not allowed, using inexact alarm");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    public static void cancelCustomDozePeriodAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getCustomDozePeriodPendingIntent(context));
    }

    public static boolean hasCustomDozePeriods(Context context) {
        return !getCustomDozePeriods(context).isEmpty();
    }

    public static boolean isInsideCustomDozePeriod(Context context) {
        Set<String> customDozePeriods = getCustomDozePeriods(context);
        if (customDozePeriods.isEmpty()) {
            return true;
        }

        int now = getCurrentMinuteOfDay();
        for (String period : customDozePeriods) {
            int[] parsedPeriod = parseCustomDozePeriod(period);
            if (parsedPeriod == null) {
                continue;
            }

            int start = parsedPeriod[0];
            int end = parsedPeriod[1];
            if (start < end && now >= start && now < end) {
                return true;
            } else if (start > end && (now >= start || now < end)) {
                return true;
            }
        }
        return false;
    }

    private static PendingIntent getCustomDozePeriodPendingIntent(Context context) {
        Intent intent = new Intent(context, CustomDozePeriodReceiver.class);
        intent.setAction(ACTION_CUSTOM_DOZE_PERIOD_BOUNDARY);
        return PendingIntent.getBroadcast(context, CUSTOM_DOZE_PERIOD_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Set<String> getCustomDozePeriods(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet("customDozePeriods", new LinkedHashSet<String>());
    }

    private static long getMillisUntilNextCustomDozePeriodBoundary(Context context) {
        Calendar now = Calendar.getInstance();
        long nowMillis = now.getTimeInMillis();
        long nextBoundaryMillis = Long.MAX_VALUE;

        for (String period : getCustomDozePeriods(context)) {
            int[] parsedPeriod = parseCustomDozePeriod(period);
            if (parsedPeriod == null) {
                continue;
            }
            nextBoundaryMillis = Math.min(nextBoundaryMillis, getNextBoundaryMillis(now, parsedPeriod[0]));
            nextBoundaryMillis = Math.min(nextBoundaryMillis, getNextBoundaryMillis(now, parsedPeriod[1]));
        }

        if (nextBoundaryMillis == Long.MAX_VALUE) {
            return -1;
        }
        return Math.max(1000, nextBoundaryMillis - nowMillis);
    }

    private static long getNextBoundaryMillis(Calendar now, int minuteOfDay) {
        Calendar boundary = (Calendar) now.clone();
        boundary.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        boundary.set(Calendar.MINUTE, minuteOfDay % 60);
        boundary.set(Calendar.SECOND, 0);
        boundary.set(Calendar.MILLISECOND, 0);
        if (!boundary.after(now)) {
            boundary.add(Calendar.DAY_OF_YEAR, 1);
        }
        return boundary.getTimeInMillis();
    }

    private static int getCurrentMinuteOfDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static int[] parseCustomDozePeriod(String period) { return CommandPolicy.period(period); }

    private static int parseCustomDozeTime(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWriteSettingsPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(context);
        }
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDumpPermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isPostNotificationPermissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isReadPhoneStatePermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isUsageStatsPermissionGranted(Context context) {
        boolean granted = false;
        AppOpsManager appOps = (AppOpsManager) context
                .getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());

        if (mode == AppOpsManager.MODE_DEFAULT) {
            granted = (context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            granted = (mode == AppOpsManager.MODE_ALLOWED);
        }
        return granted;
    }
    public static boolean isReadLogsPermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isSecureSettingsPermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean isSecureSensorPrivacyPermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission("android.permission.MANAGE_SENSOR_PRIVACY") == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isConnectedToCharger(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            return plugged > 0;
        } else return false;
    }

    public static String getDateCurrentTimeZone(long timestamp) {
        //return DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.UK).format(new Date(timestamp));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss");
        return dateFormat.format(cal.getTime());
    }

    public static int getBatteryLevel(Context context) {
        BatteryManager bm = (BatteryManager)context.getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    public static boolean checkForAutoPowerModesFlag() {
        int id = Resources.getSystem().getIdentifier("config_enableAutoPowerModes", "bool", "android");
        try { return id == 0 || Resources.getSystem().getBoolean(id); }
        catch (Resources.NotFoundException e) { return true; }
    }

    public static boolean isDeviceRunningOnN() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static int diffInMins(long start, long end) {
        return (int) ((end - start) / 1000) / 60;
    }

    public static String timeSpentString(long start, long end) {
        long diff = end - start;

        if (diff < 0) {
            throw new IllegalArgumentException("Duration must be greater than zero!");
        }

        long days = TimeUnit.MILLISECONDS.toDays(diff);
        diff -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        diff -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        diff -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);

        return String.valueOf(days) +
                " days, " +
                hours +
                " hours, " +
                minutes +
                " minutes, " +
                seconds +
                " seconds";
    }

    public static void setAutoRotateEnabled(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
    }

    public static boolean isAutoRotateEnabled(Context context) {
        return android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    public static boolean isAutoBrightnessEnabled(Context context) {
        try {
            return android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    public static void setAutoBrightnessEnabled(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, enabled ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    public static boolean isUserInCommunicationCall(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Also preserve call screening, redirected calls and newer communication modes.
        return manager != null && manager.getMode() != AudioManager.MODE_NORMAL;
    }

    public static boolean isUserInCall(Context context) {
        try {
            if (!isReadPhoneStatePermissionGranted(context)) return false;
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            return manager != null && manager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (RuntimeException e) { return false; }
    }

    public static boolean isMobileDataEnabled(Context context) {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false;
        try {
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            if (manager == null) return false;
            if (Build.VERSION.SDK_INT >= 24) {
                int sub = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId();
                if (sub >= 0) manager = manager.createForSubscriptionId(sub);
            }
            if (Build.VERSION.SDK_INT >= 26) return manager.isDataEnabled();
            return Settings.Global.getInt(context.getContentResolver(), "mobile_data", 0) == 1;
        } catch (RuntimeException e) { return false; }
    }

    public static boolean isWiFiEnabled(Context context) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }
    public static boolean isBatterSaverEnabled(ContentResolver contentResolver) {
        return Settings.Global.getInt(contentResolver, "low_power", 0) >= 1;
    }
    public static boolean isAirplaneEnabled(ContentResolver contentResolver) {
        return Settings.Global.getInt(contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
    public static boolean isBluetoothEnabled(ContentResolver contentResolver) {
        // Note: Settings.Global.BLUETOOTH_ON is not available in the public API
        // Using the hardcoded string "bluetooth_on" is the standard approach
        return Settings.Global.getInt(contentResolver,
                "bluetooth_on", 0) != 0;
    }
    public static boolean isLocationEnabled(ContentResolver contentResolver) {
        return Settings.Secure.getInt(contentResolver,
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF;
    }
    public static boolean isLockscreenTimeoutValueTooHigh(ContentResolver contentResolver) {
        return Settings.Secure.getInt(contentResolver, "lock_screen_lock_after_timeout", 5000) >= 5000;
    }

    public static float getLockscreenTimeoutValue(ContentResolver contentResolver) {
        return ((Settings.Secure.getInt(contentResolver, "lock_screen_lock_after_timeout", 5000) / 1000f) / 60f);
    }

    public static boolean doesSettingExist(String settingName) {
        String[] updatableSettings = {"respectHotspot", "turnOffDataInDoze", "turnOffWiFiInDoze", "ignoreLockscreenTimeout",
                "dozeEnterDelay", "autoRotateAndBrightnessFix", "disableMotionSensors", "disableWhenCharging",
                "showPersistentNotif", "waitForUnlock", "turnOnAirplaneInDoze", "turnOffBluetoothInDoze", "turnOffGPSInDoze", "turnOnBatterySaverInDoze", "whitelistMusicAppNetwork", "whitelistCurrentApp"};
        return Arrays.asList(updatableSettings).contains(settingName);
    }

    public static void updateSettingBool(Context context, String settingName, boolean settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(settingName, settingValue).apply();
    }

    public static void updateSettingInt(Context context, String settingName, int settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(settingName, settingValue).apply();
    }

    public static boolean isSettingBool(String settingName) {
        // Since all the settings loaded dynamically by the service except dozeEnterDelay are bools,
        // return true only if settingName != dozeEnterDelay
        if (settingName.equals("dozeEnterDelay")) {
            return false;
        } else return true;
    }

    public static boolean isScreenOn(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }
    public static boolean isDeviceLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km.isKeyguardLocked();
    }
    static class ReloadSettingsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadSettings();
        }    
    }
    static ReloadSettingsReceiver reloadSettingsReceiver;
    static boolean disableLogcat = false;
    static Context applicationContext;
    static {
        init();
    }
    private static void init() {
        applicationContext = MyApplication.getAppContext();
        reloadSettingsReceiver = new ReloadSettingsReceiver();
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(reloadSettingsReceiver, new IntentFilter("reload-settings"));
        disableLogcat = getDefaultSharedPreferences(applicationContext).getBoolean("disableLogcat", false);
    }

    public static void reloadSettings() {
        disableLogcat = getDefaultSharedPreferences(applicationContext).getBoolean("disableLogcat", false);
    }

    public static void logToLogcat(String TAG, String message) {
        if (!disableLogcat) {
            Log.i(TAG, message);
        }
    }

    public static void showDisabledNotification(Context context) {
        boolean showDisabledNotification = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("showDisabledNotification", false);
        if (!showDisabledNotification || !isPostNotificationPermissionGranted(context)) {
            return;
        }

        // Create notification channel for disabled state (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_DISABLED);
            
            if (channel == null) {
                CharSequence name = context.getString(R.string.notification_channel_disabled_name);
                String description = context.getString(R.string.notification_channel_disabled_description);
                int importance = NotificationManager.IMPORTANCE_LOW;
                channel = new NotificationChannel(CHANNEL_DISABLED, name, importance);
                channel.setDescription(description);
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Create broadcast intent to enable ForceDoze when tapping the notification
        Intent enableIntent = new Intent(context, NotificationActionReceiver.class);
        enableIntent.setAction("com.akylas.enforcedoze.ENABLE_FORCEDOZE");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 
            0, 
            enableIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = 
            new NotificationCompat.Builder(context, CHANNEL_DISABLED)
                .setSmallIcon(R.drawable.ic_battery_health)
                .setContentTitle(context.getString(R.string.enforcedoze_disabled_notif_title))
                .setContentText(context.getString(R.string.enforcedoze_disabled_notif_text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(DISABLED_NOTIFICATION_ID, builder.build());
    }

    public static void hideDisabledNotification(Context context) {
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(DISABLED_NOTIFICATION_ID);
        notificationManager.cancel(9191);
    }

    public static void updateTileState(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(context, 
                    new ComponentName(context, ForceDozeTileService.class));
            } catch (Exception e) {
                Log.e("Utils", "Failed to update tile state: " + e.getMessage());
            }
        }
    }

    public static int userId() { return android.os.Process.myUid() / 100000; }

    public static boolean isShizukuMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("executionMode", "shizuku").equals("shizuku");
    }

    public static void grantPermissionsViaShizuku(Context context) {
        ShizukuHandler shizukuHandler = ShizukuHandler.getInstance(context);
        if (!Utils.isDumpPermissionGranted(context)) {
            logToLogcat("Utils", "Granting android.permission.DUMP to com.akylas.enforcedoze via Shizuku");
            shizukuHandler.executeCommand("pm grant com.akylas.enforcedoze android.permission.DUMP",
                    (commandCode, exitCode, stdout, stderr) -> {
                        if (exitCode == 0) {
                            logToLogcat("Utils", "DUMP permission granted successfully");
                        } else {
                            Log.e("Utils", "Failed to grant DUMP permission");
                        }
                    }, true);
        }
        if (!Utils.isReadPhoneStatePermissionGranted(context)) {
            logToLogcat("Utils", "Granting android.permission.READ_PHONE_STATE to com.akylas.enforcedoze via Shizuku");
            shizukuHandler.executeCommand("pm grant com.akylas.enforcedoze android.permission.READ_PHONE_STATE",
                    (commandCode, exitCode, stdout, stderr) -> {
                        if (exitCode == 0) {
                            logToLogcat("Utils", "READ_PHONE_STATE permission granted successfully");
                        } else {
                            Log.e("Utils", "Failed to grant READ_PHONE_STATE permission");
                        }
                    }, true);
        }
        if (!Utils.isSecureSettingsPermissionGranted(context) && Utils.isDeviceRunningOnN()) {
            logToLogcat("Utils", "Granting android.permission.WRITE_SECURE_SETTINGS to com.akylas.enforcedoze via Shizuku");
            shizukuHandler.executeCommand("pm grant com.akylas.enforcedoze android.permission.WRITE_SECURE_SETTINGS",
                    (commandCode, exitCode, stdout, stderr) -> {
                        if (exitCode == 0) {
                            logToLogcat("Utils", "WRITE_SECURE_SETTINGS permission granted successfully");
                        } else {
                            Log.e("Utils", "Failed to grant WRITE_SECURE_SETTINGS permission");
                        }
                    }, true);
        }
    }


    public static void openUrl(android.app.Activity activity, String url) {
        try {
            new androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(activity, android.net.Uri.parse(url));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(activity, R.string.settings_unavailable, android.widget.Toast.LENGTH_LONG).show();
        }
    }

}
