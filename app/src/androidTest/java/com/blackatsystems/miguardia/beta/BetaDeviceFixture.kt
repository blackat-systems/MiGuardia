package com.blackatsystems.miguardia.beta

import android.content.Context
import com.blackatsystems.miguardia.MiGuardiaApplication
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Only installed in tests, never an APK asset or a trusted default. */
internal class BetaDeviceIssuer(private val installed: InstalledBetaApp, private val start: Instant = Instant.now()) {
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val keys get() = mapOf("device-test" to key.public)
    val startAt: Instant get() = Instant.ofEpochMilli(start.toEpochMilli())
    var offline = false
    val bodies = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()
    private val formatter = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
    private fun sign(payload: String) = BetaEnvelope("device-test", BetaCrypto.encode(payload.toByteArray()),
        BetaCrypto.encode(BetaCrypto.derToP1363(Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(payload.toByteArray()); sign()
        })))
    val transport = BetaTransport { path, bytes ->
        val body = BetaJson.parse(BetaJson.utf8(bytes)); bodies += path to body
        if (offline) throw java.io.IOException("Synthetic offline")
        val envelope = when (path) {
            "/activations" -> {
                body.exact("access_code", "installation_public_key_spki")
                val spki = BetaCrypto.decode(body.string("installation_public_key_spki")); BetaCrypto.publicKey(spki)
                sign("miguardia-beta-receipt-v1\nreceipt_type=ACTIVATION\ncontract_version=1\nkid=device-test\n" +
                    "activation_id=device_activation\nactivated_at=${formatter.format(startAt)}\nfeedback_due_at=${formatter.format(startAt.plusMillis(FEEDBACK_DELAY_MILLIS))}\n" +
                    "application_id=${installed.packageName}\nrelease_id=device_release\nrelease_version_code=${installed.versionCode}\n" +
                    "release_certificate_sha256=${installed.certificateSha256}\ninstallation_public_key_sha256=${BetaCrypto.hash(spki)}\n")
            }
            "/feedback" -> {
                body.exact("activation_receipt", "request_id", "category", "area", "message", "consent", "installation_signature")
                val opinion = BetaOpinion.create(BetaCategory.valueOf(body.string("category")), BetaArea.valueOf(body.string("area")), body.string("message"), body["consent"] == true)
                val activation = BetaEnvelope.parse(body.obj("activation_receipt"))
                check(activation.kid == "device-test")
                val spki = bodies.first { it.first == "/activations" }.second.string("installation_public_key_spki")
                check(BetaCrypto.verify(BetaCrypto.publicKey(BetaCrypto.decode(spki)), feedbackProof("device_activation", body.string("request_id"), opinion.hash).toByteArray(),
                    BetaCrypto.decode(body.string("installation_signature"))))
                sign("miguardia-beta-receipt-v1\nreceipt_type=FEEDBACK\ncontract_version=1\nkid=device-test\nfeedback_id=device_feedback\n" +
                    "activation_id=device_activation\nrequest_id=${body.string("request_id")}\nreceived_at=${formatter.format(Instant.now())}\nbody_sha256=${opinion.hash}\n")
            }
            else -> error("Unexpected endpoint")
        }
        BetaResponse(200, BetaJson.encode(linkedMapOf((if (path == "/activations") "activation_receipt" else "feedback_receipt") to envelope.fields(),
            "idempotent" to (bodies.count { it.first == path } > 1), "request_id" to "device_transport")).toByteArray())
    }
}

/** Waits for the real public root; never reads/writes identity or activates beta. */
internal object PublicQaRootFixture {
    suspend fun awaitReady(application: MiGuardiaApplication) {
        check(application.packageName.endsWith(".qa"))
        withTimeout(15_000) { while (!application.startupRecoveryGate.isReady) delay(20) }
    }
}
