// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.evidence

import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SessionDao
import io.github.akane599.enforcedoze.data.SessionRow

/**
 * The last result as Home words it (SPEC 9.1): the latest session, or the night it belongs to (ADR 0031) with the
 * night's verdict. [deepShare] is null when there is no verdict: a session never judged has no share, not 0 (ADR
 * 0019). [sessionId] is the session the report opens on.
 */
internal data class LastResult(
    val sessionId: Long,
    val start: Long,
    val end: Long,
    val verdict: String?,
    val deepShare: Double?,
    val drainPerHour: Double?,
    val profileId: String?,
)

/** One "last verdict" for the automation API and the evidence provider (ADR 0035). */
internal object LastResults {
    suspend fun latest(sessions: SessionDao): LastResult? {
        val session = sessions.latestSessions(1).firstOrNull() ?: return null
        val night = sessions.nightAround(session.start, session.end)
        val inside = night?.let { n -> sessions.sessionsBetween(n.start, n.end).filter { it.start >= n.start && it.end <= n.end } }
        return of(session, night, inside.orEmpty())
    }

    fun of(session: SessionRow, night: NightRow?, inside: List<SessionRow>): LastResult {
        if (night == null) {
            return LastResult(
                sessionId = session.id,
                start = session.start,
                end = session.end,
                verdict = session.verdict,
                deepShare = session.deepShare.takeIf { session.verdict != null },
                drainPerHour = session.drainPerHour,
                profileId = session.profileId,
            )
        }
        val verdict = NightReports.verdict(inside).first
        return LastResult(
            sessionId = night.sessionId,
            start = night.start,
            end = night.end,
            verdict = verdict,
            deepShare = night.deepShare.takeIf { verdict != null },
            drainPerHour = night.drainPerHour,
            profileId = night.profileId,
        )
    }
}
