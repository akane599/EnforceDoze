// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.profileShortCost
import io.github.akane599.enforcedoze.ui.word
import java.time.LocalTime

/** Picks the profile for a slot: every profile with its one-line cost, the current one selected. */
@Composable
public fun ProfilePickerDialog(title: String, profiles: List<Profile>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val formats = rememberFormats()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    Choice(profile.id == selected, onClick = { onPick(profile.id) }) {
                        Text(profile.name, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                        Text(profileShortCost(profile, formats), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_cancel)) } },
    )
}

/**
 * Picks one edge of the night window on the owner's clock format. [other] is the opposite edge: the same time
 * would leave an empty window, so it cannot be confirmed. The dial gives way to typed entry when it would not fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun EdgePickerDialog(title: String, initial: LocalTime, other: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    val picked = LocalTime.of(state.hour, state.minute)
    val compact = LocalConfiguration.current.screenHeightDp < DIAL_MIN_HEIGHT || LocalDensity.current.fontScale > DIAL_MAX_FONT_SCALE
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (compact) TimeInput(state) else TimePicker(state)
                if (picked == other) {
                    Text(
                        stringResource(R.string.plan_window_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ed.palette.critical,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }, enabled = picked != other) { Text(stringResource(R.string.plan_set)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_cancel)) } },
    )
}

private const val DIAL_MIN_HEIGHT = 640
private const val DIAL_MAX_FONT_SCALE = 1.3f

/** The urgency question (SPEC 7.2 Q) with its three answers. */
@Composable
public fun UrgencyDialog(selected: Urgency, onPick: (Urgency) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(io.github.akane599.enforcedoze.ui.R.string.ui_urgency_question)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Urgency.entries.forEach { urgency ->
                    Choice(urgency == selected, onClick = { onPick(urgency) }) {
                        Text(urgency.word(), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_cancel)) } },
    )
}

@Composable
private fun Choice(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) { content() }
    }
}
