// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import io.github.akane599.enforcedoze.doze.DozeEngine
import io.github.akane599.enforcedoze.doze.EngineClock
import io.github.akane599.enforcedoze.doze.EngineJournal
import io.github.akane599.enforcedoze.doze.EngineStart
import io.github.akane599.enforcedoze.doze.EngineStatus
import io.github.akane599.enforcedoze.doze.EngineStore
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.ProfileCatalog
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.ConstantsWriter
import io.github.akane599.enforcedoze.privilege.PrivilegeController
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Runs the engine inside the app (ADR 0015): turns screen, hold-off, maintenance-window and settings
 * broadcasts and privilege changes into engine commands. Its one timer is the plan's non-wakeup window-start
 * alarm (ADR 0024); every other command starts from a system event. Callbacks arrive on the main thread and
 * each queues its command before returning, so commands keep their order.
 */
public class EngineHost internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controller: PrivilegeController,
    /** The engine this host drives; the app sends owner commands (profile, pause, restore stock) to it directly. */
    public val engine: DozeEngine,
    private val sources: List<HoldOffSource>,
    private val keepAwake: KeepAwakeCheck,
) {
    private val main = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val nightAlarm = NightAlarm(context, main, power::isInteractive) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { engine.nightStarted() }
    }
    private val reported = mutableMapOf<HoldOff, Boolean>()
    private var screenReceiver: BroadcastReceiver? = null
    private var observer: ContentObserver? = null
    private var privilege: Job? = null
    private var plan: Job? = null

    public val status: StateFlow<EngineStatus> get() = engine.status

    /** Registers every source, starts the engine with what they show now, then follows privilege changes. */
    public suspend fun start(profile: Profile?) {
        check(screenReceiver == null) { "already started" }
        val holdOffs = withContext(Dispatchers.Main.immediate) {
            sources.filter { source ->
                source.start { active -> holdOff(source.holdOff, active) }.also { reported[source.holdOff] = it }
            }.map { it.holdOff }.toSet()
        }
        val interactive = power.isInteractive
        val writable = DeviceMapping.writable(controller.refresh())
        engine.start(EngineStart(profile, interactive, holdOffs, writable, locked = interactive && keyguard.isKeyguardLocked))
        withContext(Dispatchers.Main.immediate) {
            registerScreen()
            registerConstantsObserver()
        }
        privilege = scope.launch {
            controller.state.map(DeviceMapping::writable).distinctUntilChanged().collect { engine.setWritable(it) }
        }
        // The alarm follows the plan's window: set at start, moved when the plan changes.
        plan = scope.launch(Dispatchers.Main.immediate) {
            engine.status.map { it.plan?.plan?.night }.distinctUntilChanged().collect(nightAlarm::request)
        }
    }

    /** Unregisters everything; the engine stops with its scope. Main thread. */
    public fun stop() {
        privilege?.cancel()
        privilege = null
        plan?.cancel()
        plan = null
        nightAlarm.cancel()
        screenReceiver?.let(context::unregisterReceiver)
        screenReceiver = null
        observer?.let(context.contentResolver::unregisterContentObserver)
        observer = null
        sources.forEach { it.stop() }
        reported.clear()
    }

    private fun holdOff(holdOff: HoldOff, active: Boolean) {
        if (reported[holdOff] == active) return
        reported[holdOff] = active
        scope.launch(start = CoroutineStart.UNDISPATCHED) { engine.holdOff(holdOff, active) }
    }

    /**
     * Screen off, screen on, unlock and the end of deep idle reach a receiver registered at run time only. The
     * broadcast is held open with `goAsync` while the engine works, at most [BROADCAST_BOUND_MILLIS], so the
     * phone does not sleep between the writes and their read-backs; past the bound the engine finishes on its
     * own. `ACTION_USER_PRESENT` is sent once the keyguard is dismissed (ADR 0017). Deep idle ending with the
     * screen off is a maintenance window (ADR 0020), where a night whose alarm found the screen off begins
     * (ADR 0024).
     */
    private fun registerScreen() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> nightAlarm.screenOff()
                    Intent.ACTION_SCREEN_ON -> nightAlarm.screenOn()
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> nightAlarm.clockChanged()
                }
                val command: suspend () -> Unit = when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> ::screenOff
                    Intent.ACTION_SCREEN_ON -> ::screenOn
                    Intent.ACTION_USER_PRESENT -> engine::unlocked
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> maintenanceWindow() ?: return
                    else -> return
                }
                val pending = goAsync()
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        withTimeoutOrNull(BROADCAST_BOUND_MILLIS) { command() }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Exported because SystemUI, not the system uid, sends USER_PRESENT (KeyguardViewMediator), and an unexported
        // receiver of protected broadcasts drops any other sender ("Exported Denial", BroadcastController and
        // BroadcastSkipPolicy, android-17.0.0_r1). Every action here is protected, so only the platform can send it.
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        screenReceiver = receiver
    }

    /** Deep idle left with the screen off: a maintenance window. Entering idle, or leaving it at screen on, is not. */
    private fun maintenanceWindow(): (suspend () -> Unit)? =
        engine::maintenanceWindow.takeIf { !power.isDeviceIdleMode && !power.isInteractive }

    /** A keep-awake app counts only at screen off, so its hold-off is set before the engine engages. */
    private suspend fun screenOff() {
        val kept = withContext(Dispatchers.IO) { keepAwake.foregroundIsKept(System.currentTimeMillis()) }
        engine.holdOff(HoldOff.KEEP_AWAKE_APP, kept)
        engine.screenOff()
    }

    /**
     * The screen first, so ending the keep-awake hold-off never engages anything with the screen on. The
     * keyguard is read again after the screen-on command: an unlock whose broadcast was queued before that
     * command found nothing to release, so it is replayed and "until unlock" cannot outlast the lock screen.
     */
    private suspend fun screenOn() {
        controller.refresh()
        val locked = keyguard.isKeyguardLocked
        engine.screenOn(locked)
        if (locked && !keyguard.isKeyguardLocked) engine.unlocked()
        engine.holdOff(HoldOff.KEEP_AWAKE_APP, false)
    }

    /** Any write of the global string, the engine's own included; the engine's read-back tells them apart (ADR 0014). */
    private fun registerConstantsObserver() {
        val watch = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { engine.foreignWriteSeen() }
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(DeviceIdleSettings.SETTING), false, watch)
        observer = watch
    }

    public companion object {
        /** Under the 10 s a foreground broadcast may take. */
        public const val BROADCAST_BOUND_MILLIS: Long = 8_000

        /** The host for the app: the real privilege layer, the platform's hold-off signals and the system clock. */
        @Suppress("LongParameterList") // The module's composition root: each argument is a separate app-owned dependency.
        public fun create(
            context: Context,
            scope: CoroutineScope,
            controller: PrivilegeController,
            store: EngineStore,
            journal: EngineJournal,
            keepAwakeApps: () -> Set<String> = { emptySet() },
            /** The plan's profiles: the built-ins, plus the app's custom profiles when it passes its store. */
            catalog: ProfileCatalog = ProfileCatalog { BuiltInProfiles.byId(it) },
        ): EngineHost {
            val app = context.applicationContext
            val device = PrivilegedDozeDevice(
                worker = ControllerWorkerPort(controller),
                writer = ConstantsWriter.create(app),
                shell = ProcessLocalShell(),
                grants = { controller.state.value.grants },
            )
            val engine = DozeEngine(device, store, journal, EngineClock { Instant.now() }, app.packageName, scope, catalog = catalog)
            val sources = listOf(
                CallSource(app),
                AudioSource(app, Handler(Looper.getMainLooper())),
                CarModeSource(app),
                HotspotSource(app),
                ChargingSource(app),
            )
            return EngineHost(app, scope, controller, engine, sources, KeepAwakeCheck(app, keepAwakeApps))
        }
    }
}
