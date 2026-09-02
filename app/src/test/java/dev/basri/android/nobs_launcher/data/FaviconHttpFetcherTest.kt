package dev.basri.android.nobs_launcher.data

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconHttpFetcherTest {
    @Test
    fun privateClientDisablesAmbientStateRetriesCacheAndAutomaticRedirects() {
        val client = newFaviconOkHttpClient()

        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertEquals(4_000, client.connectTimeoutMillis)
        assertEquals(4_000, client.readTimeoutMillis)
        assertEquals(4_000, client.callTimeoutMillis)
    }

    @Test
    fun fetchesExactCandidateWithBoundedGetHeadersAndClosesBody() {
        val body = TrackingResponseBody(byteArrayOf(1, 2, 3), "image/png")
        val factory = FakeCallFactory { request, _ -> response(request, 200, body) }
        val fetcher = FaviconHttpFetcher(
            callFactory = factory,
            monotonicClockMillis = { 0L },
        )

        val result = fetcher.fetch("https://cdn.example.com/assets/icon.png?v=2")

        assertArrayEquals(byteArrayOf(1, 2, 3), result)
        val call = factory.calls.single()
        val request = call.request()
        assertEquals("https://cdn.example.com/assets/icon.png?v=2", request.url.toString())
        assertEquals("GET", request.method)
        assertEquals("image/*", request.header("Accept"))
        assertEquals("Mozilla/5.0 (Linux; Android)", request.header("User-Agent"))
        assertNull(request.header("Cookie"))
        assertNull(request.header("Authorization"))
        assertEquals(4_000L, TimeUnit.NANOSECONDS.toMillis(call.timeout().timeoutNanos()))
        assertTrue(body.closed)
    }

    @Test
    fun followsFiveRelativeRedirectsAndClosesEveryBody() {
        val bodies = mutableListOf<TrackingResponseBody>()
        val factory = FakeCallFactory { request, _ ->
            val step = request.url.encodedPath.removePrefix("/step-").toInt()
            val body = TrackingResponseBody(byteArrayOf(step.toByte()), "image/png")
            bodies += body
            if (step < 5) {
                response(request, 302, body, mapOf("Location" to "step-${step + 1}"))
            } else {
                response(request, 200, body)
            }
        }

        assertArrayEquals(
            byteArrayOf(5),
            FaviconHttpFetcher(callFactory = factory).fetch("https://example.com/step-0"),
        )
        assertEquals(6, factory.calls.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun sharesOneOverallDeadlineAcrossRedirectCalls() {
        var nowMillis = 0L
        val bodies = mutableListOf<TrackingResponseBody>()
        val factory = FakeCallFactory { request, _ ->
            val body = TrackingResponseBody(byteArrayOf(7), "image/png")
            bodies += body
            if (request.url.encodedPath == "/first") {
                nowMillis = 3_000L
                response(request, 302, body, mapOf("Location" to "/second"))
            } else {
                response(request, 200, body)
            }
        }

        assertArrayEquals(
            byteArrayOf(7),
            FaviconHttpFetcher(
                callFactory = factory,
                monotonicClockMillis = { nowMillis },
            ).fetch("https://example.com/first"),
        )
        assertEquals(
            listOf(4_000L, 1_000L),
            factory.calls.map { TimeUnit.NANOSECONDS.toMillis(it.timeout().timeoutNanos()) },
        )
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun rejectsUnsafeMissingAndExcessRedirectsAndClosesBodies() {
        listOf(null, "file:///tmp/icon.png").forEach { location ->
            val body = TrackingResponseBody(byteArrayOf(1), "image/png")
            val factory = FakeCallFactory { request, _ ->
                response(request, 302, body, location?.let { mapOf("Location" to it) }.orEmpty())
            }

            assertNull(FaviconHttpFetcher(callFactory = factory).fetch(ICON_URL))
            assertTrue(body.closed)
        }

        val bodies = mutableListOf<TrackingResponseBody>()
        val loopingFactory = FakeCallFactory { request, _ ->
            val body = TrackingResponseBody(byteArrayOf(), "image/png")
            bodies += body
            response(request, 302, body, mapOf("Location" to "/again"))
        }
        assertNull(
            FaviconHttpFetcher(maxRedirects = 2, callFactory = loopingFactory)
                .fetch(ICON_URL),
        )
        assertEquals(3, loopingFactory.calls.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun rejectsHttpsToHttpRedirects() {
        val body = TrackingResponseBody(byteArrayOf(1), "image/png")
        val factory = FakeCallFactory { request, _ ->
            response(request, 302, body, mapOf("Location" to "http://example.com/icon.png"))
        }

        assertNull(FaviconHttpFetcher(callFactory = factory).fetch(ICON_URL))
        assertEquals(1, factory.calls.size)
        assertTrue(body.closed)
    }

    @Test
    fun publicIconRejectsRedirectToPrivateAddress() {
        val body = TrackingResponseBody(byteArrayOf(1), "image/png")
        val factory = FakeCallFactory { request, _ ->
            response(request, 302, body, mapOf("Location" to "https://[::1]/icon.png"))
        }

        assertNull(FaviconHttpFetcher(callFactory = factory).fetch(ICON_URL))
        assertEquals(1, factory.calls.size)
    }

    @Test
    fun rejectsInvalidUrlsHttpErrorsAndDeclaredOversizeBodies() {
        val neverCalled = FakeCallFactory { _, _ -> error("must not execute") }
        assertNull(FaviconHttpFetcher(callFactory = neverCalled).fetch("not a url"))
        assertNull(FaviconHttpFetcher(callFactory = neverCalled).fetch("file:///tmp/icon.png"))
        assertTrue(neverCalled.calls.isEmpty())

        val notFoundBody = TrackingResponseBody(byteArrayOf(1), "image/png")
        val notFound = FakeCallFactory { request, _ -> response(request, 404, notFoundBody) }
        assertNull(FaviconHttpFetcher(callFactory = notFound).fetch(ICON_URL))
        assertTrue(notFoundBody.closed)

        val largeBody = TrackingResponseBody(ByteArray(9), "image/png", declaredLength = 9)
        val large = FakeCallFactory { request, _ -> response(request, 200, largeBody) }
        assertNull(FaviconHttpFetcher(maxBytes = 8, callFactory = large).fetch(ICON_URL))
        assertEquals(0L, largeBody.bytesRead)
        assertTrue(largeBody.closed)
    }

    @Test
    fun rejectsAnUnknownLengthBodyThatStreamsPastTheConfiguredByteLimit() {
        val bytes = ByteArray(12 * 1_024) { it.toByte() }
        val body = TrackingResponseBody(
            bytes,
            "image/png",
            declaredLength = -1,
            maxReadChunkBytes = 1L,
        )
        val factory = FakeCallFactory { request, _ -> response(request, 200, body) }

        val fetched = FaviconHttpFetcher(maxBytes = 8 * 1_024, callFactory = factory).fetch(ICON_URL)

        assertNull(fetched)
        assertEquals(8L * 1_024 + 1L, body.bytesRead)
        assertTrue(body.closed)
    }

    @Test
    fun transportFailuresReturnNull() {
        val factory = FakeCallFactory { _, _ -> throw IOException("timeout") }

        assertNull(FaviconHttpFetcher(callFactory = factory).fetch(ICON_URL))
    }

    @Test
    fun shortOverallDeadlineStopsARealLoopbackServerThatNeverResponds() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val serverExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "favicon-fetch-loopback").apply { isDaemon = true }
        }
        serverExecutor.execute {
            try {
                server.accept().use {
                    accepted.countDown()
                    releaseServer.await(2, TimeUnit.SECONDS)
                }
            } catch (_: Exception) {
                // Expected if cleanup closes the server before accept completes.
            }
        }

        try {
            val startedAtNanos = System.nanoTime()
            val result = FaviconHttpFetcher(overallTimeoutMillis = 100L)
                .fetch("http://127.0.0.1:${server.localPort}/stall.png")
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

            assertTrue(accepted.await(500, TimeUnit.MILLISECONDS))
            assertNull(result)
            assertTrue("call returned too early after ${elapsedMillis}ms", elapsedMillis >= 50L)
            assertTrue("call exceeded deadline: ${elapsedMillis}ms", elapsedMillis < 1_000L)
        } finally {
            releaseServer.countDown()
            runCatching { server.close() }
            serverExecutor.shutdownNow()
        }
    }

    private class FakeCallFactory(
        private val execute: (Request, FakeCall) -> Response,
    ) : Call.Factory {
        val calls = mutableListOf<FakeCall>()

        override fun newCall(request: Request): Call = FakeCall(request, execute).also(calls::add)
    }

    private class FakeCall(
        private val originalRequest: Request,
        private val executeBlock: (Request, FakeCall) -> Response,
    ) : Call {
        private val callTimeout = Timeout()
        private var executed = false
        private var canceled = false

        override fun request(): Request = originalRequest

        override fun execute(): Response {
            executed = true
            return executeBlock(originalRequest, this)
        }

        override fun enqueue(responseCallback: Callback) =
            throw UnsupportedOperationException("Synchronous test call")

        override fun cancel() {
            canceled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = canceled

        override fun timeout(): Timeout = callTimeout

        override fun addEventListener(eventListener: EventListener) = Unit

        override fun <T : Any> tag(type: KClass<T>): T? = null

        override fun <T> tag(type: Class<out T>): T? = null

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun clone(): Call = FakeCall(originalRequest, executeBlock)
    }

    private class TrackingResponseBody(
        bytes: ByteArray,
        contentType: String?,
        private val declaredLength: Long = bytes.size.toLong(),
        private val maxReadChunkBytes: Long = Long.MAX_VALUE,
    ) : ResponseBody() {
        private val mediaType = contentType?.toMediaTypeOrNull()
        private val content = Buffer().write(bytes)
        private val initialSize = content.size
        private val trackingSource = object : ForwardingSource(content) {
            override fun read(sink: Buffer, byteCount: Long): Long =
                super.read(sink, minOf(byteCount, maxReadChunkBytes))

            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        var closed = false

        val bytesRead: Long
            get() = initialSize - content.size

        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = declaredLength

        override fun source(): BufferedSource = trackingSource
    }

    private companion object {
        const val ICON_URL = "https://example.com/icon.png"

        fun response(
            request: Request,
            code: Int,
            body: ResponseBody,
            headers: Map<String, String> = emptyMap(),
        ): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
    }
}
