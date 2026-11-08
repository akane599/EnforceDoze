// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class BatteryDrainTest {
    private val eightHours = Duration.ofHours(8)

    @Test fun levelsGivePercentPerHourAndTheCounterGivesMah() {
        val drain = BatteryDrain.of(BatteryReading(90, 4_000_000), BatteryReading(88, 3_900_000), eightHours, charged = false)
        assertThat(drain.percentPerHour).isWithin(1e-9).of(0.25)
        assertThat(drain.mah).isWithin(1e-9).of(100.0)
    }

    @Test fun aMissingCounterLeavesOnlyThePercent() {
        val drain = BatteryDrain.of(BatteryReading(90, null), BatteryReading(89, 3_900_000), eightHours, charged = false)
        assertThat(drain.percentPerHour).isWithin(1e-9).of(0.125)
        assertThat(drain.mah).isNull()
    }

    @Test fun nothingIsToldWhenItChargedOrAReadingIsMissing() {
        val start = BatteryReading(50, 2_000_000)
        val end = BatteryReading(60, 2_500_000)
        assertThat(BatteryDrain.of(start, end, eightHours, charged = true)).isEqualTo(Drain(null, null))
        assertThat(BatteryDrain.of(null, end, eightHours, charged = false)).isEqualTo(Drain(null, null))
        assertThat(BatteryDrain.of(start, null, eightHours, charged = false)).isEqualTo(Drain(null, null))
        assertThat(BatteryDrain.of(start, start, Duration.ZERO, charged = false)).isEqualTo(Drain(null, null))
    }
}
