package com.blackatsystems.miguardia.beta

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

internal class BetaTestIssuer {
    val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val identityKey = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val installed = InstalledBetaApp("com.blackatsystems.miguardia", "b".repeat(64), 2)
    val fingerprint = BetaCrypto.hash(identityKey.public.encoded)
    val start = Instant.parse("2026-09-03T12:00:00.000Z")
    val verifier = BetaReceiptVerifier(mapOf("test-key" to key.public))
    fun payload() = "miguardia-beta-receipt-v1\nreceipt_type=ACTIVATION\ncontract_version=1\nkid=test-key\n" +
        "activation_id=activation_test_01\nactivated_at=2026-09-03T12:00:00.000Z\nfeedback_due_at=2026-09-18T12:00:00.000Z\n" +
        "application_id=${installed.packageName}\nrelease_id=release_test_01\nrelease_version_code=2\n" +
        "release_certificate_sha256=${installed.certificateSha256}\ninstallation_public_key_sha256=$fingerprint\n"
    fun sign(payload: String): BetaEnvelope = signBytes(payload.toByteArray(Charsets.UTF_8))
    fun signBytes(bytes: ByteArray): BetaEnvelope = BetaEnvelope("test-key", BetaCrypto.encode(bytes), BetaCrypto.encode(
        BetaCrypto.derToP1363(Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(bytes); sign() })))
    fun feedback(pending: BetaPending, id: String = "activation_test_01") = sign(
        "miguardia-beta-receipt-v1\nreceipt_type=FEEDBACK\ncontract_version=1\nkid=test-key\nfeedback_id=feedback_test_01\n" +
            "activation_id=$id\nrequest_id=${pending.requestId}\nreceived_at=2026-09-18T12:00:01.000Z\nbody_sha256=${pending.opinion.hash}\n")
    fun identity() = object : BetaIdentity {
        override fun publicKey(allowCreate: Boolean) = identityKey.public.encoded
        override fun sign(bytes: ByteArray) = BetaCrypto.derToP1363(Signature.getInstance("SHA256withECDSA").run {
            initSign(identityKey.private); update(bytes); sign()
        })
    }
}
class BetaProtocolTest {
    private val fixture = BetaTestIssuer()
    private fun rejected(block: () -> Unit) { assertThrows(Exception::class.java, block) }
    @Test fun validReceiptAndOlderVersionSurviveUpgrade() {
        val receipt = fixture.sign(fixture.payload())
        assertEquals(fixture.start.plusMillis(FEEDBACK_DELAY_MILLIS), fixture.verifier.activation(receipt, fixture.installed, fixture.fingerprint).dueAt)
        fixture.verifier.activation(receipt, fixture.installed.copy(versionCode = 3), fixture.fingerprint)
        rejected { fixture.verifier.activation(receipt, fixture.installed.copy(versionCode = 1), fixture.fingerprint) }
    }
    @Test fun allInstallationBindingsAreRequired() {
        val receipt = fixture.sign(fixture.payload())
        rejected { fixture.verifier.activation(receipt, fixture.installed.copy(packageName = "wrong.package"), fixture.fingerprint) }
        rejected { fixture.verifier.activation(receipt, fixture.installed.copy(certificateSha256 = "c".repeat(64)), fixture.fingerprint) }
        rejected { fixture.verifier.activation(receipt, fixture.installed, "a".repeat(64)) }
        rejected { BetaReceiptVerifier(emptyMap()).activation(receipt, fixture.installed, fixture.fingerprint) }
        rejected { BetaReceiptVerifier(mapOf("test-key" to fixture.identityKey.public)).activation(receipt, fixture.installed, fixture.fingerprint) }
    }
    @Test fun signedButInvalidDatesNumbersAndFieldOrderFail() {
        val original = fixture.payload()
        listOf(
            original.replace("2026-09-03", "2026-02-30"), original.replace("2026-09-18", "2026-09-19"),
            original.replace(".000Z", "Z"), original.replace("T12:00:00", "T24:00:00"),
            original.replace("release_version_code=2", "release_version_code=0"),
            original.replace("release_version_code=2", "release_version_code=02"),
            original.replace("release_version_code=2", "release_version_code=1e2"),
            original.replace("release_version_code=2", "release_version_code=9007199254740992"),
            original.replace("receipt_type=ACTIVATION", "receipt_type=FEEDBACK"),
            original.replace("kid=test-key", "kid=other-key"),
            original.replace("activation_id=activation_test_01", "activation_id=short"),
            original.replace("release_id=release_test_01", "activation_id=release_test_01"),
            original.replace("kid=test-key\nactivation_id=activation_test_01", "activation_id=activation_test_01\nkid=test-key"),
            original.dropLast(1), original + "extra=value\n", original.replace("\n", "\r\n"), "\uFEFF$original",
        ).forEach { candidate -> rejected { fixture.verifier.activation(fixture.sign(candidate), fixture.installed, fixture.fingerprint) } }
    }
    @Test fun invalidUtf8AndAlteredPayloadOrSignatureFail() {
        rejected { fixture.verifier.activation(fixture.signBytes(byteArrayOf(0xc3.toByte(), 0x28)), fixture.installed, fixture.fingerprint) }
        val receipt = fixture.sign(fixture.payload())
        rejected { fixture.verifier.activation(receipt.copy(payload = BetaCrypto.encode(fixture.payload().replace("2026-09", "2026-08").toByteArray())), fixture.installed, fixture.fingerprint) }
        val signature = BetaCrypto.decode(receipt.signature).also { it[20] = (it[20].toInt() xor 8).toByte() }
        rejected { fixture.verifier.activation(receipt.copy(signature = BetaCrypto.encode(signature)), fixture.installed, fixture.fingerprint) }
    }
    @Test fun envelopeRejectsDuplicatesUnknownKeysTypesAlgorithmsAndSizes() {
        val json = BetaJson.encode(fixture.sign(fixture.payload()).fields())
        fun parse(s: String) = BetaEnvelope.parse(BetaJson.parse(s))
        parse(json)
        listOf(json.replace("ES256", "HS256"), json.replace("P1363", "DER"), json.replace("\"contract_version\":1", "\"contract_version\":\"1\""),
            json.replace("{", "{\"kid\":\"duplicate\","), json.dropLast(1) + ",\"extra\":true}").forEach { rejected { parse(it) } }
        rejected { parse(BetaJson.encode(fixture.sign(fixture.payload()).fields() + ("payload_base64url" to "A".repeat(2049)))) }
        rejected { BetaJson.parse("{\"x\":{},\"x\":{}}") }
        rejected { BetaJson.parse("{\"x\":1e0}") }
        rejected { BetaJson.parse("{\"x\":{\"y\":{\"z\":{\"overflow\":{}}}}}") }
    }
    @Test fun base64IsCanonicalAndHasNoPadding() {
        listOf("AA=", "AA==", "A+", "A/", "A", "AB", "\nAA", "", "AA ").forEach { rejected { BetaCrypto.decode(it) } }
        assertArrayEquals(byteArrayOf(0), BetaCrypto.decode("AA"))
    }
    @Test fun derP1363ChecksZeroRangeSignLengthsAndMinimalEncoding() {
        repeat(32) {
            val receipt = fixture.sign(fixture.payload())
            val raw = BetaCrypto.decode(receipt.signature)
            assertArrayEquals(raw, BetaCrypto.derToP1363(BetaCrypto.p1363ToDer(raw)))
        }
        rejected { BetaCrypto.p1363ToDer(ByteArray(64)) }
        rejected { BetaCrypto.p1363ToDer(ByteArray(64) { 0xff.toByte() }) }
        listOf(byteArrayOf(0x30, 6, 2, 1, 0x80.toByte(), 2, 1, 1), byteArrayOf(0x30, 7, 2, 2, 0, 1, 2, 1, 1),
            byteArrayOf(0x30, 6, 2, 1, 0, 2, 1, 1), byteArrayOf(0x30, 0x81.toByte(), 6, 2, 1, 1, 2, 1, 1)).forEach { rejected { BetaCrypto.derToP1363(it) } }
        val valid = BetaCrypto.p1363ToDer(BetaCrypto.decode(fixture.sign(fixture.payload()).signature))
        rejected { BetaCrypto.derToP1363(valid + 0) }
    }
    @Test fun feedbackAcknowledgementBindsToTheConfirmedOperation() {
        val pending = BetaPending("request_test_01", BetaOpinion.create(BetaCategory.BUG, BetaArea.ACCESS, "Un detalle", true), "")
        fixture.verifier.feedback(fixture.feedback(pending), "activation_test_01", pending.requestId, pending.opinion.hash)
        rejected { fixture.verifier.feedback(fixture.feedback(pending), "another_activation", pending.requestId, pending.opinion.hash) }
        rejected { fixture.verifier.feedback(fixture.feedback(pending), "activation_test_01", "another_request", pending.opinion.hash) }
        rejected { fixture.verifier.feedback(fixture.feedback(pending), "activation_test_01", pending.requestId, "a".repeat(64)) }
    }
    @Test fun textNormalizationAndUtf16LimitsMatchContract() {
        fun opinion(s: String) = BetaOpinion.create(BetaCategory.OTHER, BetaArea.OTHER, s, true)
        assertEquals("á", opinion("\uFEFF\u00a0a\u0301\u3000").message)
        assertEquals(2000, opinion("😀".repeat(1000)).message.length)
        rejected { opinion("😀".repeat(1001)) }
        rejected { opinion("\u0085mensaje") }
        rejected { opinion("mensaje\u200b") }
        rejected { opinion("  ") }
        assertEquals("a\r\n\tb", opinion("a\r\n\tb").message)
        rejected { BetaOpinion.create(BetaCategory.IDEA, BetaArea.OTHER, "idea", false) }
        assertEquals("\"/<> &\\\"\\\\\\r\\n\\t\"", BetaJson.quote("/<> &\"\\\r\n\t"))
        assertEquals("\"\\ud800\"", BetaJson.quote("\ud800"))
    }
    @Test fun accessCodeUsesActualAlphabetAndNeverBecomesPersistentState() {
        val code = "mg-" + List(6) { "z234" }.joinToString("-")
        assertEquals("MG" + "Z234".repeat(6), normalizeBetaCode(code))
        rejected { normalizeBetaCode(code.replace('z', '0')) }
        rejected { normalizeBetaCode(code + "\n") }
        assertFalse(BetaRecordCodec.encode(BetaRecord()).contains("access_code"))
    }
    @Test fun noAuthorityFailsClosedAndInstalledVersionIsNotChanged() {
        assertTrue(BetaVariant.environment().keys.isEmpty())
        rejected { BetaReceiptVerifier(BetaVariant.environment().keys).activation(fixture.sign(fixture.payload()), fixture.installed, fixture.fingerprint) }
    }
}
