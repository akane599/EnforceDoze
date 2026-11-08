// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.profile.Profile

/** What the engine starts from: the plan's profile now, and what the app sees of the phone (SPEC 12.4). */
public data class EngineStart(
    val profile: Profile?,
    val screenOn: Boolean,
    val holdOffs: Set<HoldOff> = emptySet(),
    val writable: Writable = Writable.NOTHING,
    /** The keyguard is showing (ADR 0017). */
    val locked: Boolean = false,
)

/** Where one lever stands, judged from what was read back, never from what was asked for (SPEC 5.6). */
public enum class LeverState {
    /** The profile does not use it and nothing the engine set is left on it. */
    UNUSED,

    /** The profile uses it at screen off; the screen is on. */
    ARMED,

    /** The profile uses it, but a hold-off is active (SPEC 5.5). */
    HELD_OFF,

    /** The device shows the wanted value. */
    APPLIED,

    /** The device shows something else; [LeverStatus.reason] says what. */
    PARTLY,

    /** The current tier cannot write it. */
    NOT_WRITABLE,

    /** Wanted, but not read back. */
    UNKNOWN,

    /** The profile uses it, but something it needs is missing; [LeverStatus.reason] says what. */
    WAITING,

    /** The loop guard tripped (SPEC 5.7): it is left alone until the owner answers. */
    GUARDED,
}

public data class LeverStatus(
    val state: LeverState,
    val wanted: Setting? = null,
    val written: Setting? = null,
    val seen: Setting? = null,
    val reason: String? = null,
)

public data class EngineStatus(
    val started: Boolean = false,
    val mode: Mode = Mode.MANAGED,
    val profileId: String? = null,
    val screenOn: Boolean = true,
    val holdOffs: Set<HoldOff> = emptySet(),
    val levers: Map<Lever, LeverStatus> = emptyMap(),
    /** Levers where the loop guard tripped; the app asks the owner about each (SPEC 5.7). */
    val guarded: Set<Lever> = emptySet(),
    /** Profile keys this phone does not have (SPEC 6.2). */
    val missingKeys: Set<String> = emptySet(),
    /** Idle is forced and no worker can release it: the banner of ADR 0015. */
    val forcedWithoutWorker: Boolean = false,
    /** The package that last wrote the global string (ADR 0014). */
    val constantsWriter: String? = null,
    val previousCaptured: Boolean = false,
    val stockCaptured: Boolean = false,
    /** The engine's state could not be saved; it is tried again after every command. */
    val storeError: String? = null,
    /** A quick proof froze the battery as unplugged and giving it back is not confirmed yet (SPEC 8.6). */
    val batteryFrozen: Boolean = false,
) {
    /** "Partly applied" on the home screen (SPEC 5.6 step 2). */
    val partlyApplied: Boolean get() = missingKeys.isNotEmpty() || levers.values.any { it.state in PARTLY_STATES }

    private companion object {
        val PARTLY_STATES = setOf(LeverState.PARTLY, LeverState.NOT_WRITABLE, LeverState.UNKNOWN, LeverState.WAITING, LeverState.GUARDED)
    }
}

/** The result of a one-shot job: Restore stock, Return to previous setup or the `stock.json` capture. */
public data class JobResult(
    val actions: List<ActionRecord> = emptyList(),
    /** Why the job did not run, or null when it ran. */
    val refused: String? = null,
    /** Levers the job needed but the tier cannot write. */
    val notWritable: Set<Lever> = emptySet(),
    /** Levers the job left alone on purpose, with why. */
    val skipped: Map<Lever, String> = emptyMap(),
    /** After Restore stock: keys that do not read back as in `stock.json`; null when that was not judged. */
    val stockDifferences: List<String>? = null,
    /** Why the job's result could not be checked, or null when it was. */
    val unverified: String? = null,
    /** After the capture: the new `stock.json`. */
    val stock: StockSnapshot? = null,
) {
    /** Ran, every write read back as asked, the result was checked, and nothing was left out. */
    val ok: Boolean
        get() = refused == null && notWritable.isEmpty() && skipped.isEmpty() && unverified == null &&
            stockDifferences.isNullOrEmpty() && actions.all { it.outcome == ActionRecord.Outcome.OK }
}

/** The quick proof's steps (SPEC 8.6): what was applied and confirmed, then what was undone. */
public enum class ProofStepKind {
    /** 1. The profile's constants, read back. */
    CONSTANTS,

    /** 2. `dumpsys battery unplug`. */
    UNPLUG,

    /** 2. `cmd deviceidle force-idle deep`, read back from `dumpsys deviceidle`. */
    FORCE_IDLE,

    /** 2. `cmd deviceidle get deep` reports IDLE. */
    DEEP_IDLE,

    /** 3. Sensors restricted, the mode read back from `dumpsys sensorservice`. */
    SENSORS,

    /** 4. `cmd deviceidle unforce`. */
    UNDO_FORCE_IDLE,

    /** 4. `dumpsys sensorservice enable`. */
    UNDO_SENSORS,

    /** 4. `dumpsys battery reset`. */
    UNDO_BATTERY,
}

/** One line of the result card: the step, whether it passed, and the raw line it was judged on (SPEC 8.6 step 5). */
public data class ProofStep(val kind: ProofStepKind, val ok: Boolean, val line: String)

public data class QuickProof(
    val steps: List<ProofStep> = emptyList(),
    /** Why the proof did not run, or null when it ran. */
    val refused: String? = null,
) {
    /** Ran, and every step passed, the undo included. */
    val ok: Boolean get() = refused == null && steps.isNotEmpty() && steps.all { it.ok }
}
