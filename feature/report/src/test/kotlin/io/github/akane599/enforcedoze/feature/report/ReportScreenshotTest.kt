// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.StateNotice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class ReportScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun summary() = shoot(MODULE, "summary", tall = true) { ReportScreen(ReportFixtures.held, NoActions) }

    @Test fun timeline() = shoot(MODULE, "timeline") { ReportScreen(ReportFixtures.held, NoActions, initialTab = ReportTab.TIMELINE) }

    @Test fun wakers() = shoot(MODULE, "wakers") { ReportScreen(ReportFixtures.held, NoActions, initialTab = ReportTab.WAKERS) }

    @Test fun sensors() = shoot(MODULE, "sensors", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        ReportScreen(ReportFixtures.held, NoActions, initialTab = ReportTab.SENSORS)
    }

    @Test fun actions() = shoot(MODULE, "actions", listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true))) {
        ReportScreen(ReportFixtures.held, NoActions, initialTab = ReportTab.ACTIONS)
    }

    @Test fun raw() = shoot(MODULE, "raw", listOf(Primary)) { ReportScreen(ReportFixtures.held, NoActions, initialTab = ReportTab.RAW) }

    @Test fun firstNight() = shoot(MODULE, "first-night", listOf(Primary, Primary.copy(turkish = true)), tall = true) {
        ReportScreen(ReportFixtures.firstNight, NoActions)
    }

    @Test fun didNotDoze() = shoot(MODULE, "did-not-doze", listOf(Primary, Primary.copy(dark = false)), tall = true) {
        ReportScreen(ReportFixtures.didNotDoze, NoActions)
    }

    @Test fun didNotDozeTimeline() = shoot(MODULE, "did-not-doze-timeline", listOf(Primary)) {
        ReportScreen(ReportFixtures.didNotDoze, NoActions, initialTab = ReportTab.TIMELINE)
    }

    @Test fun shortSession() = shoot(MODULE, "short-session", listOf(Primary)) { ReportScreen(ReportFixtures.shortSession, NoActions) }

    private companion object {
        const val MODULE = "feature/report"
    }
}

private object NoActions : ReportActions {
    override fun back() = Unit
    override fun export() = Unit
    override fun late() = Unit
    override fun allowlist() = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}
