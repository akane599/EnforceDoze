// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.parse.AlarmStats
import io.github.akane599.enforcedoze.parse.AlarmTagStats
import io.github.akane599.enforcedoze.parse.AppAlarmStats
import io.github.akane599.enforcedoze.parse.BatteryHistory
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class WakersTest {
    private fun at(time: String): Instant = Instant.parse("2026-11-08T${time}Z")

    private val session = Interval(at("23:00:00"), at("23:30:00"))
    private val self = 10_099

    private val history = BatteryHistory.parse(
        """
        |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
        |  11-08 22:59:59.000 TIME: 2026-11-08-22-59-59
        |  11-08 22:59:59.000 090 +running +wake_lock +mobile_radio
        |  11-08 22:59:59.500 090 -job=u0a51:"old"
        |  11-08 23:00:00.000 090 -screen
        |  11-08 23:00:01.000 090 -wake_lock
        |  11-08 23:00:05.000 090 -mobile_radio
        |  11-08 23:01:00.000 090 +job=u0a50:"com.x/.Job"
        |  11-08 23:01:30.000 090 -job=u0a50:"com.x/.Job"
        |  11-08 23:02:00.000 090 +wake_lock=u0a50:"x:sync"
        |  11-08 23:02:00.500 090 wake_lock=u0a60:"important"
        |  11-08 23:02:10.000 090 -wake_lock=u0a50:"x:sync"
        |  11-08 23:05:00.000 089 device_idle=full
        |  11-08 23:06:00.000 089 wakeupap=u0a70:"" wake_reason=0:"Abort: Pending Wakeup Sources: NETLINK"
        |  11-08 23:07:00.000 089 wake_reason=0:"Abort: Pending Wakeup Sources: NETLINK"
        |  11-08 23:07:30.000 089 wake_reason=0:"170 qcom,smd-modem"
        |  11-08 23:10:00.000 089 +job=u0a99:"self/.Copy"
        |  11-08 23:10:01.000 089 -job=u0a99:"self/.Copy"
        |  11-08 23:14:59.000 089 +wake_lock=u0a99:"self:x"
        |  11-08 23:15:00.000 089 device_idle=off
        |  11-08 23:15:01.000 089 +job=u0a99:"self/.Copy"
        |  11-08 23:15:02.000 089 -wake_lock
        |  11-08 23:15:30.000 089 -job=u0a99:"self/.Copy"
        |  11-08 23:20:00.000 088 +sync=u0a50:"account"
        |  11-08 23:29:00.000 088 +job=u0a50:"late"
        |  11-08 23:31:00.000 088 -job=u0a50:"late" +screen
        |  NEXT: 987654
        """.trimMargin(),
        ZoneOffset.UTC,
        2026,
    )

    private val before = AlarmStats.parse(
        """
        |  Alarm Stats:
        |  u0a50:com.x +1s0ms running, 2 wakeups:
        |    +1s0ms 2 wakes 5 alarms, last -1m0s0ms:
        |      *walarm*:x.TICK
        |  1000:android +1s0ms running, 10 wakeups:
        |    +1s0ms 10 wakes 10 alarms, last -1m0s0ms:
        |      *walarm*:WriteBufferAlarm
        |
        """.trimMargin(),
    )

    private val after = AlarmStats.parse(
        """
        |  Alarm Stats:
        |  u0a50:com.x +2s0ms running, 5 wakeups:
        |    +1s0ms 5 wakes 9 alarms, last -1m0s0ms:
        |      *walarm*:x.TICK
        |    +1s0ms 0 wakes 3 alarms, last -1m0s0ms:
        |      *alarm*:x.POLL
        |  1000:android +1s0ms running, 10 wakeups:
        |    +1s0ms 10 wakes 10 alarms, last -1m0s0ms:
        |      *walarm*:WriteBufferAlarm
        |  u0a80:com.fresh +1ms running, 1 wakeups:
        |    +1ms 1 wakes 1 alarms, last -1m0s0ms:
        |      *walarm*:fresh
        |
        """.trimMargin(),
    )

    private val idle = listOf(Interval(at("23:05:00"), at("23:15:00")))

    private fun summary(history: BatteryHistory? = this.history, alarmsAfter: AlarmStats? = after) =
        Wakers.summarize(WakerInput(session, history, before, alarmsAfter, idle, self))

    @Test fun appsAreRankedByWakeupsThenWakelockTime() {
        val apps = summary().apps
        assertThat(apps.map { it.uid }).containsExactly(10_050, 10_070, 10_080, 10_060, 10_099).inOrder()
        val x = apps.first()
        assertThat(x.packages).containsExactly("com.x")
        assertThat(x.alarmWakeups).isEqualTo(3)
        assertThat(x.alarms).containsExactly(AlarmTagStats("*walarm*:x.TICK", 3, 4), AlarmTagStats("*alarm*:x.POLL", 0, 3)).inOrder()
        assertWithMessage("a job cut by screen on counts the part inside").that(x.jobs).isEqualTo(2)
        assertThat(x.jobTime).isEqualTo(Duration.ofSeconds(90))
        assertWithMessage("a sync still running at the read ran to screen on").that(x.syncTime).isEqualTo(Duration.ofMinutes(10))
        assertWithMessage("the stretch went to the holder that took over").that(x.wakelockTime).isEqualTo(Duration.ZERO)
        assertThat(apps.single { it.uid == 10_060 }.wakelockTime).isEqualTo(Duration.ofSeconds(10))
        assertThat(apps.single { it.uid == 10_070 }.networkWakeups).isEqualTo(1)
        assertWithMessage("a job that ended before screen off is not a waker").that(apps.map { it.uid }).doesNotContain(10_051)
    }

    @Test fun deviceFiguresAreCutToTheSession() {
        val summary = summary()
        assertThat(summary.wakeReasons).containsExactly(
            WakeReason("Abort: Pending Wakeup Sources: NETLINK", 2),
            WakeReason("170 qcom,smd-modem", 1),
        ).inOrder()
        assertThat(summary.mobileRadio).isEqualTo(Duration.ofSeconds(5))
        assertThat(summary.unattributedWakelock).isEqualTo(Duration.ofSeconds(1))
        assertThat(summary.historyCoverage).isEqualTo(session)
        assertThat(Wakers.historyDeepIdle(history, session)).containsExactly(Interval(at("23:05:00"), at("23:15:00")))
    }

    @Test fun ownRunsCountOnlyWhenTheyStartInDeepIdle() {
        val summary = summary()
        val own = summary.own
        assertThat(own.inIdle.map { it.interval.start }).containsExactly(at("23:10:00"))
        assertWithMessage("a run in the last 2 s of IDLE belongs to the maintenance window")
            .that(own.outsideIdle.map { it.kind to it.interval.start })
            .containsExactly(RunKind.WAKELOCK to at("23:14:59"), RunKind.JOB to at("23:15:01"))
        assertThat(own.alarmWakeups).isEqualTo(0)
        assertThat(summary.ownWakeups).isEqualTo(1)
    }

    @Test fun ownWakeupsAreUnknownWithoutBothSources() {
        assertThat(summary(alarmsAfter = null).ownWakeups).isNull()
        assertThat(summary(alarmsAfter = null).alarmsRead).isFalse()
        assertThat(summary(history = null).ownWakeups).isNull()
        val late = BatteryHistory.parse(
            """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  11-08 23:05:00.000 TIME: 2026-11-08-23-05-00
            |  11-08 23:05:01.000 090 -running
            """.trimMargin(),
            ZoneOffset.UTC,
            2026,
        )
        assertWithMessage("a history that starts 5 min late does not cover the session").that(summary(history = late).ownWakeups).isNull()
        assertThat(summary(history = late).historyCoverage).isEqualTo(Interval(at("23:05:00"), session.end))
    }

    @Test fun alarmCountersThatRestartedCountFromZero() {
        val rebooted =
            AlarmStats(true, listOf(AppAlarmStats(10_050, "com.x", 1, listOf(AlarmTagStats("*walarm*:x.TICK", 1, 1)))), emptyList())
        assertThat(Wakers.alarmDelta(before, rebooted)).containsExactly(
            AppAlarmStats(10_050, "com.x", 1, listOf(AlarmTagStats("*walarm*:x.TICK", 1, 1))),
        )
        assertThat(Wakers.alarmDelta(after, after)).isEmpty()
    }

    @Test fun everyCapturedHistoryReplays() {
        Fixtures.with("batterystats-history.txt").forEach { capture ->
            val history = BatteryHistory.parse(capture.text("batterystats-history.txt"), ZoneOffset.UTC, 2026)
            val whole = Interval(Instant.ofEpochMilli(history.start!!), Instant.ofEpochMilli(history.end!!))
            val summary = Wakers.summarize(WakerInput(whole, history, null, null, emptyList(), self))
            val text = capture.text("batterystats-history.txt")
            assertWithMessage("$capture: every finished job is a run")
                .that(summary.apps.sumOf { it.jobs }).isAtLeast(Regex("""(^| )-job=""", RegexOption.MULTILINE).findAll(text).count())
            assertWithMessage("$capture").that(summary.apps.map { it.jobTime }.filter { it.isNegative }).isEmpty()
            assertWithMessage("$capture").that(summary.ownWakeups).isNull()
        }
    }
}
