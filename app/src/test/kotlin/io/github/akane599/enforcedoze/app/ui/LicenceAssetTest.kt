// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The licence and notice the app shows (Settings, About) are the repository's own, byte for byte (SPEC 0.3 rule 6). */
class LicenceAssetTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    @Test fun licenceMatchesTheRepository() = same("LICENSE.txt", "app/src/main/assets/LICENSE.txt")

    @Test fun noticeMatchesTheRepository() = same("NOTICE", "app/src/main/assets/NOTICE.txt")

    private fun same(source: String, shipped: String) = assertEquals(File(root, source).readText(), File(root, shipped).readText())
}
