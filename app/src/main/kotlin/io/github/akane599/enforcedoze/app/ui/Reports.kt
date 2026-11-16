// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.report.ReportState

/** A session's report, or its night's when the session is part of one (SPEC 8.4, 8.5); null when it is gone. */
internal fun interface Reports {
    suspend fun report(sessionId: Long): ReportState?
}
