// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord
import kotlinx.coroutines.flow.Flow

/**
 * One line of the log: a lone screen-off session, or a joined night (SPEC 8.5, ADR 0031) that opens as one report.
 * [sessionId] is the session the report opens on: a night's first.
 */
@Immutable
public data class LogEntry(
    val sessionId: Long,
    val start: Long,
    val end: Long,
    val verdict: VerdictWord,
    /** Battery percent per hour; null when the record does not hold it (a dash, never 0). */
    val drainPerHour: Double?,
    /** The charger was in at some point: Doze does not run then (SPEC 7.3). */
    val plugged: Boolean,
    /** The profile it ran; null when a night's sessions ran different ones. */
    val profileId: String?,
    val night: Boolean = false,
    val sessions: Int = 1,
    /** The session's or night's shape, as in its report; null when nothing was recorded to draw. */
    val strip: StripModel? = null,
)

/** Everything the log shows, mapped by the app from the evidence log, newest first. */
@Immutable
public data class LogState(
    val entries: List<LogEntry>,
    /** Profile ids to the names the owner sees, for the rows and the profile filter. */
    val profileNames: Map<String, String> = emptyMap(),
    /** How long parsed records are kept (SPEC 8.3, 7–90 days). */
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val notices: List<StateNotice> = emptyList(),
) {
    public fun name(profileId: String?): String? = profileId?.let { profileNames[it] ?: it }

    private companion object {
        const val DEFAULT_RETENTION_DAYS = 30
    }
}

/** What the log shows of [LogState.entries]: each set field narrows it; the defaults show everything. */
@Immutable
public data class LogFilter(
    val verdicts: Set<VerdictWord> = emptySet(),
    /** Null: both; false: unplugged only; true: plugged only. */
    val plugged: Boolean? = null,
    val profileId: String? = null,
) {
    public val active: Boolean get() = verdicts.isNotEmpty() || plugged != null || profileId != null

    public fun accepts(entry: LogEntry): Boolean = (verdicts.isEmpty() || entry.verdict in verdicts) &&
        (plugged == null || entry.plugged == plugged) &&
        (profileId == null || entry.profileId == profileId)
}

/** What the log needs from the app (ADR 0029). */
public interface LogSource {
    public val state: Flow<LogState>

    public fun seen(noticeId: String)
}
