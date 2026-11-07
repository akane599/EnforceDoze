// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import android.hardware.ISensorPrivacyManager
import android.os.ServiceManager

/**
 * The all-sensors privacy toggle (SPEC 5.4, developer tile) through the hidden `ISensorPrivacyManager`
 * of the `sensor_privacy` service (`service list` on API 37 and One UI 9: `sensor_privacy:
 * [android.hardware.ISensorPrivacyManager]`). Methods as in AOSP `ISensorPrivacyManager.aidl` at
 * `android-16.0.0_r1` and `android-17.0.0_r1`. The shell uid holds `MANAGE_SENSOR_PRIVACY` on both
 * (AOSP `packages/Shell/AndroidManifest.xml`). `cmd sensor_privacy` covers only the microphone and
 * camera toggles, so it cannot replace this.
 */
internal object HiddenSensorPrivacy : SensorPrivacyAccess {
    private const val SERVICE = "sensor_privacy"

    private fun service(): ISensorPrivacyManager = ISensorPrivacyManager.Stub.asInterface(
        ServiceManager.getService(SERVICE) ?: error("$SERVICE service not found"),
    )

    override fun isEnabled(): Boolean = service().isSensorPrivacyEnabled

    override fun setEnabled(enabled: Boolean) {
        service().setSensorPrivacy(enabled)
    }
}
