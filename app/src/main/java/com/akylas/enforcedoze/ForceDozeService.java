package com.akylas.enforcedoze;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Event-driven lifecycle. All mutations and undo operations run on one worker queue. */
public class ForceDozeService extends Service {
    public static final String ACTION_STOP = "stop-enforcedoze";
    public static final String ACTION_STATE = "enforcedoze-state";
    public static volatile String status = "OFF";
    private static final int NOTIFICATION_ID = 1234;
    private static final String CHANNEL = "enforcedoze_service_v2";
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile boolean destroyed;
    private boolean stopping;
    private boolean waitingForUnlock;
    private boolean entering;
    private volatile boolean sessionActive;
    private volatile boolean maintenance;
    private long sessionStart;
    private int entryBattery;
    private volatile boolean sessionCharged;
    private String sessionMode;
    private Map<String, ?> sessionConfig;
    private SharedPreferences prefs;
    private RecoveryJournal journal;
    private ShizukuHandler shizuku;
    private PowerManager power;
    private PowerManager.WakeLock transitionLock;
    private final ShizukuHandler.OnAvailibilityChange accessListener = available -> {
        if (!Utils.isShizukuMode(this)) return;
        if (!available) leave("NEEDS_ACCESS", false);
        else evaluate(false);
    };
    private final BroadcastReceiver internal = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP.equals(intent.getAction())) leave("OFF", true);
            else if ("reenter-doze".equals(intent.getAction())) evaluate(true);
            else evaluate(false);
        }
    };
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action) && sessionActive) sessionCharged = true;
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                cancelDelay();
                // Restore biometrics/sensors/radios immediately so unlocking always works.
                waitingForUnlock = prefs.getBoolean("waitForUnlock", false) && Utils.isDeviceLocked(context);
                if (waitingForUnlock && sessionActive && !entering) restoreForUnlock();
                else leave("WAITING", false);
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                waitingForUnlock = false;
                leave("WAITING", false);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                waitingForUnlock = false;
                evaluate(false);
            } else if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                handleIdleChanged();
            } else {
                // Includes charging, phone state, timezone and manual clock changes.
                Utils.scheduleNextCustomDozePeriodBoundary(context);
                evaluate(false);
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        journal = new RecoveryJournal(this);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.notification_channel_silent_name), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        // Promote before initialization, permission work, or shell commands (API 26+).
        publish("STARTING");
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT,
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED}) filter.addAction(action);
        ContextCompat.registerReceiver(this, events, filter, ContextCompat.RECEIVER_EXPORTED);
        IntentFilter local = new IntentFilter();
        for (String action : new String[]{ACTION_STOP, "reload-settings", "reload-app-blocklist", "reload-notification-blocklist", "reenter-doze"}) local.addAction(action);
        LocalBroadcastManager.getInstance(this).registerReceiver(internal, local);
        shizuku = ShizukuHandler.getInstance(this);
        shizuku.addAvailabilityListener(accessListener);
        transitionLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "enforcedoze:transition");
        transitionLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) leave("OFF", true);
        else if (!prefs.getBoolean("serviceEnabled", false)) leave("OFF", true);
        else { Utils.hideDisabledNotification(this); evaluate(false); }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void publish(String next) {
        if (destroyed) return;
        status = next;
        prefs.edit().putString("runtimeStatus", next).apply();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, NotificationActionReceiver.class).setAction("com.akylas.enforcedoze.DISABLE_FORCEDOZE");
        PendingIntent pause = PendingIntent.getBroadcast(this, 10, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_power_settings_new_white_24dp).setContentTitle(getString(R.string.app_name))
                .setContentText(UiSupport.statusText(this, next)).setContentIntent(content)
                .setStyle(prefs.getBoolean("showPersistentNotif", true) && prefs.contains("lastSessionSummary")
                        ? new NotificationCompat.BigTextStyle().bigText(UiSupport.statusText(this, next) + "\n" + prefs.getString("lastSessionSummary", "")) : null)
                .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).setShowWhen(false)
                .addAction(0, getString(R.string.dashboard_stop), pause).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION_ID, notification);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_STATE));
        Utils.updateTileState(this);
    }
    private boolean accessReady() {
        String mode = CommandExecutor.mode(this);
        if ("shizuku".equals(mode)) return shizuku.isShizukuAvailable();
        if ("root".equals(mode)) return prefs.getBoolean("isSuAvailable", false);
        return "adb".equals(mode) && Build.VERSION.SDK_INT < 34 && Utils.isDumpPermissionGranted(this)
                && Utils.isSecureSettingsPermissionGranted(this);
    }
    private boolean eligible() {
        return !destroyed && !stopping && prefs.getBoolean("serviceEnabled", false)
                && !Utils.isScreenOn(this) && !waitingForUnlock && Utils.isInsideCustomDozePeriod(this)
                && !(prefs.getBoolean("disableWhenCharging", true) && Utils.isConnectedToCharger(this))
                && !Utils.isUserInCommunicationCall(this) && !Utils.isUserInCall(this) && accessReady();
    }
    private void evaluate(boolean delayElapsed) {
        if (destroyed || stopping) return;
        if (!prefs.getBoolean("serviceEnabled", false)) { leave("OFF", true); return; }
        if (!accessReady()) { leave("NEEDS_ACCESS", false); return; }
        if (!Utils.isInsideCustomDozePeriod(this)) { leave("SCHEDULED", false); return; }
        if (Utils.isScreenOn(this)) { if (waitingForUnlock && sessionActive) return; leave("WAITING", false); return; }
        if (!eligible()) { leave("PAUSED", false); return; }
        if (sessionActive || entering) return;
        cancelDelay();
        long delay = Math.max(0, Math.min(1800, prefs.getInt("dozeEnterDelay", 0))) * 1000L;
        if (!prefs.getBoolean("ignoreLockscreenTimeout", true)) {
            delay += Math.max(0, Math.min(1800000, Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000)));
        }
        if (!delayElapsed && delay > 0) {
            publish("DELAYED");
            AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, delayIntent());
            return;
        }
        enter();
    }
    private PendingIntent delayIntent() {
        return PendingIntent.getBroadcast(this, 77, new Intent(this, ReenterDoze.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void cancelDelay() { ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(delayIntent()); }
    private void enter() {
        final int token = ++generation;
        final String mode = CommandExecutor.mode(this);
        final Map<String, ?> config = prefs.getAll();
        entering = true;
        prefs.edit().remove("lastError").apply();
        publish("ENTERING");
        transitionLock.acquire(60000);
        CommandExecutor.submit(() -> {
            try {
                if (!journal.restore()) { finishEnter(token, false, "Restore pending changes before starting another session"); return; }
                if (!valid(token)) { finishEnter(token, false, ""); return; }
                // Ensure the controller can receive events while the phone is idle.
                CommandExecutor.run(this, mode, "dumpsys deviceidle whitelist +" + getPackageName());
                boolean privileged = "shizuku".equals(mode) || "root".equals(mode) && bool(config, "isSuAvailable", false);
                boolean entered;
                if (privileged || Build.VERSION.SDK_INT < 24) {
                    entered = journal.applyCore(mode, "dumpsys deviceidle force-idle" + (Build.VERSION.SDK_INT >= 24 ? " deep" : ""), "dumpsys deviceidle unforce");
                    if (entered) {
                        CommandResult state = CommandExecutor.run(this, mode, "dumpsys deviceidle");
                        entered = state.success() && "IDLE".equals(CommandPolicy.idleState(state.output));
                    }
                } else {
                    // Legacy ADB grants: scope restoration to the exact keys changed.
                    entered = applyTunables(mode);
                }
                if (!entered || !valid(token)) { finishEnter(token, false, "Doze command failed; check Diagnostics"); return; }
                sessionMode = mode;
                sessionConfig = config;
                sessionActive = true;
                sessionStart = System.currentTimeMillis();
                entryBattery = Utils.getBatteryLevel(this);
                sessionCharged = Utils.isConnectedToCharger(this);
                record("ENTER");
                applyEnhancements(mode, config, token, privileged);
                finishEnter(token, true, "");
            } catch (RuntimeException e) { finishEnter(token, false, e.toString()); }
        });
    }
    private boolean applyTunables(String mode) {
        DozeTunableHandler tunables = DozeTunableHandler.getInstance();
        if (Build.VERSION.SDK_INT >= 34) {
            for (String command : tunables.getCommandsList()) {
                String[] parts = command.split(" ");
                if (parts.length != 5) return false;
                CommandResult old = CommandExecutor.run(this, mode, "device_config get device_idle " + parts[3]);
                if (!old.success()) return false;
                String undo = old.output.trim().equals("null") ? "device_config delete device_idle " + parts[3]
                        : "device_config put device_idle " + parts[3] + " " + CommandPolicy.quote(old.output.trim());
                if (!journal.applyCore(mode, command, undo)) return false;
            }
            return true;
        }
        return setting(mode, "global", "device_idle_constants", tunables.getTunableString()
                + ",inactive_to=0,sensing_to=0,locating_to=0,motion_inactive_to=0,idle_after_inactive_to=0,light_after_inactive_to=0", true);
    }
    private boolean valid(int token) { return token == generation && eligible(); }
    private static boolean bool(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback;
    }
    private boolean setting(String mode, String namespace, String key, String value) { return setting(mode, namespace, key, value, false); }
    private boolean setting(String mode, String namespace, String key, String value, boolean core) {
        CommandResult old = CommandExecutor.run(this, mode, "settings --user " + Utils.userId() + " get " + namespace + " " + key);
        if (!old.success()) return false;
        String original = old.output.trim();
        if (original.equals(value)) return true;
        String undo = original.equals("null") ? "settings --user " + Utils.userId() + " delete " + namespace + " " + key
                : "settings --user " + Utils.userId() + " put " + namespace + " " + key + " " + CommandPolicy.quote(original);
        String command = "settings --user " + Utils.userId() + " put " + namespace + " " + key + " " + CommandPolicy.quote(value);
        return core ? journal.applyCore(mode, command, undo) : journal.apply(mode, command, undo);
    }
    private void applyEnhancements(String mode, Map<String, ?> config, int token, boolean privileged) {
        if (!valid(token)) return;
        String playing = null;
        if (bool(config, "whitelistMusicAppNetwork", false)) {
            NotificationService listener = NotificationService.Companion.getInstance();
            if (listener == null) playing = "unknown"; // Fail conservatively if notification access was revoked.
            else {
                String[] name = new String[1];
                listener.getPlayingPackageName(value -> { name[0] = value; return null; });
                playing = name[0];
            }
        }
        boolean keepNetwork = playing != null;
        if (bool(config, "respectHotspot", true) && (bool(config, "turnOffWiFiInDoze", false)
                || bool(config, "turnOffDataInDoze", false) || bool(config, "turnOnAirplaneInDoze", false))) {
            CommandResult hotspot = CommandExecutor.run(this, mode, PrivilegedOperations.PREFIX + "hotspot get");
            // WIFI_AP_STATE_DISABLED = 11. Unknown/transitional states preserve connectivity.
            keepNetwork |= !hotspot.success() || !hotspot.output.trim().equals("11");
        }
        if (privileged && valid(token)) {
            if (!keepNetwork) {
                if (bool(config, "turnOnAirplaneInDoze", false) && !Utils.isAirplaneEnabled(getContentResolver()))
                    mutate(token, mode, "cmd connectivity airplane-mode enable", "cmd connectivity airplane-mode disable");
                if (bool(config, "turnOffWiFiInDoze", false) && Utils.isWiFiEnabled(this))
                    mutate(token, mode, "svc wifi disable", "svc wifi enable");
                if (bool(config, "turnOffDataInDoze", false) && Utils.isMobileDataEnabled(this))
                    mutate(token, mode, "svc data disable", "svc data enable");
            }
            if (!keepNetwork && bool(config, "turnOffBluetoothInDoze", false) && Utils.isBluetoothEnabled(getContentResolver()))
                mutate(token, mode, "svc bluetooth disable", "svc bluetooth enable");
            if (!keepNetwork && bool(config, "turnOffGPSInDoze", false) && Utils.isLocationEnabled(getContentResolver())) {
                if (Build.VERSION.SDK_INT >= 30) mutate(token, mode, "cmd location set-location-enabled false --user " + Utils.userId(), "cmd location set-location-enabled true --user " + Utils.userId());
                else if (valid(token)) setting(mode, "secure", "location_mode", "0");
            }
            if (bool(config, "turnOnBatterySaverInDoze", false) && !power.isPowerSaveMode())
                mutate(token, mode, "cmd power set-mode 1", "cmd power set-mode 0");
            if (bool(config, "turnOffBiometricsInDoze", false) && valid(token)) setting(mode, "secure", "biometric_keyguard_enabled", "0");
            if (bool(config, "turnOffAllSensorsInDoze", false) && valid(token)) {
                CommandResult old = CommandExecutor.run(this, mode, PrivilegedOperations.PREFIX + "sensors get");
                if (old.success() && old.output.trim().equals("false"))
                    mutate(token, mode, PrivilegedOperations.PREFIX + "sensors true", PrivilegedOperations.PREFIX + "sensors false");
            }
        } else if (Build.VERSION.SDK_INT < 29 && !keepNetwork && bool(config, "turnOffWiFiInDoze", false) && Utils.isWiFiEnabled(this)) {
            mutate(token, mode, "svc wifi disable", "svc wifi enable");
        }
        if (bool(config, "disableMotionSensors", true) && valid(token)) {
            Object sensorPackage = config.get("sensorWhitelistPackage");
            String pkg = sensorPackage == null ? "" : sensorPackage.toString();
            // Refuse to overwrite another tool's sensor restriction.
            CommandResult sensors = CommandExecutor.run(this, mode, "dumpsys sensorservice");
            if (sensors.success() && sensors.output.contains("Mode : NORMAL"))
                mutate(token, mode, "dumpsys sensorservice restrict" + (CommandPolicy.validPackage(pkg) ? " " + pkg : ""), "dumpsys sensorservice enable");
        }
        Set<String> focused = new HashSet<>();
        if (bool(config, "whitelistCurrentApp", false)) {
            CommandResult apps = CommandExecutor.run(this, mode, "dumpsys activity activities");
            Matcher matcher = Pattern.compile("(?:mResumedActivity|topResumedActivity|mFocusedApp)[^\\n]*? ([A-Za-z][\\w.]*)/").matcher(apps.output);
            while (matcher.find()) focused.add(matcher.group(1));
            if (!apps.success() || focused.isEmpty()) return; // Do not suspend an unknown foreground app.
        }
        Set<String> blocked = set(config, "dozeAppBlockList");
        for (String pkg : blocked) {
            if (!valid(token)) return;
            if (!CommandPolicy.validPackage(pkg) || focused.contains(pkg) || CommandPolicy.protectedPackage(pkg)) continue;
            try {
                ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
                if (!info.enabled || (Build.VERSION.SDK_INT >= 24 && (info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0)) continue;
                mutate(token, mode, "pm " + (Build.VERSION.SDK_INT >= 24 ? "suspend " : "disable ") + "--user " + Utils.userId() + " " + pkg,
                        "pm " + (Build.VERSION.SDK_INT >= 24 ? "unsuspend " : "enable ") + "--user " + Utils.userId() + " " + pkg);
            } catch (Exception ignored) { }
        }
        for (String pkg : set(config, "notificationBlockList")) {
            if (!valid(token)) return;
            if (!CommandPolicy.validPackage(pkg) || pkg.equals(getPackageName()) || blocked.contains(pkg)) continue;
            try {
                int uid = getPackageManager().getApplicationInfo(pkg, 0).uid;
                String suffix = " " + pkg + " " + uid;
                CommandResult old = CommandExecutor.run(this, mode, PrivilegedOperations.PREFIX + "notifications get" + suffix);
                if (old.success() && old.output.trim().equals("true"))
                    mutate(token, mode, PrivilegedOperations.PREFIX + "notifications false" + suffix, PrivilegedOperations.PREFIX + "notifications true" + suffix);
            } catch (Exception ignored) { }
        }
    }
    @SuppressWarnings("unchecked") private static Set<String> set(Map<String, ?> config, String key) {
        Object value = config.get(key);
        return value instanceof Set ? new HashSet<>((Set<String>) value) : Collections.emptySet();
    }
    private void mutate(int token, String mode, String command, String undo) {
        if (valid(token)) journal.apply(mode, command, undo);
    }
    private void finishEnter(int token, boolean success, String error) {
        if (!success || token != generation) journal.restore();
        main.post(() -> {
            if (token != generation || destroyed) return;
            entering = false;
            releaseLock();
            if (!success) {
                sessionActive = false;
                prefs.edit().putString("lastError", error).apply();
                publish(accessReady() ? "ERROR" : "NEEDS_ACCESS");
            } else publish("ACTIVE");
        });
    }
    private void leave(String next, boolean stop) {
        if (destroyed) return;
        cancelDelay();
        final int token = ++generation;
        entering = false;
        stopping |= stop;
        transitionLock.acquire(60000);
        publish("RESTORING");
        CommandExecutor.submit(() -> {
            boolean restored = journal.restore();
            if (restored && sessionActive && prefs.getBoolean("autoRotateAndBrightnessFix", false) && Utils.isWriteSettingsPermissionGranted(this)) {
                repairDisplaySettings();
            }
            if (sessionActive) {
                record("EXIT");
                long minutes = Math.max(0, System.currentTimeMillis() - sessionStart) / 60000;
                int used = entryBattery - Utils.getBatteryLevel(this);
                String summary = sessionCharged || used < 0 ? getString(R.string.stats_charging) : getString(R.string.last_session_summary, minutes, used);
                prefs.edit().putString("lastSessionSummary", summary).apply();
            }
            sessionActive = false;
            maintenance = false;
            main.post(() -> {
                if (destroyed || token != generation) return;
                releaseLock();
                if (!restored) prefs.edit().putString("lastError", "Some changes still need restoration. Reconnect the original access mode and tap Retry.").apply();
                publish(restored ? next : "RECOVERY");
                if (stopping) stopSelf();
            });
        });
    }
    private void restoreForUnlock() {
        final int token = ++generation;
        transitionLock.acquire(60000);
        CommandExecutor.submit(() -> {
            boolean restored = journal.restoreEnhancements();
            main.post(() -> {
                if (destroyed || token != generation) return;
                releaseLock(); publish(restored ? "LOCKED" : "RECOVERY");
            });
        });
    }
    private void handleIdleChanged() {
        if (!sessionActive || destroyed || stopping || Utils.isScreenOn(this)) return;
        final int token = generation;
        final String mode = sessionMode;
        CommandExecutor.submit(() -> {
            CommandResult result = CommandExecutor.run(this, mode, "dumpsys deviceidle");
            if (token != generation || !result.success()) return;
            String state = CommandPolicy.idleState(result.output);
            if ("IDLE_MAINTENANCE".equals(state) && !maintenance) {
                maintenance = true;
                record("EXIT_MAINTENANCE");
                boolean restored = journal.restoreEnhancements();
                main.post(() -> { if (!destroyed && token == generation) publish(restored ? "MAINTENANCE" : "RECOVERY"); });
            } else if ("IDLE".equals(state) && maintenance && valid(token)) {
                if (!journal.restoreEnhancements()) return;
                maintenance = false;
                record("ENTER_MAINTENANCE");
                applyEnhancements(mode, sessionConfig, token, "shizuku".equals(mode) || "root".equals(mode) && bool(sessionConfig, "isSuAvailable", false));
                main.post(() -> { if (!destroyed && token == generation) publish("ACTIVE"); });
            }
        });
    }
    private void record(String event) {
        if (prefs.getBoolean("disableStats", false)) return;
        ArrayList<String> entries = new ArrayList<>(prefs.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        entries.add(System.currentTimeMillis() + "," + (sessionCharged || Utils.isConnectedToCharger(this) ? -1 : Utils.getBatteryLevel(this)) + "," + event);
        Collections.sort(entries);
        if (entries.size() > 500) entries = new ArrayList<>(entries.subList(entries.size() - 500, entries.size()));
        prefs.edit().putStringSet("dozeUsageDataAdvanced", new LinkedHashSet<>(entries)).apply();
    }
    private void repairDisplaySettings() {
        try {
            boolean rotate = Utils.isAutoRotateEnabled(this), brightness = Utils.isAutoBrightnessEnabled(this);
            try {
                Utils.setAutoRotateEnabled(this, !rotate);
                Utils.setAutoBrightnessEnabled(this, !brightness);
                Thread.sleep(100);
            } finally {
                Utils.setAutoRotateEnabled(this, rotate);
                Utils.setAutoBrightnessEnabled(this, brightness);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (RuntimeException e) { prefs.edit().putString("lastError", e.toString()).apply(); }
    }
    private void releaseLock() { if (transitionLock != null && transitionLock.isHeld()) transitionLock.release(); }
    @Override public void onDestroy() {
        destroyed = true;
        ++generation;
        cancelDelay();
        unregisterReceiver(events);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(internal);
        shizuku.removeAvailabilityListener(accessListener);
        main.removeCallbacksAndMessages(null);
        CommandExecutor.submit(() -> { journal.restore(); sessionActive = false; });
        releaseLock();
        status = "OFF";
        stopForeground(true);
        Utils.updateTileState(this);
        super.onDestroy();
    }
}
