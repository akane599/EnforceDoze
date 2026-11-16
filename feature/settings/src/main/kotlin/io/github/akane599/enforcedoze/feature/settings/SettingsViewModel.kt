// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

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

/** The last command and what came of it; [outcome] is null while it runs. */
public data class CommandStatus(val command: SettingsCommand, val outcome: ActionOutcome? = null)

public class SettingsViewModel(private val source: SettingsSource) : ViewModel() {
    public val state: StateFlow<SettingsState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val statusState = MutableStateFlow<CommandStatus?>(null)
    public val status: StateFlow<CommandStatus?> = statusState.asStateFlow()

    public fun reapplyOnDrift(on: Boolean) {
        viewModelScope.launch { source.reapplyOnDrift(on) }
    }

    public fun automation(on: Boolean) {
        viewModelScope.launch { source.automation(on) }
    }

    public fun retention(days: Int) {
        viewModelScope.launch { source.retention(days) }
    }

    public fun removeKeepAwake(app: AppRef) {
        viewModelScope.launch { source.removeKeepAwake(app.packageName) }
    }

    public fun run(command: SettingsCommand, flagSyncDisabled: Boolean = false) {
        if (statusState.value?.let { it.outcome == null } == true) return
        statusState.value = CommandStatus(command)
        viewModelScope.launch {
            val outcome = try {
                when (command) {
                    SettingsCommand.RESTORE_STOCK -> source.restoreStock()
                    SettingsCommand.ALLOW_TASKER -> source.allowTasker()
                    SettingsCommand.FLAG_SYNC -> source.flagSync(flagSyncDisabled)
                    SettingsCommand.RESET -> source.reset()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            }
            statusState.value = CommandStatus(command, outcome)
        }
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}
