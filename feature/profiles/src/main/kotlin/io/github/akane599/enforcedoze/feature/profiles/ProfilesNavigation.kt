// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.StateNotice

/** Where the profile screens send the owner; the app's navigation implements it (ADR 0029). */
public interface ProfilesNavigation {
    public fun detail(profileId: String)

    public fun edit(profileId: String)

    /** The system's document picker with the profile's JSON (SPEC 6.3). */
    public fun export(profileId: String)

    public fun notice(notice: StateNotice)

    public fun back()
}

@Composable
public fun ProfilesRoute(
    viewModel: ProfilesViewModel,
    navigation: ProfilesNavigation,
    modifier: Modifier = Modifier,
    selected: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cloning by viewModel.cloner.subject.collectAsStateWithLifecycle()
    val error by viewModel.cloner.error.collectAsStateWithLifecycle()
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val actions = object : ProfilesActions {
        override fun open(profileId: String) = navigation.detail(profileId)
        override fun clone(profile: Profile) = viewModel.cloner.start(profile)
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    ProfilesScreen(current, actions, modifier, selected, error)
    cloning?.let {
        CloneDialog(it, onClone = { name -> viewModel.cloner.clone(name, navigation::edit) }, onDismiss = viewModel.cloner::cancel)
    }
}

/** [showBack] is false when the detail sits beside the list on a wide screen. */
@Composable
public fun ProfileDetailRoute(
    viewModel: ProfileDetailViewModel,
    navigation: ProfilesNavigation,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val cloning by viewModel.cloner.subject.collectAsStateWithLifecycle()
    val error by viewModel.cloner.error.collectAsStateWithLifecycle()
    when (val current = ui) {
        DetailUi.Loading -> Placeholder(null, navigation.takeIf { showBack }, modifier)

        DetailUi.Missing -> Placeholder(stringResource(R.string.profiles_not_found), navigation.takeIf { showBack }, modifier)

        is DetailUi.Ready -> {
            val profile = current.profile
            val actions = object : ProfileDetailActions {
                override val back: (() -> Unit)? = if (showBack) navigation::back else null
                override fun apply(slot: Slot) = viewModel.apply(slot)
                override fun clone() = viewModel.cloner.start(profile)
                override fun edit() = navigation.edit(profile.id)
                override fun export() = navigation.export(profile.id)
                override fun notice(notice: StateNotice) = navigation.notice(notice)
                override fun seen(notice: StateNotice) = viewModel.seen(notice)
            }
            ProfileDetailScreen(current.state, profile, actions, modifier, status, error)
            cloning?.let {
                CloneDialog(it, onClone = { name -> viewModel.cloner.clone(name, navigation::edit) }, onDismiss = viewModel.cloner::cancel)
            }
        }
    }
}

/** Back with unsaved edits asks first; a saved or deleted profile returns to where the owner came from. */
@Composable
public fun ProfileEditorRoute(viewModel: ProfileEditorViewModel, navigation: ProfilesNavigation, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var discarding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    when (val current = ui) {
        EditorUi.Loading -> Placeholder(null, navigation, modifier)

        EditorUi.Missing -> Placeholder(stringResource(R.string.profiles_not_found), navigation, modifier)

        is EditorUi.Ready -> {
            val state = current.state
            BackHandler(enabled = state.dirty) { discarding = true }
            val actions = object : ProfileEditorActions {
                override fun back() = if (state.dirty) discarding = true else navigation.back()
                override fun edit(draft: Draft) = viewModel.edit(draft)
                override fun save() = viewModel.save(navigation::back)
                override fun delete() {
                    deleting = true
                }
            }
            ProfileEditorScreen(state, actions, modifier)
            if (discarding) {
                DiscardDialog(
                    onDiscard = {
                        discarding = false
                        navigation.back()
                    },
                    onDismiss = { discarding = false },
                )
            }
            if (deleting) {
                DeleteDialog(
                    state.saved.name,
                    onDelete = {
                        deleting = false
                        viewModel.delete(navigation::back)
                    },
                    onDismiss = { deleting = false },
                )
            }
        }
    }
}

@Composable
private fun Placeholder(text: String?, navigation: ProfilesNavigation?, modifier: Modifier) {
    Scaffold(modifier, containerColor = Ed.palette.ground, topBar = { EdTopBar("", onBack = navigation?.let { it::back }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            text?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.quiet, modifier = Modifier.padding(Space.xl))
            }
        }
    }
}
