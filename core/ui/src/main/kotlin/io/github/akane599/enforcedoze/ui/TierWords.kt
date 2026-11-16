// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** "Tier T2": the privilege tier's name (SPEC 4.1). */
@Composable
public fun tierName(tier: Int): String = stringResource(R.string.ui_tier, tier)

/** What the tier lets the app do, in one line. A tier above T3 reads as T3. */
@Composable
public fun tierLine(tier: Int): String = stringResource(
    when (tier) {
        0 -> R.string.ui_tier_0
        1 -> R.string.ui_tier_1
        2 -> R.string.ui_tier_2
        else -> R.string.ui_tier_3
    },
)
