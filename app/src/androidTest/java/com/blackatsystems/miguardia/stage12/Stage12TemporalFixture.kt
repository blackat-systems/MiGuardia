package com.blackatsystems.miguardia.stage12

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Own synthetic state, validated before UI and again after reopening the exact same paths. */
internal data class Stage12TemporalFixture(
    val recipe: Stage12TemporalFixtureRecipe,
    val binding: Stage12TemporalPaths,
    val initialExpected: Stage12StoreSnapshot,
) {
    suspend fun requireSeedReopened(opened: Stage12TemporalStore, context: Context) {
        val store = opened.requireBinding(binding)
        check(context.packageName == "com.blackatsystems.miguardia.qa" && binding.run == recipe.run && binding.scope == recipe.scope)
        binding.requireStored(stage12RecurringPathAccess(context))
        Stage12RecurringReadbackOracle.requireExact(
            Stage12FullStoreReadback.captureTemporalOwned(context, recipe.run, recipe.scope, binding), initialExpected)
        requireHistory(store.workConfiguration.get(), recipe)
        for (expected in recipe.shifts) check(store.v2Shifts.getShift(expected.shift.id) == V2ShiftLookup.V2(expected))
        check(store.availabilityWindows.get(recipe.neighbourWindow.id) == recipe.neighbourWindow)
        check(store.shiftNotes.getWithAttachments(recipe.note.id) == ShiftNoteWithAttachments(recipe.note, listOf(recipe.attachment)))
        check(store.shiftNotificationConfigs.getForShift(recipe.notice.shiftId) == recipe.notice)
    }

    companion object {
        private fun matchesHistory(actual: WorkConfigurationHistory?, recipe: Stage12TemporalFixtureRecipe): Boolean =
            actual != null && actual.timeline.id == recipe.business.timelineId &&
                actual.timeline.revisions == listOf(recipe.revision) && actual.perPeriodHoursValues.entries.isEmpty()

        private fun requireHistory(actual: WorkConfigurationHistory?, recipe: Stage12TemporalFixtureRecipe) {
            check(matchesHistory(actual, recipe)) { "TEMPORAL_CONFIGURATION_DIFFERS" }
        }

        suspend fun create(
            context: Context,
            run: String,
            scope: Stage12TemporalScope,
            opened: Stage12TemporalStore,
            clock: Clock,
            binding: Stage12TemporalPaths,
            prefix: Stage12TemporalFixturePrefix,
        ): Stage12TemporalFixture {
            prefix.call("ENTRY") { Unit }
            prefix.call("PACKAGE_QA") { context.packageName == "com.blackatsystems.miguardia.qa" }
            prefix.call("BINDING_IDENTITY") { binding.run == run && binding.scope == scope && prefix.scope == scope }
            val store = prefix.call("STORE_BINDING") { opened.requireBinding(binding) }
            val access = prefix.call("PATH_ACCESS") { stage12RecurringPathAccess(context) }
            prefix.call("PATH_REVALIDATE") { binding.revalidate(access) }
            val stamp = prefix.call("STAMP") { clock.instant().truncatedTo(ChronoUnit.MILLIS) }
            val recipe = prefix.call("RECIPE") { Stage12TemporalFixtureRecipe(run, scope, stamp) }
            prefix.call("RECIPE_VALIDATED") { recipe.requireSafeBeforeDelegates() }
            val fence = prefix.call("FILE_FENCE") { Stage12PathPolicy(binding.privateRoot) }
            for ((name, relative) in listOf("SCHEDULE_PHOTOS" to "files/schedule_photos", "DATASTORE" to "files/datastore",
                "CACHE" to "cache", "NO_BACKUP" to "no_backup")) {
                val directory = prefix.call("DIRECTORY_$name/RESOLVE") { fence.resolve(relative) }
                prefix.call("DIRECTORY_$name/ABSENT") { Files.exists(directory.toPath(), NOFOLLOW_LINKS) }
                prefix.call("DIRECTORY_$name/CREATE") { directory.mkdir() }
            }
            for ((index, name) in Stage12TemporalFixtureRecipe.preferenceNames.withIndex()) {
                val file = prefix.call("PREFERENCE_$index/RESOLVE") { fence.resolve("files/datastore/$name") }
                prefix.call("PREFERENCE_$index/ABSENT") { Files.exists(file.toPath(), NOFOLLOW_LINKS) }
                prefix.call("PREFERENCE_$index/CREATE_EMPTY") { Files.newOutputStream(file.toPath(), CREATE_NEW, WRITE).use { } }
            }
            // Force Room's real file/schema creation. Constructor return alone is not that evidence.
            val emptyRoot = prefix.callSuspend("ROOT_READ") { store.workConfiguration.get() }
            prefix.call("ROOT_EMPTY") { emptyRoot == null }
            val before = prefix.call("BASELINE_CAPTURE") { Stage12FullStoreReadback.captureTemporalOwned(context, run, scope, binding) }
            prefix.call("BASELINE_SCHEMA") {
                Stage12TemporalFixtureRecipe.businessTables.all { it in before.tables } &&
                    before.tables.keys.all { it in Stage12TemporalFixtureRecipe.businessTables ||
                        it in setOf("android_metadata", "room_master_table", "sqlite_master", "sqlite_sequence") }
            }
            prefix.call("BASELINE_EMPTY") { Stage12TemporalFixtureRecipe.businessTables.all { before.tables.getValue(it).rows.isEmpty() } }
            prefix.call("BASELINE_PREFERENCES") { before.preferences == recipe.expectedPrivatePreferences() }
            prefix.call("BASELINE_FILES") { before.files == recipe.expectedPrivateFiles(false) }
            val expected = prefix.call("EXPECTED_BUILD") { Stage12RecurringReadbackOracle.expected(before,
                inserted = recipe.expectedRows(), files = recipe.expectedPrivateFiles(true)) }
            prefix.callSuspend("CONFIG_CREATE") { store.workConfiguration.createInitial(recipe.business.timelineId, recipe.revision) }
            val history = prefix.callSuspend("CONFIG_READ") { store.workConfiguration.get() }
            prefix.call("CONFIG_MATCH") { matchesHistory(history, recipe) }
            prefix.callSuspend("FIRST_SET_CREATE") { store.workCatalog.createFirstWorkSet(recipe.firstSet) }
            recipe.additionalTemplates.forEachIndexed { index, template ->
                prefix.callSuspend("TEMPLATE_$index/CREATE") { store.workCatalog.createWorkTemplate(template) }
            }
            recipe.shifts.forEachIndexed { index, pair ->
                prefix.call("SHIFT_$index/ARGUMENT") { recipe.requireSeedArgumentBeforeDelegate(pair) }
                prefix.callSuspend("SHIFT_$index/INSERT") { store.v2Shifts.insert(pair) }
            }
            val configuration = prefix.call("WINDOW_CONFIGURATION") { recipe.business.configuration(recipe.ownerDate) }
            val window = recipe.neighbourWindow
            val expectation = prefix.callSuspend("WINDOW_EXPECTATION_READ") {
                store.availabilityWindows.captureExpectation(null, configuration, window.start, window.end)
            }
            prefix.call("WINDOW_EXPECTATION_MATCH") {
                expectation.previous == null && expectation.configuration.timelineId == recipe.business.timelineId &&
                    expectation.configuration.referenceDate == recipe.ownerDate && expectation.configuration.revision == recipe.revision &&
                    expectation.observedStart == window.start && expectation.observedEnd == window.end &&
                    expectation.observedWindows.isEmpty() && expectation.observedActiveSources.isEmpty() && expectation.protectionFingerprint.isEmpty()
            }
            val mutation = prefix.call("WINDOW_MUTATION_BUILD") { AvailabilityWindowMutation(expectation, window) }
            val saved = prefix.callSuspend("WINDOW_WRITE") { store.availabilityWindows.applyMutation(mutation) }
            prefix.call("WINDOW_SAVED") { saved == AvailabilityWindowWriteResult.Saved(window) }
            val image = prefix.call("PNG_RESOLVE") { fence.resolve("files/schedule_photos/${recipe.pngKey}") }
            prefix.call("PNG_ABSENT") { Files.exists(image.toPath(), NOFOLLOW_LINKS) }
            prefix.call("PNG_CREATE_WRITE") { Files.newOutputStream(image.toPath(), CREATE_NEW, WRITE).use { it.write(recipe.png) } }
            prefix.callSuspend("NOTE_SAVE") { store.shiftNotes.saveWithAttachments(recipe.note, listOf(recipe.attachment)) }
            prefix.callSuspend("NOTICE_SAVE") { store.shiftNotificationConfigs.replace(recipe.notice) }
            val after = prefix.call("READBACK_FINAL") { Stage12FullStoreReadback.captureTemporalOwned(context, run, scope, binding) }
            prefix.call("FINAL_MATCH") { Stage12RecurringReadbackOracle.requireExact(after, expected) }
            recipe.shifts.forEachIndexed { index, pair ->
                val read = prefix.callSuspend("VALIDATE_SHIFT_$index/READ") { store.v2Shifts.getShift(pair.shift.id) }
                prefix.guard("VALIDATE_SHIFT_$index/MATCH") { read == V2ShiftLookup.V2(pair) }
            }
            val readWindow = prefix.callSuspend("VALIDATE_WINDOW/READ") { store.availabilityWindows.get(window.id) }
            prefix.call("VALIDATE_WINDOW/MATCH") { readWindow == window }
            val readNote = prefix.callSuspend("VALIDATE_NOTE/READ") { store.shiftNotes.getWithAttachments(recipe.note.id) }
            prefix.call("VALIDATE_NOTE/MATCH") { readNote == ShiftNoteWithAttachments(recipe.note, listOf(recipe.attachment)) }
            val readNotice = prefix.callSuspend("VALIDATE_NOTICE/READ") { store.shiftNotificationConfigs.getForShift(recipe.notice.shiftId) }
            prefix.call("VALIDATE_NOTICE/MATCH") { readNotice == recipe.notice }
            return prefix.call("RETURN") { Stage12TemporalFixture(recipe, binding, expected) }
        }
    }
}
