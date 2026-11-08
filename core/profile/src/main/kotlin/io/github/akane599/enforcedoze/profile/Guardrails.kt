// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import io.github.akane599.enforcedoze.parse.DeviceIdleSettings

/** Why a custom profile's constants are refused (SPEC 6.3). The UI words each case; the engine logs it. */
public sealed interface GuardrailProblem {
    public val key: String?

    /** The string encoder would refuse it (key or value syntax, too many keys; ADR 0012). */
    public data class Invalid(override val key: String?, val detail: String) : GuardrailProblem

    public data class NotANumber(override val key: String) : GuardrailProblem

    public data class BelowMinimum(override val key: String, val minimum: Double) : GuardrailProblem

    public data class AboveMaximum(override val key: String, val maximum: Double) : GuardrailProblem

    /** An allowlist duration below the device's own value: SMS, MMS and notification exemptions would break. */
    public data class BelowStock(override val key: String, val stock: Long) : GuardrailProblem

    /** An allowlist duration cannot be checked before `stock.json` exists, so it is refused until then. */
    public data class StockUnknown(override val key: String) : GuardrailProblem
}

/** The SPEC 6.3 guardrails. The built-in profiles pass them; custom and imported profiles are checked on save. */
public object Guardrails {
    private const val SECOND = 1_000.0
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val MAX_FACTOR = 4.0

    /** Inclusive bounds per key; null means unbounded on that side. */
    private val BOUNDS: Map<String, Pair<Double?, Double?>> = mapOf(
        "sensing_to" to (10 * SECOND to null),
        "idle_to" to (5 * MINUTE to null),
        "max_idle_to" to (null to 24 * HOUR),
        "idle_factor" to (1.0 to MAX_FACTOR),
        // 0 passes straight through IDLE_PENDING on API 37 only (ADR 0013).
        "idle_after_inactive_to" to (1 * SECOND to null),
    )

    /** Keep SMS, MMS, notification and temporary allowlist exemptions working (ADR 0012). */
    public val ALLOWLIST_DURATION_KEYS: Set<String> = setOf(
        "max_temp_app_allowlist_duration_ms",
        "mms_temp_app_allowlist_duration_ms",
        "sms_temp_app_allowlist_duration_ms",
        "notification_allowlist_duration_ms",
    )

    /**
     * Problems with [deviceIdle], empty when it may be written. [stock] holds the device's own values in
     * milliseconds from `stock.json`, or null before it was captured.
     */
    public fun check(deviceIdle: Map<String, String>, stock: Map<String, Long>?): List<GuardrailProblem> {
        if (deviceIdle.isEmpty()) return emptyList()
        val problems = mutableListOf<GuardrailProblem>()
        DeviceIdleSettings.problems(deviceIdle).forEach { problems += GuardrailProblem.Invalid(keyOf(it, deviceIdle), it) }
        for ((key, text) in deviceIdle) {
            val bounds = BOUNDS[key]
            val guarded = bounds != null || key in ALLOWLIST_DURATION_KEYS
            val value = text.toDoubleOrNull()
            when {
                !guarded -> Unit
                value == null -> problems += GuardrailProblem.NotANumber(key)
                bounds != null -> bounded(key, value, bounds)?.let { problems += it }
                else -> stockFloor(key, value, stock)?.let { problems += it }
            }
        }
        return problems
    }

    private fun bounded(key: String, value: Double, bounds: Pair<Double?, Double?>): GuardrailProblem? {
        val (min, max) = bounds
        return when {
            min != null && value < min -> GuardrailProblem.BelowMinimum(key, min)
            max != null && value > max -> GuardrailProblem.AboveMaximum(key, max)
            else -> null
        }
    }

    private fun stockFloor(key: String, value: Double, stock: Map<String, Long>?): GuardrailProblem? {
        val floor = stock?.get(key) ?: return GuardrailProblem.StockUnknown(key)
        return if (value < floor) GuardrailProblem.BelowStock(key, floor) else null
    }

    /** `DeviceIdleSettings.problems` names the key in its message; find it so the UI can point at the field. */
    private fun keyOf(problem: String, deviceIdle: Map<String, String>): String? =
        deviceIdle.keys.firstOrNull { problem == "invalid key: $it" || problem.startsWith("invalid value for $it: ") }
}
