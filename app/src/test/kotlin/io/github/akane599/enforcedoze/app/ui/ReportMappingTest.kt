// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.ActionRow
import io.github.akane599.enforcedoze.data.EventRow
import io.github.akane599.enforcedoze.data.EvidenceRows
import io.github.akane599.enforcedoze.data.JournalRows
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SensorSnapshotRow
import io.github.akane599.enforcedoze.data.SessionAssembly
import io.github.akane599.enforcedoze.data.SessionRecord
import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.data.TransitionRow
import io.github.akane599.enforcedoze.data.WakerRow
import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.StripBreak
import io.github.akane599.enforcedoze.design.StripSegment
import io.github.akane599.enforcedoze.design.StripWake
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.feature.report.AppWaker
import io.github.akane599.enforcedoze.feature.report.Baseline
import io.github.akane599.enforcedoze.feature.report.BreakReason
import io.github.akane599.enforcedoze.feature.report.ComparedNight
import io.github.akane599.enforcedoze.feature.report.DozeChange
import io.github.akane599.enforcedoze.feature.report.EngineAction
import io.github.akane599.enforcedoze.feature.report.KernelWaker
import io.github.akane599.enforcedoze.feature.report.LateCandidate
import io.github.akane599.enforcedoze.feature.report.RawTitle
import io.github.akane599.enforcedoze.feature.report.ReportState
import io.github.akane599.enforcedoze.feature.report.SensorHold
import io.github.akane599.enforcedoze.feature.report.SensorMoment
import io.github.akane599.enforcedoze.feature.report.Stop
import io.github.akane599.enforcedoze.feature.report.StopEvent
import io.github.akane599.enforcedoze.ui.VerdictWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Illustrative records only: invented apps under com.example, times counted in minutes from an arbitrary instant. */
class ReportMappingTest {
    @Test
    fun heldNightSummary() {
        val (night, state) = heldNight()

        val summary = state.summary
        assertTrue(summary.night)
        assertEquals(1L, summary.sessionId)
        assertEquals(at(0), summary.start)
        assertEquals(at(440), summary.end)
        assertEquals("Night", summary.profile)
        assertEquals(VerdictWord.HELD, summary.verdict)
        assertNull(summary.cause)
        assertNull(summary.lateBy)
        assertEquals((188 + 209) * MIN, summary.timeInDeep)
        assertEquals(1, summary.maintenanceWindows)
        assertEquals(24 * MIN, summary.timeToDeep)
        assertEquals(0, summary.ownWakeups)
        assertEquals(70.0, summary.drainMah!!, DELTA)
        assertEquals(night.drainPerHour!!, summary.drainPerHour!!, DELTA)
        // Both sessions were judged: 436 min of screen off, the 4 min wake-up left out.
        assertEquals(397.0 / 436, summary.deepShare!!, DELTA)
        assertEquals((19.0 + 10) / 436, summary.lightShare!!, DELTA)
        assertEquals((5.0 + 5) / 436, summary.activeShare!!, DELTA)
    }

    @Test
    fun heldNightWithAWakeBetweenItsSessions() {
        val state = heldNight().second
        val summary = state.summary

        assertEquals(
            listOf(
                StripSegment(DozeFill.ACTIVE, at(0), at(5)),
                StripSegment(DozeFill.LIGHT, at(5), at(24)),
                StripSegment(DozeFill.DEEP, at(24), at(212)),
                StripSegment(DozeFill.ACTIVE, at(216), at(221)),
                StripSegment(DozeFill.LIGHT, at(221), at(231)),
                StripSegment(DozeFill.DEEP, at(231), at(440)),
            ),
            summary.strip.segments,
        )
        assertEquals(listOf(StripWake(at(212), at(216))), summary.strip.wakes)
        assertEquals(listOf(at(90)), summary.strip.maintenance)

        assertEquals(
            listOf(
                Stop(at(0), StopEvent.ScreenOff),
                Stop(at(5), StopEvent.Light),
                Stop(at(24), StopEvent.Deep(), due = at(23), late = MIN),
                Stop(at(90), StopEvent.Maintenance(lasted = MIN)),
                Stop(at(212), StopEvent.Wake(lasted = 4 * MIN)),
                Stop(at(221), StopEvent.Light),
                Stop(at(231), StopEvent.Deep(again = true), due = at(231)),
                Stop(at(440), StopEvent.ScreenOn),
            ),
            state.timetable,
        )
    }

