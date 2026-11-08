// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * The `Settings.Global.device_idle_constants` string, lever A's write path (ADR 0012).
 *
 * AOSP `DeviceIdleController.Constants` (API 36 and 37) reads it with
 * `UserSettingDeviceConfigMediator.SettingsOverridesIndividualMediator(',')`: a key in this string
 * overrides the same key in `device_config device_idle`, key by key, and the whole string is
 * re-parsed on every change, so a key left out falls back to `device_config` or the default.
 * Parsing is `android.util.KeyValueListParser`: pairs split on `,`, key and value split on the
 * first `=` and trimmed, the last duplicate wins, and a pair without `=` makes the whole string
 * invalid (every key falls back). Durations are parsed with `Long.parseLong`, factors with
 * `Float.parseFloat`, flags with `Boolean.parseBoolean`.
 */
public object DeviceIdleSettings {
    /** The global setting's name (`Settings.Global.DEVICE_IDLE_CONSTANTS`, hidden). */
    public const val SETTING: String = "device_idle_constants"

    /** `device_idle` keys are lower snake case (AOSP `DeviceIdleController.Constants`, One UI's extras). */
    public val KEY: Regex = Regex("""[a-z][a-z0-9_]{0,63}""")

    /** Milliseconds, factors and flags: what the parser accepts, nothing a shell could misread. */
    public val VALUE: Regex = Regex("""-?\d{1,13}(\.\d{1,6})?|true|false""")

    /** At most this many keys in one string; AOSP and One UI together read under 40. */
    public const val MAX_KEYS: Int = 64

    /** The string as `DeviceIdleController` would read it. */
    public data class Parsed(val values: Map<String, String>, val valid: Boolean)

    /** Builds the string in the map's order. Callers check [problems] first; this throws on bad input. */
    public fun encode(values: Map<String, String>): String {
        val bad = problems(values)
        require(bad.isEmpty()) { bad.joinToString("; ") }
        return values.entries.joinToString(",") { (key, value) -> "$key=$value" }
    }

    /** Why [values] cannot be written, or an empty list. An empty map is not written: clear the setting instead. */
    public fun problems(values: Map<*, *>): List<String> {
        if (values.isEmpty()) return listOf("no constants")
        if (values.size > MAX_KEYS) return listOf("too many constants: ${values.size}")
        return values.entries.mapNotNull { (key, value) ->
            when {
                key !is String || !KEY.matches(key) -> "invalid key: $key"
                value !is String || !VALUE.matches(value) -> "invalid value for $key: $value"
                else -> null
            }
        }
    }

    /**
     * Checks a complete string before it is written: every pair valid, no empty pair, no duplicate.
     * Returns the problems, or an empty list.
     */
    public fun problems(text: String): List<String> {
        if (text.isEmpty()) return listOf("empty string")
        val pairs = text.split(',')
        if (pairs.size > MAX_KEYS) return listOf("too many constants: ${pairs.size}")
        val seen = mutableSetOf<String>()
        return pairs.mapNotNull { pair ->
            val eq = pair.indexOf('=')
            val key = if (eq < 0) pair else pair.substring(0, eq)
            val value = if (eq < 0) null else pair.substring(eq + 1)
            when {
                value == null -> "not a key=value pair: $pair"
                !KEY.matches(key) -> "invalid key: $key"
                !VALUE.matches(value) -> "invalid value for $key: $value"
                !seen.add(key) -> "duplicate key: $key"
                else -> null
            }
        }
    }

    /**
     * Reads a stored string the way `KeyValueListParser` does. Null, empty and `settings get`'s
     * literal `null` all mean "not set".
     */
    public fun parse(text: String?): Parsed {
        if (text == null || text.isEmpty() || text == "null") return Parsed(emptyMap(), valid = true)
        val values = linkedMapOf<String, String>()
        // KeyValueListParser uses TextUtils.SimpleStringSplitter, which yields no segment after a trailing comma.
        for (pair in text.removeSuffix(",").split(',')) {
            val eq = pair.indexOf('=')
            if (eq < 0) return Parsed(emptyMap(), valid = false)
            values[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
        }
        return Parsed(values, valid = true)
    }
}
