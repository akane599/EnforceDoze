// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.evidence.BatteryDrain
import io.github.akane599.enforcedoze.evidence.BatteryReading
import io.github.akane599.enforcedoze.evidence.EventLogRead
import io.github.akane599.enforcedoze.evidence.ExpectedWalk
import io.github.akane599.enforcedoze.evidence.HoldOffSpan
import io.github.akane599.enforcedoze.evidence.Interval
import io.github.akane599.enforcedoze.evidence.SessionInput
import io.github.akane599.enforcedoze.evidence.SessionSources
import io.github.akane599.enforcedoze.evidence.Sessions
import io.github.akane599.enforcedoze.evidence.Verdict
import io.github.akane599.enforcedoze.evidence.WakerSummary
import io.github.akane599.enforcedoze.evidence.Wakers
import io.github.akane599.enforcedoze.parse.AlarmStats
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.HistoryItem
import io.github.akane599.enforcedoze.parse.SensorConnections
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The screen-on worker batch (SPEC 8.2; ADR 0018, 0019): each dump's text, or null when it was not read. */
public data class ScreenOnReads(
    val eventLog: String?,
    val history: String?,
    val sensorService: String?,
    val deviceIdle: String?,
    val alarms: String?,
    /** When the batch returned, epoch milliseconds: where the screen-on event-log read's coverage ends. */
    val readAt: Long,
    val tookMillis: Long,
    /** Why reads are missing, one reason each. */
    val failures: List<String> = emptyList(),
)

/** Everything known about a session once the screen is on again. */
public data class SessionClose(
    val open: OpenSession,
    /** Screen on, epoch milliseconds. */
    val end: Long,
    val battery: BatteryReading?,
    /** The journal's events from the session's start on. */
    val events: List<EventRow>,
    /** The profile forces idle, so the walk to deep idle is zero. */
    val forcedIdle: Boolean,
    /** Walk timers by precedence: the profile's constants, then stock, then previous (SPEC 8.4). */
    val constants: List<Map<String, String>>,
    /** The copies taken at maintenance windows (ADR 0020), as read time and text. */
    val copied: List<Pair<Long, String>>,
    /** `dumpsys alarm` at screen off. */
    val alarmsBefore: String?,
    /** Null for a session too short to judge: nothing is read at its screen on (ADR 0020). */
    val reads: ScreenOnReads?,
    val minimum: Duration,
    val selfUid: Int,
    val zone: ZoneId,
)

/** The record to store and the battery history's `NEXT:`; a null cursor leaves the stored one as it is (ADR 0018). */
public data class ClosedSession(val record: SessionRecord, val nextCursor: Long?)

/** Turns a closed session's dumps into its record (SPEC 8.1, 8.4; ADR 0018–0020). Pure, so it is tested on the JVM. */
public object SessionAssembly {
    public const val SCREEN_OFF: String = "SCREEN_OFF"
    public const val COPY: String = "COPY"
    public const val SCREEN_ON: String = "SCREEN_ON"

    /** Sessions this short get no verdict (SPEC 8.4), so their screen-on batch is skipped (ADR 0020). */
    public fun tooShort(start: Long, end: Long, minimum: Duration): Boolean = Duration.ofMillis(end - start) <= minimum

