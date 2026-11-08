// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.CLASSIC
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.EVERYDAY
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.FEEDBACK_WINDOW
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.LOW_GAIN
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.LOW_GAIN_NIGHTS
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.STOCK
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.STRICT
import java.time.Duration
import java.time.Instant

/** A complaint still in force: why, and the strictest plan it allows. */
internal data class Step(val reason: Reason, val ceiling: Plan)

/**
 * SPEC 7.5 (ADR 0026). A late message, or a plan that saved under [LOW_GAIN] over [LOW_GAIN_NIGHTS] unplugged
 * nights, caps the recommendation at one notch gentler than the plan in use when it happened. The cap holds
 * for [FEEDBACK_WINDOW], also after the gentler plan is applied, so the recommendation never returns to the
 * plan that was complained about. It only ever lowers the fresh recommendation, never raises it.
 */
public object FeedbackLoop {
    /** Strict → Classic → Night off → Everyday. Pocket and custom profiles step to Everyday; Stock and Everyday stay. */
    public fun gentler(plan: Plan): Plan = when {
        plan.dayProfileId == STRICT -> plan.copy(dayProfileId = CLASSIC)
        plan.night != null -> plan.copy(night = null)
        plan.dayProfileId != EVERYDAY && plan.dayProfileId != STOCK -> plan.copy(dayProfileId = EVERYDAY)
        else -> plan
    }

    /** [plan] no stricter than [ceiling]: the day profile that asks less, and [plan]'s night only if [ceiling] has one. */
    public fun atMost(plan: Plan, ceiling: Plan): Plan {
        val day = if (rank(plan.dayProfileId) <= rank(ceiling.dayProfileId)) plan.dayProfileId else ceiling.dayProfileId
        return Plan(day, plan.night?.takeIf { ceiling.night != null })
    }

    /** Day profiles from gentlest to strictest; null stands for Pocket and custom profiles, between Everyday and Classic. */
    private val LADDER: List<String?> = listOf(STOCK, EVERYDAY, null, CLASSIC, STRICT)

    private fun rank(profileId: String): Int = LADDER.indexOf(profileId).takeIf { it >= 0 } ?: LADDER.indexOf(null)

    /** Every complaint inside [FEEDBACK_WINDOW], newest first. */
    internal fun steps(history: History, signals: Signals): List<Step> {
        val from = history.to.minus(FEEDBACK_WINDOW)
        val late = history.feedback.lateArrivals.filter { it >= from && it <= history.to }.mapNotNull { time ->
            history.feedback.planAt(time)?.let { Step(Reason.ArrivedLate(time), gentler(it.plan)) to time }
        }
        return (late + lowGain(history, signals, from)).sortedByDescending { it.second }.map { it.first }
    }

    /** Each applied plan that could step, judged on its own unplugged nights inside the window against the stock drain R. */
    private fun lowGain(history: History, signals: Signals, from: Instant): List<Pair<Step, Instant>> {
        val baseline = signals.drainPerHour.valueOrNull?.takeIf { it > 0 } ?: return emptyList()
        val applied = history.feedback.applied
        return applied.mapIndexedNotNull { i, plan ->
            if (gentler(plan.plan) == plan.plan) return@mapIndexedNotNull null
            val until = applied.getOrNull(i + 1)?.since ?: history.to
            val nights = history.sessions
                .filter { it.start >= maxOf(plan.since, from) && it.start < until && it.batteryDrop != null }
                .filter { Signals.isNight(it, history.zone) }
                .groupBy { Signals.nightOf(it.start.atZone(history.zone)) }
                .map { (_, same) -> same.maxBy { it.duration } }
            if (nights.size < LOW_GAIN_NIGHTS) return@mapIndexedNotNull null
            val drain = nights.sumOf { it.batteryDrop!! } / hours(nights.fold(Duration.ZERO) { sum, s -> sum + s.duration })
            // Saved under LOW_GAIN: drain above the allowed drain, with the tolerance so exactly 10% is not a step.
            if (!exceeds(drain, baseline * (1 - LOW_GAIN))) return@mapIndexedNotNull null
            Step(Reason.LowGain(1 - drain / baseline, nights.size), gentler(plan.plan)) to nights.maxOf { it.start }
        }
    }
}
