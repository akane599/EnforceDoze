// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.Context
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.evidence.Verdicts
import io.github.akane599.enforcedoze.profile.Profile
import java.time.Duration

/** Release builds have no test profiles and the spec's thresholds: 10-minute sessions, 4-hour nights (SPEC 13.2: never in release). */
internal object DebugHooks {
    fun profiles(): List<Profile> = emptyList()

    @Suppress("UNUSED_PARAMETER", "UnusedParameter") // Same signature as the debug build's.
    fun minimumSession(context: Context): Duration = Verdicts.MINIMUM

    @Suppress("UNUSED_PARAMETER", "UnusedParameter") // Same signature as the debug build's.
    fun nightThreshold(context: Context): Duration = NightReports.THRESHOLD
}
