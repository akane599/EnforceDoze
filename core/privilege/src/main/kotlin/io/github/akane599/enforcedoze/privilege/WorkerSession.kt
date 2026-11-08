// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

public sealed interface WorkerResult<out T> {
    public data class Ok<T>(val value: T) : WorkerResult<T>

    /** No worker: below T2, or it did not connect in time this session. */
    public data class Unavailable(val reason: String) : WorkerResult<Nothing>

    /** The call itself failed, for example because the worker died. */
    public data class Failed(val error: Throwable) : WorkerResult<Nothing>
}

/**
 * Connects the worker for short jobs and lets it go (SPEC 4.3): binds on the first call, with a 5 s
 * limit, and unbinds 60 s after the last call ends. A source that does not connect in time stays
 * unavailable until [forgetFailures], which the controller calls when the Shizuku binder comes back.
 */
public class WorkerSession(
    private val scope: CoroutineScope,
    private val bindTimeoutMillis: Long = WorkerContract.BIND_TIMEOUT_MILLIS,
    private val idleUnbindMillis: Long = WorkerContract.IDLE_UNBIND_MILLIS,
) {
    private val mutex = Mutex()
    private var binder: WorkerBinder? = null
    private var api: DozeWorkerApi? = null
    private var active = 0
    private var generation = 0
    private var idleJob: Job? = null
    private val failures = mutableMapOf<WorkerSource, String>()
    private val _status = MutableStateFlow<WorkerStatus>(WorkerStatus.Idle)

    public val status: StateFlow<WorkerStatus> = _status.asStateFlow()

    // A dead worker surfaces as any RuntimeException or RemoteException; it becomes WorkerResult.Failed.
    @Suppress("TooGenericExceptionCaught")
    public suspend fun <T> use(with: WorkerBinder, block: suspend (DozeWorkerApi) -> T): WorkerResult<T> {
        val (worker, bound) = when (val connected = connect(with)) {
            is WorkerResult.Ok -> connected.value
            is WorkerResult.Unavailable -> return connected
            is WorkerResult.Failed -> return connected
        }
        return try {
            WorkerResult.Ok(block(worker))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WorkerResult.Failed(e)
        } finally {
            release(bound)
        }
    }

    public suspend fun forgetFailures() {
        mutex.withLock {
            failures.clear()
            if (_status.value is WorkerStatus.Unavailable) _status.value = WorkerStatus.Idle
        }
    }

    /** Unbinds now. */
    public suspend fun close() {
        mutex.withLock { disconnectLocked() }
    }

    /** Returns the worker and the number of the bind it came from. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun connect(with: WorkerBinder): WorkerResult<Pair<DozeWorkerApi, Int>> = mutex.withLock {
        failures[with.source]?.let { return WorkerResult.Unavailable(it) }
        idleJob?.cancel()
        val current = api
        if (current != null && binder === with) {
            active++
            return WorkerResult.Ok(current to generation)
        }
        disconnectLocked()
        with.awaitUnbound()
        val bound = ++generation
        val connected = try {
            withTimeoutOrNull(bindTimeoutMillis) { with.bind(onLost = { lost(bound) }) }
        } catch (e: CancellationException) {
            with.unbind()
            throw e
        } catch (e: Exception) {
            with.unbind()
            return WorkerResult.Failed(e)
        }
        if (connected == null) {
            with.unbind()
            val reason = "no connection within ${bindTimeoutMillis / MILLIS_PER_SECOND} s"
            failures[with.source] = reason
            _status.value = WorkerStatus.Unavailable(with.source, reason)
            return WorkerResult.Unavailable(reason)
        }
        val uid = try {
            connected.uid()
        } catch (e: CancellationException) {
            with.unbind()
            throw e
        } catch (e: Exception) {
            with.unbind()
            return WorkerResult.Failed(e)
        }
        binder = with
        api = connected
        active = 1
        _status.value = WorkerStatus.Bound(with.source, uid)
        WorkerResult.Ok(connected to bound)
    }

    private suspend fun release(bound: Int) {
        mutex.withLock {
            // A call that outlived its connection (lost, or replaced by another bind) owns no count.
            if (generation != bound || api == null) return
            active--
            if (active > 0) return
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(idleUnbindMillis)
                mutex.withLock { if (active == 0) disconnectLocked() }
            }
        }
    }

    /** The connection of bind number [bound] died; ignored if a later bind has replaced it. */
    private fun lost(bound: Int) {
        scope.launch {
            mutex.withLock {
                if (generation == bound && api != null) disconnectLocked()
            }
        }
    }

    private fun disconnectLocked() {
        generation++
        idleJob?.cancel()
        idleJob = null
        binder?.unbind()
        binder = null
        api = null
        active = 0
        if (_status.value is WorkerStatus.Bound) _status.value = WorkerStatus.Idle
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
