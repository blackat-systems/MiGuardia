package com.blackatsystems.miguardia.ui.help

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal sealed interface OnboardingStoreState {
    data class Ready(val completedVersion: Int) : OnboardingStoreState
    data object Error : OnboardingStoreState
}

internal interface OnboardingVersionStore {
    val state: Flow<OnboardingStoreState>

    suspend fun completeAtLeast(version: Int, stillApplicable: () -> Boolean = { true }): Int
}

internal class OnboardingPreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingVersionStore {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { EmptyPreferences },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.applicationContext.dataStoreFile(fileName) },
        ),
    )

    internal constructor(file: File, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { EmptyPreferences },
            scope = scope,
            produceFile = { file },
        ),
    )

    override val state: Flow<OnboardingStoreState> = dataStore.data
        .map(::decode)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(OnboardingStoreState.Error)
        }

    override suspend fun completeAtLeast(version: Int, stillApplicable: () -> Boolean): Int {
        require(version > 0) { "La versión del recorrido debe ser positiva" }
        var completedVersion: Int? = null
        dataStore.edit { values ->
            val current = decode(values) as? OnboardingStoreState.Ready
                ?: throw IllegalStateException("No se puede completar un estado de recorrido inválido")
            val next = if (stillApplicable()) maxOf(current.completedVersion, version) else current.completedVersion
            if (next != current.completedVersion) {
                values.clear()
                values[CompletedVersion] = next
            }
            completedVersion = next
        }
        return requireNotNull(completedVersion)
    }

    internal suspend fun resetForInstrumentation() {
        dataStore.edit { values -> values.clear() }
    }

    internal companion object {
        const val DEFAULT_FILE_NAME = "onboarding.preferences_pb"
        const val CURRENT_VERSION = 1
        val CompletedVersion = intPreferencesKey("completed_version")
        private val ExpectedKeys = setOf(CompletedVersion)
        private val EmptyPreferences = mutablePreferencesOf()

        internal fun decode(values: Preferences): OnboardingStoreState {
            if (values.asMap().isEmpty()) return OnboardingStoreState.Ready(completedVersion = 0)
            if (values.asMap().keys != ExpectedKeys) return OnboardingStoreState.Error
            val version = values[CompletedVersion] ?: return OnboardingStoreState.Error
            return if (version >= 0) {
                OnboardingStoreState.Ready(version)
            } else {
                OnboardingStoreState.Error
            }
        }
    }
}
