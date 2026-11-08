// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Test

class CardsTest {
    @Test
    fun `the Strict card shows at 20 percent or less, unplugged`() {
        assertThat(Cards.strictCard(20, plugged = false)).isTrue()
        assertThat(Cards.strictCard(5, plugged = false)).isTrue()
        assertThat(Cards.strictCard(21, plugged = false)).isFalse()
        assertThat(Cards.strictCard(10, plugged = true)).isFalse()
    }

    @Test
    fun `a changed recommendation shows once, never for the plan in use or the one turned down`() {
        val pocket = Plan("pocket")
        assertThat(Cards.showChange(pocket, current = Plan("everyday"), lastDeclined = null)).isTrue()
        assertThat(Cards.showChange(pocket, current = pocket, lastDeclined = null)).isFalse()
        assertThat(Cards.showChange(pocket, current = Plan("everyday"), lastDeclined = pocket)).isFalse()
        assertThat(Cards.showChange(pocket, current = null, lastDeclined = Plan("classic"))).isTrue()
    }
}
