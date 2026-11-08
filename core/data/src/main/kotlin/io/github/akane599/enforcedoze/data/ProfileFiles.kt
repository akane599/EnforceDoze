// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Guardrails
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What an import needs to know about this phone and the profiles already on it (SPEC 6.3). */
public data class ImportContext(
    /** The keys `stock.json` showed; null before it was captured, when no key can be called missing. */
    val phoneKeys: Set<String>?,
    /** `stock.json` values in milliseconds, for the allowlist floors; null before it was captured. */
    val stock: Map<String, Long>?,
    /** Ids in use, built-ins included: a clash gets a fresh id, so an import never replaces a profile. */
    val takenIds: Set<String>,
)

/** A key an imported profile keeps but the owner should know about (SPEC 6.3). */
public sealed interface ImportNote {
    public val key: String

    /** `stock.json` does not list it: the engine skips it and the profile shows "partly applied". */
    public data class NotOnThisPhone(override val key: String) : ImportNote

    /** A One UI key (ADR 0012): kept, effect unknown on this phone. */
    public data class EffectUnknown(override val key: String) : ImportNote
}

/** Why a profile file is refused. The UI words each case. */
public sealed interface ImportProblem {
    /** Not JSON, not an object, or [field] missing or of the wrong kind. */
    public data class NotAProfile(val field: String?, val detail: String) : ImportProblem

    /** Written by a newer version of the app. */
    public data class NewerSchema(val schema: Int) : ImportProblem

    public data class Guardrail(val problem: GuardrailProblem) : ImportProblem
}

public sealed interface ProfileImport {
    /** [renamedFrom] is the file's id when it clashed with a profile already on the phone. */
    public data class Imported(val profile: Profile, val notes: List<ImportNote>, val renamedFrom: String?) : ProfileImport

    public data class Refused(val problems: List<ImportProblem>) : ProfileImport
}

/**
 * The profile file (SPEC 6.4): one JSON file per profile, values in milliseconds as numbers. [encode] writes
 * it for export and for the app's own store; [import] reads one the owner chose, with the guardrails, and
 * [read] reads back the app's own store, which was checked when it was saved.
 */
public object ProfileFiles {
    public fun encode(profile: Profile): String = json.encodeToString(JsonObject.serializer(), toJson(profile))

    /** A file name for [profile] that no id can turn into a path. */
    public fun fileName(profile: Profile): String = fileName(profile.id)

    public fun fileName(id: String): String = id.replace(UNSAFE, "_") + ".json"

    /** A file the owner imports: refused with every reason found, or kept with its notes. */
    public fun import(text: String, context: ImportContext): ProfileImport {
        val parsed = read(text)
        val profile = parsed.profile ?: return ProfileImport.Refused(parsed.problems)
        val guardrails = Guardrails.check(profile.deviceIdle, context.stock).map(ImportProblem::Guardrail)
        if (guardrails.isNotEmpty()) return ProfileImport.Refused(guardrails)
        val id = Profile.freshId(profile.id, context.takenIds)
        val renamed = id != profile.id
        val kept = profile.copy(id = id, name = if (renamed) "${profile.name} (${id.substringAfterLast('-')})" else profile.name)
        return ProfileImport.Imported(kept, notes(profile.deviceIdle.keys, context.phoneKeys), profile.id.takeIf { renamed })
    }

    /** A file of the app's own store, or null with the reasons when it no longer reads. */
    public fun read(text: String): Parsed = ProfileReader().read(text)

    public data class Parsed(val profile: Profile?, val problems: List<ImportProblem>)

    private fun notes(keys: Set<String>, phoneKeys: Set<String>?): List<ImportNote> = keys.flatMap { key ->
        listOfNotNull(
            ImportNote.EffectUnknown(key).takeIf { key in Guardrails.VENDOR_KEYS },
            ImportNote.NotOnThisPhone(key).takeIf { phoneKeys != null && key !in phoneKeys },
        )
    }

    internal fun toJson(profile: Profile): JsonObject = buildJsonObject {
        put("id", profile.id)
        put("name", profile.name)
        put("schema", Profile.SCHEMA)
        putJsonObject("levers") {
            put("constants", profile.levers.constants)
            putJsonObject("forceIdle") {
                put("enabled", profile.levers.forceIdle.enabled)
                put("delaySec", profile.levers.forceIdle.delaySec)
                put("untilUnlock", profile.levers.forceIdle.untilUnlock)
            }
            put("sensors", SENSORS.entries.first { it.value == profile.levers.sensors }.key)
            put("allowlist", ALLOWLIST.entries.first { it.value == profile.levers.allowlist }.key)
        }
        putJsonObject("deviceIdle") {
            profile.deviceIdle.forEach { (key, value) -> put(key, primitive(value)) }
        }
    }

