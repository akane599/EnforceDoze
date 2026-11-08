// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class DozeEventLogTest {
    @Test fun everyCaptureParsesItsDozeLines() {
        Fixtures.with("events.txt").forEach { capture ->
            val log = DozeEventLog.parse(capture.text("events.txt"))
            assertWithMessage("$capture").that(log.unparsed).isEmpty()
            assertWithMessage("$capture").that(log.tagsSeen).containsAtLeast(DozeTag.DEEP, DozeTag.LIGHT)
        }
    }

    @Test fun emulatorWalkReachesDeepIdle() {
        val log = DozeEventLog.parse(Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3").text("events.txt"))
        assertThat(log.events.first().time).isInstanceOf(EventTime.Epoch::class.java)
        assertWithMessage("coverage starts at the first line of any tag").that(log.firstTime).isEqualTo(EventTime.Epoch(1_794_081_724_038))
        assertThat(log.events.mapNotNull { it.deepState }).contains(DeepState.IDLE)
        assertThat(log.events.filter { it.tag == DozeTag.DEEP }.map { it.reason }).contains("s:shell")
    }

    @Test fun phoneThreadtimeFormat() {
        val log = DozeEventLog.parse(Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("events.txt"))
        val first = log.events.first()
        assertThat(first.time).isEqualTo(EventTime.Local(11, 8, 0, 17, 42, 689))
        assertThat(first.deepState).isEqualTo(DeepState.ACTIVE)
        assertThat(first.reason).isEqualTo("unlocked")
        val deep = log.events.filter { it.tag == DozeTag.DEEP }.map { it.deepState to it.reason }
        assertThat(deep).containsAtLeast(DeepState.IDLE_PENDING to "s:alarm", DeepState.ACTIVE to "motion")
        assertThat(log.events.filter { it.tag == DozeTag.LIGHT }.mapNotNull { it.lightState })
            .containsAtLeast(LightState.IDLE, LightState.IDLE_MAINTENANCE)
        assertThat(log.events).hasSize(54)
    }

    @Test fun epochLineAndUnknownTag() {
        val text = """
            |         1794082053.012   587   587 I device_idle: [1,no activity]
            |         1794082053.512   587  1496 I device_idle_light_step: 
            |         1794082054.000   587   587 I device_idle_future_tag: [9]
            |         1794082054.100   587   587 I am_proc_start: [0,1,2]
        """.trimMargin()
        val log = DozeEventLog.parse(text)
        assertThat(log.events.map { it.tag }).containsExactly(DozeTag.DEEP, DozeTag.LIGHT_STEP).inOrder()
        assertThat(log.events.first().time).isEqualTo(EventTime.Epoch(1_794_082_053_012))
        assertThat(log.unparsed).hasSize(1)
        assertThat(log.otherLines).isEqualTo(1)
        assertThat(log.firstTime).isEqualTo(EventTime.Epoch(1_794_082_053_012))
        assertThat(DozeEventLog.parse("--------- beginning of events\n").firstTime).isNull()
    }

    @Test fun copyKeepsTheFirstLineAndTheDozeLines() {
        val text = """
            |--------- beginning of events
            |         1794082050.000   587   587 I am_proc_start: [0,1,2]
            |         1794082053.012   587   587 I device_idle: [1,no activity]
            |         1794082053.100   587   587 I am_proc_died: [0,1,2]
            |         1794082053.512   587  1496 I device_idle_light_step: 
        """.trimMargin()
        val copy = DozeEventLog.copyLines(text)
        assertThat(copy.lines()).hasSize(3)
        val log = DozeEventLog.parse(copy)
        assertWithMessage("the copy covers what the read covered").that(log.firstTime).isEqualTo(EventTime.Epoch(1_794_082_050_000))
        assertThat(log.events.map { it.tag }).containsExactly(DozeTag.DEEP, DozeTag.LIGHT_STEP).inOrder()
        assertThat(DozeEventLog.copyLines("")).isEmpty()
    }

    @Test fun copyOfTheEmulatorWalkKeepsEveryDozeEvent() {
        val text = Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3").text("events.txt")
        val full = DozeEventLog.parse(text)
        val copied = DozeEventLog.parse(DozeEventLog.copyLines(text))
        assertThat(copied.events).isEqualTo(full.events)
        assertThat(copied.firstTime).isEqualTo(full.firstTime)
        assertThat(copied.otherLines).isAtMost(1)
    }
}
