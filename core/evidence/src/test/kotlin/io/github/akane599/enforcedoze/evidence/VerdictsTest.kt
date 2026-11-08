// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.DeepState
import io.github.akane599.enforcedoze.parse.DeepState.ACTIVE
import io.github.akane599.enforcedoze.parse.DeepState.IDLE
import io.github.akane599.enforcedoze.parse.DeepState.IDLE_MAINTENANCE
import io.github.akane599.enforcedoze.parse.DeepState.IDLE_PENDING
import io.github.akane599.enforcedoze.parse.DeepState.INACTIVE
import io.github.akane599.enforcedoze.parse.DeepState.LOCATING
import io.github.akane599.enforcedoze.parse.DeepState.SENSING
import io.github.akane599.enforcedoze.parse.DozeEventLog
import org.junit.Test
import java.time.Duration
import java.time.Instant

class VerdictsTest {
    private val t0 = Instant.parse("2026-11-08T23:00:00Z")

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun change(seconds: Long, state: DeepState, reason: String = "s:alarm") = DeepChange(at(seconds), state, reason)

    private fun minutes(n: Long): Long = n * 60

    /** The SPEC 13.2 test profile walks to deep idle in 30 + 30 + 10 + 5 s. */
    private val compressedWalk = listOf(
        change(0, INACTIVE, "no activity"),
        change(30, IDLE_PENDING),
        change(60, SENSING),
        change(70, LOCATING),
        change(75, IDLE),
    )

    private fun compressed(changes: List<DeepChange>, end: Long = 300) = SessionInput(
        start = t0,
        end = at(end),
        expectedToDeep = Duration.ofSeconds(75),
        changes = changes,
        minimum = Duration.ofMinutes(1),
    )

    @Test
    fun `the compressed night holds with two maintenance windows`() {
        val changes = compressedWalk + listOf(
            change(135, IDLE_MAINTENANCE),
            change(140, IDLE),
            change(260, IDLE_MAINTENANCE),
            change(265, IDLE),
            change(298, ACTIVE, "unlocked"),
        )
        val result = Verdicts.judge(compressed(changes))
        assertThat(result.verdict).isEqualTo(Verdict.Held)
        assertThat(result.timeToDeep).isEqualTo(Duration.ofSeconds(75))
        assertThat(result.maintenanceWindows).isEqualTo(2)
        assertThat(result.breaks).isEmpty()
        assertThat(result.deepShare).isWithin(1e-9).of(223.0 / 225)
    }

    @Test
    fun `deep reached late is partly, with the delay past the allowance`() {
        val changes = listOf(change(0, INACTIVE, "no activity"), change(75 + 180, IDLE))
        val verdict = Verdicts.judge(compressed(changes, end = 1200)).verdict
        assertThat(verdict).isEqualTo(Verdict.Partly(breaks = emptyList(), lateBy = Duration.ofMinutes(1)))
    }

    @Test
    fun `deep reached exactly at the allowance still holds`() {
        val changes = listOf(change(0, INACTIVE, "no activity"), change(75 + 120, IDLE))
        assertThat(Verdicts.judge(compressed(changes, end = 1200)).verdict).isEqualTo(Verdict.Held)
    }

    @Test
    fun `motion breaks deep idle once, even when the walk restarts in the same instant`() {
        val changes = compressedWalk + listOf(change(minutes(30), ACTIVE, "motion"), change(minutes(30), INACTIVE, "no activity"))
        val result = Verdicts.judge(compressed(changes, end = minutes(60)))
        val verdict = result.verdict as Verdict.Partly
        assertThat(verdict.breaks).containsExactly(Break(at(minutes(30)), ACTIVE, "motion", Cause.MOTION))
        assertThat(verdict.lateBy).isNull()
        assertThat(result.motionResets).isEqualTo(1)
    }

    @Test
    fun `a break that returns to deep quickly still holds when the share stays above 80 percent`() {
        val changes = compressedWalk + listOf(change(minutes(30), ACTIVE, "motion"), change(minutes(31), IDLE))
        val result = Verdicts.judge(compressed(changes, end = minutes(60)))
        assertThat(result.verdict).isEqualTo(Verdict.Held)
        assertThat(result.breaks).hasSize(1)
    }

    @Test
    fun `the screen coming on is not a break`() {
        val changes = compressedWalk + change(297, ACTIVE, "unlocked")
        assertThat(Verdicts.judge(compressed(changes)).breaks).isEmpty()
    }

    @Test
    fun `charging the whole session explains a miss`() {
        val input = compressed(listOf(change(0, ACTIVE, "charging")), end = 1200)
            .copy(plugged = listOf(Interval(at(-60), at(1300))))
        assertThat(Verdicts.judge(input).verdict).isEqualTo(Verdict.DidNotDoze(Cause.CHARGING))
    }

    @Test
    fun `short unplugged stretches are not enough for the walk`() {
        val plugged = (0 until 10L).map { Interval(at(it * 120 + 60), at(it * 120 + 120)) }
        val input = compressed(emptyList(), end = 1200).copy(plugged = plugged)
        assertThat(Verdicts.judge(input).verdict).isEqualTo(Verdict.DidNotDoze(Cause.CHARGING))
    }

