package com.blackatsystems.miguardia.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class WidgetInstancePreferences(
    val mode: WidgetMode = WidgetMode.AUTOMATIC,
    val privacy: WidgetPrivacy = WidgetPrivacy.HIDDEN,
    val includeWeather: Boolean = false,
    val configured: Boolean = false,
)

class WidgetPreferencesStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
        ),
    )

    internal constructor(file: File, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { file },
        ),
    )

    private val safeData = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
    val instances: Flow<Map<Int, WidgetInstancePreferences>> = safeData.map(::decodeAll)

    suspend fun current(appWidgetId: Int): WidgetInstancePreferences {
        if (appWidgetId <= 0) return SafeDefault
        return decodeWidgetPreferences(safeData.first(), appWidgetId)
    }

    suspend fun all(): Map<Int, WidgetInstancePreferences> = instances.first()

    suspend fun save(appWidgetId: Int, preferences: WidgetInstancePreferences) {
        requireValidId(appWidgetId)
        dataStore.edit { values ->
            values[KnownIds] = values[KnownIds].orEmpty() + appWidgetId.toString()
            values.write(appWidgetId, preferences)
        }
    }

    suspend fun delete(appWidgetIds: Collection<Int>) {
        val validIds = appWidgetIds.filter { it > 0 }.toSet()
        if (validIds.isEmpty()) return
        dataStore.edit { values ->
            validIds.forEach { id -> values.removeInstance(id) }
            values[KnownIds] = values[KnownIds].orEmpty() - validIds.map(Int::toString).toSet()
        }
    }

    /** Atomically transfers only exact old-id configurations and clears the old ids. */
    suspend fun remap(oldIds: IntArray, newIds: IntArray) {
        require(oldIds.size == newIds.size) { "La restauración del Widget recibió IDs desparejos." }
        val pairs = oldIds.zip(newIds).filter { (oldId, newId) -> oldId > 0 && newId > 0 }
        if (pairs.isEmpty()) return
        dataStore.edit { values ->
            val knownBefore = values[KnownIds].orEmpty()
            val originals = pairs.associate { (oldId, newId) ->
                val selected = when {
                    oldId.toString() in knownBefore -> decodeWidgetPreferences(values, oldId)
                    newId.toString() in knownBefore -> decodeWidgetPreferences(values, newId)
                    else -> SafeDefault
                }
                oldId to selected
            }
            pairs.map { it.first }.toSet().forEach { id -> values.removeInstance(id) }
            val known = values[KnownIds].orEmpty().toMutableSet().apply {
                removeAll(pairs.map { it.first.toString() }.toSet())
                addAll(pairs.map { it.second.toString() })
            }
            pairs.forEach { (oldId, newId) ->
                values.write(newId, originals.getValue(oldId))
            }
            values[KnownIds] = known
        }
    }

    private fun decodeAll(values: Preferences): Map<Int, WidgetInstancePreferences> =
        values[KnownIds].orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filter { it > 0 }
            .distinct()
            .sorted()
            .associateWith { id -> decodeWidgetPreferences(values, id) }

    private fun MutablePreferences.write(id: Int, preferences: WidgetInstancePreferences) {
        this[modeKey(id)] = preferences.mode.name
        this[privacyKey(id)] = preferences.privacy.name
        this[weatherKey(id)] = preferences.includeWeather
        this[configuredKey(id)] = preferences.configured
    }

    private fun MutablePreferences.removeInstance(id: Int) {
        remove(modeKey(id))
        remove(privacyKey(id))
        remove(weatherKey(id))
        remove(configuredKey(id))
    }

    private fun requireValidId(id: Int) = require(id > 0) { "El ID del Widget no es válido." }

    internal companion object {
        const val DEFAULT_FILE_NAME = "widget_preferences.preferences_pb"
        val SafeDefault = WidgetInstancePreferences()
        val KnownIds = stringSetPreferencesKey("known_widget_ids")
        fun modeKey(id: Int) = stringPreferencesKey("widget_${id}_mode")
        fun privacyKey(id: Int) = stringPreferencesKey("widget_${id}_privacy")
        fun weatherKey(id: Int) = booleanPreferencesKey("widget_${id}_include_weather")
        fun configuredKey(id: Int) = booleanPreferencesKey("widget_${id}_configured")
    }
}

internal fun decodeWidgetPreferences(values: Preferences, id: Int): WidgetInstancePreferences {
    val configured = values[WidgetPreferencesStore.configuredKey(id)] ?: false
    val mode = values[WidgetPreferencesStore.modeKey(id)]
        ?.let { runCatching { WidgetMode.valueOf(it) }.getOrNull() }
    val privacy = values[WidgetPreferencesStore.privacyKey(id)]
        ?.let { runCatching { WidgetPrivacy.valueOf(it) }.getOrNull() }
    if (!configured || mode == null || privacy == null) return WidgetPreferencesStore.SafeDefault
    return WidgetInstancePreferences(
        mode = mode,
        privacy = privacy,
        includeWeather = values[WidgetPreferencesStore.weatherKey(id)] ?: false,
        configured = true,
    )
}
