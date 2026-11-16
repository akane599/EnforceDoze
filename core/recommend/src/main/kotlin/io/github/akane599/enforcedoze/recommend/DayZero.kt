// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.HistoryCommand
import io.github.akane599.enforcedoze.parse.HistoryItem
import java.time.Duration
import java.time.Instant

/**
 * The day-zero history (SPEC 7.1): the screen-off sessions the phone's own records hold before the app has recorded
 * any. The battery history gives plug state, deep idle and battery level; the UsageStats screen events reach further
 * back, with the screen alone. A figure the records do not give stays null.
 *
 * Settings are vouched for as stock only when nothing says otherwise: stock at the read ([Sources.stockNow]) and no
 * session that reached deep idle faster than the stock timers allow, which only forced idle does. A wrong "stock"
 * would mislead D and R; a wrong "unknown" only leaves them unmeasured.
 */
public object DayZero {
    /** A UsageStats event: `SCREEN_INTERACTIVE` ([interactive]) or `SCREEN_NON_INTERACTIVE`. */
    public data class ScreenEvent(val time: Instant, val interactive: Boolean)

    public data class Sources(
        /** `dumpsys batterystats --history-start 0`, or null when it could not be read. */
        val history: BatteryHistory?,
        /** UsageStats screen events, in any order. */
        val screenEvents: List<ScreenEvent>,
        /** No constants string, idle not forced and the sensors normal when the records were read. */
        val stockNow: Boolean,
        /** The walk to deep idle under the settings read now (SPEC 8.4); null when unknown. */
        val expectedToDeep: Duration?,
        /**
         * `inactive_to` plus `idle_after_inactive_to` as read now: no stock walk reaches deep idle sooner after screen
         * off (`stepIdleStateLocked`, android-17.0.0_r1). Null when unknown, and then forced idle cannot be spotted.
         */
        val fastestStockDeep: Duration?,
    )

    /** The sessions found and where the records begin; [from] is the start of the history they cover. */
    public data class Result(val from: Instant, val sessions: List<ScreenOffSession>)

    /** Slack under [Sources.fastestStockDeep] before a deep idle counts as forced: alarms fire a little early. */
    public val FORCED_SLACK: Duration = Duration.ofMinutes(1)

    /** Null when neither source holds anything. */
    public fun read(sources: Sources): Result? {
        val history = sources.history?.takeIf { it.lines.isNotEmpty() }
        val walked = history?.let(::historySessions).orEmpty()
        val historyStart = history?.start?.let(Instant::ofEpochMilli)
        val screenOnly = screenSessions(sources.screenEvents).filter { historyStart == null || it.second <= historyStart }
        val from = listOfNotNull(historyStart, sources.screenEvents.minOfOrNull { it.time }).minOrNull() ?: return null
        val forced = walked.any { forced(it, sources.fastestStockDeep) }
        val provenance = if (sources.stockNow && !forced) Provenance.STOCK else Provenance.UNKNOWN
        val sensors = false.takeIf { provenance == Provenance.STOCK }
        val sessions = screenOnly.map { (start, end) ->
            ScreenOffSession(start, end, plugged = null, provenance = provenance, sensorsRestricted = sensors)
        } + walked.map {
            ScreenOffSession(
                start = it.start,
                end = it.end,
                plugged = it.plugged,
                provenance = provenance,
                sensorsRestricted = sensors,
                expectedToDeep = sources.expectedToDeep.takeIf { provenance == Provenance.STOCK },
                timeInDeep = it.deep,
                motionResets = null,
                batteryDrop = it.drop,
            )
        }
        return Result(from, sessions.sortedBy { it.start })
    }

    /** One screen-off stretch of the battery history. */
    internal data class Walked(
        val start: Instant,
        val end: Instant,
        val plugged: Boolean?,
        val deep: Duration,
        /** When deep idle first began inside it; null when it never did. */
        val firstDeep: Instant?,
        val drop: Double?,
    )

    private fun forced(session: Walked, fastest: Duration?): Boolean {
        fastest ?: return false
        val firstDeep = session.firstDeep ?: return false
        return session.plugged == false && Duration.between(session.start, firstDeep) < fastest - FORCED_SLACK
    }

