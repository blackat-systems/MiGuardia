package com.blackatsystems.miguardia.beta

import java.io.File
import java.security.Signature
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(WebCryptoInterop::class)
class BetaWebCryptoTest {
    @Test fun realWebsiteWebCryptoSignatureAndNormalizationVectorsMatchJcaAndKotlin() {
        val lines = javaClass.getResourceAsStream("/beta/webcrypto-vectors.jsonl")!!.bufferedReader().use { it.readLines() }
        val vector = BetaJson.parse(lines.first())
        val key = BetaCrypto.publicKey(BetaCrypto.decode(vector.string("public_spki")))
        val receipt = BetaEnvelope.parse(vector.obj("activation"))
        BetaReceiptVerifier(mapOf(receipt.kid to key)).activation(receipt,
            InstalledBetaApp("com.blackatsystems.miguardia", "b".repeat(64), 1), "a".repeat(64))
        lines.drop(1).forEach { line ->
            val v = BetaJson.parse(line)
            val opinion = BetaOpinion.create(BetaCategory.IDEA, BetaArea.CALENDAR, v.string("input"), true)
            assertEquals(v.string("normalized"), opinion.message)
            assertEquals(v.string("body"), BetaJson.encode(opinion.fields()))
            assertEquals(v.string("hash"), opinion.hash)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BetaOpinion.create(BetaCategory.IDEA, BetaArea.OTHER, "a" + String(Character.toChars(0xe0020)), true)
        }
    }
    @Test fun jcaInstallationSignatureVerifiesInNativeWebCrypto() {
        val issuer = BetaTestIssuer()
        val opinion = BetaOpinion.create(BetaCategory.IDEA, BetaArea.CALENDAR, "á 😀 / <>&\r\n\t", true)
        val proof = feedbackProof("vector_activation", "vector_request", opinion.hash)
        val raw = BetaCrypto.derToP1363(Signature.getInstance("SHA256withECDSA").run {
            initSign(issuer.identityKey.private); update(proof.toByteArray()); sign()
        })
        val oracle = File(javaClass.getResource("/beta/webcrypto-oracle.mjs")!!.toURI())
        val website = checkNotNull(System.getenv("MIGUARDIA_BETA_WEBSITE")) {
            "Read-only website path required for authoritative interoperability test"
        }
        val node = checkNotNull(System.getenv("NODE_BINARY")) { "Explicit Node runtime required for interoperability test" }
        val command = listOf(node, "--experimental-transform-types", oracle.absolutePath, website)
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        try {
            process.outputStream.bufferedWriter().use { it.write(BetaJson.encode(linkedMapOf("public_spki" to BetaCrypto.encode(issuer.identityKey.public.encoded),
                "signature" to BetaCrypto.encode(raw), "payload" to proof))) }
            assertTrue(process.waitFor(20, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertEquals("verified", process.inputStream.bufferedReader().readText().trim())
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}
