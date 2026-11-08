// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import java.time.LocalTime

/** The night slot of a plan: [profileId] between [start] and [end], which may cross midnight. */
public data class NightSlot(val profileId: String, val start: LocalTime, val end: LocalTime) {
    init {
        require(start != end) { "the night window must not be empty" }
    }

    public operator fun contains(time: LocalTime): Boolean = if (start < end) time >= start && time < end else time >= start || time < end

    public companion object {
        /** SPEC 6.1: 23:00–07:30. */
        public val DEFAULT_START: LocalTime = LocalTime.of(23, 0)
        public val DEFAULT_END: LocalTime = LocalTime.of(7, 30)
    }
}

/**
 * What is actually active (SPEC 6): a daytime profile plus an optional night profile with a time window.
 * [slotAt] only says which slot the clock is in; when a switch takes effect is the engine's rule (6.5).
 */
public data class Plan(val dayProfileId: String, val night: NightSlot? = null) {
    public fun slotAt(time: LocalTime): String = night?.takeIf { time in it }?.profileId ?: dayProfileId
}
