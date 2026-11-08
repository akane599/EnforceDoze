// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.StripBreak
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.design.StripSegment
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The night of 7 to 8 November 2026 in Istanbul, as Home and the direction study draw it (ADR 0028), with
 * the seven nights before it. App names are invented; none is from a real phone (ADR 0008).
 */
internal object ReportFixtures {
    private val zone = ZoneId.of("Europe/Istanbul")

    fun at(day: Int, hour: Int, minute: Int, month: Int = 11): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private const val MIN = 60_000L
    private val off = at(7, 23, 41)
    private val on = at(8, 6, 58)

    private val maintenance = listOf(at(8, 1, 12), at(8, 2, 21), at(8, 4, 51), at(8, 5, 58))

    val strip = StripModel(
        start = off,
        end = on,
        segments = listOf(
            StripSegment(DozeFill.ACTIVE, off, at(8, 0, 5)),
            StripSegment(DozeFill.LIGHT, at(8, 0, 5), at(8, 0, 24)),
            StripSegment(DozeFill.DEEP, at(8, 0, 24), at(8, 3, 40)),
            StripSegment(DozeFill.ACTIVE, at(8, 3, 40), at(8, 3, 44)),
            StripSegment(DozeFill.LIGHT, at(8, 3, 44), at(8, 3, 58)),
            StripSegment(DozeFill.DEEP, at(8, 3, 58), on),
        ),
        maintenance = maintenance,
        breaks = listOf(StripBreak(at(8, 3, 40), "motion")),
    )

    val summary = ReportSummary(
        sessionId = 41,
        start = off,
        end = on,
        night = true,
        profile = "Night off",
        verdict = VerdictWord.HELD,
        strip = strip,
        timeInDeep = 376 * MIN,
        maintenanceWindows = 4,
        motionResets = 1,
        timeToDeep = 43 * MIN,
        deepShare = 0.86,
        lightShare = 0.08,
        activeShare = 0.06,
        drainPerHour = 0.40,
        drainMah = 147.0,
        ownWakeups = 0,
    )

    val timetable = listOf(
        Stop(off, StopEvent.ScreenOff),
        Stop(at(8, 0, 5), StopEvent.Light),
        Stop(at(8, 0, 24), StopEvent.Deep(), due = at(8, 0, 23), late = MIN),
        Stop(at(8, 1, 12), StopEvent.Maintenance(lasted = 52_000)),
        Stop(at(8, 2, 21), StopEvent.Maintenance(lasted = 38_000)),
        Stop(at(8, 3, 40), StopEvent.Break(BreakReason.MOTION, awake = 4 * MIN)),
        Stop(at(8, 3, 58), StopEvent.Deep(again = true)),
        Stop(at(8, 4, 51), StopEvent.Maintenance(lasted = 41_000)),
        Stop(at(8, 5, 58), StopEvent.Maintenance(lasted = 47_000)),
        Stop(on, StopEvent.ScreenOn),
    )

    /** One earlier night: the same shape, moved to [day] of the month and given its own verdict and drain. */
    private fun earlier(day: Int, month: Int, verdict: VerdictWord, drain: Double?, deepFrom: Int, breakAt: Int? = null): ComparedNight {
        val start = at(day, 23, 30 + (day % 4) * 7, month)
        val end = start + (7 * 60 + (day % 3) * 12) * MIN
        val deep = start + deepFrom * MIN
        val segments = buildList {
            add(StripSegment(DozeFill.ACTIVE, start, start + (deepFrom - 20) * MIN))
            add(StripSegment(DozeFill.LIGHT, start + (deepFrom - 20) * MIN, deep))
            if (breakAt == null) {
                add(StripSegment(DozeFill.DEEP, deep, end))
            } else {
                val b = start + breakAt * MIN
                add(StripSegment(DozeFill.DEEP, deep, b))
                add(StripSegment(DozeFill.ACTIVE, b, b + 25 * MIN))
                add(StripSegment(DozeFill.DEEP, b + 25 * MIN, end))
            }
        }
        val model = StripModel(start, end, segments, breaks = breakAt?.let { listOf(StripBreak(start + it * MIN, "")) }.orEmpty())
        return ComparedNight(start, model, verdict, drain)
    }

