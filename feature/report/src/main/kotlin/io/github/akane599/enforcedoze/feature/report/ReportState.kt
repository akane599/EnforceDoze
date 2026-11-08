// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord

/**
 * One session's report (SPEC 8.4) or a night's (SPEC 8.5), mapped by the app from the evidence log. A number
 * the records do not hold is null and prints as a dash, never as 0.
 */
@Immutable
public data class ReportState(
    val summary: ReportSummary,
    val timetable: List<Stop>,
    /** Null for a session shorter than a night: only nights are compared (SPEC 8.5). */
    val compared: Compared? = null,
    val apps: List<AppWaker> = emptyList(),
    val kernel: List<KernelWaker> = emptyList(),
    val sensors: List<SensorHold> = emptyList(),
    val changes: List<DozeChange> = emptyList(),
    val actions: List<EngineAction> = emptyList(),
    val raw: List<RawGroup> = emptyList(),
    val notices: List<StateNotice> = emptyList(),
    /** The owner already said something arrived late for this night. */
    val lateReported: Boolean = false,
    /** Apps offered by "Something arrived late": messaging and alarm apps first, then this night's wakers. */
    val lateCandidates: List<LateCandidate> = emptyList(),
)

@Immutable
public data class ReportSummary(
    val sessionId: Long,
    val start: Long,
    val end: Long,
    /** A night report rather than a session report: at least 4 h unplugged (SPEC 8.5). */
    val night: Boolean,
    val profile: String?,
    val verdict: VerdictWord,
    val cause: Cause? = null,
    /** Why the session was not judged, as the recorder wrote it. */
    val note: String? = null,
    val lateBy: Long? = null,
    val strip: StripModel,
    val timeInDeep: Long,
    val maintenanceWindows: Int,
    val motionResets: Int,
    val timeToDeep: Long?,
    val deepShare: Double?,
    val lightShare: Double?,
    val activeShare: Double?,
    val drainPerHour: Double?,
    val drainMah: Double?,
    val ownWakeups: Int?,
)

/** One row of the night's timetable, the route line drawn down its side. */
@Immutable
public data class Stop(val time: Long, val event: StopEvent, val due: Long? = null, val late: Long? = null)

public sealed interface StopEvent {
    public data object ScreenOff : StopEvent

    public data object Light : StopEvent

    public data class Deep(val again: Boolean = false) : StopEvent

    public data class Maintenance(val lasted: Long? = null) : StopEvent

    /** Deep idle broken; [subject] names the app, the hold-off or the other manager when one is known. */
    public data class Break(val reason: BreakReason, val subject: String? = null, val awake: Long? = null) : StopEvent

    /** Nothing covered the session from here to [until]: hatched on the strip (ADR 0018). */
    public data class Gap(val until: Long) : StopEvent

    /** The battery history filled the record from here to [until] (ADR 0018). */
    public data class Filled(val until: Long) : StopEvent

    public data object ScreenOn : StopEvent
}

public enum class BreakReason { MOTION, HOLD_OFF, WAKER, ANOTHER_MANAGER, UNKNOWN }

/** This night against the nights before it and against Stock (SPEC 8.5 "Compared with"). */
@Immutable
public data class Compared(
    /** This night first, then up to 7 earlier nights, newest first. */
    val nights: List<ComparedNight>,
    val recent: Baseline,
    val stock: Baseline,
)

@Immutable
public data class ComparedNight(val start: Long, val strip: StripModel, val verdict: VerdictWord, val drainPerHour: Double?)

/** Mean drain and deep-idle share over [nights]; null when no night had the figure. */
@Immutable
public data class Baseline(val nights: Int, val drainPerHour: Double?, val deepShare: Double?)

/** An app that woke the phone; [self] is EnforceDoze, whose expected count is 0 (SPEC 8.2). */
@Immutable
public data class AppWaker(val label: String, val packageName: String, val wakeups: Int, val wakelock: Long, val self: Boolean = false)

/** A kernel wakeup reason as the kernel names it. */
@Immutable
public data class KernelWaker(val reason: String, val count: Int)

/** An app that held a sensor open, at the moments the snapshots saw it (SPEC 8.5). */
@Immutable
public data class SensorHold(
    val label: String,
    val sensor: String,
    /** Sampling rate in hertz, when the sensor service gave a period. */
    val rateHz: Double?,
    val moments: List<SensorMoment>,
)

/** When a sensor snapshot was taken: at screen off, at a deep maintenance window, at screen on (ADR 0020). */
public enum class SensorMoment { SCREEN_OFF, MAINTENANCE, SCREEN_ON }

/** Something that changed Doze in the night (SPEC 8.5 "What changed Doze"). */
public sealed interface DozeChange {
    public val time: Long

    public data class HoldOff(override val time: Long, val name: String, val until: Long?) : DozeChange

    /** A setting found changed and put back; [writer] is the app that wrote it, when known (ADR 0014). */
    public data class Drift(override val time: Long, val key: String, val found: String?, val expected: String?, val writer: String?) :
        DozeChange
}

/** One write or read-back by the engine, as the action log keeps it. */
@Immutable
public data class EngineAction(
    val time: Long,
    val lever: Lever,
    val command: String,
    val readBack: String?,
    val ok: Boolean,
    val detail: String? = null,
)

/** Records exactly as read, under a heading. */
@Immutable
public data class RawGroup(val title: RawTitle, val lines: List<String>)

public enum class RawTitle { TIMELINE, TRANSITIONS, BREAKS, COVERAGE, NOTES }

@Immutable
public data class LateCandidate(val label: String, val packageName: String)

/**
 * What "Something arrived late" did (SPEC 7.5): the recommendation now steps to [stepTo] for 30 days, or
 * stays where it is when the plan is already the gentlest ([stepTo] null).
 */
@Immutable
public data class LateOutcome(val app: LateCandidate?, val stepTo: String?)

/** What the owner can do from the report (SPEC 8.5 item 6). */
public interface ReportActions {
    public fun back()

    public fun export()

    public fun late()

    public fun allowlist()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}
