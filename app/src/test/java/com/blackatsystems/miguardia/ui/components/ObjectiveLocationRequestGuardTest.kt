package com.blackatsystems.miguardia.ui.components

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ObjectiveLocationRequestGuardTest {
    @Test
    fun `saving message disappears once the location is available again`() {
        assertEquals(
            "Guardando ubicación…",
            visibleLocationCaptureMessage("Guardando ubicación…", hasSavedLocation = true, enabled = false),
        )
        assertNull(
            visibleLocationCaptureMessage("Guardando ubicación…", hasSavedLocation = true, enabled = true),
        )
        assertEquals(
            "No pudimos guardar la ubicación.",
            visibleLocationCaptureMessage(
                "No pudimos guardar la ubicación.",
                hasSavedLocation = true,
                enabled = true,
            ),
        )
    }

    @Test
    fun `a newer request makes an older callback harmless`() {
        val guard = ObjectiveLocationRequestGuard()
        val first = guard.begin()
        val second = guard.begin()

        assertFalse(guard.finish(first))
        assertTrue(guard.finish(second))
        assertFalse(guard.finish(second))
    }

    @Test
    fun `disposing the surface makes every pending callback harmless`() {
        val guard = ObjectiveLocationRequestGuard()
        val pending = guard.begin()

        guard.invalidate()

        assertFalse(guard.finish(pending))
        assertFalse(guard.finish(guard.begin()))
    }

    @Test
    fun `changing the address invalidates its pending lookup without disposing the surface`() {
        val guard = ObjectiveLocationRequestGuard()
        val oldAddressLookup = guard.begin()

        guard.supersede()

        assertFalse(guard.finish(oldAddressLookup))
        assertTrue(guard.finish(guard.begin()))
    }

    @Test
    fun `an address lookup that never answers fails within its bounded wait`() = runBlocking {
        try {
            boundedObjectiveLocationLookup<Unit>(timeoutMillis = 25L) { awaitCancellation() }
            fail("La búsqueda debía finalizar con un error recuperable.")
        } catch (error: IOException) {
            assertEquals(
                "El servicio de direcciones tardó demasiado en responder.",
                error.message,
            )
        }
    }

    @Test
    fun `legacy lookup returns on timeout even when its blocking call ignores interruption`() = runBlocking {
        val releaseBlockingCall = CountDownLatch(1)
        val started = CountDownLatch(1)
        try {
            boundedBlockingObjectiveLocationLookup<Unit>(timeoutMillis = 50L) {
                started.countDown()
                var released = false
                while (!released) {
                    try {
                        released = releaseBlockingCall.await(10L, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        // Keep waiting to emulate an OEM Binder that ignores Thread.interrupt.
                    }
                }
            }
            fail("La búsqueda síncrona debía finalizar con un error recuperable.")
        } catch (error: IOException) {
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            assertEquals(
                "El servicio de direcciones tardó demasiado en responder.",
                error.message,
            )
        } finally {
            releaseBlockingCall.countDown()
        }
    }
}
