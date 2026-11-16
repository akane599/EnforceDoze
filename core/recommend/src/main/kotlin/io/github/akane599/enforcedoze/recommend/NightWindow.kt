// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.WINDOW_PADDING
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.WINDOW_STEP
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * Rule 3's window: the median start and end of the nights, rounded to [WINDOW_STEP] and padded by [WINDOW_PADDING]
 * on each side. Nights may fall at any hour (ADR 0032), so the clock is cut just after the widest gap between their
 * starts, and the starts lie in one stretch however it crosses midnight or noon. An end is its start plus the clock
 * time between start and end, so a daylight-saving night ends at the hour on the clock, not an hour early or late.
 */
internal object NightWindow {
    fun learn(nights: List<Night>): NightSlot {
        val origin = cut(nights.map { minuteOfDay(it.start) }).first
        val starts = nights.map { unwrap(minuteOfDay(it.start), origin).toDouble() }
        val ends = nights.map { unwrap(minuteOfDay(it.start), origin) + clockMinutes(it).toDouble() }
        val step = WINDOW_STEP.toMinutes()
        val start = round(median(starts), step) - WINDOW_PADDING.toMinutes()
        val end = minOf(round(median(ends), step) + WINDOW_PADDING.toMinutes(), start + MINUTES_PER_DAY - step)
        return NightSlot(NIGHT, clock(start), clock(end))
    }

    /** The smallest stretch of the clock that holds every night's start: a day less the widest gap between starts. */
    fun spread(nights: List<Night>): Duration = Duration.ofMinutes(MINUTES_PER_DAY - cut(nights.map { minuteOfDay(it.start) }).second)

    /** Where to cut the clock, just after the widest gap between [minutes] (wrapping past midnight), and that gap. */
    private fun cut(minutes: List<Long>): Pair<Long, Long> {
        val sorted = minutes.sorted()
        if (sorted.size < 2) return (sorted.firstOrNull() ?: 0L) to MINUTES_PER_DAY
        val gaps = sorted.indices.map { i ->
            val next = if (i == sorted.lastIndex) sorted.first() + MINUTES_PER_DAY else sorted[i + 1]
            Math.floorMod(next, MINUTES_PER_DAY) to next - sorted[i]
        }
        return gaps.maxBy { it.second }
    }

    private fun clockMinutes(night: Night): Long = Duration.between(night.start.toLocalDateTime(), night.end.toLocalDateTime()).toMinutes()

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** To the nearest [step] minutes, half up. */
    internal fun round(minutes: Double, step: Long): Long = (minutes / step).roundToLong() * step

    private fun minuteOfDay(time: ZonedDateTime): Long = time.toLocalTime().toSecondOfDay() / SECONDS_PER_MINUTE.toLong()

    /** [minute] of the day counted on from [origin], so every start lies in [origin, origin + a day). */
    private fun unwrap(minute: Long, origin: Long): Long = origin + Math.floorMod(minute - origin, MINUTES_PER_DAY)

    private fun clock(minutes: Long): LocalTime = LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(minutes, MINUTES_PER_DAY))

    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_DAY = 24 * 60L
}
