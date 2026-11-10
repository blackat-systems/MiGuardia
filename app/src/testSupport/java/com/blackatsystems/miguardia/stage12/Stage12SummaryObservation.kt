package com.blackatsystems.miguardia.stage12

/** Bounded metadata for one existing Compose test. Never reads Android or stores text/Throwable. */
internal class Stage12SummaryObservation {
    enum class State { OBSERVING, DONE, FAILED }
    enum class Phase { INIT, FILES, RULE_ENTER, METHOD_ENTER, HOST_BOUND, BEFORE_SET_CONTENT,
        CONTENT_ENTERED, AFTER_SET_CONTENT, OVERVIEW_SAMPLE, BEFORE_CLICK, CALLBACK_ENTERED,
        AFTER_CLICK, DETAIL_VISIBLE, ASSERTIONS_COMPLETE, CONTENT_DISPOSED, RULE_RETURN,
        CLOSE_SAMPLE, PUBLISH, DONE, FAILED }
    enum class Life { UNKNOWN, INITIALIZED, CREATED, STARTED, RESUMED, DESTROYED }
    enum class Reason { NONE, HOST_UNKNOWN, HOST_CHANGED, CONTEXT_NOT_QA, HOST_NOT_QA, UID_NOT_QA,
        WRONG_THREAD, SCREEN_OFF, KEYGUARD_LOCKED, HOST_NOT_RESUMED,
        HOST_FINISHING, HOST_DESTROYED, WINDOW_UNKNOWN, WINDOW_NOT_READY, READ_FAILED,
        ROOT_READ_FAILED, BODY_FAILED, RULE_FAILED, CLOSE_FAILED, PUBLICATION_FAILED,
        INCOMPLETE_OBSERVATION, RUNNER_FAILED, FILES_FAILED }
    enum class Failure { NONE, ASSERTION, IO, STATE, OTHER }

    data class Snapshot(val contextQa: Boolean? = null, val hostQa: Boolean? = null,
        val uidQa: Boolean? = null, val sameHost: Boolean? = null, val onMain: Boolean? = null,
        val life: Life = Life.UNKNOWN, val finishing: Boolean? = null, val destroyed: Boolean? = null,
        val decorPresent: Boolean? = null, val attached: Boolean? = null, val focused: Boolean? = null,
        val interactive: Boolean? = null,
        val keyguardLocked: Boolean? = null) {
        fun wire() = "{\"context_qa\":$contextQa,\"host_qa\":$hostQa,\"uid_qa\":$uidQa," +
            "\"same_host\":$sameHost,\"on_main\":$onMain,\"life\":\"${life.name}\"," +
            "\"finishing\":$finishing,\"destroyed\":$destroyed,\"decor_present\":$decorPresent," +
            "\"attached\":$attached,\"focused\":$focused,\"interactive\":$interactive,\"keyguard_locked\":$keyguardLocked}"
    }
    private data class Event(val phase: Phase, val elapsed: Long, val snapshot: Snapshot?) {
        fun wire() = "{\"phase\":\"${phase.name}\",\"elapsed_ms\":$elapsed,\"snapshot\":${snapshot?.wire() ?: "null"}}"
    }
    private val startedNanos = System.nanoTime()
    private val events = mutableListOf<Event>()
    private var truncated = false
    var phase = Phase.INIT; private set
    private var state = State.OBSERVING
    private var reason = Reason.NONE
    private var failurePhase: Phase? = null
    private var failure = Failure.NONE
    private var last: Snapshot? = null
    private var bodyEntered = false
    private var bodyComplete = false
    private var bodyFailed = false
    private var ruleReturned = false
    private var ruleFailed = false
    private var additionalRuleFailure = false
    private var closeFailed = false
    private var publicationFailed = false
    private var publicationComplete = false
    private var contentEnters: Int? = null
    private var contentDisposes: Int? = null
    private var overviewNodes: Int? = null
    private var clickCalls: Int? = null
    private var clickReturned: Boolean? = null
    private var callbackCalls: Int? = null
    private var callbackMatches: Boolean? = null
    private var callbacksOnMain: Boolean? = null
    private var detailAsserted: Boolean? = null
    private var closeObserved: Boolean? = null

