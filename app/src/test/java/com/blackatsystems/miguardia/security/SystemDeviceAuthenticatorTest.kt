package com.blackatsystems.miguardia.security

import android.app.Activity
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemDeviceAuthenticatorTest {
    @Test
    fun `legacy credential result accepts only the system confirmation success`() {
        assertEquals(
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
            translateLegacyCredentialResult(Activity.RESULT_OK),
        )
        assertEquals(
            DeviceAuthenticationResult.UserCancelled,
            translateLegacyCredentialResult(Activity.RESULT_CANCELED),
        )
    }

    @Test
    fun `api matrix never requests the unsupported old strong plus credential combination`() {
        assertEquals(
            DeviceAuthenticationApiPath.LEGACY_STRONG_THEN_CREDENTIAL,
            deviceAuthenticationApiPath(26),
        )
        assertEquals(
            DeviceAuthenticationApiPath.LEGACY_STRONG_THEN_CREDENTIAL,
            deviceAuthenticationApiPath(29),
        )
        assertEquals(
            DeviceAuthenticationApiPath.MODERN_STRONG_OR_CREDENTIAL,
            deviceAuthenticationApiPath(30),
        )
        assertEquals(
            DeviceAuthenticationApiPath.MODERN_STRONG_OR_CREDENTIAL,
            deviceAuthenticationApiPath(33),
        )
    }

    @Test
    fun `recreated legacy host restores credential fallback only for its active token`() {
        assertEquals(true, shouldRestoreLegacyCredentialFallback(26, 41L))
        assertEquals(true, shouldRestoreLegacyCredentialFallback(29, 41L))
        assertEquals(false, shouldRestoreLegacyCredentialFallback(29, null))
        assertEquals(false, shouldRestoreLegacyCredentialFallback(30, 41L))
        assertEquals(false, shouldRestoreLegacyCredentialFallback(36, 41L))
    }

    @Test
    fun `system and user cancellation remain distinct`() {
        assertEquals(
            DeviceAuthenticationResult.UserCancelled,
            SystemDeviceAuthenticator.translateBiometricError(BiometricPrompt.ERROR_USER_CANCELED),
        )
        assertEquals(
            DeviceAuthenticationResult.SystemCancelled,
            SystemDeviceAuthenticator.translateBiometricError(BiometricPrompt.ERROR_CANCELED),
        )
    }

    @Test
    fun `lockout hardware enrolment credential and recoverable errors never become success`() {
        val cases = mapOf(
            BiometricPrompt.ERROR_LOCKOUT to DeviceAuthenticationResult.Lockout,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT to DeviceAuthenticationResult.Lockout,
            BiometricPrompt.ERROR_HW_UNAVAILABLE to DeviceAuthenticationResult.HardwareUnavailable,
            BiometricPrompt.ERROR_HW_NOT_PRESENT to DeviceAuthenticationResult.HardwareUnavailable,
            BiometricPrompt.ERROR_NO_BIOMETRICS to DeviceAuthenticationResult.BiometricNotEnrolled,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL to DeviceAuthenticationResult.NoSecureCredential,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED to DeviceAuthenticationResult.Unsupported,
            BiometricPrompt.ERROR_TIMEOUT to DeviceAuthenticationResult.RecoverableError,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS to DeviceAuthenticationResult.RecoverableError,
            9_999 to DeviceAuthenticationResult.FinalError,
        )

        cases.forEach { (code, expected) ->
            assertEquals(expected, SystemDeviceAuthenticator.translateBiometricError(code))
        }
    }
}
