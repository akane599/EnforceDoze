// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Guardrails
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import java.math.BigDecimal
import java.math.RoundingMode

/** The unit a timing is typed in. Values are stored in milliseconds; a factor and any other key as written. */
public enum class TimingUnit(internal val millis: Long) { SECONDS(1_000), MINUTES(60_000), HOURS(3_600_000), FACTOR(1), RAW(1) }

/**
 * The timings of SPEC 6.2 in the order the editor and the detail list them, each with the unit it reads
 * best in. Key meanings: ADR 0013, checked against AOSP `DeviceIdleController`.
 */
public val Timings: List<Pair<String, TimingUnit>> = listOf(
    "inactive_to" to TimingUnit.MINUTES,
    "idle_after_inactive_to" to TimingUnit.MINUTES,
    "sensing_to" to TimingUnit.SECONDS,
    "locating_to" to TimingUnit.SECONDS,
    "motion_inactive_to" to TimingUnit.MINUTES,
    "light_after_inactive_to" to TimingUnit.MINUTES,
    "idle_to" to TimingUnit.MINUTES,
    "idle_factor" to TimingUnit.FACTOR,
    "max_idle_to" to TimingUnit.HOURS,
)

public fun unitOf(key: String): TimingUnit = Timings.firstOrNull { it.first == key }?.second ?: TimingUnit.RAW

/** The keys a profile shows: the standard timings, then any other key it carries. */
public fun keysOf(profile: Profile): List<String> =
    Timings.map { it.first } + profile.deviceIdle.keys.filter { unitOf(it) == TimingUnit.RAW }

/**
 * A custom profile as the editor holds it while the owner types (SPEC 6.3). [values] holds each key's
 * text in its unit; blank leaves the key out, so the phone's own value applies (ADR 0012).
 */
@Immutable
public data class Draft(
    val id: String,
    val name: String,
    val constants: Boolean,
    val forceIdle: Boolean,
    val delaySec: String,
    val untilUnlock: Boolean,
    val sensors: SensorLever,
    val allowlist: AllowlistLever,
    val values: Map<String, String>,
)

/** Why a draft cannot be saved yet. Guardrail cases come from [Guardrails], the rest from the fields. */
public sealed interface DraftProblem {
    /** The field the problem belongs to, or null for the name and the delay. */
    public val key: String? get() = null

    public data object NameEmpty : DraftProblem

    public data object DelayNotANumber : DraftProblem

    public data class NotANumber(override val key: String) : DraftProblem

    public data class Guardrail(val problem: GuardrailProblem) : DraftProblem {
        override val key: String? get() = problem.key
    }
}

public fun Profile.toDraft(): Draft = Draft(
    id = id,
    name = name,
    constants = levers.constants,
    forceIdle = levers.forceIdle.enabled,
    delaySec = levers.forceIdle.delaySec.toString(),
    untilUnlock = levers.forceIdle.untilUnlock,
    sensors = levers.sensors,
    allowlist = levers.allowlist,
    values = keysOf(this).associateWith { key -> deviceIdle[key]?.let { toUnit(it, unitOf(key)) }.orEmpty() },
)

/** The profile this draft saves as, or null while a field does not hold a number. */
public fun Draft.toProfile(): Profile? {
    val delay = delaySec.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val deviceIdle = LinkedHashMap<String, String>()
    for ((key, text) in values) {
        if (text.isBlank()) continue
        deviceIdle[key] = fromUnit(text, unitOf(key)) ?: return null
    }
    return Profile(
        id = id,
        name = name.trim(),
        levers = Levers(constants, ForceIdleLever(forceIdle, delay, untilUnlock), sensors, allowlist),
        deviceIdle = deviceIdle,
    )
}

/** Everything that stops a save, field problems first. [stock] is `stock.json` in milliseconds, or null. */
public fun Draft.problems(stock: Map<String, Long>?): List<DraftProblem> {
    val out = mutableListOf<DraftProblem>()
    if (name.isBlank()) out += DraftProblem.NameEmpty
    if (forceIdle && delaySec.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } == null) out += DraftProblem.DelayNotANumber
    values.filter { (key, text) -> text.isNotBlank() && fromUnit(text, unitOf(key)) == null }.keys.forEach {
        out +=
            DraftProblem.NotANumber(it)
    }
    if (out.any { it is DraftProblem.NotANumber }) return out
    val profile = toProfile() ?: return out
    // The guardrails apply to what lever A would write; with A off nothing is written.
    if (constants) Guardrails.check(profile.deviceIdle, stock).forEach { out += DraftProblem.Guardrail(it) }
    return out
}

/** Places kept when milliseconds are shown in a larger unit: 100000 ms reads "1.667" minutes. */
private const val UNIT_PLACES = 3

/** "300000" in minutes reads "5"; "90000" reads "1.5". A factor or another key stays as written. */
internal fun toUnit(stored: String, unit: TimingUnit): String = when (unit) {
    TimingUnit.FACTOR, TimingUnit.RAW -> stored

    else -> stored.toBigDecimalOrNull()?.divide(
        BigDecimal(unit.millis),
        UNIT_PLACES,
        RoundingMode.HALF_UP,
    )?.stripTrailingZeros()?.toPlainString()
        ?: stored
}

/** The stored text for what the owner typed, accepting a decimal comma; null if it is not a number ≥ 0. */
internal fun fromUnit(typed: String, unit: TimingUnit): String? {
    val text = typed.trim()
    if (unit == TimingUnit.RAW) return text
    val number = text.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return null
    return when (unit) {
        TimingUnit.FACTOR -> number.stripTrailingZeros().toPlainString()
        else -> number.multiply(BigDecimal(unit.millis)).setScale(0, RoundingMode.HALF_UP).toPlainString()
    }
}
