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
        Nights.find(stretches.toList(), Nights.Rule(minScreenOff = min)) { it }

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

    @Test fun `a rest is a night at whatever hour it falls`() {
        val daySleep = s(at(2, 9, 30), at(2, 13, 0))
        val afterLunch = s(at(2, 13, 6), at(2, 15, 0))
        assertThat(nights(daySleep, afterLunch)).containsExactly(listOf(daySleep, afterLunch))
        assertThat(nights(s(at(2, 18, 0), at(2, 22, 0)))).hasSize(1)
    }

    @Test fun `of two rests less than 12 hours apart only the longer is a night`() {
        val nap = s(at(2, 14, 0), at(2, 18, 30))
        val sleep = s(at(2, 23, 0), at(3, 7, 0))
        assertThat(nights(nap, sleep)).containsExactly(listOf(sleep))
        val napAfter = s(at(3, 9, 59), at(3, 14, 30))
        assertThat(nights(sleep, napAfter)).containsExactly(listOf(sleep))
        val tie = s(at(3, 10, 0), at(3, 18, 0))
        assertThat(nights(sleep, tie)).containsExactly(listOf(sleep))
    }

    @Test fun `sleeps on consecutive days stay two, even when the second starts earlier on the clock`() {
        val first = s(at(2, 1, 0), at(2, 6, 0))
        val second = s(at(2, 23, 30), at(3, 6, 30))
        assertThat(nights(first, second)).containsExactly(listOf(first), listOf(second)).inOrder()
        val closer = s(at(2, 12, 59), at(2, 21, 0))
        assertThat(nights(first, closer)).containsExactly(listOf(closer))
        val apart = s(at(2, 13, 0), at(2, 21, 0))
        assertThat(nights(first, apart)).containsExactly(listOf(first), listOf(apart)).inOrder()
    }

    @Test fun `a longer rest replaces the night before it, and the next shorter one is dropped`() {
        // 5 h from 01:00; 8 h from 10:00 starts 9 h later and is longer, so it is the night; 6 h from 21:00 starts 11 h
        // after that and is shorter, so it is dropped, though it is 20 h after the first.
        val first = s(at(2, 1, 0), at(2, 6, 0))
        val long = s(at(2, 10, 0), at(2, 18, 0))
        val third = s(at(2, 21, 0), at(3, 3, 0))
        assertThat(nights(first, long, third)).containsExactly(listOf(long))
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

    @Test fun `the compressed night scales the gap with the threshold and has no spacing`() {
        val rule = Nights.Rule.compressed(Duration.ofMinutes(4))
        assertThat(rule.gap).isEqualTo(Duration.ofSeconds(10))
        assertThat(rule.spacing).isEqualTo(Duration.ZERO)
        val first = s(at(2, 14, 0), at(2, 14, 2))
        val second = s(at(2, 14, 2).plusSeconds(9), at(2, 14, 4).plusSeconds(9))
        assertThat(Nights.find(listOf(first, second), rule) { it }).containsExactly(listOf(first, second))
        val apart = s(at(2, 14, 2).plusSeconds(10), at(2, 14, 6).plusSeconds(10))
        assertThat(Nights.find(listOf(first, apart), rule) { it }).containsExactly(listOf(apart))
        val next = s(at(2, 14, 7), at(2, 14, 12))
        assertThat(Nights.find(listOf(apart, next), rule) { it }).containsExactly(listOf(apart), listOf(next)).inOrder()
    }

    @Test fun `a charge during the screen-on gap ends the run`() {
        val first = s(at(1, 23, 0), at(2, 2, 0))
        val second = s(at(2, 2, 5), at(2, 7, 0))
        val runs = Nights.runs(listOf(first, second), charged = { _, after -> after == second }) { it }
        assertThat(runs).containsExactly(listOf(first), listOf(second)).inOrder()
    }
}
