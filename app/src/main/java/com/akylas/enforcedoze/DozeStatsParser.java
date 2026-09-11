package com.akylas.enforcedoze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Accept old fractional battery records and skip damaged/unpaired events. */
public final class DozeStatsParser {
    public static final class Session {
        public final long start, end;
        public final Integer batteryUsed;
        Session(Event start, Event end, boolean batteryKnown) {
            this.start = start.time; this.end = end.time;
            batteryUsed = !batteryKnown ? null : Math.round(start.battery - end.battery);
        }
    }
    private static final class Event {
        final long time; final float battery; final String type;
        Event(long time, float battery, String type) { this.time = time; this.battery = battery; this.type = type; }
    }
    private static boolean isKnownEvent(String event) {
        return "ENTER".equals(event) || "EXIT".equals(event)
                || "ENTER_MAINTENANCE".equals(event) || "EXIT_MAINTENANCE".equals(event);
    }
    public static List<Session> parse(Collection<String> records) {
        List<Event> events = new ArrayList<>();
        for (String record : records) {
            if (record == null) continue;
            String[] parts = record.split(",");
            if (parts.length != 3) continue;
            try {
                long time = Long.parseLong(parts[0]); float battery = Float.parseFloat(parts[1]);
                if (time < 0 || !Float.isFinite(battery) || (battery < 0 && battery != -1) || battery > 100) continue;
                if (!isKnownEvent(parts[2])) continue;
                events.add(new Event(time, battery, parts[2]));
            } catch (NumberFormatException ignored) { }
        }
        events.sort(Comparator.comparingLong(event -> event.time));
        List<Session> sessions = new ArrayList<>(); Event start = null;
        boolean batteryKnown = false;
        float previousBattery = -1;
        for (Event event : events) {
            if (event.type.equals("ENTER")) {
                start = event;
                previousBattery = event.battery;
                batteryKnown = event.battery >= 0;
            } else if (start != null) {
                // A charge or unavailable intermediate reading makes a net drop misleading,
                // even if the session's final level is lower than its initial level.
                batteryKnown &= event.battery >= 0 && event.battery <= previousBattery;
                previousBattery = event.battery;
                if (event.type.equals("EXIT")) {
                    if (event.time > start.time) sessions.add(new Session(start, event, batteryKnown));
                    start = null;
                }
            }
        }
        java.util.Collections.reverse(sessions);
        return sessions;
    }
}
