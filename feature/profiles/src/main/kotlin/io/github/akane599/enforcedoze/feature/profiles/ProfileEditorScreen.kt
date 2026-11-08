// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import io.github.akane599.enforcedoze.profile.SensorLever
import io.github.akane599.enforcedoze.ui.title

/** The editor's state: the draft, the profile as saved, and what the store needs to judge it. */
public data class EditorState(
    val draft: Draft,
    val saved: Draft,
    /** `stock.json` in milliseconds, or null before onboarding read it. */
    val stock: Map<String, Long>?,
    /** A slot of the plan runs this profile, so it cannot be deleted. */
    val inUse: Boolean,
    val busy: Boolean = false,
    /** Why the last save or delete failed, as the store reported it. */
    val error: String? = null,
    /** The guardrails the store refused the last save with, if it judged differently from the draft. */
    val refused: List<GuardrailProblem> = emptyList(),
) {
    val dirty: Boolean get() = draft != saved
}

/** What the editor does on the owner's taps. */
public interface ProfileEditorActions {
    public fun back()

    public fun edit(draft: Draft)

    public fun save()

    public fun delete()
}

/**
 * A custom profile's editor (SPEC 6.3): its name, the four levers, and each timing in the unit it reads best
 * in. A blank timing is left to the phone. Every value is checked as it is typed and refused with the reason.
 */
