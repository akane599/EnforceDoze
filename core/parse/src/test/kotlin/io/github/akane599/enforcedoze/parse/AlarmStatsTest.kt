// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class AlarmStatsTest {
    @Test fun everyCaptureParsesItsAlarmStats() {
        Fixtures.with("alarm.txt").forEach { capture ->
            val text = capture.text("alarm.txt")
            val stats = AlarmStats.parse(text)
            assertWithMessage("$capture").that(stats.found).isTrue()
            assertWithMessage("$capture").that(stats.unparsed).isEmpty()
            val section = text.lineSequence().dropWhile { it.trim() != "Alarm Stats:" }.drop(1).takeWhile { it.isNotBlank() }.toList()
            assertWithMessage("$capture: every app line is read")
                .that(stats.apps).hasSize(section.count { it.contains(" running, ") })
            assertWithMessage("$capture: every tag line is read")
                .that(stats.apps.sumOf { it.tags.size }).isEqualTo(section.count { it.contains(" wakes ") })
            assertWithMessage("$capture: an app's wakeups are the sum of its tags'")
                .that(stats.apps.filter { app -> app.wakeups != app.tags.sumOf { it.wakeups } }).isEmpty()
        }
    }

    @Test fun phoneSystemServerAlarms() {
        val stats = AlarmStats.parse(Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("alarm.txt"))
        val android = stats.apps.filter { it.uid == 1000 && it.packageName == "android" }.single()
        assertThat(android.tags).contains(AlarmTagStats("*walarm*:WriteBufferAlarm", 15, 15))
        assertThat(android.tags).contains(AlarmTagStats("*alarm*:*job.delay*", 0, 38))
    }

    @Test fun activeAppsTagsAndAnotherUser() {
        val text = """
            |  Top Alarms:
            |    +2s120ms running, 1 wakeups, 1 alarms: u0a357:com.example.top
            |      *walarm*:top
            |  
            |  Alarm Stats:
            |  *ACTIVE* u10a5:com.example.work +1s0ms running, 3 wakeups:
            |    *ACTIVE* +600ms 2 wakes 2 alarms, last -5s0ms:
            |      *walarm*:sync now
            |    +400ms 1 wakes 7 alarms, last --:
            |      *alarm*:com.example.TICK
            |  1000:android 0 running, 0 wakeups:
            |
            |  Alarm manager stats:
            |    +1s0ms running, 3 wakeups, 9 alarms: u10a5:com.example.work
        """.trimMargin()
        val stats = AlarmStats.parse(text)
        assertThat(stats.unparsed).isEmpty()
        assertThat(stats.apps).containsExactly(
            AppAlarmStats(
                1_010_005,
                "com.example.work",
                3,
                listOf(AlarmTagStats("*walarm*:sync now", 2, 2), AlarmTagStats("*alarm*:com.example.TICK", 1, 7)),
            ),
            AppAlarmStats(1000, "android", 0, emptyList()),
        ).inOrder()
        assertThat(stats.apps.first().alarms).isEqualTo(9)
    }

    @Test fun tagsAfterAnAppLineThatDidNotParseAreNotGivenToTheAppBefore() {
        val text = """
            |  Alarm Stats:
            |  1000:android 0 running, 0 wakeups:
            |  u0a5:com.example.next running with a new layout:
            |    +600ms 2 wakes 2 alarms, last -5s0ms:
            |      *walarm*:sync now
            |  u0a6:com.example.after +1s0ms running, 1 wakeups:
            |    +1s0ms 1 wakes 1 alarms, last -5s0ms:
            |      *walarm*:tick
            |
        """.trimMargin()
        val stats = AlarmStats.parse(text)
        assertThat(stats.apps.single { it.packageName == "android" }.tags).isEmpty()
        assertThat(stats.apps.single { it.packageName == "com.example.after" }.tags).containsExactly(AlarmTagStats("*walarm*:tick", 1, 1))
        assertThat(stats.unparsed).hasSize(3)
    }

    @Test fun aDumpWithoutTheSectionSaysSo() {
        val stats = AlarmStats.parse("Current Alarm Manager state:\n  Settings:\n")
        assertThat(stats.found).isFalse()
        assertThat(AlarmStats.parse("  Alarm Stats:\n  garbage line\n").unparsed).containsExactly("  garbage line")
    }

    @Test fun uidsAsUserHandlePrintsThem() {
        assertThat(FormattedUid.parse("1000")).isEqualTo(1000)
        assertThat(FormattedUid.parse("-1")).isEqualTo(-1)
        assertThat(FormattedUid.parse("u0a357")).isEqualTo(10_357)
        assertThat(FormattedUid.parse("u10a5")).isEqualTo(1_010_005)
        assertThat(FormattedUid.parse("u0i12")).isEqualTo(99_012)
        assertThat(FormattedUid.parse("u0ai3")).isEqualTo(90_003)
        assertThat(FormattedUid.parse("u0p1")).isEqualTo(30_001)
        assertThat(FormattedUid.parse("u10s1000")).isEqualTo(1_001_000)
        assertThat(FormattedUid.parse("u0x1")).isNull()
    }
}
