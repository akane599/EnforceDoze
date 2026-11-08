// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** How `dumpsys batterystats --history` stamps its lines (ADR 0018). */
public enum class HistoryFormat {
    /** API 36+: `[Format: 2]`, wall time in the device time zone, `MM-dd HH:mm:ss.SSS`. */
    FORMAT_2,

    /** API ≤ 35: an offset from the first record (`+1s147ms`), anchored by the `TIME:` lines. */
    LEGACY,

    /** No header was seen; lines are read in whichever layout they match. */
    UNKNOWN,
}

/** A command record: `RESET:TIME:`, `TIME:`, `START`, `SHUTDOWN` or `*OVERFLOW*`. */
public enum class HistoryCommand { RESET, TIME, START, SHUTDOWN, OVERFLOW }

/** Whether an event starts (`+job=`), finishes (`-job=`) or happens at one moment (`wakeupap=`). */
public enum class EventPhase { START, FINISH, POINT }

/** One item of a history line, as `BatteryStats.HistoryPrinter` prints it on API 35–37. */
public sealed interface HistoryItem {
    /** `+screen`, `-mobile_radio`; `+wake_lock=u0a1:"tag"` carries the first holder in [uid] and [tag]. */
    public data class Flag(val name: String, val on: Boolean, val uid: Int? = null, val tag: String? = null) : HistoryItem

    /** `device_idle=light`, `charge=4880`, `plug=none`. */
    public data class Value(val name: String, val value: String) : HistoryItem

    /** `+job=u0a1:"pkg/.Svc"`, `wakeupap=1000:""`, `wake_reason=0:"…"`. */
    public data class Event(val name: String, val phase: EventPhase, val uid: Int, val tag: String) : HistoryItem

    /** A command record; [wallTime] for `TIME:` and `RESET:TIME:`. */
    public data class Command(val command: HistoryCommand, val wallTime: Long?) : HistoryItem
}

/** One record: when (epoch milliseconds), the battery level when printed, and its items. */
public data class HistoryLine(val time: Long, val level: Int?, val items: List<HistoryItem>)

/**
 * `dumpsys batterystats --history` or `--history-start <cursor>` (ADR 0018). Times are resolved to
 * epoch milliseconds with the device [ZoneId]: Format 2 lines carry wall time without a year, which
 * comes from the nearest `TIME:` line; legacy lines carry offsets, anchored by the same lines.
 */