@Composable
public fun ProfileEditorScreen(state: EditorState, actions: ProfileEditorActions, modifier: Modifier = Modifier) {
    val formats = rememberFormats()
    val draft = state.draft
    val problems = remember(draft, state.stock, state.refused) {
        val live = draft.problems(state.stock)
        live + state.refused.map { DraftProblem.Guardrail(it) }.filterNot { it in live }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = {
            EdTopBar(stringResource(R.string.profiles_edit_title), onBack = actions::back) {
                TextButton(onClick = actions::save, enabled = state.dirty && problems.isEmpty() && !state.busy) {
                    Text(stringResource(R.string.profiles_save))
                }
            }
        },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.l))
            state.error?.let {
                Notice(stringResource(R.string.profiles_failed), it, tone = Tone.CRITICAL, raw = true)
                Spacer(Modifier.height(Space.l))
            }
            val nameProblem = problems.firstOrNull { it == DraftProblem.NameEmpty }
            OutlinedTextField(
                draft.name,
                onValueChange = { actions.edit(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.profiles_name)) },
                singleLine = true,
                isError = nameProblem != null,
                supportingText = nameProblem?.let { { Text(problemText(it, formats)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.profiles_levers))
            LeverEditors(draft, problems, formats, actions)
            SectionHeader(stringResource(R.string.profiles_timings))
            if (!draft.constants) {
                Text(
                    stringResource(R.string.profiles_timings_off_editor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ed.palette.quiet,
                )
            }
            draft.values.keys.forEach { key ->
                TimingField(key, state, problems.firstOrNull { it.key == key }, formats, actions)
            }
            Spacer(Modifier.height(Space.xl))
            TextButton(onClick = actions::delete, enabled = !state.inUse && !state.busy) {
                Text(stringResource(R.string.profiles_delete), color = if (state.inUse) Ed.palette.quiet else Ed.palette.critical)
            }
            if (state.inUse) {
                Text(stringResource(R.string.profiles_delete_in_use), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun ColumnScope.LeverEditors(draft: Draft, problems: List<DraftProblem>, formats: Formats, actions: ProfileEditorActions) {
    SwitchRow(Lever.CONSTANTS.letter, Lever.CONSTANTS.title(), stringResource(R.string.profiles_lever_a_detail), draft.constants) {
        actions.edit(draft.copy(constants = it))
    }
    SwitchRow(Lever.FORCE_IDLE.letter, Lever.FORCE_IDLE.title(), stringResource(R.string.profiles_lever_b_detail), draft.forceIdle) {
        actions.edit(draft.copy(forceIdle = it))
    }
    if (draft.forceIdle) {
        Column(Modifier.padding(start = INDENT, top = Space.xs)) {
            val delayProblem = problems.firstOrNull { it == DraftProblem.DelayNotANumber }
            OutlinedTextField(
                draft.delaySec,
                onValueChange = { actions.edit(draft.copy(delaySec = it)) },
                label = { Text(stringResource(R.string.profiles_force_delay)) },
                suffix = { Text(stringResource(R.string.profiles_unit_s)) },
                singleLine = true,
                isError = delayProblem != null,
                supportingText = {
                    Text(delayProblem?.let { problemText(it, formats) } ?: stringResource(R.string.profiles_force_delay_help))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                null,
                stringResource(R.string.profiles_hold_until_unlock),
                stringResource(R.string.profiles_hold_until_unlock_detail),
                draft.untilUnlock,
            ) {
                actions.edit(draft.copy(untilUnlock = it))
            }
        }
    }
    Choices(Lever.SENSORS, SensorLever.entries, draft.sensors, { it.label }) { actions.edit(draft.copy(sensors = it)) }
    if (draft.sensors == SensorLever.ALL_SENSORS_OFF) {
        Text(
            stringResource(R.string.profiles_all_off_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.warning,
            modifier = Modifier.padding(start = INDENT, bottom = Space.s),
        )
    }
    Choices(Lever.ALLOWLIST, AllowlistLever.entries, draft.allowlist, {
        it.label
    }) { actions.edit(draft.copy(allowlist = it)) }
}

private val INDENT = Sizes.touch - Space.s

@Composable
private fun SwitchRow(letter: String?, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        letter?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink, modifier = Modifier.width(INDENT)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }
        Spacer(Modifier.width(Space.m))
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun <T> Choices(lever: Lever, options: List<T>, chosen: T, label: (T) -> Int, onChoose: (T) -> Unit) {
    val letter = lever.letter
    val title = lever.title()
    Row(Modifier.fillMaxWidth().padding(top = Space.m), verticalAlignment = Alignment.Top) {
        Text(letter, style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink, modifier = Modifier.width(INDENT))
        Column(Modifier.weight(1f).selectableGroup()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.touch)
                        .selectable(option == chosen, role = Role.RadioButton, onClick = { onChoose(option) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == chosen, onClick = null)
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(label(option)), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                }
            }
        }
    }
}

@Composable
private fun TimingField(key: String, state: EditorState, problem: DraftProblem?, formats: Formats, actions: ProfileEditorActions) {
    val draft = state.draft
    val unit = unitOf(key)
    val stockText = state.stock?.get(key)?.let { stringResource(R.string.profiles_stock_value, timingText(key, it.toString(), formats)) }
        ?: stringResource(R.string.profiles_stock_hint)
    Column(Modifier.fillMaxWidth().padding(top = Space.l)) {
        Text(key, style = RawLine, color = if (draft.constants) Ed.palette.ink else Ed.palette.quiet)
        Text(stringResource(keyLine(key)), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        Spacer(Modifier.height(Space.xs))
        OutlinedTextField(
            draft.values[key].orEmpty(),
            onValueChange = { actions.edit(draft.copy(values = draft.values + (key to it))) },
            placeholder = { Text(stockText) },
            suffix = unit.suffix()?.let { { Text(stringResource(it)) } },
            singleLine = true,
            enabled = draft.constants,
            isError = problem != null,
            supportingText = problem?.let { { Text(problemText(it, formats)) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (unit == TimingUnit.RAW) KeyboardType.Text else KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun TimingUnit.suffix(): Int? = when (this) {
    TimingUnit.SECONDS -> R.string.profiles_unit_s
    TimingUnit.MINUTES -> R.string.profiles_unit_min
    TimingUnit.HOURS -> R.string.profiles_unit_h
    TimingUnit.FACTOR -> R.string.profiles_unit_factor
    TimingUnit.RAW -> null
}
