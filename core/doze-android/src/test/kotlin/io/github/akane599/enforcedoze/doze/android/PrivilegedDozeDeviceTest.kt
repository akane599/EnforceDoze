// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ConstantsValue
import io.github.akane599.enforcedoze.doze.DeviceResult
import io.github.akane599.enforcedoze.doze.DeviceWrite
import io.github.akane599.enforcedoze.privilege.ConstantsWriter
import io.github.akane599.enforcedoze.privilege.DozeWorkerApi
import io.github.akane599.enforcedoze.privilege.Grant
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrivilegedDozeDeviceTest {
    private val calls = mutableListOf<String>()
    private var grants = emptySet<Grant>()
    private var workerUp = true
    private val shellOutput = mutableMapOf<List<String>, String>()

    private fun ok(argv: List<String>, stdout: String = "") = CommandOutcome(argv, 0, stdout, "", false, 1, null)

    /** A worker that records each call and answers with exit 0. */
    private val api = object : DozeWorkerApi {
        private fun record(call: String, stdout: String = ""): CommandOutcome {
            calls += "worker:$call"
            return ok(listOf(call), stdout)
        }

        private fun read(call: String): String {
            calls += "worker:$call()"
            return "Mode : NORMAL\n"
        }

        override suspend fun uid(): Int = 2000

        override suspend fun version(): Int = 2

        override suspend fun forceIdle(mode: String) = record("forceIdle($mode)")

        override suspend fun unforce() = record("unforce()")

        override suspend fun idleState(which: String) = record("idleState($which)", "IDLE\n")

        override suspend fun setBatteryUnplugged(unplugged: Boolean) = record("setBatteryUnplugged($unplugged)")

        override suspend fun setSensorRestriction(allowPackage: String?) = record("setSensorRestriction($allowPackage)")

        override suspend fun setAllSensorsOff(off: Boolean) = record("setAllSensorsOff($off)")

        override suspend fun allSensorsOff() = record("allSensorsOff()")

        override suspend fun allowlist(packageName: String, add: Boolean) = record("allowlist($packageName,$add)")

        override suspend fun grantSelf(permission: String) = record("grantSelf($permission)")

        override suspend fun setSelfAppOp(op: String, mode: String) = record("setSelfAppOp($op,$mode)")

        override suspend fun deviceIdleSettings() = record("deviceIdleSettings()", "null\n")

        override suspend fun deviceIdleConfig() = record("deviceIdleConfig()")

        override suspend fun readDeviceIdle() = read("readDeviceIdle")

        override suspend fun readSensorService() = read("readSensorService")

        override suspend fun readEventLog(sinceMillis: Long) = read("readEventLog")

        override suspend fun readBatteryHistory(historyStart: Long) = read("readBatteryHistory")

        override suspend fun readAllowlist() = read("readAllowlist")

        override suspend fun readAlarms() = read("readAlarms")
    }

    private val worker = object : WorkerPort {
        override suspend fun <T> run(block: suspend (DozeWorkerApi) -> T): WorkerResult<T> =
            if (workerUp) WorkerResult.Ok(block(api)) else WorkerResult.Unavailable("tier T1: no worker")
    }

    private val writer = object : ConstantsWriter {
        override fun canWrite(): Boolean = true

        override fun write(value: String?): Boolean {
            calls += "settings:$value"
            return true
        }
    }

    private val shell = object : LocalShell {
        override suspend fun dumpsys(args: List<String>, large: Boolean): CommandOutcome {
            calls += "dumpsys:${args.joinToString(" ")}"
            return ok(listOf("dumpsys") + args, shellOutput[args].orEmpty())
        }
    }

    private val device = PrivilegedDozeDevice(worker, writer, shell) { grants }

    @Test
    fun `lever A is written by the app itself`() = runTest {
        device.write(DeviceWrite.SetConstants("inactive_to=1"))
        device.write(DeviceWrite.SetConstants(null))
        assertThat(calls).containsExactly("settings:inactive_to=1", "settings:null").inOrder()
    }

    @Test
    fun `with DUMP the app reads and restricts sensors itself`() = runTest {
        grants = setOf(Grant.DUMP)
        shellOutput[listOf("sensorservice")] = "Mode : RESTRICTED : io.github.akane599.enforcedoze\nSensor Privacy: disabled\n"
        device.write(DeviceWrite.RestrictSensors("io.github.akane599.enforcedoze"))
        device.write(DeviceWrite.EnableSensors)
        val reading = device.readSensors()
        assertThat(calls).containsExactly(
            "dumpsys:sensorservice restrict io.github.akane599.enforcedoze",
            "dumpsys:sensorservice enable",
            "dumpsys:sensorservice",
        ).inOrder()
        assertThat((reading as DeviceResult.Ok).value.privacy).isFalse()
    }

    @Test
    fun `without DUMP everything goes through the worker`() = runTest {
        device.write(DeviceWrite.RestrictSensors("p"))
        device.write(DeviceWrite.EnableSensors)
        device.write(DeviceWrite.ForceIdle)
        device.write(DeviceWrite.Allowlist("a", add = false))
        val constants = device.readConstants()
        assertThat(calls).containsExactly(
            "worker:setSensorRestriction(p)",
            "worker:setSensorRestriction(null)",
            "worker:forceIdle(deep)",
            "worker:allowlist(a,false)",
            "worker:deviceIdleSettings()",
        ).inOrder()
        assertThat((constants as DeviceResult.Ok).value.value).isEqualTo(ConstantsValue.Absent)
    }

    @Test
    fun `the quick proof's battery and deep state go through the worker, even with DUMP`() = runTest {
        grants = setOf(Grant.DUMP)
        device.write(DeviceWrite.Battery(unplugged = true))
        device.write(DeviceWrite.Battery(unplugged = false))
        assertThat(device.readDeepState()).isEqualTo(DeviceResult.Ok("IDLE", "IDLE"))
        assertThat(calls).containsExactly(
            "worker:setBatteryUnplugged(true)",
            "worker:setBatteryUnplugged(false)",
            "worker:idleState(deep)",
        ).inOrder()
    }

    @Test
    fun `a denied dump falls back to the worker`() = runTest {
        grants = setOf(Grant.DUMP)
        shellOutput[listOf("settings")] = "Permission Denial: can't dump SettingsProvider"
        shellOutput[listOf("deviceidle")] = "Permission Denial: can't dump DeviceIdleController"
        device.readConstants()
        device.readDeviceIdle()
        assertThat(
            calls,
        ).containsExactly("dumpsys:settings", "worker:deviceIdleSettings()", "dumpsys:deviceidle", "worker:readDeviceIdle()")
            .inOrder()
    }

    @Test
    fun `no worker is a failed write, never an exception`() = runTest {
        workerUp = false
        assertThat(device.write(DeviceWrite.Unforce)).isEqualTo(DeviceResult.Failed("no worker: tier T1: no worker"))
    }
}
