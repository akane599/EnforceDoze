// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.data.ActionRow
import io.github.akane599.enforcedoze.data.EventRow
import io.github.akane599.enforcedoze.data.EvidenceRows
import io.github.akane599.enforcedoze.data.JournalRows
import io.github.akane599.enforcedoze.data.NightReport
import io.github.akane599.enforcedoze.data.NightReports
import io.github.akane599.enforcedoze.data.NightRow
import io.github.akane599.enforcedoze.data.SensorHolder
import io.github.akane599.enforcedoze.data.SessionAssembly
import io.github.akane599.enforcedoze.data.SessionRecord
import io.github.akane599.enforcedoze.data.SessionRow
import io.github.akane599.enforcedoze.data.Span
import io.github.akane599.enforcedoze.data.StoredNights
import io.github.akane599.enforcedoze.data.TransitionRow
import io.github.akane599.enforcedoze.data.WakerRow
import io.github.akane599.enforcedoze.design.DozeFill
import io.github.akane599.enforcedoze.design.StripBreak
import io.github.akane599.enforcedoze.design.StripModel
import io.github.akane599.enforcedoze.design.StripSegment
import io.github.akane599.enforcedoze.design.StripWake
import io.github.akane599.enforcedoze.doze.ActionRecord
import io.github.akane599.enforcedoze.doze.Lever
import io.github.akane599.enforcedoze.evidence.Cause
import io.github.akane599.enforcedoze.evidence.TransitionSource
import io.github.akane599.enforcedoze.feature.report.AppWaker
import io.github.akane599.enforcedoze.feature.report.Baseline
import io.github.akane599.enforcedoze.feature.report.BreakReason
import io.github.akane599.enforcedoze.feature.report.Compared
import io.github.akane599.enforcedoze.feature.report.ComparedNight
import io.github.akane599.enforcedoze.feature.report.DozeChange
import io.github.akane599.enforcedoze.feature.report.EngineAction
import io.github.akane599.enforcedoze.feature.report.KernelWaker
import io.github.akane599.enforcedoze.feature.report.LateCandidate
import io.github.akane599.enforcedoze.feature.report.RawGroup
import io.github.akane599.enforcedoze.feature.report.RawTitle
import io.github.akane599.enforcedoze.feature.report.ReportState
import io.github.akane599.enforcedoze.feature.report.ReportSummary
import io.github.akane599.enforcedoze.feature.report.SensorHold
import io.github.akane599.enforcedoze.feature.report.SensorMoment
import io.github.akane599.enforcedoze.feature.report.Stop
import io.github.akane599.enforcedoze.feature.report.StopEvent
import io.github.akane599.enforcedoze.parse.DeepState
import io.github.akane599.enforcedoze.parse.LightState
import io.github.akane599.enforcedoze.ui.VerdictWord

/** An app as the report names it: its label, and the package the allowlist would add. */
internal data class AppName(val label: String, val packageName: String)

/**
 * What the mapper cannot know. [app] gets the uid when the record has one and the packages it names (a waker's
 * packages, a sensor listener, a drift's writer); it returns null for an app it does not know, and the report then
 * shows the name as the record holds it. [holdOff] words a hold-off's stored name (`AUDIO`, `CALL`, …).
 */
internal class ReportNames(
    val profile: (String) -> String,
    val app: (uid: Int?, packages: List<String>) -> AppName?,
    val holdOff: (String) -> String = { it },
)

/** An earlier night for "Compared with": its row and its sessions' records, since a row holds no verdict or timeline. */
internal data class EarlierNight(val row: NightRow, val records: List<SessionRecord>)

/**
 * The evidence log (`:core:data`) as the report shows it (`:feature:report`, ADR 0029). Pure: no Android calls, no
 * I/O. A figure the records do not hold is null and prints as a dash, never 0 (SPEC 0.2); a verdict is the one
 * stored, never worked out again here.
 */
