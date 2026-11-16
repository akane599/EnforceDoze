// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import io.github.akane599.enforcedoze.ui.VerdictWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {
    private val entries = LogFixtures.state.entries

    @Test fun theDefaultShowsEverything() {
        assertFalse(LogFilter().active)
        assertEquals(entries, entries.filter(LogFilter()::accepts))
    }

    @Test fun eachFieldNarrowsAndTheyCombine() {
        val held = LogFilter(verdicts = setOf(VerdictWord.HELD))
        assertTrue(entries.filter(held::accepts).all { it.verdict == VerdictWord.HELD })
        val unpluggedHeldNight = held.copy(plugged = false, profileId = "night")
        assertEquals(1, entries.count(unpluggedHeldNight::accepts))
        assertEquals(1, entries.count(LogFilter(plugged = true)::accepts))
    }

    @Test fun aMixedNightMatchesNoSingleProfile() {
        val mixed = entries.single { it.profileId == null }
        assertFalse(LogFilter(profileId = "everyday").accepts(mixed))
        assertTrue(LogFilter().accepts(mixed))
    }
}