    @Test
    fun heldNightComparedAndWakers() {
        val (night, state) = heldNight()
        val summary = state.summary

        val compared = state.compared!!
        assertEquals(ComparedNight(at(0), summary.strip, VerdictWord.HELD, night.drainPerHour), compared.nights[0])
        assertEquals(2, compared.nights.size)
        val earlier = compared.nights[1]
        assertEquals(at(-1440), earlier.start)
        assertEquals(VerdictWord.PARTLY, earlier.verdict)
        assertEquals(0.6, earlier.drainPerHour!!, DELTA)
        assertEquals(
            listOf(StripSegment(DozeFill.ACTIVE, at(-1440), at(-1410)), StripSegment(DozeFill.DEEP, at(-1410), at(-1000))),
            earlier.strip.segments,
        )
        assertEquals(Baseline(1, 0.6, 0.7), compared.recent)
        assertEquals(Baseline(0, null, null), compared.stock)

        // The courier's wakeups summed over both sessions: 3 alarm and 1 network.
        assertEquals(AppWaker("Courier", "com.example.courier", wakeups = 4, wakelock = 4_000), state.apps.first())
        assertEquals(listOf(KernelWaker("wlan_rx_wake", 3)), state.kernel)
        assertEquals(
            listOf(EngineAction(at(0), Lever.FORCE_IDLE, "cmd deviceidle force-idle deep", "IDLE", ok = true, detail = "SCREEN_OFF")),
            state.actions,
        )
        assertEquals(listOf(LateCandidate("Courier", "com.example.courier")), state.lateCandidates)
    }

    @Test
    fun sessionWithAGapAndAHistoryFilledStretch() {
        val state = ReportMapping.session(gapped, emptyList(), emptyList(), names)

        assertEquals(
            listOf(
                StripSegment(DozeFill.ACTIVE, at(0), at(5)),
                StripSegment(DozeFill.LIGHT, at(5), at(10)),
                StripSegment(DozeFill.DEEP, at(10), at(50)),
                // The history said off here, though the event log's last light state was OVERRIDE.
                StripSegment(DozeFill.ACTIVE, at(50), at(55)),
                StripSegment(DozeFill.LIGHT, at(55), at(65)),
                StripSegment(DozeFill.DEEP, at(65), at(70)),
                StripSegment(DozeFill.MISSING, at(70), at(90)),
                StripSegment(DozeFill.DEEP, at(90), at(120)),
            ),
            state.summary.strip.segments,
        )
        assertEquals(
            listOf(
                Stop(at(0), StopEvent.ScreenOff),
                Stop(at(5), StopEvent.Light),
                Stop(at(10), StopEvent.Deep(), due = at(9), late = MIN),
                Stop(at(40), StopEvent.Filled(until = at(60))),
                Stop(at(55), StopEvent.Light),
                Stop(at(65), StopEvent.Deep(again = true)),
                Stop(at(70), StopEvent.Gap(until = at(90))),
                Stop(at(120), StopEvent.ScreenOn),
            ),
            state.timetable,
        )
    }

    @Test
    fun aGappedSessionIsNotJudgedAndKeepsItsRecords() {
        val state = ReportMapping.session(gapped, emptyList(), emptyList(), names)
        val note = gapped.session.note

        val summary = state.summary
        assertEquals(false, summary.night)
        assertEquals(VerdictWord.NOT_JUDGED, summary.verdict)
        assertEquals(note, summary.note)
        assertEquals(10 * MIN, summary.timeToDeep)
        // Not judged: a gap hides part of the session, so no share is shown.
        assertNull(summary.deepShare)
        assertNull(summary.lightShare)
        assertNull(summary.activeShare)
        assertNull(state.compared)

        val raw = state.raw.associate { it.title to it.lines }
        // As stored: the two IDLE lines the history's edge split stay two.
        assertEquals(gapped.session.timeline.lines(), raw[RawTitle.TIMELINE])
        assertEquals(5, raw[RawTitle.TIMELINE]?.size)
        assertEquals("${at(2)} ACTIVE → INACTIVE screen EVENT_LOG", raw[RawTitle.TRANSITIONS]?.first())
        assertEquals(5, raw[RawTitle.TRANSITIONS]?.size)
        assertTrue(raw.getValue(RawTitle.COVERAGE).containsAll(listOf("gap ${at(70)} ${at(90)}", "historyFilled ${at(40)} ${at(60)}")))
        assertEquals(listOf(note!!), raw[RawTitle.NOTES])
        assertNull(raw[RawTitle.BREAKS])
    }

