// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import io.github.akane599.enforcedoze.doze.ConstantsReading
import io.github.akane599.enforcedoze.doze.ConstantsValue
import io.github.akane599.enforcedoze.doze.DeviceResult
import io.github.akane599.enforcedoze.doze.SensorReading
import io.github.akane599.enforcedoze.doze.SensorValue
import io.github.akane599.enforcedoze.doze.Writable
import io.github.akane599.enforcedoze.parse.Allowlist
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.parse.SensorMode
import io.github.akane599.enforcedoze.parse.SensorServiceDump
import io.github.akane599.enforcedoze.parse.SettingsDump
import io.github.akane599.enforcedoze.privilege.Grant
import io.github.akane599.enforcedoze.privilege.PrivilegeState
import io.github.akane599.enforcedoze.privilege.WorkerResult
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome

/** Pure translations between the privilege layer's results and the engine's port (ADR 0015). */
internal object DeviceMapping {
    /** What `ProcessCommandRunner` appends when it cut the output at its cap. */
    private const val TRUNCATED = "\n[truncated]"

    /** `cmd deviceidle whitelist` prints the user allowlist as `user,<pkg>,<uid>` (DeviceIdleController, android-17.0.0_r1). */
    private const val USER_KIND = "user"

    /**
     * What the tier lets the engine write (SPEC 4.1, ADR 0004, 0012): the string with
     * `WRITE_SECURE_SETTINGS`; sensor restriction with `DUMP` (`dumpsys sensorservice restrict|enable`
     * checks only `DUMP`) or a worker; everything else only through a worker.
     */
    fun writable(state: PrivilegeState): Writable {
        val worker = state.workerSource != null
        return Writable(
            constants = Grant.WRITE_SECURE_SETTINGS in state.grants,
            forceIdle = worker,
            sensors = worker || Grant.DUMP in state.grants,
            sensorPrivacy = worker,
            allowlist = worker,
        )
    }

    /**
     * The global string and its writer from a local `dumpsys settings` (ADR 0014), or null when the dump
     * cannot tell: it failed, it shows no global table (a `Permission Denial`), or it was cut short before
     * the row could be found, so a missing row cannot be read as "no string".
     */
    fun constantsFromSettingsDump(outcome: CommandOutcome): ConstantsReading? {
        if (!outcome.isSuccess) return null
        val text = outcome.stdout
        return when (val found = SettingsDump.lookup(text, SettingsDump.Table.GLOBAL, DeviceIdleSettings.SETTING)) {
            is SettingsDump.Lookup.Found -> ConstantsReading(ConstantsValue.of(found.row.value), found.row.packageName)
            SettingsDump.Lookup.NoRow -> if (text.endsWith(TRUNCATED)) null else ConstantsReading(ConstantsValue.Absent, null)
            SettingsDump.Lookup.NoTable -> null
        }
    }

    /** `settings get global device_idle_constants` through the worker: `null` when unset, and no writer. */
    fun constantsFromSettingsGet(outcome: CommandOutcome): DeviceResult<ConstantsReading> = if (outcome.isSuccess) {
        DeviceResult.Ok(ConstantsReading(ConstantsValue.of(outcome.stdout.trimEnd('\n')), null))
    } else {
        DeviceResult.Failed(describe(outcome))
    }

    /** Lever C's mode and the privacy toggle; a line that is missing (or cut off) stays unknown. */
    fun sensors(text: String): SensorReading {
        val dump = SensorServiceDump.parse(text)
        val mode = when (dump.mode) {
            null -> null
            SensorMode.NORMAL -> SensorValue.Normal
            SensorMode.RESTRICTED -> dump.modePackage?.let { SensorValue.Restricted(it) } ?: SensorValue.Other(dump.modeLine.orEmpty())
            SensorMode.OTHER -> SensorValue.Other(dump.modeName.orEmpty())
        }
        return SensorReading(mode, dump.sensorPrivacy)
    }

    /** The user allowlist; output with no recognisable rows at all is an error message, not an empty list. */
    fun allowlist(text: String): DeviceResult<Set<String>> {
        val parsed = Allowlist.parse(text)
        if (parsed.entries.isEmpty() && parsed.unknownLines.isNotEmpty()) {
            return DeviceResult.Failed("unexpected output: ${parsed.unknownLines.first().take(MAX_DETAIL)}")
        }
        return DeviceResult.Ok(parsed.entries.filter { it.kind == USER_KIND }.map { it.packageName }.toSet())
    }

    /** A write's outcome: exit 0 is only "answered"; the engine's read-back decides (SPEC 5.6). */
    fun write(outcome: CommandOutcome): DeviceResult<Unit> {
        val output = outcome.stdout.trim()
        return when {
            !outcome.isSuccess -> DeviceResult.Failed(describe(outcome))

            // `dumpsys` exits 0 when the caller lacks the service's permission.
            output.startsWith(PERMISSION_DENIAL) -> DeviceResult.Failed(output.take(MAX_DETAIL))

            else -> DeviceResult.Ok(Unit, output.take(MAX_DETAIL))
        }
    }

    /** Whether a dump answered with its text rather than a `Permission Denial`. */
    fun answered(outcome: CommandOutcome): Boolean = outcome.isSuccess && !outcome.stdout.trimStart().startsWith(PERMISSION_DENIAL)

    fun <T, R> worker(result: WorkerResult<T>, map: (T) -> DeviceResult<R>): DeviceResult<R> = when (result) {
        is WorkerResult.Ok -> map(result.value)
        is WorkerResult.Unavailable -> DeviceResult.Failed("no worker: ${result.reason}")
        is WorkerResult.Failed -> DeviceResult.Failed("worker call failed: ${result.error}")
    }

    fun describe(outcome: CommandOutcome): String {
        val command = outcome.argv.joinToString(" ")
        return when {
            outcome.error != null -> "$command: ${outcome.error}"
            outcome.timedOut -> "$command: timed out"
            else -> "$command: exit ${outcome.exitCode} ${outcome.stderr.trim().take(MAX_DETAIL)}".trim()
        }
    }

    private const val MAX_DETAIL = 200
    private const val PERMISSION_DENIAL = "Permission Denial"
}
