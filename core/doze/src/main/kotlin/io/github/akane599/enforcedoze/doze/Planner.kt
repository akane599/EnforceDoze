// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import java.time.Duration
import java.time.Instant

/** The planner (ADR 0015): from targets and the book to the writes to make, in order. */
public object Planner {
    /**
     * Each wanted value that differs from the last write, plus the [corrections] decided for drift, on
     * levers the tier can write. Levers the tier cannot write wait for the next reconcile.
     */
    public fun plan(wanted: Map<Lever, Setting>, written: Map<Lever, Setting>, writable: Writable, corrections: Set<Lever>): List<Setting> =
        order(wanted).mapNotNull { lever ->
            wanted[lever]?.takeIf { writable.can(lever) && (it != written[lever] || lever in corrections) }
        }

    /**
     * Write order. Engaging at screen off: sensors first, then forced idle (ADR 0015), so motion cannot undo
     * the jump. Otherwise forced idle is released first, so the phone is fully awake before anything else.
     * Constants and the allowlist come last: they are never time-critical.
     */
    public fun order(wanted: Map<Lever, Setting>): List<Lever> = if (wanted[Lever.FORCE_IDLE] == Setting.FORCED) ENGAGE else RELEASE

    private val ENGAGE = listOf(Lever.SENSORS, Lever.SENSOR_PRIVACY, Lever.FORCE_IDLE, Lever.CONSTANTS, Lever.ALLOWLIST)
    private val RELEASE = listOf(Lever.FORCE_IDLE, Lever.SENSORS, Lever.SENSOR_PRIVACY, Lever.CONSTANTS, Lever.ALLOWLIST)
}

/** SPEC 5.7: after two drift corrections of one lever within an hour, the engine stops re-applying it and asks. */
public object LoopGuard {
    public const val LIMIT: Int = 2
    public val WINDOW: Duration = Duration.ofHours(1)

    /** The corrections still inside the window at [now]. */
    public fun recent(times: List<Instant>, now: Instant): List<Instant> = times.filter { Duration.between(it, now) < WINDOW }

    public fun allows(times: List<Instant>, now: Instant): Boolean = recent(times, now).size < LIMIT
}
