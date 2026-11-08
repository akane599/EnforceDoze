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
}
