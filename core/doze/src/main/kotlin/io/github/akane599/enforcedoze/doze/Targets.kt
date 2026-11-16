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
    /** The keyguard is showing; only read with the screen on (ADR 0017). */
    val locked: Boolean,
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
    /** The owner's root-tier extras (ADR 0038). */
    val extras: Extras = Extras(),
    val radios: RadioReading? = null,
    val suspension: SuspensionReading? = null,
    /** Extras someone else changed while the screen was off: left alone until the next screen off. */
    val letGo: Set<Lever> = emptySet(),
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
    /** Extras the owner chose that stay off now, and why (ADR 0038). Not a partial application. */
    val idle: Map<Lever, String> = emptyMap(),
)

/**
 * The target function (ADR 0015). Constants (A) and the allowlist (D) follow the profile only, so a
 * screen or hold-off change can never write them. Forced idle (B), sensor restriction (C) and sensor
 * privacy are on only in managed mode with the screen off and no hold-off; otherwise an "on" state the
 * engine set is released and anything else is left alone. With lever B's "until unlock", an "on" state
 * the engine set is kept while the screen is on at the lock screen, never newly set (ADR 0017).
 */
public object Targets {
    public fun compute(inputs: TargetInputs): TargetSet {
        val profile = inputs.profile?.takeIf { inputs.mode == Mode.MANAGED }
        val result = Builder(inputs)
        result.constants(profile)
        val levers = profile?.levers
        val forceIdle = levers?.forceIdle
        val engaged = !inputs.screenOn && inputs.holdOffs.isEmpty()
        val untilUnlock = forceIdle?.untilUnlock == true && inputs.screenOn && inputs.locked && inputs.holdOffs.isEmpty()
        if (forceIdle?.enabled == true && forceIdle.delaySec > 0) {
            result.waiting[Lever.FORCE_IDLE] = "a forced-idle delay needs an alarm, which this version does not set"
        }
        result.timed(
            Lever.FORCE_IDLE,
            forceIdle?.enabled == true && forceIdle.delaySec == 0,
            engaged,
            untilUnlock,
            Setting.FORCED,
            Setting.RELEASED,
        )
        result.timed(
            Lever.SENSORS,
            levers?.sensors == SensorLever.RESTRICTED,
            engaged,
            untilUnlock,
            Setting.Sensors(SensorValue.Restricted(inputs.allowPackage)),
            Setting.SENSORS_NORMAL,
        )
        result.timed(
            Lever.SENSOR_PRIVACY,
            levers?.sensors == SensorLever.ALL_SENSORS_OFF,
            engaged,
            untilUnlock,
            Setting.SensorPrivacy(true),
            Setting.PRIVACY_OFF,
        )
        result.allowlist(profile)
        result.extras(profile != null && !inputs.screenOn && inputs.holdOffs.isEmpty())
        return result.build()
    }

    /**
     * Restore stock (SPEC 5.6 step 3): no string, released, sensors normal, privacy off, the allowlist as captured,
     * and any extra the engine holds put back. An extra it does not hold is the owner's and is left alone.
     */
    public fun restoreStock(ownerAllowlist: Set<String>?, written: Map<Lever, Setting> = emptyMap()): Map<Lever, Setting> = buildMap {
        put(Lever.CONSTANTS, Setting.ABSENT)
        put(Lever.FORCE_IDLE, Setting.RELEASED)
        put(Lever.SENSORS, Setting.SENSORS_NORMAL)
        put(Lever.SENSOR_PRIVACY, Setting.PRIVACY_OFF)
        if (ownerAllowlist != null) put(Lever.ALLOWLIST, Setting.Allowlist(ownerAllowlist))
        putAll(releaseExtras(written))
    }

    /** Every extra the engine holds, put back (ADR 0038). */
    public fun releaseExtras(written: Map<Lever, Setting>): Map<Lever, Setting> = buildMap {
        if (written[Lever.WIFI] == Setting.WIFI_OFF) put(Lever.WIFI, Setting.WIFI_ON)
        if (written[Lever.MOBILE_DATA] == Setting.DATA_OFF) put(Lever.MOBILE_DATA, Setting.DATA_ON)
        if ((written[Lever.APP_SUSPENSION] as? Setting.Suspension)?.packages?.isNotEmpty() == true) {
            put(Lever.APP_SUSPENSION, Setting.NONE_SUSPENDED)
        }
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
        putAll(releaseExtras(written))
    }

    private fun Setting?.isRestricted(): Boolean = this is Setting.Sensors && value is SensorValue.Restricted

    private const val UNREAD = "its state could not be read"

