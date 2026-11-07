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
}
