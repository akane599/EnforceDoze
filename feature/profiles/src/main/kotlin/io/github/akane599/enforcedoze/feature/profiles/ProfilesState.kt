// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow

/** The two places a profile can run in a plan (SPEC 6). */
public enum class Slot { DAY, NIGHT }

/** Everything the profile screens show, mapped by the app from the profile store, the plan and the probes. */
@Immutable
public data class ProfilesState(
    /** The six built-ins in SPEC 6.1 order, then the owner's custom profiles. */
    val profiles: List<Profile>,
    val plan: Plan,
    /** The profiles the current recommendation's plan uses (SPEC 7), marked "Recommended". */
    val recommended: Set<String> = emptySet(),
    /** The phone's own values in milliseconds from `stock.json`, or null before onboarding read them (SPEC 5.2). */
    val stock: Map<String, Long>? = null,
    /** Keys the capability probe did not find on this phone: a profile that sets one is partly applied (SPEC 6.2). */
    val missingKeys: Set<String> = emptySet(),
    /** False without privilege (read-only, SPEC 9.3): nothing can be applied. */
    val canWrite: Boolean = true,
    val notices: List<StateNotice> = emptyList(),
) {
    public fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** The slots [id] runs in now. */
    public fun slots(id: String): Set<Slot> = buildSet {
        if (plan.dayProfileId == id) add(Slot.DAY)
        if (plan.night?.profileId == id) add(Slot.NIGHT)
    }
}

/** What the profile screens need from the app (ADR 0029). */
public interface ProfilesSource {
    public val state: Flow<ProfilesState>

    /** Puts [profileId] in [slot] of the plan through the engine (SPEC 6.5). */
    public suspend fun apply(profileId: String, slot: Slot): ActionOutcome

    /** Saves a custom copy of [profileId] named [name] and returns its id (SPEC 6.3). */
    public suspend fun clone(profileId: String, name: String): String

    /** Saves [profile] when the guardrails pass; otherwise saves nothing and returns why (SPEC 6.3). */
    public suspend fun save(profile: Profile): List<GuardrailProblem>

    /** Removes a custom profile that no slot uses. */
    public suspend fun delete(profileId: String)

    public fun seen(noticeId: String)
}

/** What the list does on the owner's taps; the route implements it. */
public interface ProfilesActions {
    public fun open(profileId: String)

    public fun clone(profile: Profile)

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}
