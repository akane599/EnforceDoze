// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.doze.ActionRecord.Outcome
import io.github.akane599.enforcedoze.profile.BuiltInProfiles
import io.github.akane599.enforcedoze.profile.ForceIdleLever
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The root-tier extras (ADR 0038): Wi-Fi off, mobile data off and app suspension during Doze. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtrasEngineTest {
    private val device = FakeDevice()
    private val store = FakeStore()
    private val journal = FakeJournal()

    private fun TestScope.engine(): DozeEngine {
        val clock = EngineClock { BASE_TIME.plusMillis(testScheduler.currentTime) }
        return DozeEngine(device.port, store, journal, clock, FakeDevice.SELF, backgroundScope, StandardTestDispatcher(testScheduler))
    }

    /** Everyday has no timed lever, so every write here is an extra's. */
    private suspend fun DozeEngine.startWith(
        extras: Extras,
        profile: Profile = BuiltInProfiles.EVERYDAY,
        writable: Writable = Writable.EVERYTHING,
    ) {
        store.state = store.state.copy(extras = extras)
        start(EngineStart(profile, screenOn = true, writable = writable))
    }

    private fun extraWrites() = device.writes.filter { it is DeviceWrite.Wifi || it is DeviceWrite.MobileData || it is DeviceWrite.Suspend }

    private fun DozeEngine.state(lever: Lever) = status.value.levers.getValue(lever)

    @Test
    fun `Wi-Fi on and unconnected goes off at screen off and back on at screen on`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.ARMED)
        engine.screenOff()
        assertThat(device.wifi).isFalse()
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.APPLIED)
        engine.screenOn()
        assertThat(device.wifi).isTrue()
        assertThat(extraWrites()).containsExactly(DeviceWrite.Wifi(false), DeviceWrite.Wifi(true)).inOrder()
        assertThat(journal.actions.map { it.outcome }).containsExactly(Outcome.OK, Outcome.OK)
        // Put back, it is the owner's again: nothing is booked.
        assertThat(store.state.written).doesNotContainKey(Lever.WIFI)
    }

    @Test
    fun `a connected Wi-Fi, a Wi-Fi already off or airplane mode keep Wi-Fi as it is`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        device.wifiConnected = true
        engine.screenOff()
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.UNUSED)
        assertThat(engine.state(Lever.WIFI).reason).contains("connected")
        engine.screenOn()

        device.wifiConnected = false
        device.wifi = false
        engine.screenOff()
        engine.screenOn()
        // Screen on never turns on a radio the owner had off.
        assertThat(device.wifi).isFalse()

        device.wifi = true
        device.airplane = true
        engine.screenOff()
        // An extra that stays off for a reason is not a partial application.
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.UNUSED)
        assertThat(engine.state(Lever.WIFI).reason).contains("airplane")
        engine.screenOn()
        assertThat(extraWrites()).isEmpty()
    }

    @Test
    fun `every hold-off brings Wi-Fi back, and it goes off again when the hold-off ends`() = runTest {
        for (holdOff in HoldOff.entries) {
            val engine = engine()
            device.wifi = true
            engine.startWith(Extras(wifiOff = true))
            engine.screenOff()
            assertThat(device.wifi).isFalse()
            engine.holdOff(holdOff, active = true)
            assertThat(device.wifi).isTrue()
            engine.holdOff(holdOff, active = false)
            // Wi-Fi is on and still not connected, so it may go off again.
            assertThat(device.wifi).isFalse()
            engine.screenOn()
            assertThat(device.wifi).isTrue()
        }
    }

    @Test
    fun `until unlock does not keep an extra off at the lock screen`() = runTest {
        val classic = BuiltInProfiles.CLASSIC
        val waiting = classic.copy(levers = classic.levers.copy(forceIdle = ForceIdleLever(true, untilUnlock = true)))
        val engine = engine()
        engine.startWith(Extras(wifiOff = true), profile = waiting)
        engine.screenOff()
        engine.screenOn(locked = true)
        assertThat(device.forced).isTrue()
        assertThat(device.wifi).isTrue()
    }

    @Test
    fun `mobile data goes off only on a working Wi-Fi network and comes back when it goes`() = runTest {
        val engine = engine()
        engine.startWith(Extras(dataOff = true))
        engine.screenOff()
        assertThat(device.data).isTrue()
        assertThat(engine.state(Lever.MOBILE_DATA).reason).contains("Wi-Fi")
        engine.screenOn()

        device.wifiConnected = true
        device.wifiValidated = true
        engine.screenOff()
        assertThat(device.data).isFalse()
        device.wifiConnected = false
        device.wifiValidated = false
        engine.radiosChanged()
        assertThat(device.data).isTrue()
        assertThat(journal.actions.last().cause).isEqualTo(Cause.NETWORK)
    }

    @Test
    fun `Wi-Fi and mobile data are never off together`() = runTest {
        val flags = listOf(false, true)
        for (wifi in flags) {
            for (connected in flags) {
                for (validated in flags) {
                    for (data in flags) {
                        for (airplane in flags) {
                            if (validated && !connected) continue
                            val engine = engine()
                            device.wifi = wifi
                            device.wifiConnected = connected && wifi
                            device.wifiValidated = validated && wifi
                            device.data = data
                            device.airplane = airplane
                            engine.startWith(Extras(wifiOff = true, dataOff = true))
                            engine.screenOff()
                            // Whatever the radios did next, both are never off at the hands of the engine.
                            device.wifiConnected = false
                            device.wifiValidated = false
                            engine.radiosChanged()
                            val held = store.state.written
                            assertThat(held[Lever.WIFI] == Setting.WIFI_OFF && held[Lever.MOBILE_DATA] == Setting.DATA_OFF).isFalse()
                            engine.screenOn()
                            assertThat(store.state.written.keys intersect Lever.EXTRAS).isEmpty()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `an engage is saved before it is written, so a process killed mid-write undoes it at the next start`() = runTest {
        val engine = engine()
        var bookedFirst = false
        device.beforeWrite = { if (it == DeviceWrite.Wifi(false)) bookedFirst = store.state.written[Lever.WIFI] == Setting.WIFI_OFF }
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        assertThat(bookedFirst).isTrue()

        // The process dies with Wi-Fi off; the next start, screen on, puts it back.
        val next = engine()
        next.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = true, writable = Writable.EVERYTHING))
        assertThat(device.wifi).isTrue()
        assertThat(store.state.written).doesNotContainKey(Lever.WIFI)
    }

    @Test
    fun `a release that fails stays held and is tried again`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        device.refuse += "write:Wifi"
        engine.screenOn()
        assertThat(device.wifi).isFalse()
        assertThat(store.state.written[Lever.WIFI]).isEqualTo(Setting.WIFI_OFF)
        device.refuse.clear()
        engine.unlocked()
        engine.holdOff(HoldOff.CALL, active = true)
        assertThat(device.wifi).isTrue()
    }

    @Test
    fun `a change by someone else at night is logged, never re-applied, and left alone until the next screen off`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        device.wifi = true
        engine.radiosChanged()
        assertThat(journal.events<EngineEvent.Drift>().single().lever).isEqualTo(Lever.WIFI)
        engine.holdOff(HoldOff.CALL, active = true)
        engine.holdOff(HoldOff.CALL, active = false)
        engine.screenOn()
        assertThat(extraWrites()).containsExactly(DeviceWrite.Wifi(false))
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.ARMED)

        engine.screenOff()
        assertThat(device.wifi).isFalse()
    }

    @Test
    fun `chosen apps are suspended unless held back, and only the engine's are unsuspended`() = runTest {
        device.blocked = mapOf("org.example.clock" to "handles alarms")
        device.suspended = setOf("org.example.paused")
        val engine = engine()
        engine.startWith(Extras(suspend = setOf("org.example.feed", "org.example.clock", "org.example.paused")))
        engine.screenOff()
        assertThat(device.suspended).containsExactly("org.example.feed", "org.example.paused")
        assertThat(store.state.written[Lever.APP_SUSPENSION]).isEqualTo(Setting.Suspension(setOf("org.example.feed")))
        engine.screenOn()
        // The app someone else suspended stays suspended.
        assertThat(device.suspended).containsExactly("org.example.paused")
        assertThat(extraWrites()).containsExactly(
            DeviceWrite.Suspend(listOf("org.example.feed"), suspended = true),
            DeviceWrite.Suspend(listOf("org.example.feed"), suspended = false),
        ).inOrder()
    }

    @Test
    fun `an app that joins the never-suspend list is lifted at the next screen off`() = runTest {
        val engine = engine()
        engine.startWith(Extras(suspend = setOf("org.example.feed", "org.example.chat")))
        engine.screenOff()
        assertThat(device.suspended).containsExactly("org.example.feed", "org.example.chat")
        // A screen on whose unsuspend fails, then the chat app becomes the alarm app.
        device.refuse += "write:Suspend"
        engine.screenOn()
        device.refuse.clear()
        device.blocked = mapOf("org.example.chat" to "the next alarm clock's app")
        engine.screenOff()
        assertThat(device.suspended).containsExactly("org.example.feed")
        engine.screenOn()
        assertThat(device.suspended).isEmpty()
        assertThat(store.state.written).doesNotContainKey(Lever.APP_SUSPENSION)
    }

    @Test
    fun `restore stock, pause and return to previous put every held extra back`() = runTest {
        for (undo in listOf<suspend DozeEngine.() -> Unit>({ restoreStock() }, { pause() }, { returnToPrevious() })) {
            device.wifi = true
            device.data = true
            device.suspended = emptySet()
            device.wifiConnected = false
            store.previous = PreviousSnapshot(BASE_TIME, ConstantsValue.Absent, null, emptyMap(), null, null, null)
            store.state = PersistedState()
            val engine = engine()
            engine.startWith(Extras(wifiOff = true, suspend = setOf("org.example.feed")))
            engine.screenOff()
            assertThat(device.wifi).isFalse()
            engine.undo()
            assertThat(device.wifi).isTrue()
            assertThat(device.suspended).isEmpty()
            assertThat(store.state.written.keys intersect Lever.EXTRAS).isEmpty()
        }
    }

    @Test
    fun `restore stock leaves an extra the engine does not hold alone`() = runTest {
        device.wifi = false
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.restoreStock()
        assertThat(device.wifi).isFalse()
        assertThat(extraWrites()).isEmpty()
    }

    @Test
    fun `below the root tier nothing is written, and a held extra the tier cannot release is reported`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true), writable = Writable.EVERYTHING.copy(extras = false))
        engine.screenOff()
        assertThat(extraWrites()).isEmpty()
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.NOT_WRITABLE)
        engine.screenOn()

        engine.setWritable(Writable.EVERYTHING)
        engine.screenOff()
        engine.setWritable(Writable.EVERYTHING.copy(extras = false))
        engine.screenOn()
        assertThat(device.wifi).isFalse()
        assertThat(engine.status.value.extrasStuck).containsExactly(Lever.WIFI)
    }

    @Test
    fun `an owner who chose no extra pays nothing for them`() = runTest {
        val engine = engine()
        engine.startWith(Extras())
        engine.screenOff()
        engine.screenOn()
        assertThat(device.log.filter { it == "read:radios" || it == "read:suspension" }).isEmpty()
        assertThat(engine.status.value.levers.filterKeys { it.extra }.values.map { it.state }.toSet()).containsExactly(LeverState.UNUSED)
    }

    /** Review of dcbd23a, finding 1: a chosen app someone else suspended is theirs, never drift of the engine's. */
    @Test
    fun `an app someone else suspended is no drift, and after a restart the engine's own are still lifted`() = runTest {
        device.suspended = setOf("org.example.paused")
        val engine = engine()
        engine.startWith(Extras(suspend = setOf("org.example.feed", "org.example.chat", "org.example.paused")))
        engine.screenOff()
        assertThat(engine.state(Lever.APP_SUSPENSION).state).isEqualTo(LeverState.APPLIED)

        val next = engine()
        next.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = false, writable = Writable.EVERYTHING))
        assertThat(journal.events<EngineEvent.Drift>()).isEmpty()
        next.screenOn()
        assertThat(device.suspended).containsExactly("org.example.paused")
        assertThat(store.state.written).doesNotContainKey(Lever.APP_SUSPENSION)
    }

    @Test
    fun `when someone lifts one of the engine's apps, the others are lifted too and the lever is let go`() = runTest {
        val engine = engine()
        engine.startWith(Extras(suspend = setOf("org.example.feed", "org.example.chat")))
        engine.screenOff()
        device.suspended = setOf("org.example.chat")
        val next = engine()
        next.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = false, writable = Writable.EVERYTHING))
        assertThat(journal.events<EngineEvent.Drift>().single().lever).isEqualTo(Lever.APP_SUSPENSION)
        assertThat(device.suspended).isEmpty()
        assertThat(store.state.written).doesNotContainKey(Lever.APP_SUSPENSION)
        assertThat(extraWrites().count { it is DeviceWrite.Suspend && it.suspended }).isEqualTo(1)
    }

    /** Finding 2: a suspension that answers after its read-back stays booked, so screen on lifts it. */
    @Test
    fun `a suspension that times out stays booked and is lifted at screen on`() = runTest {
        device.hang += "write:Suspend"
        val engine = engine()
        engine.startWith(Extras(suspend = setOf("org.example.feed")))
        engine.screenOff()
        assertThat(store.state.written[Lever.APP_SUSPENSION]).isEqualTo(Setting.Suspension(setOf("org.example.feed")))
        testScheduler.advanceUntilIdle()
        assertThat(device.suspended).containsExactly("org.example.feed")
        device.hang.clear()
        engine.screenOn()
        assertThat(device.suspended).isEmpty()
    }

    /** Finding 3: Wi-Fi never goes off while mobile data is off, or once it was connected since the screen went off. */
    @Test
    fun `Wi-Fi stays on with mobile data off, and after losing its network at night`() = runTest {
        device.data = false
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        assertThat(device.wifi).isTrue()
        assertThat(engine.state(Lever.WIFI).reason).isEqualTo("mobile data is off, so Wi-Fi stays on")
        engine.screenOn()

        device.data = true
        device.wifiConnected = true
        engine.screenOff()
        // The router restarts at night: Wi-Fi stays on so the phone reconnects, even across a restart of the app.
        device.wifiConnected = false
        engine.radiosChanged()
        val next = engine()
        next.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = false, writable = Writable.EVERYTHING))
        next.holdOff(HoldOff.CALL, active = true)
        next.holdOff(HoldOff.CALL, active = false)
        assertThat(extraWrites()).isEmpty()
        assertThat(next.state(Lever.WIFI).reason).contains("was connected since the screen went off")
        next.screenOn()
        next.screenOff()
        assertThat(device.wifi).isFalse()
    }

    /** Finding 4: Wi-Fi reads on only once it has started, so the release waits for that before judging. */
    @Test
    fun `a Wi-Fi release waits for Wi-Fi to start before it is judged`() = runTest {
        device.wifiStartReads = 3
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        engine.screenOn()
        assertThat(journal.actions.map { it.outcome }).containsExactly(Outcome.OK, Outcome.OK)
        assertThat(store.state.written).doesNotContainKey(Lever.WIFI)
    }

    /** Finding 5: a lever let go of stays let go across a restart, until the next screen event. */
    @Test
    fun `a lever let go of stays let go when the app restarts`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        device.wifi = true
        engine.radiosChanged()
        val next = engine()
        next.start(EngineStart(BuiltInProfiles.EVERYDAY, screenOn = false, writable = Writable.EVERYTHING))
        assertThat(device.wifi).isTrue()
        assertThat(extraWrites()).containsExactly(DeviceWrite.Wifi(false))
    }

    /** Finding 6: Wi-Fi the engine holds off is not turned on inside airplane mode; it comes back when that ends. */
    @Test
    fun `Wi-Fi held off waits out airplane mode`() = runTest {
        val engine = engine()
        engine.startWith(Extras(wifiOff = true))
        engine.screenOff()
        device.airplane = true
        engine.radiosChanged()
        engine.screenOn()
        assertThat(device.wifi).isFalse()
        assertThat(engine.state(Lever.WIFI).state).isEqualTo(LeverState.WAITING)
        device.airplane = false
        engine.radiosChanged()
        assertThat(device.wifi).isTrue()
        assertThat(store.state.written).doesNotContainKey(Lever.WIFI)
    }
}
