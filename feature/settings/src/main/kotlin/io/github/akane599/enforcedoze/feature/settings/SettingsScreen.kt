// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.SwitchRow
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.tierLine
import io.github.akane599.enforcedoze.ui.tierName

/**
 * Settings (SPEC 9.1): privilege, hold-offs with the keep-awake apps, drift, automation, records, language, the
 * advanced flag-sync switch, the two commands that undo everything, and the credits. The commands that cannot be
 * taken back sit apart at the end, outlined (ADR 0028).
 */
@Composable
public fun SettingsScreen(state: SettingsState, actions: SettingsActions, modifier: Modifier = Modifier, status: CommandStatus? = null) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.settings_title)) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            state.notices.forEach { notice ->
                Spacer(Modifier.height(Space.s))
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
            }
            Privilege(state.privilege, actions, first = state.notices.isEmpty())
            HoldOffs(state, actions)
            SectionHeader(stringResource(R.string.settings_doze))
            SwitchRow(
                stringResource(R.string.settings_reapply),
                stringResource(R.string.settings_reapply_detail),
                checked = state.reapplyOnDrift,
                onChange = actions::reapplyOnDrift,
                enabled = state.canWrite,
            )
            val open = stringResource(R.string.settings_open)
            Line(stringResource(R.string.settings_allowlist), stringResource(R.string.settings_allowlist_detail), open, actions::allowlist)
            Line(stringResource(R.string.settings_sensors), stringResource(R.string.settings_sensors_detail), open, actions::sensors)
            Automation(state, actions, status)
            SectionHeader(stringResource(R.string.settings_records))
            Line(
                stringResource(R.string.settings_retention),
                pluralStringResource(R.plurals.settings_days, state.retentionDays, state.retentionDays),
                action = stringResource(R.string.settings_change),
                onAction = actions::retention,
            )
            Line(
                stringResource(R.string.settings_export_all),
                stringResource(R.string.settings_export_all_detail),
                action = stringResource(R.string.settings_export),
                onAction = actions::exportAll,
            )
            SectionHeader(stringResource(R.string.settings_language_look))
            Line(
                state.language ?: stringResource(R.string.settings_language_system),
                stringResource(R.string.settings_language_detail),
                action = stringResource(R.string.settings_change),
                onAction = actions::language,
            )
            SwitchRow(
                stringResource(R.string.settings_wallpaper_accent),
                stringResource(R.string.settings_wallpaper_accent_detail),
                checked = state.wallpaperAccent,
                onChange = actions::wallpaperAccent,
            )
            SectionHeader(stringResource(R.string.settings_advanced))
            SwitchRow(
                stringResource(R.string.settings_flag_sync),
                stringResource(R.string.settings_flag_sync_detail),
                checked = state.flagSyncDisabled,
                onChange = actions::flagSync,
                enabled = state.canWrite,
            )
            StatusLine(status, SettingsCommand.FLAG_SYNC)
            Undo(state, actions, status)
            About(state.version, actions)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Privilege(privilege: PrivilegeStatus, actions: SettingsActions, first: Boolean) {
    SectionHeader(
        stringResource(R.string.settings_privilege),
        first = first,
        action = stringResource(R.string.settings_check),
        onAction = actions::privilege,
    )
    val tier = tierName(privilege.tier)
    Text(
        privilege.via?.let { stringResource(R.string.settings_tier_via, tier, it) } ?: tier,
        style = MaterialTheme.typography.titleLarge,
        color = Ed.palette.ink,
    )
    Text(tierLine(privilege.tier), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
    if (privilege.missingProbes > 0) {
        Text(
            pluralStringResource(R.plurals.settings_missing_probes, privilege.missingProbes, privilege.missingProbes),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.quiet,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/** The hold-offs are fixed: they keep calls, alarms, navigation and music working (SPEC 1.2 item 4, 5.5). */
@Composable
private fun HoldOffs(state: SettingsState, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_hold_offs))
    Text(stringResource(R.string.settings_hold_offs_detail), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
    SectionHeader(
        stringResource(R.string.settings_keep_awake),
        action = stringResource(R.string.settings_add).takeIf { state.canWrite },
        onAction = actions::addKeepAwake,
    )
    if (state.keepAwake.isEmpty()) {
        Text(stringResource(R.string.settings_keep_awake_none), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        return
    }
    Rule()
    state.keepAwake.forEach { app ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
            }
            TextButton(onClick = {
                actions.removeKeepAwake(app)
            }, enabled = state.canWrite) { Text(stringResource(R.string.settings_remove)) }
        }
        Rule()
    }
}

@Composable
private fun Automation(state: SettingsState, actions: SettingsActions, status: CommandStatus?) {
    SectionHeader(stringResource(R.string.settings_automation))
    SwitchRow(
        stringResource(R.string.settings_automation_api),
        stringResource(R.string.settings_automation_detail),
        checked = state.automation,
        onChange = actions::automation,
    )
    if (!state.automation) return
    when (state.tasker) {
        TaskerState.NOT_INSTALLED -> Line(stringResource(R.string.settings_tasker), stringResource(R.string.settings_tasker_missing))

        TaskerState.NOT_ALLOWED -> Line(
            stringResource(R.string.settings_tasker),
            stringResource(R.string.settings_tasker_not_allowed),
            action = stringResource(R.string.settings_allow).takeIf { state.canWrite },
            onAction = actions::allowTasker,
        )

        TaskerState.ALLOWED -> Line(stringResource(R.string.settings_tasker), stringResource(R.string.settings_tasker_allowed))
    }
    // The outcome of "Allow" reads under the Tasker line it belongs to.
    StatusLine(status, SettingsCommand.ALLOW_TASKER)
}

/** Restore stock and Reset: apart from everything else, outlined, each behind a confirmation (ADR 0028). */
@Composable
private fun Undo(state: SettingsState, actions: SettingsActions, status: CommandStatus?) {
    Spacer(Modifier.height(Space.xxl))
    Rule()
    Spacer(Modifier.height(Space.l))
    Text(stringResource(R.string.settings_undo_detail), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
    Spacer(Modifier.height(Space.m))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedButton(onClick = actions::restoreStock, enabled = state.canWrite) { Text(stringResource(R.string.settings_restore_stock)) }
        OutlinedButton(onClick = actions::reset) { Text(stringResource(R.string.settings_reset), color = Ed.palette.critical) }
    }
    StatusLine(status, SettingsCommand.RESTORE_STOCK)
    StatusLine(status, SettingsCommand.RESET)
}

@Composable
private fun About(version: String, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_about))
    Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.titleMedium, color = Ed.palette.ink)
    Spacer(Modifier.height(Space.s))
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(stringResource(R.string.settings_licence), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        Text(stringResource(R.string.settings_credit_enforcedoze), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        Text(stringResource(R.string.settings_credit_forcedoze), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        Text(stringResource(R.string.settings_credit_translators), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = actions::licence) { Text(stringResource(R.string.settings_read_licence)) }
    }
}

/** A setting's value with a quiet line under it and an optional action at its end. */
@Composable
private fun Line(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

/** What came of [command], when it is the last one run: a quiet line while it runs, the reason or raw line after. */
@Composable
private fun StatusLine(status: CommandStatus?, command: SettingsCommand) {
    if (status?.command != command) return
    when (val outcome = status.outcome) {
        null -> Text(stringResource(R.string.settings_running), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)

        ActionOutcome.Done -> Text(
            stringResource(DONE_LINES.getValue(command)),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.quiet,
        )

        is ActionOutcome.Unavailable -> {
            Spacer(Modifier.height(Space.s))
            Notice(stringResource(R.string.settings_failed), outcome.reason, tone = Tone.WARNING)
        }

        is ActionOutcome.Failed -> {
            Spacer(Modifier.height(Space.s))
            Notice(stringResource(R.string.settings_failed), outcome.raw, tone = Tone.CRITICAL, raw = true)
        }
    }
}

private val DONE_LINES = mapOf(
    SettingsCommand.RESTORE_STOCK to R.string.settings_done_restore,
    SettingsCommand.ALLOW_TASKER to R.string.settings_done_tasker,
    SettingsCommand.FLAG_SYNC to R.string.settings_done_flag_sync,
    SettingsCommand.RESET to R.string.settings_done_reset,
)
