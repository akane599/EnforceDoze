// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.ConnectionService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import io.github.akane599.enforcedoze.doze.RadioReading
import io.github.akane599.enforcedoze.doze.SuspensionReading

/** What the extras need to read from the phone (ADR 0038), all through public calls in the app process. */
internal interface ExtrasReader {
    fun radios(): RadioReading

    fun suspension(packages: Set<String>): SuspensionReading
}

/**
 * Why an app may never be suspended (ADR 0038): it rings for calls, keeps alarms, is the phone's own, or EnforceDoze
 * needs it to undo the suspension. Checked when the owner picks apps and again at every screen off.
 */
public enum class NeverSuspend {
    NOT_INSTALLED,
    UNDO,
    SYSTEM,
    DIALER,
    SMS,
    HOME,
    KEYBOARD,
    ACCESSIBILITY,
    ALARMS,
    NEXT_ALARM,
    CALLS,
    KEEP_AWAKE,
    ;

    /** The reason as the action log and the evidence export show it. */
    public val line: String
        get() = when (this) {
            NOT_INSTALLED -> "not installed"
            UNDO -> "EnforceDoze needs it to undo the suspension"
            SYSTEM -> "a system app"
            DIALER -> "the phone app"
            SMS -> "the messaging app"
            HOME -> "a home screen"
            KEYBOARD -> "an enabled keyboard"
            ACCESSIBILITY -> "an enabled accessibility service"
            ALARMS -> "it sets alarms"
            NEXT_ALARM -> "the next alarm clock's app"
            CALLS -> "it can ring for calls"
            KEEP_AWAKE -> "a keep-awake app"
        }
}

/** Why each of [packages] may never be suspended now, for the app picker and Settings (ADR 0038). */
public fun neverSuspend(context: Context, packages: Set<String>, keepAwake: Set<String>): Map<String, NeverSuspend> =
    AndroidExtrasReader(context.applicationContext) { keepAwake }.reasons(packages)

/** The phone's facts the never-suspend rules read, gathered once per check. */
public data class PackageFacts(
    val installed: Set<String>,
    val system: Set<String>,
    val undo: Set<String>,
    val dialer: String?,
    val sms: String?,
    val home: Set<String>,
    val keyboards: Set<String>,
    val accessibility: Set<String>,
    val alarms: Set<String>,
    val nextAlarm: String?,
    val calls: Set<String>,
    val keepAwake: Set<String>,
)

/** Pure rules; the first reason that applies is the one shown. */
public object NeverSuspendRules {
    public fun reasons(packages: Set<String>, facts: PackageFacts): Map<String, NeverSuspend> =
        packages.mapNotNull { name -> reason(name, facts)?.let { name to it } }.toMap()

    @Suppress("CyclomaticComplexMethod") // One branch per rule of ADR 0038, in the order shown to the owner.
    private fun reason(name: String, facts: PackageFacts): NeverSuspend? = when (name) {
        !in facts.installed -> NeverSuspend.NOT_INSTALLED
        in facts.undo -> NeverSuspend.UNDO
        in facts.system -> NeverSuspend.SYSTEM
        facts.dialer -> NeverSuspend.DIALER
        facts.sms -> NeverSuspend.SMS
        in facts.home -> NeverSuspend.HOME
        in facts.keyboards -> NeverSuspend.KEYBOARD
        in facts.accessibility -> NeverSuspend.ACCESSIBILITY
        facts.nextAlarm -> NeverSuspend.NEXT_ALARM
        in facts.alarms -> NeverSuspend.ALARMS
        in facts.calls -> NeverSuspend.CALLS
        in facts.keepAwake -> NeverSuspend.KEEP_AWAKE
        else -> null
    }
}

/**
 * The extras' reads from public SDK calls. Wi-Fi with `ACCESS_WIFI_STATE`; connections, and
 * `TelephonyManager.isDataEnabled`, with `ACCESS_NETWORK_STATE` (its `@RequiresPermission` anyOf, android-17.0.0_r1);
 * suspension with `PackageManager.isPackageSuspended`, which `QUERY_ALL_PACKAGES` makes see every app.
 */
internal class AndroidExtrasReader(private val context: Context, private val keepAwake: () -> Set<String>) : ExtrasReader {
    private val pm: PackageManager get() = context.packageManager

    override fun radios(): RadioReading {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)

        @Suppress("DEPRECATION") // allNetworks still lists every network; its replacement needs a callback.
        val wifiNetworks = connectivity.allNetworks.mapNotNull(connectivity::getNetworkCapabilities)
            .filter { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        // The default network: null while the app is blocked, which reads as "not on Wi-Fi", so mobile data stays on.
        val active = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        return RadioReading(
            wifiEnabled = context.getSystemService(WifiManager::class.java).isWifiEnabled,
            wifiConnected = wifiNetworks.isNotEmpty(),
            wifiValidated = active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                active.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            dataEnabled = dataEnabled(),
            airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
        )
    }

    /** A refusal fails the whole read, so nothing is turned off on a guess. */
    private fun dataEnabled(): Boolean = try {
        context.getSystemService(TelephonyManager::class.java).isDataEnabled
    } catch (e: SecurityException) {
        throw IllegalStateException("TelephonyManager.isDataEnabled was refused", e)
    }

    override fun suspension(packages: Set<String>): SuspensionReading {
        val facts = facts(packages)
        val suspended = packages.filter { it in facts.installed && suspended(it) }.toSet()
        return SuspensionReading(suspended, NeverSuspendRules.reasons(packages, facts).mapValues { it.value.line })
    }

    /** The never-suspend list's reasons for [packages], for the app picker. */
    fun reasons(packages: Set<String>): Map<String, NeverSuspend> = NeverSuspendRules.reasons(packages, facts(packages))

    private fun suspended(name: String): Boolean = try {
        pm.isPackageSuspended(name)
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun facts(packages: Set<String>): PackageFacts {
        val infos = packages.mapNotNull(::info).associateBy { it.packageName }
        val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock
        return PackageFacts(
            installed = infos.keys,
            system = infos.values.filter { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { it.packageName }.toSet(),
            undo = setOf(context.packageName, SHIZUKU_MANAGER),
            dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            sms = Telephony.Sms.getDefaultSmsPackage(context),
            home = activities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
            keyboards = context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet(),
            accessibility = context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet(),
            alarms = activities(Intent(AlarmClock.ACTION_SET_ALARM)) + activities(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            nextAlarm = alarm?.showIntent?.creatorPackage,
            calls = pm.queryIntentServices(Intent(ConnectionService.SERVICE_INTERFACE), PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { it.serviceInfo?.packageName }.toSet(),
            keepAwake = keepAwake(),
        )
    }

    private fun info(name: String): ApplicationInfo? = try {
        pm.getApplicationInfo(name, PackageManager.ApplicationInfoFlags.of(0))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun activities(intent: Intent): Set<String> =
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            .mapNotNull { it.activityInfo?.packageName }.toSet()

    private companion object {
        /** Shizuku's manager app: suspending it would stop the worker that lifts the suspension. */
        const val SHIZUKU_MANAGER = "moe.shizuku.privileged.api"
    }
}
