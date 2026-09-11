package com.akylas.enforcedoze;

import android.content.Context;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 36)
public class SettingsSupportTest {
    @Test public void android16ShowsUsableModesAndNotificationDependencies() {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadDetails = prefs.contains("detailedMonitorNotification");
        boolean details = prefs.getBoolean("detailedMonitorNotification", false);
        boolean hadBiometrics = prefs.contains("turnOffBiometricsInDoze");
        boolean biometrics = prefs.getBoolean("turnOffBiometricsInDoze", false);
        try {
            prefs.edit().putBoolean("detailedMonitorNotification", false)
                    .putBoolean("turnOffBiometricsInDoze", false).commit();
            try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
                scenario.onActivity(activity -> {
                    SettingsActivity.SettingsFragment fragment = (SettingsActivity.SettingsFragment)
                            activity.getSupportFragmentManager().findFragmentById(R.id.settings);
                    ListPreference mode = fragment.findPreference("executionMode");
                    assertArrayEquals(new CharSequence[]{"root", "shizuku"}, mode.getEntryValues());
                    Preference unavailable = fragment.findPreference("turnOffBiometricsInDoze");
                    assertFalse(unavailable.isEnabled());
                    assertEquals(activity.getString(R.string.biometrics_unsupported_summary), unavailable.getSummary());
                    Preference summary = fragment.findPreference("showPersistentNotif");
                    assertFalse(summary.isEnabled());
                    ((SwitchPreferenceCompat) fragment.findPreference("detailedMonitorNotification")).setChecked(true);
                    assertTrue(summary.isEnabled());
                });
                scenario.recreate();
                scenario.onActivity(activity -> {
                    SettingsActivity.SettingsFragment fragment = (SettingsActivity.SettingsFragment)
                            activity.getSupportFragmentManager().findFragmentById(R.id.settings);
                    assertTrue(fragment.findPreference("showPersistentNotif").isEnabled());
                });
            }
        } finally {
            var edit = prefs.edit();
            if (hadDetails) edit.putBoolean("detailedMonitorNotification", details); else edit.remove("detailedMonitorNotification");
            if (hadBiometrics) edit.putBoolean("turnOffBiometricsInDoze", biometrics); else edit.remove("turnOffBiometricsInDoze");
            edit.commit();
        }
    }

    @Test public void retiredTunablesAreDisabledAndCurrentKeysAreNamed() {
        try (ActivityScenario<DozeTunablesActivity> scenario = ActivityScenario.launch(DozeTunablesActivity.class)) {
            scenario.onActivity(activity -> {
                DozeTunablesActivity.DozeTunablesFragment fragment = (DozeTunablesActivity.DozeTunablesFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.content);
                assertFalse(fragment.findPreference("light_pre_idle_to").isEnabled());
                assertEquals("notification_allowlist_duration_ms",
                        fragment.findPreference("notification_whitelist_duration").getTitle());
                Preference duration = fragment.findPreference("idle_to");
                assertFalse(duration.getOnPreferenceChangeListener().onPreferenceChange(duration, "1.5"));
            });
        }
    }
}
