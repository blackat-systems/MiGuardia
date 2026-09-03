package com.blackatsystems.miguardia.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class QueuedWeatherRefreshes<T>(
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val refreshItem: suspend (T) -> Unit,
    private val afterBatch: suspend () -> Unit,
) {
    private val lock = Any()
    private val pending = linkedSetOf<T>()
    private var worker: Job? = null

    fun request(values: Collection<T>) {
        if (!isEnabled()) return
        synchronized(lock) {
            pending.addAll(values)
            if (pending.isNotEmpty()) ensureWorkerLocked()
        }
    }

    fun cancel() {
        synchronized(lock) {
            pending.clear()
            worker?.cancel()
            worker = null
        }
    }

    private fun ensureWorkerLocked() {
        if (worker?.isActive == true || !isEnabled()) return
        val newWorker = scope.launch(start = CoroutineStart.LAZY) {
            val currentWorker = checkNotNull(currentCoroutineContext()[Job])
            try {
                drain()
            } finally {
                synchronized(lock) {
                    if (worker === currentWorker) {
                        worker = null
                        if (pending.isNotEmpty() && isEnabled()) ensureWorkerLocked()
                    }
                }
            }
        }
        worker = newWorker
        newWorker.start()
    }

    private suspend fun drain() {
        while (true) {
            val batch = synchronized(lock) {
                if (pending.isEmpty()) return
                pending.toList().also { pending.clear() }
            }
            batch.forEach { item ->
                try {
                    supervisorScope {
                        async { refreshItem(item) }.await()
                    }
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                } catch (_: Exception) {
                    // El widget local ya está renderizado; un objetivo fallido no bloquea los demás.
                }
            }
            try {
                afterBatch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Una relectura fallida no debe descartar solicitudes que llegaron durante este lote.
            }
        }
    }
}
