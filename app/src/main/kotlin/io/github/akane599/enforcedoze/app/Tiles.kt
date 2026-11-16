// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.akane599.enforcedoze.R
import io.github.akane599.enforcedoze.app.ui.MainActivity
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.Tier
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.probe.LocalReader
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Quick Settings tiles (SPEC 9.2, 11.2) run in active mode: the system asks them for their state only when they
 * say it changed, so whatever changes it calls [refresh]. Their state comes from the store and the status the app
 * uses, and their actions go through the engine and the worker like the app's own switches.
 */
internal object Tiles {
    fun refresh(context: Context) {
        for (tile in listOf(PlanTileService::class.java, SensorsTileService::class.java)) {
            TileService.requestListeningState(context, ComponentName(context, tile))
        }
    }
}

/** What the tiles show and do, apart from the system's tile service so it can be tested (parity row 32). */
internal object TileRules {
    enum class PlanLook { NO_PLAN, OFF, ON }

    enum class PlanClick { SET_UP, RESUME, PAUSE }

    enum class SensorsClick { TURN_ON_SENSORS, CONFIRM_OFF, OPEN_APP }

    /** On only while the engine runs the plan; a paused plan or a stopped engine is off (Stock). */
    fun planLook(running: Boolean, mode: Mode, profileId: String?): PlanLook = when {
        profileId == null -> PlanLook.NO_PLAN
        !running || mode == Mode.PAUSED -> PlanLook.OFF
        else -> PlanLook.ON
    }

    fun planClick(running: Boolean, mode: Mode, profileId: String?): PlanClick = when (planLook(running, mode, profileId)) {
        PlanLook.NO_PLAN -> PlanClick.SET_UP
        PlanLook.OFF -> PlanClick.RESUME
        PlanLook.ON -> PlanClick.PAUSE
    }

    /**
     * [sensorsOff] is the sensor-privacy state, null when it could not be read. Turning sensors back on needs no
     * confirmation; turning them off stops the camera and microphone too, so it asks first.
     */
    fun sensorsClick(sensorsOff: Boolean?): SensorsClick = when (sensorsOff) {
        true -> SensorsClick.TURN_ON_SENSORS
        false -> SensorsClick.CONFIRM_OFF
        null -> SensorsClick.OPEN_APP
    }
}

/** Shared by the tiles: a scope that lives while the system listens, and the tile's text. */
abstract class EdTileService : TileService() {
    internal val graph: EngineGraph by lazy { EngineGraph.get(this) }
    private var scope: CoroutineScope? = null

    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        val running = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        return running.launch(block = block)
    }

    override fun onStartListening() {
        super.onStartListening()
        launch { show() }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    /** Reads the state and draws the tile. */
    protected abstract suspend fun show()

    protected fun draw(state: Int, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.subtitle = subtitle
        tile.stateDescription = subtitle
        tile.updateTile()
    }
}

/** "Doze plan": on runs the plan, off is Stock (the plan paused). A long press opens Plan. */
class PlanTileService : EdTileService() {
    override suspend fun show() {
        val (running, mode, profileId) = state()
        when (TileRules.planLook(running, mode, profileId)) {
            TileRules.PlanLook.NO_PLAN -> draw(Tile.STATE_INACTIVE, getString(R.string.tile_plan_none))
            TileRules.PlanLook.OFF -> draw(Tile.STATE_INACTIVE, getString(R.string.tile_plan_off))
            TileRules.PlanLook.ON -> draw(Tile.STATE_ACTIVE, profileId?.let { graph.profiles.profile(it)?.name ?: it }.orEmpty())
        }
    }

    /** The live status while the engine runs, else its saved state, as Home reads it. */
    private suspend fun state(): Triple<Boolean, Mode, String?> {
        val status = graph.hosts.value?.status?.value
        if (status != null) return Triple(true, status.mode, status.plan?.profileId)
        val saved = graph.store.load()
        return Triple(false, saved.mode, saved.plan?.profileId)
    }

    override fun onClick() {
        launch {
            val (running, mode, profileId) = state()
            val click = TileRules.planClick(running, mode, profileId)
            if (click == TileRules.PlanClick.SET_UP) {
                // No plan to run yet: the app sets one up first.
                openApp(Notifications.OPEN_PLAN)
                return@launch
            }
            val on = click == TileRules.PlanClick.RESUME
            graph.ui.access.send(turnOn = on) { host ->
                if (on) host.engine.resume() else host.engine.pause()
                ActionOutcome.Done
            }
            show()
        }
    }
}

/** "Sensors off": the all-sensors privacy switch (lever C's strongest form), confirmed before it turns on. */
class SensorsTileService : EdTileService() {
    private val local = LocalReader()

    override suspend fun show() {
        // A fresh read: the tile may be the first thing to run in this process, with the engine off.
        if (graph.controller.refresh().tier < Tier.T2) {
            draw(Tile.STATE_UNAVAILABLE, getString(R.string.tile_needs_worker))
            return
        }
        when (read()) {
            true -> draw(Tile.STATE_ACTIVE, getString(R.string.tile_sensors_on))
            false -> draw(Tile.STATE_INACTIVE, getString(R.string.tile_sensors_off))
            null -> draw(Tile.STATE_INACTIVE, getString(R.string.tile_sensors_unknown))
        }
    }

    /** The app's own read with `DUMP` first, which needs no worker; then the worker's. */
    private suspend fun read(): Boolean? {
        val own = withContext(Dispatchers.IO) { local.sensorService() }
        if (own.isSuccess) SensorServiceDump.parse(own.stdout).sensorPrivacy?.let { return it }
        val worker = graph.controller.withWorker { it.readSensorService() }
        return (worker as? WorkerResult.Ok)?.value?.let { SensorServiceDump.parse(it).sensorPrivacy }
    }

    override fun onClick() {
        launch {
            when (TileRules.sensorsClick(read())) {
                TileRules.SensorsClick.TURN_ON_SENSORS -> set(false)
                TileRules.SensorsClick.CONFIRM_OFF -> confirm()
                TileRules.SensorsClick.OPEN_APP -> openApp(Notifications.OPEN_SENSORS)
            }
        }
    }

    private fun confirm() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.tile_sensors_confirm_title)
            .setMessage(R.string.tile_sensors_confirm_body)
            .setPositiveButton(R.string.tile_sensors_confirm) { _, _ -> launch { set(true) } }
            .setNegativeButton(R.string.tile_cancel, null)
            .create()
        showDialog(dialog)
    }

    private suspend fun set(off: Boolean) {
        graph.controller.withWorker { it.setAllSensorsOff(off) }
        show()
    }
}

/** Opens the app at [target], collapsing the panel (API 34+ takes a PendingIntent). */
private fun TileService.openApp(target: String) {
    val intent = Intent(this, MainActivity::class.java)
        .putExtra(Notifications.EXTRA_OPEN, target)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val pending = PendingIntent.getActivity(this, target.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    startActivityAndCollapse(pending)
}
