// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.akane599.enforcedoze.data.EventRow
import io.github.akane599.enforcedoze.data.JournalDao
import io.github.akane599.enforcedoze.data.JournalRows
import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.doze.EngineStore
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.LeverState
import io.github.akane599.enforcedoze.doze.android.EngineHost
import io.github.akane599.enforcedoze.evidence.DeepChange
import io.github.akane599.enforcedoze.evidence.ExpectedWalk
import io.github.akane599.enforcedoze.evidence.HoldOffSpan
import io.github.akane599.enforcedoze.evidence.Interval
import io.github.akane599.enforcedoze.evidence.SessionInput
import io.github.akane599.enforcedoze.evidence.SessionResult
import io.github.akane599.enforcedoze.evidence.Verdict
import io.github.akane599.enforcedoze.evidence.Verdicts
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.privilege.PrivilegeController
import io.github.akane599.enforcedoze.privilege.WorkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Records each screen-off session and judges it (SPEC 8.1, 8.4). Screen off only notes the time and the
 * engine's state, so nothing runs while the phone sleeps; screen on reads the event log in one worker call
 * and stores the verdict (SPEC 8.2). P3 adds battery, wakers, sensor snapshots and the night report.
 */
internal class SessionRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: EngineHost,
    private val controller: PrivilegeController,
    private val store: EngineStore,
    private val journal: JournalDao,
    private val minimum: () -> Duration,
) {
    private class Open(val start: Instant, val profileId: String?, val holdOffs: Set<HoldOff>, val constantsApplied: Boolean)

    // Main thread only.
    private var open: Open? = null
    private var receiver: BroadcastReceiver? = null

    /** The latest stored session of this run, for the debug commands. */
    @Volatile
    var last: SessionRow? = null
        private set

    /** Main thread. */
    fun start() {
        val watch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOff()
                    Intent.ACTION_SCREEN_ON -> screenOn()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(watch, filter, Context.RECEIVER_NOT_EXPORTED)
        receiver = watch
    }

    /** Main thread. A session still open is dropped: its end was not seen. */
    fun stop() {
        receiver?.let(context::unregisterReceiver)
        receiver = null
        open = null
    }

    private fun screenOff() {
        val status = host.status.value
        val constants = status.levers[Lever.CONSTANTS]?.state
        open = Open(Instant.now(), status.profileId, status.holdOffs, constants == null || constants in CONSTANTS_OK)
    }

    private fun screenOn() {
        val session = open ?: return
        open = null
        val end = Instant.now()
        scope.launch {
            val row = judge(session, end)
            journal.insert(row)
            last = row
        }
    }

    private suspend fun judge(session: Open, end: Instant): SessionRow {
        val events = journal.eventsSince(session.start.toEpochMilli()).filter { it.time <= end.toEpochMilli() }
        val spans = holdOffSpans(session.holdOffs, events, session.start, end)
        val profile = session.profileId?.let(Profiles::byId)
        val expected = ExpectedWalk.of(
            forcedIdle = profile?.levers?.forceIdle?.enabled == true,
            profile?.constants.orEmpty(),
            store.stock()?.effective.orEmpty(),
            store.previous()?.effective.orEmpty(),
        )
        val base = SessionRow(
            start = session.start.toEpochMilli(),
            end = end.toEpochMilli(),
            profileId = session.profileId,
            pluggedAtStart = HoldOff.CHARGING in session.holdOffs,
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
        )
        if (expected == null) return base.copy(note = "the walk to deep idle is unknown: no stock.json or previous.json values")
        // The worker reads the log as shell; reading it in the app would ask the owner each time (READ_LOGS consent).
        val since = session.start.minus(LOOKBACK).toEpochMilli()
        val log = when (val read = controller.withWorker { it.readEventLog(since) }) {
            is WorkerResult.Ok -> DozeEventLog.parse(read.value)
            is WorkerResult.Unavailable -> return base.copy(note = "event log not read: ${read.reason}")
            is WorkerResult.Failed -> return base.copy(note = "event log not read: ${read.error}")
        }
        val result = Verdicts.judge(
            SessionInput(
                start = session.start,
                end = end,
                expectedToDeep = expected,
                changes = DeepChange.from(log),
                plugged = spans.filter { it.name == HoldOff.CHARGING.name }.map { it.interval },
                holdOffs = spans.filter { it.name != HoldOff.CHARGING.name },
                foreignWrites = events.filter { it.kind == JournalRows.DRIFT }.map { Instant.ofEpochMilli(it.time) },
                constantsApplied = session.constantsApplied,
                minimum = minimum(),
            ),
        )
        return fill(base, result)
    }

    private fun fill(base: SessionRow, result: SessionResult): SessionRow {
        val verdict = result.verdict
        return base.copy(
            verdict = when (verdict) {
                Verdict.Held -> "HELD"
                is Verdict.Partly -> "PARTLY"
                is Verdict.DidNotDoze -> "DID_NOT_DOZE"
                null -> null
            },
            cause = (verdict as? Verdict.DidNotDoze)?.cause?.name,
            timeToDeepMillis = result.timeToDeep?.toMillis(),
            lateByMillis = (verdict as? Verdict.Partly)?.lateBy?.toMillis(),
            deepShare = result.deepShare,
            maintenanceWindows = result.maintenanceWindows,
            motionResets = result.motionResets,
            breaks = result.breaks.joinToString("\n") { "${it.time.toEpochMilli()} ${it.state} ${it.reason} ${it.cause}" },
            note = if (verdict == null) "shorter than ${minimum().seconds} s" else null,
        )
    }

    /** Each hold-off's spans within the session, from the set active at screen off and the engine's events. */
    private fun holdOffSpans(atStart: Set<HoldOff>, events: List<EventRow>, start: Instant, end: Instant): List<HoldOffSpan> {
        val opened = atStart.associate { it.name to start }.toMutableMap()
        val spans = mutableListOf<HoldOffSpan>()
        for (event in events) {
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

    private companion object {
        /** Enough log before screen off to know the state the session began in. */
        val LOOKBACK: Duration = Duration.ofMinutes(1)

        val CONSTANTS_OK = setOf(LeverState.APPLIED, LeverState.UNUSED)
    }
}
