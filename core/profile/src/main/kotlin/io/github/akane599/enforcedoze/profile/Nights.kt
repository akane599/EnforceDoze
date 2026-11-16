// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import java.time.Duration
import java.time.Instant

/**
 * What one night is (SPEC 7.2 N, 8.5; ADR 0031, 0032). Unplugged screen-off sessions count as one rest when every
 * screen-on gap between them is shorter than [JOIN_GAP]: a glance at the clock or a trip to the kitchen does not
 * split it in two. A rest with [MIN_SCREEN_OFF] of screen-off time in all, the gaps not counted, is a night at
 * whatever hour it falls, because the owner sleeps at irregular hours (ADR 0032). Of rests that start less than
 * [SPACING] apart only the longest is a night, so each night is the longest rest in the 24 hours around its start:
 * a nap and the main sleep are not two nights, and two sleeps on consecutive days stay two even when the second
 * starts earlier on the clock. The recommender's N signal and the night report both use it.
 */
public object Nights {
    /** Screen-off time a night needs, summed over its sessions (SPEC 7.2 N, 8.5). */
    public val MIN_SCREEN_OFF: Duration = Duration.ofHours(4)

    /** A screen-on gap shorter than this joins the sessions on either side into one night (ADR 0031). */
    public val JOIN_GAP: Duration = Duration.ofMinutes(10)

    /**
     * Two nights start at least this far apart; of two rests closer than this, the longer is the night (ADR 0032).
     * Half a day either side of a rest is the owner's "at most one in 24 hours". A full day after each night would
     * also drop the next sleep whenever it starts earlier on the clock than the last one did.
     */
    public val SPACING: Duration = Duration.ofHours(12)

    /**
     * One screen-off session as the rule sees it. [unplugged] is true only when the session had no charger at all;
     * a plugged session, or one whose plug state is unknown, is never part of a night and breaks a run.
     */
    public data class Stretch(val start: Instant, val end: Instant, val unplugged: Boolean)

    /** A screen-on moment between two sessions of one night: the short wake-ups the report's strip marks. */
    public data class Wake(val start: Instant, val end: Instant)

    /**
     * The rule's numbers. Only the compressed-night test (SPEC 13.2) changes them. It scales the threshold and the
     * join gap down to minutes and turns the spacing off, so two test nights minutes apart stay two (ADR 0032).
     */
    public data class Rule(val minScreenOff: Duration = MIN_SCREEN_OFF, val gap: Duration = JOIN_GAP, val spacing: Duration = SPACING) {
        public companion object {
            /** The rule scaled so a night needs [minScreenOff]: the join gap shrinks by the same factor, no spacing. */
            public fun compressed(minScreenOff: Duration): Rule = Rule(
                minScreenOff,
                Duration.ofMillis(JOIN_GAP.toMillis() * minScreenOff.toMillis() / MIN_SCREEN_OFF.toMillis()),
                spacing = Duration.ZERO,
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

    /** The runs of [sessions] long enough to be a night, before the spacing picks among them. */
    public fun <T> rests(
        sessions: List<T>,
        rule: Rule = Rule(),
        charged: (before: T, after: T) -> Boolean = { _, _ -> false },
        stretch: (T) -> Stretch,
    ): List<List<T>> = runs(sessions, rule.gap, charged, stretch).filter { screenOff(it, stretch) >= rule.minScreenOff }

    /** The nights among [sessions]: the rests, at most one per [Rule.spacing], each the longest ([keep]). */
    public fun <T> find(
        sessions: List<T>,
        rule: Rule = Rule(),
        charged: (before: T, after: T) -> Boolean = { _, _ -> false },
        stretch: (T) -> Stretch,
    ): List<List<T>> = keep(rests(sessions, rule, charged, stretch), rule.spacing, stretch)

    /**
     * Of [rests] in start order, the ones that are nights, decided as they come, the way the report decides them at
     * each unlock: a rest that starts less than [spacing] after the last night takes its place if it has more screen
     * off, and is dropped otherwise (a tie keeps the night already there). Nights then start at least [spacing] apart.
     */
    public fun <T> keep(rests: List<List<T>>, spacing: Duration, stretch: (T) -> Stretch): List<List<T>> {
        val nights = mutableListOf<List<T>>()
        for (rest in rests.sortedBy { stretch(it.first()).start }) {
            val last = nights.lastOrNull()
            if (last == null || Duration.between(stretch(last.first()).start, stretch(rest.first()).start) >= spacing) {
                nights += rest
            } else if (screenOff(rest, stretch) > screenOff(last, stretch)) {
                nights[nights.lastIndex] = rest
            }
        }
        return nights
    }

    /** Screen-off time of a run; the screen-on gaps between its sessions do not count. */
    public fun <T> screenOff(run: List<T>, stretch: (T) -> Stretch): Duration =
        run.fold(Duration.ZERO) { sum, session -> stretch(session).let { sum + Duration.between(it.start, it.end) } }

    /** The screen-on gaps inside a run, in order. */
    public fun <T> wakes(run: List<T>, stretch: (T) -> Stretch): List<Wake> =
        run.map(stretch).zipWithNext().filter { (a, b) -> b.start > a.end }.map { (a, b) -> Wake(a.end, b.start) }
}
