// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.StateNotice

@Composable
public fun SensorsRoute(viewModel: SensorsViewModel, navigation: SensorsNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val actions = object : SensorsActions {
        // Turning sensors back on needs no question; turning them off does (SPEC 9.1 "with confirmation").
        override fun allSensorsOff(on: Boolean) = if (on) confirming = true else viewModel.allSensorsOff(false)
        override fun openSnapshot(sessionId: Long) = navigation.snapshot(sessionId)
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    SensorsScreen(current, actions, modifier, status)
    if (confirming) {
        AllSensorsOffDialog(onConfirm = {
            confirming = false
            viewModel.allSensorsOff(true)
        }, onDismiss = { confirming = false })
    }
}

/** Asks before every sensor, the camera and the microphone stop for every app. */
@Composable
public fun AllSensorsOffDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(R.string.sensors_confirm_title)) },
        text = { Text(stringResource(R.string.sensors_confirm_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.sensors_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensors_cancel)) } },
    )
}
