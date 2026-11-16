// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

import io.github.akane599.enforcedoze.testing.screenshots.Primary
import io.github.akane599.enforcedoze.testing.screenshots.ownerClock
import io.github.akane599.enforcedoze.testing.screenshots.shoot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "hdpi")
class SensorsScreenshotTest {
    @Before fun clock() = ownerClock()

    private val at = ZonedDateTime.of(2026, 11, 15, 23, 10, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()

    /** Illustrative holders only; no capture from any phone. */
    private val state = SensorsState(
        mode = SensorModeNow.Restricted(allowed = null),
        snapshot = SensorSnapshot(
            sessionId = 7,
            time = at,
            holders = listOf(
                SensorHolder("Fitness", "Step counter", null),
                SensorHolder("Launcher", "Accelerometer", 50.0),
                SensorHolder("Screen rotation", "Game rotation vector", 5.0),
            ),
        ),
    )

    @Test fun sensors() = shoot(MODULE, "sensors", tall = true) { SensorsScreen(state, NoSensors) }

    @Test fun normal() = shoot(MODULE, "sensors-normal", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        SensorsScreen(SensorsState(mode = SensorModeNow.Normal, snapshot = state.snapshot?.copy(holders = emptyList())), NoSensors)
    }

    @Test fun readOnly() = shoot(
        MODULE,
        "sensors-read-only",
        listOf(Primary, Primary.copy(dark = false, turkish = true, largeFont = true)),
    ) {
        SensorsScreen(
            SensorsState(
                SensorModeNow.Unknown,
                allSensorsOff = null,
                canWrite = false,
                notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)),
            ),
            NoSensors,
        )
    }

    @Test fun allOff() = shoot(MODULE, "sensors-all-off", listOf(Primary)) {
        SensorsScreen(state.copy(allSensorsOff = true), NoSensors)
    }

    @Test fun failed() = shoot(MODULE, "sensors-failed", listOf(Primary)) {
        SensorsScreen(
            state,
            NoSensors,
            status = SwitchStatus(true, ActionOutcome.Failed("SecurityException: MANAGE_SENSOR_PRIVACY not held")),
        )
    }

    @Test fun confirm() = shoot(MODULE, "confirm", listOf(Primary, Primary.copy(dark = false, turkish = true))) {
        SensorsScreen(state, NoSensors)
        AllSensorsOffDialog(onConfirm = {}, onDismiss = {})
    }

    private companion object {
        const val MODULE = "feature/sensors"
    }
}

private object NoSensors : SensorsActions {
    override fun allSensorsOff(on: Boolean) = Unit
    override fun openSnapshot(sessionId: Long) = Unit
    override fun notice(notice: StateNotice) = Unit
    override fun seen(notice: StateNotice) = Unit
}
