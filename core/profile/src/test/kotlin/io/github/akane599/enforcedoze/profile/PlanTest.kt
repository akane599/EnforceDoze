// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @Test
    fun `the next window start is today's while ahead, else tomorrow's`() {
        val night = plan.night!!
        val zone = ZoneId.of("Europe/Istanbul")
        fun at(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), zone)
        assertThat(night.nextStart(at("2026-11-08T22:00"))).isEqualTo(at("2026-11-08T23:00"))
        assertThat(night.nextStart(at("2026-11-08T23:00"))).isEqualTo(at("2026-11-09T23:00"))
        assertThat(night.nextStart(at("2026-11-09T02:00"))).isEqualTo(at("2026-11-09T23:00"))
    }

    @Test
    fun `a window start in a daylight-saving gap moves forward by the gap`() {
        val zone = ZoneId.of("Europe/Berlin")
        val night = NightSlot("night", LocalTime.of(2, 30), LocalTime.of(6, 0))
        val start = night.nextStart(ZonedDateTime.of(LocalDateTime.parse("2026-03-29T01:00"), zone))
        assertThat(start.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-03-29T03:30"))
        val after = night.nextStart(ZonedDateTime.of(LocalDateTime.parse("2026-10-24T12:00"), zone))
        assertThat(after.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-25T02:30"))
    }
}
