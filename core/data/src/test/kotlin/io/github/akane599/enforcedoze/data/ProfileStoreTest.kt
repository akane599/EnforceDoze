// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.GuardrailProblem
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val store by lazy { ProfileStore(folder.root.resolve("profiles")) }
    private val taken = BuiltInProfiles.ALL.map { it.id }.toSet()

    @Test fun `a saved clone comes back after the built-ins`() = runTest {
        val clone = BuiltInProfiles.POCKET.cloneAs("Bike", taken)
        assertThat(store.save(clone, stock = null)).isEmpty()
        assertThat(store.custom()).containsExactly(clone)
        assertThat(store.all()).containsExactlyElementsIn(BuiltInProfiles.ALL + clone).inOrder()
        assertThat(store.byId("bike")).isEqualTo(clone)
        assertThat(store.byId("night")).isEqualTo(BuiltInProfiles.NIGHT)
    }

    @Test fun `saving the same id replaces the profile`() = runTest {
        val clone = BuiltInProfiles.NIGHT.cloneAs("Late", taken)
        store.save(clone, stock = null)
        val edited = clone.copy(deviceIdle = clone.deviceIdle + ("idle_to" to "1800000"))
        assertThat(store.save(edited, stock = null)).isEmpty()
        assertThat(store.custom()).containsExactly(edited)
    }

    @Test fun `out-of-range constants and built-in ids are refused and nothing is written`() = runTest {
        val clone = BuiltInProfiles.EVERYDAY.cloneAs("Fast", taken)
        val bad = clone.copy(deviceIdle = clone.deviceIdle + ("sensing_to" to "1000"))
        assertThat(store.save(bad, stock = null)).containsExactly(GuardrailProblem.BelowMinimum("sensing_to", 10_000.0))
        assertThat(store.save(BuiltInProfiles.EVERYDAY, stock = null)).hasSize(1)
        assertThat(store.custom()).isEmpty()
    }

    @Test fun `delete removes a custom profile and never a built-in`() = runTest {
        val clone = BuiltInProfiles.CLASSIC.cloneAs("Mine", taken)
        store.save(clone, stock = null)
        assertThat(store.delete("classic")).isFalse()
        assertThat(store.delete("mine")).isTrue()
        assertThat(store.custom()).isEmpty()
    }

    @Test fun `a file that no longer reads is set aside, not thrown`() = runTest {
        val dir = folder.root.resolve("profiles").apply { mkdirs() }
        dir.resolve("broken.json").writeText("{ not json")
        assertThat(store.custom()).isEmpty()
        assertThat(dir.list()!!.toList()).hasSize(1)
        assertThat(dir.list()!!.single()).startsWith("broken.json.unreadable-")
    }
}
