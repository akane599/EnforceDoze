package com.akylas.enforcedoze;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.Assert.*;

/** Called only by ShizukuIntegrationTest after its disposable-emulator gate. */
final class MonitorLockscreenScenario {
    private static final String PIN = "471963";
    private static final String[] KEYS = {"executionMode", "serviceEnabled", "disableWhenCharging",
            "disableMotionSensors", "turnOffAllSensorsInDoze", "sensorWhitelistPackage", "dozeEnterDelay",
            "ignoreLockscreenTimeout", "waitForUnlock", "disableStats"};

    static void verify(Context context, Function<String, CommandResult> checked) throws Exception {
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> previous = prefs.getAll();
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        assertFalse("Disposable emulator must begin without a credential", keyguard.isDeviceSecure());
        assertFalse(new RecoveryJournal(context).hasPending());
        assertEquals("NORMAL", motionMode(checked));
        CountDownLatch release = new CountDownLatch(1);
        try {
            prefs.edit().putString("executionMode", "shizuku").putBoolean("serviceEnabled", true)
                    .putBoolean("disableWhenCharging", false).putBoolean("disableMotionSensors", true)
                    .putBoolean("turnOffAllSensorsInDoze", false).putString("sensorWhitelistPackage", "")
                    .putInt("dozeEnterDelay", 0).putBoolean("ignoreLockscreenTimeout", true)
                    .putBoolean("waitForUnlock", true).putBoolean("disableStats", false).commit();
            try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
                TestUi.await("Monitor did not start", () -> "WAITING".equals(ForceDozeService.status));
                TestUi.shell("locksettings set-pin " + PIN);
                TestUi.await("PIN lock was not enabled", keyguard::isDeviceSecure);
                TestUi.shell("input keyevent KEYCODE_SLEEP");
                TestUi.await("First screen-off session did not activate", () -> "ACTIVE".equals(ForceDozeService.status));
                String first = latestConfirmedSession(context);
                assertNotNull(first);
                assertEquals("RESTRICTED", motionMode(checked));

                // A complete lockscreen wake must restore enhancements for unlocking,
                // then a resleep without USER_PRESENT must apply them in a fresh session.
                TestUi.shell("input keyevent KEYCODE_WAKEUP");
                TestUi.await("Lockscreen wake did not restore enhancements", () -> "LOCKED".equals(ForceDozeService.status));
                assertTrue(keyguard.isDeviceLocked());
                assertEquals("NORMAL", motionMode(checked));
                TestUi.shell("input keyevent KEYCODE_SLEEP");
                awaitNewSession(context, first);
                String second = latestConfirmedSession(context);
                assertEquals("RESTRICTED", motionMode(checked));

                // Also cover resleep while unlock restoration is still queued. Its stale
                // callback must never overwrite the restarted session with LOCKED.
                CountDownLatch blocked = new CountDownLatch(1);
                CommandExecutor.submit(() -> {
                    blocked.countDown();
                    try { release.await(45, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                assertTrue(blocked.await(5, TimeUnit.SECONDS));
                TestUi.shell("input keyevent KEYCODE_WAKEUP");
                TestUi.await("Unlock restoration was not queued", () -> "RESTORING".equals(ForceDozeService.status));
                assertTrue(keyguard.isDeviceLocked());
                TestUi.shell("input keyevent KEYCODE_SLEEP");
                TestUi.await("Resleep did not turn the screen off", () -> !power.isInteractive());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                release.countDown();
                awaitNewSession(context, second);
                assertEquals("RESTRICTED", motionMode(checked));
                assertEquals("ACTIVE", ForceDozeService.status);
            }
        } finally {
            release.countDown();
            prefs.edit().putBoolean("serviceEnabled", false).commit();
            try {
                // Always remove the temporary credential, including after an assertion.
                if (keyguard.isDeviceSecure()) TestUi.shell("locksettings clear --old " + PIN);
                TestUi.await("Temporary PIN was not removed", () -> !keyguard.isDeviceSecure());
            } finally {
                try {
                    TestUi.shell("input keyevent KEYCODE_WAKEUP");
                    TestUi.shell("wm dismiss-keyguard");
                } finally {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Utils.stopForceDozeService(context));
                    TestUi.await("Monitor did not stop", () -> !Utils.isMyServiceRunning(ForceDozeService.class, context));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    // onDestroy can enqueue one final restoration. Serialize cleanup behind
                    // it, and retain the test backend/preferences until all undo work finishes.
                    CountDownLatch cleaned = new CountDownLatch(1);
                    AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
                    CommandExecutor.submit(() -> {
                        try {
                            try { checked.apply("dumpsys deviceidle unforce"); }
                            finally { assertTrue("Pending test changes did not restore", new RecoveryJournal(context).restore()); }
                        } catch (Throwable failure) { cleanupFailure.set(failure); }
                        finally { cleaned.countDown(); }
                    });
                    assertTrue("Device cleanup did not complete", cleaned.await(30, TimeUnit.SECONDS));
                    var editor = prefs.edit();
                    for (String key : KEYS) {
                        Object value = previous.get(key);
                        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                        else if (value instanceof String) editor.putString(key, (String) value);
                        else editor.remove(key);
                    }
                    editor.commit();
                    if (cleanupFailure.get() != null) throw new AssertionError("Device cleanup failed", cleanupFailure.get());
                }
            }
        }
    }

    private static String motionMode(Function<String, CommandResult> checked) {
        return SensorRestriction.parse(checked.apply(PrivilegedOperations.PREFIX + "motion get").output).mode;
    }

    private static String latestConfirmedSession(Context context) {
        var observations = DozeEvidence.read(context);
        for (int i = observations.size() - 1; i >= 0; i--) {
            DozeObservation observation = observations.get(i);
            if (observation.event.equals("ENTRY") && observation.confirmedScreenOffIdle()) return observation.session;
        }
        return null;
    }

    private static void awaitNewSession(Context context, String previous) throws Exception {
        TestUi.await("Resleep did not start a fresh verified session", () -> {
            String next = latestConfirmedSession(context);
            return "ACTIVE".equals(ForceDozeService.status) && next != null && !next.equals(previous);
        });
    }
}
