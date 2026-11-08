// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Home needs from the app (ADR 0029): the mapped state and the commands behind its buttons. */
public interface HomeSource {
    public val state: Flow<HomeState>

    public suspend fun pause()

    public suspend fun resume()

    public suspend fun quickProof()

    public fun seen(noticeId: String)
}

public class HomeViewModel(private val source: HomeSource) : ViewModel() {
    public val state: StateFlow<HomeState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    public fun pause() {
        viewModelScope.launch { source.pause() }
    }

    public fun resume() {
        viewModelScope.launch { source.resume() }
    }

    public fun quickProof() {
        viewModelScope.launch { source.quickProof() }
    }

    public fun seen(notice: HomeNotice) {
        source.seen(notice.id)
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
