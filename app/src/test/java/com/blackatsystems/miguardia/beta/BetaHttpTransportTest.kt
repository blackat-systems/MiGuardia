package com.blackatsystems.miguardia.beta

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.CookieHandler
import java.net.CookieManager
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BetaHttpTransportTest {
    private class Connection(url: URL, val status: Int = 200, val response: ByteArray = "{}".toByteArray(), val mime: String = "application/json") : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun getErrorStream() = ByteArrayInputStream(response)
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getContentLengthLong() = -1L
    }
    @Test fun onlyTwoFixedHttpsPostsHaveBoundedBodyAndDisableRedirectsAndCookies() = runBlocking {
        for (path in listOf("/activations", "/feedback")) {
            lateinit var connection: Connection
            val transport = BetaHttpTransport { url -> Connection(url).also { connection = it } }
            transport.post(path, "{}".toByteArray())
            assertEquals("https://blackatsystems.com/api/miguardia/beta/v1$path", connection.url.toString())
            assertEquals("POST", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects); assertFalse(connection.useCaches)
            assertEquals("", connection.getRequestProperty("Cookie")); assertEquals(10_000, connection.connectTimeout)
            assertEquals(10_000, connection.readTimeout); assertTrue(connection.disconnected)
            assertEquals("{}", connection.sent.toString("UTF-8"))
        }
        for ((path, bytes) in listOf("/release" to ByteArray(0), "https://foreign/activations" to ByteArray(0),
            "/activations" to ByteArray(8193), "/feedback" to ByteArray(24577))) {
            try { BetaHttpTransport { error("Must not open a connection") }.post(path, bytes); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun redirectsWrongMimeOversizeAndAmbientCookieHandlerFailClosed() = runBlocking {
        for (connection in listOf(Connection(URL("https://test.invalid"), 302), Connection(URL("https://test.invalid"), mime = "text/html"),
            Connection(URL("https://test.invalid"), response = ByteArray(32769)))) {
            try { BetaHttpTransport { connection }.post("/activations", "{}".toByteArray()); fail() } catch (_: IllegalArgumentException) { }
            assertTrue(connection.disconnected)
        }
        val old = CookieHandler.getDefault()
        try {
            CookieHandler.setDefault(CookieManager())
            try { BetaHttpTransport { error("No ambient session") }.post("/activations", ByteArray(0)); fail() } catch (_: IllegalStateException) { }
        } finally { CookieHandler.setDefault(old) }
    }
}
