package com.blackatsystems.miguardia.stage12

/** Decisions and bounded technical metadata only. Does not read Android or perform actions. */
internal class Stage12ActivityAdmission(val technicalOnly: Boolean) {
    enum class State { PREPARING, READY, REJECTED }
    enum class Foreground { OWN, NONE, OTHER }
    enum class Life { INITIALIZED, CREATED, STARTED, RESUMED, DESTROYED }
    enum class Startup { RECOVERING, READY, FAILED }
    enum class Access { WAITING, LOADING, READY, FAILED }
    enum class Reason { NONE, CONTEXT_NOT_QA, SCREEN_OFF, KEYGUARD_LOCKED, FOREGROUND_OTHER,
        ACTIVITY_DESTROYED, STARTUP_FAILED, ACCESS_FAILED, ACCESS_BLOCKED, WRONG_THREAD, READ_FAILED,
        DEADLINE_EXCEEDED, READY_LOST, HARNESS_ERROR }
    enum class Phase { INIT, FILES, LAUNCH, READINESS, BODY, CLOSE, POSTCONDITION, PUBLISH, DONE, FAILED }
    enum class Failure { NONE, READINESS, IO, STATE, OTHER }
    enum class X015Step {
        UNKNOWN, PREPARATION, LAUNCH, READINESS, CALENDAR_READY, RECEIVED_READBACK, FIXTURE_SETUP,
        BASELINE_READBACK, OPEN_DAY, WAIT_DAY_SHEET, BEFORE_DAY_BACK, AFTER_DAY_BACK,
        DAY_BACK_ACQUIRE_DEVICE, DAY_BACK_READ_FOREGROUND, DAY_BACK_CHECK_FOREGROUND,
        DAY_BACK_READ_LIFECYCLE, DAY_BACK_CHECK_RESUMED, DAY_BACK_CALL, DAY_BACK_CHECK_RESULT,
        WAIT_DAY_DISMISSED, DAY_DISMISSED, NEXT_EVENT, SUMMARY, RETURN_CALENDAR, WIDGET_SOURCE,
        WIDGET_APPLY, AFTER_READBACK, CONSERVATION, CLEANUP_WIDGET, CLOSE_ACTIVITY, CLEANUP_SHIFT,
        CLEANUP_CANARY, CLEANUP_PREFERENCES, RESTORED_READBACK, PUBLISH, COMPLETE,
    }
    data class Snapshot(val contextQa: Boolean, val foreground: Foreground, val screenOn: Boolean,
        val keyguardLocked: Boolean, val life: Life, val attached: Boolean?, val focused: Boolean?,
        val startup: Startup, val accessAllowed: Boolean, val onMainThread: Boolean,
        val accessPhase: Access = Access.READY, val decorPresent: Boolean = true) {
        fun wire() = "{\"context_qa\":$contextQa,\"foreground\":\"${foreground.name}\",\"screen_on\":$screenOn," +
            "\"keyguard_locked\":$keyguardLocked,\"life\":\"${life.name}\",\"decor_present\":$decorPresent,\"attached\":$attached,\"focused\":$focused," +
            "\"startup\":\"${startup.name}\",\"access_allowed\":$accessAllowed,\"access_phase\":\"${accessPhase.name}\",\"on_main_thread\":$onMainThread}"
    }
    private data class Event(val elapsed: Long, val snapshot: Snapshot) {
        fun wire() = "{\"elapsed_ms\":$elapsed,\"snapshot\":${snapshot.wire()}}"
    }
    @Volatile var state = State.PREPARING; private set
    @Volatile var reason = Reason.NONE; private set
    @Volatile var phase = Phase.INIT; private set
    @Volatile var everReady = false; private set
    private var failurePhase: Phase? = null
    private var failure = Failure.NONE
    private var closeFailed = false
    private var publicationFailed = false
    private var elapsed = 0L
    private var observations = 0
    private var last: Snapshot? = null
    private val events = mutableListOf<Event>()
    private var truncated = false
    private var x015Step: X015Step? = null
    private var x015FailureStep: X015Step? = null
    private var x015BackForeground: Foreground? = null
    private var x015BackLifecycle: Life? = null
    private var x015BackResult: Boolean? = null

    @Synchronized fun phase(value: Phase) { phase = value }
    @Synchronized fun x015Step(value: X015Step) { x015Step = value }
    @Synchronized fun x015Step(): X015Step? = x015Step

