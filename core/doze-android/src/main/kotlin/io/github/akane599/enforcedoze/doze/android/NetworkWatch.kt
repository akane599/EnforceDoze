// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Wi-Fi networks coming, going or gaining and losing validation, and airplane mode turning on or off (ADR 0038):
 * mobile data comes back the moment the Wi-Fi network it relied on goes, and Wi-Fi held off through airplane mode
 * comes back when it ends. Events from the system, not polling (SPEC 1.3); signal-strength changes, which arrive
 * often, are filtered out. Main thread.
 */
internal class NetworkWatch(private val context: Context, private val changed: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val validated = mutableMapOf<Network, Boolean>()

    /** `ACTION_AIRPLANE_MODE_CHANGED` is a protected broadcast only the system sends. */
    private val airplane = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = changed()
    }

    fun start() {
        if (callback != null) return
        val watch = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed()

            override fun onLost(network: Network) {
                validated.remove(network)
                changed()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val now = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (validated.put(network, now) != now) changed()
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, watch)
        context.registerReceiver(airplane, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        callback = watch
    }

    fun stop() {
        val watch = callback ?: return
        connectivity.unregisterNetworkCallback(watch)
        context.unregisterReceiver(airplane)
        callback = null
        validated.clear()
    }
}
