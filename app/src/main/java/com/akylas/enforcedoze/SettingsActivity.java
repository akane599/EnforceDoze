package com.akylas.enforcedoze;

import android.content.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.*;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.*;

public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Settings", true);
        root.removeViewAt(1);
        FrameLayout content = new FrameLayout(this);
        content.setId(R.id.settings);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (bundle == null)
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
    }

    public static void reloadSettings(Context context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("reload-settings"));
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private SharedPreferences prefs;

        @Override
        public void onCreatePreferences(Bundle bundle, String key) {
            prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            PreferenceScreen screen =
                    getPreferenceManager().createPreferenceScreen(requireContext());
            setPreferenceScreen(screen);
            PreferenceCategory general = category("Monitoring");
            link(
                    general,
                    "Access & permissions",
                    "Shizuku, root, notifications, call protection and exact alarms",
                    AccessActivity.class);
            toggle(
                    general,
                    "disableWhenCharging",
                    "Pause while charging",
                    "Restore temporary changes when power is connected.",
                    true);
            toggle(
                    general,
                    "ignoreLockscreenTimeout",
                    "Ignore lock delay",
                    "Start after the screen-off delay without also waiting for Android’s lock"
                        + " timeout.",
                    true);
            Preference delay =
                    entry(
                            general,
                            "Screen-off delay",
                            "0–1800 seconds. Without exact-alarm access, Android may delay the"
                                + " trigger.");
            delay.setKey("delayEditor");
            delay.setOnPreferenceClickListener(
                    p -> {
                        editNumber("Screen-off delay", "dozeEnterDelay", 0, 1800);
                        return true;
                    });
            Preference periods =
                    entry(
                            general,
                            "Daily schedule",
                            "Local-time periods, including overnight. Empty means all day. Manual"
                                + " Stop always takes priority.");
            periods.setOnPreferenceClickListener(
                    p -> {
                        editPeriods();
                        return true;
                    });
            toggle(
                    general,
                    "waitForUnlock",
                    "Restore connectivity on unlock",
                    "Sensor controls and forced Doze end on screen-on. Other temporary changes may"
                        + " remain until unlock. Charging, calls and stopping still restore"
                        + " everything.",
                    false);
            toggle(
                    general,
                    "showPersistentNotif",
                    "Detailed monitoring notification",
                    "Optional live status. Off keeps a quiet, stable monitoring notification."
                        + " Errors use a separate notification.",
                    false);
            toggle(
                    general,
                    "showDisabledNotification",
                    "Reminder when monitoring is off",
                    "Show a quiet shortcut to reopen EnforceDoze after stopping.",
                    false);

            PreferenceCategory network = category("Connectivity & power");
            entry(
                            network,
                            "Delivery can be interrupted",
                            "Disabling a radio can delay messages and VoIP calls. Doze exemptions"
                                + " cannot compensate for a disabled connection.")
                    .setSelectable(false);
            toggle(
                    network,
                    "ignoreIfHotspot",
                    "Protect hotspot",
                    "Keep Wi-Fi, mobile data and airplane mode unchanged when hotspot is active or"
                        + " its state cannot be read.",
                    true);
            toggle(
                    network,
                    "whitelistMusicAppNetwork",
                    "Protect media playback",
                    "Keep connectivity and playing apps available. Requires notification access;"
                        + " unknown playback state is protected conservatively.",
                    false);
            toggle(
                    network,
                    "turnOffWiFiInDoze",
                    "Turn off Wi-Fi",
                    "Temporarily disables Wi-Fi, then restores the observed original state. Shizuku"
                        + " started over wireless debugging may disconnect on some devices.",
                    false);
            toggle(
                    network,
                    "turnOffDataInDoze",
                    "Turn off mobile data",
                    "Controls the default data SIM’s user setting. The saved SIM is restored even"
                        + " if the default changes. Requires a compatible telephony interface; no"
                        + " SIM means skipped.",
                    false);
            toggle(
                    network,
                    "turnOnAirplaneInDoze",
                    "Enable airplane mode",
                    "Intentionally disconnects cellular service, including incoming calls. Carrier"
                        + " and OEM behavior varies. Restore is attempted on observed calls and"
                        + " screen-on.",
                    false);
            toggle(
                    network,
                    "turnOffBluetoothInDoze",
                    "Turn off Bluetooth",
                    "Disconnects headphones, watches and other Bluetooth devices. Media protection"
                        + " takes priority.",
                    false);
            toggle(
                    network,
                    "turnOffGPSInDoze",
                    "Turn off location",
                    "Disables the user’s location setting, including navigation and location-based"
                        + " background work.",
                    false);
            toggle(
                    network,
                    "turnOnBatterySaverInDoze",
                    "Enable battery saver",
                    "Restores the original saver state. Android may reject battery saver while"
                        + " charging.",
                    false);

            PreferenceCategory sensors = category("Sensor & unlock controls");
            toggle(
                    sensors,
                    "disableMotionSensors",
                    "Restrict app sensor access",
                    "Requests SensorService restricted mode and saves readback evidence. May affect"
                        + " rotation, activity tracking and sensor apps. Does not prove physical"
                        + " sensors are powered off.",
                    false);
            Preference exemption =
                    entry(
                            sensors,
                            "Sensor access exemption",
                            "Optional package name. Android matches this as a substring; it may"
                                + " match related packages too.");
            exemption.setOnPreferenceClickListener(
                    p -> {
                        editSensorExemption();
                        return true;
                    });
            toggle(
                    sensors,
                    "turnOffAllSensorsInDoze",
                    "Developer sensor privacy",
                    "Separate Sensors off control, including camera/microphone clients where"
                        + " Android supports it. Uses Shizuku or root and requires a verified"
                        + " original state.",
                    false);
            toggle(
                    sensors,
                    "turnOffBiometricsInDoze",
                    "Biometric keyguard setting (experimental)",
                    "Only changes an existing biometric_keyguard_enabled setting; it does not power"
                        + " off fingerprint hardware. Samsung may ignore it. Restored on screen-on"
                        + " before unlock.",
                    false);
            Preference legacy =
                    entry(
                            sensors,
                            "Legacy rotation/brightness toggle",
                            "Retired: SensorService is restored directly. Toggling display settings"
                                + " can overwrite user changes and is not required by the Android"
                                + " 16 restore path.");
            legacy.setSelectable(false);

            PreferenceCategory apps = category("App controls");
            link(
                    apps,
                    "Doze exemptions",
                    "Persistent system exemptions; review messaging apps here.",
                    WhitelistAppsActivity.class);
            link(
                    apps,
                    "Suspend selected apps",
                    "Temporary suspension interrupts app execution and notifications. System apps"
                        + " and the access provider are protected.",
                    BlockAppsActivity.class);
            toggle(
                    apps,
                    "whitelistCurrentApp",
                    "Protect foreground app",
                    "Skips suspension when the foreground app cannot be identified. The screen-off"
                        + " system snapshot may show the launcher or lock screen.",
                    false);
            link(
                    apps,
                    "Filter selected notifications",
                    "Dismisses new, non-ongoing notifications during monitored screen-off"
                        + " intervals. Calls, alarms and media are protected. Dismissed"
                        + " notifications cannot be recreated.",
                    BlockNotificationsActivity.class);
            link(
                    apps,
                    "Media & notification access",
                    "Open permission setup for playback detection and filtering.",
                    AccessActivity.class);

            PreferenceCategory other = category("Appearance, history & advanced");
            ListPreference theme = new ListPreference(requireContext());
            theme.setKey("theme");
            theme.setTitle("Theme");
            theme.setEntries(new String[] {"Follow system", "Light", "Dark"});
            theme.setEntryValues(new String[] {"system", "light", "dark"});
            theme.setDefaultValue("system");
            theme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            theme.setIconSpaceReserved(false);
            theme.setSingleLineTitle(false);
            other.addPreference(theme);
            toggle(
                    other,
                    "disableStats",
                    "Do not save monitoring history",
                    "Diagnostic observations and recovery records remain available for safe"
                        + " operation.",
                    false);
            toggle(
                    other,
                    "disableLogcat",
                    "Reduce developer logging",
                    "Local recovery evidence remains enabled.",
                    false);
            link(other, "Diagnostics", "Copy or clear local saved evidence.", LogActivity.class);
            link(
                    other,
                    "Doze tunables",
                    "Advanced Android timing values. Changes are verified and can be restored.",
                    DozeTunablesActivity.class);
            link(
                    other,
                    "Tasker automation",
                    "Opt-in token protects exported broadcasts.",
                    TaskerBroadcastsActivity.class);
            link(
                    other,
                    "Android & Samsung guidance",
                    "Recovery limits, schedules, sensors and background settings.",
                    CompatibilityActivity.class);
            Preference reset =
                    entry(
                            other,
                            "Reset app options",
                            "Stops monitoring and restores saved changes before clearing options."
                                + " Recovery data is never discarded on failure.");
            reset.setOnPreferenceClickListener(
                    p -> {
                        reset();
                        return true;
                    });
            link(
                    other,
                    "About EnforceDoze",
                    "Version, source, license and support",
                    AboutAppActivity.class);
        }

        private PreferenceCategory category(String title) {
            PreferenceCategory p = new PreferenceCategory(requireContext());
            p.setTitle(title);
            p.setIconSpaceReserved(false);
            getPreferenceScreen().addPreference(p);
            return p;
        }

        private Preference entry(PreferenceGroup parent, String title, String summary) {
            Preference p = new Preference(requireContext());
            p.setTitle(title);
            p.setSummary(summary);
            p.setIconSpaceReserved(false);
            p.setSingleLineTitle(false);
            parent.addPreference(p);
            return p;
        }

        private void link(PreferenceGroup parent, String title, String summary, Class<?> target) {
            entry(parent, title, summary)
                    .setOnPreferenceClickListener(
                            p -> {
                                startActivity(new Intent(requireContext(), target));
                                return true;
                            });
        }

        private void toggle(
                PreferenceGroup parent, String key, String title, String summary, boolean value) {
            SwitchPreferenceCompat p = new SwitchPreferenceCompat(requireContext());
            p.setKey(key);
            p.setTitle(title);
            p.setSummary(summary);
            p.setDefaultValue(value);
            p.setIconSpaceReserved(false);
            p.setSingleLineTitle(false);
            parent.addPreference(p);
        }

        private EditText input(String text, int type) {
            EditText input = new EditText(requireContext());
            input.setInputType(type);
            input.setText(text);
            input.setPadding(32, 24, 32, 24);
            return input;
        }

        private void editNumber(String title, String key, int min, int max) {
            EditText value =
                    input(String.valueOf(prefs.getInt(key, 0)), InputType.TYPE_CLASS_NUMBER);
            androidx.appcompat.app.AlertDialog dialog =
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(title)
                            .setView(value)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save", null)
                            .create();
            dialog.setOnShowListener(
                    d ->
                            dialog.getButton(-1)
                                    .setOnClickListener(
                                            v -> {
                                                try {
                                                    int n =
                                                            Integer.parseInt(
                                                                    value.getText().toString());
                                                    if (n < min || n > max)
                                                        throw new NumberFormatException();
                                                    prefs.edit().putInt(key, n).apply();
                                                    dialog.dismiss();
                                                } catch (NumberFormatException e) {
                                                    value.setError(
                                                            "Use a number from "
                                                                    + min
                                                                    + " to "
                                                                    + max);
                                                }
                                            }));
            dialog.show();
        }

        private void editPeriods() {
            Set<String> saved = prefs.getStringSet("customDozePeriods", Collections.emptySet());
            EditText value =
                    input(
                            android.text.TextUtils.join("\n", saved),
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            value.setHint("22:00-07:00\n12:00-13:00");
            androidx.appcompat.app.AlertDialog dialog =
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Daily periods · one per line")
                            .setMessage("Use 24-hour HH:mm-HH:mm. Leave empty for all day.")
                            .setView(value)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save", null)
                            .create();
            dialog.setOnShowListener(
                    d ->
                            dialog.getButton(-1)
                                    .setOnClickListener(
                                            v -> {
                                                Set<String> periods = new LinkedHashSet<>();
                                                for (String line :
                                                        value.getText().toString().split("\\n")) {
                                                    line = line.trim();
                                                    if (line.isEmpty()) continue;
                                                    if (ScheduleRules.parse(line) == null) {
                                                        value.setError(
                                                                "Use a valid period, for example"
                                                                    + " 22:00-07:00");
                                                        return;
                                                    }
                                                    periods.add(line);
                                                }
                                                prefs.edit()
                                                        .putStringSet("customDozePeriods", periods)
                                                        .apply();
                                                dialog.dismiss();
                                            }));
            dialog.show();
        }

        private void editSensorExemption() {
            EditText value =
                    input(prefs.getString("sensorWhitelistPackage", ""), InputType.TYPE_CLASS_TEXT);
            androidx.appcompat.app.AlertDialog dialog =
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Sensor exemption")
                            .setView(value)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save", null)
                            .create();
            dialog.setOnShowListener(
                    d ->
                            dialog.getButton(-1)
                                    .setOnClickListener(
                                            v -> {
                                                String pkg = value.getText().toString().trim();
                                                if (!pkg.isEmpty()
                                                        && !CommandResult.validPackage(pkg)) {
                                                    value.setError(
                                                            "Enter a package name or leave empty");
                                                    return;
                                                }
                                                prefs.edit()
                                                        .putString("sensorWhitelistPackage", pkg)
                                                        .apply();
                                                dialog.dismiss();
                                            }));
            dialog.show();
        }

        private void reset() {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reset app options?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                            "Stop, restore & reset",
                            (d, w) -> {
                                Context context = requireContext().getApplicationContext();
                                prefs.edit().putBoolean("serviceEnabled", false).apply();
                                Utils.stopForceDozeService(context);
                                AccessExecutor.SERIAL.execute(
                                        () -> {
                                            boolean restored;
                                            try {
                                                restored = new DeviceController(context).restore();
                                                restored =
                                                        TunableRecovery.restore(context)
                                                                && restored;
                                            } catch (RuntimeException e) {
                                                restored = false;
                                            }
                                            if (restored) prefs.edit().clear().commit();
                                            boolean done = restored;
                                            if (getActivity() != null)
                                                requireActivity()
                                                        .runOnUiThread(
                                                                () -> {
                                                                    if (!isAdded()) return;
                                                                    if (done) {
                                                                        MyApplication.applyTheme(
                                                                                context);
                                                                        requireActivity()
                                                                                .recreate();
                                                                    } else
                                                                        new MaterialAlertDialogBuilder(
                                                                                        requireContext())
                                                                                .setTitle(
                                                                                        "Restoration"
                                                                                            + " pending")
                                                                                .setMessage(
                                                                                        new RecoveryStore(
                                                                                                                context)
                                                                                                        .summary()
                                                                                                + "\n"
                                                                                                + new RecoveryStore(
                                                                                                                context,
                                                                                                                "tunable_recovery")
                                                                                                        .summary())
                                                                                .setPositiveButton(
                                                                                        "Close",
                                                                                        null)
                                                                                .show();
                                                                });
                                        });
                            })
                    .show();
        }

        @Override
        public void onStart() {
            super.onStart();
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onStop();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if ("theme".equals(key)) MyApplication.applyTheme(requireContext());
            reloadSettings(requireContext());
        }
    }
}
