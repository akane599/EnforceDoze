// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.parse.DeepState
import io.github.akane599.enforcedoze.parse.DozeEventLog
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** When the test histories were read: Format 2 lines before a `TIME:` line take the year nearest it. */
private val READ_AT = Instant.parse("2026-11-09T00:00:00Z")

class DozeSourcesTest {
    private val t0 = Instant.parse("2026-11-08T23:00:00Z")

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun min(n: Long): Long = n * 60

    /** One `logcat -v epoch` line. */
    private fun line(seconds: Long, tag: String, body: String): String {
        val time = at(seconds).toEpochMilli()
        return "%10d.%03d   587   587 I %s: %s".format(time / 1000, time % 1000, tag, body)
    }

    private fun read(readAt: Long, vararg lines: String) = EventLogRead(DozeEventLog.parse(lines.joinToString("\n")), at(readAt))

    private val walk = arrayOf(
        line(-60, "am_proc_start", "[0,1,2]"),
        line(-30, "device_idle", "[0,screen]"),
        line(1, "device_idle", "[1,no activity]"),
        line(30, "device_idle", "[2,s:alarm]"),
        line(31, "device_idle_light", "[4,s:alarm]"),
        line(60, "device_idle", "[3,s:alarm]"),
        line(70, "device_idle", "[4,s:alarm]"),
        line(75, "device_idle", "[5,s:alarm]"),
        line(min(10), "device_idle", "[6,s:alarm]"),
        line(min(10) + 30, "device_idle", "[5,s:alarm]"),
    )

