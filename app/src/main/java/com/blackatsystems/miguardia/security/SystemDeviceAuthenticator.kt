package com.blackatsystems.miguardia.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal interface DeviceAuthenticator {
    fun capability(): DeviceAuthenticationCapability
    fun deviceIsLocked(): Boolean
    fun attachToInFlightAuthentication(token: Long?)
    fun attachedAuthenticationToken(): Long?
    fun cancelAuthentication()
    fun authenticate(token: Long)
    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean
    fun openDeviceSecuritySettings(): Boolean
}

internal enum class DeviceAuthenticationApiPath {
    LEGACY_STRONG_THEN_CREDENTIAL,
    MODERN_STRONG_OR_CREDENTIAL,
}

internal fun deviceAuthenticationApiPath(sdkInt: Int): DeviceAuthenticationApiPath =
    if (sdkInt >= 30) {
        DeviceAuthenticationApiPath.MODERN_STRONG_OR_CREDENTIAL
    } else {
        DeviceAuthenticationApiPath.LEGACY_STRONG_THEN_CREDENTIAL
    }

internal fun shouldRestoreLegacyCredentialFallback(sdkInt: Int, token: Long?): Boolean =
    token != null &&
        deviceAuthenticationApiPath(sdkInt) == DeviceAuthenticationApiPath.LEGACY_STRONG_THEN_CREDENTIAL

internal class SystemDeviceAuthenticator(
    private val activity: FragmentActivity,
    private val onResult: (Long, DeviceAuthenticationResult) -> Unit,
) : DeviceAuthenticator {
    private val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
    private val biometricManager = BiometricManager.from(activity)
    private var pendingToken: Long? = null
    private var legacyCredentialFallback = false

    private val biometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val source = if (
                    result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
                ) {
                    DeviceAuthenticationSource.DEVICE_CREDENTIAL
                } else {
                    DeviceAuthenticationSource.STRONG_BIOMETRIC
                }
                completeOnce(DeviceAuthenticationResult.Success(source))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (legacyCredentialFallback && errorCode in LegacyCredentialFallbackErrors) {
                    launchCredentialFallback()
                    return
                }
                completeOnce(translateBiometricError(errorCode))
            }
        },
    )

    override fun capability(): DeviceAuthenticationCapability = when {
        keyguardManager?.isDeviceSecure != true -> DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL
        else -> DeviceAuthenticationCapability.AVAILABLE
    }

    override fun deviceIsLocked(): Boolean = keyguardManager?.isDeviceLocked == true

    override fun attachToInFlightAuthentication(token: Long?) {
        pendingToken = token
        legacyCredentialFallback = shouldRestoreLegacyCredentialFallback(Build.VERSION.SDK_INT, token)
    }

    override fun attachedAuthenticationToken(): Long? = pendingToken

    override fun cancelAuthentication() {
        biometricPrompt.cancelAuthentication()
    }

    override fun authenticate(token: Long) {
        if (pendingToken != null) return
        pendingToken = token
        if (capability() != DeviceAuthenticationCapability.AVAILABLE) {
            completeOnce(DeviceAuthenticationResult.NoSecureCredential)
            return
        }
        if (deviceAuthenticationApiPath(Build.VERSION.SDK_INT) ==
            DeviceAuthenticationApiPath.MODERN_STRONG_OR_CREDENTIAL
        ) {
            legacyCredentialFallback = false
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear MiGuardia")
                .setSubtitle("Usá la biometría fuerte o el bloqueo seguro del teléfono")
                .setAllowedAuthenticators(authenticators)
                .build()
            biometricPrompt.authenticate(promptInfo)
            return
        }
        authenticateOnLegacyApi()
    }

    override fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != LEGACY_CREDENTIAL_REQUEST_CODE || pendingToken == null) return false
        completeOnce(translateLegacyCredentialResult(resultCode))
        return true
    }

    override fun openDeviceSecuritySettings(): Boolean {
        val primary = Intent(Settings.ACTION_SECURITY_SETTINGS)
        val fallback = Intent(Settings.ACTION_SETTINGS)
        return runCatching { activity.startActivity(primary) }
            .recoverCatching { activity.startActivity(fallback) }
            .isSuccess
    }

    private fun authenticateOnLegacyApi() {
        val strongStatus = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS ||
            strongStatus == BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        ) {
            legacyCredentialFallback = true
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear MiGuardia")
                .setSubtitle("Usá una biometría fuerte")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Usar PIN, patrón o contraseña")
                .build()
            biometricPrompt.authenticate(promptInfo)
        } else {
            launchCredentialFallback()
        }
    }

    @Suppress("DEPRECATION")
    private fun launchCredentialFallback() {
        legacyCredentialFallback = false
        if (keyguardManager?.isDeviceSecure != true) {
            completeOnce(DeviceAuthenticationResult.NoSecureCredential)
            return
        }
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Desbloquear MiGuardia",
            "Confirmá el PIN, patrón o contraseña del teléfono.",
        )
        if (intent == null) {
            completeOnce(DeviceAuthenticationResult.Unsupported)
        } else {
            activity.startActivityForResult(intent, LEGACY_CREDENTIAL_REQUEST_CODE)
        }
    }

    private fun completeOnce(result: DeviceAuthenticationResult) {
        val token = pendingToken ?: return
        pendingToken = null
        legacyCredentialFallback = false
        onResult(token, result)
    }

    internal companion object {
        private const val LEGACY_CREDENTIAL_REQUEST_CODE = 0x4D47
        private val LegacyCredentialFallbackErrors = setOf(
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        )

        fun translateBiometricError(errorCode: Int): DeviceAuthenticationResult = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            -> DeviceAuthenticationResult.UserCancelled

            BiometricPrompt.ERROR_CANCELED -> DeviceAuthenticationResult.SystemCancelled
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            -> DeviceAuthenticationResult.Lockout

            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            -> DeviceAuthenticationResult.HardwareUnavailable

            BiometricPrompt.ERROR_NO_BIOMETRICS -> DeviceAuthenticationResult.BiometricNotEnrolled
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> DeviceAuthenticationResult.NoSecureCredential
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> DeviceAuthenticationResult.Unsupported
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_VENDOR,
            -> DeviceAuthenticationResult.RecoverableError

            else -> DeviceAuthenticationResult.FinalError
        }
    }
}

internal fun Context.hasSecureDeviceCredential(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

internal fun translateLegacyCredentialResult(resultCode: Int): DeviceAuthenticationResult =
    if (resultCode == Activity.RESULT_OK) {
        DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL)
    } else {
        DeviceAuthenticationResult.UserCancelled
    }
