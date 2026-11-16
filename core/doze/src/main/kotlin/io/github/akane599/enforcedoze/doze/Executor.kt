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
        // A smaller suspension is booked with what it lifts, so an unsuspend that fails is tried again (ADR 0038).
        engages.forEach {
            book.wrote(if (it is Setting.Suspension) Setting.Suspension(it.packages + held(before[Lever.APP_SUSPENSION])) else it)
        }
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
        settleRadios(attempts)
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

    /** Radio writes that answered are re-read until they show: Wi-Fi reports on only once it has started. */
    private suspend fun settleRadios(attempts: Map<Setting, List<Attempt>>) {
        val radios = attempts.filter { (setting, tries) ->
            (setting is Setting.Wifi || setting is Setting.MobileData) && tries.all { it.result is DeviceResult.Ok }
        }.keys.toList()
        if (radios.isNotEmpty()) observer.settleRadios(radios, DozeEngine.RADIO_SETTLE_MILLIS, DozeEngine.SETTLE_POLL_MILLIS)
    }

    /** A write that takes a lever back to where the engine found it: off, normal, no string, the owner's allowlist. */
    fun isRelease(setting: Setting): Boolean = when (setting) {
        is Setting.Constants -> setting.value == ConstantsValue.Absent
        is Setting.ForceIdle -> !setting.forced
        is Setting.Sensors -> setting.value == SensorValue.Normal
        is Setting.SensorPrivacy -> !setting.on
        is Setting.Allowlist -> setting.packages == book.persisted.ownerAllowlist
        is Setting.Wifi, is Setting.MobileData, is Setting.Suspension -> setting.releasesExtra
    }

    private suspend fun write(setting: Setting): List<Attempt> {
        val writes = writesFor(setting) ?: return listOf(unreadable(setting))
        return writes.map { write -> Attempt(setting, write.label, write, calls.run(setting.lever) { device.write(write) }) }
    }

    private fun unreadable(setting: Setting): Attempt = if (setting is Setting.Suspension) {
        Attempt(setting, SUSPEND, null, DeviceResult.Failed(UNREADABLE_SUSPENSION))
    } else {
        Attempt(setting, ALLOWLIST, null, DeviceResult.Failed(UNREADABLE_ALLOWLIST))
    }

    private fun held(setting: Setting?): Set<String> = (setting as? Setting.Suspension)?.packages.orEmpty()

    /** Books [setting] or puts back what was booked [before], after the read-back (see the class comment). */
    private fun settleBooking(setting: Setting, attempts: List<Attempt>, before: Setting?) {
        if (setting is Setting.Suspension) {
            // The engine holds what it asked for or held before and that reads as suspended. Unread, or with a call
            // that timed out and may still suspend after the read, it keeps all of it.
            val asked = setting.packages + held(before)
            val seen = (book.seen[Lever.APP_SUSPENSION] as? Setting.Suspension)?.packages
            val unsure = seen == null || attempts.any { it.result == null }
            book.wrote(Setting.Suspension(if (unsure) asked else asked intersect seen.orEmpty()))
            return
        }
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

        is Setting.Wifi -> listOf(DeviceWrite.Wifi(setting.enabled))

        is Setting.MobileData -> listOf(DeviceWrite.MobileData(setting.enabled))

        is Setting.Suspension -> suspensionWrites(setting)
    }

    /**
     * Suspends what is wanted and not suspended yet, and unsuspends only what the engine holds (ADR 0038). With the
     * apps unread, a release still unsuspends everything held: that is harmless for an app that is not suspended.
     */
    private fun suspensionWrites(setting: Setting.Suspension): List<DeviceWrite>? {
        val held = held(book.written[Lever.APP_SUSPENSION])
        val current = (book.seen[Lever.APP_SUSPENSION] as? Setting.Suspension)?.packages
            ?: if (setting.packages.isEmpty()) held else return null
        val lift = ((current intersect held) - setting.packages).sorted()
        val add = (setting.packages - current).sorted()
        return listOfNotNull(
            lift.takeIf { it.isNotEmpty() }?.let { DeviceWrite.Suspend(it, suspended = false) },
            add.takeIf { it.isNotEmpty() }?.let { DeviceWrite.Suspend(it, suspended = true) },
        )
    }

    private fun expected(attempt: Attempt): String = when (val write = attempt.write) {
        is DeviceWrite.Allowlist -> "${write.packageName} ${if (write.add) "on" else "off"} the allowlist"
        is DeviceWrite.Suspend -> "${write.packages.joinToString(",")} ${if (write.suspended) "suspended" else "not suspended"}"
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
        when (val write = attempt.write) {
            is DeviceWrite.Allowlist -> return verifyPackages(listOf(write.packageName), write.add, seen, detail) { on ->
                if (on) "on the allowlist" else "off the allowlist"
            }

            is DeviceWrite.Suspend -> return verifyPackages(write.packages, write.suspended, seen, detail) { on ->
                if (on) "suspended" else "not suspended"
            }

            else -> Unit
        }
        val check = book.constantsCheck.takeIf { attempt.setting.lever == Lever.CONSTANTS }
        return when {
            seen != null && seen != attempt.setting -> Triple(Outcome.MISMATCH, seen.toString(), detail)
            !check.isNullOrEmpty() -> Triple(Outcome.MISMATCH, seen?.toString(), check.joinToString("; "))
            seen == null && check == null -> Triple(Outcome.UNVERIFIED, null, detail)
            else -> Triple(Outcome.OK, seen?.toString() ?: "effective values match", detail)
        }
    }

    /** A write that moves [packages] in or out of a set (the allowlist, the suspended apps), judged per package. */
    private fun verifyPackages(
        packages: List<String>,
        wantedIn: Boolean,
        seen: Setting?,
        detail: String,
        word: (Boolean) -> String,
    ): Triple<Outcome, String?, String> {
        val shown = when (seen) {
            is Setting.Allowlist -> seen.packages
            is Setting.Suspension -> seen.packages
            else -> return Triple(Outcome.UNVERIFIED, null, detail)
        }
        val outcome = if (packages.all { (it in shown) == wantedIn }) Outcome.OK else Outcome.MISMATCH
        return Triple(outcome, packages.joinToString(",") { "$it ${word(it in shown)}" }, detail)
    }

    private companion object {
        const val ALLOWLIST = "cmd deviceidle whitelist"
        const val NOTHING = "nothing written"
        const val UNREADABLE_ALLOWLIST = "the allowlist could not be read, so it was not changed"
        const val SUSPEND = "pm suspend"
        const val UNREADABLE_SUSPENSION = "the chosen apps could not be read, so none was suspended"
        const val UNSAVED = "the engine's state could not be saved, so nothing was turned on"
    }
}
