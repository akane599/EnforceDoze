// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import org.junit.Test

class DraftTest {
    private val night = BuiltInProfiles.NIGHT.cloneAs("Night 2", emptySet())

    @Test fun `a draft shows each timing in its own unit`() {
        val draft = night.toDraft()
        assertThat(draft.values["inactive_to"]).isEqualTo("2")
        assertThat(draft.values["sensing_to"]).isEqualTo("30")
        assertThat(draft.values["max_idle_to"]).isEqualTo("8")
        assertThat(draft.values["idle_factor"]).isEqualTo("2")
        assertThat(draft.values["motion_inactive_to"]).isEmpty()
    }

    @Test fun `an untouched draft saves the same profile`() {
        val saved = night.toDraft().toProfile()!!
        assertThat(saved.deviceIdle).isEqualTo(night.deviceIdle)
        assertThat(saved.levers).isEqualTo(night.levers)
    }

    @Test fun `a decimal comma is read, and a blank timing is left to the phone`() {
        val draft = night.toDraft().let { it.copy(values = it.values + ("inactive_to" to "1,5") + ("idle_to" to " ")) }
        val saved = draft.toProfile()!!
        assertThat(saved.deviceIdle["inactive_to"]).isEqualTo("90000")
        assertThat(saved.deviceIdle).doesNotContainKey("idle_to")
    }

    @Test fun `a value that does not divide evenly keeps three places`() {
        assertThat(toUnit("100000", TimingUnit.MINUTES)).isEqualTo("1.667")
    }

    @Test fun `field problems come before the guardrails run`() {
        val draft = night.toDraft().let { it.copy(name = " ", values = it.values + ("sensing_to" to "abc")) }
        assertThat(draft.problems(null)).containsExactly(DraftProblem.NameEmpty, DraftProblem.NotANumber("sensing_to"))
        assertThat(draft.toProfile()).isNull()
    }

    @Test fun `a timing below its guardrail is refused with the key`() {
        val draft = night.toDraft().let { it.copy(values = it.values + ("sensing_to" to "5")) }
        val problems = draft.problems(null)
        assertThat(problems).containsExactly(DraftProblem.Guardrail(GuardrailProblem.BelowMinimum("sensing_to", 10_000.0)))
        assertThat(problems.single().key).isEqualTo("sensing_to")
    }

    @Test fun `with Doze timing off nothing is written, so nothing is refused`() {
        val draft = night.toDraft().let { it.copy(constants = false, values = it.values + ("sensing_to" to "5")) }
        assertThat(draft.problems(null)).isEmpty()
    }

    @Test fun `an allowlist duration waits for the phone's own value`() {
        val profile = night.copy(deviceIdle = night.deviceIdle + ("sms_temp_app_allowlist_duration_ms" to "20000"))
        val problems = profile.toDraft().problems(stock = null)
        assertThat(problems).containsExactly(DraftProblem.Guardrail(GuardrailProblem.StockUnknown("sms_temp_app_allowlist_duration_ms")))
    }

    @Test fun `a negative delay is not a number the editor accepts`() {
        val draft = night.toDraft().copy(forceIdle = true, delaySec = "-5")
        assertThat(draft.problems(null)).containsExactly(DraftProblem.DelayNotANumber)
    }
}
