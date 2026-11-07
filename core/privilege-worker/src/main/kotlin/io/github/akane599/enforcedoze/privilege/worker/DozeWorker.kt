// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.IDozeWorker
import io.github.akane599.enforcedoze.privilege.api.ProcessCommandRunner
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * The privileged worker (SPEC 4.3). Shizuku and Sui construct it by name with the `(Context)`
 * constructor in their UserService process; [DozeRootService] hosts it for libsu. The context is this
 * app's package context, so [allowedUid] is the app's uid.
 */
public class DozeWorker internal constructor(
    private val core: WorkerCore,
    private val allowedUid: Int,
    private val callingUid: () -> Int,
) : IDozeWorker.Stub() {
    public constructor(context: Context) : this(
        core = WorkerCore(
            runner = ProcessCommandRunner(),
            packages = AndroidPackageChecker(context.packageManager),
            sensorPrivacy = HiddenSensorPrivacy,
            selfPackage = context.packageName,
        ),
        allowedUid = context.applicationInfo.uid,
        callingUid = Binder::getCallingUid,
    )

    private val runner = ProcessCommandRunner()

    override fun destroy() {
        // Shizuku's server (same uid as this process) or the app may stop the worker.
        val uid = callingUid()
        if (uid != allowedUid && uid != Process.myUid()) throw SecurityException("uid $uid may not stop the worker")
        exitProcess(0)
    }

    override fun getVersion(): Int = checked { WorkerContract.VERSION }

    override fun getUid(): Int = checked { Process.myUid() }

    override fun forceIdle(mode: String?): CommandOutcome = checked { core.forceIdle(mode) }

    override fun unforce(): CommandOutcome = checked { core.unforce() }

    override fun getIdleState(which: String?): CommandOutcome = checked { core.getIdleState(which) }

    override fun setSensorRestriction(allowPackage: String?): CommandOutcome = checked { core.setSensorRestriction(allowPackage) }

    override fun setAllSensorsOff(off: Boolean): CommandOutcome = checked { core.setAllSensorsOff(off) }

    override fun getAllSensorsOff(): CommandOutcome = checked { core.getAllSensorsOff() }

    override fun allowlist(packageName: String?, add: Boolean): CommandOutcome = checked { core.allowlist(packageName, add) }

    override fun grantSelf(permission: String?): CommandOutcome = checked { core.grantSelf(permission) }

    override fun setSelfAppOp(op: String?, mode: String?): CommandOutcome = checked { core.setSelfAppOp(op, mode) }

    override fun getDeviceIdleSettings(): CommandOutcome = checked { core.getDeviceIdleSettings() }

    override fun listDeviceIdleConfig(): CommandOutcome = checked { core.listDeviceIdleConfig() }

    override fun readDeviceIdle(pipe: ParcelFileDescriptor?): Unit = stream(pipe) { core.readDeviceIdleArgv() }

    override fun readSensorService(pipe: ParcelFileDescriptor?): Unit = stream(pipe) { core.readSensorServiceArgv() }

    override fun readEventLog(sinceMillis: Long, pipe: ParcelFileDescriptor?): Unit = stream(pipe) { core.readEventLogArgv(sinceMillis) }

    override fun readBatteryHistory(historyStart: Long, pipe: ParcelFileDescriptor?): Unit =
        stream(pipe) { core.readBatteryHistoryArgv(historyStart) }

    override fun readAllowlist(pipe: ParcelFileDescriptor?): Unit = stream(pipe) { core.readAllowlistArgv() }

    private inline fun <T> checked(block: () -> T): T {
        val uid = callingUid()
        if (uid != allowedUid) throw SecurityException("uid $uid may not call the worker")
        return block()
    }

    /** Copies the command's stdout into the caller's pipe on a worker thread and returns at once. */
    // Any failure while streaming is reported through the pipe, which must always be closed.
    @Suppress("TooGenericExceptionCaught")
    private inline fun stream(out: ParcelFileDescriptor?, argv: () -> List<String>) {
        val pipe = requireNotNull(out) { "no pipe" }
        val command = try {
            checked(argv)
        } catch (e: SecurityException) {
            pipe.closeWithError(e.message)
            throw e
        }
        STREAMS.execute {
            // The pipe must be closed on every path, or the reader waits for an end that never comes.
            val failure = try {
                val result = runner.stream(command, WorkerContract.READ_TIMEOUT_MILLIS, FileOutputStream(pipe.fileDescriptor))
                if (result.isSuccess) null else result.describe(command)
            } catch (e: Exception) {
                "${command.joinToString(" ")}: ${e.javaClass.simpleName}: ${e.message}"
            }
            try {
                if (failure == null) pipe.close() else pipe.closeWithError(failure)
            } catch (_: IOException) {
                // The reader went away; nothing left to tell it.
            }
        }
    }

    private class AndroidPackageChecker(private val pm: PackageManager) : PackageChecker {
        override fun isInstalled(packageName: String): Boolean = try {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        val STREAMS: ExecutorService = Executors.newCachedThreadPool { r ->
            Thread(r, "worker-stream").apply { isDaemon = true }
        }
    }
}
