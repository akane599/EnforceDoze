// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space

/** An installed app the owner can pick; [note] says why it is offered first or why it is already chosen. */
@Immutable
public data class PickApp(val packageName: String, val label: String, val system: Boolean, val note: String? = null)

/**
 * Picks one installed app (SPEC 9.1): user apps with search, system apps behind a toggle. The allowlist and the
 * keep-awake list both use it; [apps] holds only the apps that can still be added.
 */
@Composable
public fun AppPickerScreen(
    title: String,
    apps: List<PickApp>,
    onPick: (PickApp) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(false) }
    val shown = apps.filter { (system || !it.system) && it.matches(query) }
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(title, onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = Space.column).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.gutter)) {
                item {
                    OutlinedTextField(
                        query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.ui_picker_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    )
                    FilterChip(
                        selected = system,
                        onClick = { system = !system },
                        label = { Text(stringResource(R.string.ui_picker_system)) },
                        modifier = Modifier.padding(vertical = Space.s),
                    )
                    Rule()
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.ui_picker_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ed.palette.ink,
                            modifier = Modifier.padding(top = Space.l),
                        )
                    }
                }
                items(shown, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touch)
                            .clickable(role = Role.Button, onClick = { onPick(app) })
                            .padding(vertical = Space.m)
                            .semantics(mergeDescendants = true) { },
                    ) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                        Text(app.note ?: app.packageName, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
                    }
                    Rule()
                }
            }
        }
    }
}

private fun PickApp.matches(query: String): Boolean =
    query.isBlank() || label.contains(query.trim(), ignoreCase = true) || packageName.contains(query.trim(), ignoreCase = true)
