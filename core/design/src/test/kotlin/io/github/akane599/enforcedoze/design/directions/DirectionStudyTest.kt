// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The direction study of SPEC 10.5: Home and the night report in each of three directions, written to
 * `docs/design/directions` by `./gradlew :core:design:recordRoborazziDebug` (ADR 0028).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h832dp-night-xxhdpi")
class DirectionStudyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun timetableHome() = shoot("a-timetable-home") { TimetableHome() }

    @Test fun timetableReport() = shoot("a-timetable-report") { TimetableReport() }

    @Test fun hypnogramHome() = shoot("b-hypnogram-home") { HypnogramHome() }

    @Test fun hypnogramReport() = shoot("b-hypnogram-report") { HypnogramReport() }

    @Test fun helicorderHome() = shoot("c-helicorder-home") { HelicorderHome() }

    @Test fun helicorderReport() = shoot("c-helicorder-report") { HelicorderReport() }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        val out = File(System.getProperty("enforcedoze.root"), "docs/design/directions/$name.png")
        compose.onRoot().captureRoboImage(out.path)
    }
}
