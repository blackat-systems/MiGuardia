package com.blackatsystems.miguardia.updates

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal const val APK_MIME = "application/vnd.android.package-archive"
internal const val MAX_APK_BYTES = 128L * 1024 * 1024
internal const val PUBLIC_UPDATE_BASE = "/api/miguardia/public/v1"
internal const val PUBLIC_PACKAGE = "com.blackatsystems.miguardia"
internal const val PUBLIC_CERTIFICATE = "ab6a80e649d10a63d4524278aaae62588c29b5ff459a91063dec62b534c69b0d"
internal data class InstalledAppIdentity(val packageName: String, val certificateSha256: String, val versionCode: Long)
internal data class InstalledUpdateApp(val identity: InstalledAppIdentity, val versionName: String, val deviceApi: Int)

internal data class UpdateRelease(
    val id: String, val versionName: String, val versionCode: Long, val packageName: String,
    val minAndroid: Int, val byteSize: Long, val apkSha256: String, val certificateSha256: String,
    val publishedAt: Instant, val serverIncompatible: Boolean = false,
) {
    init {
        require(Regex("[A-Za-z0-9_-]{8,80}").matches(id))
        require(versionName.length in 1..80 && Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?").matches(versionName))
        require(versionCode in 1..2_100_000_000L && minAndroid in 26..100)
        require(byteSize in 1..MAX_APK_BYTES)
        require(packageName == PUBLIC_PACKAGE && certificateSha256 == PUBLIC_CERTIFICATE)
        require(Regex("[a-f0-9]{64}").matches(apkSha256))
    }
    fun availability(installed: InstalledUpdateApp): UpdateAvailability = when {
        serverIncompatible || packageName != installed.identity.packageName || certificateSha256 != installed.identity.certificateSha256 -> UpdateAvailability.INCOMPATIBLE
        minAndroid > installed.deviceApi -> UpdateAvailability.INCOMPATIBLE
        versionCode <= installed.identity.versionCode -> UpdateAvailability.NO_NEWER_VERSION
        else -> UpdateAvailability.AVAILABLE
    }
    val downloadPath: String get() = "$PUBLIC_UPDATE_BASE/download/$id/$apkSha256"
    val fileName: String get() = "MiGuardia-$versionCode.apk"
    companion object {
        fun parse(map: Map<String, Any?>): UpdateRelease {
            map.exact("id", "version_name", "version_code", "application_id", "min_android", "byte_size", "apk_sha256", "certificate_sha256", "published_at", "artifact_kind", "download_path")
            require(map.string("artifact_kind") == "UNIVERSAL_APK")
            val api = map.long("min_android")
            require(api in 26..100)
            val text = map.string("published_at")
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z").matches(text))
            val date = java.time.LocalDateTime.parse(text, DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withResolverStyle(ResolverStyle.STRICT)).toInstant(java.time.ZoneOffset.UTC)
            return UpdateRelease(map.string("id"), map.string("version_name"), map.long("version_code"),
                map.string("application_id"), api.toInt(), map.long("byte_size"), map.string("apk_sha256"),
                map.string("certificate_sha256"), date).also { require(map.string("download_path") == it.downloadPath) }
        }
    }
}
internal enum class UpdateAvailability { AVAILABLE, NO_NEWER_VERSION, INCOMPATIBLE }
internal class InvalidUpdateResponse : Exception("Invalid public update response")
internal enum class UpdateFailureReaction { RETRY_AFTER, TEMPORARY_ERROR, RECHECK_AND_RESELECT, STOP_BETA_RETRIES, TRANSPORT_ERROR }
internal class UpdateServiceFailure(val status: Int, val code: String, val retryAt: Instant?, val retryMillis: Long = 0) : Exception("Update service unavailable") {
    val reaction: UpdateFailureReaction get() = when {
        status == 429 -> UpdateFailureReaction.RETRY_AFTER
        status == 503 -> UpdateFailureReaction.TEMPORARY_ERROR
        status == 409 && code == "RELEASE_CHANGED" -> UpdateFailureReaction.RECHECK_AND_RESELECT
        status == 410 && code == "BETA_RETIRED" -> UpdateFailureReaction.STOP_BETA_RETRIES
        else -> UpdateFailureReaction.TRANSPORT_ERROR
    }
}
internal fun Map<String, Any?>.long(key: String): Long = get(key) as? Long ?: error("Invalid integer")

internal fun updateRetryMillis(header: String?, now: Instant): Long {
    if (header == null || header.length > 128) return 60_000
    if (Regex("[0-9]+").matches(header)) return header.toLongOrNull()?.coerceAtMost(86400)?.times(1000) ?: 86_400_000
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", Locale.US).withResolverStyle(ResolverStyle.STRICT)
        val date = java.time.LocalDateTime.parse(header, formatter)
        require(formatter.format(date) == header)
        java.time.Duration.between(now, date.toInstant(java.time.ZoneOffset.UTC)).toMillis().coerceIn(0, 86_400_000)
    }.getOrDefault(60_000)
}

