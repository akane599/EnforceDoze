// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.recommend

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * SPEC 7.6: whole synthetic owners, each run through `recommend` as onboarding would. Every case names the plan,
 * the confidence, the why-card lines in order and the notes it must get.
 */
class FixtureHistoriesTest {
    private class Case(
        val name: String,
        val history: History,
        val urgency: Urgency,
        val plan: Plan,
        val confidence: Confidence,
        val reasons: List<KClass<out Reason>>,
        val notes: Set<Note> = emptySet(),
    )

    private val sleeperNight = NightSlot("night", LocalTime.of(22, 45), LocalTime.of(7, 30))

    /** Bed between 22:50 and 23:40, up at 07:00. */
    private val bedtimes = listOf("23:00", "23:10", "23:20", "22:50", "23:10", "23:40", "23:05")

    /** Up at 07:00 on the clock, so a night across a clock change is an hour shorter or longer. */
    private fun sleeper(days: Int = 7, zone: ZoneId = ZONE, first: Int = 0) = (first until first + days).flatMap { d ->
        val start = bedtimes[d % bedtimes.size]
        val minutes = Duration.between(at(d, start, zone), at(d + 1, "07:00", zone)).toMinutes().toInt()
        listOf(
            session(d, "13:00", 60, zone).copy(timeInDeep = 20.min, motionResets = 0, batteryDrop = 0.5),
            session(d, start, minutes, zone).copy(timeInDeep = (minutes / 2).min, motionResets = 0, batteryDrop = minutes / 60.0),
        )
    }

    /** Four 45-minute stretches each afternoon in a pocket: deep idle in one of them, two motion resets in each. */
    private fun commuter() = (0 until 7).flatMap { d ->
        listOf("13:00", "14:30", "16:00", "17:30").mapIndexed { i, start ->
            session(d, start, 45).copy(timeInDeep = if (i == 3) 10.min else 0.min, motionResets = 2, batteryDrop = 0.75)
        } + charging(d)
    }

    /** Twenty glances of 10 minutes on workdays, an hour away in the evening, the phone charged at night. */
    private fun deskWorker() = (0 until 7).flatMap { d ->
        val evening = session(d, "18:00", 60).copy(timeInDeep = 20.min, motionResets = 0, batteryDrop = 0.5)
        (if (d in 1..5) shorts(d, 20) else emptyList()) + evening + charging(d)
    }

    /** Glances by day and long, deep nights at 0.2 %/h under stock settings. */
    private fun dozing() = (0 until 7).flatMap { d ->
        val night = session(d, "23:10", 470).copy(timeInDeep = 400.min, motionResets = 0, batteryDrop = 470 * 0.2 / 60)
        (if (d in 1..6) shorts(d, 20) else emptyList()) + night
    }

