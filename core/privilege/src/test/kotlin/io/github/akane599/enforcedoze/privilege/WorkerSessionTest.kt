// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerSessionTest {
    private class FakeBinder(
        override val source: WorkerSource = WorkerSource.SHIZUKU,
        var connects: Boolean = true,
        var bindError: Exception? = null,
    ) : WorkerBinder {
        var binds = 0
        var unbinds = 0
        var settleMillis = 0L
        var settledBeforeBind = 0
        var onLost: (() -> Unit)? = null
        val worker = FakeDozeWorker()

        override suspend fun bind(onLost: () -> Unit): DozeWorkerApi {
            binds++
            bindError?.let { throw it }
            if (!connects) awaitCancellation()
            this.onLost = onLost
            return worker
        }

        override fun unbind() {
            unbinds++
        }

        override suspend fun awaitUnbound() {
            delay(settleMillis)
            settledBeforeBind = binds + 1
        }
    }

    private fun TestScope.session() = WorkerSession(backgroundScope, bindTimeoutMillis = 5_000, idleUnbindMillis = 60_000)

    @Test
    fun `binds once for calls inside the idle window and unbinds 60 s after the last`() = runTest {
        val session = session()
        val binder = FakeBinder()
        assertThat(session.use(binder) { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
        assertThat(session.status.value).isEqualTo(WorkerStatus.Bound(WorkerSource.SHIZUKU, 2000))
        advanceTimeBy(30_000)
        session.use(binder) { it.version() }
        assertThat(binder.binds).isEqualTo(1)
        advanceTimeBy(59_000)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(0)
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(1)
        assertThat(session.status.value).isEqualTo(WorkerStatus.Idle)
    }

    @Test
    fun `waits for the previous worker to go before binding, outside the bind timeout`() = runTest {
        val session = session()
        val binder = FakeBinder()
        session.use(binder) { it.uid() }
        session.close()
        binder.settleMillis = 6_000 // longer than the 5 s bind timeout
        binder.connects = true
        val started = currentTime
        assertThat(session.use(binder) { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
        assertThat(binder.settledBeforeBind).isEqualTo(2)
        assertThat(currentTime - started).isEqualTo(6_000)
    }

    @Test
    fun `stays bound while a call is running`() = runTest {
        val session = session()
        val binder = FakeBinder()
        val gate = CompletableDeferred<Unit>()
        val long = launch { session.use(binder) { gate.await() } }
        runCurrent()
        session.use(binder) { it.uid() }
        advanceTimeBy(120_000)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(0)
        gate.complete(Unit)
        long.join()
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(1)
    }

    @Test
    fun `no connection in 5 s marks the source unavailable until failures are forgotten`() = runTest {
        val session = session()
        val binder = FakeBinder(connects = false)
        val first = session.use(binder) { it.uid() }
        assertThat(first).isInstanceOf(WorkerResult.Unavailable::class.java)
        assertThat(session.status.value).isEqualTo(WorkerStatus.Unavailable(WorkerSource.SHIZUKU, "no connection within 5 s"))
        assertThat(binder.unbinds).isEqualTo(1)

        assertThat(session.use(binder) { it.uid() }).isInstanceOf(WorkerResult.Unavailable::class.java)
        assertThat(binder.binds).isEqualTo(1)

        // Another source is not affected.
        val root = FakeBinder(source = WorkerSource.ROOT)
        assertThat(session.use(root) { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
        session.close()

        session.forgetFailures()
        assertThat(session.status.value).isEqualTo(WorkerStatus.Idle)
        binder.connects = true
        assertThat(session.use(binder) { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
        assertThat(binder.binds).isEqualTo(2)
    }

    @Test
    fun `a bind error fails the call without marking the source unavailable`() = runTest {
        val session = session()
        val binder = FakeBinder(bindError = IllegalStateException("binder died"))
        val result = session.use(binder) { it.uid() }
        assertThat(result).isInstanceOf(WorkerResult.Failed::class.java)
        assertThat(binder.unbinds).isEqualTo(1)
        binder.bindError = null
        assertThat(session.use(binder) { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
    }

    @Test
    fun `a failing call is reported and still releases the connection`() = runTest {
        val session = session()
        val binder = FakeBinder()
        val result = session.use(binder) { error("boom") }
        assertThat((result as WorkerResult.Failed).error).hasMessageThat().isEqualTo("boom")
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(1)
    }

    @Test
    fun `a lost connection is rebound on the next call`() = runTest {
        val session = session()
        val binder = FakeBinder()
        session.use(binder) { it.uid() }
        binder.onLost!!.invoke()
        runCurrent()
        assertThat(session.status.value).isEqualTo(WorkerStatus.Idle)
        assertThat(binder.unbinds).isEqualTo(1)
        session.use(binder) { it.uid() }
        assertThat(binder.binds).isEqualTo(2)
    }

    @Test
    fun `a late loss report from an old connection leaves the new one alone`() = runTest {
        val session = session()
        val binder = FakeBinder()
        session.use(binder) { it.uid() }
        val oldLost = binder.onLost!!
        session.close()
        session.use(binder) { it.uid() }
        oldLost()
        runCurrent()
        assertThat(session.status.value).isEqualTo(WorkerStatus.Bound(WorkerSource.SHIZUKU, 2000))
        assertThat(binder.unbinds).isEqualTo(1)
    }

    @Test
    fun `a call that outlives its connection does not release the new one`() = runTest {
        val session = session()
        val binder = FakeBinder()
        val gate = CompletableDeferred<Unit>()
        val stale = launch { session.use(binder) { gate.await() } }
        runCurrent()
        binder.onLost!!.invoke()
        runCurrent()
        val fresh = CompletableDeferred<Unit>()
        val current = launch { session.use(binder) { fresh.await() } }
        runCurrent()
        gate.complete(Unit)
        stale.join()
        advanceTimeBy(120_000)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(1) // only the lost connection
        fresh.complete(Unit)
        current.join()
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(binder.unbinds).isEqualTo(2)
    }

    @Test
    fun `switching source unbinds the previous one`() = runTest {
        val session = session()
        val shizuku = FakeBinder()
        val root = FakeBinder(source = WorkerSource.ROOT)
        session.use(shizuku) { it.uid() }
        session.use(root) { it.uid() }
        assertThat(shizuku.unbinds).isEqualTo(1)
        assertThat(session.status.value).isEqualTo(WorkerStatus.Bound(WorkerSource.ROOT, 2000))
    }
}
