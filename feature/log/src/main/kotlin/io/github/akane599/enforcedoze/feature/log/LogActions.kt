// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import io.github.akane599.enforcedoze.ui.StateNotice
import java.time.LocalDate

/** What the log does on the owner's taps. */
public interface LogActions {
    public fun open(entry: LogEntry)

    public fun filter(filter: LogFilter)

    public fun export()

    public fun notice(notice: StateNotice)

    public fun seen(notice: StateNotice)
}

/** Where the log sends the owner; the app's navigation implements it (ADR 0029). */
public interface LogNavigation {
    /** The session's report, or the night's when [LogEntry.night]. */
    public fun open(sessionId: Long)

    /** The system's document picker for an evidence export of [from] to [to], both days included (SPEC 8.7). */
    public fun export(from: LocalDate, to: LocalDate)

    public fun notice(notice: StateNotice)
}