internal object ReportMapping {
    /**
     * The night report (SPEC 8.5, ADR 0031). [records] are the night's sessions with their rows, the same ones
     * [NightReports.report] was given: the strip and the timetable read their transitions, of which the report keeps
     * only the light ones. [earlier] are the nights before it, newest first; up to [NightReports.RECENT] are compared.
     */
    fun night(report: NightReport, records: List<SessionRecord>, earlier: List<EarlierNight>, names: ReportNames): ReportState {
        val night = report.night
        val sessions = report.sessions
        val parts = Parts(records, night.start, night.end, report.wakes, report.holdOffs, report.drift, names)
        val shares = parts.shares(night.deepShare)
        val verdict = verdict(report.verdict)
        val summary = ReportSummary(
            sessionId = night.sessionId,
            start = night.start,
            end = night.end,
            night = true,
            profile = night.profileId?.let(names.profile),
            verdict = verdict,
            cause = cause(report.cause),
            note = notes(sessions),
            // The largest of the sessions', so a late start after a wake-up is not hidden behind an early first one.
            lateBy = sessions.mapNotNull { it.lateByMillis }.maxOrNull(),
            strip = parts.strip,
            timeInDeep = report.timeInDeepMillis,
            maintenanceWindows = report.maintenanceWindows,
            motionResets = sessions.sumOf { it.motionResets },
            timeToDeep = sessions.firstOrNull()?.timeToDeepMillis,
            deepShare = shares.deep,
            lightShare = shares.light,
            activeShare = shares.active,
            drainPerHour = night.drainPerHour,
            drainMah = sessions.map { it.drainMah }.takeIf { all -> all.isNotEmpty() && all.all { it != null } }?.sumOf { it ?: 0.0 },
            ownWakeups = report.ownWakeups,
        )
        val apps = report.apps.map { app(it, names) }
        val compared = Compared(
            nights = listOf(ComparedNight(night.start, parts.strip, verdict, night.drainPerHour)) +
                earlier.sortedByDescending { it.row.start }.take(NightReports.RECENT).map(::compared),
            recent = Baseline(report.recent.nights, report.recent.drainPerHour, report.recent.deepShare),
            stock = Baseline(report.stock.nights, report.stock.drainPerHour, report.stock.deepShare),
        )
        return ReportState(
            summary = summary,
            timetable = parts.timetable,
            compared = compared,
            apps = apps,
            kernel = report.kernel.map(::kernel),
            sensors = report.sensors.map { sensor(it, names) },
            changes = parts.changes,
            actions = report.actions.mapNotNull(::action),
            raw = raw(records),
            lateCandidates = lateCandidates(apps),
        )
    }

    /**
     * The report of one session shorter than a night (SPEC 8.4): no comparison. [events] and [actions] are the
     * journal's rows from [NightReports.from] onwards; rows after screen on are left out here.
     */
    fun session(record: SessionRecord, events: List<EventRow>, actions: List<ActionRow>, names: ReportNames): ReportState {
        val row = record.session
        val span = NightReports.from(row)..row.end
        val journal = events.filter { it.time in span }
        val parts = Parts(
            listOf(record),
            row.start,
            row.end,
            emptyList(),
            journal.filter { it.holdOff != null },
            journal.filter { it.kind == JournalRows.DRIFT },
            names,
        )
        val shares = parts.shares(parts.judgedDeep)
        val summary = ReportSummary(
            sessionId = row.id,
            start = row.start,
            end = row.end,
            night = false,
            profile = row.profileId?.let(names.profile),
            verdict = verdict(row.verdict),
            cause = cause(row.cause),
            note = notes(listOf(row)),
            lateBy = row.lateByMillis,
            strip = parts.strip,
            timeInDeep = row.timeInDeepMillis,
            maintenanceWindows = row.maintenanceWindows,
            motionResets = row.motionResets,
            timeToDeep = row.timeToDeepMillis,
            deepShare = shares.deep,
            lightShare = shares.light,
            activeShare = shares.active,
            drainPerHour = row.drainPerHour,
            drainMah = row.drainMah,
            ownWakeups = row.ownWakeups,
        )
        val apps = record.wakers.filter { it.uid != null }.map { app(it, names) }
        return ReportState(
            summary = summary,
            timetable = parts.timetable,
            compared = null,
            apps = apps,
            kernel = record.wakers.filter { it.uid == null }.map(::kernel),
            sensors = NightReports.sensorHolders(record.snapshots).map { sensor(it, names) },
            changes = parts.changes,
            actions = actions.filter { it.time in span }.mapNotNull(::action),
            raw = raw(listOf(record)),
            lateCandidates = lateCandidates(apps),
        )
    }

