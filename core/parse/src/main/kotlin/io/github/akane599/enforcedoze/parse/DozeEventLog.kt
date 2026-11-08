// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * Doze event-log tags from AOSP `services/core/java/com/android/server/EventLogTags.logtags`
 * (34000–34010), checked at `android-17.0.0_r1`.
 */
public enum class DozeTag(public val tag: String) {
    /** `34000 device_idle (state|1|5), (reason|3)` */
    DEEP("device_idle"),

    /** `34001 device_idle_step` */
    DEEP_STEP("device_idle_step"),

    /** `34002 device_idle_wake_from_idle (is_idle|1|5), (reason|3)` */
    WAKE_FROM_IDLE("device_idle_wake_from_idle"),

    /** `34003 device_idle_on_start` */
    ON_START("device_idle_on_start"),

    /** `34004 device_idle_on_phase (what|3)` */
    ON_PHASE("device_idle_on_phase"),

    /** `34005 device_idle_on_complete` */
    ON_COMPLETE("device_idle_on_complete"),

    /** `34006 device_idle_off_start (reason|3)` */
    OFF_START("device_idle_off_start"),

    /** `34007 device_idle_off_phase (what|3)` */
    OFF_PHASE("device_idle_off_phase"),

    /** `34008 device_idle_off_complete` */
    OFF_COMPLETE("device_idle_off_complete"),

    /** `34009 device_idle_light (state|1|5), (reason|3)` */
    LIGHT("device_idle_light"),

    /** `34010 device_idle_light_step` */
    LIGHT_STEP("device_idle_light_step"),
    ;

    public companion object {
        public fun fromTag(tag: String): DozeTag? = entries.firstOrNull { it.tag == tag }
    }
}

/** When an event was logged, as far as the log format tells. */
public sealed interface EventTime {
    /** `logcat -v epoch`: wall-clock milliseconds since the Unix epoch. */
    public data class Epoch(val millis: Long) : EventTime

    /** `logcat -v threadtime`: local month, day and time without a year or time zone. */
    public data class Local(val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int, val millis: Int) : EventTime
}

public data class DozeEvent(
    val time: EventTime,
    val tag: DozeTag,
    /** The integer of `device_idle`, `device_idle_light` and `device_idle_wake_from_idle`. */
    val state: Int?,
    /** The reason or phase string, empty when the tag has none. */
    val reason: String,
    val raw: String,
) {
    val deepState: DeepState? get() = if (tag == DozeTag.DEEP) state?.let(DeepState::fromCode) else null
    val lightState: LightState? get() = if (tag == DozeTag.LIGHT) state?.let(LightState::fromCode) else null
}

/**
 * `logcat -b events` output in `epoch` (what the worker requests) or `threadtime` format. Lines with
 * other tags are counted, not kept; `device_idle*` lines that do not parse are kept in [unparsed].
 */
public data class DozeEventLog(
    val events: List<DozeEvent>,
    val unparsed: List<String>,
    val otherLines: Int,
    /** The time of the first log line of any tag: where the read's coverage starts (ADR 0018). */
    val firstTime: EventTime? = null,
) {
    val tagsSeen: Set<DozeTag> get() = events.mapTo(sortedSetOf()) { it.tag }

    public companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private val EPOCH = Regex("""^\s*(\d+)\.(\d{3})\s+\d+\s+\d+\s+[VDIWEF]\s+(\S+?)\s*:\s?(.*)$""")
        private val THREADTIME =
            Regex("""^(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+\d+\s+\d+\s+[VDIWEF]\s+(\S+?)\s*:\s?(.*)$""")
        private val STATE_REASON = Regex("""^\[(-?\d+),(.*)]$""")
        private val WITH_STATE = setOf(DozeTag.DEEP, DozeTag.LIGHT, DozeTag.WAKE_FROM_IDLE)
        private val DOZE_TAG = Regex("""\sdevice_idle\w*\s*:""")

        public fun parse(text: String): DozeEventLog {
            val events = mutableListOf<DozeEvent>()
            val unparsed = mutableListOf<String>()
            var other = 0
            var first: EventTime? = null
            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val stamped = stamped(line)
                if (first == null) first = stamped?.time
                val parsed = stamped?.let(::event)
                when {
                    parsed != null -> events += parsed
                    DOZE_TAG.containsMatchIn(line) -> unparsed += line
                    else -> other++
                }
            }
            return DozeEventLog(events, unparsed, other, first)
        }

        /**
         * What a copy at a maintenance window keeps of a read (ADR 0020): the first timestamped line,
         * which marks where the read's coverage starts, and every `device_idle*` line.
         */
        public fun copyLines(text: String): String {
            var seenStamp = false
            return text.lineSequence().filter { line ->
                val isStamped = stamped(line) != null
                val keep = (isStamped && !seenStamp) || DOZE_TAG.containsMatchIn(line)
                if (isStamped) seenStamp = true
                keep
            }.joinToString("\n")
        }

        /** The time, tag and payload of a log line in either format. */
        // Destructuring names the regex groups, which reads better than indices.
        @Suppress("DestructuringDeclarationWithTooManyEntries")
        private fun stamped(line: String): Stamped? = EPOCH.matchEntire(line)?.destructured?.let { (seconds, millis, tag, body) ->
            Stamped(EventTime.Epoch(seconds.toLong() * MILLIS_PER_SECOND + millis.toLong()), tag, body, line)
        } ?: THREADTIME.matchEntire(line)?.destructured?.let { (month, day, hour, minute, second, millis, tag, body) ->
            val local = EventTime.Local(month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), millis.toInt())
            Stamped(local, tag, body, line)
        }

        private data class Stamped(val time: EventTime, val tag: String, val payload: String, val line: String)

        private fun event(stamped: Stamped): DozeEvent? {
            val tag = DozeTag.fromTag(stamped.tag) ?: return null
            val body = stamped.payload.trim()
            if (tag !in WITH_STATE) return DozeEvent(stamped.time, tag, null, body, stamped.line)
            val match = STATE_REASON.matchEntire(body) ?: return null
            return DozeEvent(stamped.time, tag, match.groupValues[1].toInt(), match.groupValues[2], stamped.line)
        }
    }
}
