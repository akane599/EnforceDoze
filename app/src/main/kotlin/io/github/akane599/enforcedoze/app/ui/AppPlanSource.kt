// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.feature.plan.PlanSource
import io.github.akane599.enforcedoze.feature.plan.PlanState
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Plan and Recommended (SPEC 9.1, 7.4) over the engine's plan, the recommendation and the owner's answers. */
internal class AppPlanSource(private val services: AppServices) : PlanSource {
    override val state: Flow<PlanState> = combine(
        services.engine,
        services.profiles,
        services.recommendations.current,
        combine(services.canWrite, services.prefs.changes) { canWrite, _ -> canWrite },
        services.notices.notices,
    ) { view, profiles, recommendation, canWrite, notices ->
        val range = services.recommendations.range.value
        PlanState(
            plan = view.plan?.plan ?: Plan(BuiltInProfiles.STOCK.id),
            profiles = profiles,
            recommendation = recommendation,
            historyFrom = range?.takeIf { recommendation != null }?.from?.toEpochMilli(),
            historyTo = range?.takeIf { recommendation != null }?.to?.toEpochMilli(),
            urgency = services.prefs.urgency,
            follow = services.prefs.followRecommended,
            canWrite = canWrite,
            notices = notices,
        )
    }

    override suspend fun apply(plan: Plan): ActionOutcome = services.applyPlan(plan)

    override suspend fun follow(on: Boolean) {
        services.prefs.followRecommended = on
        if (on) services.followRecommendation()
    }

    override suspend fun answer(urgency: Urgency) {
        services.prefs.urgency = urgency
    }

    override fun decline() {
        services.graph.scope.launch { services.prefs.declined = services.recommendations.current.first()?.plan }
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)
}
