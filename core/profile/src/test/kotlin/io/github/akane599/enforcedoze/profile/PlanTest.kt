// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalTime

class PlanTest {
    private val plan = Plan("everyday", NightSlot("night", NightSlot.DEFAULT_START, NightSlot.DEFAULT_END))

    @Test
    fun `a window across midnight`() {
        assertThat(plan.slotAt(LocalTime.of(22, 59))).isEqualTo("everyday")
        assertThat(plan.slotAt(LocalTime.of(23, 0))).isEqualTo("night")
        assertThat(plan.slotAt(LocalTime.of(3, 0))).isEqualTo("night")
        assertThat(plan.slotAt(LocalTime.of(7, 29))).isEqualTo("night")
        assertThat(plan.slotAt(LocalTime.of(7, 30))).isEqualTo("everyday")
    }

    @Test
    fun `a window within one day and no window`() {
        val nap = Plan("everyday", NightSlot("night", LocalTime.of(13, 0), LocalTime.of(14, 0)))
        assertThat(nap.slotAt(LocalTime.of(12, 59))).isEqualTo("everyday")
        assertThat(nap.slotAt(LocalTime.of(13, 30))).isEqualTo("night")
        assertThat(Plan("pocket").slotAt(LocalTime.MIDNIGHT)).isEqualTo("pocket")
    }

    @Test
    fun `an empty window is refused`() {
        assertThrows(IllegalArgumentException::class.java) { NightSlot("night", LocalTime.NOON, LocalTime.NOON) }
    }
}
