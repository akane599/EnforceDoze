package com.akylas.enforcedoze;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DozeTunablePolicyTest {
    @Test public void android16CommandsUseNamespaceAndCurrentNames() {
        assertEquals(Arrays.asList("device_config put device_idle idle_to 3600000",
                        "device_config put device_idle notification_allowlist_duration_ms 30000"),
                DozeTunablePolicy.modernCommands("idle_to=3600000,light_pre_idle_to=600000,notification_whitelist_duration=30000"));
        assertEquals("max_temp_app_allowlist_duration_ms", DozeTunablePolicy.modernKey("max_temp_app_whitelist_duration"));
        assertEquals("mms_temp_app_allowlist_duration_ms", DozeTunablePolicy.modernKey("mms_temp_app_whitelist_duration"));
        assertEquals("sms_temp_app_allowlist_duration_ms", DozeTunablePolicy.modernKey("sms_temp_app_whitelist_duration"));
    }

    @Test public void rejectsValuesThatAndroidWouldClampOrCannotParse() {
        assertFalse(DozeTunablePolicy.validValue("idle_factor", "0.1"));
        assertFalse(DozeTunablePolicy.validValue("idle_factor", "NaN"));
        assertFalse(DozeTunablePolicy.validValue("idle_factor", "Infinity"));
        assertFalse(DozeTunablePolicy.validValue("idle_to", "1.5"));
        assertFalse(DozeTunablePolicy.validValue("idle_to", "-1"));
        assertTrue(DozeTunablePolicy.validValue("idle_factor", "1.5"));
        assertTrue(DozeTunablePolicy.validValue("location_accuracy", "0.5"));
        assertTrue(DozeTunablePolicy.validValue("idle_to", "0"));
    }

    @Test(expected = IllegalArgumentException.class) public void doesNotExportShellInput() {
        DozeTunablePolicy.modernCommands("idle_to=10;id");
    }
}
