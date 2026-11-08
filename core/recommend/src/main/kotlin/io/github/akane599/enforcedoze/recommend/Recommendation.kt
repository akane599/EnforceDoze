// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.Plan
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** SPEC 7.4 with ADR 0026: High also needs D and R measured under stock settings. */
public enum class Confidence { LOW, MEDIUM, HIGH }

/** One line of the "why" card (SPEC 7.4), with its numbers; the UI words it. Strongest first. */
public sealed interface Reason {
    /** Rule 1: too little screen-off time to say more yet. */
    public data class ThinHistory(val screenOff: Duration) : Reason

    /** Signal S, the context for every other line. */
    public data class ScreenOffPerDay(val hours: Double) : Reason

    /** Signal M: movement kept the phone out of deep idle in [sessions] of [of] long sessions. */
    public data class MotionKeptAwake(val sessions: Int, val of: Int) : Reason

    /** Signal M: motion resets per screen-off hour. */
    public data class MotionResets(val perHour: Double) : Reason

    /** Signal S: share of daytime screen-off time in short sessions, with Q = It can wait. */
    public data class ShortSessions(val share: Double) : Reason

    /** Q = Right away kept the plan at Everyday or Pocket and without Night. */
    public data class MessagesRightAway(val dropped: List<String>) : Reason

    /** Signal N: [nights] of [of] available nights were long and unplugged; the window learned from them. */
    public data class UnpluggedNights(val nights: Int, val of: Int, val start: LocalTime, val end: LocalTime) : Reason

    /** Rule 4: the phone already dozes well under stock settings. */
    public data class DozingWell(val drainPerHour: Double, val deepShare: Double) : Reason

    /** SPEC 7.5: the last [nights] nights on the plan saved only [gain] against stock. */
    public data class LowGain(val gain: Double, val nights: Int) : Reason

    /** SPEC 7.5: the owner said something arrived late. */
    public data class ArrivedLate(val time: Instant) : Reason
}

/** Short notes beside the plan (SPEC 7.3, 7.4). */
public enum class Note {
    /** Rule 1: "refines after the first night". */
    REFINES_AFTER_FIRST_NIGHT,

    /** Rule 4: "little to gain here". */
    LITTLE_TO_GAIN,

    /** D or R could not be measured: no stock time in the history (ADR 0026); one Stock night would measure them. */
    STOCK_NOT_MEASURED,

    /** Feedback holds the plan gentler than the signals alone suggest (SPEC 7.5, ADR 0026). */
    GENTLER,

    /** Pocket stays: its own sensor restriction hides movement, so M cannot be measured while it runs (ADR 0026). */
    MOTION_NOT_MEASURED,
}

public data class Recommendation(
    val plan: Plan,
    val confidence: Confidence,
    val reasons: List<Reason>,
    val notes: Set<Note>,
    val signals: Signals,
)
