// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

import androidx.compose.runtime.Immutable
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.Flow

/** The sensor service's mode as `dumpsys sensorservice` prints it (`Mode : NORMAL`, `Mode : RESTRICTED : <pkg>`). */
public sealed interface SensorModeNow {
    public data object Normal : SensorModeNow

    /**
     * Restricted mode (lever C): only [allowed] keeps its sensors. Null when the allow package is this app's own,
     * which holds no sensors and stands in when nothing is exempt (ADR 0004): no app keeps its sensors then.
     */
    public data class Restricted(val allowed: String?) : SensorModeNow

    /** Another mode, as printed. */
    public data class Other(val raw: String) : SensorModeNow

    /** Not read: no privilege to dump the sensor service. */
    public data object Unknown : SensorModeNow
}

/** One app that held a sensor at the last screen off, with the rate it asked for. */
@Immutable
public data class SensorHolder(val label: String, val sensor: String, val rateHz: Double?)

/** Who held sensors at the last screen off: the snapshot of SPEC 8.2, from the session it opened. */
@Immutable
public data class SensorSnapshot(val sessionId: Long, val time: Long, val holders: List<SensorHolder>)

/** Everything Sensors shows, mapped by the app from the last sensor dump, the snapshot and sensor privacy. */
@Immutable
public data class SensorsState(
    val mode: SensorModeNow,
    /** Sensor privacy (all sensors off); null when it could not be read. */
    val allSensorsOff: Boolean? = false,
    val snapshot: SensorSnapshot? = null,
    /** All sensors off needs the shell worker (SPEC 5.4); false without it. */
    val canWrite: Boolean = true,
    val notices: List<StateNotice> = emptyList(),
)

/** What Sensors needs from the app (ADR 0029). */
public interface SensorsSource {
    public val state: Flow<SensorsState>

    /** The developer "sensors off" switch through the worker's sensor-privacy call (SPEC 5.4). */
    public suspend fun allSensorsOff(on: Boolean): ActionOutcome

    public fun seen(noticeId: String)
}
