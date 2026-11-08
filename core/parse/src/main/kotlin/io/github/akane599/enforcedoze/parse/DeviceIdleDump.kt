// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * `dumpsys deviceidle` as structured data. Section headers (`  Settings:`, `  Flags:`, the allowlist
 * and history lists) keep their lines in [sections]; top-level `name=value` pairs go to [fields]; any
 * other top-level line, such as Samsung's `Doze Allowlist Gatekeeper: false`, is kept in
 * [unknownLines] (SPEC 3.3, 12.4). Parsing never throws.
 */
public data class DeviceIdleDump(
    val flags: Map<String, String>,
    val settings: Map<String, SettingValue>,
    val fields: Map<String, String>,
    val sections: Map<String, List<String>>,
    val unknownLines: List<String>,
) {
    val deepState: DeepState? get() = fields["mState"]?.let(DeepState::fromName)
    val lightState: LightState? get() = fields["mLightState"]?.let(LightState::fromName)
    val forceIdle: Boolean? get() = bool("mForceIdle")
    val deepEnabled: Boolean? get() = bool("mDeepEnabled")
    val lightEnabled: Boolean? get() = bool("mLightEnabled")
    val screenOn: Boolean? get() = bool("mScreenOn")
    val screenLocked: Boolean? get() = bool("mScreenLocked")
    val charging: Boolean? get() = bool("mCharging")
    val useMotionSensor: Boolean? get() = bool("mUseMotionSensor")

    /** The motion sensor description, or null when the device has none (`mMotionSensor=null`). */
    val motionSensor: String? get() = fields["mMotionSensor"]?.takeUnless { it == "null" }

    /**
     * `mInactiveTimeout`: the INACTIVE time the next screen off uses, latched from `inactive_to` when
     * the device last became active (ADR 0016). Printed as `+15s0ms` on API 35–37 (fixtures).
     */
    val inactiveTimeoutMillis: Long? get() = fields["mInactiveTimeout"]?.let(AndroidDuration::parseMillis)

    /** Settings keys this build reads that AOSP does not (One UI adds `skip_sensing`, for example). */
    val extraSettingKeys: Set<String> get() = settings.keys - AospDeviceIdle.KEYS

    /** AOSP keys missing from this build's Settings section. */
    val missingAospKeys: Set<String> get() = AospDeviceIdle.KEYS - settings.keys

    /** False when nothing was recognised, for example a `Permission Denial` message. */
    val isRecognised: Boolean get() = settings.isNotEmpty() || fields.isNotEmpty()

    private fun bool(name: String): Boolean? = fields[name]?.toBooleanStrictOrNull()

    public companion object {
        private const val SETTINGS = "Settings"
        private const val FLAGS = "Flags"
        private val FIELD = Regex("""(?:^|\s)([A-Za-z][\w.]*)=(\{[^}]*\}|\S+)""")

        /** Section headers and top-level fields sit at 2 spaces; section bodies at 4 or as `- ` items. */
        private const val TOP_INDENT = 2
        private const val BODY_INDENT = 4

        private enum class Kind { BODY, SECTION, FIELDS, TEXT }

        private fun kind(inSection: Boolean, indent: Int, body: String): Kind = when {
            inSection && (indent >= BODY_INDENT || (indent >= TOP_INDENT && body.startsWith("- "))) -> Kind.BODY
            indent == TOP_INDENT && body.endsWith(":") && '=' !in body -> Kind.SECTION
            indent >= TOP_INDENT && FIELD.containsMatchIn(body) -> Kind.FIELDS
            else -> Kind.TEXT
        }

        public fun parse(text: String): DeviceIdleDump {
            val sections = linkedMapOf<String, MutableList<String>>()
            val fields = linkedMapOf<String, String>()
            val unknown = mutableListOf<String>()
            var current: MutableList<String>? = null
            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val indent = line.length - line.trimStart().length
                val body = line.trim()
                when (kind(current != null, indent, body)) {
                    Kind.BODY -> current?.add(body)

                    Kind.SECTION -> current = sections.getOrPut(body.removeSuffix(":")) { mutableListOf() }

                    Kind.FIELDS -> {
                        current = null
                        FIELD.findAll(body).forEach { fields[it.groupValues[1]] = it.groupValues[2] }
                    }

                    Kind.TEXT -> {
                        current = null
                        if (AospDeviceIdle.KNOWN_TEXT_LINES.none { it.matches(body) }) unknown += line
                    }
                }
            }
            return DeviceIdleDump(
                flags = keyValues(sections[FLAGS]),
                settings = keyValues(sections[SETTINGS]).mapValues { SettingValue(it.value) },
                fields = fields,
                sections = sections,
                unknownLines = unknown,
            )
        }

        private fun keyValues(lines: List<String>?): Map<String, String> = lines.orEmpty().mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) null else line.substring(0, eq) to line.substring(eq + 1)
        }.toMap(LinkedHashMap())
    }
}
