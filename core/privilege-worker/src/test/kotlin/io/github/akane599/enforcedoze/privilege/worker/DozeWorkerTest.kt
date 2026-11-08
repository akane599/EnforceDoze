// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.privilege.api.CommandOutcome
import io.github.akane599.enforcedoze.privilege.api.CommandRunner
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.OutputStream

class DozeWorkerTest {
    private val runner = object : CommandRunner {
        override fun run(argv: List<String>, timeoutMillis: Long) = CommandOutcome(argv, 0, "ok", "", false, 1, null)

        override fun stream(argv: List<String>, timeoutMillis: Long, sink: OutputStream) = error("not used")
    }
    private val privacy = object : SensorPrivacyAccess {
        override fun isEnabled() = false

        override fun setEnabled(enabled: Boolean) = Unit
    }
    private val core = WorkerCore(runner, { true }, privacy, "io.github.akane599.enforcedoze")
    private var caller = APP_UID
    private val worker = DozeWorker(core, APP_UID) { caller }

    @Test fun appUidMayCall() {
        assertThat(worker.unforce().isSuccess).isTrue()
        assertThat(worker.version).isEqualTo(WorkerContract.VERSION)
    }

    @Test fun otherUidsAreRefused() {
        caller = 10_999
        assertThrows(SecurityException::class.java) { worker.unforce() }
        assertThrows(SecurityException::class.java) { worker.deviceIdleSettings }
        assertThrows(SecurityException::class.java) { worker.grantSelf("android.permission.DUMP") }
        caller = 2000
        assertThrows(SecurityException::class.java) { worker.forceIdle("deep") }
    }

    private companion object {
        const val APP_UID = 10_216
    }
}
