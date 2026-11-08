// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.content.Context
import android.content.pm.ApplicationInfo
import io.github.akane599.enforcedoze.privilege.probe.CapabilityProber
import io.github.akane599.enforcedoze.privilege.probe.CapabilityReport
import io.github.akane599.enforcedoze.privilege.probe.DeviceInfo
import io.github.akane599.enforcedoze.privilege.probe.LocalReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The single source of privilege state (SPEC 4.2: one `PrivilegeState` flow from the binder-received,
 * binder-dead and permission-result listeners) and the way in to the worker.
 */
public class PrivilegeController internal constructor(
    private val scope: CoroutineScope,
    private val shizuku: ShizukuGateway,
    private val grants: GrantReader,
    private val root: RootGateway,
    private val binders: Map<WorkerSource, WorkerBinder>,
    private val session: WorkerSession,
    private val prober: CapabilityProber,
) {
    private val _state = MutableStateFlow(PrivilegeState.NONE)
    private var observation: AutoCloseable? = null
    private var workerStatus: Job? = null

    public val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    /** Starts listening to Shizuku and reads the current state. */
    public fun start() {
        if (observation != null) return
        workerStatus = scope.launch { session.status.collect { worker -> _state.update { it.copy(worker = worker) } } }
        observation = shizuku.observe {
            scope.launch {
                // A binder that comes back gets a fresh chance to connect (SPEC 4.3 "for the session").
                if (shizuku.status() is ShizukuStatus.Ready) session.forgetFailures()
                refresh()
            }
        }
        refresh()
    }

    /** Stops listening and unbinds the worker before returning, so a later [start] begins clean. */
    public suspend fun stop() {
        observation?.close()
        observation = null
        workerStatus?.cancel()
        workerStatus = null
        session.close()
    }

    /** Re-reads grants, Shizuku and root; call after a grant changes or the app returns to the foreground. */
    public fun refresh(): PrivilegeState = _state.updateAndGet {
        it.copy(grants = grants.read(), shizuku = shizuku.status(), rootGranted = root.isGranted())
    }

    public fun requestShizukuPermission() {
        shizuku.requestPermission()
    }

    /**
     * Asks the root manager for root (SPEC 4.1 T3). Only on the user's explicit request: it can show
     * Magisk's or KernelSU's prompt. Returns the new state.
     */
    public suspend fun requestRoot(): PrivilegeState {
        withContext(Dispatchers.IO) { root.request() }
        return refresh()
    }

    /** Runs [block] against the worker of the current tier, or reports why there is none. */
    public suspend fun <T> withWorker(block: suspend (DozeWorkerApi) -> T): WorkerResult<T> {
        val source = refresh().workerSource ?: return WorkerResult.Unavailable("tier ${_state.value.tier}: no worker")
        val binder = binders[source] ?: return WorkerResult.Unavailable("no binder for $source")
        return session.use(binder, block)
    }

    /**
     * Runs the capability probes (SPEC 4.4) with the worker of the current tier, or without one.
     * [includeChanges] also runs the probes that change state and undo it; only on the user's request.
     */
    public suspend fun probe(includeChanges: Boolean, device: DeviceInfo = DeviceInfo.current()): CapabilityReport {
        val before = refresh()
        val results = if (before.workerSource == null) {
            prober.run(null, includeChanges)
        } else {
            when (val run = withWorker { prober.run(it, includeChanges) }) {
                is WorkerResult.Ok -> run.value
                is WorkerResult.Unavailable, is WorkerResult.Failed -> prober.run(null, includeChanges)
            }
        }
        val state = _state.value
        val bound = state.worker as? WorkerStatus.Bound
        return CapabilityReport(
            device = device,
            tier = state.tier,
            workerSource = bound?.source,
            workerUid = bound?.uid,
            includedChanges = includeChanges,
            capturedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            results = results,
        )
    }

    public companion object {
        public fun create(context: Context, scope: CoroutineScope): PrivilegeController {
            val app = context.applicationContext
            val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return PrivilegeController(
                scope = scope,
                shizuku = RealShizukuGateway(app.packageName),
                grants = AndroidGrantReader(app),
                root = LibsuRootGateway,
                binders = mapOf(
                    WorkerSource.SHIZUKU to ShizukuWorkerBinder(app.packageName, debuggable, WorkerSource.SHIZUKU),
                    WorkerSource.SUI to ShizukuWorkerBinder(app.packageName, debuggable, WorkerSource.SUI),
                    WorkerSource.ROOT to RootWorkerBinder(app),
                ),
                session = WorkerSession(scope),
                prober = CapabilityProber(LocalReader(), app.packageName, SettingsConstantsWriter(app)),
            )
        }
    }
}