    /**
     * The stored verdict ([EvidenceRows]) as every screen words it. No verdict, or one this build does not know, is
     * Not judged: the report never shows a verdict the row does not hold. (`:core:ui` and `:core:evidence` have no
     * mapping from the stored string, since only `:app` sees the rows.)
     */
    fun verdict(stored: String?): VerdictWord = when (stored) {
        EvidenceRows.HELD -> VerdictWord.HELD
        EvidenceRows.PARTLY -> VerdictWord.PARTLY
        EvidenceRows.DID_NOT_DOZE -> VerdictWord.DID_NOT_DOZE
        else -> VerdictWord.NOT_JUDGED
    }

    /** The stored cause, written as [Cause.name] by [EvidenceRows.record]; null when none is stored or it is unknown. */
    fun cause(stored: String?): Cause? = Cause.entries.firstOrNull { it.name == stored }

    private fun compared(night: EarlierNight): ComparedNight {
        val tracks = night.records.sortedBy { it.session.start }.map(::Track)
        val sessions = tracks.map { it.row }
        val strip = strip(tracks, StoredNights.wakes(sessions), night.row.start, night.row.end)
        return ComparedNight(night.row.start, strip, verdict(NightReports.verdict(sessions).first), night.row.drainPerHour)
    }
}

/** The records of a session, a night or an earlier night, put together for the strip, the timetable and the changes. */
private class Parts(
    records: List<SessionRecord>,
    val start: Long,
    val end: Long,
    private val wakes: List<Span>,
    holdOffEvents: List<EventRow>,
    private val drift: List<EventRow>,
    private val names: ReportNames,
) {
    private val tracks = records.sortedBy { it.session.start }.map(::Track)
    private val holdOffs = holdOffChanges(holdOffEvents, start, names)

    val strip: StripModel = strip(tracks, wakes, start, end)

    val changes: List<DozeChange> = (holdOffs + drift.map { driftChange(it, names) }).sortedBy { it.time }

    val timetable: List<Stop> = run {
        val edges = listOf(Stop(start, StopEvent.ScreenOff)) +
            wakes.map { Stop(it.start, StopEvent.Wake(it.end - it.start)) } +
            Stop(end, StopEvent.ScreenOn)
        val stops = (edges + tracks.flatMap { it.stops(holdOffs, drift, names) })
            .sortedWith(compareBy<Stop> { it.time }.thenBy { STOP_ORDER.indexOf(it.event::class) })
        // Only the night's first entry into deep idle is "Deep idle"; every later one, after a break or a wake-up, is "again".
        var reached = false
        stops.map { stop ->
            if (stop.event !is StopEvent.Deep) return@map stop
            stop.copy(event = StopEvent.Deep(again = reached)).also { reached = true }
        }
    }

    private val judged = tracks.filter { it.judged }
    private val judgedTime = judged.sumOf { it.end - it.start }

    /** Time in deep idle over the judged sessions' time: the rule of [NightReports.night]; null when none was judged. */
    val judgedDeep: Double? = if (judgedTime > 0) judged.sumOf { it.row.timeInDeepMillis }.toDouble() / judgedTime else null

    /**
     * The three shares over the judged sessions only: a session with a gap is not judged, and a gap hides part of the
     * night (ADR 0020). Light and awake come from the strip; [deep] is the stored or [judgedDeep] figure.
     */
    fun shares(deep: Double?): Shares = if (judgedTime <= 0) {
        Shares(null, null, null)
    } else {
        Shares(
            deep,
            judged.sumOf { it.timeIn(DozeFill.LIGHT) }.toDouble() / judgedTime,
            judged.sumOf { it.timeIn(DozeFill.ACTIVE) }.toDouble() / judgedTime,
        )
    }
}