    /** One scoped operation sequence; providers execute once on the original caller thread. */
    fun <T> x015Back(acquireDevice: () -> T, readForeground: (T) -> Foreground,
                    readLifecycle: () -> Life, pressBack: (T) -> Boolean) {
        x015Step(X015Step.DAY_BACK_ACQUIRE_DEVICE)
        val device = acquireDevice()
        x015Step(X015Step.DAY_BACK_READ_FOREGROUND)
        val foreground = readForeground(device)
        synchronized(this) { x015BackForeground = foreground }
        x015Step(X015Step.DAY_BACK_CHECK_FOREGROUND)
        check(foreground == Foreground.OWN) { "X015_DAY_SHEET_NOT_OWN_FOREGROUND" }
        x015Step(X015Step.DAY_BACK_READ_LIFECYCLE)
        val lifecycle = readLifecycle()
        synchronized(this) { x015BackLifecycle = lifecycle }
        x015Step(X015Step.DAY_BACK_CHECK_RESUMED)
        check(lifecycle == Life.RESUMED) { "X015_DAY_SHEET_NOT_OWN_FOREGROUND" }
        x015Step(X015Step.DAY_BACK_CALL)
        val result = pressBack(device)
        synchronized(this) { x015BackResult = result }
        x015Step(X015Step.DAY_BACK_CHECK_RESULT)
        check(result) { "X015_DAY_SHEET_BACK_INJECTION_FAILED" }
    }

    @Synchronized fun observe(snapshot: Snapshot, elapsedMs: Long): State {
        require(elapsedMs >= elapsed)
        elapsed = elapsedMs
        if (state == State.REJECTED) return state
        observations++
        if (snapshot != last) {
            if (events.size < 8) events.add(Event(elapsed, snapshot))
            else { events[7] = Event(elapsed, snapshot); truncated = true }
        }
        last = snapshot
        val fatal = when {
            !snapshot.onMainThread -> Reason.WRONG_THREAD
            !snapshot.contextQa -> Reason.CONTEXT_NOT_QA
            !snapshot.screenOn -> Reason.SCREEN_OFF
            snapshot.keyguardLocked -> Reason.KEYGUARD_LOCKED
            snapshot.foreground == Foreground.OTHER -> Reason.FOREGROUND_OTHER
            snapshot.life == Life.DESTROYED -> Reason.ACTIVITY_DESTROYED
            snapshot.startup == Startup.FAILED -> Reason.STARTUP_FAILED
            snapshot.accessPhase == Access.FAILED -> Reason.ACCESS_FAILED
            state == State.PREPARING && elapsed >= DEADLINE_MS -> Reason.DEADLINE_EXCEEDED
            snapshot.startup == Startup.READY && snapshot.accessPhase == Access.READY && !snapshot.accessAllowed -> Reason.ACCESS_BLOCKED
            else -> Reason.NONE
        }
        if (fatal != Reason.NONE) return reject(fatal)
        val ready = snapshot.foreground == Foreground.OWN && snapshot.life == Life.RESUMED &&
            snapshot.decorPresent && snapshot.attached == true && snapshot.focused == true && snapshot.startup == Startup.READY &&
            snapshot.accessPhase == Access.READY && snapshot.accessAllowed
        if (ready) { state = State.READY; everReady = true }
        else if (state == State.READY) return reject(Reason.READY_LOST)
        return state
    }

    @Synchronized fun readFailed(wrongThread: Boolean = false) {
        if (state != State.REJECTED) reject(if (wrongThread) Reason.WRONG_THREAD else Reason.READ_FAILED)
    }

    @Synchronized fun activityDestroyed() {
        if (state != State.REJECTED) reject(Reason.ACTIVITY_DESTROYED)
    }

    @Synchronized fun requireReady() {
        check(state == State.READY && failurePhase == null) { safeSummary() }
    }

    @Synchronized private fun reject(value: Reason): State {
        state = State.REJECTED; reason = value
        return state
    }

    @Synchronized fun failed(error: Throwable) = failed(when (error) {
        is Stage12ReadinessRejected -> Failure.READINESS
        is java.io.IOException -> Failure.IO
        is IllegalStateException -> Failure.STATE
        else -> Failure.OTHER
    })

    @Synchronized fun failed(kind: Failure) {
        if (phase == Phase.CLOSE) closeFailed = true
        if (phase == Phase.PUBLISH) publicationFailed = true
        if (failurePhase == null) {
            failurePhase = phase
            failure = kind
            x015FailureStep = x015Step
        }
        if (state == State.PREPARING) reject(Reason.HARNESS_ERROR)
    }

    @Synchronized fun finish(success: Boolean) {
        phase = if (success && failurePhase == null && state == State.READY) Phase.DONE else Phase.FAILED
    }

