// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Confidence
import io.github.akane599.enforcedoze.recommend.Measured
import io.github.akane599.enforcedoze.recommend.Night
import io.github.akane599.enforcedoze.recommend.Note
import io.github.akane599.enforcedoze.recommend.Reason
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.recommend.Share
import io.github.akane599.enforcedoze.recommend.Signals
import io.github.akane599.enforcedoze.recommend.Unmeasured
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Plan and Recommended for the shots: a week of history, illustrative numbers, no capture from any phone. */
object PlanFixtures {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val to = ZonedDateTime.of(2026, 11, 16, 8, 0, 0, 0, zone)
    private val from = to.minusDays(7)

    private fun nights(count: Int, startHour: (Int) -> Int) = (1..count).map {
        val start = from.plusDays(it.toLong() - 1).withHour(startHour(it)).withMinute(30)
        Night(start, start.plusHours(7))
    }

    private val signals = Signals(
        screenOff = Duration.ofHours(65),
        span = Duration.ofDays(7),
        screenOffHoursPerDay = 9.25,
        longShare = 0.62,
        shortDaytimeShare = Measured.Known(0.18, 40),
        neverDeep = Measured.Known(Share(6, 10), 10),
        motionResetsPerHour = Measured.Known(1.4, 10),
        nights = nights(6) { 23 },
        availableNights = 7,
        deepShare = Measured.Known(0.41, 5),
        drainPerHour = Measured.Known(0.62, 5),
    )

    val recommended = Recommendation(
        plan = Plan(BuiltInProfiles.POCKET.id, NightSlot(BuiltInProfiles.NIGHT.id, LocalTime.of(23, 0), LocalTime.of(7, 30))),
        confidence = Confidence.HIGH,
        reasons = listOf(
            Reason.ScreenOffPerDay(9.25),
            Reason.MotionKeptAwake(6, 10),
            Reason.UnpluggedNights(6, 7, LocalTime.of(23, 0), LocalTime.of(7, 30)),
            Reason.DozingWell(0.62, 0.41),
        ),
        notes = emptySet(),
        signals = signals,
    )

    /** Starts spread over most of the day: Night is offered by hand (ADR 0032); stock never measured. */
    val irregular = Recommendation(
        plan = Plan(BuiltInProfiles.EVERYDAY.id),
        confidence = Confidence.MEDIUM,
        reasons = listOf(Reason.ScreenOffPerDay(10.0), Reason.IrregularNights(5, 7, Duration.ofMinutes(9 * 60 + 15))),
        notes = setOf(Note.NIGHT_BY_HAND, Note.STOCK_NOT_MEASURED),
        signals = signals.copy(
            nights = nights(5) { listOf(23, 4, 13, 8, 1)[it - 1] },
            neverDeep = Measured.Unknown(Unmeasured.NOT_RECORDED),
            motionResetsPerHour = Measured.Unknown(Unmeasured.NOT_RECORDED),
            deepShare = Measured.Unknown(Unmeasured.NO_STOCK_SESSIONS),
            drainPerHour = Measured.Unknown(Unmeasured.NO_STOCK_SESSIONS),
        ),
    )

    val state = PlanState(
        plan = Plan(BuiltInProfiles.EVERYDAY.id, NightSlot(BuiltInProfiles.NIGHT.id, LocalTime.of(23, 0), LocalTime.of(7, 30))),
        profiles = BuiltInProfiles.ALL,
        recommendation = recommended,
        historyFrom = from.toInstant().toEpochMilli(),
        historyTo = to.toInstant().toEpochMilli(),
    )

    val inUse = state.copy(plan = recommended.plan, follow = true)

    val byHand = state.copy(plan = Plan(BuiltInProfiles.EVERYDAY.id), recommendation = irregular, urgency = Urgency.IT_CAN_WAIT)

    val empty = PlanState(plan = Plan(BuiltInProfiles.EVERYDAY.id), profiles = BuiltInProfiles.ALL)

    val readOnly = state.copy(canWrite = false, notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)))
}
