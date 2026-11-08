// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class SensorConnectionsTest {
    private val sensorLine = Regex("""^\t .+ 0x[0-9a-f]{8} \| (?:status|first flush pending):""", RegexOption.MULTILINE)

    @Test fun everyCaptureReadsEverySensorOfEveryConnection() {
        Fixtures.with("sensorservice.txt").forEach { capture ->
            val text = capture.text("sensorservice.txt")
            val connections = SensorConnections.parse(text)
            assertWithMessage("$capture").that(connections).hasSize(sensorLine.findAll(text).count())
            assertWithMessage("$capture").that(connections).isNotEmpty()
        }
    }

    @Test fun emulatorConnectionsCarryTheirRequestedPeriods() {
        val text = Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3").text("sensorservice.txt")
        assertThat(SensorConnections.parse(text)).containsExactly(
            SensorConnection("com.android.server.power.FaceDownDetector", 1000, "Goldfish 3-axis Accelerometer", 0, 200_000),
            SensorConnection(
                "com.android.server.wm.WindowOrientationListener\$AccelSensorJudge",
                1000,
                "Goldfish 3-axis Accelerometer",
                0,
                66_667,
            ),
        )
    }

    @Test fun phoneConnectionsIncludeSignificantMotion() {
        val text = Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("sensorservice.txt")
        val connections = SensorConnections.parse(text)
        assertThat(connections.map { it.handle }).contains(0x0b)
        // Connections opened hours before the capture have rolled out of the registration log.
        assertThat(connections.mapNotNull { it.samplingPeriodUs }).isNotEmpty()
    }

    @Test fun android15LayoutAndARemovalAfterTheLastActivation() {
        val text = """
            |2 active connections
            |Connection Number: 0 
            |	Operating Mode: NORMAL
            |	 com.example.Step | WakeLockRefCount 0 | uid 10143 | cache size 0 | max cache size 0
            |	 Step Detector 0x5f737464 | status: active | pending flush events 0 
            |	 Pressure 0x00000007 | status: active | pending flush events 0 
            |0 direct connections
            |Previous Registrations:
            |23:07:22 - 0x00000007 pid= 1053 uid=10143 package=com.example.Step
            |23:07:21 + 0x00000007 pid= 1053 uid=10143 package=com.example.Step samplingPeriod=1000000us batchingPeriod=0us
            |23:07:20 + 0x5f737464 pid= 1053 uid=10143 package=com.example.Step samplingPeriod=20000us batchingPeriod=0us
        """.trimMargin()
        assertThat(SensorConnections.parse(text)).containsExactly(
            SensorConnection("com.example.Step", 10_143, "Step Detector", 0x5f737464, 20_000),
            SensorConnection("com.example.Step", 10_143, "Pressure", 7, null),
        ).inOrder()
    }
}
