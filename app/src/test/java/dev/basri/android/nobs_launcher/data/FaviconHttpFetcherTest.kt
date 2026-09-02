package dev.basri.android.nobs_launcher.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconHttpFetcherTest {
    @Test
    fun fetchesDirectOriginFaviconWithBoundsAndNoCookies() {
        val connection = FakeConnection(
            url = URL("https://example.com/favicon.ico"),
            code = 200,
            body = byteArrayOf(1, 2, 3),
        )
        val openedUrls = mutableListOf<URL>()
        val fetcher = FaviconHttpFetcher { url ->
            openedUrls += url
            connection
        }

        val result = fetcher.fetch("https://example.com/some/page?x=1")

        assertArrayEquals(byteArrayOf(1, 2, 3), result)
        assertEquals(listOf(URL("https://example.com/favicon.ico")), openedUrls)
        assertEquals(4_000, connection.connectTimeout)
        assertEquals(4_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertNull(connection.requestHeaders["Cookie"])
        assertEquals("image/*", connection.requestHeaders["Accept"])
        assertTrue(connection.disconnected)
    }

    @Test
    fun followsRelativeHttpRedirectButRejectsUnsafeRedirect() {
        val first = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 302,
            headers = mapOf("Location" to "/assets/icon.png"),
        )
        val second = FakeConnection(
            URL("https://example.com/assets/icon.png"),
            code = 200,
            body = byteArrayOf(9),
        )
        val byUrl = mapOf(first.url.toString() to first, second.url.toString() to second)

        assertArrayEquals(
            byteArrayOf(9),
            FaviconHttpFetcher { byUrl.getValue(it.toString()) }.fetch("https://example.com/page"),
        )

        val unsafe = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 302,
            headers = mapOf("Location" to "file:///tmp/icon"),
        )
        assertNull(FaviconHttpFetcher { unsafe }.fetch("https://example.com"))
        assertTrue(unsafe.disconnected)
    }

    @Test
    fun rejectsTooManyRedirectsHttpErrorsDeclaredAndStreamedOversizeBodies() {
        val redirect = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 302,
            headers = mapOf("Location" to "/favicon.ico"),
        )
        assertNull(
            FaviconHttpFetcher(maxRedirects = 2, connectionFactory = { redirect })
                .fetch("https://example.com"),
        )

        val notFound = FakeConnection(URL("https://example.com/favicon.ico"), code = 404)
        assertNull(FaviconHttpFetcher { notFound }.fetch("https://example.com"))

        val declaredLarge = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 200,
            body = ByteArray(9),
            declaredLength = 9,
        )
        assertNull(
            FaviconHttpFetcher(maxBytes = 8, connectionFactory = { declaredLarge })
                .fetch("https://example.com"),
        )

        val streamedLarge = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 200,
            body = ByteArray(9),
            declaredLength = -1,
        )
        assertNull(
            FaviconHttpFetcher(maxBytes = 8, connectionFactory = { streamedLarge })
                .fetch("https://example.com"),
        )
    }

    @Test
    fun connectionFailuresReturnFallbackInsteadOfEscaping() {
        val broken = FakeConnection(
            URL("https://example.com/favicon.ico"),
            code = 200,
            responseFailure = IOException("timeout"),
        )

        assertNull(FaviconHttpFetcher { broken }.fetch("https://example.com"))
        assertTrue(broken.disconnected)
    }

    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val body: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(),
        private val declaredLength: Int = body.size,
        private val responseFailure: IOException? = null,
    ) : HttpURLConnection(url) {
        val requestHeaders = linkedMapOf<String, String>()
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return code
        }

        override fun getHeaderField(name: String?): String? = headers[name]

        override fun getContentLength(): Int = declaredLength

        override fun getInputStream(): InputStream = ByteArrayInputStream(body)

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }
    }
}
