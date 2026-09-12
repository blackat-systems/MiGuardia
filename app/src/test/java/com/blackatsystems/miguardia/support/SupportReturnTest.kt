package com.blackatsystems.miguardia.support

import com.blackatsystems.miguardia.beta.*
import com.blackatsystems.miguardia.security.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SupportReturnTest {
    @Test fun `browser return retains existing disabled valid and expired lock rules`() = runBlocking {
        for ((enabled, away, expectedAllowed) in listOf(
            Triple(false, 60_001L, true), Triple(true, 59_999L, true), Triple(true, 60_000L, false),
        )) {
            var ticks = 0L
            val session = AccessLockSession { ticks }
            session.installConfiguration(AccessLockConfiguration(enabled, AccessLockTimeout.ONE_MINUTE), false)
            session.activityEnteredForeground(false)
            if (enabled) session.authenticationSucceeded(session.currentSecurityGeneration)
            fun allowed() = session.snapshot(AccessLockPhase.READY, false, null).allowsSensitiveContent
            assertTrue(allowed())
            val action = SupportMenuAction()
            var opened = 0
            action.open({}, ::allowed) { opened++ }
            session.activityPausedForPrivacy()
            session.activityLeftForeground()
            ticks = away
            session.activityEnteredForeground(false)
            session.activityResumed(false)
            assertEquals(expectedAllowed, allowed())
            assertEquals(1, opened)
            if (!expectedAllowed) {
                session.authenticationSucceeded(session.currentSecurityGeneration)
                assertTrue(allowed())
                assertEquals(1, opened)
            }
        }
    }

    @Test fun `opinion expiring outside keeps priority and never queues a support tap`() = runBlocking {
        val issuer = BetaTestIssuer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clock = MutableClock(issuer.start)
        var ticks = 0L
        val storage = object : BetaStorage {
            var record = BetaRecord(installationHash = issuer.fingerprint, activation = issuer.sign(issuer.payload()))
            var writes = 0
            override suspend fun read() = record
            override suspend fun write(value: BetaRecord) { record = value; writes++ }
        }
        var network = 0
        val beta = BetaCoordinator(storage, issuer.identity(), { issuer.installed }, {
            BetaEnvironment(mapOf("test-key" to issuer.key.public), BetaTransport { _, _ -> network++; throw IOException() })
        }, scope, clock) { ticks }
        try {
            beta.initialize()
            val before = storage.record
            val writesBefore = storage.writes
            val action = SupportMenuAction()
            var opened = 0
            action.open({}, { beta.state.value.allowsContent }) { opened++ }
            assertEquals(before, storage.record)
            assertEquals(writesBefore, storage.writes)
            assertEquals(0, network)
            beta.onBackground()
            clock.now = issuer.start.plusMillis(FEEDBACK_DELAY_MILLIS)
            ticks = FEEDBACK_DELAY_MILLIS
            beta.onForeground()
            withTimeout(5_000) { beta.state.firstDue() }
            assertFalse(beta.state.value.allowsContent)
            assertEquals(BetaPhase.OPINION, beta.state.value.phase)
            action.open({}, { beta.state.value.allowsContent }) { opened++ }
            assertEquals(1, opened)
            beta.confirm(BetaOpinion.create(BetaCategory.IDEA, BetaArea.OTHER, "Opinión sintética", true))
            withTimeout(5_000) { while (!beta.state.value.confirmationSaved) delay(5) }
            assertTrue(beta.state.value.allowsContent)
            assertEquals(1, opened)
            action.open({}, { beta.state.value.allowsContent }) { opened++ }
            assertEquals(2, opened)
        } finally { scope.cancel() }
    }

    private suspend fun kotlinx.coroutines.flow.StateFlow<BetaState>.firstDue() {
        while (!value.due) delay(5)
    }
    private class MutableClock(@Volatile var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
