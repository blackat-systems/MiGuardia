package com.blackatsystems.miguardia.beta

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.CookieHandler
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.coroutineContext

internal data class BetaResponse(val status: Int, val body: ByteArray, val retryAfter: String? = null) {
    override fun toString() = "BetaResponse(status=$status)"
}
internal fun interface BetaTransport { suspend fun post(path: String, body: ByteArray): BetaResponse }
internal class BetaHttpTransport(private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : BetaTransport {
    override suspend fun post(path: String, body: ByteArray): BetaResponse = try { withTimeout(20_000) {
        require(path == "/activations" || path == "/feedback")
        require(body.size <= if (path == "/activations") 8192 else 24576)
        withContext(Dispatchers.IO) {
            // No ambient web session may attach or persist cookies for this protocol.
            check(CookieHandler.getDefault() == null)
            val connection = open(URL("https://blackatsystems.com/api/miguardia/beta/v1$path"))
            val deadline = System.nanoTime() + 20_000_000_000L
            try {
                connection.apply {
                    requestMethod = "POST"
                    instanceFollowRedirects = false
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    useCaches = false
                    doOutput = true
                    setFixedLengthStreamingMode(body.size)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-store")
                    setRequestProperty("Cookie", "")
                }
                coroutineContext.ensureActive()
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                require(status !in 300..399)
                require(connection.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true)
                require(connection.contentLengthLong <= 32768)
                val output = ByteArrayOutputStream()
                (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { stream ->
                    val buffer = ByteArray(2048)
                    while (true) {
                        coroutineContext.ensureActive()
                        if (System.nanoTime() >= deadline) throw IOException("Beta timeout")
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 32768)
                        output.write(buffer, 0, count)
                    }
                }
                BetaResponse(status, output.toByteArray(), connection.getHeaderField("Retry-After"))
            } finally { connection.disconnect() }
        }
    } } catch (timeout: TimeoutCancellationException) {
        coroutineContext.ensureActive()
        throw IOException("Beta timeout", timeout)
    }
}
internal class BetaServiceFailure(val status: Int, val code: String, val retryAt: Instant? = null) :
    Exception("Beta request failed") {
    val retryable: Boolean get() = status == 429 || status >= 500
}
internal class BetaClient(private val transport: BetaTransport, private val now: () -> Instant) {
    suspend fun activate(code: String, spki: ByteArray): BetaEnvelope = request("/activations", linkedMapOf(
        "access_code" to normalizeBetaCode(code), "installation_public_key_spki" to BetaCrypto.encode(spki),
    ), "activation_receipt")

    suspend fun feedback(activation: BetaEnvelope, pending: BetaPending): BetaEnvelope = request("/feedback", linkedMapOf<String, Any?>(
        "activation_receipt" to activation.fields(), "request_id" to pending.requestId,
    ).apply { putAll(pending.opinion.fields()); put("installation_signature", pending.signature) }, "feedback_receipt")

    private suspend fun request(path: String, fields: Map<String, Any?>, receipt: String): BetaEnvelope {
        val bytes = BetaJson.encode(fields).toByteArray(Charsets.UTF_8)
        require(bytes.size <= if (path == "/activations") 8192 else 24576)
        val response = transport.post(path, bytes)
        require(response.body.size <= 32768)
        val map = BetaJson.parse(BetaJson.utf8(response.body))
        BetaReceiptVerifier.identifier(map.string("request_id"))
        if (response.status != 200) {
            map.exact("error", "request_id")
            val error = map.obj("error"); error.exact("code", "message")
            error.string("message")
            val code = error.string("code")
            require(Regex("[A-Z_]{1,80}").matches(code))
            val retryAt = if (response.status == 429 || response.status == 503) retryAfter(response.retryAfter, now()) else null
            throw BetaServiceFailure(response.status, code, retryAt)
        }
        map.exact(receipt, "idempotent", "request_id")
        require(map["idempotent"] is Boolean)
        return BetaEnvelope.parse(map.obj(receipt))
    }
    companion object {
        fun retryAfter(value: String?, now: Instant): Instant? = runCatching {
            value ?: return null
            if (Regex("[0-9]{1,10}").matches(value)) now.plusSeconds(value.toLong())
            else ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().coerceAtLeast(now)
        }.getOrNull()
    }
}
