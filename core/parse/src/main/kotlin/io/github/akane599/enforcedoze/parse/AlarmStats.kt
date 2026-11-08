// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * One alarm tag of one app: wakeup alarms delivered (`ELAPSED_REALTIME_WAKEUP` or `RTC_WAKEUP`) and
 * all alarms delivered, both counted since boot.
 */
public data class AlarmTagStats(val tag: String, val wakeups: Int, val alarms: Int)

/** One app's line of `Alarm Stats:`, keyed by uid and package as AlarmManagerService keeps them. */
public data class AppAlarmStats(val uid: Int, val packageName: String, val wakeups: Int, val tags: List<AlarmTagStats>) {
    val alarms: Int get() = tags.sumOf { it.alarms }
}

/**
 * The `Alarm Stats:` section of `dumpsys alarm` (ADR 0019). AOSP `AlarmManagerService.dumpImpl`
 * prints it the same at `android-16.0.0_r1` and `android-17.0.0_r1`; the counters are those
 * `deliverLocked` raises (`BroadcastStats.numWakeup`, `FilterStats.numWakeup`, `count`) and only
 * grow until reboot, so a session's alarms are the difference between two reads.
 */
public data class AlarmStats(
    /** False when the dump has no `Alarm Stats:` header. */
    val found: Boolean,
    val apps: List<AppAlarmStats>,
    /** Lines inside the section that matched neither layout. */
    val unparsed: List<String>,
) {
    public companion object {
        private const val HEADER = "Alarm Stats:"

        /** `[*ACTIVE* ]u0a357:com.example +2s120ms running, 1 wakeups:` */
        private val APP =
            Regex("""^\s*(?:\*ACTIVE\* )?(?<uid>${FormattedUid.PATTERN}):(?<pkg>\S+) \S+ running, (?<wakeups>\d+) wakeups:$""")

        /** `[*ACTIVE* ]+419ms 15 wakes 15 alarms, last -1m2s308ms:`, followed by the tag on its own line. */
        private val TAG = Regex("""^\s*(?:\*ACTIVE\* )?\S+ (?<wakes>\d+) wakes (?<alarms>\d+) alarms, last .*:$""")

        public fun parse(text: String): AlarmStats {
            val lines = text.lineSequence().dropWhile { it.trim() != HEADER }.toList()
            if (lines.isEmpty()) return AlarmStats(found = false, apps = emptyList(), unparsed = emptyList())
            // The section ends with the blank line AlarmManagerService prints after it.
            val section = lines.drop(1).takeWhile { it.isNotBlank() }
            val apps = mutableListOf<AppAlarmStats>()
            val unparsed = mutableListOf<String>()
            var index = 0
            while (index < section.size) {
                val line = section[index]
                val app = APP.matchEntire(line)?.let(::appStats)
                val tag = if (app == null) TAG.matchEntire(line)?.let(::counts) else null
                val tagName = section.getOrNull(index + 1)?.trim()
                when {
                    app != null -> apps += app

                    tag != null && apps.isNotEmpty() && tagName != null -> {
                        val last = apps.removeAt(apps.lastIndex)
                        apps += last.copy(tags = last.tags + AlarmTagStats(tagName, tag.first, tag.second))
                        index++
                    }

                    else -> unparsed += line
                }
                index++
            }
            return AlarmStats(found = true, apps = apps, unparsed = unparsed)
        }

        private fun appStats(match: MatchResult): AppAlarmStats? {
            val uid = FormattedUid.parse(match.group("uid")) ?: return null
            val wakeups = match.group("wakeups").toIntOrNull() ?: return null
            return AppAlarmStats(uid, match.group("pkg"), wakeups, tags = emptyList())
        }

        /** Wakeups and alarms of a tag line. */
        private fun counts(match: MatchResult): Pair<Int, Int>? {
            val wakes = match.group("wakes").toIntOrNull() ?: return null
            return match.group("alarms").toIntOrNull()?.let { wakes to it }
        }

        private fun MatchResult.group(name: String): String = groups[name]?.value.orEmpty()
    }
}
