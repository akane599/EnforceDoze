// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.report.LateCandidate
import io.github.akane599.enforcedoze.feature.report.LateOutcome
import io.github.akane599.enforcedoze.feature.report.ReportSource
import io.github.akane599.enforcedoze.feature.report.ReportState
import io.github.akane599.enforcedoze.recommend.FeedbackLoop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant

/** The session and night report (SPEC 8.4, 8.5) with the shared warnings and "Something arrived late" (SPEC 7.5). */
internal class AppReportSource(private val services: AppServices, private val reports: Reports) : ReportSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun report(sessionId: Long): Flow<ReportState?> =
        combine(services.evidence, services.notices.notices, services.prefs.changes) { _, notices, _ -> notices }
            .mapLatest { notices ->
                reports.report(sessionId)?.let { report ->
                    report.copy(notices = notices, lateReported = services.prefs.lateReported(report.summary.sessionId))
                }
            }

    /**
     * Records the late arrival; the recommendation reads it through the feedback loop and steps one plan gentler for
     * 30 days (SPEC 7.5). The outcome names the profile the plan now steps to, or none when it is the gentlest.
     */
    override suspend fun late(sessionId: Long, packageName: String?): LateOutcome {
        services.prefs.late(Instant.now(), sessionId, packageName)
        val current = services.engine.first().plan?.plan
        val gentler = current?.let(FeedbackLoop::gentler)?.takeIf { it != current }
        val names = services.profileNames.first()
        // The slot that changed: the night's profile when the gentler plan changed it, else the day's.
        val changed = gentler?.let { plan -> plan.night?.profileId?.takeIf { it != current.night?.profileId } ?: plan.dayProfileId }
        val stepTo = changed?.let { names[it] ?: it }
        val app = packageName?.let { LateCandidate(services.labels.label(it) ?: it, it) }
        return LateOutcome(app, stepTo)
    }

    override fun seen(noticeId: String) = services.prefs.seen(noticeId)
}
