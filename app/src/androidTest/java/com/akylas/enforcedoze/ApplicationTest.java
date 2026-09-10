package com.akylas.enforcedoze;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ApplicationTest {
    @Test public void dashboardSurvivesRecreationWithoutAccess() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("serviceEnabled", false).putString("executionMode", "shizuku").commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.dashboardToggle));
                assertTrue(activity.findViewById(R.id.dashboardSetup).isShown());
            });
            screenshot("dashboard");
            scenario.recreate();
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(R.id.dashboardDiagnostics)));
        }
    }
    @Test public void settingsAndListsOpenWithoutRootPrompts() throws Exception {
        for (Class<? extends Activity> screen : new Class[]{SettingsActivity.class, BlockAppsActivity.class,
                BlockNotificationsActivity.class, DozeBatteryStatsActivity.class, DiagnosticsActivity.class,
                PackageChooserActivity.class, DozeTunablesActivity.class}) {
            try (ActivityScenario<?> scenario = ActivityScenario.launch(screen)) {
                scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
                screenshot(screen.getSimpleName());
            }
        }
    }
    @Test public void externalAutomationRequiresOptInAndValidArguments() {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean("allowExternalAutomation", false).putBoolean("serviceEnabled", false).putInt("dozeEnterDelay", 30).commit();
        new EnableForceDozeService().onReceive(context, new android.content.Intent("com.akylas.enforcedoze.ENABLE_FORCEDOZE"));
        assertFalse(prefs.getBoolean("serviceEnabled", false));
        prefs.edit().putBoolean("allowExternalAutomation", true).commit();
        new SettingsChangeReceiver().onReceive(context, new android.content.Intent("com.akylas.enforcedoze.CHANGE_SETTING")
                .putExtra("settingName", "dozeEnterDelay").putExtra("settingValue", "-1"));
        assertEquals(30, prefs.getInt("dozeEnterDelay", 0));
        new SettingsChangeReceiver().onReceive(context, new android.content.Intent("com.akylas.enforcedoze.CHANGE_SETTING")
                .putExtra("settingName", "dozeEnterDelay").putExtra("settingValue", "60"));
        assertEquals(60, prefs.getInt("dozeEnterDelay", 0));
        prefs.edit().putBoolean("allowExternalAutomation", false).putInt("dozeEnterDelay", 0).commit();
    }
    private void screenshot(String name) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "screenshots");
        assertTrue(directory.exists() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
