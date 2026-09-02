package dev.basri.android.nobs_launcher.data

import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface WebsiteProbeResult {
    data class Reachable(val finalUrl: String, val html: String?) : WebsiteProbeResult

    data object Inaccessible : WebsiteProbeResult
}

fun interface WebsiteProbeGateway {
    fun probe(url: String): WebsiteProbeResult
}

internal fun newWebsiteProbeOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(CookieJar.NO_COOKIES)
    .authenticator(Authenticator.NONE)
    .proxyAuthenticator(Authenticator.NONE)
    .cache(null)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .connectTimeout(PER_OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    .readTimeout(PER_OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    .callTimeout(DEFAULT_OVERALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    .rejectUnexpectedPrivatePeers()
    .build()

private val WEBSITE_PROBE_HTTP_CLIENT: OkHttpClient by lazy(::newWebsiteProbeOkHttpClient)

class WebsiteHttpClient(
    private val callFactory: Call.Factory = WEBSITE_PROBE_HTTP_CLIENT,
    private val monotonicClockMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val overallTimeoutMillis: Long = DEFAULT_OVERALL_TIMEOUT_MILLIS,
) : WebsiteProbeGateway {
    override fun probe(url: String): WebsiteProbeResult {
        val startedAtMillis = monotonicClockMillis()
        var currentUrl = url.toHttpUrlOrNull() ?: return WebsiteProbeResult.Inaccessible
        val networkAccess = initialNetworkAccess(currentUrl)
        var redirects = 0

        while (true) {
            try {
                val request = Request.Builder()
                    .url(currentUrl)
                    .head()
                    .header("Accept", ACCEPT_HEADER)
                    .header("User-Agent", USER_AGENT)
                    .tag(NetworkAccess::class.java, networkAccess)
                    .build()
                val call = callFactory.newCall(request)
                call.timeout().timeout(
                    remainingTimeoutMillis(startedAtMillis),
                    TimeUnit.MILLISECONDS,
                )

                val nextUrl: HttpUrl? = call.execute().use { response ->
                    remainingTimeoutMillis(startedAtMillis)
                    if (response.code in 300..399) {
                        if (redirects >= MAX_REDIRECTS) return WebsiteProbeResult.Inaccessible
                        val location = response.header("Location")
                            ?: return WebsiteProbeResult.Inaccessible
                        return@use currentUrl.resolve(location)
                            ?.takeIf { target ->
                                isAllowedRedirect(currentUrl, target, networkAccess)
                            }
                    }

                    if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                        return WebsiteProbeResult.Reachable(currentUrl.toString(), null)
                    }
                    if (response.code !in 200..299) return WebsiteProbeResult.Inaccessible

                    return WebsiteProbeResult.Reachable(currentUrl.toString(), null)
                }
                currentUrl = nextUrl ?: return WebsiteProbeResult.Inaccessible
                redirects += 1
            } catch (_: Exception) {
                return WebsiteProbeResult.Inaccessible
            }
        }
    }

    private fun remainingTimeoutMillis(startedAtMillis: Long): Long {
        val elapsedMillis = monotonicClockMillis() - startedAtMillis
        val remainingMillis = when {
            elapsedMillis <= 0L -> overallTimeoutMillis
            elapsedMillis >= overallTimeoutMillis -> 0L
            else -> overallTimeoutMillis - elapsedMillis
        }
        if (remainingMillis <= 0L) throw SocketTimeoutException("Website probe deadline exceeded")
        return remainingMillis
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val ACCEPT_HEADER = "text/html, application/xhtml+xml"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android)"
    }
}

private const val PER_OPERATION_TIMEOUT_MILLIS = 4_000L
private const val DEFAULT_OVERALL_TIMEOUT_MILLIS = 12_000L