    /** No arbitrary string enters this wire format: only fixed enums, booleans and bounded counters. */
    @Synchronized fun wire(): String = "{\"schema\":\"stage12-activity-admission-v1\",\"technical_only\":$technicalOnly," +
        "\"observation_gestures\":0,\"state\":\"${state.name}\",\"reason\":\"${reason.name}\",\"phase\":\"${phase.name}\"," +
        "\"result_reason\":\"${resultReason()}\"," +
        "\"failure_phase\":\"${failurePhase?.name ?: "NONE"}\",\"failure_kind\":\"${failure.name}\",\"ever_ready\":$everReady," +
        "\"close_failed\":$closeFailed,\"publication_failed\":$publicationFailed," +
        "\"elapsed_ms\":$elapsed,\"observations\":$observations,\"trace_truncated\":$truncated," +
        "\"last\":${last?.wire() ?: "null"},\"events\":[${events.joinToString(",") { it.wire() }}]" +
        (x015Step?.let { ",\"x015_route_step\":\"${it.name}\",\"x015_failure_step\":\"${x015FailureStep?.name ?: "UNKNOWN"}\"" +
            ",\"x015_back_foreground\":${x015BackForeground?.let { value -> "\"${value.name}\"" } ?: "null"}" +
            ",\"x015_back_lifecycle\":${x015BackLifecycle?.let { value -> "\"${value.name}\"" } ?: "null"}" +
            ",\"x015_back_result\":$x015BackResult" } ?: "") + "}"

    @Synchronized fun safeSummary() = "STAGE12_ACTIVITY_ADMISSION state=${state.name} reason=${reason.name} " +
        "result=${resultReason()} phase=${phase.name} failure_phase=${failurePhase?.name ?: "NONE"} failure_kind=${failure.name} wire=${wire()}"

    @Synchronized private fun resultReason(): String = when {
        publicationFailed -> "PUBLICATION_FAILED"
        closeFailed -> "CLOSE_FAILED"
        state == State.REJECTED -> reason.name
        failurePhase != null -> "HARNESS_ERROR"
        phase == Phase.DONE && state == State.READY -> "READY"
        else -> "OBSERVING"
    }

    companion object { const val DEADLINE_MS = 30_000L }
}

internal class Stage12ReadinessRejected(admission: Stage12ActivityAdmission) :
    IllegalStateException(admission.safeSummary())

/** Used directly by the runner to build NEW technical Bundles; unknown input keys never escape. */
internal object Stage12AdmissionProjection {
    const val X015_SELECTOR = "com.blackatsystems.miguardia.stage12.Stage12X015NativeTest#realCalendarSummaryCardAndAppliedWidgetPreserveCompleteStores"
    const val UNKNOWN_SELECTOR = "stage12.Unknown#unattributed"
    fun statusSelector(original: String, selectors: Set<String>, admitted: Boolean = true): String =
        if (admitted && original in selectors) original else UNKNOWN_SELECTOR

    fun needsSafeStatus(technical: Boolean, selector: String, report: Stage12ActivityAdmission?,
                        selectors: Set<String> = emptySet(), admitted: Boolean = true): Boolean =
        !admitted || technical || selector == UNKNOWN_SELECTOR || selector == X015_SELECTOR ||
            X015_SELECTOR in selectors || selector == Stage12RuntimeTarget.WORK_FIXTURE ||
            Stage12RuntimeTarget.WORK_FIXTURE in selectors || Stage12RuntimeTarget.C090 in selectors ||
            Stage12B1Selectors.OWNED_LOAD_EDIT in selectors ||
            (report != null && !report.everReady)

    fun needsSafeTerminal(technical: Boolean, sanitizedFailure: Boolean, selectors: Set<String>, admitted: Boolean = true): Boolean =
        !admitted || technical || sanitizedFailure || X015_SELECTOR in selectors || Stage12RuntimeTarget.WORK_FIXTURE in selectors ||
            Stage12RuntimeTarget.C090 in selectors ||
            Stage12B1Selectors.OWNED_LOAD_EDIT in selectors

    fun requireScope(selectors: List<String>, functional: Set<String>, technical: String, clock: String): Boolean {
        require(selectors.isNotEmpty() && selectors.size <= 256 && selectors.size == selectors.toSet().size)
        val only = selectors == listOf(technical)
        require(only || selectors.all { it in functional }) { "Mixed or unknown Activity admission selector" }
        require(!only || clock == "REAL") { "Activity admission uses the real Android/Calendar clocks" }
        return only
    }

    fun status(selector: String, original: Map<String, Any?>, code: Int, report: Stage12ActivityAdmission): Map<String, Any> {
        val parts = selector.split('#'); require(parts.size == 2)
        val safe = linkedMapOf<String, Any>("class" to parts[0], "test" to parts[1], "id" to "AndroidJUnitRunner",
            "numtests" to (original["numtests"] as? Int ?: -1), "stream" to report.safeSummary())
        if (code !in setOf(0, 1) || (original["stack"] as? String)?.isNotBlank() == true) safe["stack"] = report.safeSummary()
        return safe
    }

    fun terminal(report: Stage12ActivityAdmission): Map<String, Any> = linkedMapOf(
        "stream" to report.safeSummary(), "stage12ActivityAdmission" to report.wire())

    fun terminalReport(technical: Boolean, sanitizeFailure: Boolean, current: Stage12ActivityAdmission?,
                       lastFailure: Stage12ActivityAdmission?): Stage12ActivityAdmission? = when {
        technical -> current ?: fallback(true)
        sanitizeFailure -> lastFailure ?: fallback(false)
        else -> current
    }

    private fun fallback(technical: Boolean) = Stage12ActivityAdmission(technical).apply {
        failed(Stage12ActivityAdmission.Failure.OTHER); finish(false)
    }
}
