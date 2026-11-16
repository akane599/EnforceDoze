// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.AppliedPlan
import io.github.akane599.enforcedoze.recommend.Feedback
import io.github.akane599.enforcedoze.recommend.Urgency
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.Instant
import java.time.LocalTime

/**
 * The owner's answers and choices that are the app's own record, not the engine's: onboarding, the urgency
 * question, the plans applied and the late arrivals (the feedback loop's input, SPEC 7.5), retention, the
 * keep-awake apps, the declined recommendation and the warnings already seen.
 */
internal class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Emits once at the start and after every change, so a screen's state can be recomputed from the getters. */
    val changes: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    var onboarded: Boolean
        get() = prefs.getBoolean(ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(ONBOARDED, value) }

    /** The notification permission was asked for once (SPEC 9.2); Android shows the question once anyway. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean(NOTIFICATIONS_ASKED, false)
        set(value) = prefs.edit { putBoolean(NOTIFICATIONS_ASKED, value) }

    var urgency: Urgency
        get() = prefs.getString(URGENCY, null)?.let { runCatching { Urgency.valueOf(it) }.getOrNull() } ?: Urgency.WITHIN_THE_HOUR
        set(value) = prefs.edit { putString(URGENCY, value.name) }

    /** Records are kept this many days (SPEC 8.3, 30 by default); raw dumps keep their own 7 days. */
    var retentionDays: Int
        get() = prefs.getInt(RETENTION, DEFAULT_RETENTION_DAYS)
        set(value) = prefs.edit { putInt(RETENTION, value) }

    var automation: Boolean
        get() = prefs.getBoolean(AUTOMATION, false)
        set(value) = prefs.edit { putBoolean(AUTOMATION, value) }

    /** "Follow Recommended automatically" (SPEC 9.1 Plan). */
    var followRecommended: Boolean
        get() = prefs.getBoolean(FOLLOW, false)
        set(value) = prefs.edit { putBoolean(FOLLOW, value) }

    /** The apps whose being on screen at screen off holds Doze off for that session (SPEC 5.5). */
    var keepAwake: Set<String>
        get() = prefs.getStringSet(KEEP_AWAKE, null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEEP_AWAKE, value) }

    var seenNotices: Set<String>
        get() = prefs.getStringSet(SEEN, null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(SEEN, value) }

    /** The recommendation the owner turned down, so it is not offered again until it changes (SPEC 7.4). */
    var declined: Plan?
        get() = prefs.getString(DECLINED, null)?.let(::decodePlan)
        set(value) = prefs.edit { putString(DECLINED, value?.let(::encodePlan)) }

    /** Every plan the owner applied and every late arrival they reported, oldest first (SPEC 7.5, ADR 0026). */
    val feedback: Feedback
        get() = Feedback(
            applied = prefs.getString(APPLIED, null).orEmpty().lines().mapNotNull(::decodeApplied),
            lateArrivals = prefs.getString(LATE, null).orEmpty().lines().mapNotNull {
                it.substringBefore(';').toLongOrNull()?.let(Instant::ofEpochMilli)
            },
        )

    fun applied(plan: Plan, at: Instant) {
        val lines = prefs.getString(APPLIED, null).orEmpty().lines().filter { it.isNotBlank() }
        if (lines.lastOrNull()?.let(::decodeApplied)?.plan == plan) return
        prefs.edit { putString(APPLIED, (lines + "${at.toEpochMilli()};${encodePlan(plan)}").takeLast(KEPT_LINES).joinToString("\n")) }
    }

    /** "Something arrived late" (SPEC 7.5) for the report of [sessionId], with the app the owner named, if any. */
    fun late(at: Instant, sessionId: Long, packageName: String?) {
        val line = "${at.toEpochMilli()};$sessionId;${packageName.orEmpty()}"
        prefs.edit { putString(LATE, (lateLines() + line).takeLast(KEPT_LINES).joinToString("\n")) }
    }

    /** Whether the owner already reported a late arrival from the report of [sessionId]. */
    fun lateReported(sessionId: Long): Boolean = lateLines().any { it.split(';').getOrNull(1)?.toLongOrNull() == sessionId }

    /** The apps named in late reports, the latest report per app, newest first: allowlist suggestions (SPEC 9.1). */
    val lateApps: List<Pair<String, Long>>
        get() = lateLines().mapNotNull { line ->
            val parts = line.split(';')
            val app = parts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            parts[0].toLongOrNull()?.let { app to it }
        }.groupBy({ it.first }, { it.second }).map { (app, times) -> app to times.max() }.sortedByDescending { it.second }

    private fun lateLines(): List<String> = prefs.getString(LATE, null).orEmpty().lines().filter { it.isNotBlank() }

    /** Tells the screens to read again after a choice saved outside these preferences (the engine's state file). */
    fun touch() = prefs.edit { putLong(TOUCHED, System.nanoTime()) }

    fun seen(noticeId: String) {
        if (noticeId !in seenNotices) seenNotices = seenNotices + noticeId
    }

    /** Reset (SPEC 9.1 Settings): the app's own record goes; the engine's files are cleared by the caller. */
    fun clear() = prefs.edit { clear() }

    private fun decodeApplied(line: String): AppliedPlan? {
        val since = line.substringBefore(';').toLongOrNull() ?: return null
        return decodePlan(line.substringAfter(';'))?.let { AppliedPlan(it, Instant.ofEpochMilli(since)) }
    }

    companion object {
        private const val PREFS = "ui"
        private const val ONBOARDED = "onboarded"
        private const val NOTIFICATIONS_ASKED = "notifications-asked"
        private const val URGENCY = "urgency"
        private const val RETENTION = "retention_days"
        private const val AUTOMATION = "automation"
        private const val FOLLOW = "follow_recommended"
        private const val KEEP_AWAKE = "keep_awake"
        private const val SEEN = "seen_notices"
        private const val DECLINED = "declined_plan"
        private const val APPLIED = "applied_plans"
        private const val LATE = "late_arrivals"
        private const val TOUCHED = "touched"
        private const val KEPT_LINES = 200
        private const val DAY_ONLY = 1
        private const val WITH_NIGHT = 4
        private const val END_PART = 3
        const val DEFAULT_RETENTION_DAYS = 30

        /** `day` or `day,night,HH:mm,HH:mm`. */
        fun encodePlan(plan: Plan): String =
            listOfNotNull(plan.dayProfileId, plan.night?.let { "${it.profileId},${it.start},${it.end}" }).joinToString(",")

        fun decodePlan(text: String): Plan? {
            val parts = text.split(',')
            return runCatching {
                when (parts.size) {
                    DAY_ONLY -> Plan(parts[0])
                    WITH_NIGHT -> Plan(parts[0], NightSlot(parts[1], LocalTime.parse(parts[2]), LocalTime.parse(parts[END_PART])))
                    else -> null
                }
            }.getOrNull()
        }
    }
}
