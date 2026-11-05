package com.blackatsystems.miguardia.stage12

import java.io.IOException

/** Finite journal of the original recurring setup. Values and original errors stay in RAM. */
internal class Stage12RecurringPreparation(retain: (Throwable) -> Unit) {
    enum class Step(val expected: Boolean? = null) {
        CASE_REGISTERED, NAMES, APP_ANCHOR, APP_SHAPE(true), APP_IDENTITY, APP_PREFIX(true), APP_DIRECTORY(true),
        DATABASE_DIRECTORY(true), DATABASE_DIRECTORY_LINK(false), DATABASE_DIRECTORY_IDENTITY(true),
        DATABASE_RESOLUTION, CONTEXT_DATABASE, CONTEXT_DATABASE_NAME(true), CONTEXT_DATABASE_PREFIX(true), CONTEXT_DATABASE_PARENT(true),
        CONTEXT_DATABASE_ANCHOR(true), CONTEXT_DATABASE_DIRECTORY_LINK(false), CONTEXT_DATABASE_LINK(false), CONTEXT_DATABASE_IDENTITY(true),
        FILES_DIRECTORY(true), FILES_DIRECTORY_LINK(false), FILES_DIRECTORY_IDENTITY(true),
        CONTEXT_FILES, CONTEXT_FILES_NAME(true), CONTEXT_FILES_PREFIX(true), CONTEXT_FILES_ANCHOR(true), CONTEXT_FILES_LINK(false), CONTEXT_FILES_IDENTITY(true),
        PRIVATE_RESOLUTION, DATA_RESOLUTION, APP_STABLE(true),
        DATABASE_PRESENT(false), WAL_PRESENT(false), SHM_PRESENT(false), JOURNAL_PRESENT(false),
        PRIVATE_PRESENT(false), PRIVATE_BINDING, PRIVATE_MKDIR(true), DATA_BINDING, DATA_MKDIR(true),
        BINDING_BEFORE_CREATE, DATABASE_CREATE, FIXTURE_CREATE, CIVIL_DATE(true), PHOTO_STORE,
        SETUP_READBACK, SETUP_STORE_CLOSE, REOPEN_PATHS, SETUP_STORE_REOPEN, REOPEN_VALIDATION, REOPEN_READBACK,
        ACTIVITY_LAUNCH, ACTIVITY_MOUNT,
    }
    private val journal = Stage12PreparationJournal(Step.entries.map { Stage12PreparationJournal.Operation(it.name, it.expected) }, retain)
    val failedStep: Step? get() = journal.failedStep?.let(Step::valueOf)
    fun <T> call(step: Step, body: () -> T): T = journal.call(Stage12PreparationJournal.Operation(step.name, step.expected), body)
    fun guard(step: Step, body: () -> Boolean) { call<Boolean>(step, body) }
    fun projection(): Map<String, Any> = linkedMapOf<String, Any>("schema" to "stage12-recurring-measured-setup-v1").apply {
        putAll(journal.projection())
        put("creation", mapOf("private_root" to journal.state(Step.PRIVATE_MKDIR.ordinal), "data" to journal.state(Step.DATA_MKDIR.ordinal),
            "database" to journal.state(Step.DATABASE_CREATE.ordinal), "database_validation" to journal.state(Step.REOPEN_READBACK.ordinal),
            "activity_launch" to journal.state(Step.ACTIVITY_LAUNCH.ordinal), "activity_mount" to journal.state(Step.ACTIVITY_MOUNT.ordinal)))
    }
}

/** Shared finite prefix engine. Its two callers supply closed, immutable operation profiles. */
internal class Stage12PreparationJournal(profile: List<Operation>, private val retain: (Throwable) -> Unit) {
    data class Operation(val name: String, val expected: Boolean? = null)
    private val profile = profile.toList().also { check(it.isNotEmpty() && it.map(Operation::name).distinct().size == it.size) }
    private val events = mutableListOf<MutableMap<String, Any>>()
    private var failure: Throwable? = null
    private var protocolError: Map<String, String>? = null
    private var externalFailure: Map<String, String>? = null
    var failedStep: String? = null; private set

    private fun enter(step: Operation): MutableMap<String, Any> {
        failure?.let { throw it }
        if (profile.getOrNull(events.size) != step) {
            val error = IllegalStateException("RECURRING_SETUP_STEP_ORDER")
            protocolError = mapOf("kind" to "STEP_ORDER", "requested_step" to step.name)
            remember(error); throw error
        }
        val event = linkedMapOf<String, Any>("step" to step.name, "state" to "ENTERED"); events += event
        return event
    }
    private fun <T> returned(step: Operation, event: MutableMap<String, Any>, result: T): T {
        if (step.expected != null) check(result is Boolean) { "RECURRING_SETUP_BOOLEAN_REQUIRED" }
        event["state"] = "RETURNED"
        step.expected?.let { expected ->
            event["result"] = result as Boolean
            if (result != expected) {
                val error = IllegalStateException("RECURRING_SETUP_GUARD_REJECTED")
                event["state"] = "THREW"; event["error"] = "GUARD_REJECTED"
                failedStep = step.name; remember(error); throw error
            }
        }
        return result
    }
    private fun threw(step: Operation, event: MutableMap<String, Any>, error: Throwable): Nothing {
        if (failure == null) {
            event["state"] = "THREW"; event["error"] = if (error is IOException) "IO" else "OTHER"
            failedStep = step.name; remember(error)
        }
        throw checkNotNull(failure)
    }
    fun <T> call(step: Operation, body: () -> T): T {
        val event = enter(step)
        return try { returned(step, event, body()) } catch (error: Throwable) { threw(step, event, error) }
    }
    fun requireActive() { failure?.let { throw it } }
    suspend fun <T> callSuspend(step: Operation, body: suspend () -> T): T {
        val event = enter(step)
        return try { returned(step, event, body()) } catch (error: Throwable) { threw(step, event, error) }
    }
    /** A real wrapper failure between callbacks has no invented entered operation. */
    fun outside(error: Throwable): Throwable {
        if (failure == null) {
            externalFailure = mapOf("kind" to "BETWEEN_STEPS", "error" to if (error is IOException) "IO" else "OTHER")
            remember(error)
        } else remember(error)
        return checkNotNull(failure)
    }
    fun projection(): Map<String, Any> = linkedMapOf<String, Any>(
        "events" to events.map { it.toMap() },
        "status" to if (failure != null) "FAILED" else if (events.size == profile.size && events.last()["state"] == "RETURNED") "COMPLETE" else "PREFIX",
    ).apply {
        failedStep?.let { put("first_negative_step", it) }
        protocolError?.let { put("protocol_error", it.toMap()) }
        externalFailure?.let { put("external_failure", it.toMap()) }
    }

    private fun remember(error: Throwable) {
        val first = failure
        if (first == null) {
            failure = error
            try { retain(error) } catch (secondary: Throwable) { if (secondary !== error && error.suppressed.none { it === secondary }) error.addSuppressed(secondary) }
        } else if (first !== error && first.suppressed.none { it === error }) first.addSuppressed(error)
    }

    fun state(index: Int): String = events.getOrNull(index)?.let {
        if (it["state"] == "RETURNED" && profile[index].expected != null) "RETURNED_${it["result"].toString().uppercase()}"
        else it["state"] as String
    } ?: "NOT_REACHED"
}
