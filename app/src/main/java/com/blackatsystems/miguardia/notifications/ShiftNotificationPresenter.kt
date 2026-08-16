package com.blackatsystems.miguardia.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.model.Shift
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class ShiftNotificationPresenter(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun show(
        shift: Shift,
        now: Instant,
        preferences: NotificationPreferences,
        weatherText: String? = null,
        silentUpdate: Boolean = false,
    ) {
        val ongoing = now >= shift.startAt
        val channelId = ensureChannel(preferences.soundUri)
        val entryTime = shift.startTimeSnapshot.format(TimeFormatter)
        val title = if (ongoing) "Guardia en curso" else "Entrás a las $entryTime"
        val timeRange = "${shift.startTimeSnapshot.format(TimeFormatter)}–${shift.endTimeSnapshot.format(TimeFormatter)}"
        val fullText = buildString {
            append(shift.objectiveNameSnapshot)
            append(" (")
            append(shift.objectiveAbbreviationSnapshot)
            append(") · ")
            append("Horario ")
            append(timeRange)
            shift.position?.takeIf(String::isNotBlank)?.let { append(" · Puesto: ").append(it) }
            weatherText
                ?.takeIf { preferences.privacy == NotificationPrivacy.COMPLETE }
                ?.let { append('\n').append(it) }
        }
        val reducedText = "$title · $timeRange"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(shift.colorArgbSnapshot)
            .setContentTitle(title)
            .setContentText(fullText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setShowWhen(true)
            .setWhen((if (ongoing) shift.endAt else shift.startAt).toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(silentUpdate)
            .setOngoing(preferences.persistentWhileActive)
            .setAutoCancel(!preferences.persistentWhileActive)
            .setContentIntent(actionIntent(MainActivity.ACTION_VIEW_SHIFT, shift, now))
            .setDeleteIntent(dismissIntent(shift))
            .addAction(secureAction("Ver detalles", MainActivity.ACTION_VIEW_SHIFT, shift, now))
            .addAction(secureAction("Cómo llegar", MainActivity.ACTION_DIRECTIONS, shift, now))
            .addAction(secureAction("Informar novedad", MainActivity.ACTION_REPORT_NOVELTY, shift, now))
        if (silentUpdate) builder.setSilent(true)
        when (preferences.privacy) {
            NotificationPrivacy.COMPLETE -> builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            NotificationPrivacy.REDUCED -> builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(channelId, title, reducedText))
            NotificationPrivacy.HIDDEN -> builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(channelId, "MiGuardia", "Tenés un aviso de guardia."))
        }
        notifySafely(shift.id.toString(), NOTIFICATION_ID, builder.build())
    }

    fun cancel(shiftId: String) {
        manager.cancel(shiftId, NOTIFICATION_ID)
    }

    fun updateGroupSummary(count: Int, preferences: NotificationPreferences) {
        if (count <= 1) {
            manager.cancel(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID)
            return
        }
        val channelId = ensureChannel(preferences.soundUri)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$count guardias")
            .setContentText("Avisos de guardias activos o próximos")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channelId, "MiGuardia", "$count avisos de guardia"))
            .build()
        notifySafely(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID, notification)
    }

    private fun notifySafely(tag: String, id: Int, notification: Notification) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()
        ) return
        try {
            manager.notify(tag, id, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked after the explicit check; reconciliation will retry only if valid.
        }
    }

    private fun publicVersion(channelId: String, title: String, text: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun secureAction(
        title: String,
        action: String,
        shift: Shift,
        boundary: Instant,
    ): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0,
        title,
        actionIntent(action, shift, boundary),
    ).setAuthenticationRequired(true).build()

    private fun actionIntent(
        action: String,
        shift: Shift,
        boundary: Instant,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("notification-action")
                    .appendPath(action.substringAfterLast('.'))
                    .appendPath(shift.id.toString())
                    .appendPath(boundary.toEpochMilli().toString())
                    .build(),
            )
            .putExtra(MainActivity.EXTRA_SHIFT_ID, shift.id.toString())
        return PendingIntent.getActivity(
            context,
            "${action}|${shift.id}|${boundary.toEpochMilli()}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(shift: Shift): PendingIntent {
        val intent = Intent(context, ShiftAlarmReceiver::class.java)
            .setAction(ShiftAlarmReceiver.ACTION_NOTIFICATION_DISMISSED)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("notification-dismissed")
                    .appendPath(shift.id.toString())
                    .build(),
            )
        return PendingIntent.getBroadcast(
            context,
            "dismiss|${shift.id}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(selectedSound: Uri?): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val effectiveSound = selectedSound ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundKey = effectiveSound?.toString().orEmpty()
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(soundKey.toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }
        val id = "$CHANNEL_PREFIX$suffix"
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        val channel = NotificationChannel(
            id,
            "Avisos de guardia",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Recordatorios y estado de las guardias configuradas."
            setSound(
                effectiveSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        return id
    }

    companion object {
        const val CHANNEL_PREFIX = "guard_shifts_v1_"
        const val GROUP_KEY = "com.blackatsystems.miguardia.GUARD_SHIFTS"
        const val NOTIFICATION_ID = 1042
        private const val GROUP_SUMMARY_ID = 1043
        private const val GROUP_SUMMARY_TAG = "guard_shift_summary"
        private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
