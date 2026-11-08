// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.Context
import androidx.core.content.edit
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.evidence.Verdicts
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import java.time.Duration

/** Debug builds only: the compressed-night test profiles and injectable session and night thresholds (SPEC 13.2). */
internal object DebugHooks {
    /** SPEC 13.2 step 2, in milliseconds as lever A writes them. */
    private val COMPRESSED = linkedMapOf(
        "inactive_to" to "30000",
        "idle_after_inactive_to" to "30000",
        "sensing_to" to "10000",
        "locating_to" to "5000",
        "idle_to" to "60000",
        "max_idle_to" to "120000",
    )

    /** Everyday with the compressed walk and idle timers. */
    val TEST_EVERYDAY: Profile = BuiltInProfiles.EVERYDAY.copy(
        id = "test-everyday",
        name = "Test Everyday",
        deviceIdle = BuiltInProfiles.EVERYDAY.deviceIdle + COMPRESSED,
        builtIn = false,
    )

    /** Classic (forced idle and sensor restriction) plus the compressed idle timers, so maintenance windows come every minute. */
    val TEST_CLASSIC: Profile = BuiltInProfiles.CLASSIC.copy(
        id = "test-classic",
        name = "Test Classic",
        levers = BuiltInProfiles.CLASSIC.levers.copy(constants = true),
        deviceIdle = COMPRESSED,
        builtIn = false,
    )

    private const val PREFS = "debug"
    private const val MINIMUM_SECONDS = "minimum_session_seconds"
    private const val NIGHT_SECONDS = "night_threshold_seconds"

    fun profiles(): List<Profile> = listOf(TEST_EVERYDAY, TEST_CLASSIC)

    fun minimumSession(context: Context): Duration {
        val seconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(MINIMUM_SECONDS, -1)
        return if (seconds < 0) Verdicts.MINIMUM else Duration.ofSeconds(seconds)
    }

    fun setMinimumSession(context: Context, seconds: Long?) = set(context, MINIMUM_SECONDS, seconds)

    /** SPEC 8.5's 4 hours, shortened by the compressed-night test. */
    fun nightThreshold(context: Context): Duration {
        val seconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(NIGHT_SECONDS, -1)
        return if (seconds < 0) NightReports.THRESHOLD else Duration.ofSeconds(seconds)
    }

    fun setNightThreshold(context: Context, seconds: Long?) = set(context, NIGHT_SECONDS, seconds)

    private fun set(context: Context, key: String, seconds: Long?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (seconds == null) remove(key) else putLong(key, seconds)
        }
    }
}
