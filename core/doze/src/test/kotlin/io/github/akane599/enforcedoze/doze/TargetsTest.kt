// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.AospDeviceIdle
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Profile
import org.junit.Test
import java.time.Instant

class TargetsTest {
    private val self = FakeDevice.SELF
    private val restricted = Setting.Sensors(SensorValue.Restricted(self))

    @Suppress("LongParameterList")
    private fun inputs(
        profile: Profile? = BuiltInProfiles.EVERYDAY,
        mode: Mode = Mode.MANAGED,
        screenOn: Boolean = true,
        locked: Boolean = false,
        holdOffs: Set<HoldOff> = emptySet(),
        guarded: Set<Lever> = emptySet(),
        knownKeys: Set<String>? = AospDeviceIdle.KEYS,
        snapshotsReady: Boolean = true,
        keep: Set<String>? = null,
        owner: Set<String>? = null,
        written: Map<Lever, Setting> = emptyMap(),
    ) = TargetInputs(profile, mode, screenOn, locked, holdOffs, guarded, knownKeys, snapshotsReady, self, keep, owner, written)

    private fun everydayString() = Setting.Constants(ConstantsValue.Exact(DeviceIdleSettings.encode(BuiltInProfiles.EVERYDAY.deviceIdle)))

    @Test
    fun `everyday wants its string and leaves the timed levers alone`() {
        val targets = Targets.compute(inputs())
        assertThat(targets.wanted).containsExactly(Lever.CONSTANTS, everydayString())
        assertThat(targets.used).containsExactly(Lever.CONSTANTS)
    }

