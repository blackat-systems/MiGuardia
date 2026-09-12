package com.blackatsystems.miguardia.updates

import com.blackatsystems.miguardia.beta.*
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset

internal class UpdateTestClock(initial: Instant = updateNow) : Clock() {
    @Volatile var civil = initial
    @Volatile var nanos = 0L
    fun advance(seconds: Long) { nanos += seconds * 1_000_000_000L }
    override fun instant() = civil
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
internal val updateNow: Instant = Instant.parse("2026-09-06T12:00:00.000Z")
internal val updateBytes = "Fictitious bounded APK bytes for unit tests".toByteArray()
internal val updateInstalled = InstalledUpdateApp(InstalledAppIdentity("com.blackatsystems.miguardia", PUBLIC_CERTIFICATE, 2), "2.0.0-beta.1", 26)
internal fun updateRelease(bytes: ByteArray = updateBytes) = UpdateRelease("release_test_02", "2.0.0-beta.2", 3,
    updateInstalled.identity.packageName, 26, bytes.size.toLong(), BetaCrypto.hash(bytes), PUBLIC_CERTIFICATE, updateNow)
internal fun UpdateRelease.fields(): Map<String, Any?> = linkedMapOf("id" to id, "version_name" to versionName,
    "version_code" to versionCode, "application_id" to packageName, "min_android" to minAndroid.toLong(), "byte_size" to byteSize,
    "apk_sha256" to apkSha256, "certificate_sha256" to certificateSha256, "published_at" to "2026-09-06T12:00:00.000Z", "artifact_kind" to "UNIVERSAL_APK", "download_path" to downloadPath)
internal fun releaseResponse(release: UpdateRelease?) = linkedMapOf<String, Any?>("contract_version" to 1,
    "availability" to if (release == null) "UNAVAILABLE" else "AVAILABLE", "current_release" to release?.fields())
internal fun jsonResponse(fields: Map<String, Any?>, status: Int = 200, retry: String? = null): UpdateResponse {
    val bytes = BetaJson.encode(fields).toByteArray()
    return UpdateResponse(status, ByteArrayInputStream(bytes), "application/json", bytes.size.toLong(), retry)
}
internal class FixtureUpdateInspector : UpdateApkInspector {
    var current = updateInstalled
    var apk = UpdateApkInfo(current.identity.packageName, "2.0.0-beta.2", 3, 26, listOf(PUBLIC_CERTIFICATE), false)
    override fun installed() = current
    override fun inspect(file: java.io.File) = apk
}
internal class FixtureUpdateTransport : UpdateTransport {
    val paths = mutableListOf<UpdateEndpoint>()
    val requests = mutableListOf<UpdateRequest>()
    var candidate: UpdateRelease? = updateRelease()
    var binary = updateBytes
    var mime = APK_MIME
    var status = 200
    var declaredSize: Long? = binary.size.toLong()
    var expired = false
    var otherRelease = false
    private var challengeNumber = 0
    var transform: (UpdateEndpoint, UpdateResponse) -> UpdateResponse = { _, response -> response }
    override suspend fun <T> exchange(request: UpdateRequest, read: suspend (UpdateResponse) -> T): T {
        paths += request.endpoint; requests += request
        val releaseId = if (otherRelease) "release_foreign" else "release_test_02"
        val expiry = if (expired) "2026-09-06T12:00:00.000Z" else "2026-09-06T12:05:00.000Z"
        val response = when (request.endpoint) {
            UpdateEndpoint.RELEASE -> jsonResponse(releaseResponse(candidate))
            UpdateEndpoint.DOWNLOAD -> UpdateResponse(status, ByteArrayInputStream(binary), mime, declaredSize)
        }
        val actual = transform(request.endpoint, response)
        return actual.body.use { read(actual) }
    }
}
