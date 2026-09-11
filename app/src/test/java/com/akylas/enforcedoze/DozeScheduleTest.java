package com.akylas.enforcedoze;

import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class DozeScheduleTest {
    @Test public void overlappingAndAdjacentPeriodsDoNotWakeAtInternalBoundaries() {
        assertEquals(Arrays.asList(8 * 60, 12 * 60), DozeSchedule.boundaries(
                Arrays.asList("08:00-10:00", "09:00-11:00", "11:00-12:00", "09:00-11:00")));
    }
    @Test public void allDayCoverageAndInvalidPeriodsNeedNoAlarm() {
        assertEquals(Collections.emptyList(), DozeSchedule.boundaries(Arrays.asList("22:00-07:00", "07:00-22:00")));
        assertEquals(Collections.emptyList(), DozeSchedule.boundaries(Arrays.asList("bad", "24:00-04:00", "04:00-04:00")));
    }
    @Test public void overnightPeriodSchedulesNextMorningInPhoneTimezone() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));
        now.set(2026, Calendar.SEPTEMBER, 11, 23, 0, 0);
        now.set(Calendar.MILLISECOND, 0);
        assertEquals(8 * 60 * 60 * 1000L,
                DozeSchedule.nextBoundaryMillis(Collections.singletonList("22:00-07:00"), now) - now.getTimeInMillis());
    }
}
