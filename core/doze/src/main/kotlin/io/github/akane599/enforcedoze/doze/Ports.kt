// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleDump
import java.time.Instant

/** One write the engine can ask of the device. Adapters map each to a worker call or an app-process call. */
public sealed interface DeviceWrite {
    /** What the action log shows for this write (SPEC 8.1 "command or API call"). */
    public val label: String

    /** Lever A from the app process: the whole string, or delete the setting when [text] is null (ADR 0012). */
    public data class SetConstants(val text: String?) : DeviceWrite {
        override val label: String
            get() = if (text == null) "Settings.Global delete device_idle_constants" else "Settings.Global put device_idle_constants"
    }

    public data object ForceIdle : DeviceWrite {
        override val label: String get() = "cmd deviceidle force-idle deep"
    }

    public data object Unforce : DeviceWrite {
        override val label: String get() = "cmd deviceidle unforce"
    }

    public data class RestrictSensors(val allowPackage: String) : DeviceWrite {
        override val label: String get() = "dumpsys sensorservice restrict $allowPackage"
    }

    public data object EnableSensors : DeviceWrite {
        override val label: String get() = "dumpsys sensorservice enable"
    }

    /** Through the worker's `setAllSensorsOff` (SPEC 5.4). */
    public data class SensorPrivacy(val on: Boolean) : DeviceWrite {
        override val label: String get() = "worker setAllSensorsOff($on)"
    }

    public data class Allowlist(val packageName: String, val add: Boolean) : DeviceWrite {
        override val label: String get() = "cmd deviceidle whitelist ${if (add) "+" else "-"}$packageName"
    }
}

/** The outcome of one device call. [detail] is the raw line it was judged on, kept for the action log. */
public sealed interface DeviceResult<out T> {
    public data class Ok<T>(val value: T, val detail: String = "") : DeviceResult<T>

    public data class Failed(val reason: String) : DeviceResult<Nothing>
}

/** Lever A as read back: the value and, where `dumpsys settings` was readable, the package that last wrote it (ADR 0014). */
public data class ConstantsReading(val value: ConstantsValue, val writer: String?)

/** `dumpsys sensorservice`: the mode line (lever C) and the sensor privacy line, either possibly missing. */
public data class SensorReading(val mode: SensorValue?, val privacy: Boolean?)

/**
 * The device as the engine sees it. Calls may block for a long time (binder); the engine stops waiting
 * after [DozeEngine.CALL_TIMEOUT_MILLIS], so implementations need not time out themselves. A read the
 * current tier cannot make returns [DeviceResult.Failed].
 */
public interface DozeDevice {
    public suspend fun write(write: DeviceWrite): DeviceResult<Unit>

    /** The exact global string, through the worker or `dumpsys settings` (ADR 0014). */
    public suspend fun readConstants(): DeviceResult<ConstantsReading>

    /** `dumpsys deviceidle`: effective constants, state, the force flag. */
    public suspend fun readDeviceIdle(): DeviceResult<DeviceIdleDump>

    public suspend fun readSensors(): DeviceResult<SensorReading>

    /** The packages on the user allowlist (`user,…` rows of `cmd deviceidle whitelist`). */
    public suspend fun readAllowlist(): DeviceResult<Set<String>>
}

public fun interface EngineClock {
    public fun now(): Instant
}

/** One row per write (SPEC 8.1 "Action", 12.4). */
public data class ActionRecord(
    val time: Instant,
    val lever: Lever,
    val command: String,
    val expected: String,
    val readBack: String?,
    val outcome: Outcome,
    val cause: Cause,
    val detail: String = "",
) {
    public enum class Outcome {
        OK,

        /** The read-back did not match; [detail] names what differed. */
        MISMATCH,

        /** The device refused the write. */
        FAILED,

        /** No answer within the call timeout; the read-back decides what the lever holds. */
        TIMEOUT,

        /** Written, but the lever could not be read back. */
        UNVERIFIED,
    }
}

/** Hold-off, drift and guard rows (SPEC 8.1 "Hold-off, drift", 8.3 `event`). */
public sealed interface EngineEvent {
    public val time: Instant

    public data class HoldOffStarted(override val time: Instant, val holdOff: HoldOff) : EngineEvent

    public data class HoldOffEnded(override val time: Instant, val holdOff: HoldOff) : EngineEvent

    /** Someone else changed [lever]; [writer] is the package from `dumpsys settings` when known (ADR 0014). */
    public data class Drift(
        override val time: Instant,
        val lever: Lever,
        val expected: String,
        val found: String,
        val writer: String?,
        val whileStopped: Boolean,
    ) : EngineEvent

    /** Two corrections of [lever] within an hour: the engine stops re-applying it and asks (SPEC 5.7). */
    public data class LoopGuardTripped(override val time: Instant, val lever: Lever) : EngineEvent

    public data class ModeChanged(override val time: Instant, val mode: Mode) : EngineEvent

    public data class ProfileSwitched(override val time: Instant, val profileId: String, val reason: SwitchReason) : EngineEvent
}

public interface EngineJournal {
    public suspend fun action(record: ActionRecord)

    public suspend fun event(event: EngineEvent)
}

/** What the engine keeps across restarts (ADR 0015 "Book"). What it last read is not kept: it is read again. */
public data class PersistedState(
    val mode: Mode = Mode.MANAGED,
    val profileId: String? = null,
    /** Per lever, what the engine last asked the device for. */
    val written: Map<Lever, Setting> = emptyMap(),
    val corrections: Map<Lever, List<Instant>> = emptyMap(),
    val guarded: Set<Lever> = emptySet(),
    /** The owner's user allowlist as found before the engine first trimmed it (lever D). */
    val ownerAllowlist: Set<String>? = null,
    val reapplyOnDrift: Boolean = true,
    /** Apps the owner marked "keep" for a trimmed allowlist; null until chosen, and nothing is trimmed before. */
    val allowlistKeep: Set<String>? = null,
)

/** The `previous.json` snapshot (SPEC 5.2, ADR 0012): the string as found, or absent, and who wrote it. */
public data class PreviousSnapshot(
    val capturedAt: Instant,
    val constants: ConstantsValue,
    val writer: String?,
    val effective: Map<String, String>,
    val sensors: SensorValue?,
    val forceIdle: Boolean?,
    val allowlist: Set<String>?,
)

/** The `stock.json` snapshot: effective values with the string deleted, captured once with consent (ADR 0012). */
public data class StockSnapshot(val capturedAt: Instant, val effective: Map<String, String>)

public interface EngineStore {
    public suspend fun load(): PersistedState

    public suspend fun save(state: PersistedState)

    public suspend fun previous(): PreviousSnapshot?

    public suspend fun savePrevious(snapshot: PreviousSnapshot)

    public suspend fun stock(): StockSnapshot?

    public suspend fun saveStock(snapshot: StockSnapshot)
}
