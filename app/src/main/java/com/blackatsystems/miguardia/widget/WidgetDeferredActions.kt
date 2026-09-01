package com.blackatsystems.miguardia.widget

import android.annotation.SuppressLint
import android.content.Context
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps identity-changing widget broadcasts durable while backup recovery owns local data.
 * Refresh-only broadcasts can be recomputed later; restored and deleted IDs cannot.
 */
@SuppressLint("UseKtx") // The boolean result of synchronous commit is part of the durability contract.
class WidgetDeferredActions(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )
    private val replayMutex = Mutex()

    fun enqueueRestoration(oldIds: IntArray, newIds: IntArray) {
        require(oldIds.size == newIds.size) { "La restauración del Widget recibió IDs desparejos." }
        val additions = oldIds.zip(newIds)
            .filter { (oldId, newId) -> oldId > 0 && newId > 0 }
            .mapTo(linkedSetOf()) { (oldId, newId) -> "$oldId:$newId" }
        if (additions.isEmpty()) return
        synchronized(preferences) {
            val pending = preferences.getStringSet(PENDING_RESTORATIONS, emptySet()).orEmpty() + additions
            if (!preferences.edit().putStringSet(PENDING_RESTORATIONS, pending).commit()) {
                throw IOException("No se pudo conservar la restauración pendiente del Widget.")
            }
        }
    }

    fun enqueueDeletion(ids: IntArray) {
        val additions = ids.filter { it > 0 }.mapTo(linkedSetOf(), Int::toString)
        if (additions.isEmpty()) return
        synchronized(preferences) {
            val pending = preferences.getStringSet(PENDING_DELETIONS, emptySet()).orEmpty() + additions
            if (!preferences.edit().putStringSet(PENDING_DELETIONS, pending).commit()) {
                throw IOException("No se pudo conservar la eliminación pendiente del Widget.")
            }
        }
    }

    suspend fun replay(runtime: WidgetRuntime) = replayMutex.withLock {
        val rawRestorations = preferences.getStringSet(PENDING_RESTORATIONS, emptySet()).orEmpty().toSet()
        val rawDeletions = preferences.getStringSet(PENDING_DELETIONS, emptySet()).orEmpty().toSet()
        val restorations = rawRestorations
            .mapNotNull(::parseRestoration)
            .sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val deletions = rawDeletions
            .mapNotNull(String::toIntOrNull)
            .filter { it > 0 }
            .distinct()
            .sorted()
        if (restorations.isNotEmpty()) {
            val oldIds = restorations.map { it.first }.toIntArray()
            val newIds = restorations.map { it.second }.toIntArray()
            runtime.registerRestoration(newIds)
            runtime.restoreNow(oldIds, newIds)
        }
        if (deletions.isNotEmpty()) runtime.deleteNow(deletions.toIntArray())
        if (rawRestorations.isNotEmpty() || rawDeletions.isNotEmpty()) {
            synchronized(preferences) {
                val remainingRestorations = preferences
                    .getStringSet(PENDING_RESTORATIONS, emptySet())
                    .orEmpty() - rawRestorations
                val remainingDeletions = preferences
                    .getStringSet(PENDING_DELETIONS, emptySet())
                    .orEmpty() - rawDeletions
                if (!preferences.edit()
                        .putStringSet(PENDING_RESTORATIONS, remainingRestorations)
                        .putStringSet(PENDING_DELETIONS, remainingDeletions)
                        .commit()
                ) {
                    throw IOException("No se pudieron cerrar las acciones pendientes del Widget.")
                }
            }
        }
    }

    internal fun hasPendingActions(): Boolean =
        preferences.getStringSet(PENDING_RESTORATIONS, emptySet()).orEmpty().isNotEmpty() ||
            preferences.getStringSet(PENDING_DELETIONS, emptySet()).orEmpty().isNotEmpty()

    private fun parseRestoration(value: String): Pair<Int, Int>? {
        val pieces = value.split(':')
        if (pieces.size != 2) return null
        val oldId = pieces[0].toIntOrNull() ?: return null
        val newId = pieces[1].toIntOrNull() ?: return null
        return (oldId to newId).takeIf { oldId > 0 && newId > 0 }
    }

    private companion object {
        const val FILE_NAME = "widget_deferred_actions"
        const val PENDING_RESTORATIONS = "pending_restorations"
        const val PENDING_DELETIONS = "pending_deletions"
    }
}
