package com.blackatsystems.miguardia.updates

import com.blackatsystems.miguardia.beta.WebCryptoInterop
import java.io.File
import java.net.URL
import java.security.cert.Certificate
import java.time.Instant
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Reads the accepted corpus from its real read-only checkout. Never an APK inspector fixture. */
@Category(WebCryptoInterop::class)
class PublicDistributionInteropTest {
    private fun runCase(name: String): Unit = runBlocking {
        val case = cases().single { it[0] == name }[1] as Map<String, Any?>
        val actual = if (case.string("kind") == "download") {
            val selected = UpdateRelease.parse(case.obj("selection"))
            try {
                val bytes = Base64.getDecoder().decode(case.string("body_base64"))
                val result = copyUpdate(bytes.inputStream(), null, selected.byteSize, {case["cancelled"] != true})
                if (result.sha256 == selected.apkSha256) "VERIFIED_BYTES" else "DISCARD_PARTIAL"
            } catch (_: Exception) { if (case["cancelled"] == true) "CANCELLED_AND_DISCARD_PARTIAL" else "DISCARD_PARTIAL" }
        } else {
            val response = case.obj("response")
            val headers = response.obj("headers")
            val transport = UpdateHttpTransport({ url -> object : HttpsURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getCipherSuite() = "TLS_FIXTURE"
                override fun getLocalCertificates(): Array<Certificate>? = null
                override fun getServerCertificates(): Array<Certificate> = emptyArray()
                override fun getResponseCode() = response.long("status").toInt()
                override fun getContentType() = getHeaderField("Content-Type")
                override fun getHeaderField(name: String): String? = headers.entries.firstOrNull { it.key.equals(name,true) }?.value as? String
                override fun getInputStream() = Base64.getDecoder().decode(response.string("body_base64")).inputStream()
                override fun getErrorStream() = inputStream
            } })
            try {
                val selected = UpdateClient(transport, { 0L }) { Instant.ofEpochMilli(case.long("now_ms")) }.release()
                val installed = case.obj("installed")
                selected?.availability(InstalledUpdateApp(InstalledAppIdentity(installed.string("application_id"),
                    installed.string("certificate_sha256"), installed.long("version_code")), "fixture", installed.long("android_api").toInt()))?.name ?: "NO_RELEASE"
            } catch (failure: UpdateServiceFailure) {
                case["expected_retry_ms"]?.let { assertEquals(name, it as Long, failure.retryMillis) }
                failure.reaction.name
            } catch (_: Exception) { "INVALID_RESPONSE" }
        }
        assertEquals(name, case.string("expected"), actual)
    }
    @Test fun no_public_release() = runCase("no_public_release")
    @Test fun update_available() = runCase("update_available")
    @Test fun same_version() = runCase("same_version")
    @Test fun older_version() = runCase("older_version")
    @Test fun old_android() = runCase("old_android")
    @Test fun storage_failure() = runCase("storage_failure")
    @Test fun changed_selection() = runCase("changed_selection")
    @Test fun retired_beta() = runCase("retired_beta")
    @Test fun duplicate_keys() = runCase("duplicate_keys")
    @Test fun invalid_utf8() = runCase("invalid_utf8")
    @Test fun redirect() = runCase("redirect")
    @Test fun compressed() = runCase("compressed")
    @Test fun not_found() = runCase("not_found")
    @Test fun rate_limit() = runCase("rate_limit")
    @Test fun short_file() = runCase("short_file")
    @Test fun long_file() = runCase("long_file")
    @Test fun wrong_file_hash() = runCase("wrong_file_hash")
    @Test fun invalid_metadata_wrong_package() = runCase("invalid_metadata_wrong_package")
    @Test fun invalid_metadata_wrong_certificate() = runCase("invalid_metadata_wrong_certificate")
    @Test fun oversize() = runCase("oversize")
    @Test fun cancel() = runCase("cancel")
    @Test fun installed_package_mismatch() = runCase("installed_package_mismatch")
    @Test fun installed_certificate_mismatch() = runCase("installed_certificate_mismatch")
    @Test fun exact_file() = runCase("exact_file")
    companion object {
        private fun cases(): List<Array<Any>> {
            val root = File(checkNotNull(System.getenv("MIGUARDIA_BETA_WEBSITE")))
            val corpus = File(root, "tests/fixtures/public-distribution/android-oracles.json")
            check(updateHash(corpus.readBytes()) == "d086f721c29a806fd41924529fda1bdec9909f8492e4fc492d6882616f587072")
            // Node only unpacks the outer array. All wire decoding/parsing/transport/comparison and bytes use Kotlin production.
            val script = "const fs=require('node:fs'); const c=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); if(c.oracle_version!==2||c.contract_version!==1||c.cases.length!==24)throw Error('Corpus'); for(const x of c.cases)console.log(JSON.stringify(x));"
            val process = ProcessBuilder(checkNotNull(System.getenv("NODE_BINARY")), "-e", script, corpus.absolutePath)
                .directory(File(System.getProperty("user.dir"))).start()
            val lines = process.inputStream.bufferedReader().readLines()
            check(process.waitFor() == 0 && lines.size == 24)
            return lines.map { UpdateJson.parse(it) }.map { arrayOf(it.string("name"), it) }
        }
    }
}
