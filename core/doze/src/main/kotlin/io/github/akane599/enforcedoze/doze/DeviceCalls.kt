// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs each device call in its own coroutine and waits for it at most [timeoutMillis] (SPEC 12.4,
 * ADR 0015). Binder calls block, so cancelling cannot cut one short: an overrunning call keeps running
 * in [scope] and the caller moves on with null. Calls with the same key (a [Lever], or [BATTERY]) run in
 * order, so a late write can never land after the one that replaced it.
 */
internal class DeviceCalls(private val scope: CoroutineScope, private val timeoutMillis: Long) {
    private val last = mutableMapOf<Any, Deferred<*>>()

    /** The result, or null when the call did not answer in time. Exceptions become [DeviceResult.Failed]. */
    suspend fun <T> run(key: Any?, block: suspend () -> DeviceResult<T>): DeviceResult<T>? {
        val before = key?.let { last[it] }
        val call = scope.async {
            before?.join()
            guarded(block)
        }
        if (key != null) last[key] = call
        val result = withTimeoutOrNull(timeoutMillis) { call.await() }
        last.entries.removeAll { it.value.isCompleted }
        return result
    }

    /** The key of the quick proof's battery freeze and its undo, which is not a lever (SPEC 8.6). */
    object BATTERY

    /** No call is still running, reads included. */
    val idle: Boolean get() = scope.coroutineContext.job.children.none { it.isActive }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(block: suspend () -> DeviceResult<T>): DeviceResult<T> = try {
        block()
    } catch (e: CancellationException) {
        // Only this call being cancelled is a cancellation; an adapter's own timeout is a failed call,
        // and letting it through would end the actor (ADR 0015).
        currentCoroutineContext().ensureActive()
        DeviceResult.Failed(e.toString())
    } catch (e: Exception) {
        // An adapter bug must not kill the actor; it is logged like any refused call.
        DeviceResult.Failed(e.toString())
    }
}
