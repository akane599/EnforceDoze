// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akane599.enforcedoze.app.EngineGraph
import io.github.akane599.enforcedoze.design.EdTheme

/** The Compose app, the launcher activity (ADR 0029); the upstream screens stay reachable only through their code. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val services = EngineGraph.get(this).ui
        setContent { EdTheme { EnforceDozeApp(services) } }
    }
}
