package dev.basri.android.nobs_launcher.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

class FaviconHttpFetcher(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    fun fetch(siteUrl: String): ByteArray? {
        val initialUrl = faviconUrl(siteUrl) ?: return null
        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = connectionFactory(currentUrl).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    doInput = true
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    if (redirects >= maxRedirects) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    val redirected = runCatching {
                        currentUrl.toURI().resolve(location).toURL()
                    }.getOrNull() ?: return null
                    if (redirected.protocol.lowercase(Locale.ROOT) !in ALLOWED_SCHEMES) return null
                    currentUrl = redirected
                    redirects += 1
                    continue
                }
                if (responseCode !in 200..299) return null
                val declaredLength = connection.contentLength
                if (declaredLength > maxBytes) return null
                return connection.inputStream.use(::readBounded)
            } catch (_: Exception) {
                return null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun faviconUrl(siteUrl: String): URL? = runCatching {
        val site = URI(siteUrl)
        URI(
            site.scheme,
            null,
            site.host,
            site.port,
            "/favicon.ico",
            null,
            null,
        ).toURL()
    }.getOrNull()?.takeIf { it.protocol.lowercase(Locale.ROOT) in ALLOWED_SCHEMES }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf(ByteArray::isNotEmpty)
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 256 * 1024
        const val DEFAULT_MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 4_000
        const val BUFFER_SIZE = 8 * 1024
        const val USER_AGENT = "NoBullshitLauncher/0.3"
        val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
