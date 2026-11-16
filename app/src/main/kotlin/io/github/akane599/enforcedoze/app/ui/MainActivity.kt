// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.akane599.enforcedoze.app.EngineGraph
import io.github.akane599.enforcedoze.app.Notifications
import io.github.akane599.enforcedoze.app.PlanTileService
import io.github.akane599.enforcedoze.app.SensorsTileService
import io.github.akane599.enforcedoze.design.EdTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Compose app, the launcher activity (ADR 0029). Notifications and the tiles' long press open a screen in it. */
class MainActivity : ComponentActivity() {
    private val opening = MutableStateFlow<Route?>(null)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val services = EngineGraph.get(this).ui
        if (savedInstanceState == null) opening.value = target(intent)
        // SPEC 9.2: the engine line and night reports need the notification permission; asked once, after onboarding.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                services.prefs.changes.collect {
                    if (services.prefs.onboarded && !services.prefs.notificationsAsked && !Notifications.allowed(this@MainActivity)) {
                        services.prefs.notificationsAsked = true
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
        setContent {
            val open by opening.collectAsState()
            val wallpaperAccent by remember { services.prefs.changes.map { services.prefs.wallpaperAccent } }
                .collectAsState(initial = services.prefs.wallpaperAccent)
            EdTheme(wallpaperAccent = wallpaperAccent) { EnforceDozeApp(services, open) { opening.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opening.value = target(intent)
    }

    private fun target(intent: Intent?): Route? {
        intent ?: return null
        if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            val tile = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
            return when (tile?.className) {
                PlanTileService::class.java.name -> PlanKey
                SensorsTileService::class.java.name -> SensorsKey
                else -> null
            }
        }
        // Coulomb's report link (ADR 0035); a session that is gone opens the Log.
        if (intent.action == OPEN_REPORT) return intent.getLongExtra(EXTRA_SESSION_ID, -1).takeIf { it >= 0 }?.let(::ReportKey) ?: LogKey
        return when (intent.getStringExtra(Notifications.EXTRA_OPEN)) {
            Notifications.OPEN_REPORT -> intent.getLongExtra(Notifications.EXTRA_SESSION, -1).takeIf { it >= 0 }?.let(::ReportKey)
            Notifications.OPEN_PLAN -> PlanKey
            Notifications.OPEN_SENSORS -> SensorsKey
            else -> null
        }
    }

    private companion object {
        const val OPEN_REPORT = "io.github.akane599.enforcedoze.action.OPEN_REPORT"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