    public fun close(input: SessionClose): ClosedSession {
        val open = input.open
        val start = Instant.ofEpochMilli(open.start)
        val end = Instant.ofEpochMilli(input.end)
        val spans = holdOffSpans(open.holdOffs, input.events, start, end)
        val plugged = spans.filter { it.name == HoldOff.CHARGING.name }.map { it.interval }
        val latched = open.latchedInactiveMillis?.let { mapOf(INACTIVE_TO to it.toString()) }
        val expected = ExpectedWalk.of(input.forcedIdle, listOfNotNull(latched) + input.constants)
        val charged = plugged.sumOf { it.duration.toMillis() }
        val base = base(input, expected, charged > 0).copy(chargedMillis = charged)
        val snapshots = open.snapshots.map { it.toRow() }
        val reads = input.reads ?: return ClosedSession(
            SessionRecord(base.copy(note = "shorter than ${input.minimum.seconds} s: not judged"), emptyList(), emptyList(), snapshots),
            nextCursor = null,
        )
        val history = reads.history?.let { BatteryHistory.parse(it, input.zone, Instant.ofEpochMilli(reads.readAt)) }
        val eventReads = input.copied.map { (at, text) -> EventLogRead(DozeEventLog.parse(text), Instant.ofEpochMilli(at)) } +
            listOfNotNull(reads.eventLog?.let { EventLogRead(DozeEventLog.parse(it), Instant.ofEpochMilli(reads.readAt)) })
        val analysis = Sessions.analyze(
            SessionInput(
                start = start,
                end = end,
                expectedToDeep = expected ?: Duration.ZERO,
                changes = emptyList(),
                plugged = plugged,
                holdOffs = spans.filter { it.name != HoldOff.CHARGING.name },
                foreignWrites = input.events.filter { it.kind == JournalRows.DRIFT && it.time in open.start..input.end }
                    .map { Instant.ofEpochMilli(it.time) },
                constantsApplied = open.constantsApplied,
                minimum = input.minimum,
            ),
            SessionSources(
                eventReads,
                history,
                input.alarmsBefore?.let(AlarmStats::parse),
                reads.alarms?.let(AlarmStats::parse),
                input.selfUid,
            ),
        )
        val screenOn = reads.sensorService?.let {
            EvidenceRows.snapshot(reads.readAt, SCREEN_ON, SensorServiceDump.parse(it), SensorConnections.parse(it))
        }
        val record = EvidenceRows.record(base, analysis, snapshots + listOfNotNull(screenOn), input.selfUid)
        val withheld = withheld(input, expected, analysis.doze.gaps, history, eventReads.sumOf { it.log.unparsed.size })
        val motion = noMotionSensor(analysis.result.verdict, reads.deviceIdle).takeIf { withheld.isEmpty() }
        val unreadHistory = history?.unparsed?.size?.takeIf { it > 0 }?.let { "$it battery history lines could not be read" }
        val notes = reads.failures + withheld + listOfNotNull(missing(input, reads, analysis.wakers), motion, unreadHistory)
        val session = record.session.let { if (withheld.isEmpty()) it else it.copy(verdict = null, cause = null, lateByMillis = null) }
        return ClosedSession(record.copy(session = session.copy(note = notes.joinToString("\n").ifEmpty { null })), history?.next)
    }

    /** What the recorder knows without the analysis: times, profile, battery and drain. */
    private fun base(input: SessionClose, expected: Duration?, charged: Boolean): SessionRow {
        val open = input.open
        val drain = BatteryDrain.of(
            open.batteryLevel?.let { BatteryReading(it, open.chargeUah) },
            input.battery,
            Duration.ofMillis(input.end - open.start),
            charged,
        )
        return SessionRow(
            start = open.start,
            end = input.end,
            profileId = open.profileId,
            pluggedAtStart = HoldOff.CHARGING.name in open.holdOffs,
            verdict = null,
            cause = null,
            expectedToDeepMillis = expected?.toMillis(),
            timeToDeepMillis = null,
            lateByMillis = null,
            deepShare = 0.0,
            maintenanceWindows = 0,
            motionResets = 0,
            breaks = "",
            note = null,
            batteryStart = open.batteryLevel,
            batteryEnd = input.battery?.level,
            chargeStartUah = open.chargeUah,
            chargeEndUah = input.battery?.chargeUah,
            drainPerHour = drain.percentPerHour,
            drainMah = drain.mah,
            collectionMillis = input.reads?.tookMillis,
        )
    }

