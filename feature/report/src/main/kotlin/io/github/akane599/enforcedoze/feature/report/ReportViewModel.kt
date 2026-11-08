// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the report needs from the app (ADR 0029). */
public interface ReportSource {
    /** The report, or null when the session is gone (retention, or a reset). */
    public fun report(sessionId: Long): Flow<ReportState?>

    /** Records "Something arrived late" for [sessionId] and steps the recommendation (SPEC 7.5). */
    public suspend fun late(sessionId: Long, packageName: String?): LateOutcome

    public fun seen(noticeId: String)
}

public sealed interface ReportUi {
    public data object Loading : ReportUi

    public data object Missing : ReportUi

    public data class Ready(val state: ReportState) : ReportUi
}

/** The late sheet while it is open: [outcome] is null until the owner records. */
@Immutable
public data class LateSheet(val outcome: LateOutcome? = null, val busy: Boolean = false, val error: String? = null)

public class ReportViewModel(private val sessionId: Long, private val source: ReportSource) : ViewModel() {
    public val state: StateFlow<ReportUi> = source.report(sessionId)
        .map { if (it == null) ReportUi.Missing else ReportUi.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ReportUi.Loading)

    private val sheetState = MutableStateFlow<LateSheet?>(null)
    public val sheet: StateFlow<LateSheet?> = sheetState.asStateFlow()

    public fun openLate() {
        sheetState.value = LateSheet()
    }

    public fun closeLate() {
        sheetState.value = null
    }

    public fun recordLate(packageName: String?) {
        if (sheetState.value?.busy == true) return
        sheetState.update { it?.copy(busy = true) }
        viewModelScope.launch {
            val next = try {
                LateSheet(outcome = source.late(sessionId, packageName))
            } catch (e: CancellationException) {
                throw e
            } catch (expected: Exception) {
                // Shown in the sheet with the message that caused it (SPEC 9.3), so the owner can try again.
                LateSheet(error = expected.message ?: expected.javaClass.simpleName)
            }
            sheetState.update { it?.let { next } }
        }
    }

    public fun seen(noticeId: String) {
        source.seen(noticeId)
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
