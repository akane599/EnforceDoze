// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.AllowlistLever
import io.github.akane599.enforcedoze.profile.Profile
import io.github.akane599.enforcedoze.profile.SensorLever

/** What the targets are computed from (ADR 0015 "Inputs"). */
public data class TargetInputs(
    val profile: Profile?,
    val mode: Mode,
    val screenOn: Boolean,
    val holdOffs: Set<HoldOff>,
    val guarded: Set<Lever>,
    /** The `device_idle` keys this phone has, from the last `dumpsys deviceidle`; null until read (SPEC 5.2). */
    val knownKeys: Set<String>?,
    /** `previous.json` and `stock.json` both exist, so a constants write can be undone (SPEC 5.2). */
    val snapshotsReady: Boolean,
    val allowPackage: String,
    val allowlistKeep: Set<String>?,
    val ownerAllowlist: Set<String>?,
    /** What the engine last asked for, per lever: only an "on" state the engine set is ever released. */
    val written: Map<Lever, Setting>,
)

/**
 * One target per lever (ADR 0015): a lever missing from [wanted] is "don't care" and is never written.
 * [waiting] says why a lever the profile uses has no target yet; [armed] levers apply at the next screen
 * off, [heldOff] ones when the hold-offs end.
 */
public data class TargetSet(
    val wanted: Map<Lever, Setting>,
    val waiting: Map<Lever, String> = emptyMap(),
    val armed: Set<Lever> = emptySet(),
    val heldOff: Set<Lever> = emptySet(),
    /** Levers the active profile uses, guarded or not. */
    val used: Set<Lever> = emptySet(),
    /** Profile keys this phone does not have; they are skipped (SPEC 6.2). */
    val missingKeys: Set<String> = emptySet(),
)

/**
 * The target function (ADR 0015). Constants (A) and the allowlist (D) follow the profile only, so a
 * screen or hold-off change can never write them. Forced idle (B), sensor restriction (C) and sensor
 * privacy are on only in managed mode with the screen off and no hold-off; otherwise an "on" state the
 * engine set is released and anything else is left alone.
 */
public object Targets {
    public fun compute(inputs: TargetInputs): TargetSet {
        val profile = inputs.profile?.takeIf { inputs.mode == Mode.MANAGED }
        val result = Builder(inputs)
        result.constants(profile)
        val engaged = !inputs.screenOn && inputs.holdOffs.isEmpty()
        val levers = profile?.levers
        val forceIdle = levers?.forceIdle
        if (forceIdle?.enabled == true && forceIdle.delaySec > 0) {
            result.waiting[Lever.FORCE_IDLE] = "a forced-idle delay needs an alarm, which this version does not set"
        }
        result.timed(Lever.FORCE_IDLE, forceIdle?.enabled == true && forceIdle.delaySec == 0, engaged, Setting.FORCED, Setting.RELEASED)
        result.timed(
            Lever.SENSORS,
            levers?.sensors == SensorLever.RESTRICTED,
            engaged,
            Setting.Sensors(SensorValue.Restricted(inputs.allowPackage)),
            Setting.SENSORS_NORMAL,
        )
        result.timed(
            Lever.SENSOR_PRIVACY,
            levers?.sensors == SensorLever.ALL_SENSORS_OFF,
            engaged,
            Setting.SensorPrivacy(true),
            Setting.PRIVACY_OFF,
        )
        result.allowlist(profile)
        return result.build()
    }

    /** Restore stock (SPEC 5.6 step 3): no string, released, sensors normal, privacy off, the allowlist as captured. */
    public fun restoreStock(ownerAllowlist: Set<String>?): Map<Lever, Setting> = buildMap {
        put(Lever.CONSTANTS, Setting.ABSENT)
        put(Lever.FORCE_IDLE, Setting.RELEASED)
        put(Lever.SENSORS, Setting.SENSORS_NORMAL)
        put(Lever.SENSOR_PRIVACY, Setting.PRIVACY_OFF)
        if (ownerAllowlist != null) put(Lever.ALLOWLIST, Setting.Allowlist(ownerAllowlist))
    }

