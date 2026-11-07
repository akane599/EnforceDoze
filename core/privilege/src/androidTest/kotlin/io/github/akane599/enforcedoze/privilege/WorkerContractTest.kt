// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.parse.Allowlist
import io.github.akane599.enforcedoze.parse.AospDeviceIdle
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.SensorMode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1 gate: every `IDozeWorker` method does what it says on a real system server (SPEC 13.1 "Worker
 * contract"). Runs in the SHELL and ROOT scenarios; each test puts back what it changed.
 */
@RunWith(AndroidJUnit4::class)
class WorkerContractTest {
    private lateinit var fixture: ControllerFixture
    private val self get() = fixture.context.packageName

    @Before
    fun setUp() {
        OnDeviceScenario.requireWorker()
        fixture = ControllerFixture()
        val state = fixture.awaitScenarioState()
        assertWithMessage("Shizuku state: ${state.shizuku}").that(state.tier).isEqualTo(OnDeviceScenario.current.expectedTier)
    }

    @After
    fun tearDown() {
        if (::fixture.isInitialized) fixture.close()
    }

    /** Runs [block] against the worker; JUnit test methods must return void, so this returns Unit. */
    private fun worker(block: suspend (DozeWorkerApi) -> Unit): Unit = runBlocking {
        when (val result = fixture.controller.withWorker(block)) {
            is WorkerResult.Ok -> Unit
            is WorkerResult.Unavailable -> throw AssertionError("worker unavailable: ${result.reason}")
            is WorkerResult.Failed -> throw AssertionError("worker call failed", result.error)
        }
    }

    private fun assertOk(outcome: CommandOutcome) = assertWithMessage(
        "%s -> exit %s, stdout %s, stderr %s, error %s",
        outcome.argv,
        outcome.exitCode,
        outcome.stdout,
        outcome.stderr,
        outcome.error,
    )
        .that(outcome.isSuccess).isTrue()

    @Test
    fun identity() = worker { w ->
        assertThat(w.uid()).isEqualTo(OnDeviceScenario.current.serverUid)
        assertThat(w.version()).isEqualTo(WorkerContract.VERSION)
    }

    @Test
    fun readDeviceIdleHasEveryAospKey() = worker { w ->
        val dump = DeviceIdleDump.parse(w.readDeviceIdle())
        assertThat(dump.isRecognised).isTrue()
        assertThat(dump.missingAospKeys).isEmpty()
        assertThat(dump.settings.keys).containsAtLeastElementsIn(AospDeviceIdle.KEYS)
    }

    @Test
    fun constantsAreWrittenReadBackAndReset() = worker { w ->
        try {
            w.setDeviceIdleConstants(mapOf("inactive_to" to "123000", "light_idle_factor" to "2.5")).forEach(::assertOk)
            val written = DeviceIdleDump.parse(w.readDeviceIdle())
            assertThat(written.settings.getValue("inactive_to").durationMillis).isEqualTo(123_000L)
            assertThat(written.settings.getValue("light_idle_factor").number).isEqualTo(2.5)
            w.deleteDeviceIdleConstants(listOf("light_idle_factor")).forEach(::assertOk)
        } finally {
            assertOk(w.resetDeviceIdleConstants())
        }
        val reset = DeviceIdleDump.parse(w.readDeviceIdle())
        assertThat(reset.settings.getValue("inactive_to").durationMillis).isNotEqualTo(123_000L)
    }

    @Test
    fun invalidInputIsRejectedWithoutWriting() = worker { w ->
        val before = DeviceIdleDump.parse(w.readDeviceIdle()).settings.getValue("inactive_to").raw
        val outcomes = w.setDeviceIdleConstants(mapOf("inactive_to" to "60000", "bad key" to "1"))
        assertThat(outcomes.none { it.isSuccess }).isTrue()
        assertThat(DeviceIdleDump.parse(w.readDeviceIdle()).settings.getValue("inactive_to").raw).isEqualTo(before)
        assertThat(w.forceIdle("everything").isSuccess).isFalse()
        assertThat(w.setSensorRestriction("no.such.package.installed").isSuccess).isFalse()
        assertThat(w.allowlist("no.such.package.installed", true).isSuccess).isFalse()
        assertThat(w.grantSelf("android.permission.INSTALL_PACKAGES").isSuccess).isFalse()
    }

    @Test
    fun forceIdleDeepAndUnforce() = worker { w ->
        try {
            assertOk(w.forceIdle("deep"))
            assertThat(w.idleState("force").stdout.trim()).isEqualTo("true")
            assertThat(w.idleState("deep").stdout.trim()).isEqualTo("IDLE")
        } finally {
            assertOk(w.unforce())
        }
        assertThat(w.idleState("force").stdout.trim()).isEqualTo("false")
        // The transitions are in the event log, which the worker reads as well.
        val log = DozeEventLog.parse(w.readEventLog(0))
        assertThat(log.events).isNotEmpty()
    }

    @Test
    fun sensorRestrictionAllowsOnePackageAndIsUndone() = worker { w ->
        try {
            assertOk(w.setSensorRestriction(self))
            val restricted = SensorServiceDump.parse(w.readSensorService())
            assertThat(restricted.mode).isEqualTo(SensorMode.RESTRICTED)
            assertThat(restricted.modePackage).isEqualTo(self)
        } finally {
            assertOk(w.setSensorRestriction(null))
        }
        assertThat(SensorServiceDump.parse(w.readSensorService()).mode).isEqualTo(SensorMode.NORMAL)
    }

    @Test
    fun allSensorsOffThroughTheHiddenApi() = worker { w ->
        val read = w.allSensorsOff()
        assertOk(read)
        val before = read.stdout.trim().toBooleanStrict()
        try {
            assertOk(w.setAllSensorsOff(!before))
            assertThat(w.allSensorsOff().stdout.trim()).isEqualTo((!before).toString())
            assertThat(SensorServiceDump.parse(w.readSensorService()).sensorPrivacy).isEqualTo(!before)
        } finally {
            assertOk(w.setAllSensorsOff(before))
        }
        assertThat(w.allSensorsOff().stdout.trim()).isEqualTo(before.toString())
    }

    @Test
    fun allowlistAddAndRemove() = worker { w ->
        try {
            assertOk(w.allowlist(self, add = true))
            assertThat(Allowlist.parse(w.readAllowlist()).contains(self)).isTrue()
        } finally {
            assertOk(w.allowlist(self, add = false))
        }
        assertThat(Allowlist.parse(w.readAllowlist()).contains(self)).isFalse()
    }

    @Test
    fun grantsAndAppOpsForThisApp() = worker { w ->
        assertOk(w.grantSelf("android.permission.DUMP"))
        assertThat(fixture.context.checkSelfPermission("android.permission.DUMP")).isEqualTo(PackageManager.PERMISSION_GRANTED)
        assertOk(w.setSelfAppOp("GET_USAGE_STATS", "allow"))
        val appOps = fixture.context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), self)
        assertThat(mode).isEqualTo(AppOpsManager.MODE_ALLOWED)
    }

    @Test
    fun largeReadsStreamThroughThePipe() = worker { w ->
        val history = w.readBatteryHistory(0)
        assertThat(history).contains("Battery History")
        assertThat(w.readEventLog(System.currentTimeMillis() - 60_000)).isNotNull()
    }
}
