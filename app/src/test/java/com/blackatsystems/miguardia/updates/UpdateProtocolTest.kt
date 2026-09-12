package com.blackatsystems.miguardia.updates

import com.blackatsystems.miguardia.beta.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateProtocolTest {
    @Test fun nullReleaseIsDistinctFromNoNewerAndVersionNamesDoNotOrderUpdates(): Unit = runBlocking {
        val transport = FixtureUpdateTransport().apply { candidate = null }
        assertNull(UpdateClient(transport) { updateNow }.release())
        assertEquals(UpdateAvailability.AVAILABLE, updateRelease().copy(versionName = "1.0.0").availability(updateInstalled))
        for (code in listOf(1L, 2L)) assertEquals(UpdateAvailability.NO_NEWER_VERSION, updateRelease().copy(versionCode = code, versionName = "99.0.0").availability(updateInstalled))
        assertEquals(UpdateAvailability.AVAILABLE, updateRelease().copy(versionName = "2.0.0").availability(updateInstalled))
    }
    @Test fun identityCompatibilityAndSizeAreCheckedBeforeDownload() {
        val release = updateRelease()
        for (installed in listOf(updateInstalled.copy(identity = updateInstalled.identity.copy(packageName = "wrong.package")),
            updateInstalled.copy(identity = updateInstalled.identity.copy(certificateSha256 = "a".repeat(64))), updateInstalled.copy(deviceApi = 25)))
            assertEquals(UpdateAvailability.INCOMPATIBLE, release.availability(installed))
        assertThrows(Exception::class.java) { release.copy(byteSize = MAX_APK_BYTES + 1) }
        assertThrows(Exception::class.java) { release.copy(packageName = "wrong.package") }
        assertThrows(Exception::class.java) { release.copy(certificateSha256 = "a".repeat(64)) }

    }
    @Test fun strictReleaseFieldsRejectInvalidTypesNumbersHashesIdsDatesAndVersions() {
        val original = updateRelease().fields()
        val bad = listOf("id" to "a", "version_code" to 0L, "version_code" to 9007199254740992L,
            "byte_size" to -1L, "byte_size" to 536870913L, "min_android" to 101L, "min_android" to 25L,
            "version_name" to "../../apk", "version_name" to "3".repeat(81), "apk_sha256" to "B".repeat(64),
            "certificate_sha256" to "z".repeat(64), "published_at" to "2026-02-30T12:00:00.000Z",
            "published_at" to "2026-09-06T12:00:00Z", "version_code" to "3")
        bad.forEach { (key, value) -> assertThrows(Exception::class.java) { UpdateRelease.parse(original + (key to value)) } }
        assertThrows(Exception::class.java) { UpdateRelease.parse(original + ("extra" to true)) }
        assertThrows(Exception::class.java) { UpdateRelease.parse(original - "id") }
    }
    @Test fun selectedPathIsDerivedAndUnknownPathsAreRejected() {
        val release = updateRelease()
        assertEquals("$PUBLIC_UPDATE_BASE/download/${release.id}/${release.apkSha256}", release.downloadPath)
        assertThrows(Exception::class.java) { UpdateRelease.parse(release.fields() + ("download_path" to "/latest")) }
        assertThrows(Exception::class.java) { UpdateRelease.parse(release.fields() + ("artifact_kind" to "SPLIT_APK")) }
    }
    @Test fun malformedSecretsAndJsonAreRejected() {
        listOf("{\"id\":1,\"id\":2}", "{\"id\":1.5}", "{\"id\":3e2}").forEach { assertThrows(Exception::class.java) { UpdateJson.parse(it) } }
    }
    @Test fun serviceErrorsNeverMeanCurrentAndRetryAfterIsPreserved(): Unit = runBlocking {
        for ((status, code) in listOf(403 to "ORIGIN_DENIED", 409 to "RELEASE_CHANGED", 429 to "RATE_LIMITED", 503 to "CONFIGURATION_UNAVAILABLE", 404 to "NO_PUBLIC_RELEASE")) {
            val transport = object : UpdateTransport {
                override suspend fun <T> exchange(request: UpdateRequest, read: suspend (UpdateResponse) -> T): T =
                    read(jsonResponse(linkedMapOf("contract_version" to 1, "error" to linkedMapOf("code" to code)), status, "60"))
            }
            val error = assertThrows(UpdateServiceFailure::class.java) { runBlocking { UpdateClient(transport) { updateNow }.release() } }
            assertEquals(code, error.code)
            if (status in setOf(429, 503)) assertEquals(updateNow.plusSeconds(60), error.retryAt)
        }
    }
}
