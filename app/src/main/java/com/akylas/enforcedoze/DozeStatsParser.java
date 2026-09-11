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
        Session(Event start, Event end) {
            this.start = start.time; this.end = end.time;
            batteryUsed = start.battery < 0 || end.battery < 0 || end.battery > start.battery ? null : Math.round(start.battery - end.battery);
        }
    }
    private static final class Event {
        final long time; final float battery; final String type;
        Event(long time, float battery, String type) { this.time = time; this.battery = battery; this.type = type; }
    }
    public static List<Session> parse(Collection<String> records) {
        List<Event> events = new ArrayList<>();
        for (String record : records) {
            if (record == null) continue;
            String[] parts = record.split(",");
            if (parts.length != 3) continue;
            try {
                long time = Long.parseLong(parts[0]); float battery = Float.parseFloat(parts[1]);
                if (time < 0 || !Float.isFinite(battery) || battery < -1 || battery > 100) continue;
                events.add(new Event(time, battery, parts[2]));
            } catch (NumberFormatException ignored) { }
        }
        events.sort(Comparator.comparingLong(event -> event.time));
        List<Session> sessions = new ArrayList<>(); Event start = null;
        for (Event event : events) {
            if (event.type.equals("ENTER")) start = event;
            else if (event.type.equals("EXIT") && start != null) {
                if (event.time > start.time) sessions.add(new Session(start, event));
                start = null;
            }
        }
        java.util.Collections.reverse(sessions);
        return sessions;
    }
}
