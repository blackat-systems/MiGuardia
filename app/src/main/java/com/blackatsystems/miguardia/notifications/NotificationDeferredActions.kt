package com.blackatsystems.miguardia.notifications

import android.annotation.SuppressLint
import android.content.Context
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps user dismissals durable while restore temporarily pauses the notification runtime. */
@SuppressLint("UseKtx") // The boolean result of synchronous commit is part of the durability contract.
class NotificationDeferredActions(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )
    private val replayMutex = Mutex()

    fun enqueueDismissal(rawEventKey: String) {
        val eventKey = requireNotNull(NextEventIdentity.parseTrackingKey(rawEventKey)) {
            "La identidad del aviso descartado no es válida."
        }.trackingKey
        synchronized(preferences) {
            val pending = preferences.getStringSet(PENDING_DISMISSALS, emptySet()).orEmpty() + eventKey
            if (!preferences.edit().putStringSet(PENDING_DISMISSALS, pending).commit()) {
                throw IOException("No se pudo conservar el aviso descartado durante la recuperación.")
            }
        }
    }

    suspend fun replay(store: NotificationPreferencesStore) = replayMutex.withLock {
        val captured = preferences.getStringSet(PENDING_DISMISSALS, emptySet()).orEmpty().toSet()
        captured.sorted().forEach { eventKey -> store.markDismissed(eventKey) }
        if (captured.isNotEmpty()) {
            synchronized(preferences) {
                val remaining = preferences.getStringSet(PENDING_DISMISSALS, emptySet()).orEmpty() - captured
                if (!preferences.edit().putStringSet(PENDING_DISMISSALS, remaining).commit()) {
                    throw IOException("No se pudieron cerrar los avisos descartados pendientes.")
                }
            }
        }
    }

    internal fun hasPendingActions(): Boolean =
        preferences.getStringSet(PENDING_DISMISSALS, emptySet()).orEmpty().isNotEmpty()

    private companion object {
        const val FILE_NAME = "notification_deferred_actions"
        const val PENDING_DISMISSALS = "pending_dismissals"
    }
}
