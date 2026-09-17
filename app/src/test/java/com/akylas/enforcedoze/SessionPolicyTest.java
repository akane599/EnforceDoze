package com.akylas.enforcedoze;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionPolicyTest {
    @Test public void midnightPeriodIncludesStartExcludesEnd() {
        assertTrue(ScheduleRules.contains("22:00-07:00",1320));
        assertTrue(ScheduleRules.contains("22:00-07:00",0));
        assertFalse(ScheduleRules.contains("22:00-07:00",420));
        assertFalse(ScheduleRules.contains("22:00-07:00",720));
    }
    @Test public void malformedPeriodsAreRejected() {
        for(String s:new String[]{null,"","-1:00-23:00","12:99-22:00","00:00-00:00","text"}) assertNull(ScheduleRules.parse(s));
    }
    @Test public void everyEntryGateAppliesToStartupAndDelay() {
        assertTrue(ScheduleRules.mayEnter(true,false,true,false,true,false));
        assertFalse(ScheduleRules.mayEnter(false,false,true,false,true,false));
        assertFalse(ScheduleRules.mayEnter(true,true,true,false,true,false));
        assertFalse(ScheduleRules.mayEnter(true,false,false,false,true,false));
        assertFalse(ScheduleRules.mayEnter(true,false,true,true,true,false));
        assertFalse(ScheduleRules.mayEnter(true,false,true,false,true,true));
        assertTrue(ScheduleRules.mayEnter(true,false,true,true,false,false));
    }
    @Test public void chargingMissingAndInterruptedReadingsNeverBecomeConsumption() {
        assertFalse(SessionStore.validDrop(0,90,true,false));
        assertFalse(SessionStore.validDrop(90,89,false,true));
        assertFalse(SessionStore.validDrop(-1,89,false,false));
        assertFalse(SessionStore.validDrop(80,81,false,false));
        assertTrue(SessionStore.validDrop(80,78,false,false));
    }
}