    @Test
    fun missingFiguresStayNull() {
        val row = row(8, at(0), at(9)).copy(verdict = null, profileId = null, note = "shorter than 600 s: not judged")
        val wakers = listOf(
            WakerRow(sessionId = 8, uid = 10003, name = "", alarmWakeups = 1),
            WakerRow(sessionId = 8, uid = 10004, name = "com.example.unknown", networkWakeups = 2),
        )

        val state = ReportMapping.session(SessionRecord(row, emptyList(), wakers, emptyList()), emptyList(), emptyList(), names)

        val summary = state.summary
        assertEquals(VerdictWord.NOT_JUDGED, summary.verdict)
        assertNull(summary.profile)
        assertNull(summary.timeToDeep)
        assertNull(summary.lateBy)
        assertNull(summary.deepShare)
        assertNull(summary.lightShare)
        assertNull(summary.activeShare)
        assertNull(summary.drainPerHour)
        assertNull(summary.drainMah)
        assertNull(summary.ownWakeups)
        // No timeline was stored, so nothing is drawn.
        assertTrue(summary.strip.segments.isEmpty())
        assertEquals(listOf(Stop(at(0), StopEvent.ScreenOff), Stop(at(9), StopEvent.ScreenOn)), state.timetable)
        // An app the lookup does not know keeps the name the row holds; one with no package keeps its uid.
        assertEquals(listOf("uid 10003", "com.example.unknown"), state.apps.map { it.label })
        assertEquals(listOf(LateCandidate("com.example.unknown", "com.example.unknown")), state.lateCandidates)

        // A night: one session's own wakeups and mAh unknown makes the night's unknown too.
        val unknown = second.copy(session = second.session.copy(ownWakeups = null, drainMah = null))
        val night = NightReports.night(listOf(first.session, unknown.session), at(441), emptyList(), emptyList())
        val nightState = ReportMapping.night(
            NightReports.report(night, listOf(first, unknown), emptyList(), emptyList()),
            listOf(first, unknown),
            emptyList(),
            names,
        )
        assertNull(nightState.summary.ownWakeups)
        assertNull(nightState.summary.drainMah)
        assertEquals(Baseline(0, null, null), nightState.compared?.recent)
        assertEquals(1, nightState.compared?.nights?.size)
    }

    @Test
    fun driftWithItsWriter() {
        val state = ReportMapping.session(broken, brokenEvents, emptyList(), names)

        assertEquals(
            listOf(
                DozeChange.HoldOff(at(25), "audio playing", until = at(40)),
                DozeChange.Drift(at(59), "CONSTANTS", found = "light_idle_to=300000", expected = "light_idle_to=900000", writer = "Tuner"),
            ),
            state.changes,
        )
        assertEquals(
            listOf(
                Stop(at(0), StopEvent.ScreenOff),
                Stop(at(5), StopEvent.Light),
                Stop(at(10), StopEvent.Deep(), due = at(9), late = MIN),
                Stop(at(25), StopEvent.Break(BreakReason.HOLD_OFF, subject = "audio playing", awake = 15 * MIN)),
                Stop(at(40), StopEvent.Deep(again = true)),
                Stop(at(60), StopEvent.Break(BreakReason.ANOTHER_MANAGER, subject = "Tuner", awake = 8 * MIN)),
                Stop(at(68), StopEvent.Light),
                Stop(at(75), StopEvent.Deep(again = true)),
                Stop(at(100), StopEvent.ScreenOn),
            ),
            state.timetable,
        )
        assertEquals(listOf(StripBreak(at(25), "unforced"), StripBreak(at(60), "unforced")), state.summary.strip.breaks)
        assertEquals(VerdictWord.PARTLY, state.summary.verdict)
    }

    @Test
    fun aWriterTheLookupDoesNotKnowKeepsItsPackage() {
        val unknown = ReportMapping.session(broken, brokenEvents, emptyList(), ReportNames(profile = { it }, app = { _, _ -> null }))

        // Shown as the package the settings dump named, and the hold-off by its stored name.
        assertEquals("com.example.tuner", (unknown.changes.last() as DozeChange.Drift).writer)
        assertEquals("com.example.tuner", (unknown.timetable[5].event as StopEvent.Break).subject)
        assertEquals("AUDIO", (unknown.changes.first() as DozeChange.HoldOff).name)
    }

