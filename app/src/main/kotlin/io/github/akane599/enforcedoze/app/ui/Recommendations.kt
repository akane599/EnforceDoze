// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.data.StoredNights
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import io.github.akane599.enforcedoze.recommend.Answers
import io.github.akane599.enforcedoze.recommend.History
import io.github.akane599.enforcedoze.recommend.Provenance
import io.github.akane599.enforcedoze.recommend.Recommendation
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy
import io.github.akane599.enforcedoze.recommend.ScreenOffSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The recommendation (SPEC 7) from the app's own session records, the owner's answer to Q and the feedback loop.
 * With no session recorded yet it is null: the screens say there is not enough history rather than guess.
 */
internal class Recommendations(private val services: AppServices) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val current: Flow<Recommendation?> = combine(
        services.evidence,
        services.prefs.changes,
        services.profiles,
    ) { _, _, profiles -> profiles }.mapLatest { profiles -> compute(profiles) }

    /** The history range the signals rest on (SPEC 9.1 Recommended "the history range used"). */
    data class Range(val from: Instant, val to: Instant)

    fun range(now: Instant = Instant.now()): Range = Range(now.minus(HISTORY), now)

    suspend fun compute(profiles: List<Profile>, now: Instant = Instant.now()): Recommendation? {
        val range = range(now)
        val rows = services.graph.sessions.sessionsBetween(range.from.toEpochMilli(), range.to.toEpochMilli())
        if (rows.isEmpty()) return null
        val byId = profiles.associateBy { it.id }
        val history = History(range.from, range.to, ZoneId.systemDefault(), rows.map { session(it, byId) }, services.prefs.feedback)
        return RecommendationPolicy.recommend(history, Answers(services.prefs.urgency))
    }

    companion object {
        /** SPEC 7.2: the signals look back 30 days at most. */
        val HISTORY: Duration = Duration.ofDays(30)

        /** One recorded session as the policy reads it; a figure the row does not hold stays null. */
        fun session(row: SessionRow, profiles: Map<String, Profile>): ScreenOffSession {
            val profile = row.profileId?.let(profiles::get)
            val provenance = when {
                row.profileId == BuiltInProfiles.STOCK.id -> Provenance.STOCK
                profile != null -> Provenance.TUNED
                else -> Provenance.UNKNOWN
            }
            val judged = row.verdict != null
            val batteryStart = row.batteryStart
            val batteryEnd = row.batteryEnd
            return ScreenOffSession(
                start = Instant.ofEpochMilli(row.start),
                end = Instant.ofEpochMilli(row.end),
                plugged = !StoredNights.unplugged(row),
                provenance = provenance,
                sensorsRestricted = profile?.let { it.levers.sensors != SensorLever.OFF },
                expectedToDeep = row.expectedToDeepMillis?.let(Duration::ofMillis),
                // An unjudged session's deep time is incomplete (a gap hides part of it), so it is not a figure.
                timeInDeep = if (judged) Duration.ofMillis(row.timeInDeepMillis) else null,
                motionResets = if (judged) row.motionResets else null,
                batteryDrop = if (batteryStart != null && batteryEnd != null) (batteryStart - batteryEnd).toDouble() else null,
            )
        }
    }
}
