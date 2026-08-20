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

class NotificationPreferencesStore private constructor(
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

    internal val dismissedShiftIdsFlow: Flow<Set<String>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values -> values[DismissedShiftIds].orEmpty() }
        .distinctUntilChanged()

    suspend fun current(): NotificationPreferences = preferences.first()

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

    internal suspend fun displayedShiftIds(): Set<String> =
        dataStore.data.first()[DisplayedShiftIds].orEmpty()

    internal suspend fun setDisplayedShiftIds(ids: Set<String>) = update {
        it[DisplayedShiftIds] = ids
    }

    internal suspend fun dismissedShiftIds(): Set<String> =
        dataStore.data.first()[DismissedShiftIds].orEmpty()

    internal suspend fun markDismissed(shiftId: String) = update { values ->
        values[DisplayedShiftIds] = values[DisplayedShiftIds].orEmpty() - shiftId
        values[DismissedShiftIds] = values[DismissedShiftIds].orEmpty() + shiftId
    }

    internal suspend fun markDisplayed(shiftId: String) = update { values ->
        values[DismissedShiftIds] = values[DismissedShiftIds].orEmpty() - shiftId
        values[DisplayedShiftIds] = values[DisplayedShiftIds].orEmpty() + shiftId
    }

    internal suspend fun markDisplayedUnlessDismissed(shiftId: String): Boolean {
        var accepted = false
        update { values ->
            if (shiftId !in values[DismissedShiftIds].orEmpty()) {
                values[DisplayedShiftIds] = values[DisplayedShiftIds].orEmpty() + shiftId
                accepted = true
            }
        }
        return accepted
    }

    internal suspend fun clearShiftTracking(shiftId: String) = update { values ->
        values[DisplayedShiftIds] = values[DisplayedShiftIds].orEmpty() - shiftId
        values[DismissedShiftIds] = values[DismissedShiftIds].orEmpty() - shiftId
    }

    internal suspend fun setDismissedShiftIds(ids: Set<String>) = update {
        it[DismissedShiftIds] = ids
    }

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
