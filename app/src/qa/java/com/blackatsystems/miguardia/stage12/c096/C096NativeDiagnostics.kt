package com.blackatsystems.miguardia.stage12.c096

/** Finite public observations only. Never retain exception messages, causes or private paths. */
internal object C096NativeDiagnostics {
    /** Closed, content-free milestones shared only by this process's owned image route. */
    enum class ImageEdge {
        NOT_STARTED, BUTTON_READY, BUTTON_TOUCH, LAUNCH_ENTERED, LAUNCH_RETURNED,
        PICKER_CREATED, HUMAN_PNG_SELECTED, RESULT_SET, PICKER_FINISHED,
        CALLBACK_RECEIVED, RESULT_EMPTY, URI_EXACT, IMPORT_ENTERED, IMPORT_RETURNED,
        TYPE_ENTERED, TYPE_RETURNED, OPEN_ENTERED, OPEN_RETURNED,
        IMPORT_PENDING, IMPORT_REJECTED, ATTACHMENT_CONFIRMED,
    }
    val imageCodes = setOf(
        "C096_IMAGE_RESULT_EMPTY", "C096_IMAGE_RESULT_REJECTED", "C096_IMAGE_LAUNCH_FAILED",
        "C096_IMAGE_PICKER_FAILED", "C096_IMAGE_PROVIDER_REJECTED", "C096_IMAGE_IMPORT_REJECTED",
        "C096_IMAGE_IMPORT_NOT_STARTED", "C096_IMAGE_AFTER_FAILURE",
    )
    class ImageFailure(val code:String):IllegalStateException() {
        init { require(code in imageCodes) }
    }
    class ImageAttempt {
        private val edges=linkedSetOf(ImageEdge.NOT_STARTED)
        private var firstCode:String?=null
        private var stopped=false
        private var humanRun:String?=null
        @Synchronized fun active(){if(firstCode!=null || stopped)throw ImageFailure("C096_IMAGE_AFTER_FAILURE")}
        @Synchronized fun reached(edge:ImageEdge){active();edges+=edge}
        @Synchronized fun failed(code:String){require(code in imageCodes);if(!stopped && firstCode==null)firstCode=code}
        /** Host retention and ledger closure share failed()'s exact linearization monitor. */
        @Synchronized fun retainFirst(retainHost:()->Unit){stopped=true;retainHost()}
        @Synchronized fun humanSelection(run:String){
            active();check(run==C096Variant.EDIT_DRAFT.run && humanRun==null && ImageEdge.PICKER_CREATED in edges)
            humanRun=run;edges+=ImageEdge.HUMAN_PNG_SELECTED
        }
        @Synchronized fun initialInput(run:String):org.json.JSONObject {
            check(humanRun==null || humanRun==run)
            return org.json.JSONObject().put("mode",if(humanRun==null) "NONE" else C096Recipe.INITIAL_IMAGE_INPUT)
                .put("run",run).put("selections",if(humanRun==null) 0 else 1)
        }
        @Synchronized fun stop(){stopped=true}
        @Synchronized fun failure():String?=firstCode
        @Synchronized fun observed(edge:ImageEdge)=edge in edges
        @Synchronized fun snapshot()=org.json.JSONObject()
            .put("schema","stage12-c096-image-attempt-v1")
            .put("edges",org.json.JSONArray(edges.map{it.name}))
            .put("first_code",firstCode?:org.json.JSONObject.NULL)
            .put("stopped",stopped)
    }
    @Volatile private var ownImage:ImageAttempt?=null
    @Synchronized fun beginImage(attempt:ImageAttempt){check(ownImage==null);ownImage=attempt}
    fun image():ImageAttempt=checkNotNull(ownImage).also{it.active()}

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
