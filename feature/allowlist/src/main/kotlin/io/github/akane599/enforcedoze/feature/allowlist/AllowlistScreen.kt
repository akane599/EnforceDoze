// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.allowlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard

/**
 * Allowlist (SPEC 9.1): what the phone's Doze allowlist holds, read from `deviceidle whitelist`. Suggestions first,
 * then the owner's entries with their "keep" marks, then the system's own entries, which only the system changes.
 */
@Composable
public fun AllowlistScreen(state: AllowlistState, actions: AllowlistActions, modifier: Modifier = Modifier, status: ChangeStatus? = null) {
    val formats = rememberFormats()
    var showSystem by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = {
            EdTopBar(stringResource(R.string.allowlist_title)) {
                if (state.canWrite) {
                    IconButton(onClick = actions::add) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.allowlist_add))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = Space.column).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.gutter)) {
                items(state.notices, key = { "notice-${it.id}" }) { notice ->
                    Spacer(Modifier.height(Space.s))
                    StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
                }
                item {
                    Text(
                        stringResource(R.string.allowlist_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ed.palette.ink,
                        modifier = Modifier.padding(top = Space.m),
                    )
                    StatusLine(status)
                }
                suggestions(state, formats, actions)
                yours(state, actions)
                item {
                    SectionHeader(
                        stringResource(R.string.allowlist_system, state.system.size),
                        action = stringResource(if (showSystem) R.string.allowlist_hide else R.string.allowlist_show),
                        onAction = { showSystem = !showSystem },
                    )
                    Text(
                        stringResource(R.string.allowlist_system_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ed.palette.quiet,
                    )
                    if (showSystem) Rule()
                }
                if (showSystem) {
                    items(state.system, key = { "system-${it.packageName}" }) { entry ->
                        AppLine(
                            entry.label,
                            if (entry.kind ==
                                EntryKind.SYSTEM_EXCEPT_IDLE
                            ) {
                                stringResource(R.string.allowlist_except_idle, entry.packageName)
                            } else {
                                entry.packageName
                            },
                        )
                        Rule()
                    }
                }
                item { Spacer(Modifier.height(Space.xxl)) }
            }
        }
    }
}

private fun LazyListScope.suggestions(state: AllowlistState, formats: Formats, actions: AllowlistActions) {
    if (state.suggestions.isEmpty()) return
    item {
        SectionHeader(stringResource(R.string.allowlist_suggestions))
        Rule()
    }
    items(state.suggestions, key = { "suggestion-${it.packageName}" }) { suggestion ->
        val why = when (val reason = suggestion.reason) {
            is SuggestionReason.Late -> stringResource(R.string.allowlist_why_late, formats.date(reason.time), formats.clock(reason.time))
            SuggestionReason.Alarm -> stringResource(R.string.allowlist_why_alarm)
            SuggestionReason.Messaging -> stringResource(R.string.allowlist_why_messaging)
        }
        AppLine(suggestion.label, why) {
            TextButton(onClick = {
                actions.accept(suggestion)
            }, enabled = state.canWrite) { Text(stringResource(R.string.allowlist_add_short)) }
        }
        Rule()
    }
}

private fun LazyListScope.yours(state: AllowlistState, actions: AllowlistActions) {
    item {
        SectionHeader(stringResource(R.string.allowlist_yours))
        if (state.user.isEmpty()) {
            Text(stringResource(R.string.allowlist_yours_none), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        } else {
            Text(
                stringResource(R.string.allowlist_keep_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.quiet,
                modifier = Modifier.padding(bottom = Space.s),
            )
            Rule()
        }
    }
    items(state.user, key = { "user-${it.packageName}" }) { entry ->
        AppLine(entry.label, entry.packageName) {
            val kept = stringResource(if (entry.keep) R.string.allowlist_kept else R.string.allowlist_not_kept)
            FilterChip(
                selected = entry.keep,
                onClick = { actions.keep(entry, !entry.keep) },
                label = { Text(stringResource(R.string.allowlist_keep)) },
                // Kept shows as a check on a raised chip, not in the accent (DESIGN.md One Signal).
                leadingIcon = if (entry.keep) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ed.palette.raisedHigh,
                    selectedLabelColor = Ed.palette.ink,
                    selectedLeadingIconColor = Ed.palette.ink,
                ),
                modifier = Modifier.semantics { stateDescription = kept },
            )
            TextButton(onClick = { actions.remove(entry) }, enabled = state.canWrite) { Text(stringResource(R.string.allowlist_remove)) }
        }
        Rule()
    }
}

@Composable
private fun AppLine(label: String, detail: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun StatusLine(status: ChangeStatus?) {
    status ?: return
    val title = stringResource(if (status.adding) R.string.allowlist_add_failed else R.string.allowlist_remove_failed, status.label)
    when (val outcome = status.outcome) {
        null -> Text(
            stringResource(if (status.adding) R.string.allowlist_adding else R.string.allowlist_removing, status.label),
            style = MaterialTheme.typography.bodyMedium,
            color = Ed.palette.quiet,
            modifier = Modifier.padding(top = Space.s),
        )

        ActionOutcome.Done -> Unit

        is ActionOutcome.Unavailable -> {
            Spacer(Modifier.height(Space.m))
            Notice(title, outcome.reason, tone = Tone.WARNING)
        }

        is ActionOutcome.Failed -> {
            Spacer(Modifier.height(Space.m))
            Notice(title, outcome.raw, tone = Tone.CRITICAL, raw = true)
        }
    }
}
