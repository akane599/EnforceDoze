// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.StreamResult
import io.github.akane599.enforcedoze.privilege.probe.CapabilityProber
import io.github.akane599.enforcedoze.privilege.probe.DeviceInfo
import io.github.akane599.enforcedoze.privilege.probe.LocalReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PrivilegeControllerTest {
    private class FakeShizuku(var status: ShizukuStatus = ShizukuStatus.NotRunning) : ShizukuGateway {
        var listener: (() -> Unit)? = null
        var requests = 0

        override fun status(): ShizukuStatus = status

        override fun requestPermission() {
            requests++
        }

        override fun observe(onChange: () -> Unit): AutoCloseable {
            listener = onChange
            return AutoCloseable { listener = null }
        }
    }

    private class FakeRoot(var granted: Boolean = false, val grantOnRequest: Boolean = false) : RootGateway {
        override fun isGranted(): Boolean = granted

        override fun request() {
            if (grantOnRequest) granted = true
        }
    }

    private class Binder(override val source: WorkerSource, val uid: Int, var connects: Boolean = true) : WorkerBinder {
        var binds = 0

        override suspend fun bind(onLost: () -> Unit): DozeWorkerApi {
            binds++
            if (!connects) awaitCancellation()
            return FakeDozeWorker(uid)
        }

        override fun unbind() = Unit
    }

    private class Env(scope: TestScope, shizuku: ShizukuStatus = ShizukuStatus.NotRunning, root: Boolean = false) {
        val shizuku = FakeShizuku(shizuku)
        val root = FakeRoot(root, grantOnRequest = true)
        var grants: Set<Grant> = emptySet()
        val binders = mapOf(
            WorkerSource.SHIZUKU to Binder(WorkerSource.SHIZUKU, 2000),
            WorkerSource.SUI to Binder(WorkerSource.SUI, 0),
            WorkerSource.ROOT to Binder(WorkerSource.ROOT, 0),
        )
        val controller = PrivilegeController(
            scope = scope.backgroundScope,
            shizuku = this.shizuku,
            grants = { grants },
            root = this.root,
            binders = binders,
            session = WorkerSession(scope.backgroundScope),
            prober = CapabilityProber(LocalReader(DeniedRunner), "io.github.akane599.enforcedoze", NoConstants),
        )
    }

    /** The app's own process without any grant. */
    private object DeniedRunner : CommandRunner {
        override fun run(argv: List<String>, timeoutMillis: Long): CommandOutcome =
            CommandOutcome(argv, 0, "Permission Denial: can't dump", "", false, 1, null)

        override fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream): StreamResult = error("not used")
    }

    private val device = DeviceInfo("Google", "sdk_gphone64_x86_64", "17", 37, "CE2A.260420.051.A3", "google/sdk/emu:17")

    @Test
    fun `no privilege has no worker`() = runTest {
        val env = Env(this)
        env.controller.start()
        assertThat(env.controller.state.value.tier).isEqualTo(Tier.T0)
        val result = env.controller.withWorker { it.uid() }
        assertThat(result).isEqualTo(WorkerResult.Unavailable("tier T0: no worker"))
    }

    @Test
    fun `refresh picks up a new grant`() = runTest {
        val env = Env(this)
        env.controller.start()
        env.grants = setOf(Grant.DUMP, Grant.READ_LOGS)
        assertThat(env.controller.refresh().tier).isEqualTo(Tier.T1)
    }

    @Test
    fun `Shizuku as shell runs the worker and the state shows it bound`() = runTest {
        val env = Env(this, shizuku = ShizukuStatus.Ready(2000, 13, sui = false))
        env.controller.start()
        assertThat(env.controller.withWorker { it.uid() }).isEqualTo(WorkerResult.Ok(2000))
        runCurrent()
        val state = env.controller.state.value
        assertThat(state.tier).isEqualTo(Tier.T2)
        assertThat(state.worker).isEqualTo(WorkerStatus.Bound(WorkerSource.SHIZUKU, 2000))
    }

    @Test
    fun `a binder that arrives later is seen through the listener`() = runTest {
        val env = Env(this)
        env.controller.start()
        env.shizuku.status = ShizukuStatus.Ready(0, 13, sui = true)
        env.shizuku.listener!!.invoke()
        runCurrent()
        assertThat(env.controller.state.value.tier).isEqualTo(Tier.T3)
        assertThat(env.controller.state.value.workerSource).isEqualTo(WorkerSource.SUI)
    }

    @Test
    fun `a Shizuku worker that never connects drops to root, and a new binder retries it`() = runTest {
        val env = Env(this, shizuku = ShizukuStatus.Ready(0, 13, sui = false), root = true)
        env.binders.getValue(WorkerSource.SHIZUKU).connects = false
        env.controller.start()
        assertThat(env.controller.withWorker { it.uid() }).isInstanceOf(WorkerResult.Unavailable::class.java)
        runCurrent()
        assertThat(env.controller.state.value.workerSource).isEqualTo(WorkerSource.ROOT)
        assertThat(env.controller.withWorker { it.uid() }).isEqualTo(WorkerResult.Ok(0))

        env.binders.getValue(WorkerSource.SHIZUKU).connects = true
        env.shizuku.listener!!.invoke()
        runCurrent()
        assertThat(env.controller.state.value.workerSource).isEqualTo(WorkerSource.SHIZUKU)
    }

    @Test
    fun `root is only granted when asked for`() = runTest {
        val env = Env(this)
        env.controller.start()
        assertThat(env.controller.state.value.rootGranted).isFalse()
        assertThat(env.controller.requestRoot().tier).isEqualTo(Tier.T3)
    }

    @Test
    fun `the report uses the worker when there is one and names it`() = runTest {
        val env = Env(this, shizuku = ShizukuStatus.Ready(2000, 13, sui = false))
        env.controller.start()
        val report = env.controller.probe(includeChanges = false, device = device)
        assertThat(report.tier).isEqualTo(Tier.T2)
        assertThat(report.workerSource).isEqualTo(WorkerSource.SHIZUKU)
        assertThat(report.workerUid).isEqualTo(2000)
        assertThat(env.binders.getValue(WorkerSource.SHIZUKU).binds).isEqualTo(1)
    }

    @Test
    fun `the report runs without a worker below T2`() = runTest {
        val env = Env(this)
        env.controller.start()
        val report = env.controller.probe(includeChanges = true, device = device)
        assertThat(report.tier).isEqualTo(Tier.T0)
        assertThat(report.workerSource).isNull()
        assertThat(report.result(CapabilityProber.READ)!!.detail).contains("Permission Denial")
    }

    @Test
    fun `stop removes the listener and start again collects once`() = runTest {
        val env = Env(this, shizuku = ShizukuStatus.Ready(2000, 13, sui = false))
        env.controller.start()
        env.controller.stop()
        assertThat(env.shizuku.listener).isNull()
        env.controller.start()
        assertThat(env.shizuku.listener).isNotNull()
        env.controller.withWorker { it.uid() }
        runCurrent()
        assertThat(env.controller.state.value.worker).isEqualTo(WorkerStatus.Bound(WorkerSource.SHIZUKU, 2000))
    }
}

private object NoConstants : ConstantsWriter {
    override fun canWrite(): Boolean = false

    override fun write(value: String?): Boolean = false
}