    private class Builder(val inputs: TargetInputs) {
        val wanted = linkedMapOf<Lever, Setting>()
        val waiting = linkedMapOf<Lever, String>()
        val armed = mutableSetOf<Lever>()
        val heldOff = mutableSetOf<Lever>()
        val used = mutableSetOf<Lever>()
        val idle = linkedMapOf<Lever, String>()
        var missing = emptySet<String>()

        fun build() = TargetSet(wanted, waiting, armed, heldOff, used, missing, idle)

        /**
         * The extras (ADR 0038): on only while [engaged] (managed, a profile, screen off, no hold-off of any kind),
         * newly set only when the read just before shows the feature on, and off at every screen on.
         */
        fun extras(engaged: Boolean) {
            val radios = inputs.radios
            extra(
                Lever.WIFI,
                Setting.WIFI_OFF,
                Setting.WIFI_ON,
                engaged,
                start = wifiStart(radios),
                keep = UNREAD.takeIf {
                    radios == null
                },
            )
            val data = when {
                radios == null -> UNREAD
                !radios.wifiValidated -> "the phone is not on a working Wi-Fi network, so mobile data stays on"
                else -> null
            }
            val dataStart = data ?: "mobile data is already off".takeIf { radios?.dataEnabled == false }
            extra(Lever.MOBILE_DATA, Setting.DATA_OFF, Setting.DATA_ON, engaged, start = dataStart, keep = data)
            suspension(engaged)
        }

        /** Wi-Fi goes off only on, unconnected and outside airplane mode: without a connection there is no Wi-Fi calling. */
        private fun wifiStart(radios: RadioReading?): String? = when {
            radios == null -> UNREAD
            !radios.wifiEnabled -> "Wi-Fi is already off"
            radios.wifiConnected -> "Wi-Fi is connected, and calls may need it"
            radios.airplane -> "airplane mode is on"
            else -> null
        }

        private fun suspension(engaged: Boolean) {
            val held = (inputs.written[Lever.APP_SUSPENSION] as? Setting.Suspension)?.packages.orEmpty()
            val reading = inputs.suspension
            // An app someone else suspended is theirs to lift; one the never-suspend list holds back now is lifted.
            val allowed = reading?.let { inputs.extras.suspend - it.blocked.keys - (it.suspended - held) }.orEmpty()
            val start = when {
                reading == null -> "the chosen apps could not be read"
                allowed.isEmpty() -> "every chosen app is on the never-suspend list or suspended already"
                else -> null
            }
            extra(
                Lever.APP_SUSPENSION,
                Setting.Suspension(allowed),
                Setting.NONE_SUSPENDED,
                engaged,
                start = start,
                keep = start,
                ours = held.isNotEmpty(),
            )
        }

        /**
         * [start] says why the extra may not go on now; [keep] why it must come off although the engine set it.
         * Null means it may.
         */
        @Suppress("LongParameterList") // One call per extra; each argument is a separate rule input.
        private fun extra(
            lever: Lever,
            on: Setting,
            off: Setting,
            engaged: Boolean,
            start: String?,
            keep: String?,
            ours: Boolean = inputs.written[lever] == on,
        ) {
            val chosen = inputs.extras.uses(lever)
            if (chosen) used += lever
            val usable = chosen && lever !in inputs.guarded && lever !in inputs.letGo
            val active = usable && engaged && keep == null && (ours || start == null)
            when {
                active -> wanted[lever] = on
                ours -> wanted[lever] = off
            }
            if (chosen && !active) rest(lever, keep ?: start)
        }

        /** Where a chosen extra that is not on stands: armed, held off, or idle with the reason. */
        private fun rest(lever: Lever, reason: String?) {
            when {
                inputs.screenOn -> armed += lever
                inputs.holdOffs.isNotEmpty() -> heldOff += lever
                lever in inputs.letGo -> idle[lever] = "changed by someone else tonight, so it is left as it is until the next screen off"
                inputs.profile == null || inputs.mode != Mode.MANAGED -> idle[lever] = "the plan is not running"
                else -> idle[lever] = reason ?: "not needed now"
            }
        }

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

        @Suppress("LongParameterList") // One call per timed lever; each argument is a separate rule input.
        fun timed(lever: Lever, usedByProfile: Boolean, engaged: Boolean, keepOn: Boolean, on: Setting, off: Setting) {
            if (usedByProfile) used += lever
            val uses = usedByProfile && lever !in inputs.guarded
            val written = inputs.written[lever]
            val ours = written != null && written != off && written.lever == lever
            val active = uses && (engaged || (keepOn && written == on))
            when {
                active -> wanted[lever] = on
                ours -> wanted[lever] = off
            }
            when {
                !uses || active -> Unit
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
