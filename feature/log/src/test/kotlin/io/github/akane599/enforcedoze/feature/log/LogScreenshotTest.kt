// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class LogScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun log() = shoot(MODULE, "log", tall = true) { LogScreen(LogFixtures.state, LogFilter(), NoLog) }

    @Test fun filtered() = shoot(MODULE, "log-filtered", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        LogScreen(LogFixtures.state, LogFilter(verdicts = setOf(VerdictWord.HELD), plugged = false, profileId = "night"), NoLog)
    }

    @Test fun noMatch() = shoot(MODULE, "log-no-match", listOf(Primary)) {
        LogScreen(LogFixtures.state, LogFilter(verdicts = setOf(VerdictWord.DID_NOT_DOZE), plugged = false), NoLog)
    }

    @Test fun empty() = shoot(MODULE, "log-empty", listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true))) {
        LogScreen(LogFixtures.empty, LogFilter(), NoLog)
    }

    @Test fun readOnly() = shoot(MODULE, "log-read-only", listOf(Primary)) { LogScreen(LogFixtures.readOnly, LogFilter(), NoLog) }

    @Test fun export() = shoot(MODULE, "export", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        LogScreen(LogFixtures.state, LogFilter(), NoLog)
        ExportRangeDialog(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 16), onExport = { _, _ -> }, onDismiss = {})
    }

    private companion object {
        const val MODULE = "feature/log"
    }
}

private object NoLog : LogActions {
    override fun open(entry: LogEntry) = Unit
    override fun filter(filter: LogFilter) = Unit
    override fun export() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}
