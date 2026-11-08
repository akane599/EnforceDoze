// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

/** Lever C (SPEC 5.4, 6.4 `sensors`): left alone, restricted mode, or every sensor off through sensor privacy. */
public enum class SensorLever { OFF, RESTRICTED, ALL_SENSORS_OFF }

/** Lever D (SPEC 6.4 `allowlist`): the owner's allowlist as it is, or only the apps marked "keep". */
public enum class AllowlistLever { INHERIT, TRIMMED }

/**
 * Lever B (SPEC 5.3): forced deep idle at screen off, after [delaySec] (0 = at once; non-zero needs an
 * alarm). With [untilUnlock], forced idle and the sensor lever stay on when the screen comes on at the
 * lock screen and are released at unlock (upstream's "wait for unlock", ADR 0017).
 */
public data class ForceIdleLever(val enabled: Boolean, val delaySec: Int = 0, val untilUnlock: Boolean = false) {
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

    /** A custom copy to edit (SPEC 6.3), with an id none of [taken] uses. */
    public fun cloneAs(name: String, taken: Set<String>): Profile =
        copy(id = freshId(slug(name).ifEmpty { id }, taken), name = name, builtIn = false)

    public companion object {
        /** Profile file schema (SPEC 6.4). */
        public const val SCHEMA: Int = 1

        /** What an id may hold: it names a file and a log column (ProfileFiles). */
        public val ID: Regex = Regex("""[A-Za-z0-9._-]{1,64}""")

        /** [base] if no profile uses it, else the first of `base-2`, `base-3`, … that is free. */
        public fun freshId(base: String, taken: Set<String>): String =
            if (base !in taken) base else generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }

        private fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_SLUG)

        private const val MAX_SLUG = 40
    }
}
