// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tag
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard

/**
 * The profiles (SPEC 9.1) as a timetable of services: each row names a profile, marks the slot it runs in and
 * whether Recommended uses it, says its cost in a line, and shows its levers like a service's days of
 * operation. [selected] is highlighted when the detail sits beside the list.
 */
@Composable
public fun ProfilesScreen(
    state: ProfilesState,
    actions: ProfilesActions,
    modifier: Modifier = Modifier,
    selected: String? = null,
    error: String? = null,
) {
    val formats = rememberFormats()
    Scaffold(modifier = modifier, containerColor = Ed.palette.ground, topBar = {
        EdTopBar(stringResource(R.string.profiles_title))
    }) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.l))
            error?.let {
                Notice(stringResource(R.string.profiles_failed), it, tone = Tone.CRITICAL, raw = true)
                Spacer(Modifier.height(Space.m))
            }
            state.notices.forEach { notice ->
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
                Spacer(Modifier.height(Space.m))
            }
            SectionHeader(stringResource(R.string.profiles_built_in), first = state.notices.isEmpty() && error == null)
            Rows(state.profiles.filter { it.builtIn }, state, formats, actions, selected)
            SectionHeader(stringResource(R.string.profiles_custom))
            val custom = state.profiles.filterNot { it.builtIn }
            if (custom.isEmpty()) {
                Text(
                    stringResource(R.string.profiles_custom_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ed.palette.quiet,
                    modifier = Modifier.padding(vertical = Space.s),
                )
            } else {
                Rows(custom, state, formats, actions, selected)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun ColumnScope.Rows(profiles: List<Profile>, state: ProfilesState, formats: Formats, actions: ProfilesActions, selected: String?) {
    Rule()
    profiles.forEach { profile ->
        ProfileRow(profile, state, formats, actions, profile.id == selected)
        Rule()
    }
}

@Composable
private fun ProfileRow(profile: Profile, state: ProfilesState, formats: Formats, actions: ProfilesActions, selected: Boolean) {
    val palette = Ed.palette
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(palette.raised) else Modifier)
            .semantics { this.selected = selected }
            .clickable { actions.open(profile.id) },
    ) {
        // The marks go under the cost when the name would have to share a narrow row with them.
        val wide = maxWidth >= 360.dp * LocalDensity.current.fontScale
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.m), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(profile.name, style = MaterialTheme.typography.titleLarge, color = palette.ink)
                    ProfileTags(profile, state, formats)
                }
                Text(shortCost(profile, formats), style = MaterialTheme.typography.bodyMedium, color = palette.quiet)
                if (!wide) {
                    Spacer(Modifier.height(Space.s))
                    LeverMarks(profile)
                }
            }
            if (wide) {
                Box(Modifier.padding(start = Space.m, top = Space.xs)) { LeverMarks(profile) }
            }
            IconButton(onClick = { actions.clone(profile) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.profiles_clone_named, profile.name))
            }
        }
    }
}

/** The slot a profile runs in (accent: it is the current item), and quiet marks for Recommended and custom. */
@Composable
internal fun ProfileTags(profile: Profile, state: ProfilesState, formats: Formats, showCustom: Boolean = false) {
    val slots = state.slots(profile.id)
    if (Slot.DAY in slots) Tag(stringResource(R.string.profiles_by_day))
    if (Slot.NIGHT in slots) {
        state.plan.night?.let { Tag(stringResource(R.string.profiles_at_night, formats.time(it.start), formats.time(it.end))) }
    }
    if (profile.id in state.recommended) Tag(stringResource(R.string.profiles_recommended), accent = false)
    if (showCustom && !profile.builtIn) Tag(stringResource(R.string.profiles_custom_tag), accent = false)
}

/**
 * The four levers A–D in fixed places, like the days a timetable service runs: a lever the profile uses is
 * outlined in ink, one it leaves alone is a hairline box with a quiet letter. TalkBack reads the names.
 */
@Composable
internal fun LeverMarks(profile: Profile) {
    val palette = Ed.palette
    val lines = leverLines(profile, rememberFormats())
    val on = lines.filter { it.on }.map { it.title }
    val spoken = if (on.isEmpty()) {
        stringResource(
            R.string.profiles_levers_none,
        )
    } else {
        stringResource(R.string.profiles_levers_spoken, on.joinToString(", "))
    }
    val box = MARK * LocalDensity.current.fontScale.coerceAtMost(MAX_MARK_SCALE)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        lines.forEach { line ->
            val shape = MaterialTheme.shapes.extraSmall
            Box(
                Modifier
                    .size(box)
                    .border(BorderStroke(if (line.on) 1.5.dp else Space.hair, if (line.on) palette.ink else palette.hairline), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    line.letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (line.on) palette.ink else palette.hairline,
                    fontWeight = if (line.on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val MARK = 22.dp

/** The marks grow with the font up to 1.5×, then stay that size so a row keeps room for the name. */
private const val MAX_MARK_SCALE = 1.5f
