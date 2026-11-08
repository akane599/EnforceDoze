// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.profile.Plan
import java.time.LocalTime

/** Which slot of the plan is applied (SPEC 6.5). */
public enum class PlanSlot { DAY, NIGHT }

/** Where the plan stands; kept in the book, so a restart neither repeats nor skips a switch. */
public data class PlanState(
    val plan: Plan,
    val slot: PlanSlot = PlanSlot.DAY,
    /** A profile applied outside the plan (owner tap, Tasker `APPLY_PROFILE`); the plan resumes on `APPLY_PLAN`. */
    val override: String? = null,
) {
    /** The profile this state asks for. */
    val profileId: String get() = override ?: plan.night?.takeIf { slot == PlanSlot.NIGHT }?.profileId ?: plan.dayProfileId
}

/**
 * The switching rules of SPEC 6.5 as pure steps (ADR 0024). Day to night: the first moment the phone is awake
 * inside the window. That is the window-start alarm with the screen on; with the screen off the alarm switches
 * nothing and the next maintenance window does, or the screen going on or off, whichever is first. Night to
 * day: the first unlock after the window end, so a phone still asleep in the morning keeps its night profile.
 */
internal object PlanSchedule {
    /** The owner set the plan or asked to follow it again: the slot the clock is in, at once. */
    fun follow(plan: Plan, time: LocalTime): PlanState =
        PlanState(plan, if (plan.night?.contains(time) == true) PlanSlot.NIGHT else PlanSlot.DAY)

    /** The window-start alarm was delivered. With the screen off it waits; a late delivery switches nothing. */
    fun nightStarted(state: PlanState, time: LocalTime, screenOn: Boolean): PlanState = if (screenOn) awake(state, time) else state

    /** A maintenance window, or the screen going on or off: inside the window, the night begins. */
    fun awake(state: PlanState, time: LocalTime): PlanState =
        if (state.slot == PlanSlot.DAY && state.plan.night?.contains(time) == true) state.copy(slot = PlanSlot.NIGHT) else state

    /** The keyguard went away: after the window end, the night is over. */
    fun unlocked(state: PlanState, time: LocalTime): PlanState {
        val night = state.plan.night
        if (state.slot != PlanSlot.NIGHT || (night != null && time in night)) return state
        return state.copy(slot = PlanSlot.DAY)
    }

    /**
     * After a restart, as if the phone just woke: a window that opened meanwhile starts the night when the screen
     * is on, else at the next maintenance window; a night past its end waits for an unlock, unless the phone is
     * already on and unlocked.
     */
    fun started(state: PlanState, time: LocalTime, screenOn: Boolean, locked: Boolean): PlanState = when {
        state.slot == PlanSlot.DAY -> nightStarted(state, time, screenOn)
        screenOn && !locked -> unlocked(state, time)
        else -> state
    }
}
