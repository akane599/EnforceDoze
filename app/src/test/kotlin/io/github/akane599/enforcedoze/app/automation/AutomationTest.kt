// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutomationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun clients() = AutomationClients(folder.root.resolve("automation/clients.json"), clock = { 1_000L })

    @Test
    fun `a token is bound to its package and only its hash is stored`() {
        val store = clients()
        val token = store.issue("net.dinglisch.android.taskerm")
        assertThat(token).matches("[0-9a-f]{32}")
        assertThat(store.match(token)).isEqualTo("net.dinglisch.android.taskerm")
        assertThat(store.match(" $token\n")).isEqualTo("net.dinglisch.android.taskerm")
        assertThat(folder.root.resolve("automation/clients.json").readText()).doesNotContain(token)
        // A fresh instance reads the same file.
        assertThat(clients().match(token)).isEqualTo("net.dinglisch.android.taskerm")
    }

    @Test
    fun `a new token or removing the client revokes the old one`() {
        val store = clients()
        val first = store.issue("com.example.macro")
        val second = store.issue("com.example.macro")
        assertThat(first).isNotEqualTo(second)
        assertThat(store.match(first)).isNull()
        assertThat(store.match(second)).isEqualTo("com.example.macro")
        assertThat(store.clients()).hasSize(1)
        store.remove("com.example.macro")
        assertThat(store.match(second)).isNull()
    }

    @Test
    fun `wrong, empty and oversized tokens match nothing`() {
        val store = clients()
        store.issue("com.example.macro")
        assertThat(store.match(null)).isNull()
        assertThat(store.match("")).isNull()
        assertThat(store.match("0".repeat(32))).isNull()
        assertThat(store.match("a".repeat(10_000))).isNull()
    }

    @Test
    fun `state changes share one 30 s limit and status reads are never limited`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.PAUSE, "a", null, 0)).isEqualTo(Admission.Run("a"))
        assertThat(gate.admit(AutomationAction.APPLY_PROFILE, "b", null, 29_999)).isEqualTo(Admission.RateLimited("b"))
        assertThat(gate.admit(AutomationAction.GET_STATUS, "b", null, 29_999)).isEqualTo(Admission.Run("b"))
        // A dropped call does not restart the window.
        assertThat(gate.admit(AutomationAction.RESUME, "a", null, 30_000)).isEqualTo(Admission.Run("a"))
    }

    @Test
    fun `a bad token is logged at most once a minute`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.PAUSE, null, null, 0)).isEqualTo(Admission.BadToken(log = true))
        assertThat(gate.admit(AutomationAction.PAUSE, null, null, 59_999)).isEqualTo(Admission.BadToken(log = false))
        assertThat(gate.admit(AutomationAction.GET_STATUS, null, null, 60_000)).isEqualTo(Admission.BadToken(log = true))
    }

    @Test
    fun `a sender that shares its identity must be the bound package`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.PAUSE, "a", "evil.app", 0)).isEqualTo(Admission.Mismatch("a", "evil.app"))
        // The mismatch did not use up the window.
        assertThat(gate.admit(AutomationAction.PAUSE, "a", "a", 1)).isEqualTo(Admission.Run("a"))
    }

    @Test
    fun `intent actions map to the spec names only`() {
        assertThat(AutomationAction.of("io.github.akane599.enforcedoze.action.APPLY_PROFILE")).isEqualTo(AutomationAction.APPLY_PROFILE)
        assertThat(AutomationAction.of("io.github.akane599.enforcedoze.action.apply_profile")).isNull()
        assertThat(AutomationAction.of(null)).isNull()
    }
}
