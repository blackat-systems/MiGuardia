package com.blackatsystems.miguardia.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class GuardProfile(
    val displayName: String? = null,
)

internal fun normalizeGuardProfile(displayName: String): GuardProfile {
    return GuardProfile(
        displayName = displayName.trim().takeIf(String::isNotEmpty),
    )
}

class GuardProfileStore private constructor(
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

    val profile: Flow<GuardProfile> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toProfile)

    suspend fun current(): GuardProfile = profile.first()

    suspend fun save(displayName: String): GuardProfile {
        val normalized = normalizeGuardProfile(displayName)
        dataStore.edit { values ->
            if (normalized.displayName == null) {
                values.remove(DisplayName)
            } else {
                values[DisplayName] = normalized.displayName
            }
        }
        return normalized
    }

    private fun toProfile(values: Preferences): GuardProfile = GuardProfile(
        displayName = values[DisplayName]?.trim()?.takeIf(String::isNotEmpty),
    )

    private companion object {
        const val DEFAULT_FILE_NAME = "guard_profile.preferences_pb"
        val DisplayName = stringPreferencesKey("display_name")
    }
}
