package com.blackatsystems.miguardia.notifications

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class NotificationSystemAccessState(
    val notificationPermissionGranted: Boolean,
    val exactAlarmAccessGranted: Boolean,
    val appNotificationsEnabled: Boolean = notificationPermissionGranted,
) {
    val notificationAccessGranted: Boolean
        get() = notificationAccessGranted(
            runtimePermissionGranted = notificationPermissionGranted,
            appNotificationsEnabled = appNotificationsEnabled,
        )
}

class NotificationSystemAccess(private val context: Context) {
    fun read(): NotificationSystemAccessState {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return NotificationSystemAccessState(
            notificationPermissionGranted = runtimePermissionGranted,
            exactAlarmAccessGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }
}

internal fun notificationAccessGranted(
    runtimePermissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
): Boolean = runtimePermissionGranted && appNotificationsEnabled
