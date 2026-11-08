// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.DEEP_MARGIN
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.LONG_SESSION
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_LENGTH
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_STARTS_FROM
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_STARTS_UNTIL
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.SHORT_SESSION
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Why a signal has no value: the why card says so instead of guessing (ADR 0026). */
public enum class Unmeasured {
    /** No session of the kind the signal needs. */
    NO_SESSIONS,

    /** Sessions exist, but none ran under stock settings (the owner's previous setup, ADR 0026). */
    NO_STOCK_SESSIONS,

    /** Sessions of the right kind exist, but their sources did not record this fact. */
    NOT_RECORDED,
}

/** A signal's value with the number of sessions behind it, or why it has none. */
public sealed interface Measured<out T> {
    public data class Known<T>(val value: T, val sessions: Int) : Measured<T>

    public data class Unknown(val reason: Unmeasured) : Measured<Nothing>

    public val valueOrNull: T? get() = (this as? Known<T>)?.value
}

/** [count] of [of] sessions; the why card shows both ("6 of the last 10"). */
public data class Share(val count: Int, val of: Int) {
    val value: Double get() = if (of == 0) 0.0 else count.toDouble() / of
}

/** One unplugged night (signal N): the longest qualifying session that started in that night's window. */
public data class Night(val start: ZonedDateTime, val end: ZonedDateTime)

