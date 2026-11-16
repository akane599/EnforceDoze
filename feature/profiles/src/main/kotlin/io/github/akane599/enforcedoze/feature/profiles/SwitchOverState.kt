// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.doze.FoundSetup
import io.github.akane599.enforcedoze.doze.SwitchCheck
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.flow.Flow

/** Another Doze manager installed on the phone (SPEC 5.7, 11.5). */
@Immutable
public data class OtherManager(val packageName: String, val label: String)

/** The switch-over from another setup (SPEC 11.4): what it left, the import, the check and the way back. */
@Immutable
public data class SwitchOverState(
    /** Null until the engine has captured the phone's setup (`previous.json`) on its first start. */
    val found: FoundSetup?,
    /** Who wrote the string that was found, as the owner knows the app; null when no writer was recorded. */
    val writer: String? = null,
    val managers: List<OtherManager> = emptyList(),
    /** The custom profile the last import made; null before an import. */
    val imported: Profile? = null,
    /** The profiles the import is compared with (SPEC 11.4 step 2): Everyday and Pocket. */
    val compared: List<Profile> = emptyList(),
    val check: SwitchCheck = SwitchCheck.NotStarted,
    /**
     * EnforceDoze sees another app's write only while it holds the string (a profile with timings, managed): the check
     * needs that, or it would pass without having watched.
     */
    val watching: Boolean = false,
    /** "Return to my previous setup" is in force: EnforceDoze writes nothing until the plan is resumed. */
    val handsOff: Boolean = false,
    val canWrite: Boolean = false,
)

/** What the switch-over needs from the app (ADR 0029). */
public interface SwitchOverSource {
    public val state: Flow<SwitchOverState>

    /** Saves the found setup as a custom profile named [name]; the guardrails' problems if it could not be saved. */
    public suspend fun import(name: String): List<String>

    /** Starts step 3: from now, any other write fails the check; a clean screen off and on passes it. */
    public suspend fun startCheck()

    /** SPEC 11.4 step 4: pauses the plan and puts back what `previous.json` holds (SPEC 5.2). */
    public suspend fun returnToPrevious(): ActionOutcome

    /** The other manager's App info page, where it can be turned off or removed. */
    public fun appInfo(packageName: String)
}

/** What the switch-over does on the owner's taps. */
public interface SwitchOverActions {
    public fun import()

    public fun startCheck()

    public fun returnToPrevious()

    public fun appInfo(packageName: String)

    public fun openProfile(profileId: String)

    public val back: () -> Unit
}
