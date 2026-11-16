// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.StateNotice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
public fun LogRoute(viewModel: LogViewModel, navigation: LogNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var exporting by rememberSaveable { mutableStateOf(false) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val actions = object : LogActions {
        override fun open(entry: LogEntry) = navigation.open(entry.sessionId)
        override fun filter(filter: LogFilter) = viewModel.filter(filter)
        override fun export() = run { exporting = true }
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    LogScreen(current, filter, actions, modifier)
    if (exporting && current.entries.isNotEmpty()) {
        val zone = ZoneId.systemDefault()
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        ExportRangeDialog(
            first = day(current.entries.minOf { it.start }),
            last = day(current.entries.maxOf { it.end }),
            onExport = { from, to ->
                exporting = false
                navigation.export(from, to)
            },
            onDismiss = { exporting = false },
        )
    }
}