private data class Shares(val deep: Double?, val light: Double?, val active: Double?)

/** One deep state as the session's stored timeline holds it (`STATE start end`, [EvidenceRows.record]). */
private data class Stretch(val state: String, val start: Long, val end: Long)

/** One stored break: the time, the deep state entered, the reason the system logged and the cause (`EvidenceRows`). */
private data class BreakLine(val time: Long, val state: String, val reason: String, val cause: String)

/** One session's stored records, read for drawing: the strip's fills, its ticks and the timetable's stops. */
private class Track(record: SessionRecord) {
    val row: SessionRow = record.session
    val start: Long = row.start
    val end: Long = row.end
    val judged: Boolean = row.verdict != null && end > start
    private val deep = stretches(row.timeline)
    private val gaps = spans(row.gaps)
    private val filled = spans(row.historyFilled)
    val breaks: List<BreakLine> = breakLines(row.breaks)
    private val light = record.transitions.filter { !it.deep }.sortedBy { it.time }
    private val history = record.transitions.filter { it.source == TransitionSource.HISTORY.name }.sortedBy { it.time }

    /**
     * Deep idle and its maintenance windows are deep; otherwise light idle is light and the rest is awake. A gap is
     * hatched. Time the stored timeline does not cover is left blank: nothing is drawn that was not seen (SPEC 8.2).
     */
    val segments: List<StripSegment> = run {
        val cuts = (
            listOf(start, end) + deep.flatMap { listOf(it.start, it.end) } + light.map { it.time } + history.map { it.time } +
                (gaps + filled).flatMap { listOf(it.start, it.end) }
            ).filter { it in start..end }.distinct().sorted()
        val out = mutableListOf<StripSegment>()
        cuts.zipWithNext().forEach { (from, to) ->
            val fill = fillAt(from) ?: return@forEach
            val last = out.lastOrNull()
            if (last != null && last.fill == fill && last.end == from) {
                out[out.lastIndex] = last.copy(end = to)
            } else {
                out += StripSegment(fill, from, to)
            }
        }
        out
    }

    /** The maintenance windows with a record: a stretch a gap began in is not one the record saw. */
    private val windows = deep.filter { it.state == DeepState.IDLE_MAINTENANCE.name && gaps.none { gap -> it.start in gap } }

    val maintenance: List<Long> = windows.map { it.start }

    fun timeIn(fill: DozeFill): Long = segments.filter { it.fill == fill }.sumOf { it.end - it.start }

    fun stops(holdOffs: List<DozeChange.HoldOff>, drift: List<EventRow>, names: ReportNames): List<Stop> =
        lightStops() + deepStops() + maintenanceStops() +
            breaks.map { breakStop(it, holdOffs, drift, names) } +
            gaps.map { Stop(it.start, StopEvent.Gap(it.end)) } +
            filled.map { Stop(it.start, StopEvent.Filled(it.end)) }

    private fun fillAt(time: Long): DozeFill? {
        if (gaps.any { time in it }) return DozeFill.MISSING
        val stretch = deep.firstOrNull { time >= it.start && time < it.end } ?: return null
        return when {
            stretch.state in DEEP_STATES -> DozeFill.DEEP
            lightIdleAt(time) -> DozeFill.LIGHT
            else -> DozeFill.ACTIVE
        }
    }

    /**
     * Light idle from the event log's `device_idle_light` changes. Inside a stretch the battery history filled, the
     * event log has nothing, so the history's own `device_idle` value decides (ADR 0018).
     */
    private fun lightIdleAt(time: Long): Boolean {
        filled.firstOrNull { time in it }?.let { span ->
            history.lastOrNull { it.time in span.start..time }?.let { return it.toState == HISTORY_LIGHT }
        }
        val state = light.lastOrNull { it.time <= time }?.toState ?: light.firstOrNull()?.fromState
        return state != null && state !in AWAKE_LIGHT
    }

