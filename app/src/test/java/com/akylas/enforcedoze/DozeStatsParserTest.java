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
}
