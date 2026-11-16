// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

/** The SPEC 11.1 actions; [changesState] actions share the rate limit. */
internal enum class AutomationAction(val changesState: Boolean) {
    APPLY_PROFILE(true),
    APPLY_PLAN(true),
    PAUSE(true),
    RESUME(true),
    RESTORE_STOCK(true),
    SENSORS_OFF(true),
    GET_STATUS(false),
    ;

    val intentAction: String get() = PREFIX + name

    companion object {
        const val PREFIX = "io.github.akane599.enforcedoze.action."

        fun of(intentAction: String?): AutomationAction? = entries.firstOrNull { it.intentAction == intentAction }
    }
}

/** Why a call was dropped before it reached the engine or the worker (ADR 0033). */
internal enum class Drop {
    /** No client has this token; no reply. */
    BAD_TOKEN,

    /** The sender shared its identity and it is not the package the token is bound to. */
    SENDER_MISMATCH,

    /** A missing or unusable extra, such as an unknown profile id: it does not use up the rate limit. */
    INVALID,

    /** A state change within [AutomationGate.MIN_GAP_MILLIS] of the last one. */
    RATE_LIMITED,
}

/** What becomes of one automation call. */
internal sealed interface Admission {
    /** Run it for [client]. */
    data class Run(val client: String) : Admission

    /**
     * Dropped; [client] is null for a bad token and [detail] says more. [log] is false when the same drop for the same
     * client was logged within [AutomationGate.LOG_GAP_MILLIS], so a flood of calls writes one row a minute.
     */
    data class Dropped(val client: String?, val drop: Drop, val detail: String? = null, val log: Boolean = true) : Admission
}

/**
 * Decides each call before anything reaches the engine or the worker. Times are a monotonic clock
 * (`SystemClock.elapsedRealtime`), so a wall clock set back cannot hold the limit shut. Thread-safe.
 */
internal class AutomationGate {
    private var lastChange: Long? = null
    private val lastLog = HashMap<String, Long>()

    @Synchronized
    fun admit(action: AutomationAction, client: String?, sender: String?, problem: String?, now: Long): Admission {
        if (client == null) return dropped(null, Drop.BAD_TOKEN, null, now)
        if (sender != null && sender != client) return dropped(client, Drop.SENDER_MISMATCH, sender, now)
        if (problem != null) return dropped(client, Drop.INVALID, problem, now)
        if (!action.changesState) return Admission.Run(client)
        val last = lastChange
        if (last != null && now - last < MIN_GAP_MILLIS) return dropped(client, Drop.RATE_LIMITED, null, now)
        lastChange = now
        return Admission.Run(client)
    }

    private fun dropped(client: String?, drop: Drop, detail: String?, now: Long): Admission.Dropped {
        val key = "$drop:$client"
        val log = lastLog[key]?.let { now - it >= LOG_GAP_MILLIS } ?: true
        if (log) lastLog[key] = now
        return Admission.Dropped(client, drop, detail, log)
    }

    companion object {
        /** SPEC 11.1: at most one profile switch per 30 s from automation; ADR 0033 extends it to every state change. */
        const val MIN_GAP_MILLIS = 30_000L
        const val LOG_GAP_MILLIS = 60_000L
    }
}
