package com.akylas.enforcedoze;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MonitorLifecycleTest {
    @Test public void emptyRestorationDoesNotWaitForTheCommandQueue() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String previousMode = prefs.getString("executionMode", "shizuku");
        boolean previousEnabled = prefs.getBoolean("serviceEnabled", false);
        assertFalse("Test requires a stopped monitor", Utils.isMyServiceRunning(ForceDozeService.class, context));
        assertFalse("Test requires no pending recovery", new RecoveryJournal(context).hasPending());
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        CommandExecutor.submit(() -> {
            blocked.countDown();
            try { release.await(45, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            prefs.edit().putString("executionMode", "unavailable-test-mode").putBoolean("serviceEnabled", false).commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    prefs.edit().putBoolean("serviceEnabled", true).apply();
                    ContextCompat.startForegroundService(activity, new Intent(activity, ForceDozeService.class));
                });
                // No session and no journal means no shell work, transition wakelock,
                // or transient RESTORING state is necessary, even after duplicate events.
                TestUi.await("An empty restoration waited behind unrelated commands",
                        () -> "NEEDS_ACCESS".equals(ForceDozeService.status));
                scenario.onActivity(activity -> {
                    for (int i = 0; i < 10; i++) {
                        LocalBroadcastManager.getInstance(context).sendBroadcastSync(new Intent("reload-settings"));
                        assertEquals("NEEDS_ACCESS", ForceDozeService.status);
                    }
                    prefs.edit().putBoolean("serviceEnabled", false).apply();
                    Utils.stopForceDozeService(context);
                });
                TestUi.await("Stopping an empty monitor waited for the command queue",
                        () -> !Utils.isMyServiceRunning(ForceDozeService.class, context));
            }
        } finally {
            release.countDown();
            prefs.edit().putBoolean("serviceEnabled", false).commit();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Utils.stopForceDozeService(context));
            TestUi.await("Monitor did not stop", () -> !Utils.isMyServiceRunning(ForceDozeService.class, context));
            prefs.edit().putString("executionMode", previousMode).putBoolean("serviceEnabled", previousEnabled).commit();
        }
    }
}
