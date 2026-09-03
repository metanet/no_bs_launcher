package dev.basri.android.nobs_launcher.data

import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer

fun interface FaviconBytesFetcher {
    fun fetch(iconUrl: String): ByteArray?

    fun fetch(iconUrl: String, timeoutMillis: Long): ByteArray? = fetch(iconUrl)
}

internal fun newFaviconOkHttpClient(): OkHttpClient = newPrivateHttpClient(
    perOperationTimeoutMillis = TIMEOUT_MILLIS,
    overallTimeoutMillis = TIMEOUT_MILLIS,
)

private val FAVICON_HTTP_CLIENT: OkHttpClient by lazy(::newFaviconOkHttpClient)

class FaviconHttpFetcher(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    private val callFactory: Call.Factory = FAVICON_HTTP_CLIENT,
    private val monotonicClockMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val overallTimeoutMillis: Long = TIMEOUT_MILLIS,
) : FaviconBytesFetcher {
    override fun fetch(iconUrl: String): ByteArray? = fetch(iconUrl, overallTimeoutMillis)

    override fun fetch(iconUrl: String, timeoutMillis: Long): ByteArray? {
        val effectiveTimeoutMillis = minOf(timeoutMillis, overallTimeoutMillis)
        if (maxBytes <= 0 || maxRedirects < 0 || effectiveTimeoutMillis <= 0L) return null
        var currentUrl = iconUrl.toHttpUrlOrNull() ?: return null
        val networkAccess = initialNetworkAccess(currentUrl)
        var redirects = 0
        val startedAtMillis = monotonicClockMillis()

        while (true) {
            try {
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", ACCEPT_HEADER)
                    .header("User-Agent", USER_AGENT)
                    .tag(NetworkAccess::class.java, networkAccess)
                    .build()
                val call = callFactory.newCall(request)
                call.timeout().timeout(
                    remainingTimeoutMillis(startedAtMillis, effectiveTimeoutMillis),
                    TimeUnit.MILLISECONDS,
                )

                val nextUrl: HttpUrl? = call.execute().use { response ->
                    remainingTimeoutMillis(startedAtMillis, effectiveTimeoutMillis)
                    if (response.code in 300..399) {
                        if (redirects >= maxRedirects) return null
                        val location = response.header("Location") ?: return null
                        return@use currentUrl.resolve(location)
                            ?.takeIf { target ->
                                isAllowedRedirect(currentUrl, target, networkAccess)
                            }
                    }
                    if (response.code !in 200..299) return null

                    val body = response.body
                    if (body.contentLength() > maxBytes) return null
                    return readBounded(body, startedAtMillis, effectiveTimeoutMillis)
                }
                currentUrl = nextUrl ?: return null
                redirects += 1
            } catch (_: Exception) {
                return null
            }
        }
    }

    private fun readBounded(
        body: ResponseBody,
        startedAtMillis: Long,
        timeoutMillis: Long,
    ): ByteArray? {
        val output = Buffer()
        val source = body.source()
        var remainingBytes = maxBytes.toLong() + 1L
        while (remainingBytes > 0L) {
            remainingTimeoutMillis(startedAtMillis, timeoutMillis)
            val read = source.read(output, minOf(BUFFER_SIZE.toLong(), remainingBytes))
            remainingTimeoutMillis(startedAtMillis, timeoutMillis)
            if (read < 0L) break
            if (read == 0L) continue
            remainingBytes -= read
        }
        return output.readByteArray()
            .takeIf { it.isNotEmpty() && it.size <= maxBytes }
    }

    private fun remainingTimeoutMillis(startedAtMillis: Long, timeoutMillis: Long): Long {
        val elapsedMillis = monotonicClockMillis() - startedAtMillis
        val remainingMillis = when {
            elapsedMillis <= 0L -> timeoutMillis
            elapsedMillis >= timeoutMillis -> 0L
            else -> timeoutMillis - elapsedMillis
        }
        if (remainingMillis <= 0L) throw SocketTimeoutException("Favicon fetch deadline exceeded")
        return remainingMillis
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 256 * 1024
        const val DEFAULT_MAX_REDIRECTS = 5
        const val BUFFER_SIZE = 8 * 1024
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val ACCEPT_HEADER = "image/*"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android)"
    }
}

private const val TIMEOUT_MILLIS = 4_000L
