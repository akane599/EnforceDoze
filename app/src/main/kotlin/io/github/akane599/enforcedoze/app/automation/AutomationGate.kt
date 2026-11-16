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

/** What becomes of one automation call (ADR 0033). */
internal sealed interface Admission {
    /** Run it for [client]. */
    data class Run(val client: String) : Admission

    /** Logged and dropped: a state change within [AutomationGate.MIN_GAP_MILLIS] of the last one. */
    data class RateLimited(val client: String) : Admission

    /** The sender shared its identity and it is not the package the token is bound to. */
    data class Mismatch(val client: String, val sender: String) : Admission

    /** No such token: dropped with no reply; [log] is true at most once a minute. */
    data class BadToken(val log: Boolean) : Admission
}

/** Decides each call before anything reaches the engine or the worker. Thread-safe. */
internal class AutomationGate {
    private var lastChange: Long? = null
    private var lastBadTokenLog: Long? = null

    @Synchronized
    fun admit(action: AutomationAction, client: String?, sender: String?, now: Long): Admission {
        if (client == null) {
            val log = lastBadTokenLog?.let { now - it >= BAD_TOKEN_LOG_GAP_MILLIS } ?: true
            if (log) lastBadTokenLog = now
            return Admission.BadToken(log)
        }
        if (sender != null && sender != client) return Admission.Mismatch(client, sender)
        if (!action.changesState) return Admission.Run(client)
        val last = lastChange
        if (last != null && now - last < MIN_GAP_MILLIS) return Admission.RateLimited(client)
        lastChange = now
        return Admission.Run(client)
    }

    companion object {
        /** SPEC 11.1: at most one profile switch per 30 s from automation; ADR 0033 extends it to every state change. */
        const val MIN_GAP_MILLIS = 30_000L
        const val BAD_TOKEN_LOG_GAP_MILLIS = 60_000L
    }
}