    /**
     * The battery history's screen-off stretches, `-screen` to `+screen`. Names as the One UI 9 capture prints them
     * (`fixtures/sm-s938b/api37_CP2A.260605.016/batterystats-history.txt`): the `screen` and `plugged` flags, `plug=`
     * (`none` when unplugged) and `device_idle=` (`off`, `light`, `full`; `off` until the first one, as the
     * evidence readers take it). A reboot or lost records drop the stretch they cut, and so does one still open at
     * the read.
     */
    internal fun historySessions(history: BatteryHistory): List<Walked> {
        val walker = Walker()
        for (line in history.lines) walker.line(Instant.ofEpochMilli(line.time), line.level, line.items)
        return walker.found
    }

    /** The state the history has stated so far, and the stretch open in it. */
    private class Walker {
        val found = mutableListOf<Walked>()
        private var screenOn: Boolean? = null
        private var plugged: Boolean? = null
        private var idle = OFF
        private var level: Int? = null
        private var open: Open? = null

        fun line(time: Instant, lineLevel: Int?, items: List<HistoryItem>) {
            open?.advance(time, idle)
            level = lineLevel ?: level
            for (item in items) {
                when (item) {
                    is HistoryItem.Command -> command(item.command)
                    is HistoryItem.Flag -> flag(time, item)
                    is HistoryItem.Value -> value(time, item)
                    is HistoryItem.Event -> Unit
                }
            }
        }

        private fun command(command: HistoryCommand) {
            if (command !in BREAKS) return
            open = null
            // RESET is followed by the full state; a boot or lost records leave the state unknown.
            if (command == HistoryCommand.RESET) return
            screenOn = null
            plugged = null
            idle = OFF
        }

        private fun flag(time: Instant, flag: HistoryItem.Flag) {
            when (flag.name) {
                SCREEN -> screen(time, flag.on)
                PLUGGED -> plug(flag.on)
            }
        }

        private fun value(time: Instant, value: HistoryItem.Value) {
            when (value.name) {
                PLUG -> plug(value.value != NONE)

                DEVICE_IDLE -> {
                    idle = value.value
                    if (idle == FULL) open?.deepAt(time)
                }
            }
        }

        private fun screen(time: Instant, on: Boolean) {
            if (on) {
                open?.let { found += it.close(time, level) }
                open = null
            } else if (screenOn != false) {
                open = Open(time, plugged, level)
            }
            screenOn = on
        }

        private fun plug(on: Boolean) {
            plugged = on
            if (on) open?.plugged()
        }
    }

    /** A screen-off stretch still open: its deep idle so far, and whether a charger was in. */
    private class Open(val start: Instant, plugged: Boolean?, val level: Int?) {
        private var pluggedAny: Boolean? = plugged
        private var last = start
        private var deep = Duration.ZERO
        private var firstDeep: Instant? = null

        fun advance(time: Instant, idle: String) {
            if (idle == FULL) deep += Duration.between(last, time)
            last = time
        }

        fun plugged() {
            pluggedAny = true
        }

        fun deepAt(time: Instant) {
            if (firstDeep == null) firstDeep = time
        }

        fun close(end: Instant, levelNow: Int?): Walked {
            val drop = if (level != null && levelNow != null) (level - levelNow).toDouble() else null
            return Walked(start, end, pluggedAny, deep, firstDeep, drop)
        }
    }

    /** UsageStats pairs: each `SCREEN_NON_INTERACTIVE` to the next `SCREEN_INTERACTIVE`. */
    internal fun screenSessions(events: List<ScreenEvent>): List<Pair<Instant, Instant>> {
        val pairs = mutableListOf<Pair<Instant, Instant>>()
        var off: Instant? = null
        for (event in events.sortedBy { it.time }) {
            if (!event.interactive && off == null) off = event.time
            if (event.interactive) {
                off?.let { pairs += it to event.time }
                off = null
            }
        }
        return pairs
    }

    private const val SCREEN = "screen"
    private const val PLUGGED = "plugged"
    private const val PLUG = "plug"
    private const val NONE = "none"
    private const val DEVICE_IDLE = "device_idle"
    private const val FULL = "full"
    private const val OFF = "off"
    private val BREAKS = setOf(HistoryCommand.RESET, HistoryCommand.START, HistoryCommand.SHUTDOWN, HistoryCommand.OVERFLOW)
}
