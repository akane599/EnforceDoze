// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import java.time.Duration
import java.time.LocalTime

/**
 * The recommendation policy (SPEC 7.3, ADR 0026): `recommend(history, answers)` is pure and fast, so every rule
 * and every boundary is a JVM test. Every threshold is a named constant here with its unit and why; the numbers
 * are SPEC 7's starting points, not facts about all phones, and changing one needs an ADR.
 */
public object RecommendationPolicy {
    /** Rule 1, hours of screen-off time in total: less than a day of it says too little to pick anything but Everyday. */
    public val THIN_HISTORY: Duration = Duration.ofHours(24)

    /** Signal S, minutes: a session this long gives Doze's own walk a chance to matter. */
    public val LONG_SESSION: Duration = Duration.ofMinutes(30)

    /** Signal S, minutes: a session this short ends before stock Doze gets anywhere; forced idle (Classic) helps there. */
    public val SHORT_SESSION: Duration = Duration.ofMinutes(15)

    /** Signal M, minutes past the expected walk: deep idle counts as never reached only then, so timer slack is not motion. */
    public val DEEP_MARGIN: Duration = Duration.ofMinutes(2)

    /** Rule 2, share of long unplugged daytime sessions that never reached deep idle: this often, movement is the problem. */
    public const val POCKET_NEVER_DEEP_SHARE: Double = 0.40

    /** Rule 2, motion resets per screen-off hour: this often, the walk keeps starting over in a pocket. */
    public const val POCKET_RESETS_PER_HOUR: Double = 3.0

    /** Rule 2, share of daytime screen-off time in short sessions: short sessions dominate, so forcing idle pays. */
    public const val CLASSIC_SHORT_SHARE: Double = 0.60

    /** Signal N, hours: a night session is at least this long, enough for the Night profile's long gaps to pay. */
    public val NIGHT_LENGTH: Duration = Duration.ofHours(4)

    /** Signal N, local time: a night session starts at or after this... */
    public val NIGHT_STARTS_FROM: LocalTime = LocalTime.of(21, 0)

    /** ...and before this, the next morning. */
    public val NIGHT_STARTS_UNTIL: LocalTime = LocalTime.of(3, 0)

    /** Rule 3, nights: N must hold this often (or on every available night when fewer), so one late night is not a habit. */
    public const val NIGHTS_NEEDED: Int = 3

    /** Rule 3, minutes: the learned window's median start and end are rounded to this step... */
    public val WINDOW_STEP: Duration = Duration.ofMinutes(15)

    /** ...and widened by this on each side, so an ordinary early or late night still falls inside. */
    public val WINDOW_PADDING: Duration = Duration.ofMinutes(30)

    /** Rule 4, battery percent per hour under stock settings: at or below this, little is left to save. */
    public const val DOZING_WELL_DRAIN_PER_HOUR: Double = 0.3

    /** Rule 4, share of long unplugged stock screen-off time in deep idle: at or above this, Doze already works. */
    public const val DOZING_WELL_DEEP_SHARE: Double = 0.70

    /** Rule 5, battery percent: the one-tap Strict card appears at or below this, unplugged. */
    public const val STRICT_CARD_BATTERY_PERCENT: Int = 20

    /** SPEC 7.4, days of history: High confidence from here (with D and R measured, ADR 0026). */
    public val HIGH_CONFIDENCE_FROM: Duration = Duration.ofDays(7)

    /** SPEC 7.5, share of the stock drain saved: below this after [LOW_GAIN_NIGHTS] nights, a stricter plan costs for nothing. */
    public const val LOW_GAIN: Double = 0.10

    /** SPEC 7.5, unplugged nights on the plan before its gain is judged. */
    public const val LOW_GAIN_NIGHTS: Int = 3

    /** SPEC 7.5, days: feedback older than this no longer moves the plan (ADR 0026). */
    public val FEEDBACK_WINDOW: Duration = Duration.ofDays(30)

