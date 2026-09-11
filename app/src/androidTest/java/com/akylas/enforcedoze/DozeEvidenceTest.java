package com.akylas.enforcedoze;

import android.content.Context;
import android.preference.PreferenceManager;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DozeEvidenceTest {
    private Context context;
    private String previous;
    private boolean disabled;
    @Before public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        previous = DozeEvidence.preferences(context).getString("observations_v1", null);
        disabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("disableStats", false);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("disableStats", false).commit();
        DozeEvidence.clear(context);
    }
    @After public void restore() {
        DozeEvidence.preferences(context).edit().putString("observations_v1", previous).commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("disableStats", disabled).commit();
    }
    private DozeObservation fixture() {
        return new DozeObservation(1789081200000L, 1234, 25, "test-fixture", "ENTRY", "shizuku", "test fixture", "test",
                0, "IDLE", "mState=IDLE\nmScreenOn=false", false, false, true, true, true);
    }
    @Test public void savedEvidenceSurvivesRecreationWithoutACommandConnection() throws Exception {
        DozeEvidence.append(context, fixture());
        DozeObservation saved = DozeEvidence.read(context).get(0);
        assertTrue(saved.confirmedScreenOffIdle());
        assertEquals("test-fixture", saved.session);
        assertEquals(25, saved.readMillis);
        assertTrue(DozeEvidence.report(context).contains("mState=IDLE"));
        CountDownLatch busy = new CountDownLatch(1), release = new CountDownLatch(1);
        CommandExecutor.submit(() -> {
            busy.countDown();
            try { release.await(30, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(busy.await(5, TimeUnit.SECONDS));
            try (ActivityScenario<DozeEvidenceActivity> scenario = ActivityScenario.launch(DozeEvidenceActivity.class)) {
                String expected = context.getString(R.string.evidence_last_confirmed, DozeEvidence.timestamp(saved));
                scenario.onActivity(activity -> assertEquals(expected, ((TextView) activity.findViewById(R.id.evidenceSummary)).getText().toString()));
                scenario.recreate();
                scenario.onActivity(activity -> assertEquals(expected, ((TextView) activity.findViewById(R.id.evidenceSummary)).getText().toString()));
            }
        } finally { release.countDown(); }
    }
    @Test public void missingFieldsNeverBecomeConfirmationAndHistoryIsNotBackfilled() throws Exception {
        DozeEvidence.append(context, fixture());
        JSONArray data = new JSONArray(DozeEvidence.preferences(context).getString("observations_v1", "[]"));
        data.getJSONObject(0).remove("interactiveAfter");
        DozeEvidence.preferences(context).edit().putString("observations_v1", data.toString()).commit();
        assertTrue(DozeEvidence.read(context).isEmpty());
        try (ActivityScenario<DozeEvidenceActivity> scenario = ActivityScenario.launch(DozeEvidenceActivity.class)) {
            scenario.onActivity(activity -> assertEquals(activity.getString(R.string.evidence_none_confirmed),
                    ((TextView) activity.findViewById(R.id.evidenceSummary)).getText().toString()));
        }
    }
    @Test public void storageIsBoundedAndStatisticsOptOutStopsNewRecords() throws Exception {
        DozeEvidence.append(context, fixture());
        String item = new JSONArray(DozeEvidence.preferences(context).getString("observations_v1", "[]")).getJSONObject(0).toString();
        JSONArray many = new JSONArray();
        for (int i = 0; i < DozeEvidence.MAX_EVENTS + 10; i++) many.put(new JSONObject(item).put("session", "old-" + i));
        DozeEvidence.preferences(context).edit().putString("observations_v1", many.toString()).commit();
        DozeEvidence.append(context, fixture());
        assertEquals(DozeEvidence.MAX_EVENTS, new JSONArray(DozeEvidence.preferences(context).getString("observations_v1", "[]")).length());
        String before = DozeEvidence.preferences(context).getString("observations_v1", "[]");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("disableStats", true).commit();
        DozeEvidence.append(context, fixture());
        assertEquals(before, DozeEvidence.preferences(context).getString("observations_v1", "[]"));
        DozeEvidence.clear(context);
        assertTrue(DozeEvidence.read(context).isEmpty());
    }
}
