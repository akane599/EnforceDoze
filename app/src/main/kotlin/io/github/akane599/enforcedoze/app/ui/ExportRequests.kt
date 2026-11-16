// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.ui.ActionOutcome
import java.time.LocalDate

/** What the screens ask the shell to write: the system's document picker, then the file (SPEC 8.7, 6.3). */
internal interface ExportRequests {
    fun days(from: LocalDate, to: LocalDate)

    fun all()

    fun profile(profileId: String)

    fun outcome(outcome: ActionOutcome)
}
