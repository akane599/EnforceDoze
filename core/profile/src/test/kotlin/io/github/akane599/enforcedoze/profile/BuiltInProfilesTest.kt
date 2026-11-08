// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import org.junit.Test

class BuiltInProfilesTest {
    @Test
    fun `six built-ins with unique ids`() {
        assertThat(BuiltInProfiles.ALL.map { it.id }).containsExactly("stock", "everyday", "pocket", "night", "classic", "strict").inOrder()
        assertThat(BuiltInProfiles.ALL.all { it.builtIn }).isTrue()
        assertThat(BuiltInProfiles.byId("pocket")).isSameInstanceAs(BuiltInProfiles.POCKET)
        assertThat(BuiltInProfiles.byId("nope")).isNull()
    }

    @Test
    fun `levers match SPEC 6_1`() {
        with(BuiltInProfiles) {
            assertThat(STOCK.levers.constants || STOCK.levers.forceIdle.enabled || STOCK.levers.sensors != SensorLever.OFF).isFalse()
            assertThat(EVERYDAY.levers).isEqualTo(Levers(true, ForceIdleLever(false), SensorLever.OFF, AllowlistLever.INHERIT))
            assertThat(POCKET.levers).isEqualTo(Levers(true, ForceIdleLever(false), SensorLever.RESTRICTED, AllowlistLever.INHERIT))
            assertThat(NIGHT.levers).isEqualTo(Levers(true, ForceIdleLever(false), SensorLever.RESTRICTED, AllowlistLever.INHERIT))
            assertThat(CLASSIC.levers).isEqualTo(Levers(false, ForceIdleLever(true, 0), SensorLever.RESTRICTED, AllowlistLever.INHERIT))
            assertThat(STRICT.levers).isEqualTo(Levers(true, ForceIdleLever(true, 0), SensorLever.RESTRICTED, AllowlistLever.TRIMMED))
        }
    }

    @Test
    fun `constants match the SPEC 6_2 table`() {
        val table = mapOf(
            "everyday" to "inactive_to=300000,idle_after_inactive_to=300000,sensing_to=60000,locating_to=15000," +
                "motion_inactive_to=300000,light_after_inactive_to=60000",
            "pocket" to "inactive_to=300000,idle_after_inactive_to=180000,sensing_to=30000,locating_to=15000,light_after_inactive_to=60000",
            "night" to "inactive_to=120000,idle_after_inactive_to=120000,sensing_to=30000,locating_to=10000," +
                "light_after_inactive_to=60000,idle_to=7200000,max_idle_to=28800000,idle_factor=2",
            "strict" to "idle_to=7200000,max_idle_to=43200000,idle_factor=2",
        )
        for ((id, string) in table) {
            assertThat(DeviceIdleSettings.encode(BuiltInProfiles.byId(id)!!.constants)).isEqualTo(string)
        }
        assertThat(BuiltInProfiles.STOCK.constants).isEmpty()
        assertThat(BuiltInProfiles.CLASSIC.constants).isEmpty()
    }

    @Test
    fun `built-ins pass the guardrails without stock json`() {
        for (profile in BuiltInProfiles.ALL) {
            assertThat(Guardrails.check(profile.deviceIdle, stock = null)).isEmpty()
        }
    }
}
