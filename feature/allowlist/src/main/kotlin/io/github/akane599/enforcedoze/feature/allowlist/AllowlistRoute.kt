// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.AppPickerScreen
import io.github.akane599.enforcedoze.ui.StateNotice

/** The allowlist, with the app picker over it while the owner adds an app; back closes the picker. */
@Composable
public fun AllowlistRoute(viewModel: AllowlistViewModel, navigation: AllowlistNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    if (picking) {
        BackHandler { picking = false }
        AppPickerScreen(stringResource(R.string.allowlist_add), current.addable, onPick = { app ->
            picking = false
            viewModel.add(app.packageName, app.label)
        }, onBack = { picking = false }, modifier = modifier)
        return
    }
    val actions = object : AllowlistActions {
        override fun add() = run { picking = true }
        override fun accept(suggestion: Suggestion) = viewModel.add(suggestion.packageName, suggestion.label)
        override fun remove(entry: AllowlistEntry) = viewModel.remove(entry)
        override fun keep(entry: AllowlistEntry, keep: Boolean) = viewModel.keep(entry, keep)
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    AllowlistScreen(current, actions, modifier, status)
}
