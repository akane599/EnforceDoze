// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.akane599.enforcedoze.doze.KeyDifference
import io.github.akane599.enforcedoze.doze.SwitchCheck
import io.github.akane599.enforcedoze.parse.SettingValue
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.ui.ActionOutcome

@Composable
public fun SwitchOverRoute(viewModel: SwitchOverViewModel, navigation: ProfilesNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val name = stringResource(R.string.switch_imported_name)
    val actions = object : SwitchOverActions {
        override fun import() = viewModel.import(name)
        override fun startCheck() = viewModel.startCheck()
        override fun returnToPrevious() = run { confirming = true }
        override fun appInfo(packageName: String) = viewModel.appInfo(packageName)
        override fun openProfile(profileId: String) = navigation.detail(profileId)
        override val back: () -> Unit = navigation::back
    }
    SwitchOverScreen(current, actions, modifier, status)
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Ed.palette.raised,
            title = { Text(stringResource(R.string.switch_return_title)) },
            text = { Text(stringResource(R.string.switch_return_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.returnToPrevious()
                }) { Text(stringResource(R.string.switch_return)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.switch_cancel)) } },
        )
    }
}

/**
 * The switch-over from another setup (SPEC 11.4): what was set before EnforceDoze, kept as a profile and compared,
 * the check that the other setup is off, and the way back. Sections in the order the owner does them.
 */
@Composable
public fun SwitchOverScreen(
    state: SwitchOverState,
    actions: SwitchOverActions,
    modifier: Modifier = Modifier,
    status: SwitchStatus? = null,
) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.switch_title), onBack = actions.back) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.switch_intro), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            state.managers.forEach { manager ->
                Spacer(Modifier.height(Space.m))
                Notice(
                    stringResource(R.string.switch_manager_title, manager.label),
                    stringResource(R.string.switch_manager_body),
                    action = stringResource(R.string.switch_app_info),
                    onAction = { actions.appInfo(manager.packageName) },
                )
            }
            Found(state, formats)
            Import(state, actions, formats, status)
            Check(state, actions, formats)
            WayBack(state, actions, status)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Found(state: SwitchOverState, formats: Formats) {
    val found = state.found
    SectionHeader(
        stringResource(R.string.switch_found),
        detail = found?.capturedAt?.toEpochMilli()?.let { stringResource(R.string.switch_at, formats.date(it), formats.clock(it)) },
    )
    when {
        found == null -> Quiet(stringResource(R.string.switch_found_unread))

        !found.changed -> Quiet(stringResource(R.string.switch_found_stock))

        else -> {
            state.writer?.let { Quiet(stringResource(R.string.switch_found_writer, it)) }
            Spacer(Modifier.height(Space.s))
            Rule()
            found.differences.sortedBy { inWalkOrder(it.key) }.forEach { difference ->
                DifferenceRow(difference, formats)
                Rule()
            }
            if (found.sensorsRestricted) Flag(stringResource(R.string.switch_found_sensors))
            if (found.forceIdle) Flag(stringResource(R.string.switch_found_force))
        }
    }
}

@Composable
private fun DifferenceRow(difference: KeyDifference, formats: Formats) {
    val found = readable(difference.key, difference.found, formats)
    val stock = difference.stock?.let { readable(difference.key, it, formats) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
        Text(difference.key, style = RawLine, color = Ed.palette.ink)
        Text(stringResource(keyLine(difference.key)), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        Spacer(Modifier.height(Space.xs))
        Text(
            stock?.let { stringResource(R.string.switch_found_value_stock, found, it) }
                ?: stringResource(R.string.switch_found_value, found),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.ink,
        )
    }
}

/** A value as `dumpsys deviceidle` printed it, read the way the profile screens show timings. */
@Composable
private fun readable(key: String, raw: String, formats: Formats): String =
    SettingValue(raw).durationMillis?.let { timingText(key, it.toString(), formats) } ?: raw

@Composable
private fun Import(state: SwitchOverState, actions: SwitchOverActions, formats: Formats, status: SwitchStatus?) {
    val found = state.found ?: return
    if (!found.changed) return
    SectionHeader(
        stringResource(R.string.switch_import),
        action = state.imported?.let { stringResource(R.string.switch_open_profile) },
        onAction = { state.imported?.let { actions.openProfile(it.id) } },
    )
    val imported = state.imported
    if (imported == null) {
        Text(stringResource(R.string.switch_import_body), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        Spacer(Modifier.height(Space.m))
        FilledTonalButton(onClick = actions::import, enabled = status?.let { it.outcome != null } ?: true) {
            Text(stringResource(R.string.switch_import_action))
        }
    } else {
        Quiet(stringResource(R.string.switch_compare_body, imported.name))
        Spacer(Modifier.height(Space.s))
        Comparison(listOf(imported) + state.compared, formats)
    }
    Outcome(status, SwitchCommand.IMPORT)
}

/** Every key any of [profiles] sets, one ruled row each; a value left to stock reads "stock". */
@Composable
private fun Comparison(profiles: List<Profile>, formats: Formats) {
    val keys = profiles.flatMap { it.constants.keys }.distinct().sortedBy(::inWalkOrder)
    BoxWithConstraints {
        val scale = LocalDensity.current.fontScale
        val wide = maxWidth >= 360.dp * scale
        val valueWidth = VALUE_WIDTH * scale
        Column {
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                    ColumnHead(stringResource(R.string.profiles_col_setting), Modifier.weight(1f))
                    profiles.forEach { ColumnHead(it.name, Modifier.width(valueWidth), TextAlign.End) }
                }
            }
            Rule()
            keys.forEach { key ->
                CompareRow(key, profiles, formats, wide, valueWidth)
                Rule()
            }
        }
    }
}

