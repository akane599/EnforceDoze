// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import io.github.akane599.enforcedoze.ui.NoticeKind
import io.github.akane599.enforcedoze.ui.StateNotice
import io.github.akane599.enforcedoze.ui.VerdictWord
import java.time.ZoneId
import java.time.ZonedDateTime

/** Three days of a log, illustrative only: nights, glances, a charger evening and a mixed night. */
object LogFixtures {
    private val zone = ZoneId.of("Europe/Istanbul")
    private var id = 100L

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun entry(
        start: Long,
        minutes: Long,
        verdict: VerdictWord,
        drain: Double?,
        profile: String? = "everyday",
        plugged: Boolean = false,
        sessions: Int = 1,
    ) = LogEntry(id++, start, start + minutes * 60_000, verdict, drain, plugged, profile, night = sessions > 1 || minutes >= 240, sessions)

    val state = LogState(
        entries = listOf(
            entry(at(16, 7, 52), 18, VerdictWord.NOT_JUDGED, null),
            entry(at(15, 23, 10), 485, VerdictWord.HELD, 0.31, profile = "night", sessions = 3),
            entry(at(15, 19, 5), 140, VerdictWord.DID_NOT_DOZE, null, plugged = true),
            entry(at(15, 14, 20), 55, VerdictWord.PARTLY, 0.84),
            entry(at(15, 9, 40), 32, VerdictWord.HELD, 0.52),
            entry(at(14, 23, 45), 430, VerdictWord.PARTLY, 0.47, profile = null, sessions = 2),
            entry(at(14, 16, 0), 41, VerdictWord.HELD, 0.6, profile = "pocket"),
        ).sortedByDescending { it.start },
        profileNames = mapOf("everyday" to "Everyday", "night" to "Night", "pocket" to "Pocket"),
    )

    val readOnly = state.copy(notices = listOf(StateNotice("ro", NoticeKind.READ_ONLY)))

    val empty = LogState(entries = emptyList())
}
