// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import io.github.akane599.enforcedoze.ui.StateNotice

/** What Plan does on the owner's taps; the route opens the pickers and writes the plan. */
public interface PlanActions {
    public val back: (() -> Unit)?

    public fun pickDay()

    public fun pickNight()

    public fun night(on: Boolean)

    /** Opens the time picker for the window's start ([start] true) or end. */
    public fun editEdge(start: Boolean)

    public fun follow(on: Boolean)

    public fun recommended()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** What Recommended does on the owner's taps. [back] is null when it sits beside the plan. */
public interface RecommendedActions {
    public val back: (() -> Unit)?

    public fun apply()

    /** Choose something else: remembered, then the profiles (SPEC 7.4). */
    public fun somethingElse()

    public fun changeUrgency()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}
