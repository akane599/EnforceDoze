// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The profile file (SPEC 6.4): one JSON file per profile, values in milliseconds as numbers. Written for
 * exports (SPEC 8.7); reading one back, with its checks, comes with custom profiles (SPEC 6.3).
 */
public object ProfileFiles {
    private val json = Json { prettyPrint = true }

    public fun encode(profile: Profile): String = json.encodeToString(JsonObject.serializer(), toJson(profile))

    /** A file name for [profile] that no id can turn into a path. */
    public fun fileName(profile: Profile): String = profile.id.replace(UNSAFE, "_") + ".json"

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
            put(
                "sensors",
                when (profile.levers.sensors) {
                    SensorLever.OFF -> "off"
                    SensorLever.RESTRICTED -> "restricted"
                    SensorLever.ALL_SENSORS_OFF -> "allSensorsOff"
                },
            )
            put(
                "allowlist",
                when (profile.levers.allowlist) {
                    AllowlistLever.INHERIT -> "inherit"
                    AllowlistLever.TRIMMED -> "trimmed"
                },
            )
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
