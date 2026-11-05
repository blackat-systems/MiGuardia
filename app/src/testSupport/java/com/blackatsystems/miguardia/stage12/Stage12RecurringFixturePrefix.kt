package com.blackatsystems.miguardia.stage12

/** Closed measurement profile. It neither reads fixture data nor initializes the Android producer. */
internal class Stage12RecurringFixturePrefix(val ownCase: Stage12RecurringCase, retain: (Throwable) -> Unit) {
    private val journal = Stage12PreparationJournal(profile(ownCase).map { Stage12PreparationJournal.Operation(it) }, retain)
    private fun operation(name: String) = Stage12PreparationJournal.Operation(if (name in allowed) name else "UNRECOGNIZED_OPERATION")
    fun <T> call(name: String, body: () -> T): T = journal.call(operation(name), body)
    suspend fun <T> callSuspend(name: String, body: suspend () -> T): T = journal.callSuspend(operation(name), body)
    fun <T> wrapper(body: () -> T): T {
        journal.requireActive()
        return try { body() } catch (error: Throwable) { throw journal.outside(error) }
    }
    fun projection(): Map<String, Any> = linkedMapOf<String, Any>("schema" to "stage12-recurring-fixture-prefix-v1").apply { putAll(journal.projection()) }
    fun expectedSteps(): List<String> = profile(ownCase).toList()

