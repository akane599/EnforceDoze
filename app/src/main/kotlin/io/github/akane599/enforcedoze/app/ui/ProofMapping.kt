// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.doze.QuickProof
import io.github.akane599.enforcedoze.ui.ProofStep
import io.github.akane599.enforcedoze.ui.ProofStepKind
import io.github.akane599.enforcedoze.ui.ProofStepStatus
import io.github.akane599.enforcedoze.doze.ProofStepKind as EngineStep

/**
 * The quick proof's result card (SPEC 8.6 step 5): the engine's eight checks grouped into the four steps the owner
 * reads, each with the raw lines it was judged on. A group passes only when every check in it passed.
 */
internal object ProofMapping {
    /** While the engine runs the proof: it reports only at the end, so the first step shows as running. */
    val running: List<ProofStep> = ProofStepKind.entries.map {
        ProofStep(it, if (it == ProofStepKind.CONSTANTS) ProofStepStatus.RUNNING else ProofStepStatus.WAITING)
    }

    fun steps(proof: QuickProof): List<ProofStep> {
        proof.refused?.let { reason ->
            // Refused before anything ran: say why on the first step and leave the rest as not run.
            return ProofStepKind.entries.map {
                if (it == ProofStepKind.CONSTANTS) ProofStep(it, ProofStepStatus.FAILED, reason) else ProofStep(it, ProofStepStatus.SKIPPED)
            }
        }
        return ProofStepKind.entries.map { kind ->
            val checks = proof.steps.filter { group(it.kind) == kind }
            val status = when {
                checks.isEmpty() -> ProofStepStatus.SKIPPED
                checks.all { it.ok } -> ProofStepStatus.OK
                else -> ProofStepStatus.FAILED
            }
            ProofStep(kind, status, checks.joinToString("; ") { it.line }.ifBlank { null })
        }
    }

    /** A failed proof with the error that stopped it, when the engine could not be reached at all. */
    fun failed(raw: String): List<ProofStep> = ProofStepKind.entries.map {
        if (it == ProofStepKind.CONSTANTS) ProofStep(it, ProofStepStatus.FAILED, raw) else ProofStep(it, ProofStepStatus.SKIPPED)
    }

    private fun group(step: EngineStep): ProofStepKind = when (step) {
        EngineStep.CONSTANTS -> ProofStepKind.CONSTANTS
        EngineStep.UNPLUG, EngineStep.FORCE_IDLE, EngineStep.DEEP_IDLE -> ProofStepKind.FORCE_IDLE
        EngineStep.SENSORS -> ProofStepKind.SENSORS
        EngineStep.UNDO_FORCE_IDLE, EngineStep.UNDO_SENSORS, EngineStep.UNDO_BATTERY -> ProofStepKind.UNDO
    }
}
