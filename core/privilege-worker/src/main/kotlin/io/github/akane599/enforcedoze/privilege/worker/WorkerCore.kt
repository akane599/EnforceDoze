// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.api.WorkerContract

/** Whether a package is installed; package names are checked before any command uses them (SPEC 4.3). */
internal fun interface PackageChecker {
    fun isInstalled(packageName: String): Boolean
}

/** The all-sensors privacy toggle; only the worker process can reach it. */
internal interface SensorPrivacyAccess {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

/**
 * The worker's logic without binder: argument validation and the fixed argument lists. Commands and
 * flags are checked against each release's own help (the `help-` files of the API 37 emulator fixtures) and
 * SPEC 16.1.
 */
internal class WorkerCore(
    private val runner: CommandRunner,
    private val packages: PackageChecker,
    private val sensorPrivacy: SensorPrivacyAccess,
    private val selfPackage: String,
) {
    fun forceIdle(mode: String?): CommandOutcome {
        val argv = listOf("cmd", "deviceidle", "force-idle", mode.orEmpty())
        return if (mode in WorkerContract.FORCE_MODES) write(argv) else CommandOutcome.rejected(argv, "mode must be deep or light")
    }

    fun unforce(): CommandOutcome = write(listOf("cmd", "deviceidle", "unforce"))

    fun getIdleState(which: String?): CommandOutcome {
        val argv = listOf("cmd", "deviceidle", "get", which.orEmpty())
        return if (which in WorkerContract.IDLE_QUERIES) write(argv) else CommandOutcome.rejected(argv, "unknown state $which")
    }

    /** API 35–37 reject a bare `restrict`; the allow package is mandatory (ADR 0004). */
    fun setSensorRestriction(allowPackage: String?): CommandOutcome {
        if (allowPackage == null) return write(listOf("dumpsys", "sensorservice", "enable"))
        val argv = listOf("dumpsys", "sensorservice", "restrict", allowPackage)
        return packageProblem(allowPackage)?.let { CommandOutcome.rejected(argv, it) } ?: write(argv)
    }

    fun allowlist(packageName: String?, add: Boolean): CommandOutcome {
        val name = packageName.orEmpty()
        val argv = listOf("cmd", "deviceidle", "whitelist", (if (add) "+" else "-") + name)
        return packageProblem(name)?.let { CommandOutcome.rejected(argv, it) } ?: write(argv)
    }

    fun grantSelf(permission: String?): CommandOutcome {
        val argv = listOf("pm", "grant", selfPackage, permission.orEmpty())
        return if (permission in
            WorkerContract.SELF_PERMISSIONS
        ) {
            write(argv)
        } else {
            CommandOutcome.rejected(argv, "not an allowed permission")
        }
    }

    fun setSelfAppOp(op: String?, mode: String?): CommandOutcome {
        val argv = listOf("appops", "set", selfPackage, op.orEmpty(), mode.orEmpty())
        return if (op in WorkerContract.SELF_APP_OPS && mode in WorkerContract.APP_OP_MODES) {
            write(argv)
        } else {
            CommandOutcome.rejected(argv, "not an allowed app op or mode")
        }
    }

    fun setAllSensorsOff(off: Boolean): CommandOutcome = hidden(SENSOR_PRIVACY_SET + off.toString()) {
        sensorPrivacy.setEnabled(off)
        sensorPrivacy.isEnabled().toString()
    }

    fun getAllSensorsOff(): CommandOutcome = hidden(SENSOR_PRIVACY_GET) { sensorPrivacy.isEnabled().toString() }

    /** Small reads that return through binder. The app cannot read this hidden, non-@Readable setting itself (ADR 0012). */
    fun getDeviceIdleSettings(): CommandOutcome = query(listOf("settings", "get", "global", DeviceIdleSettings.SETTING))

    /** Needs READ_DEVICE_CONFIG, which shell holds and the app cannot get. */
    fun listDeviceIdleConfig(): CommandOutcome = query(listOf("device_config", "list", WorkerContract.DEVICE_IDLE_NAMESPACE))

    fun readDeviceIdleArgv(): List<String> = listOf("dumpsys", "deviceidle")

    fun readSensorServiceArgv(): List<String> = listOf("dumpsys", "sensorservice")

    fun readAllowlistArgv(): List<String> = listOf("cmd", "deviceidle", "whitelist")

    /** `logcat -T` accepts `sssss.mmm` (seconds since the epoch) and does not imply `-d` (logcat help). */
    fun readEventLogArgv(sinceMillis: Long): List<String> {
        val base = listOf("logcat", "-b", "events", "-d", "-v", "epoch")
        if (sinceMillis <= 0) return base
        return base + listOf("-T", "%d.%03d".format(sinceMillis / MILLIS_PER_SECOND, sinceMillis % MILLIS_PER_SECOND))
    }

    /** `--history-start <num>`: "show only history data starting at given time offset" (API 37 help). */
    fun readBatteryHistoryArgv(historyStart: Long): List<String> = if (historyStart <= 0) {
        listOf("dumpsys", "batterystats", "--history")
    } else {
        listOf("dumpsys", "batterystats", "--history-start", historyStart.toString())
    }

    private fun write(argv: List<String>): CommandOutcome = runner.run(argv, WorkerContract.WRITE_TIMEOUT_MILLIS)

    private fun query(argv: List<String>): CommandOutcome = runner.run(argv, WorkerContract.WRITE_TIMEOUT_MILLIS)

    // Any failure of a hidden call becomes a reported outcome; the worker must never die on one (SPEC 12.4).
    @Suppress("TooGenericExceptionCaught")
    private inline fun hidden(argv: List<String>, block: () -> String): CommandOutcome {
        val started = System.nanoTime()
        return try {
            val out = block()
            CommandOutcome(argv, 0, out, "", false, (System.nanoTime() - started) / NANOS_PER_MILLI, null)
        } catch (e: Exception) {
            hiddenFailure(argv, e)
        } catch (e: LinkageError) {
            // A renamed or removed hidden method or class (NoSuchMethodError, NoClassDefFoundError).
            hiddenFailure(argv, e)
        }
    }

    /** Hidden API unavailable, renamed or denied on this build: reported, never thrown (SPEC 12.4). */
    private fun hiddenFailure(argv: List<String>, e: Throwable): CommandOutcome =
        CommandOutcome(argv, CommandOutcome.NOT_RUN, "", "", false, 0, "${e.javaClass.simpleName}: ${e.message}")

    private fun packageProblem(name: String): String? = when {
        !PACKAGE.matches(name) -> "not a package name: $name"
        !packages.isInstalled(name) -> "package not installed: $name"
        else -> null
    }

    companion object {
        /** Java package name syntax. */
        val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        private val SENSOR_PRIVACY_SET = listOf("ISensorPrivacyManager.setSensorPrivacy")
        private val SENSOR_PRIVACY_GET = listOf("ISensorPrivacyManager.isSensorPrivacyEnabled")
        private const val MILLIS_PER_SECOND = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
