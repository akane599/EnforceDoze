package com.akylas.enforcedoze;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Raw history retained for inspecting entry, exit, and maintenance events. */
public class DozeStatsActivity extends UiActivity {
    private final ArrayList<BatteryConsumptionItem> items = new ArrayList<>();
    private BatteryConsumptionAdapter adapter;
    private SharedPreferences preferences;
    private Set<String> displayedRecords;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "dozeUsageDataAdvanced".equals(key)) refresh();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doze_battery_consumption);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        adapter = new BatteryConsumptionAdapter(this, items);
        ((ListView) findViewById(R.id.listView)).setAdapter(adapter);
    }

    @Override protected void onStart() {
        super.onStart();
        preferences.registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    @Override protected void onStop() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onStop();
    }

    private void refresh() {
        Set<String> records = new HashSet<>(preferences.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        if (records.equals(displayedRecords)) return;
        displayedRecords = records;
        ArrayList<String> sorted = new ArrayList<>(records);
        // The raw view retains damaged records for diagnosis; valid timestamps sort numerically.
        sorted.sort((a, b) -> Long.compare(timestamp(b), timestamp(a)));
        items.clear();
        for (String record : sorted) {
            BatteryConsumptionItem item = new BatteryConsumptionItem();
            item.setTimestampPercCombo(record);
            items.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private static long timestamp(String record) {
        if (record == null) return Long.MIN_VALUE;
        int separator = record.indexOf(',');
        if (separator < 0) return Long.MIN_VALUE;
        try { return Long.parseLong(record.substring(0, separator)); }
        catch (NumberFormatException ignored) { return Long.MIN_VALUE; }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.doze_stats_menu, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_stats) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.history_clear_question)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.clear_doze_stats_setting_title, (dialog, which) -> clearStats()).show();
        } else if (id == android.R.id.home) {
            finish();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    public void clearStats() {
        preferences.edit().remove("dozeUsageDataAdvanced").apply();
        DozeEvidence.clear(this);
        SensorEvidence.clear(this);
        refresh();
    }
}
