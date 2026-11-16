// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.Nights
import java.time.Instant
import java.time.ZoneId

/** The night rule of [Nights] over stored sessions (ADR 0031). */
public object StoredNights {
    /** No charger touched the session; only these make a night. */
    public fun unplugged(session: SessionRow): Boolean = session.chargedMillis == 0L && !session.pluggedAtStart

    /** The battery rose while the screen was on between [before] and [after], so the phone charged and the night ends. */
    public fun chargedBetween(before: SessionRow, after: SessionRow): Boolean {
        val was = before.batteryEnd
        val now = after.batteryStart
        return was != null && now != null && now > was
    }

    /** The nights among [sessions], each its sessions in start order (ADR 0031). */
    public fun nights(sessions: List<SessionRow>, zone: ZoneId, rule: Nights.Rule = Nights.Rule()): List<List<SessionRow>> =
        Nights.find(sessions, zone, rule, ::chargedBetween, ::stretch)

    /** The screen-on moments between a night's sessions. */
    public fun wakes(sessions: List<SessionRow>): List<Span> =
        Nights.wakes(sessions, ::stretch).map { Span(it.start.toEpochMilli(), it.end.toEpochMilli()) }

    private fun stretch(session: SessionRow) =
        Nights.Stretch(Instant.ofEpochMilli(session.start), Instant.ofEpochMilli(session.end), unplugged(session))
}
