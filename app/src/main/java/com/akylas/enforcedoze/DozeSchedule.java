package com.akylas.enforcedoze;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/** The union of daily periods has alarms only where eligibility actually changes. */
final class DozeSchedule {
    private DozeSchedule() { }

    static List<Integer> boundaries(Collection<String> periods) {
        List<int[]> valid = new ArrayList<>();
        for (String period : periods) {
            int[] parsed = CommandPolicy.period(period);
            if (parsed != null) valid.add(parsed);
        }
        List<Integer> boundaries = new ArrayList<>();
        for (int[] period : valid) for (int minute : period) {
            boolean before = contains(valid, (minute + 1439) % 1440);
            boolean after = contains(valid, minute);
            if (before != after && !boundaries.contains(minute)) boundaries.add(minute);
        }
        java.util.Collections.sort(boundaries);
        return boundaries;
    }

    private static boolean contains(List<int[]> periods, int minute) {
        for (int[] period : periods) if (CommandPolicy.contains(minute, period)) return true;
        return false;
    }

    static long nextBoundaryMillis(Collection<String> periods, Calendar now) {
        long next = Long.MAX_VALUE;
        for (int minute : boundaries(periods)) {
            Calendar boundary = (Calendar) now.clone();
            boundary.set(Calendar.HOUR_OF_DAY, minute / 60);
            boundary.set(Calendar.MINUTE, minute % 60);
            boundary.set(Calendar.SECOND, 0);
            boundary.set(Calendar.MILLISECOND, 0);
            if (!boundary.after(now)) boundary.add(Calendar.DAY_OF_YEAR, 1);
            next = Math.min(next, boundary.getTimeInMillis());
        }
        return next == Long.MAX_VALUE ? -1 : next;
    }
}
