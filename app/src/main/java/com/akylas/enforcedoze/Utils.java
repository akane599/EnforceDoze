package com.akylas.enforcedoze;

import static android.content.Context.BATTERY_SERVICE;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.TileService;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

public class Utils {

    private static final int DISABLED_NOTIFICATION_ID = 9876;
    private static final String CHANNEL_DISABLED = "CHANNEL_DISABLED";
    public static final String ACTION_CUSTOM_DOZE_PERIOD_BOUNDARY =
            "com.akylas.enforcedoze.ACTION_CUSTOM_DOZE_PERIOD_BOUNDARY";
    private static final int CUSTOM_DOZE_PERIOD_REQUEST_CODE = 9012;

    public static void startForceDozeService(Context context) {
        Intent intent = new Intent(context, ForceDozeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            hideDisabledNotification(context);
        } catch (RuntimeException e) {
            String error =
                    "Android blocked background startup. Open EnforceDoze and retry. "
                            + e.getClass().getSimpleName();
            context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
                    .edit()
                    .putString("status", "Monitoring needs attention")
                    .putString("error", error)
                    .apply();
            new EvidenceStore(context).record("Startup blocked", error);
            showRecoveryNotification(context, error);
        }
        updateTileState(context);
    }

    public static void stopForceDozeService(Context context) {
        cancelCustomDozePeriodAlarm(context);
        if (isMyServiceRunning(ForceDozeService.class, context)) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("reload-settings"));
        } else if (new RecoveryStore(context).pending()) {
            startForceDozeService(context);
        }
        updateTileState(context);
    }

    public static void showRecoveryNotification(Context context, String detail) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(
                    new NotificationChannel(
                            "recovery_v2",
                            "Access and restoration",
                            NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open =
                PendingIntent.getActivity(
                        context,
                        0,
                        new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(
                8765,
                new NotificationCompat.Builder(context, "recovery_v2")
                        .setSmallIcon(R.drawable.ic_battery_health)
                        .setContentTitle("EnforceDoze needs attention")
                        .setContentText(detail)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                        .setContentIntent(open)
                        .setOnlyAlertOnce(true)
                        .build());
    }

    public static void applyForceDozeSchedule(Context context) {
        scheduleNextCustomDozePeriodBoundary(context);
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("serviceEnabled", false)) {
            if (isMyServiceRunning(ForceDozeService.class, context))
                LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(new Intent("schedule-boundary"));
            else startForceDozeService(context);
        }
    }

    public static void scheduleNextCustomDozePeriodBoundary(Context context) {
        cancelCustomDozePeriodAlarm(context);
        if (!getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false)
                || !hasCustomDozePeriods(context)) {
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
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            logToLogcat(
                    "EnforceDoze", "Exact custom period alarm not allowed, using inexact alarm");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
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
        return PendingIntent.getBroadcast(
                context,
                CUSTOM_DOZE_PERIOD_REQUEST_CODE,
                intent,
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
            nextBoundaryMillis =
                    Math.min(nextBoundaryMillis, getNextBoundaryMillis(now, parsedPeriod[0]));
            nextBoundaryMillis =
                    Math.min(nextBoundaryMillis, getNextBoundaryMillis(now, parsedPeriod[1]));
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

    private static int[] parseCustomDozePeriod(String period) {
        return ScheduleRules.parse(period);
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPostNotificationPermissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkCallingOrSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean areRecoveryNotificationsAvailable(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && !isPostNotificationPermissionGranted(context))
            return false;
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
            return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                    context.getSystemService(NotificationManager.class)
                            .getNotificationChannel("recovery_v2");
            return channel == null
                    || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    public static boolean isReadPhoneStatePermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isConnectedToCharger(Context context) {
        Intent intent =
                context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            return plugged > 0;
        } else return false;
    }

    public static int getBatteryLevel(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return level >= 0 && level <= 100 ? level : -1;
    }

    public static boolean isUserInCommunicationCall(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return manager.getMode() == AudioManager.MODE_IN_CALL
                || manager.getMode() == AudioManager.MODE_IN_COMMUNICATION;
    }

    public static boolean isUserInCall(Context context) {
        if (Utils.isReadPhoneStatePermissionGranted(context)) {
            TelephonyManager manager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            try {
                return manager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
            } catch (SecurityException e) {
                return true;
            }
        }
        return false;
    }

    public static boolean doesSettingExist(String settingName) {
        String[] updatableSettings = {
            "ignoreIfHotspot",
            "turnOffDataInDoze",
            "turnOffWiFiInDoze",
            "ignoreLockscreenTimeout",
            "dozeEnterDelay",
            "disableMotionSensors",
            "disableWhenCharging",
            "showPersistentNotif",
            "waitForUnlock",
            "turnOnBatterySaverInDoze",
            "whitelistMusicAppNetwork"
        };
        return Arrays.asList(updatableSettings).contains(settingName);
    }

    public static void updateSettingBool(
            Context context, String settingName, boolean settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(settingName, settingValue)
                .apply();
    }

    public static void updateSettingInt(Context context, String settingName, int settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(settingName, settingValue)
                .apply();
    }

    public static boolean isDeviceLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km.isKeyguardLocked();
    }

    public static void logToLogcat(String tag, String message) {
        Context context = MyApplication.getAppContext();
        if (context != null
                && !getDefaultSharedPreferences(context).getBoolean("disableLogcat", false))
            Log.i(tag, message);
    }

    public static void showDisabledNotification(Context context) {
        boolean showDisabledNotification =
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("showDisabledNotification", false);
        if (!showDisabledNotification) {
            return;
        }

        // Create notification channel for disabled state (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel =
                    notificationManager.getNotificationChannel(CHANNEL_DISABLED);

            if (channel == null) {
                CharSequence name = context.getString(R.string.notification_channel_disabled_name);
                String description =
                        context.getString(R.string.notification_channel_disabled_description);
                int importance = NotificationManager.IMPORTANCE_LOW;
                channel = new NotificationChannel(CHANNEL_DISABLED, name, importance);
                channel.setDescription(description);
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Create broadcast intent to enable ForceDoze when tapping the notification
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_DISABLED)
                        .setSmallIcon(R.drawable.ic_battery_health)
                        .setContentTitle(
                                context.getString(R.string.enforcedoze_disabled_notif_title))
                        .setContentText("Open EnforceDoze to start monitoring")
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
    }

    public static void updateTileState(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                        context, new ComponentName(context, ForceDozeTileService.class));
            } catch (Exception e) {
                Log.e("Utils", "Failed to update tile state: " + e.getMessage());
            }
        }
    }

    public static void openUrl(android.app.Activity activity, String url) {
        try {
            new androidx.browser.customtabs.CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(activity, android.net.Uri.parse(url));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(
                            activity, "No browser is installed", android.widget.Toast.LENGTH_LONG)
                    .show();
        }
    }
}
