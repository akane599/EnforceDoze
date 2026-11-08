// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.evidence

import io.github.akane599.enforcedoze.parse.AlarmStats
import io.github.akane599.enforcedoze.parse.AlarmTagStats
import io.github.akane599.enforcedoze.parse.AppAlarmStats
import io.github.akane599.enforcedoze.parse.BatteryHistory
import java.time.Duration
import java.time.Instant

/** Work an app ran, as the battery history shows it (ADR 0018, 0019). */
public enum class RunKind { JOB, SYNC, WAKELOCK, LONG_WAKELOCK }

/**
 * One run, with its whole span: it may start before the session or end after it. [uid] is null for a
 * wakelock stretch whose holder the history did not name (it was already held when the read began).
 */
public data class Run(val kind: RunKind, val uid: Int?, val tag: String, val interval: Interval)

/** What one app did during a session. */
public data class AppWakers(
    val uid: Int,
    /** Package names from the alarm dump; empty when the app delivered no alarms in the session. */
    val packages: List<String>,
    val alarmWakeups: Int,
    /** Network packets for this uid that woke the processor (`wakeupap`, ADR 0019). */
    val networkWakeups: Int,
    /** Alarm tags delivered in the session, with their wakeups. */
    val alarms: List<AlarmTagStats>,
    val jobs: Int,
    val jobTime: Duration,
    val syncs: Int,
    val syncTime: Duration,
    /** Wakelock time while this app was the holder the history names. */
    val wakelockTime: Duration,
    val longWakelocks: Int,
    val longWakelockTime: Duration,
) {
    val wakeups: Int get() = alarmWakeups + networkWakeups
}

/** A kernel wakeup reason and how often it woke the device in the session. */
public data class WakeReason(val reason: String, val count: Int)

/** The app's own wakeups (SPEC 8.2, 15; ADR 0018, 0019). */
public data class OwnWakeups(
    val alarmWakeups: Int,
    val networkWakeups: Int,
    /** Jobs, syncs and wakelocks of the app that started while deep idle was IDLE: each is a wakeup. */
    val inIdle: List<Run>,
    /** The app's runs at other times, such as the copy job in a maintenance window: listed, not counted. */
    val outsideIdle: List<Run>,
) {
    val total: Int get() = alarmWakeups + networkWakeups + inIdle.size
}

public data class WakerSummary(
    /** Apps that woke the device or ran work, most wakeups first, then most wakelock time. */
    val apps: List<AppWakers>,
    val wakeReasons: List<WakeReason>,
    /** Mobile radio time as a device total; the history keeps none per app (ADR 0018). */
    val mobileRadio: Duration,
    /** Wakelock time without a named holder. */
    val unattributedWakelock: Duration,
    /** The part of the session the battery history covered; null when it covered none. */
    val historyCoverage: Interval?,
    /** Both alarm reads were present and every line of their sections was read, so the alarm wakeups are known. */
    val alarmsRead: Boolean,
    val own: OwnWakeups,
    /** [OwnWakeups.total], or null when the alarm reads or the history do not cover the session: never a guessed 0. */
    val ownWakeups: Int?,
)

public data class WakerInput(
    val session: Interval,
    /** The battery history read at screen on; null when the read failed. */
    val history: BatteryHistory?,
    /** `dumpsys alarm` at screen off and at screen on; null when a read failed. */
    val alarmsBefore: AlarmStats?,
    val alarmsAfter: AlarmStats?,
    /** Deep IDLE stretches in the session, maintenance windows excluded (see [Verdicts.timeline]). */
    val deepIdle: List<Interval>,
    val selfUid: Int,
)

/** The waker record of SPEC 8.1 and the app's own wakeups, from the battery history and the alarm dumps. */
public object Wakers {
    /**
     * The event log and the history do not stamp times from the same clock reading, so a run that
     * starts this close to the end of an IDLE stretch belongs to the maintenance window that follows
     * (ADR 0019).
     */
    public val CLOCK_SKEW: Duration = Duration.ofSeconds(2)

    /** A source that starts this soon after screen off still covers the session (ADR 0018). */
    public val COVERAGE_SLACK: Duration = Duration.ofMinutes(1)

