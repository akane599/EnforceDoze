// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DayZeroTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(time: String): Instant = LocalDateTime.parse("2026-11-07T$time").atZone(zone).toInstant()

    private fun history(vararg lines: String): BatteryHistory {
        val text = (
            listOf("Battery History [Format: 2] (1% used, 1KB used of 8192KB, 10 strings using 1KB):") + lines.map {
                "  11-07 $it"
            }
            )
        return BatteryHistory.parse(text.joinToString("\n"), zone, at("23:59:00"))
    }

    private fun sources(
        history: BatteryHistory?,
        events: List<DayZero.ScreenEvent> = emptyList(),
        stockNow: Boolean = true,
        fastest: Duration? = 5.min,
    ) = DayZero.Sources(history, events, stockNow, expectedToDeep = 35.min, fastestStockDeep = fastest)

    private val night = arrayOf(
        "08:00:00.000 RESET:TIME: 2026-11-07-08-00-00-000",
        "08:00:00.000 080 status=discharging plug=none +screen",
        "09:00:00.000 079 -screen",
        "09:40:00.000 079 device_idle=light",
        "10:00:00.000 078 device_idle=full",
        "11:00:00.000 077 device_idle=off",
        "11:05:00.000 077 device_idle=full",
        "11:30:00.000 076 +screen",
    )

    @Test
    fun `a screen-off stretch carries its deep idle, plug state and battery drop`() {
        val result = DayZero.read(sources(history(*night)))!!
        assertThat(result.from).isEqualTo(at("08:00:00"))
        val session = result.sessions.single()
        assertThat(session.start).isEqualTo(at("09:00:00"))
        assertThat(session.end).isEqualTo(at("11:30:00"))
        assertThat(session.plugged).isFalse()
        // 10:00-11:00 and 11:05-11:30; the maintenance window between is not deep idle.
        assertThat(session.timeInDeep).isEqualTo(85.min)
        assertThat(session.batteryDrop).isEqualTo(3.0)
        assertThat(session.provenance).isEqualTo(Provenance.STOCK)
        assertThat(session.sensorsRestricted).isFalse()
        assertThat(session.expectedToDeep).isEqualTo(35.min)
        // The event log is not read on day zero.
        assertThat(session.motionResets).isNull()
    }

    @Test
    fun `a charger at any point makes the stretch plugged`() {
        val result = DayZero.read(
            sources(
                history(
                    "08:00:00.000 RESET:TIME: 2026-11-07-08-00-00-000",
                    "08:00:00.000 080 status=discharging plug=none +screen",
                    "09:00:00.000 079 -screen",
                    "09:30:00.000 079 status=charging plug=ac +plugged +charging",
                    "10:00:00.000 085 status=discharging plug=none -plugged -charging",
                    "10:30:00.000 084 +screen",
                ),
            ),
        )!!
        assertThat(result.sessions.single().plugged).isTrue()
    }

    @Test
    fun `plug state the history never gave stays unknown`() {
        val result = DayZero.read(
            sources(
                history(
                    "08:00:00.000 RESET:TIME: 2026-11-07-08-00-00-000",
                    "08:00:00.000 080 +screen",
                    "09:00:00.000 079 -screen",
                    "10:00:00.000 078 +screen",
                ),
            ),
        )!!
        assertThat(result.sessions.single().plugged).isNull()
    }

    @Test
    fun `a reboot drops the stretch it cut, and so does one still open at the read`() {
        val result = DayZero.read(
            sources(
                history(
                    "08:00:00.000 RESET:TIME: 2026-11-07-08-00-00-000",
                    "08:00:00.000 080 status=discharging plug=none +screen",
                    "09:00:00.000 079 -screen",
                    "09:30:00.000 START",
                    "09:31:00.000 TIME: 2026-11-07-09-31-00-000",
                    "09:31:00.000 078 status=discharging plug=none +screen",
                    "10:00:00.000 078 +screen",
                    "12:00:00.000 077 -screen",
                    "13:00:00.000 076 +screen",
                    "14:00:00.000 075 -screen",
                ),
            ),
        )!!
        assertThat(result.sessions.map { it.start to it.end }).containsExactly(at("12:00:00") to at("13:00:00"))
    }

    @Test
    fun `deep idle sooner than the stock timers allow means forced idle, so nothing is vouched for as stock`() {
        val forced = history(
            "08:00:00.000 RESET:TIME: 2026-11-07-08-00-00-000",
            "08:00:00.000 080 status=discharging plug=none +screen",
            "09:00:00.000 079 -screen",
            "09:01:00.000 079 device_idle=full",
            "10:00:00.000 078 +screen",
        )
        val result = DayZero.read(sources(forced))!!
        assertThat(result.sessions.single().provenance).isEqualTo(Provenance.UNKNOWN)
        assertThat(result.sessions.single().expectedToDeep).isNull()
        assertThat(result.sessions.single().sensorsRestricted).isNull()
        // Without the timers it cannot be told, and the settings read now decide.
        assertThat(DayZero.read(sources(forced, fastest = null))!!.sessions.single().provenance).isEqualTo(Provenance.STOCK)
    }

    @Test
    fun `settings that were not stock at the read vouch for nothing`() {
        val result = DayZero.read(sources(history(*night), stockNow = false))!!
        assertThat(result.sessions.single().provenance).isEqualTo(Provenance.UNKNOWN)
        // The facts the history gave are kept.
        assertThat(result.sessions.single().timeInDeep).isEqualTo(85.min)
    }

    @Test
    fun `screen events reach back before the battery history, with the screen alone`() {
        val events = listOf(
            DayZero.ScreenEvent(at("02:00:00"), interactive = false),
            DayZero.ScreenEvent(at("03:00:00"), interactive = true),
            // Covered by the battery history: its own stretch wins.
            DayZero.ScreenEvent(at("09:00:01"), interactive = false),
            DayZero.ScreenEvent(at("11:29:59"), interactive = true),
            // Unpaired at the end.
            DayZero.ScreenEvent(at("05:00:00"), interactive = false),
        )
        val result = DayZero.read(sources(history(*night), events))!!
        assertThat(result.from).isEqualTo(at("02:00:00"))
        assertThat(result.sessions.map { it.start }).containsExactly(at("02:00:00"), at("09:00:00")).inOrder()
        val early = result.sessions.first()
        assertThat(early.end).isEqualTo(at("03:00:00"))
        assertThat(early.plugged).isNull()
        assertThat(early.timeInDeep).isNull()
        assertThat(early.batteryDrop).isNull()
    }

    @Test
    fun `screen events alone make a history, and nothing at all makes none`() {
        val events = listOf(DayZero.ScreenEvent(at("02:00:00"), false), DayZero.ScreenEvent(at("03:00:00"), true))
        assertThat(DayZero.read(sources(null, events))!!.sessions).hasSize(1)
        assertThat(DayZero.read(sources(null))).isNull()
    }

    @Test
    fun `the One UI 9 capture reads as sane sessions`() {
        val capture = Fixtures.capture("sm-s938b", "api37_CP2A.260605.016")
        val readAt = Instant.parse("2026-11-08T12:00:00Z")
        val history = BatteryHistory.parse(capture.text("batterystats-history.txt"), zone, readAt)
        val result = DayZero.read(sources(history, stockNow = false, fastest = 5.min))!!
        val sessions = result.sessions
        assertThat(sessions.size).isAtLeast(10)
        assertThat(sessions.zipWithNext().all { (a, b) -> !b.start.isBefore(a.end) }).isTrue()
        for (session in sessions) {
            assertThat(session.timeInDeep!!).isAtMost(session.duration)
            assertThat(session.provenance).isEqualTo(Provenance.UNKNOWN)
        }
        assertThat(sessions.count { it.timeInDeep!! > Duration.ZERO }).isAtLeast(1)
        assertThat(sessions.count { it.unplugged }).isAtLeast(1)
        // The policy takes it as it is.
        RecommendationPolicy.recommend(History(result.from, readAt, zone, sessions), Answers())
    }
}
