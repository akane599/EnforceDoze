// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.doze.Mode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The foreground service the engine lives in (SPEC 12.4): type `specialUse`, started at boot and after
 * updates when the engine is on. It does no work of its own; the engine reacts to broadcasts.
 */
class EngineService : Service() {
    private val graph by lazy { EngineGraph.get(this) }
    private var lines: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.channels(this)
        startForeground(
            Notifications.ENGINE_ID,
            Notifications.engine(this, getString(R.string.engine_notification_title)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        graph.scope.launch { graph.start() }
        lines = graph.scope.launch { followStatus() }
    }

    /** SPEC 9.2: the notification names the profile in force in one line, and the tiles follow the same status. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followStatus() {
        graph.hosts.flatMapLatest { host -> host?.status ?: flowOf(null) }
            .map { status -> status?.let { it.mode to it.plan?.profileId } }
            .distinctUntilChanged()
            .collect { state ->
                Tiles.refresh(this)
                val (mode, profileId) = state ?: return@collect
                val line = if (mode == Mode.PAUSED || profileId == null) {
                    getString(R.string.engine_line_paused)
                } else {
                    getString(R.string.engine_line, graph.profiles.profile(profileId)?.name ?: profileId)
                }
                Notifications.updateEngine(this, line)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        lines?.cancel()
        graph.scope.launch {
            graph.stop()
            Tiles.refresh(this@EngineService)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, EngineService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineService::class.java))
        }
    }
}

/** Starts the engine after a reboot or an update of the app, when the owner turned it on (SPEC 12.4). */
class EngineStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val starts = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (starts && EngineGraph.get(context).switch.enabled) EngineService.start(context)
    }
}
