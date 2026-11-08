// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.akane599.enforcedoze.data.EvidenceRows
import io.github.akane599.enforcedoze.data.JournalDao
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.OpenSession
import io.github.akane599.enforcedoze.data.PendingSnapshot
import io.github.akane599.enforcedoze.data.RecorderFiles
import io.github.akane599.enforcedoze.data.ScreenOnReads
import io.github.akane599.enforcedoze.data.SessionAssembly
import io.github.akane599.enforcedoze.data.SessionClose
import io.github.akane599.enforcedoze.data.SessionDao
import io.github.akane599.enforcedoze.data.SessionRecord
import io.github.akane599.enforcedoze.doze.EngineStore
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.LeverState
import io.github.akane599.enforcedoze.doze.android.EngineHost
import io.github.akane599.enforcedoze.evidence.BatteryReading
import io.github.akane599.enforcedoze.parse.AlarmStats
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.SensorConnections
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.PrivilegeController
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records each screen-off session and judges it (SPEC 8.1–8.4; ADR 0018–0020). All work runs in one
 * coroutine, one signal at a time, so a screen on never overtakes the screen off before it:
 * - screen off: battery, then one worker call for the sensor, alarm and `deviceidle` dumps and the
 *   history cursor; the open session is kept in a file so it survives the process;
 * - each deep maintenance window: a copy of the event log's Doze lines and a sensor snapshot;
 * - screen on: battery, then one timed worker call for everything else, then the record;
 * - the first unlock after a night: the night report (SPEC 8.5).
 * Nothing runs on a timer while the screen is off (SPEC 8.2).
 */
internal class SessionRecorder(graph: EngineGraph, private val scope: CoroutineScope, private val host: EngineHost) {
    private val context: Context = graph.app
    private val controller: PrivilegeController = graph.controller
    private val store: EngineStore = graph.store
    private val journal: JournalDao = graph.journal
    private val sessions: SessionDao = graph.sessions
    private val files: RecorderFiles = graph.recorderFiles

    private sealed interface Signal {
        data class Resume(val interactive: Boolean) : Signal

        data class ScreenOff(val time: Long, val profileId: String?, val holdOffs: List<String>, val constantsApplied: Boolean) : Signal

        data class ScreenOn(val time: Long) : Signal

        class Copy(val done: () -> Unit) : Signal

        data class Unlocked(val time: Long) : Signal
    }

    /** The screen-off reads (ADR 0018–0020). */
    private class Texts(val sensorService: String?, val alarms: String?, val deviceIdle: String?, val history: String?)

    /** The screen-on reads; each name is also the raw dump's file name. */
    private class Batch(
        val eventLog: String?,
        val history: String?,
        val sensorService: String?,
        val deviceIdle: String?,
        val alarms: String?,
    ) {
        fun named() = listOf(
            "eventlog" to eventLog,
            "history" to history,
            "sensorservice-on" to sensorService,
            "deviceidle-on" to deviceIdle,
            "alarm-on" to alarms,
        )
    }

    private val power = context.getSystemService(PowerManager::class.java)
    private val signals = Channel<Signal>(Channel.UNLIMITED)
    private val receivers = mutableListOf<BroadcastReceiver>()

    /** The latest stored session of this run, for the debug commands. */
    @Volatile
    var last: SessionRecord? = null
        private set