    private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)

    private fun history(vararg values: Pair<Long, String>): BatteryHistory {
        val lines = values.joinToString("\n") { (seconds, value) -> "  ${stamp.format(at(seconds))} 090 device_idle=$value" }
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  ${stamp.format(at(-60))} TIME: 2026-11-08-22-59-00
            |$lines
        """.trimMargin()
        return BatteryHistory.parse(text, ZoneOffset.UTC, READ_AT)
    }

    @Test fun oneReadCoversTheSession() {
        val session = Interval(t0, at(min(20)))
        val record = DozeSources.merge(session, listOf(read(min(20), *walk)), null)
        assertThat(record.eventCoverage).containsExactly(session)
        assertThat(record.gaps).isEmpty()
        val deep = record.transitions.filter { it.deep }
        assertWithMessage("the state before screen off is the first from-state").that(deep.first())
            .isEqualTo(Transition(at(1), true, "ACTIVE", "INACTIVE", "no activity", TransitionSource.EVENT_LOG))
        assertThat(
            deep.map {
                it.to
            },
        ).containsExactly("INACTIVE", "IDLE_PENDING", "SENSING", "LOCATING", "IDLE", "IDLE_MAINTENANCE", "IDLE")
            .inOrder()
        assertThat(
            record.transitions.single {
                !it.deep
            },
        ).isEqualTo(Transition(at(31), false, null, "IDLE", "s:alarm", TransitionSource.EVENT_LOG))
        assertThat(record.deepChanges.first()).isEqualTo(DeepChange(at(-30), DeepState.ACTIVE, "screen"))
    }

    @Test fun overlappingReadsDoNotRepeatAChange() {
        val session = Interval(t0, at(min(20)))
        val copy = read(min(5), *walk.take(8).toTypedArray())
        val record = DozeSources.merge(session, listOf(copy, read(min(20), *walk)), null)
        assertThat(record.transitions.count { it.deep }).isEqualTo(7)
    }

    @Test fun aRolledBufferIsFilledFromTheHistory() {
        val session = Interval(t0, at(min(30)))
        val late = read(min(30), line(min(20), "am_proc_start", "[0]"), line(min(22), "device_idle", "[6,s:alarm]"))
        val history = history(min(5) to "full", min(15) to "off", min(16) to "full", min(25) to "off")
        val record = DozeSources.merge(session, listOf(late), history)
        assertThat(record.historyFilled).containsExactly(Interval(t0, at(min(20))))
        assertThat(record.gaps).isEmpty()
        assertThat(record.transitions.filter { it.source == TransitionSource.HISTORY }).containsExactly(
            Transition(t0, true, null, "off", "history", TransitionSource.HISTORY),
            Transition(at(min(5)), true, "off", "full", "history", TransitionSource.HISTORY),
            Transition(at(min(15)), true, "full", "off", "history", TransitionSource.HISTORY),
            Transition(at(min(16)), true, "off", "full", "history", TransitionSource.HISTORY),
        ).inOrder()
        assertWithMessage("off between two full stretches is a maintenance window")
            .that(record.deepChanges.map { it.state })
            .containsExactly(DeepState.INACTIVE, DeepState.IDLE, DeepState.IDLE_MAINTENANCE, DeepState.IDLE, DeepState.IDLE_MAINTENANCE)
            .inOrder()
    }

    @Test fun whatNoSourceSawStaysAGap() {
        val session = Interval(t0, at(min(30)))
        val first = read(min(10), *walk.take(8).toTypedArray())
        val second = read(min(30), line(min(12), "am_proc_start", "[0]"))
        val record = DozeSources.merge(session, listOf(first, second), null)
        assertThat(record.gaps).containsExactly(Interval(at(min(10)), at(min(12))))
        val short = DozeSources.merge(session, listOf(first, read(min(30), line(min(10) + 30, "am_proc_start", "[0]"))), null)
        assertWithMessage("a hole within the 1-minute slack is not a gap").that(short.gaps).isEmpty()
        val withHistory = history(min(5) to "full").let { h -> DozeSources.merge(session, listOf(second), h) }
        assertWithMessage("a history that starts before the gap fills it").that(withHistory.gaps).isEmpty()
    }

    @Test fun aHistoryReadJustAfterScreenOffFillsTheStart() {
        val session = Interval(t0, at(min(30)))
        val late = read(min(30), line(min(20), "am_proc_start", "[0]"))
        val wall = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC)
        fun startingAt(seconds: Long): BatteryHistory = BatteryHistory.parse(
            """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  ${stamp.format(at(seconds))} TIME: ${wall.format(at(seconds))}
            |  ${stamp.format(at(min(5)))} 090 device_idle=full
            """.trimMargin(),
            ZoneOffset.UTC,
            READ_AT,
        )
        assertWithMessage("the cursor read starts a second after screen off (ADR 0020)")
            .that(DozeSources.merge(session, listOf(late), startingAt(2)).historyFilled)
            .containsExactly(Interval(t0, at(min(20))))
        assertWithMessage("a history that starts later than the slack leaves the gap")
            .that(DozeSources.merge(session, listOf(late), startingAt(min(2))).gaps)
            .containsExactly(Interval(t0, at(min(20))))
    }

    @Test fun intervalsJoinAndSubtract() {
        val a = Interval(at(0), at(10))
        val b = Interval(at(10), at(20))
        val c = Interval(at(30), at(40))
        assertThat(DozeSources.union(listOf(c, b, a))).containsExactly(Interval(at(0), at(20)), c).inOrder()
        assertThat(DozeSources.minus(Interval(at(-5), at(50)), listOf(Interval(at(0), at(20)), c)))
            .containsExactly(Interval(at(-5), at(0)), Interval(at(20), at(30)), Interval(at(40), at(50))).inOrder()
    }

    @Test fun aSessionIsJudgedOnTheMergedRecord() {
        val input = SessionInput(start = t0, end = at(min(20)), expectedToDeep = Duration.ofSeconds(75), changes = emptyList())
        val analysis = Sessions.analyze(input, SessionSources(listOf(read(min(20), *walk)), null, null, null, 10_099))
        assertThat(analysis.result.verdict).isEqualTo(Verdict.Held)
        assertThat(analysis.timeline.filter { it.state == DeepState.IDLE }.map { it.interval })
            .containsExactly(Interval(at(75), at(min(10))), Interval(at(min(10) + 30), at(min(20)))).inOrder()
        assertWithMessage("no history and no alarm reads: the app's own wakeups are unknown").that(analysis.wakers.ownWakeups).isNull()
    }
}
