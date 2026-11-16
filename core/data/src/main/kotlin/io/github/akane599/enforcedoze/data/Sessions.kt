// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A Doze change inside a session (SPEC 8.1 "Doze transition"). */
@Entity(
    tableName = "transition",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("time")],
)
@Serializable
public data class TransitionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    val time: Long,
    val deep: Boolean,
    val fromState: String?,
    val toState: String,
    val reason: String,
    /** `EVENT_LOG` or `HISTORY` (ADR 0018). */
    val source: String,
)

/**
 * One waker of a session (SPEC 8.1 "Waker"): an app, with [uid] set, or a kernel wakeup reason, with
 * [uid] null, [name] the reason and [kernelWakeups] its count.
 */
@Entity(
    tableName = "waker",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
@Serializable
public data class WakerRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    val uid: Int?,
    /** The app's packages from the alarm dump, comma-separated, or the kernel reason. */
    val name: String,
    /** The app is EnforceDoze itself (SPEC 8.2 self-audit). */
    val self: Boolean = false,
    val alarmWakeups: Int = 0,
    val networkWakeups: Int = 0,
    val kernelWakeups: Int = 0,
    val jobs: Int = 0,
    val jobMillis: Long = 0,
    val syncs: Int = 0,
    val syncMillis: Long = 0,
    val wakelockMillis: Long = 0,
    val longWakelocks: Int = 0,
    val longWakelockMillis: Long = 0,
    /** Alarm tags delivered in the session, one per line: wakeups, alarms, tag. */
    val alarms: String = "",
)

/** The sensor service at one moment (SPEC 8.1 "Sensor snapshot"). */
@Entity(
    tableName = "sensor_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
@Serializable
public data class SensorSnapshotRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    val time: Long,
    /** `SCREEN_OFF`, `COPY` (a copy at a deep maintenance window, ADR 0020) or `SCREEN_ON`: [SessionAssembly]. */
    val moment: String,
    /** The mode line's mode, such as `NORMAL` or `RESTRICTED`. */
    val mode: String?,
    val privacy: Boolean?,
    /** Open connections, one per line: uid, listener, sensor, sampling period in µs or `-`, tab-separated. */
    val connections: String,
)

/** A session with its child rows, stored in one transaction. */
public data class SessionRecord(
    val session: SessionRow,
    val transitions: List<TransitionRow>,
    val wakers: List<WakerRow>,
    val snapshots: List<SensorSnapshotRow>,
)

@Dao
public abstract class SessionDao {
    @Insert
    protected abstract suspend fun insertSession(row: SessionRow): Long

    @Insert
    protected abstract suspend fun insertTransitions(rows: List<TransitionRow>)

    @Insert
    protected abstract suspend fun insertWakers(rows: List<WakerRow>)

    @Insert
    protected abstract suspend fun insertSnapshots(rows: List<SensorSnapshotRow>)

    /** Stores [record] and returns the session's id. */
    @Transaction
    public open suspend fun record(record: SessionRecord): Long {
        val id = insertSession(record.session)
        insertTransitions(record.transitions.map { it.copy(sessionId = id) })
        insertWakers(record.wakers.map { it.copy(sessionId = id) })
        insertSnapshots(record.snapshots.map { it.copy(sessionId = id) })
        return id
    }

    @Query("SELECT * FROM session ORDER BY start DESC, id DESC LIMIT :limit")
    public abstract suspend fun latestSessions(limit: Int): List<SessionRow>

    /**
     * Emits whenever a session or a night is written or removed, so a screen reads its rows again. Room re-runs it
     * on every change to either table; the number itself means nothing.
     */
    @Query("SELECT (SELECT COUNT(*) FROM session) + (SELECT COUNT(*) FROM night)")
    public abstract fun revision(): Flow<Long>

    @Query("SELECT * FROM transition WHERE sessionId = :sessionId ORDER BY time, id")
    public abstract suspend fun transitions(sessionId: Long): List<TransitionRow>

