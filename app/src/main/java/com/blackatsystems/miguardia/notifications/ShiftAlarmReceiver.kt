package com.blackatsystems.miguardia.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShiftAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? MiGuardiaApplication ?: return
        val dismissedShiftId = readDismissedShiftId(intent)
        val identity = if (dismissedShiftId == null) {
            AndroidShiftAlarmScheduler.readIdentity(intent) ?: return
        } else {
            null
        }
        val pendingResult = goAsync()
        application.notificationRuntime.scope.launch {
            try {
                if (dismissedShiftId != null) {
                    application.notificationRuntime.dismissNow(dismissedShiftId.toString())
                    return@launch
                }
                checkNotNull(identity)
                val presenter = ShiftNotificationPresenter(application)
                val preferences = application.notificationPreferences.current()
                val dataStore = application.localDataStore
                val shift = dataStore.shifts.getById(identity.shiftId) ?: run {
                    presenter.cancel(identity.shiftId.toString())
                    application.notificationPreferences.clearShiftTracking(identity.shiftId.toString())
                    return@launch
                }
                val now = Instant.now()
                if (now < identity.triggerAt) return@launch
                if (identity.type == NotificationBoundaryType.END) {
                    if (identity.triggerAt == shift.endAt) {
                        presenter.cancel(identity.shiftId.toString())
                        application.notificationPreferences.clearShiftTracking(identity.shiftId.toString())
                    }
                    return@launch
                }
                if (!preferences.enabled || !NotificationSystemAccess(application).read().notificationPermissionGranted) {
                    presenter.cancel(identity.shiftId.toString())
                    application.notificationPreferences.clearShiftTracking(identity.shiftId.toString())
                    return@launch
                }
                val vacations = dataStore.vacations
                    .observeEndingOnOrAfter(shift.localStartDate)
                    .first()
                if (!shift.isEligibleUpcomingWork(now, vacations)) {
                    presenter.cancel(identity.shiftId.toString())
                    application.notificationPreferences.clearShiftTracking(identity.shiftId.toString())
                    return@launch
                }
                val override = dataStore.shiftNotificationConfigs.getForShift(shift.id)
                if (!identity.isStillCurrent(shift.startAt, shift.endAt, preferences, override)) {
                    return@launch
                }
                presenter.show(shift, now, preferences)
                application.notificationPreferences.markDisplayed(shift.id.toString())
            } finally {
                try {
                    application.notificationRuntime.reconcileNow()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DELIVER_BOUNDARY = "com.blackatsystems.miguardia.action.DELIVER_SHIFT_BOUNDARY"
        const val ACTION_NOTIFICATION_DISMISSED =
            "com.blackatsystems.miguardia.action.SHIFT_NOTIFICATION_DISMISSED"
    }
}

private fun readDismissedShiftId(intent: Intent): UUID? {
    if (intent.action != ShiftAlarmReceiver.ACTION_NOTIFICATION_DISMISSED) return null
    if (intent.data?.scheme != "miguardia" || intent.data?.authority != "notification-dismissed") return null
    return intent.data?.lastPathSegment
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

private fun com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity.isStillCurrent(
    startAt: Instant,
    endAt: Instant,
    preferences: NotificationPreferences,
    override: ShiftNotificationConfig?,
): Boolean = when (type) {
    NotificationBoundaryType.REMINDER -> {
        val lead = leadMinutes
        val configured = override?.reminderLeadMinutes ?: preferences.globalReminderLeadMinutes
        lead != null &&
            override?.reminderLeadMinutes?.isEmpty() != true &&
            lead in configured &&
            triggerAt == startAt.minusSeconds(lead * 60L)
    }
    NotificationBoundaryType.START ->
        override?.reminderLeadMinutes?.isEmpty() != true && triggerAt == startAt
    NotificationBoundaryType.END -> triggerAt == endAt
}
