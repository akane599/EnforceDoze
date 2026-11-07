// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * Deep idle states with their codes and names from AOSP `DeviceIdleController` (`STATE_*`,
 * `stateToString`), identical at `android-16.0.0_r1` and `android-17.0.0_r1`. The code is what the
 * `device_idle` event-log tag carries; the name is what `dumpsys deviceidle` prints as `mState`.
 */
public enum class DeepState(public val code: Int) {
    ACTIVE(0),
    INACTIVE(1),
    IDLE_PENDING(2),
    SENSING(3),
    LOCATING(4),
    IDLE(5),
    IDLE_MAINTENANCE(6),
    QUICK_DOZE_DELAY(7),
    ;

    public companion object {
        public fun fromName(name: String): DeepState? = entries.firstOrNull { it.name == name }

        public fun fromCode(code: Int): DeepState? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Light idle states from AOSP `DeviceIdleController` (`LIGHT_STATE_*`, `lightStateToString`) at the
 * same tags. Codes 2 and 3 are unused there.
 */
public enum class LightState(public val code: Int) {
    ACTIVE(0),
    INACTIVE(1),
    IDLE(4),
    WAITING_FOR_NETWORK(5),
    IDLE_MAINTENANCE(6),
    OVERRIDE(7),
    ;

    public companion object {
        public fun fromName(name: String): LightState? = entries.firstOrNull { it.name == name }

        public fun fromCode(code: Int): LightState? = entries.firstOrNull { it.code == code }
    }
}
