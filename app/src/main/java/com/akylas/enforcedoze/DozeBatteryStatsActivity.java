package com.akylas.enforcedoze;

import static com.akylas.enforcedoze.Utils.logToLogcat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DozeBatteryStatsActivity extends UiActivity {

    Set<String> dozeUsageStats;
    ArrayList<String> sortedDozeUsageStats;
    RecyclerView mListView;
    DozeStatsAdapter adapter;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    public static String TAG = "EnforceDoze";

    private static void log(String message) {
        logToLogcat(TAG, message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doze_battery_stats);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = sharedPreferences.edit();
        dozeUsageStats = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageDataAdvanced", new LinkedHashSet<String>());
        mListView = findViewById(R.id.material_listview);
        adapter = new DozeStatsAdapter();
        mListView.setAdapter(adapter);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        ViewCompat.setOnApplyWindowInsetsListener(mListView, (v, insets) -> {
            if (mListView != null) {
                int bottomInset = insets
                        .getInsets(WindowInsetsCompat.Type.systemBars())
                        .bottom;

                mListView.setPadding(
                        mListView.getPaddingLeft(),
                        mListView.getPaddingTop(),
                        mListView.getPaddingRight(),
                        bottomInset
                );
                mListView.setClipToPadding(false);
            }
            return insets;
        });
        java.util.List<DozeStatsParser.Session> sessions = DozeStatsParser.parse(dozeUsageStats);
        for (DozeStatsParser.Session session : sessions) {
            String usage = session.batteryUsed == null ? getString(R.string.stats_charging) : session.batteryUsed + "%";
            DozeStatsCard card = new DozeStatsCard(getString(R.string.stats_session),
                    Utils.getDateCurrentTimeZone(session.start) + "\n" + Utils.timeSpentString(this, session.start, session.end) + "\n" + usage,
                    returnDrawableBattery(session.batteryUsed == null ? 0 : session.batteryUsed));
            adapter.addCard(card);
        }
        if (sessions.isEmpty()) adapter.addCard(new DozeStatsCard(getString(R.string.dashboard_stats), getString(R.string.stat_empty), returnDrawableBattery(0)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.doze_stats_menu_new, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_stats) {
            clearStats();
        } else if (id == R.id.action_switch_stats_ui) {
            startActivity(new Intent(this, DozeStatsActivity.class));
        } else if (id == R.id.action_stats_more_info) {
            showMoreInfoDialog();
        } else if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showMoreInfoDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.doze_stats_battery_icon_meaning_dialog_title));
        builder.setMessage(getString(R.string.doze_stats_battery_icon_meaning_dialog_text));
        builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public Drawable returnDrawableBattery(int bUsage) {
        return (bUsage >= 3) ? ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_battery_alert_black_48dp) : ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_battery_charging_full_black_48dp);
    }

    public void clearStats() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove("dozeUsageDataAdvanced").apply();
        SettingsActivity.reloadSettings(this);
        adapter.clearAll();
        adapter.addCard(new DozeStatsCard(getString(R.string.dashboard_stats), getString(R.string.stat_empty), returnDrawableBattery(0)));
    }
}
