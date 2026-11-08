// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.DeepState
import io.github.akane599.enforcedoze.parse.DozeEventLog
import io.github.akane599.enforcedoze.parse.EventTime
import java.time.Duration
import java.time.Instant

public data class Interval(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end).coerceAtLeast(Duration.ZERO)

    public operator fun contains(time: Instant): Boolean = !time.isBefore(start) && time.isBefore(end)

    public fun overlap(other: Interval): Duration {
        val from = maxOf(start, other.start)
        val to = minOf(end, other.end)
        return if (to.isAfter(from)) Duration.between(from, to) else Duration.ZERO
    }
}

/** True when the span overlaps [window], or is an instant inside it. */
internal fun Interval.touches(window: Interval): Boolean = overlap(window) > Duration.ZERO || start in window

internal fun Interval.clip(window: Interval): Interval? =
    Interval(maxOf(start, window.start), minOf(end, window.end)).takeIf { it.duration > Duration.ZERO }

internal fun List<Interval>.sumOf(window: Interval): Duration = fold(Duration.ZERO) { sum, span -> sum + span.overlap(window) }

/** One `device_idle` change: the deep state entered and the reason the system logged (`motion`, `s:alarm`, …). */
public data class DeepChange(val time: Instant, val state: DeepState, val reason: String) {
    public companion object {
        /** The deep changes of [log] that carry a wall-clock time (`logcat -v epoch`, what the worker asks for). */
        public fun from(log: DozeEventLog): List<DeepChange> = log.events.mapNotNull { event ->
            val time = event.time as? EventTime.Epoch ?: return@mapNotNull null
            val state = event.deepState ?: return@mapNotNull null
            DeepChange(Instant.ofEpochMilli(time.millis), state, event.reason)
        }
    }
}

/** One deep state held over [interval]. */
public data class DeepStretch(val state: DeepState, val interval: Interval)

/** A hold-off that was active during [interval] (SPEC 5.5), by name. */
public data class HoldOffSpan(val name: String, val interval: Interval)

/** What a session's records say (SPEC 8.1); the verdict comes from these alone (SPEC 8.4). */
public data class SessionInput(
    /** Screen off. */
    val start: Instant,
    /** Screen on. */
    val end: Instant,
    /** The walk to deep idle with the effective timers, or zero when the profile forces idle. */
    val expectedToDeep: Duration,
    /** Deep changes, sorted or not; those before [start] set the state the session began in. */
    val changes: List<DeepChange>,
    /** Charging spans, not overlapping one another. */
    val plugged: List<Interval> = emptyList(),
    val holdOffs: List<HoldOffSpan> = emptyList(),
    /** Drift events: another app wrote Doze settings (SPEC 5.6). */
    val foreignWrites: List<Instant> = emptyList(),
    /** The profile's constants read back as written for this session. */
    val constantsApplied: Boolean = true,
    /** Shorter sessions get no verdict: 10 minutes, shortened only by the compressed-night test (SPEC 13.2). */
    val minimum: Duration = Verdicts.MINIMUM,
)

/** Why deep idle was left or never reached (SPEC 8.4). Wakers join in P3. */
public enum class Cause { CHARGING, HOLD_OFF, MOTION, CONSTANTS_NOT_APPLIED, ANOTHER_MANAGER, UNKNOWN }

/** Deep idle left for [state] before the screen came on. */
public data class Break(val time: Instant, val state: DeepState, val reason: String, val cause: Cause)

public sealed interface Verdict {
    public data object Held : Verdict

    /** Deep reached, but late by [lateBy] or broken by [breaks], so under 80% of the time after it. */
    public data class Partly(val breaks: List<Break>, val lateBy: Duration?) : Verdict

    public data class DidNotDoze(val cause: Cause) : Verdict
}

public data class SessionResult(
    /** Null for a session shorter than [SessionInput.minimum]. */
    val verdict: Verdict?,
    val timeToDeep: Duration?,
    /** Share of the unplugged time after deep was reached spent in deep idle or its maintenance windows. */
    val deepShare: Double,
    val timeInDeep: Duration,
    val maintenanceWindows: Int,
    val motionResets: Int,
    val breaks: List<Break>,
)

/** The session verdict of SPEC 8.4. */
public object Verdicts {
    public val MINIMUM: Duration = Duration.ofMinutes(10)

    /** Allowance on the expected time to deep idle (SPEC 8.4). */
    public val GRACE: Duration = Duration.ofMinutes(2)

    public const val HELD_SHARE: Double = 0.8

    /** Changes this close to screen on belong to the screen coming on, not to a break. */
    private val END_SLACK: Duration = Duration.ofSeconds(5)

    /** A drift this long before a break is taken as its cause. */
    private val FOREIGN_WINDOW: Duration = Duration.ofMinutes(1)

    private val DEEP = setOf(DeepState.IDLE, DeepState.IDLE_MAINTENANCE)

