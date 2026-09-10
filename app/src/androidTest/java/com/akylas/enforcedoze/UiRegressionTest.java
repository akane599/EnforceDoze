package com.akylas.enforcedoze;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31)
public class UiRegressionTest {
    @Test public void pickerLoadsWhileCommandsAreBusyAndRetainsSearch() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        CommandExecutor.submit(() -> {
            blocked.countDown();
            try { release.await(45, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            try (ActivityScenario<PackageChooserActivity> scenario = ActivityScenario.launch(PackageChooserActivity.class)) {
                scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.packageSearch)).setText("com.akylas.enforcedoze"));
                waitForResults(scenario);
                scenario.recreate();
                scenario.onActivity(activity -> assertEquals("com.akylas.enforcedoze",
                        ((EditText) activity.findViewById(R.id.packageSearch)).getText().toString()));
                waitForResults(scenario);
                TestUi.screenshot("picker_filtered");
            }
        } finally { release.countDown(); }
    }
    private void waitForResults(ActivityScenario<PackageChooserActivity> scenario) throws Exception {
        TestUi.await("App metadata waited for the device-command queue", () -> {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(activity -> {
                AppsAdapter adapter = (AppsAdapter) ((RecyclerView) activity.findViewById(R.id.packageList)).getAdapter();
                if (adapter.getItemCount() > 0) {
                    for (int i = 0; i < adapter.getItemCount(); i++)
                        assertTrue(adapter.getItem(i).getAppPackageName().contains("com.akylas.enforcedoze"));
                    ready.set(true);
                }
            });
            return ready.get();
        });
    }
    @Test public void missingRootAndUnknownModesCannotRunAnAppShell() {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean previous = prefs.getBoolean("isSuAvailable", false);
        try {
            prefs.edit().putBoolean("isSuAvailable", false).commit();
            for (String mode : new String[]{"root", "invalid-mode"}) {
                CommandResult result = CommandExecutor.run(context, mode, "printf unexpected-execution");
                assertFalse(result.success());
                assertFalse(result.output.contains("unexpected-execution"));
            }
        } finally { prefs.edit().putBoolean("isSuAvailable", previous).commit(); }
    }
    @Test public void largeTextDarkLandscapeKeepsAppRowsReachable() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var previousApps = new HashSet<>(prefs.getStringSet("dozeAppBlockList", Collections.emptySet()));
        String previousScale = TestUi.shell("settings get system font_scale");
        int previousNight = AppCompatDelegate.getDefaultNightMode();
        try {
            prefs.edit().putStringSet("dozeAppBlockList", Collections.singleton("org.example.testapp")).commit();
            TestUi.shell("settings put system font_scale 2.0");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            TestUi.await("Font scaling did not update", () -> context.getResources().getConfiguration().fontScale > 1.9f);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                TestUi.screenshot("dashboard_large_dark");
            }
            try (ActivityScenario<BlockAppsActivity> scenario = ActivityScenario.launch(BlockAppsActivity.class)) {
                scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                TestUi.await("Landscape app list did not load", () -> {
                    AtomicBoolean ready = new AtomicBoolean();
                    scenario.onActivity(activity -> {
                        RecyclerView list = activity.findViewById(R.id.packageList);
                        ready.set(activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                                && list.getAdapter().getItemCount() == 2 && list.getHeight() > 0);
                        if (ready.get()) list.scrollToPosition(1);
                    });
                    return ready.get();
                });
                TestUi.await("Large-text row is not reachable", () -> {
                    AtomicBoolean visible = new AtomicBoolean();
                    scenario.onActivity(activity -> {
                        RecyclerView list = activity.findViewById(R.id.packageList);
                        RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(1);
                        Rect area = new Rect();
                        visible.set(row != null && row.itemView.getGlobalVisibleRect(area) && area.height() > 0);
                        assertEquals(Configuration.UI_MODE_NIGHT_YES,
                                activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    });
                    return visible.get();
                });
                TestUi.screenshot("blocklist_large_dark_landscape");
            }
        } finally {
            prefs.edit().putStringSet("dozeAppBlockList", previousApps).commit();
            TestUi.shell(previousScale.equals("null") ? "settings delete system font_scale"
                    : "settings put system font_scale " + CommandPolicy.quote(previousScale));
            float restoredScale = previousScale.equals("null") ? 1f : Float.parseFloat(previousScale);
            TestUi.await("Font scaling did not restore", () ->
                    Math.abs(context.getResources().getConfiguration().fontScale - restoredScale) < 0.01f);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(previousNight));
        }
    }
}
