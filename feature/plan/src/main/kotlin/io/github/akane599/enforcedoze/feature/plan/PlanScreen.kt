// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.profileShortCost
import io.github.akane599.enforcedoze.ui.word

/**
 * Plan (SPEC 9.1): the day slot, the night slot with its window, what Recommended proposes, and "Follow
 * Recommended automatically". Every change goes to the engine at once, as a profile's slot buttons do.
 */
@Composable
public fun PlanScreen(
    state: PlanState,
    actions: PlanActions,
    modifier: Modifier = Modifier,
    applying: Boolean = false,
    outcome: ActionOutcome? = null,
) {
    val formats = rememberFormats()
    val editable = state.canWrite && !applying
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.plan_title), onBack = actions.back) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.plan_slot_day), first = true)
            ProfileLine(state, state.plan.dayProfileId, formats, editable, actions::pickDay)
            NightSection(state, formats, editable, actions)
            OutcomeLine(applying, outcome)
            state.notices.forEach { notice ->
                Spacer(Modifier.height(Space.m))
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
            }
            SectionHeader(
                stringResource(R.string.plan_recommended_title),
                action = stringResource(R.string.plan_open),
                onAction = actions::recommended,
            )
            val recommendation = state.recommendation
            if (recommendation == null) {
                Text(stringResource(R.string.plan_recommended_none), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            } else {
                Text(
                    stringResource(
                        if (state.recommendedInUse) R.string.plan_recommended_in_use else R.string.plan_recommended_differs,
                        recommendation.confidence.word(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ed.palette.quiet,
                    modifier = Modifier.padding(bottom = Space.s),
                )
                PlanTimetable(recommendation.plan, state, formats, byHand = recommendation.byHand)
            }
            Spacer(Modifier.height(Space.l))
            SwitchLine(
                stringResource(R.string.plan_follow),
                stringResource(R.string.plan_follow_detail),
                checked = state.follow,
                enabled = state.canWrite,
                onChange = actions::follow,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun NightSection(state: PlanState, formats: Formats, editable: Boolean, actions: PlanActions) {
    val night = state.plan.night
    // The section's heading carries the switch: "Night", then "Night profile", then the profile Night read as one word three times.
    SwitchLine(
        stringResource(R.string.plan_slot_night),
        stringResource(if (night == null) R.string.plan_night_off_detail else R.string.plan_night_on_detail),
        checked = night != null,
        enabled = editable,
        onChange = actions::night,
        heading = true,
    )
    if (night == null) return
    Rule()
    ProfileLine(state, night.profileId, formats, editable, actions::pickNight)
    Rule()
    Window(night, formats, editable, actions)
    Text(
        stringResource(R.string.plan_window_rule, formats.time(night.start), formats.time(night.end)),
        style = MaterialTheme.typography.bodyMedium,
        color = Ed.palette.quiet,
        modifier = Modifier.padding(top = Space.s),
    )
}

/** A slot's profile with its short cost and a Change button (SPEC 9.1 "Change slots"). */
@Composable
private fun ProfileLine(state: PlanState, profileId: String, formats: Formats, editable: Boolean, onChange: () -> Unit) {
    val profile = state.profile(profileId)
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(state.name(profileId), style = MaterialTheme.typography.titleLarge, color = Ed.palette.ink)
            profile?.let { Text(profileShortCost(it, formats), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet) }
        }
        TextButton(onClick = onChange, enabled = editable) { Text(stringResource(R.string.plan_change)) }
    }
}

/** The window's two edges as ruled timetable columns; each opens a time picker (SPEC 9.1 "Change window"). */
@Composable
private fun Window(night: NightSlot, formats: Formats, editable: Boolean, actions: PlanActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        Edge(stringResource(R.string.plan_from), formats.time(night.start), editable, Modifier.weight(1f)) { actions.editEdge(true) }
        Edge(stringResource(R.string.plan_until), formats.time(night.end), editable, Modifier.weight(1f)) { actions.editEdge(false) }
    }
}

@Composable
private fun Edge(label: String, time: String, editable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val change = stringResource(R.string.plan_change)
    Column(
        modifier
            .heightIn(min = Sizes.touch)
            .clickable(enabled = editable, onClickLabel = change, role = Role.Button, onClick = onClick)
            .padding(vertical = Space.m),
    ) {
        ColumnHead(label)
        Text(
            time,
            style = MaterialTheme.typography.headlineMedium,
            color = if (editable) Ed.palette.ink else Ed.palette.quiet,
            textAlign = TextAlign.Start,
        )
    }
}

/** A switch with its title and a quiet line; as a section's [heading] the title takes the heading's size and spacing. */
@Composable
private fun SwitchLine(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    heading: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (heading) Space.xl else 0.dp)
            .heightIn(min = Sizes.touch)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (heading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = if (enabled || heading) Ed.palette.ink else Ed.palette.quiet,
                modifier = if (heading) Modifier.semantics { heading() } else Modifier,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }
        Spacer(Modifier.width(Space.m))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically))
    }
}
