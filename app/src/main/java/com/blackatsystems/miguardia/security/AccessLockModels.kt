package com.blackatsystems.miguardia.security

import android.os.SystemClock

enum class AccessLockTimeout(val durationMillis: Long) {
    IMMEDIATE(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5L * 60_000L),
    FIFTEEN_MINUTES(15L * 60_000L),
}

data class AccessLockConfiguration(
    val enabled: Boolean = false,
    val timeout: AccessLockTimeout = AccessLockTimeout.IMMEDIATE,
)

internal fun interface ElapsedRealtimeClock {
    fun nowMillis(): Long
}

internal object AndroidElapsedRealtimeClock : ElapsedRealtimeClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

enum class AccessLockPhase {
    WAITING_FOR_RECOVERY,
    LOADING,
    READY,
    STORE_ERROR,
}

enum class AccessLockMessage {
    USER_CANCELLED,
    SYSTEM_CANCELLED,
    LOCKOUT,
    HARDWARE_UNAVAILABLE,
    BIOMETRIC_NOT_ENROLLED,
    NO_SECURE_CREDENTIAL,
    UNSUPPORTED,
    RECOVERABLE_ERROR,
    FINAL_ERROR,
    STORE_WRITE_ERROR,
}

data class AccessLockState(
    val phase: AccessLockPhase = AccessLockPhase.WAITING_FOR_RECOVERY,
    val configuration: AccessLockConfiguration? = null,
    val locked: Boolean = true,
    val privacyCoverVisible: Boolean = true,
    val authenticationInProgress: Boolean = false,
    val persistenceInProgress: Boolean = false,
    val message: AccessLockMessage? = null,
) {
    val allowsSensitiveContent: Boolean
        get() = phase == AccessLockPhase.READY &&
            configuration != null &&
            !locked &&
            !privacyCoverVisible

    val protectionRequired: Boolean
        get() = phase != AccessLockPhase.READY || configuration?.enabled != false || locked
}

internal sealed interface AccessLockOperation {
    data object Unlock : AccessLockOperation
    data class Activate(val timeout: AccessLockTimeout) : AccessLockOperation
    data class ChangeTimeout(val timeout: AccessLockTimeout) : AccessLockOperation
    data object Disable : AccessLockOperation
    data object RepairStore : AccessLockOperation
}

internal enum class DeviceAuthenticationSource {
    STRONG_BIOMETRIC,
    DEVICE_CREDENTIAL,
}

internal sealed interface DeviceAuthenticationResult {
    data class Success(val source: DeviceAuthenticationSource) : DeviceAuthenticationResult
    data object UserCancelled : DeviceAuthenticationResult
    data object SystemCancelled : DeviceAuthenticationResult
    data object Lockout : DeviceAuthenticationResult
    data object HardwareUnavailable : DeviceAuthenticationResult
    data object BiometricNotEnrolled : DeviceAuthenticationResult
    data object NoSecureCredential : DeviceAuthenticationResult
    data object Unsupported : DeviceAuthenticationResult
    data object RecoverableError : DeviceAuthenticationResult
    data object FinalError : DeviceAuthenticationResult
}

enum class DeviceAuthenticationCapability {
    AVAILABLE,
    NO_SECURE_CREDENTIAL,
    TEMPORARILY_UNAVAILABLE,
}
