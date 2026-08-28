package com.blackatsystems.miguardia.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundary
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import java.time.Instant
import java.util.UUID

internal class AndroidShiftAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(boundary: NotificationBoundary, preferExact: Boolean): Boolean {
        val triggerAt = boundary.identity.triggerAt.toEpochMilli()
        val pendingIntent = pendingIntent(boundary.identity.opaqueKey)
        val canUseExact = shouldScheduleExactAlarm(
            preferExact = preferExact,
            exactAlarmAccessGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms(),
        )
        if (canUseExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
                return true
            } catch (_: SecurityException) {
                // The user can revoke special access between the capability check and this call.
            } catch (_: IllegalStateException) {
                // AlarmManager has a device-configurable per-UID quota.
                return false
            }
        }
        return try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun cancel(opaqueKey: String) {
        alarmManager.cancel(pendingIntent(opaqueKey))
    }

    private fun pendingIntent(opaqueKey: String): PendingIntent {
        val intent = Intent(context, ShiftAlarmReceiver::class.java)
            .setAction(ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("shift-alarm")
                    .appendQueryParameter(KEY_BOUNDARY, opaqueKey)
                    .build(),
            )
        return PendingIntent.getBroadcast(
            context,
            opaqueKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val KEY_BOUNDARY = "boundary"

        fun readIdentity(intent: Intent?): NotificationBoundaryIdentity? {
            if (intent?.action != ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY) return null
            val parts = intent.data?.getQueryParameter(KEY_BOUNDARY)?.split('|') ?: return null
            return when {
                parts.size == 5 && parts[0] == "v2" -> runCatching {
                    NotificationBoundaryIdentity(
                        eventIdentity = requireNotNull(NextEventIdentity.parseTrackingKey(parts[1])),
                        type = NotificationBoundaryType.valueOf(parts[2]),
                        triggerAt = Instant.ofEpochMilli(parts[3].toLong()),
                        leadMinutes = parts[4].toLong().takeIf { it > 0L },
                    )
                }.getOrNull()
                parts.size == 4 -> runCatching {
                    NotificationBoundaryIdentity(
                        shiftId = UUID.fromString(parts[0]),
                        type = NotificationBoundaryType.valueOf(parts[1]),
                        triggerAt = Instant.ofEpochMilli(parts[2].toLong()),
                        leadMinutes = parts[3].toLong().takeIf { it > 0L },
                    )
                }.getOrNull()
                else -> null
            }
        }
    }
}

internal fun shouldScheduleExactAlarm(
    preferExact: Boolean,
    exactAlarmAccessGranted: Boolean,
): Boolean = preferExact && exactAlarmAccessGranted
