// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Synthetic histories for the policy tests (SPEC 7.6). Day 0 is a Monday; a history runs from noon on day 0 to
// noon on its last day, so the night of day 0 is the first one it can hold.

internal val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
private val DAY_ZERO: LocalDate = LocalDate.of(2026, 3, 2)

internal val Int.min: Duration get() = Duration.ofMinutes(toLong())

internal fun at(day: Int, time: String, zone: ZoneId = ZONE): Instant =
    DAY_ZERO.plusDays(day.toLong()).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

/** Unplugged, under stock settings, sensors free and a stock walk of 35 minutes; facts beyond that are added with `copy`. */
internal fun session(day: Int, start: String, minutes: Int, zone: ZoneId = ZONE): ScreenOffSession {
    val begin = at(day, start, zone)
    return ScreenOffSession(
        begin,
        begin + minutes.min,
        plugged = false,
        Provenance.STOCK,
        sensorsRestricted = false,
        expectedToDeep = 35.min,
    )
}

internal fun history(days: Int, sessions: List<ScreenOffSession>, feedback: Feedback = Feedback(), zone: ZoneId = ZONE): History =
    History(at(0, "12:00", zone), at(days, "12:00", zone), zone, sessions, feedback)

/** A long afternoon away from the phone that reaches deep idle: 60 min, 20 in deep, no resets, 0.5 %/h. */
internal fun afternoon(day: Int): ScreenOffSession =
    session(day, "13:00", 60).copy(timeInDeep = 20.min, motionResets = 0, batteryDrop = 0.5)

/** A night on the charger: screen-off time that counts for S and for nothing else. */
internal fun charging(day: Int): ScreenOffSession = session(day, "23:00", 480).copy(plugged = true)

/** An unplugged night from [start] for [minutes], half of it in deep idle, 1 %/h. */
internal fun sleep(day: Int, start: String = "23:10", minutes: Int = 470): ScreenOffSession =
    session(day, start, minutes).copy(timeInDeep = (minutes / 2).min, motionResets = 0, batteryDrop = minutes / 60.0)

/** A quiet phone: charged every night and one deep-reaching afternoon a day. It gets Everyday and nothing else. */
internal fun quiet(days: Int): List<ScreenOffSession> = (0 until days).flatMap { listOf(charging(it), afternoon(it)) }

/** [count] short sessions of [minutes] every 20 minutes from 09:00, unplugged. */
internal fun shorts(day: Int, count: Int, minutes: Int = 10): List<ScreenOffSession> =
    (0 until count).map { session(day, LocalTime.of(9, 0).plusMinutes(20L * it).toString(), minutes) }

internal fun recommend(history: History, urgency: Urgency = Urgency.WITHIN_THE_HOUR): Recommendation =
    RecommendationPolicy.recommend(history, Answers(urgency))
