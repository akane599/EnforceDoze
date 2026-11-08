// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.WINDOW_PADDING
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.WINDOW_STEP
import java.time.Duration
import java.time.LocalTime
import kotlin.math.roundToLong

/**
 * Rule 3's window: the median start and end of the qualifying nights, rounded to [WINDOW_STEP] and padded by
 * [WINDOW_PADDING] on each side. Times are counted in minutes from noon, so a night that crosses midnight is one
 * stretch; an end is its start plus the clock time between start and end, so a daylight-saving night ends at the
 * hour on the clock, not an hour early or late.
 */
internal object NightWindow {
    fun learn(nights: List<Night>): NightSlot {
        val starts = nights.map { fromNoon(it.start.toLocalTime()).toDouble() }
        val ends = nights.map { fromNoon(it.start.toLocalTime()) + clockMinutes(it).toDouble() }
        val step = WINDOW_STEP.toMinutes()
        val start = round(median(starts), step) - WINDOW_PADDING.toMinutes()
        val end = minOf(round(median(ends), step) + WINDOW_PADDING.toMinutes(), start + MINUTES_PER_DAY - step)
        return NightSlot(NIGHT, clock(start), clock(end))
    }

    private fun clockMinutes(night: Night): Long = Duration.between(night.start.toLocalDateTime(), night.end.toLocalDateTime()).toMinutes()

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** To the nearest [step] minutes, half up. */
    internal fun round(minutes: Double, step: Long): Long = (minutes / step).roundToLong() * step

    private fun fromNoon(time: LocalTime): Long = Math.floorMod(time.toSecondOfDay() / SECONDS_PER_MINUTE - NOON, MINUTES_PER_DAY)

    private fun clock(fromNoon: Long): LocalTime = LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(fromNoon + NOON, MINUTES_PER_DAY))

    private const val SECONDS_PER_MINUTE = 60
    private const val NOON = 12 * 60L
    private const val MINUTES_PER_DAY = 24 * 60L
}
