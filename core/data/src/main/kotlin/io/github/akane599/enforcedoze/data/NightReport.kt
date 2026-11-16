// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.akane599.enforcedoze.profile.Nights
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * A night (SPEC 8.5, ADR 0031, 0032): unplugged screen-off sessions joined across screen-on gaps shorter than 10
 * minutes, with at least [NightReports.THRESHOLD] of screen off in all, at whatever hour, and the longest rest
 * within 12 hours of its start. The report is built from the sessions' rows; this row keeps the night's own
 * figures, which later nights compare against, and the comparison as it stood when it was generated.
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
    indices = [Index("sessionId", unique = true), Index("unlockedAt"), Index("start")],
)
@Serializable
public data class NightRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The night's first session; retention removes the night with it. */
    val sessionId: Long,
    /** The first session's screen off and the last session's screen on. */
    val start: Long,
    val end: Long,
    /** More than one when the owner woke the screen briefly in the night. */
    val sessionCount: Int,
    /** Screen-off time over the sessions, the wake-ups between them left out: what the threshold counts. */
    val screenOffMillis: Long,
    /** The sessions' drain weighted by their length; null when no session had a figure. */
    val drainPerHour: Double?,
    /** Deep idle over the judged sessions' time; null when none was judged, since a gap hides part of it. */
    val deepShare: Double?,
    /** The profile every session ran; null when they ran different ones, so the night is no baseline for either. */
    val profileId: String?,
    /** The unlock that generated the report, or the latest one that extended the night. */
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
    /** The night's sessions in start order; more than one when short wake-ups split it (ADR 0031). */
    val sessions: List<SessionRow>,
    /** 1. The verdict line: the sessions' verdicts joined ([NightReports.verdict]) and their summed figures. */
    val verdict: String?,
    val cause: String?,
    val timeInDeepMillis: Long,
    val maintenanceWindows: Int,
    /** The app's own wakeups over the sessions; null when any session's count is unknown (ADR 0019), never a guessed 0. */
    val ownWakeups: Int?,
    /** 2. The timeline strip: deep stretches, light changes, breaks, gaps, history-filled stretches and the wake-ups. */
    val timeline: List<TimelineSpan>,
    val light: List<TransitionRow>,
    val breaks: List<String>,
    val gaps: List<Span>,
    val historyFilled: List<Span>,
    val wakes: List<Span>,
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
    /** SPEC 8.5: this much screen off makes a night. Shortened only by the debug build's compressed night. */
    public val THRESHOLD: Duration = Nights.MIN_SCREEN_OFF

    /** How many earlier nights the comparison takes (SPEC 8.5). */
    public const val RECENT: Int = 7

    /**
     * The engine and the recorder each take the screen-off broadcast on their own, so the engine's screen-off
     * writes can be logged just before the session's start (114 ms on the API 37 emulator). They belong to the night.
     */
    private val SCREEN_SLACK: Duration = Duration.ofSeconds(5)

    /** Where a session's hold-offs, drift and actions start: read the journal from here. */
    public fun from(session: SessionRow): Long = session.start - SCREEN_SLACK.toMillis()

    /** Where a night's hold-offs, drift and actions start: read the journal from here for [report]. */
    public fun from(night: NightRow): Long = night.start - SCREEN_SLACK.toMillis()

    /** Mean drain and deep share over [nights], each from the nights that have it. */
    public fun compare(nights: List<NightRow>): Comparison = Comparison(
        nights = nights.size,
        drainPerHour = nights.mapNotNull { it.drainPerHour }.takeIf { it.isNotEmpty() }?.average(),
        deepShare = nights.mapNotNull { it.deepShare }.takeIf { it.isNotEmpty() }?.average(),
    )

    /**
     * The row for the night made of [sessions] (start order), compared with [recent] earlier nights and [stock]
     * ones. Called again with the same first session when a later session joins: the caller keeps the row's id.
     */
    public fun night(sessions: List<SessionRow>, unlockedAt: Long, recent: List<NightRow>, stock: List<NightRow>): NightRow {
        require(sessions.isNotEmpty()) { "a night has a session" }
        val before = compare(recent)
        val baseline = compare(stock)
        val timed = sessions.filter { it.drainPerHour != null && it.end > it.start }
        val judged = sessions.filter { it.verdict != null && it.end > it.start }
        return NightRow(
            sessionId = sessions.first().id,
            start = sessions.first().start,
            end = sessions.last().end,
            sessionCount = sessions.size,
            screenOffMillis = sessions.sumOf { it.end - it.start },
            drainPerHour = timed.takeIf { it.isNotEmpty() }
                ?.let { list -> list.sumOf { (it.drainPerHour ?: 0.0) * (it.end - it.start) } / list.sumOf { it.end - it.start } },
            deepShare = judged.takeIf { it.isNotEmpty() }
                ?.let { list -> list.sumOf { it.timeInDeepMillis }.toDouble() / list.sumOf { it.end - it.start } },
            profileId = sessions.map { it.profileId }.distinct().singleOrNull(),
            unlockedAt = unlockedAt,
            recentNights = before.nights,
            recentDrainPerHour = before.drainPerHour,
            recentDeepShare = before.deepShare,
            stockNights = baseline.nights,
            stockDrainPerHour = baseline.drainPerHour,
            stockDeepShare = baseline.deepShare,
        )
    }

    /**
     * One verdict for the night's sessions: Held when every judged session held, Did not doze when none dozed,
     * Partly otherwise; null when no session was judged. The cause is the longest undozed session's.
     */
    public fun verdict(sessions: List<SessionRow>): Pair<String?, String?> {
        val judged = sessions.mapNotNull { it.verdict }
        val verdict = when {
            judged.isEmpty() -> null
            judged.all { it == EvidenceRows.HELD } -> EvidenceRows.HELD
            judged.all { it == EvidenceRows.DID_NOT_DOZE } -> EvidenceRows.DID_NOT_DOZE
            else -> EvidenceRows.PARTLY
        }
        val cause = sessions.filter { it.verdict == EvidenceRows.DID_NOT_DOZE }.maxByOrNull { it.end - it.start }?.cause
        return verdict to cause.takeIf { verdict == EvidenceRows.DID_NOT_DOZE }
    }

    /** The report of [night] from its sessions' [records] in start order, with the journal read from [from]. */
    public fun report(night: NightRow, records: List<SessionRecord>, events: List<EventRow>, actions: List<ActionRow>): NightReport {
        val sessions = records.map { it.session }.sortedBy { it.start }
        val inNight = from(night)..night.end
        val (verdict, cause) = verdict(sessions)
        return NightReport(
            night = night,
            sessions = sessions,
            verdict = verdict,
            cause = cause,
            timeInDeepMillis = sessions.sumOf { it.timeInDeepMillis },
            maintenanceWindows = sessions.sumOf { it.maintenanceWindows },
            ownWakeups = sessions.map { it.ownWakeups }.takeIf { counts -> counts.all { it != null } }?.sumOf { it ?: 0 },
            timeline = sessions.flatMap { session -> session.timeline.lines().mapNotNull(::timelineSpan) },
            light = records.flatMap { it.transitions }.filter { !it.deep }.sortedBy { it.time },
            breaks = sessions.flatMap { session -> session.breaks.lines().filter { it.isNotBlank() } },
            gaps = sessions.flatMap { spans(it.gaps) },
            historyFilled = sessions.flatMap { spans(it.historyFilled) },
            wakes = StoredNights.wakes(sessions),
            recent = Comparison(night.recentNights, night.recentDrainPerHour, night.recentDeepShare),
            stock = Comparison(night.stockNights, night.stockDrainPerHour, night.stockDeepShare),
            apps = NightWakers.merge(records.map { record -> record.wakers.filter { it.uid != null } }),
            kernel = NightWakers.merge(records.map { record -> record.wakers.filter { it.uid == null } }),
            sensors = sensorHolders(records.flatMap { it.snapshots }),
            holdOffs = events.filter { it.time in inNight && it.holdOff != null },
            drift = events.filter { it.time in inNight && it.kind == JournalRows.DRIFT },
            actions = actions.filter { it.time in inNight },
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

/** A night's wakers over its sessions. */
public object NightWakers {
    /**
     * The wakers of a night: one session's as stored, or each app and kernel reason once with its counts summed
     * over the sessions, ranked as the recorder ranks them (most wakeups, then most wakelock time).
     */
    public fun merge(perSession: List<List<WakerRow>>): List<WakerRow> {
        if (perSession.size <= 1) return perSession.firstOrNull().orEmpty()
        val merged = perSession.flatten().groupBy { it.uid to it.name }.values.map { rows -> rows.reduce(::sum) }
        return merged.sortedWith(
            compareByDescending<WakerRow> { it.alarmWakeups + it.networkWakeups + it.kernelWakeups }
                .thenByDescending { it.wakelockMillis }
                .thenBy { it.uid ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )
    }

    private fun sum(a: WakerRow, b: WakerRow) = a.copy(
        self = a.self || b.self,
        alarmWakeups = a.alarmWakeups + b.alarmWakeups,
        networkWakeups = a.networkWakeups + b.networkWakeups,
        kernelWakeups = a.kernelWakeups + b.kernelWakeups,
        jobs = a.jobs + b.jobs,
        jobMillis = a.jobMillis + b.jobMillis,
        syncs = a.syncs + b.syncs,
        syncMillis = a.syncMillis + b.syncMillis,
        wakelockMillis = a.wakelockMillis + b.wakelockMillis,
        longWakelocks = a.longWakelocks + b.longWakelocks,
        longWakelockMillis = a.longWakelockMillis + b.longWakelockMillis,
        alarms = sumAlarms(a.alarms, b.alarms),
    )

    /** Alarm lines (`wakeups alarms tag`) of two sessions, each tag once with its counts summed, in first-seen order. */
    private fun sumAlarms(a: String, b: String): String {
        val tags = LinkedHashMap<String, Pair<Int, Int>>()
        (a.lines() + b.lines()).map { it.split(' ', limit = ALARM_FIELDS) }.filter { it.size == ALARM_FIELDS }.forEach { (w, n, tag) ->
            val was = tags[tag] ?: (0 to 0)
            tags[tag] = (was.first + (w.toIntOrNull() ?: 0)) to (was.second + (n.toIntOrNull() ?: 0))
        }
        return tags.entries.joinToString("\n") { (tag, counts) -> "${counts.first} ${counts.second} $tag" }
    }

    private const val ALARM_FIELDS = 3
}
