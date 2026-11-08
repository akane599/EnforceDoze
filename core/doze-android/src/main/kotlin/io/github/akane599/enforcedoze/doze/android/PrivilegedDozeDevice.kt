// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import io.github.akane599.enforcedoze.doze.ConstantsReading
import io.github.akane599.enforcedoze.doze.DeviceResult
import io.github.akane599.enforcedoze.doze.DeviceWrite
import io.github.akane599.enforcedoze.doze.DozeDevice
import io.github.akane599.enforcedoze.doze.SensorReading
import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.privilege.ConstantsWriter
import io.github.akane599.enforcedoze.privilege.DozeWorkerApi
import io.github.akane599.enforcedoze.privilege.Grant
import io.github.akane599.enforcedoze.privilege.PrivilegeController
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The worker of the current tier, or why there is none; [PrivilegeController.withWorker] in the app. */
internal interface WorkerPort {
    suspend fun <T> run(block: suspend (DozeWorkerApi) -> T): WorkerResult<T>
}

internal class ControllerWorkerPort(private val controller: PrivilegeController) : WorkerPort {
    override suspend fun <T> run(block: suspend (DozeWorkerApi) -> T): WorkerResult<T> = controller.withWorker(block)
}

/** Fixed argument lists the app runs in its own process with durable grants (tier T1): no shell. */
internal interface LocalShell {
    /** `dumpsys` with [args]; [large] for `dumpsys settings`, which runs to hundreds of KB on a phone. */
    suspend fun dumpsys(args: List<String>, large: Boolean = false): CommandOutcome
}

internal class ProcessLocalShell(
    private val small: CommandRunner = ProcessCommandRunner(SMALL_CAP),
    private val large: CommandRunner = ProcessCommandRunner(LARGE_CAP),
) : LocalShell {
    override suspend fun dumpsys(args: List<String>, large: Boolean): CommandOutcome = withContext(Dispatchers.IO) {
        (if (large) this@ProcessLocalShell.large else small).run(listOf("dumpsys") + args, TIMEOUT_MILLIS)
    }

    private companion object {
        /** The phone's `dumpsys sensorservice` is 235 KB with the mode line past the middle; 64 KB would cut it. */
        const val SMALL_CAP = 1024 * 1024
        const val LARGE_CAP = 8 * 1024 * 1024

        /** Longer than the engine's 5 s wait, so a slow dump still ends and its process is reaped. */
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * The engine's device port (ADR 0015) over the privilege layer. Lever A is written by the app itself
 * (ADR 0012) and read from `dumpsys settings` with `DUMP` (ADR 0014), else through the worker. Reads and
 * sensor restriction use the app's own `DUMP` when it holds it, so a T1 phone needs no worker for them;
 * forced idle, sensor privacy and the allowlist always go through the worker.
 */
internal class PrivilegedDozeDevice(
    private val worker: WorkerPort,
    private val writer: ConstantsWriter,
    private val shell: LocalShell,
    private val grants: () -> Set<Grant>,
) : DozeDevice {
    private val dump: Boolean get() = Grant.DUMP in grants()

    override suspend fun write(write: DeviceWrite): DeviceResult<Unit> = when (write) {
        is DeviceWrite.SetConstants -> withContext(Dispatchers.IO) {
            if (writer.write(write.text)) DeviceResult.Ok(Unit) else DeviceResult.Failed("Settings.Global refused the write")
        }

        DeviceWrite.ForceIdle -> onWorker { forceIdle(DEEP) }

        DeviceWrite.Unforce -> onWorker { unforce() }

        is DeviceWrite.RestrictSensors -> sensors(write.allowPackage)

        DeviceWrite.EnableSensors -> sensors(null)

        is DeviceWrite.SensorPrivacy -> onWorker { setAllSensorsOff(write.on) }

        is DeviceWrite.Allowlist -> onWorker { allowlist(write.packageName, write.add) }

        // DEVICE_POWER, which only the worker holds.
        is DeviceWrite.Battery -> onWorker { setBatteryUnplugged(write.unplugged) }
    }

    override suspend fun readConstants(): DeviceResult<ConstantsReading> {
        if (dump) {
            DeviceMapping.constantsFromSettingsDump(shell.dumpsys(listOf("settings"), large = true))?.let { return DeviceResult.Ok(it) }
        }
        return DeviceMapping.worker(worker.run { it.deviceIdleSettings() }, DeviceMapping::constantsFromSettingsGet)
    }

    override suspend fun readDeviceIdle(): DeviceResult<DeviceIdleDump> = read(listOf("deviceidle"), DozeWorkerApi::readDeviceIdle) {
        DeviceResult.Ok(DeviceIdleDump.parse(it))
    }

    override suspend fun readSensors(): DeviceResult<SensorReading> = read(listOf("sensorservice"), DozeWorkerApi::readSensorService) {
        DeviceResult.Ok(DeviceMapping.sensors(it))
    }

    /** Only through the worker: `cmd deviceidle whitelist` is a shell command, not a dump. */
    override suspend fun readAllowlist(): DeviceResult<Set<String>> =
        DeviceMapping.worker(worker.run { it.readAllowlist() }, DeviceMapping::allowlist)

    override suspend fun readDeepState(): DeviceResult<String> =
        DeviceMapping.worker(worker.run { it.idleState(DEEP) }, DeviceMapping::printed)

    /** `dumpsys sensorservice restrict <pkg>` or `enable`: with `DUMP` from the app (ADR 0004), else the worker. */
    private suspend fun sensors(allowPackage: String?): DeviceResult<Unit> {
        if (!dump) return onWorker { setSensorRestriction(allowPackage) }
        val args = if (allowPackage == null) listOf("sensorservice", "enable") else listOf("sensorservice", "restrict", allowPackage)
        return DeviceMapping.write(shell.dumpsys(args))
    }

    private suspend fun onWorker(call: suspend DozeWorkerApi.() -> CommandOutcome): DeviceResult<Unit> =
        DeviceMapping.worker(worker.run { it.call() }, DeviceMapping::write)

    private suspend fun <T> read(
        args: List<String>,
        viaWorker: suspend (DozeWorkerApi) -> String,
        parse: (String) -> DeviceResult<T>,
    ): DeviceResult<T> {
        if (dump) {
            val outcome = shell.dumpsys(args)
            if (DeviceMapping.answered(outcome)) return parse(outcome.stdout)
        }
        return DeviceMapping.worker(worker.run(viaWorker), parse)
    }

    private companion object {
        const val DEEP = "deep"
    }
}
