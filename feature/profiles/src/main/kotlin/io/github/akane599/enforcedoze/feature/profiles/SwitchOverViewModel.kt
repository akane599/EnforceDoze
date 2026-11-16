// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_MS = 5_000L

/** The last switch-over command and what came of it; [outcome] is null while it runs. */
public data class SwitchStatus(val command: SwitchCommand, val outcome: ActionOutcome? = null)

public enum class SwitchCommand { IMPORT, RETURN }

public class SwitchOverViewModel(private val source: SwitchOverSource) : ViewModel() {
    public val state: StateFlow<SwitchOverState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val statusState = MutableStateFlow<SwitchStatus?>(null)
    public val status: StateFlow<SwitchStatus?> = statusState.asStateFlow()

    public fun import(name: String): Unit = run(SwitchCommand.IMPORT) {
        val problems = source.import(name)
        if (problems.isEmpty()) ActionOutcome.Done else ActionOutcome.Failed(problems.joinToString("\n"))
    }

    public fun returnToPrevious(): Unit = run(SwitchCommand.RETURN) { source.returnToPrevious() }

    public fun startCheck() {
        viewModelScope.launch { source.startCheck() }
    }

    public fun appInfo(packageName: String): Unit = source.appInfo(packageName)

    private fun run(command: SwitchCommand, block: suspend () -> ActionOutcome) {
        if (statusState.value?.let { it.outcome == null } == true) return
        statusState.value = SwitchStatus(command)
        viewModelScope.launch {
            val outcome = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            }
            statusState.value = SwitchStatus(command, outcome)
        }
    }
}
