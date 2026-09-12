package com.blackatsystems.miguardia.security

internal class AccessLockSession(
    private val clock: ElapsedRealtimeClock,
) {
    private var configuration: AccessLockConfiguration? = null
    private var authenticated = false
    private var privacyCover = true
    private var authenticationInProgress = false
    private var backgroundAtMillis: Long? = null
    private var deviceLockedWhileAway = false
    private var inForeground = false
    private var securityGeneration = 0L

    val currentSecurityGeneration: Long
        get() = securityGeneration

    fun installConfiguration(value: AccessLockConfiguration, preserveAuthentication: Boolean) {
        val wasAuthenticated = authenticated && configuration?.enabled == true
        configuration = value
        authenticated = when {
            !value.enabled -> true
            preserveAuthentication && wasAuthenticated -> true
            else -> false
        }
        privacyCover = value.enabled && (!inForeground || !authenticated)
        backgroundAtMillis = null
        deviceLockedWhileAway = false
    }

    fun authenticationStarted() {
        authenticationInProgress = true
    }

    fun authenticationCancelled(expectedSecurityGeneration: Long) {
        authenticationInProgress = false
        privacyCover = configuration?.enabled == true && (!inForeground || !authenticated)
    }

    fun authenticationSucceeded(expectedSecurityGeneration: Long): Boolean {
        authenticationInProgress = false
        if (expectedSecurityGeneration != securityGeneration) {
            privacyCover = configuration?.enabled == true
            return false
        }
        authenticated = true
        backgroundAtMillis = null
        deviceLockedWhileAway = false
        privacyCover = configuration?.enabled == true && !inForeground
        return true
    }

    fun applyAuthenticatedConfiguration(
        value: AccessLockConfiguration,
        expectedSecurityGeneration: Long,
    ) {
        val authenticationStillValid =
            authenticated && expectedSecurityGeneration == securityGeneration
        configuration = value
        authenticationInProgress = false
        authenticated = !value.enabled || authenticationStillValid
        if (!value.enabled) {
            backgroundAtMillis = null
            deviceLockedWhileAway = false
        } else if (inForeground) {
            backgroundAtMillis = null
            deviceLockedWhileAway = false
        } else {
            val startedAt = backgroundAtMillis ?: clock.nowMillis().also {
                backgroundAtMillis = it
            }
            val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)
            if (elapsed >= value.timeout.durationMillis) authenticated = false
        }
        privacyCover = value.enabled && (!inForeground || !authenticated)
    }

    fun activityEnteredForeground(deviceLocked: Boolean) {
        inForeground = true
        val current = configuration
        if (current == null) {
            privacyCover = true
            return
        }
        if (!current.enabled) {
            authenticated = true
            privacyCover = false
            backgroundAtMillis = null
            deviceLockedWhileAway = false
            return
        }
        if (!authenticationInProgress) {
            val elapsed = backgroundAtMillis?.let { start ->
                (clock.nowMillis() - start).coerceAtLeast(0L)
            }
            if (deviceLocked || deviceLockedWhileAway ||
                (elapsed != null && elapsed >= current.timeout.durationMillis)
            ) {
                authenticated = false
            }
            backgroundAtMillis = null
            deviceLockedWhileAway = false
        }
        privacyCover = authenticationInProgress || !authenticated
    }

    fun activityResumed(deviceLocked: Boolean) {
        if (deviceLocked) deviceLocked()
        if (configuration?.enabled != true) {
            authenticated = true
            privacyCover = false
        } else if (!authenticationInProgress) {
            privacyCover = !authenticated
        }
    }

    fun activityPausedForPrivacy() {
        if (configuration?.enabled == true) privacyCover = true
    }

    fun activityLeftForeground() {
        inForeground = false
        val current = configuration ?: return
        if (!current.enabled) {
            return
        }
        backgroundAtMillis = clock.nowMillis()
        privacyCover = true
        if (!authenticationInProgress && current.timeout == AccessLockTimeout.IMMEDIATE) {
            authenticated = false
        }
    }

    fun deviceLocked() {
        invalidateAuthentication()
        deviceLockedWhileAway = true
        if (configuration?.enabled == true) {
            authenticated = false
            privacyCover = true
        }
    }

    fun secureCredentialUnavailable() {
        invalidateAuthentication()
        if (configuration?.enabled == true) {
            authenticated = false
            privacyCover = true
            backgroundAtMillis = null
        }
    }

    fun lockNow() {
        if (configuration?.enabled == true) {
            invalidateAuthentication()
            authenticated = false
            privacyCover = false
            backgroundAtMillis = null
        }
    }

    fun snapshot(
        phase: AccessLockPhase,
        persistenceInProgress: Boolean,
        message: AccessLockMessage?,
    ): AccessLockState = AccessLockState(
        phase = phase,
        configuration = configuration,
        locked = configuration?.let { it.enabled && !authenticated } ?: true,
        privacyCoverVisible = privacyCover,
        authenticationInProgress = authenticationInProgress,
        persistenceInProgress = persistenceInProgress,
        message = message,
    )

    private fun invalidateAuthentication() {
        securityGeneration++
        authenticationInProgress = false
    }
}