    public fun judge(input: SessionInput): SessionResult {
        val session = Interval(input.start, input.end)
        val initial = initialState(input)
        val segments = segments(input, initial)
        val firstDeep = segments.firstOrNull { it.state == DeepState.IDLE }?.interval?.start
        val after = firstDeep?.let { Interval(it, input.end) }
        val unplugged = after?.let { unpluggedTime(it, input.plugged) } ?: Duration.ZERO
        val inDeep = after?.let { window ->
            segments.filter { it.state in DEEP }.fold(Duration.ZERO) { sum, segment ->
                sum + unpluggedTime(Interval(maxOf(segment.interval.start, window.start), segment.interval.end), input.plugged)
            }
        } ?: Duration.ZERO
        val share = if (unplugged.isZero) (if (firstDeep != null) 1.0 else 0.0) else inDeep.toMillis().toDouble() / unplugged.toMillis()
        val inSession = inSession(input)
        val breaks = breaks(input, initial)
        val timeToDeep = firstDeep?.let { Duration.between(input.start, it) }
        val result = SessionResult(
            verdict = null,
            timeToDeep = timeToDeep,
            deepShare = share,
            timeInDeep = inDeep,
            maintenanceWindows = inSession.count { it.state == DeepState.IDLE_MAINTENANCE },
            motionResets = inSession.count { it.reason == MOTION },
            breaks = breaks,
        )
        if (session.duration <= input.minimum) return result
        val verdict = when {
            timeToDeep == null -> Verdict.DidNotDoze(missedCause(input, session))
            timeToDeep <= input.expectedToDeep + GRACE && share >= HELD_SHARE -> Verdict.Held
            else -> Verdict.Partly(breaks, (timeToDeep - input.expectedToDeep - GRACE).takeIf { it > Duration.ZERO })
        }
        return result.copy(verdict = verdict)
    }

    /** The deep state over the session: what the timeline draws, and where the app's own runs count as wakeups (ADR 0018). */
    public fun timeline(input: SessionInput): List<DeepStretch> = segments(input, initialState(input))

    private fun initialState(input: SessionInput): DeepState =
        input.changes.filter { !it.time.isAfter(input.start) }.maxByOrNull { it.time }?.state ?: DeepState.ACTIVE

    /** Changes after screen off and before screen on, in log order. */
    private fun inSession(input: SessionInput): List<DeepChange> =
        input.changes.filter { it.time.isAfter(input.start) && it.time.isBefore(input.end) }.sortedBy { it.time }

    /** The deep state over the session, from the last change before it began (active when none is known). */
    private fun segments(input: SessionInput, initial: DeepState): List<DeepStretch> {
        val points = listOf(input.start to initial) + inSession(input).map { it.time to it.state }
        return points.mapIndexed { index, (time, state) ->
            DeepStretch(state, Interval(time, points.getOrNull(index + 1)?.first ?: input.end))
        }.filter { it.interval.duration > Duration.ZERO }
    }

    /** Each step out of deep idle before the screen came on; states logged in the same instant are walked in order. */
    private fun breaks(input: SessionInput, initial: DeepState): List<Break> {
        val cutoff = input.end - END_SLACK
        var state = initial
        val breaks = mutableListOf<Break>()
        for (change in inSession(input).filter { it.time.isBefore(cutoff) }) {
            if (state in DEEP && change.state !in DEEP) breaks += Break(change.time, change.state, change.reason, cause(input, change))
            state = change.state
        }
        return breaks
    }

    private fun cause(input: SessionInput, change: DeepChange): Cause = when {
        change.reason == MOTION -> Cause.MOTION
        change.reason == CHARGING || input.plugged.any { change.time in it } -> Cause.CHARGING
        input.holdOffs.any { change.time in it.interval || it.interval.start == change.time } -> Cause.HOLD_OFF
        input.foreignWrites.any { !it.isAfter(change.time) && Duration.between(it, change.time) <= FOREIGN_WINDOW } -> Cause.ANOTHER_MANAGER
        else -> Cause.UNKNOWN
    }

    /**
     * SPEC 8.4's order: charging, hold-off, motion, constants not applied, another manager, unknown.
     * Charging (and a hold-off on top of it) explains the miss when it left no stretch long enough
     * for the walk to deep idle; the engine treats charging as a hold-off too.
     */
    private fun missedCause(input: SessionInput, session: Interval): Cause {
        val needed = input.expectedToDeep + GRACE
        return when {
            longestFree(session, input.plugged) < needed -> Cause.CHARGING
            longestFree(session, input.plugged + input.holdOffs.map { it.interval }) < needed -> Cause.HOLD_OFF
            inSession(input).any { it.reason == MOTION } -> Cause.MOTION
            !input.constantsApplied -> Cause.CONSTANTS_NOT_APPLIED
            input.foreignWrites.any { it in session } -> Cause.ANOTHER_MANAGER
            else -> Cause.UNKNOWN
        }
    }

    /** The longest part of [window] that none of [blocked] covers. */
    private fun longestFree(window: Interval, blocked: List<Interval>): Duration {
        var longest = Duration.ZERO
        var free = window.start
        for (block in blocked.sortedBy { it.start }) {
            if (block.start.isAfter(free)) longest = maxOf(longest, Duration.between(free, minOf(block.start, window.end)))
            if (block.end.isAfter(free)) free = block.end
            if (!free.isBefore(window.end)) return longest
        }
        return maxOf(longest, Duration.between(free, window.end))
    }

    private fun unpluggedTime(window: Interval, plugged: List<Interval>): Duration = window.duration - plugged.sumOf(window)

    /** `DeviceIdleController` logs these reasons with `device_idle` (captured on API 35–37 and One UI 9). */
    private const val MOTION = "motion"
    private const val CHARGING = "charging"
}
