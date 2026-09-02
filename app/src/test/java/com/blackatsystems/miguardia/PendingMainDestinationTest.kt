package com.blackatsystems.miguardia

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingMainDestinationTest {
    @Test
    fun `incoming destination keeps only the minimal identifier for its action`() {
        val shift = PendingMainDestination.from(
            action = MainActivity.ACTION_VIEW_SHIFT,
            shiftId = "shift-id",
            ownerLocalDate = "2030-01-02",
        )
        val date = PendingMainDestination.from(
            action = MainActivity.ACTION_VIEW_DATE,
            shiftId = "must-not-survive",
            ownerLocalDate = "2030-01-02",
        )
        val calendar = PendingMainDestination.from(
            action = MainActivity.ACTION_OPEN_CALENDAR,
            shiftId = "must-not-survive",
        )

        assertEquals("shift-id", shift!!.shiftId)
        assertNull(shift.ownerLocalDate)
        assertEquals("2030-01-02", date!!.ownerLocalDate)
        assertNull(date.shiftId)
        assertNull(calendar!!.shiftId)
        assertNull(calendar.ownerLocalDate)
    }

    @Test
    fun `unknown intent is not retained`() {
        assertNull(PendingMainDestination.from(action = "external.unknown"))
    }

    @Test
    fun `cancelled resolution keeps the sanitized destination for the recreated host`() = runBlocking {
        val coordinator = PendingMainDestinationCoordinator()
        val request = coordinator.capture(
            PendingMainDestination(MainActivity.ACTION_VIEW_SHIFT, shiftId = "shift-id"),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstHost = launch {
            coordinator.consume(request) {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()

        firstHost.cancelAndJoin()

        assertEquals(request, coordinator.state.value)
        var terminalExecutions = 0
        assertEquals(
            true,
            coordinator.consume(request) { terminalExecutions++ },
        )
        assertEquals(1, terminalExecutions)
        assertNull(coordinator.state.value)
    }

    @Test
    fun `older resolution never clears a newer incoming destination`() = runBlocking {
        val coordinator = PendingMainDestinationCoordinator()
        val first = coordinator.capture(
            PendingMainDestination(MainActivity.ACTION_VIEW_SHIFT, shiftId = "first"),
        )
        val secondDestination = PendingMainDestination(
            MainActivity.ACTION_VIEW_DATE,
            ownerLocalDate = "2030-01-03",
        )

        assertEquals(
            false,
            coordinator.consume(first) { coordinator.capture(secondDestination) },
        )
        val second = coordinator.state.value
        assertEquals(secondDestination, second?.destination)
        assertEquals(true, coordinator.consume(requireNotNull(second)) {})
        assertNull(coordinator.state.value)
    }

    @Test
    fun `gate closing before resolution keeps the destination pending`() = runBlocking {
        val coordinator = PendingMainDestinationCoordinator()
        val request = coordinator.capture(
            PendingMainDestination(MainActivity.ACTION_VIEW_SHIFT, shiftId = "shift-id"),
        )

        try {
            coordinator.consume(request) {
                throw CancellationException("access lock closed")
            }
        } catch (_: CancellationException) {
            // The Activity effect is cancelled and retries the same request after unlocking.
        }

        assertEquals(request, coordinator.state.value)
    }
}
