package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage

/** RAM-only observations of the existing three CAS waits; it supplies no wait backend or UI reads. */
internal class Stage12ClearCasObservation {
    enum class Adversary { SOURCE, ASSOCIATED, RANGE, CATALOG, CONFIGURATION }
    enum class Boundary { WARNINGS, REVIEW, REJECTION }
    private class Case(val adversary: Adversary, val boundaries: MutableList<Window> = mutableListOf())
    private val cases = mutableListOf<Case>()
    private var active: Window? = null
    private var halted = false

    fun beginCase(adversary: Adversary) {
        check(!halted && active == null && cases.size < Adversary.entries.size)
        check(adversary == Adversary.entries[cases.size])
        cases.lastOrNull()?.let { previous ->
            check(previous.boundaries.map { it.boundary } == expected(previous.adversary) && previous.boundaries.all { it.complete })
        }
        cases += Case(adversary)
    }

    fun <T> observe(boundary: Boundary, body: (Window) -> T): T {
        check(!halted && active == null)
        val current = checkNotNull(cases.lastOrNull())
        check(current.boundaries.size < expected(current.adversary).size)
        check(boundary == expected(current.adversary)[current.boundaries.size])
        check(current.boundaries.all { it.complete })
        val window = Window(boundary, current.adversary)
        current.boundaries += window
        active = window
        try {
            val result = body(window)
            window.finish()
            return result
        } catch (error: Throwable) {
            halted = true
            window.fail(error)
            throw error
        } finally { active = null }
    }

    inner class Window internal constructor(val boundary: Boundary, val adversary: Adversary) {
        private var state = "ENTERED"
        private var phase = "ENTERED"
        private var mutation = "NOT_ENTERED"
        private var input = "NOT_ENTERED"
        private var wait = "NOT_ENTERED"
        private var iteration: String? = null
        private var latest: Evaluation? = null
        private var error: Map<String, Any>? = null
        val complete get() = state == "COMPLETE"

        fun mutate(body: () -> Unit) {
            check(active === this && boundary == Boundary.REJECTION && phase == "ENTERED")
            phase = "MUTATION"; mutation = "ENTERED"
            try { body(); mutation = "RETURNED"; phase = "AFTER_MUTATION" }
            catch (failure: Throwable) { mutation = "THREW"; throw failure }
        }
        fun input(body: () -> Unit) {
            check(active === this && boundary == Boundary.REJECTION && phase == "AFTER_MUTATION" && mutation == "RETURNED")
            phase = "INPUT"; input = "ENTERED"
            try { body(); input = "RETURNED"; phase = "AFTER_INPUT" }
            catch (failure: Throwable) { input = "THREW"; throw failure }
        }
        fun waitFor(body: () -> Unit) {
            check(active === this && wait == "NOT_ENTERED")
            check(if (boundary == Boundary.REJECTION) phase == "AFTER_INPUT" && input == "RETURNED" else phase == "ENTERED")
            phase = "WAIT"; wait = "ENTERED"
            try { body(); wait = "RETURNED"; phase = "AFTER_WAIT" }
            catch (failure: Throwable) { wait = "THREW"; throw failure }
        }
        fun iterationEntered() {
            check(active === this && wait == "ENTERED" && iteration !in setOf("MODEL_ENTERED", "MODEL_THREW"))
            latest?.scope = "PREVIOUS_ITERATION"
            iteration = "BEFORE_GUARD"
        }
        private fun evaluate(body: (Evaluation) -> Boolean): Boolean {
            check(active === this && wait == "ENTERED" && iteration == "BEFORE_GUARD")
            iteration = "MODEL_ENTERED"
            val evaluation = Evaluation(boundary, adversary)
            latest = evaluation
            return try {
                val result = body(evaluation)
                evaluation.returned(result); iteration = "MODEL_RETURNED"; result
            } catch (failure: Throwable) {
                evaluation.threw(); iteration = "MODEL_THREW"; throw failure
            }
        }
        fun warnings(stage: () -> V2ManualShiftLoadStage): Boolean {
            check(boundary == Boundary.WARNINGS)
            return evaluate { it.stage(stage) == V2ManualShiftLoadStage.CONFIRM_WARNINGS }
        }
        fun review(stage: () -> V2ManualShiftLoadStage, loading: () -> Boolean): Boolean {
            check(boundary == Boundary.REVIEW)
            return evaluate { it.stage(stage) == V2ManualShiftLoadStage.REVIEW && !it.loading(loading) }
        }
        fun rejection(saving: () -> Boolean, loading: () -> Boolean, errorPresent: () -> Boolean): Boolean {
            check(boundary == Boundary.REJECTION && adversary != Adversary.ASSOCIATED)
            return evaluate { !it.saving(saving) && !it.loading(loading) && it.errorPresent(errorPresent) }
        }
        fun associatedReconfirmation(stage: () -> V2ManualShiftLoadStage, active: () -> Boolean,
            sameOperation: () -> Boolean, sameDates: () -> Boolean, sameTemplate: () -> Boolean,
            saving: () -> Boolean, loading: () -> Boolean, exactWarnings: () -> Boolean,
            warningsUnaccepted: () -> Boolean): Boolean {
            check(boundary == Boundary.REJECTION && adversary == Adversary.ASSOCIATED)
            return evaluate {
                it.stage(stage) == V2ManualShiftLoadStage.CONFIRM_WARNINGS &&
                    it.associatedFlag("is_active", active) && it.associatedFlag("same_operation", sameOperation) &&
                    it.associatedFlag("same_dates", sameDates) && it.associatedFlag("same_template", sameTemplate) &&
                    !it.associatedFlag("is_saving", saving) && !it.associatedFlag("is_loading", loading) &&
                    it.associatedFlag("warnings_exact", exactWarnings) && it.associatedFlag("warnings_unaccepted", warningsUnaccepted)
            }
        }
        internal fun finish() {
            check(wait == "RETURNED" && iteration == "MODEL_RETURNED" && latest?.successful() == true)
            if (boundary == Boundary.REJECTION) check(mutation == "RETURNED" && input == "RETURNED")
            state = "COMPLETE"; phase = "COMPLETE"
        }
        internal fun fail(failure: Throwable) { state = "FAILED"; error = classify(failure) }
        internal fun projection(): Map<String, Any> = linkedMapOf<String, Any>(
            "boundary" to boundary.name, "state" to state, "phase" to phase, "wait_state" to wait,
        ).apply {
            if (boundary == Boundary.REJECTION) {
                put("criterion", if (adversary == Adversary.ASSOCIATED) "NEW_LOSS_WARNINGS" else "ERROR_REJECTION")
                put("mutation_state", mutation); put("input_state", input)
            }
            iteration?.let { put("iteration_state", it) }
            latest?.let { put("latest_evaluation", it.projection()) }
            error?.let { put("error", it.toMap()) }
        }
    }

