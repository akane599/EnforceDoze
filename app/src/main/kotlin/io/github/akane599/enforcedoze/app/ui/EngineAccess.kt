// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.util.Log
import android.content.Context
import io.github.akane599.enforcedoze.app.EngineGraph
import io.github.akane599.enforcedoze.app.EngineService
import io.github.akane599.enforcedoze.R
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.android.EngineHost
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one way screens reach the engine (ADR 0029). The host exists only while [EngineService] runs, so no
 * view model holds it: a command looks it up when it is sent, and an engine that is off answers with a typed
 * [ActionOutcome.Unavailable] instead of a command queued where nothing drains it.
 */
internal class EngineAccess(private val context: Context, private val graph: EngineGraph) {
    /** The engine's status while it runs; null while it is off. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val status: Flow<EngineStatus?> = graph.hosts.flatMapLatest { host -> host?.status ?: flowOf(null) }

    val running: Boolean get() = graph.host != null

    /**
     * Sends [block] to the running engine. With [turnOn], an engine that is off is switched on first (the owner
     * asked for something that needs it), and the command waits up to [START_TIMEOUT_MS] for it.
     */
    suspend fun send(turnOn: Boolean = false, block: suspend (EngineHost) -> ActionOutcome): ActionOutcome {
        val host = graph.host ?: if (turnOn) start() else null
        val why = if (turnOn) R.string.ui_engine_did_not_start else R.string.ui_engine_off
        host ?: return ActionOutcome.Unavailable(context.getString(why))
        return try {
            block(host)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
        }
    }

    private suspend fun start(): EngineHost? {
        val wasEnabled = graph.switch.enabled
        graph.switch.enabled = true
        try {
            EngineService.start(context)
        } catch (refused: IllegalStateException) {
            // From the background (an automation call) Android refuses a foreground service start unless the app is
            // exempt from battery optimisation (ForegroundServiceStartNotAllowedException, API 31+): nothing started.
            Log.w(TAG, "engine service not started", refused)
            graph.switch.enabled = wasEnabled
            return null
        }
        return withTimeoutOrNull(START_TIMEOUT_MS) { graph.hosts.filterNotNull().first() }
    }

    private companion object {
        const val START_TIMEOUT_MS = 10_000L
        const val TAG = "EngineAccess"
    }
}
