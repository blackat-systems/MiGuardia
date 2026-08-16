package com.blackatsystems.miguardia.remuneration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class RemunerationPreferences(
    val seniorityYears: Int = 0,
)

class RemunerationPreferencesStore private constructor(
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

    val preferences: Flow<RemunerationPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            RemunerationPreferences(
                seniorityYears = values[SeniorityYears]?.coerceIn(MIN_YEARS, MAX_YEARS) ?: MIN_YEARS,
            )
        }

    suspend fun setSeniorityYears(years: Int) {
        require(years in MIN_YEARS..MAX_YEARS) { "La antigüedad debe estar entre 0 y 60 años." }
        dataStore.edit { it[SeniorityYears] = years }
    }

    companion object {
        const val MIN_YEARS = 0
        const val MAX_YEARS = 60
        private const val DEFAULT_FILE_NAME = "remuneration_preferences.preferences_pb"
        private val SeniorityYears = intPreferencesKey("seniority_years")
    }
}
