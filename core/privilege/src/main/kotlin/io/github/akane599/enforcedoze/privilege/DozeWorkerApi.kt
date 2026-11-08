// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.os.ParcelFileDescriptor
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.IDozeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException

/**
 * The worker as the engine sees it (SPEC 4.3). The engine's tests use a fake of this interface, not
 * the AIDL stub (SPEC 13.1). Reads return the command's whole output and throw [IOException] when
 * the worker reports a failure.
 */
public interface DozeWorkerApi {
    public suspend fun uid(): Int

    public suspend fun version(): Int

    public suspend fun forceIdle(mode: String): CommandOutcome

    public suspend fun unforce(): CommandOutcome

    public suspend fun idleState(which: String): CommandOutcome

    public suspend fun setSensorRestriction(allowPackage: String?): CommandOutcome

    public suspend fun setAllSensorsOff(off: Boolean): CommandOutcome

    public suspend fun allSensorsOff(): CommandOutcome

    public suspend fun allowlist(packageName: String, add: Boolean): CommandOutcome

    public suspend fun grantSelf(permission: String): CommandOutcome

    public suspend fun setSelfAppOp(op: String, mode: String): CommandOutcome

    /** `settings get global device_idle_constants`; the app writes the string itself (ADR 0012). */
    public suspend fun deviceIdleSettings(): CommandOutcome

    /** `device_config list device_idle`, to tell server values from the global string. */
    public suspend fun deviceIdleConfig(): CommandOutcome

    public suspend fun readDeviceIdle(): String

    public suspend fun readSensorService(): String

    public suspend fun readEventLog(sinceMillis: Long): String

    public suspend fun readBatteryHistory(historyStart: Long): String

    public suspend fun readAllowlist(): String

    /** `dumpsys alarm`, for the alarm wakeups of a session (ADR 0019). */
    public suspend fun readAlarms(): String
}

/** [DozeWorkerApi] over the binder. Calls run on the IO dispatcher. */
internal class BinderDozeWorkerApi(private val worker: IDozeWorker) : DozeWorkerApi {
    override suspend fun uid(): Int = io { worker.uid }

    override suspend fun version(): Int = io { worker.version }

    override suspend fun forceIdle(mode: String): CommandOutcome = io { worker.forceIdle(mode) }

    override suspend fun unforce(): CommandOutcome = io { worker.unforce() }

    override suspend fun idleState(which: String): CommandOutcome = io { worker.getIdleState(which) }

    override suspend fun setSensorRestriction(allowPackage: String?): CommandOutcome = io { worker.setSensorRestriction(allowPackage) }

    override suspend fun setAllSensorsOff(off: Boolean): CommandOutcome = io { worker.setAllSensorsOff(off) }

    override suspend fun allSensorsOff(): CommandOutcome = io { worker.allSensorsOff }

    override suspend fun allowlist(packageName: String, add: Boolean): CommandOutcome = io { worker.allowlist(packageName, add) }

    override suspend fun grantSelf(permission: String): CommandOutcome = io { worker.grantSelf(permission) }

    override suspend fun setSelfAppOp(op: String, mode: String): CommandOutcome = io { worker.setSelfAppOp(op, mode) }

    override suspend fun deviceIdleSettings(): CommandOutcome = io { worker.deviceIdleSettings }

    override suspend fun deviceIdleConfig(): CommandOutcome = io { worker.listDeviceIdleConfig() }

    override suspend fun readDeviceIdle(): String = read { worker.readDeviceIdle(it) }

    override suspend fun readSensorService(): String = read { worker.readSensorService(it) }

    override suspend fun readEventLog(sinceMillis: Long): String = read { worker.readEventLog(sinceMillis, it) }

    override suspend fun readBatteryHistory(historyStart: Long): String = read { worker.readBatteryHistory(historyStart, it) }

    override suspend fun readAllowlist(): String = read { worker.readAllowlist(it) }

    override suspend fun readAlarms(): String = read { worker.readAlarms(it) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /**
     * Hands the write end of a reliable pipe to the worker and reads to the end. The local copy of the
     * write end is closed right after the call, or the read would never see the end of the stream;
     * `checkError` then reports a `closeWithError` from the worker.
     */
    private suspend fun read(call: (ParcelFileDescriptor) -> Unit): String = withContext(Dispatchers.IO) {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createReliablePipe()
        try {
            try {
                call(writeEnd)
            } finally {
                writeEnd.close()
            }
            val bytes = FileInputStream(readEnd.fileDescriptor).readBytes()
            readEnd.checkError()
            bytes.toString(Charsets.UTF_8)
        } finally {
            readEnd.close()
        }
    }
}
