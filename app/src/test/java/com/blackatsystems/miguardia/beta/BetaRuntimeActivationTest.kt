package com.blackatsystems.miguardia.beta

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BetaRuntimeActivationTest {
    @Test fun deniedPublicationDoesNotStartAndTransientFailureRetriesOnNextEvent() = runBlocking {
        var allowed = false
        var attempts = 0
        val activation = BetaRuntimeActivation({ allowed }) {
            attempts++
            if (attempts == 1) throw IOException("Synthetic storage failure")
        }
        assertFalse(activation.ensureStarted())
        assertEquals(0, attempts)
        allowed = true
        assertFalse(activation.ensureStarted())
        assertTrue(activation.ensureStarted())
        assertTrue(activation.ensureStarted())
        assertEquals(2, attempts)
    }

    @Test fun simultaneousForegroundAndReceiptEventsStartOnlyOnce() = runBlocking {
        var starts = 0
        val activation = BetaRuntimeActivation({ true }) { starts++; delay(30) }
        coroutineScope { List(20) { async { activation.ensureStarted() } }.awaitAll().forEach { assertTrue(it) } }
        assertEquals(1, starts)
    }

    @Test fun cancellationDoesNotSealAnIncompleteStart() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var attempts = 0
        val activation = BetaRuntimeActivation({ true }) {
            attempts++
            if (attempts == 1) { entered.complete(Unit); awaitCancellation() }
        }
        val first = launch { activation.ensureStarted() }
        entered.await(); first.cancelAndJoin()
        assertTrue(activation.ensureStarted())
        assertEquals(2, attempts)
    }

    @Test fun successfulStartIsInvalidatedByRestorePauseAndFailedRecovery() = runBlocking {
        var ready = true
        var paused = false
        var attempts = 0
        var fail = false
        val activation = BetaRuntimeActivation({ ready && !paused }) {
            attempts++
            if (fail) throw IOException("Synthetic resume failure")
        }
        assertTrue(activation.ensureStarted())
        activation.pause { paused = true }
        ready = false; paused = false
        assertFalse(activation.ensureStarted())
        ready = true; fail = true
        assertFalse(activation.ensureStarted())
        activation.pause { paused = true }
        paused = false; fail = false
        assertTrue(activation.ensureStarted())
        assertTrue(activation.ensureStarted())
        assertEquals(3, attempts)
    }
}
