package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12TemporalMainCreationTest {
    @Test fun terminalMissingAndMixedSelectionStopBeforeAnyConstruction() {
        for (exclusive in listOf(false, true)) for (active in listOf(false, true))
            for (finished in listOf(false, true)) for (graph in listOf(false, true)) {
                var calls = 0
                val result = runCatching { Stage12TemporalMainCreation.construct(true, true, exclusive, active, finished, graph) { calls++; "actual" } }
                if (exclusive && active && !finished && graph) {
                    assertEquals("actual", result.getOrThrow()); assertEquals(1, calls)
                } else {
                    assertTrue(result.exceptionOrNull() is IllegalStateException); assertEquals(0, calls)
                }
            }
    }
    @Test fun ordinaryAndHostConstructionKeepTheirRealDelegateAndOriginalFailure() {
        val original = IllegalArgumentException("original")
        for ((isMain, selected) in listOf(true to false, false to true, false to false)) {
            var calls = 0
            assertSame(original, runCatching {
                Stage12TemporalMainCreation.construct(isMain, selected, false, false, true, false) { calls++; throw original }
            }.exceptionOrNull())
            assertEquals(1, calls)
        }
    }
}
