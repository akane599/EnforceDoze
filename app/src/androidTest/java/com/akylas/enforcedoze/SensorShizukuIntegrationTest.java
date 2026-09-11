package com.akylas.enforcedoze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in tests that temporarily suppress actual sensor delivery on a disposable emulator. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31)
public class SensorShizukuIntegrationTest {
    private static final String INTERNAL = PrivilegedOperations.PREFIX;
    private static final String NO_SENSOR_CLIENTS = "@enforcedoze:no-sensor-clients@";
    private Context context;

    @BeforeClass public static void requireDisposableEmulator() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("shizukuIntegration")));
        assertTrue("Run privileged integration tests only on a disposable emulator",
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
    }

    @Before public void requireAuthorizedNormalSensorState() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        TestUi.await("Shizuku did not provide authorized access",
                () -> ShizukuHandler.getInstance(context).isShizukuAvailable());
        assertEquals("2000", checked("id -u").output.trim());
        assertEquals("Sensor tests require normal mode before modifying global state",
                "NORMAL", sensorServiceField("Mode"));
        assertEquals("Sensor tests require privacy to start disabled",
                "false", checked(INTERNAL + "sensors get").output.trim());
        TestUi.shell("input keyevent KEYCODE_WAKEUP");
        TestUi.await("The sensor test must remain interactive",
                () -> context.getSystemService(PowerManager.class).isInteractive());
    }

    @Test public void motionRestrictionSuppressesRealEventsAndRestoresDelivery() throws Exception {
        try (ActivityScenario<DozeEvidenceActivity> foreground = ActivityScenario.launch(DozeEvidenceActivity.class);
                SensorProbe probe = new SensorProbe(context)) {
            probe.awaitDelivery("No baseline accelerometer events");
            try {
                checked(INTERNAL + "motion restrict");
                assertEquals("RESTRICTED : " + NO_SENSOR_CLIENTS, sensorServiceField("Mode"));
                assertEquals("Motion restriction must not enable the separate privacy switch",
                        "false", checked(INTERNAL + "sensors get").output.trim());
                probe.assertDeliveryStopped();
            } finally {
                checked(INTERNAL + "motion enable " + NO_SENSOR_CLIENTS);
            }
            assertEquals("NORMAL", sensorServiceField("Mode"));
            probe.awaitDelivery("Accelerometer events did not resume after removing the restriction");
        }
    }

    @Test public void allSensorPrivacySuppressesRealEventsAndRestoresDelivery() throws Exception {
        try (ActivityScenario<DozeEvidenceActivity> foreground = ActivityScenario.launch(DozeEvidenceActivity.class);
                SensorProbe probe = new SensorProbe(context)) {
            probe.awaitDelivery("No baseline accelerometer events");
            try {
                // Android 16's shell package declares MANAGE_SENSOR_PRIVACY. A denial is a
                // genuine failure here, rather than a silently skipped capability check.
                checked(INTERNAL + "sensors true");
                assertEquals("true", checked(INTERNAL + "sensors get").output.trim());
                TestUi.await("SensorService did not apply the privacy switch",
                        () -> "enabled".equals(sensorServiceField("Sensor Privacy")));
                assertEquals("Privacy must not change the separate operating mode",
                        "NORMAL", sensorServiceField("Mode"));
                probe.assertDeliveryStopped();
            } finally {
                checked(INTERNAL + "sensors false");
            }
            assertEquals("false", checked(INTERNAL + "sensors get").output.trim());
            TestUi.await("SensorService did not restore privacy state",
                    () -> "disabled".equals(sensorServiceField("Sensor Privacy")));
            probe.awaitDelivery("Accelerometer events did not resume after disabling privacy");
        }
    }

    private CommandResult checked(String command) {
        CommandResult result = CommandExecutor.run(context, "shizuku", command);
        assertTrue(command + ": " + result.output, result.success());
        return result;
    }

    private String sensorServiceField(String name) {
        String dump = checked("dumpsys sensorservice").output;
        Matcher field = Pattern.compile("(?m)^\\s*" + Pattern.quote(name)
                + "\\s*:\\s*([^\\r\\n]+)$").matcher(dump);
        assertTrue("Missing " + name + " in SensorService output", field.find());
        return field.group(1).trim();
    }

    /** The foreground activity prevents background UID policy from creating a false positive. */
    private static final class SensorProbe implements SensorEventListener, AutoCloseable {
        private final SensorManager manager;
        private final HandlerThread thread = new HandlerThread("sensor-integration-probe");
        private final AtomicInteger count = new AtomicInteger();
        private final Semaphore events = new Semaphore(0);

        SensorProbe(Context context) {
            manager = context.getSystemService(SensorManager.class);
            assertNotNull("SensorManager is required on the CI emulator", manager);
            Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            assertNotNull("Enable the emulator accelerometer for the real delivery test", accelerometer);
            assertEquals(Sensor.REPORTING_MODE_CONTINUOUS, accelerometer.getReportingMode());
            thread.start();
            if (!manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL,
                    new Handler(thread.getLooper()))) {
                thread.quitSafely();
                fail("Could not register the accelerometer listener");
            }
        }

        @Override public void onSensorChanged(SensorEvent event) {
            count.incrementAndGet();
            events.release();
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

        void awaitDelivery(String message) throws Exception {
            int before = count.get();
            TestUi.await(message, () -> count.get() - before >= 3);
        }

        void assertDeliveryStopped() throws InterruptedException {
            // Discard events already queued before SensorService stopped the connection.
            SystemClock.sleep(250);
            events.drainPermits();
            assertFalse("Accelerometer continued delivering events while sensors were disabled",
                    events.tryAcquire(1500, TimeUnit.MILLISECONDS));
        }

        @Override public void close() throws InterruptedException {
            manager.unregisterListener(this);
            thread.quitSafely();
            thread.join(2000);
        }
    }
}
