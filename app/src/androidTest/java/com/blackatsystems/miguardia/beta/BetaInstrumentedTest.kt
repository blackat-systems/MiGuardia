package com.blackatsystems.miguardia.beta

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaInstrumentedTest {
    @Test fun keystoreAndDurableQueueReopenOfflineThenAcknowledgeTheSameOperation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".qa"))
        val alias = "miguardia.beta.test.${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "beta-test-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val installed = installedBetaApp(context)
            val issuer = BetaDeviceIssuer(installed, Instant.parse("2026-09-03T12:00:00.000Z"))
            val clock = MutableBetaDeviceClock(issuer.startAt)
            val identity = KeystoreBetaIdentity(alias)
            val spki = identity.publicKey(true)
            assertArrayEquals(spki, KeystoreBetaIdentity(alias).publicKey(false))
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
            assertNull(key.encoded)
            val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
            assertFalse(info.isUserAuthenticationRequired)
            val proof = "synthetic\n".toByteArray()
            assertTrue(BetaCrypto.verify(BetaCrypto.publicKey(spki), proof, identity.sign(proof)))
            var store = BetaStore(file, scope)
            fun coordinator() = BetaCoordinator(store, KeystoreBetaIdentity(alias), { installed },
                { BetaEnvironment(issuer.keys, issuer.transport) }, scope, clock)
            var c = coordinator(); c.initialize(); c.activate("MG" + "2345".repeat(6))
            awaitBeta { c.state.value.activationValid }
            issuer.offline = true
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); store = BetaStore(file, scope); c = coordinator()
            c.initialize(); assertTrue(c.state.value.allowsContent)
            assertEquals(1, issuer.bodies.size)
            clock.value = issuer.startAt.plusMillis(FEEDBACK_DELAY_MILLIS); c.onForeground(); awaitBeta { c.state.value.due }
            c.confirm(BetaOpinion.create(BetaCategory.IDEA, BetaArea.ACCESS, "Prueba ficticia", true))
            awaitBeta { c.state.value.confirmationSaved && !c.state.value.busy }
            assertTrue(c.state.value.allowsContent); assertFalse(c.state.value.received)
            val pending = store.read().pending!!
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); store = BetaStore(file, scope); c = coordinator()
            c.initialize(); assertEquals(pending, store.read().pending)
            issuer.offline = false; clock.value = clock.value.plusSeconds(31); c.onForeground()
            awaitBeta { c.state.value.received }
            assertNull(store.read().pending); assertNotNull(store.read().feedbackReceipt)
            val sends = issuer.bodies.filter { it.first == "/feedback" }
            assertEquals(2, sends.size); assertEquals(sends[0], sends[1])
            assertFalse(BetaRecordCodec.encode(store.read()).contains("Prueba ficticia"))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            check(!file.exists() || file.delete())
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
    @Test fun unavailableTrustDoesNotCreateAnIdentityOrTouchWorkingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val c = BetaCoordinator(object : BetaStorage {
                override suspend fun read() = BetaRecord()
                override suspend fun write(value: BetaRecord) = error("No record needed without trust")
            }, object : BetaIdentity {
                override fun publicKey(allowCreate: Boolean) = error("No identity needed without trust")
                override fun sign(bytes: ByteArray) = error("No sign without trust")
            }, { installedBetaApp(context) }, { BetaEnvironment() }, scope)
            c.initialize(); assertEquals(BetaPhase.UNAVAILABLE, c.state.value.phase); assertFalse(c.state.value.allowsContent)
        } finally { scope.cancel() }
    }
}
internal class MutableBetaDeviceClock(@Volatile var value: Instant) : Clock() {
    override fun instant() = value
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
internal suspend fun awaitBeta(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }
