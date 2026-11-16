// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.akane599.enforcedoze.app.EngineGraph
import io.github.akane599.enforcedoze.app.ui.AppPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The automation API's one exported receiver (SPEC 11.1). It holds no permission: each call carries a client's
 * token instead (ADR 0033). It is disabled while Settings › Automation API is off.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Known only when the sender opted in to share its identity (API 34+); checked against the token's package.
        val sender = sentFromPackage
        val graph = EngineGraph.get(context)
        val pending = goAsync()
        graph.scope.launch {
            val call = graph.scope.launch { graph.ui.automation.handle(intent, sender) }
            // A call that waits on the engine's start (up to 10 s) carries on after the broadcast is finished, inside
            // Android's 10 s limit for a broadcast sent to the foreground queue.
            withTimeoutOrNull(FINISH_MILLIS) { call.join() }
            pending.finish()
        }
    }

    internal companion object {
        private const val FINISH_MILLIS = 8_000L

        fun enable(context: Context, on: Boolean) {
            val component = ComponentName(context, AutomationReceiver::class.java)
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (context.packageManager.getComponentEnabledSetting(component) == state) return
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }

        /**
         * At process start: a backup restore brings the switch back but not the component state (nor the tokens, which
         * live outside backup), so the receiver is matched to the switch again.
         */
        fun sync(context: Context) = enable(context, AppPrefs(context).automation)
    }
}