    /** Light idle reached from awake or after deep idle; its own maintenance cycles are not stops. */
    private fun lightStops(): List<Stop> = segments.zipWithNext()
        .filter { (before, segment) -> segment.fill == DozeFill.LIGHT && before.end == segment.start && before.fill != DozeFill.MISSING }
        .map { (_, segment) -> Stop(segment.start, StopEvent.Light) }

    /** Each entry into deep idle; the session's first shows when it was due (screen off + the expected walk) and how late it came. */
    private fun deepStops(): List<Stop> {
        val entries = deep.filterIndexed { i, stretch ->
            stretch.state == DeepState.IDLE.name && (i == 0 || deep[i - 1].state !in DEEP_STATES)
        }
        return entries.mapIndexed { i, stretch ->
            val due = if (i == 0) row.expectedToDeepMillis?.let { start + it } else null
            Stop(stretch.start, StopEvent.Deep(), due = due, late = due?.let { stretch.start - it }?.takeIf { it > 0 })
        }
    }

    /** A window cut by a gap has no known length. */
    private fun maintenanceStops(): List<Stop> = windows.map { stretch ->
        val whole = gaps.none { it.start < stretch.end && stretch.start < it.end }
        Stop(stretch.start, StopEvent.Maintenance(lasted = (stretch.end - stretch.start).takeIf { whole }))
    }

    private fun breakStop(line: BreakLine, holdOffs: List<DozeChange.HoldOff>, drift: List<EventRow>, names: ReportNames): Stop {
        val reason = breakReason(line.cause)
        val subject = when (reason) {
            BreakReason.HOLD_OFF -> holdOffs.filter { it.time <= line.time && line.time < (it.until ?: Long.MAX_VALUE) }
                .joinToString(", ") { it.name }.ifEmpty { null }

            // The cause is another manager only when a drift came shortly before (Verdicts), so the latest one is it.
            BreakReason.ANOTHER_MANAGER -> drift.lastOrNull { it.time <= line.time }?.writer?.let { writerName(it, names) }

            else -> null
        }
        return Stop(line.time, StopEvent.Break(reason, subject, awake(line.time)))
    }

    /** How long the phone stayed awake after a break: until idle resumed or the screen came on; unknown if a gap cut it. */
    private fun awake(time: Long): Long? {
        val index = segments.indexOfFirst { time >= it.start && time < it.end }
        val segment = segments.getOrNull(index)?.takeIf { it.fill == DozeFill.ACTIVE } ?: return null
        val next = segments.getOrNull(index + 1)
        val ended = if (next == null) segment.end == end else next.start == segment.end && next.fill != DozeFill.MISSING
        return (segment.end - time).takeIf { ended }
    }
}

private fun strip(tracks: List<Track>, wakes: List<Span>, start: Long, end: Long) = StripModel(
    start = start,
    end = end,
    segments = tracks.flatMap { it.segments },
    maintenance = tracks.flatMap { it.maintenance },
    breaks = tracks.flatMap { track -> track.breaks.map { StripBreak(it.time, it.reason) } },
    wakes = wakes.map { StripWake(it.start, it.end) },
)

/** Each hold-off once, from its start to its end; one that ended with no start in the span was on from [from]. */
private fun holdOffChanges(events: List<EventRow>, from: Long, names: ReportNames): List<DozeChange.HoldOff> {
    val open = LinkedHashMap<String, Long>()
    val out = mutableListOf<DozeChange.HoldOff>()
    events.sortedBy { it.time }.forEach { event ->
        val name = event.holdOff ?: return@forEach
        when (event.kind) {
            JournalRows.HOLD_OFF_STARTED -> open.putIfAbsent(name, event.time)

            JournalRows.HOLD_OFF_ENDED -> {
                val started = open.remove(name)
                if (started != null || event.time > from) out += DozeChange.HoldOff(started ?: from, names.holdOff(name), event.time)
            }
        }
    }
    open.forEach { (name, time) -> out += DozeChange.HoldOff(time, names.holdOff(name), until = null) }
    return out
}

