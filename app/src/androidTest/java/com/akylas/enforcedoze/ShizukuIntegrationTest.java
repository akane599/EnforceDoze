package com.akylas.enforcedoze;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Explicit opt-in only: these tests control a disposable emulator's Shizuku server and Doze state. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31)
public class ShizukuIntegrationTest {
    private Context context;
    private ShizukuHandler shizuku;

    @BeforeClass public static void requireDisposableEmulator() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("shizukuIntegration")));
        assertTrue("Run privileged integration tests only on a disposable emulator",
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
    }
    @Before public void connect() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        shizuku = ShizukuHandler.getInstance(context);
        TestUi.await("Shizuku did not provide authorized access", shizuku::isShizukuAvailable);
        checked("id -u");
    }
    private CommandResult checked(String command) {
        CommandResult result = CommandExecutor.run(context, "shizuku", command);
        assertTrue(command + ": " + result.output, result.success());
        return result;
    }
    @Test public void shellIdentityAndNamedSystemReadsWork() {
        assertEquals("2000", checked("id -u").output.trim());
        assertNotEquals("2000", ProcessRunner.run(2000, "/system/bin/sh", "-c", "id -u").output.trim());
        assertEquals("11", checked(PrivilegedOperations.PREFIX + "hotspot get").output.trim());
        String sensors = checked(PrivilegedOperations.PREFIX + "sensors get").output.trim();
        assertTrue(sensors, sensors.equals("true") || sensors.equals("false"));
        String notifications = checked(PrivilegedOperations.PREFIX + "notifications get "
                + context.getPackageName() + " " + context.getApplicationInfo().uid).output.trim();
        assertTrue(notifications, notifications.equals("true") || notifications.equals("false"));
        CommandResult failure = CommandExecutor.run(context, "shizuku", "printf expected-failure; exit 7");
        assertEquals(7, failure.exitCode);
        assertEquals("expected-failure", failure.output);
    }
    @Test public void realSettingsMutationRestoresItsOriginalValue() {
        String key = "enforcedoze_ci_recovery";
        String original = checked("settings get global " + key).output.trim();
        String undo = original.equals("null") ? "settings delete global " + key
                : "settings put global " + key + " " + CommandPolicy.quote(original);
        RecoveryJournal journal = new RecoveryJournal(context);
        assertFalse("Unexpected pending recovery before test", journal.hasPending());
        try {
            assertTrue(journal.apply("shizuku", "settings put global " + key + " ci-test", undo));
            assertEquals("ci-test", checked("settings get global " + key).output.trim());
            assertTrue(new RecoveryJournal(context).hasPending());
            assertTrue(new RecoveryJournal(context).restore());
            assertEquals(original, checked("settings get global " + key).output.trim());
            assertFalse(journal.hasPending());
        } finally { checked(undo); journal.restore(); }
    }
    @Test public void forceIdleIsVerifiedAndUnforcedThroughTheJournal() {
        String pkg = context.getPackageName();
        boolean exempt = context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(pkg);
        RecoveryJournal journal = new RecoveryJournal(context);
        assertFalse(journal.hasPending());
        try {
            if (!exempt) checked("dumpsys deviceidle whitelist +" + pkg);
            assertTrue(journal.applyCore("shizuku", "dumpsys deviceidle force-idle deep", "dumpsys deviceidle unforce"));
            assertEquals("IDLE", checked("dumpsys deviceidle get deep").output.trim());
            assertTrue(journal.restore());
            assertNotEquals("IDLE", checked("dumpsys deviceidle get deep").output.trim());
            assertFalse(journal.hasPending());
        } finally {
            checked("dumpsys deviceidle unforce");
            journal.restore();
            if (!exempt) checked("dumpsys deviceidle whitelist -" + pkg);
        }
    }
    @Test public void serverDisconnectIsReportedAndAccessReconnects() throws Exception {
        CountDownLatch lost = new CountDownLatch(1);
        ShizukuHandler.OnAvailibilityChange listener = available -> { if (!available) lost.countDown(); };
        shizuku.checkShizukuAvailability();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        shizuku.addAvailabilityListener(listener);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean rootWasAvailable = prefs.getBoolean("isSuAvailable", false);
        String starter = InstrumentationRegistry.getArguments().getString("shizukuStarter");
        assertNotNull("The CI starter path is required", starter);
        try {
            prefs.edit().putBoolean("isSuAvailable", true).commit();
            String pids = TestUi.shell("pidof shizuku_server");
            assertTrue("Shizuku server PID unavailable", pids.matches("[0-9 ]+"));
            TestUi.shell("kill " + pids);
            assertTrue("Binder death was not reported", lost.await(5, TimeUnit.SECONDS));
            CommandResult result = CommandExecutor.run(context, "shizuku", "id -u");
            assertFalse("Disconnected Shizuku must not fall back to another backend", result.success());
        } finally {
            prefs.edit().putBoolean("isSuAvailable", rootWasAvailable).commit();
            shizuku.removeAvailabilityListener(listener);
            TestUi.shell(CommandPolicy.quote(starter));
        }
        TestUi.await("Access did not recover after Shizuku restart", shizuku::isShizukuAvailable);
        assertEquals("2000", checked("id -u").output.trim());
    }
    @Test public void foregroundMonitorEntersOnScreenOffAndRestoresOnWake() throws Exception {
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean previousCharging = prefs.getBoolean("disableWhenCharging", true);
        boolean previousSensors = prefs.getBoolean("disableMotionSensors", true);
        int previousDelay = prefs.getInt("dozeEnterDelay", 0);
        boolean previousLockTimeout = prefs.getBoolean("ignoreLockscreenTimeout", true);
        boolean previousUnlock = prefs.getBoolean("waitForUnlock", false);
        boolean previousStats = prefs.getBoolean("disableStats", false);
        DozeEvidence.clear(context);
        try {
            prefs.edit().putString("executionMode", "shizuku").putBoolean("serviceEnabled", true)
                    .putBoolean("disableWhenCharging", false).putBoolean("disableMotionSensors", false)
                    .putInt("dozeEnterDelay", 0).putBoolean("ignoreLockscreenTimeout", true)
                    .putBoolean("waitForUnlock", false).putBoolean("disableStats", false).commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                TestUi.await("Foreground monitor did not start", () -> "WAITING".equals(ForceDozeService.status));
                TestUi.screenshot("dashboard_shizuku_ready");
                TestUi.shell("input keyevent KEYCODE_SLEEP");
                TestUi.await("Screen-off did not enter Doze: " + ForceDozeService.status,
                        () -> "ACTIVE".equals(ForceDozeService.status));
                assertEquals("IDLE", checked("dumpsys deviceidle get deep").output.trim());
                TestUi.await("No saved screen-off confirmation", () -> DozeEvidence.read(context).stream()
                        .anyMatch(o -> o.event.equals("ENTRY") && o.confirmedScreenOffIdle() && o.raw.contains("mState=IDLE")));
                TestUi.shell("input keyevent KEYCODE_WAKEUP");
                TestUi.await("Wake did not restore the session", () -> "WAITING".equals(ForceDozeService.status)
                        && !new RecoveryJournal(context).hasPending());
                assertNotEquals("IDLE", checked("dumpsys deviceidle get deep").output.trim());
                TestUi.await("Restoration observation missing", () -> DozeEvidence.read(context).stream()
                        .anyMatch(o -> o.event.equals("RESTORED") && o.interactiveAfter && !o.idleAfter));
                try (ActivityScenario<DozeEvidenceActivity> evidence = ActivityScenario.launch(DozeEvidenceActivity.class)) {
                    evidence.recreate();
                    evidence.onActivity(activity -> {
                        var records = DozeEvidence.read(activity);
                        int entry = -1;
                        for (int i = 0; i < records.size(); i++) if (records.get(i).event.equals("ENTRY") && records.get(i).confirmedScreenOffIdle()) entry = i;
                        assertTrue(entry >= 0);
                        var list = (androidx.recyclerview.widget.RecyclerView) activity.findViewById(R.id.evidenceList);
                        ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(records.size() - entry, 0);
                    });
                    TestUi.screenshot("doze_evidence_verified");
                }
            }
        } finally {
            prefs.edit().putBoolean("serviceEnabled", false).putBoolean("disableWhenCharging", previousCharging)
                    .putBoolean("disableMotionSensors", previousSensors).putInt("dozeEnterDelay", previousDelay)
                    .putBoolean("ignoreLockscreenTimeout", previousLockTimeout).putBoolean("waitForUnlock", previousUnlock)
                    .putBoolean("disableStats", previousStats).commit();
            TestUi.shell("input keyevent KEYCODE_WAKEUP");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Utils.stopForceDozeService(context));
            TestUi.await("Monitor did not stop", () -> !Utils.isMyServiceRunning(ForceDozeService.class, context));
            checked("dumpsys deviceidle unforce");
        }
    }
}
