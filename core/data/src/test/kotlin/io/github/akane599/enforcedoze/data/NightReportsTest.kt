// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.Duration

class NightReportsTest {
    private val hour = 3_600_000L

    private fun session(hours: Long = 8, verdict: String? = EvidenceRows.HELD, deepHours: Double = 6.0, drain: Double? = 0.25) = SessionRow(
        id = 7,
        start = 0,
        end = hours * hour,
        profileId = "everyday",
        pluggedAtStart = false,
        verdict = verdict,
        cause = null,
        expectedToDeepMillis = 600_000,
        timeToDeepMillis = 700_000,
        lateByMillis = null,
        deepShare = 0.95,
        maintenanceWindows = 7,
        motionResets = 0,
        breaks = "",
        note = null,
        timeInDeepMillis = (deepHours * hour).toLong(),
        drainPerHour = drain,
    )

    @Test fun aNightIsFourUnpluggedHours() {
        val threshold = NightReports.THRESHOLD
        assertThat(NightReports.qualifies(session(hours = 4), threshold)).isTrue()
        assertThat(NightReports.qualifies(session(hours = 3), threshold)).isFalse()
        assertThat(NightReports.qualifies(session().copy(chargedMillis = 60_000), threshold)).isFalse()
        assertThat(NightReports.qualifies(session().copy(pluggedAtStart = true), threshold)).isFalse()
        assertThat(NightReports.qualifies(session(hours = 3), Duration.ofHours(2))).isTrue()
    }

    @Test fun theComparisonAveragesWhatEachNightHas() {
        val nights = listOf(
            session(deepHours = 6.0, drain = 0.2),
            session(deepHours = 4.0, drain = null),
            session(verdict = null, deepHours = 0.0, drain = 0.6),
        )
        val comparison = NightReports.compare(nights)
        assertThat(comparison.nights).isEqualTo(3)
        assertThat(comparison.drainPerHour).isWithin(1e-9).of(0.4)
        assertWithMessage("an unjudged night hides part of its deep idle").that(comparison.deepShare).isWithin(1e-9).of(0.625)
        assertThat(NightReports.compare(emptyList())).isEqualTo(Comparison(0, null, null))
    }

    @Test fun theNightKeepsItsComparison() {
        val night = NightReports.night(session(), unlockedAt = 9 * hour, recent = listOf(session(drain = 0.3)), stock = emptyList())
        assertThat(night).isEqualTo(NightRow(0, 7, 9 * hour, 1, 0.3, 0.75, 0, null, null))
    }

    private val row = session().copy(
        timeline = "INACTIVE 0 600000\nIDLE 600000 3600000\nbroken line\nIDLE_MAINTENANCE 3600000 3630000",
        breaks = "5000000 ACTIVE motion MOTION",
        gaps = "7000000 7200000",
        historyFilled = "100 200\n",
    )

    private val record = SessionRecord(
        session = row,
        transitions = listOf(
            TransitionRow(
                time = 1,
                deep = true,
                fromState = "ACTIVE",
                toState = "INACTIVE",
                reason = "no activity",
                source = "EVENT_LOG",
            ),
            TransitionRow(
                time = 2,
                deep = false,
                fromState = "ACTIVE",
                toState = "INACTIVE",
                reason = "no activity",
                source = "EVENT_LOG",
            ),
        ),
        wakers = listOf(
            WakerRow(uid = 10_050, name = "com.x", alarmWakeups = 3),
            WakerRow(uid = null, name = "170 qcom,smd-modem", kernelWakeups = 2),
        ),
        snapshots = listOf(
            SensorSnapshotRow(
                time = 3,
                moment = SessionAssembly.SCREEN_ON,
                mode = "NORMAL",
                privacy = false,
                connections = "10050\tcom.x\tStep Counter\t-",
            ),
            SensorSnapshotRow(
                time = 1,
                moment = SessionAssembly.SCREEN_OFF,
                mode = "NORMAL",
                privacy = false,
                connections = "10050\tcom.x\tStep Counter\t-\n1000\tcom.android.server.Foo\tAccelerometer\t200000\nbad",
            ),
            SensorSnapshotRow(
                time = 2,
                moment = SessionAssembly.COPY,
                mode = "NORMAL",
                privacy = false,
                connections = "10050\tcom.x\tStep Counter\t66667",
            ),
        ),
    )

    private val events = listOf(
        EventRow(time = -1, kind = JournalRows.HOLD_OFF_STARTED, holdOff = "AUDIO"),
        EventRow(time = 10, kind = JournalRows.HOLD_OFF_ENDED, holdOff = "AUDIO"),
        EventRow(time = 20, kind = JournalRows.DRIFT, lever = "CONSTANTS", writer = "com.other"),
        EventRow(time = 30, kind = JournalRows.MODE, mode = "PAUSED"),
    )

    private val actions = listOf(
        ActionRow(
            time = 5,
            lever = "CONSTANTS",
            command = "put",
            expected = "a",
            readBack = "a",
            outcome = "OK",
            cause = "DRIFT",
            detail = "",
        ),
        ActionRow(
            time = 9 * hour,
            lever = "FORCE_IDLE",
            command = "unforce",
            expected = "",
            readBack = "",
            outcome = "OK",
            cause = "SCREEN_ON",
            detail = "",
        ),
    )

    @Test fun theReportHasEveryPart() {
        val night = NightReports.night(row, 9 * hour, emptyList(), emptyList())
        val report = NightReports.report(night, record, events, actions)

        assertThat(report.timeline.map { it.state }).containsExactly("INACTIVE", "IDLE", "IDLE_MAINTENANCE").inOrder()
        assertThat(report.light.map { it.time }).containsExactly(2L)
        assertThat(report.breaks).containsExactly("5000000 ACTIVE motion MOTION")
        assertThat(report.gaps).containsExactly(Span(7_000_000, 7_200_000))
        assertThat(report.historyFilled).containsExactly(Span(100, 200))
        assertThat(report.recent).isEqualTo(Comparison(0, null, null))
        assertThat(report.apps.single().name).isEqualTo("com.x")
        assertThat(report.kernel.single().kernelWakeups).isEqualTo(2)
        assertThat(report.sensors).containsExactly(
            SensorHolder(
                10_050,
                "com.x",
                "Step Counter",
                66_667,
                listOf(SessionAssembly.SCREEN_OFF, SessionAssembly.COPY, SessionAssembly.SCREEN_ON),
            ),
            SensorHolder(1_000, "com.android.server.Foo", "Accelerometer", 200_000, listOf(SessionAssembly.SCREEN_OFF)),
        ).inOrder()
        assertWithMessage("a hold-off that began before the night shows by its end").that(
            report.holdOffs.map {
                it.time
            },
        ).containsExactly(10L)
        assertThat(report.drift.single().writer).isEqualTo("com.other")
        assertThat(report.actions.single().time).isEqualTo(5)
    }
}
