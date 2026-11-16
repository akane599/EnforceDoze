// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.evidence

import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.data.StoredNights

/**
 * The evidence provider's public contract, version 1 (ADR 0035): column names and how each row is filled. Columns
 * are only ever added; none is renamed, retyped or removed. Internal row fields never leak through `SELECT *`.
 */
internal object EvidenceContract {
    const val AUTHORITY = "io.github.akane599.enforcedoze.evidence"
    const val STATUS_PATH = "status"
    const val SESSIONS_PATH = "sessions"
    const val API_VERSION = 1

    /** The engine is switched off: nothing is managed, whatever the saved mode says. */
    const val OFF = "OFF"

    /** The only callers in release builds (SPEC 11.3). */
    val CLIENTS: Set<String> = setOf("io.github.akane599.coulomb")

    val STATUS_COLUMNS: Array<String> = arrayOf(
        "api_version",
        "state",
        "profile",
        "profile_name",
        "report_session_id",
        "start_ms",
        "end_ms",
        "verdict",
        "deep_share",
        "drain",
    )

    val SESSION_COLUMNS: Array<String> = arrayOf("_id", "start_ms", "end_ms", "verdict", "deep_share", "drain", "profile", "plugged")

    fun statusRow(state: String, profileId: String?, profileName: String?, last: LastResult?): Array<Any?> = arrayOf(
        API_VERSION,
        state,
        profileId,
        profileName,
        last?.sessionId,
        last?.start,
        last?.end,
        last?.verdict,
        last?.deepShare,
        last?.drainPerHour,
    )

    /** A session as the Log lists it: plugged when the charger was in at any point (StoredNights.unplugged). */
    fun sessionRow(row: SessionRow): Array<Any?> = arrayOf(
        row.id,
        row.start,
        row.end,
        row.verdict,
        row.deepShare.takeIf { row.verdict != null },
        row.drainPerHour,
        row.profileId,
        if (StoredNights.unplugged(row)) 0 else 1,
    )

    /** The indices of [projection] in [columns]; all columns for null. An unknown column is the caller's mistake. */
    fun project(columns: Array<String>, projection: Array<String>?): IntArray {
        projection ?: return columns.indices.toList().toIntArray()
        return projection.map { name ->
            columns.indexOf(name).also { require(it >= 0) { "unknown column $name; columns: ${columns.joinToString()}" } }
        }.toIntArray()
    }
}
