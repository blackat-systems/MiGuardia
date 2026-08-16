package com.blackatsystems.miguardia.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class WeatherPreferences(
    val enabled: Boolean = false,
    val unitSystem: WeatherUnitSystem = WeatherUnitSystem.CELSIUS,
    val includeInNotifications: Boolean = false,
    val providerExplanationAccepted: Boolean = false,
    val lastRefreshAttemptAtEpochMillis: Long? = null,
    val retryAfterUntilEpochMillis: Long? = null,
)

class WeatherPreferencesStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context, fileName: String = DEFAULT_FILE_NAME) : this(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
        ),
    )

    internal constructor(file: File, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )

    val preferences: Flow<WeatherPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toPreferences)

    suspend fun current(): WeatherPreferences = preferences.first()

    suspend fun enableAfterExplanation() = dataStore.edit {
        it[Enabled] = true
        it[ExplanationAccepted] = true
    }

    suspend fun setEnabled(value: Boolean) = dataStore.edit { it[Enabled] = value }
    suspend fun setUnitSystem(value: WeatherUnitSystem) = dataStore.edit { it[Unit] = value.name }
    suspend fun setIncludeInNotifications(value: Boolean) = dataStore.edit { it[IncludeNotifications] = value }
    suspend fun recordRefreshAttempt(epochMillis: Long) = dataStore.edit { it[LastRefreshAttempt] = epochMillis }
    suspend fun setRetryAfterUntil(epochMillis: Long?) = dataStore.edit {
        if (epochMillis == null) it.remove(RetryAfterUntil) else it[RetryAfterUntil] = epochMillis
    }

    private fun toPreferences(values: Preferences): WeatherPreferences = WeatherPreferences(
        enabled = values[Enabled] ?: false,
        unitSystem = values[Unit]?.let { runCatching { WeatherUnitSystem.valueOf(it) }.getOrNull() }
            ?: WeatherUnitSystem.CELSIUS,
        includeInNotifications = values[IncludeNotifications] ?: false,
        providerExplanationAccepted = values[ExplanationAccepted] ?: false,
        lastRefreshAttemptAtEpochMillis = values[LastRefreshAttempt],
        retryAfterUntilEpochMillis = values[RetryAfterUntil],
    )

    private companion object {
        const val DEFAULT_FILE_NAME = "weather_preferences.preferences_pb"
        val Enabled = booleanPreferencesKey("enabled")
        val Unit = stringPreferencesKey("unit_system")
        val IncludeNotifications = booleanPreferencesKey("include_in_notifications")
        val ExplanationAccepted = booleanPreferencesKey("provider_explanation_accepted")
        val LastRefreshAttempt = longPreferencesKey("last_refresh_attempt_epoch_millis")
        val RetryAfterUntil = longPreferencesKey("retry_after_until_epoch_millis")
    }
}
