// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.parse.SettingValue
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Levers
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever
import java.time.Instant

/** A `device_idle` value the phone showed before EnforceDoze wrote anything, unlike stock (SPEC 11.4 step 1). */
public data class KeyDifference(val key: String, val found: String, val stock: String?)

/**
 * What another manager left on the phone: `previous.json` against `stock.json` (SPEC 11.4, 5.7). [constants] are
 * the keys of the global string as found, in milliseconds; [differences] are the effective values that differ from
 * stock, as `dumpsys deviceidle` printed them.
 */
public data class FoundSetup(
    val capturedAt: Instant,
    val writer: String?,
    val constants: Map<String, String>,
    val differences: List<KeyDifference>,
    val sensorsRestricted: Boolean,
    val forceIdle: Boolean,
) {
    /** Something else had changed Doze: an import has something to keep. */
    val changed: Boolean get() = constants.isNotEmpty() || differences.isNotEmpty() || sensorsRestricted || forceIdle
}

/** Step 3 of the switch-over: no foreign write over the next screen off and on (SPEC 11.4). */
public sealed interface SwitchCheck {
    public data object NotStarted : SwitchCheck

    /** Started at [since]; no screen off and on has ended since. */
    public data class Waiting(val since: Instant) : SwitchCheck

    /** Something changed Doze at [at] after the start: the other setup is still on. */
    public data class ForeignWrite(val since: Instant, val at: Instant, val writer: String?) : SwitchCheck

    /** A whole screen off and on, ended at [at], with no foreign write since [since]. */
    public data class Complete(val since: Instant, val at: Instant) : SwitchCheck
}

/** A foreign write as the journal holds it (a drift event): when, and which package wrote, when known. */
public data class ForeignWriteSeen(val at: Instant, val writer: String?)

public object SwitchOver {
    /** The id the imported profile starts from; a second import gets `imported-setup-2` (Profile.freshId). */
    public const val IMPORTED_ID: String = "imported-setup"

    public fun found(previous: PreviousSnapshot, stock: StockSnapshot?): FoundSetup {
        val parsed = DeviceIdleSettings.parse((previous.constants as? ConstantsValue.Exact)?.text)
        val constants = if (parsed.valid) parsed.values else emptyMap()
        // Without stock.json, the string's own keys are the difference; with it, the effective values say.
        val differences = if (stock == null) {
            constants.map { (key, value) -> KeyDifference(key, value, null) }
        } else {
            previous.effective.mapNotNull { (key, value) ->
                val base = stock.effective[key] ?: return@mapNotNull null
                KeyDifference(key, value, base).takeIf { value != base }
            }
        }
        return FoundSetup(
            capturedAt = previous.capturedAt,
            writer = previous.writer,
            constants = constants,
            differences = differences.sortedBy { it.key },
            sensorsRestricted = previous.sensors is SensorValue.Restricted,
            forceIdle = previous.forceIdle == true,
        )
    }

    /**
     * "Imported setup" (SPEC 11.4 step 2): the string's keys, plus any other effective value that differs from stock
     * and reads as a value lever A can write; sensors restricted and forced idle when they were seen. Built-in
     * profiles stay as they are; this is a custom profile the owner can edit or delete.
     */
    public fun imported(found: FoundSetup, name: String, taken: Set<String>): Profile {
        val extra = found.differences.filter { it.key !in found.constants }.mapNotNull { difference ->
            val value = SettingValue(difference.found).let { it.longValue?.toString() ?: it.raw }
            (difference.key to value).takeIf { DeviceIdleSettings.problems(mapOf(it)).isEmpty() }
        }
        val constants = found.constants + extra
        return Profile(
            id = Profile.freshId(IMPORTED_ID, taken),
            name = name,
            levers = Levers(
                constants = constants.isNotEmpty(),
                forceIdle = ForceIdleLever(enabled = found.forceIdle),
                sensors = if (found.sensorsRestricted) SensorLever.RESTRICTED else SensorLever.OFF,
                allowlist = AllowlistLever.INHERIT,
            ),
            deviceIdle = constants,
        )
    }

    /** Where step 3 stands: any foreign write after [since] fails it; a screen-off session begun and ended after it passes. */
    public fun check(since: Instant?, writes: List<ForeignWriteSeen>, sessions: List<Pair<Instant, Instant>>): SwitchCheck {
        since ?: return SwitchCheck.NotStarted
        writes.filter { it.at > since }.minByOrNull { it.at }?.let { return SwitchCheck.ForeignWrite(since, it.at, it.writer) }
        val ended = sessions.filter { (start, end) -> start >= since && end > start }.minByOrNull { it.second }
        return if (ended != null) SwitchCheck.Complete(since, ended.second) else SwitchCheck.Waiting(since)
    }
}
