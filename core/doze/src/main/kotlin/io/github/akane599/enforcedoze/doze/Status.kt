// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.profile.Profile

/** What the engine starts from: the plan's profile now, and what the app sees of the phone (SPEC 12.4). */
public data class EngineStart(
    val profile: Profile?,
    val screenOn: Boolean,
    val holdOffs: Set<HoldOff> = emptySet(),
    val writable: Writable = Writable.NOTHING,
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
    /** After Restore stock: keys that do not read back as in `stock.json`; null when that cannot be judged. */
    val stockDifferences: List<String>? = null,
    /** After the capture: the new `stock.json`. */
    val stock: StockSnapshot? = null,
) {
    /** Ran, every write read back as asked, and nothing was left out. */
    val ok: Boolean
        get() = refused == null && notWritable.isEmpty() && stockDifferences.isNullOrEmpty() &&
            actions.all { it.outcome == ActionRecord.Outcome.OK }
}
