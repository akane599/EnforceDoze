// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.profile.Profile

/** Names the copy before it is made; the editor opens on it next (SPEC 6.3). */
@Composable
public fun CloneDialog(profile: Profile, onClone: (name: String) -> Unit, onDismiss: () -> Unit) {
    val suggested = stringResource(R.string.profiles_clone_default, profile.name)
    var name by rememberSaveable(profile.id) { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(R.string.profiles_clone_named, profile.name)) },
        text = {
            OutlinedTextField(
                name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profiles_clone_name)) },
                singleLine = true,
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) ({ Text(stringResource(R.string.profiles_problem_name)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onClone(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.profiles_clone)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

/** Asks before a custom profile is removed; sessions keep its name (SPEC 8.3). */
@Composable
public fun DeleteDialog(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(R.string.profiles_delete_title, name)) },
        text = { Text(stringResource(R.string.profiles_delete_body)) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.profiles_delete_confirm), color = Ed.palette.critical) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

/** Asks before unsaved edits are dropped on the way back. */
@Composable
public fun DiscardDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.palette.raised,
        title = { Text(stringResource(R.string.profiles_discard_title)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.profiles_discard)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_keep_editing)) } },
    )
}
