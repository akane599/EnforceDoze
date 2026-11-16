// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What another app can reach (ADR 0036): no upstream component, and every exported one guarded by a permission,
 * except the launcher and the automation receiver, which checks a token per call and is off until the owner turns
 * it on (ADR 0033).
 */
class ManifestSafetyTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val kinds = listOf("activity", "activity-alias", "receiver", "service", "provider")

    private fun components(path: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(File(path))
        return kinds.flatMap { kind ->
            val nodes = doc.getElementsByTagName(kind)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
    }

    private fun Element.attr(name: String): String? = getAttributeNS(android, name).takeIf { it.isNotEmpty() }

    @Test
    fun `no upstream component is registered`() {
        val upstream = components("src/main/AndroidManifest.xml").mapNotNull { it.attr("name") }
            .filter { it.startsWith("com.akylas.enforcedoze.") }
        assertThat(upstream).isEmpty()
    }

    @Test
    fun `every exported component is guarded`() {
        val open = listOf("src/main/AndroidManifest.xml", "src/debug/AndroidManifest.xml").flatMap(::components)
            .filter { it.attr("exported") == "true" && it.attr("permission") == null }
            .mapNotNull { it.attr("name") }
        assertThat(open).containsExactly(
            "io.github.akane599.enforcedoze.app.ui.MainActivity",
            "io.github.akane599.enforcedoze.app.automation.AutomationReceiver",
        )
    }

    @Test
    fun `the automation receiver starts disabled`() {
        val receiver = components("src/main/AndroidManifest.xml")
            .single { it.attr("name") == "io.github.akane599.enforcedoze.app.automation.AutomationReceiver" }
        assertWithMessage("enabled only when the owner turns the API on").that(receiver.attr("enabled")).isEqualTo("false")
    }
}
