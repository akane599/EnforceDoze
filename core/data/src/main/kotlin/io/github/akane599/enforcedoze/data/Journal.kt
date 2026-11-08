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
import kotlinx.serialization.Serializable

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
    /** `HOLD_OFF_STARTED`, `HOLD_OFF_ENDED`, `DRIFT`, `LOOP_GUARD`, `MODE`, `PROFILE`, `BATTERY`. */
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

/**
 * One screen-off session and its SPEC 8.4 verdict (SPEC 8.1 "Session"). Its transitions, wakers and
 * sensor snapshots are in their own tables ([SessionDao]); the schema is not shipped before 1.0.0, so
 * it still changes in place.
 */
@Entity(tableName = "session", indices = [Index("start")])
@Serializable
public data class SessionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Screen off, epoch milliseconds. */
    val start: Long,
    /** Screen on. */
    val end: Long,
    val profileId: String?,
    val pluggedAtStart: Boolean,
    /** `HELD`, `PARTLY` or `DID_NOT_DOZE`; null when the session was too short or not judged ([note] says why). */
    val verdict: String?,
    /** The likely cause of `DID_NOT_DOZE`. */
    val cause: String?,
    val expectedToDeepMillis: Long?,
    val timeToDeepMillis: Long?,
    /** How late deep idle came past the allowance, for `PARTLY`. */
    val lateByMillis: Long?,
    val deepShare: Double,
    val maintenanceWindows: Int,
    val motionResets: Int,
    /** One line per break: epoch milliseconds, the state entered, the logged reason and the cause. */
    val breaks: String,
    /** Why something is missing or unjudged, one reason per line. */
    val note: String?,
    val timeInDeepMillis: Long = 0,
    /** Battery level in percent at screen off and screen on (`BatteryManager`, ADR 0018). */
    val batteryStart: Int? = null,
    val batteryEnd: Int? = null,
    /** The charge counter in µAh, when the device reports one. */
    val chargeStartUah: Long? = null,
    val chargeEndUah: Long? = null,
    /** Null when the phone was charging during the session or a reading is missing. */
    val drainPerHour: Double? = null,
    val drainMah: Double? = null,
    /** The app's own wakeups; null when a source was missing (ADR 0019), never a guessed 0. */
    val ownWakeups: Int? = null,
    /** The app's own runs, one per line: kind, start, end, `idle` or `outside`, tag. */
    val ownRuns: String = "",
    /** Both alarm reads were taken, so alarm wakeups are known. */
    val alarmsRead: Boolean = false,
    val mobileRadioMillis: Long = 0,
    val unattributedWakelockMillis: Long = 0,
    /** How long the screen-on worker batch took (SPEC 8.2: under 2 s). */
    val collectionMillis: Long? = null,
    /** Where the event log's and the history's coverage of the session begins, epoch milliseconds. */
    val eventLogFrom: Long? = null,
    val historyFrom: Long? = null,
    /** Stretches the history filled and stretches nothing covered, one `start end` pair per line. */
    val historyFilled: String = "",
    val gaps: String = "",
    /** Time on the charger within the session; a night needs none (SPEC 8.5). */
    val chargedMillis: Long = 0,
    /** The deep timeline the verdict saw, one `STATE start end` per line (SPEC 8.5 "Timeline strip"). */
    val timeline: String = "",
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

/** The evidence database (SPEC 8.3). */
@Database(
    entities = [
        ActionRow::class,
        EventRow::class,
        SessionRow::class,
        TransitionRow::class,
        WakerRow::class,
        SensorSnapshotRow::class,
        NightRow::class,
    ],
    version = 1,
    exportSchema = true,
)
public abstract class EvidenceDatabase : RoomDatabase() {
    public abstract fun journal(): JournalDao

    public abstract fun sessions(): SessionDao

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
    public const val HOLD_OFF_STARTED: String = "HOLD_OFF_STARTED"
    public const val HOLD_OFF_ENDED: String = "HOLD_OFF_ENDED"
    public const val DRIFT: String = "DRIFT"
    public const val LOOP_GUARD: String = "LOOP_GUARD"
    public const val MODE: String = "MODE"
    public const val PROFILE: String = "PROFILE"
    public const val PROFILE_MISSING: String = "PROFILE_MISSING"
    public const val BATTERY: String = "BATTERY"

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
            is EngineEvent.HoldOffStarted -> EventRow(time = time, kind = HOLD_OFF_STARTED, holdOff = event.holdOff.name)

            is EngineEvent.HoldOffEnded -> EventRow(time = time, kind = HOLD_OFF_ENDED, holdOff = event.holdOff.name)

            is EngineEvent.Drift -> EventRow(
                time = time,
                kind = DRIFT,
                lever = event.lever.name,
                expected = event.expected,
                found = event.found,
                writer = event.writer,
                whileStopped = event.whileStopped,
            )

            is EngineEvent.LoopGuardTripped -> EventRow(time = time, kind = LOOP_GUARD, lever = event.lever.name)

            is EngineEvent.ModeChanged -> EventRow(time = time, kind = MODE, mode = event.mode.name)

            is EngineEvent.ProfileSwitched -> EventRow(
                time = time,
                kind = PROFILE,
                profileId = event.profileId,
                reason = event.reason.name,
            )

            is EngineEvent.ProfileMissing -> EventRow(
                time = time,
                kind = PROFILE_MISSING,
                profileId = event.profileId,
                reason = event.reason.name,
            )

            // The quick proof's battery freeze (SPEC 8.6): what was asked, and what the device answered.
            is EngineEvent.Battery -> EventRow(
                time = time,
                kind = BATTERY,
                expected = if (event.unplugged) "unplugged" else "reset",
                found = event.line,
                reason = if (event.ok) "OK" else "FAILED",
            )
        }
    }
}
