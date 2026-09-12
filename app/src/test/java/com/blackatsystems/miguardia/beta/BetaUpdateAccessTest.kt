package com.blackatsystems.miguardia.beta

import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import com.blackatsystems.miguardia.updates.UpdateTimeBudget
import com.blackatsystems.miguardia.updates.UpdateTestClock

class BetaUpdateAccessTest {
    @Test fun updateContextAndSignaturePreserveOriginalReceiptIdentityQueueAndStore() = runBlocking {
        for (feedback in listOf("none", "queued", "received")) {
            val issuer = BetaTestIssuer()
            val opinion = BetaOpinion.create(BetaCategory.IDEA, BetaArea.OTHER, "Opinión sintética", true)
            val pending = BetaPending("request_test_01", opinion, BetaCrypto.encode(issuer.identity().sign(feedbackProof("activation_test_01", "request_test_01", opinion.hash).toByteArray())))
            var record = BetaRecord(installationHash = issuer.fingerprint, activation = issuer.sign(issuer.payload()),
                pending = if (feedback == "queued") pending else null,
                feedbackReceipt = if (feedback == "received") issuer.feedback(pending) else null,
                feedbackRequestId = if (feedback == "received") pending.requestId else null,
                feedbackHash = if (feedback == "received") opinion.hash else null)
            var writes = 0
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val coordinator = BetaCoordinator(object : BetaStorage {
                    override suspend fun read() = record
                    override suspend fun write(value: BetaRecord) { writes++; record = value }
                }, issuer.identity(), { issuer.installed }, { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { _, _ -> error("No beta network") }) },
                    scope, Clock.fixed(issuer.start, ZoneOffset.UTC))
                coordinator.initialize()
                val before = BetaRecordCodec.encode(record)
                val context = coordinator.updateAccess.capture("release_test_02")
                assertSame(record.activation, context.receipt)
                val challenge = BetaDownloadChallenge(context.releaseId, "challenge_test_01", BetaCrypto.encode(ByteArray(32) { 8 }), issuer.start.plusSeconds(300), UpdateTimeBudget.start(), context)
                val signature = coordinator.updateAccess.sign(context, challenge)
                assertTrue(BetaCrypto.verify(issuer.identityKey.public, challenge.proof(context.activationId).toByteArray(), BetaCrypto.decode(signature)))
                coordinator.updateAccess.validate(context)
                assertEquals(before, BetaRecordCodec.encode(record)); assertEquals(0, writes)
                assertFalse(context.toString().contains(context.activationId))
                assertFalse(context.toString().contains(context.receipt.payload))
            } finally { scope.cancel() }
        }
    }
    @Test fun expiredWrongReleaseChangedPackageTrustOrIdentityRejectWithoutWriting() = runBlocking {
        val issuer = BetaTestIssuer()
        var app = issuer.installed
        var keys = mapOf("test-key" to issuer.key.public)
        var spki = issuer.identityKey.public.encoded
        var writes = 0
        val record = BetaRecord(installationHash = issuer.fingerprint, activation = issuer.sign(issuer.payload()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = BetaCoordinator(object : BetaStorage {
                override suspend fun read() = record
                override suspend fun write(value: BetaRecord) { writes++ }
            }, object : BetaIdentity {
                override fun publicKey(allowCreate: Boolean): ByteArray { assertFalse(allowCreate); return spki }
                override fun sign(bytes: ByteArray) = issuer.identity().sign(bytes)
            }, { app }, { BetaEnvironment(keys) }, scope, Clock.fixed(issuer.start, ZoneOffset.UTC))
            coordinator.initialize()
            val access = coordinator.updateAccess
            val context = access.capture("release_test_02")
            val time = UpdateTestClock(issuer.start)
            val budget = UpdateTimeBudget.start { time.nanos }
            val challenge = BetaDownloadChallenge(context.releaseId, "challenge_test_01", BetaCrypto.encode(ByteArray(32)), issuer.start.plusSeconds(300), budget, context)
            assertThrows(Exception::class.java) { runBlocking { access.sign(context, BetaDownloadChallenge("release_foreign", challenge.id, challenge.challenge, challenge.expiresAt, budget, context)) } }
            time.advance(300)
            assertThrows(Exception::class.java) { runBlocking { access.sign(context, challenge) } }
            app = app.copy(versionCode = 3)
            assertThrows(Exception::class.java) { runBlocking { access.validate(context) } }
            app = issuer.installed; keys = emptyMap()
            assertThrows(Exception::class.java) { runBlocking { access.validate(context) } }
            keys = mapOf("test-key" to issuer.key.public); spki = issuer.key.public.encoded
            assertThrows(Exception::class.java) { runBlocking { access.validate(context) } }
            assertEquals(0, writes)
        } finally { scope.cancel() }
    }
    @Test fun unavailableCoordinatorCannotInitializeOrGenerateIdentityThroughUpdateBoundary() = runBlocking {
        var reads = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = BetaCoordinator(object : BetaStorage {
                override suspend fun read(): BetaRecord { reads++; error("Must not initialize") }
                override suspend fun write(value: BetaRecord) = error("Must not write")
            }, object : BetaIdentity {
                override fun publicKey(allowCreate: Boolean): ByteArray = error("No identity")
                override fun sign(bytes: ByteArray): ByteArray = error("No signature")
            }, { error("No installed access") }, { BetaEnvironment() }, scope)
            assertThrows(Exception::class.java) { runBlocking { c.updateAccess.capture("release_test_02") } }
            assertEquals(0, reads)
        } finally { scope.cancel() }
    }

    @Test fun signingLatencyConsumesMonotonicBudgetAndCivilChangesDoNotAffectIt() = runBlocking {
        for (seconds in listOf(299L, 300L)) {
            val issuer = BetaTestIssuer()
            val time = UpdateTestClock(issuer.start.plusSeconds(3600))
            val record = BetaRecord(installationHash = issuer.fingerprint, activation = issuer.sign(issuer.payload()))
            var writes = 0
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val coordinator = BetaCoordinator(object : BetaStorage {
                    override suspend fun read() = record
                    override suspend fun write(value: BetaRecord) { writes++ }
                }, object : BetaIdentity {
                    override fun publicKey(allowCreate: Boolean) = issuer.identityKey.public.encoded
                    override fun sign(bytes: ByteArray): ByteArray {
                        time.advance(seconds); time.civil = issuer.start.minusSeconds(3600)
                        return issuer.identity().sign(bytes)
                    }
                }, { issuer.installed }, { BetaEnvironment(mapOf("test-key" to issuer.key.public)) }, scope, time)
                coordinator.initialize()
                val before = BetaRecordCodec.encode(record)
                val context = coordinator.updateAccess.capture("release_test_02")
                val challenge = BetaDownloadChallenge(context.releaseId, "challenge_test_01", BetaCrypto.encode(ByteArray(32)),
                    issuer.start.plusSeconds(300), UpdateTimeBudget.start { time.nanos }, context)
                if (seconds == 300L) assertThrows(Exception::class.java) { runBlocking { coordinator.updateAccess.sign(context, challenge) } }
                else assertEquals(64, BetaCrypto.decode(coordinator.updateAccess.sign(context, challenge)).size)
                assertEquals(before, BetaRecordCodec.encode(record)); assertEquals(0, writes)
            } finally { scope.cancel() }
        }
    }
}
