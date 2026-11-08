// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.home

import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.StripBreak
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.design.StripSegment
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class HomeScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun held() = shoot(MODULE, "held") { HomeScreen(HomeFixtures.held, NoActions) }

    @Test fun noDataYet() = shoot(MODULE, "no-data", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        HomeScreen(HomeFixtures.firstRun, NoActions)
    }

    @Test fun readOnly() = shoot(MODULE, "read-only", listOf(Primary)) { HomeScreen(HomeFixtures.readOnly, NoActions) }

    @Test fun trouble() = shoot(MODULE, "trouble", listOf(Primary, Primary.copy(turkish = true)), tall = true) {
        HomeScreen(HomeFixtures.trouble, NoActions)
    }

    @Test fun paused() = shoot(MODULE, "paused", listOf(Primary.copy(dark = false))) { HomeScreen(HomeFixtures.paused, NoActions) }

    @Test fun twelveHour() {
        ownerClock(twentyFour = false)
        shoot(MODULE, "twelve-hour", listOf(Primary.copy(dark = false))) { HomeScreen(HomeFixtures.held, NoActions) }
    }

    private companion object {
        const val MODULE = "feature/home"
    }
}

private object NoActions : HomeActions {
    override fun changePlan() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun openReport(sessionId: Long) = Unit
    override fun quickProof() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}

/** The night of 7 to 8 November 2026 in Istanbul, the same night the direction study drew (ADR 0028). */
internal object HomeFixtures {
    private val zone = ZoneId.of("Europe/Istanbul")

    fun at(day: Int, hour: Int, minute: Int): Long = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val off = at(7, 23, 41)
    private val on = at(8, 6, 58)

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
        maintenance = listOf(at(8, 1, 12), at(8, 2, 21), at(8, 4, 51), at(8, 5, 47), at(8, 6, 30)),
        breaks = listOf(StripBreak(at(8, 3, 40), "motion")),
    )

    private val board = PlanBoard(
        listOf(
            BoardRow(null, "Everyday", Until.At(at(8, 23, 0))),
            BoardRow(at(8, 23, 0), "Night", Until.UnlockAfter(at(9, 7, 30))),
        ),
    )

    private val night = NightSummary(
        sessionId = 41,
        start = off,
        end = on,
        night = true,
        verdict = VerdictWord.HELD,
        timeInDeep = 376 * 60_000L,
        maintenanceWindows = 5,
        strip = strip,
        timeToDeep = 43 * 60_000L,
        deepShare = 0.86,
        drainPerHour = 0.40,
        ownWakeups = 0,
    )

    val held = HomeState(
        plan = board,
        night = night,
        notices = listOf(StateNotice("tasker", NoticeKind.ANOTHER_MANAGER, subject = "Tasker", time = at(7, 22, 14))),
    )

    val firstRun = HomeState(plan = board)

    val readOnly = HomeState(
        plan = board.copy(canWrite = false),
        night = night.copy(
            verdict = VerdictWord.DID_NOT_DOZE,
            cause = Cause.UNKNOWN,
            timeInDeep = 0,
            timeToDeep = null,
            deepShare = null,
            ownWakeups = null,
            strip = strip.copy(segments = listOf(StripSegment(DozeFill.ACTIVE, off, on)), maintenance = emptyList(), breaks = emptyList()),
        ),
        notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)),
    )

    val trouble = HomeState(
        plan = board,
        night = night.copy(
            verdict = VerdictWord.PARTLY,
            breaks = 3,
            timeInDeep = 241 * 60_000L,
            deepShare = 0.62,
            drainPerHour = 0.71,
            ownWakeups = null,
            strip = strip.copy(
                segments = strip.segments.take(3) + StripSegment(DozeFill.MISSING, at(8, 3, 40), at(8, 4, 30)) +
                    StripSegment(DozeFill.DEEP, at(8, 4, 30), on),
            ),
        ),
        notices = listOf(
            StateNotice("lost", NoticeKind.PRIVILEGE_LOST, subject = "sensor restriction, forced idle"),
            StateNotice("partly", NoticeKind.PARTLY_APPLIED, subject = "light_idle_to"),
            StateNotice("tasker", NoticeKind.ANOTHER_MANAGER, subject = "Tasker", time = at(7, 22, 14), seen = true),
            StateNotice(
                "err",
                NoticeKind.ERROR,
                subject = "Could not read the alarm dump",
                raw = "  RTC_WAKEUP #3: Alarm{4f2a1c0 type 0 origWhen -1 ???}",
            ),
        ),
    )

    val paused = HomeState(
        plan = PlanBoard(listOf(BoardRow(null, "Stock", Until.Resumed)), paused = true),
        night = night.copy(
            verdict = VerdictWord.DID_NOT_DOZE,
            cause = Cause.CHARGING,
            timeInDeep = 0,
            timeToDeep = null,
            deepShare = 0.0,
            drainPerHour = null,
            strip = strip.copy(segments = listOf(StripSegment(DozeFill.ACTIVE, off, on)), maintenance = emptyList(), breaks = emptyList()),
        ),
    )
}