    companion object {
        private fun build(ownCase: Stage12RecurringCase): List<String> = buildList {
            val single = ownCase == Stage12RecurringCase.SINGLE_OCCURRENCE
            val lifecycle = single || ownCase == Stage12RecurringCase.LIFECYCLE
            val main = (0..14).filter { if (single) it != 11 else if (lifecycle) it !in setOf(5, 11) else it !in setOf(3, 4) }
            val writeCount = main.size + if (lifecycle) 1 else 4
            fun addAllNames(vararg names: String) { addAll(names) }
            fun image(role: String) {
                for (name in listOf("KEY", "FILE", "PARENT_READY", "CREATE", "WRITE", "EXPECTATION")) add("IMAGE_$role/$name")
            }
            fun note(role: String) {
                add("NOTE_$role/BUILD"); image(role)
                for (name in listOf("ATTACHMENT", "SAVE_ARGUMENT", "SAVE", "EXPECTATION")) add("NOTE_$role/$name")
            }
            addAllNames("WRAPPER_BEFORE", "BLOCK_ENTRY", "CREATE_RECEIVER")
            for (arg in listOf("CONTEXT", "RUN", "CASE", "STORE", "CLOCK", "ZONE", "DATA", "BINDING")) add("ARG_$arg")
            addAllNames("CREATE_BEFORE", "CREATE_ENTRY", "PACKAGE_CHECK", "BINDING_CHECK", "PATH_ACCESS", "REVALIDATE", "DATA_ROOT_CHECK",
                "DATE", "STAMP", "ACTUAL_START", "ACTUAL_END", "ACTUAL_PAST", "TIMELINE", "CONFIG_REVISION", "CONFIG_CREATE",
                "OBJECTIVE_BUILD", "PLACE_BUILD", "TYPE_BUILD", "INDIVIDUAL_TYPE_BUILD", "PRIMARY_BUILD", "ALTERNATIVE_BUILD", "CHANGE_BUILD", "RULE_BUILD",
                "CONFIG_READ", "CONFIG_NONNULL", "CONFIG_RESOLVE", "FIRST_SET_BUILD", "FIRST_SET_CREATE", "INDIVIDUAL_TYPE_CREATE", "ALTERNATIVE_CREATE", "CHANGE_CREATE", "WRITES_PLAN")
            for (index in main) add("MAIN_BUILD/$index")
            if (!lifecycle) { add("MANUAL_BUILD/0"); add("MANUAL_BUILD/1") }
            add("SECOND_BUILD"); add("ALIEN_BUILD")
            repeat(writeCount) { add("SHIFT_INSERT/$it") }
            addAllNames("FILE_CONTEXT", "PHOTO_STORE", "PNG", "FILE_EXPECTATIONS", "NOTE_EXPECTATIONS")
            note("PROTECTION"); if (single) note("EDITED_TARGET")
            add("NOTICE_EXPECTATIONS")
            repeat(if (single) 2 else 1) { index -> for (name in listOf("BUILD", "REPLACE", "EXPECTATION")) add("NOTICE/$index/$name") }
            addAllNames("EXTRA_CLASS_BUILD", "EXTRA_CLASS_SAVE", "EXTRA_CLASS_SAVED", "ACTUAL_STAMP", "ACTUAL_RECORD")
            for (index in 1..2) add("ACTUAL_INTERVAL/$index")
            addAllNames("ACTUAL_AGGREGATE", "ACTUAL_EXPECTATION_ID", "ACTUAL_EXPECTATION_READ", "ACTUAL_EXPECTATION_NONNULL", "ACTUAL_DRAFT_SELECTION")
            for (index in 1..2) add("ACTUAL_FRAGMENT/$index")
            addAllNames("ACTUAL_DRAFT", "ACTUAL_MUTATION_BUILD", "ACTUAL_MUTATION_NONNULL", "ACTUAL_COMPARE", "ACTUAL_SAVE", "ACTUAL_SAVED")
            image("ALIEN")
            addAllNames("SITUATION_STAMP", "SITUATION_BUILD", "SITUATION_READ", "SITUATION_NONNULL", "SITUATION_MUTATION", "SITUATION_APPLY", "ALIEN_EXPECTATION",
                "MEDICAL_BUILD", "MEDICAL_CREATE", "MAIN_PLAN_ID", "MAIN_REVISION", "MAIN_PLAN")
            repeat(15) { add("MAIN_OCCURRENCE/$it") }
            add("MAIN_AGGREGATE")
            if (!lifecycle) add("OTHER_PLAN_REPLACEABLE")
            addAllNames("OTHER_ALIEN_LOOKUP", "OTHER_PLAN_ALIEN", "OTHER_PLANS_LIST", "HISTORY_RECEIVER", "HISTORY_ARGS", "HISTORY_INSERT", "RESULT_MAPS", "RESULT_BUILD", "VALIDATE_ENTRY")
            val linkCounts = listOf(main.size) + if (lifecycle) listOf(1) else listOf(1, 1)
            for ((plan, count) in linkCounts.withIndex()) {
                add("VALIDATE_PLAN/$plan/READ"); add("VALIDATE_PLAN/$plan/COMPARE")
                repeat(count) { index -> add("VALIDATE_LINK/$plan/$index/ID"); add("VALIDATE_LINK/$plan/$index/READ"); add("VALIDATE_LINK/$plan/$index/COMPARE") }
            }
            repeat(writeCount) { index -> add("VALIDATE_SHIFT/$index/READ"); add("VALIDATE_SHIFT/$index/COMPARE") }
            addAllNames("VALIDATE_ALL_FLOW", "VALIDATE_ALL_READ", "VALIDATE_ALL_COMPARE")
            repeat(if (single) 2 else 1) { index -> add("VALIDATE_NOTE/$index/READ"); add("VALIDATE_NOTE/$index/COMPARE") }
            repeat(if (single) 2 else 1) { index -> add("VALIDATE_NOTICE/$index/READ"); add("VALIDATE_NOTICE/$index/COMPARE") }
            addAllNames("VALIDATE_ACTUAL_ID", "VALIDATE_ACTUAL_READ", "VALIDATE_ACTUAL_NONNULL", "VALIDATE_ACTUAL_COMPARE", "VALIDATE_SITUATION_READ", "VALIDATE_SITUATION_COMPARE", "VALIDATE_MEDICAL_FLOW", "VALIDATE_MEDICAL_READ", "VALIDATE_MEDICAL_COMPARE")
            repeat(if (single) 3 else 2) { index -> for (name in listOf("FILE", "LENGTH", "LENGTH_CHECK", "BYTES", "DIGEST", "DIGEST_CHECK")) add("VALIDATE_FILE/$index/$name") }
            addAllNames("VALIDATE_RETURN", "AFTER_CREATE_RETURN", "BEFORE_BLOCK_RETURN", "AFTER_WRAPPER_RETURN")
        }
        private val profiles = Stage12RecurringCase.entries.associateWith(::build)
        private val allowed = profiles.values.flatten().toSet()
        fun profile(ownCase: Stage12RecurringCase): List<String> = profiles.getValue(ownCase).toList()
    }
}
