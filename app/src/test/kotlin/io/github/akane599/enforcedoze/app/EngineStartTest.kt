// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** Start at boot and after an update (parity row 36), which upstream's BootCompleteReceiver and AutoRestartOnUpdate did. */
class EngineStartTest {
    @Test
    fun `the engine restarts after a reboot or an update, and on nothing else`() {
        assertThat(EngineStartReceiver.starts("android.intent.action.BOOT_COMPLETED")).isTrue()
        assertThat(EngineStartReceiver.starts("android.intent.action.MY_PACKAGE_REPLACED")).isTrue()
        // Upstream listened to PACKAGE_REPLACED, which fires for every app's update.
        assertThat(EngineStartReceiver.starts("android.intent.action.PACKAGE_REPLACED")).isFalse()
        assertThat(EngineStartReceiver.starts(null)).isFalse()
    }

    @Test
    fun `the manifest delivers both broadcasts to it`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiver = manifest.substringAfter("android:name=\"io.github.akane599.enforcedoze.app.EngineStartReceiver\"")
            .substringBefore("</receiver>")
        assertThat(receiver).contains("android.intent.action.BOOT_COMPLETED")
        assertThat(receiver).contains("android.intent.action.MY_PACKAGE_REPLACED")
        assertThat(manifest).contains("android.permission.RECEIVE_BOOT_COMPLETED")
    }
}
