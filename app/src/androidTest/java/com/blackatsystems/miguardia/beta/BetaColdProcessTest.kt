package com.blackatsystems.miguardia.beta

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Run prepare and reopen in separate instrumentation invocations with an explicit QA force-stop between them. */
class BetaColdProcessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.noBackupFilesDir, "beta-test-cold-v1")
    private val alias = "miguardia.beta.test.cold.v1"
    @Test fun prepareDurablePendingOperationForProcessDeath() = runBlocking {
        check(context.packageName.endsWith(".qa"))
        check(!root.exists()) { "A previous cold fixture needs recovery; never overwrite it" }
        check(!KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        check(root.mkdir())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val installed = installedBetaApp(context)
            val issuer = BetaDeviceIssuer(installed, Instant.parse("2026-09-03T12:00:00.000Z"))
            val clock = MutableBetaDeviceClock(issuer.startAt)
            val store = BetaStore(File(root, "state.preferences_pb"), scope)
            val c = BetaCoordinator(store, KeystoreBetaIdentity(alias), { installed }, { BetaEnvironment(issuer.keys, issuer.transport) }, scope, clock)
            c.initialize(); c.activate("MG" + "2345".repeat(6)); awaitBeta { c.state.value.activationValid }
            issuer.offline = true; clock.value = issuer.startAt.plusMillis(FEEDBACK_DELAY_MILLIS)
            c.onForeground(); awaitBeta { c.state.value.due }
            c.confirm(BetaOpinion.create(BetaCategory.OTHER, BetaArea.OTHER, "Ensayo de reapertura", true))
            awaitBeta { c.state.value.confirmationSaved && !c.state.value.busy }
            val saved = store.read(); val pending = saved.pending!!
            // Model an acknowledged server operation whose response was lost before local persistence.
            issuer.offline = false
            val receipt = BetaClient(issuer.transport, Instant::now).feedback(saved.activation!!, pending)
            File(root, "ack.json").writeText(BetaJson.encode(receipt.fields()))
            File(root, "public-spki").writeBytes(issuer.keys.getValue("device-test").encoded)
            File(root, "pid").writeText(Process.myPid().toString())
            File(root, "request").writeText(pending.requestId)
            assertFalse(c.state.value.received); assertTrue(c.state.value.allowsContent)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun reopenAfterProcessDeathVerifiesOfflineAndRetransmitsExactlyOnce() = runBlocking {
        check(context.packageName.endsWith(".qa"))
        assertNotEquals(File(root, "pid").readText(), Process.myPid().toString())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val key = BetaCrypto.publicKey(File(root, "public-spki").readBytes())
            val receipt = BetaEnvelope.parse(BetaJson.parse(File(root, "ack.json").readText()))
            val requestId = File(root, "request").readText()
            val store = BetaStore(File(root, "state.preferences_pb"), scope)
            val original = store.read().pending!!
            var calls = 0
            val transport = BetaTransport { path, bytes ->
                calls++; assertEquals("/feedback", path)
                val body = BetaJson.parse(BetaJson.utf8(bytes))
                assertEquals(requestId, body.string("request_id")); assertEquals(original.signature, body.string("installation_signature"))
                assertEquals(original.opinion.hash, BetaOpinion.create(BetaCategory.valueOf(body.string("category")),
                    BetaArea.valueOf(body.string("area")), body.string("message"), body["consent"] == true).hash)
                BetaResponse(200, BetaJson.encode(linkedMapOf("feedback_receipt" to receipt.fields(), "idempotent" to true, "request_id" to "cold_transport")).toByteArray())
            }
            val c = BetaCoordinator(store, KeystoreBetaIdentity(alias), { installedBetaApp(context) }, { BetaEnvironment(mapOf("device-test" to key), transport) },
                scope, Clock.fixed(Instant.parse("2026-09-18T12:01:00.000Z"), ZoneOffset.UTC))
            c.initialize(); assertEquals(0, calls); assertTrue(c.state.value.allowsContent); assertFalse(c.state.value.received)
            c.onForeground(); c.connectionRecovered(); c.retryFeedback(true); awaitBeta { c.state.value.received }
            assertEquals(1, calls); assertNull(store.read().pending)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            check(root.deleteRecursively())
        }
    }
}
