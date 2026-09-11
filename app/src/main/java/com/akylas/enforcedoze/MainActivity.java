package com.akylas.enforcedoze;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** A dashboard with explicit setup and live service state instead of an optimistic toggle. */
public class MainActivity extends UiActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;
    private ShizukuHandler shizuku;
    private RecoveryJournal journal;
    private TextView state, access, details, battery, issue;
    private MaterialButton toggle, setup, allowBattery;
    private boolean checkingRoot;
    private boolean resumed;
    // Preference writes and the state broadcast both land per status change; render once per frame.
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable renderOnce = this::render;
    private final ShizukuHandler.OnAvailibilityChange accessListener = value -> {
        scheduleRender();
        if (!value) return;
        // Shizuku can hand the controller its own permissions, sparing the user extra prompts.
        Utils.grantPermissionsViaShizuku(this);
        if (prefs.getBoolean("serviceEnabled", false)) Utils.applyForceDozeSchedule(this);
    };
    private final BroadcastReceiver update = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { scheduleRender(); }
    };
    private void scheduleRender() {
        ui.removeCallbacks(renderOnce);
        ui.post(renderOnce);
    }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        shizuku = ShizukuHandler.getInstance(this);
        journal = new RecoveryJournal(this);
        state = findViewById(R.id.dashboardState);
        access = findViewById(R.id.dashboardAccess);
        details = findViewById(R.id.dashboardDetails);
        battery = findViewById(R.id.dashboardBattery);
        issue = findViewById(R.id.dashboardIssue);
        toggle = findViewById(R.id.dashboardToggle);
        setup = findViewById(R.id.dashboardSetup);
        allowBattery = findViewById(R.id.dashboardBatteryAllow);
        issue.setOnClickListener(v -> open(DiagnosticsActivity.class));
        allowBattery.setOnClickListener(v -> requestBatteryExemption());
        toggle.setOnClickListener(v -> {
            if (prefs.getBoolean("serviceEnabled", false)) {
                prefs.edit().putBoolean("serviceEnabled", false).apply();
                Utils.stopForceDozeService(this);
            } else if (ready()) {
                java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
                if (Build.VERSION.SDK_INT >= 33 && !Utils.isPostNotificationPermissionGranted(this)) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                if (!Utils.isReadPhoneStatePermissionGranted(this)) permissions.add(Manifest.permission.READ_PHONE_STATE);
                // Ask first so the ongoing notification is visible from the very first session.
                if (!permissions.isEmpty()) { ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 112); return; }
                startMonitor();
            } else connect();
            render();
        });
        setup.setOnClickListener(v -> connect());
        findViewById(R.id.dashboardMode).setOnClickListener(v -> chooseMode());
        findViewById(R.id.dashboardStats).setOnClickListener(v -> open(DozeBatteryStatsActivity.class));
        findViewById(R.id.dashboardApps).setOnClickListener(v -> open(WhitelistAppsActivity.class));
        findViewById(R.id.dashboardSettings).setOnClickListener(v -> open(SettingsActivity.class));
        findViewById(R.id.dashboardDiagnostics).setOnClickListener(v -> open(DiagnosticsActivity.class));
        findViewById(R.id.dashboardBatterySettings).setOnClickListener(v -> UiSupport.open(this,
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.dashboardRetry).setOnClickListener(v -> {
            if (!ready()) { connect(); return; }
            CommandExecutor.submit(() -> {
                boolean restored = journal.restore();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (restored && prefs.getBoolean("serviceEnabled", false)) Utils.applyForceDozeSchedule(this);
                    render();
                    if (!restored) message(getString(R.string.status_recovery));
                });
            });
        });
    }
    private void open(Class<?> activity) { startActivity(new Intent(this, activity)); }
    private void startMonitor() {
        prefs.edit().putBoolean("serviceEnabled", true).apply();
        Utils.applyForceDozeSchedule(this);
        render();
    }
    /**
     * One UI and stock Android both stop delivering screen and charging events to a restricted app,
     * so offer the exemption dialog directly instead of sending people hunting through Settings.
     */
    private void requestBatteryExemption() {
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        if (getPackageManager().resolveActivity(request, 0) == null) {
            message(getString(R.string.battery_request_unavailable));
            return;
        }
        UiSupport.open(this, request);
    }
    private boolean ready() {
        if ("shizuku".equals(CommandExecutor.mode(this))) return shizuku.isShizukuAvailable();
        if ("root".equals(CommandExecutor.mode(this))) return prefs.getBoolean("isSuAvailable", false);
        return "adb".equals(CommandExecutor.mode(this)) && Build.VERSION.SDK_INT < 34
                && Utils.isDumpPermissionGranted(this) && Utils.isSecureSettingsPermissionGranted(this);
    }
    private void render() {
        if (state == null || isFinishing() || isDestroyed()) return;
        ui.removeCallbacks(renderOnce);
        boolean enabled = prefs.getBoolean("serviceEnabled", false);
        boolean running = Utils.isMyServiceRunning(ForceDozeService.class, this);
        boolean ready = ready();
        boolean recovery = journal.hasPending() && !running;
        String status = !enabled ? (recovery ? "RECOVERY" : "OFF") : !ready ? "NEEDS_ACCESS"
                : running ? ForceDozeService.status : "NEEDS_START";
        state.setText(UiSupport.statusText(this, status));
        toggle.setText(enabled ? R.string.dashboard_stop : R.string.dashboard_start);
        toggle.setEnabled(!checkingRoot);
        // Show what actually failed rather than a generic line; tapping it opens Diagnostics.
        String error = prefs.getString("lastError", "");
        issue.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
        if (!error.isEmpty()) issue.setText(getString(R.string.dashboard_issue_detail, error.trim()));
        String mode = CommandExecutor.mode(this);
        int modeName = "shizuku".equals(mode) ? R.string.execution_mode_shizuku
                : "root".equals(mode) ? R.string.execution_mode_root : R.string.execution_mode_adb;
        access.setText(getString(R.string.dashboard_access_value, getString(modeName),
                getString(ready ? R.string.access_ready : R.string.access_needed)));
        setup.setVisibility(ready ? View.GONE : View.VISIBLE);
        details.setText(getString(R.string.dashboard_summary, prefs.getInt("dozeEnterDelay", 0),
                Utils.hasCustomDozePeriods(this) ? getString(R.string.schedule_custom) : getString(R.string.schedule_always)));
        boolean exempt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        battery.setText(exempt ? R.string.battery_ready : R.string.battery_setup);
        allowBattery.setVisibility(exempt ? View.GONE : View.VISIBLE);
        findViewById(R.id.dashboardRetry).setVisibility(enabled && !running || recovery || "ERROR".equals(status) || "RECOVERY".equals(status) ? View.VISIBLE : View.GONE);
        TextView guide = findViewById(R.id.dashboardDeviceGuide);
        guide.setText("samsung".equalsIgnoreCase(Build.MANUFACTURER) ? R.string.samsung_guide : R.string.device_guide);
    }
    private void chooseMode() {
        String[] modes = {"shizuku", "root", "adb"};
        String current = CommandExecutor.mode(this);
        int index = java.util.Arrays.asList(modes).indexOf(current);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.execution_mode_setting_title)
                .setSingleChoiceItems(R.array.dashboard_modes, Math.max(0, index), (dialog, which) -> {
                    prefs.edit().putString("executionMode", modes[which]).apply();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("reload-settings"));
                    dialog.dismiss(); render();
                }).setNegativeButton(R.string.close_button_text, null).show();
    }
    private void connect() {
        if ("shizuku".equals(CommandExecutor.mode(this))) {
            if (shizuku.isBinderAlive()) {
                if (shizuku.isShizukuAvailable()) { render(); return; }
                shizuku.requestShizukuPermission();
                try { if (rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale()) openShizuku(); }
                catch (RuntimeException e) { render(); }
            } else openShizuku();
        } else if ("root".equals(CommandExecutor.mode(this))) {
            checkingRoot = true; render();
            CommandExecutor.submit(() -> {
                CommandResult result = ProcessRunner.run(15000, "su", "-c", "id -u");
                boolean granted = result.success() && result.output.trim().equals("0");
                prefs.edit().putBoolean("isSuAvailable", granted).apply();
                runOnUiThread(() -> {
                    checkingRoot = false;
                    if (isFinishing() || isDestroyed()) return;
                    render();
                    if (!granted) message(getString(R.string.root_workaround_text));
                });
            });
        } else {
            if (Build.VERSION.SDK_INT >= 34) { message(getString(R.string.legacy_adb_unsupported)); return; }
            String commands = "adb shell pm grant " + getPackageName() + " android.permission.DUMP\nadb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
            new MaterialAlertDialogBuilder(this).setTitle(R.string.adb_setup_title).setMessage(commands)
                    .setPositiveButton(R.string.copy_command, (dialog, which) -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ADB setup", commands)))
                    .setNegativeButton(R.string.close_button_text, null).show();
        }
    }
    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) UiSupport.open(this, launch);
        else Utils.openUrl(this, "https://shizuku.rikka.app/download/");
    }
    private void message(String message) { new MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(R.string.okay_button_text, null).show(); }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        prefs.registerOnSharedPreferenceChangeListener(this);
        shizuku.addAvailabilityListener(accessListener);
        LocalBroadcastManager.getInstance(this).registerReceiver(update, new IntentFilter(ForceDozeService.ACTION_STATE));
        shizuku.checkShizukuAvailability();
        if (prefs.getBoolean("serviceEnabled", false) && ready()) Utils.applyForceDozeSchedule(this);
        render();
    }
    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(renderOnce);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        shizuku.removeAvailabilityListener(accessListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(update);
        super.onPause();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) { if (resumed) scheduleRender(); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        // Enabling waits on the permission prompt so the first session comes up fully configured.
        if (code == 112 && ready() && !prefs.getBoolean("serviceEnabled", false)) startMonitor();
        else render();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.main, menu); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_app_settings) open(SettingsActivity.class);
        else if (id == R.id.action_doze_batterystats) open(DozeBatteryStatsActivity.class);
        else if (id == R.id.action_show_doze_tunables) open(DozeTunablesActivity.class);
        else if (id == R.id.action_donate_dev) Utils.openUrl(this, "https://github.com/sponsors/farfromrefug");
        else if (id == R.id.action_toggle_doze) CommandExecutor.execute(this, "dumpsys deviceidle enable all", result -> message(result.success() ? getString(R.string.access_ready) : result.output));
        else if (id == R.id.action_doze_more_info) open(AboutAppActivity.class);
        else return super.onOptionsItemSelected(item);
        return true;
    }
}
