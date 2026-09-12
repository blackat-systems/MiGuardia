package com.blackatsystems.miguardia.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLockSessionTest {
    @Test
    fun `new and existing installs start disabled without authentication`() {
        val session = AccessLockSession(FakeElapsedClock())

        session.installConfiguration(AccessLockConfiguration(), preserveAuthentication = false)
        session.activityEnteredForeground(deviceLocked = false)

        assertTrue(session.readyState().allowsSensitiveContent)
        assertFalse(session.readyState().configuration!!.enabled)
    }

    @Test
    fun `new process never restores an enabled authenticated session`() {
        val session = AccessLockSession(FakeElapsedClock())

        session.installConfiguration(
            AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES),
            preserveAuthentication = false,
        )
        session.activityEnteredForeground(deviceLocked = false)

        assertTrue(session.readyState().locked)
        assertFalse(session.readyState().allowsSensitiveContent)
    }

    @Test
    fun `one minute uses inclusive monotonic boundaries`() {
        assertTimeoutResult(AccessLockTimeout.ONE_MINUTE, elapsedMillis = 59_999L, locked = false)
        assertTimeoutResult(AccessLockTimeout.ONE_MINUTE, elapsedMillis = 60_000L, locked = true)
        assertTimeoutResult(AccessLockTimeout.ONE_MINUTE, elapsedMillis = 60_001L, locked = true)
    }

    @Test
    fun `five and fifteen minute boundaries are exact`() {
        assertTimeoutResult(AccessLockTimeout.FIVE_MINUTES, 299_999L, locked = false)
        assertTimeoutResult(AccessLockTimeout.FIVE_MINUTES, 300_000L, locked = true)
        assertTimeoutResult(AccessLockTimeout.FIFTEEN_MINUTES, 899_999L, locked = false)
        assertTimeoutResult(AccessLockTimeout.FIFTEEN_MINUTES, 900_000L, locked = true)
    }

    @Test
    fun `civil clock changes cannot extend a monotonic session`() {
        val elapsedClock = FakeElapsedClock(20_000L)
        var syntheticCivilTime = 1_900_000_000_000L
        val session = unlockedSession(AccessLockTimeout.ONE_MINUTE, elapsedClock)
        session.activityLeftForeground()

        syntheticCivilTime -= 20L * 365L * 24L * 60L * 60L * 1_000L
        elapsedClock.now += 60_000L
        session.activityEnteredForeground(deviceLocked = false)

        assertTrue(syntheticCivilTime < 1_900_000_000_000L)
        assertTrue(session.readyState().locked)
    }

    @Test
    fun `immediate locks when leaving foreground`() {
        val session = unlockedSession(AccessLockTimeout.IMMEDIATE, FakeElapsedClock())

        session.activityLeftForeground()

        assertTrue(session.readyState().locked)
        assertTrue(session.readyState().privacyCoverVisible)
    }

    @Test
    fun `device lock overrides a remaining timeout`() {
        val clock = FakeElapsedClock(10_000L)
        val session = unlockedSession(AccessLockTimeout.FIFTEEN_MINUTES, clock)
        session.activityLeftForeground()
        clock.now = 10_001L

        session.deviceLocked()
        session.activityEnteredForeground(deviceLocked = false)

        assertTrue(session.readyState().locked)
    }

    @Test
    fun `biometric dialog does not create an immediate auto lock cycle`() {
        val session = unlockedSession(AccessLockTimeout.IMMEDIATE, FakeElapsedClock())
        session.authenticationStarted()

        session.activityPausedForPrivacy()
        session.activityLeftForeground()
        session.activityEnteredForeground(deviceLocked = false)
        session.authenticationCancelled(session.currentSecurityGeneration)
        session.activityResumed(deviceLocked = false)

        assertFalse(session.readyState().locked)
        assertTrue(session.readyState().allowsSensitiveContent)
    }

    @Test
    fun `lock now closes without changing the configured timeout`() {
        val session = unlockedSession(AccessLockTimeout.FIVE_MINUTES, FakeElapsedClock())

        session.lockNow()

        assertTrue(session.readyState().locked)
        assertTrue(session.readyState().configuration!!.enabled)
        assertTrue(session.readyState().configuration!!.timeout == AccessLockTimeout.FIVE_MINUTES)
    }

    @Test
    fun `disabled configuration whose atomic write already began remains disabled after a boundary`() {
        val session = unlockedSession(AccessLockTimeout.ONE_MINUTE, FakeElapsedClock())
        val authorizedGeneration = session.currentSecurityGeneration

        session.deviceLocked()
        session.applyAuthenticatedConfiguration(
            AccessLockConfiguration(),
            authorizedGeneration,
        )

        assertFalse(session.readyState().locked)
        assertTrue(session.readyState().allowsSensitiveContent)
    }

    @Test
    fun `device lock does not create a gate when the feature is disabled`() {
        val session = AccessLockSession(FakeElapsedClock())
        session.installConfiguration(AccessLockConfiguration(), preserveAuthentication = false)
        session.activityEnteredForeground(deviceLocked = false)

        session.activityPausedForPrivacy()
        session.deviceLocked()
        session.activityResumed(deviceLocked = false)

        assertFalse(session.readyState().locked)
        assertTrue(session.readyState().allowsSensitiveContent)
    }

    private fun assertTimeoutResult(
        timeout: AccessLockTimeout,
        elapsedMillis: Long,
        locked: Boolean,
    ) {
        val clock = FakeElapsedClock(2_000L)
        val session = unlockedSession(timeout, clock)
        session.activityLeftForeground()
        clock.now += elapsedMillis
        session.activityEnteredForeground(deviceLocked = false)
        assertTrue("timeout=$timeout elapsed=$elapsedMillis", session.readyState().locked == locked)
    }

    private fun unlockedSession(timeout: AccessLockTimeout, clock: FakeElapsedClock): AccessLockSession =
        AccessLockSession(clock).also { session ->
            session.installConfiguration(
                AccessLockConfiguration(enabled = true, timeout = timeout),
                preserveAuthentication = false,
            )
            session.activityEnteredForeground(deviceLocked = false)
            session.authenticationStarted()
            session.authenticationSucceeded(session.currentSecurityGeneration)
        }

    private fun AccessLockSession.readyState(): AccessLockState = snapshot(
        phase = AccessLockPhase.READY,
        persistenceInProgress = false,
        message = null,
    )
}

internal class FakeElapsedClock(var now: Long = 0L) : ElapsedRealtimeClock {
    override fun nowMillis(): Long = now
}
