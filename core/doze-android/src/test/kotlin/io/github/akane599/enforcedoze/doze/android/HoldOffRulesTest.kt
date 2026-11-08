// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.app.usage.UsageEvents
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HoldOffRulesTest {
    @Test
    fun `ringing, calls, VoIP and redirects hold off, and normal does not`() {
        val held = listOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
            AudioManager.MODE_CALL_REDIRECT,
            AudioManager.MODE_COMMUNICATION_REDIRECT,
        )
        held.forEach { assertThat(HoldOffRules.isCall(it)).isTrue() }
        listOf(AudioManager.MODE_NORMAL, AudioManager.MODE_INVALID, AudioManager.MODE_CURRENT).forEach {
            assertThat(HoldOffRules.isCall(it)).isFalse()
        }
    }

    @Test
    fun `the hotspot counts from enabling to enabled`() {
        assertThat((10..14).filter(HoldOffRules::isHotspotOn)).containsExactly(12, 13)
    }

    @Test
    fun `plugged in is charging`() {
        assertThat(HoldOffRules.isPlugged(0)).isFalse()
        assertThat(HoldOffRules.isPlugged(2)).isTrue()
    }

    @Test
    fun `the app on screen is the last one resumed`() {
        val resumed = UsageEvents.Event.ACTIVITY_RESUMED
        val paused = UsageEvents.Event.ACTIVITY_PAUSED
        assertThat(HoldOffRules.lastResumed(sequenceOf(resumed to "a", resumed to "b", paused to "b"))).isEqualTo("b")
        assertThat(HoldOffRules.lastResumed(emptySequence())).isNull()
    }
}
