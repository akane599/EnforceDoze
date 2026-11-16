// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.data.StoredNights
import io.github.akane599.enforcedoze.feature.log.LogEntry
import io.github.akane599.enforcedoze.feature.log.LogSource
import io.github.akane599.enforcedoze.feature.log.LogState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Duration

/** The log (SPEC 9.1): every session kept, a night's sessions joined into its one row (ADR 0031). */
internal class AppLogSource(private val services: AppServices) : LogSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<LogState> = combine(
        services.evidence,
        services.profileNames,
        services.notices.notices,
        services.prefs.changes,
    ) { _, names, notices, _ -> names to notices }
        .mapLatest { (names, notices) ->
            val days = services.prefs.retentionDays
            val now = System.currentTimeMillis()
            val from = now - Duration.ofDays(days.toLong()).toMillis()
            val sessions = services.graph.sessions.sessionsBetween(from, now)
            val nights = services.graph.sessions.nightsBetween(from, now)
            LogState(entries(sessions, nights), names, days, notices)
        }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)

    companion object {
        /** Newest first. A session inside a stored night is listed only through that night's row. */
        fun entries(sessions: List<SessionRow>, nights: List<NightRow>): List<LogEntry> {
            val nightRows = nights.map { night ->
                val inside = sessions.filter { it.start >= night.start && it.end <= night.end }
                LogEntry(
                    sessionId = night.sessionId,
                    start = night.start,
                    end = night.end,
                    verdict = ReportMapping.verdict(NightReports.verdict(inside).first),
                    drainPerHour = night.drainPerHour,
                    // A night is unplugged screen-off time by definition (SPEC 8.5, ADR 0031).
                    plugged = false,
                    profileId = night.profileId,
                    night = true,
                    sessions = night.sessionCount,
                )
            }
            val single = sessions.filter { session -> nights.none { session.start >= it.start && session.end <= it.end } }.map {
                LogEntry(
                    sessionId = it.id,
                    start = it.start,
                    end = it.end,
                    verdict = ReportMapping.verdict(it.verdict),
                    drainPerHour = it.drainPerHour,
                    plugged = !StoredNights.unplugged(it),
                    profileId = it.profileId,
                )
            }
            return (nightRows + single).sortedByDescending { it.start }
        }
    }
}
