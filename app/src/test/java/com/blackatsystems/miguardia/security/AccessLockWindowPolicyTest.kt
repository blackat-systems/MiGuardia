package com.blackatsystems.miguardia.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLockWindowPolicyTest {
    private val authenticated = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
        locked = false,
        privacyCoverVisible = false,
    )
    private val locked = authenticated.copy(locked = true)
    private val disabled = authenticated.copy(configuration = AccessLockConfiguration())

    @Test
    fun `api 26 through 32 secure background but allow foreground capture after authentication`() {
        listOf(26, 29, 32).forEach { sdk ->
            val foreground = accessLockWindowPolicy(sdk, authenticated, inForeground = true)
            val background = accessLockWindowPolicy(sdk, authenticated, inForeground = false)

            assertNull(foreground.recentsScreenshotsEnabled)
            assertFalse(foreground.secureWindow)
            assertTrue(background.secureWindow)
        }
    }

    @Test
    fun `api 33 plus disables only recents while enabled and secures the closed gate`() {
        val authenticatedForeground = accessLockWindowPolicy(33, authenticated, inForeground = true)
        val lockedForeground = accessLockWindowPolicy(36, locked, inForeground = true)

        assertFalse(authenticatedForeground.recentsScreenshotsEnabled!!)
        assertFalse(authenticatedForeground.secureWindow)
        assertFalse(lockedForeground.recentsScreenshotsEnabled!!)
        assertTrue(lockedForeground.secureWindow)
    }

    @Test
    fun `disabled feature restores normal recents and screenshots`() {
        val policy = accessLockWindowPolicy(33, disabled, inForeground = true)

        assertTrue(policy.recentsScreenshotsEnabled!!)
        assertFalse(policy.secureWindow)
    }
}
