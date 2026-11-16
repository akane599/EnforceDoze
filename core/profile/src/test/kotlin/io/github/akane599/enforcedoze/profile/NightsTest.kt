// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.Nights.Stretch
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class NightsTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant()

    private fun s(from: java.time.Instant, to: java.time.Instant, unplugged: Boolean = true) = Stretch(from, to, unplugged)

    private fun nights(vararg stretches: Stretch, min: Duration = Nights.MIN_SCREEN_OFF) =
        Nights.find(stretches.toList(), zone, Nights.Rule(minScreenOff = min)) { it }

    @Test fun `a short wake-up does not split the night`() {
        val first = s(at(1, 23, 30), at(2, 3, 10))
        val second = s(at(2, 3, 19), at(2, 7, 0))
        assertThat(nights(first, second)).containsExactly(listOf(first, second))
    }

    @Test fun `a gap of exactly ten minutes splits it`() {
        val first = s(at(1, 23, 0), at(2, 3, 30))
        val second = s(at(2, 3, 40), at(2, 7, 0))
        assertThat(Nights.runs(listOf(first, second)) { it }).containsExactly(listOf(first), listOf(second)).inOrder()
    }

    @Test fun `four hours count over the sessions, not the gaps`() {
        // 2 h + 1 h 55 min of screen off with a 9-minute wake-up: 3 h 55 min, not a night.
        val short = nights(s(at(1, 23, 0), at(2, 1, 0)), s(at(2, 1, 9), at(2, 3, 4)))
        assertThat(short).isEmpty()
        val enough = nights(s(at(1, 23, 0), at(2, 1, 0)), s(at(2, 1, 9), at(2, 3, 9)))
        assertThat(enough).hasSize(1)
    }

    @Test fun `neither session alone needs four hours`() {
        val parts = (0 until 5).map { s(at(2, 0).plus(Duration.ofMinutes(it * 65L)), at(2, 0).plus(Duration.ofMinutes(it * 65L + 60))) }
        assertThat(nights(*parts.toTypedArray())).containsExactly(parts)
    }

    @Test fun `a plugged session in between ends the run`() {
        val first = s(at(1, 23, 0), at(2, 1, 0))
        val charging = s(at(2, 1, 2), at(2, 1, 30), unplugged = false)
        val second = s(at(2, 1, 32), at(2, 7, 0))
        assertThat(Nights.runs(listOf(first, charging, second)) { it }).containsExactly(listOf(first), listOf(second)).inOrder()
    }

    @Test fun `the first session must start in the window, the joined ones may start after it`() {
        val evening = s(at(1, 20, 50), at(2, 2, 0))
        assertThat(nights(evening)).isEmpty()
        val late = s(at(2, 2, 50), at(2, 4, 0))
        val after = s(at(2, 4, 5), at(2, 8, 0))
        assertThat(nights(late, after)).containsExactly(listOf(late, after))
        assertThat(Nights.startsInWindow(at(2, 3, 0), zone)).isFalse()
        assertThat(Nights.startsInWindow(at(1, 21, 0), zone)).isTrue()
    }

    @Test fun `sessions are taken in start order whatever order they come in`() {
        val first = s(at(1, 23, 0), at(2, 2, 0))
        val second = s(at(2, 2, 5), at(2, 6, 0))
        assertThat(nights(second, first)).containsExactly(listOf(first, second))
    }

    @Test fun `the wakes are the screen-on gaps inside a night`() {
        val run = listOf(s(at(1, 23, 0), at(2, 2, 0)), s(at(2, 2, 5), at(2, 4, 0)), s(at(2, 4, 3), at(2, 7, 0)))
        assertThat(Nights.wakes(run) { it }).containsExactly(
            Nights.Wake(at(2, 2, 0), at(2, 2, 5)),
            Nights.Wake(at(2, 4, 0), at(2, 4, 3)),
        ).inOrder()
        assertThat(Nights.screenOff(run) { it }).isEqualTo(Duration.ofMinutes(180 + 115 + 177))
    }

    @Test fun `a night stays open until the screen has been on for ten minutes`() {
        assertThat(Nights.closed(at(2, 7, 0), at(2, 7, 9))).isFalse()
        assertThat(Nights.closed(at(2, 7, 0), at(2, 7, 10))).isTrue()
    }

    @Test fun `the compressed night scales the gap with the threshold and runs at any hour`() {
        val rule = Nights.Rule.compressed(Duration.ofMinutes(4))
        assertThat(rule.gap).isEqualTo(Duration.ofSeconds(10))
        val first = s(at(2, 14, 0), at(2, 14, 2))
        val second = s(at(2, 14, 2).plusSeconds(9), at(2, 14, 4).plusSeconds(9))
        assertThat(Nights.find(listOf(first, second), zone, rule) { it }).containsExactly(listOf(first, second))
        val apart = s(at(2, 14, 2).plusSeconds(10), at(2, 14, 6).plusSeconds(10))
        assertThat(Nights.find(listOf(first, apart), zone, rule) { it }).containsExactly(listOf(apart))
    }

    @Test fun `a charge during the screen-on gap ends the run`() {
        val first = s(at(1, 23, 0), at(2, 2, 0))
        val second = s(at(2, 2, 5), at(2, 7, 0))
        val runs = Nights.runs(listOf(first, second), charged = { _, after -> after == second }) { it }
        assertThat(runs).containsExactly(listOf(first), listOf(second)).inOrder()
    }
}
