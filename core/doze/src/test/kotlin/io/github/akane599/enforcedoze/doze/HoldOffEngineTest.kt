// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Hold-offs (SPEC 5.5): what lifts forced idle and sensors, and what never moves the constants. */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldOffEngineTest {
    private val device = FakeDevice()
    private val store = FakeStore()
    private val journal = FakeJournal()
    private val restricted = DeviceWrite.RestrictSensors(FakeDevice.SELF)

    private fun TestScope.engine(): DozeEngine {
        val clock = EngineClock { BASE_TIME.plusMillis(testScheduler.currentTime) }
        return DozeEngine(device.port, store, journal, clock, FakeDevice.SELF, backgroundScope, StandardTestDispatcher(testScheduler))
    }

    private suspend fun DozeEngine.startWith(profile: Profile) =
        start(EngineStart(profile, screenOn = true, writable = Writable.EVERYTHING))

    /** As after onboarding: the string as found, and stock captured with it deleted. */
    private fun snapshotsReady() {
        store.previous = PreviousSnapshot(BASE_TIME, ConstantsValue.Absent, null, emptyMap(), null, null, null)
        store.stock = StockSnapshot(BASE_TIME, FakeDevice().effective())
    }

    private fun writesSince(mark: Int) = device.writes.drop(mark)

    private fun DozeEngine.state(lever: Lever) = status.value.levers.getValue(lever).state

    @Test
    fun `until unlock still lets a hold-off at the lock screen release forced idle`() = runTest {
        val classic = BuiltInProfiles.CLASSIC
        val waiting = classic.copy(levers = classic.levers.copy(forceIdle = ForceIdleLever(true, untilUnlock = true)))
        val engine = engine()
        engine.startWith(waiting)
        engine.screenOff()
        engine.screenOn(locked = true)
        engine.holdOff(HoldOff.CALL, active = true)
        assertThat(writesSince(2)).containsExactly(DeviceWrite.Unforce, DeviceWrite.EnableSensors).inOrder()
        engine.holdOff(HoldOff.CALL, active = false)
        assertThat(writesSince(4)).isEmpty()
        assertThat(device.forced).isFalse()
    }

    @Test
    fun `hold-offs lift forced idle and sensors until the last one ends, and constants stay`() = runTest {
        snapshotsReady()
        val engine = engine()
        val strictKeepAll = BuiltInProfiles.STRICT
        store.state = PersistedState(allowlistKeep = emptySet())
        engine.startWith(strictKeepAll)
        engine.screenOff()
        assertThat(device.forced).isTrue()
        val string = device.constants
        val mark = device.writes.size

        engine.holdOff(HoldOff.CALL, active = true)
        assertThat(writesSince(mark)).containsExactly(DeviceWrite.Unforce, DeviceWrite.EnableSensors).inOrder()
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.HELD_OFF)
        engine.holdOff(HoldOff.AUDIO, active = true)
        engine.holdOff(HoldOff.CALL, active = false)
        assertThat(writesSince(mark)).hasSize(2)

        engine.holdOff(HoldOff.AUDIO, active = false)
        assertThat(writesSince(mark + 2)).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
        assertThat(device.constants).isEqualTo(string)
        assertThat(writesSince(mark).filterIsInstance<DeviceWrite.SetConstants>()).isEmpty()
        assertThat(journal.events<EngineEvent.HoldOffStarted>().map { it.holdOff }).containsExactly(HoldOff.CALL, HoldOff.AUDIO)
        assertThat(journal.events<EngineEvent.HoldOffEnded>().map { it.holdOff }).containsExactly(HoldOff.CALL, HoldOff.AUDIO)
    }

    /** Parity row 22, in the order the host sends it: the hold-off before screen off, and lifted after screen on. */
    @Test
    fun `a keep-awake app at screen off keeps forced idle off, and screen on engages nothing`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        engine.holdOff(HoldOff.KEEP_AWAKE_APP, active = true)
        engine.screenOff()
        assertThat(device.writes).isEmpty()
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.HELD_OFF)
        engine.screenOn(locked = false)
        engine.holdOff(HoldOff.KEEP_AWAKE_APP, active = false)
        assertThat(device.writes).isEmpty()
        assertThat(device.forced).isFalse()
        engine.screenOff()
        assertThat(device.writes).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
    }

    @Test
    fun `charging at screen off keeps forced idle off`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        engine.holdOff(HoldOff.CHARGING, active = true)
        engine.screenOff()
        assertThat(device.writes).isEmpty()
        engine.holdOff(HoldOff.CHARGING, active = false)
        assertThat(device.writes).containsExactly(restricted, DeviceWrite.ForceIdle).inOrder()
    }

    @Test
    fun `a hold-off that starts during the screen-off batch wins`() = runTest {
        val engine = engine()
        engine.startWith(BuiltInProfiles.CLASSIC)
        device.hang += "read:sensors"
        launch { engine.screenOff() }
        runCurrent()
        assertThat(device.writes).isNotEmpty()
        launch { engine.holdOff(HoldOff.CALL, active = true) }
        device.hang.clear()
        advanceUntilIdle()

        assertThat(device.forced).isFalse()
        assertThat(device.sensors).isEqualTo(SensorValue.Normal)
        assertThat(engine.state(Lever.FORCE_IDLE)).isEqualTo(LeverState.HELD_OFF)
    }
}
