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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The recommendation (SPEC 7) from the app's own session records and the phone's own history before them, the owner's
 * answer to Q and the feedback loop. With neither it is null: the screens say there is not enough history rather than
 * guess.
 */
internal class Recommendations(private val services: AppServices) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val current: Flow<Recommendation?> = combine(
        services.evidence,
        services.prefs.changes,
        services.profiles,
        services.accessChanged,
    ) { _, _, profiles, _ -> profiles }.mapLatest { profiles -> compute(profiles) }

    /** The history range the signals rest on (SPEC 9.1 Recommended "the history range used"). */
    data class Range(val from: Instant, val to: Instant)

    private val lastRange = MutableStateFlow<Range?>(null)

    /** The range of the last recommendation computed; null before one. */
    val range: StateFlow<Range?> = lastRange.asStateFlow()

    /**
     * The app's own sessions of the last 30 days, and before the first of them the phone's own history (SPEC 7.1): the
     * first recommendation needs no recorded night, and the app's records take over as they accumulate. The range
     * starts where the records do, so a short history is not spread over 30 days.
     */
    suspend fun compute(profiles: List<Profile>, now: Instant = Instant.now()): Recommendation? {
        val earliest = now.minus(HISTORY)
        val byId = profiles.associateBy { it.id }
        val own = services.graph.sessions.sessionsBetween(earliest.toEpochMilli(), now.toEpochMilli()).map { session(it, byId) }
        val firstOwn = own.minOfOrNull { it.start }
        val dayZero = if (firstOwn == null || firstOwn > earliest) services.dayZero.result() else null
        val before = dayZero?.sessions.orEmpty().filter { firstOwn == null || it.end <= firstOwn }
        val sessions = before + own
        val start = listOfNotNull(dayZero?.from, firstOwn).minOrNull()
        if (sessions.isEmpty() || start == null) return null
        val range = Range(maxOf(earliest, start), now)
        val history = History(range.from, range.to, ZoneId.systemDefault(), sessions, services.prefs.feedback)
        return RecommendationPolicy.recommend(history, Answers(services.prefs.urgency)).also { lastRange.value = range }
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
