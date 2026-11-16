// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

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

/** The last switch of all sensors: which way, and what came of it (null while it runs). */
public data class SwitchStatus(val on: Boolean, val outcome: ActionOutcome? = null)

public class SensorsViewModel(private val source: SensorsSource) : ViewModel() {
    public val state: StateFlow<SensorsState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val statusState = MutableStateFlow<SwitchStatus?>(null)
    public val status: StateFlow<SwitchStatus?> = statusState.asStateFlow()

    public fun allSensorsOff(on: Boolean) {
        if (statusState.value?.let { it.outcome == null } == true) return
        statusState.value = SwitchStatus(on)
        viewModelScope.launch {
            val outcome = try {
                source.allSensorsOff(on)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            }
            statusState.value = SwitchStatus(on, outcome)
        }
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}
