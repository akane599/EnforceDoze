// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_MS = 5_000L

/** A store write that failed, as the owner reads it: the exception's own message, never a bare "failed". */
private fun Exception.line(): String = message ?: javaClass.simpleName

/** Naming and making a copy, shared by the list and the detail: the dialog's subject and the last failure. */
public class Cloner internal constructor(private val scope: CoroutineScope, private val source: ProfilesSource) {
    private val subjectState = MutableStateFlow<Profile?>(null)
    private val errorState = MutableStateFlow<String?>(null)

    /** The profile the clone dialog is naming a copy of, or null when the dialog is closed. */
    public val subject: StateFlow<Profile?> = subjectState.asStateFlow()
    public val error: StateFlow<String?> = errorState.asStateFlow()

    public fun start(profile: Profile) {
        errorState.value = null
        subjectState.value = profile
    }

    public fun cancel() {
        subjectState.value = null
    }

    /** Saves the copy, then hands its id to [opened] so the editor can open on it. */
    public fun clone(name: String, opened: (String) -> Unit) {
        val from = subjectState.value ?: return
        subjectState.value = null
        scope.launch {
            try {
                opened(source.clone(from.id, name))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                errorState.value = expected.line()
            }
        }
    }
}

public class ProfilesViewModel(private val source: ProfilesSource) : ViewModel() {
    public val state: StateFlow<ProfilesState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)
    public val cloner: Cloner = Cloner(viewModelScope, source)

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}

/** A profile's detail before and after the store has it. */
public sealed interface DetailUi {
    public data object Loading : DetailUi

    public data object Missing : DetailUi

    public data class Ready(val state: ProfilesState, val profile: Profile) : DetailUi
}

public class ProfileDetailViewModel(private val profileId: String, private val source: ProfilesSource) : ViewModel() {
    public val state: StateFlow<DetailUi> = source.state
        .map { state -> state.profile(profileId)?.let { DetailUi.Ready(state, it) } ?: DetailUi.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), DetailUi.Loading)
    public val cloner: Cloner = Cloner(viewModelScope, source)

    private val statusState = MutableStateFlow<ApplyStatus?>(null)
    public val status: StateFlow<ApplyStatus?> = statusState.asStateFlow()

    public fun apply(slot: Slot) {
        statusState.value = ApplyStatus(slot)
        viewModelScope.launch { statusState.value = ApplyStatus(slot, source.apply(profileId, slot)) }
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}

/** The editor before the store has the profile, when it is gone or built in (not editable), and ready. */
public sealed interface EditorUi {
    public data object Loading : EditorUi

    public data object Missing : EditorUi

    public data class Ready(val state: EditorState) : EditorUi
}

public class ProfileEditorViewModel(private val profileId: String, private val source: ProfilesSource) : ViewModel() {
    /** What the owner has typed and what the last save or delete said; a null draft shows the profile as saved. */
    private data class Local(
        val draft: Draft? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val refused: List<GuardrailProblem> = emptyList(),
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<EditorUi> = combine(source.state, local) { state, local ->
        val profile = state.profile(profileId)?.takeUnless { it.builtIn } ?: return@combine EditorUi.Missing
        val saved = profile.toDraft()
        EditorUi.Ready(
            EditorState(
                draft = local.draft ?: saved,
                saved = saved,
                stock = state.stock,
                inUse = state.slots(profileId).isNotEmpty(),
                busy = local.busy,
                error = local.error,
                refused = local.refused,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), EditorUi.Loading)

    public fun edit(draft: Draft) {
        local.update { it.copy(draft = draft, error = null, refused = emptyList()) }
    }

    /** Saves the draft; [done] runs once the store has taken it. The store runs the guardrails again. */
    public fun save(done: () -> Unit) {
        val ready = state.value as? EditorUi.Ready ?: return
        val profile = ready.state.draft.toProfile() ?: return
        write(done) {
            val refused = source.save(profile)
            local.update { it.copy(refused = refused) }
            refused.isEmpty()
        }
    }

    public fun delete(done: () -> Unit) {
        write(done) {
            source.delete(profileId)
            true
        }
    }

    private fun write(done: () -> Unit, block: suspend () -> Boolean) {
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (block()) {
                    local.update { it.copy(draft = null) }
                    done()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                local.update { it.copy(error = expected.line()) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}
