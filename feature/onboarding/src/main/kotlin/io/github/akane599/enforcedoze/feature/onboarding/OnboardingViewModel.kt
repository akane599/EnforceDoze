// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.akane599.enforcedoze.profile.Plan
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_MS = 5_000L
private const val STEP_KEY = "step"

/** [start] is the first step shown: the privilege step when Settings sends the owner to check access again. */
public class OnboardingViewModel(
    private val source: OnboardingSource,
    private val saved: SavedStateHandle,
    start: OnboardingStep = OnboardingStep.WELCOME,
) : ViewModel() {
    public val state: StateFlow<OnboardingState?> = source.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** The step on screen; kept across process death, so the owner returns where they left. */
    public val step: StateFlow<OnboardingStep> = saved.getStateFlow(STEP_KEY, start)

    private val checkingState = MutableStateFlow(false)
    public val checking: StateFlow<Boolean> = checkingState.asStateFlow()

    private val applyState = MutableStateFlow<ActionOutcome?>(null)

    /** What the last Apply came to; a failure carries its raw line (SPEC 9.3). */
    public val applyOutcome: StateFlow<ActionOutcome?> = applyState.asStateFlow()

    public fun go(step: OnboardingStep) {
        saved[STEP_KEY] = step
        if (step == OnboardingStep.CAPABILITIES && state.value?.probes == null) recheck()
    }

    public fun next() {
        OnboardingStep.entries.getOrNull(step.value.ordinal + 1)?.let(::go)
    }

    public fun back(): Boolean {
        val previous = OnboardingStep.entries.getOrNull(step.value.ordinal - 1) ?: return false
        go(previous)
        return true
    }

    public fun requestPermission() {
        source.requestPermission()
    }

    public fun allowBackground() {
        source.allowBackground()
    }

    public fun openAppSettings() {
        source.openAppSettings()
    }

    public fun refreshKeepAlive() {
        source.refreshKeepAlive()
    }

    public fun recheck() {
        if (checkingState.value) return
        checkingState.value = true
        viewModelScope.launch {
            try {
                source.recheck()
            } finally {
                checkingState.value = false
            }
        }
    }

    public fun answer(urgency: Urgency) {
        viewModelScope.launch { source.answer(urgency) }
    }

    public fun apply(plan: Plan) {
        applyState.value = null
        viewModelScope.launch {
            applyState.value = try {
                source.apply(plan)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                ActionOutcome.Failed(expected.message ?: expected.javaClass.simpleName)
            }
        }
    }

    public fun runProof() {
        if (state.value?.proofRunning == true) return
        viewModelScope.launch { source.runProof() }
    }

    public fun finish(done: () -> Unit) {
        viewModelScope.launch {
            source.finish()
            done()
        }
    }
}
