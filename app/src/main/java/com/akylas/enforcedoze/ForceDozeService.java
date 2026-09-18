package com.akylas.enforcedoze;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** All device work is serialized. Broadcasts invalidate stale work immediately, without polling. */
public class ForceDozeService extends Service {
    public static volatile boolean restrictNotifications;
    private static final String MONITOR = "monitoring_v2", ERRORS = "recovery_v2";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private SharedPreferences prefs;
    private DeviceController device;
    private EvidenceStore evidence;
    private SessionStore sessions;
    private PowerManager power;
    private boolean active, maintenance, heldUntilUnlock, paused;
    private volatile boolean stopped;
    private volatile long screenOffAt;
    private String currentStatus = "", currentError = "", optionalError = "";
    private AudioManager.OnModeChangedListener audioListener;
    private final ShizukuHandler.OnAvailibilityChange accessListener =
            available -> signal("Access changed", true);
    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action))
                        screenOffAt = SystemClock.elapsedRealtime();
                    if (Intent.ACTION_POWER_CONNECTED.equals(action))
                        AccessExecutor.SERIAL.execute(() -> sessions.charging());
                    boolean reset = !PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action);
                    signal(action == null ? "Device event" : action, reset);
                }
            };
    private final BroadcastReceiver reload =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    boolean media = "media-changed".equals(action);
                    if (media && !option("whitelistMusicAppNetwork", false)) return;
                    String reason =
                            media
                                    ? "Media changed"
                                    : "schedule-boundary".equals(action)
                                            ? "Schedule boundary"
                                            : "reenter-doze".equals(action)
                                                    ? "Screen-off delay"
                                                    : "Settings changed";
                    signal(reason, true);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        device = new DeviceController(this);
        evidence = new EvidenceStore(this);
        sessions = new SessionStore(this);
        createChannels();
        Notification notification = monitoringNotification("Monitoring screen and power events");
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1234, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1234, notification);
        IntentFilter filter = new IntentFilter();
        for (String action :
                new String[] {
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_USER_PRESENT,
                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED,
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
                }) filter.addAction(action);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        IntentFilter local = new IntentFilter("reload-settings");
        local.addAction("reload-app-blocklist");
        local.addAction("media-changed");
        local.addAction("reload-notification-blocklist");
        local.addAction("schedule-boundary");
        local.addAction("reenter-doze");
        LocalBroadcastManager.getInstance(this).registerReceiver(reload, local);
        ShizukuHandler.getInstance(this).addListener(accessListener);
        if (Build.VERSION.SDK_INT >= 31) {
            audioListener = mode -> signal("Call audio changed", true);
            ((AudioManager) getSystemService(AUDIO_SERVICE))
                    .addOnModeChangedListener(getMainExecutor(), audioListener);
        }
        screenOffAt = SystemClock.elapsedRealtime();
        AccessExecutor.SERIAL.execute(
                () -> {
                    sessions.finish("Process restarted; previous end was not observed", true);
                    recover("Startup recovery");
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        signal(
                intent != null && "retry".equals(intent.getAction())
                        ? "Retry requested"
                        : "Monitoring requested",
                true);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void signal(String reason, boolean invalidate) {
        int epoch = invalidate ? generation.incrementAndGet() : generation.get();
        AccessExecutor.SERIAL.execute(
                () -> {
                    if (stopped) return;
                    PowerManager.WakeLock lock =
                            power.newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK, "enforcedoze:transaction");
                    lock.acquire(120000);
                    try {
                        if (invalidate) paused = false;
                        reconcile(reason, epoch);
                    } catch (Exception e) {
                        paused = true;
                        evidence.record("Operation failed", e.toString());
                        recover("Operation failed");
                        status("Attention needed", e.getMessage());
                    } finally {
                        if (lock.isHeld()) lock.release();
                    }
                });
    }

    private boolean option(String key, boolean fallback) {
        return prefs.getBoolean(key, fallback);
    }

    private boolean enabled() {
        return option("serviceEnabled", false);
    }

    private boolean inCall() {
        return Utils.isUserInCall(this) || Utils.isUserInCommunicationCall(this);
    }

    private boolean eligible() {
        return ScheduleRules.mayEnter(
                enabled(),
                power.isInteractive(),
                Utils.isInsideCustomDozePeriod(this),
                Utils.isConnectedToCharger(this),
                option("disableWhenCharging", true),
                inCall());
    }

    private boolean valid(int epoch) {
        return !stopped && generation.get() == epoch && eligible();
    }

    private void reconcile(String reason, int epoch) {
        if (generation.get() != epoch) return;
        if (reason.equals("Settings changed")
                || reason.equals("Monitoring requested")
                || reason.equals(Intent.ACTION_TIME_CHANGED)
                || reason.equals(Intent.ACTION_TIMEZONE_CHANGED))
            Utils.scheduleNextCustomDozePeriodBoundary(this);
        if (reason.equals("Settings changed")
                || reason.equals("Access changed")
                || reason.equals("Retry requested")
                || reason.equals("Media changed")) {
            recover(reason);
        }
        if (!eligible()) {
            cancelDelay();
            boolean deferNetwork =
                    enabled()
                            && option("waitForUnlock", false)
                            && power.isInteractive()
                            && Utils.isDeviceLocked(this)
                            && Utils.isInsideCustomDozePeriod(this)
                            && !inCall()
                            && !(option("disableWhenCharging", true)
                                    && Utils.isConnectedToCharger(this));
            if (deferNetwork && (active || heldUntilUnlock)) {
                active = false;
                heldUntilUnlock = true;
                restrictNotifications = false;
                sessions.finish("Screen on; connectivity held until unlock", false);
                if (!device.restoreScreenControls())
                    status("Restoration pending", new RecoveryStore(this).summary());
                else
                    status(
                            "Waiting for unlock",
                            "Connectivity options remain applied until you unlock.");
                return;
            }
            recover(reason);
            if (!device.pending()) {
                String state =
                        !enabled()
                                ? "Monitoring off"
                                : power.isInteractive()
                                        ? "Ready for screen off"
                                        : inCall()
                                                ? "Paused for a call"
                                                : Utils.isConnectedToCharger(this)
                                                                && option(
                                                                        "disableWhenCharging", true)
                                                        ? "Paused while charging"
                                                        : "Outside schedule";
                status(state, "");
                if (!enabled()) main.post(this::stopSelf);
            }
            return;
        }
        if (heldUntilUnlock) recover("New screen-off interval");
        if (device.pending() && !active) {
            if (!recover("Pending recovery")) return;
        }
        if (paused) return;
        if (active) {
            observeIdle(epoch);
            return;
        }
        long delay = Math.max(0, Math.min(1800, prefs.getInt("dozeEnterDelay", 0))) * 1000L;
        if (!option("ignoreLockscreenTimeout", true))
            delay +=
                    Math.max(
                            0,
                            Settings.Secure.getInt(
                                    getContentResolver(), "lock_screen_lock_after_timeout", 5000));
        long remaining = screenOffAt + delay - SystemClock.elapsedRealtime();
        if (remaining > 0) {
            scheduleDelay(remaining);
            status("Waiting for screen-off delay", "");
            return;
        }
        if (!Utils.areRecoveryNotificationsAvailable(this)) {
            status(
                    "Recovery notifications need permission",
                    "Allow Notifications in access setup before automatic device changes can"
                            + " start.");
            return;
        }
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && !Utils.isReadPhoneStatePermissionGranted(this)) {
            status(
                    "Call protection needs permission",
                    "Allow Phone permission in EnforceDoze before starting automatic Doze.");
            return;
        }
        if (!power.isIgnoringBatteryOptimizations(getPackageName())) {
            CommandResult exemption =
                    device.command("dumpsys deviceidle whitelist +" + getPackageName());
            if (!exemption.ok() || !power.isIgnoringBatteryOptimizations(getPackageName())) {
                status(
                        "Battery exemption needed",
                        "Allow unrestricted battery use for EnforceDoze, then retry access.");
                return;
            }
        }
        if (!valid(epoch)) return;
        sessions.begin();
        boolean entered;
        if (new AccessExecutor(this).mode().equals("nonroot")
                && Build.VERSION.SDK_INT >= 24
                && Build.VERSION.SDK_INT < 34) {
            entered =
                    device.setting(
                            "Legacy Doze tunables",
                            "global",
                            "device_idle_constants",
                            "inactive_to=0,idle_after_inactive_to=0,sensing_to=0,locating_to=0");
            evidence.record(
                    "Legacy mode",
                    "Requested shorter idle delays. Deep Doze requires a separate observation.");
        } else entered = device.forceIdle();
        if (!entered || !valid(epoch)) {
            paused = true;
            recover("Doze request failed or screen state changed");
            status(
                    "Doze could not start",
                    "Open diagnostics. Check privileged access and retry; no successful Doze"
                            + " session is assumed.");
            return;
        }
        active = true;
        observeIdle(epoch);
        if (valid(epoch)) applyOptions(epoch);
        if (!valid(epoch)) recover("Device state changed during entry");
        else if (active) status("Monitoring screen-off interval", optionalError);
    }

    private void observeIdle(int epoch) {
        boolean screenOffBefore = !power.isInteractive();
        String deep = device.observe("dumpsys deviceidle", "deep");
        boolean off = screenOffBefore && !power.isInteractive();
        if ("IDLE".equals(deep) && off) {
            sessions.observe(true);
            evidence.record(
                    "Deep Doze observed",
                    "DeviceIdleController mState=IDLE; screen off before and after query. This is a"
                            + " point-in-time observation.");
            if (maintenance && valid(epoch)) {
                maintenance = false;
                applyOptions(epoch);
            }
        } else if ("IDLE_MAINTENANCE".equals(deep)) {
            evidence.record(
                    "Maintenance observed",
                    "Restoring optional restrictions while Android handles background work.");
            if (!maintenance) {
                maintenance = true;
                restrictNotifications = false;
                if (!device.maintenance())
                    status("Restoration pending", new RecoveryStore(this).summary());
            }
        } else {
            evidence.record(
                    "Doze observation",
                    "mState="
                            + deep
                            + "; screen off="
                            + off
                            + "; no Deep Doze proof from this query.");
        }
    }

    private void applyOptions(int epoch) {
        optionalError = "";
        // The first idle observation can already be a maintenance window.
        if (maintenance || !valid(epoch)) return;
        Set<String> playing = Collections.emptySet();
        boolean protectMedia = option("whitelistMusicAppNetwork", false);
        NotificationService listener = NotificationService.getInstance();
        if (protectMedia) playing = listener == null ? null : listener.playingPackages();
        boolean keepNetwork = protectMedia && (playing == null || !playing.isEmpty());
        if (protectMedia && playing == null)
            evidence.record(
                    "Media protection",
                    "Playback state unavailable; preserving connectivity and app access.");
        if (option("disableMotionSensors", false) && valid(epoch))
            feature(
                    device.restrictSensors(prefs.getString("sensorWhitelistPackage", "")),
                    "Sensor access");
        if (option("turnOffAllSensorsInDoze", false) && valid(epoch))
            feature(
                    device.change(
                            "Sensor privacy",
                            "@sensor-privacy",
                            "boolean",
                            "true",
                            "@sensor-privacy true",
                            o -> "@sensor-privacy " + o),
                    "Sensor privacy");
        if (option("turnOffBiometricsInDoze", false) && valid(epoch)) {
            String value =
                    device.observe(
                            "settings --user current get secure biometric_keyguard_enabled",
                            "switch");
            if (value == null) feature(false, "Biometric keyguard unsupported on this device");
            else
                feature(
                        device.setting(
                                "Biometric keyguard", "secure", "biometric_keyguard_enabled", "0"),
                        "Biometric keyguard setting (hardware effect unverified)");
        }
        if (option("turnOnBatterySaverInDoze", false) && valid(epoch))
            feature(
                    device.change(
                            "Battery saver",
                            "settings get global low_power",
                            "switch",
                            "1",
                            "cmd power set-mode 1",
                            o -> "cmd power set-mode " + o),
                    "Battery saver");
        boolean protectHotspot = option("ignoreIfHotspot", true);
        boolean networkSelected =
                option("turnOnAirplaneInDoze", false)
                        || option("turnOffWiFiInDoze", false)
                        || option("turnOffDataInDoze", false);
        boolean hotspotOrUnknown =
                !keepNetwork && networkSelected && protectHotspot && hotspotActiveOrUnknown();
        if (hotspotOrUnknown)
            evidence.record(
                    "Connectivity preserved",
                    "Hotspot is active or its state is unavailable; selected connectivity controls"
                            + " were skipped.");
        if (!keepNetwork && !hotspotOrUnknown && valid(epoch)) {
            if (option("turnOnAirplaneInDoze", false))
                feature(
                        device.change(
                                "Airplane mode",
                                "settings get global airplane_mode_on",
                                "switch",
                                "1",
                                "cmd connectivity airplane-mode enable",
                                o ->
                                        "cmd connectivity airplane-mode "
                                                + (o.equals("1") ? "enable" : "disable")),
                        "Airplane mode");
            if (option("turnOffWiFiInDoze", false) && valid(epoch))
                feature(
                        device.change(
                                "Wi-Fi",
                                "cmd wifi status",
                                "wifi",
                                "0",
                                "svc wifi disable",
                                o -> "svc wifi " + (o.equals("1") ? "enable" : "disable")),
                        "Wi-Fi");
            if (option("turnOffDataInDoze", false) && valid(epoch)) {
                int subId =
                        Build.VERSION.SDK_INT >= 24
                                ? android.telephony.SubscriptionManager
                                        .getDefaultDataSubscriptionId()
                                : -1;
                if (subId < 0)
                    evidence.record("Mobile data skipped", "No active default data subscription");
                else
                    feature(
                            device.change(
                                    "Mobile data SIM " + subId,
                                    "@data " + subId,
                                    "boolean",
                                    "false",
                                    "@data " + subId + " false",
                                    o -> "@data " + subId + " " + o),
                            "Mobile data (default SIM only)");
            }
        }
        if (!keepNetwork && valid(epoch)) {
            if (option("turnOffBluetoothInDoze", false))
                feature(
                        device.change(
                                "Bluetooth",
                                "settings get global bluetooth_on",
                                "switch",
                                "0",
                                "svc bluetooth disable",
                                o -> "svc bluetooth " + (o.equals("1") ? "enable" : "disable")),
                        "Bluetooth");
            if (option("turnOffGPSInDoze", false) && valid(epoch))
                feature(
                        device.change(
                                "Location",
                                "cmd location is-location-enabled --user " + user(),
                                "boolean",
                                "false",
                                "cmd location set-location-enabled false --user " + user(),
                                o ->
                                        "cmd location set-location-enabled "
                                                + o
                                                + " --user "
                                                + user()),
                        "Location");
        }
        Set<String> blocked =
                new HashSet<>(prefs.getStringSet("dozeAppBlockList", Collections.emptySet()));
        Set<String> focused =
                !blocked.isEmpty() && option("whitelistCurrentApp", false)
                        ? focusedPackages()
                        : Collections.emptySet();
        for (String pkg : blocked) {
            if (!valid(epoch)) break;
            if (protectMedia && (playing == null || playing.contains(pkg))) continue;
            if (focused == null || focused.contains(pkg) || !safeToSuspend(pkg)) continue;
            String query = "dumpsys package " + CommandResult.quote(pkg);
            String original = device.observe(query, "suspended:" + user());
            if (!"false".equals(original)) {
                evidence.record(
                        "App block skipped", pkg + ": already suspended or state unavailable");
                continue;
            }
            feature(
                    device.change(
                            "App " + pkg,
                            query,
                            "suspended:" + user(),
                            "true",
                            "pm suspend --user " + user() + " " + CommandResult.quote(pkg),
                            o -> "pm unsuspend --user " + user() + " " + CommandResult.quote(pkg)),
                    "App " + pkg);
        }
        restrictNotifications = valid(epoch) && !maintenance;
    }

    private int user() {
        return android.os.Process.myUid() / 100000;
    }

    private boolean safeToSuspend(String pkg) {
        if (!CommandResult.validPackage(pkg)
                || pkg.equals(getPackageName())
                || pkg.equals("moe.shizuku.privileged.api")) return false;
        try {
            return (getPackageManager().getApplicationInfo(pkg, 0).flags
                            & ApplicationInfo.FLAG_SYSTEM)
                    == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Set<String> focusedPackages() {
        CommandResult result = device.command("dumpsys activity activities");
        if (!result.ok()) return null;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(
                                "(?:mResumedActivity|topResumedActivity|mFocusedApp)[^\\n"
                                        + "]*? ([A-Za-z][A-Za-z0-9_.]+)/")
                        .matcher(result.output);
        Set<String> found = new HashSet<>();
        while (m.find()) found.add(m.group(1));
        if (found.isEmpty()) {
            evidence.record(
                    "Foreground protection",
                    "Foreground app could not be identified; skipping app suspension.");
            return null;
        }
        return found;
    }

    private boolean hotspotActiveOrUnknown() {
        CommandResult result = device.command("@hotspot");
        // Android WIFI_AP_STATE_DISABLED=11. Unknown OEM/read errors preserve connectivity.
        return !result.ok() || !result.output.trim().equals("11");
    }

    private void feature(boolean applied, String label) {
        if (!applied) {
            optionalError = "Some optional controls could not be verified. Open diagnostics.";
            evidence.record("Optional control unverified", label);
        }
    }

    private boolean recover(String reason) {
        active = false;
        maintenance = false;
        heldUntilUnlock = false;
        restrictNotifications = false;
        sessions.finish(reason, false);
        boolean ok;
        try {
            ok = device.restore();
        } catch (IllegalStateException e) {
            status("Recovery record needs attention", e.getMessage());
            return false;
        }
        if (!ok || device.pending()) {
            status("Restoration pending", new RecoveryStore(this).summary());
            return false;
        }
        return true;
    }

    private void status(String state, String error) {
        if (error == null) error = "Unknown error; open diagnostics.";
        if (state.equals(currentStatus) && error.equals(currentError)) return;
        currentStatus = state;
        currentError = error;
        getSharedPreferences("runtime", MODE_PRIVATE)
                .edit()
                .putString("status", state)
                .putString("error", error)
                .apply();
        final String text = state, problem = error;
        main.post(
                () -> {
                    NotificationManager nm =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (option("showPersistentNotif", false))
                        nm.notify(1234, monitoringNotification(text));
                    if (!problem.isEmpty())
                        nm.notify(
                                8765,
                                new NotificationCompat.Builder(this, ERRORS)
                                        .setSmallIcon(R.drawable.ic_battery_health)
                                        .setContentTitle(text)
                                        .setContentText(problem)
                                        .setStyle(
                                                new NotificationCompat.BigTextStyle()
                                                        .bigText(problem))
                                        .setContentIntent(openApp())
                                        .setOnlyAlertOnce(true)
                                        .setOngoing(true)
                                        .build());
                    else nm.cancel(8765);
                });
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification monitoringNotification(String text) {
        return new NotificationCompat.Builder(this, MONITOR)
                .setSmallIcon(R.drawable.ic_battery_health)
                .setContentTitle("EnforceDoze monitoring")
                .setContentText(text)
                .setContentIntent(openApp())
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel monitor =
                new NotificationChannel(MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW);
        monitor.setSound(null, null);
        monitor.enableVibration(false);
        monitor.setShowBadge(false);
        nm.createNotificationChannel(monitor);
        nm.createNotificationChannel(
                new NotificationChannel(
                        ERRORS, "Access and restoration", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private PendingIntent delayIntent() {
        return PendingIntent.getBroadcast(
                this,
                9013,
                new Intent(this, CustomDozePeriodReceiver.class).setAction("screen-delay"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void scheduleDelay(long millis) {
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        long at = SystemClock.elapsedRealtime() + millis;
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
            alarm.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, at, delayIntent());
        else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, delayIntent());
    }

    private void cancelDelay() {
        ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(delayIntent());
    }

    @Override
    public void onDestroy() {
        stopped = true;
        generation.incrementAndGet();
        cancelDelay();
        restrictNotifications = false;
        unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reload);
        ShizukuHandler.getInstance(this).removeListener(accessListener);
        if (Build.VERSION.SDK_INT >= 31 && audioListener != null)
            ((AudioManager) getSystemService(AUDIO_SERVICE))
                    .removeOnModeChangedListener(audioListener);
        AccessExecutor.SERIAL.execute(
                () -> {
                    recover("Service stopped");
                    getSharedPreferences("runtime", MODE_PRIVATE)
                            .edit()
                            .putString(
                                    "status",
                                    device.pending() ? "Restoration pending" : "Monitoring off")
                            .apply();
                });
        if (!enabled()) Utils.showDisabledNotification(this);
        super.onDestroy();
    }
}