/** The drifted lever and both values as the engine logged them; the writer by its label when known (ADR 0014). */
private fun driftChange(event: EventRow, names: ReportNames) = DozeChange.Drift(
    time = event.time,
    key = event.lever.orEmpty(),
    found = event.found,
    expected = event.expected,
    writer = event.writer?.let { writerName(it, names) },
)

private fun writerName(packageName: String, names: ReportNames): String = names.app(null, listOf(packageName))?.label ?: packageName

/** Only an OK read-back is applied (SPEC 0.2); the cause, any other outcome and the stored detail go under it. */
private fun action(row: ActionRow): EngineAction? {
    val lever = Lever.entries.firstOrNull { it.name == row.lever } ?: return null
    val ok = row.outcome == ActionRecord.Outcome.OK.name
    val detail = listOfNotNull(row.cause.ifBlank { null }, row.outcome.takeIf { !ok }, row.detail.ifBlank { null }).joinToString(" · ")
    return EngineAction(row.time, lever, row.command, row.readBack, ok, detail.ifEmpty { null })
}

/** An app waker: alarm and network wakeups (ADR 0019), and the wakelock time it held. */
private fun app(row: WakerRow, names: ReportNames): AppWaker {
    val packages = row.name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val known = names.app(row.uid, packages)
    return AppWaker(
        label = known?.label ?: row.name.ifBlank { "uid ${row.uid}" },
        packageName = known?.packageName ?: packages.firstOrNull().orEmpty(),
        wakeups = row.alarmWakeups + row.networkWakeups,
        wakelock = row.wakelockMillis,
        self = row.self,
    )
}

private fun kernel(row: WakerRow) = KernelWaker(row.name, row.kernelWakeups)

private fun sensor(holder: SensorHolder, names: ReportNames) = SensorHold(
    label = names.app(holder.uid, listOf(holder.listener))?.label ?: holder.listener,
    sensor = holder.sensor,
    rateHz = holder.samplingPeriodUs?.takeIf { it > 0 }?.let { MICROS_PER_SECOND / it },
    moments = holder.moments.mapNotNull(::moment).distinct(),
)

private fun moment(stored: String): SensorMoment? = when (stored) {
    SessionAssembly.SCREEN_OFF -> SensorMoment.SCREEN_OFF
    SessionAssembly.COPY -> SensorMoment.MAINTENANCE
    SessionAssembly.SCREEN_ON -> SensorMoment.SCREEN_ON
    else -> null
}

/** This night's app wakers for "Something arrived late", in rank order; the app adds messaging and alarm apps first. */
private fun lateCandidates(apps: List<AppWaker>): List<LateCandidate> =
    apps.filter { !it.self && it.packageName.isNotEmpty() }.distinctBy { it.packageName }.map { LateCandidate(it.label, it.packageName) }

/** The evidence [Cause] of a stored break; the display model names no reason for charging, so it is unknown there. */
private fun breakReason(stored: String): BreakReason = when (ReportMapping.cause(stored)) {
    Cause.MOTION -> BreakReason.MOTION
    Cause.HOLD_OFF -> BreakReason.HOLD_OFF
    Cause.ANOTHER_MANAGER -> BreakReason.ANOTHER_MANAGER
    else -> BreakReason.UNKNOWN
}

/** The sessions' notes, one reason per line as the recorder wrote them; null when there are none. */
private fun notes(sessions: List<SessionRow>): String? =
    sessions.flatMap { it.note.orEmpty().lines() }.filter { it.isNotBlank() }.distinct().joinToString("\n").ifEmpty { null }

