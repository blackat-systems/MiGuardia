package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12NativeDiagnosticsTest {
    @Test fun onlyExactPublicReadbackCodesCanBeReported() {
        assertEquals("READBACK_FILES_CHANGED_DURING_SNAPSHOT",
            Stage12NativeDiagnostics.readbackCode("READBACK_FILES_CHANGED_DURING_SNAPSHOT"))
        for (message in listOf(null, "", "READBACK_PRIVATE_IDENTIFIER", "READBACK_CAPTURE_FAILED: PRIVATE_PATH")) {
            assertNull(Stage12NativeDiagnostics.readbackCodeOrNull(message))
            assertEquals("READBACK_CAPTURE_FAILED", Stage12NativeDiagnostics.readbackCode(message))
        }
    }

    @Test fun captureReturnAndProjectionAreDifferentObservedBoundaries() {
        val attempt = Stage12NativeDiagnostics.ReadbackAttempt()
        assertFalse(attempt.captureReturned); assertFalse(attempt.projectionRecorded)
        attempt.reached(Stage12NativeDiagnostics.ReadbackPhase.DATABASE_ROWS)
        attempt.returned()
        assertTrue(attempt.captureReturned); assertFalse(attempt.projectionRecorded)
        attempt.projecting(); attempt.recorded()
        assertTrue(attempt.projectionRecorded); assertNull(attempt.failureCode)
    }

    @Test fun failedCaptureKeepsItsFirstPhaseCodeAndNoSuccessClaims() {
        val attempt = Stage12NativeDiagnostics.ReadbackAttempt()
        attempt.reached(Stage12NativeDiagnostics.ReadbackPhase.PREFERENCES)
        attempt.failed(IllegalStateException("READBACK_PREFERENCE_MAP_UNAVAILABLE"))
        attempt.reached(Stage12NativeDiagnostics.ReadbackPhase.DATABASE_CLOSE)
        attempt.failed(IllegalStateException("PRIVATE_SECONDARY"))
        assertEquals(Stage12NativeDiagnostics.ReadbackPhase.PREFERENCES, attempt.failurePhase)
        assertEquals("READBACK_PREFERENCE_MAP_UNAVAILABLE", attempt.failureCode)
        assertFalse(attempt.captureReturned); assertFalse(attempt.projectionRecorded)
    }

    @Test fun projectionFailureDoesNotPretendTheCaptureFailedToReturn() {
        val attempt = Stage12NativeDiagnostics.ReadbackAttempt()
        attempt.returned(); attempt.projecting(); attempt.failed(IllegalArgumentException("PRIVATE_VALUE"))
        assertTrue(attempt.captureReturned); assertFalse(attempt.projectionRecorded)
        assertEquals(Stage12NativeDiagnostics.ReadbackPhase.PROJECTION, attempt.failurePhase)
        assertEquals("READBACK_PROJECTION_FAILED", attempt.failureCode)
    }
}
