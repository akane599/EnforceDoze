// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Test
import java.time.Duration

class FeedbackLoopTest {
    private val night = NightSlot("night", NightSlot.DEFAULT_START, NightSlot.DEFAULT_END)

    @Test
    fun `one notch gentler - Strict, Classic, Night off, Everyday`() {
        assertThat(FeedbackLoop.gentler(Plan("strict", night))).isEqualTo(Plan("classic", night))
        assertThat(FeedbackLoop.gentler(Plan("classic", night))).isEqualTo(Plan("classic"))
        assertThat(FeedbackLoop.gentler(Plan("classic"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.gentler(Plan("pocket", night))).isEqualTo(Plan("pocket"))
        assertThat(FeedbackLoop.gentler(Plan("pocket"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.gentler(Plan("custom-1a2b"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.gentler(Plan("everyday"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.gentler(Plan("stock"))).isEqualTo(Plan("stock"))
    }

    @Test
    fun `at most - the gentler day wins, and the night stays only under a ceiling that has one`() {
        val other = NightSlot("night", java.time.LocalTime.of(22, 0), java.time.LocalTime.of(6, 0))
        assertThat(FeedbackLoop.atMost(Plan("strict", night), Plan("classic", night))).isEqualTo(Plan("classic", night))
        assertThat(FeedbackLoop.atMost(Plan("pocket", night), Plan("everyday"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.atMost(Plan("everyday", night), Plan("classic"))).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.atMost(Plan("stock"), Plan("classic", night))).isEqualTo(Plan("stock"))
        // Pocket and a custom profile rank alike; the plan keeps its own night window.
        assertThat(FeedbackLoop.atMost(Plan("custom-1a2b", night), Plan("pocket", other))).isEqualTo(Plan("custom-1a2b", night))
    }

    /** A week of stock nights at 1 %/h, then [plan] from noon on day 7 with its own nights after. */
    private fun onPlan(
        plan: Plan,
        nights: List<ScreenOffSession>,
        late: List<java.time.Instant> = emptyList(),
        stockDrop: Double = 10.0,
    ): History {
        val stock = (0 until 7).map { session(it, "23:00", 600).copy(timeInDeep = 300.min, batteryDrop = stockDrop) }
        val tuned = nights.map { it.copy(provenance = Provenance.TUNED) }
        return history(7 + nights.size, stock + tuned, Feedback(listOf(AppliedPlan(plan, at(7, "12:00"))), late))
    }

    private fun steps(history: History) = FeedbackLoop.steps(history, Signals.from(history))

    private fun step(history: History) = steps(history).firstOrNull()?.reason

    private fun nights(count: Int, drop: Double) = (7 until 7 + count).map { session(it, "23:00", 600).copy(batteryDrop = drop) }

    @Test
    fun `exactly 10 percent saved is enough, a little less steps`() {
        val plan = Plan("everyday", night)
        assertThat(step(onPlan(plan, nights(3, 9.0)))).isNull()
        val low = steps(onPlan(plan, nights(3, 9.1))).single()
        assertThat((low.reason as Reason.LowGain).nights).isEqualTo(3)
        assertThat((low.reason as Reason.LowGain).gain).isWithin(1e-9).of(0.09)
        assertThat(low.ceiling).isEqualTo(Plan("everyday"))
        // 8.118 is 90 percent of 9.02, though the sums come out a rounding error above it.
        assertThat(step(onPlan(plan, nights(3, 8.118), stockDrop = 9.02))).isNull()
    }

    @Test
    fun `the gain is judged after 3 unplugged nights on the plan`() {
        assertThat(step(onPlan(Plan("everyday", night), nights(2, 10.0)))).isNull()
        val plugged = nights(3, 10.0).mapIndexed { i, s -> if (i == 0) s.copy(plugged = true) else s }
        assertThat(step(onPlan(Plan("everyday", night), plugged))).isNull()
    }

    @Test
    fun `a plan with nothing gentler below it never steps`() {
        assertThat(step(onPlan(Plan("everyday"), nights(3, 10.0)))).isNull()
        assertThat(step(onPlan(Plan("stock"), nights(3, 10.0)))).isNull()
    }

    @Test
    fun `no gain is judged without the stock drain`() {
        val unknown = (0 until 10).map { session(it, "23:00", 600).copy(provenance = Provenance.UNKNOWN, batteryDrop = 10.0) }
        val history = history(10, unknown, Feedback(listOf(AppliedPlan(Plan("everyday", night), at(3, "12:00")))))
        assertThat(step(history)).isNull()
    }

    @Test
    fun `a late message counts from when a plan was applied, and for 30 days at most`() {
        val plan = Plan("strict", night)
        val stock = (0 until 45).map { afternoon(it) }
        fun at45(late: java.time.Instant) = history(45, stock, Feedback(listOf(AppliedPlan(plan, at(5, "12:00"))), listOf(late)))
        val to = at(45, "12:00")
        assertThat(steps(at45(to - Duration.ofDays(30)))).containsExactly(
            Step(Reason.ArrivedLate(to - Duration.ofDays(30)), Plan("classic", night)),
        )
        assertThat(step(at45(to - Duration.ofDays(30) - Duration.ofSeconds(1)))).isNull()
        val recent = history(10, stock, Feedback(listOf(AppliedPlan(plan, at(5, "12:00"))), listOf(at(4, "09:00"))))
        assertThat(step(recent)).isNull()
        assertThat(step(history(10, stock, Feedback(lateArrivals = listOf(at(8, "09:00")))))).isNull()
    }

    /** Glances by day and unplugged nights: It can wait gets Classic with Night. From [tunedFrom], drain halves. */
    private fun glancer(days: Int, tunedFrom: Int = days): List<ScreenOffSession> = (0 until days).flatMap { day ->
        val sessions = shorts(day, 20) + sleep(day)
        if (day < tunedFrom) sessions else sessions.map { it.copy(provenance = Provenance.TUNED, batteryDrop = it.batteryDrop?.div(2)) }
    }

    @Test
    fun `a late message caps the fresh plan one notch below the plan it was about`() {
        val applied =
            Feedback(
                listOf(AppliedPlan(recommend(history(7, glancer(7)), Urgency.IT_CAN_WAIT).plan, at(7, "12:00"))),
                listOf(at(8, "09:00")),
            )
        assertThat(applied.current!!.plan.dayProfileId).isEqualTo("classic")
        assertThat(applied.current!!.plan.night).isNotNull()
        val result = recommend(history(10, glancer(10, tunedFrom = 7), applied), Urgency.IT_CAN_WAIT)
        assertThat(result.plan).isEqualTo(Plan("classic"))
        assertThat(result.reasons.first()).isEqualTo(Reason.ArrivedLate(at(8, "09:00")))
        assertThat(result.reasons.filterIsInstance<Reason.UnpluggedNights>()).isEmpty()
        assertThat(result.reasons).contains(Reason.ShortSessions(1.0))
        assertThat(result.notes).contains(Note.GENTLER)
    }

    @Test
    fun `once the gentler plan is applied the cap holds and does not step again`() {
        val fresh = recommend(history(7, glancer(7)), Urgency.IT_CAN_WAIT).plan
        val feedback = Feedback(
            listOf(AppliedPlan(fresh, at(7, "12:00")), AppliedPlan(Plan("classic"), at(9, "12:00"))),
            listOf(at(8, "09:00")),
        )
        val result = recommend(history(14, glancer(14, tunedFrom = 7), feedback), Urgency.IT_CAN_WAIT)
        assertThat(result.plan).isEqualTo(Plan("classic"))
        assertThat(result.notes).contains(Note.GENTLER)
    }

    @Test
    fun `a late message on Everyday with Night caps a fresh Pocket at Everyday`() {
        fun day(it: Int) = listOf(afternoon(it).copy(timeInDeep = Duration.ZERO), sleep(it))
        val sessions = (0 until 10).flatMap { day(it) }.map {
            if (it.start < at(7, "12:00")) it else it.copy(provenance = Provenance.TUNED, batteryDrop = it.batteryDrop!! / 2)
        }
        val feedback = Feedback(listOf(AppliedPlan(Plan("everyday", night), at(7, "12:00"))), listOf(at(8, "09:00")))
        assertThat(recommend(history(10, sessions)).plan.dayProfileId).isEqualTo("pocket")
        val result = recommend(history(10, sessions, feedback))
        assertThat(result.plan).isEqualTo(Plan("everyday"))
        assertThat(result.reasons.filterIsInstance<Reason.MotionKeptAwake>()).isEmpty()
    }

    @Test
    fun `a complaint never makes the plan stricter than the fresh one`() {
        val history = onPlan(Plan("strict", night), emptyList(), listOf(at(7, "20:00"))).copy(to = at(8, "12:00"))
        val result = recommend(history)
        assertThat(result.plan.dayProfileId).isEqualTo("everyday")
        assertThat(result.plan.night).isNotNull()
        assertThat(result.notes).doesNotContain(Note.GENTLER)
        assertThat(result.reasons.filterIsInstance<Reason.ArrivedLate>()).isEmpty()
    }
}
