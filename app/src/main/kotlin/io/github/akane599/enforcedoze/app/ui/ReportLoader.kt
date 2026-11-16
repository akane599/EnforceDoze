// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.JournalDao
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SessionDao
import io.github.akane599.enforcedoze.data.SessionRecord
import io.github.akane599.enforcedoze.feature.report.ReportState

/**
 * Reads a report from the evidence log: the night the session belongs to (SPEC 8.5, ADR 0031), else the session's
 * own (SPEC 8.4). [profileName] may read the profile store; it is asked once per profile the report names. [app]
 * and [holdOff] are passed to the mapper as they are ([ReportNames]).
 */
internal class ReportLoader(
    private val sessions: SessionDao,
    private val journal: JournalDao,
    private val profileName: suspend (String) -> String,
    private val app: (uid: Int?, packages: List<String>) -> AppName?,
    private val holdOff: (String) -> String = { it },
) {
    /** The report, or null when the session is gone (retention, or a reset). */
    suspend fun report(sessionId: Long): ReportState? {
        val record = sessions.recordOf(sessionId) ?: return null
        night(record)?.let { return it }
        val since = NightReports.from(record.session)
        return ReportMapping.session(record, journal.eventsSince(since), journal.actionsSince(since), names(listOf(record), null))
    }

    /** The night around [record] when one is stored and still holds it; earlier nights come with their records. */
    private suspend fun night(record: SessionRecord): ReportState? {
        val row = record.session
        val night = sessions.nightAround(row.start, row.end) ?: return null
        val records = sessions.recordsOf(night).takeIf { list -> list.any { it.session.id == row.id } } ?: return null
        val since = NightReports.from(night)
        val report = NightReports.report(night, records, journal.eventsSince(since), journal.actionsSince(since))
        val earlier = sessions.nightsBefore(night.start, null, NightReports.RECENT).map { EarlierNight(it, sessions.recordsOf(it)) }
        return ReportMapping.night(report, records, earlier, names(records, night))
    }

    private suspend fun names(records: List<SessionRecord>, night: NightRow?): ReportNames {
        val ids = (records.mapNotNull { it.session.profileId } + listOfNotNull(night?.profileId)).distinct()
        val profiles = ids.associateWith { profileName(it) }
        return ReportNames(profile = { profiles[it] ?: it }, app = app, holdOff = holdOff)
    }
}
