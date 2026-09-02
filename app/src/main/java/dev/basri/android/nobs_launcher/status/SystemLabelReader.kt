package dev.basri.android.nobs_launcher.status

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.TimeZone

class SystemLabelReader(context: Context) {
    private val appContext = context.applicationContext

    fun wifiName(): String? {
        if (!hasWifiNamePermission(appContext)) return null
        val modernSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            readActiveNetworkSsid()
        } else {
            null
        }
        return SystemLabelPolicy.normalizeWifiSsid(modernSsid)
            ?: SystemLabelPolicy.normalizeWifiSsid(readLegacyWifiSsid())
    }

    fun locationName(): String? =
        SystemLabelPolicy.locationFromTimeZone(TimeZone.getDefault().id)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readActiveNetworkSsid(): String? = runCatching {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return@runCatching null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@runCatching null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return@runCatching null
        }
        (capabilities.transportInfo as? WifiInfo)?.ssid
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun readLegacyWifiSsid(): String? = runCatching {
        val wifi = appContext.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.connectionInfo?.ssid
    }.getOrNull()

    companion object {
        fun hasWifiNamePermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
