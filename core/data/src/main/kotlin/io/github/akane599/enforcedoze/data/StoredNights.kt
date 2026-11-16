// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.Nights
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** The night rule of [Nights] over stored sessions (ADR 0031, 0032). */
public object StoredNights {
    /**
     * What the recorder does with one rest at an unlock, decided against the night rows near it the way [Nights.keep]
     * decides: [Leave] it, [Grow] the night of the same first session that a later session joined, or [Write] a new
     * night that takes the place of the shorter nights it [Write.replaces].
     */
    public sealed interface Decision {
        public data object Leave : Decision

        public data class Grow(val row: NightRow) : Decision

        public data class Write(val replaces: List<NightRow>) : Decision
    }

    /**
     * [rest] in start order; [near] the night rows that may start within [spacing] of it. A night already there with
     * as much screen off or more keeps its place (a tie keeps the report the owner may have read).
     */
    public fun decide(rest: List<SessionRow>, near: List<NightRow>, spacing: Duration): Decision {
        val first = rest.first()
        val same = near.firstOrNull { it.sessionId == first.id }
        if (same != null) return if (same.sessionCount == rest.size) Decision.Leave else Decision.Grow(same)
        val rivals = near.filter { abs(it.start - first.start) < spacing.toMillis() }
        val screenOff = rest.sumOf { it.end - it.start }
        return if (rivals.any { it.screenOffMillis >= screenOff }) Decision.Leave else Decision.Write(rivals)
    }

    /** The rests among [sessions]: runs with enough screen off, before the spacing picks the nights among them. */
    public fun rests(sessions: List<SessionRow>, rule: Nights.Rule = Nights.Rule()): List<List<SessionRow>> =
        Nights.rests(sessions, rule, ::chargedBetween, ::stretch)

    /** No charger touched the session; only these make a night. */
    public fun unplugged(session: SessionRow): Boolean = session.chargedMillis == 0L && !session.pluggedAtStart

    /** The battery rose while the screen was on between [before] and [after], so the phone charged and the night ends. */
    public fun chargedBetween(before: SessionRow, after: SessionRow): Boolean {
        val was = before.batteryEnd
        val now = after.batteryStart
        return was != null && now != null && now > was
    }

    /** The nights among [sessions], each its sessions in start order (ADR 0031, 0032). */
    public fun nights(sessions: List<SessionRow>, rule: Nights.Rule = Nights.Rule()): List<List<SessionRow>> =
        Nights.find(sessions, rule, ::chargedBetween, ::stretch)

    /** The screen-on moments between a night's sessions. */
    public fun wakes(sessions: List<SessionRow>): List<Span> =
        Nights.wakes(sessions, ::stretch).map { Span(it.start.toEpochMilli(), it.end.toEpochMilli()) }

    private fun stretch(session: SessionRow) =
        Nights.Stretch(Instant.ofEpochMilli(session.start), Instant.ofEpochMilli(session.end), unplugged(session))
}
