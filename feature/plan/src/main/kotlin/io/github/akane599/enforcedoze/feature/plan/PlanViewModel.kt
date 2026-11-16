// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Urgency
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

/** Plan and Recommended share one view model: both read the same plan and recommendation and write the same plan. */
public class PlanViewModel(private val source: PlanSource) : ViewModel() {
    public val state: StateFlow<PlanState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val outcomeState = MutableStateFlow<ActionOutcome?>(null)

    /** What the last apply came to; a failure carries its raw line (SPEC 9.3). Cleared by the next change. */
    public val outcome: StateFlow<ActionOutcome?> = outcomeState.asStateFlow()

    private val applyingState = MutableStateFlow(false)

    /** A plan is on its way to the engine; the controls wait for it. */
    public val applying: StateFlow<Boolean> = applyingState.asStateFlow()

    public fun apply(plan: Plan) {
        if (applyingState.value) return
        outcomeState.value = null
        applyingState.value = true
        viewModelScope.launch {
            outcomeState.value = try {
                source.apply(plan)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            } finally {
                applyingState.value = false
            }
        }
    }

    public fun follow(on: Boolean) {
        viewModelScope.launch { source.follow(on) }
    }

    public fun answer(urgency: Urgency) {
        viewModelScope.launch { source.answer(urgency) }
    }

    public fun decline() {
        source.decline()
    }

    public fun seen(notice: StateNotice) {
        source.seen(notice.id)
    }
}
