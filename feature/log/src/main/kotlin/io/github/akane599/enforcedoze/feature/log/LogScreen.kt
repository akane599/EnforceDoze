// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.TimelineStrip
import io.github.akane599.enforcedoze.design.color
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard
import io.github.akane599.enforcedoze.ui.VerdictWord
import io.github.akane599.enforcedoze.ui.tone
import io.github.akane599.enforcedoze.ui.word
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Log (SPEC 9.1): every session and night, newest first, under the day it began, with its verdict and drain;
 * filters by verdict, charger and profile; an export of a date range from the top bar.
 */
@Composable
public fun LogScreen(state: LogState, filter: LogFilter, actions: LogActions, modifier: Modifier = Modifier) {
    val formats = rememberFormats()
    val shown = state.entries.filter(filter::accepts)
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = {
            EdTopBar(stringResource(R.string.log_title)) {
                if (state.entries.isNotEmpty()) {
                    IconButton(onClick = actions::export) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.log_export))
                    }
                }
            }
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                state.notices.forEach { notice ->
                    Spacer(Modifier.height(Space.s))
                    StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
                }
                Empty(R.string.log_empty_title, R.string.log_empty)
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Space.column).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.s),
            ) {
                items(state.notices, key = { "notice-${it.id}" }) { notice ->
                    StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
                    Spacer(Modifier.height(Space.m))
                }
                item { Filters(state, filter, actions::filter) }
                if (shown.isEmpty()) item { NoMatch(actions) } else days(shown, state, formats, actions)
                item {
                    Text(
                        pluralStringResource(R.plurals.log_retention, state.retentionDays, state.retentionDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ed.palette.quiet,
                        modifier = Modifier.padding(top = Space.l, bottom = Space.xxl),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.days(entries: List<LogEntry>, state: LogState, formats: Formats, actions: LogActions) {
    val zone = ZoneId.systemDefault()
    entries.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.forEach { (day, rows) ->
        item(key = "day-$day") { DayHead(day, zone, formats) }
        items(rows, key = { it.sessionId }) { entry ->
            LogLine(entry, state, formats, onClick = { actions.open(entry) })
            Rule()
        }
    }
}

@Composable
private fun DayHead(day: LocalDate, zone: ZoneId, formats: Formats) {
    Column(Modifier.padding(top = Space.l)) {
        Text(
            formats.date(day.atStartOfDay(zone).toInstant().toEpochMilli(), DAY_SKELETON),
            style = MaterialTheme.typography.titleMedium,
            color = Ed.palette.ink,
            modifier = Modifier.padding(bottom = Space.xs).semantics { heading() },
        )
        Rule()
    }
}

private const val DAY_SKELETON = "EEEEdMMMM"

/** One session or night: its hours and verdict, then its length, kind and drain in quiet ink. */
@Composable
private fun LogLine(entry: LogEntry, state: LogState, formats: Formats, onClick: () -> Unit) {
    val details = listOfNotNull(
        formats.duration(entry.end - entry.start),
        state.name(entry.profileId) ?: stringResource(R.string.log_mixed_profiles),
        if (entry.night) pluralStringResource(R.plurals.log_night_report, entry.sessions, entry.sessions) else null,
        stringResource(R.string.log_plugged).takeIf { entry.plugged },
    ).joinToString(SEPARATOR)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .clickable(onClickLabel = stringResource(R.string.log_open), role = Role.Button, onClick = onClick)
            .padding(vertical = Space.m)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.log_hours, formats.clock(entry.start), formats.clock(entry.end)),
                style = MaterialTheme.typography.titleMedium,
                color = Ed.palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                entry.verdict.word(),
                style = MaterialTheme.typography.titleMedium,
                color = entry.verdict.tone.color(),
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Space.m),
            )
        }
        // The shape of the session at a glance (ADR 0028); the words above and below already say it, so it is not spoken.
        entry.strip?.takeIf { it.segments.isNotEmpty() }?.let { strip ->
            TimelineStrip(
                strip,
                description = "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs)
                    .height(Sizes.stripRow + Space.xs)
                    .clearAndSetSemantics { },
                animate = false,
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(details, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet, modifier = Modifier.weight(1f))
            Text(
                entry.drainPerHour?.let(formats::drainText) ?: DASH,
                style = MaterialTheme.typography.bodyMedium,
                color = Ed.palette.quiet,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Space.m),
            )
        }
    }
}

private const val SEPARATOR = " · "
private const val DASH = "–"

@Composable
private fun Filters(state: LogState, filter: LogFilter, onFilter: (LogFilter) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        VerdictWord.entries.forEach { verdict ->
            val on = verdict in filter.verdicts
            FilterChip(
                selected = on,
                onClick = { onFilter(filter.copy(verdicts = if (on) filter.verdicts - verdict else filter.verdicts + verdict)) },
                label = { Text(verdict.word()) },
            )
        }
        listOf(false to R.string.log_unplugged, true to R.string.log_plugged).forEach { (plugged, label) ->
            FilterChip(
                selected = filter.plugged == plugged,
                onClick = { onFilter(filter.copy(plugged = if (filter.plugged == plugged) null else plugged)) },
                label = { Text(stringResource(label)) },
            )
        }
        ProfileChip(state, filter, onFilter)
    }
}

@Composable
private fun ProfileChip(state: LogState, filter: LogFilter, onFilter: (LogFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ids = state.entries.mapNotNull { it.profileId }.distinct()
    Box {
        FilterChip(
            selected = filter.profileId != null,
            onClick = { open = true },
            label = { Text(state.name(filter.profileId) ?: stringResource(R.string.log_any_profile)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Ed.palette.raised) {
            DropdownMenuItem(text = { Text(stringResource(R.string.log_any_profile)) }, onClick = {
                open = false
                onFilter(filter.copy(profileId = null))
            })
            ids.forEach { id ->
                DropdownMenuItem(text = { Text(state.name(id) ?: id) }, onClick = {
                    open = false
                    onFilter(filter.copy(profileId = id))
                })
            }
        }
    }
}

@Composable
private fun NoMatch(actions: LogActions) {
    Column(Modifier.padding(top = Space.l)) {
        Text(stringResource(R.string.log_no_match), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        TextButton(onClick = { actions.filter(LogFilter()) }) { Text(stringResource(R.string.log_clear_filters)) }
    }
}

@Composable
private fun Empty(title: Int, body: Int) {
    Column(Modifier.padding(top = Space.l)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = Ed.palette.ink)
        Spacer(Modifier.height(Space.s))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
    }
}
