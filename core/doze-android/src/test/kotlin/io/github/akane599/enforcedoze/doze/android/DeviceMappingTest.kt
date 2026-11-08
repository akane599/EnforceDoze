// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ConstantsReading
import io.github.akane599.enforcedoze.doze.ConstantsValue
import io.github.akane599.enforcedoze.doze.DeviceResult
import io.github.akane599.enforcedoze.doze.SensorValue
import io.github.akane599.enforcedoze.doze.Writable
import io.github.akane599.enforcedoze.privilege.Grant
import io.github.akane599.enforcedoze.privilege.PrivilegeState
import io.github.akane599.enforcedoze.privilege.ShizukuStatus
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.WorkerSource
import io.github.akane599.enforcedoze.privilege.WorkerStatus
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class DeviceMappingTest {
    private fun outcome(stdout: String, exit: Int = 0) = CommandOutcome(listOf("dumpsys"), exit, stdout, "", false, 1, null)

    private fun settingsDump(globalRows: String) =
        """
        |CONFIG SETTINGS (user 0)
        |_id:1 name:device_idle/inactive_to pkg:com.android.vending value:1800000
        |GLOBAL SETTINGS (user 0)
        |_id:10 name:adb_enabled pkg:android value:1
        |$globalRows
        |SECURE SETTINGS (user 0)
        |_id:20 name:device_idle_constants pkg:android value:wrong_table=1
        """.trimMargin()

    @Test
    fun `the tier decides what is writable`() {
        assertThat(DeviceMapping.writable(PrivilegeState.NONE)).isEqualTo(Writable.NOTHING)
        val t1 = PrivilegeState.NONE.copy(grants = setOf(Grant.DUMP, Grant.WRITE_SECURE_SETTINGS))
        assertThat(DeviceMapping.writable(t1)).isEqualTo(Writable(constants = true, sensors = true))
        val t2 = PrivilegeState(emptySet(), ShizukuStatus.Ready(uid = 2000, version = 13, sui = false), false, WorkerStatus.Idle)
        assertThat(DeviceMapping.writable(t2)).isEqualTo(Writable(forceIdle = true, sensors = true, sensorPrivacy = true, allowlist = true))
        val lost = t2.copy(worker = WorkerStatus.Unavailable(WorkerSource.SHIZUKU, "no bind"))
        assertThat(DeviceMapping.writable(lost)).isEqualTo(Writable.NOTHING)
    }

    @Test
    fun `the global string and its writer come from the global table only`() {
        val found = DeviceMapping.constantsFromSettingsDump(
            outcome(settingsDump("_id:11 name:device_idle_constants pkg:com.android.shell value:inactive_to=600000,sensing_to=0")),
        )
        assertThat(found).isEqualTo(ConstantsReading(ConstantsValue.Exact("inactive_to=600000,sensing_to=0"), "com.android.shell"))
        assertThat(DeviceMapping.constantsFromSettingsDump(outcome(settingsDump(""))))
            .isEqualTo(ConstantsReading(ConstantsValue.Absent, null))
        assertThat(
            DeviceMapping.constantsFromSettingsDump(outcome(settingsDump("_id:11 name:device_idle_constants pkg:android value:null"))),
        )
            .isEqualTo(ConstantsReading(ConstantsValue.Absent, "android"))
    }

    @Test
    fun `a dump that cannot tell is no reading, never no string`() {
        val denied = "Permission Denial: can't dump SettingsProvider from pid=1, uid=10200 without permission android.permission.DUMP"
        assertThat(DeviceMapping.constantsFromSettingsDump(outcome(denied))).isNull()
        assertThat(DeviceMapping.constantsFromSettingsDump(outcome(settingsDump("") + "\n[truncated]"))).isNull()
        assertThat(DeviceMapping.constantsFromSettingsDump(outcome(settingsDump(""), exit = 1))).isNull()
    }

    @Test
    fun `settings get prints null for no string`() {
        assertThat(DeviceMapping.constantsFromSettingsGet(outcome("null\n")))
            .isEqualTo(DeviceResult.Ok(ConstantsReading(ConstantsValue.Absent, null)))
        assertThat(DeviceMapping.constantsFromSettingsGet(outcome("inactive_to=1\n")))
            .isEqualTo(DeviceResult.Ok(ConstantsReading(ConstantsValue.Exact("inactive_to=1"), null)))
        assertThat(DeviceMapping.constantsFromSettingsGet(outcome("", exit = 255))).isInstanceOf(DeviceResult.Failed::class.java)
    }

    @Test
    fun `sensor modes and privacy from every capture`() {
        val emulator = Fixtures.capture("sdk_gphone64_x86_64", "api37_CE2A.260420.051.A3")
        assertThat(DeviceMapping.sensors(emulator.text("sensorservice-restricted-allow.txt")).mode)
            .isEqualTo(SensorValue.Restricted("com.android.shell"))
        assertThat(DeviceMapping.sensors(emulator.text("sensorservice-enabled.txt")).mode).isEqualTo(SensorValue.Normal)
        for (capture in Fixtures.with("sensorservice.txt")) {
            val reading = DeviceMapping.sensors(capture.text("sensorservice.txt"))
            assertThat(reading.mode).isEqualTo(SensorValue.Normal)
            assertThat(reading.privacy).isFalse()
        }
        // Cut before the mode line: unknown, not normal.
        val phone = Fixtures.with("sensorservice.txt").first { it.isPhone }.text("sensorservice.txt")
        assertThat(DeviceMapping.sensors(phone.take(64 * 1024)).mode).isNull()
    }

    @Test
    fun `the allowlist keeps user entries and rejects an error message`() {
        val phone = Fixtures.with("deviceidle-whitelist.txt").first { it.isPhone }.text("deviceidle-whitelist.txt")
        val users = (DeviceMapping.allowlist(phone) as DeviceResult.Ok).value
        assertThat(users).hasSize(phone.lines().count { it.startsWith("user,") })
        assertThat(DeviceMapping.allowlist("")).isEqualTo(DeviceResult.Ok(emptySet<String>()))
        assertThat(DeviceMapping.allowlist("cmd: Can't find service: deviceidle")).isInstanceOf(DeviceResult.Failed::class.java)
    }

    @Test
    fun `a write that exits 0 with a permission denial failed`() {
        assertThat(DeviceMapping.write(outcome(""))).isEqualTo(DeviceResult.Ok(Unit, ""))
        assertThat(
            DeviceMapping.write(outcome("Permission Denial: can't dump SensorService")),
        ).isInstanceOf(DeviceResult.Failed::class.java)
        assertThat(DeviceMapping.worker(WorkerResult.Unavailable("tier T1: no worker"), DeviceMapping::write))
            .isEqualTo(DeviceResult.Failed("no worker: tier T1: no worker"))
    }
}
