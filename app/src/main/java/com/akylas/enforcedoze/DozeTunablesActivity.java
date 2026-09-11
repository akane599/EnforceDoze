package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DozeTunablesActivity extends UiActivity {
    private static final String EDITED_KEYS = "editedDozeTunables";
    private boolean applying;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_tunables);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (state == null) getSupportFragmentManager().beginTransaction().replace(R.id.content, new DozeTunablesFragment()).commit();
    }

    private List<String> commands() {
        Set<String> edited = PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet(EDITED_KEYS, Collections.emptySet());
        StringBuilder selected = new StringBuilder();
        for (String entry : DozeTunableHandler.getInstance().getTunableString().split(",")) {
            String key = entry.substring(0, entry.indexOf('='));
            if (!edited.contains(key) || (Build.VERSION.SDK_INT >= 34 && DozeTunablePolicy.modernKey(key) == null)) continue;
            if (selected.length() > 0) selected.append(',');
            selected.append(entry);
        }
        if (selected.length() == 0) return Collections.emptyList();
        if (Build.VERSION.SDK_INT >= 34) return DozeTunablePolicy.modernCommands(selected.toString());
        return Collections.singletonList("settings put global device_idle_constants " + CommandPolicy.quote(selected.toString()));
    }

    private void applyTunables() {
        if (applying) return;
        if (Utils.isMyServiceRunning(ForceDozeService.class, this) || new RecoveryJournal(this).hasPending()) {
            new MaterialAlertDialogBuilder(this).setMessage("Disable EnforceDoze and restore any pending changes before applying persistent tunables, so session restoration cannot overwrite them.")
                    .setPositiveButton(R.string.okay_button_text, null).show();
            return;
        }
        List<String> commands = commands();
        if (commands.isEmpty()) { showMessage(getString(R.string.tunable_no_edits)); return; }
        String mode = CommandExecutor.mode(this);
        applying = true;
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.tunable_apply_running, Toast.LENGTH_SHORT).show();
        CommandExecutor.submit(() -> {
            int verified = 0;
            CommandResult result = new CommandResult(0, "");
            CommandResult legacy = CommandExecutor.run(this, mode, "settings get global device_idle_constants");
            // A nonempty legacy string can override DeviceConfig on modern Android. Do not erase
            // another tool's values or report a successful write as successful controller behavior.
            if (!legacy.success()) result = legacy;
            else if (Build.VERSION.SDK_INT >= 34 && !legacy.output.trim().equals("null") && !legacy.output.trim().isEmpty())
                result = new CommandResult(-1, getString(R.string.tunable_override_conflict));
            if (result.success()) for (String command : commands) {
                result = CommandExecutor.run(this, mode, command);
                if (!result.success()) break;
                String expected;
                String read;
                if (Build.VERSION.SDK_INT >= 34) {
                    String[] parts = command.split(" ");
                    expected = parts[4];
                    read = "device_config get device_idle " + parts[3];
                } else {
                    expected = command.substring(command.indexOf(" '") + 2, command.length() - 1);
                    read = "settings get global device_idle_constants";
                }
                result = CommandExecutor.run(this, mode, read);
                if (!result.success()) break;
                if (!expected.equals(result.output.trim())) {
                    result = new CommandResult(-1, getString(R.string.tunable_readback_failed, read));
                    break;
                }
                verified++;
            }
            String message = result.success() ? getString(R.string.tunable_apply_verified, verified)
                    : getString(R.string.tunable_apply_failed, verified, result.output);
            runOnUiThread(() -> {
                applying = false;
                invalidateOptionsMenu();
                if (!isFinishing() && !isDestroyed()) showMessage(message);
            });
        });
    }

    private void showMessage(String message) {
        new MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(R.string.okay_button_text, null).show();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.doze_tunables_menu, menu); return true; }
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem apply = menu.findItem(R.id.action_apply_tunables);
        if (apply != null) apply.setEnabled(!applying);
        return super.onPrepareOptionsMenu(menu);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == R.id.action_apply_tunables) { applyTunables(); return true; }
        if (item.getItemId() == R.id.action_copy_tunables) {
            List<String> commands = commands();
            if (commands.isEmpty()) { showMessage(getString(R.string.tunable_no_edits)); return true; }
            StringBuilder adb = new StringBuilder();
            for (String command : commands) adb.append("adb shell ").append(command).append('\n');
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Doze tunables", adb));
            new MaterialAlertDialogBuilder(this).setTitle(R.string.adb_command_text).setMessage(adb).setPositiveButton(R.string.close_button_text, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class DozeTunablesFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle state, String key) {
            setPreferencesFromResource(R.xml.prefs_doze_tunables, key);
            Preference notice = new Preference(requireContext());
            notice.setTitle(R.string.tunable_editor_notice_title);
            notice.setSummary(R.string.tunable_editor_notice);
            notice.setSelectable(false);
            notice.setOrder(-1);
            getPreferenceScreen().addPreference(notice);
            configure(getPreferenceScreen());
        }
        private void configure(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference pref = group.getPreference(i);
                pref.setIconSpaceReserved(false);
                if (pref instanceof PreferenceGroup) configure((PreferenceGroup) pref);
                else if (pref instanceof EditTextPreference) {
                    EditTextPreference editor = (EditTextPreference) pref;
                    String sourceKey = pref.getKey();
                    String platformKey = Build.VERSION.SDK_INT >= 34 ? DozeTunablePolicy.modernKey(sourceKey) : sourceKey;
                    if (platformKey == null) {
                        pref.setEnabled(false);
                        pref.setSummary(R.string.tunable_retired);
                        continue;
                    }
                    pref.setTitle(platformKey);
                    CharSequence description = pref.getSummary();
                    editor.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) value -> getString(R.string.tunable_value_summary, description, value.getText()));
                    pref.setOnPreferenceChangeListener((preference, value) -> {
                        if (!DozeTunablePolicy.validValue(sourceKey, value.toString())) {
                            Toast.makeText(requireContext(), R.string.tunable_invalid, Toast.LENGTH_LONG).show();
                            return false;
                        }
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                        Set<String> edited = new HashSet<>(prefs.getStringSet(EDITED_KEYS, Collections.emptySet()));
                        edited.add(sourceKey);
                        prefs.edit().putStringSet(EDITED_KEYS, edited).apply();
                        return true;
                    });
                }
            }
        }
    }
}
