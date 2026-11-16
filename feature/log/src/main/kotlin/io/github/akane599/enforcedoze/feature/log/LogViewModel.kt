// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

private const val STOP_MS = 5_000L

public class LogViewModel(private val source: LogSource) : ViewModel() {
    public val state: StateFlow<LogState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val filterState = MutableStateFlow(LogFilter())
    public val filter: StateFlow<LogFilter> = filterState.asStateFlow()

    public fun filter(filter: LogFilter) {
        filterState.value = filter
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}
