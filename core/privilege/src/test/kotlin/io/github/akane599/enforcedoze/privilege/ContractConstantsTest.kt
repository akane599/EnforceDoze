// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import org.junit.Test

class ContractConstantsTest {
    @Test
    fun `the worker reads the setting the app writes`() {
        assertThat(WorkerContract.CONSTANTS_SETTING).isEqualTo(DeviceIdleSettings.SETTING)
    }
}
