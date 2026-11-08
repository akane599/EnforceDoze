// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.doze.ConstantsValue
import io.github.akane599.enforcedoze.doze.EngineStore
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.doze.Mode
import io.github.akane599.enforcedoze.doze.PersistedState
import io.github.akane599.enforcedoze.doze.PreviousSnapshot
import io.github.akane599.enforcedoze.doze.SensorValue
import io.github.akane599.enforcedoze.doze.Setting
import io.github.akane599.enforcedoze.doze.StockSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DateTimeException
import java.time.Instant

/**
 * The engine's book and its two snapshots as JSON files in [dir] (SPEC 5.2, ADR 0015): `state.json`,
 * `previous.json` and `stock.json`. Each write goes to a temporary file that is synced and then renamed
 * over the old one, so a crash leaves either the old file or the new one. A file that cannot be read is
 * moved aside (kept for bug reports) and treated as missing.
 */
public class EngineFiles(private val dir: File) : EngineStore {
    // Defaults are left out, so each file shows only what is set; previous.json keeps `"string": null`.
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun load(): PersistedState = read(STATE, StateFile.serializer()) { it.toState() } ?: PersistedState()

    override suspend fun save(state: PersistedState): Unit = write(STATE, StateFile.serializer(), StateFile.of(state))

    override suspend fun previous(): PreviousSnapshot? = read(PREVIOUS, PreviousFile.serializer()) { it.toSnapshot() }

    override suspend fun savePrevious(snapshot: PreviousSnapshot): Unit =
        write(PREVIOUS, PreviousFile.serializer(), PreviousFile.of(snapshot))

    override suspend fun stock(): StockSnapshot? = read(STOCK, StockFile.serializer()) {
        StockSnapshot(Instant.parse(it.capturedAt), it.effective)
    }

    override suspend fun saveStock(snapshot: StockSnapshot): Unit =
        write(STOCK, StockFile.serializer(), StockFile(snapshot.capturedAt.toString(), snapshot.effective))

    /** Decodes and converts [name]; a file that fails either step is set aside. */
    private suspend fun <F, T> read(name: String, serializer: KSerializer<F>, convert: (F) -> T): T? = withContext(Dispatchers.IO) {
        val file = File(dir, name)
        if (!file.isFile) return@withContext null
        try {
            convert(json.decodeFromString(serializer, file.readText()))
        } catch (e: SerializationException) {
            setAside(file, e)
        } catch (e: IllegalArgumentException) {
            // An enum name this version does not know, or a value missing for its lever.
            setAside(file, e)
        } catch (e: DateTimeException) {
            setAside(file, e)
        }
    }

    private fun <T> setAside(file: File, cause: Exception): T? {
        val aside = File(dir, "${file.name}.unreadable-${System.currentTimeMillis()}")
        if (!file.renameTo(aside)) throw IOException("${file.name} cannot be read and cannot be moved aside", cause)
        return null
    }

    private suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T): Unit = withContext(Dispatchers.IO) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = File(dir, name)
        val temporary = File(dir, "$name.tmp")
        FileOutputStream(temporary).use { out ->
            out.write(json.encodeToString(serializer, value).toByteArray())
            out.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val STATE = "state.json"
        const val PREVIOUS = "previous.json"
        const val STOCK = "stock.json"
    }
}

/** One lever's value as stored; only the fields its lever uses are set. */
@Serializable
internal data class SettingFile(
    val lever: String,
    /** Lever A: the exact string; absent means no string. */
    val string: String? = null,
    /** Forced idle or sensor privacy. */
    val on: Boolean? = null,
    /** Lever C: `NORMAL`, `RESTRICTED` or another mode's name. */
    val sensors: String? = null,
    val allowPackage: String? = null,
    val packages: List<String>? = null,
) {
    fun toSetting(): Setting = when (Lever.valueOf(lever)) {
        Lever.CONSTANTS -> Setting.Constants(string?.let { ConstantsValue.Exact(it) } ?: ConstantsValue.Absent)
        Lever.FORCE_IDLE -> Setting.ForceIdle(requireNotNull(on) { "forced idle without a value" })
        Lever.SENSORS -> Setting.Sensors(sensorValue(requireNotNull(sensors) { "sensors without a mode" }, allowPackage))
        Lever.SENSOR_PRIVACY -> Setting.SensorPrivacy(requireNotNull(on) { "sensor privacy without a value" })
        Lever.ALLOWLIST -> Setting.Allowlist(packages.orEmpty().toSet())
    }

    companion object {
        fun of(setting: Setting): SettingFile = when (setting) {
            is Setting.Constants -> SettingFile(setting.lever.name, string = (setting.value as? ConstantsValue.Exact)?.text)

            is Setting.ForceIdle -> SettingFile(setting.lever.name, on = setting.forced)

            is Setting.Sensors -> SettingFile(
                setting.lever.name,
                sensors = sensorName(setting.value),
                allowPackage = allowOf(setting.value),
            )

            is Setting.SensorPrivacy -> SettingFile(setting.lever.name, on = setting.on)

            is Setting.Allowlist -> SettingFile(setting.lever.name, packages = setting.packages.sorted())
        }
    }
}

