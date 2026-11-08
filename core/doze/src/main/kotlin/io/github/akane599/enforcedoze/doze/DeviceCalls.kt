// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs each device call in its own coroutine and waits for it at most [timeoutMillis] (SPEC 12.4,
 * ADR 0015). Binder calls block, so cancelling cannot cut one short: an overrunning call keeps running
 * in [scope] and the caller moves on with null. Calls for the same lever run in order, so a late write
 * can never land after the one that replaced it.
 */
internal class DeviceCalls(private val scope: CoroutineScope, private val timeoutMillis: Long) {
    private val last = mutableMapOf<Lever, Deferred<*>>()

    /** The result, or null when the call did not answer in time. Exceptions become [DeviceResult.Failed]. */
    suspend fun <T> run(lever: Lever?, block: suspend () -> DeviceResult<T>): DeviceResult<T>? {
        val before = lever?.let { last[it] }
        val call = scope.async {
            before?.join()
            guarded(block)
        }
        if (lever != null) last[lever] = call
        val result = withTimeoutOrNull(timeoutMillis) { call.await() }
        last.entries.removeAll { it.value.isCompleted }
        return result
    }

    /** No call is still running, reads included. */
    val idle: Boolean get() = scope.coroutineContext.job.children.none { it.isActive }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(block: suspend () -> DeviceResult<T>): DeviceResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // An adapter bug must not kill the actor; it is logged like any refused call.
        DeviceResult.Failed(e.toString())
    }
}
