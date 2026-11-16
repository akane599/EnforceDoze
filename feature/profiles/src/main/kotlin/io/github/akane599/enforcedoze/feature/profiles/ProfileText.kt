// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Guardrails
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import io.github.akane599.enforcedoze.ui.title
import io.github.akane599.enforcedoze.design.R as DesignR

/** The letter SPEC 5 gives each lever the owner sets: A to D. Sensor privacy is the engine's own detail of C. */
internal val Lever.letter: String
    get() = when (this) {
        Lever.CONSTANTS -> "A"

        Lever.FORCE_IDLE -> "B"

        Lever.SENSORS, Lever.SENSOR_PRIVACY -> "C"

        Lever.ALLOWLIST -> "D"

        // The root-tier extras are the owner's settings, not a profile's (ADR 0038); they carry no letter.
        Lever.WIFI, Lever.MOBILE_DATA, Lever.APP_SUSPENSION -> ""
    }

/** One of the four levers as the detail lists it: letter, name, what this profile does with it. */
internal data class LeverLine(val letter: String, val title: String, val state: String, val on: Boolean)

@Composable
internal fun leverLines(profile: Profile, formats: Formats): List<LeverLine> {
    val levers = profile.levers
    val force = levers.forceIdle
    val forceState = when {
        !force.enabled -> stringResource(R.string.profiles_state_off)
        force.delaySec == 0 -> stringResource(R.string.profiles_force_at_off)
        else -> stringResource(R.string.profiles_force_after, formats.duration(force.delaySec * MILLIS_PER_SECOND))
    }
    return listOf(
        LeverLine(
            Lever.CONSTANTS.letter,
            Lever.CONSTANTS.title(),
            stringResource(if (levers.constants) R.string.profiles_state_on else R.string.profiles_state_off),
            levers.constants,
        ),
        LeverLine(
            Lever.FORCE_IDLE.letter,
            Lever.FORCE_IDLE.title(),
            if (force.enabled && force.untilUnlock) stringResource(R.string.profiles_force_until_unlock, forceState) else forceState,
            force.enabled,
        ),
        LeverLine(Lever.SENSORS.letter, Lever.SENSORS.title(), stringResource(levers.sensors.label), levers.sensors != SensorLever.OFF),
        LeverLine(
            Lever.ALLOWLIST.letter,
            Lever.ALLOWLIST.title(),
            stringResource(levers.allowlist.label),
            levers.allowlist == AllowlistLever.TRIMMED,
        ),
    )
}

internal val SensorLever.label: Int
    get() = when (this) {
        SensorLever.OFF -> R.string.profiles_sensors_off
        SensorLever.RESTRICTED -> R.string.profiles_sensors_restricted
        SensorLever.ALL_SENSORS_OFF -> R.string.profiles_sensors_all_off
    }

internal val AllowlistLever.label: Int
    get() = when (this) {
        AllowlistLever.INHERIT -> R.string.profiles_allowlist_inherit
        AllowlistLever.TRIMMED -> R.string.profiles_allowlist_trimmed
    }

/** What a key does, in one plain line (SPEC 6.3); meanings from ADR 0013. */
@StringRes
internal fun keyLine(key: String): Int = when (key) {
    "inactive_to" -> R.string.profiles_key_inactive_to
    "idle_after_inactive_to" -> R.string.profiles_key_idle_after_inactive_to
    "sensing_to" -> R.string.profiles_key_sensing_to
    "locating_to" -> R.string.profiles_key_locating_to
    "motion_inactive_to" -> R.string.profiles_key_motion_inactive_to
    "light_after_inactive_to" -> R.string.profiles_key_light_after_inactive_to
    "idle_to" -> R.string.profiles_key_idle_to
    "idle_factor" -> R.string.profiles_key_idle_factor
    "max_idle_to" -> R.string.profiles_key_max_idle_to
    in Guardrails.ALLOWLIST_DURATION_KEYS -> R.string.profiles_key_allowlist_duration
    in Guardrails.VENDOR_KEYS -> R.string.profiles_key_vendor
    else -> R.string.profiles_key_other
}

/** A stored value as the owner reads it: "5 min", "30 s", "×2". Another key's value is shown as written. */
@Composable
internal fun timingText(key: String, stored: String, formats: Formats): String {
    val unit = unitOf(key)
    val millis = stored.toLongOrNull()
    return when {
        unit == TimingUnit.FACTOR -> stringResource(R.string.profiles_factor, stored)

        unit == TimingUnit.RAW && key !in Guardrails.ALLOWLIST_DURATION_KEYS -> stored

        millis == null -> stored

        // Formats.duration rounds to whole minutes; a timing that is not a whole minute keeps its seconds.
        millis < MILLIS_PER_HOUR && millis % MILLIS_PER_MINUTE != 0L -> stringResource(
            DesignR.string.ed_duration_s,
            millis / MILLIS_PER_SECOND,
        )

        else -> formats.duration(millis)
    }
}

/** Why the editor refuses a value, with the reason the guardrail exists (SPEC 6.3). */
@Composable
internal fun problemText(problem: DraftProblem, formats: Formats): String = when (problem) {
    DraftProblem.NameEmpty -> stringResource(R.string.profiles_problem_name)
    DraftProblem.DelayNotANumber -> stringResource(R.string.profiles_problem_number)
    is DraftProblem.NotANumber -> stringResource(R.string.profiles_problem_number)
    is DraftProblem.Guardrail -> guardrailText(problem.problem, formats)
}

@Composable
private fun guardrailText(problem: GuardrailProblem, formats: Formats): String {
    val reason = problem.key?.let(::reasonOf)?.let { " " + stringResource(it) }.orEmpty()
    return when (problem) {
        is GuardrailProblem.BelowMinimum -> stringResource(R.string.profiles_problem_min, bound(problem.key, problem.minimum, formats)) +
            reason

        is GuardrailProblem.AboveMaximum -> stringResource(R.string.profiles_problem_max, bound(problem.key, problem.maximum, formats)) +
            reason

        is GuardrailProblem.BelowStock -> stringResource(R.string.profiles_problem_below_stock, formats.duration(problem.stock))

        is GuardrailProblem.StockUnknown -> stringResource(R.string.profiles_problem_stock_unknown)

        is GuardrailProblem.NotANumber -> stringResource(R.string.profiles_problem_number)

        is GuardrailProblem.Invalid -> problem.detail
    }
}

@Composable
private fun bound(key: String, value: Double, formats: Formats): String =
    timingText(key, if (unitOf(key) == TimingUnit.FACTOR) formats.decimal(value, 0) else value.toLong().toString(), formats)

@StringRes
private fun reasonOf(key: String): Int? = when (key) {
    "sensing_to" -> R.string.profiles_reason_sensing_to
    "idle_to" -> R.string.profiles_reason_idle_to
    "max_idle_to" -> R.string.profiles_reason_max_idle_to
    "idle_factor" -> R.string.profiles_reason_idle_factor
    "idle_after_inactive_to" -> R.string.profiles_reason_idle_after_inactive_to
    else -> null
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
