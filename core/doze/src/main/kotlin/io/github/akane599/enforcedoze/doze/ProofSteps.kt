// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.doze.ActionRecord.Outcome

/**
 * The quick proof's steps (SPEC 8.6). Lever writes go through the [Executor] like any other, so they are
 * booked, read back and logged with [Cause.QUICK_PROOF]; the battery freeze is no lever and is logged as an
 * [EngineEvent.Battery]. [EngineCore.quickProof] puts them in order and undoes them.
 */
internal class ProofSteps(
    private val device: DozeDevice,
    private val calls: DeviceCalls,
    private val observer: Observer,
    private val book: Book,
    private val executor: Executor,
    private val journal: EngineJournal,
    private val clock: EngineClock,
) {
    /** Step 1: the global string as read back, against what the plan wants now. */
    suspend fun constants(wanted: Setting?): ProofStep {
        if (wanted == null) return ProofStep(ProofStepKind.CONSTANTS, false, "the plan sets no constants now")
        observer.read(setOf(Lever.CONSTANTS))
        val seen = book.seen[Lever.CONSTANTS] ?: return ProofStep(ProofStepKind.CONSTANTS, false, "$CONSTANTS could not be read")
        return ProofStep(ProofStepKind.CONSTANTS, seen == wanted, "$CONSTANTS=$seen")
    }

    /** A lever written and read back, judged as the action log judges it. */
    suspend fun write(setting: Setting, kind: ProofStepKind, stock: StockSnapshot?): ProofStep {
        val action = executor.execute(listOf(setting), stock) { Cause.QUICK_PROOF }.lastOrNull()
            ?: return ProofStep(kind, false, "nothing written")
        val parts = listOfNotNull(action.command, action.readBack?.let { "read back $it" }, action.detail.takeIf { it.isNotBlank() })
        return ProofStep(kind, action.outcome == Outcome.OK, parts.joinToString(": "))
    }

    /** Step 2: `cmd deviceidle get deep` must print IDLE. */
    suspend fun deepIdle(): ProofStep = when (val read = calls.run(null) { device.readDeepState() }) {
        null -> ProofStep(ProofStepKind.DEEP_IDLE, false, "$GET_DEEP: $NO_ANSWER")
        is DeviceResult.Failed -> ProofStep(ProofStepKind.DEEP_IDLE, false, "$GET_DEEP: ${read.reason}")
        is DeviceResult.Ok -> ProofStep(ProofStepKind.DEEP_IDLE, read.value == IDLE, "$GET_DEEP: ${read.value}")
    }

    /** Freezes the battery as unplugged, or gives it back; the freeze is what lets forced idle hold on a charger. */
    suspend fun battery(unplugged: Boolean): ProofStep {
        val write = DeviceWrite.Battery(unplugged)
        val (ok, answer) = when (val result = calls.run(DeviceCalls.BATTERY) { device.write(write) }) {
            null -> false to NO_ANSWER
            is DeviceResult.Failed -> false to result.reason
            is DeviceResult.Ok -> true to result.detail
        }
        journal.event(EngineEvent.Battery(clock.now(), unplugged, ok, answer))
        val kind = if (unplugged) ProofStepKind.UNPLUG else ProofStepKind.UNDO_BATTERY
        return ProofStep(kind, ok, listOf(write.label, answer).filter { it.isNotBlank() }.joinToString(": "))
    }

    private companion object {
        const val CONSTANTS = "device_idle_constants"
        const val GET_DEEP = "cmd deviceidle get deep"

        /** `cmd deviceidle get deep` prints `stateToString(mState)` (DeviceIdleController, android-17.0.0_r1 line 4944). */
        const val IDLE = "IDLE"
        const val NO_ANSWER = "no answer within ${DozeEngine.CALL_TIMEOUT_MILLIS} ms"
    }
}
