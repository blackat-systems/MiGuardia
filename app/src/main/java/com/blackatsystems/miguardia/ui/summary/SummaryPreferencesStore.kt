package com.blackatsystems.miguardia.ui.summary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SummaryPreferences(
    val orderedFamilies: List<SummaryOptionalFamily> = SummaryOptionalFamily.entries,
    val hiddenFamilies: Set<SummaryOptionalFamily> = emptySet(),
    val introSeen: Boolean = false,
) {
    init {
        require(
            orderedFamilies.size == SummaryOptionalFamily.entries.size &&
                orderedFamilies.distinct().size == orderedFamilies.size &&
                orderedFamilies.toSet() == SummaryOptionalFamily.entries.toSet(),
        ) {
            "El orden del Resumen debe contener cada familia vigente exactamente una vez"
        }
        require(hiddenFamilies.all { it in SummaryOptionalFamily.entries }) {
            "El Resumen sólo puede ocultar familias vigentes"
        }
    }

    fun isVisible(family: SummaryOptionalFamily): Boolean = family !in hiddenFamilies
}

internal fun normalizeSummaryPreferences(
    orderStorage: String?,
    hiddenStorage: Set<String>?,
    introSeen: Boolean?,
): SummaryPreferences {
    val knownByName = SummaryOptionalFamily.entries.associateBy { it.name }
    val normalizedOrder = buildList {
        orderStorage
            ?.split(ORDER_SEPARATOR)
            ?.map(String::trim)
            ?.mapNotNull(knownByName::get)
            ?.forEach { family -> if (family !in this) add(family) }
        SummaryOptionalFamily.entries.forEach { family -> if (family !in this) add(family) }
    }
    val hidden = hiddenStorage
        .orEmpty()
        .mapNotNull(knownByName::get)
        .toSet()
    return SummaryPreferences(
        orderedFamilies = normalizedOrder,
        hiddenFamilies = hidden,
        introSeen = introSeen ?: false,
    )
}

class SummaryPreferencesStore internal constructor(
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

    val preferences: Flow<SummaryPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toPreferences)

    suspend fun current(): SummaryPreferences = preferences.first()

    /** Backup and recovery must abort instead of silently exporting defaults on I/O failure. */
    internal suspend fun currentForBackup(): SummaryPreferences = toPreferences(dataStore.data.first())

    suspend fun replacePortable(preferences: SummaryPreferences) {
        dataStore.edit { values ->
            values[OrderedFamilies] = preferences.orderedFamilies.toStorage()
            values[HiddenFamilies] = preferences.hiddenFamilies.mapTo(linkedSetOf()) { it.name }
            values[IntroSeen] = preferences.introSeen
        }
    }

    suspend fun setVisible(family: SummaryOptionalFamily, visible: Boolean) {
        dataStore.edit { values ->
            val current = toPreferences(values)
            val hidden = current.hiddenFamilies.toMutableSet()
            if (visible) hidden.remove(family) else hidden.add(family)
            values[HiddenFamilies] = hidden.mapTo(linkedSetOf()) { it.name }
            values[OrderedFamilies] = current.orderedFamilies.toStorage()
        }
    }

    suspend fun move(family: SummaryOptionalFamily, offset: Int) {
        require(offset == -1 || offset == 1) { "Una familia sólo puede moverse una posición" }
        dataStore.edit { values ->
            val current = toPreferences(values)
            val order = current.orderedFamilies.toMutableList()
            val index = order.indexOf(family)
            val destination = (index + offset).coerceIn(order.indices)
            if (destination != index) {
                order[index] = order[destination]
                order[destination] = family
            }
            values[OrderedFamilies] = order.toStorage()
            values[HiddenFamilies] = current.hiddenFamilies.mapTo(linkedSetOf()) { it.name }
        }
    }

    suspend fun markIntroSeen() {
        dataStore.edit { values ->
            val current = toPreferences(values)
            values[IntroSeen] = true
            values[OrderedFamilies] = current.orderedFamilies.toStorage()
            values[HiddenFamilies] = current.hiddenFamilies.mapTo(linkedSetOf()) { it.name }
        }
    }

    private fun toPreferences(values: Preferences): SummaryPreferences = normalizeSummaryPreferences(
        orderStorage = values[OrderedFamilies],
        hiddenStorage = values[HiddenFamilies],
        introSeen = values[IntroSeen],
    )

    private companion object {
        const val DEFAULT_FILE_NAME = "summary_preferences.preferences_pb"
        val OrderedFamilies = stringPreferencesKey("ordered_optional_families")
        val HiddenFamilies = stringSetPreferencesKey("hidden_optional_families")
        val IntroSeen = booleanPreferencesKey("personalization_intro_seen")
    }
}

private fun List<SummaryOptionalFamily>.toStorage(): String = joinToString(ORDER_SEPARATOR) { it.name }

private const val ORDER_SEPARATOR = "|"
