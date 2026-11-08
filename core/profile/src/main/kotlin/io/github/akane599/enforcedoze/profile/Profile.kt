// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

/** Lever C (SPEC 5.4, 6.4 `sensors`): left alone, restricted mode, or every sensor off through sensor privacy. */
public enum class SensorLever { OFF, RESTRICTED, ALL_SENSORS_OFF }

/** Lever D (SPEC 6.4 `allowlist`): the owner's allowlist as it is, or only the apps marked "keep". */
public enum class AllowlistLever { INHERIT, TRIMMED }

/** Lever B (SPEC 5.3): forced deep idle at screen off, after [delaySec] (0 = at once; non-zero needs an alarm). */
public data class ForceIdleLever(val enabled: Boolean, val delaySec: Int = 0) {
    init {
        require(delaySec >= 0) { "delaySec must not be negative" }
    }
}

public data class Levers(
    /** Lever A: write [Profile.deviceIdle] as the global `device_idle_constants` string (ADR 0012). */
    val constants: Boolean,
    val forceIdle: ForceIdleLever,
    val sensors: SensorLever,
    val allowlist: AllowlistLever,
)

/**
 * A named set of lever settings (SPEC 6). [deviceIdle] holds lever A's keys in the order they are written,
 * with values as they go into the string: milliseconds, factors or flags. A key left out means stock
 * (ADR 0012).
 */
public data class Profile(
    val id: String,
    val name: String,
    val levers: Levers,
    val deviceIdle: Map<String, String> = emptyMap(),
    val builtIn: Boolean = false,
) {
    /** The keys lever A writes: none when the constants lever is off. */
    val constants: Map<String, String> get() = if (levers.constants) deviceIdle else emptyMap()

    public companion object {
        /** Profile file schema (SPEC 6.4). */
        public const val SCHEMA: Int = 1
    }
}
