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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Something arrived late" (SPEC 7.5): choosing the app, the recorded step, and a failed record. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class LateSheetScreenshotTest {
    @Before fun clock() = ownerClock()

    @Test fun choose() = shoot(MODULE, "late-sheet", listOf(Primary)) { Sheet { Choose() } }

    @Test fun chooseTurkishLight() = shoot(MODULE, "late-sheet", listOf(Primary.copy(dark = false, turkish = true))) { Sheet { Choose() } }

    @Test fun recorded() = shoot(MODULE, "late-recorded", listOf(Primary)) { Sheet { Recorded() } }

    @Test fun recordedTurkish() = shoot(MODULE, "late-recorded", listOf(Primary.copy(turkish = true))) { Sheet { Recorded() } }

    @Test fun failed() = shoot(MODULE, "late-failed", listOf(Primary)) {
        Sheet {
            LateSheetContent(
                ReportFixtures.late,
                LateSheet(error = "IllegalStateException: profile store is unreadable"),
                onRecord = {},
                onAllowlist = {},
                onClose = {},
            )
        }
    }

    private companion object {
        const val MODULE = "feature/report"
    }
}

@Composable
private fun Choose() = LateSheetContent(ReportFixtures.late, LateSheet(), onRecord = {
}, onAllowlist = {}, onClose = {})

@Composable
private fun Recorded() = LateSheetContent(
    ReportFixtures.late,
    LateSheet(LateOutcome(ReportFixtures.late.first(), stepTo = "Classic")),
    onRecord = {},
    onAllowlist = {},
    onClose = {},
)

/** The sheet's content on the raised surface it sits on, at the bottom of the screen. */
@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Scaffold(containerColor = Ed.palette.ground) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().background(Ed.palette.raised, MaterialTheme.shapes.extraLarge).padding(top = Space.xl)) {
                content()
            }
        }
    }
}