    public fun summarize(input: WakerInput): WakerSummary {
        val session = input.session
        // The batch reads the history after screen on, so what is open at its end ran to the session's end.
        val replay = input.history?.let { HistoryReplay.of(it, session.end) }
        val runs = replay?.runs.orEmpty().filter { it.interval.touches(session) }
        val points = replay?.points.orEmpty().filter { it.time in session }
        val before = input.alarmsBefore?.takeIf(::complete)
        val after = input.alarmsAfter?.takeIf(::complete)
        val alarms = if (before != null && after != null) alarmDelta(before, after) else null
        val coverage = input.history?.start?.let { start ->
            Interval(maxOf(session.start, Instant.ofEpochMilli(start)), session.end).takeIf { it.duration > Duration.ZERO }
        }
        val own = own(input, runs, points, alarms.orEmpty())
        val covered = coverage != null && Duration.between(session.start, coverage.start) <= COVERAGE_SLACK
        return WakerSummary(
            apps = apps(session, runs, points, alarms.orEmpty()),
            wakeReasons = points.filter { it.name == WAKE_REASON }.groupingBy { it.tag }.eachCount()
                .map { (reason, count) -> WakeReason(reason, count) }
                .sortedWith(compareByDescending<WakeReason> { it.count }.thenBy { it.reason }),
            mobileRadio = replay?.radio.orEmpty().sumOf(session),
            unattributedWakelock = runs.filter { it.kind == RunKind.WAKELOCK && it.uid == null }.map { it.interval }.sumOf(session),
            historyCoverage = coverage,
            alarmsRead = alarms != null,
            own = own,
            ownWakeups = own.total.takeIf { alarms != null && covered },
        )
    }

    /** A line of the section that did not parse may be any app's, the app's own included, so it leaves the count unknown. */
    private fun complete(stats: AlarmStats): Boolean = stats.found && stats.unparsed.isEmpty()

    /**
     * The alarms each app delivered between two reads of `dumpsys alarm`, per tag. A counter that is
     * lower in [after] restarted (reboot or reinstall), so its value in [after] is the count.
     */
    public fun alarmDelta(before: AlarmStats, after: AlarmStats): List<AppAlarmStats> {
        val earlier = before.apps.associateBy { it.uid to it.packageName }
        return after.apps.mapNotNull { app ->
            val old = earlier[app.uid to app.packageName]
            val oldTags = old?.tags.orEmpty().associateBy { it.tag }
            val tags = app.tags.map { tag ->
                val was = oldTags[tag.tag]
                AlarmTagStats(tag.tag, delta(tag.wakeups, was?.wakeups), delta(tag.alarms, was?.alarms))
            }.filter { it.alarms > 0 || it.wakeups > 0 }
            app.copy(wakeups = delta(app.wakeups, old?.wakeups), tags = tags).takeIf { it.wakeups > 0 || tags.isNotEmpty() }
        }
    }

    private fun delta(now: Int, before: Int?): Int = if (before == null || now < before) now else now - before

    private fun own(input: WakerInput, runs: List<Run>, points: List<Point>, alarms: List<AppAlarmStats>): OwnWakeups {
        val self = input.selfUid
        val mine = runs.filter { it.uid == self }
        val (inIdle, outside) = mine.partition { run ->
            input.deepIdle.any { idle -> run.interval.start in Interval(idle.start, idle.end - CLOCK_SKEW) }
        }
        return OwnWakeups(
            alarmWakeups = alarms.filter { it.uid == self }.sumOf { it.wakeups },
            networkWakeups = points.count { it.name == WAKEUP_AP && it.uid == self },
            inIdle = inIdle,
            outsideIdle = outside,
        )
    }

    private fun apps(session: Interval, runs: List<Run>, points: List<Point>, alarms: List<AppAlarmStats>): List<AppWakers> {
        val network = points.filter { it.name == WAKEUP_AP }.groupingBy { it.uid }.eachCount()
        val byUid = runs.mapNotNull { run -> run.uid?.let { it to run } }.groupBy({ it.first }, { it.second })
        val alarmsByUid = alarms.groupBy { it.uid }
        val uids = (network.keys + byUid.keys + alarmsByUid.keys).toSortedSet()
        return uids.map { uid ->
            val mine = byUid[uid].orEmpty()
            fun of(kind: RunKind) = mine.filter { it.kind == kind }
            val appAlarms = alarmsByUid[uid].orEmpty()
            AppWakers(
                uid = uid,
                packages = appAlarms.map { it.packageName },
                alarmWakeups = appAlarms.sumOf { it.wakeups },
                networkWakeups = network[uid] ?: 0,
                alarms = appAlarms.flatMap { it.tags },
                jobs = of(RunKind.JOB).size,
                jobTime = of(RunKind.JOB).map { it.interval }.sumOf(session),
                syncs = of(RunKind.SYNC).size,
                syncTime = of(RunKind.SYNC).map { it.interval }.sumOf(session),
                wakelockTime = of(RunKind.WAKELOCK).map { it.interval }.sumOf(session),
                longWakelocks = of(RunKind.LONG_WAKELOCK).size,
                longWakelockTime = of(RunKind.LONG_WAKELOCK).map { it.interval }.sumOf(session),
            )
        }.sortedWith(compareByDescending<AppWakers> { it.wakeups }.thenByDescending { it.wakelockTime }.thenBy { it.uid })
    }
}

internal const val WAKEUP_AP = "wakeupap"
internal const val WAKE_REASON = "wake_reason"

/** A one-moment history event: `wakeupap` or `wake_reason`. */
internal data class Point(val name: String, val uid: Int, val tag: String, val time: Instant)
