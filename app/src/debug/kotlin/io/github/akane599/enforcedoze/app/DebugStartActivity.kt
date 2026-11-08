// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only: turns the engine on from adb (`am start -n …/.app.DebugStartActivity`). A
 * foreground service may only be started while the app is in front, as when the owner taps Start, so
 * the debug receiver cannot do it.
 */
class DebugStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineGraph.get(this).switch.enabled = true
        EngineService.start(this)
        finish()
    }
}
