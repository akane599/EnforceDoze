// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.doze.Lever

/** The lever as the owner reads it (SPEC 5.2–5.4): what it does, not how it is written. */
@Composable
public fun Lever.title(): String = stringResource(
    when (this) {
        Lever.CONSTANTS -> R.string.ui_lever_constants
        Lever.FORCE_IDLE -> R.string.ui_lever_force_idle
        Lever.SENSORS -> R.string.ui_lever_sensors
        Lever.SENSOR_PRIVACY -> R.string.ui_lever_sensor_privacy
        Lever.ALLOWLIST -> R.string.ui_lever_allowlist
        Lever.WIFI -> R.string.ui_lever_wifi
        Lever.MOBILE_DATA -> R.string.ui_lever_mobile_data
        Lever.APP_SUSPENSION -> R.string.ui_lever_app_suspension
    },
)
