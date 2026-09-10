package com.akylas.enforcedoze;

import static com.akylas.enforcedoze.Utils.logToLogcat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.TimePickerDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;


public class SettingsActivity extends UiActivity {
    public static String TAG = "EnforceDoze";

    private static void log(String message) {
            logToLogcat(TAG, message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    public static void reloadSettings(Context context) {
        if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
            Intent intent = new Intent("reload-settings");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reloadSettings(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                getOnBackPressedDispatcher().onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        boolean isSuAvailable = false;
        boolean isShizukuAvailable = false;
        private ShizukuHandler shizukuHandler;
        private final ShizukuHandler.OnAvailibilityChange accessListener = value -> {
            isShizukuAvailable = value;
            if (getPreferenceScreen() != null && getContext() != null) toggleRootFeatures(Utils.isShizukuMode(getContext()) ? value : isSuAvailable);
        };

        private void removeIconSpace(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference pref = group.getPreference(i);
                pref.setIconSpaceReserved(false);

                if (pref instanceof PreferenceGroup) {
                    removeIconSpace((PreferenceGroup) pref);
                }
            }
        }
        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                RecyclerView recyclerView =
                        v.findViewById(androidx.preference.R.id.recycler_view);

                if (recyclerView != null) {
                    int bottomInset = insets
                            .getInsets(WindowInsetsCompat.Type.systemBars())
                            .bottom;

                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            bottomInset
                    );
                    recyclerView.setClipToPadding(false);
                }
                return insets;
            });
        }
        @Override
        public void onDisplayPreferenceDialog(@NonNull androidx.preference.Preference preference) {
            if (preference instanceof ListPreference) {
                showListPreferenceDialog((ListPreference)preference);
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        private void showListPreferenceDialog(ListPreference preference) {
            DialogFragment dialogFragment = new MaterialListPreference();
            Bundle bundle = new Bundle(1);
            bundle.putString("key", preference.getKey());
            dialogFragment.setArguments(bundle);
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        private void initializeShizuku() {
            shizukuHandler.checkShizukuAvailability();
            isShizukuAvailable = shizukuHandler.isShizukuAvailable();
        }


        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
            shizukuHandler = ShizukuHandler.getInstance(getActivity());
            boolean useShizuku = Utils.isShizukuMode(getActivity());
            isShizukuAvailable = false;
            if (useShizuku) {
                initializeShizuku();
            }
            isSuAvailable = !useShizuku && PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("isSuAvailable", false);
            shizukuHandler.addAvailabilityListener(accessListener);

            addPreferencesFromResource(R.xml.prefs);
            removeIconSpace(getPreferenceScreen());
//            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("preferenceScreen");
//            PreferenceCategory mainSettings = (PreferenceCategory) findPreference("mainSettings");
//            PreferenceCategory dozeSettings = (PreferenceCategory) findPreference("dozeSettings");
            Preference resetForceDozePref = (Preference) findPreference("resetForceDoze");
            Preference clearDozeStats = (Preference) findPreference("resetDozeStats");
            Preference dozeDelay = (Preference) findPreference("dozeEnterDelay");
            Preference customDozePeriods = (Preference) findPreference("customDozePeriods");
            Preference showPersistentNotif = (Preference) findPreference("showPersistentNotif");
            Preference usePermanentDoze = (Preference) findPreference("usePermanentDoze");
            Preference dozeNotificationBlocklist = (Preference) findPreference("blacklistAppNotifications");
            Preference dozeAppBlocklist = (Preference) findPreference("blacklistApps");
            final Preference executionMode = (Preference) findPreference("executionMode");
            final Preference disableMotionSensors = (Preference) findPreference("disableMotionSensors");
            Preference turnOffDataInDoze = (Preference) findPreference("turnOffDataInDoze");
            Preference whitelistMusicAppNetwork = (Preference) findPreference("whitelistMusicAppNetwork");
            Preference whitelistCurrentApp = (Preference) findPreference("whitelistCurrentApp");
            final Preference autoRotateBrightnessFix = (Preference) findPreference("autoRotateAndBrightnessFix");
            SwitchPreferenceCompat autoRotateFixPref = (SwitchPreferenceCompat) findPreference("autoRotateAndBrightnessFix");

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            updateCustomDozePeriodsSummary(customDozePeriods, sharedPreferences);

            resetForceDozePref.setOnPreferenceClickListener(preference -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                builder.setTitle(getString(R.string.forcedoze_reset_initial_dialog_title));
                builder.setMessage(getString(R.string.forcedoze_reset_initial_dialog_text));
                builder.setPositiveButton(getString(R.string.yes_button_text), (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    resetForceDoze();
                });
                builder.setNegativeButton(getString(R.string.no_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
                builder.show();
                return true;
            });
            showPersistentNotif.setOnPreferenceChangeListener((preference, value) -> {
                if ((boolean)value) {
                    if (!Utils.isPostNotificationPermissionGranted(getActivity())) {
                        requestNotificationPermission();
                        return false;
                    }
                }
                return true;
            });


            executionMode.setOnPreferenceChangeListener((preference, value) -> {
                // The preference is persisted AFTER this callback. React in onSharedPreferenceChanged.
                return true;
            });

            dozeDelay.setOnPreferenceChangeListener((preference, o) -> {
                int delay = (int) o;
                if (delay >= 5 * 60) {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                    builder.setTitle(getString(R.string.doze_delay_warning_dialog_title));
                    builder.setMessage(getString(R.string.doze_delay_warning_dialog_text));
                    builder.setPositiveButton(getString(R.string.okay_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
                    builder.show();
                }
                return true;
            });

            customDozePeriods.setOnPreferenceClickListener(preference -> {
                showCustomDozePeriodsDialog(sharedPreferences, customDozePeriods);
                return true;
            });

            autoRotateFixPref.setOnPreferenceChangeListener((preference, o) -> {
                if (!Utils.isWriteSettingsPermissionGranted(getActivity())) {
                    requestWriteSettingsPermission();
                    return false;
                } else return true;
            });

            clearDozeStats.setOnPreferenceClickListener(preference -> {
                sharedPreferences.edit().remove("dozeUsageDataAdvanced").apply();
                reloadSettings(requireContext());
                new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.doze_battery_stats_clear_msg)
                        .setPositiveButton(R.string.close_button_text, null).show();
                return true;
            });

            turnOffDataInDoze.setOnPreferenceChangeListener((preference, o) -> {
                final boolean newValue = (boolean) o;
                if (!newValue) {
                    return true;
                } else {
                    if (isSuAvailable || isShizukuAvailable) {
                        log("Phone is rooted and SU permission granted");
                        log("Granting android.permission.READ_PHONE_STATE to com.akylas.enforcedoze");
                        executeCommand("pm grant com.akylas.enforcedoze android.permission.READ_PHONE_STATE");
                        return true;
                    } else {
                        log("SU permission denied or not available");
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                        builder.setTitle(getString(R.string.error_text));
                        builder.setMessage(getString(R.string.su_perm_denied_msg));
                        builder.setPositiveButton(getString(R.string.close_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
                        builder.show();
                        return false;
                    }
                }
            });

            whitelistMusicAppNetwork.setOnPreferenceChangeListener((preference, o) -> {
                final boolean newValue = (boolean) o;
                if (newValue) {
                    // we need to check if we have notifications permissions
                    Boolean hasPermission = NotificationService.Companion.getInstance() != null;
                    if (!hasPermission) {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                        builder.setTitle(getString(R.string.notifications_permission));
                        builder.setMessage(getString(R.string.notifications_permission_explanation));
                        builder.setPositiveButton(getString(R.string.open_button_text), (dialogInterface, i) -> {
                            Intent settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, getActivity().getPackageName());
                            }
                            UiSupport.open(requireContext(), settingsIntent);
                            dialogInterface.dismiss();
                        });
                        builder.show();
                    }
                }
                return true;
            });

            whitelistCurrentApp.setOnPreferenceChangeListener((preference, o) -> {
                final boolean newValue = (boolean) o;
                if (newValue) {
                    // we need to check if we have notifications permissions
                    if (!isSuAvailable && !isShizukuAvailable && !Utils.isUsageStatsPermissionGranted(getContext())) {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                        builder.setTitle(getString(R.string.usage_access_permission));
                        builder.setMessage(getString(R.string.usage_access_explanation));
                        builder.setPositiveButton(getString(R.string.open_button_text), (dialogInterface, i) -> {
                            UiSupport.open(requireContext(), new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                            dialogInterface.dismiss();
                        });
                        builder.show();
                    }
                }
                return true;
            });

//            if (sharedPreferences.getBoolean("useNonRootSensorWorkaround", false)) {
//                autoRotateBrightnessFix.setEnabled(true);
//                disableMotionSensors.setEnabled(true);
//                sharedPreferences.edit().putBoolean("autoRotateAndBrightnessFix", false).apply();
//                sharedPreferences.edit().putBoolean("disableMotionSensors", true).apply();
//            }

            turnOffDataInDoze.setEnabled(false);
            turnOffDataInDoze.setSummary(getString(R.string.root_or_shizuku));
            dozeNotificationBlocklist.setEnabled(false);
            dozeNotificationBlocklist.setSummary(getString(R.string.root_or_shizuku));
            dozeAppBlocklist.setEnabled(false);
            dozeAppBlocklist.setSummary(getString(R.string.root_or_shizuku));
            
            Preference turnOffBluetoothInDoze = (Preference) findPreference("turnOffBluetoothInDoze");
            turnOffBluetoothInDoze.setEnabled(false);
            turnOffBluetoothInDoze.setSummary(getString(R.string.root_or_shizuku));
            
            Preference turnOffGPSInDoze = (Preference) findPreference("turnOffGPSInDoze");
            turnOffGPSInDoze.setEnabled(false);
            turnOffGPSInDoze.setSummary(getString(R.string.root_or_shizuku));

            toggleRootFeatures(useShizuku ? isShizukuAvailable : isSuAvailable);
            Preference sponsorPref = findPreference("sponsorProject");
            if (sponsorPref != null) {
                sponsorPref.setOnPreferenceClickListener(preference -> {
                    Utils.openUrl(getActivity(), "https://github.com/sponsors/farfromrefug");
                    return true;
                });
            }

        }

        public void requestWriteSettingsPermission() {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle(getString(R.string.auto_rotate_brightness_fix_dialog_title));
            builder.setMessage(getString(R.string.auto_rotate_brightness_fix_dialog_text));
            builder.setPositiveButton(getString(R.string.authorize_button_text), (dialogInterface, i) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                UiSupport.open(requireContext(), intent);
            });
            builder.setNegativeButton(getString(R.string.deny_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
            builder.show();
        }

        private void showCustomDozePeriodsDialog(SharedPreferences sharedPreferences, Preference preference) {
            ArrayList<String> periods = getSortedCustomDozePeriods(sharedPreferences);
            ArrayList<String> items = new ArrayList<>(periods);
            items.add(getString(R.string.add_custom_doze_period_button));

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle(getString(R.string.custom_doze_periods_setting_title));
            builder.setItems(items.toArray(new String[0]), (dialogInterface, which) -> {
                dialogInterface.dismiss();
                if (which == periods.size()) {
                    showStartTimePicker(sharedPreferences, preference);
                } else {
                    showRemoveCustomDozePeriodDialog(sharedPreferences, preference, periods.get(which));
                }
            });
            builder.setNegativeButton(getString(R.string.close_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
            builder.show();
        }

        private void showRemoveCustomDozePeriodDialog(SharedPreferences sharedPreferences, Preference preference, String period) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle(period);
            builder.setMessage(getString(R.string.remove_custom_doze_period_dialog_text));
            builder.setPositiveButton(getString(R.string.remove_menu_item), (dialogInterface, i) -> {
                ArrayList<String> periods = getSortedCustomDozePeriods(sharedPreferences);
                periods.remove(period);
                saveCustomDozePeriods(sharedPreferences, preference, periods);
                dialogInterface.dismiss();
            });
            builder.setNegativeButton(getString(R.string.no_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
            builder.show();
        }

        private void showStartTimePicker(SharedPreferences sharedPreferences, Preference preference) {
            TimePickerDialog dialog = new TimePickerDialog(getActivity(), (view, hourOfDay, minute) ->
                    showEndTimePicker(sharedPreferences, preference, hourOfDay, minute), 22, 0, true);
            dialog.setTitle(getString(R.string.custom_doze_period_start_title));
            dialog.show();
        }

        private void showEndTimePicker(SharedPreferences sharedPreferences, Preference preference, int startHour, int startMinute) {
            TimePickerDialog dialog = new TimePickerDialog(getActivity(), (view, hourOfDay, minute) -> {
                int start = startHour * 60 + startMinute;
                int end = hourOfDay * 60 + minute;
                if (start == end) {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
                    builder.setTitle(getString(R.string.error_text));
                    builder.setMessage(getString(R.string.custom_doze_period_same_time_error));
                    builder.setPositiveButton(getString(R.string.okay_button_text), (dialogInterface, i) -> dialogInterface.dismiss());
                    builder.show();
                    return;
                }

                ArrayList<String> periods = getSortedCustomDozePeriods(sharedPreferences);
                periods.add(formatTime(startHour, startMinute) + "-" + formatTime(hourOfDay, minute));
                saveCustomDozePeriods(sharedPreferences, preference, periods);
            }, 7, 0, true);
            dialog.setTitle(getString(R.string.custom_doze_period_end_title));
            dialog.show();
        }

        private ArrayList<String> getSortedCustomDozePeriods(SharedPreferences sharedPreferences) {
            Set<String> periodSet = sharedPreferences.getStringSet("customDozePeriods", new LinkedHashSet<String>());
            ArrayList<String> periods = new ArrayList<>(periodSet);
            Collections.sort(periods);
            return periods;
        }

        private void saveCustomDozePeriods(SharedPreferences sharedPreferences, Preference preference, ArrayList<String> periods) {
            sharedPreferences.edit()
                    .putStringSet("customDozePeriods", new LinkedHashSet<>(periods))
                    .apply();
            updateCustomDozePeriodsSummary(preference, sharedPreferences);
            Utils.scheduleNextCustomDozePeriodBoundary(getActivity());
            reloadSettings(getActivity());
        }

        private void updateCustomDozePeriodsSummary(Preference preference, SharedPreferences sharedPreferences) {
            if (preference == null) {
                return;
            }
            ArrayList<String> periods = getSortedCustomDozePeriods(sharedPreferences);
            if (periods.isEmpty()) {
                preference.setSummary(getString(R.string.custom_doze_periods_setting_summary_empty));
            } else {
                preference.setSummary(getString(R.string.custom_doze_periods_setting_summary, android.text.TextUtils.join(", ", periods)));
            }
        }

        private String formatTime(int hour, int minute) {
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        }

        final int POST_NOTIF_PERMISSION_REQUEST_CODE =112;
        public void requestNotificationPermission(){
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(
                            new String[]{"android.permission.POST_NOTIFICATIONS"},
                            POST_NOTIF_PERMISSION_REQUEST_CODE);
                }
            } catch (Exception e){

            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            if (requestCode == POST_NOTIF_PERMISSION_REQUEST_CODE && isAdded()
                    && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                SwitchPreferenceCompat preference = findPreference("showPersistentNotif");
                if (preference != null) preference.setChecked(true);
            }
        }

        public void resetForceDoze() {
            Context context = requireContext().getApplicationContext();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            preferences.edit().putBoolean("serviceEnabled", false).apply();
            Utils.stopForceDozeService(context);
            CommandExecutor.submit(() -> {
                boolean restored = new RecoveryJournal(context).restore();
                if (restored) preferences.edit().clear().putString("executionMode", "shizuku").apply();
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reset_complete_dialog_title)
                            .setMessage(restored ? R.string.reset_complete_dialog_text : R.string.status_recovery)
                            .setPositiveButton(R.string.okay_button_text, (dialog, which) -> { if (restored) requireActivity().finish(); }).show();
                });
            });
        }

        public void toggleRootFeatures(final boolean enabled) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Preference turnOffDataInDoze = (Preference) findPreference("turnOffDataInDoze");
                    Preference dozeNotificationBlocklist = (Preference) findPreference("blacklistAppNotifications");
                    Preference dozeAppBlocklist = (Preference) findPreference("blacklistApps");
                    Preference turnOffAllSensorsInDoze = (Preference) findPreference("turnOffAllSensorsInDoze");
                    Preference turnOnBatterySaverInDoze = (Preference) findPreference("turnOnBatterySaverInDoze");
                    Preference turnOffBiometricsInDoze = (Preference) findPreference("turnOffBiometricsInDoze");
                    Preference turnOnAirplaneInDoze = (Preference) findPreference("turnOnAirplaneInDoze");
                    Preference turnOffBluetoothInDoze = (Preference) findPreference("turnOffBluetoothInDoze");
                    Preference turnOffGPSInDoze = (Preference) findPreference("turnOffGPSInDoze");
                    Preference whitelistAppsFromDozeMode = (Preference) findPreference("whitelistAppsFromDozeMode");
                    if (enabled) {
                        turnOffDataInDoze.setEnabled(true);
                        turnOffDataInDoze.setSummary(getString(R.string.disable_data_during_doze_setting_summary));
                        dozeNotificationBlocklist.setEnabled(true);
                        dozeNotificationBlocklist.setSummary(getString(R.string.notif_blocklist_setting_summary));
                        dozeAppBlocklist.setEnabled(true);
                        dozeAppBlocklist.setSummary(getString(R.string.app_blocklist_setting_summary));
                        turnOffAllSensorsInDoze.setEnabled(true);
                        turnOffAllSensorsInDoze.setSummary(getString(R.string.disable_all_sensors_setting_summary));
                        turnOnBatterySaverInDoze.setEnabled(true);
                        turnOnBatterySaverInDoze.setSummary(getString(R.string.enable_battery_saver_setting_summary));
                        turnOffBiometricsInDoze.setEnabled(true);
                        turnOffBiometricsInDoze.setSummary(getString(R.string.disable_biometrics_setting_summary));
                        turnOnAirplaneInDoze.setEnabled(true);
                        turnOnAirplaneInDoze.setSummary(getString(R.string.enable_airplane_setting_summary));
                        turnOffBluetoothInDoze.setEnabled(true);
                        turnOffBluetoothInDoze.setSummary(getString(R.string.disable_bluetooth_setting_summary));
                        turnOffGPSInDoze.setEnabled(true);
                        turnOffGPSInDoze.setSummary(getString(R.string.disable_gps_setting_summary));
                        whitelistAppsFromDozeMode.setEnabled(true);
                        whitelistAppsFromDozeMode.setSummary(getString(R.string.whitelist_apps_setting_summary));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Preference turnOffWiFiInDoze = (Preference) findPreference("turnOffWiFiInDoze");
                            turnOffWiFiInDoze.setEnabled(true);
                            turnOffWiFiInDoze.setSummary(getString(R.string.disable_wifi_during_doze_setting_summary));
                        }
                    } else {
                        turnOffDataInDoze.setEnabled(false);
                        turnOffDataInDoze.setSummary(getString(R.string.root_or_shizuku));
                        dozeNotificationBlocklist.setEnabled(false);
                        dozeNotificationBlocklist.setSummary(getString(R.string.root_or_shizuku));
                        dozeAppBlocklist.setEnabled(false);
                        dozeAppBlocklist.setSummary(getString(R.string.root_or_shizuku));
                        turnOffAllSensorsInDoze.setEnabled(false);
                        turnOffAllSensorsInDoze.setSummary(getString(R.string.root_or_shizuku));
                        turnOnBatterySaverInDoze.setEnabled(false);
                        turnOnBatterySaverInDoze.setSummary(getString(R.string.root_or_shizuku));
                        turnOffBiometricsInDoze.setEnabled(false);
                        turnOffBiometricsInDoze.setSummary(getString(R.string.root_or_shizuku));
                        turnOnAirplaneInDoze.setEnabled(false);
                        turnOnAirplaneInDoze.setSummary(getString(R.string.root_or_shizuku));
                        turnOffBluetoothInDoze.setEnabled(false);
                        turnOffBluetoothInDoze.setSummary(getString(R.string.root_or_shizuku));
                        turnOffGPSInDoze.setEnabled(false);
                        turnOffGPSInDoze.setSummary(getString(R.string.root_or_shizuku));
                        whitelistAppsFromDozeMode.setEnabled(false);
                        whitelistAppsFromDozeMode.setSummary(getString(R.string.root_or_shizuku));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Preference turnOffWiFiInDoze = (Preference) findPreference("turnOffWiFiInDoze");
                            turnOffWiFiInDoze.setEnabled(false);
                            turnOffWiFiInDoze.setSummary(getString(R.string.root_or_shizuku));
                        }

                    }
                });
            }
        }

        public void executeCommand(final String command) {
            if (getContext() == null) return;
            CommandExecutor.execute(requireContext(), command, result -> {
                if (!result.success()) Utils.logToLogcat("Settings", result.output);
            });
        }


        public void executeCommandWithRoot(final String command) { executeCommand(command); }

        public void executeCommandWithoutRoot(final String command) { executeCommand(command); }

        public void printShellOutput(List<String> output) {
            if (!output.isEmpty()) {
                for (String s : output) {
                    log(s);
                }
            }
        }

        @Override public void onResume() {
            super.onResume();
            initializeShizuku();
            isSuAvailable = "root".equals(CommandExecutor.mode(requireContext())) && PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("isSuAvailable", false);
            toggleRootFeatures(Utils.isShizukuMode(requireContext()) ? isShizukuAvailable : isSuAvailable);
        }
        @Override public void onDestroy() {
            shizukuHandler.removeAvailabilityListener(accessListener);
            if (getPreferenceManager().getSharedPreferences() != null) getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
            // Runtime status and history share this file; they must never trigger reload loops.
            if (key == null || findPreference(key) == null) return;
            if ("executionMode".equals(key) && getContext() != null) {
                isSuAvailable = "root".equals(CommandExecutor.mode(requireContext()))
                        && sharedPreferences.getBoolean("isSuAvailable", false);
                initializeShizuku();
                toggleRootFeatures(Utils.isShizukuMode(getContext()) ? isShizukuAvailable : isSuAvailable);
            }
            if ("customDozePeriods".equals(key)) {
                updateCustomDozePeriodsSummary(findPreference("customDozePeriods"), sharedPreferences);
            }
            if (getActivity() != null) {
                reloadSettings(getActivity());
            }
        }
    }
}
