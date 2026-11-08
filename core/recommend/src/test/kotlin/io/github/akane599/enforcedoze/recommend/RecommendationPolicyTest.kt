// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Test
import java.time.Duration

/** One test per threshold of SPEC 7.3, on both sides of it. */
class RecommendationPolicyTest {
    private fun neverDeep(day: Int, start: String) = session(day, start, 60).copy(timeInDeep = Duration.ZERO, motionResets = 0)

    @Test
    fun `THIN_HISTORY - under 24 h of screen-off time is Everyday at Low`() {
        val thin = recommend(history(2, listOf(charging(0), session(1, "13:00", 959).copy(plugged = true))))
        assertThat(thin.plan).isEqualTo(Plan("everyday"))
        assertThat(thin.confidence).isEqualTo(Confidence.LOW)
        assertThat(thin.reasons).containsExactly(Reason.ThinHistory(Duration.ofMinutes(1439)))
        assertThat(thin.notes).contains(Note.REFINES_AFTER_FIRST_NIGHT)
        val enough = recommend(history(2, listOf(charging(0), session(1, "13:00", 960).copy(plugged = true))))
        assertThat(enough.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(enough.notes).doesNotContain(Note.REFINES_AFTER_FIRST_NIGHT)
    }

    @Test
    fun `POCKET_NEVER_DEEP_SHARE - 40 percent of long daytime sessions without deep idle is Pocket`() {
        val at40 = recommend(history(3, quiet(3) + neverDeep(0, "16:00") + neverDeep(1, "16:00")))
        assertThat(at40.plan).isEqualTo(Plan("pocket"))
        assertThat(at40.reasons).contains(Reason.MotionKeptAwake(2, 5))
        val under = recommend(history(5, quiet(5) + neverDeep(0, "16:00") + neverDeep(1, "16:00") + neverDeep(2, "16:00")))
        assertThat(under.plan).isEqualTo(Plan("everyday"))
    }

    @Test
    fun `POCKET_RESETS_PER_HOUR - 3 motion resets per hour is Pocket`() {
        fun moving(resets: Int) = session(1, "16:00", 60).copy(timeInDeep = 10.min, motionResets = resets)
        val at3 = recommend(history(3, quiet(3) + moving(12)))
        assertThat(at3.plan).isEqualTo(Plan("pocket"))
        assertThat(at3.reasons).contains(Reason.MotionResets(3.0))
        assertThat(recommend(history(3, quiet(3) + moving(11))).plan).isEqualTo(Plan("everyday"))
    }

    @Test
    fun `CLASSIC_SHORT_SHARE - 60 percent of daytime in short sessions is Classic, only when messages can wait`() {
        val at60 = history(3, quiet(3) + shorts(1, 14) + shorts(2, 13))
        assertThat(recommend(at60, Urgency.IT_CAN_WAIT).plan).isEqualTo(Plan("classic"))
        assertThat(recommend(at60, Urgency.IT_CAN_WAIT).reasons).contains(Reason.ShortSessions(0.6))
        assertThat(recommend(at60, Urgency.WITHIN_THE_HOUR).plan).isEqualTo(Plan("everyday"))
        assertThat(recommend(at60, Urgency.RIGHT_AWAY).plan).isEqualTo(Plan("everyday"))
        val under = history(3, quiet(3) + shorts(1, 13) + shorts(2, 13))
        assertThat(recommend(under, Urgency.IT_CAN_WAIT).plan).isEqualTo(Plan("everyday"))
    }

    @Test
    fun `Pocket comes before Classic`() {
        val both = history(3, quiet(3) + shorts(1, 14) + shorts(2, 13) + neverDeep(0, "16:00") + neverDeep(1, "16:00"))
        assertThat(recommend(both, Urgency.IT_CAN_WAIT).plan).isEqualTo(Plan("pocket"))
    }

    @Test
    fun `NIGHTS_NEEDED - 3 unplugged nights add Night`() {
        fun week(sleeping: Int) = history(7, (0 until 7).flatMap { listOf(afternoon(it), if (it < sleeping) sleep(it) else charging(it)) })
        val three = recommend(week(3))
        assertThat(three.plan.night).isNotNull()
        assertThat(three.reasons).contains(Reason.UnpluggedNights(3, 7, three.plan.night!!.start, three.plan.night!!.end))
        assertThat(recommend(week(2)).plan.night).isNull()
        assertThat(recommend(week(3), Urgency.RIGHT_AWAY).plan.night).isNull()
    }

    @Test
    fun `DOZING_WELL - stock drain up to 0_3 percent an hour with 70 percent deep caps the day slot`() {
        // Ten hours away a day, the walk restarting three times an hour, yet deep idle most of the time.
        fun day(it: Int, deep: Int, drop: Double) =
            session(it, "08:00", 600).copy(timeInDeep = deep.min, motionResets = 30, batteryDrop = drop)
        fun week(deep: Int, drop: Double) = history(7, (0 until 7).flatMap { listOf(day(it, deep, drop), charging(it)) })
        val well = recommend(week(420, 3.0))
        assertThat(well.plan).isEqualTo(Plan("everyday"))
        assertThat(well.notes).contains(Note.LITTLE_TO_GAIN)
        assertThat(well.reasons).contains(Reason.DozingWell(0.3, 0.7))
        assertThat(well.reasons.filterIsInstance<Reason.MotionResets>()).isEmpty()
        assertThat(recommend(week(420, 3.1)).plan).isEqualTo(Plan("pocket"))
        assertThat(recommend(week(419, 3.0)).plan).isEqualTo(Plan("pocket"))
    }

    @Test
    fun `HIGH_CONFIDENCE_FROM - a week of history with stock measured is High`() {
        assertThat(recommend(history(7, quiet(7))).confidence).isEqualTo(Confidence.HIGH)
        val almost = History(at(0, "12:00"), at(7, "11:59"), ZONE, quiet(7))
        assertThat(recommend(almost).confidence).isEqualTo(Confidence.MEDIUM)
        val unmeasured = history(14, quiet(14).map { it.copy(provenance = Provenance.UNKNOWN) })
        assertThat(recommend(unmeasured).confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(recommend(unmeasured).notes).contains(Note.STOCK_NOT_MEASURED)
    }

    @Test
    fun `the why card starts with the screen-off hours a day`() {
        val reasons = recommend(history(7, quiet(7))).reasons
        assertThat(reasons.first()).isEqualTo(Reason.ScreenOffPerDay(9.0))
    }
}
