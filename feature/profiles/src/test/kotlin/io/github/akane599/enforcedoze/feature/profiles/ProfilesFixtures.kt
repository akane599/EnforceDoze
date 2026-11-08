// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import java.time.LocalTime

/** Profile screens with the built-ins, one custom profile and a plan that runs Everyday by day and Night at night. */
object ProfilesFixtures {
    /** A custom profile that uses every lever, keeps a key that is not one of the timings, and sets one the phone lacks. */
    val nightOut = Profile(
        id = "night-out",
        name = "Night out",
        levers = Levers(
            constants = true,
            forceIdle = ForceIdleLever(enabled = true, delaySec = 300, untilUnlock = true),
            sensors = SensorLever.ALL_SENSORS_OFF,
            allowlist = AllowlistLever.TRIMMED,
        ),
        deviceIdle = linkedMapOf(
            "inactive_to" to "120000",
            "light_after_inactive_to" to "90000",
            "idle_to" to "3600000",
            "max_idle_to" to "21600000",
            "idle_factor" to "1.5",
            "quick_doze_delay_to" to "60000",
            "skip_sensing" to "true",
        ),
    )

    /** Illustrative stock values for the shots, close to AOSP's defaults; not a capture from any phone. */
    val stock = mapOf(
        "inactive_to" to 1_800_000L,
        "idle_after_inactive_to" to 1_800_000L,
        "sensing_to" to 240_000L,
        "locating_to" to 30_000L,
        "motion_inactive_to" to 600_000L,
        "light_after_inactive_to" to 180_000L,
        "idle_to" to 3_600_000L,
        "max_idle_to" to 21_600_000L,
        "idle_factor" to 2L,
    )

    val plan = Plan("everyday", NightSlot("night", LocalTime.of(23, 0), LocalTime.of(7, 30)))

    val state = ProfilesState(
        profiles = BuiltInProfiles.ALL + nightOut,
        plan = plan,
        recommended = setOf("everyday", "night"),
        stock = stock,
        missingKeys = setOf("light_after_inactive_to"),
    )

    /** Before onboarding: read-only, no custom profiles, no stock values yet. */
    val readOnly = ProfilesState(
        profiles = BuiltInProfiles.ALL,
        plan = Plan("stock"),
        canWrite = false,
        notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)),
    )
}
