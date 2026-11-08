// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.NightSlot
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

class NightWindowTest {
    private fun night(day: Int, start: String, minutes: Int, zone: ZoneId = ZONE): Night {
        val s = session(day, start, minutes, zone)
        return Night(s.start.atZone(zone), s.end.atZone(zone))
    }

    private fun window(vararg nights: Night) = NightWindow.learn(nights.toList()).let { it.start to it.end }

    private fun t(text: String) = LocalTime.parse(text)

    @Test
    fun `median start and end, rounded to 15 min and padded by 30 min each side`() {
        val learned = NightWindow.learn(listOf(night(0, "23:10", 470), night(1, "22:50", 490), night(2, "23:20", 460)))
        assertThat(learned).isEqualTo(NightSlot("night", t("22:45"), t("07:30")))
    }

    @Test
    fun `the rounding step is 15 min, halves up`() {
        assertThat(window(night(0, "23:07", 480))).isEqualTo(t("22:30") to t("07:30"))
        assertThat(window(night(0, "23:08", 480))).isEqualTo(t("22:45") to t("07:45"))
        assertThat(NightWindow.round(7.5, 15)).isEqualTo(15)
        assertThat(NightWindow.round(7.4, 15)).isEqualTo(0)
    }

    @Test
    fun `an even count takes the mean of the two middle nights`() {
        assertThat(NightWindow.median(listOf(1.0, 4.0, 2.0, 3.0))).isEqualTo(2.5)
        assertThat(window(night(0, "23:00", 480), night(1, "23:30", 480))).isEqualTo(t("22:45") to t("07:45"))
    }

    @Test
    fun `starts on both sides of midnight are one stretch`() {
        assertThat(window(night(0, "23:50", 480), night(1, "00:10", 470), night(2, "00:20", 460))).isEqualTo(t("23:45") to t("08:30"))
    }

    @Test
    fun `a daylight-saving night ends at the hour on the clock`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:00 jumps to 03:00: 23:00 to 07:00 lasts 7 h that night.
        val spring = night(26, "23:00", 420, berlin)
        assertThat(spring.end.toLocalTime()).isEqualTo(t("07:00"))
        assertThat(window(spring)).isEqualTo(t("22:30") to t("07:30"))
        // 2026-10-25 03:00 falls back to 02:00: 23:00 to 07:00 lasts 9 h.
        val autumn = night(236, "23:00", 540, berlin)
        assertThat(autumn.end.toLocalTime()).isEqualTo(t("07:00"))
        assertThat(window(autumn)).isEqualTo(t("22:30") to t("07:30"))
    }

    @Test
    fun `a window never closes on itself`() {
        val learned = NightWindow.learn(listOf(night(0, "21:00", 1380)))
        assertThat(learned.start).isEqualTo(t("20:30"))
        assertThat(learned.end).isEqualTo(t("20:15"))
    }
}
