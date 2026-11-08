// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * A night (SPEC 8.5): the first unlock after an unplugged session of at least [NightReports.THRESHOLD].
 * The report itself is built from the session's rows; this row keeps when it was generated and the
 * comparison as it stood then, so later nights do not change it.
 */
@Entity(
    tableName = "night",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId", unique = true), Index("unlockedAt")],
)
@Serializable
public data class NightRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** The unlock that generated the report. */
    val unlockedAt: Long,
    /** The last 7 nights before this one. */
    val recentNights: Int,
    val recentDrainPerHour: Double?,
    val recentDeepShare: Double?,
    /** Nights under the Stock profile: the baseline. */
    val stockNights: Int,
    val stockDrainPerHour: Double?,
    val stockDeepShare: Double?,
)

/** One stretch of the deep timeline (SPEC 8.5 "Timeline strip"); [state] is a `DeviceIdleController` state name. */
public data class TimelineSpan(val state: String, val start: Long, val end: Long)

/** A part of the night no source covered, or one the battery history filled (ADR 0018). */
public data class Span(val start: Long, val end: Long)

/** A sensor an app held open, with the snapshot moments it was seen at (SPEC 8.5 "apps that held sensors"). */
public data class SensorHolder(
    val uid: Int?,
    val listener: String,
    val sensor: String,
    val samplingPeriodUs: Long?,
    val moments: List<String>,
)

/** Mean drain and deep-idle share over some nights; a figure is null when no night had it. */
public data class Comparison(val nights: Int, val drainPerHour: Double?, val deepShare: Double?)

/** Everything the night report shows (SPEC 8.5 items 1–5; item 6 is the screen's buttons). */
public data class NightReport(
    val night: NightRow,
    /** 1. The verdict line's figures: verdict, time in deep idle, maintenance windows, drain. */
    val session: SessionRow,
    /** 2. The timeline strip: deep stretches, light changes, breaks, gaps and history-filled stretches. */
    val timeline: List<TimelineSpan>,
    val light: List<TransitionRow>,
    val breaks: List<String>,
    val gaps: List<Span>,
    val historyFilled: List<Span>,
    /** 3. Compared with the last 7 nights and with Stock nights. */
    val recent: Comparison,
    val stock: Comparison,
    /** 4. Wakers: apps in rank order, kernel wakeup reasons, and apps that held sensors. */
    val apps: List<WakerRow>,
    val kernel: List<WakerRow>,
    val sensors: List<SensorHolder>,
    /** 5. What changed Doze: hold-offs, drift (with the likely writer) and the engine's actions. */
    val holdOffs: List<EventRow>,
    val drift: List<EventRow>,
    val actions: List<ActionRow>,
)

/** Pure parts of the night report, tested on the JVM. */
public object NightReports {
    /** SPEC 8.5: a session this long, unplugged throughout, is a night. Shortened only by the debug build. */
    public val THRESHOLD: Duration = Duration.ofHours(4)

    /** How many earlier nights the comparison takes (SPEC 8.5). */
    public const val RECENT: Int = 7

    /**
     * The engine and the recorder each take the screen-off broadcast on their own, so the engine's screen-off
     * writes can be logged just before the session's start (114 ms on the API 37 emulator). They belong to the night.
     */
    private val SCREEN_SLACK: Duration = Duration.ofSeconds(5)

    /** Where the report's hold-offs, drift and actions start: read the journal from here for [report]. */
    public fun from(session: SessionRow): Long = session.start - SCREEN_SLACK.toMillis()

    public fun qualifies(session: SessionRow, threshold: Duration): Boolean =
        session.chargedMillis == 0L && !session.pluggedAtStart && session.end - session.start >= threshold.toMillis()

    /** Drain from every night that has a figure; deep share only from judged nights, since a gap hides part of it. */
    public fun compare(nights: List<SessionRow>): Comparison = Comparison(
        nights = nights.size,
        drainPerHour = nights.mapNotNull { it.drainPerHour }.takeIf { it.isNotEmpty() }?.average(),
        deepShare = nights.filter { it.verdict != null && it.end > it.start }
            .map { it.timeInDeepMillis.toDouble() / (it.end - it.start) }
            .takeIf { it.isNotEmpty() }?.average(),
    )

    public fun night(session: SessionRow, unlockedAt: Long, recent: List<SessionRow>, stock: List<SessionRow>): NightRow {
        val before = compare(recent)
        val baseline = compare(stock)
        return NightRow(
            sessionId = session.id,
            unlockedAt = unlockedAt,
            recentNights = before.nights,
            recentDrainPerHour = before.drainPerHour,
            recentDeepShare = before.deepShare,
            stockNights = baseline.nights,
            stockDrainPerHour = baseline.drainPerHour,
            stockDeepShare = baseline.deepShare,
        )
    }

    public fun report(night: NightRow, record: SessionRecord, events: List<EventRow>, actions: List<ActionRow>): NightReport {
        val session = record.session
        val inSession = from(session)..session.end
        return NightReport(
            night = night,
            session = session,
            timeline = session.timeline.lines().mapNotNull(::timelineSpan),
            light = record.transitions.filter { !it.deep },
            breaks = session.breaks.lines().filter { it.isNotBlank() },
            gaps = spans(session.gaps),
            historyFilled = spans(session.historyFilled),
            recent = Comparison(night.recentNights, night.recentDrainPerHour, night.recentDeepShare),
            stock = Comparison(night.stockNights, night.stockDrainPerHour, night.stockDeepShare),
            apps = record.wakers.filter { it.uid != null },
            kernel = record.wakers.filter { it.uid == null },
            sensors = sensorHolders(record.snapshots),
            holdOffs = events.filter { it.time in inSession && it.holdOff != null },
            drift = events.filter { it.time in inSession && it.kind == JournalRows.DRIFT },
            actions = actions.filter { it.time in inSession },
        )
    }

    /** Each held sensor once, with every moment a snapshot saw it, in snapshot order. */
    public fun sensorHolders(snapshots: List<SensorSnapshotRow>): List<SensorHolder> {
        val seen = snapshots.sortedBy { it.time }.flatMap { snapshot ->
            snapshot.connections.lines().map { it.split('\t') }.filter { it.size >= CONNECTION_FIELDS }.map { snapshot.moment to it }
        }
        return seen.groupBy { (_, fields) -> Triple(fields[0].toIntOrNull(), fields[1], fields[2]) }.map { (key, rows) ->
            SensorHolder(
                uid = key.first,
                listener = key.second,
                sensor = key.third,
                samplingPeriodUs = rows.firstNotNullOfOrNull { (_, fields) -> fields[PERIOD].toLongOrNull() },
                moments = rows.map { it.first }.distinct(),
            )
        }
    }

    private fun timelineSpan(line: String): TimelineSpan? {
        val parts = line.split(' ')
        if (parts.size != TIMELINE_FIELDS) return null
        val start = parts[1].toLongOrNull() ?: return null
        val end = parts[2].toLongOrNull() ?: return null
        return TimelineSpan(parts[0], start, end)
    }

    private fun spans(text: String): List<Span> = text.lines().mapNotNull { line ->
        val parts = line.split(' ')
        val start = parts.getOrNull(0)?.toLongOrNull()
        val end = parts.getOrNull(1)?.toLongOrNull()
        if (start == null || end == null) null else Span(start, end)
    }

    private const val CONNECTION_FIELDS = 4
    private const val PERIOD = 3
    private const val TIMELINE_FIELDS = 3
}
