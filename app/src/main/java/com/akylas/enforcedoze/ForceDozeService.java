package com.akylas.enforcedoze;

import android.app.AlarmManager;
import android.app.Notification;
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
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile boolean destroyed;
    private boolean stopping;
    private boolean waitingForUnlock;
    private boolean entering;
    private boolean restoring;
    private boolean restoringForUnlock;
    private String afterRestoreStatus;
    private long delayDeadline;
    private MonitorNotification monitorNotification;
    private boolean foregroundStarted;
    private boolean notificationPermissionGranted;
    private volatile boolean sessionActive;
    private volatile boolean maintenance;
    private long sessionStart;
    private int entryBattery;
    private volatile boolean sessionCharged;
    private boolean sensorsChanged;
    private String sessionMode;
    private Map<String, ?> sessionConfig;
    private volatile String evidenceSessionId;
    private SharedPreferences prefs;
    private RecoveryJournal journal;
    private ShizukuHandler shizuku;
    private PowerManager power;
    private PowerManager.WakeLock transitionLock;
    private AutoCloseable callModeMonitor;
    private final ShizukuHandler.OnAvailibilityChange accessListener = available -> {
        if (!Utils.isShizukuMode(this)) return;
        if (!available) leave("NEEDS_ACCESS", false);
        else evaluate();
    };
    private final BroadcastReceiver internal = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP.equals(intent.getAction())) leave("OFF", true);
            else evaluate();
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
                if (waitingForUnlock && sessionActive && !entering && !restoring && !pauseRequested()) restoreForUnlock();
                else leave("WAITING", false);
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                waitingForUnlock = false;
                leave("WAITING", false);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                boolean resumeAfterLockscreen = waitingForUnlock && sessionActive;
                waitingForUnlock = false;
                // Waking to the lockscreen restored enhancements but retained the core
                // session. End that session before resleep so sensors/radios are applied
                // again, and invalidate any unlock-restoration callback still queued.
                if (resumeAfterLockscreen) leave("WAITING", false);
                else evaluate();
            } else if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                if (evidenceSessionId != null && DozeEvidence.enabled(context)) {
                    DozeObservation observation = androidObservation("IDLE_BROADCAST", evidenceSessionId, CommandExecutor.mode(context));
                    CommandExecutor.submit(() -> DozeEvidence.append(ForceDozeService.this, observation));
                }
                handleIdleChanged();
            } else {
                // Includes charging, phone state, timezone and manual clock changes.
                if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action))
                    Utils.scheduleNextCustomDozePeriodBoundary(context);
                evaluate();
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        journal = new RecoveryJournal(this);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        monitorNotification = new MonitorNotification(this, System.currentTimeMillis());
        monitorNotification.createChannel();
        transitionLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "enforcedoze:transition");
        transitionLock.setReferenceCounted(false);
        // Remove a delayed-entry alarm left by a process that was killed.
        ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(delayIntent());
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
        callModeMonitor = CallModeMonitor.start(this, () -> evaluate());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) leave("OFF", true);
        else if (!prefs.getBoolean("serviceEnabled", false)) leave("OFF", true);
        else { Utils.hideDisabledNotification(this); evaluate(); }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void publish(String next) {
        if (destroyed) return;
        boolean stateChanged = !next.equals(status);
        status = next;
        if (!next.equals(prefs.getString("runtimeStatus", "")))
            prefs.edit().putString("runtimeStatus", next).apply();
        boolean detailed = prefs.getBoolean("detailedMonitorNotification", false);
        String summary = detailed && prefs.getBoolean("showPersistentNotif", true)
                ? prefs.getString("lastSessionSummary", "") : "";
        boolean notificationAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (notificationAllowed && !notificationPermissionGranted) monitorNotification.invalidate();
        notificationPermissionGranted = notificationAllowed;
        Notification notification = monitorNotification.update(next, summary, detailed);
        if (notification != null) {
            if (!foregroundStarted) {
                if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(NOTIFICATION_ID, notification);
                foregroundStarted = true;
            } else if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
            }
        }
        if (stateChanged) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_STATE));
            Utils.updateTileState(this);
        }
    }
    private boolean accessReady() {
        String mode = CommandExecutor.mode(this);
        if ("shizuku".equals(mode)) return shizuku.isShizukuAvailable();
        if ("root".equals(mode)) return prefs.getBoolean("isSuAvailable", false);
        return "adb".equals(mode) && Build.VERSION.SDK_INT < 34 && Utils.isDumpPermissionGranted(this)
                && Utils.isSecureSettingsPermissionGranted(this);
    }
    private boolean pauseRequested() {
        return prefs.getBoolean("disableWhenCharging", true) && Utils.isConnectedToCharger(this)
                || Utils.isUserInCommunicationCall(this) || Utils.isUserInCall(this);
    }
    private boolean eligible() {
        return !destroyed && !stopping && prefs.getBoolean("serviceEnabled", false)
                && !Utils.isScreenOn(this) && !waitingForUnlock && Utils.isInsideCustomDozePeriod(this)
                && !pauseRequested() && accessReady();
    }
    private void evaluate() {
        if (destroyed || stopping) return;
        if (!prefs.getBoolean("serviceEnabled", false)) { leave("OFF", true); return; }
        if (!accessReady()) { leave("NEEDS_ACCESS", false); return; }
        if (!Utils.isInsideCustomDozePeriod(this)) { leave("SCHEDULED", false); return; }
        // Charging and calls override waiting for unlock, including on the lock screen.
        if (pauseRequested()) { leave("PAUSED", false); return; }
        if (Utils.isScreenOn(this)) { if (waitingForUnlock && sessionActive) return; leave("WAITING", false); return; }
        if (!eligible()) { leave("PAUSED", false); return; }
        if (restoring) return; // Completion evaluates the latest screen/access state again.
        if (sessionActive || entering) return;
        long delay = Math.max(0, Math.min(1800, prefs.getInt("dozeEnterDelay", 0))) * 1000L;
        if (!prefs.getBoolean("ignoreLockscreenTimeout", true)) {
            delay += Math.max(0, Math.min(1800000, Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000)));
        }
        if (delay > 0 && (delayDeadline == 0 || SystemClock.elapsedRealtime() < delayDeadline)) {
            // Repeated binder/audio/settings events must not restart an existing countdown.
            if (delayDeadline == 0) {
                delayDeadline = SystemClock.elapsedRealtime() + delay;
                publish("DELAYED");
                AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, delayDeadline, delayIntent());
            }
            return;
        }
        cancelDelay();
        enter();
    }
    private PendingIntent delayIntent() {
        return PendingIntent.getBroadcast(this, 77, new Intent(this, ReenterDoze.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void cancelDelay() {
        if (delayDeadline == 0) return;
        delayDeadline = 0;
        ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(delayIntent());
    }
    private void enter() {
        final int token = ++generation;
        final String mode = CommandExecutor.mode(this);
        final String evidenceId = java.util.UUID.randomUUID().toString();
        evidenceSessionId = evidenceId;
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
                if (!power.isIgnoringBatteryOptimizations(getPackageName()))
                    CommandExecutor.run(this, mode, "dumpsys deviceidle whitelist +" + getPackageName());
                boolean privileged = "shizuku".equals(mode) || "root".equals(mode) && bool(config, "isSuAvailable", false);
                boolean entered;
                if (privileged || Build.VERSION.SDK_INT < 24) {
                    entered = journal.applyCore(mode, "dumpsys deviceidle force-idle" + (Build.VERSION.SDK_INT >= 24 ? " deep" : ""), "dumpsys deviceidle unforce");
                    if (entered) {
                        CommandResult state = readIdleWithEvidence("ENTRY", evidenceId, mode);
                        entered = state.success() && "IDLE".equals(CommandPolicy.idleState(state.output));
                    }
                } else {
                    // Legacy ADB grants: scope restoration to the exact keys changed.
                    entered = applyTunables(mode);
                    DozeEvidence.append(this, androidObservation("LEGACY_TUNABLES", evidenceId, mode));
                }
                if (!entered || !valid(token)) { finishEnter(token, false, "Doze command failed; check Diagnostics"); return; }
                sessionMode = mode;
                sessionConfig = config;
                sessionActive = true;
                sessionStart = SystemClock.elapsedRealtime();
                entryBattery = Utils.getBatteryLevel(this);
                sessionCharged = Utils.isConnectedToCharger(this);
                sensorsChanged = false;
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
        boolean allSensorsOff = false;
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
            if (Build.VERSION.SDK_INT < 36 && bool(config, "turnOffBiometricsInDoze", false) && valid(token)) setting(mode, "secure", "biometric_keyguard_enabled", "0");
            if (bool(config, "turnOffAllSensorsInDoze", false) && valid(token)) {
                CommandResult old = CommandExecutor.run(this, mode, PrivilegedOperations.PREFIX + "sensors get");
                allSensorsOff = old.success() && old.output.trim().equals("true");
                if (old.success() && old.output.trim().equals("false") && valid(token)) {
                    allSensorsOff = journal.apply(mode, PrivilegedOperations.PREFIX + "sensors true", PrivilegedOperations.PREFIX + "sensors false");
                    sensorsChanged |= allSensorsOff;
                }
            }
        } else if (Build.VERSION.SDK_INT < 29 && !keepNetwork && bool(config, "turnOffWiFiInDoze", false) && Utils.isWiFiEnabled(this)) {
            mutate(token, mode, "svc wifi disable", "svc wifi enable");
        }
        if (privileged && !allSensorsOff && bool(config, "disableMotionSensors", true) && valid(token)) {
            Object sensorPackage = config.get("sensorWhitelistPackage");
            String pkg = sensorPackage == null ? "" : sensorPackage.toString();
            // Refuse to overwrite another tool's sensor restriction.
            CommandResult sensors = CommandExecutor.run(this, mode, PrivilegedOperations.PREFIX + "motion get");
            if (sensors.success() && SensorRestriction.parse(sensors.output).mode.equals("NORMAL") && valid(token)) {
                String exception = SensorRestriction.exemption(CommandPolicy.validPackage(pkg) ? pkg : "");
                // Include an explicit no-client token when no exception is selected. A missing
                // argument is rejected by Android; an empty string would allow every package.
                sensorsChanged |= journal.apply(mode, PrivilegedOperations.PREFIX + "motion restrict " + exception,
                        PrivilegedOperations.PREFIX + "motion enable " + exception);
            } else if (!sensors.success()) {
                prefs.edit().putString("lastError", "Motion sensor restriction was not applied: " + sensors.output).apply();
            }
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
        if (!success || token != generation) {
            journal.restore();
            String id = evidenceSessionId;
            if (token == generation && id != null) DozeEvidence.append(this,
                    androidObservation("ENTRY_ABORTED", id, CommandExecutor.mode(this)));
        }
        main.post(() -> {
            if (token != generation || destroyed) return;
            entering = false;
            releaseLock();
            if (!success) {
                evidenceSessionId = null;
                sessionActive = false;
                recordFallbackError(error);
                publish(accessReady() ? "ERROR" : "NEEDS_ACCESS");
            } else publish("ACTIVE");
        });
    }
    private void leave(String next, boolean stop) {
        if (destroyed) return;
        cancelDelay();
        stopping |= stop;
        afterRestoreStatus = next;
        if (restoring) return; // SCREEN_ON and USER_PRESENT share one restoration.
        if (!entering && !sessionActive && !journal.hasPending()) {
            publish(next);
            if (stopping) stopSelf();
            return;
        }
        final int token = ++generation;
        final String evidenceId = evidenceSessionId;
        final String evidenceMode = sessionActive && sessionMode != null ? sessionMode : CommandExecutor.mode(this);
        evidenceSessionId = null;
        entering = false;
        restoring = true;
        restoringForUnlock = false;
        transitionLock.acquire(60000);
        publish("RESTORING");
        CommandExecutor.submit(() -> {
            boolean restored = false;
            try {
                restored = journal.restore();
                if (evidenceId != null) DozeEvidence.append(this,
                        androidObservation(restored ? "RESTORED" : "RESTORE_PENDING", evidenceId, evidenceMode));
                if (restored && sessionActive && sensorsChanged && prefs.getBoolean("autoRotateAndBrightnessFix", false) && Utils.isWriteSettingsPermissionGranted(this)) {
                    repairDisplaySettings();
                }
                if (sessionActive) {
                    record("EXIT");
                    long minutes = Math.max(0, SystemClock.elapsedRealtime() - sessionStart) / 60000;
                    int used = entryBattery - Utils.getBatteryLevel(this);
                    String summary = sessionCharged || used < 0 ? getString(R.string.stats_charging) : getString(R.string.last_session_summary, minutes, used);
                    prefs.edit().putString("lastSessionSummary", summary).apply();
                }
            } catch (RuntimeException e) {
                prefs.edit().putString("lastError", e.toString()).apply();
                restored = !journal.hasPending();
            } finally {
                sessionActive = false;
                maintenance = false;
                final boolean complete = restored;
                main.post(() -> {
                    if (destroyed || token != generation) return;
                    restoring = false;
                    releaseLock();
                    if (!complete) recordFallbackError("Some changes still need restoration. Reconnect the original access mode and tap Retry.");
                    publish(complete ? afterRestoreStatus : "RECOVERY");
                    if (stopping) stopSelf();
                    else if (complete) evaluate();
                });
            }
        });
    }
    private void restoreForUnlock() {
        if (restoringForUnlock) return;
        restoringForUnlock = true;
        final int token = ++generation;
        transitionLock.acquire(60000);
        publish("RESTORING");
        CommandExecutor.submit(() -> {
            boolean restored = journal.restoreEnhancements();
            main.post(() -> {
                if (destroyed || token != generation) return;
                restoringForUnlock = false;
                releaseLock(); publish(restored ? "LOCKED" : "RECOVERY");
            });
        });
    }
    private void handleIdleChanged() {
        if (!sessionActive || restoring || destroyed || stopping || Utils.isScreenOn(this)) return;
        final int token = generation;
        final String mode = sessionMode;
        final String evidenceId = evidenceSessionId;
        // This operation can restore radios during a maintenance window, or reapply
        // restrictions as Android returns to deep idle. Its lock belongs only to this
        // queued operation, so a stale completion cannot release a newer transition.
        final PowerManager.WakeLock idleChangeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "enforcedoze:idle-change");
        idleChangeLock.setReferenceCounted(false);
        idleChangeLock.acquire(60000);
        CommandExecutor.submit(() -> {
            try {
                // A queued broadcast from the previous state must not launch a stale shell read.
                if (token != generation || destroyed || !sessionActive) return;
                CommandResult result = readIdleWithEvidence("IDLE_CHANGED", evidenceId, mode);
                if (token != generation || !result.success()) return;
                String state = CommandPolicy.idleState(result.output);
                if ("IDLE_MAINTENANCE".equals(state) && !maintenance) {
                    maintenance = true;
                    record("EXIT_MAINTENANCE");
                    boolean restored = journal.restoreEnhancements();
                    main.post(() -> { if (!destroyed && token == generation) publish(restored ? "MAINTENANCE" : "RECOVERY"); });
                } else if ("IDLE".equals(state) && maintenance && valid(token)) {
                    if (!journal.restoreEnhancements()) {
                        main.post(() -> { if (!destroyed && token == generation) publish("RECOVERY"); });
                        return;
                    }
                    maintenance = false;
                    record("ENTER_MAINTENANCE");
                    applyEnhancements(mode, sessionConfig, token, "shizuku".equals(mode) || "root".equals(mode) && bool(sessionConfig, "isSuAvailable", false));
                    main.post(() -> { if (!destroyed && token == generation) publish("ACTIVE"); });
                }
            } catch (RuntimeException e) {
                recordFallbackError(e.toString());
                main.post(() -> { if (!destroyed && token == generation) publish("RECOVERY"); });
            } finally {
                if (idleChangeLock.isHeld()) idleChangeLock.release();
            }
        });
    }
    private void recordFallbackError(String fallback) {
        // RecoveryJournal records the exact failed command and backend result. Keep it
        // available in Diagnostics instead of replacing it with a generic status hint.
        if (!fallback.isEmpty() && prefs.getString("lastError", "").trim().isEmpty())
            prefs.edit().putString("lastError", fallback).apply();
    }
    private CommandResult readIdleWithEvidence(String event, String id, String mode) {
        if (id == null || !DozeEvidence.enabled(this)) return CommandExecutor.run(this, mode, "dumpsys deviceidle");
        int token = generation;
        long start = SystemClock.elapsedRealtime();
        boolean interactiveBefore = power.isInteractive(), idleBefore = power.isDeviceIdleMode();
        CommandResult result = CommandExecutor.run(this, mode, "dumpsys deviceidle");
        boolean interactiveAfter = power.isInteractive(), idleAfter = power.isDeviceIdleMode();
        long end = SystemClock.elapsedRealtime();
        String raw = result.success() ? DozeObservation.systemFields(result.output)
                : result.output.substring(0, Math.min(512, result.output.length()));
        DozeEvidence.append(this, new DozeObservation(System.currentTimeMillis(), end, end - start,
                id, event, mode, evidenceDevice(), BuildConfig.VERSION_NAME, result.exitCode,
                CommandPolicy.idleState(result.output), raw, interactiveBefore, interactiveAfter,
                idleBefore, idleAfter, token == generation && !destroyed));
        return result;
    }
    private DozeObservation androidObservation(String event, String id, String mode) {
        long start = SystemClock.elapsedRealtime();
        boolean interactiveBefore = power.isInteractive(), idleBefore = power.isDeviceIdleMode();
        boolean interactiveAfter = power.isInteractive(), idleAfter = power.isDeviceIdleMode();
        long end = SystemClock.elapsedRealtime();
        return new DozeObservation(System.currentTimeMillis(), end, end - start, id, event, mode,
                evidenceDevice(), BuildConfig.VERSION_NAME, null, "NOT_SAMPLED", "", interactiveBefore,
                interactiveAfter, idleBefore, idleAfter, true);
    }
    private String evidenceDevice() { return Build.MANUFACTURER + " " + Build.MODEL + " · API " + Build.VERSION.SDK_INT; }
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
        try { if (callModeMonitor != null) callModeMonitor.close(); }
        catch (Exception e) { Utils.logToLogcat("EnforceDoze", "Unable to detach audio callback: " + e); }
        main.removeCallbacksAndMessages(null);
        final String evidenceId = evidenceSessionId;
        final String evidenceMode = sessionActive && sessionMode != null ? sessionMode : CommandExecutor.mode(this);
        evidenceSessionId = null;
        if (entering || restoring || sessionActive || journal.hasPending()) {
            // Keep the CPU available for the final undo work, including service destruction
            // during entry. The timeout bounds unexpected backend stalls.
            transitionLock.acquire(60000);
            CommandExecutor.submit(() -> {
                try {
                    boolean restored = journal.restore();
                    if (evidenceId != null) DozeEvidence.append(this,
                            androidObservation(restored ? "RESTORED" : "RESTORE_PENDING", evidenceId, evidenceMode));
                    sessionActive = false;
                } finally { releaseLock(); }
            });
        } else releaseLock();
        status = "OFF";
        stopForeground(true);
        Utils.updateTileState(this);
        super.onDestroy();
    }
}
