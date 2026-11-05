package com.blackatsystems.miguardia.stage12

/** Finite public observations only. Never retain exception messages, causes or private paths. */
internal object Stage12NativeDiagnostics {
    enum class ReadbackPhase {
        NOT_STARTED, CONTEXT, INVENTORY_ROOT, INVENTORY_BEFORE, PREFERENCES,
        DATABASE_OPEN, DATABASE_TRANSACTION, DATABASE_SCHEMA, DATABASE_ROWS,
        DATABASE_TRANSACTION_END, DATABASE_CLOSE, INVENTORY_REVERIFY, INVENTORY_AFTER,
        INVENTORY_COMPARE, CAPTURE_RETURNED, PROJECTION, PROJECTION_RECORDED,
    }

    private val readbackCodes = setOf(
        "READBACK_CAPTURE_FAILED", "READBACK_CONTEXT_NOT_QA", "READBACK_DATABASE_NOT_EXISTING",
        "READBACK_FILES_CHANGED_DURING_SNAPSHOT", "READBACK_DATABASE_NOT_READONLY",
        "READBACK_READONLY_TRANSACTION_API_UNAVAILABLE", "READBACK_WRONG_DATABASE_SCHEMA",
        "READBACK_UNKNOWN_SQLITE_TYPE", "READBACK_PREFERENCE_MAP_UNAVAILABLE",
        "READBACK_PREFERENCE_KEY_TYPE", "READBACK_NULL_PREFERENCE",
        "READBACK_PREFERENCE_TYPE_UNAVAILABLE", "READBACK_PREFERENCE_BYTES_UNAVAILABLE",
        "READBACK_UNTYPED_PREFERENCE", "READBACK_DUPLICATE_SHARED_PREFERENCE",
        "READBACK_PROJECTION_FAILED",
    )

    fun readbackCodeOrNull(message: String?): String? = message?.takeIf { it in readbackCodes }
    fun readbackCode(message: String?): String = readbackCodeOrNull(message) ?: "READBACK_CAPTURE_FAILED"

    class ReadbackAttempt {
        var phase = ReadbackPhase.NOT_STARTED
            private set
        var captureReturned = false
            private set
        var projectionRecorded = false
            private set
        var failurePhase: ReadbackPhase? = null
            private set
        var failureCode: String? = null
            private set

        fun reached(value: ReadbackPhase) { phase = value }
        fun returned() { captureReturned = true; phase = ReadbackPhase.CAPTURE_RETURNED }
        fun projecting() { phase = ReadbackPhase.PROJECTION }
        fun recorded() { projectionRecorded = true; phase = ReadbackPhase.PROJECTION_RECORDED }
        fun failed(error: Throwable) {
            if (failureCode == null) {
                failurePhase = phase
                failureCode = readbackCodeOrNull(error.message)
                    ?: if (phase == ReadbackPhase.PROJECTION) "READBACK_PROJECTION_FAILED" else "READBACK_CAPTURE_FAILED"
            }
        }
    }
}
