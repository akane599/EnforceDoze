package com.akylas.enforcedoze;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class DiagnosticsActivity extends UiActivity {
    private TextView report;
    private MaterialButton run;
    private MaterialButton copy, notifications, calls;
    private volatile int checkVersion;
    private boolean reportReady;
    private static final String[] CHECKS = {"id", "dumpsys deviceidle get deep", "dumpsys deviceidle get light",
            "cmd connectivity airplane-mode", PrivilegedOperations.PREFIX + "hotspot get", PrivilegedOperations.PREFIX + "sensors get"};
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(R.string.diagnostics_title);
        root.addView(toolbar);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding); scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView intro = new TextView(this); intro.setText(R.string.diagnostics_intro); body.addView(intro);
        run = new MaterialButton(this); run.setText(R.string.diagnostics_run); body.addView(run);
        run.setOnClickListener(v -> check());
        copy = new MaterialButton(this); copy.setText(R.string.diagnostics_copy); body.addView(copy); copy.setEnabled(false);
        copy.setOnClickListener(v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("EnforceDoze diagnostics", report.getText())));
        notifications = new MaterialButton(this); notifications.setText(R.string.notification_permission_button); body.addView(notifications);
        notifications.setOnClickListener(v -> { if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 112); });
        calls = new MaterialButton(this); calls.setText(R.string.phone_permission_button); body.addView(calls);
        calls.setOnClickListener(v -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 113));
        report = new TextView(this); report.setTextIsSelectable(true); report.setText(R.string.diagnostics_idle); body.addView(report);
        if (state != null && state.containsKey("report")) {
            report.setText(state.getString("report"));
            reportReady = true;
            copy.setEnabled(true);
        }
        setContentView(root); setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    private void check() {
        run.setEnabled(false); copy.setEnabled(false); reportReady = false; report.setText(R.string.diagnostics_running);
        int version = ++checkVersion;
        String mode = CommandExecutor.mode(this);
            StringBuilder text = new StringBuilder("EnforceDoze " + BuildConfig.VERSION_NAME + "\n" + Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nMode: " + mode + "\n");
            ShizukuHandler access = ShizukuHandler.getInstance(this);
            text.append("Shizuku binder: ").append(access.isBinderAlive()).append("\nAuthorized: ").append(access.isShizukuAvailable());
            text.append("\nPending restoration: ").append(new RecoveryJournal(this).hasPending());
            text.append("\nNotifications permission: ").append(Utils.isPostNotificationPermissionGranted(this));
            text.append("\nCall detection permission: ").append(Utils.isReadPhoneStatePermissionGranted(this));
        nextCheck(version, mode, text, 0);
    }
    private void nextCheck(int version, String mode, StringBuilder text, int index) {
        if (isFinishing() || isDestroyed() || version != checkVersion) return;
        if (index == CHECKS.length) {
            text.append("\n\nLast error:\n").append(android.preference.PreferenceManager.getDefaultSharedPreferences(this).getString("lastError", "None"));
            report.setText(text); run.setEnabled(true); copy.setEnabled(true); reportReady = true;
            return;
        }
        // Yield between reads so a queued screen-on restoration is not delayed by the whole report.
        CommandExecutor.submit(() -> {
            if (version != checkVersion) return;
            CommandResult result = CommandExecutor.run(this, mode, CHECKS[index]);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || version != checkVersion) return;
                text.append("\n\n$ ").append(CHECKS[index]).append("\nExit: ").append(result.exitCode).append("\n").append(result.output);
                report.setText(text.toString());
                nextCheck(version, mode, text, index + 1);
            });
        });
    }
    private void permissionButtons() {
        notifications.setVisibility(Utils.isPostNotificationPermissionGranted(this) ? android.view.View.GONE : android.view.View.VISIBLE);
        calls.setVisibility(Utils.isReadPhoneStatePermissionGranted(this) ? android.view.View.GONE : android.view.View.VISIBLE);
    }
    @Override protected void onResume() { super.onResume(); permissionButtons(); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); permissionButtons();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (reportReady) state.putString("report", report.getText().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { ++checkVersion; super.onDestroy(); }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
