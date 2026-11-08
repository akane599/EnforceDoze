// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeepState
import io.github.akane599.enforcedoze.parse.DozeEvent
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.DozeTag
import io.github.akane599.enforcedoze.parse.EventTime
import io.github.akane599.enforcedoze.parse.HistoryItem
import java.time.Duration
import java.time.Instant

/** One read of the event log and when it was taken: the copy job's reads and the screen-on read (ADR 0018). */
public data class EventLogRead(val log: DozeEventLog, val readAt: Instant)

public enum class TransitionSource { EVENT_LOG, HISTORY }

/**
 * A Doze change as recorded (SPEC 8.1 "Doze transition"). Event-log changes carry the state names of
 * `DeviceIdleController`; history changes carry the `device_idle` value (`full`, `light`, `off`).
 */
public data class Transition(
    val time: Instant,
    val deep: Boolean,
    val from: String?,
    val to: String,
    val reason: String,
    val source: TransitionSource,
)

/** The session's Doze record from every source, with what each covered and what nothing covered. */
public data class DozeRecord(
    /** Deep changes for the verdict: the event log's, and in gaps the history's mapped to states. */
    val deepChanges: List<DeepChange>,
    /** Changes inside the session as recorded. */
    val transitions: List<Transition>,
    /** Parts of the session the event log covered. */
    val eventCoverage: List<Interval>,
    /** Gaps in the event log that the battery history filled. */
    val historyFilled: List<Interval>,
    /** Parts of the session no source covered: the timeline labels them and draws nothing (SPEC 8.2). */
    val gaps: List<Interval>,
)

/** Merges the event-log reads and the battery history into one Doze record (ADR 0018). */
public object DozeSources {
    public const val HISTORY_REASON: String = "history"

    public fun merge(session: Interval, reads: List<EventLogRead>, history: BatteryHistory?): DozeRecord {
        val events = reads.flatMap { it.log.events }.distinctBy { it.raw }
            .mapNotNull { event -> (event.time as? EventTime.Epoch)?.let { Instant.ofEpochMilli(it.millis) to event } }
            .sortedBy { it.first }
        val coverage = union(
            reads.mapNotNull { read ->
                val first = read.log.firstTime as? EventTime.Epoch ?: return@mapNotNull null
                Interval(Instant.ofEpochMilli(first.millis), read.readAt).clip(session)
            },
        )
        val missing = minus(session, coverage).filter { it.duration > Wakers.COVERAGE_SLACK }
        val historyStart = history?.start?.let(Instant::ofEpochMilli)
        val filled = missing.filter { historyStart != null && !historyStart.isAfter(it.start) }
        val values = history?.let(::idleValues).orEmpty()
        val historyChanges = filled.flatMap { gap -> inGap(gap, values) }
        val deep = events.mapNotNull { (time, event) -> event.deepState?.let { DeepChange(time, it, event.reason) } }
        return DozeRecord(
            deepChanges = (deep + historyChanges.map { it.second }).sortedBy { it.time },
            transitions = (eventTransitions(events) + historyChanges.map { it.first })
                .filter { it.time in session }
                .sortedBy { it.time },
            eventCoverage = coverage,
            historyFilled = filled,
            gaps = missing - filled.toSet(),
        )
    }

    private fun eventTransitions(events: List<Pair<Instant, DozeEvent>>): List<Transition> {
        var deep: String? = null
        var light: String? = null
        return events.mapNotNull { (time, event) ->
            when (event.tag) {
                DozeTag.DEEP -> event.deepState?.name?.let { to ->
                    Transition(time, true, deep, to, event.reason, TransitionSource.EVENT_LOG).also { deep = to }
                }

                DozeTag.LIGHT -> event.lightState?.name?.let { to ->
                    Transition(time, false, light, to, event.reason, TransitionSource.EVENT_LOG).also { light = to }
                }

                else -> null
            }
        }
    }

    /** Each `device_idle` value of the history with its time. */
    private fun idleValues(history: BatteryHistory): List<Pair<Instant, String>> = history.lines.mapNotNull { line ->
        line.items.filterIsInstance<HistoryItem.Value>().lastOrNull { it.name == DEVICE_IDLE }
            ?.let { Instant.ofEpochMilli(line.time) to it.value }
    }

    /**
     * The history's changes in [gap], starting with the value in force at its start. `full` is deep
     * IDLE; `off` between two `full` stretches is a maintenance window; anything else is a state short
     * of deep idle that the history does not name.
     */
    private fun inGap(gap: Interval, values: List<Pair<Instant, String>>): List<Pair<Transition, DeepChange>> {
        val before = values.lastOrNull { !it.first.isAfter(gap.start) }
        val inside = values.filter { it.first.isAfter(gap.start) && it.first.isBefore(gap.end) }
        val points = listOf(gap.start to (before?.second ?: OFF)) + inside
        return points.mapIndexed { index, (time, value) ->
            val previous = if (index == 0) null else points[index - 1].second
            val between = previous == FULL && points.drop(index + 1).any { it.second == FULL }
            val state = when {
                value == FULL -> DeepState.IDLE
                between -> DeepState.IDLE_MAINTENANCE
                else -> DeepState.INACTIVE
            }
            Transition(time, true, previous ?: before?.second, value, HISTORY_REASON, TransitionSource.HISTORY) to
                DeepChange(time, state, HISTORY_REASON)
        }
    }

    /** Overlapping or touching intervals joined, in order. */
    internal fun union(intervals: List<Interval>): List<Interval> {
        val joined = mutableListOf<Interval>()
        for (next in intervals.sortedBy { it.start }) {
            val last = joined.lastOrNull()
            if (last != null && !next.start.isAfter(last.end)) {
                joined[joined.lastIndex] = Interval(last.start, maxOf(last.end, next.end))
            } else {
                joined += next
            }
        }
        return joined
    }

    /** The parts of [window] that none of [covered] (joined, in order) covers. */
    internal fun minus(window: Interval, covered: List<Interval>): List<Interval> {
        val missing = mutableListOf<Interval>()
        var from = window.start
        for (span in covered) {
            if (span.start.isAfter(from)) missing += Interval(from, minOf(span.start, window.end))
            from = maxOf(from, span.end)
        }
        if (window.end.isAfter(from)) missing += Interval(from, window.end)
        return missing.filter { it.duration > Duration.ZERO }
    }

    private const val DEVICE_IDLE = "device_idle"
    private const val FULL = "full"
    private const val OFF = "off"
}