    @Test
    fun selfWakerIsFlagged() {
        val row = row(12, at(0), at(300))
        val wakers = listOf(
            WakerRow(sessionId = 12, uid = 10001, name = "com.example.courier", alarmWakeups = 2, wakelockMillis = 2_000),
            WakerRow(sessionId = 12, uid = 10002, name = SELF, self = true, alarmWakeups = 1, wakelockMillis = 500),
            WakerRow(sessionId = 12, uid = null, name = "wlan_rx_wake", kernelWakeups = 3),
        )
        val snapshots = listOf(
            snapshot(at(0), SessionAssembly.SCREEN_OFF, "10001\tcom.example.courier\tAccelerometer\t20000"),
            snapshot(at(120), SessionAssembly.COPY, "10001\tcom.example.courier\tAccelerometer\t20000"),
            snapshot(at(300), SessionAssembly.SCREEN_ON, "10005\tcom.example.steps\tStep Counter\t-"),
        )
        val unverified = ActionRow(
            time = at(0),
            lever = "SENSORS",
            command = "dumpsys sensorservice restrict $SELF",
            expected = "RESTRICTED",
            readBack = null,
            outcome = "UNVERIFIED",
            cause = "SCREEN_OFF",
            detail = "",
        )

        val state = ReportMapping.session(SessionRecord(row, emptyList(), wakers, snapshots), emptyList(), listOf(unverified), names)

        assertEquals(
            listOf(
                AppWaker("Courier", "com.example.courier", wakeups = 2, wakelock = 2_000),
                AppWaker("EnforceDoze", SELF, wakeups = 1, wakelock = 500, self = true),
            ),
            state.apps,
        )
        assertEquals(listOf(KernelWaker("wlan_rx_wake", 3)), state.kernel)
        assertEquals(listOf(LateCandidate("Courier", "com.example.courier")), state.lateCandidates)
        assertEquals(
            listOf(
                SensorHold("Courier", "Accelerometer", rateHz = 50.0, moments = listOf(SensorMoment.SCREEN_OFF, SensorMoment.MAINTENANCE)),
                SensorHold("com.example.steps", "Step Counter", rateHz = null, moments = listOf(SensorMoment.SCREEN_ON)),
            ),
            state.sensors,
        )
        // Written but not read back is not applied (SPEC 0.2).
        assertEquals(
            listOf(EngineAction(at(0), Lever.SENSORS, unverified.command, null, ok = false, detail = "SCREEN_OFF · UNVERIFIED")),
            state.actions,
        )
    }

    @Test
    fun storedVerdictsAndCausesMapAsStored() {
        assertEquals(VerdictWord.HELD, ReportMapping.verdict(EvidenceRows.HELD))
        assertEquals(VerdictWord.PARTLY, ReportMapping.verdict(EvidenceRows.PARTLY))
        assertEquals(VerdictWord.DID_NOT_DOZE, ReportMapping.verdict(EvidenceRows.DID_NOT_DOZE))
        assertEquals(VerdictWord.NOT_JUDGED, ReportMapping.verdict(null))
        assertEquals(VerdictWord.NOT_JUDGED, ReportMapping.verdict("SOMETHING_ELSE"))
        assertEquals(Cause.ANOTHER_MANAGER, ReportMapping.cause("ANOTHER_MANAGER"))
        assertNull(ReportMapping.cause(null))
        assertNull(ReportMapping.cause("SOMETHING_ELSE"))
    }

