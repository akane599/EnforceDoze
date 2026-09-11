package com.akylas.enforcedoze;

import android.content.Context;
import android.preference.PreferenceManager;
import android.widget.ListView;
import android.widget.PopupMenu;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoryRegressionTest {
    @Test public void historyRefreshesLiveAndAfterReturningWithoutAccidentalDeletion() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var previous = new HashSet<>(prefs.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        var records = new HashSet<>(Arrays.asList("1000,80,ENTER", "2000,79,EXIT", "3000,79,ENTER", "4000,79,EXIT"));
        try {
            prefs.edit().remove("dozeUsageDataAdvanced").commit();
            try (ActivityScenario<DozeBatteryStatsActivity> scenario = ActivityScenario.launch(DozeBatteryStatsActivity.class)) {
                prefs.edit().putStringSet("dozeUsageDataAdvanced", records).commit();
                awaitRows(scenario, 3); // scrolling header and two completed sessions
                scenario.onActivity(activity -> {
                    var menu = new PopupMenu(activity, activity.findViewById(R.id.material_listview)).getMenu();
                    assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.action_clear_stats, 0, "Clear")));
                    assertEquals("Opening the confirmation must not erase history", records,
                            prefs.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
                });
                scenario.recreate(); // dismiss the unconfirmed clear dialog
                scenario.moveToState(Lifecycle.State.CREATED);
                prefs.edit().remove("dozeUsageDataAdvanced").commit();
                scenario.moveToState(Lifecycle.State.RESUMED);
                awaitRows(scenario, 2); // scrolling header and empty-state card
                TestUi.screenshot("history_empty_refreshed");
            }
        } finally { prefs.edit().putStringSet("dozeUsageDataAdvanced", previous).commit(); }
    }

    private void awaitRows(ActivityScenario<DozeBatteryStatsActivity> scenario, int expected) throws Exception {
        TestUi.await("History remained stale after its stored records changed", () -> {
            AtomicInteger count = new AtomicInteger();
            scenario.onActivity(activity -> count.set(((RecyclerView) activity.findViewById(R.id.material_listview)).getAdapter().getItemCount()));
            return count.get() == expected;
        });
    }

    @Test public void rawHistorySortsNumericTimestampsAndRefreshesWhileVisible() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var previous = new HashSet<>(prefs.getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        try {
            prefs.edit().putStringSet("dozeUsageDataAdvanced", new HashSet<>(Arrays.asList("9000,80,ENTER", "10000,79,EXIT"))).commit();
            try (ActivityScenario<DozeStatsActivity> scenario = ActivityScenario.launch(DozeStatsActivity.class)) {
                scenario.onActivity(activity -> {
                    var adapter = ((ListView) activity.findViewById(R.id.listView)).getAdapter();
                    assertEquals(2, adapter.getCount());
                    assertEquals("10000,79,EXIT", ((BatteryConsumptionItem) adapter.getItem(0)).getTimestampPercCombo());
                });
                prefs.edit().remove("dozeUsageDataAdvanced").commit();
                TestUi.await("Raw history did not refresh after deletion", () -> {
                    AtomicInteger count = new AtomicInteger(-1);
                    scenario.onActivity(activity -> count.set(((ListView) activity.findViewById(R.id.listView)).getAdapter().getCount()));
                    return count.get() == 0;
                });
            }
        } finally { prefs.edit().putStringSet("dozeUsageDataAdvanced", previous).commit(); }
    }
}
