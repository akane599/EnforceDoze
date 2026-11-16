// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.StateNotice

/** Home with its state; until the first state arrives, the ground alone, so nothing flashes a wrong verdict. */
@Composable
public fun HomeRoute(viewModel: HomeViewModel, navigation: HomeNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val actions = object : HomeActions {
        override fun changePlan() = navigation.changePlan()
        override fun pause() = viewModel.pause()
        override fun resume() = viewModel.resume()
        override fun openReport(sessionId: Long) = navigation.openReport(sessionId)
        override fun quickProof() = viewModel.quickProof()
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    HomeScreen(current, actions, modifier)
}
