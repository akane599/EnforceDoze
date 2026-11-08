// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.doze.ActionRecord.Outcome

/**
 * The only code that writes to the device (ADR 0015). Makes a plan's writes in order, then reads every
 * touched lever back once, then logs one action row per write with what the read-back showed.
 */
internal class Executor(
    private val device: DozeDevice,
    private val calls: DeviceCalls,
    private val observer: Observer,
    private val book: Book,
    private val journal: EngineJournal,
    private val clock: EngineClock,
) {
    private class Attempt(val setting: Setting, val write: DeviceWrite?, val result: DeviceResult<Unit>?)

    suspend fun execute(plan: List<Setting>, stock: StockSnapshot?, causeOf: (Lever) -> Cause): List<ActionRecord> {
        if (plan.isEmpty()) return emptyList()
        val attempts = plan.flatMap { setting -> write(setting) }
        val touched = plan.map { it.lever }.toSet()
        touched.forEach { book.seen.remove(it) }
        observer.read(touched)
        val constants = attempts.firstOrNull {
            it.setting is Setting.Constants && it.result !is DeviceResult.Failed
        }?.setting as? Setting.Constants
        if (constants != null) {
            book.constantsCheck = observer.settle(constants.value, stock, DozeEngine.SETTLE_MILLIS, DozeEngine.SETTLE_POLL_MILLIS)
        }
        val now = clock.now()
        return attempts.map { attempt ->
            val (outcome, readBack, detail) = judge(attempt)
            ActionRecord(
                time = now,
                lever = attempt.setting.lever,
                command = attempt.write?.label ?: "cmd deviceidle whitelist",
                expected = expected(attempt),
                readBack = readBack,
                outcome = outcome,
                cause = causeOf(attempt.setting.lever),
                detail = detail,
            ).also { journal.action(it) }
        }
    }

    /** Makes the device writes for one setting and books it unless every write was refused. */
    private suspend fun write(setting: Setting): List<Attempt> {
        val writes = writesFor(setting) ?: return listOf(Attempt(setting, null, DeviceResult.Failed(UNREADABLE_ALLOWLIST)))
        val attempts = writes.map { write -> Attempt(setting, write, calls.run(setting.lever) { device.write(write) }) }
        if (attempts.isEmpty() || attempts.any { it.result !is DeviceResult.Failed }) book.wrote(setting)
        return attempts
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
            return Triple(
                if (on ==
                    write.add
                ) {
                    Outcome.OK
                } else {
                    Outcome.MISMATCH
                },
                "${write.packageName} ${if (on) "on" else "off"} the allowlist",
                detail,
            )
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
        const val UNREADABLE_ALLOWLIST = "the allowlist could not be read, so it was not changed"
    }
}
