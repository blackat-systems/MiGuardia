package com.blackatsystems.miguardia.widget

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuedWeatherRefreshesTest {
    @Test
    fun `request received during active batch is refreshed next and not lost`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondBatchRendered = CompletableDeferred<Unit>()
        val refreshed = Collections.synchronizedList(mutableListOf<String>())
        val queue = QueuedWeatherRefreshes(
            scope = workerScope,
            isEnabled = { true },
            refreshItem = { id: String ->
                refreshed += id
                if (id == "objetivo-a") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            },
            afterBatch = {
                if ("objetivo-b" in refreshed) secondBatchRendered.complete(Unit)
            },
        )

        queue.request(listOf("objetivo-a"))
        firstStarted.await()
        queue.request(listOf("objetivo-b", "objetivo-b"))
        releaseFirst.complete(Unit)
        withTimeout(2_000) { secondBatchRendered.await() }

        assertEquals(listOf("objetivo-a", "objetivo-b"), refreshed.toList())
        queue.cancel()
        workerScope.cancel()
    }

    @Test
    fun `cancelling one objective in a prepared batch does not discard the next one`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstItemJob = CompletableDeferred<Job>()
        val secondBatchRendered = CompletableDeferred<Unit>()
        val refreshed = Collections.synchronizedList(mutableListOf<String>())
        val queue = QueuedWeatherRefreshes(
            scope = workerScope,
            isEnabled = { true },
            refreshItem = { id: String ->
                refreshed += id
                if (id == "objetivo-a") {
                    firstItemJob.complete(checkNotNull(currentCoroutineContext()[Job]))
                    awaitCancellation()
                }
            },
            afterBatch = { secondBatchRendered.complete(Unit) },
        )

        queue.request(listOf("objetivo-a", "objetivo-b"))
        firstItemJob.await().cancel()
        withTimeout(2_000) { secondBatchRendered.await() }

        assertEquals(listOf("objetivo-a", "objetivo-b"), refreshed.toList())
        queue.cancel()
        workerScope.cancel()
    }
}
