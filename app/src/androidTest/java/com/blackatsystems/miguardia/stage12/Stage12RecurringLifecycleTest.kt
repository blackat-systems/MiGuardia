package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.V2RecurringShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.shift.*
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.management.*
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import com.blackatsystems.miguardia.stage12.Stage12RecurringPreparation.Step as SetupStep

/** Real MiGuardiaApp bindings in an owned ComponentActivity; no MainActivity/bootstrap claim. */
class Stage12RecurringLifecycleTest {
    private val trace = Stage12SelectionDiagnostics.Trace()
    private var pendingReceipt: JSONObject? = null
    private var pendingOutput: File? = null
    private var cleanup: (() -> Unit)? = null
    private val compose = safeConstruction { createEmptyComposeRule() }
    @get:Rule val rules: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                trace.finish({ compose.apply(base, description).evaluate() }, {
                    phase = "POST_RULE_OWN_CLEANUP"; checkNotNull(cleanup).invoke()
                }, { succeeded, cleaned, primary ->
                    pendingReceipt?.let { receipt ->
                        receipt.put("rule_evaluation_succeeded", succeeded).put("receipt_completion_scope", "WHOLE_TEST_RULE")
                            .put("cleanup_complete", cleaned).put("owned_resources_retained_on_failure", !cleaned)
                            .put("diagnostic", JSONObject(trace.projection()))
                            .put("status", if (primary == null && cleaned) "COMPLETE" else "FAILED")
                        if (primary != null) receipt.put("failure_phase", phase).put("failure_action_ordinal", action)
                            .put("failure_action_target_sha256", actionHash)
                        Stage12NativeReceiptFiles.publish(checkNotNull(pendingOutput), receipt.toString(2), primary)
                    }
                })
                if (trace.primary != null) throw Stage12SelectionDiagnostics.publicFailure()
            }
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val zone = com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()
    private val clock = RealClock(Clock.system(zone))
    private lateinit var run: String
    private lateinit var today: LocalDate
    private lateinit var activeCase: Stage12RecurringCase
    private lateinit var privateRoot: File
    private lateinit var dataRoot: File
    private lateinit var fixture: Stage12RecurringFixture
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var owner: ComponentActivity? = null
    private var store: LocalDataStore? = null
    private val own get() = checkNotNull(store) { "RECURRING_STORE_NOT_OPEN" }
    private lateinit var calendar: CalendarViewModel
    private lateinit var work: WorkSetupViewModel
    private lateinit var edit: V2ShiftEditViewModel
    private lateinit var recurring: V2RecurringPlanViewModel
    private lateinit var writer: Writer
    private lateinit var editorWriter: EditorWriter
    private lateinit var uuids: Ids
    private lateinit var photoStore: SchedulePhotoFileStore
    private val c get() = calendar.uiState.value
    private val w get() = work.uiState.value
    private val e get() = edit.uiState.value
    private val r get() = recurring.uiState.value
    private var phase = "PRECONDITIONS"
    private var action = 0
    private var actionHash = "NONE"
    private var reopens = 0
    private var setupStoreReopens = 0
    private val cases = mutableListOf<Stage12RecurringCase>()
    private val attempted = mutableSetOf<Stage12RecurringCase>()
    private val opened = mutableSetOf<Stage12RecurringCase>()
    private val pathBindings = mutableMapOf<Stage12RecurringCase, Stage12RecurringPaths>()
    private val preparations = mutableMapOf<Stage12RecurringCase, Stage12RecurringPreparation>()
    private val fixturePreparations = mutableMapOf<Stage12RecurringCase, Stage12RecurringFixturePrefix>()
    private val binding get() = checkNotNull(pathBindings[activeCase])
    private var storeConstructorReturns = 0
    private var ownerObjectsObserved = 0
    private val facts = JSONArray()
    private val readbacks = JSONArray()

    @Test fun mixedOccupancyPoliciesUseExactPreviewsAndAtomicRealWrites() = family("recurring-policies") {
        listOf(Stage12RecurringCase.KEEP_EXISTING to RecurringConflictPolicy.KEEP_EXISTING,
            Stage12RecurringCase.REPLACE_INTACT to RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            Stage12RecurringCase.KEEP_BOTH to RecurringConflictPolicy.KEEP_BOTH,
            Stage12RecurringCase.CANCEL to RecurringConflictPolicy.CANCEL).forEach { (ownCase, policy) ->
            isolated(ownCase) {
                phase = "POLICY_${policy.name}"
                val before = snapshot("before-policy-entry")
                drawer("drawer-action-calendar-repeat")
                await { r.stage == V2RecurringStage.FORM && !r.isLoading }
                check(r.mode == V2RecurringMode.CREATE && r.referenceDate == today && r.timelineId == fixture.place.timelineId)
                val planId = checkNotNull(r.draftPlanId)
                touch("v2-recurring-template-${fixture.primary.id}")
                ensureAdvanced("v2-recurring-surface", "v2-recurring-pattern-EVERY_N_DAYS")
                touch("v2-recurring-pattern-EVERY_N_DAYS"); fill("v2-recurring-interval", "1")
                fill("v2-recurring-position", "Own repeated position")
                touch("v2-recurring-policy-${policy.name}")
                fill("v2-recurring-start-date", inputDate(today)); fill("v2-recurring-end-date", inputDate(today.plusDays(9)))
                val visibleDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")
                compose.onNodeWithTag("v2-recurring-start-date")
                    .assertTextContains(today.format(visibleDateFormat))
                compose.onNodeWithTag("v2-recurring-end-date")
                    .assertTextContains(today.plusDays(9).format(visibleDateFormat))
                check(r.startDateText == today.format(visibleDateFormat) &&
                    r.endDateText == today.plusDays(9).format(visibleDateFormat))
                check(r.conflictPolicy == policy && r.patternKind == V2RecurringPatternKind.EVERY_N_DAYS && r.intervalText == "1")
                val next = uuids.issued.size; val clockIndex = clock.millisSamples.size
                touch("v2-recurring-review"); await { r.stage == V2RecurringStage.PREVIEW && !r.isLoading }
                check(uuids.issued.size == next + 11 && clock.millisSamples.size == clockIndex + 1)
                val timestamp = clock.millisSamples[clockIndex]
                check(timestamp > fixture.writes.values.maxOf { it.shift.updatedAt })
                val revision = fixture.expectedRevision(uuids.at(next), planId, 1, today, today.plusDays(9), 1,
                    fixture.primary, "Own repeated position", timestamp)
                val candidates = (0..9).map { offset -> fixture.expectedWrite(uuids.at(next + 1 + offset), today.plusDays(offset.toLong()),
                    fixture.primary, "Own repeated position", timestamp) }
                val createdOffsets = when (policy) {
                    RecurringConflictPolicy.KEEP_EXISTING -> setOf(1)
                    RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT -> setOf(1, 3)
                    RecurringConflictPolicy.KEEP_BOTH -> (0..9).toSet()
                    RecurringConflictPolicy.CANCEL -> emptySet()
                }
                val actions = (0..9).associate { offset -> today.plusDays(offset.toLong()) to when {
                    offset == 1 -> RecurringDateAction.CREATE
                    policy == RecurringConflictPolicy.CANCEL -> RecurringDateAction.BLOCKED_BY_CANCEL
                    policy == RecurringConflictPolicy.KEEP_BOTH -> RecurringDateAction.KEEP_BOTH
                    policy == RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT && offset == 3 -> RecurringDateAction.REPLACE_AUTOMATIC
                    else -> RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED
                } }
                val occupants = policyOccupants()
                val preview = checkNotNull(r.preview)
                check(preview.dates == actions.keys.toList() && preview.patternDescription == "Todos los días" &&
                    preview.freeDates == listOf(today.plusDays(1)) && preview.occupiedDates.toSet() == actions.keys - today.plusDays(1) &&
                    preview.protectedDates.toSet() == actions.keys - setOf(today.plusDays(1), today.plusDays(3)) &&
                    preview.medicalLeaveDates == setOf(today.plusDays(8)) && preview.canConfirm == (policy != RecurringConflictPolicy.CANCEL))
                check(preview.results.size == 10 && preview.results.all { result ->
                    result.action == actions.getValue(result.date) &&
                        result.occupants.map { it.shift to it.kind }.toSet() == occupants.getValue(result.date).toSet() &&
                        result.affectedShiftIds == if (policy == RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT && result.date == today.plusDays(3))
                            setOf(fixture.id("main-3"), fixture.id("replaceable-other")) else emptySet()
                })
                val expectedWarningPairs = if (policy == RecurringConflictPolicy.KEEP_BOTH) (0..9).flatMap { offset ->
                    occupants.getValue(today.plusDays(offset.toLong())).filter { it.first.status == ShiftStatus.PLANNED }
                        .map { setOf(it.first.id, candidates[offset].shift.id) }
                }.toSet() else emptySet()
                check(preview.warnings.size == expectedWarningPairs.size * 2 &&
                    preview.warnings.filterIsInstance<ShiftPlanningWarning.SameDate>().map { setOf(it.first.id, it.second.id) }.toSet() == expectedWarningPairs &&
                    preview.warnings.filterIsInstance<ShiftPlanningWarning.Overlap>().map { setOf(it.first.id, it.second.id) }.toSet() == expectedWarningPairs &&
                    preview.warnings.none { it is ShiftPlanningWarning.ShortRest })
                assertPreviewText(actions, occupants, expectedWarningPairs.size)
                Stage12RecurringReadbackOracle.requireExact(snapshot("policy-preview-readonly"), before)
                if (policy == RecurringConflictPolicy.CANCEL) {
                    compose.onNodeWithTag("v2-recurring-save").assertIsNotEnabled()
                    check(preview.mutation == null && writer.calls == 0)
                    recurringBack(); waitTag("v2-recurring-discard-dialog"); touch("v2-recurring-confirm-discard")
                    await { !r.isBlocking }
                    Stage12RecurringReadbackOracle.requireExact(snapshot("cancel-whole-batch"), before)
                } else {
                    val insertedWrites = candidates.filterIndexed { i, _ -> i in createdOffsets }
                    val newPlan = RecurringPlanAggregate(RecurringPlan(planId, fixture.place.timelineId, fixture.place.sector, timestamp), listOf(revision),
                        (0..9).map { offset -> RecurringOccurrence(planId, today.plusDays(offset.toLong()), revision.id,
                            if (offset in createdOffsets) candidates[offset].shift.id else null,
                            if (offset in createdOffsets) RecurringOccurrenceState.AUTOMATIC else RecurringOccurrenceState.EXCLUDED, timestamp, timestamp) })
                    val removedWrites = if (policy == RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT)
                        listOf(fixture.mainWrite(3), fixture.writes.getValue(fixture.id("replaceable-other"))) else emptyList()
                    val oldPlans = if (removedWrites.isNotEmpty()) fixture.plans.filter { p -> p.occurrences.any { it.shiftId in removedWrites.map { w -> w.shift.id } } } else emptyList()
                    val changedPlans = oldPlans.map { p -> p.copy(occurrences = p.occurrences.map { o ->
                        if (o.shiftId in removedWrites.map { it.shift.id }) o.copy(shiftId = null, state = RecurringOccurrenceState.RETIRED, updatedAt = timestamp) else o
                    }) }
                    val expected = Stage12RecurringReadbackOracle.expected(before,
                        removed = removedWrites.flatMap(Stage12RecurringReadbackOracle::write) + oldPlans.flatMap(Stage12RecurringReadbackOracle::plan),
                        inserted = insertedWrites.flatMap(Stage12RecurringReadbackOracle::write) + (changedPlans + newPlan).flatMap(Stage12RecurringReadbackOracle::plan))
                    touch("v2-recurring-save"); await { !r.isBlocking && !r.isSaving }
                    check(writer.calls == 1 && writer.commits == 1)
                    check(db { own.recurringPlans.getPlan(planId) } == newPlan)
                    changedPlans.forEach { check(db { own.recurringPlans.getPlan(it.plan.id) } == it) }
                    insertedWrites.forEach { check(db { own.v2Shifts.getShift(it.shift.id) } == V2ShiftLookup.V2(it)) }
                    Stage12RecurringReadbackOracle.requireExact(snapshot("policy-committed"), expected)
                    if (policy == RecurringConflictPolicy.KEEP_EXISTING) {
                        manage(planId); check(r.selectedPlan == newPlan)
                        recurringBack(); await { r.stage == V2RecurringStage.PLANS }; recurringBack(); await { !r.isBlocking }
                        workClose(); Stage12RecurringReadbackOracle.requireExact(snapshot("plans-entry-readonly"), expected)
                        claim("C092", "create-and-manage-own-plan")
                    }
                }
                claim(when (policy) { RecurringConflictPolicy.KEEP_EXISTING -> "A053"; RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT -> "A054";
                    RecurringConflictPolicy.KEEP_BOTH -> "A055"; RecurringConflictPolicy.CANCEL -> "A056" }, policy.name)
            }
        }
        claim("A051", "both-formatted-date-fields-and-exact-persisted-bounds")
        claim("A057", "four-exact-mixed-previews")
        claim("A058", "positive-whole-aggregate-and-new-owner-store", "positive")
    }

    @Test fun individualOccurrencesAndRealSeriesHandoffsPreserveAllAssociates() = family("recurring-occurrences") {
        isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
            phase = "INDIVIDUAL_AND_SERIES_HANDOFFS"
            val baseline = snapshot("before-individual-flow")
            val handoffDate = fixture.date(1); val handoff = fixture.mainWrite(1)
            openDayActions(handoffDate); touch("v2-edit-shift-${handoff.shift.id}")
            await { e.stage == V2ShiftEditStage.CHOOSE_EDIT_SCOPE && !e.isLoading }
            check(e.targetShiftId == handoff.shift.id && e.recurringOccurrence?.planId == fixture.main.plan.id)
            touch("v2-shift-edit-from-date")
            await { r.stage == V2RecurringStage.FORM && !r.isLoading && !e.isBlocking }
            check(r.mode == V2RecurringMode.CHANGE && r.draftPlanId == fixture.main.plan.id && r.cutDate == handoffDate && c.detailDate == null)
            recurringBack(); waitTag("v2-recurring-discard-dialog"); touch("v2-recurring-confirm-discard"); await { !r.isBlocking }
            Stage12RecurringReadbackOracle.requireExact(snapshot("change-handoff-cancelled"), baseline)
            openDayActions(handoffDate); touch("v2-delete-shift-${handoff.shift.id}")
            await { e.stage == V2ShiftEditStage.CHOOSE_DELETE_SCOPE && !e.isLoading }
            touch("v2-shift-finalize-from-date")
            await { r.stage == V2RecurringStage.PREVIEW && !r.isLoading && !e.isBlocking }
            check(r.mode == V2RecurringMode.FINALIZE && r.draftPlanId == fixture.main.plan.id && r.cutDate == handoffDate && c.detailDate == null)
            recurringBack(); await { !r.isBlocking }
            compose.onAllNodesWithTag("v2-recurring-discard-dialog").assertCountEquals(0)
            check(writer.calls == 0)
            Stage12RecurringReadbackOracle.requireExact(snapshot("finalize-handoff-cancelled"), baseline)

            phase = "INDIVIDUAL_EDIT_ASSOCIATES"
            val original = fixture.mainWrite(0)
            openDayActions(today); touch("v2-edit-shift-${original.shift.id}")
            await { e.stage == V2ShiftEditStage.CHOOSE_EDIT_SCOPE && !e.isLoading }
            touch("v2-shift-edit-only-this"); await { e.stage == V2ShiftEditStage.EDIT_FORM && !e.isLoading }
            check(e.originalWrite == original && e.date == today && e.targetShiftId == original.shift.id)
            val expectedActual = checkNotNull(db { own.shiftActuals.getExpectation(original.shift.id) })
            check(expectedActual.planned == original && expectedActual.previousActual == fixture.actual && expectedActual.observedClass == fixture.extraClass)
            touch("v2-shift-template-${fixture.individualAlternative.id}")
            fill("v2-shift-edit-position", "Own individually edited position")
            check(e.selectedTemplateId == fixture.individualAlternative.id && !e.usesHistoricalTemplate)
            touch("v2-shift-request-review")
            val warning = "Se conservarán el horario real y sus extras porque la planificación mantiene los mismos instantes."
            await { e.stage == V2ShiftEditStage.CONFIRM_WARNINGS && !e.isLoading }
            check(e.warnings == listOf(warning) && e.acknowledgedWarnings.isEmpty())
            compose.onNode(hasText("• $warning", substring = false) and hasAnyAncestor(hasTestTag("v2-shift-warning-dialog"))).assertExists()
            Stage12RecurringReadbackOracle.requireExact(snapshot("individual-edit-review-readonly"), baseline)
            touch("v2-shift-confirm-warnings"); await { e.stage == V2ShiftEditStage.REVIEW && !e.isLoading }
            check(e.acknowledgedWarnings == listOf(warning))
            var edited: V2ShiftWrite? = null; var editedPlan: RecurringPlanAggregate? = null; var afterEdit: Stage12StoreSnapshot? = null
            editorWriter.beforeUpdate = { mutation ->
                check(mutation.shiftsToUpdate.size == 1 && mutation.shiftsToInsert.isEmpty() && mutation.shiftIdsToDelete.isEmpty() &&
                    mutation.explicitDayStatusDatesToClear.isEmpty() && mutation.actualExpectations == mapOf(original.shift.id to expectedActual))
                val generated = mutation.shiftsToUpdate.single().shift.updatedAt
                check(generated > original.shift.updatedAt && clock.instantSamples.any { it.toEpochMilli() == generated.toEpochMilli() })
                val rawExpected = original.copy(shift = original.shift.copy(colorArgbSnapshot = fixture.individualAlternative.colorArgb,
                    position = "Own individually edited position", updatedAt = generated),
                    snapshot = original.snapshot.copy(templateId = fixture.individualAlternative.id, workTypeId = fixture.individualType.id,
                        workTypeNameSnapshot = fixture.individualType.name, workTypeBehaviorSnapshot = fixture.individualType.behavior))
                check(mutation.shiftsToUpdate.single() == rawExpected) { "RECURRING_INDIVIDUAL_EDIT_DEFINITION_DIFFERS" }
                val persisted = rawExpected.copy(shift = rawExpected.shift.copy(updatedAt = generated.truncatedTo(ChronoUnit.MILLIS)))
                val newPlan = fixture.main.copy(occurrences = fixture.main.occurrences.map { occurrence ->
                    if (occurrence.shiftId == original.shift.id) occurrence.copy(state = RecurringOccurrenceState.CUSTOMIZED,
                        updatedAt = maxOf(occurrence.updatedAt.plusMillis(1), persisted.shift.updatedAt)) else occurrence
                })
                edited = persisted; editedPlan = newPlan
                afterEdit = Stage12RecurringReadbackOracle.expected(baseline,
                    removed = Stage12RecurringReadbackOracle.write(original) + Stage12RecurringReadbackOracle.plan(fixture.main),
                    inserted = Stage12RecurringReadbackOracle.write(persisted) + Stage12RecurringReadbackOracle.plan(newPlan))
            }
            touch("v2-shift-save"); await { !e.isBlocking && !e.isSaving && c.detailDate == null }
            check(editorWriter.batchCalls == 1 && editorWriter.commits == 1)
            val expectedEdited = checkNotNull(edited); val expectedEditedPlan = checkNotNull(editedPlan); val expectedAfterEdit = checkNotNull(afterEdit)
            check(db { own.v2Shifts.getShift(original.shift.id) } == V2ShiftLookup.V2(expectedEdited))
            check(db { own.recurringPlans.getPlan(fixture.main.plan.id) } == expectedEditedPlan)
            val actualAfter = checkNotNull(db { own.shiftActuals.getExpectation(original.shift.id) })
            check(actualAfter.planned == expectedEdited && actualAfter.previousActual == fixture.actual && actualAfter.observedClass == fixture.extraClass)
            check(db { own.shiftNotes.getWithAttachments(fixture.notes.getValue(original.shift.id).note.id) } == fixture.notes.getValue(original.shift.id))
            check(db { own.shiftNotificationConfigs.getForShift(original.shift.id) } == fixture.notifications.getValue(original.shift.id))
            check(db { own.shiftActuals.observeExtraWorkClasses(fixture.place.timelineId, fixture.place.sector).first() } == listOf(fixture.extraClass))
            check(db { own.shiftSituations.inspect(original.shift.id) }?.previous == null)
            Stage12RecurringReadbackOracle.requireExact(snapshot("individual-final-save-complete-associates"), expectedAfterEdit)
            claim("C089", "post-save-complete-own-target-and-neighbour-associates", "same-instants-template-position-with-actual")

            phase = "INDIVIDUAL_DELETE_EXCLUSION"
            val delete = fixture.mainWrite(3)
            editorWriter.deletion = delete to checkNotNull(db { own.shiftActuals.getExpectation(delete.shift.id) })
            openDayActions(delete.shift.localStartDate); touch("v2-delete-shift-${delete.shift.id}")
            await { e.stage == V2ShiftEditStage.CHOOSE_DELETE_SCOPE && !e.isLoading }
            touch("v2-shift-delete-only-this"); await { e.stage == V2ShiftEditStage.CONFIRM_DELETE && !e.isLoading }
            touchTextIn("v2-shift-delete-dialog", "Conservar jornada"); await { e.stage == V2ShiftEditStage.DAY_ACTIONS }
            check(editorWriter.deleteCalls == 0)
            Stage12RecurringReadbackOracle.requireExact(snapshot("individual-delete-cancelled"), expectedAfterEdit)
            touch("v2-delete-shift-${delete.shift.id}"); await { e.stage == V2ShiftEditStage.CHOOSE_DELETE_SCOPE && !e.isLoading }
            touch("v2-shift-delete-only-this"); await { e.stage == V2ShiftEditStage.CONFIRM_DELETE && !e.isLoading }
            val afterDeletePlan = expectedEditedPlan.copy(occurrences = expectedEditedPlan.occurrences.map { occurrence ->
                if (occurrence.shiftId == delete.shift.id) occurrence.copy(shiftId = null, state = RecurringOccurrenceState.EXCLUDED,
                    updatedAt = maxOf(occurrence.updatedAt.plusMillis(1), delete.shift.updatedAt)) else occurrence
            })
            val expectedAfterDelete = Stage12RecurringReadbackOracle.expected(expectedAfterEdit,
                removed = Stage12RecurringReadbackOracle.write(delete) + Stage12RecurringReadbackOracle.plan(expectedEditedPlan),
                inserted = Stage12RecurringReadbackOracle.plan(afterDeletePlan))
            touch("v2-shift-confirm-delete"); await { !e.isBlocking && !e.isSaving && c.detailDate == null }
            check(editorWriter.deleteCalls == 1 && editorWriter.commits == 2 && writer.calls == 0)
            check(db { own.v2Shifts.getShift(delete.shift.id) } == V2ShiftLookup.Missing && db { own.recurringPlans.getOccurrenceForShift(delete.shift.id) } == null)
            check(db { own.recurringPlans.getPlan(fixture.main.plan.id) } == afterDeletePlan)
            Stage12RecurringReadbackOracle.requireExact(snapshot("individual-exclusion-complete"), expectedAfterDelete)
            claim("C091", "individual-customization-exclusion-and-two-real-series-handoffs")
        }
    }

    @Test fun changesAndFinalizationFromTodayKeepHistoryAndRollbackConflicts() = family("recurring-lifecycle") {
        isolated(Stage12RecurringCase.ROLLBACK) { recurringNegativeCases() }
        isolated(Stage12RecurringCase.LIFECYCLE) {
            phase = "CHANGE_FROM_TODAY"
            val before = snapshot("before-lifecycle-entry")
            manage(fixture.main.plan.id)
            check(r.referenceDate == today && r.selectedPlan == fixture.main)
            touch("v2-recurring-change-plan"); await { r.stage == V2RecurringStage.FORM && !r.isLoading }
            check(r.mode == V2RecurringMode.CHANGE && r.cutDate == today && r.draftPlanId == fixture.main.plan.id)
            compose.onNodeWithTag("v2-recurring-cut-date").assertTextContains(fullDate(today), substring = true)
            compose.onAllNodesWithTag("v2-recurring-start-date").assertCountEquals(1)
            compose.onNodeWithTag("v2-recurring-start-date").assertIsNotEnabled()
            check(r.startDateText == today.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")))
            touch("v2-recurring-template-${fixture.changeTemplate.id}")
            ensureAdvanced("v2-recurring-surface", "v2-recurring-pattern-EVERY_N_DAYS")
            touch("v2-recurring-pattern-EVERY_N_DAYS"); fill("v2-recurring-interval", "2")
            fill("v2-recurring-position", "Own changed future position")
            fill("v2-recurring-end-date", inputDate(fixture.date(14)))
            val next = uuids.issued.size; val clockIndex = clock.millisSamples.size
            touch("v2-recurring-review"); await { r.stage == V2RecurringStage.PREVIEW && !r.isLoading }
            check(uuids.issued.size == next + 3 && clock.millisSamples.size == clockIndex + 1)
            val timestamp = clock.millisSamples[clockIndex]
            check(timestamp > fixture.main.occurrences.maxOf { it.updatedAt })
            val revision = fixture.expectedRevision(uuids.at(next), fixture.main.plan.id, 2, today, fixture.date(14), 2,
                fixture.changeTemplate, "Own changed future position", timestamp)
            val updates = listOf(6, 10, 12).map { offset ->
                val old = fixture.mainWrite(offset)
                fixture.expectedWrite(old.shift.id, fixture.date(offset), fixture.changeTemplate, "Own changed future position", timestamp)
                    .let { it.copy(shift = it.shift.copy(createdAt = old.shift.createdAt)) }
            }
            val created = fixture.expectedWrite(uuids.at(next + 2), fixture.date(14), fixture.changeTemplate, "Own changed future position", timestamp)
            val removed = listOf(1, 11).map(fixture::mainWrite)
            val changed = fixture.main.copy(revisions = fixture.main.revisions + revision,
                occurrences = fixture.main.occurrences.map { o -> when (o.localDate) {
                    fixture.date(1), fixture.date(11) -> o.copy(shiftId = null, state = RecurringOccurrenceState.RETIRED, updatedAt = timestamp)
                    fixture.date(6), fixture.date(10), fixture.date(12) -> o.copy(revisionId = revision.id, updatedAt = timestamp)
                    else -> o
                } } + RecurringOccurrence(fixture.main.plan.id, fixture.date(14), revision.id, created.shift.id,
                    RecurringOccurrenceState.AUTOMATIC, timestamp, timestamp))
            val expected = Stage12RecurringReadbackOracle.expected(before,
                removed = (removed + listOf(6, 10, 12).map(fixture::mainWrite)).flatMap(Stage12RecurringReadbackOracle::write) + Stage12RecurringReadbackOracle.plan(fixture.main),
                inserted = (updates + created).flatMap(Stage12RecurringReadbackOracle::write) + Stage12RecurringReadbackOracle.plan(changed))
            val changeActions = lifecycleActions(finalized = false)
            val changeOccupants = lifecycleOccupants(fixture.main, fixture.writes)
            checkLifecyclePreview(changeActions, changeOccupants, "Cada 2 días")
            Stage12RecurringReadbackOracle.requireExact(snapshot("change-preview-readonly"), before)
            val hold = CompletableDeferred<Unit>(); writer.hold = hold
            val save = compose.onNodeWithTag("v2-recurring-save")
            if (!save.isDisplayed()) save.performScrollTo()
            save.assertIsEnabled().performTouchInput { click(); advanceEventTime(70); click() }
            await { writer.entered.isCompleted && r.isSaving }
            check(writer.calls == 1 && writer.commits == 0)
            compose.onNodeWithTag("v2-recurring-save").assertIsNotEnabled()
            Stage12RecurringReadbackOracle.requireExact(snapshot("change-double-touch-before-release"), before)
            hold.complete(Unit); await { !r.isBlocking && !r.isSaving }
            check(writer.calls == 1 && writer.commits == 1)
            check(db { own.recurringPlans.getPlan(fixture.main.plan.id) } == changed)
            (updates + created).forEach { check(db { own.v2Shifts.getShift(it.shift.id) } == V2ShiftLookup.V2(it)) }
            removed.forEach { check(db { own.v2Shifts.getShift(it.shift.id) } == V2ShiftLookup.Missing) }
            Stage12RecurringReadbackOracle.requireExact(snapshot("change-committed-whole-store"), expected)
            workClose(); reopen()
            check(db { own.recurringPlans.getPlan(fixture.main.plan.id) } == changed)
            Stage12RecurringReadbackOracle.requireExact(snapshot("change-new-owner-store"), expected)
            claim("A059", "today-cut-change-with-past-exceptions-protections-and-double-touch")

            phase = "FINALIZE_FROM_TODAY"
            manage(fixture.main.plan.id); check(r.selectedPlan == changed && r.referenceDate == today)
            val finalNext = uuids.issued.size; val finalClock = clock.millisSamples.size
            touch("v2-recurring-finalize-plan"); await { r.stage == V2RecurringStage.PREVIEW && !r.isLoading }
            check(r.mode == V2RecurringMode.FINALIZE && r.cutDate == today && uuids.issued.size == finalNext + 1 && clock.millisSamples.size == finalClock + 1)
            val finalTime = clock.millisSamples[finalClock]; check(finalTime > timestamp)
            val finalRevision = revision.copy(id = uuids.at(finalNext), revisionNumber = 3, effectiveFrom = today,
                kind = RecurringPlanRevisionKind.FINALIZED, createdAt = finalTime)
            val retiredDates = setOf(6, 10, 12, 14).map(fixture::date).toSet()
            val finalized = changed.copy(revisions = changed.revisions + finalRevision,
                occurrences = changed.occurrences.map { o -> if (o.localDate in retiredDates)
                    o.copy(shiftId = null, state = RecurringOccurrenceState.RETIRED, updatedAt = finalTime) else o })
            val finalExpected = Stage12RecurringReadbackOracle.expected(expected,
                removed = (updates + created).flatMap(Stage12RecurringReadbackOracle::write) + Stage12RecurringReadbackOracle.plan(changed),
                inserted = Stage12RecurringReadbackOracle.plan(finalized))
            val currentWrites = fixture.writes.toMutableMap().apply {
                removed.forEach { remove(it.shift.id) }; (updates + created).forEach { put(it.shift.id, it) }
            }
            checkLifecyclePreview(lifecycleActions(finalized = true), lifecycleOccupants(changed, currentWrites), "Finalizar desde $today")
            compose.onNode(hasText("Se retirarán 4 jornadas creadas por la repetición que no tenían cambios y se conservarán 7 excepciones o protecciones.") and
                hasAnyAncestor(hasTestTag("v2-recurring-surface"))).assertExists()
            Stage12RecurringReadbackOracle.requireExact(snapshot("finalize-preview-readonly"), expected)
            touch("v2-recurring-save"); await { !r.isBlocking && !r.isSaving }
            check(writer.calls == 1 && writer.commits == 1)
            check(db { own.recurringPlans.getPlan(fixture.main.plan.id) } == finalized)
            (updates + created).forEach { check(db { own.v2Shifts.getShift(it.shift.id) } == V2ShiftLookup.Missing) }
            Stage12RecurringReadbackOracle.requireExact(snapshot("finalize-committed-whole-store"), finalExpected)
            workClose(); manage(fixture.main.plan.id); check(r.selectedPlan == finalized)
            compose.onNode(hasText("Estado: finalizado") and hasAnyAncestor(hasTestTag("v2-recurring-surface"))).assertExists()
            compose.onAllNodesWithTag("v2-recurring-change-plan").assertCountEquals(0)
            compose.onAllNodesWithTag("v2-recurring-finalize-plan").assertCountEquals(0)
            recurringBack(); await { r.stage == V2RecurringStage.PLANS }; recurringBack(); await { !r.isBlocking }; workClose()
            Stage12RecurringReadbackOracle.requireExact(snapshot("finalized-history-readonly"), finalExpected)
            claim("A060", "today-cut-finalization-keeps-complete-history-exceptions-and-protections")
        }
    }

    private fun lifecycleActions(finalized: Boolean): Map<LocalDate, RecurringDateAction> = ((0..12).toList() + 14).associate { offset ->
        fixture.date(offset) to when (offset) {
            0, 4, 5, 7, 8 -> RecurringDateAction.PRESERVE_PROTECTED
            2 -> RecurringDateAction.KEEP_CUSTOMIZED
            3 -> RecurringDateAction.KEEP_EXCLUDED
            9 -> RecurringDateAction.KEEP_RETIRED
            1, 11 -> if (finalized) RecurringDateAction.KEEP_RETIRED else RecurringDateAction.RETIRE_AUTOMATIC
            14 -> if (finalized) RecurringDateAction.RETIRE_AUTOMATIC else RecurringDateAction.CREATE
            else -> if (finalized) RecurringDateAction.RETIRE_AUTOMATIC else RecurringDateAction.UPDATE_AUTOMATIC
        }
    }
    private fun lifecycleOccupants(plan: RecurringPlanAggregate, writes: Map<UUID, V2ShiftWrite>): Map<LocalDate, List<Pair<Shift, RecurringOccupantKind>>> =
        ((0..12).toList() + 14).associate { offset ->
            val occurrence = plan.occurrences.singleOrNull { it.localDate == fixture.date(offset) }
            val occupants = occurrence?.shiftId?.let { id -> listOf(writes.getValue(id).shift to when (offset) {
                0, 4, 5, 7, 8 -> RecurringOccupantKind.PROTECTED
                2 -> RecurringOccupantKind.CUSTOMIZED
                else -> RecurringOccupantKind.AUTOMATIC_INTACT
            }) }.orEmpty()
            fixture.date(offset) to occupants
        }
    private fun checkLifecyclePreview(actions: Map<LocalDate, RecurringDateAction>, occupants: Map<LocalDate, List<Pair<Shift, RecurringOccupantKind>>>, pattern: String) {
        val preview = checkNotNull(r.preview)
        check(preview.dates == actions.keys.toList() && preview.patternDescription == pattern && preview.canConfirm && preview.warnings.isEmpty())
        check(preview.results.size == actions.size && preview.results.all { result ->
            result.action == actions.getValue(result.date) && result.occupants.map { it.shift to it.kind }.toSet() == occupants.getValue(result.date).toSet() &&
                result.affectedShiftIds == if (result.action in setOf(RecurringDateAction.UPDATE_AUTOMATIC, RecurringDateAction.RETIRE_AUTOMATIC))
                    occupants.getValue(result.date).map { it.first.id }.toSet() else emptySet()
        })
        check(preview.freeDates.toSet() == actions.filterValues { it == RecurringDateAction.CREATE }.keys &&
            preview.occupiedDates.toSet() == occupants.filterValues { it.isNotEmpty() }.keys &&
            preview.protectedDates.toSet() == occupants.filterValues { list -> list.any { it.second != RecurringOccupantKind.AUTOMATIC_INTACT } }.keys &&
            preview.medicalLeaveDates == setOf(fixture.date(8)))
        assertPreviewText(actions, occupants, 0)
    }

    private fun recurringNegativeCases() {
        phase = "EXPECTED_PROTECTION_CAS"
        val baseline = snapshot("negative-original")
        prepareNew(RecurringConflictPolicy.KEEP_BOTH, today, fixture.date(9))
        check(checkNotNull(r.preview).canConfirm)
        Stage12RecurringReadbackOracle.requireExact(snapshot("cas-preview-readonly"), baseline)
        val concurrent = ShiftNote(fixture.id("cas-concurrent-note"), fixture.mainWrite(3).shift.id,
            "Own concurrent protection", fixture.stamp.plusMillis(1), fixture.stamp.plusMillis(1))
        val afterWriter = Stage12RecurringReadbackOracle.expected(baseline, inserted = listOf(Stage12RecurringReadbackOracle.note(concurrent)))
        db { own.shiftNotes.insert(concurrent) }
        Stage12RecurringReadbackOracle.requireExact(snapshot("cas-declared-concurrent-writer"), afterWriter)
        touch("v2-recurring-save"); await { !r.isSaving && r.errorMessage != null }
        check(writer.calls == 1 && writer.commits == 0 && writer.errors == listOf("CONFLICTING_LOCAL_WRITE") &&
            r.stage == V2RecurringStage.FORM && r.preview == null && r.canRetry && r.successSequence == 0)
        assertErrorVisible()
        Stage12RecurringReadbackOracle.requireExact(snapshot("cas-no-partial-mutation"), afterWriter)
        claim("A058", "expected-protection-CAS-rejects-stale-preview-without-partial-write", "protection-CAS")
        recurringBack(); waitTag("v2-recurring-discard-dialog"); touch("v2-recurring-confirm-discard"); await { !r.isBlocking }

        phase = "EXPECTED_SQL_TRANSACTION_ROLLBACK"
        prepareNew(RecurringConflictPolicy.KEEP_EXISTING, fixture.date(30), fixture.date(30))
        val target = uuids.issued.last()
        val preview = checkNotNull(r.preview)
        check(preview.canConfirm && preview.dates == listOf(fixture.date(30)) && preview.results.single().action == RecurringDateAction.CREATE && preview.warnings.isEmpty())
        Stage12RecurringReadbackOracle.requireExact(snapshot("rollback-preview-readonly"), afterWriter)
        val sql = Stage12RecurringHistoryProducer.installRollback(context, binding, target)
        val afterTrigger = snapshot("rollback-explicit-fault-baseline")
        checkOnlyDeclaredTrigger(afterWriter, afterTrigger, sql)
        touch("v2-recurring-save"); await { !r.isSaving && r.errorMessage != null }
        check(writer.calls == 2 && writer.commits == 0 && writer.errors == listOf("CONFLICTING_LOCAL_WRITE", "INVALID_LOCAL_DATA") && writer.ownRollbackFaultObserved &&
            r.stage == V2RecurringStage.PREVIEW && r.preview == preview && r.canRetry && r.successSequence == 0)
        assertErrorVisible()
        Stage12RecurringReadbackOracle.requireExact(snapshot("rollback-no-partial-aggregate-or-pairs"), afterTrigger)
        claim("A058", "declared-own-SQL-fault-retains-complete-before-state", "transaction-rollback")
        recurringBack(); waitTag("v2-recurring-discard-dialog"); touch("v2-recurring-confirm-discard"); await { !r.isBlocking }
    }
    private fun prepareNew(policy: RecurringConflictPolicy, start: LocalDate, end: LocalDate) {
        drawer("drawer-action-calendar-repeat"); await { r.stage == V2RecurringStage.FORM && !r.isLoading }
        touch("v2-recurring-template-${fixture.primary.id}")
        ensureAdvanced("v2-recurring-surface", "v2-recurring-pattern-EVERY_N_DAYS")
        touch("v2-recurring-pattern-EVERY_N_DAYS"); fill("v2-recurring-interval", "1")
        fill("v2-recurring-position", "Own negative recurring position"); touch("v2-recurring-policy-${policy.name}")
        fill("v2-recurring-start-date", inputDate(start)); fill("v2-recurring-end-date", inputDate(end))
        val next = uuids.issued.size; val samples = clock.millisSamples.size
        touch("v2-recurring-review"); await { r.stage == V2RecurringStage.PREVIEW && !r.isLoading }
        check(uuids.issued.size == next + 2 + ChronoUnit.DAYS.between(start, end).toInt() && clock.millisSamples.size == samples + 1)
    }
    private fun assertErrorVisible() {
        val message = checkNotNull(r.errorMessage)
        compose.onNode(hasText(message, substring = false) and hasAnyAncestor(hasTestTag("v2-recurring-surface"))).assertExists()
    }
    private fun checkOnlyDeclaredTrigger(before: Stage12StoreSnapshot, after: Stage12StoreSnapshot, sql: String) {
        val table = before.tables.getValue("sqlite_master")
        fun text(value: String) = "T:" + java.util.Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        val fields = mapOf("type" to text("trigger"), "name" to text("stage12_recurring_expected_rollback"), "tbl_name" to text("shifts"), "rootpage" to "I:0", "sql" to text(sql))
        check(table.columns.toSet() == fields.keys)
        val row = table.columns.map(fields::getValue)
        check(table.rows.none { it == row })
        Stage12RecurringReadbackOracle.requireExact(after, before.copy(tables = before.tables +
            ("sqlite_master" to table.copy(rows = (table.rows + listOf(row)).sortedBy { it.joinToString("\u0000") }))))
    }

    private fun openDayActions(date: LocalDate) {
        check(!r.isBlocking && !e.isBlocking && c.detailDate == null && w.surface == WorkSetupSurface.NONE)
        val month = YearMonth.from(date); var remaining = 36
        while (c.visibleMonth != month) {
            check(remaining-- > 0); val previous = c.visibleMonth
            touch(if (previous < month) "calendar-next-month" else "calendar-previous-month")
            await { c.visibleMonth != previous && c.loadState == CalendarLoadState.CONTENT }
        }
        val day = c.days.single { it.date == date }
        touch(if (day.isFullyCompleted) "completed-day-$date" else "day-$date")
        await { c.detailDate == date && e.inspectionState == V2ShiftDayInspectionState.CONTENT }
        touch("day-other-options"); touch("v2-edit-day-action")
        await { e.stage == V2ShiftEditStage.DAY_ACTIONS && !e.isLoading && e.date == date }
    }
    private fun touchTextIn(root: String, caption: String) {
        requireReady(); action++; actionHash = hash("$root/$caption")
        compose.onAllNodesWithTag(root).assertCountEquals(1)
        val selector = hasText(caption, substring = false) and hasClickAction() and hasAnyAncestor(hasTestTag(root))
        compose.onAllNodes(selector).assertCountEquals(1); val node = compose.onNode(selector)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }

    private fun policyOccupants(): Map<LocalDate, List<Pair<Shift, RecurringOccupantKind>>> = (0..9).associate { offset ->
        val occupants = when (offset) {
            0 -> listOf(fixture.mainWrite(0).shift to RecurringOccupantKind.PROTECTED)
            1 -> emptyList()
            2 -> listOf(fixture.writes.getValue(fixture.id("manual-2")).shift to RecurringOccupantKind.MANUAL)
            3 -> listOf(fixture.mainWrite(3).shift to RecurringOccupantKind.AUTOMATIC_INTACT,
                fixture.writes.getValue(fixture.id("replaceable-other")).shift to RecurringOccupantKind.AUTOMATIC_INTACT)
            4 -> listOf(fixture.mainWrite(4).shift to RecurringOccupantKind.CUSTOMIZED)
            9 -> listOf(fixture.mainWrite(9).shift to RecurringOccupantKind.AUTOMATIC_INTACT,
                fixture.writes.getValue(fixture.id("manual-9")).shift to RecurringOccupantKind.MANUAL)
            else -> listOf(fixture.mainWrite(offset).shift to RecurringOccupantKind.PROTECTED)
        }
        today.plusDays(offset.toLong()) to occupants
    }

    private fun assertPreviewText(actions: Map<LocalDate, RecurringDateAction>, occupants: Map<LocalDate, List<Pair<Shift, RecurringOccupantKind>>>, warningPairs: Int) {
        val root = hasTestTag("v2-recurring-surface")
        compose.onNode(hasText("Cantidad total de fechas: ${actions.size}") and hasAnyAncestor(root)).assertExists()
        ensureAdvanced("v2-recurring-surface", "v2-recurring-exact-dates")
        val container = compose.onNodeWithTag("v2-recurring-exact-dates")
        requireReady(); container.performScrollTo().assertIsDisplayed()
        fun texts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: node.children.flatMap { texts(it) }
        val observed = texts(container.fetchSemanticsNode())
        val expectedDateLines = actions.map { (date, action) -> "${fullDate(date)} — ${actionLabel(action)}" }
        check(observed.filterNot { it.startsWith("  Ocupa: ") } == expectedDateLines)
        actions.entries.forEachIndexed { index, (date, action) ->
            requireReady()
            val line = expectedDateLines[index]; val from = observed.indexOf(line) + 1
            val until = if (index + 1 < expectedDateLines.size) observed.indexOf(expectedDateLines[index + 1]) else observed.size
            val expectedOccupants = occupants.getValue(date).map { "  Ocupa: ${it.first.objectiveNameSnapshot} · ${occupantLabel(it.second)}" }
            check(observed.subList(from, until).sorted() == expectedOccupants.sorted())
            val selector = hasText("${fullDate(date)} — ${actionLabel(action)}", substring = false) and hasAnyAncestor(hasTestTag("v2-recurring-exact-dates"))
            compose.onAllNodes(selector).assertCountEquals(1)
            compose.onNode(selector).performScrollTo().assertIsDisplayed()
        }
        compose.onNode(hasText("Libres: ${actions.values.count { it == RecurringDateAction.CREATE }} · ocupadas: ${occupants.values.count { it.isNotEmpty() }} · protegidas: ${occupants.values.count { list -> list.any { it.second != RecurringOccupantKind.AUTOMATIC_INTACT } }}") and hasAnyAncestor(root)).assertExists()
        for (caption in listOf("• Habrá una segunda jornada el mismo día.", "• Hay horarios superpuestos.")) {
            compose.onAllNodes(hasText(caption, substring = false) and hasAnyAncestor(root)).assertCountEquals(warningPairs)
        }
    }

    private fun claim(key: String, observation: String, variant: String = "functional") {
        for (level in listOf("UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED"))
            fact("recurring.$key.$level" + if (variant == "functional") "" else ".$variant", level,
                JSONObject().put("observation", observation).put("variant", variant))
    }
    private fun drawer(tag: String) { touch("main-menu-button"); touch(tag) }
    private fun ensureAdvanced(rootTag: String, requiredTag: String) {
        requireReady()
        if (compose.onAllNodesWithTag(requiredTag).fetchSemanticsNodes().isNotEmpty()) return
        val selector = hasTestTag("advanced-options-toggle") and hasAnyAncestor(hasTestTag(rootTag))
        compose.onAllNodes(selector).assertCountEquals(1)
        val node = compose.onNode(selector); if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performTouchInput { click() }; waitTag(requiredTag)
    }
    private fun recurringBack() {
        requireReady()
        val caption = if (r.stage == V2RecurringStage.PLANS) "Cerrar" else "Volver"
        val selector = hasText(caption, substring = false) and hasClickAction() and hasAnyAncestor(hasTestTag("v2-recurring-surface"))
        compose.onAllNodes(selector).assertCountEquals(1); compose.onNode(selector).assertIsEnabled().performTouchInput { click() }
    }
    private fun manage(planId: UUID) {
        check(!r.isBlocking && w.surface == WorkSetupSurface.NONE)
        drawer("drawer-action-work-setup"); waitTag("work-setup-overview")
        ensureAdvanced("work-setup-overview", "work-setup-recurring-plans"); touch("work-setup-recurring-plans")
        await { r.stage == V2RecurringStage.PLANS && !r.isLoading && r.plansReadSuccessfully }
        touch("v2-recurring-plan-$planId"); await { r.stage == V2RecurringStage.PLAN_DETAIL && r.selectedPlanId == planId && !r.isLoading }
    }
    private fun workClose() {
        requireReady()
        val selector = hasText("Cerrar", substring = false) and hasClickAction() and hasAnyAncestor(hasTestTag("work-setup-overview"))
        compose.onAllNodes(selector).assertCountEquals(1); compose.onNode(selector).performTouchInput { click() }
        await { w.surface == WorkSetupSurface.NONE }
    }
    private fun inputDate(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("ddMMuuuu"))
    private fun fullDate(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR")))
    private fun actionLabel(action: RecurringDateAction): String = when (action) {
        RecurringDateAction.CREATE -> "se creará"; RecurringDateAction.UPDATE_AUTOMATIC -> "se actualizará"
        RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED -> "se omitirá y quedará excluida"
        RecurringDateAction.REPLACE_AUTOMATIC -> "reemplazará una jornada repetida sin cambios"
        RecurringDateAction.KEEP_BOTH -> "se mantendrán ambas"; RecurringDateAction.PRESERVE_PROTECTED -> "se conservará por protección"
        RecurringDateAction.RETIRE_AUTOMATIC -> "se retirará"; RecurringDateAction.KEEP_CUSTOMIZED -> "se conservará personalizada"
        RecurringDateAction.KEEP_EXCLUDED -> "seguirá excluida"; RecurringDateAction.KEEP_RETIRED -> "seguirá retirada"
        RecurringDateAction.BLOCKED_BY_CANCEL -> "cancelada"
    }
    private fun occupantLabel(kind: RecurringOccupantKind): String = when (kind) {
        RecurringOccupantKind.MANUAL -> "jornada manual protegida"; RecurringOccupantKind.AUTOMATIC_INTACT -> "jornada repetida sin cambios"
        RecurringOccupantKind.CUSTOMIZED -> "personalizada protegida"; RecurringOccupantKind.PROTECTED -> "protegida por estado, nota, aviso u horario real"
    }

    private fun <T> safeConstruction(body: () -> T): T = try { body() } catch (error: Throwable) {
        trace.retain(error); throw Stage12SelectionDiagnostics.publicFailure()
    }

    private class RealClock(private val delegate: Clock) : Clock() {
        val millisSamples = mutableListOf<Instant>()
        val instantSamples = mutableListOf<Instant>()
        override fun getZone(): ZoneId = delegate.zone
        override fun withZone(zone: ZoneId): Clock = if (zone == this.zone) this else delegate.withZone(zone)
        override fun instant(): Instant = delegate.instant().also { instantSamples += it }
        override fun millis(): Long = delegate.millis().also { millisSamples += Instant.ofEpochMilli(it) }
    }
    private class Ids(private val prefix: String) : UuidProvider {
        val issued = mutableListOf<UUID>()
        fun at(index: Int): UUID = UUID.nameUUIDFromBytes("$prefix/$index".toByteArray(Charsets.UTF_8))
        override fun newUuid(): UUID = at(issued.size).also { issued += it }
    }
    private class Writer(private val delegate: V2RecurringShiftRepository) : V2RecurringShiftRepository by delegate {
        var calls = 0
        var commits = 0
        var hold: CompletableDeferred<Unit>? = null
        var entered = CompletableDeferred<Unit>()
        val errors = mutableListOf<String>()
        var ownRollbackFaultObserved = false
        override suspend fun applyRecurringPlanMutation(mutation: RecurringPlanMutation, expectedPlan: RecurringPlanExpectation,
            expectedOccupancy: ShiftOccupancyExpectation, expectedPairs: V2ShiftWriteExpectation, expectedProtection: RecurringProtectionExpectation) {
            calls++; entered.complete(Unit); hold?.await()
            try { delegate.applyRecurringPlanMutation(mutation, expectedPlan, expectedOccupancy, expectedPairs, expectedProtection) }
            catch (error: Throwable) {
                errors += when (error) { is ConflictingLocalWriteException -> "CONFLICTING_LOCAL_WRITE"; is InvalidLocalDataException -> "INVALID_LOCAL_DATA"; else -> "OTHER" }
                if (error is InvalidLocalDataException) ownRollbackFaultObserved = ownRollbackFaultObserved ||
                    generateSequence(error as Throwable?) { it.cause }.take(8).any {
                        it is SQLiteConstraintException && it.message?.contains("STAGE12_OWN_RECURRING_ROLLBACK") == true
                    }
                throw error
            }
            commits++
        }
    }
    private class EditorWriter(private val delegate: V2ShiftRepository) : V2ShiftRepository by delegate {
        var batchCalls = 0
        var deleteCalls = 0
        var commits = 0
        var beforeUpdate: ((V2ShiftBatchMutation) -> Unit)? = null
        var deletion: Pair<V2ShiftWrite, ShiftActualExpectation>? = null
        override suspend fun applyV2Batch(mutation: V2ShiftBatchMutation, expectedOccupancy: ShiftOccupancyExpectation,
            expectedUpdates: V2ShiftWriteExpectation) {
            batchCalls++; checkNotNull(beforeUpdate).invoke(mutation)
            delegate.applyV2Batch(mutation, expectedOccupancy, expectedUpdates); commits++
        }
        override suspend fun deleteShift(expected: V2ShiftWrite, expectedActual: ShiftActualExpectation?) {
            deleteCalls++; val intended = checkNotNull(deletion)
            check(expected == intended.first && expectedActual == intended.second) { "RECURRING_INDIVIDUAL_DELETE_IDENTITY_DIFFERS" }
            delegate.deleteShift(expected, expectedActual); commits++
        }
    }

    private fun family(slot: String, body: () -> Unit) {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == QA)
        val args = InstrumentationRegistry.getArguments(); check(args.getString("stage12CalendarClock") == "REAL")
        run = checkNotNull(args.getString("stage12RunId")); check(UUID.fromString(run).toString() == run)
        today = LocalDate.now(clock)
        val qa = checkNotNull(args.getString("stage12QaSha256")); val runner = checkNotNull(args.getString("stage12RunnerSha256"))
        check(listOf(qa, runner).all { it.matches(Regex("[0-9a-f]{64}")) })
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
        pendingOutput = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), slot)
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-recurring-lifecycle-family-v3").put("case", slot)
            .put("calendar_clock_mode", "REAL").put("zone", zone.id).put("civil_date", today.toString())
            .put("host", "COMPONENT_ACTIVITY_REAL_MIGUARDIA_APP_BINDINGS")
            .put("main_bootstrap_proven", false).put("process_death_proven", false).put("global_settings_changed", false)
            .put("received_application_store_accessed", false).put("round_credit", 0).put("facts", facts).put("readbacks", readbacks)
        pendingReceipt = receipt
        var ownerClosed = false; var storeClosed = false
        try { phase = "BODY"; body() } catch (error: Throwable) { trace.retain(error) }
        finally {
            try { trace.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_OWNER) { closeOwner(); ownerClosed = true } }
            catch (error: Throwable) { trace.retain(error) }
            if (ownerClosed) try { trace.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_STORE) { store?.close(); store = null; storeClosed = true } }
            catch (error: Throwable) { trace.retain(error) }
            receipt.put("owner_closed", ownerClosed).put("store_closed", storeClosed).put("owned_database_count", opened.size)
                .put("registered_case_count", cases.size).put("database_creation_attempts", attempted.size)
                .put("setup_store_reopens", setupStoreReopens).put("new_owner_store_reopens", reopens)
                .put("owned_database_count_scope", "VALIDATED_SETUP_ONLY")
                .put("store_constructor_returns", storeConstructorReturns).put("owner_objects_observed", ownerObjectsObserved)
                .put("preparation", JSONArray(preparations.map { (ownCase, journal) -> JSONObject(journal.projection()).put("case", ownCase.wire) }))
                .put("fixture_preparation", JSONArray(fixturePreparations.map { (ownCase, prefix) -> JSONObject(prefix.projection()).put("case", ownCase.wire) }))
            if (trace.primary == null && ownerClosed && storeClosed && cases.all { it in opened }) cleanup = {
                cases.forEach { ownCase ->
                    val paths = checkNotNull(pathBindings[ownCase]); paths.requireStored(stage12RecurringPathAccess(context))
                    val file = paths.database
                    check(context.deleteDatabase(paths.name))
                    check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(file.path + it).toPath(), NOFOLLOW_LINKS) })
                    paths.revalidate(stage12RecurringPathAccess(context))
                    deleteOwnTree(paths.privateRoot, ownCase.privateRootName(run))
                }
            }
        }
        trace.primary?.let { throw it }
    }

    private fun isolated(ownCase: Stage12RecurringCase, body: () -> Unit) {
        closeOwner(); store?.close(); store = null
        val journal = Stage12RecurringPreparation { trace.retain(it) }
        check(ownCase !in preparations); preparations[ownCase] = journal
        journal.call(SetupStep.CASE_REGISTERED) { activeCase = ownCase; check(ownCase !in cases); cases += ownCase }
        val access = stage12RecurringPathAccess(context)
        val paths = Stage12RecurringPaths.admit(access, run, ownCase, journal); pathBindings[ownCase] = paths
        privateRoot = paths.privateRoot; dataRoot = paths.dataRoot
        for ((suffix, step) in listOf("" to SetupStep.DATABASE_PRESENT, "-wal" to SetupStep.WAL_PRESENT,
            "-shm" to SetupStep.SHM_PRESENT, "-journal" to SetupStep.JOURNAL_PRESENT))
            journal.guard(step) { access.exists(File(paths.database.path + suffix)) }
        journal.guard(SetupStep.PRIVATE_PRESENT) { access.exists(privateRoot) }
        journal.call(SetupStep.PRIVATE_BINDING) { paths.revalidate(access) }
        journal.guard(SetupStep.PRIVATE_MKDIR) { privateRoot.mkdir() }
        journal.call(SetupStep.DATA_BINDING) { paths.revalidate(access) }
        journal.guard(SetupStep.DATA_MKDIR) { dataRoot.mkdir() }
        journal.call(SetupStep.BINDING_BEFORE_CREATE) { paths.revalidate(access) }
        journal.call(SetupStep.DATABASE_CREATE) { attempted += ownCase; store = createStore(paths) }
        fixture = journal.call(SetupStep.FIXTURE_CREATE) {
            val prefix = Stage12RecurringFixturePrefix(ownCase) { trace.retain(it) }
            fixturePreparations[ownCase] = prefix
            prefix.wrapper {
                prefix.call("WRAPPER_BEFORE") { Unit }
                val result = db {
                    prefix.call("BLOCK_ENTRY") { Unit }
                    val receiver = prefix.call("CREATE_RECEIVER") { Stage12RecurringFixture.Companion }
                    val argContext = prefix.call("ARG_CONTEXT") { context }
                    val argRun = prefix.call("ARG_RUN") { run }
                    val argCase = prefix.call("ARG_CASE") { ownCase }
                    val argStore = prefix.call("ARG_STORE") { own }
                    val argClock = prefix.call("ARG_CLOCK") { clock }
                    val argZone = prefix.call("ARG_ZONE") { zone }
                    val argData = prefix.call("ARG_DATA") { dataRoot }
                    val argBinding = prefix.call("ARG_BINDING") { paths }
                    prefix.call("CREATE_BEFORE") { Unit }
                    val created = receiver.create(argContext, argRun, argCase, argStore, argClock, argZone, argData, argBinding, prefix)
                    prefix.call("AFTER_CREATE_RETURN") { Unit }
                    prefix.call("BEFORE_BLOCK_RETURN") { Unit }
                    created
                }
                prefix.call("AFTER_WRAPPER_RETURN") { Unit }
                result
            }
        }
        journal.guard(SetupStep.CIVIL_DATE) { fixture.today == today }
        val fileContext = object : ContextWrapper(context) { override fun getFilesDir() = dataRoot }
        photoStore = journal.call(SetupStep.PHOTO_STORE) { SchedulePhotoFileStore(fileContext) }
        val before = journal.call(SetupStep.SETUP_READBACK) { snapshot("validated-setup") }
        val old = own; journal.call(SetupStep.SETUP_STORE_CLOSE) { old.close() }
        journal.call(SetupStep.REOPEN_PATHS) { paths.requireStored(access) }
        journal.call(SetupStep.SETUP_STORE_REOPEN) { store = createStore(paths); check(own !== old) }
        journal.call(SetupStep.REOPEN_VALIDATION) { db { fixture.validate(own, photoStore) } }
        journal.call(SetupStep.REOPEN_READBACK) { Stage12RecurringReadbackOracle.requireExact(snapshot("setup-new-store"), before) }
        opened += ownCase; setupStoreReopens++; uuids = Ids("$run/${ownCase.wire}/recurring")
        writer = Writer(own.recurringShiftWriter); editorWriter = EditorWriter(own.v2Shifts); launch(journal)
        Stage12RecurringReadbackOracle.requireExact(snapshot("setup-owner-mounted"), before)
        body()
        val end = snapshot("case-final"); reopen(); Stage12RecurringReadbackOracle.requireExact(snapshot("case-final-reopened"), end)
    }

    private fun createStore(paths: Stage12RecurringPaths): LocalDataStore =
        LocalDataStore.create(context, paths.name).also { storeConstructorReturns++ }

    private fun launch(journal: Stage12RecurringPreparation? = null) {
        check(scenario == null)
        fun start() { scenario = ActivityScenario.launch(Intent().setComponent(ComponentName(QA, ComponentActivity::class.java.name))) }
        fun mounted() { mount(); await { c.loadState == CalendarLoadState.CONTENT && w.rootState is WorkSetupState.V2Ready } }
        if (journal == null) { start(); mounted() }
        else { journal.call(SetupStep.ACTIVITY_LAUNCH) { start() }; journal.call(SetupStep.ACTIVITY_MOUNT) { mounted() } }
    }
    private fun mount() {
        checkNotNull(scenario).onActivity { activity ->
            owner = activity
            ownerObjectsObserved++
            val gate = LocalDataMutationGate()
            calendar = ViewModelProvider(activity, CalendarViewModel.Factory(own.shifts, own.explicitDayStatuses, own.medicalLeaves,
                own.holidays, own.vacations, clock, own.shiftSituations, photoStore, gate, own.calendarBatches))
                .get("$run-${activeCase.wire}-calendar", CalendarViewModel::class.java)
            work = ViewModelProvider(activity, WorkSetupViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives, clock))
                .get("$run-${activeCase.wire}-work", WorkSetupViewModel::class.java)
            edit = ViewModelProvider(activity, V2ShiftEditViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives,
                own.shifts, own.medicalLeaves, editorWriter, own.shiftActuals, own.recurringPlans, clock))
                .get("$run-${activeCase.wire}-edit", V2ShiftEditViewModel::class.java)
            recurring = ViewModelProvider(activity, V2RecurringPlanViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives,
                own.shifts, own.medicalLeaves, own.recurringPlans, writer, clock, zone, uuids))
                .get("$run-${activeCase.wire}-recurring", V2RecurringPlanViewModel::class.java)
            val uiContext = object : ContextWrapper(activity) { override fun getFilesDir() = dataRoot }
            activity.setContent {
                val cs by calendar.uiState.collectAsState(); val ws by work.uiState.collectAsState()
                val es by edit.uiState.collectAsState(); val rs by recurring.uiState.collectAsState()
                CompositionLocalProvider(LocalContext provides uiContext) {
                    MiGuardiaTheme {
                        MiGuardiaApp(calendarState = cs, onPreviousMonth = calendar::showPreviousMonth, onNextMonth = calendar::showNextMonth,
                            onToday = calendar::showCurrentMonth, onSelectDate = calendar::selectDate, onDismissDate = calendar::clearSelectedDate,
                            onRetry = calendar::retry, workSetupState = ws, workSetupActions = WorkSetupActions.from(work),
                            v2ShiftEditState = es, v2ShiftEditActions = V2ShiftEditActions.from(edit),
                            v2RecurringState = rs, v2RecurringActions = V2RecurringActions.from(recurring))
                    }
                }
            }
        }
    }
    private fun closeOwner() {
        scenario?.close(); check(owner == null || checkNotNull(owner).isDestroyed)
        scenario = null; owner = null
    }
    private fun reopen() {
        val oldStore = own; val oldOwner = owner; closeOwner(); oldStore.close()
        binding.requireStored(stage12RecurringPathAccess(context)); store = createStore(binding); check(own !== oldStore)
        writer = Writer(own.recurringShiftWriter); editorWriter = EditorWriter(own.v2Shifts); launch(); check(owner !== oldOwner); reopens++
    }
    private fun snapshot(stage: String): Stage12StoreSnapshot {
        val previous = phase
        val result = Stage12FullStoreReadback.captureRecurringOwned(context, run, activeCase.wire, binding,
            observe = { phase = "READBACK_${it.name}" })
        readbacks.put(JSONObject().put("case", activeCase.wire).put("stage", stage).put("tables_sha256", hash(result.tables.toString()))
            .put("files_sha256", hash(result.files.toString())).put("preferences_sha256", hash(result.preferences.toString()))
            .put("tables", result.tables.size).put("file_entries", result.files.size))
        phase = previous
        return result
    }
    private fun requireReady() = Stage12SelectionQaGuard.requireReady({ device.isScreenOn }, { device.currentPackageName == QA },
        { !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked }, { LocalDate.now(clock) == today })
    private fun await(condition: () -> Boolean) = compose.waitUntil(30_000) { requireReady(); condition() }
    private fun <T> checked(point: Stage12SelectionDiagnostics.Checkpoint, body: () -> T): T = trace.checked(point, body)
    private fun waitTag(tag: String) = checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
        await { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
    }
    private fun touch(tag: String) {
        action++; actionHash = hash(tag); waitTag(tag); val node = compose.onNodeWithTag(tag)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private fun text(value: String) {
        action++; actionHash = hash(value)
        val selector = hasText(value, substring = false) and hasClickAction()
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 } }
        val node = compose.onNode(selector)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private fun fill(tag: String, value: String) {
        waitTag(tag); val node = compose.onNodeWithTag(tag)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_REPLACE_TEXT) { node.performTextReplacement(value) }
        checked(Stage12SelectionDiagnostics.Checkpoint.HIDE_OWN_IME) { checkNotNull(scenario).onActivity { activity ->
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        } }
    }
    private fun <T> db(body: suspend () -> T): T = runBlocking { body() }
    private fun fact(key: String, level: String, observed: JSONObject = JSONObject()) {
        facts.put(JSONObject().put("assertion_key", key).put("level", level).put("check", "PASS").put("observed", observed))
    }
    private fun deleteOwnTree(root: File, name: String) {
        check(root == File(context.filesDir.canonicalFile, name) && !Files.isSymbolicLink(root.toPath()))
        val paths = Files.walk(root.toPath()).use { it.iterator().asSequence().toList() }
        check(paths.none(Files::isSymbolicLink) && paths.all { it.startsWith(root.toPath()) })
        paths.sortedByDescending { it.nameCount }.forEach { check(Files.deleteIfExists(it)) }
    }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object { const val QA = "com.blackatsystems.miguardia.qa" }
}
