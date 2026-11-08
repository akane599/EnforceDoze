// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.rememberFormats

/**
 * The session or night report (SPEC 8.4, 8.5): the verdict and strip on Summary, the night as a timetable,
 * then wakers, sensors, what changed Doze, and the raw records the verdict was judged on.
 */
@Composable
public fun ReportScreen(
    state: ReportState,
    actions: ReportActions,
    modifier: Modifier = Modifier,
    initialTab: ReportTab = ReportTab.SUMMARY,
) {
    val formats = rememberFormats()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    val summary = state.summary
    val title = if (summary.night) {
        stringResource(R.string.report_title_night, formats.date(summary.start))
    } else {
        stringResource(R.string.report_title_session, formats.date(summary.start), formats.clock(summary.start))
    }
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = {
            EdTopBar(title, onBack = actions::back) {
                IconButton(onClick = actions::export) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.report_export))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = tab,
                    modifier = Modifier.widthIn(max = Space.column + Space.gutter * 2),
                    containerColor = Ed.palette.ground,
                    contentColor = Ed.palette.ink,
                    edgePadding = Space.gutter,
                    divider = {},
                ) {
                    ReportTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry.ordinal,
                            onClick = { tab = entry.ordinal },
                            text = { Text(stringResource(entry.title), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                            unselectedContentColor = Ed.palette.quiet,
                        )
                    }
                }
            }
            Rule()
            TabBody(Modifier.weight(1f)) {
                when (ReportTab.entries[tab]) {
                    ReportTab.SUMMARY -> SummaryTab(state, formats, actions, showWakers = { tab = ReportTab.WAKERS.ordinal })
                    ReportTab.TIMELINE -> TimelineTab(state, formats)
                    ReportTab.WAKERS -> WakersTab(state, formats)
                    ReportTab.SENSORS -> SensorsTab(state)
                    ReportTab.ACTIONS -> ActionsTab(state, formats)
                    ReportTab.RAW -> RawTab(state)
                }
            }
        }
    }
}

@Composable
private fun TabBody(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    ScreenColumn(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(Space.l))
        content()
        Spacer(Modifier.height(Space.xxl))
    }
}

/** A dash for a number the records do not hold. */
@Composable
internal fun unknown(): String = stringResource(io.github.akane599.enforcedoze.ui.R.string.ui_unknown)

internal fun Formats.lateText(millis: Long): String = "+" + duration(millis)