internal fun sensorName(value: SensorValue): String = when (value) {
    SensorValue.Normal -> NORMAL
    is SensorValue.Restricted -> RESTRICTED
    is SensorValue.Other -> value.name
}

internal fun allowOf(value: SensorValue): String? = (value as? SensorValue.Restricted)?.allowPackage

internal fun sensorValue(name: String, allowPackage: String?): SensorValue = when {
    name == NORMAL -> SensorValue.Normal
    name == RESTRICTED && allowPackage != null -> SensorValue.Restricted(allowPackage)
    else -> SensorValue.Other(name)
}

private const val NORMAL = "NORMAL"
private const val RESTRICTED = "RESTRICTED"

@Serializable
internal data class StateFile(
    val mode: String = Mode.MANAGED.name,
    val profileId: String? = null,
    val written: List<SettingFile> = emptyList(),
    /** Per lever, the times of recent drift corrections (SPEC 5.7). */
    val corrections: Map<String, List<String>> = emptyMap(),
    val guarded: List<String> = emptyList(),
    val ownerAllowlist: List<String>? = null,
    val reapplyOnDrift: Boolean = true,
    val allowlistKeep: List<String>? = null,
) {
    fun toState(): PersistedState = PersistedState(
        mode = Mode.valueOf(mode),
        profileId = profileId,
        written = written.map { it.toSetting() }.associateBy { it.lever },
        corrections = corrections.mapKeys { Lever.valueOf(it.key) }.mapValues { (_, times) -> times.map(Instant::parse) },
        guarded = guarded.map(Lever::valueOf).toSet(),
        ownerAllowlist = ownerAllowlist?.toSet(),
        reapplyOnDrift = reapplyOnDrift,
        allowlistKeep = allowlistKeep?.toSet(),
    )

    companion object {
        fun of(state: PersistedState): StateFile = StateFile(
            mode = state.mode.name,
            profileId = state.profileId,
            written = Lever.entries.mapNotNull { state.written[it] }.map(SettingFile::of),
            corrections = state.corrections.entries.associate { (lever, times) -> lever.name to times.map(Instant::toString) },
            guarded = state.guarded.map { it.name }.sorted(),
            ownerAllowlist = state.ownerAllowlist?.sorted(),
            reapplyOnDrift = state.reapplyOnDrift,
            allowlistKeep = state.allowlistKeep?.sorted(),
        )
    }
}

/** `previous.json` (SPEC 5.2): the string as found, `null` when there was none, and what the phone showed with it. */
@Serializable
internal data class PreviousFile(
    val capturedAt: String,
    @SerialName("string") val constants: String?,
    val writer: String? = null,
    val effective: Map<String, String> = emptyMap(),
    val sensors: String? = null,
    val sensorsAllowPackage: String? = null,
    val forceIdle: Boolean? = null,
    val allowlist: List<String>? = null,
) {
    fun toSnapshot(): PreviousSnapshot = PreviousSnapshot(
        capturedAt = Instant.parse(capturedAt),
        constants = constants?.let { ConstantsValue.Exact(it) } ?: ConstantsValue.Absent,
        writer = writer,
        effective = effective,
        sensors = sensors?.let { sensorValue(it, sensorsAllowPackage) },
        forceIdle = forceIdle,
        allowlist = allowlist?.toSet(),
    )

    companion object {
        fun of(snapshot: PreviousSnapshot): PreviousFile = PreviousFile(
            capturedAt = snapshot.capturedAt.toString(),
            constants = (snapshot.constants as? ConstantsValue.Exact)?.text,
            writer = snapshot.writer,
            effective = snapshot.effective.toSortedMap(),
            sensors = snapshot.sensors?.let(::sensorName),
            sensorsAllowPackage = snapshot.sensors?.let(::allowOf),
            forceIdle = snapshot.forceIdle,
            allowlist = snapshot.allowlist?.sorted(),
        )
    }
}

/** `stock.json` (SPEC 5.2): what `dumpsys deviceidle` showed with the string deleted. */
@Serializable
internal data class StockFile(val capturedAt: String, val effective: Map<String, String>)