    private val cases = listOf(
        Case(
            "thin history: an evening and a night",
            history(1, listOf(session(0, "13:00", 180), charging(0).copy(end = at(1, "05:00")))),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday"),
            Confidence.LOW,
            listOf(Reason.ThinHistory::class),
            setOf(Note.REFINES_AFTER_FIRST_NIGHT),
        ),
        Case(
            "pocket-heavy commuter",
            history(7, commuter()),
            Urgency.WITHIN_THE_HOUR,
            Plan("pocket"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.MotionKeptAwake::class),
        ),
        Case(
            "pocket-heavy commuter who needs messages right away",
            history(7, commuter()),
            Urgency.RIGHT_AWAY,
            Plan("pocket"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.MotionKeptAwake::class),
        ),
        Case(
            "desk worker",
            history(7, deskWorker()),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class),
        ),
        Case(
            "desk worker whose messages can wait",
            history(7, deskWorker()),
            Urgency.IT_CAN_WAIT,
            Plan("classic"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.ShortSessions::class),
        ),
        Case(
            "night charger",
            history(7, quiet(7)),
            Urgency.IT_CAN_WAIT,
            Plan("everyday"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class),
        ),
        Case(
            "unplugged sleeper",
            history(7, sleeper()),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday", sleeperNight),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.UnpluggedNights::class),
        ),
        Case(
            "unplugged sleeper who needs messages right away",
            history(7, sleeper() + (1..5).flatMap { shorts(it, 20) }),
            Urgency.RIGHT_AWAY,
            Plan("everyday"),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.MessagesRightAway::class),
        ),
        Case(
            "already-dozing phone",
            history(7, dozing()),
            Urgency.IT_CAN_WAIT,
            Plan("everyday", sleeperNight),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.UnpluggedNights::class, Reason.DozingWell::class),
            setOf(Note.LITTLE_TO_GAIN),
        ),
        Case(
            "owner who never ran stock settings",
            history(14, sleeper(14).map { it.copy(provenance = Provenance.UNKNOWN) }),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday", sleeperNight),
            Confidence.MEDIUM,
            listOf(Reason.ScreenOffPerDay::class, Reason.UnpluggedNights::class),
            setOf(Note.STOCK_NOT_MEASURED),
        ),
        Case(
            "two days of history, both nights unplugged",
            history(2, (0 until 2).flatMap { listOf(session(it, "13:00", 240), sleep(it, "23:10", 540)) }),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday", NightSlot("night", LocalTime.of(22, 45), LocalTime.of(8, 45))),
            Confidence.MEDIUM,
            listOf(Reason.ScreenOffPerDay::class, Reason.UnpluggedNights::class),
        ),
        Case(
            "two days of history, one night on the charger",
            history(
                2,
                listOf(session(0, "13:00", 240), sleep(0, "23:10", 540), session(1, "13:00", 240), charging(1).copy(end = at(2, "08:10"))),
            ),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday"),
            Confidence.MEDIUM,
            listOf(Reason.ScreenOffPerDay::class),
        ),
        Case(
            // Days 24 to 31 hold 2026-03-29, when Berlin goes from 02:00 to 03:00; eight days, as that week is 167 h.
            "sleeper across the spring clock change",
            History(at(24, "12:00", BERLIN), at(32, "12:00", BERLIN), BERLIN, sleeper(8, BERLIN, first = 24)),
            Urgency.WITHIN_THE_HOUR,
            Plan("everyday", sleeperNight),
            Confidence.HIGH,
            listOf(Reason.ScreenOffPerDay::class, Reason.UnpluggedNights::class),
        ),
    )

    @Test
    fun `every fixture gets its plan, confidence, reasons and notes`() {
        assertThat(cases.size).isAtLeast(12)
        for (case in cases) {
            val result = recommend(case.history, case.urgency)
            assertWithMessage(case.name).that(result.plan).isEqualTo(case.plan)
            assertWithMessage(case.name).that(result.confidence).isEqualTo(case.confidence)
            assertWithMessage(case.name).that(result.reasons.map { it::class }).containsExactlyElementsIn(case.reasons).inOrder()
            assertWithMessage(case.name).that(result.notes).containsAtLeastElementsIn(case.notes)
        }
    }

    @Test
    fun `Strict is never recommended, whatever the history and the answer`() {
        for (case in cases) {
            for (urgency in Urgency.entries) {
                assertWithMessage("${case.name}, $urgency").that(recommend(case.history, urgency).plan.dayProfileId).isNotEqualTo("strict")
            }
        }
    }

    @Test
    fun `low battery leaves the plan alone and offers Strict only as a card`() {
        assertThat(recommend(history(7, quiet(7))).plan).isEqualTo(Plan("everyday"))
        assertThat(Cards.strictCard(15, plugged = false)).isTrue()
        assertThat(Cards.strictCard(15, plugged = true)).isFalse()
    }

    @Test
    fun `the commuter's why card has the numbers`() {
        val reasons = recommend(history(7, commuter())).reasons
        assertThat(reasons).contains(Reason.MotionKeptAwake(21, 28))
        // 56 resets in 21 hours is under 3 an hour: no line for it.
        assertThat(reasons.filterIsInstance<Reason.MotionResets>()).isEmpty()
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
