// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ActionRecord
import io.github.akane599.enforcedoze.doze.Cause
import io.github.akane599.enforcedoze.doze.EngineEvent
import io.github.akane599.enforcedoze.doze.HoldOff
import io.github.akane599.enforcedoze.doze.Lever
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class JournalDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        EvidenceDatabase::class.java,
    ).build()
    private val journal = RoomEngineJournal(db.journal())
    private val time = Instant.parse("2026-11-08T22:00:00Z")

    @After
    fun close() = db.close()

    @Test
    fun actionsAndEventsComeBackInOrderAndRetentionCutsTheOldOnes() = runTest {
        journal.action(ActionRecord(time, Lever.CONSTANTS, "put", "a=1", "a=1", ActionRecord.Outcome.OK, Cause.START))
        journal.action(
            ActionRecord(time.plusSeconds(60), Lever.FORCE_IDLE, "force", "forced", "forced", ActionRecord.Outcome.OK, Cause.SCREEN_OFF),
        )
        journal.event(EngineEvent.HoldOffStarted(time.plusSeconds(120), HoldOff.CALL))

        val dao = db.journal()
        assertThat(dao.actionsSince(0).map { it.lever }).containsExactly("CONSTANTS", "FORCE_IDLE").inOrder()
        assertThat(dao.latestActions(1).first().single().lever).isEqualTo("FORCE_IDLE")
        assertThat(dao.eventsSince(time.toEpochMilli()).single().holdOff).isEqualTo("CALL")

        assertThat(dao.deleteActionsBefore(time.plusSeconds(30).toEpochMilli())).isEqualTo(1)
        assertThat(dao.actionsSince(0)).hasSize(1)
    }

    @Test
    fun sessionsComeBackNewestFirstAndRetentionGoesByTheirEnd() = runTest {
        val dao = db.journal()
        val start = time.toEpochMilli()
        fun session(offsetMinutes: Long, verdict: String?) = SessionRow(
            start = start + offsetMinutes * 60_000,
            end = start + (offsetMinutes + 30) * 60_000,
            profileId = "everyday",
            pluggedAtStart = false,
            verdict = verdict,
            cause = null,
            expectedToDeepMillis = 900_000,
            timeToDeepMillis = 960_000,
            lateByMillis = null,
            deepShare = 0.9,
            maintenanceWindows = 2,
            motionResets = 0,
            breaks = "",
            note = null,
        )
        val first = dao.insert(session(0, "HELD"))
        val second = dao.insert(session(60, null))
        assertThat(second).isGreaterThan(first)
        assertThat(dao.latestSessions(10).map { it.id }).containsExactly(second, first).inOrder()
        assertThat(dao.latestSessions(1).single().verdict).isNull()
        assertThat(dao.latestSessions(10).last()).isEqualTo(session(0, "HELD").copy(id = first))

        // The first session ends 30 minutes in: a cut-off at 31 minutes removes it, not the second.
        assertThat(dao.deleteSessionsBefore(start + 31 * 60_000)).isEqualTo(1)
        assertThat(dao.latestSessions(10).single().id).isEqualTo(second)
    }
}
