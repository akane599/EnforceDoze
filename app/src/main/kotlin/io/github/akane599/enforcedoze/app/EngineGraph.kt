// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.content.Context
import androidx.core.content.edit
import io.github.akane599.enforcedoze.app.ui.AppPrefs
import io.github.akane599.enforcedoze.app.ui.AppServices
import io.github.akane599.enforcedoze.data.EngineFiles
import io.github.akane599.enforcedoze.data.EvidenceDatabase
import io.github.akane599.enforcedoze.data.JournalDao
import io.github.akane599.enforcedoze.data.ProfileStore
import io.github.akane599.enforcedoze.data.RecorderFiles
import io.github.akane599.enforcedoze.data.RoomEngineJournal
import io.github.akane599.enforcedoze.data.SessionDao
import io.github.akane599.enforcedoze.doze.android.EngineHost
import io.github.akane599.enforcedoze.privilege.PrivilegeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The composition root for the process, wired by hand (ADR 0029). The engine runs only while [EngineService]
 * is up; [start] and [stop] are called from there.
 */
internal class EngineGraph private constructor(context: Context) {
    val app: Context = context.applicationContext
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller: PrivilegeController = PrivilegeController.create(app, scope)
    val store: EngineFiles = EngineFiles(File(app.filesDir, ENGINE_DIR))
    val profiles: Profiles = Profiles(ProfileStore(File(app.filesDir, PROFILES_DIR)))
    private val database: EvidenceDatabase by lazy { EvidenceDatabase.open(app) }
    val journal: JournalDao by lazy { database.journal() }
    val sessions: SessionDao by lazy { database.sessions() }
    val recorderFiles: RecorderFiles = RecorderFiles(File(app.filesDir, SESSIONS_DIR))
    val switch: EngineSwitch = EngineSwitch(app)
    val prefs: AppPrefs = AppPrefs(app)

    /** The screens' shared services (ADR 0029), made on first use: the service alone never needs them. */
    val ui: AppServices by lazy { AppServices(this) }

    private val lifecycle = Mutex()
    private var running: Running? = null

    private class Running(val scope: CoroutineScope, val host: EngineHost, val recorder: SessionRecorder)

    private val hostState = MutableStateFlow<EngineHost?>(null)

    /** The running host, or null while the service is down; screens reach it only through `EngineAccess` (ADR 0029). */
    val hosts: StateFlow<EngineHost?> = hostState.asStateFlow()

    /** The running host, or null while the service is down. */
    val host: EngineHost? get() = running?.host

    val recorder: SessionRecorder? get() = running?.recorder

    /** Starts the privilege layer, the engine with the saved profile, and the session recorder; idempotent. */
    suspend fun start() {
        lifecycle.withLock {
            if (running != null) return
            controller.start()
            // The engine's actor lives in a child scope, so a stop ends it without ending the graph.
            val engineScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))
            val host = EngineHost.create(
                app,
                engineScope,
                controller,
                store,
                RoomEngineJournal(journal),
                keepAwakeApps = { prefs.keepAwake },
                catalog = profiles,
            )
            host.start(store.load().profileId?.let { profiles.profile(it) })
            val recorder = SessionRecorder(this, engineScope, host)
            withContext(Dispatchers.Main.immediate) { recorder.start() }
            running = Running(engineScope, host, recorder)
            hostState.value = host
        }
    }

    suspend fun stop() {
        lifecycle.withLock {
            val current = running ?: return
            running = null
            hostState.value = null
            withContext(Dispatchers.Main.immediate) {
                current.recorder.stop()
                current.host.stop()
            }
            current.scope.cancel()
            controller.stop()
        }
    }

    companion object {
        private const val ENGINE_DIR = "engine"
        private const val SESSIONS_DIR = "sessions"
        private const val PROFILES_DIR = "profiles"

        // Holds only the application context, which lives as long as the process.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: EngineGraph? = null

        fun get(context: Context): EngineGraph = instance ?: synchronized(this) {
            instance ?: EngineGraph(context).also { instance = it }
        }
    }
}

/** Whether the owner turned the engine on; the boot and update receivers start the service only then. */
internal class EngineSwitch(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit { putBoolean(ENABLED, value) }

    private companion object {
        const val PREFS = "engine"
        const val ENABLED = "enabled"
    }
}
