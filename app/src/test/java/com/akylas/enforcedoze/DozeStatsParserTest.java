package com.akylas.enforcedoze;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class DozeStatsParserTest {
    @Test public void pairsSessionAcrossMaintenanceAndSkipsBadRecords() {
        var sessions = DozeStatsParser.parse(Arrays.asList("1000,85.0,ENTER", "2000,84.0,EXIT_MAINTENANCE", "3000,84.0,ENTER_MAINTENANCE", "4000,83.0,EXIT", "broken", "x,NaN,EXIT", null));
        assertEquals(1, sessions.size()); assertEquals(Integer.valueOf(2), sessions.get(0).batteryUsed);
        assertEquals(1000, sessions.get(0).start); assertEquals(4000, sessions.get(0).end);
    }
    @Test public void chargingAndOrphanEventsDoNotProduceFalseSavings() {
        var sessions = DozeStatsParser.parse(Arrays.asList("500,80,EXIT", "1000,-1,ENTER", "4000,90,EXIT", "5000,90,ENTER"));
        assertEquals(1, sessions.size()); assertNull(sessions.get(0).batteryUsed);
    }
    @Test public void batteryRiseInsideMaintenanceInvalidatesUsageEvenWhenFinalLevelFalls() {
        var sessions = DozeStatsParser.parse(Arrays.asList("1000,85,ENTER", "2000,84,EXIT_MAINTENANCE",
                "3000,86,ENTER_MAINTENANCE", "4000,83,EXIT"));
        assertEquals(1, sessions.size());
        assertNull(sessions.get(0).batteryUsed);
    }
    @Test public void unavailableIntermediateBatteryCannotBecomeAClaimedDrop() {
        var sessions = DozeStatsParser.parse(Arrays.asList("1000,85,ENTER", "2000,-1,EXIT_MAINTENANCE",
                "3000,84,ENTER_MAINTENANCE", "4000,83,EXIT"));
        assertEquals(1, sessions.size());
        assertNull(sessions.get(0).batteryUsed);
    }
    @Test public void invalidBatteryRangesAndUnknownRecordsCannotFabricateSessions() {
        var sessions = DozeStatsParser.parse(Arrays.asList("1000,-0.5,ENTER", "2000,80,EXIT",
                "3000,101,ENTER", "4000,80,EXIT", "5000,80,ENTER",
                "6000,100,UNRELATED", "7000,79,EXIT"));
        assertEquals(1, sessions.size());
        assertEquals(5000, sessions.get(0).start);
        assertEquals(Integer.valueOf(1), sessions.get(0).batteryUsed);
    }
}
