package com.blackatsystems.miguardia.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

internal sealed interface AccessLockStoreRead {
    data class Ready(val configuration: AccessLockConfiguration) : AccessLockStoreRead
    data object Error : AccessLockStoreRead
}

internal class AccessLockPreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { CorruptPreferences },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
        ),
    )

    internal constructor(file: File, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { CorruptPreferences },
            scope = scope,
            produceFile = { file },
        ),
    )

    suspend fun read(): AccessLockStoreRead = try {
        decode(dataStore.data.first())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AccessLockStoreRead.Error
    }

    suspend fun replace(configuration: AccessLockConfiguration) {
        replaceIfAuthorized(configuration) { true }
    }

    suspend fun replaceIfAuthorized(
        configuration: AccessLockConfiguration,
        authorizationIsCurrent: () -> Boolean,
    ): Boolean {
        var replaced = false
        dataStore.edit { values ->
            if (!authorizationIsCurrent()) return@edit
            values.clear()
            values[ContractVersion] = CURRENT_VERSION
            values[Enabled] = configuration.enabled
            values[Timeout] = configuration.timeout.name
            replaced = true
        }
        return replaced
    }

    suspend fun repair() = replace(AccessLockConfiguration())

    internal companion object {
        const val DEFAULT_FILE_NAME = "access_lock.preferences_pb"
        const val CURRENT_VERSION = 1
        val ContractVersion = intPreferencesKey("contract_version")
        val Enabled = booleanPreferencesKey("enabled")
        val Timeout = stringPreferencesKey("timeout")
        private val ExpectedKeys = setOf(ContractVersion, Enabled, Timeout)
        private val CorruptPreferences = mutablePreferencesOf(
            ContractVersion to Int.MIN_VALUE,
            Enabled to true,
            Timeout to "CORRUPT",
        )

        internal fun decode(values: Preferences): AccessLockStoreRead {
            if (values.asMap().isEmpty()) {
                return AccessLockStoreRead.Ready(AccessLockConfiguration())
            }
            if (values.asMap().keys != ExpectedKeys) return AccessLockStoreRead.Error
            if (values[ContractVersion] != CURRENT_VERSION) return AccessLockStoreRead.Error
            val enabled = values[Enabled] ?: return AccessLockStoreRead.Error
            val timeout = values[Timeout]
                ?.let { stored -> AccessLockTimeout.entries.firstOrNull { it.name == stored } }
                ?: return AccessLockStoreRead.Error
            return AccessLockStoreRead.Ready(AccessLockConfiguration(enabled, timeout))
        }
    }
}
