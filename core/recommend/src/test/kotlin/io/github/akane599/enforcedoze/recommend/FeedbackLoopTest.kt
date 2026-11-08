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
    fun `Right away allows no Classic, no Strict and no Night`() {
        assertThat(FeedbackLoop.capForUrgency(Plan("classic", night), Urgency.RIGHT_AWAY)).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.capForUrgency(Plan("strict"), Urgency.RIGHT_AWAY)).isEqualTo(Plan("everyday"))
        assertThat(FeedbackLoop.capForUrgency(Plan("pocket", night), Urgency.RIGHT_AWAY)).isEqualTo(Plan("pocket"))
        assertThat(FeedbackLoop.capForUrgency(Plan("classic", night), Urgency.WITHIN_THE_HOUR)).isEqualTo(Plan("classic", night))
    }

    /** A week of stock nights at 1 %/h, then [plan] from noon on day 7 with its own nights after. */
    private fun onPlan(plan: Plan, nights: List<ScreenOffSession>, late: List<java.time.Instant> = emptyList()): History {
        val stock = (0 until 7).map { session(it, "23:00", 600).copy(timeInDeep = 300.min, batteryDrop = 10.0) }
        val tuned = nights.map { it.copy(provenance = Provenance.TUNED) }
        return history(7 + nights.size, stock + tuned, Feedback(AppliedPlan(plan, at(7, "12:00")), late))
    }

    private fun step(history: History) = FeedbackLoop.step(history, Signals.from(history))

    private fun nights(count: Int, drop: Double) = (7 until 7 + count).map { session(it, "23:00", 600).copy(batteryDrop = drop) }

    @Test
    fun `exactly 10 percent saved is enough, a little less steps`() {
        val plan = Plan("everyday", night)
        assertThat(step(onPlan(plan, nights(3, 9.0)))).isNull()
        val reason = step(onPlan(plan, nights(3, 9.1))) as Reason.LowGain
        assertThat(reason.nights).isEqualTo(3)
        assertThat(reason.gain).isWithin(1e-9).of(0.09)
    }

    @Test
    fun `the gain is judged after 3 unplugged nights on the plan`() {
        assertThat(step(onPlan(Plan("everyday", night), nights(2, 10.0)))).isNull()
        val plugged = nights(3, 10.0).mapIndexed { i, s -> if (i == 0) s.copy(plugged = true) else s }
        assertThat(step(onPlan(Plan("everyday", night), plugged))).isNull()
    }

    @Test
    fun `no gain is judged without the stock drain`() {
        val unknown = (0 until 10).map { session(it, "23:00", 600).copy(provenance = Provenance.UNKNOWN, batteryDrop = 10.0) }
        val history = history(10, unknown, Feedback(AppliedPlan(Plan("everyday", night), at(3, "12:00"))))
        assertThat(step(history)).isNull()
    }

    @Test
    fun `a late message counts from when the plan was applied, and for 30 days at most`() {
        val plan = Plan("strict", night)
        val stock = (0 until 45).map { afternoon(it) }
        fun at45(late: java.time.Instant) = history(45, stock, Feedback(AppliedPlan(plan, at(5, "12:00")), listOf(late)))
        val to = at(45, "12:00")
        assertThat(step(at45(to - Duration.ofDays(30)))).isEqualTo(Reason.ArrivedLate(to - Duration.ofDays(30)))
        assertThat(step(at45(to - Duration.ofDays(30) - Duration.ofSeconds(1)))).isNull()
        val recent = history(10, stock, Feedback(AppliedPlan(plan, at(5, "12:00")), listOf(at(4, "09:00"))))
        assertThat(step(recent)).isNull()
        assertThat(step(history(10, stock, Feedback(lateArrivals = listOf(at(8, "09:00")))))).isNull()
    }

    @Test
    fun `the step comes first in the reasons and the plan is one notch gentler`() {
        val history = onPlan(Plan("strict", night), emptyList(), listOf(at(7, "20:00"))).copy(to = at(8, "12:00"))
        val result = recommend(history)
        assertThat(result.plan).isEqualTo(Plan("classic", night))
        assertThat(result.reasons.first()).isEqualTo(Reason.ArrivedLate(at(7, "20:00")))
        assertThat(result.notes).contains(Note.GENTLER)
        assertThat(recommend(history, Urgency.RIGHT_AWAY).plan).isEqualTo(Plan("everyday"))
    }
}