/** The signals of SPEC 7.2 (Q is the answer, not a signal), each from the sessions it may use. */
public data class Signals(
    /** Total screen-off time in the history. */
    val screenOff: Duration,
    val span: Duration,
    /** S: screen-off hours per day of history. */
    val screenOffHoursPerDay: Double,
    /** S: share of screen-off time in long sessions (≥ [LONG_SESSION]). */
    val longShare: Double,
    /** S: share of unplugged daytime screen-off time in short sessions (< [SHORT_SESSION]); Doze does not run on the charger. */
    val shortDaytimeShare: Measured<Double>,
    /** M: long unplugged daytime sessions that should have reached deep idle and never did. */
    val neverDeep: Measured<Share>,
    /** M: motion resets per hour of long unplugged daytime screen-off with the sensors free. */
    val motionResetsPerHour: Measured<Double>,
    /** N: the qualifying nights, one per night, oldest first. */
    val nights: List<Night>,
    /** N: nights the history could hold a qualifying session in. */
    val availableNights: Int,
    /** D: share of long unplugged stock screen-off time spent in deep idle. */
    val deepShare: Measured<Double>,
    /** R: battery percent per hour over long unplugged stock sessions. */
    val drainPerHour: Measured<Double>,
) {
    public companion object {
        /** The signals of [history]; pure, so each one is tested on its own (SPEC 7.6). */
        public fun from(history: History): Signals {
            val sessions = history.sessions.filter { it.end > history.from && it.start < history.to }
            val nightSessions = sessions.filter { isNight(it, history.zone) }
            val daytime = sessions - nightSessions.toSet()
            val screenOff = total(sessions)
            val days = history.span.toMillis().toDouble() / Duration.ofDays(1).toMillis()
            return Signals(
                screenOff = screenOff,
                span = history.span,
                screenOffHoursPerDay = if (days == 0.0) 0.0 else hours(screenOff) / days,
                longShare = ratio(total(sessions.filter { it.duration >= LONG_SESSION }), screenOff),
                shortDaytimeShare = shortShare(daytime),
                neverDeep = neverDeep(daytime),
                motionResetsPerHour = motionResets(daytime),
                nights = nights(nightSessions, history.zone),
                availableNights = availableNights(history),
                deepShare = stock(sessions, { it.timeInDeep != null }) { ratio(total(it) { s -> s.timeInDeep!! }, total(it)) },
                drainPerHour = stock(sessions, { it.batteryDrop != null }) { list -> list.sumOf { it.batteryDrop!! } / hours(total(list)) },
            )
        }

        /** SPEC 7.2 N: unplugged, at least [NIGHT_LENGTH], starting between [NIGHT_STARTS_FROM] and [NIGHT_STARTS_UNTIL]. */
        internal fun isNight(session: ScreenOffSession, zone: ZoneId): Boolean {
            val start = session.start.atZone(zone).toLocalTime()
            return session.unplugged && session.duration >= NIGHT_LENGTH && (start >= NIGHT_STARTS_FROM || start < NIGHT_STARTS_UNTIL)
        }

        private fun shortShare(daytime: List<ScreenOffSession>): Measured<Double> {
            val unplugged = daytime.filter { it.unplugged }
            if (unplugged.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            return Measured.Known(ratio(total(unplugged.filter { it.duration < SHORT_SESSION }), total(unplugged)), unplugged.size)
        }

        /** Long unplugged daytime sessions with the sensors free, long enough that deep idle was due. */
        private fun neverDeep(daytime: List<ScreenOffSession>): Measured<Share> {
            val free = longUnplugged(daytime).filter { it.sensorsRestricted == false }
            if (free.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            val due = free.filter { s -> s.expectedToDeep != null && s.timeInDeep != null && s.duration >= s.expectedToDeep + DEEP_MARGIN }
            if (due.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(Share(due.count { it.timeInDeep!!.isZero }, due.size), due.size)
        }

        private fun motionResets(daytime: List<ScreenOffSession>): Measured<Double> {
            val free = longUnplugged(daytime).filter { it.sensorsRestricted == false }
            if (free.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            val counted = free.filter { it.motionResets != null }
            if (counted.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(counted.sumOf { it.motionResets!! } / hours(total(counted)), counted.size)
        }

        /** D and R: long unplugged sessions under stock settings that recorded the fact [has]. */
        private fun stock(
            sessions: List<ScreenOffSession>,
            has: (ScreenOffSession) -> Boolean,
            value: (List<ScreenOffSession>) -> Double,
        ): Measured<Double> {
            val long = longUnplugged(sessions)
            if (long.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            val stock = long.filter { it.provenance == Provenance.STOCK }
            if (stock.isEmpty()) return Measured.Unknown(Unmeasured.NO_STOCK_SESSIONS)
            val known = stock.filter(has)
            if (known.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(value(known), known.size)
        }

        /** The night a start belongs to is the date its evening began: 01:00 on the 10th is the night of the 9th. */
        private fun nights(sessions: List<ScreenOffSession>, zone: ZoneId): List<Night> = sessions
            .groupBy { nightOf(it.start.atZone(zone)) }
            .map { (_, same) -> same.maxBy { it.duration }.let { Night(it.start.atZone(zone), it.end.atZone(zone)) } }
            .sortedBy { it.start }

        internal fun nightOf(time: ZonedDateTime): LocalDate =
            if (time.toLocalTime() < NIGHT_STARTS_UNTIL) time.toLocalDate().minusDays(1) else time.toLocalDate()

        /**
         * A night is available when the history could hold a qualifying session in it: some start inside its
         * window, at or after the history's start, with [NIGHT_LENGTH] still inside the history.
         */
        private fun availableNights(history: History): Int {
            val zone = history.zone
            val first = history.from.atZone(zone).toLocalDate().minusDays(1)
            val last = history.to.atZone(zone).toLocalDate()
            return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.count { date ->
                val opens = date.atTime(NIGHT_STARTS_FROM).atZone(zone).toInstant()
                val closes = date.plusDays(1).atTime(NIGHT_STARTS_UNTIL).atZone(zone).toInstant()
                val earliest = maxOf(opens, history.from)
                earliest < closes && !earliest.plus(NIGHT_LENGTH).isAfter(history.to)
            }
        }

        internal fun hours(duration: Duration): Double = duration.toMillis().toDouble() / Duration.ofHours(1).toMillis()
    }
}

private fun longUnplugged(sessions: List<ScreenOffSession>) = sessions.filter { it.unplugged && it.duration >= LONG_SESSION }

private fun total(sessions: List<ScreenOffSession>, of: (ScreenOffSession) -> Duration = { it.duration }): Duration =
    sessions.fold(Duration.ZERO) { sum, s -> sum + of(s) }

private fun ratio(part: Duration, whole: Duration): Double = if (whole.isZero) 0.0 else part.toMillis().toDouble() / whole.toMillis()
