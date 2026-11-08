// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.parse.SettingValue
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * A phone as the engine sees it: the global string overrides stock key by key, as
 * `DeviceIdleController` does (ADR 0012), and `dumpsys deviceidle` prints durations the AOSP way.
 */
internal class FakeDevice(var constants: ConstantsValue = ConstantsValue.Absent, var writer: String? = null) {
    val stock: Map<String, String> = linkedMapOf(
        "inactive_to" to "1800000",
        "idle_after_inactive_to" to "1800000",
        "sensing_to" to "240000",
        "locating_to" to "30000",
        "motion_inactive_to" to "600000",
        "light_after_inactive_to" to "180000",
        "idle_to" to "3600000",
        "max_idle_to" to "21600000",
        "idle_factor" to "2.0",
    )
    var forced = false
    var sensors: SensorValue = SensorValue.Normal
    var privacy = false
    var allowlist: Set<String> = emptySet()

    var canReadConstants = true
    var canReadDeviceIdle = true

    /** Operations that never answer in time, by name ("write:ForceIdle", "readDeviceIdle", …). */
    val hang = mutableSetOf<String>()

    /** Writes the device refuses. */
    val refuse = mutableSetOf<String>()

    /** Every call in order: "read:constants", "write:Settings.Global put …", … */
    val log = mutableListOf<String>()

    val writes: List<DeviceWrite> get() = mutableWrites
    private val mutableWrites = mutableListOf<DeviceWrite>()

    /** Another app writes the string, as Tasker through Shizuku does (writer `com.android.shell`). */
    fun foreignWrite(text: String?, by: String = "com.android.shell") {
        constants = ConstantsValue.of(text)
        writer = by
    }

    fun effective(): Map<String, String> {
        val overrides = (constants as? ConstantsValue.Exact)?.let { DeviceIdleSettings.parse(it.text) }
        val values = if (overrides?.valid == true) overrides.values else emptyMap()
        return stock.mapValues { (key, value) -> print(key, values[key] ?: value) }
    }

    val port: DozeDevice = object : DozeDevice {
        override suspend fun write(write: DeviceWrite): DeviceResult<Unit> {
            val name = "write:${write::class.simpleName}"
            log += "write:${write.label}"
            mutableWrites += write
            if (name in hang) delay(HANG_MILLIS)
            if (name in refuse) return DeviceResult.Failed("refused")
            when (write) {
                is DeviceWrite.SetConstants -> {
                    constants = ConstantsValue.of(write.text)
                    writer = SELF
                }

                DeviceWrite.ForceIdle -> forced = true

                DeviceWrite.Unforce -> forced = false

                is DeviceWrite.RestrictSensors -> sensors = SensorValue.Restricted(write.allowPackage)

                DeviceWrite.EnableSensors -> sensors = SensorValue.Normal

                is DeviceWrite.SensorPrivacy -> privacy = write.on

                is DeviceWrite.Allowlist -> allowlist = if (write.add) allowlist + write.packageName else allowlist - write.packageName
            }
            return DeviceResult.Ok(Unit)
        }

        override suspend fun readConstants(): DeviceResult<ConstantsReading> = read("constants", canReadConstants) {
            ConstantsReading(constants, writer)
        }

        override suspend fun readDeviceIdle(): DeviceResult<DeviceIdleDump> = read("deviceIdle", canReadDeviceIdle) {
            DeviceIdleDump(
                flags = emptyMap(),
                settings = effective().mapValues { SettingValue(it.value) },
                fields = mapOf("mForceIdle" to forced.toString()),
                sections = emptyMap(),
                unknownLines = emptyList(),
            )
        }

        override suspend fun readSensors(): DeviceResult<SensorReading> = read("sensors", true) { SensorReading(sensors, privacy) }

        override suspend fun readAllowlist(): DeviceResult<Set<String>> = read("allowlist", true) { allowlist }

        private suspend fun <T> read(name: String, allowed: Boolean, value: () -> T): DeviceResult<T> {
            log += "read:$name"
            if ("read:$name" in hang) delay(HANG_MILLIS)
            return if (allowed) DeviceResult.Ok(value()) else DeviceResult.Failed("not readable")
        }
    }

    companion object {
        const val SELF = "io.github.akane599.enforcedoze"
        const val HANG_MILLIS = 10_000L
        private val FACTORS = setOf("idle_factor")

        /** `TimeUtils.formatDuration`: `+5m0s0ms`, `+30s0ms`, `0`; factors as floats. */
        fun print(key: String, value: String): String {
            if (key in FACTORS) return value.toFloat().toString()
            val millis = value.toLongOrNull() ?: return value
            if (millis == 0L) return "0"
            val parts = listOf(
                millis / 86_400_000 to "d",
                millis / 3_600_000 % 24 to "h",
                millis / 60_000 % 60 to "m",
                millis / 1_000 % 60 to "s",
            )
            val first = parts.indexOfFirst { it.first > 0 }.takeIf { it >= 0 } ?: parts.size
            return "+" + parts.drop(first).joinToString("") { "${it.first}${it.second}" } + "${millis % 1_000}ms"
        }
    }
}

internal class FakeStore(var state: PersistedState = PersistedState()) : EngineStore {
    var previous: PreviousSnapshot? = null
    var stock: StockSnapshot? = null
    var saves = 0

    override suspend fun load(): PersistedState = state

    override suspend fun save(state: PersistedState) {
        this.state = state
        saves++
    }

    override suspend fun previous(): PreviousSnapshot? = previous

    override suspend fun savePrevious(snapshot: PreviousSnapshot) {
        previous = snapshot
    }

    override suspend fun stock(): StockSnapshot? = stock

    override suspend fun saveStock(snapshot: StockSnapshot) {
        stock = snapshot
    }
}

internal class FakeJournal : EngineJournal {
    val actions = mutableListOf<ActionRecord>()
    val events = mutableListOf<EngineEvent>()

    override suspend fun action(record: ActionRecord) {
        actions += record
    }

    override suspend fun event(event: EngineEvent) {
        events += event
    }

    inline fun <reified T : EngineEvent> events(): List<T> = events.filterIsInstance<T>()
}

internal val BASE_TIME: Instant = Instant.parse("2026-11-08T22:00:00Z")
