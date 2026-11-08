// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.probe

import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.SensorMode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.privilege.ConstantsWriter
import io.github.akane599.enforcedoze.privilege.DozeWorkerApi
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.api.ProcessCommandRunner
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * Reads the app can make from its own process with durable grants (tier T1): fixed argument lists,
 * no shell. `dumpsys` needs `DUMP`; `logcat -b events` needs `READ_LOGS`; battery history also needs
 * the usage-stats app op (ADR 0004).
 */
public class LocalReader(private val runner: CommandRunner = ProcessCommandRunner()) {
    public suspend fun deviceIdle(): CommandOutcome = run(listOf("dumpsys", "deviceidle"))

    public suspend fun sensorService(): CommandOutcome = run(listOf("dumpsys", "sensorservice"))

    public suspend fun eventLog(): CommandOutcome = run(listOf("logcat", "-b", "events", "-d", "-v", "epoch"))

    public suspend fun batteryHistory(): CommandOutcome = run(listOf("dumpsys", "batterystats", "--history"))

    /** Samsung's One UI version property (`90000` on the owner's phone); empty elsewhere. */
    public suspend fun oneUiVersion(): CommandOutcome = run(listOf("getprop", "ro.build.version.oneui"))

    private suspend fun run(argv: List<String>): CommandOutcome =
        withContext(Dispatchers.IO) { runner.run(argv, WorkerContract.READ_TIMEOUT_MILLIS) }
}

/**
 * The capability probes of SPEC 4.4. Read-only probes always run; those that change state (a lever A
 * test write, forced idle, sensor restriction, the all-sensors setter) run only when [run] is called with
 * `includeChanges`, record what they found first and put it back afterwards. A probe never throws: failures become [ProbeStatus.FAILED].
 */
public class CapabilityProber(private val local: LocalReader, private val selfPackage: String, private val constants: ConstantsWriter) {
    public suspend fun run(worker: DozeWorkerApi?, includeChanges: Boolean): List<ProbeResult> {
        val dump = readDeviceIdle(worker)
        return listOf(
            dump.result,
            probe(KEYS, "device_idle keys") { keys(dump.parsed) },
            probe(VENDOR, "Vendor fields") { vendor(dump.parsed) },
            probe(WRITE, WRITE_TITLE) { constantsWrite(worker, includeChanges, dump.parsed) },
            probe(FORCE, "Forced deep idle") { forceIdle(worker, includeChanges) },
            probe(RESTRICT, "Sensor restriction") { restrict(worker, includeChanges) },
            probe(PRIVACY, "All-sensors privacy (hidden API)") { sensorPrivacy(worker, includeChanges) },
            probe(EVENTS, "Event log") { events(worker) },
            probe(HISTORY, "Battery history") { history(worker) },
            probe(ONE_UI, "One UI version") { oneUi() },
            ProbeResult(
                PERSISTENCE,
                "Lever A persistence",
                ProbeStatus.SKIPPED,
                "Needs the global string to be checked 10 minutes after a write and after a reboot; it runs with the engine (P2).",
            ),
        )
    }

    private class Dump(val result: ProbeResult, val parsed: DeviceIdleDump?)

    private suspend fun readDeviceIdle(worker: DozeWorkerApi?): Dump {
        val viaWorker = worker?.let { w -> attempt { w.readDeviceIdle() }?.let { DeviceIdleDump.parse(it) } }
        if (viaWorker != null && viaWorker.isRecognised) {
            return Dump(ok(READ, "dumpsys deviceidle", "Read through the worker. ${state(viaWorker)}"), viaWorker)
        }
        val local = attempt { local.deviceIdle() }
        val viaGrant = local?.stdout?.let(DeviceIdleDump::parse)
        if (viaGrant != null && viaGrant.isRecognised) {
            return Dump(ok(READ, "dumpsys deviceidle", "Read with the DUMP grant. ${state(viaGrant)}"), viaGrant)
        }
        val why = local?.let { (it.error ?: it.stdout.lineSequence().firstOrNull().orEmpty()).take(DETAIL_CAP) }.orEmpty()
        return Dump(ProbeResult(READ, "dumpsys deviceidle", ProbeStatus.MISSING, "Needs DUMP or the worker. $why".trim()), null)
    }

