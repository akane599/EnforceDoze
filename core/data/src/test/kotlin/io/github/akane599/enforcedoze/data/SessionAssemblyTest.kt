// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.evidence.BatteryReading
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SessionAssemblyTest {
    private fun at(time: String): Long = Instant.parse("2026-11-08T${time}Z").toEpochMilli()

    private val self = 10_099
    private val start = at("23:00:00")
    private val end = at("23:30:00")

    /** One `logcat -v epoch` line. */
    private fun line(time: String, tag: String, body: String): String {
        val millis = at(time)
        return "%10d.%03d   587   587 I %s: %s".format(millis / 1000, millis % 1000, tag, body)
    }

    private val walk = listOf(
        line("22:59:00", "am_proc_start", "[0,1,2]"),
        line("22:59:30", "device_idle", "[0,screen]"),
        line("23:00:01", "device_idle", "[1,no activity]"),
        line("23:00:31", "device_idle", "[2,s:alarm]"),
        line("23:01:01", "device_idle", "[3,s:alarm]"),
        line("23:01:11", "device_idle", "[4,s:alarm]"),
        line("23:01:15", "device_idle", "[5,s:alarm]"),
        line("23:15:00", "device_idle", "[6,s:alarm]"),
        line("23:15:30", "device_idle", "[5,s:alarm]"),
    ).joinToString("\n")

    private fun history(vararg lines: String) = (
        listOf(
            "Battery History [Format: 2] (1% used, 40KB used of 4096KB, 10 strings using 1KB):",
            "  11-08 23:00:01.000 TIME: 2026-11-08-23-00-01",
            "  11-08 23:00:01.000 090 +running",
        ) + lines.map { "  $it" } + "  NEXT: 987654"
        ).joinToString("\n")

    private val quietHistory = history(
        "11-08 23:01:15.000 090 device_idle=full",
        "11-08 23:06:00.000 089 wakeupap=u0a70:\"\" wake_reason=0:\"170 qcom,smd-modem\"",
        "11-08 23:15:00.000 089 device_idle=off",
        "11-08 23:15:01.000 089 +job=u0a99:\"self/.Copy\"",
        "11-08 23:15:02.000 089 -job=u0a99:\"self/.Copy\"",
        "11-08 23:15:30.000 089 device_idle=full",
    )

    private fun alarms(wakeups: Int) = """
        |  Alarm Stats:
        |  u0a50:com.x +1s0ms running, $wakeups wakeups:
        |    +1s0ms $wakeups wakes $wakeups alarms, last -1m0s0ms:
        |      *walarm*:x.TICK
        |
    """.trimMargin()

    private val constants = mapOf(
        "inactive_to" to "60000",
        "idle_after_inactive_to" to "30000",
        "sensing_to" to "30000",
        "locating_to" to "10000",
    )

    private val screenOff =
        PendingSnapshot(start + 1_000, SessionAssembly.SCREEN_OFF, "NORMAL", false, "1000\tcom.x\tAccelerometer\t200000")

    private val open = OpenSession(
        start = start,
        profileId = "everyday",
        holdOffs = emptyList(),
        constantsApplied = true,
        batteryLevel = 90,
        chargeUah = 4_000_000,
        latchedInactiveMillis = 30_000,
        alarmsRead = true,
        snapshots = listOf(screenOff),
    )

    private fun reads(eventLog: String? = walk, history: String? = quietHistory, alarms: String? = alarms(5), deviceIdle: String? = null) =
        ScreenOnReads(eventLog, history, null, deviceIdle, alarms, readAt = end + 1_500, tookMillis = 900)

    private fun close(
        open: OpenSession = this.open,
        reads: ScreenOnReads? = reads(),
        events: List<EventRow> = emptyList(),
        constants: List<Map<String, String>> = listOf(this.constants),
        copied: List<Pair<Long, String>> = emptyList(),
    ) = SessionAssembly.close(
        SessionClose(
            open = open,
            end = end,
            battery = BatteryReading(89, 3_950_000),
            events = events,
            forcedIdle = false,
            constants = constants,
            copied = copied,
            alarmsBefore = alarms(2),
            reads = reads,
            minimum = Duration.ofMinutes(10),
            selfUid = self,
            zone = ZoneOffset.UTC,
        ),
    )

    @Test fun aHeldNightIsStoredWithEverySource() {
        val closed = close()
        val session = closed.record.session
        assertThat(session.verdict).isEqualTo(EvidenceRows.HELD)
        assertWithMessage("the latched inactive_to comes first (ADR 0016)").that(session.expectedToDeepMillis).isEqualTo(100_000)
        assertThat(session.timeToDeepMillis).isEqualTo(75_000)
        assertThat(session.maintenanceWindows).isEqualTo(1)
        assertThat(session.ownWakeups).isEqualTo(0)
        assertWithMessage("the copy job ran in a maintenance window").that(session.ownRuns).contains("outside self/.Copy")
        assertThat(session.batteryStart).isEqualTo(90)
        assertThat(session.batteryEnd).isEqualTo(89)
        assertThat(session.drainPerHour).isWithin(1e-9).of(2.0)
        assertThat(session.drainMah).isWithin(1e-9).of(50.0)
        assertThat(session.collectionMillis).isEqualTo(900)
        assertThat(session.alarmsRead).isTrue()
        assertThat(session.gaps).isEmpty()
        assertThat(session.note).isNull()
        assertThat(closed.nextCursor).isEqualTo(987_654)

        val wakers = closed.record.wakers
        assertThat(wakers.first { it.uid == 10_050 }.alarmWakeups).isEqualTo(3)
        assertThat(wakers.first { it.uid == 10_070 }.networkWakeups).isEqualTo(1)
        assertThat(wakers.single { it.uid == self }.self).isTrue()
        assertThat(wakers.single { it.uid == null }.name).isEqualTo("170 qcom,smd-modem")
        assertThat(closed.record.transitions.filter { it.deep }.map { it.toState }.first()).isEqualTo("INACTIVE")
        assertThat(closed.record.snapshots.map { it.moment }).containsExactly(SessionAssembly.SCREEN_OFF)
    }

    @Test fun aShortSessionReadsNothingAndKeepsTheCursor() {
        assertThat(SessionAssembly.tooShort(start, start + 600_000, Duration.ofMinutes(10))).isTrue()
        assertThat(SessionAssembly.tooShort(start, start + 600_001, Duration.ofMinutes(10))).isFalse()
        val closed = close(reads = null)
        assertThat(closed.nextCursor).isNull()
        assertThat(closed.record.session.verdict).isNull()
        assertThat(closed.record.session.note).isEqualTo("shorter than 600 s: not judged")
        assertThat(closed.record.session.drainPerHour).isWithin(1e-9).of(2.0)
        assertThat(closed.record.snapshots).hasSize(1)
        assertThat(closed.record.wakers).isEmpty()
    }

    @Test fun aGapWithholdsTheVerdict() {
        val rolled = walk.lines().filter { at("23:15:00") <= epoch(it) }.joinToString("\n")
        val closed = close(reads = reads(eventLog = rolled, history = null))
        val session = closed.record.session
        assertThat(session.verdict).isNull()
        assertThat(session.note).contains("no Doze record for 15 min: not judged")
        assertThat(session.note).contains("the app's own wakeups are unknown: missing the battery history")
        assertThat(session.gaps).isEqualTo("$start ${at("23:15:00")}")
        assertWithMessage("an unknown cursor is not advanced").that(closed.nextCursor).isNull()
    }

    @Test fun aDozeLineThatCouldNotBeReadWithholdsTheVerdict() {
        val vendor = walk + "\n" + line("23:20:00", "device_idle", "[8,vendor state]")
        val unreadHistory = quietHistory.replace("  NEXT:", "  11-08 23:20:00.000 089 +new_layout:x:y\n  NEXT:")
        val session = close(reads = reads(eventLog = vendor, history = unreadHistory)).record.session
        assertThat(session.verdict).isNull()
        assertThat(session.note).contains("1 Doze lines of the event log could not be read: not judged")
        assertThat(session.note).contains("1 battery history lines could not be read")
    }

    @Test fun theHistoryFillsWhatTheEventLogLost() {
        val rolled = walk.lines().filter { at("23:15:00") <= epoch(it) }.joinToString("\n")
        val session = close(reads = reads(eventLog = rolled)).record.session
        assertThat(session.verdict).isEqualTo(EvidenceRows.HELD)
        assertThat(session.historyFilled).isEqualTo("$start ${at("23:15:00")}")
        assertThat(session.gaps).isEmpty()
    }

    @Test fun copiesAtMaintenanceWindowsCloseTheGap() {
        val rolled = walk.lines().filter { at("23:15:00") <= epoch(it) }.joinToString("\n")
        val copy = at("23:15:05") to walk.lines().filter { epoch(it) <= at("23:15:00") }.joinToString("\n")
        val session = close(reads = reads(eventLog = rolled, history = null), copied = listOf(copy)).record.session
        assertThat(session.verdict).isEqualTo(EvidenceRows.HELD)
        assertThat(session.gaps).isEmpty()
    }

    @Test fun missingAlarmDumpsLeaveTheOwnWakeupsUnknown() {
        val session = close(reads = reads(alarms = null)).record.session
        assertThat(session.verdict).isEqualTo(EvidenceRows.HELD)
        assertThat(session.ownWakeups).isNull()
        assertThat(session.alarmsRead).isFalse()
        assertThat(session.note).isEqualTo("the app's own wakeups are unknown: missing the alarm dump at screen on")
    }

    @Test fun aResumedSessionNeedsTheScreenToHaveStayedOff() {
        val resumed = open.copy(resumedAt = at("23:20:00"))
        assertThat(close(open = resumed).record.session.verdict).isEqualTo(EvidenceRows.HELD)

        val woke = history("11-08 23:01:15.000 090 device_idle=full", "11-08 23:10:00.000 090 +screen", "11-08 23:10:30.000 090 -screen")
        val session = close(open = resumed, reads = reads(history = woke)).record.session
        assertThat(session.verdict).isNull()
        assertThat(session.note).contains("the screen came on in between: not judged")

        assertThat(close(open = resumed, reads = reads(history = null)).record.session.note)
            .contains("resumed after a restart and the battery history is missing: not judged")
    }

    @Test fun anUnknownWalkIsNotJudged() {
        val session = close(open = open.copy(latchedInactiveMillis = null), constants = emptyList()).record.session
        assertThat(session.expectedToDeepMillis).isNull()
        assertThat(session.verdict).isNull()
        assertThat(session.note).isEqualTo("the walk to deep idle is unknown: no stock or previous values")
    }

    @Test fun didNotDozeOnADeviceWithoutAMotionSensorSaysSo() {
        val stuck = walk.lines().take(3).joinToString("\n")
        val idle = history("11-08 23:00:02.000 090 device_idle=off")
        val noSensor = "DeviceIdleController:\n  mUseMotionSensor=true mMotionSensor=null\n"
        val session = close(reads = reads(eventLog = stuck, history = idle, deviceIdle = noSensor)).record.session
        assertThat(session.verdict).isEqualTo(EvidenceRows.DID_NOT_DOZE)
        assertThat(session.note).contains("no motion sensor")
    }

    @Test fun chargingHidesTheDrainAndBlocksDeepIdle() {
        val unplugged = EventRow(time = at("23:20:00"), kind = JournalRows.HOLD_OFF_ENDED, holdOff = "CHARGING")
        val session = close(open = open.copy(holdOffs = listOf("CHARGING")), events = listOf(unplugged)).record.session
        assertThat(session.pluggedAtStart).isTrue()
        assertThat(session.drainPerHour).isNull()
        assertThat(session.drainMah).isNull()
    }

    @Test fun holdOffSpansCloseAtTheSessionEnd() {
        val events = listOf(
            EventRow(time = at("22:00:00"), kind = JournalRows.HOLD_OFF_STARTED, holdOff = "AUDIO"),
            EventRow(time = at("23:05:00"), kind = JournalRows.HOLD_OFF_STARTED, holdOff = "CALL"),
            EventRow(time = at("23:06:00"), kind = JournalRows.HOLD_OFF_ENDED, holdOff = "CALL"),
            EventRow(time = at("23:10:00"), kind = JournalRows.HOLD_OFF_ENDED, holdOff = "CAR_MODE"),
        )
        val spans = SessionAssembly.holdOffSpans(
            listOf("CAR_MODE", "HOTSPOT"),
            events,
            Instant.ofEpochMilli(start),
            Instant.ofEpochMilli(end),
        )
        assertThat(spans.map { Triple(it.name, it.interval.start.toEpochMilli(), it.interval.end.toEpochMilli()) }).containsExactly(
            Triple("CALL", at("23:05:00"), at("23:06:00")),
            Triple("CAR_MODE", start, at("23:10:00")),
            Triple("HOTSPOT", start, end),
        )
    }

    private fun epoch(line: String): Long {
        val (seconds, millis) = line.trim().substringBefore(' ').split('.')
        return seconds.toLong() * 1000 + millis.toLong()
    }
}
