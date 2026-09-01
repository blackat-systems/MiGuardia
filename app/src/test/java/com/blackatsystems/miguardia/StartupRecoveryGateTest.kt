package com.blackatsystems.miguardia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupRecoveryGateTest {
    @Test
    fun `data stays blocked until recovery and runtimes are ready`() {
        val gate = StartupRecoveryGate()

        assertEquals(StartupRecoveryState.Recovering, gate.state.value)
        assertFalse(gate.isReady)

        gate.ready()

        assertEquals(StartupRecoveryState.Ready, gate.state.value)
        assertTrue(gate.isReady)
    }

    @Test
    fun `failure remains blocked and a retry has explicit transitions`() {
        val gate = StartupRecoveryGate(StartupRecoveryState.Ready)

        gate.failed("Recuperación pendiente")
        assertEquals(
            StartupRecoveryState.Failed("Recuperación pendiente"),
            gate.state.value,
        )
        assertFalse(gate.isReady)

        gate.recovering()
        assertEquals(StartupRecoveryState.Recovering, gate.state.value)
        assertFalse(gate.isReady)

        gate.ready()
        assertTrue(gate.isReady)
    }
}
