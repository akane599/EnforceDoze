// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.Nights
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.DEEP_MARGIN
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.LONG_SESSION
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_JOIN_GAP
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_LENGTH
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.NIGHT_SPACING
import io.github.akane599.enforcedoze.recommend.RecommendationPolicy.SHORT_SESSION
import java.time.Duration
import java.time.ZonedDateTime

/** Why a signal has no value: the why card says so instead of guessing (ADR 0026). */
public enum class Unmeasured {
    /** No session of the kind the signal needs. */
    NO_SESSIONS,

    /** Sessions exist, but none ran under stock settings (the owner's previous setup, ADR 0026). */
    NO_STOCK_SESSIONS,

    /** Sessions of the right kind exist, but their sources did not record this fact. */
    NOT_RECORDED,

    /** Every such session had the sensors restricted (lever C or another tool), so motion could not show. */
    SENSORS_RESTRICTED,
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

/** One night (signal N, ADR 0031, 0032): a joined unplugged rest of 4 h or more, the longest in the 24 hours around it. */
public data class Night(val start: ZonedDateTime, val end: ZonedDateTime)

/** The signals of SPEC 7.2 (Q is the answer, not a signal), each from the sessions it may use. */
public data class Signals(
    /** Total screen-off time inside the history, sessions clipped to it. */
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
    /** N: the nights, [NIGHT_SPACING] or more apart, oldest first; each from its first session's start to its last one's end. */
    val nights: List<Night>,
    /** N: how many nights the history could hold. */
    val availableNights: Int,
    /** D: share of long unplugged stock screen-off time spent in deep idle. */
    val deepShare: Measured<Double>,
    /** R: battery percent per hour over long unplugged stock sessions. */
    val drainPerHour: Measured<Double>,
) {
    public companion object {
        /** The signals of [history]; pure, so each one is tested on its own (SPEC 7.6). */
        public fun from(history: History): Signals {
            val zone = history.zone
            // S counts screen-off time inside the history; a session is long or short by its whole length.
            val overlapping = history.sessions.filter { it.end > history.from && it.start < history.to }
            val clip: (ScreenOffSession) -> Duration = { Duration.between(maxOf(it.start, history.from), minOf(it.end, history.to)) }
            // M and N take the sessions that began inside it, so a night is never counted that was not available.
            val started = overlapping.filter { it.start >= history.from }
            // A night joins its sessions across short wake-ups (ADR 0031); every session of a night is night time.
            val runs = nightRuns(overlapping)
            val night = runs.flatten().toHashSet()
            val daytime = overlapping.filterNot { it in night }
            val screenOff = total(overlapping, clip)
            val days = history.span.toMillis().toDouble() / Duration.ofDays(1).toMillis()
            val stock = history.sessions.filter { it.provenance == Provenance.STOCK }
            return Signals(
                screenOff = screenOff,
                span = history.span,
                screenOffHoursPerDay = if (days == 0.0) 0.0 else hours(screenOff) / days,
                longShare = ratio(total(overlapping.filter { it.duration >= LONG_SESSION }, clip), screenOff),
                shortDaytimeShare = shortShare(daytime, clip),
                neverDeep = neverDeep(started.filterNot { it in night }),
                motionResetsPerHour = motionResets(started.filterNot { it in night }),
                nights = runs.filter { it.first().start >= history.from }.map {
                    Night(it.first().start.atZone(zone), it.last().end.atZone(zone))
                },
                availableNights = availableNights(history),
                deepShare = stock(history.sessions, stock, {
                    it.timeInDeep != null
                }) { ratio(total(it) { s -> s.timeInDeep!! }, total(it)) },
                drainPerHour = stock(history.sessions, stock, { it.batteryDrop != null }) { list ->
                    list.sumOf { it.batteryDrop!! } / hours(total(list))
                },
            )
        }

        /**
         * SPEC 7.2 N with ADR 0031 and 0032: runs of unplugged sessions joined across screen-on gaps under
         * [NIGHT_JOIN_GAP], with [NIGHT_LENGTH] of screen-off in all, at any hour; of two closer than [NIGHT_SPACING]
         * only the longer. [sessions] must hold every session of the span, plugged ones too: one between two unplugged
         * ones ends a run.
         */
        internal fun nightRuns(sessions: List<ScreenOffSession>): List<List<ScreenOffSession>> =
            Nights.find(sessions, Nights.Rule(NIGHT_LENGTH, NIGHT_JOIN_GAP, NIGHT_SPACING)) {
                Nights.Stretch(it.start, it.end, it.unplugged)
            }

        private fun shortShare(daytime: List<ScreenOffSession>, clip: (ScreenOffSession) -> Duration): Measured<Double> {
            val unplugged = daytime.filter { it.unplugged }
            if (unplugged.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            return Measured.Known(
                ratio(
                    total(
                        unplugged.filter {
                            it.duration < SHORT_SESSION
                        },
                        clip,
                    ),
                    total(unplugged, clip),
                ),
                unplugged.size,
            )
        }

        /** Long unplugged daytime sessions with the sensors free, long enough that deep idle was due. */
        private fun neverDeep(daytime: List<ScreenOffSession>): Measured<Share> {
            val free = free(daytime).let { it.valueOrNull ?: return it as Measured.Unknown }
            val due = free.filter { s -> s.expectedToDeep != null && s.timeInDeep != null && s.duration >= s.expectedToDeep + DEEP_MARGIN }
            if (due.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(Share(due.count { it.timeInDeep!!.isZero }, due.size), due.size)
        }

        private fun motionResets(daytime: List<ScreenOffSession>): Measured<Double> {
            val free = free(daytime).let { it.valueOrNull ?: return it as Measured.Unknown }
            val counted = free.filter { it.motionResets != null }
            if (counted.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(counted.sumOf { it.motionResets!! } / hours(total(counted)), counted.size)
        }

        /** M's sessions: long, unplugged, sensors free; or why there are none. */
        private fun free(daytime: List<ScreenOffSession>): Measured<List<ScreenOffSession>> {
            val long = longUnplugged(daytime)
            if (long.isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            val free = long.filter { it.sensorsRestricted == false }
            return when {
                free.isNotEmpty() -> Measured.Known(free, free.size)
                long.any { it.sensorsRestricted == true } -> Measured.Unknown(Unmeasured.SENSORS_RESTRICTED)
                else -> Measured.Unknown(Unmeasured.NOT_RECORDED)
            }
        }

        /** D and R: long unplugged sessions under stock settings, of any age, that recorded the fact [has]. */
        private fun stock(
            all: List<ScreenOffSession>,
            stock: List<ScreenOffSession>,
            has: (ScreenOffSession) -> Boolean,
            value: (List<ScreenOffSession>) -> Double,
        ): Measured<Double> {
            if (longUnplugged(all).isEmpty()) return Measured.Unknown(Unmeasured.NO_SESSIONS)
            val long = longUnplugged(stock)
            if (long.isEmpty()) return Measured.Unknown(Unmeasured.NO_STOCK_SESSIONS)
            val known = long.filter(has)
            if (known.isEmpty()) return Measured.Unknown(Unmeasured.NOT_RECORDED)
            return Measured.Known(value(known), known.size)
        }

        /** One night a day: how many days' nights, a day apart, fit inside the history with their [NIGHT_LENGTH]. */
        private fun availableNights(history: History): Int =
            if (history.span < NIGHT_LENGTH) 0 else (history.span.minus(NIGHT_LENGTH).toMillis() / DAY.toMillis()).toInt() + 1

        private val DAY: Duration = Duration.ofDays(1)
    }
}

private fun longUnplugged(sessions: List<ScreenOffSession>) = sessions.filter { it.unplugged && it.duration >= LONG_SESSION }

private fun total(sessions: List<ScreenOffSession>, of: (ScreenOffSession) -> Duration = { it.duration }): Duration =
    sessions.fold(Duration.ZERO) { sum, s -> sum + of(s) }

private fun ratio(part: Duration, whole: Duration): Double = if (whole.isZero) 0.0 else part.toMillis().toDouble() / whole.toMillis()

internal fun hours(duration: Duration): Double = duration.toMillis().toDouble() / Duration.ofHours(1).toMillis()
