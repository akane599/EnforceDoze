// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

/**
 * The six built-in profiles of SPEC 6.1 with the constants of 6.2. Every key was checked against AOSP
 * `DeviceIdleController` for API 36 and 37 (ADR 0013); keys the table marks "stock" are left out.
 */
public object BuiltInProfiles {
    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE

    private fun ms(value: Long) = value.toString()

    private val NONE = Levers(
        constants = false,
        forceIdle = ForceIdleLever(enabled = false),
        sensors = SensorLever.OFF,
        allowlist = AllowlistLever.INHERIT,
    )

    /** Logging only: the baseline every report compares with. */
    public val STOCK: Profile = Profile("stock", "Stock", NONE, builtIn = true)

    /** Lever A: deep idle within about 10 minutes of a still, screen-off phone. */
    public val EVERYDAY: Profile = Profile(
        id = "everyday",
        name = "Everyday",
        levers = NONE.copy(constants = true),
        deviceIdle = linkedMapOf(
            "inactive_to" to ms(5 * MINUTE),
            "idle_after_inactive_to" to ms(5 * MINUTE),
            "sensing_to" to ms(1 * MINUTE),
            "locating_to" to ms(15 * SECOND),
            "motion_inactive_to" to ms(5 * MINUTE),
            "light_after_inactive_to" to ms(1 * MINUTE),
        ),
        builtIn = true,
    )

    /** Levers A and C: motion no longer resets the walk, so deep idle holds in a pocket. */
    public val POCKET: Profile = Profile(
        id = "pocket",
        name = "Pocket",
        levers = NONE.copy(constants = true, sensors = SensorLever.RESTRICTED),
        deviceIdle = linkedMapOf(
            "inactive_to" to ms(5 * MINUTE),
            "idle_after_inactive_to" to ms(3 * MINUTE),
            "sensing_to" to ms(30 * SECOND),
            "locating_to" to ms(15 * SECOND),
            "light_after_inactive_to" to ms(1 * MINUTE),
        ),
        builtIn = true,
    )

    /** Levers A (night values) and C: the longest gaps between maintenance windows. */
    public val NIGHT: Profile = Profile(
        id = "night",
        name = "Night",
        levers = NONE.copy(constants = true, sensors = SensorLever.RESTRICTED),
        deviceIdle = linkedMapOf(
            "inactive_to" to ms(2 * MINUTE),
            "idle_after_inactive_to" to ms(2 * MINUTE),
            "sensing_to" to ms(30 * SECOND),
            "locating_to" to ms(10 * SECOND),
            "light_after_inactive_to" to ms(1 * MINUTE),
            "idle_to" to ms(2 * HOUR),
            "max_idle_to" to ms(8 * HOUR),
            "idle_factor" to "2",
        ),
        builtIn = true,
    )

    /**
     * Levers B (delay 0) and C: upstream EnforceDoze's behaviour. Upstream disables motion sensing by default,
     * so C is on (docs/upstream-audit.md §4.1). Forced idle skips the walk, so no walk timers are written.
     */
    public val CLASSIC: Profile = Profile(
        id = "classic",
        name = "Classic",
        levers = NONE.copy(forceIdle = ForceIdleLever(enabled = true), sensors = SensorLever.RESTRICTED),
        builtIn = true,
    )

    /** Levers A (strict values), B and C, allowlist trimmed. Never recommended automatically. */
    public val STRICT: Profile = Profile(
        id = "strict",
        name = "Strict",
        levers = Levers(
            constants = true,
            forceIdle = ForceIdleLever(enabled = true),
            sensors = SensorLever.RESTRICTED,
            allowlist = AllowlistLever.TRIMMED,
        ),
        deviceIdle = linkedMapOf(
            "idle_to" to ms(2 * HOUR),
            "max_idle_to" to ms(12 * HOUR),
            "idle_factor" to "2",
        ),
        builtIn = true,
    )

    public val ALL: List<Profile> = listOf(STOCK, EVERYDAY, POCKET, NIGHT, CLASSIC, STRICT)

    public fun byId(id: String): Profile? = ALL.firstOrNull { it.id == id }
}
