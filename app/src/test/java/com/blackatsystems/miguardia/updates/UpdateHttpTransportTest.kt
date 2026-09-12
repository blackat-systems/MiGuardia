package com.blackatsystems.miguardia.updates

import java.io.*
import java.net.*
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdateHttpTransportTest {
    private class Connection(url: URL) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        @Volatile var disconnected = false
        var status = 200
        var encoding: String? = null
        var length: String? = null
        var input: InputStream = "{}".byteInputStream()
        var output: OutputStream = sent
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        override fun connect() = Unit
        override fun disconnect() { disconnected = true; stopped.countDown() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = output
        override fun getInputStream() = input
        override fun getErrorStream() = input
        override fun getResponseCode() = status
        override fun getContentType() = "application/json"
        override fun getHeaderField(name: String): String? = when (name) { "Content-Encoding" -> encoding; "Content-Length" -> length; else -> null }
        fun block(write: Boolean) {
            fun waitForDisconnect(): Nothing { started.countDown(); check(stopped.await(5, TimeUnit.SECONDS)); throw IOException("Synthetic disconnected socket") }
            if (write) output = object : OutputStream() { override fun write(value: Int) = waitForDisconnect() }
            else input = object : InputStream() { override fun read(): Int = waitForDisconnect() }
        }
    }
    private fun request(endpoint: UpdateEndpoint) = UpdateRequest(endpoint,
        release = if (endpoint == UpdateEndpoint.DOWNLOAD) updateRelease() else null)

    @Test fun allFourFixedEndpointsUseExpectedMethodsAndOnlyDownloadHasAuthorization(): Unit = runBlocking {
        for (endpoint in UpdateEndpoint.entries) {
            lateinit var connection: Connection
            UpdateHttpTransport({ Connection(it).also { c -> connection = c } }).exchange(request(endpoint)) { readUpdateBytes(it.body, 32768) }
            assertEquals("https://blackatsystems.com${request(endpoint).path}", connection.url.toString())
            assertEquals("GET", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects); assertFalse(connection.useCaches)
            assertNull(connection.getRequestProperty("Cookie")); assertNull(connection.getRequestProperty("Range"))
            assertEquals("identity", connection.getRequestProperty("Accept-Encoding")); assertEquals("no-store", connection.getRequestProperty("Cache-Control"))
            assertNull(connection.getRequestProperty("Authorization"))
            assertEquals("", connection.sent.toString("UTF-8"))
            assertEquals(10_000, connection.connectTimeout); assertEquals(10_000, connection.readTimeout); assertTrue(connection.disconnected)
        }
    }
    @Test fun oversizeRequestsResponsesRedirectsCompressionAndCookieSessionsFailClosed(): Unit = runBlocking {
        assertThrows(Exception::class.java) { UpdateRequest(UpdateEndpoint.DOWNLOAD) }
        for (kind in listOf("redirect", "large", "length")) {
            val connection = Connection(URL("https://test.invalid")).apply {
                when (kind) { "redirect" -> status = 302; "compressed" -> encoding = "gzip"; "large" -> input = ByteArray(32769).inputStream(); "length" -> length = "-1" }
            }
            assertThrows(Exception::class.java) { runBlocking { UpdateHttpTransport({ connection }).exchange(request(UpdateEndpoint.RELEASE)) { readUpdateBytes(it.body, 32768) } } }
            assertTrue(connection.disconnected)
        }
        val old = CookieHandler.getDefault()
        try {
            CookieHandler.setDefault(CookieManager())
            assertThrows(Exception::class.java) { runBlocking { UpdateHttpTransport({ error("No ambient cookies") }).exchange(request(UpdateEndpoint.RELEASE)) {} } }
        } finally { CookieHandler.setDefault(old) }
        Unit
    }
    @Test fun blockedReadsAndWritesAreDisconnectedOnCancellationAndTotalDeadline(): Unit = runBlocking {
        for (write in listOf(false)) for (cancel in listOf(false, true)) {
            val connection = Connection(URL("https://test.invalid")).apply { block(write) }
            val transport = UpdateHttpTransport({ connection }, jsonTimeoutMillis = if (cancel) 10_000 else 200)
            val job = async(Dispatchers.Default) {
                runCatching { transport.exchange(request(UpdateEndpoint.RELEASE)) { readUpdateBytes(it.body, 32768) } }
            }
            withContext(Dispatchers.IO) { assertTrue(connection.started.await(3, TimeUnit.SECONDS)) }
            if (cancel) { job.cancelAndJoin() } else { assertTrue(withTimeout(3000) { job.await() }.exceptionOrNull() is IOException) }
            assertTrue(connection.disconnected)
        }
    }

    @Test fun retryIsRecordedBeforeMalformedOrBlockedBodyAndSurvivesCancellation(): Unit = runBlocking {
        for (cancel in listOf(false, true)) {
            val time = UpdateTestClock()
            val connection = Connection(URL("https://test.invalid")).apply { status = 429; length = "invalid"; block(false) }
            val folder = java.nio.file.Files.createTempDirectory("public-retry").toFile()
            try {
                val service = UpdateService(UpdateHttpTransport({connection}, jsonTimeoutMillis = if (cancel) 10000 else 200),
                    FixtureUpdateInspector(), UpdateFiles(folder), time) {time.nanos}
                val task = async(Dispatchers.Default) { runCatching { service.release() } }
                withContext(Dispatchers.IO) { assertTrue(connection.started.await(3, TimeUnit.SECONDS)) }
                assertEquals(60000L,service.retryRemainingMillis())
                if (cancel) task.cancelAndJoin() else task.await()
                assertEquals(60000L,service.retryRemainingMillis())
                time.advance(60); assertEquals(0L,service.retryRemainingMillis())
            } finally { folder.deleteRecursively() }
        }
    }

    @Test fun existingBudgetLimitsTheSocketDeadlineAndIsRecheckedAfterIoDispatch(): Unit = runBlocking {
        val time = UpdateTestClock()
        val budget = UpdateTimeBudget.start { time.nanos }
        time.nanos = 299_950_000_000L
        val blocked = Connection(URL("https://test.invalid")).apply { block(false) }
        val error = runCatching { UpdateHttpTransport({ blocked }).exchange(
            UpdateRequest(UpdateEndpoint.RELEASE, budget = budget)) { readUpdateBytes(it.body, 32768) } }.exceptionOrNull()
        assertTrue(error is IOException); assertTrue(blocked.disconnected)

        time.nanos = 0
        val queued = UpdateTimeBudget.start { time.nanos }
        val connection = Connection(URL("https://test.invalid"))
        assertThrows(Exception::class.java) { runBlocking {
            UpdateHttpTransport({ time.advance(300); connection }).exchange(UpdateRequest(UpdateEndpoint.RELEASE, budget = queued)) { fail("Expired before headers") }
        } }
        assertTrue(connection.disconnected); assertTrue(connection.sent.size() == 0)
    }

    @Test fun realTransportDoesNotRequireDateHeaderForPublicationWithCivilSkew(): Unit = runBlocking {
        val connection = Connection(URL("https://test.invalid")).apply { input = jsonResponse(releaseResponse(updateRelease())).body }
        assertNotNull(UpdateClient(UpdateHttpTransport({ connection })) { updateNow.minusSeconds(30) }.release())
        assertNull(connection.getHeaderField("Date")); assertTrue(connection.disconnected)
    }
}
