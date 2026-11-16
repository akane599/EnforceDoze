// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

/**
 * Coulomb's link to a report (ADR 0035): the action and extra, taken only through the permission-protected
 * activity alias. `MainActivity` is exported as the launcher, so the same intent sent to it directly is ignored.
 */
internal object ReportLink {
    const val ACTION = "io.github.akane599.enforcedoze.action.OPEN_REPORT"
    const val EXTRA_SESSION_ID = "session_id"

    /** The alias in the manifest; an intent started through it keeps this name as its component. */
    const val ALIAS = "io.github.akane599.enforcedoze.EvidenceReport"

    /** The report for [sessionId], the Log when there is no id; null unless the intent came through the alias. */
    fun route(component: String?, sessionId: Long): Route? = when {
        component != ALIAS -> null
        sessionId >= 0 -> ReportKey(sessionId)
        else -> LogKey
    }
}
