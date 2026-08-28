package com.blackatsystems.miguardia.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ShiftAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? MiGuardiaApplication ?: return
        val dismissedEventKey = readDismissedEventKey(intent)
        val identity = if (dismissedEventKey == null) {
            AndroidShiftAlarmScheduler.readIdentity(intent) ?: return
        } else {
            null
        }
        val pendingResult = goAsync()
        application.notificationRuntime.scope.launch {
            try {
                withTimeoutOrNull(RECEIVER_WORK_TIMEOUT_MILLIS) {
                    runNotificationOperation {
                        if (dismissedEventKey != null) {
                            application.notificationRuntime.dismissNow(dismissedEventKey)
                        } else {
                            application.notificationRuntime.deliverNow(checkNotNull(identity))
                        }
                    }
                    runNotificationOperation {
                        application.notificationRuntime.reconcileNow()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELIVER_BOUNDARY = "com.blackatsystems.miguardia.action.DELIVER_SHIFT_BOUNDARY"
        const val ACTION_NOTIFICATION_DISMISSED =
            "com.blackatsystems.miguardia.action.SHIFT_NOTIFICATION_DISMISSED"
        private const val RECEIVER_WORK_TIMEOUT_MILLIS = 8_000L
    }
}

private fun readDismissedEventKey(intent: Intent): String? {
    if (intent.action != ShiftAlarmReceiver.ACTION_NOTIFICATION_DISMISSED) return null
    if (intent.data?.scheme != "miguardia" || intent.data?.authority != "notification-dismissed") return null
    val encoded = intent.data?.getQueryParameter("event") ?: intent.data?.lastPathSegment
    return encoded
        ?.let(NextEventIdentity::parseTrackingKey)
        ?.trackingKey
}
