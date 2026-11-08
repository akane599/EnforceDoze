// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import java.time.Duration

/** `BatteryManager` at one end of a session: the level in percent and the charge counter in µAh when reported (ADR 0018). */
public data class BatteryReading(val level: Int, val chargeUah: Long?)

/** Drain over a session; each figure is null when it cannot be told. */
public data class Drain(val percentPerHour: Double?, val mah: Double?)

public object BatteryDrain {
    private const val UAH_PER_MAH = 1_000.0
    private const val MILLIS_PER_HOUR = 3_600_000.0

    /** Nothing while the phone charged in the session: what it drew is then hidden by what it took in. */
    public fun of(start: BatteryReading?, end: BatteryReading?, duration: Duration, charged: Boolean): Drain {
        if (start == null || end == null || charged) return Drain(null, null)
        if (duration.isZero || duration.isNegative) return Drain(null, null)
        val hours = duration.toMillis() / MILLIS_PER_HOUR
        val mah = if (start.chargeUah != null && end.chargeUah != null) (start.chargeUah - end.chargeUah) / UAH_PER_MAH else null
        return Drain((start.level - end.level) / hours, mah)
    }
}
