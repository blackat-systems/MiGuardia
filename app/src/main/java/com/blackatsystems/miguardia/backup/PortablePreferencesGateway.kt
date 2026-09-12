package com.blackatsystems.miguardia.backup

import android.content.Context
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupPreferenceType
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferences
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.weather.WeatherPreferences
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.IOException

data class PortableSettings(
    val displayName: String?,
    val zoom: AppZoom,
    val theme: AppThemeMode,
    val summary: SummaryPreferences,
    val notifications: NotificationPreferences,
    val dismissedEventKeys: Set<String>,
    val weather: WeatherPreferences,
)

class PortablePreferencesGateway(
    context: Context,
    private val guardProfile: GuardProfileStore,
    private val summaryStore: SummaryPreferencesStore,
    private val notificationStore: NotificationPreferencesStore,
    private val weatherStore: WeatherPreferencesStore,
) {
    private val applicationContext = context.applicationContext
    private val displayPreferences = applicationContext.getSharedPreferences(
        MainActivity.DISPLAY_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    suspend fun capture(database: BackupDatabaseSnapshot): List<BackupPreference> {
        val profile = guardProfile.currentForBackup()
        val summary = summaryStore.currentForBackup()
        val notifications = notificationStore.currentForBackup()
        val dismissedEventKeys = restorableDismissedEventKeys(
            database,
            notificationStore.dismissedEventKeys(),
        )
        val weather = weatherStore.currentForBackup()
        val theme = AppThemeMode.fromStorage(
            displayPreferences.getString(MainActivity.APP_THEME_MODE, null),
        )
        val zoom = AppZoom.fromPercent(
            displayPreferences.getInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.STANDARD.percent),
        )
        return listOf(
            text(DISPLAY_NAME, profile.displayName),
            text(THEME, theme.name),
            long(ZOOM, zoom.percent.toLong()),
            texts(SUMMARY_ORDER, summary.orderedFamilies.map { it.name }),
            texts(SUMMARY_HIDDEN, summary.hiddenFamilies.map { it.name }.sorted()),
            bool(SUMMARY_INTRO, summary.introSeen),
            bool(NOTIFICATIONS_ENABLED, notifications.enabled),
            bool(NOTIFICATIONS_PRECISE, notifications.preciseTiming),
            texts(NOTIFICATIONS_REMINDERS, notifications.globalReminderLeadMinutes.map(Long::toString)),
            bool(NOTIFICATIONS_PERSISTENT, notifications.persistentWhileActive),
            text(NOTIFICATIONS_PRIVACY, notifications.privacy.name),
            text(NOTIFICATIONS_ATTENTION, notifications.attentionMode.name),
            texts(MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE, dismissedEventKeys),
            bool(WEATHER_ENABLED, weather.enabled),
            text(WEATHER_UNIT, weather.unitSystem.name),
            bool(WEATHER_NOTIFICATIONS, weather.includeInNotifications),
            bool(WEATHER_EXPLANATION, weather.providerExplanationAccepted),
        ).sortedBy(BackupPreference::key)
    }

    fun normalize(
        preferences: List<BackupPreference>,
        database: BackupDatabaseSnapshot,
    ): List<BackupPreference> {
        if (preferences.map { it.key } != EXPECTED_KEYS.sorted() ||
            preferences.map { it.key }.distinct().size != EXPECTED_KEYS.size
        ) {
            throw InvalidBackupException("Las preferencias portables no coinciden con el contrato vigente.")
        }
        return preferences.map { preference ->
            if (preference.key != MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE) {
                preference
            } else {
                val rawValues = preference.requiredTexts(MAX_DISMISSED_EVENT_KEYS)
                preference.copy(
                    values = restorableDismissedEventKeys(database, rawValues.toSet()),
                )
            }
        }
    }

    fun decode(
        preferences: List<BackupPreference>,
        database: BackupDatabaseSnapshot,
    ): PortableSettings {
        val normalized = normalize(preferences, database)
        val byKey = normalized.associateBy(BackupPreference::key)
        val displayName = byKey.getValue(DISPLAY_NAME).nullableText(MAX_DISPLAY_NAME)
        val theme = enumValue<AppThemeMode>(byKey.getValue(THEME).requiredText())
        val zoom = byKey.getValue(ZOOM).requiredLong().toInt().let(AppZoom::fromPercent)
        if (zoom.percent.toLong() != byKey.getValue(ZOOM).requiredLong()) {
            throw InvalidBackupException("El zoom de la copia no es válido.")
        }
        val summaryOrder = byKey.getValue(SUMMARY_ORDER).requiredTexts().map {
            enumValue<SummaryOptionalFamily>(it)
        }
        val summaryHidden = byKey.getValue(SUMMARY_HIDDEN).requiredTexts().mapTo(linkedSetOf()) {
            enumValue<SummaryOptionalFamily>(it)
        }
        val summary = try {
            SummaryPreferences(
                orderedFamilies = summaryOrder,
                hiddenFamilies = summaryHidden,
                introSeen = byKey.getValue(SUMMARY_INTRO).requiredBoolean(),
            )
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException("La personalización del Resumen es inválida.", error)
        }
        val reminders = byKey.getValue(NOTIFICATIONS_REMINDERS).requiredTexts().map { value ->
            value.toLongOrNull() ?: throw InvalidBackupException("Un anticipo de aviso es inválido.")
        }.let { values ->
            try {
                validateReminderLeadMinutes(values)
            } catch (error: IllegalArgumentException) {
                throw InvalidBackupException("Los anticipos de avisos son inválidos.", error)
            }
        }
        val notifications = NotificationPreferences(
            enabled = byKey.getValue(NOTIFICATIONS_ENABLED).requiredBoolean(),
            preciseTiming = byKey.getValue(NOTIFICATIONS_PRECISE).requiredBoolean(),
            globalReminderLeadMinutes = reminders,
            persistentWhileActive = byKey.getValue(NOTIFICATIONS_PERSISTENT).requiredBoolean(),
            privacy = enumValue(byKey.getValue(NOTIFICATIONS_PRIVACY).requiredText()),
            attentionMode = enumValue(byKey.getValue(NOTIFICATIONS_ATTENTION).requiredText()),
            soundUri = null,
        )
        val dismissedEventKeys = byKey
            .getValue(MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE)
            .requiredTexts(MAX_DISMISSED_EVENT_KEYS)
            .toCollection(linkedSetOf())
        val weather = WeatherPreferences(
            enabled = byKey.getValue(WEATHER_ENABLED).requiredBoolean(),
            unitSystem = enumValue(byKey.getValue(WEATHER_UNIT).requiredText()),
            includeInNotifications = byKey.getValue(WEATHER_NOTIFICATIONS).requiredBoolean(),
            providerExplanationAccepted = byKey.getValue(WEATHER_EXPLANATION).requiredBoolean(),
        )
        if (weather.enabled && !weather.providerExplanationAccepted) {
            throw InvalidBackupException("Clima no puede estar activo sin su explicación aceptada.")
        }
        return PortableSettings(
            displayName,
            zoom,
            theme,
            summary,
            notifications,
            dismissedEventKeys,
            weather,
        )
    }

    suspend fun replace(
        preferences: List<BackupPreference>,
        database: BackupDatabaseSnapshot,
    ): PortableSettings {
        val normalized = normalize(preferences, database)
        val decoded = decode(normalized, database)
        guardProfile.save(decoded.displayName.orEmpty())
        summaryStore.replacePortable(decoded.summary)
        notificationStore.replacePortable(decoded.notifications)
        notificationStore.setDismissedEventKeys(decoded.dismissedEventKeys)
        weatherStore.replacePortable(decoded.weather)
        val saved = displayPreferences.edit()
            .putInt(MainActivity.APP_ZOOM_PERCENT, decoded.zoom.percent)
            .putString(MainActivity.APP_THEME_MODE, decoded.theme.name)
            .commit()
        if (!saved) throw IOException("No se pudo guardar la apariencia restaurada.")
        return decoded
    }

    private fun restorableDismissedEventKeys(
        database: BackupDatabaseSnapshot,
        rawKeys: Set<String>,
    ): List<String> {
        val shiftTable = database.table("shifts")
        val shiftIdIndex = shiftTable.columns.indexOf("id")
        val shiftIds = shiftTable.records.mapNotNullTo(hashSetOf()) { record ->
            (record.values[shiftIdIndex] as? BackupValue.Text)?.value
        }
        val availabilityTable = database.table("availability_windows")
        val availabilityIdIndex = availabilityTable.columns.indexOf("id")
        val availabilityStartIndex = availabilityTable.columns.indexOf("startEpochMillis")
        val availabilityEndIndex = availabilityTable.columns.indexOf("endEpochMillis")
        val availabilityRanges = availabilityTable.records.mapNotNull { record ->
            val id = (record.values[availabilityIdIndex] as? BackupValue.Text)?.value ?: return@mapNotNull null
            val start = (record.values[availabilityStartIndex] as? BackupValue.Integer)?.value
                ?: return@mapNotNull null
            val end = (record.values[availabilityEndIndex] as? BackupValue.Integer)?.value
                ?: return@mapNotNull null
            id to (start to end)
        }.toMap()
        return rawKeys.mapNotNull(NextEventIdentity::parseTrackingKey)
            .filter { identity ->
                when (identity) {
                    is NextEventIdentity.Shift -> identity.shiftId.toString() in shiftIds
                    is NextEventIdentity.Availability -> availabilityRanges[identity.windowId.toString()]
                        ?.let { (start, end) ->
                            identity.segmentStart.toEpochMilli() >= start &&
                                identity.segmentEnd.toEpochMilli() <= end
                        }
                        ?: false
                }
            }
            .map(NextEventIdentity::trackingKey)
            .distinct()
            .sorted()
    }

    suspend fun ensureAccessibleSoundOrFallback() {
        val uri = notificationStore.current().soundUri ?: return
        val accessible = runCatching {
            applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!accessible) notificationStore.setSoundUri(null)
    }

    private fun BackupPreference.requiredBoolean(): Boolean {
        requireType(BackupPreferenceType.BOOLEAN, 1)
        return when (values.single()) {
            "true" -> true
            "false" -> false
            else -> throw InvalidBackupException("Una preferencia booleana es inválida.")
        }
    }

    private fun BackupPreference.requiredLong(): Long {
        requireType(BackupPreferenceType.LONG, 1)
        return values.single().toLongOrNull()
            ?: throw InvalidBackupException("Una preferencia numérica es inválida.")
    }

    private fun BackupPreference.requiredText(maxLength: Int = MAX_PREFERENCE_TEXT): String {
        requireType(BackupPreferenceType.TEXT, 1)
        return values.single().takeIf { it.length <= maxLength }
            ?: throw InvalidBackupException("Una preferencia de texto es demasiado extensa.")
    }

    private fun BackupPreference.nullableText(maxLength: Int): String? {
        if (type != BackupPreferenceType.TEXT || values.size !in 0..1) {
            throw InvalidBackupException("Una preferencia opcional es inválida.")
        }
        return values.singleOrNull()?.trim()?.takeIf(String::isNotEmpty)?.also {
            if (it.length > maxLength) throw InvalidBackupException("El perfil es demasiado extenso.")
        }
    }

    private fun BackupPreference.requiredTexts(maxValues: Int = MAX_LIST_VALUES): List<String> {
        if (type != BackupPreferenceType.TEXT_LIST || values.size > maxValues ||
            values.any { it.length > MAX_PREFERENCE_TEXT }
        ) {
            throw InvalidBackupException("Una lista de preferencias es inválida.")
        }
        return values
    }

    private fun BackupPreference.requireType(expected: BackupPreferenceType, count: Int) {
        if (type != expected || values.size != count) {
            throw InvalidBackupException("La preferencia $key no tiene el tipo esperado.")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw InvalidBackupException("La copia contiene una opción desconocida.")

    private fun text(key: String, value: String?): BackupPreference = BackupPreference(
        key,
        BackupPreferenceType.TEXT,
        value?.let(::listOf).orEmpty(),
    )

    private fun bool(key: String, value: Boolean): BackupPreference = BackupPreference(
        key,
        BackupPreferenceType.BOOLEAN,
        listOf(value.toString()),
    )

    private fun long(key: String, value: Long): BackupPreference = BackupPreference(
        key,
        BackupPreferenceType.LONG,
        listOf(value.toString()),
    )

    private fun texts(key: String, values: List<String>): BackupPreference = BackupPreference(
        key,
        BackupPreferenceType.TEXT_LIST,
        values,
    )

    private companion object {
        const val DISPLAY_NAME = "profile.display_name"
        const val THEME = "display.theme"
        const val ZOOM = "display.zoom_percent"
        const val SUMMARY_ORDER = "summary.ordered_families"
        const val SUMMARY_HIDDEN = "summary.hidden_families"
        const val SUMMARY_INTRO = "summary.intro_seen"
        const val NOTIFICATIONS_ENABLED = "notifications.enabled"
        const val NOTIFICATIONS_PRECISE = "notifications.precise_timing"
        const val NOTIFICATIONS_REMINDERS = "notifications.reminder_minutes"
        const val NOTIFICATIONS_PERSISTENT = "notifications.persistent"
        const val NOTIFICATIONS_PRIVACY = "notifications.privacy"
        const val NOTIFICATIONS_ATTENTION = "notifications.attention"
        const val WEATHER_ENABLED = "weather.enabled"
        const val WEATHER_UNIT = "weather.unit"
        const val WEATHER_NOTIFICATIONS = "weather.include_notifications"
        const val WEATHER_EXPLANATION = "weather.explanation_accepted"
        val EXPECTED_KEYS = listOf(
            DISPLAY_NAME,
            THEME,
            ZOOM,
            SUMMARY_ORDER,
            SUMMARY_HIDDEN,
            SUMMARY_INTRO,
            NOTIFICATIONS_ENABLED,
            NOTIFICATIONS_PRECISE,
            NOTIFICATIONS_REMINDERS,
            NOTIFICATIONS_PERSISTENT,
            NOTIFICATIONS_PRIVACY,
            NOTIFICATIONS_ATTENTION,
            MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE,
            WEATHER_ENABLED,
            WEATHER_UNIT,
            WEATHER_NOTIFICATIONS,
            WEATHER_EXPLANATION,
        )
        const val MAX_DISPLAY_NAME = 100
        const val MAX_PREFERENCE_TEXT = 256
        const val MAX_LIST_VALUES = 64
        const val MAX_DISMISSED_EVENT_KEYS = 16_384
    }
}
