package dev.basri.android.nobs_launcher.data

import java.io.IOException
import java.net.InetAddress
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal data class NetworkAccess(val allowPrivateAddresses: Boolean)

internal fun initialNetworkAccess(url: HttpUrl): NetworkAccess = NetworkAccess(
    allowPrivateAddresses = literalAddress(url.host)?.isNonPublicAddress() == true ||
        url.host.equals("localhost", ignoreCase = true),
)

internal fun isAllowedRedirect(from: HttpUrl, to: HttpUrl, access: NetworkAccess): Boolean =
    !(from.isHttps && !to.isHttps) &&
        to.username.isEmpty() &&
        to.password.isEmpty() &&
        (access.allowPrivateAddresses || literalAddress(to.host)?.isNonPublicAddress() != true)

internal fun OkHttpClient.Builder.rejectUnexpectedPrivatePeers(): OkHttpClient.Builder =
    addNetworkInterceptor { chain ->
        val access = chain.request().tag(NetworkAccess::class.java)
        val peer = chain.connection()?.route()?.socketAddress?.address
        if (access?.allowPrivateAddresses == false && peer?.isNonPublicAddress() == true) {
            throw IOException("Public shortcut resolved to a non-public network address")
        }
        chain.proceed(chain.request())
    }

private fun literalAddress(host: String): InetAddress? {
    val numeric = host.contains(':') || IPV4_ADDRESS.matches(host)
    if (!numeric) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()
}

private fun InetAddress.isNonPublicAddress(): Boolean {
    if (
        isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress ||
        isMulticastAddress
    ) {
        return true
    }
    val value = address
    return value.size == 16 && (value[0].toInt() and 0xfe) == 0xfc
}

private val IPV4_ADDRESS = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