    private val noDoze = run {
        val start = at(4, 0, 12)
        val end = start + 380 * MIN
        ComparedNight(
            start,
            StripModel(
                start,
                end,
                listOf(
                    StripSegment(DozeFill.ACTIVE, start, start + 60 * MIN),
                    StripSegment(
                        DozeFill.LIGHT,
                        start + 60 * MIN,
                        end,
                    ),
                ),
            ),
            VerdictWord.DID_NOT_DOZE,
            1.12,
        )
    }

    val compared = Compared(
        nights = listOf(
            ComparedNight(off, strip, VerdictWord.HELD, 0.40),
            earlier(6, 11, VerdictWord.HELD, 0.38, deepFrom = 45),
            earlier(5, 11, VerdictWord.PARTLY, 0.61, deepFrom = 50, breakAt = 190),
            noDoze,
            earlier(2, 11, VerdictWord.HELD, 0.42, deepFrom = 40),
            earlier(1, 11, VerdictWord.HELD, 0.36, deepFrom = 44),
            earlier(31, 10, VerdictWord.HELD, 0.44, deepFrom = 47, breakAt = 300),
            earlier(30, 10, VerdictWord.NOT_JUDGED, null, deepFrom = 42),
        ),
        recent = Baseline(7, 0.56, 0.79),
        stock = Baseline(5, 0.77, 0.52),
    )

    val apps = listOf(
        AppWaker("Courier", "com.example.courier", wakeups = 14, wakelock = 3 * MIN + 12_000),
        AppWaker("Weather Station", "com.example.weather", wakeups = 6, wakelock = 41_000),
        AppWaker("Photo Backup", "com.example.backup", wakeups = 3, wakelock = 9 * MIN),
        AppWaker("Podcast Shelf", "com.example.podcasts", wakeups = 1, wakelock = 4_000),
        AppWaker("EnforceDoze", "io.github.akane599.enforcedoze", wakeups = 0, wakelock = 0, self = true),
    )

    val kernel = listOf(
        KernelWaker("Abort:Pending Wakeup Sources: qcom_rx_wakelock", 9),
        KernelWaker("57 qcom,smp2p-modem", 21),
        KernelWaker("wlan_rx_wake", 4),
    )

    val sensors = listOf(
        SensorHold("Step Diary", "Step Counter, non-wakeup", rateHz = null, moments = listOf(SensorMoment.SCREEN_OFF)),
        SensorHold(
            "Sleep Lab",
            "Accelerometer, non-wakeup",
            rateHz = 50.0,
            moments = listOf(SensorMoment.SCREEN_OFF, SensorMoment.MAINTENANCE),
        ),
    )

    val changes = listOf(
        DozeChange.HoldOff(at(8, 1, 40), "audio playing", until = at(8, 1, 52)),
        DozeChange.Drift(at(8, 5, 10), "light_idle_to", found = "300000", expected = "900000", writer = "com.android.shell"),
    )

    val actions = listOf(
        EngineAction(off, Lever.FORCE_IDLE, "cmd deviceidle force-idle deep", readBack = "IDLE", ok = true),
        EngineAction(
            off,
            Lever.SENSORS,
            "dumpsys sensorservice restrict io.github.akane599.enforcedoze",
            readBack = "RESTRICTED",
            ok = true,
        ),
        EngineAction(
            at(8, 1, 40),
            Lever.FORCE_IDLE,
            "cmd deviceidle unforce",
            readBack = null,
            ok = true,
            detail = "Hold-off: audio playing",
        ),
        EngineAction(
            at(8, 5, 10),
            Lever.CONSTANTS,
            "Settings.Global put device_idle_constants",
            readBack = "light_idle_to=900000",
            ok = true,
        ),
    )

