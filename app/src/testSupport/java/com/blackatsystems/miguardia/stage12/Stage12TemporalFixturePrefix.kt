package com.blackatsystems.miguardia.stage12

/** A closed creation prefix per temporal scope. Original journal semantics are shared unchanged. */
internal class Stage12TemporalFixturePrefix(val scope: Stage12TemporalScope, retain: (Throwable) -> Unit) {
    private val profile = operations(scope)
    private val journal = Stage12PreparationJournal(profile, retain)
    private fun operation(name: String) = profile.firstOrNull { it.name == name } ?: Stage12PreparationJournal.Operation(name)
    fun <T> call(name: String, body: () -> T): T = journal.call(operation(name), body)
    fun guard(name: String, body: () -> Boolean) {
        call<Boolean>(name, body)
    }
    suspend fun <T> callSuspend(name: String, body: suspend () -> T): T = journal.callSuspend(operation(name), body)
    fun projection(): Map<String, Any> = linkedMapOf<String, Any>("schema" to "stage12-temporal-fixture-prefix-v1").apply {
        putAll(journal.projection())
    }
    fun expectedSteps(): List<String> = profile.map { it.name }

    companion object {
        fun operations(scope: Stage12TemporalScope): List<Stage12PreparationJournal.Operation> = buildList {
            fun action(name: String) { add(Stage12PreparationJournal.Operation(name)) }
            fun guard(name: String, expected: Boolean = true) { add(Stage12PreparationJournal.Operation(name, expected)) }
            action("ENTRY"); guard("PACKAGE_QA"); guard("BINDING_IDENTITY"); action("STORE_BINDING")
            action("PATH_ACCESS"); action("PATH_REVALIDATE"); action("STAMP"); action("RECIPE"); action("RECIPE_VALIDATED")
            action("FILE_FENCE")
            for (name in listOf("SCHEDULE_PHOTOS", "DATASTORE", "CACHE", "NO_BACKUP")) {
                action("DIRECTORY_$name/RESOLVE"); guard("DIRECTORY_$name/ABSENT", false); guard("DIRECTORY_$name/CREATE")
            }
            for (index in Stage12TemporalFixtureRecipe.preferenceNames.indices) {
                action("PREFERENCE_$index/RESOLVE"); guard("PREFERENCE_$index/ABSENT", false); action("PREFERENCE_$index/CREATE_EMPTY")
            }
            action("ROOT_READ"); guard("ROOT_EMPTY"); action("BASELINE_CAPTURE")
            guard("BASELINE_SCHEMA"); guard("BASELINE_EMPTY"); guard("BASELINE_PREFERENCES"); guard("BASELINE_FILES")
            action("EXPECTED_BUILD"); action("CONFIG_CREATE"); action("CONFIG_READ"); guard("CONFIG_MATCH")
            action("FIRST_SET_CREATE")
            val additionalTemplateCount = when (scope.variant) {
                Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT, Stage12TemporalVariant.TWENTY_FOUR_HOURS,
                Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL, Stage12TemporalVariant.RESUMPTION -> 1
                Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS,
                Stage12TemporalVariant.MONTH_CROSSING, Stage12TemporalVariant.YEAR_CROSSING_WINDOW -> 0
            }
            repeat(additionalTemplateCount) { action("TEMPLATE_$it/CREATE") }
            val shiftCount = when (scope.variant) {
                Stage12TemporalVariant.RESUMPTION -> 2
                Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT, Stage12TemporalVariant.TWENTY_FOUR_HOURS,
                Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL,
                Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS,
                Stage12TemporalVariant.MONTH_CROSSING, Stage12TemporalVariant.YEAR_CROSSING_WINDOW -> 1
            }
            repeat(shiftCount) { action("SHIFT_$it/ARGUMENT"); action("SHIFT_$it/INSERT") }
            action("WINDOW_CONFIGURATION"); action("WINDOW_EXPECTATION_READ"); guard("WINDOW_EXPECTATION_MATCH"); action("WINDOW_MUTATION_BUILD")
            action("WINDOW_WRITE"); guard("WINDOW_SAVED")
            action("PNG_RESOLVE"); guard("PNG_ABSENT", false); action("PNG_CREATE_WRITE")
            action("NOTE_SAVE"); action("NOTICE_SAVE"); action("READBACK_FINAL"); action("FINAL_MATCH")
            repeat(shiftCount) { action("VALIDATE_SHIFT_$it/READ"); guard("VALIDATE_SHIFT_$it/MATCH") }
            for (name in listOf("WINDOW", "NOTE", "NOTICE")) { action("VALIDATE_$name/READ"); guard("VALIDATE_$name/MATCH") }
            action("RETURN")
        }
    }
}
