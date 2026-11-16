// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** ADR 0038: apps that ring for calls, keep alarms, or that the undo needs, are never suspended. */
class NeverSuspendRulesTest {
    private val facts = PackageFacts(
        installed = setOf(
            "org.example.feed", "org.example.phone", "org.example.sms", "org.example.home", "org.example.keys",
            "org.example.reader", "org.example.clock", "org.example.voip", "org.example.maps", "org.example.system",
            "io.github.akane599.enforcedoze", "org.example.wakeup",
        ),
        system = setOf("org.example.system"),
        undo = setOf("io.github.akane599.enforcedoze", "moe.shizuku.privileged.api"),
        dialer = "org.example.phone",
        sms = "org.example.sms",
        home = setOf("org.example.home"),
        keyboards = setOf("org.example.keys"),
        accessibility = setOf("org.example.reader"),
        alarms = setOf("org.example.clock"),
        nextAlarm = "org.example.wakeup",
        calls = setOf("org.example.voip"),
        keepAwake = setOf("org.example.maps"),
    )

    @Test
    fun `each rule holds its app back, and an ordinary app may be suspended`() {
        val reasons = NeverSuspendRules.reasons(facts.installed + "moe.shizuku.privileged.api" + "org.example.gone", facts)
        assertThat(reasons).containsExactly(
            "org.example.phone", NeverSuspend.DIALER,
            "org.example.sms", NeverSuspend.SMS,
            "org.example.home", NeverSuspend.HOME,
            "org.example.keys", NeverSuspend.KEYBOARD,
            "org.example.reader", NeverSuspend.ACCESSIBILITY,
            "org.example.clock", NeverSuspend.ALARMS,
            "org.example.wakeup", NeverSuspend.NEXT_ALARM,
            "org.example.voip", NeverSuspend.CALLS,
            "org.example.maps", NeverSuspend.KEEP_AWAKE,
            "org.example.system", NeverSuspend.SYSTEM,
            "io.github.akane599.enforcedoze", NeverSuspend.UNDO,
            "moe.shizuku.privileged.api", NeverSuspend.NOT_INSTALLED,
            "org.example.gone", NeverSuspend.NOT_INSTALLED,
        )
        assertThat(reasons).doesNotContainKey("org.example.feed")
    }

    @Test
    fun `the next alarm's app is named as such even when it also sets alarms`() {
        val both = facts.copy(alarms = facts.alarms + "org.example.wakeup")
        assertThat(
            NeverSuspendRules.reasons(setOf("org.example.wakeup"), both),
        ).containsExactly("org.example.wakeup", NeverSuspend.NEXT_ALARM)
    }
}
