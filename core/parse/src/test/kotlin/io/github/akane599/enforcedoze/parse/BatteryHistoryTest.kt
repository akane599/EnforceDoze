// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** When the test histories were read: Format 2 lines before a `TIME:` line take the year nearest it. */
private val READ_AT = Instant.parse("2026-11-09T00:00:00Z")

class BatteryHistoryTest {
    private val utc = ZoneOffset.UTC

    private fun epoch(text: String, zone: ZoneId = utc) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun history(capture: String, build: String) =
        BatteryHistory.parse(Fixtures.capture(capture, build).text("batterystats-history.txt"), utc, READ_AT)

    private fun BatteryHistory.items() = lines.flatMap { it.items }

    @Test fun everyCaptureParsesEveryRecord() {
        Fixtures.with("batterystats-history.txt").forEach { capture ->
            val text = capture.text("batterystats-history.txt")
            val history = BatteryHistory.parse(text, utc, READ_AT)
            assertWithMessage("$capture").that(history.unparsed).isEmpty()
            assertWithMessage("$capture").that(history.format)
                .isEqualTo(if (capture.sdk >= 36) HistoryFormat.FORMAT_2 else HistoryFormat.LEGACY)
            assertWithMessage("$capture: every +job is read").that(
                history.items().count {
                    it is HistoryItem.Event && it.name == "job" &&
                        it.phase == EventPhase.START
                },
            )
                .isEqualTo(Regex("""(^| )\+job=""", RegexOption.MULTILINE).findAll(text).count())
            assertWithMessage("$capture: time never runs backwards by more than a clock change")
                .that(history.lines.zipWithNext().count { (a, b) -> b.time < a.time - 60_000 }).isAtMost(2)
            assertWithMessage("$capture").that(
                history.items().filterIsInstance<HistoryItem.Value>().map {
                    it.name
                },
            ).contains("device_idle")
        }
    }

    @Test fun format2LinesTakeTheirYearFromTheTimeLine() {
        val history = history("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3")
        val first = history.lines.first()
        assertThat(first.time).isEqualTo(epoch("2026-11-06T06:46:55.213"))
        assertThat(first.items.single()).isEqualTo(HistoryItem.Command(HistoryCommand.RESET, epoch("2026-11-06T06:46:55")))
        val second = history.lines[1]
        assertThat(second.level).isEqualTo(100)
        assertThat(second.items).containsAtLeast(
            HistoryItem.Value("status", "charging"),
            HistoryItem.Value("charge", "10"),
            HistoryItem.Flag("screen", true),
            HistoryItem.Event("userfg", EventPhase.START, 0, "0"),
        )
        assertThat(history.next).isNull()
    }

    @Test fun legacyOffsetsCountFromTheTimeLine() {
        val history = history("sdk_gphone64_x86_64", "api35_AE3A.240806.043")
        val reset = epoch("2026-11-07T23:07:32")
        assertThat(history.lines.first().items.single()).isEqualTo(HistoryItem.Command(HistoryCommand.RESET, reset))
        val light = history.lines.first { HistoryItem.Value("device_idle", "light") in it.items }
        assertThat(light.time).isEqualTo(reset + 1_147)
        assertThat(light.level).isEqualTo(100)
    }

    @Test fun phoneHistoryKeepsQuotedReasonsAndOneUiFields() {
        val history = history("sm-s938b", "api37_CP2A.260605.016")
        val events = history.items().filterIsInstance<HistoryItem.Event>()
        assertThat(events).contains(HistoryItem.Event("wake_reason", EventPhase.POINT, 0, "Abort: Pending Wakeup Sources: NETLINK"))
        assertThat(events.filter { it.name == "wakeupap" }).isNotEmpty()
        val wakeLocks = history.items().filterIsInstance<HistoryItem.Flag>().filter { it.name == "wake_lock" && it.on && it.uid != null }
        assertThat(wakeLocks).contains(HistoryItem.Flag("wake_lock", true, 1000, "PhoneWindowManager.mPowerKeyWakeLock"))
        assertThat(
            history.lines.first().items.single(),
        ).isEqualTo(HistoryItem.Command(HistoryCommand.RESET, epoch("2026-11-07T14:43:37.072")))
        assertThat(history.items()).contains(HistoryItem.Value("skin_temp", "34.6"))
    }

