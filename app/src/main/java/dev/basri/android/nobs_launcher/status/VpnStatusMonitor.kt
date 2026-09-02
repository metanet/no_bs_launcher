package dev.basri.android.nobs_launcher.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

internal fun buildVpnNetworkRequest(): NetworkRequest = NetworkRequest.Builder()
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
    .build()

class VpnStatusMonitor(
    context: Context,
    private val onLabelChanged: (String?) -> Unit,
) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vpnNetworks = mutableSetOf<Network>()
    private var started = false
    private var lastLabel: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(vpnNetworks) { vpnNetworks += network }
            publishCurrentState()
        }

        override fun onLost(network: Network) {
            synchronized(vpnNetworks) { vpnNetworks -= network }
            publishCurrentState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            synchronized(vpnNetworks) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpnNetworks += network
                } else {
                    vpnNetworks -= network
                }
            }
            publishCurrentState()
        }
    }

    fun start() {
        if (started) return
        started = true
        connectivityManager.registerNetworkCallback(buildVpnNetworkRequest(), callback)
        publishCurrentState()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(vpnNetworks) { vpnNetworks.clear() }
    }

    private fun publishCurrentState() {
        val vpnActive = synchronized(vpnNetworks) { vpnNetworks.isNotEmpty() }
        val label = VpnStatusPolicy.label(vpnActive)
        mainHandler.post {
            if (label != lastLabel) {
                lastLabel = label
                onLabelChanged(label)
            }
        }
    }
}
