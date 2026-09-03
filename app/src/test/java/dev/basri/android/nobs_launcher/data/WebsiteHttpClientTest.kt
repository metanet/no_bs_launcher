package dev.basri.android.nobs_launcher.data

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteHttpClientTest {
    @Test
    fun privateClientDisablesAmbientCredentialsRetriesCacheAndAutomaticRedirects() {
        val client = newWebsiteProbeOkHttpClient()

        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertEquals(4_000, client.connectTimeoutMillis)
        assertEquals(4_000, client.readTimeoutMillis)
        assertEquals(12_000, client.callTimeoutMillis)
    }

    @Test
    fun pageAndFaviconClientsShareConnectionResources() {
        val pageClient = newWebsiteProbeOkHttpClient()
        val faviconClient = newFaviconOkHttpClient()

        assertSame(pageClient.connectionPool, faviconClient.connectionPool)
        assertSame(pageClient.dispatcher, faviconClient.dispatcher)
    }

    @Test
    fun classifiesStatusesAndClosesEveryResponse() {
        listOf(200, 204, 299, 401, 403).forEach { status ->
            val body = TrackingResponseBody("ignored", "application/json")
            val factory = FakeCallFactory { request, _ -> response(request, status, body) }

            assertEquals(
                WebsiteProbeResult.Reachable(START_URL, null),
                WebsiteHttpClient(callFactory = factory).probe(START_URL),
            )
            assertTrue("status $status body was not closed", body.closed)
        }

        listOf(199, 404, 429, 500, 600).forEach { status ->
            val body = TrackingResponseBody("ignored", "text/plain")
            val factory = FakeCallFactory { request, _ -> response(request, status, body) }

            assertEquals(
                WebsiteProbeResult.Inaccessible,
                WebsiteHttpClient(callFactory = factory).probe(START_URL),
            )
            assertTrue("status $status body was not closed", body.closed)
        }
    }

    @Test
    fun followsFiveRelativeRedirectsAndClosesTheirBodies() {
        val bodies = mutableListOf<TrackingResponseBody>()
        val factory = FakeCallFactory { request, _ ->
            val index = request.url.encodedPath.removePrefix("/").toInt()
            TrackingResponseBody("redirect $index", "text/plain").let { body ->
                bodies += body
                if (index < 5) {
                    response(request, 302, body, mapOf("Location" to "${index + 1}"))
                } else {
                    response(request, 200, body)
                }
            }
        }

        assertEquals(
            WebsiteProbeResult.Reachable("https://example.com/5", null),
            WebsiteHttpClient(callFactory = factory).probe("https://example.com/0"),
        )
        assertEquals(6, factory.calls.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun rejectsMissingUnsafeAndExcessRedirectsAndClosesResponses() {
        listOf(null, "file:///tmp/page.html").forEach { location ->
            val body = TrackingResponseBody("redirect", "text/plain")
            val factory = FakeCallFactory { request, _ ->
                response(request, 302, body, location?.let { mapOf("Location" to it) }.orEmpty())
            }

            assertEquals(
                WebsiteProbeResult.Inaccessible,
                WebsiteHttpClient(callFactory = factory).probe(START_URL),
            )
            assertTrue(body.closed)
        }

        val bodies = mutableListOf<TrackingResponseBody>()
        val loopingFactory = FakeCallFactory { request, _ ->
            TrackingResponseBody("redirect", "text/plain").let { body ->
                bodies += body
                response(request, 302, body, mapOf("Location" to "/again"))
            }
        }
        assertEquals(
            WebsiteProbeResult.Inaccessible,
            WebsiteHttpClient(callFactory = loopingFactory).probe("https://example.com/again"),
        )
        assertEquals(6, loopingFactory.calls.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun rejectsHttpsToHttpRedirects() {
        val body = TrackingResponseBody("redirect", "text/plain")
        val factory = FakeCallFactory { request, _ ->
            response(request, 302, body, mapOf("Location" to "http://example.com/plain"))
        }

        assertEquals(
            WebsiteProbeResult.Inaccessible,
            WebsiteHttpClient(callFactory = factory).probe(START_URL),
        )
        assertEquals(1, factory.calls.size)
        assertTrue(body.closed)
    }

    @Test
    fun publicShortcutRejectsRedirectToPrivateAddress() {
        val body = TrackingResponseBody("redirect", "text/plain")
        val factory = FakeCallFactory { request, _ ->
            response(request, 302, body, mapOf("Location" to "https://127.0.0.1/private"))
        }

        assertEquals(
            WebsiteProbeResult.Inaccessible,
            WebsiteHttpClient(callFactory = factory).probe(START_URL),
        )
        assertEquals(1, factory.calls.size)
    }

    @Test
    fun rejectsMalformedAndNonHttpUrlsWithoutCreatingCalls() {
        val factory = FakeCallFactory { _, _ -> error("must not execute") }

        assertEquals(WebsiteProbeResult.Inaccessible, WebsiteHttpClient(factory).probe("not a url"))
        assertEquals(
            WebsiteProbeResult.Inaccessible,
            WebsiteHttpClient(factory).probe("file:///tmp/page.html"),
        )
        assertTrue(factory.calls.isEmpty())
    }

    @Test
    fun sendsSideEffectFreeHeadHeadersWithoutCookieOrAuthorization() {
        val factory = FakeCallFactory { request, _ ->
            response(request, 200, TrackingResponseBody("", "application/json"))
        }

        WebsiteHttpClient(callFactory = factory).probe(START_URL)

        val request = factory.calls.single().request()
        assertEquals("HEAD", request.method)
        assertEquals("text/html, application/xhtml+xml", request.header("Accept"))
        assertEquals("Mozilla/5.0 (Linux; Android)", request.header("User-Agent"))
        assertNull(request.header("Cookie"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun sharesTheOverallDeadlineAcrossRedirectCalls() {
        var nowMillis = 0L
        val bodies = mutableListOf<TrackingResponseBody>()
        val factory = FakeCallFactory { request, _ ->
            val body = TrackingResponseBody("", "application/json")
            bodies += body
            if (request.url.encodedPath == "/first") {
                nowMillis = 9_000L
                response(request, 302, body, mapOf("Location" to "/second"))
            } else {
                nowMillis = 12_000L
                response(request, 200, body)
            }
        }

        val result = WebsiteHttpClient(
            callFactory = factory,
            monotonicClockMillis = { nowMillis },
        ).probe("https://example.com/first")

        assertEquals(WebsiteProbeResult.Inaccessible, result)
        assertEquals(
            listOf(12_000L, 3_000L),
            factory.calls.map { TimeUnit.NANOSECONDS.toMillis(it.timeout().timeoutNanos()) },
        )
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun headProbeNeverConsumesHtmlResponseBodies() {
        listOf("text/html; charset=utf-8", "application/xhtml+xml").forEach { contentType ->
            val body = TrackingResponseBody("café ☕", contentType)
            val factory = FakeCallFactory { request, _ -> response(request, 200, body) }

            assertEquals(
                WebsiteProbeResult.Reachable(START_URL, null),
                WebsiteHttpClient(callFactory = factory).probe(START_URL),
            )
            assertTrue(body.closed)
        }
    }

    @Test
    fun headProbeDoesNotConsumeOversizedResponseBodies() {
        val prefix = ByteArray(MAX_HTML_BYTES) { ('a'.code + it % 26).toByte() }
        val oversized = prefix + "ignored suffix".toByteArray()

        listOf(Int.MAX_VALUE.toLong(), -1L).forEach { declaredLength ->
            val body = TrackingResponseBody(oversized, "text/html", declaredLength)
            val factory = FakeCallFactory { request, _ -> response(request, 200, body) }

            assertEquals(
                WebsiteProbeResult.Reachable(START_URL, null),
                WebsiteHttpClient(callFactory = factory).probe(START_URL),
            )
            assertEquals(0L, body.bytesRead)
            assertTrue(body.closed)
        }
    }

    @Test
    fun connectionFailuresAreInaccessibleWithoutAResponseToClose() {
        val factory = FakeCallFactory { _, _ -> throw SocketTimeoutException("timed out") }

        assertEquals(
            WebsiteProbeResult.Inaccessible,
            WebsiteHttpClient(callFactory = factory).probe(START_URL),
        )
    }

    @Test
    fun cancellationRegistrationCancelsTheActiveCall() {
        var cancelActiveCall: () -> Unit = {}
        val factory = FakeCallFactory { _, call ->
            cancelActiveCall()
            assertTrue(call.isCanceled())
            throw java.io.IOException("canceled")
        }

        val result = WebsiteHttpClient(callFactory = factory).probe(
            START_URL,
            CancellationRegistration { cancelAction -> cancelActiveCall = cancelAction },
        )

        assertEquals(WebsiteProbeResult.Inaccessible, result)
        assertTrue(factory.calls.single().isCanceled())
    }

    @Test
    fun shortCallDeadlineStopsARealLoopbackServerThatNeverResponds() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val serverExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "website-probe-loopback").apply { isDaemon = true }
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
            val result = WebsiteHttpClient(overallTimeoutMillis = 100L)
                .probe("http://127.0.0.1:${server.localPort}/stall")
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

            assertTrue(accepted.await(500, TimeUnit.MILLISECONDS))
            assertEquals(WebsiteProbeResult.Inaccessible, result)
            assertTrue("call returned too early after ${elapsedMillis}ms", elapsedMillis >= 50L)
            assertTrue("call exceeded deadline: ${elapsedMillis}ms", elapsedMillis < 1_000L)
        } finally {
            releaseServer.countDown()
            runCatching { server.close() }
            serverExecutor.shutdownNow()
        }
    }

    @Test
    fun rejectsDnsRebindingToPrivatePeerBeforeSendingHttp() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 1, loopback)
        val accepted = CountDownLatch(1)
        val serverExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "website-probe-rebinding").apply { isDaemon = true }
        }
        serverExecutor.execute {
            try {
                server.accept().use { accepted.countDown() }
            } catch (_: Exception) {
                // Cleanup may close the server if the connection is rejected earlier.
            }
        }
        val client = newWebsiteProbeOkHttpClient().newBuilder()
            .dns { listOf(loopback) }
            .build()

        try {
            assertEquals(
                WebsiteProbeResult.Inaccessible,
                WebsiteHttpClient(callFactory = client)
                    .probe("http://public.example:${server.localPort}/private"),
            )
            assertTrue(accepted.await(500, TimeUnit.MILLISECONDS))
        } finally {
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
    ) : ResponseBody() {
        constructor(
            content: String,
            contentType: String?,
        ) : this(content.toByteArray(Charsets.UTF_8), contentType)

        private val mediaType = contentType?.toMediaTypeOrNull()
        private val content = Buffer().write(bytes)
        private val initialSize = content.size
        private val trackingSource = object : ForwardingSource(content) {
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
        const val START_URL = "https://example.com/page"
        const val MAX_HTML_BYTES = 256 * 1024

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
