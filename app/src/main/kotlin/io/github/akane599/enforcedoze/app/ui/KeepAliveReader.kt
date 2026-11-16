// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import io.github.akane599.enforcedoze.feature.onboarding.KeepAlive

/**
 * Whether the system lets the app run in the background (SPEC 3.3, parity row 35), from public SDK calls only:
 * `PowerManager.isIgnoringBatteryOptimizations`, `UsageStatsManager.getAppStandbyBucket` and
 * `ActivityManager.isBackgroundRestricted`. One UI's "Never sleeping apps" list has no public reading, so the
 * onboarding text names where it is.
 */
internal object KeepAliveReader {
    private const val TAG = "KeepAlive"

    fun read(context: Context): KeepAlive = of(
        exempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket,
        restricted = context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
        manufacturer = Build.MANUFACTURER,
    )

    fun of(exempt: Boolean, bucket: Int, restricted: Boolean, manufacturer: String): KeepAlive = KeepAlive(
        exempt = exempt && !restricted,
        detail = "isIgnoringBatteryOptimizations=$exempt appStandbyBucket=$bucket isBackgroundRestricted=$restricted",
        samsung = manufacturer.equals("samsung", ignoreCase = true),
    )

    /**
     * The system's own dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which needs
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); the app's settings page when it is missing.
     */
    // BatteryLife is Play's policy check. The fork ships through GitHub Releases (SPEC 12.6), and a Doze manager that must
    // see every screen-off is the use case the exemption exists for; the owner confirms it in the system's dialog.
    @SuppressLint("BatteryLife")
    fun requestExemption(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        if (!start(context, request)) openAppSettings(context)
    }

    fun openAppSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no activity for ${intent.action}", e)
        false
    }
}
