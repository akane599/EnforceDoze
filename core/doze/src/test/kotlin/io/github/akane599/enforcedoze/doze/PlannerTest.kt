// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PlannerTest {
    private val restricted = Setting.Sensors(SensorValue.Restricted("self"))
    private val string = Setting.Constants(ConstantsValue.Exact("inactive_to=300000"))

    @Test
    fun `writes what differs from the last write, in engage order`() {
        val wanted = mapOf(Lever.CONSTANTS to string, Lever.FORCE_IDLE to Setting.FORCED, Lever.SENSORS to restricted)
        val plan = Planner.plan(wanted, mapOf(Lever.CONSTANTS to string), Writable.EVERYTHING, emptySet())
        assertThat(plan).containsExactly(restricted, Setting.FORCED).inOrder()
    }

    @Test
    fun `releases forced idle before anything else`() {
        val wanted = mapOf(Lever.SENSORS to Setting.SENSORS_NORMAL, Lever.FORCE_IDLE to Setting.RELEASED, Lever.CONSTANTS to string)
        assertThat(Planner.plan(wanted, emptyMap(), Writable.EVERYTHING, emptySet()))
            .containsExactly(Setting.RELEASED, Setting.SENSORS_NORMAL, string).inOrder()
    }

    @Test
    fun `a correction rewrites an unchanged target`() {
        val wanted = mapOf(Lever.CONSTANTS to string)
        assertThat(Planner.plan(wanted, wanted, Writable.EVERYTHING, emptySet())).isEmpty()
        assertThat(Planner.plan(wanted, wanted, Writable.EVERYTHING, setOf(Lever.CONSTANTS))).containsExactly(string)
    }

    @Test
    fun `levers the tier cannot write wait`() {
        val wanted = mapOf(Lever.CONSTANTS to string, Lever.FORCE_IDLE to Setting.FORCED, Lever.SENSORS to restricted)
        assertThat(Planner.plan(wanted, emptyMap(), Writable(constants = true, sensors = true), emptySet()))
            .containsExactly(restricted, string).inOrder()
    }

    @Test
    fun `don't care is never written`() {
        assertThat(Planner.plan(emptyMap(), mapOf(Lever.CONSTANTS to string), Writable.EVERYTHING, setOf(Lever.CONSTANTS))).isEmpty()
    }

    @Test
    fun `loop guard counts corrections within the last hour`() {
        val start = Instant.parse("2026-11-08T22:00:00Z")
        fun at(minutes: Long) = start + Duration.ofMinutes(minutes)
        assertThat(LoopGuard.allows(listOf(at(0), at(30)), at(59))).isFalse()
        assertThat(LoopGuard.allows(listOf(at(0), at(59)), at(61))).isTrue()
        assertThat(LoopGuard.recent(listOf(at(0), at(59)), at(61))).containsExactly(at(59))
        assertThat(LoopGuard.allows(listOf(at(0)), at(1))).isTrue()
    }
}
