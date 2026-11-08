// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Doze engine (SPEC 5, ADR 0015): one actor that owns every write, so a profile switch, a hold-off
 * and a drift fix can never interleave (SPEC 12.4). Each call queues a command and suspends until it is
 * done; a caller that stops waiting (a `goAsync` bound) does not stop the command. The engine owns no
 * timer: it works only when told about an event.
 *
 * [callDispatcher] runs the device calls, which may block on binder.
 */
public class DozeEngine(
    device: DozeDevice,
    store: EngineStore,
    journal: EngineJournal,
    clock: EngineClock,
    /** The package lever C keeps allowed: the app's own, which holds no sensor connections (ADR 0004). */
    allowPackage: String,
    scope: CoroutineScope,
    callDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val calls = DeviceCalls(CoroutineScope(SupervisorJob(scope.coroutineContext[Job]) + callDispatcher), CALL_TIMEOUT_MILLIS)
    private val core = EngineCore(device, store, journal, clock, allowPackage, calls)
    private val inbox = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val mutableStatus = MutableStateFlow(EngineStatus())

    /** Where each lever stands, updated after every command. */
    public val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            for (command in inbox) command()
        }
    }

    /** No device call is still running, timed-out ones included. */
    internal val callsIdle: Boolean get() = calls.idle

    public suspend fun start(start: EngineStart): Unit = submit { start(start) }

    public suspend fun setProfile(profile: Profile, reason: SwitchReason): Unit = submit { setProfile(profile, reason) }

    public suspend fun screenOff(): Unit = submit { screen(on = false) }

    public suspend fun screenOn(): Unit = submit { screen(on = true) }

    public suspend fun holdOff(holdOff: HoldOff, active: Boolean): Unit = submit { holdOff(holdOff, active) }

    public suspend fun setWritable(writable: Writable): Unit = submit { setWritable(writable) }

    /** The app's observer on `device_idle_constants` fired (ADR 0014). */
    public suspend fun foreignWriteSeen(): Unit = submit { foreignWriteSeen() }

    public suspend fun pause(): Unit = submit { setMode(Mode.PAUSED) }

    /** Back to managed mode; tripped guards are cleared, since the owner chose to resume. */
    public suspend fun resume(): Unit = submit { setMode(Mode.MANAGED) }

    /** The owner answered the loop-guard question for [lever] with "keep re-applying". */
    public suspend fun clearGuard(lever: Lever): Unit = submit { clearGuard(lever) }

    public suspend fun setReapplyOnDrift(on: Boolean): Unit = submit { setReapplyOnDrift(on) }

    /** Apps kept on a trimmed allowlist; null until the owner chooses, and nothing is trimmed before. */
    public suspend fun setAllowlistKeep(packages: Set<String>?): Unit = submit { setAllowlistKeep(packages) }

    public suspend fun restoreStock(): JobResult = submit { restoreStock() }

    public suspend fun returnToPrevious(): JobResult = submit { returnToPrevious() }

    /** Captures `stock.json`; only with the owner's consent and the screen on (SPEC 5.2). */
    public suspend fun captureStock(): JobResult = submit { captureStock() }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> submit(block: suspend EngineCore.() -> T): T {
        val done = CompletableDeferred<T>()
        inbox.send {
            try {
                done.complete(core.block())
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    done.cancel(e)
                    throw e
                }
                // Not the actor's own cancellation (an adapter's timeout): only this command fails.
                done.completeExceptionally(IllegalStateException("a call was cancelled", e))
            } catch (e: Exception) {
                // One failing command must not stop the actor; the caller gets the exception.
                done.completeExceptionally(e)
            }
            core.persist()
            publishStatus()
        }
        return done.await()
    }

    /** A status that cannot be computed keeps the last one; the actor must outlive a bug here too. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun publishStatus() {
        try {
            mutableStatus.value = core.status()
        } catch (_: RuntimeException) {
            // Pure in-memory work: only a bug lands here, and the tests read the status.
        }
    }

    public companion object {
        /** SPEC 12.4: every write has a 5 s timeout. */
        public const val CALL_TIMEOUT_MILLIS: Long = 5_000

        /** SPEC 6.5: about 2 s for the system to reload the string. */
        public const val SETTLE_MILLIS: Long = 2_000
        public const val SETTLE_POLL_MILLIS: Long = 250
    }
}
