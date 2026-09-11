package com.akylas.enforcedoze;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A bounded observation, never an inferred duration of continuous idle or CPU sleep. */
final class DozeObservation {
    private static final Pattern FIELD = Pattern.compile("\\b(?:mState|mLightState|mScreenOn|mCharging|mForceIdle)=[A-Za-z_]+");
    final long time, elapsed, readMillis;
    final String session, event, mode, device, version, state, raw;
    final Integer exitCode;
    final boolean interactiveBefore, interactiveAfter, idleBefore, idleAfter, uninterrupted;

    DozeObservation(long time, long elapsed, long readMillis, String session, String event, String mode,
            String device, String version, Integer exitCode, String state, String raw,
            boolean interactiveBefore, boolean interactiveAfter, boolean idleBefore,
            boolean idleAfter, boolean uninterrupted) {
        this.time = time; this.elapsed = elapsed; this.readMillis = readMillis;
        this.session = session; this.event = event; this.mode = mode; this.device = device;
        this.version = version; this.exitCode = exitCode; this.state = state; this.raw = raw;
        this.interactiveBefore = interactiveBefore; this.interactiveAfter = interactiveAfter;
        this.idleBefore = idleBefore; this.idleAfter = idleAfter; this.uninterrupted = uninterrupted;
    }

    boolean confirmedScreenOffIdle() {
        return exitCode != null && exitCode == 0 && "IDLE".equals(state)
                && idleBefore && idleAfter && !interactiveBefore && !interactiveAfter && uninterrupted;
    }

    static String systemFields(String dump) {
        // Keep exact state tokens, excluding package allowlists and unrelated device details.
        StringBuilder text = new StringBuilder();
        Matcher matcher = FIELD.matcher(dump == null ? "" : dump);
        while (matcher.find() && text.length() < 512) {
            if (text.length() > 0) text.append('\n');
            text.append(matcher.group());
        }
        return text.toString();
    }
}
