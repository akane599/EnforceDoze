// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Test
import java.time.LocalTime

/**
 * SPEC 7.5 and 15: Recommended never switches plans without a tap unless the owner turned on automatic following.
 * [AppServices.followRecommendation] is the only switch that is not an owner's tap, and it applies what
 * [planToFollow] returns.
 */
class FollowRecommendationTest {
    private val everyday = Plan("everyday")
    private val withNight = Plan("everyday", NightSlot("night", LocalTime.of(23, 0), LocalTime.of(7, 30)))

    @Test fun `with following off a different recommendation is not applied`() {
        assertThat(planToFollow(following = false, recommended = withNight, inForce = everyday)).isNull()
    }

    @Test fun `with following off it is not applied even when no plan is in force`() {
        assertThat(planToFollow(following = false, recommended = withNight, inForce = null)).isNull()
    }

    @Test fun `with following on a different recommendation is applied`() {
        assertThat(planToFollow(following = true, recommended = withNight, inForce = everyday)).isEqualTo(withNight)
    }

    @Test fun `with following on the plan in force is not applied again`() {
        assertThat(planToFollow(following = true, recommended = everyday, inForce = everyday)).isNull()
    }

    @Test fun `without a recommendation nothing is applied`() {
        assertThat(planToFollow(following = true, recommended = null, inForce = everyday)).isNull()
    }
}
