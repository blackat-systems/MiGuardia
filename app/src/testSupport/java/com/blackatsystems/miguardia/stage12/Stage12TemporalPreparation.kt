package com.blackatsystems.miguardia.stage12


/** Finite journal of the temporal fixture setup; Activity operations have a separate trace. Values and original errors stay in RAM. */
internal class Stage12TemporalPreparation(retain: (Throwable) -> Unit) {
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
        BINDING_BEFORE_CREATE, DATABASE_CREATE, FIXTURE_CREATE, CIVIL_DATE(true),
        SETUP_READBACK, SETUP_STORE_CLOSE, REOPEN_PATHS, SETUP_STORE_REOPEN, REOPEN_VALIDATION, REOPEN_READBACK,
    }
    private val journal = Stage12PreparationJournal(Step.entries.map { Stage12PreparationJournal.Operation(it.name, it.expected) }, retain)
    val failedStep: Step? get() = journal.failedStep?.let(Step::valueOf)
    fun <T> call(step: Step, body: () -> T): T = journal.call(Stage12PreparationJournal.Operation(step.name, step.expected), body)
    suspend fun <T> callSuspend(step: Step, body: suspend () -> T): T = journal.callSuspend(Stage12PreparationJournal.Operation(step.name, step.expected), body)
    fun outside(error: Throwable): Throwable = journal.outside(error)
    fun guard(step: Step, body: () -> Boolean) { call<Boolean>(step, body) }
    fun projection(): Map<String, Any> = linkedMapOf<String, Any>("schema" to "stage12-temporal-measured-setup-v1").apply {
        putAll(journal.projection())
        put("creation", mapOf("private_root" to journal.state(Step.PRIVATE_MKDIR.ordinal), "data" to journal.state(Step.DATA_MKDIR.ordinal),
            "database" to journal.state(Step.DATABASE_CREATE.ordinal), "database_validation" to journal.state(Step.REOPEN_READBACK.ordinal)))
    }
}

