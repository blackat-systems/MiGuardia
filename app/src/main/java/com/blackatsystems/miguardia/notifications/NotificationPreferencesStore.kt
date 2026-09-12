package com.blackatsystems.miguardia.notifications

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import java.io.IOException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class NotificationPrivacy {
    COMPLETE,
    REDUCED,
    HIDDEN,
}

enum class NotificationAttentionMode {
    SOUND_AND_VIBRATION,
    VIBRATION_ONLY,
    SILENT,
}

enum class NotificationRhythm {
    ACCOMPANIED,
    ESSENTIAL,
    DISCREET,
    CUSTOM,
}

data class NotificationPreferences(
    val enabled: Boolean = false,
    val preciseTiming: Boolean = false,
    val globalReminderLeadMinutes: List<Long> = listOf(DEFAULT_REMINDER_MINUTES),
    val persistentWhileActive: Boolean = true,
    val privacy: NotificationPrivacy = NotificationPrivacy.COMPLETE,
    val attentionMode: NotificationAttentionMode = NotificationAttentionMode.SOUND_AND_VIBRATION,
    val soundUri: Uri? = null,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 12L * 60L
    }

    fun rhythm(): NotificationRhythm = when {
        globalReminderLeadMinutes == listOf(120L, 720L) &&
            persistentWhileActive &&
            privacy == NotificationPrivacy.COMPLETE &&
            attentionMode == NotificationAttentionMode.SOUND_AND_VIBRATION ->
            NotificationRhythm.ACCOMPANIED

        globalReminderLeadMinutes == listOf(DEFAULT_REMINDER_MINUTES) &&
            persistentWhileActive &&
            privacy == NotificationPrivacy.COMPLETE &&
            attentionMode == NotificationAttentionMode.SOUND_AND_VIBRATION ->
            NotificationRhythm.ESSENTIAL

        globalReminderLeadMinutes == listOf(DEFAULT_REMINDER_MINUTES) &&
            !persistentWhileActive &&
            privacy == NotificationPrivacy.REDUCED &&
            attentionMode == NotificationAttentionMode.SILENT ->
            NotificationRhythm.DISCREET

        else -> NotificationRhythm.CUSTOM
    }
}

class NotificationPreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
        ),
    )

    internal constructor(file: File, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )

    val preferences: Flow<NotificationPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toPreferences)

    internal val dismissedEventKeysFlow: Flow<Set<String>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values -> normalizeEventKeys(values[DismissedShiftIds].orEmpty()) }
        .distinctUntilChanged()

    suspend fun current(): NotificationPreferences = preferences.first()

    /** Backup and recovery must abort instead of silently exporting defaults on I/O failure. */
    internal suspend fun currentForBackup(): NotificationPreferences = toPreferences(dataStore.data.first())

    /** Replaces only portable user choices and preserves device/runtime bookkeeping. */
    suspend fun replacePortable(preferences: NotificationPreferences) = update { values ->
        values[Enabled] = preferences.enabled
        values[PreciseTiming] = preferences.preciseTiming
        values[Persistent] = preferences.persistentWhileActive
        values[Privacy] = preferences.privacy.name
        values[AttentionMode] = preferences.attentionMode.name
        values[ReminderMinutes] = preferences.globalReminderLeadMinutes.map(Long::toString).toSet()
    }

    suspend fun setEnabled(value: Boolean) = update { it[Enabled] = value }
    suspend fun setPreciseTiming(value: Boolean) = update { it[PreciseTiming] = value }
    suspend fun setPersistentWhileActive(value: Boolean) = update { it[Persistent] = value }
    suspend fun setPrivacy(value: NotificationPrivacy) = update { it[Privacy] = value.name }
    suspend fun setAttentionMode(value: NotificationAttentionMode) = update {
        it[AttentionMode] = value.name
    }

    suspend fun applyRhythm(value: NotificationRhythm) {
        require(value != NotificationRhythm.CUSTOM) { "El ritmo personalizado no es un preset aplicable." }
        update { preferences ->
            val reminderMinutes = when (value) {
                NotificationRhythm.ACCOMPANIED -> listOf(120L, 720L)
                NotificationRhythm.ESSENTIAL,
                NotificationRhythm.DISCREET,
                -> listOf(NotificationPreferences.DEFAULT_REMINDER_MINUTES)
                NotificationRhythm.CUSTOM -> error("Unreachable")
            }
            preferences[ReminderMinutes] = reminderMinutes.map(Long::toString).toSet()
            preferences[Persistent] = value != NotificationRhythm.DISCREET
            preferences[Privacy] = if (value == NotificationRhythm.DISCREET) {
                NotificationPrivacy.REDUCED.name
            } else {
                NotificationPrivacy.COMPLETE.name
            }
            preferences[AttentionMode] = if (value == NotificationRhythm.DISCREET) {
                NotificationAttentionMode.SILENT.name
            } else {
                NotificationAttentionMode.SOUND_AND_VIBRATION.name
            }
        }
    }

    suspend fun setGlobalReminderLeadMinutes(values: Collection<Long>) {
        val validated = validateReminderLeadMinutes(values)
        update { preferences ->
            preferences[ReminderMinutes] = validated.map(Long::toString).toSet()
        }
    }

    suspend fun setSoundUri(uri: Uri?) = update { preferences ->
        if (uri == null) preferences.remove(SoundUri) else preferences[SoundUri] = uri.toString()
    }

    internal suspend fun installedBoundaryKeys(): Set<String> =
        dataStore.data.first()[InstalledBoundaryKeys].orEmpty()

    internal suspend fun setInstalledBoundaryKeys(keys: Set<String>) = update {
        it[InstalledBoundaryKeys] = keys
    }

    internal suspend fun installedExactMode(): Boolean =
        dataStore.data.first()[InstalledExactMode] ?: false

    internal suspend fun setInstalledExactMode(exact: Boolean) = update {
        it[InstalledExactMode] = exact
    }

    internal suspend fun displayedEventKeys(): Set<String> =
        normalizeEventKeys(dataStore.data.first()[DisplayedShiftIds].orEmpty())

    internal suspend fun setDisplayedEventKeys(keys: Set<String>) = update {
        it[DisplayedShiftIds] = normalizeEventKeys(keys)
    }

    internal suspend fun dismissedEventKeys(): Set<String> =
        normalizeEventKeys(dataStore.data.first()[DismissedShiftIds].orEmpty())

    internal suspend fun markDismissed(eventKey: String) = update { values ->
        val normalized = requireEventKey(eventKey)
        values[DisplayedShiftIds] = normalizeEventKeys(values[DisplayedShiftIds].orEmpty()) - normalized
        values[DismissedShiftIds] = normalizeEventKeys(values[DismissedShiftIds].orEmpty()) + normalized
    }

    internal suspend fun markDisplayed(eventKey: String) = update { values ->
        val normalized = requireEventKey(eventKey)
        values[DismissedShiftIds] = normalizeEventKeys(values[DismissedShiftIds].orEmpty()) - normalized
        values[DisplayedShiftIds] = normalizeEventKeys(values[DisplayedShiftIds].orEmpty()) + normalized
    }

    internal suspend fun markDisplayedUnlessDismissed(eventKey: String): Boolean {
        var accepted = false
        update { values ->
            val normalized = requireEventKey(eventKey)
            val dismissed = normalizeEventKeys(values[DismissedShiftIds].orEmpty())
            if (normalized !in dismissed) {
                values[DisplayedShiftIds] =
                    normalizeEventKeys(values[DisplayedShiftIds].orEmpty()) + normalized
                accepted = true
            }
        }
        return accepted
    }

    internal suspend fun clearEventTracking(eventKey: String) = update { values ->
        val normalized = requireEventKey(eventKey)
        values[DisplayedShiftIds] = normalizeEventKeys(values[DisplayedShiftIds].orEmpty()) - normalized
        values[DismissedShiftIds] = normalizeEventKeys(values[DismissedShiftIds].orEmpty()) - normalized
    }

    internal suspend fun setDismissedEventKeys(keys: Set<String>) = update {
        it[DismissedShiftIds] = normalizeEventKeys(keys)
    }

    private fun requireEventKey(value: String): String = requireNotNull(
        NextEventIdentity.parseTrackingKey(value)?.trackingKey,
    ) { "La identidad de aviso no es valida" }

    private fun normalizeEventKeys(values: Set<String>): Set<String> = values
        .mapNotNull { value -> NextEventIdentity.parseTrackingKey(value)?.trackingKey }
        .toCollection(linkedSetOf())

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun toPreferences(values: Preferences): NotificationPreferences {
        val reminderMinutes = values[ReminderMinutes]
            ?.mapNotNull(String::toLongOrNull)
            ?.let { runCatching { validateReminderLeadMinutes(it) }.getOrNull() }
            ?: listOf(NotificationPreferences.DEFAULT_REMINDER_MINUTES)
        return NotificationPreferences(
            enabled = values[Enabled] ?: false,
            preciseTiming = values[PreciseTiming] ?: false,
            globalReminderLeadMinutes = reminderMinutes,
            persistentWhileActive = values[Persistent] ?: true,
            privacy = values[Privacy]
                ?.let { runCatching { NotificationPrivacy.valueOf(it) }.getOrNull() }
                ?: NotificationPrivacy.COMPLETE,
            attentionMode = values[AttentionMode]
                ?.let { runCatching { NotificationAttentionMode.valueOf(it) }.getOrNull() }
                ?: NotificationAttentionMode.SOUND_AND_VIBRATION,
            soundUri = values[SoundUri]?.let(Uri::parse),
        )
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "notification_preferences.preferences_pb"
        val Enabled = booleanPreferencesKey("enabled")
        val PreciseTiming = booleanPreferencesKey("precise_timing")
        val Persistent = booleanPreferencesKey("persistent_while_active")
        val Privacy = stringPreferencesKey("privacy")
        val AttentionMode = stringPreferencesKey("attention_mode")
        val ReminderMinutes = stringSetPreferencesKey("global_reminder_minutes")
        val SoundUri = stringPreferencesKey("sound_uri")
        val InstalledBoundaryKeys = stringSetPreferencesKey("installed_boundary_keys")
        val InstalledExactMode = booleanPreferencesKey("installed_exact_mode")
        val DisplayedShiftIds = stringSetPreferencesKey("displayed_shift_ids")
        val DismissedShiftIds = stringSetPreferencesKey("dismissed_shift_ids")
    }
}
