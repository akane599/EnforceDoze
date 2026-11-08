// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import kotlinx.serialization.json.Json
import org.junit.Test

class ProfileFilesTest {
    /** The example of SPEC 6.4, verbatim. */
    private val example = """
        {
          "id": "pocket",
          "name": "Pocket",
          "schema": 1,
          "levers": {
            "constants": true,
            "forceIdle": { "enabled": false, "delaySec": 0, "untilUnlock": false },
            "sensors": "restricted",
            "allowlist": "inherit"
          },
          "deviceIdle": {
            "inactive_to": 300000,
            "idle_after_inactive_to": 180000,
            "sensing_to": 30000,
            "locating_to": 15000,
            "light_after_inactive_to": 60000
          }
        }
    """.trimIndent()

    private val pocket = Profile(
        id = "pocket",
        name = "Pocket",
        levers = Levers(constants = true, forceIdle = ForceIdleLever(enabled = false), SensorLever.RESTRICTED, AllowlistLever.INHERIT),
        deviceIdle = linkedMapOf(
            "inactive_to" to "300000",
            "idle_after_inactive_to" to "180000",
            "sensing_to" to "30000",
            "locating_to" to "15000",
            "light_after_inactive_to" to "60000",
        ),
    )

    @Test fun `the profile file is the one SPEC 6_4 shows`() {
        assertThat(Json.parseToJsonElement(ProfileFiles.encode(pocket))).isEqualTo(Json.parseToJsonElement(example))
    }

    @Test fun `factors stay decimals, flags booleans, and the key order is kept`() {
        val profile = pocket.copy(
            levers = pocket.levers.copy(sensors = SensorLever.ALL_SENSORS_OFF, allowlist = AllowlistLever.TRIMMED),
            deviceIdle = linkedMapOf("idle_factor" to "2.0", "skip_sensing" to "true", "inactive_to" to "300000"),
        )
        val text = ProfileFiles.encode(profile)
        assertThat(text).contains("\"idle_factor\": 2.0")
        assertThat(text).contains("\"skip_sensing\": true")
        assertThat(text).contains("\"sensors\": \"allSensorsOff\"")
        assertThat(text).contains("\"allowlist\": \"trimmed\"")
        assertThat(text.indexOf("idle_factor")).isLessThan(text.indexOf("inactive_to"))
    }

    @Test fun `an id never becomes a path`() {
        assertThat(ProfileFiles.fileName(pocket.copy(id = "../x/y"))).isEqualTo(".._x_y.json")
    }
}
