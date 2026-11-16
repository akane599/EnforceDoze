// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SPEC 3.3, parity row 35: what upstream's RequestIgnoreBatteryActivity asked for, read from public calls. */
class KeepAliveTest {
    @Test
    fun `the app keeps running only when exempt and not background-restricted`() {
        assertThat(KeepAliveReader.of(exempt = true, bucket = 5, restricted = false, manufacturer = "Google").exempt).isTrue()
        assertThat(KeepAliveReader.of(exempt = false, bucket = 10, restricted = false, manufacturer = "Google").exempt).isFalse()
        // "Restricted" battery use wins over the allowlist entry: the system may still stop the app.
        assertThat(KeepAliveReader.of(exempt = true, bucket = 45, restricted = true, manufacturer = "Google").exempt).isFalse()
    }

    @Test
    fun `a Samsung phone also gets the Never sleeping apps hint, with the raw values`() {
        val alive = KeepAliveReader.of(exempt = false, bucket = 30, restricted = false, manufacturer = "samsung")
        assertThat(alive.samsung).isTrue()
        assertThat(alive.detail).isEqualTo("isIgnoringBatteryOptimizations=false appStandbyBucket=30 isBackgroundRestricted=false")
        assertThat(KeepAliveReader.of(true, 5, false, "Google").samsung).isFalse()
    }
}
