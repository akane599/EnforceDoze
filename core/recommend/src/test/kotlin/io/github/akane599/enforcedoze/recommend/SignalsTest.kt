// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class SignalsTest {
    private fun signals(days: Int, sessions: List<ScreenOffSession>) = Signals.from(history(days, sessions))

    @Test
    fun `a night starts from 21_00 until 03_00, lasts 4 h and is unplugged`() {
        assertThat(Signals.isNight(session(1, "21:00", 240), ZONE)).isTrue()
        assertThat(Signals.isNight(session(1, "20:59", 240), ZONE)).isFalse()
        assertThat(Signals.isNight(session(1, "02:59", 240), ZONE)).isTrue()
        assertThat(Signals.isNight(session(1, "03:00", 240), ZONE)).isFalse()
        assertThat(Signals.isNight(session(1, "23:00", 239), ZONE)).isFalse()
        assertThat(Signals.isNight(session(1, "23:00", 240).copy(plugged = true), ZONE)).isFalse()
        // Plug state the sources did not record is not unplugged: Doze does not run on the charger.
        assertThat(Signals.isNight(session(1, "23:00", 240).copy(plugged = null), ZONE)).isFalse()
    }

    @Test
    fun `one night per evening, the longest session, and 01_00 belongs to the evening before`() {
        val s = signals(3, listOf(session(0, "22:00", 250), session(1, "01:00", 300), session(1, "23:30", 420)))
        assertThat(s.nights.map { it.start.toInstant() }).containsExactly(at(1, "01:00"), at(1, "23:30")).inOrder()
    }

    @Test
    fun `available nights are those the history could hold a whole night session in`() {
        // Noon day 0 to noon day 2: the nights of day 0 and day 1.
        assertThat(signals(2, emptyList()).availableNights).isEqualTo(2)
        // From 02:00: a night can still start before 03:00, so the night before counts as well.
        val early = History(at(0, "02:00"), at(2, "12:00"), ZONE, emptyList())
        assertThat(Signals.from(early).availableNights).isEqualTo(3)
        // Ending at 00:59 leaves no 4 h after 21:00 that evening.
        val short = History(at(0, "12:00"), at(1, "00:59"), ZONE, emptyList())
        assertThat(Signals.from(short).availableNights).isEqualTo(0)
        assertThat(Signals.from(History(at(0, "12:00"), at(1, "01:00"), ZONE, emptyList())).availableNights).isEqualTo(1)
    }

    @Test
    fun `S counts all screen-off time, long sessions from 30 min, short ones under 15 min`() {
        val s = signals(2, listOf(session(0, "13:00", 30), session(0, "14:00", 15), session(0, "15:00", 14), session(1, "09:00", 61)))
        assertThat(s.screenOff).isEqualTo(Duration.ofMinutes(120))
        assertThat(s.screenOffHoursPerDay).isWithin(1e-9).of(1.0)
        assertThat(s.longShare).isWithin(1e-9).of(91.0 / 120)
        assertThat(s.shortDaytimeShare).isEqualTo(Measured.Known(14.0 / 120, 4))
    }

    @Test
    fun `the short share is of unplugged daytime only`() {
        val charging = session(1, "11:00", 60).copy(plugged = true)
        val s = signals(2, listOf(session(0, "23:00", 480), session(1, "09:00", 10), session(1, "10:00", 10), charging))
        assertThat(s.shortDaytimeShare).isEqualTo(Measured.Known(1.0, 2))
        assertThat(signals(2, listOf(session(0, "23:00", 480))).shortDaytimeShare).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
    }

    @Test
    fun `deep idle counts as never reached only past the expected walk plus the 2 min margin`() {
        val due = session(1, "13:00", 37).copy(timeInDeep = Duration.ZERO)
        assertThat(signals(2, listOf(due)).neverDeep).isEqualTo(Measured.Known(Share(1, 1), 1))
        val early = session(1, "13:00", 36).copy(timeInDeep = Duration.ZERO)
        assertThat(signals(2, listOf(early)).neverDeep).isEqualTo(Measured.Unknown(Unmeasured.NOT_RECORDED))
    }

    @Test
    fun `M uses long unplugged daytime sessions with the sensors free`() {
        val restricted = session(1, "13:00", 60).copy(timeInDeep = Duration.ZERO, sensorsRestricted = true)
        val unknown = session(1, "15:00", 60).copy(timeInDeep = Duration.ZERO, sensorsRestricted = null)
        val plugged = session(1, "17:00", 60).copy(timeInDeep = Duration.ZERO, plugged = true)
        val short = session(1, "19:00", 29).copy(timeInDeep = Duration.ZERO, expectedToDeep = 10.min)
        val night = session(1, "23:00", 300).copy(timeInDeep = Duration.ZERO)
        val s = signals(2, listOf(restricted, unknown, plugged, short, night))
        assertThat(s.neverDeep).isEqualTo(Measured.Unknown(Unmeasured.SENSORS_RESTRICTED))
        assertThat(s.motionResetsPerHour).isEqualTo(Measured.Unknown(Unmeasured.SENSORS_RESTRICTED))
        assertThat(signals(2, listOf(unknown)).neverDeep).isEqualTo(Measured.Unknown(Unmeasured.NOT_RECORDED))
        assertThat(signals(2, listOf(plugged, short, night)).neverDeep).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
    }

    @Test
    fun `motion resets per hour of the sessions that counted them`() {
        val s = signals(2, listOf(session(1, "13:00", 60).copy(motionResets = 4), session(1, "15:00", 120).copy(motionResets = 2)))
        assertThat(s.motionResetsPerHour).isEqualTo(Measured.Known(2.0, 2))
        assertThat(signals(2, listOf(session(1, "13:00", 60))).motionResetsPerHour).isEqualTo(Measured.Unknown(Unmeasured.NOT_RECORDED))
    }

    @Test
    fun `D and R come from long unplugged stock sessions only`() {
        val stock = session(1, "13:00", 60).copy(timeInDeep = 30.min, batteryDrop = 1.0)
        val tuned = session(1, "15:00", 60).copy(provenance = Provenance.TUNED, timeInDeep = 60.min, batteryDrop = 0.1)
        val night = sleep(1, minutes = 480)
        val s = signals(2, listOf(stock, tuned, night))
        assertThat(s.deepShare).isEqualTo(Measured.Known(270.0 / 540, 2))
        assertThat(s.drainPerHour).isEqualTo(Measured.Known(9.0 / 9, 2))
    }

    @Test
    fun `D and R say why they are missing`() {
        assertThat(signals(2, listOf(session(1, "13:00", 20))).deepShare).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
        val unknown = session(1, "13:00", 60).copy(provenance = Provenance.UNKNOWN, timeInDeep = 30.min, batteryDrop = 1.0)
        assertThat(signals(2, listOf(unknown)).drainPerHour).isEqualTo(Measured.Unknown(Unmeasured.NO_STOCK_SESSIONS))
        assertThat(signals(2, listOf(session(1, "13:00", 60))).drainPerHour).isEqualTo(Measured.Unknown(Unmeasured.NOT_RECORDED))
    }

    @Test
    fun `sessions outside the history do not count`() {
        val s = signals(2, listOf(session(-1, "13:00", 60), session(0, "11:00", 60), session(2, "12:00", 60)))
        assertThat(s.screenOff).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `a session across the history's edge counts its inside part, and is long or short by its whole length`() {
        val s = Signals.from(History(at(0, "12:00"), at(1, "12:00"), ZONE, listOf(session(0, "11:00", 120), session(1, "11:50", 14))))
        assertThat(s.screenOff).isEqualTo(Duration.ofMinutes(70))
        assertThat(s.longShare).isWithin(1e-9).of(60.0 / 70)
        assertThat(s.shortDaytimeShare).isEqualTo(Measured.Known(10.0 / 70, 2))
    }

    @Test
    fun `M and N take only sessions that began inside the history`() {
        val awake = session(0, "11:30", 60).copy(timeInDeep = Duration.ZERO, motionResets = 6)
        val night = sleep(0, "23:00", 480)
        val s = Signals.from(History(at(0, "12:00"), at(2, "12:00"), ZONE, listOf(awake)))
        assertThat(s.neverDeep).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
        assertThat(s.motionResetsPerHour).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
        // The night of day 0 began before this history, so it was never one of its available nights.
        val later = Signals.from(History(at(1, "00:00"), at(3, "12:00"), ZONE, listOf(night)))
        assertThat(later.nights).isEmpty()
        assertThat(later.screenOff).isEqualTo(Duration.ofMinutes(420))
    }

    @Test
    fun `D and R use stock sessions of any age, older than the history too`() {
        val old = session(1, "13:00", 60).copy(timeInDeep = 30.min, batteryDrop = 1.0)
        val s = Signals.from(History(at(10, "12:00"), at(12, "12:00"), ZONE, listOf(old, session(11, "13:00", 60))))
        assertThat(s.deepShare).isEqualTo(Measured.Known(0.5, 1))
        assertThat(s.drainPerHour).isEqualTo(Measured.Known(1.0, 1))
        assertThat(s.screenOff).isEqualTo(Duration.ofMinutes(60))
    }

    @Test
    fun `available nights across a daylight-saving change`() {
        val berlin = java.time.ZoneId.of("Europe/Berlin")
        // Clocks go forward in the night of Saturday 28 March 2026 (day 26); that week holds 7 nights all the same.
        val week = History(at(24, "12:00", berlin), at(31, "12:00", berlin), berlin, emptyList())
        assertThat(Signals.from(week).availableNights).isEqualTo(7)
        val short = History(at(26, "12:00", berlin), at(27, "12:00", berlin), berlin, emptyList())
        assertThat(Duration.between(short.from, short.to)).isEqualTo(Duration.ofHours(23))
        assertThat(Signals.from(short).availableNights).isEqualTo(1)
        // Clocks go back in the night of Saturday 24 October 2026 (day 236), a 25-hour day.
        val autumn = History(at(236, "12:00", berlin), at(237, "12:00", berlin), berlin, emptyList())
        assertThat(Signals.from(autumn).availableNights).isEqualTo(1)
    }
}