    @Test
    fun `a hold-off over a forced-idle session explains a miss`() {
        val input = SessionInput(
            start = t0,
            end = at(minutes(40)),
            expectedToDeep = Duration.ZERO,
            changes = listOf(change(minutes(1), INACTIVE, "no activity")),
            holdOffs = listOf(HoldOffSpan("CALL", Interval(at(-10), at(minutes(39))))),
        )
        assertThat(Verdicts.judge(input).verdict).isEqualTo(Verdict.DidNotDoze(Cause.HOLD_OFF))
    }

    @Test
    fun `motion resets explain a miss when nothing blocked the walk`() {
        val changes = listOf(
            change(0, INACTIVE, "no activity"),
            change(60, SENSING),
            change(65, ACTIVE, "motion"),
            change(65, INACTIVE, "no activity"),
            change(200, SENSING),
            change(205, ACTIVE, "motion"),
        )
        val result = Verdicts.judge(compressed(changes, end = 1200))
        assertThat(result.verdict).isEqualTo(Verdict.DidNotDoze(Cause.MOTION))
        assertThat(result.motionResets).isEqualTo(2)
        assertThat(result.timeToDeep).isNull()
    }

    @Test
    fun `constants not applied come before another manager, which comes before unknown`() {
        val quiet = compressed(listOf(change(0, INACTIVE, "no activity")), end = 1200)
        assertThat(Verdicts.judge(quiet).verdict).isEqualTo(Verdict.DidNotDoze(Cause.UNKNOWN))
        val foreign = quiet.copy(foreignWrites = listOf(at(100)))
        assertThat(Verdicts.judge(foreign).verdict).isEqualTo(Verdict.DidNotDoze(Cause.ANOTHER_MANAGER))
        assertThat(Verdicts.judge(foreign.copy(constantsApplied = false)).verdict)
            .isEqualTo(Verdict.DidNotDoze(Cause.CONSTANTS_NOT_APPLIED))
    }

    @Test
    fun `a break right after another app wrote the settings names that manager`() {
        val changes = compressedWalk + change(minutes(20), ACTIVE, "s:shell")
        val input = compressed(changes, end = minutes(40)).copy(foreignWrites = listOf(at(minutes(20) - 30)))
        val verdict = Verdicts.judge(input).verdict as Verdict.Partly
        assertThat(verdict.breaks.single().cause).isEqualTo(Cause.ANOTHER_MANAGER)
    }

    @Test
    fun `plugged time after deep idle is left out of the share`() {
        val changes = compressedWalk + listOf(change(minutes(10), ACTIVE, "charging"), change(minutes(50), IDLE, "s:shell"))
        val input = compressed(changes, end = minutes(60)).copy(plugged = listOf(Interval(at(minutes(10)), at(minutes(50)))))
        val result = Verdicts.judge(input)
        assertThat(result.verdict).isEqualTo(Verdict.Held)
        assertThat(result.breaks.single().cause).isEqualTo(Cause.CHARGING)
        assertThat(result.deepShare).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `a session that began in deep idle reached it at once`() {
        val changes = listOf(change(-30, IDLE, "s:shell"))
        val result = Verdicts.judge(compressed(changes))
        assertThat(result.timeToDeep).isEqualTo(Duration.ZERO)
        assertThat(result.verdict).isEqualTo(Verdict.Held)
    }

    @Test
    fun `short sessions get numbers but no verdict`() {
        val result = Verdicts.judge(compressed(compressedWalk, end = 60).copy(minimum = Verdicts.MINIMUM))
        assertThat(result.verdict).isNull()
        val tenMinutes = compressed(compressedWalk, end = minutes(10)).copy(minimum = Verdicts.MINIMUM)
        assertThat(Verdicts.judge(tenMinutes).verdict).isNull()
        val longer = compressed(compressedWalk, end = minutes(10) + 1).copy(minimum = Verdicts.MINIMUM)
        assertThat(Verdicts.judge(longer).verdict).isEqualTo(Verdict.Held)
    }

    @Test
    fun `deep changes come from epoch event lines only`() {
        val log = DozeEventLog.parse(
            """
            |         1794082001.717   700   700 I device_idle: [1,no activity]
            |         1794082002.285   700  1970 I device_idle: [5,s:shell]
            |         1794082002.300   700  1970 I device_idle_light: [4,s:shell]
            |11-08 00:26:57.147 13384 13384 I device_idle: [0,motion]
            """.trimMargin(),
        )
        assertThat(DeepChange.from(log)).containsExactly(
            DeepChange(Instant.ofEpochMilli(1_794_082_001_717), INACTIVE, "no activity"),
            DeepChange(Instant.ofEpochMilli(1_794_082_002_285), IDLE, "s:shell"),
        ).inOrder()
    }
}

class ExpectedWalkTest {
    @Test
    fun `forced idle expects deep at once`() {
        assertThat(ExpectedWalk.of(forcedIdle = true)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `the profile's timers come first and stock fills the rest`() {
        val profile = mapOf("inactive_to" to "30000", "idle_after_inactive_to" to "30000", "idle_to" to "60000")
        val stock = mapOf("inactive_to" to "+30m0s0ms", "sensing_to" to "+4m0s0ms", "locating_to" to "+30s0ms")
        assertThat(ExpectedWalk.of(false, profile, stock)).isEqualTo(Duration.ofSeconds(30 + 30 + 240 + 30))
    }

    @Test
    fun `an unknown timer leaves the walk unknown`() {
        assertThat(ExpectedWalk.of(false, mapOf("inactive_to" to "30000"))).isNull()
    }
}
