package com.blackatsystems.miguardia.support

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

internal const val SUPPORT_URL = "https://blackatsystems.com/miguardia/apoyar"

/** Builds a fresh intent; no incoming intent or user data crosses this boundary. */
internal fun openSupportLink(startActivity: (Intent) -> Unit): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, SUPPORT_URL.toUri()))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** One in-flight tap, confined to the UI coroutine; nothing survives its composition. */
internal class SupportMenuAction {
    private var opening = false
    private var generation = Any()

    fun cancelPendingOpen() {
        generation = Any()
    }

    suspend fun open(
        closeMenu: suspend () -> Unit,
        isAllowed: () -> Boolean,
        openLink: () -> Unit,
    ) {
        if (opening) return
        opening = true
        val requestedGeneration = generation
        try {
            closeMenu()
            if (requestedGeneration === generation && isAllowed()) openLink()
        } finally {
            opening = false
        }
    }
}
