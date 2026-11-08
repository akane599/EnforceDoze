// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.StreamResult
import org.junit.Test
import java.io.OutputStream

class WorkerCoreTest {
    private class FakeRunner : CommandRunner {
        val ran = mutableListOf<List<String>>()
        val timeouts = mutableListOf<Long>()

        override fun run(argv: List<String>, timeoutMillis: Long): CommandOutcome {
            ran += argv
            timeouts += timeoutMillis
            return CommandOutcome(argv, 0, "", "", false, 1, null)
        }

        override fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream): StreamResult = error("not used")
    }

    private class FakePrivacy(var on: Boolean = false, val fail: Boolean = false) : SensorPrivacyAccess {
        override fun isEnabled(): Boolean = on

        override fun setEnabled(enabled: Boolean) {
            if (fail) throw NoSuchMethodError("setSensorPrivacy")
            on = enabled
        }
    }

    private val runner = FakeRunner()
    private val privacy = FakePrivacy()
    private val core = WorkerCore(runner, { it == "com.example.app" || it == SELF }, privacy, SELF)

    @Test fun constantReadsUseFixedArgv() {
        assertThat(core.getDeviceIdleSettings().isSuccess).isTrue()
        assertThat(core.listDeviceIdleConfig().isSuccess).isTrue()
        assertThat(runner.ran).containsExactly(
            listOf("settings", "get", "global", "device_idle_constants"),
            listOf("device_config", "list", "device_idle"),
        ).inOrder()
        assertThat(runner.timeouts.toSet()).containsExactly(5_000L)
    }

    @Test fun forceIdleAndQueriesAreWhitelisted() {
        assertThat(core.forceIdle("deep").isSuccess).isTrue()
        assertThat(core.forceIdle("light").isSuccess).isTrue()
        assertThat(core.forceIdle("deep; reboot").error).isNotNull()
        assertThat(core.forceIdle(null).error).isNotNull()
        assertThat(core.getIdleState("force").isSuccess).isTrue()
        assertThat(core.getIdleState("network").error).isNotNull()
        core.unforce()
        assertThat(runner.ran).containsExactly(
            listOf("cmd", "deviceidle", "force-idle", "deep"),
            listOf("cmd", "deviceidle", "force-idle", "light"),
            listOf("cmd", "deviceidle", "get", "force"),
            listOf("cmd", "deviceidle", "unforce"),
        ).inOrder()
    }

    @Test fun sensorRestrictionNeedsAnInstalledPackage() {
        assertThat(core.setSensorRestriction(SELF).isSuccess).isTrue()
        assertThat(core.setSensorRestriction(null).isSuccess).isTrue()
        assertThat(core.setSensorRestriction("com.not.installed").error).contains("not installed")
        assertThat(core.setSensorRestriction("").error).contains("not a package")
        assertThat(core.setSensorRestriction("a b").error).contains("not a package")
        assertThat(runner.ran).containsExactly(
            listOf("dumpsys", "sensorservice", "restrict", SELF),
            listOf("dumpsys", "sensorservice", "enable"),
        ).inOrder()
    }

    @Test fun allowlistValidatesPackages() {
        core.allowlist("com.example.app", add = true)
        core.allowlist("com.example.app", add = false)
        assertThat(core.allowlist("-com.example.app", add = true).error).isNotNull()
        assertThat(core.allowlist(null, add = true).error).isNotNull()
        assertThat(runner.ran).containsExactly(
            listOf("cmd", "deviceidle", "whitelist", "+com.example.app"),
            listOf("cmd", "deviceidle", "whitelist", "-com.example.app"),
        ).inOrder()
    }

    @Test fun grantsOnlyForThisAppAndAllowedNames() {
        core.grantSelf("android.permission.DUMP")
        core.setSelfAppOp("GET_USAGE_STATS", "allow")
        assertThat(core.grantSelf("android.permission.INTERACT_ACROSS_USERS_FULL").error).isNotNull()
        assertThat(core.setSelfAppOp("RUN_IN_BACKGROUND", "allow").error).isNotNull()
        assertThat(core.setSelfAppOp("GET_USAGE_STATS", "deny everything").error).isNotNull()
        assertThat(runner.ran).containsExactly(
            listOf("pm", "grant", SELF, "android.permission.DUMP"),
            listOf("appops", "set", SELF, "GET_USAGE_STATS", "allow"),
        ).inOrder()
    }

    @Test fun sensorPrivacyReportsStateAndFailures() {
        assertThat(core.getAllSensorsOff().stdout).isEqualTo("false")
        assertThat(core.setAllSensorsOff(true).stdout).isEqualTo("true")
        assertThat(privacy.on).isTrue()
        val broken = WorkerCore(runner, { true }, FakePrivacy(fail = true), SELF)
        val out = broken.setAllSensorsOff(true)
        assertThat(out.isSuccess).isFalse()
        assertThat(out.error).contains("NoSuchMethodError")
    }

    @Test fun readArguments() {
        assertThat(core.readEventLogArgv(0)).containsExactly("logcat", "-b", "events", "-d", "-v", "epoch").inOrder()
        assertThat(core.readEventLogArgv(1_794_082_053_012)).containsExactly(
            "logcat",
            "-b",
            "events",
            "-d",
            "-v",
            "epoch",
            "-T",
            "1794082053.012",
        ).inOrder()
        assertThat(core.readEventLogArgv(5)).contains("0.005")
        assertThat(core.readBatteryHistoryArgv(0)).containsExactly("dumpsys", "batterystats", "--history-start", "0").inOrder()
        assertThat(core.readBatteryHistoryArgv(-5)).containsExactly("dumpsys", "batterystats", "--history-start", "0").inOrder()
        assertThat(core.readBatteryHistoryArgv(42)).containsExactly("dumpsys", "batterystats", "--history-start", "42").inOrder()
        assertThat(core.readAlarmsArgv()).containsExactly("dumpsys", "alarm").inOrder()
    }

    private companion object {
        const val SELF = "io.github.akane599.enforcedoze"
    }
}
