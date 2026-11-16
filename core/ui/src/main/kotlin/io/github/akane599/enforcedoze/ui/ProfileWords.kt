// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever

/** One built-in's wording from SPEC 6.1: what it is for, what it costs, and the cost in one line for the list. */
private data class BuiltInText(@StringRes val purpose: Int, @StringRes val cost: Int, @StringRes val short: Int)

private val BUILT_IN_TEXT = mapOf(
    "stock" to BuiltInText(R.string.ui_profile_purpose_stock, R.string.ui_profile_cost_stock, R.string.ui_profile_short_stock),
    "everyday" to BuiltInText(R.string.ui_profile_purpose_everyday, R.string.ui_profile_cost_everyday, R.string.ui_profile_short_everyday),
    "pocket" to BuiltInText(R.string.ui_profile_purpose_pocket, R.string.ui_profile_cost_pocket, R.string.ui_profile_short_pocket),
    "night" to BuiltInText(R.string.ui_profile_purpose_night, R.string.ui_profile_cost_night, R.string.ui_profile_short_night),
    "classic" to BuiltInText(R.string.ui_profile_purpose_classic, R.string.ui_profile_cost_classic, R.string.ui_profile_short_classic),
    "strict" to BuiltInText(R.string.ui_profile_purpose_strict, R.string.ui_profile_cost_strict, R.string.ui_profile_short_strict),
)

private fun Profile.builtInText(): BuiltInText? = if (builtIn) BUILT_IN_TEXT[id] else null

/** What the profile is for: SPEC 6.1's line for a built-in, a plain note for a custom one. */
@Composable
public fun profilePurpose(profile: Profile): String = stringResource(profile.builtInText()?.purpose ?: R.string.ui_profile_purpose_custom)

/**
 * What the profile costs. A built-in has SPEC 6.1's sentence; a custom profile's cost follows from its levers,
 * costliest first, so the owner reads what each lever takes away.
 */
@Composable
public fun profileCost(profile: Profile, formats: Formats): List<String> {
    profile.builtInText()?.let { return listOf(stringResource(it.cost)) }
    val levers = profile.levers
    val delay = levers.forceIdle.delaySec * MILLIS_PER_SECOND
    val maxIdle = profile.deviceIdle["max_idle_to"]?.toLongOrNull()
    val firstIdle = profile.deviceIdle["idle_to"]?.toLongOrNull()
    return buildList {
        if (levers.forceIdle.enabled) {
            add(
                if (delay ==
                    0L
                ) {
                    stringResource(R.string.ui_profile_cost_force)
                } else {
                    stringResource(R.string.ui_profile_cost_force_after, formats.duration(delay))
                },
            )
        }
        if (levers.allowlist == AllowlistLever.TRIMMED) add(stringResource(R.string.ui_profile_cost_trimmed))
        if (levers.constants && maxIdle != null) {
            add(stringResource(R.string.ui_profile_cost_windows, formats.duration(maxIdle)))
        } else if (levers.constants && firstIdle != null) {
            add(stringResource(R.string.ui_profile_cost_windows_from, formats.duration(firstIdle)))
        }
        when (levers.sensors) {
            SensorLever.RESTRICTED -> add(stringResource(R.string.ui_profile_cost_sensors))
            SensorLever.ALL_SENSORS_OFF -> add(stringResource(R.string.ui_profile_cost_all_sensors_off))
            SensorLever.OFF -> Unit
        }
        if (isEmpty()) add(stringResource(if (levers.constants) R.string.ui_profile_short_everyday else R.string.ui_profile_short_stock))
    }
}

/** The cost in one line for the list row. */
@Composable
public fun profileShortCost(profile: Profile, formats: Formats): String =
    profile.builtInText()?.let { stringResource(it.short) } ?: profileCost(profile, formats).first()

private const val MILLIS_PER_SECOND = 1_000L
