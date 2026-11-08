// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuardrailsTest {
    private val stock = mapOf(
        "sms_temp_app_allowlist_duration_ms" to 20_000L,
        "notification_allowlist_duration_ms" to 30_000L,
    )

    private fun check(vararg pairs: Pair<String, String>, stock: Map<String, Long>? = this.stock) =
        Guardrails.check(linkedMapOf(*pairs), stock)

    @Test
    fun `bounds from SPEC 6_3`() {
        assertThat(check("sensing_to" to "9999")).containsExactly(GuardrailProblem.BelowMinimum("sensing_to", 10_000.0))
        assertThat(check("sensing_to" to "10000")).isEmpty()
        assertThat(check("idle_to" to "299999")).containsExactly(GuardrailProblem.BelowMinimum("idle_to", 300_000.0))
        assertThat(check("max_idle_to" to "86400001")).containsExactly(GuardrailProblem.AboveMaximum("max_idle_to", 86_400_000.0))
        assertThat(check("idle_factor" to "0.5")).containsExactly(GuardrailProblem.BelowMinimum("idle_factor", 1.0))
        assertThat(check("idle_factor" to "4.5")).containsExactly(GuardrailProblem.AboveMaximum("idle_factor", 4.0))
        assertThat(check("idle_factor" to "4")).isEmpty()
        assertThat(check("idle_after_inactive_to" to "0")).containsExactly(GuardrailProblem.BelowMinimum("idle_after_inactive_to", 1_000.0))
    }

    @Test
    fun `allowlist durations never below stock`() {
        assertThat(check("sms_temp_app_allowlist_duration_ms" to "19999"))
            .containsExactly(GuardrailProblem.BelowStock("sms_temp_app_allowlist_duration_ms", 20_000L))
        assertThat(check("sms_temp_app_allowlist_duration_ms" to "60000")).isEmpty()
        assertThat(check("mms_temp_app_allowlist_duration_ms" to "60000"))
            .containsExactly(GuardrailProblem.StockUnknown("mms_temp_app_allowlist_duration_ms"))
        assertThat(check("notification_allowlist_duration_ms" to "60000", stock = null))
            .containsExactly(GuardrailProblem.StockUnknown("notification_allowlist_duration_ms"))
    }

    @Test
    fun `syntax is the encoder's and numbers must be numbers`() {
        assertThat(check("Bad-Key" to "1")).containsExactly(GuardrailProblem.Invalid("Bad-Key", "invalid key: Bad-Key"))
        assertThat(check("idle_factor" to "two")).containsExactly(
            GuardrailProblem.Invalid("idle_factor", "invalid value for idle_factor: two"),
            GuardrailProblem.NotANumber("idle_factor"),
        )
        assertThat(check("skip_sensing" to "true")).isEmpty()
        assertThat(Guardrails.check(emptyMap(), null)).isEmpty()
    }
}
