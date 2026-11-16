// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Wi-Fi networks coming, going or gaining and losing validation (ADR 0038): mobile data comes back the moment the
 * Wi-Fi network it relied on goes. An event from the system, not polling (SPEC 1.3); signal-strength changes, which
 * arrive often, are filtered out. Main thread.
 */
internal class NetworkWatch(context: Context, private val changed: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val validated = mutableMapOf<Network, Boolean>()

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
        callback = watch
    }

    fun stop() {
        callback?.let(connectivity::unregisterNetworkCallback)
        callback = null
        validated.clear()
    }
}
