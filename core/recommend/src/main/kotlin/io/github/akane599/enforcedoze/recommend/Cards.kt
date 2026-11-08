// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.STRICT_CARD_BATTERY_PERCENT

/** When the recommendation speaks up (SPEC 7.3 rule 5, 7.4, 7.5). */
public object Cards {
    /** Rule 5: Strict is never recommended, only offered as a one-tap card at low battery, unplugged. */
    public fun strictCard(batteryPercent: Int, plugged: Boolean): Boolean = !plugged && batteryPercent <= STRICT_CARD_BATTERY_PERCENT

    /**
     * A changed recommendation shows a card (7.5), but not when it is the plan in use or the one the owner
     * already turned down: "picking something else is remembered; the recommendation does not nag" (7.4).
     */
    public fun showChange(recommended: Plan, current: Plan?, lastDeclined: Plan?): Boolean =
        recommended != current && recommended != lastDeclined
}
