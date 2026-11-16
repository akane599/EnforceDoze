// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

import io.github.akane599.enforcedoze.ui.StateNotice

/** What Sensors does on the owner's taps; the route asks before all sensors go off. */
public interface SensorsActions {
    public fun allSensorsOff(on: Boolean)

    public fun openSnapshot(sessionId: Long)

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** Where Sensors sends the owner; the app's navigation implements it (ADR 0029). */
public interface SensorsNavigation {
    /** The session's report on its Sensors tab. */
    public fun snapshot(sessionId: Long)

    public fun notice(notice: StateNotice)
}
