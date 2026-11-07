// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/** Operating mode printed by `dumpsys sensorservice` as `Mode : NORMAL` or `Mode : RESTRICTED : <pkg>`. */
public enum class SensorMode { NORMAL, RESTRICTED, OTHER }

/**
 * The parts of `dumpsys sensorservice` the engine reads back: the mode line (lever C, SPEC 5.4) and
 * the `Sensor Privacy: enabled|disabled` line. Lines are as captured on API 35–37 and One UI 9.
 */
public data class SensorServiceDump(
    val modeLine: String?,
    val mode: SensorMode?,
    val modeName: String?,
    val modePackage: String?,
    val sensorPrivacy: Boolean?,
) {
    public companion object {
        private val MODE = Regex("""^Mode\s*:\s*([A-Z_]+)(?:\s*:\s*(\S+))?\s*$""")
        private val PRIVACY = Regex("""^Sensor Privacy\s*:\s*(enabled|disabled)\s*$""")

        public fun parse(text: String): SensorServiceDump {
            var modeLine: String? = null
            var modeName: String? = null
            var modePackage: String? = null
            var privacy: Boolean? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (modeLine == null) {
                    MODE.matchEntire(line)?.let {
                        modeLine = line
                        modeName = it.groupValues[1]
                        modePackage = it.groupValues[2].ifEmpty { null }
                    }
                }
                if (privacy == null) PRIVACY.matchEntire(line)?.let { privacy = it.groupValues[1] == "enabled" }
            }
            val mode = when (modeName) {
                null -> null
                "NORMAL" -> SensorMode.NORMAL
                "RESTRICTED" -> SensorMode.RESTRICTED
                else -> SensorMode.OTHER
            }
            return SensorServiceDump(modeLine, mode, modeName, modePackage, privacy)
        }
    }
}