    private fun keys(dump: DeviceIdleDump?): ProbeResult {
        if (dump == null) return skipped(KEYS, "device_idle keys", "No deviceidle dump.")
        val found = dump.settings.size - dump.extraSettingKeys.size
        val missing = dump.missingAospKeys
        val detail = "$found AOSP keys present" + if (missing.isEmpty()) "." else "; missing: ${missing.sorted().joinToString()}."
        return ProbeResult(KEYS, "device_idle keys", if (missing.isEmpty()) ProbeStatus.OK else ProbeStatus.MISSING, detail)
    }

    private fun vendor(dump: DeviceIdleDump?): ProbeResult {
        if (dump == null) return skipped(VENDOR, "Vendor fields", "No deviceidle dump.")
        val extraSections = dump.sections.keys.filterNot { it in AOSP_SECTIONS }
        val parts = listOfNotNull(
            dump.extraSettingKeys.takeIf { it.isNotEmpty() }?.let { "extra keys: ${it.sorted().joinToString()}" },
            extraSections.takeIf { it.isNotEmpty() }?.let { "extra sections: ${it.joinToString()}" },
            dump.unknownLines.takeIf { it.isNotEmpty() }?.let { "unknown lines: ${it.size}" },
            dump.motionSensor?.let { "motion sensor: ${it.substringAfter("name=\"").substringBefore('"')}" },
        )
        return ok(VENDOR, "Vendor fields", parts.joinToString("; ").ifEmpty { "None beyond AOSP; no motion sensor." })
    }

    /**
     * Lever A (ADR 0012): the app writes the global string, the worker reads it back. The test changes only
     * `location_accuracy`, which matters only while LOCATING (skipped where `remove_idle_location=true`), and puts
     * the previous string back exactly, even when the wait is cancelled. Without the worker the current string
     * cannot be read, so nothing is written.
     */
    private suspend fun constantsWrite(worker: DozeWorkerApi?, includeChanges: Boolean, dump: DeviceIdleDump?): ProbeResult {
        if (!constants.canWrite()) {
            return missing(WRITE, WRITE_TITLE, "Needs WRITE_SECURE_SETTINGS (granted through the worker at setup, or over ADB).")
        }
        if (worker == null) return skipped(WRITE, WRITE_TITLE, "$HELD; no worker to read the string back first, so no test write.")
        if (!includeChanges) return skipped(WRITE, WRITE_TITLE, "$HELD; the test write changes state and runs only on demand.")
        val current = dump?.settings?.get(TEST_KEY)?.number
            ?: return skipped(WRITE, WRITE_TITLE, "$HELD; no $TEST_KEY in dumpsys deviceidle to test with.")
        val read = worker.deviceIdleSettings()
        if (!read.isSuccess) return failed(WRITE, WRITE_TITLE, "settings get: ${read.error ?: read.stderr.take(DETAIL_CAP)}")
        return testWrite(worker, read.stdout.trim(), current)
    }

    private suspend fun testWrite(worker: DozeWorkerApi, before: String, current: Double): ProbeResult {
        val parsed = DeviceIdleSettings.parse(before)
        val target = current + 1
        val test = LinkedHashMap(parsed.values).apply { put(TEST_KEY, "%.1f".format(Locale.ROOT, target)) }
        if (!parsed.valid || DeviceIdleSettings.problems(test).isNotEmpty()) {
            return skipped(WRITE, WRITE_TITLE, "$HELD; the current string is not one the encoder writes, so it was left alone.")
        }
        val outcome = try {
            when {
                !constants.write(DeviceIdleSettings.encode(test)) -> "refused"
                awaitSetting(worker) { abs(it - target) < TOLERANCE } -> null
                else -> "dumpsys did not show it within 2 s"
            }
        } finally {
            constants.write(before.takeUnless { it == "null" })
        }
        val after = worker.deviceIdleSettings().stdout.trim()
        val restored = after == before
        val detail = "wrote $TEST_KEY=${test[TEST_KEY]} from the app: ${outcome ?: "dumpsys showed it"}; previous string " +
            if (restored) "restored exactly" else "NOT restored (now `$after`, was `$before`)"
        return ProbeResult(WRITE, WRITE_TITLE, if (outcome == null && restored) ProbeStatus.OK else ProbeStatus.FAILED, detail)
    }

    /** `DeviceIdleController` reloads the string on a content-observer callback, shortly after the write. */
    private suspend fun awaitSetting(worker: DozeWorkerApi, matches: (Double) -> Boolean): Boolean {
        repeat(SETTLE_ATTEMPTS) {
            val value = DeviceIdleDump.parse(worker.readDeviceIdle()).settings[TEST_KEY]?.number
            if (value != null && matches(value)) return true
            delay(SETTLE_INTERVAL_MILLIS)
        }
        return false
    }

    private suspend fun forceIdle(worker: DozeWorkerApi?, includeChanges: Boolean): ProbeResult {
        val title = "Forced deep idle"
        if (worker == null) return skipped(FORCE, title, "Needs the worker.")
        if (!includeChanges) return skipped(FORCE, title, "Changes state; runs only on demand.")
        if (worker.idleState("force").stdout.trim() == "true") {
            return skipped(FORCE, title, "Already forced by someone else; left alone.")
        }
        val forced = worker.forceIdle("deep")
        val deep = worker.idleState("deep").stdout.trim()
        val force = worker.idleState("force").stdout.trim()
        val unforced = worker.unforce()
        val after = worker.idleState("force").stdout.trim()
        val works = forced.isSuccess && force == "true" && deep == "IDLE" && unforced.isSuccess && after == "false"
        val detail = "force-idle deep: deep=$deep force=$force; unforce: force=$after"
        return ProbeResult(FORCE, title, if (works) ProbeStatus.OK else ProbeStatus.FAILED, detail)
    }

    private suspend fun restrict(worker: DozeWorkerApi?, includeChanges: Boolean): ProbeResult {
        val title = "Sensor restriction"
        if (worker == null) return skipped(RESTRICT, title, "Needs the worker.")
        if (!includeChanges) return skipped(RESTRICT, title, "Changes state; runs only on demand.")
        val before = SensorServiceDump.parse(worker.readSensorService())
        val restricted = worker.setSensorRestriction(selfPackage)
        val during = SensorServiceDump.parse(worker.readSensorService())
        // Put back what was there: someone else's restriction stays, otherwise normal mode.
        val restore = if (before.mode == SensorMode.RESTRICTED) before.modePackage else null
        worker.setSensorRestriction(restore)
        val after = SensorServiceDump.parse(worker.readSensorService())
        val works = restricted.isSuccess && during.mode == SensorMode.RESTRICTED && during.modePackage == selfPackage &&
            after.mode == before.mode
        val detail = "restrict $selfPackage: `${during.modeLine}`; restored: `${after.modeLine}`"
        return ProbeResult(RESTRICT, title, if (works) ProbeStatus.OK else ProbeStatus.FAILED, detail)
    }

    private suspend fun sensorPrivacy(worker: DozeWorkerApi?, includeChanges: Boolean): ProbeResult {
        val title = "All-sensors privacy (hidden API)"
        if (worker == null) return skipped(PRIVACY, title, "Needs the worker.")
        val read = worker.allSensorsOff()
        if (!read.isSuccess) return ProbeResult(PRIVACY, title, ProbeStatus.FAILED, "isSensorPrivacyEnabled: ${read.error}")
        val current = read.stdout.trim()
        if (!includeChanges) return ok(PRIVACY, title, "isSensorPrivacyEnabled() = $current; setter not tried.")
        // Setting the value it already has checks the method and the permission without changing anything.
        val set = worker.setAllSensorsOff(current == "true")
        val status = if (set.isSuccess) ProbeStatus.OK else ProbeStatus.FAILED
        return ProbeResult(PRIVACY, title, status, "isSensorPrivacyEnabled() = $current; setSensorPrivacy($current): ${set.error ?: "ok"}")
    }

    private suspend fun events(worker: DozeWorkerApi?): ProbeResult {
        val title = "Event log"
        val own = attempt { local.eventLog() }?.takeIf { it.isSuccess }?.let { DozeEventLog.parse(it.stdout) }
        if (own != null && own.events.isNotEmpty()) {
            return ok(EVENTS, title, "The app reads the events buffer with READ_LOGS; tags: ${tags(own)}.")
        }
        val viaWorker = worker?.let { w -> attempt { w.readEventLog(0) }?.let(DozeEventLog::parse) }
        if (viaWorker != null) {
            val tags = if (viaWorker.events.isEmpty()) "no device_idle events in the buffer yet" else "tags: ${tags(viaWorker)}"
            return ok(
                EVENTS,
                title,
                "Read through the worker; $tags. READ_LOGS in the app: ${if (own == null) "no" else "no device_idle lines"}.",
            )
        }
        return ProbeResult(EVENTS, title, ProbeStatus.MISSING, "Needs READ_LOGS (and the system's log-access consent) or the worker.")
    }

    private suspend fun history(worker: DozeWorkerApi?): ProbeResult {
        val title = "Battery history"
        val viaWorker = worker?.let { w -> attempt { w.readBatteryHistory(0) } }
        if (viaWorker != null && HISTORY_HEADER in viaWorker) {
            return ok(HISTORY, title, "dumpsys batterystats --history through the worker: ${header(viaWorker)}")
        }
        val own = attempt { local.batteryHistory() }
        if (own != null && HISTORY_HEADER in own.stdout) {
            return ok(HISTORY, title, "dumpsys batterystats --history with DUMP and usage access: ${header(own.stdout)}")
        }
        return ProbeResult(HISTORY, title, ProbeStatus.MISSING, "Needs DUMP and the usage-stats app op, or the worker.")
    }

    private suspend fun oneUi(): ProbeResult {
        val version = attempt { local.oneUiVersion() }?.stdout?.trim().orEmpty()
        return if (version.isEmpty()) {
            skipped(ONE_UI, "One UI version", "Not a Samsung One UI build.")
        } else {
            ok(ONE_UI, "One UI version", "ro.build.version.oneui=$version")
        }
    }

    // A probe never throws (SPEC 4.4): whatever fails is the probe's result.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun probe(id: String, title: String, block: suspend () -> ProbeResult): ProbeResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ProbeResult(id, title, ProbeStatus.FAILED, "${e.javaClass.simpleName}: ${e.message}".take(DETAIL_CAP))
    }

    /** One way of reading; null lets the probe try the next way and report what is missing. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun state(dump: DeviceIdleDump) = "deep=${dump.deepState} light=${dump.lightState} force=${dump.forceIdle}"

    private fun tags(log: DozeEventLog) = log.tagsSeen.joinToString { it.tag }

    private fun header(text: String) = text.lineSequence().first { HISTORY_HEADER in it }.trim().take(DETAIL_CAP)

    private fun ok(id: String, title: String, detail: String) = ProbeResult(id, title, ProbeStatus.OK, detail)

    private fun skipped(id: String, title: String, detail: String) = ProbeResult(id, title, ProbeStatus.SKIPPED, detail)

    private fun missing(id: String, title: String, detail: String) = ProbeResult(id, title, ProbeStatus.MISSING, detail)

    private fun failed(id: String, title: String, detail: String) = ProbeResult(id, title, ProbeStatus.FAILED, detail)

    public companion object {
        public const val READ: String = "deviceidle.read"
        public const val KEYS: String = "deviceidle.keys"
        public const val VENDOR: String = "deviceidle.vendor"
        public const val FORCE: String = "deviceidle.force"
        public const val RESTRICT: String = "sensorservice.restrict"
        public const val PRIVACY: String = "sensorprivacy.hidden"
        public const val EVENTS: String = "eventlog.read"
        public const val HISTORY: String = "batterystats.history"
        public const val ONE_UI: String = "oneui.version"
        public const val WRITE: String = "constants.write"
        public const val PERSISTENCE: String = "constants.persistence"

        private const val WRITE_TITLE = "Lever A write (global string)"
        private const val HELD = "WRITE_SECURE_SETTINGS held"
        private const val TEST_KEY = "location_accuracy"
        private const val SETTLE_ATTEMPTS = 20
        private const val SETTLE_INTERVAL_MILLIS = 100L
        private const val TOLERANCE = 0.01

        private const val HISTORY_HEADER = "Battery History"
        private const val DETAIL_CAP = 200

        /** Section headers AOSP prints in `dumpsys deviceidle` (API 35–37 emulator fixtures). */
        private val AOSP_SECTIONS = setOf(
            "Flags",
            "Settings",
            "Idling history",
            "Whitelist (except idle) system apps",
            "Whitelist system apps",
            "Whitelist user apps",
            "Whitelist (except idle) all app ids",
            "Whitelist user app ids",
            "Whitelist all app ids",
            "Temp whitelist schedule",
            "Temp whitelist app ids",
        )
    }
}