@Composable
private fun CompareRow(key: String, profiles: List<Profile>, formats: Formats, wide: Boolean, valueWidth: Dp) {
    val stock = stringResource(R.string.profiles_left_stock)
    val values = profiles.map { profile -> profile.constants[key]?.let { timingText(key, it, formats) } }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.Top) {
            Text(key, style = RawLine, color = Ed.palette.ink, modifier = Modifier.weight(1f))
            if (wide) {
                values.forEachIndexed { i, value ->
                    Text(
                        value ?: stock,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value != null) Ed.palette.ink else Ed.palette.quiet,
                        fontWeight = if (i == 0 && value != null) FontWeight.SemiBold else null,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(valueWidth),
                    )
                }
            }
        }
        if (!wide) {
            Spacer(Modifier.height(Space.xs))
            Text(
                profiles.zip(values).joinToString("  ·  ") { (profile, value) -> "${profile.name} ${value ?: stock}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.ink,
            )
        }
    }
}

private val VALUE_WIDTH = 76.dp

/** The profile screens' order: the walk's timings first, in the order the phone passes them, then any other key. */
private fun inWalkOrder(key: String): Int = Timings.indexOfFirst { it.first == key }.let { if (it < 0) Int.MAX_VALUE else it }

@Composable
private fun Check(state: SwitchOverState, actions: SwitchOverActions, formats: Formats) {
    SectionHeader(stringResource(R.string.switch_check))
    Text(stringResource(R.string.switch_check_body), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
    Spacer(Modifier.height(Space.m))
    if (!state.watching && state.check !is SwitchCheck.Complete) {
        Quiet(stringResource(R.string.switch_check_not_watching))
        return
    }
    when (val check = state.check) {
        SwitchCheck.NotStarted -> FilledTonalButton(onClick = actions::startCheck, enabled = state.canWrite) {
            Text(stringResource(R.string.switch_check_start))
        }

        is SwitchCheck.Waiting -> Quiet(stringResource(R.string.switch_check_waiting, formats.clock(check.since.toEpochMilli())))

        is SwitchCheck.ForeignWrite -> {
            Notice(
                stringResource(
                    R.string.switch_check_foreign,
                    check.writer ?: stringResource(R.string.switch_check_someone),
                    formats.clock(check.at.toEpochMilli()),
                ),
                stringResource(R.string.switch_check_foreign_body),
                tone = Tone.WARNING,
                action = stringResource(R.string.switch_check_again),
                onAction = actions::startCheck,
            )
        }

        is SwitchCheck.Complete -> Column(Modifier.semantics(mergeDescendants = true) { }) {
            Text(stringResource(R.string.switch_check_complete), style = MaterialTheme.typography.titleLarge, color = Ed.palette.good)
            Text(
                stringResource(R.string.switch_check_complete_body, formats.clock(check.at.toEpochMilli())),
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.quiet,
            )
        }
    }
}

/** "Return to my previous setup": apart from the rest, outlined, behind a confirmation (as Settings' undo, ADR 0028). */
@Composable
private fun WayBack(state: SwitchOverState, actions: SwitchOverActions, status: SwitchStatus?) {
    val found = state.found ?: return
    Spacer(Modifier.height(Space.xxl))
    Rule()
    Spacer(Modifier.height(Space.l))
    Text(
        stringResource(if (state.handsOff) R.string.switch_return_done else R.string.switch_return_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = Ed.palette.quiet,
    )
    if (!state.handsOff && found.changed) {
        Spacer(Modifier.height(Space.m))
        OutlinedButton(onClick = actions::returnToPrevious, enabled = state.canWrite) { Text(stringResource(R.string.switch_return)) }
    }
    Outcome(status, SwitchCommand.RETURN)
}

@Composable
private fun Outcome(status: SwitchStatus?, command: SwitchCommand) {
    if (status?.command != command) return
    val text = when (val outcome = status.outcome) {
        null -> stringResource(R.string.switch_working)
        ActionOutcome.Done, is ActionOutcome.Applied -> return
        is ActionOutcome.Unavailable -> outcome.reason
        is ActionOutcome.Failed -> outcome.raw
    }
    Spacer(Modifier.height(Space.s))
    Text(
        text,
        style = if (status.outcome is ActionOutcome.Failed) RawLine else MaterialTheme.typography.bodyMedium,
        color = if (status.outcome is ActionOutcome.Failed) Ed.palette.critical else Ed.palette.quiet,
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
}

@Composable
private fun Flag(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.padding(vertical = Space.m))
    Rule()
}
