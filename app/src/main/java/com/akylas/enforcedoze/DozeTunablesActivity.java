package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class DozeTunablesActivity extends UiActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_tunables);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (state == null) getSupportFragmentManager().beginTransaction().replace(R.id.content, new DozeTunablesFragment()).commit();
    }
    private List<String> commands() {
        DozeTunableHandler values = DozeTunableHandler.getInstance();
        if (Build.VERSION.SDK_INT >= 34) return values.getCommandsList();
        return java.util.Collections.singletonList("settings put global device_idle_constants " + CommandPolicy.quote(values.getTunableString()));
    }
    private void applyTunables() {
        if (Utils.isMyServiceRunning(ForceDozeService.class, this) || new RecoveryJournal(this).hasPending()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.tunables_disable_first)
                    .setPositiveButton(R.string.okay_button_text, null).show();
            return;
        }
        String mode = CommandExecutor.mode(this);
        List<String> commands = commands();
        CommandExecutor.submit(() -> {
            CommandResult result = new CommandResult(0, "");
            for (String command : commands) {
                result = CommandExecutor.run(this, mode, command);
                if (!result.success()) break;
            }
            CommandResult completed = result;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) new MaterialAlertDialogBuilder(this)
                        .setMessage(completed.success() ? getString(R.string.applied_success_text) : completed.output)
                        .setPositiveButton(R.string.okay_button_text, null).show();
            });
        });
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.doze_tunables_menu, menu); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == R.id.action_apply_tunables) { applyTunables(); return true; }
        if (item.getItemId() == R.id.action_copy_tunables) {
            StringBuilder adb = new StringBuilder();
            for (String command : commands()) adb.append("adb shell ").append(command).append('\n');
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Doze tunables", adb));
            new MaterialAlertDialogBuilder(this).setTitle(R.string.adb_command_text).setMessage(adb).setPositiveButton(R.string.close_button_text, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public static class DozeTunablesFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle state, String key) {
            setPreferencesFromResource(R.xml.prefs_doze_tunables, key);
            configure(getPreferenceScreen());
        }
        private void configure(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference pref = group.getPreference(i);
                pref.setIconSpaceReserved(false);
                if (pref instanceof PreferenceGroup) configure((PreferenceGroup) pref);
                else pref.setOnPreferenceChangeListener((preference, value) -> {
                    try {
                        double parsed = Double.parseDouble(value.toString());
                        boolean factor = preference.getKey().contains("factor") || preference.getKey().equals("location_accuracy");
                        return Double.isFinite(parsed) && parsed >= (factor ? 0.1 : 0) && parsed <= 604800000L && (factor || parsed == Math.floor(parsed));
                    } catch (RuntimeException e) { return false; }
                });
            }
        }
    }
}
