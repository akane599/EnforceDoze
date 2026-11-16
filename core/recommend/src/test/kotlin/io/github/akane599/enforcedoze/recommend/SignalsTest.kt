// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class SignalsTest {
    private fun signals(days: Int, sessions: List<ScreenOffSession>) = Signals.from(history(days, sessions))

    private fun isNight(vararg sessions: ScreenOffSession) = Signals.nightRuns(sessions.toList()).isNotEmpty()

    @Test
    fun `a night is 4 h of unplugged screen off at whatever hour (ADR 0032)`() {
        assertThat(isNight(session(1, "21:00", 240))).isTrue()
        assertThat(isNight(session(1, "20:59", 240))).isTrue()
        assertThat(isNight(session(1, "03:00", 240))).isTrue()
        assertThat(isNight(session(1, "09:30", 240))).isTrue()
        assertThat(isNight(session(1, "23:00", 239))).isFalse()
        assertThat(isNight(session(1, "23:00", 240).copy(plugged = true))).isFalse()
        // Plug state the sources did not record is not unplugged: Doze does not run on the charger.
        assertThat(isNight(session(1, "23:00", 240).copy(plugged = null))).isFalse()
    }

    @Test
    fun `sessions joined by wake-ups under 10 min are one night, 4 h counted without the wake-ups (ADR 0031)`() {
        // 23:00-01:30, a 9 min wake-up, 01:39-03:09: 4 h of screen off in two sessions.
        assertThat(isNight(session(0, "23:00", 150), session(1, "01:39", 90))).isTrue()
        // The same night one minute short: 3 h 59 min of screen off, although 4 h 8 min passed.
        assertThat(isNight(session(0, "23:00", 150), session(1, "01:39", 89))).isFalse()
        // A 10 min gap splits it, and neither half is 4 h.
        assertThat(isNight(session(0, "23:00", 150), session(1, "01:40", 90))).isFalse()
        // A charger in between splits it too.
        val charging = session(1, "01:32", 5).copy(plugged = true)
        assertThat(isNight(session(0, "23:00", 150), charging, session(1, "01:39", 90))).isFalse()
    }

    @Test
    fun `a joined night spans its first start to its last end and its sessions are not daytime`() {
        val s = signals(2, listOf(session(0, "23:30", 200), session(1, "02:55", 60), session(1, "04:00", 180)))
        assertThat(s.nights.map { it.start.toInstant() to it.end.toInstant() }).containsExactly(at(0, "23:30") to at(1, "07:00"))
        // The 02:55 and 04:00 sessions belong to the night, so nothing is daytime.
        assertThat(s.shortDaytimeShare).isEqualTo(Measured.Unknown(Unmeasured.NO_SESSIONS))
    }

    @Test
    fun `of rests less than 12 h apart the longest is the night, and the next day's sleep stays its own`() {
        // 21:00 for 245 min and 01:30 for 300 min start 4.5 h apart: one night, the longer. 23:30 the next evening is
        // 22 h later, earlier on the clock than 01:30, and still a night of its own.
        val s = signals(3, listOf(session(0, "21:00", 245), session(1, "01:30", 300), session(1, "23:30", 420)))
        assertThat(s.nights.map { it.start.toInstant() }).containsExactly(at(1, "01:30"), at(1, "23:30")).inOrder()
        // A 4.5 h afternoon rest 9 h before a 7 h sleep is a nap: it is daytime, not a night.
        val nap = signals(2, listOf(session(0, "14:00", 270), session(0, "23:00", 420)))
        assertThat(nap.nights.map { it.start.toInstant() }).containsExactly(at(0, "23:00"))
        assertThat(nap.shortDaytimeShare).isEqualTo(Measured.Known(0.0, 1))
    }

    @Test
    fun `available nights are one a day that the history could hold with its 4 h`() {
        // Noon day 0 to noon day 2: two days.
        assertThat(signals(2, emptyList()).availableNights).isEqualTo(2)
        assertThat(Signals.from(History(at(0, "02:00"), at(2, "12:00"), ZONE, emptyList())).availableNights).isEqualTo(3)
        // Under 4 h of history holds no night; 4 h holds one; a day and 4 h hold two.
        assertThat(Signals.from(History(at(0, "12:00"), at(0, "15:59"), ZONE, emptyList())).availableNights).isEqualTo(0)
        assertThat(Signals.from(History(at(0, "12:00"), at(0, "16:00"), ZONE, emptyList())).availableNights).isEqualTo(1)
        assertThat(Signals.from(History(at(0, "12:00"), at(1, "16:00"), ZONE, emptyList())).availableNights).isEqualTo(2)
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