/** The records the verdict was judged on, as stored; a group with nothing in it is left out. */
private fun raw(records: List<SessionRecord>): List<RawGroup> {
    val rows = records.map { it.session }.sortedBy { it.start }
    val light = records.flatMap { record -> record.transitions.filter { !it.deep } }.sortedBy { it.time }
    val groups = listOf(
        RawTitle.TIMELINE to rows.flatMap { lines(it.timeline) },
        RawTitle.TRANSITIONS to light.map(::transitionLine),
        RawTitle.BREAKS to rows.flatMap { lines(it.breaks) },
        RawTitle.COVERAGE to rows.flatMap(::coverage),
        RawTitle.NOTES to rows.flatMap { lines(it.note.orEmpty()) },
    )
    return groups.filter { it.second.isNotEmpty() }.map { (title, lines) -> RawGroup(title, lines) }
}

private fun transitionLine(row: TransitionRow) = "${row.time} ${row.fromState ?: "-"} → ${row.toState} ${row.reason} ${row.source}"

private fun coverage(row: SessionRow): List<String> = listOf(
    "session ${row.id} ${row.start} ${row.end}",
    "eventLogFrom ${row.eventLogFrom ?: "-"}",
    "historyFrom ${row.historyFrom ?: "-"}",
) + lines(row.gaps).map { "gap $it" } + lines(row.historyFilled).map { "historyFilled $it" }

private fun lines(text: String): List<String> = text.lines().filter { it.isNotBlank() }

/** The stored timeline, with neighbouring stretches of one state joined (the history's fill starts a new one at its edge). */
private fun stretches(text: String): List<Stretch> {
    val out = mutableListOf<Stretch>()
    text.lines().mapNotNull(::stretch).forEach { next ->
        val last = out.lastOrNull()
        if (last != null && last.state == next.state && last.end == next.start) {
            out[out.lastIndex] = last.copy(end = next.end)
        } else {
            out += next
        }
    }
    return out
}

private fun stretch(line: String): Stretch? {
    val parts = line.split(' ').takeIf { it.size == TIMELINE_FIELDS } ?: return null
    val start = parts[1].toLongOrNull() ?: return null
    val end = parts[2].toLongOrNull()?.takeIf { it > start } ?: return null
    return Stretch(parts[0], start, end)
}

private fun spans(text: String): List<Span> = text.lines().mapNotNull { line ->
    val parts = line.split(' ')
    val start = parts.getOrNull(0)?.toLongOrNull()
    val end = parts.getOrNull(1)?.toLongOrNull()
    if (start == null || end == null || end <= start) null else Span(start, end)
}

/** A logged reason may hold spaces, so it is everything between the state and the cause. */
private fun breakLines(text: String): List<BreakLine> = lines(text).mapNotNull { line ->
    val parts = line.split(' ')
    val time = parts.first().toLongOrNull()
    if (time == null || parts.size < BREAK_FIELDS) {
        null
    } else {
        BreakLine(time, parts[1], parts.subList(2, parts.lastIndex).joinToString(" "), parts.last())
    }
}

private operator fun Span.contains(time: Long): Boolean = time >= start && time < end

private val DEEP_STATES = setOf(DeepState.IDLE.name, DeepState.IDLE_MAINTENANCE.name)

/** Light states that are not light idle (`DeviceIdleController.LIGHT_STATE_*`, [LightState]). */
private val AWAKE_LIGHT = setOf(LightState.ACTIVE.name, LightState.INACTIVE.name)

/** The battery history's `device_idle` value for light idle; it prints `off`, `light` or `full` (ADR 0018, `DozeSources`). */
private const val HISTORY_LIGHT = "light"

/** Stops at one moment read in this order: the screen first, then the record's edges, then what Doze did. */
private val STOP_ORDER = listOf(
    StopEvent.ScreenOff::class,
    StopEvent.Wake::class,
    StopEvent.Gap::class,
    StopEvent.Filled::class,
    StopEvent.Break::class,
    StopEvent.Light::class,
    StopEvent.Deep::class,
    StopEvent.Maintenance::class,
    StopEvent.ScreenOn::class,
)

private const val TIMELINE_FIELDS = 3
private const val BREAK_FIELDS = 4
private const val MICROS_PER_SECOND = 1_000_000.0
