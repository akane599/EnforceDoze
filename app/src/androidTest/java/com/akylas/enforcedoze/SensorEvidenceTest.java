package com.akylas.enforcedoze;

import android.content.Context;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SensorEvidenceTest {
    @Test public void transitionLogIsBoundedRespectsOptOutAndIgnoresReads() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean disabled = prefs.getBoolean("disableStats", false);
        String previous = SensorEvidence.preferences(context).getString("events", "[]");
        try {
            prefs.edit().putBoolean("disableStats", false).commit();
            SensorEvidence.clear(context);
            SensorEvidence.record(context, "shizuku", PrivilegedOperations.PREFIX + "motion get", new CommandResult(0, "Mode : NORMAL"));
            assertEquals("[]", SensorEvidence.preferences(context).getString("events", "[]"));
            for (int i = 0; i < 30; i++) SensorEvidence.record(context, "shizuku", PrivilegedOperations.PREFIX + "motion restrict",
                    new CommandResult(-1, "Denied observation " + i));
            JSONArray records = new JSONArray(SensorEvidence.preferences(context).getString("events", "[]"));
            assertEquals(24, records.length());
            assertEquals("Denied observation 6", records.getJSONObject(0).getString("output"));
            prefs.edit().putBoolean("disableStats", true).commit();
            String saved = SensorEvidence.preferences(context).getString("events", "[]");
            SensorEvidence.record(context, "shizuku", PrivilegedOperations.PREFIX + "sensors false", new CommandResult(0, "After: Sensors off=false"));
            assertEquals(saved, SensorEvidence.preferences(context).getString("events", "[]"));
        } finally {
            prefs.edit().putBoolean("disableStats", disabled).commit();
            SensorEvidence.preferences(context).edit().putString("events", previous).commit();
        }
    }
    @Test public void savedDeniedResultSurvivesActivityRecreationWithoutBackend() {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean disabled = prefs.getBoolean("disableStats", false);
        String previous = SensorEvidence.preferences(context).getString("events", "[]");
        try {
            prefs.edit().putBoolean("disableStats", false).commit();
            SensorEvidence.clear(context);
            SensorEvidence.record(context, "shizuku", PrivilegedOperations.PREFIX + "motion restrict",
                    new CommandResult(-1, "SensorService did not confirm the requested state"));
            try (ActivityScenario<SensorEvidenceActivity> scenario = ActivityScenario.launch(SensorEvidenceActivity.class)) {
                scenario.recreate();
                scenario.onActivity(activity -> {
                    String text = ((android.widget.TextView) activity.findViewById(R.id.sensorEvidenceReport)).getText().toString();
                    assertTrue(text.contains("Exit: -1"));
                    assertTrue(text.contains("did not confirm"));
                });
            }
        } finally {
            prefs.edit().putBoolean("disableStats", disabled).commit();
            SensorEvidence.preferences(context).edit().putString("events", previous).commit();
        }
    }
}
