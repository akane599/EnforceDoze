// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.evidence

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.data.EvidenceRows
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SessionRow
import org.junit.Assert.assertThrows
import org.junit.Test

class EvidenceContractTest {
    private fun session(id: Long, start: Long, end: Long, verdict: String?, deepShare: Double = 0.0, plugged: Boolean = false) = SessionRow(
        id = id,
        start = start,
        end = end,
        profileId = "everyday",
        pluggedAtStart = plugged,
        verdict = verdict,
        cause = null,
        expectedToDeepMillis = null,
        timeToDeepMillis = null,
        lateByMillis = null,
        deepShare = deepShare,
        maintenanceWindows = 0,
        motionResets = 0,
        breaks = "",
        note = null,
        drainPerHour = 0.4,
    )

    private fun night(sessionId: Long, start: Long, end: Long) = NightRow(
        id = 1,
        sessionId = sessionId,
        start = start,
        end = end,
        sessionCount = 2,
        screenOffMillis = end - start,
        drainPerHour = 0.3,
        deepShare = 0.8,
        profileId = "night",
        unlockedAt = end,
        recentNights = 0,
        recentDrainPerHour = null,
        recentDeepShare = null,
        stockNights = 0,
        stockDrainPerHour = null,
        stockDeepShare = null,
    )

    @Test
    fun `the column names are the published contract`() {
        // ADR 0035: columns are only ever added. A change here breaks Coulomb.
        assertThat(EvidenceContract.STATUS_COLUMNS.toList()).containsExactly(
            "api_version", "state", "profile", "profile_name", "report_session_id", "start_ms", "end_ms", "verdict", "deep_share", "drain",
        ).inOrder()
        assertThat(EvidenceContract.SESSION_COLUMNS.toList())
            .containsExactly("_id", "start_ms", "end_ms", "verdict", "deep_share", "drain", "profile", "plugged").inOrder()
        assertThat(EvidenceContract.CLIENTS).containsExactly("io.github.akane599.coulomb")
    }

    @Test
    fun `a session never judged has no deep share, not zero`() {
        val row = EvidenceContract.sessionRow(session(7, 1_000, 2_000, verdict = null, deepShare = 0.0))
        assertThat(row.toList()).containsExactly(7L, 1_000L, 2_000L, null, null, 0.4, "everyday", 0).inOrder()
        val held = EvidenceContract.sessionRow(session(8, 1_000, 2_000, EvidenceRows.HELD, deepShare = 0.9, plugged = true))
        assertThat(held[4]).isEqualTo(0.9)
        assertThat(held[7]).isEqualTo(1)
    }

    @Test
    fun `the last result is the night around the last session, with the night's verdict`() {
        val first = session(10, 0, 3_600_000, EvidenceRows.HELD, 0.9)
        val second = session(11, 3_700_000, 20_000_000, EvidenceRows.DID_NOT_DOZE)
        val last = LastResults.of(second, night(10, 0, 20_000_000), listOf(first, second))
        assertThat(last).isEqualTo(LastResult(10, 0, 20_000_000, EvidenceRows.PARTLY, 0.8, 0.3, "night"))
        val status = EvidenceContract.statusRow("MANAGED", "night", "Night", last)
        assertThat(status.toList()).containsExactly(1, "MANAGED", "night", "Night", 10L, 0L, 20_000_000L, "PARTLY", 0.8, 0.3).inOrder()
    }

    @Test
    fun `a lone session unjudged is the last result with no share`() {
        val last = LastResults.of(session(12, 5, 9, verdict = null, deepShare = 0.0), night = null, inside = emptyList())
        assertThat(last.deepShare).isNull()
        assertThat(last.sessionId).isEqualTo(12)
        assertThat(EvidenceContract.statusRow(EvidenceContract.OFF, null, null, null).toList())
            .containsExactly(1, "OFF", null, null, null, null, null, null, null, null).inOrder()
    }

    @Test
    fun `a projection picks columns and refuses unknown ones`() {
        val picked = EvidenceContract.project(EvidenceContract.SESSION_COLUMNS, arrayOf("verdict", "_id"))
        assertThat(picked.toList()).containsExactly(3, 0).inOrder()
        assertThat(EvidenceContract.project(EvidenceContract.SESSION_COLUMNS, null).size).isEqualTo(8)
        assertThrows(IllegalArgumentException::class.java) { EvidenceContract.project(EvidenceContract.SESSION_COLUMNS, arrayOf("note")) }
    }
}
