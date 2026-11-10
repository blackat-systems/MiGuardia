package com.blackatsystems.miguardia.widget

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Etapa 14 · Widget de inicio: la cola con la que el motor pide Clima después de dibujar el evento
 * (`WidgetRuntime.kt:89-98` y `:322-332`). Es el mecanismo, no el motor completo.
 */
class Stage14WidgetWeatherQueueTest {
    // W022 / X013: pedir Clima vuelve enseguida aunque la descarga tarde; el redibujado llega después.
    @Test
    fun `a slow weather download never blocks the caller and the redraw comes afterwards`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val downloadStarted = CompletableDeferred<Unit>()
            val releaseDownload = CompletableDeferred<Unit>()
            val redrawn = CompletableDeferred<Unit>()
            val queue = QueuedWeatherRefreshes(
                scope = workerScope,
                isEnabled = { true },
                refreshItem = { _: String ->
                    downloadStarted.complete(Unit)
                    releaseDownload.await()
                },
                afterBatch = { redrawn.complete(Unit) },
            )

            // request() no es suspendible: si esperara la descarga, esta línea no volvería.
            queue.request(listOf("jornada-ficticia"))
            withTimeout(2_000) { downloadStarted.await() }
            assertFalse("No debe redibujar el Clima antes de que termine la descarga", redrawn.isCompleted)

            releaseDownload.complete(Unit)
            withTimeout(2_000) { redrawn.await() }
            queue.cancel()
        } finally {
            workerScope.cancel()
        }
    }

    // X013: un fallo de red en una jornada no impide las demás ni el redibujado posterior.
    @Test
    fun `a failed weather download is swallowed and the remaining items and redraw still happen`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val asked = Collections.synchronizedList(mutableListOf<String>())
            val redraws = AtomicInteger()
            val redrawn = CompletableDeferred<Unit>()
            val queue = QueuedWeatherRefreshes(
                scope = workerScope,
                isEnabled = { true },
                refreshItem = { id: String ->
                    asked += id
                    if (id == "sin-red") throw java.io.IOException("Sin red ficticia")
                },
                afterBatch = {
                    redraws.incrementAndGet()
                    redrawn.complete(Unit)
                },
            )

            queue.request(listOf("sin-red", "con-red"))
            withTimeout(2_000) { redrawn.await() }

            assertEquals(listOf("sin-red", "con-red"), asked.toList())
            assertEquals(1, redraws.get())
            queue.cancel()
        } finally {
            workerScope.cancel()
        }
    }

    // W024 (pausa por restauración): con la cola deshabilitada no se pide Clima ni se redibuja.
    @Test
    fun `a disabled queue never asks for weather`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val asked = AtomicInteger()
            val redraws = AtomicInteger()
            val queue = QueuedWeatherRefreshes(
                scope = workerScope,
                isEnabled = { false },
                refreshItem = { _: String -> asked.incrementAndGet() },
                afterBatch = { redraws.incrementAndGet() },
            )

            queue.request(listOf("jornada-ficticia"))
            delay(200)

            assertEquals(0, asked.get())
            assertEquals(0, redraws.get())
            queue.cancel()
        } finally {
            workerScope.cancel()
        }
    }
}
