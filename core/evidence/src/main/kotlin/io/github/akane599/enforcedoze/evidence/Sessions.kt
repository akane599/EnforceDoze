// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.AlarmStats
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeepState

/** What the worker read for one session (ADR 0018, 0019), parsed. */
public data class SessionSources(
    /** The copy job's reads and the screen-on read. */
    val eventReads: List<EventLogRead>,
    val history: BatteryHistory?,
    val alarmsBefore: AlarmStats?,
    val alarmsAfter: AlarmStats?,
    val selfUid: Int,
)

/** Everything the session record and its report show (SPEC 8.1, 8.4). */
public data class SessionAnalysis(
    val result: SessionResult,
    val timeline: List<DeepStretch>,
    val doze: DozeRecord,
    val wakers: WakerSummary,
)

public object Sessions {
    /** Judges [input] on the Doze record merged from [sources]; the changes already in [input] are replaced. */
    public fun analyze(input: SessionInput, sources: SessionSources): SessionAnalysis {
        val session = Interval(input.start, input.end)
        val doze = DozeSources.merge(session, sources.eventReads, sources.history)
        val judged = input.copy(changes = doze.deepChanges)
        val timeline = Verdicts.timeline(judged)
        val idle = timeline.filter { it.state == DeepState.IDLE }.map { it.interval }
        val wakers = Wakers.summarize(
            WakerInput(session, sources.history, sources.alarmsBefore, sources.alarmsAfter, idle, sources.selfUid),
        )
        return SessionAnalysis(Verdicts.judge(judged), timeline, doze, wakers)
    }
}