    /** Main thread. */
    fun start() {
        scope.launch {
            for (signal in signals) handle(signal)
        }
        signals.trySend(Signal.Resume(power.isInteractive))
        register(IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }) { intent ->
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> signals.trySend(screenOff())
                Intent.ACTION_SCREEN_ON -> signals.trySend(Signal.ScreenOn(System.currentTimeMillis()))
            }
        }
        // SPEC 8.5: the night report is generated at the first unlock after the night.
        register(IntentFilter(Intent.ACTION_USER_PRESENT)) { signals.trySend(Signal.Unlocked(System.currentTimeMillis())) }
        // ADR 0020: each deep maintenance window announces itself; the copy rides on it instead of a job.
        register(IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
            if (!power.isDeviceIdleMode && !power.isInteractive) copyInside(goAsync())
        }
    }

    /** Main thread. An open session stays in its file; the next start decides whether to resume it (ADR 0020). */
    fun stop() {
        receivers.forEach(context::unregisterReceiver)
        receivers.clear()
        signals.close()
    }

    private fun register(filter: IntentFilter, onReceive: BroadcastReceiver.(Intent) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onReceive(intent)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        receivers += receiver
    }

    private fun screenOff(): Signal.ScreenOff {
        val status = host.status.value
        val constants = status.levers[Lever.CONSTANTS]?.state
        return Signal.ScreenOff(
            time = System.currentTimeMillis(),
            profileId = status.profileId,
            holdOffs = status.holdOffs.map { it.name }.sorted(),
            constantsApplied = constants == null || constants in CONSTANTS_OK,
        )
    }

    /** Holds the broadcast, which keeps the maintenance window open, for the copy, but never past [COPY_LIMIT]. */
    private fun copyInside(pending: BroadcastReceiver.PendingResult) {
        val finished = AtomicBoolean(false)
        val done = { if (finished.compareAndSet(false, true)) pending.finish() }
        scope.launch {
            delay(COPY_LIMIT.toMillis())
            done()
        }
        if (signals.trySend(Signal.Copy(done)).isFailure) done()
    }

    private suspend fun handle(signal: Signal) {
        try {
            when (signal) {
                is Signal.Resume -> resume(signal.interactive)
                is Signal.ScreenOff -> opened(signal)
                is Signal.ScreenOn -> closed(signal.time)
                is Signal.Copy -> copy()
                is Signal.Unlocked -> unlocked(signal.time)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // One bad session must not stop the recorder.
        ) {
            Log.e(TAG, "recording ${signal::class.simpleName} failed", e)
        } finally {
            if (signal is Signal.Copy) signal.done()
        }
    }

    /** ADR 0020: with the screen on, the open session's end was not seen; with it off, the session goes on. */
    private suspend fun resume(interactive: Boolean) {
        val open = files.open() ?: return
        if (interactive) files.clearOpen() else files.saveOpen(open.copy(resumedAt = System.currentTimeMillis()))
    }

    private suspend fun opened(signal: Signal.ScreenOff) {
        // A session still open here missed its screen on.
        files.clearOpen()
        val battery = battery()
        var open = OpenSession(
            start = signal.time,
            profileId = signal.profileId,
            holdOffs = signal.holdOffs,
            constantsApplied = signal.constantsApplied,
            batteryLevel = battery?.level,
            chargeUah = battery?.chargeUah,
        )
        files.saveOpen(open)
        val cursor = files.cursor() ?: 0
        val texts = when (
            val read = controller.withWorker { api ->
                Texts(
                    sensorService = attempt { api.readSensorService() },
                    alarms = attempt { api.readAlarms() },
                    deviceIdle = attempt { api.readDeviceIdle() },
                    history = attempt { api.readBatteryHistory(cursor) },
                )
            }
        ) {
            is WorkerResult.Ok -> read.value
            else -> return
        }
        texts.sensorService?.let { files.saveRaw(open.start, SENSOR_OFF, it) }
        texts.alarms?.let { files.saveRaw(open.start, ALARM_OFF, it) }
        texts.deviceIdle?.let { files.saveRaw(open.start, DEVICE_IDLE_OFF, it) }
        open = open.copy(
            latchedInactiveMillis = texts.deviceIdle?.let { DeviceIdleDump.parse(it).inactiveTimeoutMillis },
            alarmsRead = texts.alarms?.let { AlarmStats.parse(it).found } == true,
            snapshots = listOfNotNull(texts.sensorService?.let { snapshot(SessionAssembly.SCREEN_OFF, it) }),
        )
        files.saveOpen(open)
        // ADR 0020: the screen-on read then starts at screen off instead of at the last session's end.
        texts.history?.let(BatteryHistory::nextCursor)?.let { files.saveCursor(it) }
    }

    private suspend fun copy() {
        if (power.isInteractive) return
        val open = files.open() ?: return
        val since = open.copiedUntil ?: (open.start - LOOKBACK.toMillis())
        val read = controller.withWorker { api -> api.readEventLog(since) to attempt { api.readSensorService() } }
        if (read !is WorkerResult.Ok) return
        val readAt = System.currentTimeMillis()
        val (log, sensors) = read.value
        files.addCopied(readAt, DozeEventLog.copyLines(log))
        val snapshot = sensors?.let { snapshot(SessionAssembly.COPY, it) }
        files.saveOpen(open.copy(copiedUntil = readAt, snapshots = open.snapshots + listOfNotNull(snapshot)))
    }

    private suspend fun closed(end: Long) {
        val open = files.open() ?: return
        val battery = battery()
        val minimum = DebugHooks.minimumSession(context)
        val reads = if (SessionAssembly.tooShort(open.start, end, minimum)) null else collect(open)
        val profile = open.profileId?.let(Profiles::byId)
        val record = SessionAssembly.close(
            SessionClose(
                open = open,
                end = end,
                battery = battery,
                events = journal.eventsSince(open.start),
                forcedIdle = profile?.levers?.forceIdle?.enabled == true,
                constants = listOf(profile?.constants.orEmpty(), store.stock()?.effective.orEmpty(), store.previous()?.effective.orEmpty()),
                copied = files.copied(),
                alarmsBefore = files.readRaw(open.start, ALARM_OFF),
                reads = reads,
                minimum = minimum,
                selfUid = Process.myUid(),
                zone = ZoneId.systemDefault(),
            ),
        )
        val id = sessions.record(record.record)
        record.nextCursor?.let { files.saveCursor(it) }
        files.clearOpen()
        if (reads == null) files.deleteRaw(open.start)
        last = record.record.copy(session = record.record.session.copy(id = id))
        retain(System.currentTimeMillis())
    }

    /** SPEC 8.5: each unplugged session of at least the night threshold, not yet reported, becomes a night now. */
    private suspend fun unlocked(time: Long) {
        val threshold = DebugHooks.nightThreshold(context)
        for (session in sessions.unreportedNights(time - NIGHT_LOOKBACK.toMillis(), threshold.toMillis())) {
            val recent = sessions.nightSessions(session.start, null, NightReports.RECENT)
            val stock = sessions.nightSessions(session.start, BuiltInProfiles.STOCK.id, NightReports.RECENT)
            sessions.insertNight(NightReports.night(session, time, recent, stock))
        }
    }

    /** The screen-on batch (SPEC 8.2), timed; the raw dumps are kept first, so a parser bug loses nothing. */
    private suspend fun collect(open: OpenSession): ScreenOnReads {
        val cursor = files.cursor() ?: 0
        val started = SystemClock.elapsedRealtime()
        val read = controller.withWorker { api ->
            Batch(
                eventLog = attempt { api.readEventLog(open.start - LOOKBACK.toMillis()) },
                history = attempt { api.readBatteryHistory(cursor) },
                sensorService = attempt { api.readSensorService() },
                deviceIdle = attempt { api.readDeviceIdle() },
                alarms = attempt { api.readAlarms() },
            )
        }
        val took = SystemClock.elapsedRealtime() - started
        val batch = (read as? WorkerResult.Ok)?.value ?: Batch(null, null, null, null, null)
        val failures = when (read) {
            is WorkerResult.Ok -> batch.named().filter { it.second == null }.map { "${it.first} not read at screen on" }
            is WorkerResult.Unavailable -> listOf("screen-on reads not taken: ${read.reason}")
            is WorkerResult.Failed -> listOf("screen-on reads failed: ${read.error}")
        }
        batch.named().forEach { (name, text) -> text?.let { files.saveRaw(open.start, name, it) } }
        return ScreenOnReads(
            eventLog = batch.eventLog,
            history = batch.history,
            sensorService = batch.sensorService,
            deviceIdle = batch.deviceIdle,
            alarms = batch.alarms,
            readAt = System.currentTimeMillis(),
            tookMillis = took,
            failures = failures,
        )
    }

    /** Retention (SPEC 8.3): 30 days of records, 7 days of raw dumps. */
    private suspend fun retain(now: Long) {
        val records = now - RECORDS.toMillis()
        sessions.deleteSessionsBefore(records)
        journal.deleteActionsBefore(records)
        journal.deleteEventsBefore(records)
        files.pruneRaw(now - RAW.toMillis())
    }

    private fun snapshot(moment: String, text: String): PendingSnapshot {
        val row = EvidenceRows.snapshot(System.currentTimeMillis(), moment, SensorServiceDump.parse(text), SensorConnections.parse(text))
        return PendingSnapshot.of(row)
    }

    /** `BatteryManager` (ADR 0018): the level, and the charge counter in µAh when the device reports one. */
    private fun battery(): BatteryReading? {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..FULL } ?: return null
        val charge = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 }?.toLong()
        return BatteryReading(level, charge)
    }

    /** One dump inside a worker call: a failed dump is noted by the caller and the others are kept. */
    private suspend fun <T> attempt(read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception, // Each dump is separate evidence.
    ) {
        Log.w(TAG, "a worker read failed", e)
        null
    }

    private companion object {
        const val TAG = "SessionRecorder"
        const val FULL = 100

        /** Enough log before screen off to know the state the session began in. */
        val LOOKBACK: Duration = Duration.ofMinutes(1)

        /** Under the 10 s a foreground broadcast may be held (ADR 0020). */
        val COPY_LIMIT: Duration = Duration.ofSeconds(8)
        val RECORDS: Duration = Duration.ofDays(30)
        val RAW: Duration = Duration.ofDays(7)

        /** A night is reported at the first unlock after it, which comes well within a day. */
        val NIGHT_LOOKBACK: Duration = Duration.ofDays(1)

        const val SENSOR_OFF = "sensorservice-off"
        const val ALARM_OFF = "alarm-off"
        const val DEVICE_IDLE_OFF = "deviceidle-off"

        val CONSTANTS_OK = setOf(LeverState.APPLIED, LeverState.UNUSED)
    }
}
