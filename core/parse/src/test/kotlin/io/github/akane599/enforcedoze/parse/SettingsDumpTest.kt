// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsDumpTest {
    /** Shaped like `dumpsys settings` on the API 37 emulator; the device_idle_constants row is verbatim. */
    private val dump = """
        CONFIG SETTINGS (user 0)
        version: 1
        _id:1920 name:device_idle/light_idle_to pkg:com.google.android.gms value:300000
        _id:1921 name:device_idle_constants pkg:com.example.wrong value:config_table

        Historical operations
        GLOBAL SETTINGS (user 0)
        version: 236
        _id:12 name:adb_enabled pkg:android value:1 default:1 defaultSystemSet:true
        _id:597 name:device_idle_constants pkg:com.android.shell value:inactive_to=1800000 notPreservedInRestore
        _id:600 name:low_power pkg:android value:{null}

        Historical operations
        2026-11-08 00:12:12 update device_idle_constants
        SECURE SETTINGS (user 0)
        version: 210
        _id:7 name:device_idle_constants pkg:com.example.wrong value:secure_table
    """.trimIndent()

    @Test
    fun `finds the global row with its writer and value`() {
        val row = SettingsDump.findRow(dump, SettingsDump.Table.GLOBAL, "device_idle_constants")
        assertThat(row).isEqualTo(
            SettingsRow(
                id = "597",
                name = "device_idle_constants",
                packageName = "com.android.shell",
                value = "inactive_to=1800000",
                raw = "_id:597 name:device_idle_constants pkg:com.android.shell value:inactive_to=1800000 notPreservedInRestore",
            ),
        )
    }

    @Test
    fun `reads defaults and null values`() {
        assertThat(SettingsDump.findRow(dump, SettingsDump.Table.GLOBAL, "adb_enabled")?.value).isEqualTo("1")
        val lowPower = SettingsDump.findRow(dump, SettingsDump.Table.GLOBAL, "low_power")
        assertThat(lowPower?.value).isNull()
        assertThat(lowPower?.packageName).isEqualTo("android")
    }

    @Test
    fun `keeps to the table asked for`() {
        assertThat(SettingsDump.findRow(dump, SettingsDump.Table.SECURE, "device_idle_constants")?.value).isEqualTo("secure_table")
        assertThat(SettingsDump.findRow(dump, SettingsDump.Table.SYSTEM, "device_idle_constants")).isNull()
        assertThat(SettingsDump.findRow(dump, SettingsDump.Table.GLOBAL, "device_idle_constants", user = 10)).isNull()
    }

    @Test
    fun `a row without a package and a refused dump`() {
        val noPkg = "GLOBAL SETTINGS (user 0)\n_id:3 name:device_idle_constants value:inactive_to=1"
        assertThat(SettingsDump.findRow(noPkg, SettingsDump.Table.GLOBAL, "device_idle_constants")?.packageName).isNull()
        val denied = "Permission Denial: can't dump settings from from pid=1, uid=10200"
        assertThat(SettingsDump.findRow(denied, SettingsDump.Table.GLOBAL, "device_idle_constants")).isNull()
    }

    @Test
    fun `tells a setting that is not set from a dump without the table`() {
        assertThat(SettingsDump.lookup(dump, SettingsDump.Table.GLOBAL, "not_set")).isEqualTo(SettingsDump.Lookup.NoRow)
        assertThat(SettingsDump.lookup(dump, SettingsDump.Table.SYSTEM, "device_idle_constants")).isEqualTo(SettingsDump.Lookup.NoTable)
        val denied = "Permission Denial: can't dump settings from from pid=1, uid=10200"
        assertThat(SettingsDump.lookup(denied, SettingsDump.Table.GLOBAL, "device_idle_constants")).isEqualTo(SettingsDump.Lookup.NoTable)
        assertThat(SettingsDump.lookup(dump, SettingsDump.Table.GLOBAL, "device_idle_constants"))
            .isInstanceOf(SettingsDump.Lookup.Found::class.java)
    }
}
