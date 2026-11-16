// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_MS = 5_000L

/** The last add or remove: the app's label and what came of it, null while it runs. */
public data class ChangeStatus(val label: String, val adding: Boolean, val outcome: ActionOutcome? = null)

public class AllowlistViewModel(private val source: AllowlistSource) : ViewModel() {
    public val state: StateFlow<AllowlistState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val statusState = MutableStateFlow<ChangeStatus?>(null)
    public val status: StateFlow<ChangeStatus?> = statusState.asStateFlow()

    public fun add(packageName: String, label: String) {
        change(label, adding = true) { source.add(packageName) }
    }

    public fun remove(entry: AllowlistEntry) {
        change(entry.label, adding = false) { source.remove(entry.packageName) }
    }

    public fun keep(entry: AllowlistEntry, keep: Boolean) {
        viewModelScope.launch { source.keep(entry.packageName, keep) }
    }

    private fun change(label: String, adding: Boolean, command: suspend () -> ActionOutcome) {
        statusState.value = ChangeStatus(label, adding)
        viewModelScope.launch {
            val outcome = try {
                command()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            }
            statusState.value = ChangeStatus(label, adding, outcome)
        }
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}
