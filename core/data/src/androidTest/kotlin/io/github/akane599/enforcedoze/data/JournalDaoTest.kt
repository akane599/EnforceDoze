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
    fun sessionsComeBackNewestFirstWithTheirRowsAndRetentionGoesByTheirEnd() = runTest {
        val dao = db.sessions()
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
            batteryStart = 90,
            ownWakeups = 0,
        )
        fun record(offsetMinutes: Long, verdict: String?) = SessionRecord(
            session = session(offsetMinutes, verdict),
            transitions = listOf(
                TransitionRow(
                    time = start + 2,
                    deep = true,
                    fromState = "INACTIVE",
                    toState = "IDLE",
                    reason = "s:alarm",
                    source = "EVENT_LOG",
                ),
                TransitionRow(
                    time = start + 1,
                    deep = true,
                    fromState = "ACTIVE",
                    toState = "INACTIVE",
                    reason = "no activity",
                    source = "EVENT_LOG",
                ),
            ),
            wakers = listOf(
                WakerRow(uid = 10_050, name = "com.x", alarmWakeups = 3),
                WakerRow(uid = null, name = "170 qcom,smd-modem", kernelWakeups = 2),
            ),
            snapshots = listOf(SensorSnapshotRow(time = start, moment = "SCREEN_OFF", mode = "NORMAL", privacy = false, connections = "")),
        )
        val first = dao.record(record(0, "HELD"))
        val second = dao.record(record(60, null))
        assertThat(second).isGreaterThan(first)
        assertThat(dao.latestSessions(10).map { it.id }).containsExactly(second, first).inOrder()
        assertThat(dao.latestSessions(1).single().verdict).isNull()
        assertThat(dao.latestSessions(10).last()).isEqualTo(session(0, "HELD").copy(id = first))
        assertThat(dao.transitions(first).map { it.toState }).containsExactly("INACTIVE", "IDLE").inOrder()
        assertThat(dao.transitions(first).map { it.sessionId }.distinct()).containsExactly(first)
        assertThat(dao.wakers(first).map { it.name }).containsExactly("com.x", "170 qcom,smd-modem").inOrder()
        assertThat(dao.snapshots(second).single().moment).isEqualTo("SCREEN_OFF")

        // The first session ends 30 minutes in: a cut-off at 31 minutes removes it and its rows, not the second.
        assertThat(dao.deleteSessionsBefore(start + 31 * 60_000)).isEqualTo(1)
        assertThat(dao.latestSessions(10).single().id).isEqualTo(second)
        assertThat(dao.transitions(first)).isEmpty()
        assertThat(dao.wakers(first)).isEmpty()
        assertThat(dao.snapshots(first)).isEmpty()
        assertThat(dao.wakers(second)).hasSize(2)
    }

    @Test
    fun nightsAreFoundOnceAndComparedWithEarlierOnes() = runTest {
        val dao = db.sessions()
        val hour = 3_600_000L
        val start = time.toEpochMilli()
        fun night(day: Long, hours: Long, profileId: String = "everyday", chargedMillis: Long = 0) = SessionRecord(
            session = SessionRow(
                start = start + day * 24 * hour,
                end = start + day * 24 * hour + hours * hour,
                profileId = profileId,
                pluggedAtStart = false,
                verdict = "HELD",
                cause = null,
                expectedToDeepMillis = 600_000,
                timeToDeepMillis = 700_000,
                lateByMillis = null,
                deepShare = 0.9,
                maintenanceWindows = 5,
                motionResets = 0,
                breaks = "",
                note = null,
                chargedMillis = chargedMillis,
            ),
            transitions = emptyList(),
            wakers = emptyList(),
            snapshots = emptyList(),
        )
        val stock = dao.record(night(0, 8, profileId = "stock"))
        val everyday = dao.record(night(1, 8))
        dao.record(night(2, 3))
        dao.record(night(3, 8, chargedMillis = 60_000))
        val unreported = dao.unreportedNights(sinceMillis = 0, minMillis = 4 * hour)
        assertThat(unreported.map { it.id }).containsExactly(stock, everyday).inOrder()

        dao.insertNight(
            NightRow(
                sessionId = stock,
                unlockedAt = start + 9 * hour,
                recentNights = 0,
                recentDrainPerHour = null,
                recentDeepShare = null,
                stockNights = 0,
                stockDrainPerHour = null,
                stockDeepShare = null,
            ),
        )
        dao.insertNight(
            NightRow(
                sessionId = everyday,
                unlockedAt = start + 33 * hour,
                recentNights = 1,
                recentDrainPerHour = null,
                recentDeepShare = null,
                stockNights = 1,
                stockDrainPerHour = null,
                stockDeepShare = null,
            ),
        )
        assertThat(dao.unreportedNights(sinceMillis = 0, minMillis = 4 * hour)).isEmpty()
        assertThat(
            dao.nightSessions(beforeMillis = Long.MAX_VALUE, profileId = null, limit = 7).map {
                it.id
            },
        ).containsExactly(everyday, stock).inOrder()
        assertThat(dao.nightSessions(beforeMillis = Long.MAX_VALUE, profileId = "stock", limit = 7).map { it.id }).containsExactly(stock)
        assertThat(dao.latestNights(1).single().sessionId).isEqualTo(everyday)
        assertThat(dao.recordOf(everyday)?.session?.id).isEqualTo(everyday)
        assertThat(dao.recordOf(-1)).isNull()

        // Retention removes the night with its session.
        dao.deleteSessionsBefore(start + 9 * hour)
        assertThat(dao.latestNights(10).map { it.sessionId }).containsExactly(everyday)
    }
}
