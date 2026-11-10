package com.blackatsystems.miguardia.beta

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BetaCoordinatorTest {
    private val issuer = BetaTestIssuer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = TestClock(issuer.start)
    private val store = TestStore()
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var ticks = 0L
    private val code get() = "MG" + "2345".repeat(6)
    private val opinion get() = BetaOpinion.create(BetaCategory.IDEA, BetaArea.CALENDAR, " Una mejora ", true)
    @After fun stop() { scope.cancel() }
    private class TestStore : BetaStorage {
        @Volatile var saved = BetaRecord()
        @Volatile var fail = false
        var afterWrite: (BetaRecord) -> Unit = {}
        override suspend fun read() = BetaRecordCodec.decode(BetaRecordCodec.encode(saved))
        override suspend fun write(value: BetaRecord) {
            if (fail) throw IOException()
            saved = BetaRecordCodec.decode(BetaRecordCodec.encode(value))
            afterWrite(value)
        }
    }
    private class TestClock(@Volatile var instant: Instant) : Clock() {
        override fun instant() = instant
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
    private fun ok(envelope: BetaEnvelope, name: String = "activation_receipt") = BetaResponse(200,
        BetaJson.encode(linkedMapOf(name to envelope.fields(), "idempotent" to false, "request_id" to "transport_request_01")).toByteArray())
    private fun failure(status: Int, code: String = "ACCESS_DENIED", after: String? = null) = BetaResponse(status,
        BetaJson.encode(linkedMapOf("error" to linkedMapOf("code" to code, "message" to "Error"), "request_id" to "transport_request_01")).toByteArray(), after)
    private fun coordinator(reply: suspend (String, String) -> BetaResponse = { _, _ -> ok(issuer.sign(issuer.payload())) }) = BetaCoordinator(
        store, issuer.identity(), { issuer.installed }, { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { path, bytes ->
            val body = BetaJson.utf8(bytes); requests += path to body; reply(path, body)
        }) }, scope, clock) { ticks }
    private suspend fun awaitState(c: BetaCoordinator, predicate: (BetaState) -> Boolean) = withTimeout(5000) {
        while (!predicate(c.state.value)) delay(5)
    }
    private suspend fun activated(c: BetaCoordinator) { c.initialize(); c.activate(code); awaitState(c) { it.activationValid } }
    private fun due() { clock.instant = issuer.start.plusMillis(FEEDBACK_DELAY_MILLIS); ticks = FEEDBACK_DELAY_MILLIS }

    @Test fun doubleCanjeUsesOneKeyAndOneCallAndOnlyPersistsVerifiedReceipt() = runBlocking {
        val response = CompletableDeferred<Unit>()
        val c = coordinator { _, body ->
            BetaJson.parse(body).exact("access_code", "installation_public_key_spki")
            response.await(); ok(issuer.sign(issuer.payload()))
        }
        c.initialize(); c.activate(code); c.activate(code)
        withTimeout(5000) { while (requests.isEmpty()) delay(5) }
        assertFalse(c.state.value.allowsContent); assertNull(store.saved.activation)
        response.complete(Unit); awaitState(c) { it.activationValid }
        assertEquals(1, requests.size)
        assertEquals(issuer.fingerprint, store.saved.installationHash)
        assertFalse(BetaRecordCodec.encode(store.saved).contains(code))
    }
    @Test fun lateCancelledCanjeDoesNotOpenGate() = runBlocking {
        val response = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val c = coordinator { _, _ -> withContext(NonCancellable) { response.await(); finished.complete(Unit); ok(issuer.sign(issuer.payload())) } }
        c.initialize(); c.activate(code)
        withTimeout(5000) { while (requests.isEmpty()) delay(5) }
        c.cancelActivation(); response.complete(Unit); finished.await(); delay(80)
        assertFalse(c.state.value.activationValid); assertNull(store.saved.activation)
    }
    @Test fun cancellationDuringDurableCommitKeepsGateClosedUntilVerifiedReread() = runBlocking {
        val committed = CompletableDeferred<Unit>()
        val c = coordinator()
        c.initialize()
        store.afterWrite = { value ->
            if (value.activation != null) {
                c.cancelActivation()
                committed.complete(Unit)
            }
        }
        c.activate(code)
        withTimeout(5000) { committed.await() }
        delay(50)
        assertNotNull(store.saved.activation)
        assertFalse(c.state.value.activationValid)
        store.afterWrite = {}
        c.retryRead()
        awaitState(c) { it.activationValid }
        c.activate(code)
        assertEquals(1, requests.size)
        assertEquals(issuer.fingerprint, store.saved.installationHash)
    }
    @Test fun reconnectDuringCooldownDefersOneSendUntilAllowed() = runBlocking {
        val initial = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else throw IOException() }
        activated(initial); initial.confirm(opinion)
        awaitState(initial) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
        val queue = store.saved.pending!!
        val liveClock = Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), issuer.start))
        store.saved = store.saved.copy(retryAt = liveClock.instant().plusMillis(400))
        val deferred = BetaCoordinator(store, issuer.identity(), { issuer.installed },
            { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { path, bytes ->
                requests += path to BetaJson.utf8(bytes)
                ok(issuer.feedback(queue), "feedback_receipt")
            }) }, scope, liveClock)
        deferred.initialize(); deferred.onForeground(); deferred.connectionRecovered()
        delay(50)
        assertEquals(1, requests.count { it.first == "/feedback" })
        awaitState(deferred) { it.received }
        assertEquals(2, requests.count { it.first == "/feedback" })
        assertEquals(requests.first { it.first == "/feedback" }, requests.last())
    }
    @Test fun backgroundCancelsDeferredSendAndNextForegroundCanRetry() = runBlocking {
        val initial = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else throw IOException() }
        activated(initial); initial.confirm(opinion)
        awaitState(initial) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
        val queue = store.saved.pending!!
        val liveClock = Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), issuer.start))
        store.saved = store.saved.copy(retryAt = liveClock.instant().plusMillis(500))
        val deferred = BetaCoordinator(store, issuer.identity(), { issuer.installed },
            { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { path, bytes ->
                requests += path to BetaJson.utf8(bytes)
                ok(issuer.feedback(queue), "feedback_receipt")
            }) }, scope, liveClock)
        deferred.initialize(); deferred.onForeground(); deferred.connectionRecovered()
        delay(50); deferred.onBackground(); delay(650)
        assertEquals(1, requests.count { it.first == "/feedback" })
        assertFalse(deferred.state.value.received)
        deferred.onForeground(); awaitState(deferred) { it.received }
        assertEquals(2, requests.count { it.first == "/feedback" })
    }
    @Test fun deferredRetryFailureDoesNotCreateAnAutomaticPollingLoop() = runBlocking {
        val initial = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else throw IOException() }
        activated(initial); initial.confirm(opinion)
        awaitState(initial) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
        val liveClock = Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), issuer.start))
        store.saved = store.saved.copy(retryAt = liveClock.instant().plusMillis(300))
        val deferred = BetaCoordinator(store, issuer.identity(), { issuer.installed },
            { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { path, bytes ->
                requests += path to BetaJson.utf8(bytes)
                failure(503, "TEMPORARILY_UNAVAILABLE", "1")
            }) }, scope, liveClock)
        deferred.initialize(); deferred.onForeground(); deferred.connectionRecovered()
        withTimeout(5000) { while (requests.count { it.first == "/feedback" } != 2) delay(10) }
        awaitState(deferred) { !it.busy }
        delay(1250)
        assertEquals(2, requests.count { it.first == "/feedback" })
        assertTrue(deferred.state.value.confirmationSaved)
        assertFalse(deferred.state.value.received)
    }
    @Test fun failedActivationPersistenceAndWrongReceiptStayClosedAndCanRetry() = runBlocking {
        val c = coordinator(); c.initialize(); store.fail = true; c.activate(code)
        awaitState(c) { !it.busy && it.message != null }
        assertFalse(c.state.value.allowsContent); assertNull(store.saved.activation)
        store.fail = false; c.activate(code); awaitState(c) { it.activationValid }
        assertEquals(requests[0], requests[1])
        val invalid = BetaCoordinator(TestStore(), issuer.identity(), { issuer.installed.copy(packageName = "foreign") },
            { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { _, _ -> ok(issuer.sign(issuer.payload())) }) }, scope, clock)
        invalid.initialize(); invalid.activate(code); awaitState(invalid) { !it.busy && it.message != null }
        assertFalse(invalid.state.value.activationValid)
    }
    @Test fun beforeAtAndAfterDeadlineLatchAcrossRecreationAndClockRollback() = runBlocking {
        val c = coordinator(); activated(c)
        clock.instant = issuer.start.plusMillis(FEEDBACK_DELAY_MILLIS - 1); ticks = FEEDBACK_DELAY_MILLIS - 1
        c.onForeground(); delay(30); assertFalse(c.state.value.due)
        due(); c.onForeground(); awaitState(c) { it.due }
        assertFalse(c.state.value.allowsContent); assertTrue(store.saved.dueObserved)
        clock.instant = issuer.start.minusSeconds(60); ticks = 0
        val reopened = coordinator { _, _ -> error("Offline reopen must not call the service") }; reopened.initialize()
        assertTrue(reopened.state.value.due); assertTrue(reopened.state.value.activationValid)
        assertFalse(reopened.state.value.allowsContent)
    }
    @Test fun monotonicDeadlineSurvivesCivilRollbackAndFailedLatchIsRetried() = runBlocking {
        val c = coordinator(); activated(c)
        clock.instant = issuer.start.minusSeconds(3600); ticks = FEEDBACK_DELAY_MILLIS; store.fail = true
        c.onForeground(); awaitState(c) { it.due }
        assertFalse(store.saved.dueObserved); assertFalse(c.state.value.allowsContent)
        store.fail = false; c.onForeground()
        withTimeout(5000) { while (!store.saved.dueObserved) delay(5) }
        assertTrue(c.state.value.due)
    }
    @Test fun offlineReopenDoesNotCallServiceOrRegenerateIdentity() = runBlocking {
        val c = coordinator(); activated(c)
        var creates = 0
        val identity = object : BetaIdentity {
            override fun publicKey(allowCreate: Boolean): ByteArray { if (allowCreate) creates++; return issuer.identityKey.public.encoded }
            override fun sign(bytes: ByteArray) = error("No feedback pending")
        }
        val reopened = BetaCoordinator(store, identity, { issuer.installed.copy(versionCode = 3) },
            { BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { _, _ -> error("No network on reopen") }) }, scope, clock)
        reopened.initialize(); reopened.onForeground(); delay(50)
        assertTrue(reopened.state.value.allowsContent); assertEquals(0, creates)
    }
    @Test fun missingKeyUnknownAuthorityAndCorruptStoreFailClosedWithoutDeletingRecord() = runBlocking {
        activated(coordinator()); val saved = store.saved
        val missing = object : BetaIdentity {
            override fun publicKey(allowCreate: Boolean): ByteArray { assertFalse(allowCreate); throw IOException() }
            override fun sign(bytes: ByteArray) = error("Missing key")
        }
        val c = BetaCoordinator(store, missing, { issuer.installed }, { BetaEnvironment(mapOf("test-key" to issuer.key.public)) }, scope, clock)
        c.initialize(); assertEquals(BetaPhase.REPAIR, c.state.value.phase); assertEquals(saved, store.saved)
        val noTrust = BetaCoordinator(store, issuer.identity(), { issuer.installed }, { BetaEnvironment() }, scope, clock)
        noTrust.initialize(); assertEquals(BetaPhase.UNAVAILABLE, noTrust.state.value.phase)
        val corrupt = object : BetaStorage {
            override suspend fun read() = BetaRecordCodec.decode("{\"version\":1}")
            override suspend fun write(value: BetaRecord) = error("Never overwrite corruption")
        }
        val broken = BetaCoordinator(corrupt, issuer.identity(), { issuer.installed }, { BetaEnvironment(mapOf("test-key" to issuer.key.public)) }, scope, clock)
        broken.initialize(); assertEquals(BetaPhase.REPAIR, broken.state.value.phase)
    }
    @Test fun failedConfirmationCannotReleaseGateAndDoubleTapOnlyCreatesOneImmutableQueueItem() = runBlocking {
        val c = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else throw IOException() }
        activated(c); due(); c.onForeground(); awaitState(c) { it.due }
        store.fail = true; c.confirm(opinion); awaitState(c) { !it.busy && it.message != null }
        assertFalse(c.state.value.allowsContent); assertNull(store.saved.pending)
        store.fail = false; c.confirm(opinion); c.confirm(BetaOpinion.create(BetaCategory.BUG, BetaArea.OTHER, "Otro", true))
        awaitState(c) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
        assertTrue(c.state.value.allowsContent); assertFalse(c.state.value.received)
        assertEquals(opinion, store.saved.pending!!.opinion)
        assertEquals(1, requests.count { it.first == "/feedback" })
    }
    @Test fun lostResponseRestartAndIdempotentRetryKeepExactBodyThenEraseMessage() = runBlocking {
        val c = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else throw IOException() }
        activated(c); due(); c.confirm(opinion); awaitState(c) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
        val queue = store.saved.pending!!; val sentBody = requests.last().second
        val body = BetaJson.parse(sentBody)
        body.exact("activation_receipt", "request_id", "category", "area", "message", "consent", "installation_signature")
        assertTrue(BetaCrypto.verify(issuer.identityKey.public,
            feedbackProof("activation_test_01", queue.requestId, queue.opinion.hash).toByteArray(), BetaCrypto.decode(queue.signature)))
        clock.instant = clock.instant.plusSeconds(31); ticks += 31_000
        val reopened = coordinator { path, bodyAgain ->
            assertEquals("/feedback", path); assertEquals(sentBody, bodyAgain)
            ok(issuer.feedback(queue), "feedback_receipt")
        }
        reopened.initialize(); assertTrue(reopened.state.value.allowsContent); reopened.onForeground()
        awaitState(reopened) { it.received }
        assertNull(store.saved.pending); assertNotNull(store.saved.feedbackReceipt)
        assertFalse(BetaRecordCodec.encode(store.saved).contains(opinion.message))
        val offline = coordinator { _, _ -> error("Already received") }; offline.initialize(); offline.onForeground(); delay(30)
        assertTrue(offline.state.value.received)
    }
    @Test fun http200WithForeignReceiptOrAlreadyReceivedNeverAcknowledgesAndTerminalDoesNotLoop() = runBlocking {
        for (status in listOf(200, 403, 409)) {
            store.saved = BetaRecord(); clock.instant = issuer.start; ticks = 0
            val c = coordinator { path, _ ->
                if (path == "/activations") ok(issuer.sign(issuer.payload()))
                else if (status == 200) ok(issuer.feedback(store.saved.pending!!, "foreign_activation"), "feedback_receipt")
                else failure(status, "FEEDBACK_ALREADY_RECEIVED")
            }
            activated(c); due(); c.confirm(opinion); awaitState(c) { it.phase == BetaPhase.SEND_ERROR }
            assertTrue(c.state.value.allowsContent); assertFalse(c.state.value.received); assertNotNull(store.saved.pending)
            val count = requests.size; clock.instant = clock.instant.plusSeconds(60); ticks += 60_000
            c.onForeground(); c.connectionRecovered(); delay(40); assertEquals(count, requests.size); c.onBackground()
        }
    }
    @Test fun retryAfterIsRespectedFor429And503AcrossRestartAndParallelTriggers() = runBlocking {
        for (status in listOf(429, 503)) {
            store.saved = BetaRecord(); clock.instant = issuer.start; ticks = 0
            val c = coordinator { path, _ -> if (path == "/activations") ok(issuer.sign(issuer.payload())) else failure(status, "RATE_LIMITED", "120") }
            activated(c); due(); c.confirm(opinion); awaitState(c) { it.confirmationSaved && !it.busy && store.saved.retryAt != null }
            val count = requests.size
            val reopened = coordinator { _, _ -> failure(status, "RATE_LIMITED", "120") }
            reopened.initialize(); reopened.onForeground(); reopened.retryFeedback(true); reopened.connectionRecovered(); delay(50)
            assertEquals(count, requests.size)
            clock.instant = clock.instant.plusSeconds(121); ticks += 121_000
            reopened.retryFeedback(true); reopened.retryFeedback(true)
            withTimeout(5000) { while (requests.size == count) delay(5) }; delay(40)
            assertEquals(count + 1, requests.size); reopened.onBackground(); c.onBackground()
        }
    }
    @Test fun retryAfterHttpDateAndErrorEnvelopeAreStrict() = runBlocking {
        assertEquals(issuer.start.plusSeconds(60), BetaClient.retryAfter("60", issuer.start))
        assertEquals(issuer.start, BetaClient.retryAfter("Wed, 02 Sep 2026 12:00:00 GMT", issuer.start))
        assertNull(BetaClient.retryAfter("-1", issuer.start))
        val bad = BetaClient(BetaTransport { _, _ -> BetaResponse(200, "{\"activation_receipt\":{},\"idempotent\":true,\"request_id\":\"transport_request_01\",\"extra\":1}".toByteArray()) }, { clock.instant() })
        try { bad.activate(code, issuer.identityKey.public.encoded); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun repeatedForegroundAndDeadlineCallbacksCannotDuplicateAnInflightSend() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val c = coordinator { path, _ ->
            if (path == "/activations") ok(issuer.sign(issuer.payload()))
            else { release.await(); ok(issuer.feedback(store.saved.pending!!), "feedback_receipt") }
        }
        activated(c); due(); c.confirm(opinion); awaitState(c) { it.phase == BetaPhase.SENDING }
        repeat(8) { c.onForeground(); c.connectionRecovered(); c.retryFeedback(true) }
        delay(80)
        assertEquals(1, requests.count { it.first == "/feedback" })
        assertEquals(BetaPhase.SENDING, c.state.value.phase)
        release.complete(Unit); awaitState(c) { it.received }
    }
    @Test fun initialCivilClockBehindActivationStillUsesTheExactSignedDeadline() = runBlocking {
        clock.instant = issuer.start.minusSeconds(86_400)
        val c = coordinator(); activated(c)
        ticks = FEEDBACK_DELAY_MILLIS; c.onForeground(); delay(40)
        assertFalse(c.state.value.due)
        ticks += 86_400_000; c.onForeground(); awaitState(c) { it.due }
    }
}
