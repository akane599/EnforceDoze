// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.evidence.AppWakers
import io.github.akane599.enforcedoze.evidence.Interval
import io.github.akane599.enforcedoze.evidence.Run
import io.github.akane599.enforcedoze.evidence.SessionAnalysis
import io.github.akane599.enforcedoze.evidence.Transition
import io.github.akane599.enforcedoze.evidence.Verdict
import io.github.akane599.enforcedoze.evidence.WakeReason
import io.github.akane599.enforcedoze.parse.SensorConnection
import io.github.akane599.enforcedoze.parse.SensorServiceDump

/** Pure mappings from the session analysis (`:core:evidence`) to rows. */
public object EvidenceRows {
    public const val HELD: String = "HELD"
    public const val PARTLY: String = "PARTLY"
    public const val DID_NOT_DOZE: String = "DID_NOT_DOZE"

    /**
     * [base] carries what the recorder knows (times, profile, battery, notes); the analysis fills the
     * verdict, its numbers, the coverage and the child rows. A verdict of null keeps [base]'s note and
     * adds why.
     */
    public fun record(base: SessionRow, analysis: SessionAnalysis, snapshots: List<SensorSnapshotRow>, selfUid: Int): SessionRecord {
        val result = analysis.result
        val verdict = result.verdict
        val wakers = analysis.wakers
        val own = wakers.own
        val session = base.copy(
            verdict = when (verdict) {
                Verdict.Held -> HELD
                is Verdict.Partly -> PARTLY
                is Verdict.DidNotDoze -> DID_NOT_DOZE
                null -> null
            },
            cause = (verdict as? Verdict.DidNotDoze)?.cause?.name,
            timeToDeepMillis = result.timeToDeep?.toMillis(),
            lateByMillis = (verdict as? Verdict.Partly)?.lateBy?.toMillis(),
            deepShare = result.deepShare,
            timeInDeepMillis = result.timeInDeep.toMillis(),
            maintenanceWindows = result.maintenanceWindows,
            motionResets = result.motionResets,
            breaks = result.breaks.joinToString("\n") { "${it.time.toEpochMilli()} ${it.state} ${it.reason} ${it.cause}" },
            ownWakeups = wakers.ownWakeups,
            ownRuns = (own.inIdle.map { run(it, "idle") } + own.outsideIdle.map { run(it, "outside") }).joinToString("\n"),
            alarmsRead = wakers.alarmsRead,
            mobileRadioMillis = wakers.mobileRadio.toMillis(),
            unattributedWakelockMillis = wakers.unattributedWakelock.toMillis(),
            eventLogFrom = analysis.doze.eventCoverage.firstOrNull()?.start?.toEpochMilli(),
            historyFrom = wakers.historyCoverage?.start?.toEpochMilli(),
            historyFilled = spans(analysis.doze.historyFilled),
            gaps = spans(analysis.doze.gaps),
            timeline = analysis.timeline.joinToString("\n") {
                "${it.state} ${it.interval.start.toEpochMilli()} ${it.interval.end.toEpochMilli()}"
            },
        )
        return SessionRecord(
            session = session,
            transitions = analysis.doze.transitions.map(::transition),
            wakers = wakers.apps.map { app(it, selfUid) } + wakers.wakeReasons.map(::kernel),
            snapshots = snapshots,
        )
    }

    public fun snapshot(time: Long, moment: String, dump: SensorServiceDump, connections: List<SensorConnection>): SensorSnapshotRow =
        SensorSnapshotRow(
            time = time,
            moment = moment,
            mode = dump.modeName,
            privacy = dump.sensorPrivacy,
            connections = connections.joinToString("\n") {
                listOf(it.uid, it.listener, it.sensor, it.samplingPeriodUs ?: "-").joinToString("\t")
            },
        )

    private fun transition(transition: Transition) = TransitionRow(
        time = transition.time.toEpochMilli(),
        deep = transition.deep,
        fromState = transition.from,
        toState = transition.to,
        reason = transition.reason,
        source = transition.source.name,
    )

    private fun app(app: AppWakers, selfUid: Int) = WakerRow(
        uid = app.uid,
        name = app.packages.joinToString(","),
        self = app.uid == selfUid,
        alarmWakeups = app.alarmWakeups,
        networkWakeups = app.networkWakeups,
        jobs = app.jobs,
        jobMillis = app.jobTime.toMillis(),
        syncs = app.syncs,
        syncMillis = app.syncTime.toMillis(),
        wakelockMillis = app.wakelockTime.toMillis(),
        longWakelocks = app.longWakelocks,
        longWakelockMillis = app.longWakelockTime.toMillis(),
        alarms = app.alarms.joinToString("\n") { "${it.wakeups} ${it.alarms} ${it.tag}" },
    )

    private fun kernel(reason: WakeReason) = WakerRow(uid = null, name = reason.reason, kernelWakeups = reason.count)

    private fun run(run: Run, where: String) =
        "${run.kind} ${run.interval.start.toEpochMilli()} ${run.interval.end.toEpochMilli()} $where ${run.tag}"

    private fun spans(spans: List<Interval>) = spans.joinToString("\n") { "${it.start.toEpochMilli()} ${it.end.toEpochMilli()}" }
}
