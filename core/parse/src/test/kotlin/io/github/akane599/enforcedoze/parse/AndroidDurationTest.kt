// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidDurationTest {
    @Test fun parsesTimeUtilsFormats() {
        assertThat(AndroidDuration.parseMillis("+1h0m0s0ms")).isEqualTo(3_600_000)
        assertThat(AndroidDuration.parseMillis("+30s0ms")).isEqualTo(30_000)
        assertThat(AndroidDuration.parseMillis("+5s0ms")).isEqualTo(5_000)
        assertThat(AndroidDuration.parseMillis("+6h0m0s0ms")).isEqualTo(21_600_000)
        assertThat(AndroidDuration.parseMillis("-2h30m25s431ms")).isEqualTo(-9_025_431)
        assertThat(AndroidDuration.parseMillis("+1d2h3m4s5ms")).isEqualTo(93_784_005)
        assertThat(AndroidDuration.parseMillis("0")).isEqualTo(0)
    }

    @Test fun rejectsOtherText() {
        assertThat(AndroidDuration.parseMillis("2.0")).isNull()
        assertThat(AndroidDuration.parseMillis("20.0m")).isNull()
        assertThat(AndroidDuration.parseMillis("300000")).isNull()
        assertThat(AndroidDuration.parseMillis("+")).isNull()
        assertThat(AndroidDuration.parseMillis("true")).isNull()
    }

    @Test fun settingValueTypes() {
        assertThat(SettingValue("+3m0s0ms").longValue).isEqualTo(180_000)
        assertThat(SettingValue("300000").longValue).isEqualTo(300_000)
        assertThat(SettingValue("2.0").number).isEqualTo(2.0)
        assertThat(SettingValue("20.0m").number).isEqualTo(20.0)
        assertThat(SettingValue("true").flag).isTrue()
        assertThat(SettingValue("+3m0s0ms").flag).isNull()
    }
}
