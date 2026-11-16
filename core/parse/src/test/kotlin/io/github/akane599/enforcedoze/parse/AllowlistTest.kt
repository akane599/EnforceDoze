// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.testing.fixtures.Fixtures
import org.junit.Test

class AllowlistTest {
    @Test fun everyCaptureParsesCompletely() {
        Fixtures.with("deviceidle-whitelist.txt").forEach { capture ->
            val list = Allowlist.parse(capture.text("deviceidle-whitelist.txt"))
            assertWithMessage("$capture").that(list.unknownLines).isEmpty()
            assertWithMessage("$capture").that(list.entries).isNotEmpty()
            assertWithMessage("$capture").that(list.contains("com.android.shell", "system-excidle")).isTrue()
        }
    }

    @Test fun phoneHasUserRows() {
        val list = Allowlist.parse(Fixtures.capture("sm-s938b", "api37_CP2A.260605.016").text("deviceidle-whitelist.txt"))
        assertThat(list.entries).hasSize(185)
        assertThat(list.entries.groupingBy { it.kind }.eachCount())
            .containsExactly("system-excidle", 76, "system", 73, "user", 36)
    }

    @Test
    fun `the deviceidle dump lists the same rows as the whitelist command`() {
        for (build in listOf("sm-s938b" to "api37_CP2A.260605.016", "sdk_gphone64_x86_64" to "api37_CE2A.260420.051.A3")) {
            val capture = Fixtures.capture(build.first, build.second)
            val fromDump = Allowlist.fromDump(DeviceIdleDump.parse(capture.text("deviceidle.txt")))
            val fromCommand = Allowlist.parse(capture.text("deviceidle-whitelist.txt"))
            assertThat(fromDump.entries.map { it.kind to it.packageName })
                .containsExactlyElementsIn(fromCommand.entries.map { it.kind to it.packageName })
            assertThat(fromDump.entries.all { it.uid == null }).isTrue()
            assertThat(fromDump.unknownLines).isEmpty()
        }
    }
}
