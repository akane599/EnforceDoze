// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.akane599.enforcedoze.parse.DeviceIdleSettings

/**
 * Writes lever A, the global `device_idle_constants` string, from the app's own process (ADR 0012).
 * SettingsProvider needs only `WRITE_SECURE_SETTINGS` for a global write and records this package as
 * the writer. The app cannot read the setting back (hidden, not `@Readable`); the worker reads it.
 */
public interface ConstantsWriter {
    /** Whether the app holds `WRITE_SECURE_SETTINGS`. */
    public fun canWrite(): Boolean

    /** Stores [value] as the whole string, or deletes the setting when it is null. False when refused. */
    public fun write(value: String?): Boolean

    public companion object {
        /** The writer the engine uses: `Settings.Global` from the app's process. */
        public fun create(context: Context): ConstantsWriter = SettingsConstantsWriter(context.applicationContext)
    }
}

internal class SettingsConstantsWriter(private val context: Context) : ConstantsWriter {
    override fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    override fun write(value: String?): Boolean = try {
        val resolver = context.contentResolver
        if (value == null) {
            resolver.delete(Settings.Global.getUriFor(DeviceIdleSettings.SETTING), null, null)
            true
        } else {
            Settings.Global.putString(resolver, DeviceIdleSettings.SETTING, value)
        }
    } catch (_: SecurityException) {
        false
    }
}