    /** In the order they were stored: apps by rank, then kernel reasons by count. */
    @Query("SELECT * FROM waker WHERE sessionId = :sessionId ORDER BY id")
    public abstract suspend fun wakers(sessionId: Long): List<WakerRow>

    @Query("SELECT * FROM sensor_snapshot WHERE sessionId = :sessionId ORDER BY time, id")
    public abstract suspend fun snapshots(sessionId: Long): List<SensorSnapshotRow>

    @Insert
    public abstract suspend fun insertNight(row: NightRow): Long

    /** A night that a later session joined (ADR 0031): the same row, its range and figures grown. */
    @Update
    public abstract suspend fun updateNight(row: NightRow)

    @Delete
    protected abstract suspend fun deleteNights(rows: List<NightRow>)

    /**
     * A longer rest became the night (ADR 0032): the shorter nights near it, naps after all, are no longer nights.
     * Their sessions keep their own reports. Returns the new night's id.
     */
    @Transaction
    public open suspend fun replaceNights(replaced: List<NightRow>, night: NightRow): Long {
        deleteNights(replaced)
        return insertNight(night)
    }

    /**
     * The nights that started before [beforeMillis], newest first; with [profileId], only nights whose every
     * session ran that profile (SPEC 8.5 comparison).
     */
    @Query(
        "SELECT * FROM night WHERE start < :beforeMillis AND (:profileId IS NULL OR profileId = :profileId) " +
            "ORDER BY start DESC LIMIT :limit",
    )
    public abstract suspend fun nightsBefore(beforeMillis: Long, profileId: String?, limit: Int): List<NightRow>

    @Query("SELECT * FROM night ORDER BY unlockedAt DESC, id DESC LIMIT :limit")
    public abstract suspend fun latestNights(limit: Int): List<NightRow>

    /** Nights that overlap [fromMillis]..[toMillis], oldest first. */
    @Query("SELECT * FROM night WHERE `end` >= :fromMillis AND start <= :toMillis ORDER BY start, id")
    public abstract suspend fun nightsBetween(fromMillis: Long, toMillis: Long): List<NightRow>

    /** The night a session belongs to, by time: its first session or one joined after a short wake-up. */
    @Query("SELECT * FROM night WHERE start <= :start AND `end` >= :end ORDER BY start DESC LIMIT 1")
    public abstract suspend fun nightAround(start: Long, end: Long): NightRow?

    @Query("SELECT * FROM night WHERE id = :id")
    public abstract suspend fun night(id: Long): NightRow?

    /** Sessions that overlap [fromMillis]..[toMillis], oldest first: an export's range (SPEC 8.7). */
    @Query("SELECT * FROM session WHERE `end` >= :fromMillis AND start <= :toMillis ORDER BY start, id")
    public abstract suspend fun sessionsBetween(fromMillis: Long, toMillis: Long): List<SessionRow>

    @Query("SELECT * FROM session WHERE id = :id")
    public abstract suspend fun session(id: Long): SessionRow?

    /** A stored session with its rows, or null when retention removed it. */
    @Transaction
    public open suspend fun recordOf(id: Long): SessionRecord? {
        val session = session(id) ?: return null
        return SessionRecord(session, transitions(id), wakers(id), snapshots(id))
    }

    /** The sessions of [night] with their rows, in start order. */
    @Transaction
    public open suspend fun recordsOf(night: NightRow): List<SessionRecord> =
        sessionsBetween(night.start, night.end).filter { it.start >= night.start && it.end <= night.end }
            .mapNotNull { recordOf(it.id) }

    /** Retention (SPEC 8.3) by the session's end; its child rows go with it. */
    @Query("DELETE FROM session WHERE `end` < :beforeMillis")
    public abstract suspend fun deleteSessionsBefore(beforeMillis: Long): Int
}
