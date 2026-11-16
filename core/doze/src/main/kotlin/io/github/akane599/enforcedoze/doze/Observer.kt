// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.parse.SettingValue
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/** Reads the device into the [book]; a read that fails or times out leaves its levers unknown. */
internal class Observer(private val device: DozeDevice, private val calls: DeviceCalls, private val book: Book) {
    /** Reads what [levers] need, each source once. */
    suspend fun read(levers: Set<Lever>) {
        val exact = Lever.CONSTANTS in levers && readConstants()
        // Without the exact string, lever A is judged on the effective values instead.
        if (Lever.FORCE_IDLE in levers || (Lever.CONSTANTS in levers && !exact)) readDeviceIdle()
        if (Lever.SENSORS in levers || Lever.SENSOR_PRIVACY in levers) readSensors()
        if (Lever.ALLOWLIST in levers) readAllowlist()
        // The extras are read only when chosen or still held, so an owner who never uses them pays nothing.
        if (needed(Lever.WIFI, levers) || needed(Lever.MOBILE_DATA, levers)) readRadios()
        if (needed(Lever.APP_SUSPENSION, levers)) readSuspension()
    }

    private fun needed(lever: Lever, levers: Set<Lever>): Boolean =
        lever in levers && (book.persisted.extras.uses(lever) || book.written[lever] != null)

    private suspend fun readRadios() {
        val reading = (calls.run(null) { device.readRadios() } as? DeviceResult.Ok)?.value
        book.radios = reading
        if (reading == null) {
            book.seen.remove(Lever.WIFI)
            book.seen.remove(Lever.MOBILE_DATA)
            return
        }
        book.seen[Lever.WIFI] = Setting.Wifi(reading.wifiEnabled)
        book.seen[Lever.MOBILE_DATA] = Setting.MobileData(reading.dataEnabled)
    }

    /**
     * The chosen apps and any the engine still holds are read; the lever reads as the held ones that are suspended.
     * A chosen app someone else suspended is in [Book.suspension] only: it is theirs, so it is never drift (ADR 0038).
     * The read waits behind a suspension call still running, so it never reports an app a late call then suspends.
     */
    private suspend fun readSuspension() {
        val held = (book.written[Lever.APP_SUSPENSION] as? Setting.Suspension)?.packages.orEmpty()
        val asked = book.persisted.extras.suspend + held
        val reading = (calls.run(Lever.APP_SUSPENSION) { device.readSuspension(asked) } as? DeviceResult.Ok)?.value
        book.suspension = reading
        reading?.let { book.seen[Lever.APP_SUSPENSION] = Setting.Suspension(it.suspended intersect held) }
            ?: book.seen.remove(Lever.APP_SUSPENSION)
    }

    /**
     * Re-reads the radios until [expected] shows or [settleMillis] pass: Wi-Fi reports on only once its state machine
     * has started, a moment after the command returns (ADR 0038).
     */
    suspend fun settleRadios(expected: List<Setting>, settleMillis: Long, pollMillis: Long) {
        var waited = 0L
        while (expected.any { book.seen[it.lever] != it } && waited < settleMillis) {
            delay(pollMillis)
            waited += pollMillis
            readRadios()
        }
    }

    suspend fun readConstants(): Boolean {
        val result = calls.run(null) { device.readConstants() }
        if (result is DeviceResult.Ok) {
            book.seen[Lever.CONSTANTS] = Setting.Constants(result.value.value)
            book.writer = result.value.writer
            return true
        }
        book.seen.remove(Lever.CONSTANTS)
        // A writer read earlier may no longer be the last one; never name it from an old read.
        book.writer = null
        return false
    }

    suspend fun readDeviceIdle(): Boolean {
        val result = calls.run(null) { device.readDeviceIdle() }
        val dump = (result as? DeviceResult.Ok)?.value
        if (dump == null) {
            book.seen.remove(Lever.FORCE_IDLE)
            book.effective = null
            return false
        }
        // A dump without a Settings section shows no effective values, but the keys seen last still hold.
        book.effective = dump.settings.takeIf { it.isNotEmpty() }
        if (dump.settings.isNotEmpty()) book.knownKeys = dump.settings.keys
        dump.forceIdle?.let { book.seen[Lever.FORCE_IDLE] = Setting.ForceIdle(it) } ?: book.seen.remove(Lever.FORCE_IDLE)
        return true
    }

    private suspend fun readSensors() {
        val reading = (calls.run(null) { device.readSensors() } as? DeviceResult.Ok)?.value
        reading?.mode?.let { book.seen[Lever.SENSORS] = Setting.Sensors(it) } ?: book.seen.remove(Lever.SENSORS)
        reading?.privacy?.let { book.seen[Lever.SENSOR_PRIVACY] = Setting.SensorPrivacy(it) } ?: book.seen.remove(Lever.SENSOR_PRIVACY)
    }

    private suspend fun readAllowlist() {
        val packages = (calls.run(null) { device.readAllowlist() } as? DeviceResult.Ok)?.value
        if (packages != null) book.seen[Lever.ALLOWLIST] = Setting.Allowlist(packages) else book.seen.remove(Lever.ALLOWLIST)
    }

    /**
     * Polls `dumpsys deviceidle` until the effective values show [expected], at most [settleMillis]
     * (SPEC 6.5: about 2 s for the system to reload the string). Returns the differences left, or null
     * when they cannot be judged.
     */
    suspend fun settle(expected: ConstantsValue, stock: StockSnapshot?, settleMillis: Long, pollMillis: Long): List<String>? {
        var waited = 0L
        while (true) {
            val diffs = if (readDeviceIdle()) differences(expected, stock) else null
            if (diffs == null || diffs.isEmpty() || waited >= settleMillis) return diffs
            delay(pollMillis)
            waited += pollMillis
        }
    }

    /**
     * What the last effective values show against [expected]: each key of the string must read back as
     * written; no string must read back as `stock.json`, exactly. Null when that cannot be judged.
     */
    fun differences(expected: ConstantsValue, stock: StockSnapshot?): List<String>? {
        val effective = book.effective ?: return null
        return when (expected) {
            is ConstantsValue.Exact -> {
                val parsed = DeviceIdleSettings.parse(expected.text)
                if (!parsed.valid) return listOf("the string is not valid")
                parsed.values.mapNotNull { (key, value) ->
                    val shown = effective[key]
                    if (shown != null && sameValue(value, shown)) null else "$key=$value (shows ${shown?.raw ?: "nothing"})"
                }
            }

            ConstantsValue.Absent -> stock?.effective?.mapNotNull { (key, value) ->
                val shown = effective[key]?.raw
                if (shown == value) null else "$key=$value in stock.json (shows ${shown ?: "nothing"})"
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-4

        /** Written values are milliseconds, factors or flags; dumpsys prints durations as `+5m0s0ms`. */
        fun sameValue(written: String, shown: SettingValue): Boolean {
            written.toBooleanStrictOrNull()?.let { return shown.flag == it }
            val number = written.toDoubleOrNull() ?: return written == shown.raw
            val value = shown.number ?: return false
            return abs(number - value) <= TOLERANCE * max(1.0, abs(number))
        }
    }
}
