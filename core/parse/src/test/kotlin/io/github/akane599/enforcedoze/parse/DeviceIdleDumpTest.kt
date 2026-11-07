// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class DeviceIdleDumpTest {
    private val dumps = Fixtures.captures.flatMap { capture ->
        capture.dir.listFiles().orEmpty()
            .filter { it.name.startsWith("deviceidle") && it.name != "deviceidle-whitelist.txt" && !it.name.contains("get") }
            .map { "$capture/${it.name}" to DeviceIdleDump.parse(it.readText()) }
    }

    @Test fun everyCaptureParses() {
        assertThat(dumps.size).isAtLeast(12)
        dumps.forEach { (name, dump) ->
            assertWithMessage(name).that(dump.isRecognised).isTrue()
            assertWithMessage(name).that(dump.deepState).isNotNull()
            assertWithMessage(name).that(dump.lightState).isNotNull()
            assertWithMessage(name).that(dump.forceIdle).isNotNull()
            assertWithMessage(name).that(dump.missingAospKeys).isEmpty()
            assertWithMessage(name).that(dump.settings["inactive_to"]?.durationMillis).isNotNull()
        }
    }

    @Test fun emulatorsHaveNoExtrasAndNoUnknownLines() {
        dumps.filter { it.first.startsWith("sdk_") }.forEach { (name, dump) ->
            assertWithMessage(name).that(dump.extraSettingKeys).isEmpty()
            assertWithMessage(name).that(dump.unknownLines).isEmpty()
            assertWithMessage(name).that(dump.motionSensor).isNull()
        }
    }

    @Test fun api37EmulatorValues() {
        val dump = DeviceIdleDump.parse(Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3").text("deviceidle.txt"))
        assertThat(dump.flags).containsEntry("com.android.server.deviceidle.remove_idle_location", "true")
        assertThat(dump.settings["inactive_to"]?.durationMillis).isEqualTo(15_000)
        assertThat(dump.deepState).isEqualTo(DeepState.ACTIVE)
        assertThat(dump.lightState).isEqualTo(LightState.ACTIVE)
        assertThat(dump.charging).isTrue()
        assertThat(dump.screenOn).isTrue()
        assertThat(dump.sections.keys).contains("Whitelist (except idle) system apps")
        assertThat(dump.fields["mNextLightIdleDelay"]).isEqualTo("+5m0s0ms")
    }

    @Test fun forcedDumpShowsForceAndOverride() {
        val dump = DeviceIdleDump.parse(Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3").text("deviceidle-forced.txt"))
        assertThat(dump.forceIdle).isTrue()
        assertThat(dump.deepState).isEqualTo(DeepState.IDLE)
        assertThat(dump.lightState).isEqualTo(LightState.OVERRIDE)
    }

    @Test fun oneUiPhoneDump() {
        val dump = DeviceIdleDump.parse(Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("deviceidle.txt"))
        assertThat(dump.extraSettingKeys).containsExactly("skip_sensing", "skip_locating", "dna_request_version")
        assertThat(dump.settings["skip_sensing"]?.flag).isTrue()
        assertThat(dump.settings["inactive_to"]?.durationMillis).isEqualTo(180_000)
        assertThat(dump.settings["location_accuracy"]?.number).isEqualTo(20.0)
        assertThat(dump.motionSensor).contains("smd  Wakeup")
        assertThat(dump.useMotionSensor).isTrue()
        assertThat(dump.unknownLines.map { it.trim() }).containsExactly("Doze Allowlist Gatekeeper: false")
        assertThat(dump.sections).containsKey("Allowlist History")
        assertThat(dump.sections.getValue("Allowlist History").first()).isEqualTo("- history count : 100")
    }

    @Test fun garbageNeverThrows() {
        val dump = DeviceIdleDump.parse("Permission Denial: can't dump DeviceIdleController from pid=1, uid=10200\n")
        assertThat(dump.isRecognised).isFalse()
        assertThat(dump.unknownLines).hasSize(1)
        assertThat(DeviceIdleDump.parse("").isRecognised).isFalse()
    }
}
