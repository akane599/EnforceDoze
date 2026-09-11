package com.akylas.enforcedoze;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Completed automation sessions, refreshed only while this history is visible. */
public class DozeBatteryStatsActivity extends UiActivity {
    private final DozeStatsAdapter adapter = new DozeStatsAdapter();
    private SharedPreferences preferences;
    private View recordingState;
    private Set<String> displayedRecords;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "dozeUsageDataAdvanced".equals(key) || "disableStats".equals(key)) refresh();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doze_battery_stats);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        RecyclerView list = findViewById(R.id.material_listview);
        list.setLayoutManager(new LinearLayoutManager(this));
        View header = getLayoutInflater().inflate(R.layout.doze_history_header, list, false);
        recordingState = header.findViewById(R.id.historyRecordingState);
        header.findViewById(R.id.viewDozeEvidence).setOnClickListener(v ->
                startActivity(new Intent(this, DozeEvidenceActivity.class)));
        list.setAdapter(new ConcatAdapter(new ListHeaderAdapter(header), adapter));
        list.setItemAnimator(null);
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
        recordingState.setVisibility(preferences.getBoolean("disableStats", false) ? View.VISIBLE : View.GONE);
        Set<String> records = new HashSet<>(preferences.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        if (records.equals(displayedRecords)) return;
        displayedRecords = records;
        List<DozeStatsCard> cards = new ArrayList<>();
        Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_doze_moon);
        for (DozeStatsParser.Session session : DozeStatsParser.parse(records)) {
            String usage = session.batteryUsed == null ? getString(R.string.stats_charging)
                    : session.batteryUsed == 0 ? getString(R.string.history_level_unchanged)
                    : getString(R.string.history_level_drop, session.batteryUsed);
            cards.add(new DozeStatsCard(getString(R.string.history_session),
                    Utils.getDateCurrentTimeZone(session.start) + "\n"
                            + Utils.timeSpentString(session.start, session.end) + "\n" + usage, icon));
        }
        if (cards.isEmpty()) cards.add(new DozeStatsCard(getString(R.string.dashboard_stats), getString(R.string.stat_empty), icon));
        adapter.replaceCards(cards);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.doze_stats_menu_new, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_stats) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.history_clear_question)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.clear_doze_stats_setting_title, (dialog, which) -> clearStats()).show();
        } else if (id == R.id.action_switch_stats_ui) {
            startActivity(new Intent(this, DozeStatsActivity.class));
        } else if (id == R.id.action_stats_more_info) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dashboard_stats).setMessage(R.string.history_info)
                    .setPositiveButton(R.string.close_button_text, null).show();
        } else if (id == android.R.id.home) {
            finish();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    public void clearStats() {
        preferences.edit().remove("dozeUsageDataAdvanced").apply();
        DozeEvidence.clear(this);
        SensorEvidence.clear(this);
        // History is read on demand; clearing it must not reconfigure a live Doze session.
        refresh();
    }
}