    /**
     * Return to previous setup (SPEC 5.2, 11.4): the previous string exactly, or no string; every "on"
     * state the engine set released; a trimmed allowlist put back.
     */
    public fun returnToPrevious(
        previous: PreviousSnapshot,
        written: Map<Lever, Setting>,
        ownerAllowlist: Set<String>?,
    ): Map<Lever, Setting> = buildMap {
        put(Lever.CONSTANTS, Setting.Constants(previous.constants))
        if (written[Lever.FORCE_IDLE] == Setting.FORCED) put(Lever.FORCE_IDLE, Setting.RELEASED)
        if (written[Lever.SENSORS].isRestricted()) put(Lever.SENSORS, Setting.SENSORS_NORMAL)
        if (written[Lever.SENSOR_PRIVACY] == Setting.SensorPrivacy(true)) put(Lever.SENSOR_PRIVACY, Setting.PRIVACY_OFF)
        if (written[Lever.ALLOWLIST] != null && ownerAllowlist != null) put(Lever.ALLOWLIST, Setting.Allowlist(ownerAllowlist))
    }

    private fun Setting?.isRestricted(): Boolean = this is Setting.Sensors && value is SensorValue.Restricted

    private class Builder(val inputs: TargetInputs) {
        val wanted = linkedMapOf<Lever, Setting>()
        val waiting = linkedMapOf<Lever, String>()
        val armed = mutableSetOf<Lever>()
        val heldOff = mutableSetOf<Lever>()
        val used = mutableSetOf<Lever>()
        var missing = emptySet<String>()

        fun build() = TargetSet(wanted, waiting, armed, heldOff, used, missing)

        fun constants(profile: Profile?) {
            if (profile == null || Lever.CONSTANTS in inputs.guarded) return
            val ours = (inputs.written[Lever.CONSTANTS] as? Setting.Constants)?.value is ConstantsValue.Exact
            val keys = profile.constants
            val known = inputs.knownKeys
            if (keys.isNotEmpty()) used += Lever.CONSTANTS
            when {
                keys.isEmpty() -> if (ours) wanted[Lever.CONSTANTS] = Setting.ABSENT

                known == null -> waiting[Lever.CONSTANTS] = "this phone's device_idle keys have not been read yet"

                !inputs.snapshotsReady -> waiting[Lever.CONSTANTS] = "previous.json and stock.json are not captured yet"

                else -> {
                    val kept = keys.filterKeys { it in known }
                    missing = keys.keys - known
                    when {
                        kept.isNotEmpty() -> wanted[Lever.CONSTANTS] =
                            Setting.Constants(ConstantsValue.Exact(DeviceIdleSettings.encode(kept)))

                        ours -> wanted[Lever.CONSTANTS] = Setting.ABSENT

                        else -> waiting[Lever.CONSTANTS] = "none of the profile's keys are on this phone"
                    }
                }
            }
        }

        fun timed(lever: Lever, usedByProfile: Boolean, engaged: Boolean, on: Setting, off: Setting) {
            if (usedByProfile) used += lever
            val uses = usedByProfile && lever !in inputs.guarded
            val written = inputs.written[lever]
            val ours = written != null && written != off && written.lever == lever
            when {
                uses && engaged -> wanted[lever] = on
                ours -> wanted[lever] = off
            }
            when {
                !uses || engaged -> Unit
                inputs.screenOn -> armed += lever
                else -> heldOff += lever
            }
        }

        fun allowlist(profile: Profile?) {
            if (profile == null || Lever.ALLOWLIST in inputs.guarded) return
            val keep = inputs.allowlistKeep
            val owner = inputs.ownerAllowlist
            if (profile.levers.allowlist == AllowlistLever.TRIMMED) used += Lever.ALLOWLIST
            when {
                profile.levers.allowlist == AllowlistLever.TRIMMED ->
                    if (keep ==
                        null
                    ) {
                        waiting[Lever.ALLOWLIST] = "no apps are marked keep yet"
                    } else {
                        wanted[Lever.ALLOWLIST] =
                            Setting.Allowlist(keep)
                    }

                inputs.written[Lever.ALLOWLIST] != null && owner != null -> wanted[Lever.ALLOWLIST] = Setting.Allowlist(owner)
            }
        }
    }
}