    /**
     * Why the session gets no verdict (ADR 0020); empty when it gets one. A Doze line that could not be read may be
     * a state change the verdict would miss, so [unreadDoze] withholds it too.
     */
    private fun withheld(
        input: SessionClose,
        expected: Duration?,
        gaps: List<Interval>,
        history: BatteryHistory?,
        unreadDoze: Int,
    ): List<String> = listOfNotNull(
        "the walk to deep idle is unknown: no stock or previous values".takeIf { expected == null },
        "$unreadDoze Doze lines of the event log could not be read: not judged".takeIf { unreadDoze > 0 },
        gaps.takeIf { it.isNotEmpty() }?.let { spans ->
            "no Doze record for ${spans.fold(Duration.ZERO) { sum, span -> sum + span.duration }.toMinutes()} min: not judged"
        },
        input.open.resumedAt?.let { screenOnInside(input, history) },
    )

    /** A resumed session is judged only when the history shows the screen stayed off (ADR 0020). */
    private fun screenOnInside(input: SessionClose, history: BatteryHistory?): String? {
        history ?: return "resumed after a restart and the battery history is missing: not judged"
        val inside = (input.open.start + Wakers.COVERAGE_SLACK.toMillis())..(input.end - Wakers.COVERAGE_SLACK.toMillis())
        val screenOn = history.lines.any { line ->
            line.time in inside && line.items.any { it is HistoryItem.Flag && it.name == SCREEN && it.on }
        }
        return "resumed after a restart and the screen came on in between: not judged".takeIf { screenOn }
    }

    /** Why the app's own wakeups are unknown (ADR 0019); null when they are known. */
    private fun missing(input: SessionClose, reads: ScreenOnReads, wakers: WakerSummary): String? {
        if (wakers.ownWakeups != null) return null
        val start = Instant.ofEpochMilli(input.open.start)
        val late = wakers.historyCoverage?.let { Duration.between(start, it.start) > Wakers.COVERAGE_SLACK } ?: true
        val reasons = listOfNotNull(
            "the alarm dump at screen off".takeIf { input.alarmsBefore == null },
            "the alarm dump at screen on".takeIf { reads.alarms == null },
            "alarm stats in the alarm dumps".takeIf { !wakers.alarmsRead && input.alarmsBefore != null && reads.alarms != null },
            "the battery history".takeIf { reads.history == null },
            "the battery history from screen off".takeIf { reads.history != null && late },
        )
        return "the app's own wakeups are unknown: missing ${reasons.joinToString(", ")}"
    }

    /** ADR 0016: without a motion sensor the system schedules no step before IDLE, so nothing reaches deep idle by itself. */
    private fun noMotionSensor(verdict: Verdict?, deviceIdle: String?): String? {
        if (verdict !is Verdict.DidNotDoze || deviceIdle == null) return null
        val dump = DeviceIdleDump.parse(deviceIdle)
        val none = dump.useMotionSensor == true && dump.motionSensor == null
        return "this device reports no motion sensor, so it does not step to deep idle by itself (ADR 0016)".takeIf { none }
    }

    /** Each hold-off's spans within the session, from the set active at screen off and the engine's events. */
    internal fun holdOffSpans(atStart: List<String>, events: List<EventRow>, start: Instant, end: Instant): List<HoldOffSpan> {
        val opened = atStart.associateWith { start }.toMutableMap()
        val spans = mutableListOf<HoldOffSpan>()
        for (event in events.filter { it.time in start.toEpochMilli()..end.toEpochMilli() }) {
            val name = event.holdOff ?: continue
            val time = Instant.ofEpochMilli(event.time)
            when (event.kind) {
                JournalRows.HOLD_OFF_STARTED -> opened.putIfAbsent(name, time)
                JournalRows.HOLD_OFF_ENDED -> opened.remove(name)?.let { spans += HoldOffSpan(name, Interval(it, time)) }
            }
        }
        opened.forEach { (name, from) -> spans += HoldOffSpan(name, Interval(from, end)) }
        return spans
    }

    private const val INACTIVE_TO = "inactive_to"
    private const val SCREEN = "screen"
}
