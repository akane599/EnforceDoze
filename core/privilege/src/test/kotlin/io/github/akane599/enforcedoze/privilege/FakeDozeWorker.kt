// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.WorkerContract

/** A scriptable worker for JVM tests: state lives in fields, every call is recorded in [calls]. */
open class FakeDozeWorker(private val workerUid: Int = 2000) : DozeWorkerApi {
    val calls = mutableListOf<String>()
    var deviceIdle: String = ""
    var forced = false
    var deep = "ACTIVE"
    var sensorService: (allow: String?) -> String = { "" }
    var restriction: String? = null
    var sensorsOff = false
    var eventLog: String = ""
    var batteryHistory: String = ""
    var alarms: String = ""
    var globalConstants: String? = null

    protected fun ok(stdout: String = "", vararg argv: String) = CommandOutcome(argv.toList(), 0, stdout, "", false, 0, null)

    override suspend fun uid(): Int = workerUid.also { calls += "uid" }

    override suspend fun version(): Int = WorkerContract.VERSION

    override suspend fun forceIdle(mode: String): CommandOutcome {
        calls += "forceIdle $mode"
        forced = true
        deep = "IDLE"
        return ok("Now forced in to deep idle mode")
    }

    override suspend fun unforce(): CommandOutcome {
        calls += "unforce"
        forced = false
        deep = "ACTIVE"
        return ok("Light state: ACTIVE, deep state: ACTIVE")
    }

    var unplugged = false

    override suspend fun setBatteryUnplugged(unplugged: Boolean): CommandOutcome {
        calls += if (unplugged) "battery unplug" else "battery reset"
        this.unplugged = unplugged
        return ok()
    }

    override suspend fun idleState(which: String): CommandOutcome = when (which) {
        "force" -> ok(forced.toString())
        "deep" -> ok(deep)
        else -> ok("")
    }

    override suspend fun setSensorRestriction(allowPackage: String?): CommandOutcome {
        calls += "restrict ${allowPackage ?: "<enable>"}"
        restriction = allowPackage
        return ok()
    }

    override suspend fun setAllSensorsOff(off: Boolean): CommandOutcome {
        calls += "sensorsOff $off"
        sensorsOff = off
        return ok()
    }

    override suspend fun allSensorsOff(): CommandOutcome = ok(sensorsOff.toString())

    override suspend fun allowlist(packageName: String, add: Boolean): CommandOutcome = ok()

    override suspend fun grantSelf(permission: String): CommandOutcome = ok()

    override suspend fun setSelfAppOp(op: String, mode: String): CommandOutcome = ok()

    override suspend fun deviceIdleSettings(): CommandOutcome = ok(globalConstants ?: "null")

    override suspend fun deviceIdleConfig(): CommandOutcome = ok()

    override suspend fun readDeviceIdle(): String = deviceIdle

    override suspend fun readSensorService(): String = sensorService(restriction)

    override suspend fun readEventLog(sinceMillis: Long): String = eventLog

    override suspend fun readBatteryHistory(historyStart: Long): String = batteryHistory

    override suspend fun readAllowlist(): String = ""

    override suspend fun readAlarms(): String = alarms
}
