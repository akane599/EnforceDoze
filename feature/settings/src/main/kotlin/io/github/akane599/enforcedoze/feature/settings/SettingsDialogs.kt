// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space

/** How long parsed records are kept (SPEC 8.3). */
@Composable
public fun RetentionDialog(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(R.string.settings_retention)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    stringResource(R.string.settings_retention_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ed.palette.quiet,
                )
                SettingsState.RETENTION_CHOICES.forEach { days ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touch)
                            .selectable(days == selected, role = Role.RadioButton, onClick = { onPick(days) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == selected, onClick = null)
                        Spacer(Modifier.width(Space.m))
                        Text(
                            pluralStringResource(R.plurals.settings_days, days, days),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ed.palette.ink,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Asks before a command that changes the phone or clears the app; [critical] marks the one that cannot be undone. */
@Composable
public fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    critical: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (critical) Ed.palette.critical else Ed.palette.ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
