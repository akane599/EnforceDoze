// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

/** The engine's levers (SPEC 5): A constants, B forced idle, C sensors (restricted mode and privacy), D allowlist. */
public enum class Lever { CONSTANTS, FORCE_IDLE, SENSORS, SENSOR_PRIVACY, ALLOWLIST }

/** Lever A's state: the global `device_idle_constants` string, or no string at all (stock; ADR 0012). */
public sealed interface ConstantsValue {
    public data object Absent : ConstantsValue {
        override fun toString(): String = "absent"
    }

    public data class Exact(val text: String) : ConstantsValue {
        override fun toString(): String = text
    }

    public companion object {
        /** `settings get` prints `null` for an absent setting; an empty string behaves like no string. */
        public fun of(raw: String?): ConstantsValue = if (raw == null || raw.isEmpty() || raw == "null") Absent else Exact(raw)
    }
}

/** Lever C's sensor service mode (SPEC 5.4): normal, restricted with the one package still allowed, or another mode. */
public sealed interface SensorValue {
    public data object Normal : SensorValue {
        override fun toString(): String = "NORMAL"
    }

    public data class Restricted(val allowPackage: String) : SensorValue {
        override fun toString(): String = "RESTRICTED : $allowPackage"
    }

    /** A mode the engine never sets (data injection, replay), as printed. */
    public data class Other(val name: String) : SensorValue {
        override fun toString(): String = name
    }
}

/** One lever's value: what the engine wants, last wrote, or last read. Each value knows its lever. */
public sealed interface Setting {
    public val lever: Lever

    public data class Constants(val value: ConstantsValue) : Setting {
        override val lever: Lever get() = Lever.CONSTANTS

        override fun toString(): String = value.toString()
    }

    public data class ForceIdle(val forced: Boolean) : Setting {
        override val lever: Lever get() = Lever.FORCE_IDLE

        override fun toString(): String = if (forced) "forced" else "released"
    }

    public data class Sensors(val value: SensorValue) : Setting {
        override val lever: Lever get() = Lever.SENSORS

        override fun toString(): String = value.toString()
    }

    public data class SensorPrivacy(val on: Boolean) : Setting {
        override val lever: Lever get() = Lever.SENSOR_PRIVACY

        override fun toString(): String = if (on) "all sensors off" else "sensors on"
    }

    /** The packages on the user allowlist; system entries are never touched. */
    public data class Allowlist(val packages: Set<String>) : Setting {
        override val lever: Lever get() = Lever.ALLOWLIST

        override fun toString(): String = packages.sorted().joinToString(",", "[", "]")
    }

    public companion object {
        public val RELEASED: ForceIdle = ForceIdle(false)
        public val FORCED: ForceIdle = ForceIdle(true)
        public val SENSORS_NORMAL: Sensors = Sensors(SensorValue.Normal)
        public val PRIVACY_OFF: SensorPrivacy = SensorPrivacy(false)
        public val ABSENT: Constants = Constants(ConstantsValue.Absent)
    }
}

/** Hold-offs (SPEC 5.5): while any is active, forced idle and sensor restriction are lifted; constants stay. */
public enum class HoldOff { CALL, AUDIO, CAR_MODE, HOTSPOT, KEEP_AWAKE_APP, CHARGING }

/** What the current tier lets the engine write, per lever (SPEC 4.1, ADR 0004, 0012). Reads fail on their own. */
public data class Writable(
    /** WRITE_SECURE_SETTINGS: the app writes the global string itself (ADR 0012). */
    val constants: Boolean = false,
    /** A worker. */
    val forceIdle: Boolean = false,
    /** DUMP or a worker (ADR 0004). */
    val sensors: Boolean = false,
    /** A worker. */
    val sensorPrivacy: Boolean = false,
    /** A worker. */
    val allowlist: Boolean = false,
) {
    public fun can(lever: Lever): Boolean = when (lever) {
        Lever.CONSTANTS -> constants
        Lever.FORCE_IDLE -> forceIdle
        Lever.SENSORS -> sensors
        Lever.SENSOR_PRIVACY -> sensorPrivacy
        Lever.ALLOWLIST -> allowlist
    }

    public companion object {
        public val NOTHING: Writable = Writable()
        public val EVERYTHING: Writable = Writable(true, true, true, true, true)
    }
}

/**
 * The engine's mode (ADR 0015). A tripped loop guard is kept per lever beside the mode
 * ([EngineStatus.guarded]), so the other levers keep working while the owner is asked.
 */
public enum class Mode {
    /** The plan's profile is applied and kept. */
    MANAGED,

    /** Log only; set after Restore stock or when the owner pauses the plan. */
    PAUSED,

    /** After "Return to previous setup": nothing is written, so the owner's own setup is never fought. */
    HANDS_OFF,
}

/** Why the engine wrote something; one per action row (SPEC 8.1 "reason"). */
public enum class Cause {
    START,
    PROFILE_SWITCH,
    SCREEN_OFF,
    SCREEN_ON,

    /** The keyguard was dismissed after a screen on that "until unlock" kept forced idle through (ADR 0017). */
    UNLOCK,
    HOLD_OFF,
    PRIVILEGE,
    DRIFT,
    RESTORE_STOCK,
    RETURN_TO_PREVIOUS,
    STOCK_CAPTURE,
    OWNER,

    /** The quick proof's writes and their undo (SPEC 8.6). */
    QUICK_PROOF,
}

/** Why the plan's profile changed (SPEC 6.5). */
public enum class SwitchReason { SCHEDULE, OWNER, TASKER, RECOMMENDATION, RESTORED }
