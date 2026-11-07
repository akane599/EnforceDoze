// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class SensorServiceDumpTest {
    @Test fun everyCaptureHasModeAndPrivacy() {
        val files = Fixtures.captures.flatMap { c ->
            c.dir.listFiles().orEmpty().filter { it.name.startsWith("sensorservice") }.map { "$c/${it.name}" to it }
        }
        assertThat(files.size).isAtLeast(4)
        files.forEach { (name, file) ->
            val dump = SensorServiceDump.parse(file.readText())
            assertWithMessage(name).that(dump.mode).isNotNull()
            assertWithMessage(name).that(dump.sensorPrivacy).isFalse()
        }
    }

    @Test fun restrictedWithPackage() {
        val api37 = Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3")
        val restricted = SensorServiceDump.parse(api37.text("sensorservice-restricted-allow.txt"))
        assertThat(restricted.mode).isEqualTo(SensorMode.RESTRICTED)
        assertThat(restricted.modePackage).isEqualTo("com.android.shell")
        val normal = SensorServiceDump.parse(api37.text("sensorservice-enabled.txt"))
        assertThat(normal.mode).isEqualTo(SensorMode.NORMAL)
        assertThat(normal.modePackage).isNull()
    }

    @Test fun phoneIsNormal() {
        val dump = SensorServiceDump.parse(Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("sensorservice.txt"))
        assertThat(dump.mode).isEqualTo(SensorMode.NORMAL)
    }

    @Test fun syntheticLines() {
        assertThat(SensorServiceDump.parse("Mode : DATA_INJECTION : com.x.y\nSensor Privacy: enabled").mode)
            .isEqualTo(SensorMode.OTHER)
        assertThat(SensorServiceDump.parse("Sensor Privacy: enabled").sensorPrivacy).isTrue()
        assertThat(SensorServiceDump.parse("junk").mode).isNull()
    }
}
