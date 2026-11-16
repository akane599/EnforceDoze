// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** Everything Plan and Recommended show, mapped by the app from the plan, the profile store and the recommender. */
@Immutable
public data class PlanState(
    /** The plan the engine runs now. */
    val plan: Plan,
    /** The six built-ins in SPEC 6.1 order, then the owner's custom profiles: names and the pickers. */
    val profiles: List<Profile>,
    /** The current recommendation; null before any screen-off time is recorded (SPEC 9.3 "no data yet"). */
    val recommendation: Recommendation? = null,
    /** The history the recommendation read, epoch milliseconds (SPEC 9.1 "the history range used"). */
    val historyFrom: Long? = null,
    val historyTo: Long? = null,
    /** The owner's answer to the urgency question (SPEC 7.2 Q). */
    val urgency: Urgency = Urgency.WITHIN_THE_HOUR,
    /** "Follow Recommended automatically", off by default (SPEC 7.5). */
    val follow: Boolean = false,
    /** False without privilege (read-only, SPEC 9.3): nothing can be applied. */
    val canWrite: Boolean = true,
    val notices: List<StateNotice> = emptyList(),
) {
    /** The name the owner sees for [id], or the id itself for a profile the store no longer has. */
    public fun name(id: String): String = profiles.firstOrNull { it.id == id }?.name ?: id

    public fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** The recommendation's plan is the one running. */
    public val recommendedInUse: Boolean get() = recommendation?.plan == plan
}

/** What Plan and Recommended need from the app (ADR 0029). */
public interface PlanSource {
    public val state: Flow<PlanState>

    /** Runs [plan] through the engine (SPEC 6.5). */
    public suspend fun apply(plan: Plan): ActionOutcome

    public suspend fun follow(on: Boolean)

    public suspend fun answer(urgency: Urgency)

    /** The owner chose something else: remembered, so the recommendation does not nag (SPEC 7.4). */
    public fun decline()

    public fun seen(noticeId: String)
}

/** Plan edits that only change data, tested on their own. */
public object PlanEdits {
    /** [plan] with a night slot: the recommended slot if there is one, otherwise Night over SPEC 6.1's 23:00–07:30. */
    public fun nightOn(plan: Plan, recommended: Plan?): Plan = plan.night?.let { plan }
        ?: plan.copy(night = recommended?.night ?: NightSlot(NIGHT, NightSlot.DEFAULT_START, NightSlot.DEFAULT_END))

    /** [plan]'s night slot with its start or end at [time]; null when that would make the window empty. */
    public fun withEdge(plan: Plan, start: Boolean, time: LocalTime): Plan? {
        val night = plan.night ?: return null
        val from = if (start) time else night.start
        val until = if (start) night.end else time
        // NightSlot refuses an empty window, so the check comes before the copy.
        return if (from == until) null else plan.copy(night = NightSlot(night.profileId, from, until))
    }

    private const val NIGHT = "night"
}
