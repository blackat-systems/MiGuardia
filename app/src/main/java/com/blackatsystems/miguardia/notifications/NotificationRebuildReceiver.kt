package com.blackatsystems.miguardia.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackatsystems.miguardia.MiGuardiaApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class NotificationRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in AllowedActions) return
        val application = context.applicationContext as? MiGuardiaApplication ?: return
        if (
            intent.action == ACTION_EXACT_ALARM_ACCESS_CHANGED &&
            !NotificationSystemAccess(application).read().exactAlarmAccessGranted
        ) {
            return
        }
        val pendingResult = goAsync()
        application.notificationRuntime.scope.launch {
            try {
                withTimeoutOrNull(RECEIVER_WORK_TIMEOUT_MILLIS) {
                    runNotificationOperation {
                        application.notificationRuntime.rebuildNow()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val AllowedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_ACCESS_CHANGED,
        )
        const val ACTION_EXACT_ALARM_ACCESS_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val RECEIVER_WORK_TIMEOUT_MILLIS = 8_000L
    }
}
