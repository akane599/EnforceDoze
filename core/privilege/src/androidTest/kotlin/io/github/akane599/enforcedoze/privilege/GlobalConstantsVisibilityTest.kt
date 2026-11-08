// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.api.ProcessCommandRunner
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What the app itself can see of the global `device_idle_constants` string without the worker (ADR 0012
 * left both open for P2): a content observer on the setting, and `dumpsys settings` with the DUMP grant.
 * The foreign writer is `settings put` from the shell uid, as Tasker through Shizuku writes (SPEC 13.3).
 * Every test puts back the string it found.
 */
@RunWith(AndroidJUnit4::class)
class GlobalConstantsVisibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun withForeignWrite(value: String, block: () -> Unit) {
        val before = shell("settings get global ${DeviceIdleSettings.SETTING}").trim()
        try {
            shell("settings put global ${DeviceIdleSettings.SETTING} $value")
            block()
        } finally {
            if (before == "null") {
                shell("settings delete global ${DeviceIdleSettings.SETTING}")
            } else {
                shell("settings put global ${DeviceIdleSettings.SETTING} $before")
            }
        }
        assertThat(shell("settings get global ${DeviceIdleSettings.SETTING}").trim()).isEqualTo(before)
    }

    @Test
    fun aForeignWriteReachesTheAppsContentObserver() {
        val thread = HandlerThread("observer").apply { start() }
        val changed = CountDownLatch(1)
        val observer = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changed.countDown()
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(DeviceIdleSettings.SETTING), false, observer)
        try {
            withForeignWrite("inactive_to=1800000") {
                assertWithMessage("onChange for a shell write of device_idle_constants within 5 s")
                    .that(changed.await(OBSERVE_SECONDS, TimeUnit.SECONDS)).isTrue()
            }
        } finally {
            resolver.unregisterContentObserver(observer)
            thread.quitSafely()
        }
    }

    @Test
    fun dumpsysSettingsShowsTheStringAndItsWriter() {
        assumeTrue(
            "needs the DUMP grant",
            context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED,
        )
        val value = "inactive_to=1800000,light_after_inactive_to=60000"
        withForeignWrite(value) {
            // The whole provider dump runs to several hundred KB; the default 64 KB cap would cut the global table.
            val dump = ProcessCommandRunner(DUMP_CAP).run(listOf("dumpsys", "settings"), DUMP_TIMEOUT_MILLIS)
            assertWithMessage("dumpsys settings: exit %s, %s", dump.exitCode, dump.error).that(dump.isSuccess).isTrue()
            val line = dump.stdout.lineSequence().firstOrNull { " name:${DeviceIdleSettings.SETTING} " in it }
            assertWithMessage("the device_idle_constants row in %s chars of output", dump.stdout.length).that(line).isNotNull()
            assertThat(line).contains("pkg:com.android.shell value:$value")
        }
    }

    private companion object {
        const val OBSERVE_SECONDS = 5L
        const val DUMP_TIMEOUT_MILLIS = 30_000L
        const val DUMP_CAP = 8 * 1024 * 1024
    }
}
