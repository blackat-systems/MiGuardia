package com.blackatsystems.miguardia.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class ShiftNotificationPresenter(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun show(
        event: NextEventItem,
        now: Instant,
        preferences: NotificationPreferences,
        weatherText: String? = null,
        silentUpdate: Boolean = false,
    ) {
        val ongoing = now >= event.start
        val timeRange = when (event) {
            is NextEventItem.Shift ->
                "${event.startTimeSnapshot.format(TimeFormatter)}–${event.endTimeSnapshot.format(TimeFormatter)}"
            is NextEventItem.Availability ->
                "${event.start.atZone(event.zoneId).toLocalTime().format(TimeFormatter)}–" +
                    event.end.atZone(event.zoneId).toLocalTime().format(TimeFormatter)
        }
        val card = when (event) {
            is NextEventItem.Shift -> NotificationCard(
                state = if (ongoing) "JORNADA EN CURSO" else "PRÓXIMA JORNADA",
                objective = "${event.workTypeNameSnapshot} · ${event.placeNameSnapshot}",
                abbreviation = event.placeAbbreviationSnapshot,
                schedule = timeRange,
                position = event.positionSnapshot?.takeIf(String::isNotBlank),
                weather = weatherText,
                accentColor = event.colorArgbSnapshot,
                countdownBase = event.countdownBase(now, ongoing),
                countdownLabel = if (ongoing) "Finaliza en %s" else "Comienza en %s",
                privacy = preferences.privacy,
            )
            is NextEventItem.Availability -> NotificationCard(
                state = if (ongoing) "DISPONIBILIDAD ACTIVA" else "PRÓXIMA DISPONIBILIDAD",
                objective = event.labelSnapshot,
                abbreviation = null,
                schedule = timeRange,
                position = null,
                weather = null,
                accentColor = VIGILIA_ACCENT,
                countdownBase = event.countdownBase(now, ongoing),
                countdownLabel = if (ongoing) "Finaliza en %s" else "Comienza en %s",
                privacy = preferences.privacy,
            )
        }
        val views = createViews(card)
        val channelId = ensureChannel(preferences)
        val dismissPendingIntent = dismissIntent(event.identity)
        val persistentDuringActiveEvent = preferences.persistentWhileActive && ongoing
        views.expanded.setOnClickPendingIntent(R.id.notification_dismiss, dismissPendingIntent)
        val detailsAction = event.detailsAction()
        val builder = baseBuilder(channelId, card, views)
            .setColor(card.displayedAccent())
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(silentUpdate)
            .setOngoing(persistentDuringActiveEvent)
            .setAutoCancel(!persistentDuringActiveEvent)
            .setContentIntent(actionIntent(detailsAction, event, now))
            .setDeleteIntent(dismissPendingIntent)
            .addAction(secureAction("Ver detalles", detailsAction, event, now))
        if (event is NextEventItem.Shift && event.hasHistoricalAddress) {
            builder.addAction(secureAction("Cómo llegar", MainActivity.ACTION_DIRECTIONS, event, now))
        }
        if (silentUpdate) builder.setSilent(true)
        applyPrivacy(builder, channelId, card)
        cancelLegacyTag(event.identity)
        notifySafely(event.identity.trackingKey, NOTIFICATION_ID, builder.build())
    }

    fun showTestNotification(preferences: NotificationPreferences) {
        val card = NotificationCard(
            state = "PRUEBA · PRÓXIMA JORNADA",
            objective = "Jornada habitual · Hospital Norte",
            abbreviation = "NOR",
            schedule = "19:00–07:00",
            position = "Acceso principal",
            weather = "Clima: fresco, sin lluvia prevista",
            accentColor = VIGILIA_ACCENT,
            countdownBase = SystemClock.elapsedRealtime() + PREVIEW_COUNTDOWN_MILLIS,
            countdownLabel = "Comienza en %s",
            privacy = preferences.privacy,
        )
        val views = createViews(card, dismissVisible = false)
        val channelId = ensureChannel(preferences)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .setData("miguardia://notification-preview".toUri())
        val pendingIntent = PendingIntent.getActivity(
            context,
            PREVIEW_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = baseBuilder(channelId, card, views)
            .setColor(card.displayedAccent())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setTimeoutAfter(PREVIEW_TIMEOUT_MILLIS)
        applyPrivacy(builder, channelId, card)
        notifySafely(PREVIEW_TAG, PREVIEW_NOTIFICATION_ID, builder.build())
    }

    fun cancel(eventKey: String) {
        val identity = NextEventIdentity.parseTrackingKey(eventKey) ?: return
        manager.cancel(identity.trackingKey, NOTIFICATION_ID)
        cancelLegacyTag(identity)
    }

    fun updateGroupSummary(count: Int, preferences: NotificationPreferences) {
        if (count <= 1) {
            manager.cancel(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID)
            return
        }
        val channelId = ensureChannel(preferences)
        val summary = notificationGroupSummaryContent(count, preferences.privacy)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(summary.visibility)
            .setPublicVersion(publicVersion(channelId, summary.publicTitle, summary.publicText))
            .build()
        notifySafely(GROUP_SUMMARY_TAG, GROUP_SUMMARY_ID, notification)
    }

    private fun NextEventItem.countdownBase(now: Instant, ongoing: Boolean): Long =
        SystemClock.elapsedRealtime() + Duration.between(
            now,
            if (ongoing) end else start,
        ).toMillis().coerceAtLeast(0L)

    private fun NextEventItem.detailsAction(): String = when (this) {
        is NextEventItem.Shift -> MainActivity.ACTION_VIEW_SHIFT
        is NextEventItem.Availability -> MainActivity.ACTION_VIEW_DATE
    }

    private fun baseBuilder(
        channelId: String,
        card: NotificationCard,
        views: NotificationViews,
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(card.fallbackTitle())
        .setContentText(card.fallbackText())
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(views.compact)
        .setCustomBigContentView(views.expanded)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setShowWhen(false)
        .setUsesChronometer(false)

    private fun createViews(card: NotificationCard, dismissVisible: Boolean = true): NotificationViews {
        val compact = RemoteViews(context.packageName, R.layout.notification_shift_compact).apply {
            setInt(R.id.notification_accent, "setBackgroundColor", card.displayedAccent())
            setTextViewText(R.id.notification_title, card.compactTitle())
            setTextViewText(R.id.notification_schedule, card.compactSchedule())
            configureCountdown(card)
        }
        val expanded = RemoteViews(context.packageName, R.layout.notification_shift_expanded).apply {
            setInt(R.id.notification_accent, "setBackgroundColor", card.displayedAccent())
            setTextViewText(R.id.notification_title, card.expandedState())
            setOptionalText(R.id.notification_objective, card.expandedObjective())
            setOptionalText(R.id.notification_schedule, card.expandedSchedule())
            setOptionalText(R.id.notification_position, card.expandedPosition())
            setOptionalText(R.id.notification_weather, card.expandedWeather())
            configureCountdown(card)
            setViewVisibility(R.id.notification_dismiss, if (dismissVisible) View.VISIBLE else View.GONE)
        }
        return NotificationViews(compact, expanded)
    }

    private fun RemoteViews.configureCountdown(card: NotificationCard) {
        val visible = card.privacy != NotificationPrivacy.HIDDEN
        setViewVisibility(R.id.notification_countdown, if (visible) View.VISIBLE else View.GONE)
        if (visible) {
            setChronometer(R.id.notification_countdown, card.countdownBase, card.countdownLabel, true)
            setChronometerCountDown(R.id.notification_countdown, true)
        }
    }

    private fun RemoteViews.setOptionalText(viewId: Int, text: String?) {
        setViewVisibility(viewId, if (text == null) View.GONE else View.VISIBLE)
        text?.let { setTextViewText(viewId, it) }
    }

    private fun applyPrivacy(
        builder: NotificationCompat.Builder,
        channelId: String,
        card: NotificationCard,
    ) {
        when (card.privacy) {
            NotificationPrivacy.COMPLETE -> builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            NotificationPrivacy.REDUCED -> builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(channelId, card.state, "Horario ${card.schedule}"))
            NotificationPrivacy.HIDDEN -> builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(channelId, "MiGuardia", "Tenés un aviso de MiGuardia."))
        }
    }

    private fun notifySafely(tag: String, id: Int, notification: Notification) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()
        ) return
        try {
            manager.notify(tag, id, notification)
        } catch (_: SecurityException) {
            // El permiso puede revocarse después del chequeo; la reconciliación mantiene el estado seguro.
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
        event: NextEventItem,
        boundary: Instant,
    ): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0,
        title,
        actionIntent(action, event, boundary),
    ).setAuthenticationRequired(true).build()

    private fun actionIntent(
        action: String,
        event: NextEventItem,
        boundary: Instant,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("notification-action")
                    .appendPath(action.substringAfterLast('.'))
                    .appendQueryParameter("event", event.identity.trackingKey)
                    .appendQueryParameter("boundary", boundary.toEpochMilli().toString())
                    .build(),
            )
        when (event) {
            is NextEventItem.Shift -> intent.putExtra(MainActivity.EXTRA_SHIFT_ID, event.shiftId.toString())
            is NextEventItem.Availability -> intent.putExtra(
                MainActivity.EXTRA_OWNER_LOCAL_DATE,
                event.ownerLocalDate.toString(),
            )
        }
        return PendingIntent.getActivity(
            context,
            "$action|${event.identity.trackingKey}|${boundary.toEpochMilli()}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(identity: NextEventIdentity): PendingIntent {
        val intent = Intent(context, ShiftAlarmReceiver::class.java)
            .setAction(ShiftAlarmReceiver.ACTION_NOTIFICATION_DISMISSED)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("notification-dismissed")
                    .appendQueryParameter("event", identity.trackingKey)
                    .build(),
            )
        return PendingIntent.getBroadcast(
            context,
            "dismiss|${identity.trackingKey}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelLegacyTag(identity: NextEventIdentity) {
        if (identity is NextEventIdentity.Shift) {
            manager.cancel(identity.shiftId.toString(), NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(preferences: NotificationPreferences): String {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val effectiveSound = preferences.soundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelKey = buildString {
            append(preferences.attentionMode.name)
            if (preferences.attentionMode == NotificationAttentionMode.SOUND_AND_VIBRATION) {
                append('|').append(effectiveSound)
            }
        }
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(channelKey.toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }
        val id = "$CHANNEL_PREFIX$suffix"
        notificationManager.notificationChannels
            .filter { it.id.startsWith(OWNED_CHANNEL_PREFIX) && it.id != id }
            .forEach { notificationManager.deleteNotificationChannel(it.id) }
        val importance = if (preferences.attentionMode == NotificationAttentionMode.SILENT) {
            NotificationManager.IMPORTANCE_LOW
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        val channel = NotificationChannel(id, "Avisos laborales", importance).apply {
            description = "Recordatorios y estado de jornadas y disponibilidad configuradas."
            when (preferences.attentionMode) {
                NotificationAttentionMode.SOUND_AND_VIBRATION -> {
                    setSound(
                        effectiveSound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                }
                NotificationAttentionMode.VIBRATION_ONLY -> {
                    setSound(null, null)
                    enableVibration(true)
                }
                NotificationAttentionMode.SILENT -> {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
        return id
    }

    private data class NotificationViews(val compact: RemoteViews, val expanded: RemoteViews)

    private data class NotificationCard(
        val state: String,
        val objective: String,
        val abbreviation: String?,
        val schedule: String,
        val position: String?,
        val weather: String?,
        val accentColor: Int,
        val countdownBase: Long,
        val countdownLabel: String,
        val privacy: NotificationPrivacy,
    ) {
        fun displayedAccent(): Int = if (privacy == NotificationPrivacy.HIDDEN) HIDDEN_ACCENT else accentColor
        fun compactTitle(): String = when (privacy) {
            NotificationPrivacy.COMPLETE -> "$state · $objective"
            NotificationPrivacy.REDUCED -> state
            NotificationPrivacy.HIDDEN -> "MiGuardia"
        }
        fun compactSchedule(): String = when (privacy) {
            NotificationPrivacy.COMPLETE -> listOfNotNull(abbreviation, schedule).joinToString(" · ")
            NotificationPrivacy.REDUCED -> "Horario $schedule"
            NotificationPrivacy.HIDDEN -> "Tenés un aviso de MiGuardia."
        }
        fun expandedState(): String = when (privacy) {
            NotificationPrivacy.COMPLETE,
            NotificationPrivacy.REDUCED,
            -> state
            NotificationPrivacy.HIDDEN -> "MiGuardia"
        }
        fun expandedObjective(): String? = if (privacy == NotificationPrivacy.COMPLETE) objective else null
        fun expandedSchedule(): String? = when (privacy) {
            NotificationPrivacy.COMPLETE -> listOfNotNull(abbreviation, "Horario $schedule").joinToString(" · ")
            NotificationPrivacy.REDUCED -> "Horario $schedule"
            NotificationPrivacy.HIDDEN -> "Tenés un aviso de MiGuardia."
        }
        fun expandedPosition(): String? = if (privacy == NotificationPrivacy.COMPLETE) {
            position?.let { "Puesto: $it" }
        } else {
            null
        }
        fun expandedWeather(): String? = if (privacy == NotificationPrivacy.COMPLETE) weather else null
        fun fallbackTitle(): String = compactTitle()
        fun fallbackText(): String = compactSchedule()
    }

    companion object {
        const val CHANNEL_PREFIX = "guard_shifts_v3_"
        const val OWNED_CHANNEL_PREFIX = "guard_shifts_"
        const val GROUP_KEY = "com.blackatsystems.miguardia.GUARD_SHIFTS"
        const val NOTIFICATION_ID = 1042
        const val PREVIEW_NOTIFICATION_ID = 1044
        const val PREVIEW_TAG = "pulso_vigilia_preview"
        private const val GROUP_SUMMARY_ID = 1043
        private const val GROUP_SUMMARY_TAG = "guard_shift_summary"
        private const val PREVIEW_COUNTDOWN_MILLIS = 3L * 60L * 60L * 1000L + 12L * 60L * 1000L
        private const val PREVIEW_TIMEOUT_MILLIS = 60_000L
        private const val VIGILIA_ACCENT = 0xFF8B5CFF.toInt()
        private const val HIDDEN_ACCENT = 0xFF665E70.toInt()
        private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

internal data class NotificationGroupSummaryContent(
    val title: String,
    val text: String,
    val visibility: Int,
    val publicTitle: String,
    val publicText: String,
)

internal fun notificationGroupSummaryContent(
    count: Int,
    privacy: NotificationPrivacy,
): NotificationGroupSummaryContent {
    require(count > 1) { "Un resumen agrupado necesita al menos dos eventos" }
    return when (privacy) {
        NotificationPrivacy.COMPLETE -> NotificationGroupSummaryContent(
            title = "$count eventos",
            text = "Avisos laborales activos o próximos",
            visibility = NotificationCompat.VISIBILITY_PUBLIC,
            publicTitle = "$count eventos",
            publicText = "Avisos laborales activos o próximos",
        )
        NotificationPrivacy.REDUCED -> NotificationGroupSummaryContent(
            title = "$count avisos",
            text = "Avisos activos o próximos",
            visibility = NotificationCompat.VISIBILITY_PRIVATE,
            publicTitle = "MiGuardia",
            publicText = "$count avisos",
        )
        NotificationPrivacy.HIDDEN -> NotificationGroupSummaryContent(
            title = "MiGuardia",
            text = "Tenés avisos de MiGuardia.",
            visibility = NotificationCompat.VISIBILITY_PRIVATE,
            publicTitle = "MiGuardia",
            publicText = "Tenés avisos de MiGuardia.",
        )
    }
}
