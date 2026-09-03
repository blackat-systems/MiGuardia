package com.blackatsystems.miguardia.notifications

import android.annotation.SuppressLint
import android.content.Context
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps notification identities durable while startup recovery pauses the runtime. */
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

    fun enqueueDelivery(rawBoundaryKey: String) {
        val boundaryKey = requireNotNull(
            AndroidShiftAlarmScheduler.decodeOpaqueKey(rawBoundaryKey),
        ) { "La identidad del aviso pendiente no es válida." }.opaqueKey
        synchronized(preferences) {
            val pending = preferences.getStringSet(PENDING_DELIVERIES, emptySet()).orEmpty().toSet()
            if (boundaryKey !in pending && pending.size >= MAX_PENDING_DELIVERIES) {
                throw IOException("Hay demasiados avisos pendientes durante la recuperación.")
            }
            if (!preferences.edit().putStringSet(PENDING_DELIVERIES, pending + boundaryKey).commit()) {
                throw IOException("No se pudo conservar el aviso pendiente durante la recuperación.")
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

    /**
     * Replays only validated boundary identities. A normal return means that the runtime either
     * delivered the still-current boundary or rejected it deterministically after re-reading V2.
     * Exceptional results stay queued for a later retry.
     */
    suspend fun replayDeliveries(
        deliver: suspend (NotificationBoundaryIdentity) -> Unit,
    ): Boolean = replayMutex.withLock {
        val captured = preferences.getStringSet(PENDING_DELIVERIES, emptySet()).orEmpty().toSet()
        var allResolved = true
        captured.sorted().forEach { boundaryKey ->
            val identity = AndroidShiftAlarmScheduler.decodeOpaqueKey(boundaryKey)
            if (identity == null) {
                removePendingDelivery(boundaryKey)
                return@forEach
            }
            try {
                deliver(identity)
                removePendingDelivery(boundaryKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                allResolved = false
            }
        }
        allResolved
    }

    /**
     * Gives transient local failures a short bounded recovery window. Unresolved identities remain
     * durable for the next startup or alarm; this never becomes a polling loop.
     */
    suspend fun replayDeliveriesWithRetry(
        maxAttempts: Int = DEFAULT_DELIVERY_REPLAY_ATTEMPTS,
        retryDelayMillis: Long = DEFAULT_DELIVERY_REPLAY_DELAY_MILLIS,
        deliver: suspend (NotificationBoundaryIdentity) -> Unit,
    ): Boolean {
        require(maxAttempts > 0)
        require(retryDelayMillis >= 0L)
        repeat(maxAttempts) { attempt ->
            if (replayDeliveries(deliver)) return true
            if (attempt < maxAttempts - 1 && retryDelayMillis > 0L) delay(retryDelayMillis)
        }
        return false
    }

    internal fun hasPendingActions(): Boolean =
        preferences.getStringSet(PENDING_DISMISSALS, emptySet()).orEmpty().isNotEmpty() ||
            preferences.getStringSet(PENDING_DELIVERIES, emptySet()).orEmpty().isNotEmpty()

    private fun removePendingDelivery(boundaryKey: String) {
        synchronized(preferences) {
            val remaining = preferences.getStringSet(PENDING_DELIVERIES, emptySet()).orEmpty() - boundaryKey
            if (!preferences.edit().putStringSet(PENDING_DELIVERIES, remaining).commit()) {
                throw IOException("No se pudo cerrar el aviso pendiente después de procesarlo.")
            }
        }
    }

    private companion object {
        const val FILE_NAME = "notification_deferred_actions"
        const val PENDING_DISMISSALS = "pending_dismissals"
        const val PENDING_DELIVERIES = "pending_deliveries"
        const val MAX_PENDING_DELIVERIES = 256
        const val DEFAULT_DELIVERY_REPLAY_ATTEMPTS = 3
        const val DEFAULT_DELIVERY_REPLAY_DELAY_MILLIS = 250L
    }
}
