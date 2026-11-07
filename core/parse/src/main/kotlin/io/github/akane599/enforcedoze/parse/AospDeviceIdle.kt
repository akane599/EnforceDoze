// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * Facts from AOSP `DeviceIdleController` (`apex/jobscheduler/service/java/com/android/server/
 * DeviceIdleController.java`), checked at tags `android-16.0.0_r1` and `android-17.0.0_r1`, where they
 * are identical.
 */
public object AospDeviceIdle {
    /** Every `KEY_*` constant of `DeviceIdleController.Constants`: the keys it reads from settings. */
    public val KEYS: Set<String> = setOf(
        "flex_time_short",
        "idle_after_inactive_to",
        "idle_factor",
        "idle_pending_factor",
        "idle_pending_to",
        "idle_to",
        "inactive_to",
        "light_after_inactive_to",
        "light_idle_factor",
        "light_idle_flex_linear_increase_factor_ms",
        "light_idle_increase_linearly",
        "light_idle_linear_increase_factor_ms",
        "light_idle_maintenance_max_budget",
        "light_idle_maintenance_min_budget",
        "light_idle_to",
        "light_idle_to_initial_flex",
        "light_max_idle_to",
        "light_max_idle_to_flex",
        "locating_to",
        "location_accuracy",
        "max_idle_pending_to",
        "max_idle_to",
        "max_temp_app_allowlist_duration_ms",
        "min_deep_maintenance_time",
        "min_light_maintenance_time",
        "min_time_to_alarm",
        "mms_temp_app_allowlist_duration_ms",
        "motion_inactive_to",
        "motion_inactive_to_flex",
        "notification_allowlist_duration_ms",
        "quick_doze_delay_to",
        "sensing_to",
        "sms_temp_app_allowlist_duration_ms",
        "use_mode_manager",
        "use_window_alarms",
        "wait_for_unlock",
    )

    /** Free-text lines AOSP prints at the top level of `dumpsys deviceidle` (not `key=value`). */
    internal val KNOWN_TEXT_LINES: List<Regex> = listOf(
        Regex("""\d+ stationary listeners registered"""),
        Regex("""Location prefetching (?:enabled|disabled)"""),
    )
}
