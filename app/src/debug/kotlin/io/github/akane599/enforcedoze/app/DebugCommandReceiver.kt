// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.doze.DozeEngine
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.JobResult
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.SwitchReason
import io.github.akane599.enforcedoze.doze.android.EngineHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Debug builds only: drives the engine from adb for the emulator gates (SPEC 13.2, 13.3). Only callers
 * holding DUMP (the shell) can reach it. The reply is the broadcast's result data:
 *
 *     adb shell am broadcast -n io.github.akane599.enforcedoze/io.github.akane599.enforcedoze.app.DebugCommandReceiver \
 *         --es cmd profile --es arg test-everyday
 *
 * Start the engine first with `am start -n io.github.akane599.enforcedoze/io.github.akane599.enforcedoze.app.DebugStartActivity`.
 * Commands: `stop`, `status`, `profile <id>`, `capture-stock`, `restore-stock`,
 * `return-to-previous`, `pause`, `resume`, `reapply <true|false>`, `clear-guard <LEVER>`,
 * `min-session <seconds|default>`, `last-session`, `events <count>`, `actions <count>`.
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
        return engineCommand(host, arg)
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
    )

    private val engineCommands: Map<String, suspend (EngineHost, String?) -> String> = mapOf(
        "status" to { host, _ -> describe(host.status.value) },
        "profile" to { host, arg ->
            val profile = arg?.let(Profiles::byId)
            if (profile == null) "unknown profile $arg" else after(host) { setProfile(profile, SwitchReason.OWNER) }
        },
        "capture-stock" to { host, _ -> describe(host.engine.captureStock()) },
        "restore-stock" to { host, _ -> describe(host.engine.restoreStock()) },
        "return-to-previous" to { host, _ -> describe(host.engine.returnToPrevious()) },
        "pause" to { host, _ -> after(host) { pause() } },
        "resume" to { host, _ -> after(host) { resume() } },
        "reapply" to { host, arg -> after(host) { setReapplyOnDrift(arg.toBoolean()) } },
        "clear-guard" to { host, arg -> after(host) { clearGuard(Lever.valueOf(arg.orEmpty())) } },
    )

    private fun rows(arg: String?): Int = arg?.toIntOrNull() ?: DEFAULT_ROWS

    /** Runs an engine command, then reports the status it left. */
    private suspend fun after(host: EngineHost, command: suspend DozeEngine.() -> Unit): String {
        host.engine.command()
        return describe(host.status.value)
    }

    private fun describe(status: EngineStatus): String = buildString {
        appendLine("started=${status.started} mode=${status.mode} profile=${status.profileId} screenOn=${status.screenOn}")
        appendLine("holdOffs=${status.holdOffs.sorted()} guarded=${status.guarded.sorted()}")
        appendLine("missingKeys=${status.missingKeys.sorted()}")
        appendLine("constantsWriter=${status.constantsWriter} previous=${status.previousCaptured} stock=${status.stockCaptured}")
        appendLine("forcedWithoutWorker=${status.forcedWithoutWorker} storeError=${status.storeError}")
        status.levers.toSortedMap().forEach { (lever, it) ->
            appendLine("lever $lever ${it.state} wanted=${it.wanted} written=${it.written} seen=${it.seen} reason=${it.reason}")
        }
    }.trimEnd()

    private fun describe(result: JobResult): String = buildString {
        appendLine("ok=${result.ok} refused=${result.refused} notWritable=${result.notWritable.sorted()}")
        appendLine("skipped=${result.skipped} stockDifferences=${result.stockDifferences} unverified=${result.unverified}")
        result.actions.forEach { action ->
            appendLine("action ${action.lever} ${action.outcome} expected=${action.expected} readBack=${action.readBack} ${action.detail}")
        }
    }.trimEnd()

    private fun describe(row: SessionRow): String = buildString {
        appendLine("verdict=${row.verdict} cause=${row.cause} note=${row.note} profile=${row.profileId}")
        appendLine("start=${row.start} end=${row.end} pluggedAtStart=${row.pluggedAtStart}")
        appendLine("expectedToDeepMs=${row.expectedToDeepMillis} timeToDeepMs=${row.timeToDeepMillis} lateByMs=${row.lateByMillis}")
        appendLine("deepShare=${"%.3f".format(row.deepShare)} maintenance=${row.maintenanceWindows} motionResets=${row.motionResets}")
        if (row.breaks.isNotEmpty()) row.breaks.lines().forEach { appendLine("break $it") }
    }.trimEnd()

    private companion object {
        const val CMD = "cmd"
        const val ARG = "arg"
        const val DEFAULT_ROWS = 20

        /** Under the 60 s a background broadcast may take. */
        const val TIMEOUT_MILLIS = 50_000L
    }
}