internal class UpdateClient(private val transport: UpdateTransport,
    private val nanoTime: () -> Long = System::nanoTime, private val now: () -> Instant) {
    var retryObserved: (UpdateServiceFailure) -> Unit = {}
    suspend fun release(): UpdateRelease? {
        val budget = UpdateTimeBudget.start(nanoTime)
        return transport.exchange(UpdateRequest(UpdateEndpoint.RELEASE, budget = budget)) { response ->
            if (response.status != 200) fail(response)
            try {
                val map = readJson(response)
                map.exact("contract_version", "availability", "current_release")
                require(map.long("contract_version") == 1L)
                val availability = map.string("availability")
                val result = when (availability) {
                    "UNAVAILABLE" -> { require(map["current_release"] == null); null }
                    "AVAILABLE", "INCOMPATIBLE" -> UpdateRelease.parse(map.obj("current_release")).copy(serverIncompatible = availability == "INCOMPATIBLE")
                    else -> error("Invalid availability")
                }
                budget.requireRemaining()
                result
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) { throw InvalidUpdateResponse() }
        }
    }
    suspend fun readJson(response: UpdateResponse): Map<String, Any?> {
        require(response.contentEncoding == null || response.contentEncoding.equals("identity", true))
        require(response.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true)
        require(response.contentLength == null || response.contentLength in 0..32768)
        val bytes = readUpdateBytes(response.body, 32768)
        response.contentLength?.let { require(bytes.size.toLong() == it) }
        return UpdateJson.parse(UpdateJson.utf8(bytes))
    }
    suspend fun fail(response: UpdateResponse): Nothing {
        // Preserve HTTP backoff even if an intermediary supplies HTML, broken UTF-8 or wrong headers.
        val receivedNanos = nanoTime()
        val receivedAt = now()
        val wait = if (response.status in setOf(429, 503)) updateRetryMillis(response.retryAfter, receivedAt) else 0L
        if (response.status in setOf(429, 503)) retryObserved(UpdateServiceFailure(response.status, "HTTP_BACKOFF", receivedAt.plusMillis(wait), wait))
        val code = try {
            val map = readJson(response)
            map.exact("contract_version", "error"); require(map.long("contract_version") == 1L)
            val error = map.obj("error"); error.exact("code")
            error.string("code").also { require(it in ERROR_CODES[response.status].orEmpty()) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { "HTTP_ERROR" }
        val remaining = (wait - (nanoTime() - receivedNanos) / 1_000_000).coerceAtLeast(0)
        throw UpdateServiceFailure(response.status, code, if (response.status in setOf(429, 503)) receivedAt.plusMillis(wait) else null, remaining)
    }
    companion object {
        private val ERROR_CODES = mapOf(400 to setOf("BAD_REQUEST"), 403 to setOf("ORIGIN_DENIED"),
            404 to setOf("NOT_FOUND", "NO_PUBLIC_RELEASE"), 405 to setOf("METHOD_NOT_ALLOWED"),
            408 to setOf("REQUEST_CANCELLED"), 409 to setOf("RELEASE_CHANGED"), 410 to setOf("BETA_RETIRED"),
            413 to setOf("PAYLOAD_TOO_LARGE"), 414 to setOf("URI_TOO_LONG"), 416 to setOf("RANGE_NOT_SUPPORTED"),
            429 to setOf("RATE_LIMITED", "STORAGE_BUSY", "DOWNLOAD_BUSY"), 431 to setOf("HEADERS_TOO_LARGE"),
            503 to setOf("CONFIGURATION_UNAVAILABLE", "STORAGE_UNAVAILABLE", "ARTIFACT_INVALID", "TEMPORARY_UNAVAILABLE"))
    }
}
