// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.akane599.enforcedoze.app.EngineGraph
import kotlinx.coroutines.launch

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
            try {
                graph.ui.automation.handle(intent, sender)
            } finally {
                pending.finish()
            }
        }
    }
}