    /** Milliseconds and counts as integers, factors as decimals, flags as booleans; anything else as written. */
    private fun primitive(value: String): JsonPrimitive = value.toLongOrNull()?.let(::JsonPrimitive)
        ?: value.toDoubleOrNull()?.let(::JsonPrimitive)
        ?: value.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        ?: JsonPrimitive(value)

    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
}

/**
 * Reads one profile file and collects every problem in it, so the owner sees them all at once. A wrong field
 * records its problem and reads as a placeholder; any problem refuses the whole file, so no placeholder leaves.
 */
private class ProfileReader {
    private val problems = mutableListOf<ImportProblem>()

    fun read(text: String): ProfileFiles.Parsed {
        val profile = root(text)?.let(::profile)
        return ProfileFiles.Parsed(profile.takeIf { problems.isEmpty() }, problems.toList())
    }

    /** The file's object, if it is one this version reads. */
    private fun root(text: String): JsonObject? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject ?: return wrong(null, "not a JSON object", null)
        } catch (e: SerializationException) {
            return wrong(null, "not JSON: ${e.message?.lineSequence()?.firstOrNull()}", null)
        }
        val schema = root.int("schema")
        return when {
            schema == null -> wrong("schema", "a whole number", null)
            schema > Profile.SCHEMA -> null.also { problems += ImportProblem.NewerSchema(schema) }
            schema < 1 -> wrong("schema", "1 or more", null)
            else -> root
        }
    }

    private fun profile(root: JsonObject): Profile = Profile(
        id = string(root, "id")?.takeIf { Profile.ID.matches(it) } ?: wrong("id", "1 to 64 letters, digits, '.', '_' or '-'", ""),
        name = string(root, "name")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
            ?: wrong("name", "1 to $MAX_NAME characters", ""),
        levers = levers(root["levers"]),
        deviceIdle = deviceIdle(root["deviceIdle"]),
    )

    private fun levers(element: JsonElement?): Levers {
        val levers = element as? JsonObject ?: return wrong("levers", "an object", NO_LEVERS)
        return Levers(
            constants = boolean(levers, "constants", "levers"),
            forceIdle = forceIdle(levers["forceIdle"]),
            sensors = choice(levers, "sensors", SENSORS, SensorLever.OFF),
            allowlist = choice(levers, "allowlist", ALLOWLIST, AllowlistLever.INHERIT),
        )
    }

    /** `delaySec` and `untilUnlock` may be left out: 0 and false. */
    private fun forceIdle(element: JsonElement?): ForceIdleLever {
        val force = element as? JsonObject ?: return wrong("levers.forceIdle", "an object", NO_LEVERS.forceIdle)
        val delaySec = if ("delaySec" !in force) {
            0
        } else {
            force.int("delaySec")?.takeIf { it >= 0 } ?: wrong("levers.forceIdle.delaySec", "a whole number of seconds, 0 or more", 0)
        }
        return ForceIdleLever(
            enabled = boolean(force, "enabled", "levers.forceIdle"),
            delaySec = delaySec,
            untilUnlock = "untilUnlock" in force && boolean(force, "untilUnlock", "levers.forceIdle"),
        )
    }

    /** Values as they go into the string; numbers keep their JSON spelling, which the guardrails check. */
    private fun deviceIdle(element: JsonElement?): Map<String, String> {
        val values = element as? JsonObject ?: return if (element == null) emptyMap() else wrong("deviceIdle", "an object", emptyMap())
        val out = linkedMapOf<String, String>()
        for ((key, value) in values) {
            val primitive = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }
            if (primitive == null) wrong("deviceIdle.$key", "a number or true/false", Unit) else out[key] = primitive.content
        }
        return out
    }

    private fun string(obj: JsonObject, field: String): String? = (obj[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun boolean(obj: JsonObject, field: String, parent: String): Boolean =
        (obj[field] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: wrong("$parent.$field", "true or false", false)

    private fun <T> choice(obj: JsonObject, field: String, values: Map<String, T>, placeholder: T): T =
        values[string(obj, field)] ?: wrong("levers.$field", values.keys.joinToString(", ", prefix = "one of "), placeholder)

    private fun <T> wrong(field: String?, detail: String, placeholder: T): T {
        problems += ImportProblem.NotAProfile(field, detail)
        return placeholder
    }

    private companion object {
        val NO_LEVERS = Levers(false, ForceIdleLever(false), SensorLever.OFF, AllowlistLever.INHERIT)
        const val MAX_NAME = 60
    }
}

private val json = Json { prettyPrint = true }

private val SENSORS = mapOf(
    "off" to SensorLever.OFF,
    "restricted" to SensorLever.RESTRICTED,
    "allSensorsOff" to SensorLever.ALL_SENSORS_OFF,
)

private val ALLOWLIST = mapOf("inherit" to AllowlistLever.INHERIT, "trimmed" to AllowlistLever.TRIMMED)

private fun JsonObject.int(field: String): Int? = (this[field] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
