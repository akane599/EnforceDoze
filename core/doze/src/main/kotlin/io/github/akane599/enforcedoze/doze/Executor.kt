// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.doze.ActionRecord.Outcome

/**
 * The only code that writes to the device (ADR 0015). Makes a plan's writes in order, then reads every
 * touched lever back once, then logs one action row per write with what the read-back showed.
 *
 * What gets booked as written is chosen so that doubt always ends in a release (SPEC 0.3 device safety):
 * - an engage (anything that is not a [release][isRelease]) is saved before it is written, so a process
 *   killed mid-write still knows to undo it, and stays booked unless every write was refused;
 * - a release is booked only once it answered and the read-back does not contradict it, so a release that
 *   timed out or did not take is tried again at the next event.
 */
internal class Executor(
    private val device: DozeDevice,
    private val calls: DeviceCalls,
    private val observer: Observer,
    private val book: Book,
    private val journal: EngineJournal,
    private val clock: EngineClock,
    /** Saves the book now; false when the store failed. */
    private val persist: suspend () -> Boolean,
) {
    private class Attempt(val setting: Setting, val command: String, val write: DeviceWrite?, val result: DeviceResult<Unit>?)

    /**
     * Executes [plan]. [writeAhead] off is for writes whose booking the caller puts back itself (the
     * `stock.json` capture), so nothing it touches is ever saved as the engine's own.
     */
    suspend fun execute(
        plan: List<Setting>,
        stock: StockSnapshot?,
        writeAhead: Boolean = true,
        causeOf: (Lever) -> Cause,
    ): List<ActionRecord> {
        if (plan.isEmpty()) return emptyList()
        val before = book.written
        val engages = if (writeAhead) plan.filterNot { isRelease(it) } else emptyList()
        engages.forEach { book.wrote(it) }
        val saved = engages.isEmpty() || persist()
        val attempts = plan.associateWith { setting ->
            if (!saved && setting in engages) {
                listOf(Attempt(setting, writesFor(setting)?.firstOrNull()?.label ?: NOTHING, null, DeviceResult.Failed(UNSAVED)))
            } else {
                write(setting)
            }
        }
        val touched = plan.map { it.lever }.toSet()
        touched.forEach { book.seen.remove(it) }
        observer.read(touched)
        val constants = attempts.entries.firstOrNull { (setting, tries) ->
            setting is Setting.Constants && tries.any { it.result !is DeviceResult.Failed }
        }?.key as? Setting.Constants
        if (constants != null) {
            book.constantsCheck = observer.settle(constants.value, stock, DozeEngine.SETTLE_MILLIS, DozeEngine.SETTLE_POLL_MILLIS)
        }
        attempts.forEach { (setting, tries) -> settleBooking(setting, tries, before[setting.lever]) }
        val now = clock.now()
        return attempts.values.flatten().map { attempt ->
            val (outcome, readBack, detail) = judge(attempt)
            ActionRecord(
                time = now,
                lever = attempt.setting.lever,
                command = attempt.command,
                expected = expected(attempt),
                readBack = readBack,
                outcome = outcome,
                cause = causeOf(attempt.setting.lever),
                detail = detail,
            ).also { journal.action(it) }
        }
    }

    /** A write that takes a lever back to where the engine found it: off, normal, no string, the owner's allowlist. */
    fun isRelease(setting: Setting): Boolean = when (setting) {
        is Setting.Constants -> setting.value == ConstantsValue.Absent
        is Setting.ForceIdle -> !setting.forced
        is Setting.Sensors -> setting.value == SensorValue.Normal
        is Setting.SensorPrivacy -> !setting.on
        is Setting.Allowlist -> setting.packages == book.persisted.ownerAllowlist
    }

    private suspend fun write(setting: Setting): List<Attempt> {
        val writes = writesFor(setting) ?: return listOf(Attempt(setting, ALLOWLIST, null, DeviceResult.Failed(UNREADABLE_ALLOWLIST)))
        return writes.map { write -> Attempt(setting, write.label, write, calls.run(setting.lever) { device.write(write) }) }
    }

    /** Books [setting] or puts back what was booked [before], after the read-back (see the class comment). */
    private fun settleBooking(setting: Setting, attempts: List<Attempt>, before: Setting?) {
        val seen = book.seen[setting.lever]
        val keep = when {
            seen == setting -> true
            isRelease(setting) -> attempts.all { it.result is DeviceResult.Ok } && seen == null
            else -> attempts.isEmpty() || attempts.any { it.result !is DeviceResult.Failed }
        }
        when {
            keep -> book.wrote(setting)
            before != null -> book.wrote(before)
            else -> book.forgetWritten(setting.lever)
        }
    }

    private fun writesFor(setting: Setting): List<DeviceWrite>? = when (setting) {
        is Setting.Constants -> listOf(DeviceWrite.SetConstants((setting.value as? ConstantsValue.Exact)?.text))

        is Setting.ForceIdle -> listOf(if (setting.forced) DeviceWrite.ForceIdle else DeviceWrite.Unforce)

        is Setting.Sensors -> when (val value = setting.value) {
            SensorValue.Normal -> listOf(DeviceWrite.EnableSensors)
            is SensorValue.Restricted -> listOf(DeviceWrite.RestrictSensors(value.allowPackage))
            is SensorValue.Other -> emptyList()
        }

        is Setting.SensorPrivacy -> listOf(DeviceWrite.SensorPrivacy(setting.on))

        is Setting.Allowlist -> (book.seen[Lever.ALLOWLIST] as? Setting.Allowlist)?.packages?.let { current ->
            (current - setting.packages).sorted().map { DeviceWrite.Allowlist(it, add = false) } +
                (setting.packages - current).sorted().map { DeviceWrite.Allowlist(it, add = true) }
        }
    }

    private fun expected(attempt: Attempt): String = when (val write = attempt.write) {
        is DeviceWrite.Allowlist -> "${write.packageName} ${if (write.add) "on" else "off"} the allowlist"
        else -> attempt.setting.toString()
    }

    private fun judge(attempt: Attempt): Triple<Outcome, String?, String> {
        val seen = book.seen[attempt.setting.lever]
        return when (val result = attempt.result) {
            null -> Triple(Outcome.TIMEOUT, seen?.toString(), "no answer within ${DozeEngine.CALL_TIMEOUT_MILLIS} ms")
            is DeviceResult.Failed -> Triple(Outcome.FAILED, seen?.toString(), result.reason)
            is DeviceResult.Ok -> verify(attempt, seen, result.detail)
        }
    }

    private fun verify(attempt: Attempt, seen: Setting?, detail: String): Triple<Outcome, String?, String> {
        val write = attempt.write
        if (write is DeviceWrite.Allowlist) {
            val packages = (seen as? Setting.Allowlist)?.packages ?: return Triple(Outcome.UNVERIFIED, null, detail)
            val on = write.packageName in packages
            val outcome = if (on == write.add) Outcome.OK else Outcome.MISMATCH
            return Triple(outcome, "${write.packageName} ${if (on) "on" else "off"} the allowlist", detail)
        }
        val check = book.constantsCheck.takeIf { attempt.setting.lever == Lever.CONSTANTS }
        return when {
            seen != null && seen != attempt.setting -> Triple(Outcome.MISMATCH, seen.toString(), detail)
            !check.isNullOrEmpty() -> Triple(Outcome.MISMATCH, seen?.toString(), check.joinToString("; "))
            seen == null && check == null -> Triple(Outcome.UNVERIFIED, null, detail)
            else -> Triple(Outcome.OK, seen?.toString() ?: "effective values match", detail)
        }
    }

    private companion object {
        const val ALLOWLIST = "cmd deviceidle whitelist"
        const val NOTHING = "nothing written"
        const val UNREADABLE_ALLOWLIST = "the allowlist could not be read, so it was not changed"
        const val UNSAVED = "the engine's state could not be saved, so nothing was turned on"
    }
}
