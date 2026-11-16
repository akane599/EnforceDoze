// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Rule
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.design.Tone
import io.github.akane599.enforcedoze.design.color

/** The four steps of the quick proof, in order (SPEC 8.6). */
public enum class ProofStepKind { CONSTANTS, FORCE_IDLE, SENSORS, UNDO }

public enum class ProofStepStatus { WAITING, RUNNING, OK, FAILED, SKIPPED }

/** One step of the proof and the raw line it was judged on, when it ran. */
@Immutable
public data class ProofStep(val kind: ProofStepKind, val status: ProofStepStatus, val raw: String? = null)

/** The quick proof's result card (SPEC 8.6 step 5): each step with ok or failed and its raw line. */
@Composable
public fun ProofCard(steps: List<ProofStep>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Rule()
        steps.forEach { step ->
            Column(Modifier.fillMaxWidth().padding(vertical = Space.m).semantics(mergeDescendants = true) { }) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        stringResource(step.kind.title()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ed.palette.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(step.status.word()),
                        style = MaterialTheme.typography.titleMedium,
                        color = step.status.tone().color(neutral = Ed.palette.quiet),
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = Space.m),
                    )
                }
                step.raw?.let { Text(it, style = RawLine, color = Ed.palette.quiet, modifier = Modifier.padding(top = Space.xs)) }
            }
            Rule()
        }
    }
}

private fun ProofStepKind.title(): Int = when (this) {
    ProofStepKind.CONSTANTS -> R.string.ui_proof_constants
    ProofStepKind.FORCE_IDLE -> R.string.ui_proof_force_idle
    ProofStepKind.SENSORS -> R.string.ui_proof_sensors
    ProofStepKind.UNDO -> R.string.ui_proof_undo
}

private fun ProofStepStatus.word(): Int = when (this) {
    ProofStepStatus.WAITING -> R.string.ui_proof_waiting
    ProofStepStatus.RUNNING -> R.string.ui_proof_running
    ProofStepStatus.OK -> R.string.ui_proof_ok
    ProofStepStatus.FAILED -> R.string.ui_proof_failed
    ProofStepStatus.SKIPPED -> R.string.ui_proof_skipped
}

private fun ProofStepStatus.tone(): Tone = when (this) {
    ProofStepStatus.OK -> Tone.GOOD
    ProofStepStatus.FAILED -> Tone.CRITICAL
    else -> Tone.NEUTRAL
}
