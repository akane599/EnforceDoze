// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import android.util.Log
import java.time.Duration
import java.time.Instant

/**
 * The alarm and messaging apps the owner uses most, for the allowlist's suggestions (SPEC 9.1). Public calls only:
 * the next alarm clock's creator, the default SMS app, and communication apps by foreground time (usage access).
 */
internal class UsedApps(private val context: Context) {
    /** The app that set the next alarm clock; null when no alarm clock is set. */
    fun alarm(): String? = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.showIntent?.creatorPackage

    /**
     * The default SMS app, then apps in the social and communication category used in the last week, most used first,
     * [MESSAGING] at most. Without usage access only the SMS app is known.
     */
    fun messaging(now: Instant = Instant.now()): List<String> {
        val sms = Telephony.Sms.getDefaultSmsPackage(context)
        val social = context.packageManager.getInstalledApplications(0)
            .filter { it.category == ApplicationInfo.CATEGORY_SOCIAL }
            .map { it.packageName }
        val usage = try {
            context.getSystemService(UsageStatsManager::class.java)
                .queryAndAggregateUsageStats(now.minus(WEEK).toEpochMilli(), now.toEpochMilli())
        } catch (denied: SecurityException) {
            Log.i(TAG, "no usage access: only the SMS app is suggested", denied)
            emptyMap()
        }
        val used = social.mapNotNull { pkg -> usage[pkg]?.totalTimeInForeground?.takeIf { it > 0 }?.let { pkg to it } }
            .sortedByDescending { it.second }
            .map { it.first }
        return (listOfNotNull(sms) + used).distinct().take(MESSAGING)
    }

    private companion object {
        const val TAG = "UsedApps"
        val WEEK: Duration = Duration.ofDays(7)
        const val MESSAGING = 3
    }
}
