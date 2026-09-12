package com.blackatsystems.miguardia.security

import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AccessLockCoordinator(
    private val store: AccessLockPreferencesStore,
    private val scope: CoroutineScope,
    clock: ElapsedRealtimeClock = AndroidElapsedRealtimeClock,
) {
    private val session = AccessLockSession(clock)
    private val mutableState = MutableStateFlow(session.snapshot(
        AccessLockPhase.WAITING_FOR_RECOVERY,
        persistenceInProgress = false,
        message = null,
    ))
    val state: StateFlow<AccessLockState> = mutableState.asStateFlow()

    private val startedActivities = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    private var phase = AccessLockPhase.WAITING_FOR_RECOVERY
    private var initialized = false
    private var configurationTransition = false
    private var persistenceInProgress = false
    private var message: AccessLockMessage? = null
    private var nextAuthenticationToken = 1L
    private var activeAuthentication: ActiveAuthentication? = null

    @Synchronized
    fun activeAuthenticationToken(hostId: String): Long? =
        activeAuthentication?.takeIf { it.hostId == hostId }?.token

    val activeAuthenticationToken: Long?
        @Synchronized get() = activeAuthentication?.token

    suspend fun initializeAfterRecovery() {
        loadFromStore(preserveAuthentication = initialized)
    }

    fun retryStoreRead() {
        synchronized(this) {
            if (phase != AccessLockPhase.STORE_ERROR || persistenceInProgress) return
            phase = AccessLockPhase.LOADING
            message = null
            publish()
        }
        scope.launch { loadFromStore(preserveAuthentication = initialized) }
    }

    @Synchronized
    fun activityStarted(token: Any, deviceLocked: Boolean) {
        val wasEmpty = startedActivities.isEmpty()
        startedActivities += token
        if (wasEmpty) {
            session.activityEnteredForeground(deviceLocked)
            configurationTransition = false
            publish()
        }
    }

    @Synchronized
    fun activityResumed(deviceLocked: Boolean) {
        session.activityResumed(deviceLocked)
        publish()
    }

    @Synchronized
    fun activityPausedForPrivacy() {
        session.activityPausedForPrivacy()
        publish()
    }

    @Synchronized
    fun activityStopped(token: Any, changingConfigurations: Boolean) {
        startedActivities -= token
        if (startedActivities.isNotEmpty()) return
        if (changingConfigurations) {
            configurationTransition = true
            return
        }
        configurationTransition = false
        session.activityLeftForeground()
        publish()
    }

    @Synchronized
    fun deviceLocked() {
        activeAuthentication = null
        session.deviceLocked()
        publish()
    }

    @Synchronized
    fun secureCredentialUnavailable() {
        activeAuthentication = null
        session.secureCredentialUnavailable()
        message = AccessLockMessage.NO_SECURE_CREDENTIAL
        publish()
    }

    @Synchronized
    fun lockNow() {
        if (phase != AccessLockPhase.READY || persistenceInProgress) return
        activeAuthentication = null
        session.lockNow()
        message = null
        publish()
    }

    @Synchronized
    fun beginAuthentication(
        operation: AccessLockOperation,
        hostId: String = DefaultAuthenticationHost,
    ): Long? {
        if (!operationIsAllowed(operation) || activeAuthentication != null || persistenceInProgress) return null
        val token = nextAuthenticationToken++
        activeAuthentication = ActiveAuthentication(
            token = token,
            operation = operation,
            hostId = hostId,
            securityGeneration = session.currentSecurityGeneration,
        )
        message = null
        session.authenticationStarted()
        publish()
        return token
    }

    fun completeAuthentication(token: Long, result: DeviceAuthenticationResult) {
        val authenticatedOperation = synchronized(this) {
            val active = activeAuthentication
            if (active?.token != token) return
            activeAuthentication = null
            if (result !is DeviceAuthenticationResult.Success) {
                session.authenticationCancelled(active.securityGeneration)
                message = result.toMessage()
                publish()
                return
            }
            if (!session.authenticationSucceeded(active.securityGeneration)) {
                message = AccessLockMessage.SYSTEM_CANCELLED
                publish()
                return
            }
            when (active.operation) {
                AccessLockOperation.Unlock -> {
                    message = null
                    publish()
                    return
                }
                else -> {
                    persistenceInProgress = true
                    publish()
                    AuthenticatedPersistence(
                        operation = active.operation,
                        securityGeneration = active.securityGeneration,
                    )
                }
            }
        }
        scope.launch { persistAuthenticatedOperation(authenticatedOperation) }
    }

    @Synchronized
    fun abandonAuthentication(
        token: Long?,
        hostId: String = DefaultAuthenticationHost,
    ) {
        val active = activeAuthentication ?: return
        if (token == null || active.token != token || active.hostId != hostId) return
        activeAuthentication = null
        session.authenticationCancelled(active.securityGeneration)
        message = AccessLockMessage.SYSTEM_CANCELLED
        publish()
    }

    @Synchronized
    fun reportUnavailable(capability: DeviceAuthenticationCapability) {
        if (activeAuthentication != null || persistenceInProgress) return
        message = when (capability) {
            DeviceAuthenticationCapability.AVAILABLE -> AccessLockMessage.RECOVERABLE_ERROR
            DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL -> AccessLockMessage.NO_SECURE_CREDENTIAL
            DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE -> AccessLockMessage.HARDWARE_UNAVAILABLE
        }
        publish()
    }

    @Synchronized
    fun clearMessage() {
        message = null
        publish()
    }

    private suspend fun loadFromStore(preserveAuthentication: Boolean) {
        synchronized(this) {
            phase = AccessLockPhase.LOADING
            message = null
            publish()
        }
        when (val read = store.read()) {
            is AccessLockStoreRead.Ready -> synchronized(this) {
                session.installConfiguration(read.configuration, preserveAuthentication)
                phase = AccessLockPhase.READY
                initialized = true
                persistenceInProgress = false
                publish()
            }
            AccessLockStoreRead.Error -> synchronized(this) {
                phase = AccessLockPhase.STORE_ERROR
                persistenceInProgress = false
                publish()
            }
        }
    }

    private suspend fun persistAuthenticatedOperation(persistence: AuthenticatedPersistence) {
        val result = runCatching {
            val configuration = when (val operation = persistence.operation) {
                is AccessLockOperation.Activate -> AccessLockConfiguration(true, operation.timeout)
                is AccessLockOperation.ChangeTimeout -> AccessLockConfiguration(true, operation.timeout)
                AccessLockOperation.Disable,
                AccessLockOperation.RepairStore,
                -> AccessLockConfiguration()
                AccessLockOperation.Unlock -> error("Unlock is not a persistence operation")
            }
            PersistedConfiguration(
                configuration = configuration,
                applied = store.replaceIfAuthorized(configuration) {
                    persistenceAuthorizationIsCurrent(persistence.securityGeneration)
                },
            )
        }
        synchronized(this) {
            persistenceInProgress = false
            if (result.isSuccess) {
                val persisted = result.getOrThrow()
                if (persisted.applied) {
                    session.applyAuthenticatedConfiguration(
                        persisted.configuration,
                        persistence.securityGeneration,
                    )
                    phase = AccessLockPhase.READY
                    initialized = true
                    // The guarded DataStore transform is the point of no return. A
                    // security boundary before it aborts the write; one after it
                    // cannot retroactively revoke a configuration already committed.
                    message = null
                } else {
                    session.authenticationCancelled(persistence.securityGeneration)
                    message = AccessLockMessage.SYSTEM_CANCELLED
                }
            } else {
                session.authenticationCancelled(persistence.securityGeneration)
                message = AccessLockMessage.STORE_WRITE_ERROR
            }
            publish()
        }
    }

    private fun persistenceAuthorizationIsCurrent(expectedSecurityGeneration: Long): Boolean =
        synchronized(this) {
            persistenceInProgress && session.currentSecurityGeneration == expectedSecurityGeneration
        }

    @Synchronized
    private fun operationIsAllowed(operation: AccessLockOperation): Boolean {
        val current = mutableState.value
        return when (operation) {
            AccessLockOperation.Unlock -> phase == AccessLockPhase.READY && current.locked
            is AccessLockOperation.Activate ->
                phase == AccessLockPhase.READY && current.configuration?.enabled == false
            is AccessLockOperation.ChangeTimeout ->
                phase == AccessLockPhase.READY &&
                    current.configuration?.enabled == true &&
                    current.configuration.timeout != operation.timeout
            AccessLockOperation.Disable ->
                phase == AccessLockPhase.READY && current.configuration?.enabled == true
            AccessLockOperation.RepairStore -> phase == AccessLockPhase.STORE_ERROR
        }
    }

    private fun publish() {
        mutableState.value = session.snapshot(phase, persistenceInProgress, message)
    }

    private data class ActiveAuthentication(
        val token: Long,
        val operation: AccessLockOperation,
        val hostId: String,
        val securityGeneration: Long,
    )

    private data class AuthenticatedPersistence(
        val operation: AccessLockOperation,
        val securityGeneration: Long,
    )

    private data class PersistedConfiguration(
        val configuration: AccessLockConfiguration,
        val applied: Boolean,
    )

    private companion object {
        const val DefaultAuthenticationHost = "default-authentication-host"
    }
}

private fun DeviceAuthenticationResult.toMessage(): AccessLockMessage = when (this) {
    is DeviceAuthenticationResult.Success -> error("Success has no error message")
    DeviceAuthenticationResult.UserCancelled -> AccessLockMessage.USER_CANCELLED
    DeviceAuthenticationResult.SystemCancelled -> AccessLockMessage.SYSTEM_CANCELLED
    DeviceAuthenticationResult.Lockout -> AccessLockMessage.LOCKOUT
    DeviceAuthenticationResult.HardwareUnavailable -> AccessLockMessage.HARDWARE_UNAVAILABLE
    DeviceAuthenticationResult.BiometricNotEnrolled -> AccessLockMessage.BIOMETRIC_NOT_ENROLLED
    DeviceAuthenticationResult.NoSecureCredential -> AccessLockMessage.NO_SECURE_CREDENTIAL
    DeviceAuthenticationResult.Unsupported -> AccessLockMessage.UNSUPPORTED
    DeviceAuthenticationResult.RecoverableError -> AccessLockMessage.RECOVERABLE_ERROR
    DeviceAuthenticationResult.FinalError -> AccessLockMessage.FINAL_ERROR
}
