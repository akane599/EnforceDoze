// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.ColumnHead
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard

/** What the detail does on the owner's taps. [back] is null when the detail sits beside the list. */
public interface ProfileDetailActions {
    public val back: (() -> Unit)?

    public fun apply(slot: Slot)

    public fun clone()

    public fun edit()

    public fun export()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** The last apply the owner asked for: running while [outcome] is null. */
public data class ApplyStatus(val slot: Slot, val outcome: ActionOutcome? = null)

/**
 * One profile (SPEC 9.1): what it is for, the slot buttons, what it costs, its four levers, and its timings
 * against the phone's own values with a plain line for each key.
 */
@Composable
public fun ProfileDetailScreen(
    state: ProfilesState,
    profile: Profile,
    actions: ProfileDetailActions,
    modifier: Modifier = Modifier,
    status: ApplyStatus? = null,
    error: String? = null,
) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = {
            EdTopBar(profile.name, onBack = actions.back) {
                IconButton(onClick = actions::export) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.profiles_export))
                }
            }
        },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                ProfileTags(profile, state, formats, showCustom = true)
            }
            Spacer(Modifier.height(Space.m))
            Text(purpose(profile), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            Spacer(Modifier.height(Space.l))
            SlotButtons(state, profile, actions)
            StatusLine(profile, status)
            error?.let {
                Spacer(Modifier.height(Space.m))
                Notice(stringResource(R.string.profiles_failed), it, tone = Tone.CRITICAL, raw = true)
            }
            state.notices.forEach { notice ->
                Spacer(Modifier.height(Space.m))
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
            }
            SectionHeader(stringResource(R.string.profiles_what_costs))
            // Each cost is its own sentence with air between, so four derived lines do not read as one paragraph.
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                costLines(profile, formats).forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink) }
            }
            SectionHeader(stringResource(R.string.profiles_levers))
            LeverTable(profile, formats)
            SectionHeader(stringResource(R.string.profiles_timings))
            Timings(state, profile, formats)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun SlotButtons(state: ProfilesState, profile: Profile, actions: ProfileDetailActions) {
    val slots = state.slots(profile.id)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedButton(onClick = { actions.apply(Slot.DAY) }, enabled = state.canWrite && Slot.DAY !in slots) {
            Text(stringResource(if (Slot.DAY in slots) R.string.profiles_in_use_day else R.string.profiles_use_day))
        }
        OutlinedButton(onClick = { actions.apply(Slot.NIGHT) }, enabled = state.canWrite && Slot.NIGHT !in slots) {
            Text(stringResource(if (Slot.NIGHT in slots) R.string.profiles_in_use_night else R.string.profiles_use_night))
        }
        // The secondary actions wrap as one group, so "Edit" never sits alone on a line.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton(onClick = actions::clone) { Text(stringResource(R.string.profiles_clone)) }
            if (!profile.builtIn) TextButton(onClick = actions::edit) { Text(stringResource(R.string.profiles_edit)) }
        }
    }
}

@Composable
private fun StatusLine(profile: Profile, status: ApplyStatus?) {
    when (val outcome = status?.outcome) {
        null -> if (status != null) {
            Text(stringResource(R.string.profiles_applying), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }

        ActionOutcome.Done -> Unit

        is ActionOutcome.Unavailable -> {
            Spacer(Modifier.height(Space.m))
            Notice(stringResource(R.string.profiles_apply_failed, profile.name), outcome.reason, tone = Tone.WARNING)
        }

        is ActionOutcome.Failed -> {
            Spacer(Modifier.height(Space.m))
            Notice(stringResource(R.string.profiles_apply_failed, profile.name), outcome.raw, tone = Tone.CRITICAL, raw = true)
        }
    }
}

@Composable
private fun LeverTable(profile: Profile, formats: Formats) {
    Rule()
    leverLines(profile, formats).forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
            Text(
                line.letter,
                style = MaterialTheme.typography.titleMedium,
                color = if (line.on) Ed.palette.ink else Ed.palette.quiet,
                modifier = Modifier.width(LETTER_WIDTH * LocalDensity.current.fontScale.coerceAtMost(2f)),
            )
            Text(line.title, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.weight(1f))
            Text(
                line.state,
                style = MaterialTheme.typography.bodyLarge,
                color = if (line.on) Ed.palette.ink else Ed.palette.quiet,
                fontWeight = if (line.on) FontWeight.SemiBold else null,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Space.m).weight(1f),
            )
        }
        Rule()
    }
}

private val LETTER_WIDTH = 28.dp

@Composable
private fun ColumnScope.Timings(state: ProfilesState, profile: Profile, formats: Formats) {
    if (!profile.levers.constants) {
        Text(stringResource(R.string.profiles_timings_off), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        return
    }
    if (state.stock == null) {
        Text(
            stringResource(R.string.profiles_stock_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.quiet,
            modifier = Modifier.padding(bottom = Space.s),
        )
    }
    BoxWithConstraints {
        val scale = LocalDensity.current.fontScale
        val wide = maxWidth >= 360.dp * scale
        val valueWidth = VALUE_WIDTH * scale
        Column {
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                    ColumnHead(stringResource(R.string.profiles_col_setting), Modifier.weight(1f))
                    ColumnHead(stringResource(R.string.profiles_col_stock), Modifier.width(valueWidth), TextAlign.End)
                    ColumnHead(profile.name, Modifier.width(valueWidth), TextAlign.End)
                }
            }
            Rule()
            keysOf(profile).forEach { key ->
                TimingRow(key, state, profile, formats, wide)
                Rule()
            }
        }
    }
}

private val VALUE_WIDTH = 76.dp

@Composable
private fun TimingRow(key: String, state: ProfilesState, profile: Profile, formats: Formats, wide: Boolean) {
    val palette = Ed.palette
    val valueWidth = VALUE_WIDTH * LocalDensity.current.fontScale
    val stored = profile.deviceIdle[key]
    val mine = stored?.let { timingText(key, it, formats) } ?: stringResource(R.string.profiles_left_stock)
    val known = state.stock?.get(key)?.let { timingText(key, it.toString(), formats) }
    val stock = known ?: "–"
    Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(key, style = RawLine, color = palette.ink)
                Text(stringResource(keyLine(key)), style = MaterialTheme.typography.bodyMedium, color = palette.quiet)
            }
            if (wide) {
                Text(
                    stock,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.quiet,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(valueWidth),
                )
                Text(
                    mine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (stored != null) palette.ink else palette.quiet,
                    fontWeight = if (stored != null) FontWeight.SemiBold else null,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
        if (!wide) {
            Spacer(Modifier.height(Space.xs))
            val fold = stringResource(R.string.profiles_fold_value, profile.name, mine)
            Text(
                known?.let { stringResource(R.string.profiles_fold_stock, it) + "  ·  " + fold } ?: fold,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
            )
        }
        if (stored != null && key in state.missingKeys) {
            Text(
                stringResource(R.string.profiles_flag_missing),
                style = MaterialTheme.typography.labelMedium,
                color = palette.warning,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}
