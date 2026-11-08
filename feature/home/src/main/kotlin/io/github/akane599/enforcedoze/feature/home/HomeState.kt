// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.home

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.evidence.Cause

/** Everything Home shows, mapped by the app from the engine, the evidence log and the privilege layer. */
@Immutable
public data class HomeState(
    val plan: PlanBoard,
    /** Null until the first session is recorded (SPEC 9.3 "no data yet"). */
    val night: NightSummary? = null,
    val notices: List<HomeNotice> = emptyList(),
    val proofRunning: Boolean = false,
)

/** The plan as a departure board: one row per slot, the current one first (SPEC 9.1). */
@Immutable
public data class PlanBoard(val rows: List<BoardRow>, val paused: Boolean = false, val canWrite: Boolean = true)

/** One slot; [from] is null for the slot running now. */
@Immutable
public data class BoardRow(val from: Long?, val profile: String, val until: Until)

public sealed interface Until {
    public data class At(val time: Long) : Until

    /** The night slot ends at the first unlock after [time]. */
    public data class UnlockAfter(val time: Long) : Until

    /** The plan is paused and runs stock until the owner resumes it. */
    public data object Resumed : Until

    /** A single profile with no switch. */
    public data object Always : Until
}

public enum class VerdictWord { HELD, PARTLY, DID_NOT_DOZE, NOT_JUDGED }

/** The last session with its verdict and its four key numbers (SPEC 9.1, 8.4). Unknown numbers are null, never 0. */
@Immutable
public data class NightSummary(
    val sessionId: Long,
    val start: Long,
    val end: Long,
    /** At least the night-report threshold long, so it is "Last night" rather than "Last session". */
    val night: Boolean,
    val verdict: VerdictWord,
    val cause: Cause? = null,
    val breaks: Int = 0,
    val lateBy: Long? = null,
    val timeInDeep: Long,
    val maintenanceWindows: Int,
    val strip: StripModel,
    val timeToDeep: Long?,
    val deepShare: Double?,
    val drainPerHour: Double?,
    val ownWakeups: Int?,
    /** First look since the morning report: the verdict settles into place once. */
    val fresh: Boolean = false,
)

public enum class NoticeKind { READ_ONLY, PRIVILEGE_LOST, PARTLY_APPLIED, ANOTHER_MANAGER, HOLD_OFF_ALL_NIGHT, ERROR }

/**
 * One warning (SPEC 9.3). [subject] is the levers or keys concerned, the other app's name, the hold-off's
 * name, or the error's summary; [raw] is the line that caused an error, shown as it was read.
 */
@Immutable
public data class HomeNotice(
    val id: String,
    val kind: NoticeKind,
    val subject: String? = null,
    val time: Long? = null,
    val raw: String? = null,
    val seen: Boolean = false,
)

/** What the owner can do from Home. */
public interface HomeActions {
    public fun changePlan()

    public fun pause()

    public fun resume()

    public fun openReport(sessionId: Long)

    public fun quickProof()

    public fun notice(notice: HomeNotice)

    public fun seen(notice: HomeNotice)
}
