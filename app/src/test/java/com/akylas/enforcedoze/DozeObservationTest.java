package com.akylas.enforcedoze;

import org.junit.Test;
import static org.junit.Assert.*;

public class DozeObservationTest {
    private DozeObservation observation(Integer exit, String state, boolean screenBefore, boolean screenAfter,
            boolean idleBefore, boolean idleAfter, boolean uninterrupted) {
        return new DozeObservation(100, 50, 2, "session", "ENTRY", "shizuku", "test", "test",
                exit, state, "", screenBefore, screenAfter, idleBefore, idleAfter, uninterrupted);
    }
    @Test public void confirmsOnlyAgreeingSystemReadsWhileNonInteractive() {
        assertTrue(observation(0, "IDLE", false, false, true, true, true).confirmedScreenOffIdle());
        assertFalse(observation(null, "IDLE", false, false, true, true, true).confirmedScreenOffIdle());
        assertFalse(observation(1, "IDLE", false, false, true, true, true).confirmedScreenOffIdle());
    }
    @Test public void maintenanceAndUnknownStatesAreNotCountedAsDeepIdle() {
        for (String state : new String[]{"ACTIVE", "IDLE_MAINTENANCE", "UNKNOWN", "NOT_SAMPLED"})
            assertFalse(state, observation(0, state, false, false, true, true, true).confirmedScreenOffIdle());
    }
    @Test public void wakeOrStateTransitionsCannotProduceAConfirmedSample() {
        assertFalse(observation(0, "IDLE", true, false, true, true, true).confirmedScreenOffIdle());
        assertFalse(observation(0, "IDLE", false, true, true, true, true).confirmedScreenOffIdle());
        assertFalse(observation(0, "IDLE", false, false, false, true, true).confirmedScreenOffIdle());
        assertFalse(observation(0, "IDLE", false, false, true, false, true).confirmedScreenOffIdle());
        assertFalse(observation(0, "IDLE", false, false, true, true, false).confirmedScreenOffIdle());
    }
    @Test public void retainsExactSystemStateTokensWithoutPackageAllowlists() {
        String fields = DozeObservation.systemFields("Whitelist:\n  com.private.app\n"
                + "  mScreenOn=false mCharging=false\n  mState=IDLE mLightState=OVERRIDE\n  mForceIdle=true\n");
        assertEquals("mScreenOn=false\nmCharging=false\nmState=IDLE\nmLightState=OVERRIDE\nmForceIdle=true", fields);
        assertFalse(fields.contains("com.private"));
        assertEquals("", DozeObservation.systemFields("permission denied"));
    }
}
