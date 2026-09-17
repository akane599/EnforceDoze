package com.akylas.enforcedoze;

import static org.junit.Assert.*;

import android.app.NotificationManager;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Requires API 36, shell-started Shizuku and tools/prepare-shizuku.sh on a disposable emulator.
 * Missing prerequisites FAIL explicitly; this suite never silently skips compatibility failures.
 */
@RunWith(AndroidJUnit4.class)
public class ShizukuIntegrationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private SharedPreferences prefs;

    private interface Check {
        boolean done() throws Exception;
    }

    private void await(String message, Check check) throws Exception {
        long until = SystemClock.elapsedRealtime() + 90000;
        do {
            if (check.done()) return;
            SystemClock.sleep(500);
        } while (SystemClock.elapsedRealtime() < until);
        fail(message + "\n" + new EvidenceStore(context).text());
    }

    private <T> T device(Callable<T> task) throws Exception {
        return AccessExecutor.SERIAL.submit(task).get(45, TimeUnit.SECONDS);
    }

    private String read(String query, String kind) throws Exception {
        return device(() -> new DeviceController(context).observe(query, kind));
    }

    @Before
    public void setup() throws Exception {
        assertEquals("This suite targets Android 16", 36, Build.VERSION.SDK_INT);
        ui.wakeUp();
        ui.executeShellCommand("wm dismiss-keyguard");
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .putBoolean("serviceEnabled", false)
                .putString("executionMode", "shizuku")
                .commit();
        context.stopService(new Intent(context, ForceDozeService.class));
        device(() -> true);
        ui.executeShellCommand(
                "pm grant " + context.getPackageName() + " android.permission.READ_PHONE_STATE");
        ui.executeShellCommand(
                "pm grant " + context.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        try (ActivityScenario<AccessActivity> scenario =
                ActivityScenario.launch(AccessActivity.class)) {
            scenario.onActivity(a -> ShizukuHandler.getInstance(a).requestShizukuPermission());
            // Shizuku v13 UI. A pre-authorized installation has no dialog.
            UiObject2 allow = ui.wait(Until.findObject(By.text("Allow all the time")), 10000);
            if (allow != null) allow.click();
            await(
                    "Shizuku must be running and authorized",
                    () -> ShizukuHandler.getInstance(context).isShizukuAvailable());
            assertEquals("2000", device(() -> new AccessExecutor(context).run("id -u")).output);
            assertTrue(
                    "Any earlier interrupted test must recover",
                    device(() -> new DeviceController(context).restore()));
        }
        prefs.edit()
                .clear()
                .putString("executionMode", "shizuku")
                .putBoolean("disableMotionSensors", true)
                .putBoolean("disableWhenCharging", true)
                .commit();
        ui.executeShellCommand("dumpsys battery unplug");
        ui.executeShellCommand("dumpsys deviceidle enable all");
        new EvidenceStore(context).clear();
        new SessionStore(context).clear();
    }

    @After
    public void cleanup() throws Exception {
        prefs.edit().putBoolean("serviceEnabled", false).commit();
        ui.wakeUp();
        ui.executeShellCommand("wm dismiss-keyguard");
        Utils.stopForceDozeService(context);
        assertTrue(
                "Test must restore device changes",
                device(() -> new DeviceController(context).restore()));
        context.stopService(new Intent(context, ForceDozeService.class));
        ui.executeShellCommand("dumpsys battery reset");
    }

    private void start() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(
                    a -> {
                        prefs.edit().putBoolean("serviceEnabled", true).commit();
                        Utils.startForceDozeService(a);
                    });
        }
    }

    @Test
    public void repeatedScreenCyclesObserveDeepIdleAndRestoreWithoutRepostingMonitor()
            throws Exception {
        start();
        await("foreground notification", () -> monitorPostTime() > 0);
        long posted = monitorPostTime();
        for (int cycle = 0; cycle < 3; cycle++) {
            ui.sleep();
            await(
                    "Deep Doze while screen off",
                    () ->
                            !((PowerManager) context.getSystemService(Context.POWER_SERVICE))
                                            .isInteractive()
                                    && "IDLE".equals(read("dumpsys deviceidle", "deep")));
            await(
                    "sensor restricted mode",
                    () ->
                            "RESTRICTED : !enforcedoze:no-client!"
                                    .equals(read("dumpsys sensorservice", "sensor")));
            assertTrue(new RecoveryStore(context).pending());
            ui.wakeUp();
            ui.executeShellCommand("wm dismiss-keyguard");
            await("screen-on restoration", () -> !new RecoveryStore(context).pending());
            assertEquals("false", read("dumpsys deviceidle", "forced"));
            assertEquals("NORMAL", read("dumpsys sensorservice", "sensor"));
            assertEquals(
                    "Routine transition must not repost monitoring notification",
                    posted,
                    monitorPostTime());
        }
        assertTrue(new EvidenceStore(context).text().contains("Deep Doze observed"));
        assertTrue(new SessionStore(context).text().contains("observed at least once"));
    }

    @Test
    public void chargingAndStopDuringDelayPreventLateEntry() throws Exception {
        prefs.edit().putInt("dozeEnterDelay", 3).commit();
        start();
        ui.sleep();
        ui.executeShellCommand("dumpsys battery set ac 1");
        SystemClock.sleep(5000);
        assertEquals("false", read("dumpsys deviceidle", "forced"));
        assertFalse(new RecoveryStore(context).pending());
        ui.executeShellCommand("dumpsys battery unplug");
        prefs.edit().putBoolean("serviceEnabled", false).commit();
        Utils.stopForceDozeService(context);
        SystemClock.sleep(5000);
        assertEquals("false", read("dumpsys deviceidle", "forced"));
        assertFalse(new RecoveryStore(context).pending());
    }

    @Test
    public void sensorEventsAreSuppressedAndResumeAfterRestore() throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            SensorManager sensors =
                    (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            Sensor accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            assertNotNull("Emulator must expose an accelerometer", accelerometer);
            AtomicInteger events = new AtomicInteger();
            HandlerThread callbacks = new HandlerThread("sensor-test");
            callbacks.start();
            SensorEventListener listener =
                    new SensorEventListener() {
                        public void onSensorChanged(SensorEvent event) {
                            events.incrementAndGet();
                        }

                        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                    };
            try {
                assertTrue(
                        sensors.registerListener(
                                listener,
                                accelerometer,
                                SensorManager.SENSOR_DELAY_NORMAL,
                                new Handler(callbacks.getLooper())));
                await("Baseline accelerometer events", () -> events.get() > 3);
                assertTrue(device(() -> new DeviceController(context).restrictSensors("")));
                SystemClock.sleep(1000);
                int restricted = events.get();
                SystemClock.sleep(1500);
                assertEquals(
                        "Sensor events must stop, not just command success",
                        restricted,
                        events.get());
                assertTrue(device(() -> new DeviceController(context).restore()));
                await("Accelerometer events resume", () -> events.get() > restricted + 3);
            } finally {
                sensors.unregisterListener(listener);
                callbacks.quitSafely();
            }
        }
    }

    @Test
    public void sensorPrivacyIsSeparatelyReadBackAndRestored() throws Exception {
        String original = read("@sensor-privacy", "boolean");
        assertNotNull("Android 16 sensor privacy interface", original);
        assertTrue(
                device(
                        () ->
                                new DeviceController(context)
                                        .change(
                                                "Sensor privacy",
                                                "@sensor-privacy",
                                                "boolean",
                                                "true",
                                                "@sensor-privacy true",
                                                o -> "@sensor-privacy " + o)));
        assertEquals("true", read("@sensor-privacy", "boolean"));
        assertTrue(device(() -> new DeviceController(context).restore()));
        assertEquals(original, read("@sensor-privacy", "boolean"));
    }

    @Test
    public void binderLossRetainsUndoAndReconnectRestores() throws Exception {
        assertTrue(device(() -> new DeviceController(context).restrictSensors("")));
        String pid = ui.executeShellCommand("pidof shizuku_server").trim();
        assertTrue("Shizuku server PID", pid.matches("[0-9]+"));
        ui.executeShellCommand("kill -9 " + pid);
        await(
                "Binder death must be visible",
                () -> !ShizukuHandler.getInstance(context).isShizukuAvailable());
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            try {
                assertFalse(device(() -> new DeviceController(context).restore()));
                assertTrue(new RecoveryStore(context).pending());
                await(
                        "Recovery failure is visible",
                        () ->
                                context.getSharedPreferences("runtime", 0)
                                        .getString("status", "")
                                        .contains("Restoration pending"));
                ApplicationTest.screenshot("recovery-access-lost");
            } finally {
                String start = ui.executeShellCommand("/data/local/tmp/enforcedoze_shizuku");
                await(
                        "Shizuku reconnect after restart: " + start,
                        () -> ShizukuHandler.getInstance(context).isShizukuAvailable());
            }
            await(
                    "Service automatically restores after reconnection",
                    () -> !new RecoveryStore(context).pending());
            assertEquals("NORMAL", read("dumpsys sensorservice", "sensor"));
            ApplicationTest.screenshot("recovery-completed");
        }
    }

    private long monitorPostTime() {
        for (StatusBarNotification n :
                context.getSystemService(NotificationManager.class).getActiveNotifications())
            if (n.getId() == 1234) return n.getPostTime();
        return -1;
    }
}