    @Synchronized fun phase(value: Phase) { phase = value; event(value) }
    @Synchronized fun sample(value: Snapshot) { last = value; event(phase, value) }
    @Synchronized fun beginBody() {
        bodyEntered = true; clickCalls = 0; callbackCalls = 0; phase(Phase.METHOD_ENTER)
    }
    @Synchronized fun contentRequested() {
        contentEnters = 0; contentDisposes = 0; phase(Phase.BEFORE_SET_CONTENT)
    }
    @Synchronized fun contentEntered(onMain: Boolean) {
        contentEnters = contentEnters?.plus(1)
        if (contentEnters == null) failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION)
        if (!onMain) failed(Failure.STATE, Reason.WRONG_THREAD)
        event(Phase.CONTENT_ENTERED)
    }
    @Synchronized fun contentDisposed(onMain: Boolean) {
        contentDisposes = contentDisposes?.plus(1)
        if (contentDisposes == null) failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION)
        if (!onMain) failed(Failure.STATE, Reason.WRONG_THREAD)
        event(Phase.CONTENT_DISPOSED)
    }
    @Synchronized fun overview(count: Int) {
        require(count >= 0); overviewNodes = count; phase(Phase.OVERVIEW_SAMPLE)
    }
    @Synchronized fun enteringClick() {
        clickCalls = clickCalls?.plus(1)
        if (clickCalls == null) { failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION); throw Rejected(safeSummary()) }
        clickReturned = false
    }
    @Synchronized fun returnedClick() { clickReturned = true; phase(Phase.AFTER_CLICK) }
    @Synchronized fun callback(matches: Boolean, onMain: Boolean) {
        callbackCalls = callbackCalls?.plus(1)
        if (callbackCalls == null) failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION)
        callbackMatches = callbackMatches?.let { it && matches } ?: matches
        callbacksOnMain = callbacksOnMain?.let { it && onMain } ?: onMain
        event(Phase.CALLBACK_ENTERED)
    }
    @Synchronized fun detailAsserted() { detailAsserted = true; phase(Phase.DETAIL_VISIBLE) }
    @Synchronized fun completedBody() { bodyComplete = true; phase(Phase.ASSERTIONS_COMPLETE) }
    @Synchronized fun bodyFailed(error: Throwable) {
        bodyFailed = true; failed(kind(error), Reason.BODY_FAILED)
    }
    @Synchronized fun ruleReturned() { ruleReturned = true; phase(Phase.RULE_RETURN) }
    @Synchronized fun ruleFailed(error: Throwable, additional: Boolean) {
        ruleFailed = true; additionalRuleFailure = additional
        failed(kind(error), Reason.RULE_FAILED)
    }
    @Synchronized fun closed(observed: Boolean?) { closeObserved = observed }
    @Synchronized fun closeFailed(error: Throwable) {
        closeFailed = true; failed(kind(error), Reason.CLOSE_FAILED)
    }
    @Synchronized fun publicationFailed(error: Throwable) {
        publicationFailed = true; failed(kind(error), Reason.PUBLICATION_FAILED)
    }
    @Synchronized fun published() { publicationComplete = true }
    @Synchronized fun failed(kind: Failure, why: Reason) {
        if (failurePhase == null) { failurePhase = phase; failure = kind; reason = why }
    }
    @Synchronized fun requireOwnerSafe() {
        val s = last
        val why = when {
            s == null || s.contextQa == null || s.hostQa == null || s.uidQa == null || s.sameHost == null || s.onMain == null -> Reason.HOST_UNKNOWN
            !s.contextQa -> Reason.CONTEXT_NOT_QA
            !s.hostQa -> Reason.HOST_NOT_QA
            !s.uidQa -> Reason.UID_NOT_QA
            !s.sameHost -> Reason.HOST_CHANGED
            !s.onMain -> Reason.WRONG_THREAD
            else -> Reason.NONE
        }
        if (why != Reason.NONE) { failed(Failure.STATE, why); throw Rejected(safeSummary()) }
    }
    @Synchronized fun requireActionSafe() {
        val s = last
        val why = when {
            s == null -> Reason.HOST_UNKNOWN
            s.contextQa == null || s.hostQa == null || s.uidQa == null || s.sameHost == null ||
                s.onMain == null || s.interactive == null || s.keyguardLocked == null || s.destroyed == null ||
                s.finishing == null || s.life == Life.UNKNOWN -> Reason.HOST_UNKNOWN
            s.contextQa != true -> Reason.CONTEXT_NOT_QA
            s.hostQa != true -> Reason.HOST_NOT_QA
            s.uidQa != true -> Reason.UID_NOT_QA
            s.sameHost != true -> Reason.HOST_CHANGED
            s.onMain != true -> Reason.WRONG_THREAD
            s.interactive != true -> Reason.SCREEN_OFF
            s.keyguardLocked != false -> Reason.KEYGUARD_LOCKED
            s.destroyed != false -> Reason.HOST_DESTROYED
            s.finishing != false -> Reason.HOST_FINISHING
            s.life != Life.RESUMED -> Reason.HOST_NOT_RESUMED
            s.decorPresent == null || (s.decorPresent && (s.attached == null || s.focused == null)) -> Reason.WINDOW_UNKNOWN
            s.decorPresent != true || s.attached != true || s.focused != true -> Reason.WINDOW_NOT_READY
            else -> Reason.NONE
        }
        if (why != Reason.NONE) { failed(Failure.STATE, why); throw Rejected(safeSummary()) }
    }
    @Synchronized fun requireCompleteBeforePublish() {
        if (!completeBeforePublish()) {
            failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION); throw Rejected(safeSummary())
        }
    }
    @Synchronized fun runnerFailed() {
        failed(Failure.OTHER, Reason.RUNNER_FAILED); finish(false)
    }
    @Synchronized fun finish(success: Boolean) {
        if (success && (!completeBeforePublish() || !publicationComplete)) failed(Failure.STATE, Reason.INCOMPLETE_OBSERVATION)
        state = if (success && completeBeforePublish() && publicationComplete) State.DONE else State.FAILED
        phase = if (state == State.DONE) Phase.DONE else Phase.FAILED
        event(phase)
    }
    private fun completeBeforePublish() = failurePhase == null && bodyEntered && bodyComplete && !bodyFailed &&
        ruleReturned && !ruleFailed && contentEnters == 1 && contentDisposes == 1 && overviewNodes != null &&
        clickCalls == 1 && clickReturned == true && callbackCalls == 1 && callbackMatches == true &&
        callbacksOnMain == true && detailAsserted == true && closeObserved == true
    @Synchronized private fun event(value: Phase, snapshot: Snapshot? = null) {
        val event = Event(value, (System.nanoTime() - startedNanos) / 1_000_000L, snapshot)
        if (events.size < 24) events.add(event) else { events[23] = event; truncated = true }
    }
    @Synchronized fun wire(): String = "{\"schema\":\"stage12-summary-observation-v1\"," +
        "\"scope\":\"ONE_EXISTING_COMPOSE_FUNCTIONAL_METHOD\",\"state\":\"${state.name}\",\"phase\":\"${phase.name}\"," +
        "\"reason\":\"${reason.name}\",\"failure_phase\":\"${failurePhase?.name ?: "NONE"}\",\"failure_kind\":\"${failure.name}\"," +
        "\"body_entered\":$bodyEntered,\"body_complete\":$bodyComplete,\"body_failed\":$bodyFailed," +
        "\"rule_returned\":$ruleReturned,\"rule_failed\":$ruleFailed,\"additional_rule_failure\":$additionalRuleFailure," +
        "\"close_failed\":$closeFailed,\"publication_failed\":$publicationFailed,\"publication_complete\":$publicationComplete," +
        "\"content_enters\":$contentEnters,\"content_disposes\":$contentDisposes,\"overview_nodes\":$overviewNodes," +
        "\"click_calls\":$clickCalls,\"click_returned\":$clickReturned,\"callback_calls\":$callbackCalls," +
        "\"callback_matches\":$callbackMatches,\"callbacks_on_main\":$callbacksOnMain,\"detail_asserted\":$detailAsserted," +
        "\"close_observed\":$closeObserved,\"last\":${last?.wire() ?: "null"}," +
        "\"trace_truncated\":$truncated,\"events\":[${events.joinToString(",") { it.wire() }}]}"
    @Synchronized fun safeSummary() = "STAGE12_SUMMARY_OBSERVATION wire=${wire()}"
    @Synchronized fun statusFields(numberOfTests: Int, code: Int, hasStack: Boolean): Map<String, Any> =
        linkedMapOf<String, Any>("class" to SELECTOR.substringBefore('#'), "test" to SELECTOR.substringAfter('#'),
            "id" to "AndroidJUnitRunner", "numtests" to numberOfTests, "stream" to safeSummary()).apply {
            if (code !in setOf(0, 1) || hasStack) put("stack", safeSummary())
        }
    class Rejected(message: String) : IllegalStateException(message)
    /** Shared orchestration: captures the body before a surrounding rule can replace/aggregate it. */
    class FailureOrder {
        private var body: Throwable? = null
        fun observeBody(block: () -> Unit) {
            try { block() } catch (error: Throwable) { body = error; throw error }
        }
        fun observeRule(block: () -> Unit, onFailure: (Throwable, Boolean) -> Unit,
            components: (Throwable) -> List<Throwable>) {
            try { block() } catch (error: Throwable) {
                onFailure(error, body != null && body !== error)
                throw retainRuleFailure(body, error, components(error))
            }
        }
    }
    class OwnHost<T : Any> {
        private var attempted = false
        @Volatile private var value: T? = null
        @Synchronized fun acquire(onMain: (() -> Unit) -> Unit, provider: () -> T): T {
            check(!attempted) { "OWN_HOST_ACQUISITION_ALREADY_ATTEMPTED" }
            attempted = true
            value = Stage12SummaryObservation.onMain(onMain, provider)
            return requireNotNull(value)
        }
        fun existing(): T? = value
    }
    companion object {
        const val SELECTOR = "com.blackatsystems.miguardia.SummaryComposeTest#metricDetailUsesTheExactRowsAndNeverDisplaysPrivateFields"
        const val ARGUMENT = "stage12SummaryObservation"
        const val ADMISSION = "OBSERVE_OWN_SUMMARY_20261025"
        fun requireSelection(selectors: List<String>, clock: String, argument: String) {
            require(selectors == listOf(SELECTOR) && clock == "REAL" && argument == ADMISSION)
        }
        fun retain(primary: Throwable?, secondary: Throwable): Throwable {
            if (primary == null) return secondary
            if (primary !== secondary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
            return primary
        }
        fun retainRuleFailure(body: Throwable?, rule: Throwable, contained: List<Throwable>): Throwable {
            if (body == null) return rule
            // JUnit aggregates may already contain body. Keep its identity and attach the other originals,
            // rather than creating a suppressed-container cycle back to body.
            if (contained.isEmpty()) retain(body, rule) else contained.forEach { retain(body, it) }
            return body
        }
        fun <T> onMain(dispatch: (() -> Unit) -> Unit, block: () -> T): T {
            var result: Result<T>? = null
            dispatch { result = runCatching(block) }
            return checkNotNull(result) { "OWN_MAIN_CALLBACK_NOT_EXECUTED" }.getOrThrow()
        }
        fun kind(error: Throwable) = when (error) {
            is AssertionError -> Failure.ASSERTION
            is java.io.IOException -> Failure.IO
            is IllegalStateException -> Failure.STATE
            else -> Failure.OTHER
        }
    }
}
