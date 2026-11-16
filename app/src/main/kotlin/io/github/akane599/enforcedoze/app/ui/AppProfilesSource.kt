// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.profiles.ProfilesSource
import io.github.akane599.enforcedoze.feature.profiles.ProfilesState
import io.github.akane599.enforcedoze.feature.profiles.Slot
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

/** Profiles, profile detail and the editor (SPEC 9.1, 6.3) over the profile store and the engine's plan. */
internal class AppProfilesSource(private val services: AppServices) : ProfilesSource {
    private data class Inputs(val profiles: List<Profile>, val view: EngineView, val recommended: Plan?, val canWrite: Boolean)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<ProfilesState> = combine(
        services.profiles,
        services.engine,
        services.recommendations.current,
        services.canWrite,
    ) { profiles, view, recommendation, canWrite -> Inputs(profiles, view, recommendation?.plan, canWrite) }
        .combine(services.notices.notices) { inputs, notices -> inputs to notices }
        .mapLatest { (inputs, notices) ->
            ProfilesState(
                profiles = inputs.profiles,
                plan = inputs.view.plan?.plan ?: Plan(BuiltInProfiles.STOCK.id),
                recommended = listOfNotNull(inputs.recommended?.dayProfileId, inputs.recommended?.night?.profileId).toSet(),
                stock = services.stockMillis(),
                missingKeys = inputs.view.status?.missingKeys.orEmpty(),
                canWrite = inputs.canWrite,
                notices = notices,
            )
        }

    override suspend fun apply(profileId: String, slot: Slot): ActionOutcome {
        val current = services.engine.first().plan?.plan ?: Plan(BuiltInProfiles.STOCK.id)
        val plan = when (slot) {
            Slot.DAY -> current.copy(dayProfileId = profileId)
            // A night slot keeps its window; a new one takes SPEC 6.1's 23:00–07:30.
            Slot.NIGHT -> current.copy(
                night = current.night?.copy(profileId = profileId) ?: NightSlot(profileId, NightSlot.DEFAULT_START, NightSlot.DEFAULT_END),
            )
        }
        return services.applyPlan(plan)
    }

    override suspend fun clone(profileId: String, name: String): String {
        val source = services.profiles.first().first { it.id == profileId }
        val taken = services.profiles.first().map { it.id }.toSet()
        val base = name.lowercase().replace(UNSAFE, "-").trim('-').ifEmpty { "custom" }.take(ID_ROOM)
        val id = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
        val problems = services.graph.profiles.store.save(source.copy(id = id, name = name, builtIn = false), services.stockMillis())
        check(problems.isEmpty()) { "a copy of $profileId did not pass the guardrails: $problems" }
        services.profilesChanged()
        return id
    }

    override suspend fun save(profile: Profile): List<GuardrailProblem> {
        val problems = services.graph.profiles.store.save(profile, services.stockMillis())
        if (problems.isEmpty()) services.profilesChanged()
        return problems
    }

    override suspend fun delete(profileId: String) {
        services.graph.profiles.store.delete(profileId)
        services.profilesChanged()
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    private companion object {
        val UNSAFE = Regex("[^a-z0-9._-]+")

        /** Room for a "-n" suffix inside the 64 characters an id may have (SPEC 6.3). */
        const val ID_ROOM = 56
    }
}
