// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.Plan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Under which settings a session ran, so D and R count stock time only (SPEC 7.2, ADR 0026). */
public enum class Provenance {
    /** The Stock profile, or day-zero history while the phone's settings were stock (no string, sensors normal, no other manager). */
    STOCK,

    /** One of the app's profiles other than Stock. */
    TUNED,

    /** Settings nobody can vouch for: the owner's previous setup, another Doze manager, or a gap in the records. */
    UNKNOWN,
}

/**
 * One screen-off session from any source: the phone's own history on day zero, the app's session records
 * later. A fact the source did not give is null, and every signal says how many sessions it rests on.
 */
public data class ScreenOffSession(
    val start: Instant,
    val end: Instant,
    /** Charging at any point. Doze does not run on the charger, so only `false` counts as unplugged. */
    val plugged: Boolean?,
    val provenance: Provenance,
    /** Lever C restricted the sensors, so motion could not reset the walk; stock is `false`. */
    val sensorsRestricted: Boolean? = null,
    /** How long the walk to deep idle takes under the settings in force (SPEC 8.4). */
    val expectedToDeep: Duration? = null,
    /** Time spent in deep idle; zero means deep idle was never reached. */
    val timeInDeep: Duration? = null,
    /** Walks reset by motion (`device_idle` reason `mo`). */
    val motionResets: Int? = null,
    /** Battery percent at the start minus at the end. */
    val batteryDrop: Double? = null,
) {
    init {
        require(!end.isBefore(start)) { "a session cannot end before it starts" }
    }

    val duration: Duration get() = Duration.between(start, end)

    val unplugged: Boolean get() = plugged == false
}

/** The plan the owner is on, and since when (SPEC 7.5). */
public data class AppliedPlan(val plan: Plan, val since: Instant)

/**
 * What the owner did with earlier plans, for the feedback loop (SPEC 7.5). Every applied plan is kept, so a
 * complaint is judged against the plan it was about and still holds after a gentler plan is applied (ADR 0026).
 */
public data class Feedback(
    /** Each plan the owner applied, oldest first; the last one is in use. Empty before any. */
    val applied: List<AppliedPlan> = emptyList(),
    /** Each "Something arrived late" from a night report. */
    val lateArrivals: List<Instant> = emptyList(),
) {
    init {
        require(applied.zipWithNext().none { (a, b) -> b.since.isBefore(a.since) }) { "applied plans must be oldest first" }
    }

    val current: AppliedPlan? get() = applied.lastOrNull()

    /** The plan in use at [time], or null before the first one. */
    public fun planAt(time: Instant): AppliedPlan? = applied.lastOrNull { !it.since.isAfter(time) }
}

/**
 * Everything the policy knows: the span its sources cover, the sessions, the zone their local times are read in,
 * and the feedback. Keeping feedback here keeps the signature `recommend(history, answers)` (SPEC 7.6).
 *
 * S, M and N use the sessions inside [from]–[to], screen-off time clipped to it. D and R use every stock session
 * given, whatever its age: stock time is rare once a plan runs, and a baseline that aged out would undo rule 4
 * and the gain check (ADR 0026).
 */
public data class History(
    val from: Instant,
    val to: Instant,
    val zone: ZoneId,
    val sessions: List<ScreenOffSession>,
    val feedback: Feedback = Feedback(),
) {
    init {
        require(!to.isBefore(from)) { "the history cannot end before it starts" }
    }

    val span: Duration get() = Duration.between(from, to)
}

/** The onboarding question Q (SPEC 7.2): how soon a message that arrives while the phone is locked must be seen. */
public enum class Urgency { RIGHT_AWAY, WITHIN_THE_HOUR, IT_CAN_WAIT }

public data class Answers(val urgency: Urgency = Urgency.WITHIN_THE_HOUR)
