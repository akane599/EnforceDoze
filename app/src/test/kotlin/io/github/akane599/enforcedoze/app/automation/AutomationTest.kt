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
        assertThat(gate.admit(AutomationAction.PAUSE, "a", null, null, 0)).isEqualTo(Admission.Run("a"))
        assertThat(gate.admit(AutomationAction.APPLY_PROFILE, "b", null, null, 29_999))
            .isEqualTo(Admission.Dropped("b", Drop.RATE_LIMITED))
        assertThat(gate.admit(AutomationAction.GET_STATUS, "b", null, null, 29_999)).isEqualTo(Admission.Run("b"))
        // A dropped call does not restart the window.
        assertThat(gate.admit(AutomationAction.RESUME, "a", null, null, 30_000)).isEqualTo(Admission.Run("a"))
    }

    @Test
    fun `a flood of dropped calls is logged once a minute per client and kind`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.PAUSE, null, null, null, 0)).isEqualTo(Admission.Dropped(null, Drop.BAD_TOKEN))
        assertThat(gate.admit(AutomationAction.PAUSE, null, null, null, 59_999))
            .isEqualTo(Admission.Dropped(null, Drop.BAD_TOKEN, log = false))
        assertThat(gate.admit(AutomationAction.GET_STATUS, null, null, null, 60_000)).isEqualTo(Admission.Dropped(null, Drop.BAD_TOKEN))
        gate.admit(AutomationAction.PAUSE, "a", null, null, 60_000)
        assertThat(gate.admit(AutomationAction.PAUSE, "a", null, null, 60_001)).isEqualTo(Admission.Dropped("a", Drop.RATE_LIMITED))
        assertThat(gate.admit(AutomationAction.PAUSE, "a", null, null, 60_002))
            .isEqualTo(Admission.Dropped("a", Drop.RATE_LIMITED, log = false))
        // Another client's drop has its own row.
        assertThat(gate.admit(AutomationAction.PAUSE, "b", null, null, 60_003)).isEqualTo(Admission.Dropped("b", Drop.RATE_LIMITED))
    }

    @Test
    fun `a sender that shares its identity must be the bound package`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.PAUSE, "a", "evil.app", null, 0))
            .isEqualTo(Admission.Dropped("a", Drop.SENDER_MISMATCH, "evil.app"))
        // The mismatch did not use up the window.
        assertThat(gate.admit(AutomationAction.PAUSE, "a", "a", null, 1)).isEqualTo(Admission.Run("a"))
    }

    @Test
    fun `an invalid call is rejected without using up the window`() {
        val gate = AutomationGate()
        assertThat(gate.admit(AutomationAction.APPLY_PROFILE, "a", null, "no profile nope", 0))
            .isEqualTo(Admission.Dropped("a", Drop.INVALID, "no profile nope"))
        assertThat(gate.admit(AutomationAction.APPLY_PROFILE, "a", null, null, 1)).isEqualTo(Admission.Run("a"))
    }

    @Test
    fun `sensors off needs on as a boolean or its text`() {
        assertThat(Automation.onValue("true", present = true) { it }).isTrue()
        assertThat(Automation.onValue(" false ", present = true) { it }).isFalse()
        assertThat(Automation.onValue("yes", present = true) { it }).isNull()
        assertThat(Automation.onValue(null, present = false) { it }).isNull()
        // A boolean extra reads the same whatever the default.
        assertThat(Automation.onValue(null, present = true) { true }).isTrue()
        assertThat(Automation.onValue(null, present = true) { false }).isFalse()
        // Another type (say an int) gives back the default each time.
        assertThat(Automation.onValue(null, present = true) { it }).isNull()
    }

    @Test
    fun `intent actions map to the spec names only`() {
        assertThat(AutomationAction.of("io.github.akane599.enforcedoze.action.APPLY_PROFILE")).isEqualTo(AutomationAction.APPLY_PROFILE)
        assertThat(AutomationAction.of("io.github.akane599.enforcedoze.action.apply_profile")).isNull()
        assertThat(AutomationAction.of(null)).isNull()
    }
}
