// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.recommend.Urgency

/** Onboarding; back steps to the previous stop, and on the first stop leaves to the system as usual. */
@Composable
public fun OnboardingRoute(viewModel: OnboardingViewModel, navigation: OnboardingNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val applyOutcome by viewModel.applyOutcome.collectAsStateWithLifecycle()
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    BackHandler(enabled = step != OnboardingStep.WELCOME) { viewModel.back() }
    // Back from the system's battery dialog or the app's settings page: read the exemption again.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshKeepAlive()
        onPauseOrDispose { }
    }
    val actions = object : OnboardingActions {
        override fun next() = viewModel.next()
        override fun allowBackground() = viewModel.allowBackground()
        override fun openAppSettings() = viewModel.openAppSettings()
        override fun back() {
            viewModel.back()
        }
        override fun grant() = viewModel.requestPermission()
        override fun recheck() = viewModel.recheck()
        override fun answer(urgency: Urgency) = viewModel.answer(urgency)
        override fun apply() = current.recommendation?.plan?.let(viewModel::apply) ?: Unit
        override fun runProof() = viewModel.runProof()
        override fun finish() = viewModel.finish(navigation::done)
    }
    OnboardingScreen(current, step, actions, modifier, OnboardingStatus(checking, applyOutcome))
}