public data class BatteryHistory(
    val format: HistoryFormat,
    val lines: List<HistoryLine>,
    /** The `NEXT:` cursor a `--history-start` read ends with, in the history's own clock; null otherwise. */
    val next: Long?,
    /** Lines that look like records but did not parse, or came before any time anchor. */
    val unparsed: List<String>,
) {
    public val start: Long? get() = lines.firstOrNull()?.time
    public val end: Long? get() = lines.lastOrNull()?.time

    public companion object {
        private val HEADER = Regex("""^Battery History(?: \[Format: (\d+)])? \(""")
        private val NEXT = Regex("""^\s*NEXT: (\d+)\s*$""")
        private val FORMAT_2_LINE =
            Regex("""^\s*(?<mo>\d{2})-(?<d>\d{2}) (?<h>\d{2}):(?<mi>\d{2}):(?<s>\d{2})\.(?<ms>\d{3}) (?<body>.*)$""")
        private val LEGACY_LINE = Regex("""^\s*(?<offset>0|[+-][\dhdms]+) \(\d+\) (?<body>.*)$""")
        private val TIME =
            Regex(
                """^(?<reset>RESET:)?TIME: (?<y>\d{4})-(?<mo>\d{2})-(?<d>\d{2})-(?<h>\d{2})-(?<mi>\d{2})-(?<s>\d{2})(?:-(?<ms>\d{3}))?$""",
            )
        private val LEVEL = Regex("""^(\d{3})(?: (.*))?$""")
        private val TAGGED = Regex("""^(?<sign>[+-]?)(?<name>[A-Za-z_]\w*)=(?<uid>${FormattedUid.PATTERN}):"(?<tag>.*)"$""")
        private val FLAG = Regex("""^([+-])([A-Za-z_]\w*)$""")
        private val VALUE = Regex("""^([A-Za-z_]\w*)=(.*)$""")
        private val COMMANDS = mapOf(
            "START" to HistoryCommand.START,
            "SHUTDOWN" to HistoryCommand.SHUTDOWN,
            "*OVERFLOW*" to HistoryCommand.OVERFLOW,
        )

        /** Parses [text]; [fallbackYear] dates Format 2 lines printed before any `TIME:` line. */
        public fun parse(text: String, zone: ZoneId, fallbackYear: Int): BatteryHistory {
            val reader = Reader(zone, fallbackYear)
            for (raw in text.lineSequence()) reader.read(raw)
            return reader.result()
        }

        /** Splits on spaces outside quotes; a doubled quote inside a quoted tag is part of the tag. */
        internal fun tokens(body: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '"' && quoted && body.getOrNull(i + 1) == '"' -> {
                        current.append("\"\"")
                        i++
                    }

                    c == '"' -> {
                        quoted = !quoted
                        current.append(c)
                    }

                    c == ' ' && !quoted -> {
                        if (current.isNotEmpty()) out += current.toString()
                        current.clear()
                    }

                    else -> current.append(c)
                }
                i++
            }
            if (current.isNotEmpty()) out += current.toString()
            return out
        }

        internal fun item(token: String): HistoryItem? {
            val tagged = TAGGED.matchEntire(token)
            val flag = if (tagged == null) FLAG.matchEntire(token) else null
            val value = if (tagged == null && flag == null) VALUE.matchEntire(token) else null
            return when {
                tagged != null -> tagged(tagged)
                flag != null -> HistoryItem.Flag(flag.groupValues[2], flag.groupValues[1] == "+")
                value != null -> HistoryItem.Value(value.groupValues[1], value.groupValues[2])
                else -> null
            }
        }

        /** `±name=uid:"tag"`; a signed `wake_lock` is the wake-lock state with its first holder. */
        private fun tagged(match: MatchResult): HistoryItem? {
            val sign = match.text("sign")
            val name = match.text("name")
            val uid = FormattedUid.parse(match.text("uid")) ?: return null
            val tag = match.text("tag").replace("\"\"", "\"")
            return when {
                name == "wake_lock" && sign.isNotEmpty() -> HistoryItem.Flag(name, sign == "+", uid, tag)
                else -> HistoryItem.Event(name, phase(sign), uid, tag)
            }
        }

        private fun phase(sign: String) = when (sign) {
            "+" -> EventPhase.START
            "-" -> EventPhase.FINISH
            else -> EventPhase.POINT
        }

        /** The body after the time stamp: a command record, or a level followed by items. */
        internal fun body(body: String): Pair<Int?, List<HistoryItem>>? {
            val trimmed = body.trim()
            COMMANDS[trimmed]?.let { return null to listOf(HistoryItem.Command(it, null)) }
            val level = LEVEL.matchEntire(trimmed) ?: return null
            val items = tokens(level.groupValues[2]).map(::item)
            return if (null in items) null else level.groupValues[1].toInt() to items.filterNotNull()
        }

        /** Reads one history in order and keeps the time anchor the next lines need. */
        private class Reader(private val zone: ZoneId, fallbackYear: Int) {
            private var format = HistoryFormat.UNKNOWN
            private val lines = mutableListOf<HistoryLine>()
            private val unparsed = mutableListOf<String>()
            private var next: Long? = null

            /** The local date and time of the last `TIME:` line, for the year of Format 2 lines. */
            private var anchorLocal: LocalDateTime = LocalDateTime.of(fallbackYear, 1, 1, 0, 0)

            /** Legacy: the offset and the epoch milliseconds of the last `TIME:` line. */
            private var anchorOffset: Long? = null
            private var anchorEpoch: Long = 0

            fun result() = BatteryHistory(format, lines, next, unparsed)

            fun read(raw: String) {
                if (raw.isBlank()) return
                val header = HEADER.find(raw)
                val next = if (header == null) NEXT.matchEntire(raw) else null
                val format2 = if (header == null && next == null) FORMAT_2_LINE.matchEntire(raw) else null
                val legacy = if (header == null && next == null && format2 == null) LEGACY_LINE.matchEntire(raw) else null
                when {
                    header != null -> format = if (header.groupValues[1].isEmpty()) HistoryFormat.LEGACY else HistoryFormat.FORMAT_2

                    next != null -> this.next = next.groupValues[1].toLong()

                    format2 != null -> format2(raw, format2)

                    legacy != null -> legacy(raw, legacy)

                    // Lines that are not records (step details, vendor sections) are not part of the history.
                    else -> Unit
                }
            }

            private fun format2(raw: String, match: MatchResult) {
                val body = match.text("body")
                val time = TIME.matchEntire(body.trim())
                if (time != null) {
                    val local = local(time)
                    anchorLocal = local
                    val epoch = epoch(local)
                    lines += HistoryLine(epoch(stamp(match)), null, listOf(HistoryItem.Command(command(time), epoch)))
                    return
                }
                val parsed = body(body)
                if (parsed == null) unparsed += raw else lines += HistoryLine(epoch(stamp(match)), parsed.first, parsed.second)
            }

            private fun legacy(raw: String, match: MatchResult) {
                val offset = AndroidDuration.parseMillis(match.text("offset"))
                val body = match.text("body")
                val time = TIME.matchEntire(body.trim())
                if (offset != null && time != null) {
                    anchorEpoch = epoch(local(time))
                    anchorOffset = offset
                    lines += HistoryLine(anchorEpoch, null, listOf(HistoryItem.Command(command(time), anchorEpoch)))
                    return
                }
                val base = anchorOffset
                val parsed = body(body)
                if (offset == null || base == null || parsed == null) {
                    unparsed += raw
                } else {
                    lines += HistoryLine(anchorEpoch + (offset - base), parsed.first, parsed.second)
                }
            }

            /** The year of a Format 2 stamp: whichever of the anchor's year and its neighbours lies nearest. */
            private fun stamp(match: MatchResult): LocalDateTime {
                val year = anchorLocal.year
                return listOf(year - 1, year, year + 1)
                    .mapNotNull { y -> runCatching { local(match, y) }.getOrNull() }
                    .minBy { abs(ChronoUnit.MINUTES.between(anchorLocal, it)) }
            }

            private fun epoch(local: LocalDateTime): Long = local.atZone(zone).toInstant().toEpochMilli()
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000

private fun MatchResult.text(group: String): String = groups[group]?.value.orEmpty()

private fun MatchResult.int(group: String): Int = text(group).toIntOrNull() ?: 0

private fun command(time: MatchResult) = if (time.text("reset").isEmpty()) HistoryCommand.TIME else HistoryCommand.RESET

private fun local(time: MatchResult, year: Int = time.int("y")): LocalDateTime = LocalDateTime.of(
    year,
    time.int("mo"),
    time.int("d"),
    time.int("h"),
    time.int("mi"),
    time.int("s"),
    time.int("ms") * NANOS_PER_MILLI,
)
