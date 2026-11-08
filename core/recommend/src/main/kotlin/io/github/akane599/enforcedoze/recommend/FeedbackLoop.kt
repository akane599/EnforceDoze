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

/**
 * SPEC 7.5: a late message, or a plan that saves under [LOW_GAIN] after [LOW_GAIN_NIGHTS] unplugged nights,
 * steps the plan in use one notch gentler (ADR 0026). Only feedback since that plan was applied, and within
 * [FEEDBACK_WINDOW], counts, so applying the gentler plan ends the step instead of stepping again.
 */
public object FeedbackLoop {
    /** Strict → Classic → Night off → Everyday. Pocket and custom profiles step to Everyday; Stock and Everyday stay. */
    public fun gentler(plan: Plan): Plan = when {
        plan.dayProfileId == STRICT -> plan.copy(dayProfileId = CLASSIC)
        plan.night != null -> plan.copy(night = null)
        plan.dayProfileId != EVERYDAY && plan.dayProfileId != STOCK -> plan.copy(dayProfileId = EVERYDAY)
        else -> plan
    }

    /** Q = Right away allows no Classic, no Strict and no Night (SPEC 7.3), also after a step. */
    public fun capForUrgency(plan: Plan, urgency: Urgency): Plan {
        if (urgency != Urgency.RIGHT_AWAY) return plan
        val day = if (plan.dayProfileId == CLASSIC || plan.dayProfileId == STRICT) EVERYDAY else plan.dayProfileId
        return Plan(day, null)
    }

    /** Why the plan in use should step, a late message first; null when it should not. */
    internal fun step(history: History, signals: Signals): Reason? = arrivedLate(history) ?: lowGain(history, signals)

    private fun arrivedLate(history: History): Reason? {
        val since = since(history) ?: return null
        return history.feedback.lateArrivals.filter { it >= since && it <= history.to }.maxOrNull()?.let(Reason::ArrivedLate)
    }

    /** The plan's unplugged nights against the stock drain R; nothing to judge without R. */
    private fun lowGain(history: History, signals: Signals): Reason? {
        val since = since(history)
        val baseline = signals.drainPerHour.valueOrNull?.takeIf { it > 0 }
        if (since == null || baseline == null) return null
        val nights = history.sessions
            .filter { it.start >= since && it.batteryDrop != null && Signals.isNight(it, history.zone) }
            .groupBy { Signals.nightOf(it.start.atZone(history.zone)) }
            .map { (_, same) -> same.maxBy { it.duration } }
        if (nights.size < LOW_GAIN_NIGHTS) return null
        val drain = nights.sumOf { it.batteryDrop!! } / Signals.hours(nights.fold(Duration.ZERO) { sum, s -> sum + s.duration })
        // Saved under LOW_GAIN, compared as drain against the allowed drain so exactly 10% is not a step.
        return if (drain > baseline * (1 - LOW_GAIN)) Reason.LowGain(1 - drain / baseline, nights.size) else null
    }

    private fun since(history: History) = history.feedback.current?.since?.let { maxOf(it, history.to.minus(FEEDBACK_WINDOW)) }
}
