// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What one night is (SPEC 7.2 N, 8.5; ADR 0031). Unplugged screen-off sessions count as one night when every
 * screen-on gap between them is shorter than [JOIN_GAP]: a glance at the clock or a trip to the kitchen does not
 * split the night in two. The night begins with its first session, which starts between [STARTS_FROM] and
 * [STARTS_UNTIL]; the sessions it joins may start later, since a night runs past 03:00. It needs [MIN_SCREEN_OFF]
 * of screen-off time in all, the gaps not counted. The recommender's N signal and the night report both use it.
 */
public object Nights {
    /** The earliest a night's first session may start. */
    public val STARTS_FROM: LocalTime = LocalTime.of(21, 0)

    /** A first session from here on belongs to the day. */
    public val STARTS_UNTIL: LocalTime = LocalTime.of(3, 0)

    /** Screen-off time a night needs, summed over its sessions (SPEC 7.2 N, 8.5). */
    public val MIN_SCREEN_OFF: Duration = Duration.ofHours(4)

    /** A screen-on gap shorter than this joins the sessions on either side into one night (ADR 0031). */
    public val JOIN_GAP: Duration = Duration.ofMinutes(10)

    /**
     * One screen-off session as the rule sees it. [unplugged] is true only when the session had no charger at all;
     * a plugged session, or one whose plug state is unknown, is never part of a night and breaks a run.
     */
    public data class Stretch(val start: Instant, val end: Instant, val unplugged: Boolean)

    /** A screen-on moment between two sessions of one night: the short wake-ups the report's strip marks. */
    public data class Wake(val start: Instant, val end: Instant)

    /**
     * The rule's numbers. Only the compressed-night test (SPEC 13.2) changes them: it scales the night down to
     * minutes and drops the clock window, so a test night can run at any hour (ADR 0031).
     */
    public data class Rule(val minScreenOff: Duration = MIN_SCREEN_OFF, val gap: Duration = JOIN_GAP, val window: Boolean = true) {
        public companion object {
            /** The whole rule scaled so a night needs [minScreenOff]: the join gap shrinks by the same factor. */
            public fun compressed(minScreenOff: Duration): Rule = Rule(
                minScreenOff,
                Duration.ofMillis(JOIN_GAP.toMillis() * minScreenOff.toMillis() / MIN_SCREEN_OFF.toMillis()),
                window = false,
            )
        }
    }

    /**
     * Runs of unplugged sessions in start order, each joined to the one before it when the screen was on for less
     * than [gap] in between. [sessions] are every screen-off session, so a plugged or unknown session between two
     * unplugged ones ends the run, and so does a gap of [gap] or more. [charged] says the phone charged during the
     * screen-on gap between two sessions (the battery rose), which ends the run too: no session saw the charger.
     */
    public fun <T> runs(
        sessions: List<T>,
        gap: Duration = JOIN_GAP,
        charged: (before: T, after: T) -> Boolean = { _, _ -> false },
        stretch: (T) -> Stretch,
    ): List<List<T>> {
        val runs = mutableListOf<MutableList<T>>()
        var open: MutableList<T>? = null
        for (session in sessions.sortedBy { stretch(it).start }) {
            val s = stretch(session)
            val before = open?.last()
            val joins = before != null && Duration.between(stretch(before).end, s.start) < gap && !charged(before, session)
            open = when {
                !s.unplugged -> null
                joins -> open?.apply { add(session) }
                else -> mutableListOf(session).also(runs::add)
            }
        }
        return runs
    }

    /** True when [start] falls where a night may begin, read on the clock in [zone]. */
    public fun startsInWindow(start: Instant, zone: ZoneId): Boolean {
        val time = start.atZone(zone).toLocalTime()
        return time >= STARTS_FROM || time < STARTS_UNTIL
    }

    /** The runs of [sessions] that are nights: the first session starts in the window, with enough screen off in all. */
    public fun <T> find(
        sessions: List<T>,
        zone: ZoneId,
        rule: Rule = Rule(),
        charged: (before: T, after: T) -> Boolean = { _, _ -> false },
        stretch: (T) -> Stretch,
    ): List<List<T>> = runs(sessions, rule.gap, charged, stretch).filter { run ->
        (!rule.window || startsInWindow(stretch(run.first()).start, zone)) && screenOff(run, stretch) >= rule.minScreenOff
    }

    /** Screen-off time of a run; the screen-on gaps between its sessions do not count. */
    public fun <T> screenOff(run: List<T>, stretch: (T) -> Stretch): Duration =
        run.fold(Duration.ZERO) { sum, session -> stretch(session).let { sum + Duration.between(it.start, it.end) } }

    /** The screen-on gaps inside a run, in order. */
    public fun <T> wakes(run: List<T>, stretch: (T) -> Stretch): List<Wake> =
        run.map(stretch).zipWithNext().filter { (a, b) -> b.start > a.end }.map { (a, b) -> Wake(a.end, b.start) }

    /**
     * A run can still grow until the screen has stayed on for [gap] after its last session ended, so a night is
     * closed, and its report written, only then (or when the phone is plugged in, which the caller knows).
     */
    public fun closed(lastEnd: Instant, now: Instant, gap: Duration = JOIN_GAP): Boolean = !now.isBefore(lastEnd.plus(gap))
}