    val raw = listOf(
        RawGroup(RawTitle.TIMELINE, listOf("IDLE ${at(8, 0, 24)} ${at(8, 3, 40)}", "IDLE ${at(8, 3, 58)} $on")),
        RawGroup(
            RawTitle.TRANSITIONS,
            listOf("${at(8, 0, 5)} ACTIVE → INACTIVE screen off EVENT_LOG", "${at(8, 3, 44)} ACTIVE → INACTIVE motion EVENT_LOG"),
        ),
        RawGroup(RawTitle.BREAKS, listOf("${at(8, 3, 40)} ACTIVE motion MOTION")),
    )

    val late = listOf(LateCandidate("Courier", "com.example.courier"), LateCandidate("Alarm Clock", "com.example.alarm"))

    val held = ReportState(
        summary = summary,
        timetable = timetable,
        compared = compared,
        apps = apps,
        kernel = kernel,
        sensors = sensors,
        changes = changes,
        actions = actions,
        raw = raw,
        lateCandidates = late,
    )

    /** A first night: nothing to compare with, no wakers, no Stock baseline (SPEC 9.3 "no data yet"). */
    val firstNight = held.copy(
        compared = Compared(listOf(ComparedNight(off, strip, VerdictWord.HELD, 0.40)), Baseline(0, null, null), Baseline(0, null, null)),
        apps = listOf(apps.last()),
        kernel = emptyList(),
        sensors = emptyList(),
        changes = emptyList(),
        actions = emptyList(),
    )

    val didNotDoze = held.copy(
        summary = summary.copy(
            verdict = VerdictWord.DID_NOT_DOZE,
            cause = Cause.ANOTHER_MANAGER,
            strip = StripModel(
                off,
                on,
                listOf(StripSegment(DozeFill.ACTIVE, off, at(8, 0, 30)), StripSegment(DozeFill.LIGHT, at(8, 0, 30), on)),
            ),
            timeInDeep = 0,
            maintenanceWindows = 0,
            timeToDeep = null,
            deepShare = 0.0,
            lightShare = 0.88,
            activeShare = 0.12,
            drainPerHour = 1.08,
            drainMah = 396.0,
        ),
        timetable = listOf(
            Stop(off, StopEvent.ScreenOff),
            Stop(at(8, 0, 30), StopEvent.Light),
            Stop(at(8, 0, 52), StopEvent.Break(BreakReason.ANOTHER_MANAGER, subject = "com.android.shell")),
            Stop(at(8, 2, 10), StopEvent.Gap(until = at(8, 2, 48))),
            Stop(at(8, 2, 48), StopEvent.Filled(until = at(8, 3, 30))),
            Stop(on, StopEvent.ScreenOn),
        ),
        notices = listOf(StateNotice("shell", NoticeKind.ANOTHER_MANAGER, subject = "com.android.shell", time = at(8, 0, 52))),
    )

    /** A short daytime session: no comparison, not judged because of a gap (ADR 0020). */
    val shortSession = held.copy(
        summary = summary.copy(
            night = false,
            start = at(8, 14, 2),
            end = at(8, 15, 31),
            verdict = VerdictWord.NOT_JUDGED,
            note = "The event log has a gap from 14:40 to 15:05, so the session is not judged.",
            strip = StripModel(
                at(8, 14, 2),
                at(8, 15, 31),
                listOf(
                    StripSegment(DozeFill.ACTIVE, at(8, 14, 2), at(8, 14, 12)),
                    StripSegment(DozeFill.DEEP, at(8, 14, 12), at(8, 14, 40)),
                    StripSegment(DozeFill.MISSING, at(8, 14, 40), at(8, 15, 5)),
                    StripSegment(DozeFill.DEEP, at(8, 15, 5), at(8, 15, 31)),
                ),
            ),
            timeInDeep = 54 * MIN,
            maintenanceWindows = 1,
            motionResets = 0,
            timeToDeep = 10 * MIN,
            deepShare = null,
            lightShare = null,
            activeShare = null,
            drainPerHour = null,
            drainMah = null,
            ownWakeups = null,
        ),
        compared = null,
        raw = emptyList(),
    )
}
