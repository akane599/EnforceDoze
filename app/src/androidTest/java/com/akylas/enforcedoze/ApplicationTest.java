package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

/**
 * Run only on a disposable test device: these tests clear the test installation's local records.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Before
    public void setup() throws Exception {
        ui.wakeUp();
        ui.executeShellCommand("wm dismiss-keyguard");
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("serviceEnabled", false)
                .commit();
        context.stopService(new Intent(context, ForceDozeService.class));
        AccessExecutor.SERIAL.submit(() -> {}).get(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    public void corruptedJournalBlocksNewChangesAndRetainsBytes() {
        SharedPreferences prefs = context.getSharedPreferences("test_journal", 0);
        try {
            prefs.edit().putString("journal", "[broken").commit();
            RecoveryStore store = new RecoveryStore(context, "test_journal");
            assertTrue(store.pending());
            try {
                store.load();
                fail("Corrupt recovery must not become an empty journal");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("retained"));
            }
            assertEquals("[broken", prefs.getString("journal", null));
        } finally {
            prefs.edit().clear().commit();
        }
    }

    @Test
    public void evidenceIsBoundedAndClearingCannotDiscardRecovery() throws Exception {
        RecoveryStore recovery = new RecoveryStore(context, "test_journal");
        recovery.save(
                Collections.singletonList(
                        new RestorationJournal.Entry(
                                "Sensor access",
                                "shizuku",
                                "query",
                                "sensor",
                                "NORMAL",
                                "RESTRICTED",
                                "apply",
                                "undo")));
        try {
            EvidenceStore evidence = new EvidenceStore(context);
            evidence.clear();
            for (int i = 0; i < 205; i++) evidence.record("test " + i, "observation");
            JSONArray records =
                    new JSONArray(
                            context.getSharedPreferences("evidence", 0).getString("events", "[]"));
            assertEquals(200, records.length());
            assertEquals("test 5", records.getJSONObject(0).getString("event"));
            evidence.clear();
            assertTrue(recovery.pending());
            assertEquals(
                    "NORMAL", new RecoveryStore(context, "test_journal").load().get(0).original);
        } finally {
            context.getSharedPreferences("test_journal", 0).edit().clear().commit();
        }
    }

    @Test
    public void interruptedAndChargingHistoryNeverInventsDrainOrDuration() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("disableStats", false)
                .commit();
        SessionStore sessions = new SessionStore(context);
        sessions.clear();
        sessions.finish("no entry", false);
        assertEquals("[]", context.getSharedPreferences("sessions", 0).getString("history", "[]"));
        sessions.begin();
        sessions.observe(true);
        sessions.finish("restart", true);
        sessions.begin();
        sessions.charging();
        sessions.finish("power connected", false);
        JSONArray history =
                new JSONArray(
                        context.getSharedPreferences("sessions", 0).getString("history", "[]"));
        assertEquals(2, history.length());
        assertEquals(-1, history.getJSONObject(0).getLong("duration"));
        assertTrue(history.getJSONObject(0).getBoolean("observed"));
        assertEquals(-1, history.getJSONObject(0).getInt("drop"));
        assertEquals(-1, history.getJSONObject(1).getInt("drop"));
        sessions.clear();
    }

    @Test
    public void automationRejectsWrongTokenAndMalformedSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .putBoolean("allowAutomation", true)
                .putString("automationToken", "expected")
                .putInt("dozeEnterDelay", 17)
                .commit();
        Intent bad =
                new Intent()
                        .putExtra("automationToken", "wrong")
                        .putExtra("settingName", "dozeEnterDelay")
                        .putExtra("settingValue", "10");
        new SettingsChangeReceiver().onReceive(context, bad);
        assertEquals(17, prefs.getInt("dozeEnterDelay", 0));
        bad.putExtra("automationToken", "expected").putExtra("settingValue", "not a number");
        new SettingsChangeReceiver().onReceive(context, bad);
        assertEquals(17, prefs.getInt("dozeEnterDelay", 0));
        prefs.edit()
                .remove("automationToken")
                .putBoolean("allowAutomation", false)
                .remove("dozeEnterDelay")
                .commit();
    }

    @Test
    public void dashboardAndFeatureScreensLaunchAndCapture() {
        Class<?>[] screens = {
            MainActivity.class,
            AccessActivity.class,
            SettingsActivity.class,
            LogActivity.class,
            DozeBatteryStatsActivity.class,
            DozeStatsActivity.class,
            BlockAppsActivity.class,
            BlockNotificationsActivity.class,
            WhitelistAppsActivity.class,
            PackageChooserActivity.class,
            DozeTunablesActivity.class,
            TaskerBroadcastsActivity.class,
            CompatibilityActivity.class,
            AboutAppActivity.class
        };
        for (Class<?> screen : screens) {
            try (ActivityScenario<?> scenario =
                    ActivityScenario.launch(new Intent(context, screen))) {
                scenario.onActivity(
                        activity -> {
                            assertNotNull(activity.findViewById(android.R.id.content));
                            assertTrue(activity.findViewById(android.R.id.content).isShown());
                        });
                ui.waitForIdle();
                screenshot(screen.getSimpleName());
            }
        }
    }

    @Test
    public void largeFontDashboardCommonActionsRemainReachable() throws Exception {
        String original = ui.executeShellCommand("settings get system font_scale").trim();
        try {
            ui.executeShellCommand("settings put system font_scale 2.0");
            try (ActivityScenario<MainActivity> scenario =
                    ActivityScenario.launch(MainActivity.class)) {
                ui.waitForIdle();
                scenario.onActivity(
                        a -> {
                            assertTrue(a.getResources().getConfiguration().fontScale >= 1.9f);
                            TextView toggle = a.findViewById(R.id.monitor_toggle);
                            assertTrue(toggle.getHeight() > 0);
                        });
                assertTrue(
                        new UiScrollable(new UiSelector().scrollable(true))
                                .scrollTextIntoView("Android & Samsung guidance"));
                assertTrue(ui.hasObject(By.text("Android & Samsung guidance")));
                screenshot("dashboard-large-font-bottom");
            }
        } finally {
            ui.executeShellCommand(
                    original.equals("null")
                            ? "settings delete system font_scale"
                            : "settings put system font_scale " + original);
        }
    }

    @Test
    public void themesAndLandscapeKeepNavigationReachable() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String original = prefs.getString("theme", "system");
        try {
            for (String theme : new String[] {"light", "dark"}) {
                prefs.edit().putString("theme", theme).commit();
                InstrumentationRegistry.getInstrumentation()
                        .runOnMainSync(() -> MyApplication.applyTheme(context));
                for (boolean landscape : new boolean[] {false, true}) {
                    if (landscape) ui.setOrientationLeft();
                    else ui.setOrientationNatural();
                    try (ActivityScenario<MainActivity> scenario =
                            ActivityScenario.launch(MainActivity.class)) {
                        ui.waitForIdle();
                        scenario.onActivity(
                                a -> {
                                    android.content.res.Configuration config =
                                            a.getResources().getConfiguration();
                                    assertEquals(
                                            theme.equals("dark") ? 32 : 16, config.uiMode & 48);
                                    assertEquals(landscape ? 2 : 1, config.orientation);
                                });
                        screenshot("dashboard-" + theme + (landscape ? "-landscape" : "-portrait"));
                        assertTrue(
                                new UiScrollable(new UiSelector().scrollable(true))
                                        .scrollTextIntoView("Android & Samsung guidance"));
                        ui.findObject(By.text("Android & Samsung guidance")).click();
                        assertTrue(ui.wait(Until.hasObject(By.text("Android & Samsung")), 10000));
                        screenshot("guidance-" + theme + (landscape ? "-landscape" : "-portrait"));
                        ui.pressBack();
                    }
                }
            }
        } finally {
            ui.unfreezeRotation();
            prefs.edit().putString("theme", original).commit();
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> MyApplication.applyTheme(context));
        }
    }

    static void screenshot(String name) {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(c.getExternalFilesDir(null), "screenshots");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        assertTrue(
                "Screenshot " + name,
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .takeScreenshot(new File(directory, name + ".png")));
    }
}