    /** The plan for [history] and [answers], with its confidence, its reasons and its signals (SPEC 7.6). */
    public fun recommend(history: History, answers: Answers): Recommendation {
        val signals = Signals.from(history)
        val notes = mutableSetOf<Note>()
        if (signals.deepShare is Measured.Unknown || signals.drainPerHour is Measured.Unknown) notes += Note.STOCK_NOT_MEASURED
        if (signals.screenOff < THIN_HISTORY) {
            notes += Note.REFINES_AFTER_FIRST_NIGHT
            return Recommendation(Plan(EVERYDAY), Confidence.LOW, listOf(Reason.ThinHistory(signals.screenOff)), notes, signals)
        }
        val reasons = mutableListOf<Reason>(Reason.ScreenOffPerDay(signals.screenOffHoursPerDay))
        var day = daySlot(signals, answers.urgency, reasons)
        val night = nightSlot(signals, answers.urgency, reasons)
        if (dozingWell(signals)) {
            if (day == POCKET || day == CLASSIC) {
                day = EVERYDAY
                // They no longer explain the plan; the dozing-well line does.
                reasons.removeAll { it is Reason.MotionKeptAwake || it is Reason.MotionResets || it is Reason.ShortSessions }
            }
            reasons += Reason.DozingWell(signals.drainPerHour.valueOrNull!!, signals.deepShare.valueOrNull!!)
            notes += Note.LITTLE_TO_GAIN
        }
        var plan = Plan(day, night)
        val step = FeedbackLoop.step(history, signals)
        val current = history.feedback.current?.plan
        if (step != null && current != null) {
            val gentler = FeedbackLoop.capForUrgency(FeedbackLoop.gentler(current), answers.urgency)
            if (gentler != current) {
                plan = gentler
                reasons.add(0, step)
                notes += Note.GENTLER
            }
        }
        return Recommendation(plan, confidence(history, signals), reasons, notes, signals)
    }

    /** Rule 2. */
    private fun daySlot(signals: Signals, urgency: Urgency, reasons: MutableList<Reason>): String {
        val never = signals.neverDeep.valueOrNull?.takeIf { it.value >= POCKET_NEVER_DEEP_SHARE }
        val resets = signals.motionResetsPerHour.valueOrNull?.takeIf { it >= POCKET_RESETS_PER_HOUR }
        if (never != null || resets != null) {
            never?.let { reasons += Reason.MotionKeptAwake(it.count, it.of) }
            resets?.let { reasons += Reason.MotionResets(it) }
            return POCKET
        }
        val short = signals.shortDaytimeShare.valueOrNull?.takeIf { it >= CLASSIC_SHORT_SHARE }
        // Only It can wait allows Classic, so Right away is capped at Everyday or Pocket here as well.
        if (short != null && urgency == Urgency.IT_CAN_WAIT) {
            reasons += Reason.ShortSessions(short)
            return CLASSIC
        }
        return EVERYDAY
    }

    /** Rule 3: N on enough of the available nights, and messages may wait. */
    private fun nightSlot(signals: Signals, urgency: Urgency, reasons: MutableList<Reason>): NightSlot? {
        val nights = signals.nights
        if (nights.isEmpty() || nights.size < minOf(NIGHTS_NEEDED, signals.availableNights)) return null
        if (urgency == Urgency.RIGHT_AWAY) {
            reasons += Reason.MessagesRightAway(listOf(NIGHT))
            return null
        }
        val window = NightWindow.learn(nights)
        reasons += Reason.UnpluggedNights(nights.size, signals.availableNights, window.start, window.end)
        return window
    }

    /** Rule 4: both D and R measured under stock settings, and both good. */
    private fun dozingWell(signals: Signals): Boolean {
        val drain = signals.drainPerHour.valueOrNull ?: return false
        val deep = signals.deepShare.valueOrNull ?: return false
        return drain <= DOZING_WELL_DRAIN_PER_HOUR && deep >= DOZING_WELL_DEEP_SHARE
    }

    /** SPEC 7.4 with ADR 0026: Low is rule 1; High needs a week and D and R measured, so an unmeasured stock stays Medium. */
    private fun confidence(history: History, signals: Signals): Confidence {
        val stockMeasured = signals.deepShare is Measured.Known && signals.drainPerHour is Measured.Known
        return if (history.span >= HIGH_CONFIDENCE_FROM && stockMeasured) Confidence.HIGH else Confidence.MEDIUM
    }

    internal val STOCK = BuiltInProfiles.STOCK.id
    internal val EVERYDAY = BuiltInProfiles.EVERYDAY.id
    internal val POCKET = BuiltInProfiles.POCKET.id
    internal val CLASSIC = BuiltInProfiles.CLASSIC.id
    internal val STRICT = BuiltInProfiles.STRICT.id
    internal val NIGHT = BuiltInProfiles.NIGHT.id
}
