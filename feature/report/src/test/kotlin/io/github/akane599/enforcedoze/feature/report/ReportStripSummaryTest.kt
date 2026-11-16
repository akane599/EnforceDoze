// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.akane599.enforcedoze.design.EdTheme
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SPEC 10.4: TalkBack reads the night's strip as a text summary: its times, deep idle, maintenance and breaks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportStripSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Before fun clock() = ownerClock()

    @Test fun theStripReadsItsSummary() {
        compose.setContent { EdTheme(dark = true) { ReportScreen(ReportFixtures.held, NoActions) } }
        compose.onNode(
            hasContentDescription("Screen off 23:41, screen on 06:58.", substring = true) and
                hasContentDescription("in deep idle, 4 maintenance windows, 1 break.", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }
}
