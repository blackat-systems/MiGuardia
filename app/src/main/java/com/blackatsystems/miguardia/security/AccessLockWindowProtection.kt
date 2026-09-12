package com.blackatsystems.miguardia.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

internal object AccessLockWindowProtection {
    fun applyForeground(activity: Activity, state: AccessLockState) {
        val policy = accessLockWindowPolicy(Build.VERSION.SDK_INT, state, inForeground = true)
        configureRecents(activity, policy)
        if (!policy.secureWindow) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun protectForBackground(activity: Activity, state: AccessLockState) {
        val policy = accessLockWindowPolicy(Build.VERSION.SDK_INT, state, inForeground = false)
        configureRecents(activity, policy)
        if (policy.secureWindow) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun configureRecents(activity: Activity, policy: AccessLockWindowPolicy) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            policy.recentsScreenshotsEnabled != null
        ) {
            activity.setRecentsScreenshotEnabled(policy.recentsScreenshotsEnabled)
        }
    }
}

internal data class AccessLockWindowPolicy(
    val recentsScreenshotsEnabled: Boolean?,
    val secureWindow: Boolean,
)

internal fun accessLockWindowPolicy(
    sdkInt: Int,
    state: AccessLockState,
    inForeground: Boolean,
): AccessLockWindowPolicy = AccessLockWindowPolicy(
    recentsScreenshotsEnabled = if (sdkInt >= 33) !state.protectionRequired else null,
    secureWindow = !state.allowsSensitiveContent || (!inForeground && state.protectionRequired),
)
