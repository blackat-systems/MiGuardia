package com.blackatsystems.miguardia.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

internal class DeviceLockMonitor(
    context: Context,
    private val onDeviceLocked: () -> Unit,
) : DisplayManager.DisplayListener {
    private val displayManager = context.applicationContext
        .getSystemService(DisplayManager::class.java)
    private val keyguardManager = context.applicationContext
        .getSystemService(KeyguardManager::class.java)

    fun start() {
        displayManager?.registerDisplayListener(this, null)
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    @Suppress("DEPRECATION")
    override fun onDisplayChanged(displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY) return
        if (keyguardManager?.isDeviceLocked == true) onDeviceLocked()
    }
}
