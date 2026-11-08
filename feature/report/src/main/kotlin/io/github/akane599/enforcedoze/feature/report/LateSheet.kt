// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Notice
import io.github.akane599.enforcedoze.design.Sizes
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone

/**
 * "Something arrived late" (SPEC 7.5): pick the app (or none), record it, then see how the plan steps
 * gentler and offer the allowlist for that app.
 */
@Composable
public fun LateSheetContent(
    candidates: List<LateCandidate>,
    sheet: LateSheet,
    onRecord: (packageName: String?) -> Unit,
    onAllowlist: (packageName: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val outcome = sheet.outcome
    Column(modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
        if (outcome == null) {
            var picked by rememberSaveable { mutableStateOf<String?>(null) }
            Text(stringResource(R.string.report_late_title), style = MaterialTheme.typography.headlineSmall, color = palette.ink)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.report_late_body), style = MaterialTheme.typography.bodyMedium, color = palette.quiet)
            Spacer(Modifier.height(Space.l))
            Column(Modifier.selectableGroup()) {
                candidates.forEach { app -> Choice(app.label, picked == app.packageName) { picked = app.packageName } }
                Choice(stringResource(R.string.report_late_not_sure), picked == null) { picked = null }
            }
            sheet.error?.let {
                Spacer(Modifier.height(Space.m))
                Notice(stringResource(R.string.report_late_failed), it, raw = true, tone = Tone.CRITICAL)
            }
            Spacer(Modifier.height(Space.l))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.report_cancel)) }
                FilledTonalButton(onClick = {
                    onRecord(picked)
                }, enabled = !sheet.busy) { Text(stringResource(R.string.report_late_record)) }
            }
        } else {
            Text(stringResource(R.string.report_late_recorded), style = MaterialTheme.typography.headlineSmall, color = palette.ink)
            Spacer(Modifier.height(Space.s))
            Text(
                outcome.stepTo?.let { stringResource(R.string.report_late_step, it) } ?: stringResource(R.string.report_late_gentlest),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.quiet,
            )
            Spacer(Modifier.height(Space.l))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.report_done)) }
                outcome.app?.let { app ->
                    FilledTonalButton(onClick = {
                        onAllowlist(app.packageName)
                    }) { Text(stringResource(R.string.report_late_allowlist, app.label)) }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.ink, modifier = Modifier.padding(start = Space.m))
    }
}
