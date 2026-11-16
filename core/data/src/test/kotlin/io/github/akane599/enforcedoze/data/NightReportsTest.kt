// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.profile.Nights
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

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

    private val zone = ZoneId.of("Europe/Istanbul")

    /** 23:00 on 1 November 2026 in Istanbul (UTC+3), plus [minutes]. */
    private fun at(minutes: Long) = ZonedDateTime.of(2026, 11, 1, 23, 0, 0, 0, zone).toInstant().toEpochMilli() + minutes * 60_000

    private fun part(id: Long, from: Long, to: Long, batteryStart: Int? = null, batteryEnd: Int? = null) = session().copy(
        id = id,
        start = at(from),
        end = at(to),
        batteryStart = batteryStart,
        batteryEnd = batteryEnd,
    )

    @Test fun aNightIsFourHoursOfUnpluggedScreenOff() {
        assertThat(StoredNights.nights(listOf(part(1, 0, 240)))).hasSize(1)
        assertThat(StoredNights.nights(listOf(part(1, 0, 239)))).isEmpty()
        assertThat(StoredNights.nights(listOf(part(1, 0, 480).copy(chargedMillis = 60_000)))).isEmpty()
        assertThat(StoredNights.nights(listOf(part(1, 0, 480).copy(pluggedAtStart = true)))).isEmpty()
        assertWithMessage("the compressed night of the debug build").that(
            StoredNights.nights(listOf(part(1, 0, 5)), Nights.Rule.compressed(Duration.ofMinutes(4))),
        ).hasSize(1)
    }

    @Test fun shortWakeUpsJoinOneNightAndAChargeBetweenEndsIt() {
        val first = part(1, 0, 180, batteryStart = 80, batteryEnd = 78)
        val second = part(2, 185, 480, batteryStart = 78, batteryEnd = 74)
        assertWithMessage("3 h + 4 h 55 min of screen off, 5 min awake between").that(StoredNights.nights(listOf(second, first)))
            .containsExactly(listOf(first, second))
        assertThat(StoredNights.wakes(listOf(first, second))).containsExactly(Span(at(180), at(185)))
        val charged = second.copy(batteryStart = 85)
        assertWithMessage("the battery rose while the screen was on").that(StoredNights.nights(listOf(first, charged)))
            .containsExactly(listOf(charged))
    }

    @Test fun aNightIsARestAtAnyHourAndTheLongerOfTwoWithin12Hours() {
        // 23:00 plus 15 h is 14:00 the next day.
        val daySleep = part(1, 15 * 60, 15 * 60 + 300)
        assertThat(StoredNights.nights(listOf(daySleep))).containsExactly(listOf(daySleep))
        val nap = part(2, 15 * 60 + 360, 15 * 60 + 600)
        assertWithMessage("a 4 h rest 6 h after a 5 h one is a nap").that(StoredNights.nights(listOf(daySleep, nap)))
            .containsExactly(listOf(daySleep))
    }

    @Test fun aLongerRestTakesTheNightsPlaceAndAShorterOneLeavesIt() {
        val spacing = Nights.SPACING
        val napNight = NightReports.night(listOf(part(1, -9 * 60, -9 * 60 + 270)), 0, emptyList(), emptyList()).copy(id = 3)
        val sleep = listOf(part(2, 0, 480))
        assertThat(StoredNights.decide(sleep, listOf(napNight), spacing)).isEqualTo(StoredNights.Decision.Write(listOf(napNight)))
        val longNight = napNight.copy(screenOffMillis = 8 * hour)
        assertWithMessage("a tie keeps the night already there").that(StoredNights.decide(sleep, listOf(longNight), spacing))
            .isEqualTo(StoredNights.Decision.Leave)
        val farNight = napNight.copy(start = at(-12 * 60))
        assertWithMessage("12 h apart is another night").that(StoredNights.decide(sleep, listOf(farNight), spacing))
            .isEqualTo(StoredNights.Decision.Write(emptyList()))
    }

    @Test fun theSameFirstSessionGrowsItsNightOrLeavesIt() {
        val first = part(1, 0, 240)
        val row = NightReports.night(listOf(first), 0, emptyList(), emptyList()).copy(id = 9)
        assertThat(StoredNights.decide(listOf(first), listOf(row), Nights.SPACING)).isEqualTo(StoredNights.Decision.Leave)
        assertThat(StoredNights.decide(listOf(first, part(2, 245, 400)), listOf(row), Nights.SPACING))
            .isEqualTo(StoredNights.Decision.Grow(row))
    }

    @Test fun theComparisonAveragesWhatEachNightHas() {
        val nights = listOf(
            NightReports.night(listOf(session(deepHours = 6.0, drain = 0.2)), 0, emptyList(), emptyList()),
            NightReports.night(listOf(session(deepHours = 4.0, drain = null)), 0, emptyList(), emptyList()),
            NightReports.night(listOf(session(verdict = null, deepHours = 0.0, drain = 0.6)), 0, emptyList(), emptyList()),
        )
        val comparison = NightReports.compare(nights)
        assertThat(comparison.nights).isEqualTo(3)
        assertThat(comparison.drainPerHour).isWithin(1e-9).of(0.4)
        assertWithMessage("an unjudged night hides part of its deep idle").that(comparison.deepShare).isWithin(1e-9).of(0.625)
        assertThat(NightReports.compare(emptyList())).isEqualTo(Comparison(0, null, null))
    }

    @Test fun theNightKeepsItsFiguresAndItsComparison() {
        val earlier = NightReports.night(listOf(session(drain = 0.3)), 0, emptyList(), emptyList())
        val night = NightReports.night(listOf(session()), unlockedAt = 9 * hour, recent = listOf(earlier), stock = emptyList())
        assertThat(night).isEqualTo(
            NightRow(
                sessionId = 7,
                start = 0,
                end = 8 * hour,
                sessionCount = 1,
                screenOffMillis = 8 * hour,
                drainPerHour = 0.25,
                deepShare = 0.75,
                profileId = "everyday",
                unlockedAt = 9 * hour,
                recentNights = 1,
                recentDrainPerHour = 0.3,
                recentDeepShare = 0.75,
                stockNights = 0,
                stockDrainPerHour = null,
                stockDeepShare = null,
            ),
        )
    }

    @Test fun aJoinedNightCountsItsSessionsNotTheWakeUps() {
        val first = part(1, 0, 120).copy(drainPerHour = 0.6, timeInDeepMillis = hour)
        val second = part(2, 125, 485).copy(drainPerHour = 0.2, timeInDeepMillis = 5 * hour, profileId = "stock")
        val night = NightReports.night(listOf(first, second), 0, emptyList(), emptyList())
        assertThat(night.start).isEqualTo(first.start)
        assertThat(night.end).isEqualTo(second.end)
        assertThat(night.sessionCount).isEqualTo(2)
        assertThat(night.screenOffMillis).isEqualTo(8 * hour)
        assertWithMessage("weighted by length: (0.6 × 2 + 0.2 × 6) / 8").that(night.drainPerHour).isWithin(1e-9).of(0.3)
        assertThat(night.deepShare).isWithin(1e-9).of(0.75)
        assertWithMessage("two profiles: no baseline for either").that(night.profileId).isNull()
    }

    @Test fun theNightsVerdictJoinsItsSessions() {
        fun verdict(vararg verdicts: String?) = NightReports.verdict(verdicts.map { session(verdict = it) }).first
        assertThat(verdict(EvidenceRows.HELD, EvidenceRows.HELD)).isEqualTo(EvidenceRows.HELD)
        assertWithMessage("an unjudged part does not count").that(verdict(EvidenceRows.HELD, null)).isEqualTo(EvidenceRows.HELD)
        assertThat(verdict(EvidenceRows.HELD, EvidenceRows.PARTLY)).isEqualTo(EvidenceRows.PARTLY)
        assertThat(verdict(EvidenceRows.HELD, EvidenceRows.DID_NOT_DOZE)).isEqualTo(EvidenceRows.PARTLY)
        assertThat(verdict(EvidenceRows.DID_NOT_DOZE, EvidenceRows.DID_NOT_DOZE)).isEqualTo(EvidenceRows.DID_NOT_DOZE)
        assertThat(verdict(null, null)).isNull()
        val undozed = listOf(
            session(hours = 2, verdict = EvidenceRows.DID_NOT_DOZE).copy(cause = "MOTION"),
            session(hours = 5, verdict = EvidenceRows.DID_NOT_DOZE).copy(cause = "HOLD_OFF"),
        )
        assertWithMessage("the longest part's cause").that(NightReports.verdict(undozed).second).isEqualTo("HOLD_OFF")
    }

    @Test fun aJoinedNightSumsItsWakersOncePerApp() {
        val merged = NightWakers.merge(
            listOf(
                listOf(WakerRow(uid = 10_050, name = "com.x", alarmWakeups = 1, alarms = "1 1 sync\n0 2 tick")),
                listOf(
                    WakerRow(uid = 10_060, name = "com.y", alarmWakeups = 2),
                    WakerRow(uid = 10_050, name = "com.x", alarmWakeups = 2, wakelockMillis = 5, alarms = "2 2 sync"),
                ),
            ),
        )
        assertThat(merged.map { it.name to it.alarmWakeups }).containsExactly("com.x" to 3, "com.y" to 2).inOrder()
        assertThat(merged.first().alarms).isEqualTo("3 3 sync\n0 2 tick")
        val one = listOf(WakerRow(uid = 1, name = "a"), WakerRow(uid = 2, name = "b", alarmWakeups = 9))
        assertWithMessage("one session keeps the recorder's order").that(NightWakers.merge(listOf(one))).isEqualTo(one)
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
        EventRow(time = -60_000, kind = JournalRows.HOLD_OFF_STARTED, holdOff = "AUDIO"),
        EventRow(time = 10, kind = JournalRows.HOLD_OFF_ENDED, holdOff = "AUDIO"),
        EventRow(time = 20, kind = JournalRows.DRIFT, lever = "CONSTANTS", writer = "com.other"),
        EventRow(time = 30, kind = JournalRows.MODE, mode = "PAUSED"),
    )

    private val actions = listOf(
        ActionRow(
            time = -114,
            lever = "FORCE_IDLE",
            command = "force-idle",
            expected = "forced",
            readBack = "forced",
            outcome = "OK",
            cause = "SCREEN_OFF",
            detail = "",
        ),
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
        val night = NightReports.night(listOf(row), 9 * hour, emptyList(), emptyList())
        val report = NightReports.report(night, listOf(record), events, actions)

        assertThat(report.verdict).isEqualTo(EvidenceRows.HELD)
        assertThat(report.timeInDeepMillis).isEqualTo(6 * hour)
        assertThat(report.wakes).isEmpty()
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
        assertWithMessage("the engine's screen-off write logged just before the recorder's start belongs to the night")
            .that(report.actions.map { it.time }).containsExactly(-114L, 5L).inOrder()
    }

    @Test fun aJoinedNightsReportCoversEverySession() {
        val second = row.copy(id = 8, start = 8 * hour + 300_000, end = 10 * hour, timeline = "IDLE 29100000 36000000", breaks = "")
        val night = NightReports.night(listOf(row, second), 10 * hour, emptyList(), emptyList())
        val report = NightReports.report(night, listOf(record.copy(session = second, transitions = emptyList()), record), events, actions)
        assertThat(report.sessions.map { it.id }).containsExactly(7L, 8L).inOrder()
        assertThat(report.wakes).containsExactly(Span(8 * hour, 8 * hour + 300_000))
        assertThat(report.timeline.map { it.state }).containsExactly("INACTIVE", "IDLE", "IDLE_MAINTENANCE", "IDLE").inOrder()
        assertWithMessage("the screen-on release at 09:00 is inside the joined night").that(report.actions.map { it.time })
            .containsExactly(-114L, 5L, 9 * hour).inOrder()
        assertThat(report.apps.single().alarmWakeups).isEqualTo(6)
    }
}
