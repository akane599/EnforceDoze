package com.akylas.enforcedoze;

import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.widget.*;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends BaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs, runtime, evidencePrefs, recoveryPrefs;
    private TextView state, detail, access, changes, last;
    private MaterialButton toggle;
    private final ShizukuHandler.OnAvailibilityChange accessListener = available -> render();

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("EnforceDoze", false);
        toolbar.getMenu()
                .add("Settings")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        toolbar.setOnMenuItemClickListener(
                item -> {
                    open(SettingsActivity.class);
                    return true;
                });
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        runtime = getSharedPreferences("runtime", MODE_PRIVATE);
        evidencePrefs = getSharedPreferences("evidence", MODE_PRIVATE);
        recoveryPrefs = getSharedPreferences("recovery", MODE_PRIVATE);
        SharedPreferences migration = getSharedPreferences("migration", MODE_PRIVATE);
        if (migration.getBoolean("reviewUpgrade", false)) {
            LinearLayout upgrade =
                    card(
                            "Review after upgrading",
                            "Monitoring is paused. Earlier versions did not save every original"
                                + " device setting, so this build cannot reconstruct old changes."
                                + " Check connectivity, sensor privacy and app suspension before"
                                + " restarting. Your options and older history are retained.");
            button(
                    upgrade,
                    "I have reviewed device settings",
                    () -> {
                        migration.edit().putBoolean("reviewUpgrade", false).apply();
                        recreate();
                    });
        }
        LinearLayout monitor = card(null, null);
        text(monitor, "SCREEN-OFF MONITORING", 12, true);
        state = text(monitor, "Monitoring off", 28, true);
        state.setId(R.id.dashboard_status);
        detail =
                text(
                        monitor,
                        "Android manages your device normally while monitoring is off.",
                        16,
                        false);
        toggle =
                button(
                        monitor,
                        "Start monitoring",
                        () -> {
                            boolean enabled = !prefs.getBoolean("serviceEnabled", false);
                            if (enabled && !Utils.areRecoveryNotificationsAvailable(this)) {
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                                this)
                                        .setTitle("Allow recovery notifications")
                                        .setMessage(
                                                "Notifications are needed before monitoring can"
                                                    + " make device changes, so access errors and"
                                                    + " pending restoration stay visible.")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton(
                                                "Set up access",
                                                (d, w) -> open(AccessActivity.class))
                                        .show();
                                return;
                            }
                            prefs.edit().putBoolean("serviceEnabled", enabled).apply();
                            if (enabled) Utils.startForceDozeService(this);
                            else Utils.stopForceDozeService(this);
                            render();
                        });
        toggle.setId(R.id.monitor_toggle);
        LinearLayout accessCard = card("Access & readiness", null);
        access = text(accessCard, "Checking saved access state…", 16, false);
        button(accessCard, "Set up access", () -> open(AccessActivity.class));
        LinearLayout recovery = card("Temporary device changes", null);
        changes = text(recovery, "Nothing to restore.", 16, false);
        changes.setId(R.id.recovery_status);
        button(
                recovery,
                "Retry access & restoration",
                () -> {
                    Intent retry = new Intent(this, ForceDozeService.class).setAction("retry");
                    try {
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(retry);
                        else startService(retry);
                    } catch (RuntimeException e) {
                        message("Could not start recovery", e.getMessage());
                    }
                });
        LinearLayout observations = card("Evidence, saved locally", null);
        last = text(observations, "No observations yet.", 16, false);
        button(observations, "Diagnostics & saved evidence", () -> open(LogActivity.class));
        button(observations, "Monitoring history", () -> open(DozeBatteryStatsActivity.class));
        LinearLayout controls =
                card(
                        "Make it yours",
                        "Connectivity and sensor options are independent controls. Review their"
                                + " effects before enabling them.");
        button(controls, "Settings", () -> open(SettingsActivity.class));
        button(controls, "App exemptions", () -> open(WhitelistAppsActivity.class));
        button(controls, "Android & Samsung guidance", () -> open(CompatibilityActivity.class));
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (SharedPreferences p :
                new SharedPreferences[] {prefs, runtime, evidencePrefs, recoveryPrefs})
            p.registerOnSharedPreferenceChangeListener(this);
        ShizukuHandler.getInstance(this).addListener(accessListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShizukuHandler.getInstance(this).checkShizukuAvailability();
        render();
        if ((prefs.getBoolean("serviceEnabled", false) || new RecoveryStore(this).pending())
                && !Utils.isMyServiceRunning(ForceDozeService.class, this))
            Utils.startForceDozeService(this);
    }

    @Override
    protected void onStop() {
        for (SharedPreferences p :
                new SharedPreferences[] {prefs, runtime, evidencePrefs, recoveryPrefs})
            p.unregisterOnSharedPreferenceChangeListener(this);
        ShizukuHandler.getInstance(this).removeListener(accessListener);
        super.onStop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        runOnUiThread(this::render);
    }

    private void render() {
        if (state == null) return;
        boolean enabled = prefs.getBoolean("serviceEnabled", false),
                pending = new RecoveryStore(this).pending();
        boolean running = Utils.isMyServiceRunning(ForceDozeService.class, this);
        state.setText(
                !running && !pending
                        ? enabled ? "Monitoring is not running" : "Monitoring off"
                        : runtime.getString("status", "Starting monitoring…"));
        String error = runtime.getString("error", "");
        detail.setText(
                !error.isEmpty()
                        ? error
                        : enabled
                                ? "Responds to screen, power and call events. Check saved evidence"
                                        + " to confirm Deep Doze."
                                : "Temporary changes are restored before monitoring stops.");
        toggle.setText(enabled ? "Stop & restore" : "Start monitoring");
        String mode = prefs.getString("executionMode", "shizuku");
        access.setText(
                (mode.equals("shizuku")
                                ? ShizukuHandler.getInstance(this).status()
                                : mode.equals("root")
                                        ? "Root mode • use Set up access to verify"
                                        : "ADB-granted app mode • limited on Android 14+")
                        + "\nNotifications: "
                        + (Utils.areRecoveryNotificationsAvailable(this)
                                ? "allowed"
                                : "permission needed")
                        + "\nCall protection: "
                        + (Utils.isReadPhoneStatePermissionGranted(this)
                                ? "allowed"
                                : "Phone permission needed"));
        changes.setText(
                pending
                        ? new RecoveryStore(this).summary()
                                + "\n\n"
                                + (enabled && running
                                        ? "These changes have saved original values. Access is"
                                                + " needed to restore them."
                                        : "Restoration is pending. Reconnect the original access"
                                                + " mode and retry.")
                        : "Nothing to restore.");
        last.setText(
                evidencePrefs.getString(
                        "latest",
                        "No observations yet. Turn the screen off with monitoring enabled, then"
                                + " review the results here."));
    }
}
