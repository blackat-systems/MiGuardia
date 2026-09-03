package com.blackatsystems.miguardia.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
