// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Formats
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.ScreenColumn
import io.github.akane599.enforcedoze.design.SectionHeader
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.SwitchRow
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.rememberFormats
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.StateNoticeCard

/**
 * Sensors (SPEC 9.1): the sensor mode now and sensor privacy, the all-sensors-off switch, what restriction costs,
 * and who held sensors at the last screen off.
 */
@Composable
public fun SensorsScreen(state: SensorsState, actions: SensorsActions, modifier: Modifier = Modifier, status: SwitchStatus? = null) {
    val formats = rememberFormats()
    Scaffold(
        modifier = modifier,
        containerColor = Ed.palette.ground,
        topBar = { EdTopBar(stringResource(R.string.sensors_title)) },
    ) { padding ->
        ScreenColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            state.notices.forEach { notice ->
                Spacer(Modifier.height(Space.s))
                StateNoticeCard(notice, formats, onAction = { actions.notice(notice) }, onSeen = { actions.seen(notice) })
            }
            SectionHeader(stringResource(R.string.sensors_now), first = state.notices.isEmpty())
            Mode(state.mode)
            SwitchRow(
                stringResource(R.string.sensors_all_off),
                stringResource(
                    when (state.allSensorsOff) {
                        true -> R.string.sensors_all_off_on
                        false -> R.string.sensors_all_off_detail
                        null -> R.string.sensors_all_off_unknown
                    },
                ),
                checked = state.allSensorsOff == true,
                onChange = actions::allSensorsOff,
                enabled = state.canWrite && state.allSensorsOff != null && status?.let { it.outcome != null } != false,
                modifier = Modifier.padding(top = Space.m),
            )
            StatusLine(status)
            SectionHeader(stringResource(R.string.sensors_side_effects))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(R.string.sensors_effect_steps, R.string.sensors_effect_wake, R.string.sensors_effect_calls).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                }
            }
            Snapshot(state.snapshot, formats, actions)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Mode(mode: SensorModeNow) {
    val (word, detail) = when (mode) {
        SensorModeNow.Normal -> stringResource(R.string.sensors_mode_normal) to stringResource(R.string.sensors_mode_normal_detail)

        is SensorModeNow.Restricted -> stringResource(R.string.sensors_mode_restricted) to
            (
                mode.allowed?.let { stringResource(R.string.sensors_mode_restricted_detail, it) }
                    ?: stringResource(R.string.sensors_mode_restricted_none)
                )

        is SensorModeNow.Other -> mode.raw to stringResource(R.string.sensors_mode_other_detail)

        SensorModeNow.Unknown -> stringResource(R.string.sensors_mode_unknown) to stringResource(R.string.sensors_mode_unknown_detail)
    }
    Column(Modifier.semantics(mergeDescendants = true) { }) {
        Text(word, style = MaterialTheme.typography.headlineMedium, color = Ed.palette.ink)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
    }
}

@Composable
private fun Snapshot(snapshot: SensorSnapshot?, formats: Formats, actions: SensorsActions) {
    SectionHeader(
        stringResource(R.string.sensors_last_screen_off),
        action = snapshot?.let { stringResource(R.string.sensors_open) },
        onAction = { snapshot?.let { actions.openSnapshot(it.sessionId) } },
    )
    if (snapshot == null) {
        Text(stringResource(R.string.sensors_no_snapshot), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        return
    }
    Text(
        stringResource(R.string.sensors_taken, formats.date(snapshot.time), formats.clock(snapshot.time)),
        style = MaterialTheme.typography.bodyMedium,
        color = Ed.palette.quiet,
        modifier = Modifier.padding(bottom = Space.s),
    )
    if (snapshot.holders.isEmpty()) {
        Text(stringResource(R.string.sensors_no_holders), style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
        return
    }
    Rule()
    snapshot.holders.forEach { holder ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(holder.label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink)
                Text(holder.sensor, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
            }
            Text(
                holder.rateHz?.let { stringResource(R.string.sensors_rate, formats.decimal(it, if (it < WHOLE_HZ_FROM) 1 else 0)) } ?: DASH,
                style = MaterialTheme.typography.bodyLarge,
                color = Ed.palette.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Space.m),
            )
        }
        Rule()
    }
}

private const val DASH = "–"

/** From 10 Hz a rate reads in whole hertz; below it, with one decimal. */
private const val WHOLE_HZ_FROM = 10.0

@Composable
private fun StatusLine(status: SwitchStatus?) {
    status ?: return
    when (val outcome = status.outcome) {
        null -> Text(stringResource(R.string.sensors_switching), style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)

        ActionOutcome.Done, is ActionOutcome.Applied -> Unit

        is ActionOutcome.Unavailable -> {
            Spacer(Modifier.height(Space.s))
            Notice(stringResource(R.string.sensors_switch_failed), outcome.reason, tone = Tone.WARNING)
        }

        is ActionOutcome.Failed -> {
            Spacer(Modifier.height(Space.s))
            Notice(stringResource(R.string.sensors_switch_failed), outcome.raw, tone = Tone.CRITICAL, raw = true)
        }
    }
}
