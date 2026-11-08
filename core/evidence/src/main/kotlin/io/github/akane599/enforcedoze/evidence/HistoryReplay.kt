// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.EventPhase
import io.github.akane599.enforcedoze.parse.HistoryCommand
import io.github.akane599.enforcedoze.parse.HistoryItem
import java.time.Instant

/**
 * Walks a battery history in order and pairs its starts and finishes (`BatteryStats.HistoryPrinter`,
 * `BatteryStatsImpl` and `BatteryStatsHistory` at `android-16.0.0_r1` and `android-17.0.0_r1`):
 *
 * - `±job`, `±sync` and `±longwake` pair by uid and tag (`HistoryEventTracker` keeps them balanced).
 * - The `wake_lock` state flag is one stretch while any partial wakelock is held. `+wake_lock=` names
 *   its first holder, an unsigned `wake_lock=` names a more important holder that took over
 *   (`maybeUpdateWakelockTag`), and `-wake_lock` ends the stretch whatever it names (ADR 0019).
 * - A cursor read prints its first record against an empty state, so whatever was on at the cursor
 *   shows as starting there. A finish without its start began before the read.
 * - A boot (`START`) or shutdown ends everything that was open.
 */
internal class HistoryReplay private constructor(private val readStart: Instant) {
    val runs = mutableListOf<Run>()
    val points = mutableListOf<Point>()
    val radio = mutableListOf<Interval>()

    private val open = mutableMapOf<Key, Instant>()
    private var held: Held? = null
    private var radioOn: Instant? = null

    private data class Key(val kind: RunKind, val uid: Int, val tag: String)

    private data class Held(val since: Instant, val uid: Int?, val tag: String)

    private fun line(time: Instant, items: List<HistoryItem>) {
        for (item in items) {
            when (item) {
                is HistoryItem.Event -> event(time, item)
                is HistoryItem.Flag -> flag(time, item)
                is HistoryItem.Command -> if (item.command in ENDS_ALL) closeAll(time)
                is HistoryItem.Value -> Unit
            }
        }
    }

    private fun event(time: Instant, event: HistoryItem.Event) {
        val kind = RUN_EVENTS[event.name]
        when {
            kind != null -> run(time, kind, event)
            event.phase != EventPhase.POINT -> Unit
            event.name == WAKEUP_AP || event.name == WAKE_REASON -> points += Point(event.name, event.uid, event.tag, time)
            event.name == WAKE_LOCK -> held = held?.copy(uid = event.uid, tag = event.tag)
        }
    }

    private fun run(time: Instant, kind: RunKind, event: HistoryItem.Event) {
        val key = Key(kind, event.uid, event.tag)
        when (event.phase) {
            EventPhase.START -> open.putIfAbsent(key, time)
            EventPhase.FINISH -> runs += Run(kind, event.uid, event.tag, Interval(open.remove(key) ?: readStart, time))
            EventPhase.POINT -> Unit
        }
    }

    private fun flag(time: Instant, flag: HistoryItem.Flag) {
        when (flag.name) {
            WAKE_LOCK -> if (flag.on) {
                if (held == null) held = Held(time, flag.uid, flag.tag.orEmpty())
            } else {
                closeWakeLock(time)
            }

            MOBILE_RADIO -> if (flag.on) {
                if (radioOn == null) radioOn = time
            } else {
                radio += Interval(radioOn ?: readStart, time)
                radioOn = null
            }
        }
    }

    private fun closeWakeLock(time: Instant) {
        val stretch = held ?: Held(readStart, null, "")
        runs += Run(RunKind.WAKELOCK, stretch.uid, stretch.tag, Interval(stretch.since, time))
        held = null
    }

    private fun closeAll(time: Instant) {
        open.forEach { (key, since) -> runs += Run(key.kind, key.uid, key.tag, Interval(since, time)) }
        open.clear()
        held?.let { closeWakeLock(time) }
        radioOn?.let { radio += Interval(it, time) }
        radioOn = null
    }

    companion object {
        private const val WAKE_LOCK = "wake_lock"
        private const val MOBILE_RADIO = "mobile_radio"
        private val RUN_EVENTS = mapOf("job" to RunKind.JOB, "sync" to RunKind.SYNC, "longwake" to RunKind.LONG_WAKELOCK)
        private val ENDS_ALL = setOf(HistoryCommand.START, HistoryCommand.SHUTDOWN)

        /** Replays [history]; what is still open at its end was still on at [readAt], when it was read. */
        fun of(history: BatteryHistory, readAt: Instant): HistoryReplay {
            val replay = HistoryReplay(Instant.ofEpochMilli(history.start ?: 0))
            for (line in history.lines) replay.line(Instant.ofEpochMilli(line.time), line.items)
            replay.closeAll(maxOf(readAt, Instant.ofEpochMilli(history.end ?: 0)))
            return replay
        }
    }
}
