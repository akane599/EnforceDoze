// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.design.StripSegment
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
    ) = LogEntry(
        id++,
        start,
        start + minutes * 60_000,
        verdict,
        drain,
        plugged,
        profile,
        night = sessions > 1 || minutes >= 240,
        sessions,
        strip = strip(start, minutes, verdict, plugged),
    )

    /** An illustrative shape per verdict: awake, then light, then deep with maintenance windows; a charger run stays awake. */
    private fun strip(start: Long, minutes: Long, verdict: VerdictWord, plugged: Boolean): StripModel {
        val end = start + minutes * 60_000
        fun at(share: Double) = start + (minutes * 60_000 * share).toLong()
        val segments = when {
            plugged || verdict == VerdictWord.DID_NOT_DOZE -> listOf(StripSegment(DozeFill.ACTIVE, start, end))

            verdict == VerdictWord.NOT_JUDGED -> listOf(
                StripSegment(DozeFill.ACTIVE, start, at(0.6)),
                StripSegment(DozeFill.LIGHT, at(0.6), end),
            )

            verdict == VerdictWord.PARTLY -> listOf(
                StripSegment(DozeFill.ACTIVE, start, at(0.12)),
                StripSegment(DozeFill.LIGHT, at(0.12), at(0.35)),
                StripSegment(DozeFill.DEEP, at(0.35), at(0.58)),
                StripSegment(DozeFill.ACTIVE, at(0.58), at(0.66)),
                StripSegment(DozeFill.LIGHT, at(0.66), end),
            )

            else -> listOf(
                StripSegment(DozeFill.ACTIVE, start, at(0.06)),
                StripSegment(DozeFill.LIGHT, at(0.06), at(0.14)),
                StripSegment(DozeFill.DEEP, at(0.14), end),
            )
        }
        val windows = if (verdict == VerdictWord.HELD && minutes >= 120) listOf(at(0.4), at(0.7)) else emptyList()
        return StripModel(start, end, segments, maintenance = windows)
    }

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