    @Test
    fun `constants ignore the screen and the hold-offs`() {
        val expected = Targets.compute(inputs()).wanted[Lever.CONSTANTS]
        for (screenOn in listOf(true, false)) {
            for (holdOffs in listOf(emptySet(), setOf(HoldOff.CALL), HoldOff.entries.toSet())) {
                for (profile in listOf(BuiltInProfiles.EVERYDAY, BuiltInProfiles.NIGHT, BuiltInProfiles.STRICT)) {
                    val written = mapOf(Lever.CONSTANTS to Targets.compute(inputs(profile)).wanted.getValue(Lever.CONSTANTS))
                    val atRest = Targets.compute(inputs(profile, written = written)).wanted[Lever.CONSTANTS]
                    val now = Targets.compute(
                        inputs(profile, screenOn = screenOn, holdOffs = holdOffs, written = written),
                    ).wanted[Lever.CONSTANTS]
                    assertThat(now).isEqualTo(atRest)
                }
                assertThat(Targets.compute(inputs(screenOn = screenOn, holdOffs = holdOffs)).wanted[Lever.CONSTANTS]).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `classic engages only with the screen off and no hold-off`() {
        val classic = BuiltInProfiles.CLASSIC
        assertThat(Targets.compute(inputs(classic, screenOn = false)).wanted)
            .containsExactly(Lever.FORCE_IDLE, Setting.FORCED, Lever.SENSORS, restricted)
        val on = Targets.compute(inputs(classic))
        assertThat(on.wanted).isEmpty()
        assertThat(on.armed).containsExactly(Lever.FORCE_IDLE, Lever.SENSORS)
        val held = Targets.compute(inputs(classic, screenOn = false, holdOffs = setOf(HoldOff.CHARGING)))
        assertThat(held.wanted).isEmpty()
        assertThat(held.heldOff).containsExactly(Lever.FORCE_IDLE, Lever.SENSORS)
    }

    @Test
    fun `an on state the engine set is released at screen on, at a hold-off and when paused`() {
        val ours = mapOf(Lever.FORCE_IDLE to Setting.FORCED, Lever.SENSORS to restricted)
        val released = mapOf(Lever.FORCE_IDLE to Setting.RELEASED, Lever.SENSORS to Setting.SENSORS_NORMAL)
        val classic = BuiltInProfiles.CLASSIC
        assertThat(Targets.compute(inputs(classic, written = ours)).wanted).isEqualTo(released)
        assertThat(
            Targets.compute(inputs(classic, screenOn = false, holdOffs = setOf(HoldOff.CALL), written = ours)).wanted,
        ).isEqualTo(released)
        for (mode in listOf(Mode.PAUSED, Mode.HANDS_OFF)) {
            assertThat(Targets.compute(inputs(classic, mode = mode, screenOn = false, written = ours)).wanted).isEqualTo(released)
        }
        assertThat(Targets.compute(inputs(BuiltInProfiles.EVERYDAY, screenOn = false, written = ours)).wanted - Lever.CONSTANTS)
            .isEqualTo(released)
    }

    @Test
    fun `until unlock keeps what the engine set on at the lock screen and releases it at unlock`() {
        val waiting = BuiltInProfiles.CLASSIC.let { it.copy(levers = it.levers.copy(forceIdle = ForceIdleLever(true, untilUnlock = true))) }
        val ours = mapOf(Lever.FORCE_IDLE to Setting.FORCED, Lever.SENSORS to restricted)
        val released = mapOf(Lever.FORCE_IDLE to Setting.RELEASED, Lever.SENSORS to Setting.SENSORS_NORMAL)

        val atLockScreen = Targets.compute(inputs(waiting, locked = true, written = ours))
        assertThat(atLockScreen.wanted).isEqualTo(ours)
        assertThat(atLockScreen.armed).isEmpty()
        assertThat(Targets.compute(inputs(waiting, locked = false, written = ours)).wanted).isEqualTo(released)
        assertThat(Targets.compute(inputs(waiting, locked = true, holdOffs = setOf(HoldOff.CALL), written = ours)).wanted)
            .isEqualTo(released)
        assertThat(Targets.compute(inputs(waiting, mode = Mode.PAUSED, locked = true, written = ours)).wanted).isEqualTo(released)
        assertThat(Targets.compute(inputs(BuiltInProfiles.CLASSIC, locked = true, written = ours)).wanted).isEqualTo(released)
    }

    @Test
    fun `until unlock never sets an on state at the lock screen`() {
        val waiting = BuiltInProfiles.CLASSIC.let { it.copy(levers = it.levers.copy(forceIdle = ForceIdleLever(true, untilUnlock = true))) }
        val notYet = Targets.compute(inputs(waiting, locked = true))
        assertThat(notYet.wanted).isEmpty()
        assertThat(notYet.armed).containsExactly(Lever.FORCE_IDLE, Lever.SENSORS)
        val released = mapOf(Lever.FORCE_IDLE to Setting.RELEASED, Lever.SENSORS to Setting.SENSORS_NORMAL)
        assertThat(Targets.compute(inputs(waiting, locked = true, written = released)).wanted).isEmpty()
    }

    @Test
    fun `nothing is wanted outside managed mode unless the engine holds an on state`() {
        val written = mapOf(Lever.CONSTANTS to everydayString(), Lever.FORCE_IDLE to Setting.RELEASED)
        for (mode in listOf(Mode.PAUSED, Mode.HANDS_OFF)) {
            assertThat(Targets.compute(inputs(BuiltInProfiles.STRICT, mode = mode, screenOn = false, written = written)).wanted).isEmpty()
        }
    }

    @Test
    fun `keys the phone lacks are skipped and reported`() {
        val profile = BuiltInProfiles.EVERYDAY.copy(deviceIdle = BuiltInProfiles.EVERYDAY.deviceIdle + ("skip_sensing" to "true"))
        val targets = Targets.compute(inputs(profile))
        assertThat(targets.wanted[Lever.CONSTANTS]).isEqualTo(everydayString())
        assertThat(targets.missingKeys).containsExactly("skip_sensing")
        val known = setOf("inactive_to")
        assertThat(Targets.compute(inputs(knownKeys = known)).wanted[Lever.CONSTANTS])
            .isEqualTo(Setting.Constants(ConstantsValue.Exact("inactive_to=300000")))
    }

    @Test
    fun `constants wait for the keys and both snapshots`() {
        assertThat(Targets.compute(inputs(knownKeys = null)).waiting).containsKey(Lever.CONSTANTS)
        val noSnapshots = Targets.compute(inputs(snapshotsReady = false))
        assertThat(noSnapshots.wanted).isEmpty()
        assertThat(noSnapshots.waiting[Lever.CONSTANTS]).contains("stock.json")
    }

    @Test
    fun `a profile without constants deletes only the engine's own string`() {
        val ours = mapOf(Lever.CONSTANTS to everydayString())
        assertThat(Targets.compute(inputs(BuiltInProfiles.STOCK, written = ours)).wanted).containsExactly(Lever.CONSTANTS, Setting.ABSENT)
        assertThat(Targets.compute(inputs(BuiltInProfiles.CLASSIC, written = ours)).wanted).containsExactly(Lever.CONSTANTS, Setting.ABSENT)
        assertThat(Targets.compute(inputs(BuiltInProfiles.STOCK, written = mapOf(Lever.CONSTANTS to Setting.ABSENT))).wanted).isEmpty()
        assertThat(Targets.compute(inputs(BuiltInProfiles.STOCK)).wanted).isEmpty()
    }

    @Test
    fun `a guarded lever is left alone, except that forced idle is still released`() {
        assertThat(Targets.compute(inputs(guarded = setOf(Lever.CONSTANTS))).wanted).isEmpty()
        val strict = inputs(BuiltInProfiles.STRICT, guarded = setOf(Lever.FORCE_IDLE), written = mapOf(Lever.FORCE_IDLE to Setting.FORCED))
        assertThat(Targets.compute(strict.copy(screenOn = false)).wanted[Lever.FORCE_IDLE]).isEqualTo(Setting.RELEASED)
    }

    @Test
    fun `the allowlist is trimmed only to a chosen keep list and put back after`() {
        val strict = BuiltInProfiles.STRICT
        assertThat(Targets.compute(inputs(strict)).waiting).containsKey(Lever.ALLOWLIST)
        assertThat(Targets.compute(inputs(strict, keep = setOf("a"))).wanted[Lever.ALLOWLIST]).isEqualTo(Setting.Allowlist(setOf("a")))
        val trimmed = mapOf(Lever.ALLOWLIST to Setting.Allowlist(setOf("a")))
        assertThat(Targets.compute(inputs(written = trimmed, owner = setOf("a", "b"))).wanted[Lever.ALLOWLIST])
            .isEqualTo(Setting.Allowlist(setOf("a", "b")))
        assertThat(Targets.compute(inputs(mode = Mode.PAUSED, written = trimmed, owner = setOf("a", "b"))).wanted).isEmpty()
    }

    @Test
    fun `a forced-idle delay waits, since it would need an alarm`() {
        val delayed = BuiltInProfiles.CLASSIC.copy(levers = BuiltInProfiles.CLASSIC.levers.copy(forceIdle = ForceIdleLever(true, 60)))
        val targets = Targets.compute(inputs(delayed, screenOn = false))
        assertThat(targets.wanted).doesNotContainKey(Lever.FORCE_IDLE)
        assertThat(targets.waiting).containsKey(Lever.FORCE_IDLE)
    }

    @Test
    fun `all sensors off uses sensor privacy at screen off`() {
        val profile = BuiltInProfiles.STRICT.copy(
            levers = BuiltInProfiles.STRICT.levers.copy(sensors = io.github.akane599.enforcedoze.profile.SensorLever.ALL_SENSORS_OFF),
        )
        val wanted = Targets.compute(inputs(profile, screenOn = false, keep = emptySet())).wanted
        assertThat(wanted[Lever.SENSOR_PRIVACY]).isEqualTo(Setting.SensorPrivacy(true))
        assertThat(wanted).doesNotContainKey(Lever.SENSORS)
    }

    @Test
    fun `restore stock and return to previous`() {
        assertThat(Targets.restoreStock(null)).containsExactly(
            Lever.CONSTANTS,
            Setting.ABSENT,
            Lever.FORCE_IDLE,
            Setting.RELEASED,
            Lever.SENSORS,
            Setting.SENSORS_NORMAL,
            Lever.SENSOR_PRIVACY,
            Setting.PRIVACY_OFF,
        )
        assertThat(Targets.restoreStock(setOf("a"))[Lever.ALLOWLIST]).isEqualTo(Setting.Allowlist(setOf("a")))
        val previous =
            PreviousSnapshot(Instant.EPOCH, ConstantsValue.Exact("inactive_to=1"), "com.android.shell", emptyMap(), null, null, null)
        assertThat(Targets.returnToPrevious(previous, mapOf(Lever.SENSORS to Setting.SENSORS_NORMAL), null))
            .containsExactly(Lever.CONSTANTS, Setting.Constants(ConstantsValue.Exact("inactive_to=1")))
        assertThat(
            Targets.returnToPrevious(previous, mapOf(Lever.FORCE_IDLE to Setting.FORCED), null)[Lever.FORCE_IDLE],
        ).isEqualTo(Setting.RELEASED)
    }
}