    private class Evaluation(private val boundary: Boundary, private val adversary: Adversary) {
        internal var scope = "CURRENT_ITERATION"
        private var state = "ENTERED"
        private var result: Boolean? = null
        private val fields = linkedMapOf<String, Any>()
        fun stage(read: () -> V2ManualShiftLoadStage): V2ManualShiftLoadStage {
            check((boundary != Boundary.REJECTION || adversary == Adversary.ASSOCIATED) && fields.isEmpty())
            val value = read(); fields["stage"] = value.name; return value
        }
        fun saving(read: () -> Boolean): Boolean {
            check(boundary == Boundary.REJECTION && fields.isEmpty())
            val value = read(); fields["is_saving"] = value; return value
        }
        fun loading(read: () -> Boolean): Boolean {
            check(if (boundary == Boundary.REVIEW) fields == mapOf("stage" to "REVIEW")
                else boundary == Boundary.REJECTION && fields == mapOf("is_saving" to false))
            val value = read(); fields["is_loading"] = value; return value
        }
        fun errorPresent(read: () -> Boolean): Boolean {
            check(boundary == Boundary.REJECTION && fields == mapOf("is_saving" to false, "is_loading" to false))
            val value = read(); fields["error_present"] = value; return value
        }
        fun associatedFlag(key: String, read: () -> Boolean): Boolean {
            check(boundary == Boundary.REJECTION && adversary == Adversary.ASSOCIATED)
            val flags = listOf("is_active", "same_operation", "same_dates", "same_template", "is_saving", "is_loading", "warnings_exact", "warnings_unaccepted")
            val index = flags.indexOf(key)
            check(index >= 0)
            val expected = linkedMapOf<String, Any>("stage" to "CONFIRM_WARNINGS")
            flags.take(index).forEach { prior -> expected[prior] = prior !in setOf("is_saving", "is_loading") }
            check(fields == expected)
            val value = read(); fields[key] = value; return value
        }
        internal fun returned(value: Boolean) { state = "RETURNED"; result = value }
        internal fun threw() { state = "THREW" }
        internal fun successful() = scope == "CURRENT_ITERATION" && state == "RETURNED" && result == true
        internal fun projection(): Map<String, Any> = linkedMapOf<String, Any>(
            "scope" to scope, "state" to state,
        ).apply { putAll(fields); result?.let { put("result", it) } }
    }

    private fun expected(adversary: Adversary) = if (adversary == Adversary.RANGE) listOf(Boundary.REJECTION)
        else listOf(Boundary.WARNINGS, Boundary.REVIEW, Boundary.REJECTION)

    private fun classify(error: Throwable): Map<String, Any> {
        if (error.javaClass.name == "androidx.compose.ui.test.ComposeTimeoutException")
            return mapOf("type" to "COMPOSE_TIMEOUT", "classification" to "COMPOSE_TIMEOUT_TYPE")
        if (error.javaClass == IllegalStateException::class.java) {
            val constant = when (error.message) {
                "SELECTION_QA_FOCUS_LOST" -> "FOCUS"
                "SELECTION_CIVIL_DAY_CHANGED" -> "CIVIL_DAY"
                else -> "NONE"
            }
            val ownGuard = error.stackTrace.any { it.className == "com.blackatsystems.miguardia.stage12.Stage12SelectionQaGuard" && it.methodName == "requireReady" }
            val code = if (ownGuard && constant == "FOCUS") "QA_FOCUS_GUARD"
                else if (ownGuard && constant == "CIVIL_DAY") "CIVIL_DAY_GUARD" else "UNKNOWN"
            return mapOf("type" to "ILLEGAL_STATE", "classification" to code,
                "guard_constant" to constant, "shared_guard_frame" to ownGuard)
        }
        return mapOf("type" to "OTHER", "classification" to "UNKNOWN")
    }

    fun projection(): Map<String, Any> = linkedMapOf("schema" to "stage12-selection-clear-cas-observation-v2",
        "cases" to cases.map { case -> linkedMapOf("adversary" to case.adversary.name,
            "boundaries" to case.boundaries.map { it.projection() }) })
}
