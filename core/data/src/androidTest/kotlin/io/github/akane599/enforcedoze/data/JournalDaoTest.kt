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
    fun nightsAreStoredGrownAndComparedWithEarlierOnes() = runTest {
        val dao = db.sessions()
        val hour = 3_600_000L
        val start = time.toEpochMilli()
        fun session(from: Long, hours: Long, profileId: String = "everyday") = SessionRecord(
            session = SessionRow(
                start = start + from,
                end = start + from + hours * hour,
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
            ),
            transitions = emptyList(),
            wakers = emptyList(),
            snapshots = emptyList(),
        )
        val stock = dao.record(session(0, 8, profileId = "stock"))
        val first = dao.record(session(24 * hour, 5))
        val second = dao.record(session(29 * hour + 300_000, 3))
        val rows = dao.sessionsBetween(start, start + 40 * hour)
        val stockNight = NightReports.night(rows.filter { it.id == stock }, start + 9 * hour, emptyList(), emptyList())
        dao.insertNight(stockNight)
        assertThat(dao.nightsBefore(start + 24 * hour, "stock", 7).map { it.sessionId }).containsExactly(stock)

        // Written at the first unlock with the first session, then grown when the second joined after a wake-up.
        val recent = dao.nightsBefore(start + 24 * hour, null, 7)
        val id = dao.insertNight(NightReports.night(rows.filter { it.id == first }, start + 29 * hour, recent, recent))
        dao.updateNight(
            NightReports.night(
                rows.filter {
                    it.id == first || it.id == second
                },
                start + 33 * hour,
                recent,
                recent,
            ).copy(id = id),
        )
        val night = dao.night(id) ?: error("no night $id")
        assertThat(night.sessionCount).isEqualTo(2)
        assertThat(night.end).isEqualTo(start + 32 * hour + 300_000)
        assertThat(night.recentNights).isEqualTo(1)
        assertThat(dao.recordsOf(night).map { it.session.id }).containsExactly(first, second).inOrder()
        assertThat(dao.nightAround(start + 29 * hour + 300_000, start + 32 * hour + 300_000)?.id).isEqualTo(id)
        assertThat(dao.nightsBetween(start + 30 * hour, start + 31 * hour).map { it.id }).containsExactly(id)
        assertThat(dao.nightsBefore(Long.MAX_VALUE, null, 7).map { it.sessionId }).containsExactly(first, stock).inOrder()
        assertThat(dao.latestNights(1).single().id).isEqualTo(id)
        assertThat(dao.recordOf(-1)).isNull()

        // A longer rest near it takes its place (ADR 0032); the replaced night's sessions stay.
        val longer = dao.record(session(32 * hour + 600_000, 9))
        val replacement = NightReports.night(
            dao.sessionsBetween(start + 32 * hour, start + 42 * hour).filter {
                it.id == longer
            },
            0,
            recent,
            recent,
        )
        val newId = dao.replaceNights(listOf(night), replacement)
        assertThat(dao.night(id)).isNull()
        assertThat(dao.night(newId)?.sessionId).isEqualTo(longer)
        assertThat(dao.session(second)).isNotNull()

        // Retention removes the night with its first session.
        dao.deleteSessionsBefore(start + 9 * hour)
        assertThat(dao.latestNights(10).map { it.sessionId }).containsExactly(longer)
    }
}