    @Test fun aCursorReadEndsWithNext() {
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  11-08 05:01:30.000 TIME: 2026-11-08-05-01-30
            |  11-08 05:01:31.500 087 -screen device_idle=light
            |  11-08 05:02:31.500 087 device_idle=full wakeupap=u0a42:""
            |  NEXT: 123456789
        """.trimMargin()
        val history = BatteryHistory.parse(text, ZoneId.of("Europe/Istanbul"), READ_AT)
        assertThat(history.next).isEqualTo(123_456_789)
        assertThat(BatteryHistory.nextCursor(text)).isEqualTo(123_456_789)
        assertWithMessage(
            "a read cut off before NEXT does not advance the cursor",
        ).that(BatteryHistory.nextCursor(text.substringBefore("  NEXT"))).isNull()
        assertThat(history.lines[1].time).isEqualTo(epoch("2026-11-08T05:01:31.500", ZoneId.of("Europe/Istanbul")))
        assertThat(history.lines[2].items).containsExactly(
            HistoryItem.Value("device_idle", "full"),
            HistoryItem.Event("wakeupap", EventPhase.POINT, 10_042, ""),
        ).inOrder()
    }

    @Test fun aNewYearBetweenTimeLinesKeepsTheDatesInOrder() {
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  12-31 23:59:00.000 TIME: 2026-12-31-23-59-00
            |  01-01 00:01:00.000 050 +screen
        """.trimMargin()
        val history = BatteryHistory.parse(text, utc, READ_AT)
        assertThat(history.lines[1].time).isEqualTo(epoch("2027-01-01T00:01:00"))
    }

    @Test fun aReadWithoutATimeLineTakesTheYearNearestTheRead() {
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  11-08 05:01:31.500 087 -screen device_idle=light
            |  NEXT: 42
        """.trimMargin()
        val november = BatteryHistory.parse(text, utc, Instant.parse("2026-11-08T06:00:00Z"))
        assertThat(november.lines.single().time).isEqualTo(epoch("2026-11-08T05:01:31.500"))
        val december = text.replace("11-08 05:01:31.500", "12-31 23:50:00.000")
        assertWithMessage("read just after new year")
            .that(BatteryHistory.parse(december, utc, Instant.parse("2027-01-01T00:10:00Z")).lines.single().time)
            .isEqualTo(epoch("2026-12-31T23:50:00"))
    }

    @Test fun theHourThatRepeatsWhenDaylightSavingEndsKeepsTimeMovingForward() {
        // Europe/Berlin goes from 03:00 CEST back to 02:00 CET on 2026-10-25.
        val berlin = ZoneId.of("Europe/Berlin")
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  10-25 02:30:00.000 TIME: 2026-10-25-02-30-00
            |  10-25 02:59:00.000 050 -screen
            |  10-25 02:01:00.000 050 device_idle=full
            |  10-25 03:30:00.000 050 +screen
        """.trimMargin()
        val history = BatteryHistory.parse(text, berlin, READ_AT)
        assertThat(history.lines.map { Instant.ofEpochMilli(it.time).toString() }).containsExactly(
            "2026-10-25T00:30:00Z",
            "2026-10-25T00:59:00Z",
            "2026-10-25T01:01:00Z",
            "2026-10-25T02:30:00Z",
        ).inOrder()
        assertWithMessage("the clock value takes the stamp's offset")
            .that((history.lines.first().items.single() as HistoryItem.Command).wallTime)
            .isEqualTo(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli())
    }

    @Test fun impossibleDatesAndOversizedNumbersAreUnparsedNotThrown() {
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  11-08 05:01:30.000 TIME: 2026-13-01-05-01-30
            |  02-30 05:01:31.000 087 +screen
            |  11-08 05:01:32.000 087 -screen
            |  NEXT: 99999999999999999999
        """.trimMargin()
        val history = BatteryHistory.parse(text, utc, READ_AT)
        assertThat(history.unparsed).hasSize(3)
        assertThat(history.lines.single().items).containsExactly(HistoryItem.Flag("screen", false))
        assertThat(history.next).isNull()
    }

    @Test fun tagsWithSpacesAndDoubledQuotesAreOneToken() {
        assertThat(BatteryHistory.tokens("""+wake_lock=u0a1:"say ""hi"" now" -screen""")).containsExactly(
            "+wake_lock=u0a1:\"say \"\"hi\"\" now\"",
            "-screen",
        ).inOrder()
        assertThat(BatteryHistory.item("+wake_lock=u0a1:\"say \"\"hi\"\" now\"")).isEqualTo(
            HistoryItem.Flag("wake_lock", true, 10_001, "say \"hi\" now"),
        )
        assertThat(BatteryHistory.item("-wake_lock")).isEqualTo(HistoryItem.Flag("wake_lock", false))
        assertThat(BatteryHistory.item("wake_lock=1000:\"x\"")).isEqualTo(HistoryItem.Event("wake_lock", EventPhase.POINT, 1000, "x"))
    }

    @Test fun recordsThatDoNotParseAreKeptAndOthersIgnored() {
        val text = """
            |Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):
            |  11-08 05:01:30.000 TIME: 2026-11-08-05-01-30
            |  11-08 05:01:31.000 087 +job=u0a1:"unterminated
            |  11-08 05:01:32.000 SHUTDOWN
            |Companion Device Baterry Infos:
            |    [ 000000_0, 2, 13, -1, -1, -1, 3 ]
        """.trimMargin()
        val history = BatteryHistory.parse(text, utc, READ_AT)
        assertThat(history.unparsed).hasSize(1)
        assertThat(history.lines.last().items.single()).isEqualTo(HistoryItem.Command(HistoryCommand.SHUTDOWN, null))
    }
}
