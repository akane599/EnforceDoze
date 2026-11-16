// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.recommend.Urgency
import io.github.akane599.enforcedoze.ui.StateNotice

/** Where Plan and Recommended send the owner; the app's navigation implements it (ADR 0029). */
public interface PlanNavigation {
    public fun recommended()

    /** "Choose something else" ends on the profiles, where a slot can be filled by hand. */
    public fun profiles()

    public fun notice(notice: StateNotice)

    public fun back()
}

/** The picker Plan has open. */
private enum class PlanPicker { DAY, NIGHT, START, END }

@Composable
public fun PlanRoute(viewModel: PlanViewModel, navigation: PlanNavigation, modifier: Modifier = Modifier, showBack: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<PlanPicker?>(null) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val plan = current.plan
    val actions = object : PlanActions {
        override val back = if (showBack) navigation::back else null
        override fun pickDay() = run { picker = PlanPicker.DAY }
        override fun pickNight() = run { picker = PlanPicker.NIGHT }
        override fun night(on: Boolean) =
            viewModel.apply(if (on) PlanEdits.nightOn(plan, current.recommendation?.plan) else plan.copy(night = null))
        override fun editEdge(start: Boolean) = run { picker = if (start) PlanPicker.START else PlanPicker.END }
        override fun follow(on: Boolean) = viewModel.follow(on)
        override fun recommended() = navigation.recommended()
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    PlanScreen(current, actions, modifier, applying, outcome)
    val close = { picker = null }
    val night = plan.night
    when (picker) {
        PlanPicker.DAY -> ProfilePickerDialog(stringResource(R.string.plan_pick_day), current.profiles, plan.dayProfileId, onPick = {
            close()
            if (it != plan.dayProfileId) viewModel.apply(plan.copy(dayProfileId = it))
        }, onDismiss = close)

        PlanPicker.NIGHT -> if (night != null) {
            ProfilePickerDialog(stringResource(R.string.plan_pick_night), current.profiles, night.profileId, onPick = {
                close()
                if (it != night.profileId) viewModel.apply(plan.copy(night = night.copy(profileId = it)))
            }, onDismiss = close)
        }

        PlanPicker.START, PlanPicker.END -> if (night != null) {
            val start = picker == PlanPicker.START
            EdgePickerDialog(
                stringResource(if (start) R.string.plan_pick_start else R.string.plan_pick_end),
                initial = if (start) night.start else night.end,
                other = if (start) night.end else night.start,
                onPick = { time ->
                    close()
                    PlanEdits.withEdge(plan, start, time)?.takeIf { it != plan }?.let(viewModel::apply)
                },
                onDismiss = close,
            )
        }

        null -> Unit
    }
}

@Composable
public fun RecommendedRoute(viewModel: PlanViewModel, navigation: PlanNavigation, modifier: Modifier = Modifier, showBack: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val actions = object : RecommendedActions {
        override val back = if (showBack) navigation::back else null
        override fun apply() = current.recommendation?.plan?.let(viewModel::apply) ?: Unit
        override fun somethingElse() {
            viewModel.decline()
            navigation.profiles()
        }
        override fun changeUrgency() = run { asking = true }
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    RecommendedScreen(current, actions, modifier, applying, outcome)
    if (asking) {
        UrgencyDialog(current.urgency, onPick = { urgency: Urgency ->
            asking = false
            if (urgency != current.urgency) viewModel.answer(urgency)
        }, onDismiss = { asking = false })
    }
}
