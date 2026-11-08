// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.akane599.enforcedoze.doze.ActionRecord
import io.github.akane599.enforcedoze.doze.EngineEvent
import io.github.akane599.enforcedoze.doze.EngineJournal
import kotlinx.coroutines.flow.Flow

/** One engine write and what its read-back showed (SPEC 8.1 "Action"). */
@Entity(tableName = "action", indices = [Index("time")])
public data class ActionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds. */
    val time: Long,
    val lever: String,
    val command: String,
    val expected: String,
    val readBack: String?,
    val outcome: String,
    val cause: String,
    val detail: String,
)

/** A hold-off, drift, loop-guard, mode or profile event (SPEC 8.1 "Hold-off, drift"); unused columns are null. */
@Entity(tableName = "event", indices = [Index("time")])
public data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    /** `HOLD_OFF_STARTED`, `HOLD_OFF_ENDED`, `DRIFT`, `LOOP_GUARD`, `MODE`, `PROFILE`. */
    val kind: String,
    val lever: String? = null,
    val holdOff: String? = null,
    val expected: String? = null,
    val found: String? = null,
    /** The package that last wrote the global string, when known (ADR 0014). */
    val writer: String? = null,
    val whileStopped: Boolean? = null,
    val mode: String? = null,
    val profileId: String? = null,
    val reason: String? = null,
)

@Dao
public interface JournalDao {
    @Insert
    public suspend fun insert(row: ActionRow)

    @Insert
    public suspend fun insert(row: EventRow)

    @Query("SELECT * FROM action WHERE time >= :sinceMillis ORDER BY time, id")
    public suspend fun actionsSince(sinceMillis: Long): List<ActionRow>

    @Query("SELECT * FROM event WHERE time >= :sinceMillis ORDER BY time, id")
    public suspend fun eventsSince(sinceMillis: Long): List<EventRow>

    @Query("SELECT * FROM action ORDER BY time DESC, id DESC LIMIT :limit")
    public fun latestActions(limit: Int): Flow<List<ActionRow>>

    @Query("SELECT * FROM event ORDER BY time DESC, id DESC LIMIT :limit")
    public fun latestEvents(limit: Int): Flow<List<EventRow>>

    /** Retention (SPEC 8.3): parsed records older than the cut-off go. */
    @Query("DELETE FROM action WHERE time < :beforeMillis")
    public suspend fun deleteActionsBefore(beforeMillis: Long): Int

    @Query("DELETE FROM event WHERE time < :beforeMillis")
    public suspend fun deleteEventsBefore(beforeMillis: Long): Int
}

/** The evidence database (SPEC 8.3). Sessions, transitions, wakers and sensor snapshots join it in P3. */
@Database(entities = [ActionRow::class, EventRow::class], version = 1, exportSchema = true)
public abstract class EvidenceDatabase : RoomDatabase() {
    public abstract fun journal(): JournalDao

    public companion object {
        public const val NAME: String = "evidence.db"

        public fun open(context: Context): EvidenceDatabase =
            Room.databaseBuilder(context.applicationContext, EvidenceDatabase::class.java, NAME).build()
    }
}

/** The engine's journal port over Room. */
public class RoomEngineJournal(private val dao: JournalDao) : EngineJournal {
    override suspend fun action(record: ActionRecord): Unit = dao.insert(JournalRows.of(record))

    override suspend fun event(event: EngineEvent): Unit = dao.insert(JournalRows.of(event))
}

/** Pure mappings from the engine's records to rows. */
public object JournalRows {
    public fun of(record: ActionRecord): ActionRow = ActionRow(
        time = record.time.toEpochMilli(),
        lever = record.lever.name,
        command = record.command,
        expected = record.expected,
        readBack = record.readBack,
        outcome = record.outcome.name,
        cause = record.cause.name,
        detail = record.detail,
    )

    public fun of(event: EngineEvent): EventRow {
        val time = event.time.toEpochMilli()
        return when (event) {
            is EngineEvent.HoldOffStarted -> EventRow(time = time, kind = "HOLD_OFF_STARTED", holdOff = event.holdOff.name)

            is EngineEvent.HoldOffEnded -> EventRow(time = time, kind = "HOLD_OFF_ENDED", holdOff = event.holdOff.name)

            is EngineEvent.Drift -> EventRow(
                time = time,
                kind = "DRIFT",
                lever = event.lever.name,
                expected = event.expected,
                found = event.found,
                writer = event.writer,
                whileStopped = event.whileStopped,
            )

            is EngineEvent.LoopGuardTripped -> EventRow(time = time, kind = "LOOP_GUARD", lever = event.lever.name)

            is EngineEvent.ModeChanged -> EventRow(time = time, kind = "MODE", mode = event.mode.name)

            is EngineEvent.ProfileSwitched -> EventRow(
                time = time,
                kind = "PROFILE",
                profileId = event.profileId,
                reason = event.reason.name,
            )
        }
    }
}
