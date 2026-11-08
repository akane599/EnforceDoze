// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.SettingValue
import java.time.Duration

/** How long a still, unplugged, screen-off phone should take to reach deep idle (SPEC 8.4 "expected time"). */
public object ExpectedWalk {
    /**
     * The timers `DeviceIdleController` waits through from screen off to `IDLE`: `inactive_to` (to
     * `IDLE_PENDING`), `idle_after_inactive_to` (to `SENSING`), `sensing_to` (to `LOCATING`) and
     * `locating_to` (to `IDLE`), as at `android-17.0.0_r1` `stepIdleStateLocked`. Each is an upper bound.
     */
    public val KEYS: List<String> = listOf("inactive_to", "idle_after_inactive_to", "sensing_to", "locating_to")

    /**
     * Zero when the profile forces idle; otherwise the sum of [KEYS], each taken from the first of [sources]
     * that has it (the profile's own values, then the captured stock values), or null when one is unknown.
     * Values are milliseconds or `dumpsys` durations.
     */
    public fun of(forcedIdle: Boolean, vararg sources: Map<String, String>): Duration? {
        if (forcedIdle) return Duration.ZERO
        val millis = KEYS.map { key -> sources.firstNotNullOfOrNull { it[key]?.let(::SettingValue)?.longValue } ?: return null }
        return Duration.ofMillis(millis.sum())
    }
}
