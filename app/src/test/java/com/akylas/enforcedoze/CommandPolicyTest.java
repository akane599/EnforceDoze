package com.akylas.enforcedoze;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandPolicyTest {
    @Test public void essentialSamsungCallPackagesCannotBeSuspended() {
        for (String name : new String[]{"com.samsung.android.dialer", "com.samsung.android.incallui", "com.samsung.android.app.telephonyui"})
            assertTrue(name, CommandPolicy.protectedPackage(name));
        assertFalse(CommandPolicy.protectedPackage("com.spotify.music"));
    }
    @Test public void launcherAndKeyboardCannotBeSuspended() {
        // Suspending either locks the owner out of their own phone until they reboot.
        for (String name : new String[]{"com.sec.android.app.launcher", "com.samsung.android.honeyboard",
                "com.google.android.apps.nexuslauncher", "com.google.android.inputmethod.latin"})
            assertTrue(name, CommandPolicy.protectedPackage(name));
    }
    @Test public void maintenanceIsNotIdle() {
        assertEquals("IDLE_MAINTENANCE", CommandPolicy.idleState("  mState=IDLE_MAINTENANCE\n mLightState=IDLE"));
        assertEquals("UNKNOWN", CommandPolicy.idleState("permission denied"));
    }
    @Test public void rejectShellInjectionAndMalformedPackages() {
        assertTrue(CommandPolicy.validPackage("com.spotify.music"));
        for (String name : new String[]{"", "a;id", "com.test $(id)", "com.test\nreboot", "-com.test", "com..test", null}) assertFalse(CommandPolicy.validPackage(name));
    }
    @Test public void periodsCrossMidnightAndHaveExclusiveEnd() {
        int[] period = CommandPolicy.period("22:00-07:00");
        assertTrue(CommandPolicy.contains(1320, period));
        assertTrue(CommandPolicy.contains(0, period));
        assertFalse(CommandPolicy.contains(420, period));
        assertNull(CommandPolicy.period("07:00-07:00"));
        assertNull(CommandPolicy.period("24:00-07:00"));
        assertNull(CommandPolicy.period("12:99-13:00"));
    }
    @Test public void quoteRoundTripsLiteralShellMetacharacters() {
        String value = "hello 'quoted' $HOME `id` $(id)\nend";
        CommandResult result = ProcessRunner.run(2000, "sh", "-c", "printf '%s' " + CommandPolicy.quote(value));
        assertTrue(result.output, result.success()); assertEquals(value, result.output);
    }
}
