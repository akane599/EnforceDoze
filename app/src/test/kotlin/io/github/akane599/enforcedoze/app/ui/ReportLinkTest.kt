// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReportLinkTest {
    @Test
    fun `the report link works only through the protected alias`() {
        assertThat(ReportLink.route(ReportLink.ALIAS, 42)).isEqualTo(ReportKey(42))
        assertThat(ReportLink.route(ReportLink.ALIAS, -1)).isEqualTo(LogKey)
        // ADR 0035 review: MainActivity is exported, so an app without the permission could address it directly.
        assertThat(ReportLink.route(MainActivity::class.java.name, 42)).isNull()
        assertThat(ReportLink.route(null, 42)).isNull()
    }

    @Test
    fun `the alias name matches the manifest`() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android:name=\"${ReportLink.ALIAS}\"")
        assertThat(manifest).contains("<action android:name=\"${ReportLink.ACTION}\" />")
    }
}
