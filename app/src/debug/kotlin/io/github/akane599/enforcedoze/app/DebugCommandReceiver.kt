// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.akane599.enforcedoze.data.NightReport
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.SessionRecord
import io.github.akane599.enforcedoze.doze.DozeEngine
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.JobResult
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.QuickProof
import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.doze.android.EngineHost
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalTime
import java.util.zip.ZipFile

/**
 * Debug builds only: drives the engine from adb for the emulator gates (SPEC 13.2, 13.3). Only callers
 * holding DUMP (the shell) can reach it. The reply is the broadcast's result data:
 *
 *     adb shell am broadcast -n io.github.akane599.enforcedoze/io.github.akane599.enforcedoze.app.DebugCommandReceiver \
 *         --es cmd profile --es arg test-everyday
 *
 * Start the engine first with `am start -n io.github.akane599.enforcedoze/io.github.akane599.enforcedoze.app.DebugStartActivity`.
 * Commands: `stop`, `status`, `profile <id>`, `plan <day>[,<night>,<HH:mm[:ss]>,<HH:mm[:ss]>]`, `follow-plan`,
 * `night-start` (as if the window-start alarm arrived), `capture-stock`, `restore-stock`,
 * `return-to-previous`, `pause`, `resume`, `reapply <true|false>`, `clear-guard <LEVER>`,
 * `min-session <seconds|default>`, `last-session`, `night-threshold <seconds|default>`, `last-night`,
 * `quick-proof`, `export <session id|all>`, `events <count>`, `actions <count>`.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(CMD) ?: return
        val arg = intent.getStringExtra(ARG)
        val graph = EngineGraph.get(context)
        val pending = goAsync()
        graph.scope.launch {
            val reply = try {
                withTimeout(TIMEOUT_MILLIS) { run(graph, command, arg) }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception, // A test harness reports every failure as text.
            ) {
                "error: $e"
            }
            pending.resultData = reply
            pending.finish()
        }
    }

    private suspend fun run(graph: EngineGraph, command: String, arg: String?): String {
        appCommands[command]?.let { return it(graph, arg) }
        val engineCommand = engineCommands[command] ?: return "unknown command $command"
        val host = graph.host ?: return "engine not running"
        return graph.engineCommand(host, arg)
    }

    /** Commands that do not need the engine running. */
    private val appCommands: Map<String, suspend (EngineGraph, String?) -> String> = mapOf(
        "stop" to { graph, _ ->
            graph.switch.enabled = false
            EngineService.stop(graph.app)
            "stopping"
        },
        "min-session" to { graph, arg ->
            DebugHooks.setMinimumSession(graph.app, arg?.toLongOrNull())
            "minimum session ${DebugHooks.minimumSession(graph.app).seconds} s"
        },
        "events" to { graph, arg -> graph.journal.eventsSince(0).takeLast(rows(arg)).joinToString("\n") },
        "actions" to { graph, arg -> graph.journal.actionsSince(0).takeLast(rows(arg)).joinToString("\n") },
        "last-session" to { graph, _ -> graph.recorder?.last?.let(::describe) ?: "no session yet" },
        "night-threshold" to { graph, arg ->
            DebugHooks.setNightThreshold(graph.app, arg?.toLongOrNull())
            "night threshold ${DebugHooks.nightThreshold(graph.app).seconds} s"
        },
        "last-night" to { graph, _ -> lastNight(graph)?.let(::describe) ?: "no night yet" },
        "export" to { graph, arg -> export(graph, arg) },
    )

    private val engineCommands: Map<String, suspend EngineGraph.(EngineHost, String?) -> String> = mapOf(
        "status" to { host, _ -> describe(host.status.value) },
        "profile" to { host, arg ->
            val profile = arg?.let { profiles.profile(it) }
            if (profile == null) "unknown profile $arg" else after(host) { setProfile(profile, SwitchReason.OWNER) }
        },
        "plan" to { host, arg ->
            val plan = arg?.let(::plan)
            val unknown = listOfNotNull(plan?.dayProfileId, plan?.night?.profileId).filter { profiles.profile(it) == null }
            when {
                plan == null -> "usage: plan <day>[,<night>,<HH:mm[:ss]>,<HH:mm[:ss]>]"
                unknown.isNotEmpty() -> "unknown profile ${unknown.joinToString()}"
                else -> after(host) { setPlan(plan, SwitchReason.OWNER) }
            }
        },
        "follow-plan" to { host, _ -> after(host) { followPlan(SwitchReason.OWNER) } },
        "night-start" to { host, _ -> after(host) { nightStarted() } },
        "capture-stock" to { host, _ -> describe(host.engine.captureStock()) },
        "quick-proof" to { host, _ -> describe(host.engine.quickProof()) },
        "restore-stock" to { host, _ -> describe(host.engine.restoreStock()) },
        "return-to-previous" to { host, _ -> describe(host.engine.returnToPrevious()) },
        "pause" to { host, _ -> after(host) { pause() } },
        "resume" to { host, _ -> after(host) { resume() } },
        "reapply" to { host, arg -> after(host) { setReapplyOnDrift(arg.toBoolean()) } },
        "clear-guard" to { host, arg -> after(host) { clearGuard(Lever.valueOf(arg.orEmpty())) } },
    )

    private fun rows(arg: String?): Int = arg?.toIntOrNull() ?: DEFAULT_ROWS

    /** `everyday` or `everyday,night,23:00,07:30` (seconds allowed, for the gate); null when it does not read. */
    private fun plan(arg: String): Plan? {
        val day = arg.substringBefore(',').trim()
        if (',' !in arg) return Plan(day)
        val (night, start, end) = NIGHT.matchEntire(arg.substringAfter(',').trim())?.destructured ?: return null
        return runCatching { Plan(day, NightSlot(night, LocalTime.parse(start), LocalTime.parse(end))) }.getOrNull()
    }

    /** Writes the export ZIP and lists what is in it, for the P3 gate (SPEC 8.7). */
    private suspend fun export(graph: EngineGraph, arg: String?): String {
        val exporter = EvidenceExporter(graph)
        val file = if (arg == "all") exporter.range(0, System.currentTimeMillis()) else arg?.toLongOrNull()?.let { exporter.session(it) }
        file ?: return "no session $arg"
        val names = ZipFile(file).use { zip -> zip.entries().toList().map { "${it.name} ${it.size}" } }
        return (listOf("path=${file.absolutePath} bytes=${file.length()}") + names.map { "entry $it" }).joinToString("\n")
    }

    private suspend fun lastNight(graph: EngineGraph): NightReport? {
        val night = graph.sessions.latestNights(1).firstOrNull() ?: return null
        val record = graph.sessions.recordOf(night.sessionId) ?: return null
        val since = NightReports.from(record.session)
        return NightReports.report(night, record, graph.journal.eventsSince(since), graph.journal.actionsSince(since))
    }

    /** Runs an engine command, then reports the status it left. */
    private suspend fun after(host: EngineHost, command: suspend DozeEngine.() -> Unit): String {
        host.engine.command()
        return describe(host.status.value)
    }

    private fun describe(status: EngineStatus): String = buildString {
        append("started=${status.started} mode=${status.mode} profile=${status.profileId}")
        appendLine(" screenOn=${status.screenOn} locked=${status.locked}")
        appendLine("holdOffs=${status.holdOffs.sorted()} guarded=${status.guarded.sorted()}")
        appendLine("missingKeys=${status.missingKeys.sorted()}")
        appendLine("constantsWriter=${status.constantsWriter} previous=${status.previousCaptured} stock=${status.stockCaptured}")
        appendLine("forcedWithoutWorker=${status.forcedWithoutWorker} storeError=${status.storeError}")
        appendLine("batteryFrozen=${status.batteryFrozen}")
        status.plan?.let { plan ->
            val night = plan.plan.night?.let { "${it.profileId} ${it.start}-${it.end}" }
            appendLine("plan day=${plan.plan.dayProfileId} night=$night slot=${plan.slot} override=${plan.override}")
        }
        status.levers.toSortedMap().forEach { (lever, it) ->
            appendLine("lever $lever ${it.state} wanted=${it.wanted} written=${it.written} seen=${it.seen} reason=${it.reason}")
        }
    }.trimEnd()

    /** The result card (SPEC 8.6 step 5), one line per step. */
    private fun describe(proof: QuickProof): String = buildString {
        appendLine("ok=${proof.ok} refused=${proof.refused}")
        proof.steps.forEach { appendLine("step ${it.kind} ${if (it.ok) "ok" else "failed"} ${it.line}") }
    }.trimEnd()

    private fun describe(result: JobResult): String = buildString {
        appendLine("ok=${result.ok} refused=${result.refused} notWritable=${result.notWritable.sorted()}")
        appendLine("skipped=${result.skipped} stockDifferences=${result.stockDifferences} unverified=${result.unverified}")
        result.actions.forEach { action ->
            appendLine("action ${action.lever} ${action.outcome} expected=${action.expected} readBack=${action.readBack} ${action.detail}")
        }
    }.trimEnd()

    private fun describe(record: SessionRecord): String = buildString {
        val row = record.session
        appendLine("verdict=${row.verdict} cause=${row.cause} profile=${row.profileId} id=${row.id}")
        appendLine("start=${row.start} end=${row.end} pluggedAtStart=${row.pluggedAtStart}")
        appendLine("expectedToDeepMs=${row.expectedToDeepMillis} timeToDeepMs=${row.timeToDeepMillis} lateByMs=${row.lateByMillis}")
        appendLine(
            "deepShare=${"%.3f".format(row.deepShare)} timeInDeepMs=${row.timeInDeepMillis} maintenance=${row.maintenanceWindows} " +
                "motionResets=${row.motionResets}",
        )
        appendLine(
            "battery=${row.batteryStart}->${row.batteryEnd} chargeUah=${row.chargeStartUah}->${row.chargeEndUah} " +
                "drainPerHour=${row.drainPerHour} drainMah=${row.drainMah}",
        )
        appendLine("ownWakeups=${row.ownWakeups} alarmsRead=${row.alarmsRead} collectionMs=${row.collectionMillis}")
        appendLine("eventLogFrom=${row.eventLogFrom} historyFrom=${row.historyFrom} mobileRadioMs=${row.mobileRadioMillis}")
        appendLine("transitions=${record.transitions.size} wakers=${record.wakers.size} snapshots=${record.snapshots.map { it.moment }}")
        row.breaks.lines().filter { it.isNotBlank() }.forEach { appendLine("break $it") }
        row.ownRuns.lines().filter { it.isNotBlank() }.forEach { appendLine("own $it") }
        row.historyFilled.lines().filter { it.isNotBlank() }.forEach { appendLine("historyFilled $it") }
        row.gaps.lines().filter { it.isNotBlank() }.forEach { appendLine("gap $it") }
        record.wakers.take(TOP_WAKERS).forEach {
            appendLine(
                "waker uid=${it.uid} self=${it.self} alarms=${it.alarmWakeups} network=${it.networkWakeups} " +
                    "kernel=${it.kernelWakeups} jobs=${it.jobs} wakelockMs=${it.wakelockMillis} ${it.name}",
            )
        }
        row.note?.lines()?.forEach { appendLine("note $it") }
    }.trimEnd()

    /** Every part of the night report (SPEC 8.5), one line per item, for the P3 gate. */
    private fun describe(report: NightReport): String = buildString {
        val row = report.session
        appendLine("night id=${report.night.id} session=${row.id} unlockedAt=${report.night.unlockedAt} start=${row.start} end=${row.end}")
        appendLine(
            "verdict=${row.verdict} timeInDeepMs=${row.timeInDeepMillis} maintenance=${row.maintenanceWindows} " +
                "drainPerHour=${row.drainPerHour} ownWakeups=${row.ownWakeups}",
        )
        report.timeline.forEach { appendLine("timeline ${it.state} ${it.start} ${it.end}") }
        report.light.forEach { appendLine("light ${it.time} ${it.fromState}->${it.toState} ${it.reason}") }
        report.breaks.forEach { appendLine("break $it") }
        report.gaps.forEach { appendLine("gap ${it.start} ${it.end}") }
        report.historyFilled.forEach { appendLine("historyFilled ${it.start} ${it.end}") }
        appendLine("recent nights=${report.recent.nights} drainPerHour=${report.recent.drainPerHour} deepShare=${report.recent.deepShare}")
        appendLine("stock nights=${report.stock.nights} drainPerHour=${report.stock.drainPerHour} deepShare=${report.stock.deepShare}")
        report.apps.forEach {
            val wakeups = it.alarmWakeups + it.networkWakeups
            appendLine("app uid=${it.uid} self=${it.self} wakeups=$wakeups jobs=${it.jobs} wakelockMs=${it.wakelockMillis} ${it.name}")
        }
        report.kernel.forEach { appendLine("kernel ${it.kernelWakeups} ${it.name}") }
        report.sensors.forEach {
            appendLine("sensor uid=${it.uid} ${it.listener} ${it.sensor} periodUs=${it.samplingPeriodUs} at=${it.moments}")
        }
        report.holdOffs.forEach { appendLine("holdOff ${it.time} ${it.kind} ${it.holdOff}") }
        report.drift.forEach { appendLine("drift ${it.time} ${it.lever} writer=${it.writer}") }
        report.actions.forEach { appendLine("action ${it.time} ${it.lever} ${it.outcome} ${it.cause}") }
    }.trimEnd()

    private companion object {
        const val CMD = "cmd"
        const val ARG = "arg"
        const val DEFAULT_ROWS = 20
        val NIGHT = Regex("""([^,\s]+)\s*,\s*(\d{2}:\d{2}(?::\d{2})?)\s*,\s*(\d{2}:\d{2}(?::\d{2})?)""")
        const val TOP_WAKERS = 5

        /** Under the 60 s a background broadcast may take. */
        const val TIMEOUT_MILLIS = 50_000L
    }
}
