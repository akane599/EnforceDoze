// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.plan

import io.github.akane599.enforcedoze.profile.NightSlot
import io.github.akane599.enforcedoze.profile.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalTime

class PlanEditsTest {
    private val day = Plan("everyday")
    private val night = NightSlot("night", LocalTime.of(23, 0), LocalTime.of(7, 30))

    @Test fun turningNightOnTakesTheRecommendedSlot() {
        val recommended = Plan("pocket", NightSlot("classic", LocalTime.of(1, 0), LocalTime.of(9, 0)))
        assertEquals(Plan("everyday", recommended.night), PlanEdits.nightOn(day, recommended))
    }

    @Test fun turningNightOnWithoutARecommendedSlotUsesTheSpecDefault() {
        assertEquals(Plan("everyday", night), PlanEdits.nightOn(day, Plan("pocket")))
        assertEquals(Plan("everyday", night), PlanEdits.nightOn(day, null))
    }

    @Test fun aPlanThatHasANightKeepsIt() {
        val plan = day.copy(night = night.copy(profileId = "strict"))
        assertSame(plan, PlanEdits.nightOn(plan, Plan("pocket", night)))
    }

    @Test fun anEdgeMovesOnlyItsOwnEnd() {
        val plan = day.copy(night = night)
        assertEquals(LocalTime.of(22, 15), PlanEdits.withEdge(plan, start = true, LocalTime.of(22, 15))?.night?.start)
        assertEquals(LocalTime.of(7, 30), PlanEdits.withEdge(plan, start = true, LocalTime.of(22, 15))?.night?.end)
        assertEquals(LocalTime.of(9, 0), PlanEdits.withEdge(plan, start = false, LocalTime.of(9, 0))?.night?.end)
    }

    @Test fun anEdgeOnTheOtherEndWouldEmptyTheWindowAndIsRefused() {
        val plan = day.copy(night = night)
        assertNull(PlanEdits.withEdge(plan, start = true, LocalTime.of(7, 30)))
        assertNull(PlanEdits.withEdge(plan, start = false, LocalTime.of(23, 0)))
    }

    @Test fun aPlanWithoutANightHasNoEdges() {
        assertNull(PlanEdits.withEdge(day, start = true, LocalTime.of(22, 0)))
    }
}
