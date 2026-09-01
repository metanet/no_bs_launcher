package dev.basri.android.nobs_launcher.status

object VpnStatusPolicy {
    const val CONNECTED_LABEL = "VPN connected"

    fun label(vpnActive: Boolean): String? = if (vpnActive) CONNECTED_LABEL else null
}
