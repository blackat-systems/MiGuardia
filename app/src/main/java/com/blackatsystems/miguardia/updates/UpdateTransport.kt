package com.blackatsystems.miguardia.updates

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.CookieHandler
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

internal enum class UpdateEndpoint { RELEASE, DOWNLOAD }
internal class UpdateRequest(val endpoint: UpdateEndpoint, val release: UpdateRelease? = null,
    val budget: UpdateTimeBudget? = null) {
    init { require((endpoint == UpdateEndpoint.DOWNLOAD) == (release != null)) }
    val path get() = release?.downloadPath ?: "$PUBLIC_UPDATE_BASE/release"
    override fun toString() = "UpdateRequest(endpoint=$endpoint)"
}
internal class UpdateResponse(val status: Int, val body: InputStream, val contentType: String?, val contentLength: Long?,
    val retryAfter: String? = null, val contentEncoding: String? = null) {
    override fun toString() = "UpdateResponse(status=$status)"
}
internal interface UpdateTransport {
    suspend fun <T> exchange(request: UpdateRequest, read: suspend (UpdateResponse) -> T): T
}

/** Fixed origin and paths; no ambient cookies, redirects, cache, ranges or resumable credentials. */
internal class UpdateHttpTransport(
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val jsonTimeoutMillis: Long = 20_000,
    private val downloadTimeoutMillis: Long = 300_000,
) : UpdateTransport {
    override suspend fun <T> exchange(request: UpdateRequest, read: suspend (UpdateResponse) -> T): T {
        val configuredTimeout = if (request.endpoint == UpdateEndpoint.DOWNLOAD) downloadTimeoutMillis else jsonTimeoutMillis
        val timeout = request.budget?.remainingMillis()?.coerceAtMost(configuredTimeout) ?: configuredTimeout
        require(timeout > 0)
        return try { withTimeout(timeout) { withContext(Dispatchers.IO) { coroutineScope {
            check(CookieHandler.getDefault() == null)
            request.budget?.requireRemaining()
            val connection = open(URL("https://blackatsystems.com${request.path}"))
            // Also disconnect blocked socket writes/reads on cancellation and at the total deadline.
            val watchdog = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { delay(timeout) } finally { connection.disconnect() }
            }
            try {
                connection.apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-store")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Accept", if (request.endpoint == UpdateEndpoint.DOWNLOAD) APK_MIME else "application/json")

                }
                ensureActive()
                request.budget?.requireRemaining()
                val status = connection.responseCode
                if (status in 300..399) throw UpdateServiceFailure(status, "REDIRECT_REJECTED", null)
                val rawSize = connection.getHeaderField("Content-Length")
                val size = if (status in setOf(429, 503)) rawSize?.takeIf { Regex("[0-9]{1,16}").matches(it) }?.toLongOrNull()
                else rawSize?.let { require(Regex("[0-9]{1,16}").matches(it)); it.toLong() }
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream ?: java.io.ByteArrayInputStream(byteArrayOf())
                stream.use { read(UpdateResponse(status, it, connection.contentType, size, connection.getHeaderField("Retry-After"), connection.getHeaderField("Content-Encoding"))) }
                    .also { ensureActive(); request.budget?.requireRemaining() }
            } finally {
                connection.disconnect()
                watchdog.cancel()
            }
        } } } } catch (timeoutError: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            throw IOException("Update transfer timed out")
        }
    }
}

internal suspend fun readUpdateBytes(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
