// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.akylas.enforcedoze.R
import kotlinx.coroutines.launch

/**
 * The foreground service the engine lives in (SPEC 12.4): type `specialUse`, started at boot and after
 * updates when the engine is on. It does no work of its own; the engine reacts to broadcasts.
 */
class EngineService : Service() {
    private val graph by lazy { EngineGraph.get(this) }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        graph.scope.launch { graph.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        graph.scope.launch { graph.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, getString(R.string.engine_channel_name), NotificationManager.IMPORTANCE_MIN)
        channel.description = getString(R.string.engine_channel_description)
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.icon_monochrome)
            .setContentTitle(getString(R.string.engine_notification_title))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "engine"
        private const val NOTIFICATION_ID = 0x0D0E

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
