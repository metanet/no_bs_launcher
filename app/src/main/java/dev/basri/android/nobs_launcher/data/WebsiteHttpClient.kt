package dev.basri.android.nobs_launcher.data

import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer

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
        var redirects = 0

        while (true) {
            try {
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", ACCEPT_HEADER)
                    .header("User-Agent", USER_AGENT)
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
                    }

                    if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                        return WebsiteProbeResult.Reachable(currentUrl.toString(), null)
                    }
                    if (response.code !in 200..299) return WebsiteProbeResult.Inaccessible

                    val body = response.body
                    val html = if (body.contentType().isHtml()) {
                        readHtmlPrefix(body, startedAtMillis)
                    } else {
                        null
                    }
                    return WebsiteProbeResult.Reachable(currentUrl.toString(), html)
                }
                currentUrl = nextUrl ?: return WebsiteProbeResult.Inaccessible
                redirects += 1
            } catch (_: Exception) {
                return WebsiteProbeResult.Inaccessible
            }
        }
    }

    private fun readHtmlPrefix(body: ResponseBody, startedAtMillis: Long): String {
        val output = Buffer()
        val source = body.source()
        var remainingBytes = MAX_HTML_BYTES.toLong()
        while (remainingBytes > 0L) {
            remainingTimeoutMillis(startedAtMillis)
            val read = source.read(output, minOf(BUFFER_SIZE.toLong(), remainingBytes))
            remainingTimeoutMillis(startedAtMillis)
            if (read < 0L) break
            if (read == 0L) continue
            remainingBytes -= read
        }
        return output.readString(Charsets.UTF_8)
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

    private fun okhttp3.MediaType?.isHtml(): Boolean {
        val mediaType = this ?: return false
        val value = "${mediaType.type}/${mediaType.subtype}".lowercase(Locale.ROOT)
        return value == "text/html" || value == "application/xhtml+xml"
    }

    private companion object {
        const val MAX_HTML_BYTES = 256 * 1024
        const val MAX_REDIRECTS = 5
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val BUFFER_SIZE = 8 * 1024
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val ACCEPT_HEADER = "text/html, application/xhtml+xml"
        const val USER_AGENT = "NoBullshitLauncher/0.3"
    }
}

private const val PER_OPERATION_TIMEOUT_MILLIS = 4_000L
private const val DEFAULT_OVERALL_TIMEOUT_MILLIS = 12_000L
