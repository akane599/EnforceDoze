// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TierResolverTest {
    private val shell = ShizukuStatus.Ready(uid = 2000, version = 13, sui = false)
    private val shizukuRoot = ShizukuStatus.Ready(uid = 0, version = 13, sui = false)
    private val sui = ShizukuStatus.Ready(uid = 0, version = 13, sui = true)

    private fun state(
        grants: Set<Grant> = emptySet(),
        shizuku: ShizukuStatus = ShizukuStatus.NotRunning,
        root: Boolean = false,
        worker: WorkerStatus = WorkerStatus.Idle,
    ) = PrivilegeState(grants, shizuku, root, worker)

    @Test
    fun `no privilege is T0 without a worker`() {
        assertThat(PrivilegeState.NONE.tier).isEqualTo(Tier.T0)
        assertThat(PrivilegeState.NONE.workerSource).isNull()
    }

    @Test
    fun `grants without DUMP stay T0`() {
        assertThat(state(grants = setOf(Grant.WRITE_SECURE_SETTINGS, Grant.READ_LOGS)).tier).isEqualTo(Tier.T0)
    }

    @Test
    fun `DUMP grant is T1`() {
        assertThat(state(grants = setOf(Grant.DUMP)).tier).isEqualTo(Tier.T1)
    }

    @Test
    fun `Shizuku as shell is T2 and as root is T3`() {
        assertThat(state(shizuku = shell).tier).isEqualTo(Tier.T2)
        assertThat(state(shizuku = shell).workerSource).isEqualTo(WorkerSource.SHIZUKU)
        assertThat(state(shizuku = shizukuRoot).tier).isEqualTo(Tier.T3)
        assertThat(state(shizuku = shizukuRoot).workerSource).isEqualTo(WorkerSource.SHIZUKU)
    }

    @Test
    fun `Sui is its own source`() {
        assertThat(state(shizuku = sui).tier).isEqualTo(Tier.T3)
        assertThat(state(shizuku = sui).workerSource).isEqualTo(WorkerSource.SUI)
    }

    @Test
    fun `Shizuku without permission does not count`() {
        val pending = state(grants = setOf(Grant.DUMP), shizuku = ShizukuStatus.PermissionNeeded(canRequest = true))
        assertThat(pending.tier).isEqualTo(Tier.T1)
        assertThat(pending.workerSource).isNull()
        assertThat(state(shizuku = ShizukuStatus.Unsupported).tier).isEqualTo(Tier.T0)
    }

    @Test
    fun `root beats Shizuku as shell`() {
        val both = state(shizuku = shell, root = true)
        assertThat(both.tier).isEqualTo(Tier.T3)
        assertThat(both.workerSource).isEqualTo(WorkerSource.ROOT)
    }

    @Test
    fun `Shizuku as root is preferred over libsu at the same tier`() {
        assertThat(state(shizuku = shizukuRoot, root = true).workerSource).isEqualTo(WorkerSource.SHIZUKU)
        assertThat(state(shizuku = sui, root = true).workerSource).isEqualTo(WorkerSource.SUI)
    }

    @Test
    fun `an unavailable source falls back to the next one`() {
        val shizukuDown = WorkerStatus.Unavailable(WorkerSource.SHIZUKU, "no connection within 5 s")
        assertThat(state(grants = setOf(Grant.DUMP), shizuku = shell, worker = shizukuDown).tier).isEqualTo(Tier.T1)
        assertThat(state(shizuku = shell, worker = shizukuDown).tier).isEqualTo(Tier.T0)
        val rootLeft = state(shizuku = shizukuRoot, root = true, worker = shizukuDown)
        assertThat(rootLeft.tier).isEqualTo(Tier.T3)
        assertThat(rootLeft.workerSource).isEqualTo(WorkerSource.ROOT)
    }

    @Test
    fun `a bound worker does not change the tier`() {
        val bound = state(shizuku = shell, worker = WorkerStatus.Bound(WorkerSource.SHIZUKU, 2000))
        assertThat(bound.tier).isEqualTo(Tier.T2)
    }
}