    private companion object {
        const val MIN = 60_000L
        const val DELTA = 1e-9
        const val SELF = "io.github.akane599.enforcedoze"

        /** An arbitrary instant; nothing depends on the date. */
        const val T0 = 1_790_000_000_000L

        fun at(minutes: Long): Long = T0 + minutes * MIN

        fun line(state: String, from: Long, to: Long) = "$state ${at(from)} ${at(to)}"

        /** [first] and [second] as one night, compared with [earlierRow], with the engine's screen-off write. */
        fun heldNight(): Pair<NightRow, ReportState> {
            val night = NightReports.night(listOf(first.session, second.session), at(441), recent = listOf(earlierRow), stock = emptyList())
                .copy(id = 7)
            val records = listOf(first, second)
            val screenOff = ActionRow(
                time = at(0),
                lever = "FORCE_IDLE",
                command = "cmd deviceidle force-idle deep",
                expected = "IDLE",
                readBack = "IDLE",
                outcome = "OK",
                cause = "SCREEN_OFF",
                detail = "",
            )
            val report = NightReports.report(night, records, emptyList(), listOf(screenOff))
            return night to ReportMapping.night(report, records, listOf(EarlierNight(earlierRow, listOf(earlierRecord))), names)
        }

        val names = ReportNames(
            profile = { if (it == "night") "Night" else it },
            app = { _, packages ->
                when (packages.firstOrNull()) {
                    "com.example.courier" -> AppName("Courier", "com.example.courier")
                    "com.example.tuner" -> AppName("Tuner", "com.example.tuner")
                    SELF -> AppName("EnforceDoze", SELF)
                    else -> null
                }
            },
            holdOff = { if (it == "AUDIO") "audio playing" else it },
        )

        fun row(id: Long, start: Long, end: Long) = SessionRow(
            id = id,
            start = start,
            end = end,
            profileId = "night",
            pluggedAtStart = false,
            verdict = EvidenceRows.HELD,
            cause = null,
            expectedToDeepMillis = null,
            timeToDeepMillis = null,
            lateByMillis = null,
            deepShare = 0.0,
            maintenanceWindows = 0,
            motionResets = 0,
            breaks = "",
            note = null,
        )

        fun light(session: Long, minute: Long, from: String?, to: String, reason: String) = TransitionRow(
            sessionId = session,
            time = at(minute),
            deep = false,
            fromState = from,
            toState = to,
            reason = reason,
            source = "EVENT_LOG",
        )

        fun history(session: Long, minute: Long, from: String?, to: String) = TransitionRow(
            sessionId = session,
            time = at(minute),
            deep = true,
            fromState = from,
            toState = to,
            reason = "history",
            source = "HISTORY",
        )

        fun snapshot(time: Long, moment: String, connections: String) =
            SensorSnapshotRow(time = time, moment = moment, mode = "NORMAL", privacy = false, connections = connections)

        /** 23:40-ish to 03:12: light idle after 5 min, deep idle 1 min late, one maintenance window. */
        val first = SessionRecord(
            row(1, at(0), at(212)).copy(
                expectedToDeepMillis = 23 * MIN,
                timeToDeepMillis = 24 * MIN,
                timeInDeepMillis = 188 * MIN,
                maintenanceWindows = 1,
                drainPerHour = 0.4,
                drainMah = 40.0,
                ownWakeups = 0,
                timeline = listOf(line("INACTIVE", 0, 24), line("IDLE", 24, 90), line("IDLE_MAINTENANCE", 90, 91), line("IDLE", 91, 212))
                    .joinToString("\n"),
            ),
            transitions = listOf(
                light(1, 1, "ACTIVE", "INACTIVE", "screen"),
                light(1, 5, "INACTIVE", "IDLE", "step"),
                light(1, 24, "IDLE", "OVERRIDE", "deep"),
            ),
            wakers = listOf(
                WakerRow(
                    sessionId = 1,
                    uid = 10001,
                    name = "com.example.courier",
                    alarmWakeups = 2,
                    networkWakeups = 1,
                    wakelockMillis = 3_000,
                ),
                WakerRow(sessionId = 1, uid = 10002, name = SELF, self = true),
                WakerRow(sessionId = 1, uid = null, name = "wlan_rx_wake", kernelWakeups = 3),
            ),
            snapshots = emptyList(),
        )

        /** After a 4 min look at the clock: back to deep idle on time. */
        val second = SessionRecord(
            row(2, at(216), at(440)).copy(
                expectedToDeepMillis = 15 * MIN,
                timeToDeepMillis = 15 * MIN,
                timeInDeepMillis = 209 * MIN,
                drainPerHour = 0.5,
                drainMah = 30.0,
                ownWakeups = 0,
                timeline = listOf(line("INACTIVE", 216, 231), line("IDLE", 231, 440)).joinToString("\n"),
            ),
            transitions = listOf(
                light(2, 217, "ACTIVE", "INACTIVE", "screen"),
                light(2, 221, "INACTIVE", "IDLE", "step"),
                light(2, 231, "IDLE", "OVERRIDE", "deep"),
            ),
            wakers = listOf(WakerRow(sessionId = 2, uid = 10001, name = "com.example.courier", alarmWakeups = 1, wakelockMillis = 1_000)),
            snapshots = emptyList(),
        )

        /** Two hours with a stretch the battery history filled (40 to 60) and one nothing covered (70 to 90). */
        val gapped = SessionRecord(
            row(5, at(0), at(120)).copy(
                verdict = null,
                note = "no Doze record for 20 min: not judged",
                expectedToDeepMillis = 9 * MIN,
                timeToDeepMillis = 10 * MIN,
                timeInDeepMillis = 75 * MIN,
                drainPerHour = 0.3,
                timeline = listOf(
                    line("INACTIVE", 0, 10),
                    line("IDLE", 10, 40),
                    line("IDLE", 40, 50),
                    line("INACTIVE", 50, 65),
                    line("IDLE", 65, 120),
                ).joinToString("\n"),
                gaps = "${at(70)} ${at(90)}",
                historyFilled = "${at(40)} ${at(60)}",
            ),
            transitions = listOf(
                light(5, 2, "ACTIVE", "INACTIVE", "screen"),
                light(5, 5, "INACTIVE", "IDLE", "step"),
                light(5, 10, "IDLE", "OVERRIDE", "deep"),
                // The battery history in the stretch the event log lost: full, then off, then light.
                history(5, 40, "light", "full"),
                history(5, 50, "full", "off"),
                history(5, 55, "off", "light"),
                light(5, 60, "OVERRIDE", "IDLE", "step"),
                light(5, 65, "IDLE", "OVERRIDE", "deep"),
            ),
            wakers = emptyList(),
            snapshots = emptyList(),
        )

        /** Deep idle broken twice: by an audio hold-off at 25, and at 60 by another app that rewrote the constants. */
        val broken = SessionRecord(
            row(11, at(0), at(100)).copy(
                verdict = EvidenceRows.PARTLY,
                expectedToDeepMillis = 9 * MIN,
                timeToDeepMillis = 10 * MIN,
                timeline = listOf(
                    line("INACTIVE", 0, 10),
                    line("IDLE", 10, 25),
                    line("ACTIVE", 25, 40),
                    line("IDLE", 40, 60),
                    line("ACTIVE", 60, 64),
                    line("INACTIVE", 64, 75),
                    line("IDLE", 75, 100),
                ).joinToString("\n"),
                breaks = "${at(25)} ACTIVE unforced HOLD_OFF\n${at(60)} ACTIVE unforced ANOTHER_MANAGER",
            ),
            transitions = listOf(
                light(11, 1, "ACTIVE", "INACTIVE", "screen"),
                light(11, 5, "INACTIVE", "IDLE", "step"),
                light(11, 10, "IDLE", "OVERRIDE", "deep"),
                light(11, 25, "OVERRIDE", "ACTIVE", "unforced"),
                light(11, 40, "ACTIVE", "OVERRIDE", "deep"),
                light(11, 60, "OVERRIDE", "ACTIVE", "unforced"),
                light(11, 68, "ACTIVE", "IDLE", "step"),
                light(11, 75, "IDLE", "OVERRIDE", "deep"),
            ),
            wakers = emptyList(),
            snapshots = emptyList(),
        )

        val drift = EventRow(
            time = at(59),
            kind = JournalRows.DRIFT,
            lever = "CONSTANTS",
            expected = "light_idle_to=900000",
            found = "light_idle_to=300000",
            writer = "com.example.tuner",
            whileStopped = false,
        )

        val brokenEvents = listOf(
            EventRow(time = at(25), kind = JournalRows.HOLD_OFF_STARTED, holdOff = "AUDIO"),
            EventRow(time = at(40), kind = JournalRows.HOLD_OFF_ENDED, holdOff = "AUDIO"),
            drift,
            // After screen on: not this session's.
            drift.copy(time = at(150)),
        )

        val earlierRow = NightRow(
            id = 3,
            sessionId = 9,
            start = at(-1440),
            end = at(-1000),
            sessionCount = 1,
            screenOffMillis = 440 * MIN,
            drainPerHour = 0.6,
            deepShare = 0.7,
            profileId = "night",
            unlockedAt = at(-999),
            recentNights = 0,
            recentDrainPerHour = null,
            recentDeepShare = null,
            stockNights = 0,
            stockDrainPerHour = null,
            stockDeepShare = null,
        )

        val earlierRecord = SessionRecord(
            row(9, at(-1440), at(-1000)).copy(
                verdict = EvidenceRows.PARTLY,
                timeline = listOf(line("INACTIVE", -1440, -1410), line("IDLE", -1410, -1000)).joinToString("\n"),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
        )
    }
}
