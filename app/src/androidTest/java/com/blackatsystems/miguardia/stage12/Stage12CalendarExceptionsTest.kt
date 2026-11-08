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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.*
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
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftSituationRepository
import com.blackatsystems.miguardia.ui.Stage2DayActions
import com.blackatsystems.miguardia.ui.exceptions.*
import com.blackatsystems.miguardia.ui.vacation.*

class Stage12CalendarExceptionsTest {
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
                        // Diagnóstico local temporal (B1 humana, 8/11): sólo clases/mensajes/frames propios, sin datos.
                        if (primary != null) receipt.put("debug_primary_failure", org.json.JSONArray().also { chain ->
                            generateSequence(primary) { it.cause }.take(5).forEach { error ->
                                chain.put(JSONObject().put("type", error.javaClass.name).put("message", error.message ?: "")
                                    .put("frames", org.json.JSONArray(error.stackTrace.filter { it.className.startsWith("com.blackatsystems") }
                                        .take(25).map { "${it.className}.${it.methodName}:${it.lineNumber}" })))
                            }
                        })
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
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
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

    private lateinit var exceptionFixture: Stage12CalendarExceptionFixture
    private val exceptionPreparations = mutableMapOf<Stage12RecurringCase, Stage12PreparationJournal>()
    private lateinit var exceptions: ExceptionsViewModel
    private lateinit var vacation: VacationViewModel
    private val h get() = exceptions.uiState.value
    private val v get() = vacation.uiState.value
    private val medicalCreates = mutableListOf<MedicalLeave>()
    private val medicalUpdates = mutableListOf<Pair<MedicalLeave, MedicalLeave>>()
    private val medicalDeletes = mutableListOf<MedicalLeave>()
    private val vacationCreates = mutableListOf<Vacation>()
    private val vacationUpdates = mutableListOf<Pair<Vacation, Vacation>>()
    private val vacationDeletes = mutableListOf<Vacation>()
    private val holidayMutations = mutableListOf<HolidayBatchMutation>()
    private val holidayDeletes = mutableListOf<UUID>()
    private val situationMutations = mutableListOf<ShiftSituationMutation>()
    private val situationClockWindows = mutableListOf<Pair<Instant, Instant>>()
    private var situationSubmissionStarted: Instant? = null
    private var vacationWriteGate: CompletableDeferred<Unit>? = null
    private var vacationPendingGuardObserved = false
    private var waitOrdinal = 0
    private var lastWaitObservation: JSONObject? = null
    private val comparisons = JSONArray()
    private val roomClockTolerances = JSONArray()
    private var pickerInput: Uri? = null
    private var pickerSelections = 0
    private var pickerCancellations = 0
    private var pickerSource: File? = null
    private var pickerSourceUri: Uri? = null
    private val pickerRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?,
        ) {
            check(contract is ActivityResultContracts.PickVisualMedia && input is PickVisualMediaRequest)
            check(input.mediaType == ActivityResultContracts.PickVisualMedia.ImageOnly)
            requireReady()
            if (pickerInput == null) pickerCancellations++ else {
                check(pickerInput == pickerSourceUri)
                pickerSelections++
            }
            // Controlled delivery only; this never launches or enumerates a native gallery.
            dispatchResult(requestCode, pickerInput)
        }
    }
    private val pickerOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry get() = pickerRegistry }

    @Test fun medicalRangesErrorsEditsAndDeletionKeepTheCompleteStore() = family("calendar-medical") {
        medicalLifecycle()
    }
    @Test fun absenceCancellationAndConsciousRestorationKeepOnlyDeclaredDeltas() = family("calendar-situations") {
        situationLifecycle()
    }
    @Test fun holidaySelectionPoliciesAndIdentityChangesAreAtomicAndPersistent() = family("calendar-holidays") {
        holidayLifecycle()
    }
    @Test fun vacationRangesConflictsAndConsciousCasRecoveryKeepTheWinner() = family("calendar-vacations") {
        vacationLifecycle()
    }

    private fun extendFixture() {
        phase = "LITERAL_EXCEPTION_FIXTURE"
        val journal = Stage12PreparationJournal(Stage12CalendarExceptionFixture.profile()) { trace.retain(it) }
        check(exceptionPreparations.put(activeCase, journal) == null)
        try {
            exceptionFixture = db { Stage12CalendarExceptionFixture.create(context, own, fixture, binding, photoStore, clock, journal) }
            check(journal.projection()["status"] == "COMPLETE")
        } catch (error: Throwable) { throw journal.outside(error) }
        val initial = snapshot("literal-exception-setup")
        reopen()
        exact("literal-exception-setup-reopened", initial)
    }

    private inner class MedicalRecorder(private val delegate: MedicalLeaveRepository) : MedicalLeaveRepository by delegate {
        override suspend fun create(medicalLeave: MedicalLeave) {
            medicalCreates += medicalLeave
            delegate.create(medicalLeave)
        }
        override suspend fun updateExpected(expected: MedicalLeave, replacement: MedicalLeave) {
            medicalUpdates += expected to replacement
            delegate.updateExpected(expected, replacement)
        }
        override suspend fun deleteExpected(expected: MedicalLeave) {
            medicalDeletes += expected
            delegate.deleteExpected(expected)
        }
    }
    private inner class VacationRecorder(private val delegate: VacationRepository) : VacationRepository by delegate {
        override suspend fun insert(vacation: Vacation) { vacationCreates += vacation; vacationWriteGate?.await(); delegate.insert(vacation) }
        override suspend fun update(expected: Vacation, replacement: Vacation) {
            vacationUpdates += expected to replacement; delegate.update(expected, replacement)
        }
        override suspend fun delete(expected: Vacation) { vacationDeletes += expected; delegate.delete(expected) }
    }
    private inner class HolidayRecorder(private val delegate: HolidayRepository) : HolidayRepository by delegate {
        override suspend fun applyBatch(mutation: HolidayBatchMutation) { holidayMutations += mutation; delegate.applyBatch(mutation) }
        override suspend fun delete(id: UUID) { holidayDeletes += id; delegate.delete(id) }
    }
    private inner class SituationRecorder(private val delegate: ShiftSituationRepository) : ShiftSituationRepository by delegate {
        override suspend fun apply(mutation: ShiftSituationMutation) {
            situationMutations += mutation
            val before = clock.instant()
            try { delegate.apply(mutation) } finally { situationClockWindows += before to clock.instant() }
        }
    }

    private fun claim(key: String, observation: String, variant: String = "complete-measured-lifecycle") {
        listOf("UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED").forEach { level ->
            fact("exceptions.$key.$level.$variant", level,
                JSONObject().put("observation", observation).put("variant", variant))
        }
    }
    private fun exact(stage: String, expected: Stage12StoreSnapshot) = compareAlreadyCaptured(stage, snapshot(stage), expected)
    private fun compareAlreadyCaptured(stage: String, actual: Stage12StoreSnapshot, expected: Stage12StoreSnapshot) {
        val publicTables = setOf("medical_leaves", "vacations", "holidays", "shifts", "shift_work_snapshots",
            "shift_situations", "shift_actual_records", "shift_extra_intervals", "recurring_plans",
            "recurring_plan_revisions", "recurring_occurrences", "shift_notes", "shift_note_attachments")
        val differences = (actual.tables.keys + expected.tables.keys).filter { actual.tables[it] != expected.tables[it] }
        comparisons.put(JSONObject().put("case", activeCase.wire).put("stage", stage)
            .put("tables_equal", actual.tables == expected.tables).put("preferences_equal", actual.preferences == expected.preferences)
            .put("files_equal", actual.files == expected.files).put("different_table_count", differences.size)
            .put("different_table_codes", JSONArray(differences.map { if (it in publicTables) it else "OTHER_FIXED_SCHEMA_TABLE" }.distinct().sorted()))
            .put("expected_tables_sha256", hash(expected.tables.toString())).put("expected_files_sha256", hash(expected.files.toString()))
            .put("observation_scope", "ALREADY_CAPTURED_VALUES_BEFORE_ORIGINAL_COMPARISON_NO_FAILURE_QUERY"))
        checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { Stage12RecurringReadbackOracle.requireExact(actual, expected) }
    }
    private fun stage2MessageCode(message: String?): String = when (message) {
        null -> "NONE"
        "Carpeta médica guardada. Las jornadas se conservaron." -> "MEDICAL_SAVED"
        "Carpeta médica eliminada." -> "MEDICAL_DELETED"
        "La jornada volvió a Planificada." -> "PLANNED_SAVED"
        "La jornada quedó marcada como Ausencia." -> "ABSENCE_SAVED"
        "La jornada quedó marcada como Cancelada." -> "CANCELLATION_SAVED"
        "Sin espacio disponible" -> "IMAGE_SPACE_REJECTED"
        "El archivo seleccionado no es una imagen compatible." -> "IMAGE_TYPE_REJECTED"
        else -> "UNKNOWN"
    }

    private fun viewMonth(month: YearMonth) {
        check(c.detailDate == null && h.surface == ExceptionsSurface.NONE && v.surface == VacationSurface.NONE)
        var remaining = 36
        while (c.visibleMonth != month) {
            check(remaining-- > 0)
            val previous = c.visibleMonth
            touch(if (previous < month) "calendar-next-month" else "calendar-previous-month")
            await { c.visibleMonth != previous && c.loadState == CalendarLoadState.CONTENT }
        }
    }
    private fun touchCalendarDate(date: LocalDate) {
        val day = c.days.single { it.date == date }
        touch(if (day.isFullyCompleted) "completed-day-$date" else "day-$date")
    }
    private val ownedDetailSessions = mutableListOf<Stage12OwnedDetailSemantics.Session>()
    private fun ownedDetailProjection() = JSONArray(ownedDetailSessions.map { JSONObject(it.projection()) })
    private fun openOwnedConditionDetail(site: Stage12OwnedDetailSemantics.Site, date: LocalDate,
        exactText: String): Stage12OwnedDetailSemantics.Session {
        val detailOwner = owner
        var dayTag: String? = null
        val modal = hasTestTag("day-detail-modal-sheet")
        val scroll = hasTestTag("day-detail-scroll") and hasAnyAncestor(modal)
        val card = hasTestTag("day-condition-card") and hasAnyAncestor(scroll)
        val text = hasText(exactText, substring = false) and hasAnyAncestor(card)
        fun selector(part: Stage12OwnedDetailSemantics.Part): SemanticsMatcher = when (part) {
            Stage12OwnedDetailSemantics.Part.DAY -> hasTestTag(checkNotNull(dayTag)) and hasClickAction()
            Stage12OwnedDetailSemantics.Part.MODAL -> modal
            Stage12OwnedDetailSemantics.Part.SCROLL -> scroll
            Stage12OwnedDetailSemantics.Part.CARD -> card
            Stage12OwnedDetailSemantics.Part.TEXT -> text
        }
        val adapter = object : Stage12OwnedDetailSemantics.Adapter {
            override fun nowMs() = android.os.SystemClock.elapsedRealtime()
            override fun awaitWithin(timeoutMs: Long, condition: () -> Boolean) {
                check(timeoutMs in 1..30_000L)
                compose.waitUntil(timeoutMs) { requireReady(); condition() }
            }
            override fun context(): Stage12OwnedDetailSemantics.Context {
                requireReady()
                val calendarState = c
                return Stage12OwnedDetailSemantics.Context(
                    detailOwner != null && owner === detailOwner,
                    detailOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true,
                    calendarState.visibleMonth == YearMonth.from(date), calendarState.detailDate == null,
                    calendarState.detailDate == date, calendarState.stage2Busy,
                    h.surface == ExceptionsSurface.NONE && v.surface == VacationSurface.NONE,
                )
            }
            override fun dayModel(): Stage12OwnedDetailSemantics.DayModel {
                val rows = c.days.filter { it.date == date }
                val completed = if (rows.size == 1) rows.single().isFullyCompleted else null
                dayTag = completed?.let { if (it) "completed-day-$date" else "day-$date" }
                return Stage12OwnedDetailSemantics.DayModel(rows.size, completed)
            }
            override fun count(part: Stage12OwnedDetailSemantics.Part) =
                compose.onAllNodes(selector(part)).fetchSemanticsNodes().size
            override fun visible(part: Stage12OwnedDetailSemantics.Part) = compose.onNode(selector(part)).isDisplayed()
            override fun dayEnabled() = compose.onNode(selector(Stage12OwnedDetailSemantics.Part.DAY))
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null
            override fun openDate() {
                action++; actionHash = hash(checkNotNull(dayTag))
                compose.onNode(selector(Stage12OwnedDetailSemantics.Part.DAY)).performTouchInput { click() }
            }
            override fun scrollOwnerMatches(): Boolean {
                val ownScroll = compose.onNode(scroll).fetchSemanticsNode()
                var ancestor = compose.onNode(text).fetchSemanticsNode().parent
                var depth = 0
                while (ancestor != null) {
                    check(depth++ < 64)
                    if (ancestor.config.contains(androidx.compose.ui.semantics.SemanticsActions.ScrollBy))
                        return ancestor.id == ownScroll.id
                    ancestor = ancestor.parent
                }
                return false
            }
            override fun scrollText() { compose.onNode(text).performScrollTo() }
            override fun assertTextVisible() { compose.onNode(text).assertIsDisplayed() }
            override fun back() = device.pressBack()
        }
        val session = Stage12OwnedDetailSemantics.Session(site, run, activeCase.wire, date.toString(),
            hash(exactText), trace, adapter)
        check(ownedDetailSessions.none { it.site == site })
        ownedDetailSessions += session
        session.prepare()
        return session
    }

    private fun openDay(date: LocalDate, editing: Boolean = true) {
        viewMonth(YearMonth.from(date))
        touchCalendarDate(date)
        await { c.detailDate == date && !c.stage2Busy }
        if (editing) touch("day-other-options")
    }
    private fun closeDay() {
        requireReady()
        val detailOwner = checkNotNull(owner)
        val detailDate = checkNotNull(c.detailDate)
        val observed = Stage12OwnedBackEventSemantics.Observation()
        fun sameOwner() = owner === detailOwner &&
            detailOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        fun detailTransition(): Boolean? {
            if (!sameOwner()) return null
            val modals = compose.onAllNodesWithTag("day-detail-modal-sheet").fetchSemanticsNodes().size
            return when {
                c.detailDate == null && modals == 0 -> true
                c.detailDate == detailDate && modals == 1 &&
                    compose.onNodeWithTag("day-detail-modal-sheet").isDisplayed() -> false
                else -> null
            }
        }
        checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) {
            Stage12OwnedBackEventSemantics.execute(observed,
                formReady = { !c.stage2Busy && detailTransition() == false },
                confirmation = { detailTransition() },
                nativeCall = {
                    requireReady()
                    checked(Stage12SelectionDiagnostics.Checkpoint.NATIVE_ASSERTION) { device.pressBack() }
                },
                awaitUi = { condition -> await { condition() } },
            )
        }
    }
    private fun drawer(tag: String) { touch("main-menu-button"); touch(tag) }
    private fun touchDescription(value: String) {
        requireReady(); action++; actionHash = hash(value)
        val selector = hasContentDescription(value, substring = false) and hasClickAction()
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 } }
        val node = compose.onNode(selector)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private fun has(tag: String): Boolean = checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
    }
    private fun privateMedicalNoteAbsentFromCards(value: String) = checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) {
        compose.onAllNodesWithText(value, substring = true).assertCountEquals(0)
    }
    private fun textVisible(value: String) {
        checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) {
            compose.onNodeWithText(value, substring = false).assertIsDisplayed()
        }
    }
    private fun touchIn(root: SemanticsMatcher, caption: String) {
        requireReady(); action++; actionHash = hash(caption)
        val selector = hasText(caption, substring = false) and hasClickAction() and hasAnyAncestor(root)
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 } }
        val node = compose.onNode(selector)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private data class MedicalConfirmationObservation(
        val context: String, val ownCase: String, val actionOrdinal: Int,
        val observed: Stage12MedicalConfirmationSemantics.Observation,
    )
    private val medicalConfirmationObservations = mutableListOf<MedicalConfirmationObservation>()
    private fun medicalConfirmationProjection() = JSONArray(medicalConfirmationObservations.map { item ->
        val value = item.observed
        JSONObject().put("context", item.context).put("case", item.ownCase)
            .put("action_ordinal", item.actionOrdinal).put("phase", value.phase)
            .put("scope", "OWN_MEDICAL_TITLE_AND_TARGET_RANGE_RETURNED_QUERIES_NON_ATOMIC")
            .put("samples", JSONArray(value.samples.map { sample ->
                JSONObject().put("step", sample.step).also { result ->
                    sample.count?.let { result.put("count", it) }
                    sample.identity?.let { result.put("identity_matches", it) }
                    sample.value?.let { result.put("value", it) }
                }
            }))
            .put("input_intent_recorded", value.inputIntentRecorded)
            .put("input_wrapper_entered", value.inputEntered)
            .put("input_returned", value.inputReturned)
    })
    private fun touchMedicalConfirmation(leave: MedicalLeave, caption: String) {
        requireReady()
        val ownOwner = checkNotNull(owner)
        val ownDate = checkNotNull(c.detailDate)
        check(!c.stage2Busy && caption in setOf("Cancelar", "Eliminar"))
        action++; actionHash = hash(caption)
        val expectedTitle = "Eliminar carpeta médica"
        val expectedRange = "Se quitará sólo el rango ${isoInput(leave.startDate)} – " +
            "${isoInput(leave.endDateInclusive)}. Las jornadas se conservarán."
        val root = SemanticsMatcher("OWN_MEDICAL_CONFIRMATION_TITLE_AND_TARGET_RANGE") { node ->
            Stage12MedicalConfirmationSemantics.matchesRoot(
                isDialog().matches(node),
                hasAnyDescendant(hasText(expectedTitle, substring = false)).matches(node),
                hasAnyDescendant(hasText(expectedRange, substring = false)).matches(node),
            )
        }
        val selector = hasText(caption, substring = false) and hasClickAction() and hasAnyAncestor(root)
        val observed = Stage12MedicalConfirmationSemantics.Observation()
        medicalConfirmationObservations += MedicalConfirmationObservation(
            if (caption == "Cancelar") "MEDICAL_DELETE_CANCEL" else "MEDICAL_DELETE_CONFIRM",
            activeCase.wire, action, observed,
        )
        fun sameOwner() {
            requireReady()
            check(owner === ownOwner && ownOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            check(c.detailDate == ownDate && !c.stage2Busy)
        }
        Stage12MedicalConfirmationSemantics.execute(observed,
            roots = {
                sameOwner()
                checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
                    val nodes = compose.onAllNodes(root).fetchSemanticsNodes()
                    // Every returned node already satisfied the complete identity matcher.
                    Stage12MedicalConfirmationSemantics.Roots(nodes.size, nodes.size == 1)
                }
            },
            candidates = { sameOwner(); checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
                compose.onAllNodes(selector).fetchSemanticsNodes().size
            } },
            rootVisible = { sameOwner(); compose.onNode(root).isDisplayed() },
            rootEnabled = { sameOwner(); compose.onNode(root).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null },
            controlVisible = { sameOwner(); compose.onNode(selector).isDisplayed() },
            controlEnabled = { sameOwner(); compose.onNode(selector).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null },
            awaitUnique = { condition -> checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await(condition) } },
            checkpoint = { step, body ->
                check(step in setOf("ROOT_VISIBLE", "ROOT_ENABLED", "CONTROL_VISIBLE", "CONTROL_ENABLED"))
                checked(if (step.endsWith("_VISIBLE")) Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE
                    else Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { body() }
            },
            publish = { checkNotNull(pendingReceipt).put("medical_confirmation_observations", medicalConfirmationProjection()) },
            input = { entered ->
                sameOwner()
                checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) {
                    entered()
                    compose.onNode(selector).performTouchInput { click() }
                }
            },
        )
    }

    private fun headerNavigationMatcher(title: String, caption: String) =
        SemanticsMatcher("OWN_TEXT_HEADER_NAVIGATION") { node ->
            Stage12ExceptionTextActionCriterion.matchesAction(
                hasText(caption, substring = false).matches(node), hasClickAction().matches(node),
                hasAnySibling(hasText(title, substring = false)).matches(node),
            )
        }
    private fun touchOwnedTextAction(selector: SemanticsMatcher, caption: String, root: SemanticsMatcher? = null) {
        requireReady()
        val ownOwner = checkNotNull(owner)
        action++; actionHash = hash(caption)
        fun ready() {
            requireReady()
            check(owner === ownOwner && ownOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        val node = compose.onNode(selector)
        Stage12ExceptionTextActionCriterion.execute(
            query = {
                ready()
                if (root == null) Stage12ExceptionTextActionCriterion.Matches(null,
                    compose.onAllNodes(selector).fetchSemanticsNodes().size)
                else {
                    val count = compose.onAllNodes(root).fetchSemanticsNodes().size
                    Stage12ExceptionTextActionCriterion.Matches(count,
                        if (count == 1) compose.onAllNodes(selector).fetchSemanticsNodes().size else null)
                }
            },
            visible = {
                ready()
                if (root != null) check(compose.onNode(root).isDisplayed())
                node.isDisplayed()
            },
            enabled = {
                ready()
                if (root != null) check(compose.onNode(root).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null)
                node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null
            },
            awaitUnique = { condition -> await(condition) },
            checkpoint = { point, body -> checked(point) { body() } },
            input = { ready(); node.performTouchInput { click() } },
        )
    }
    private fun touchHeaderNavigation(title: String, caption: String) {
        when (title) {
            "Feriados" -> check(caption == "Cerrar" && h.surface == ExceptionsSurface.HOLIDAYS && !h.isSaving)
            "Vacaciones" -> check(caption == "Cerrar" && v.surface == VacationSurface.LIST && !v.isSaving)
            "Agregar vacaciones" -> check(caption == "Volver" && v.surface == VacationSurface.EDITOR && v.draft.editingId == null)
            "Editar vacaciones" -> check(caption == "Volver" && v.surface == VacationSurface.EDITOR && v.draft.editingId != null && !v.isSaving)
            else -> error("UNEXPECTED_OWN_HEADER")
        }
        touchOwnedTextAction(headerNavigationMatcher(title, caption), caption)
    }
    private fun dismissVacationError(expected: String, surface: VacationSurface) {
        check(surface in setOf(VacationSurface.LIST, VacationSurface.EDITOR))
        val before = v
        check(before.surface == surface && !before.isSaving && before.errorMessage == expected)
        val writes = listOf(vacationCreates.size, vacationUpdates.size, vacationDeletes.size)
        val messageRoot = SemanticsMatcher("OWN_ASSERTIVE_VACATION_ERROR") { node ->
            Stage12ExceptionTextActionCriterion.matchesMessageRoot(
                node.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Assertive,
                hasAnyDescendant(hasText(expected, substring = false)).matches(node),
            )
        }
        val dismiss = SemanticsMatcher("OWN_VACATION_ERROR_DISMISS") { node ->
            Stage12ExceptionTextActionCriterion.matchesAction(
                hasText("Cerrar", substring = false).matches(node), hasClickAction().matches(node),
                hasAnyAncestor(messageRoot).matches(node),
            )
        }
        touchOwnedTextAction(dismiss, "Cerrar", messageRoot)
        checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) {
            check(v == before.copy(errorMessage = null, infoMessage = null))
            check(listOf(vacationCreates.size, vacationUpdates.size, vacationDeletes.size) == writes)
        }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
            compose.onAllNodes(messageRoot).assertCountEquals(0)
        }
    }

    private fun fillLabel(label: String, value: String) {
        action++; actionHash = hash(label)
        val selector = hasText(label, substring = false) and hasSetTextAction()
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 } }
        val node = compose.onNode(selector)
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
    private fun isoInput(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
    private fun medicalRowControl(leave: MedicalLeave, caption: String) {
        touchIn(hasTestTag("medical-leave-card-${leave.id}"), caption)
    }
    private fun requireMedical(value: MedicalLeave, previous: MedicalLeave?, start: LocalDate, end: LocalDate,
        note: String?, started: Instant, ended: Instant,
    ) {
        check(value.startDate == start && value.endDateInclusive == end && value.privateNote == note)
        check(value.updatedAt.toEpochMilli() in maxOf(started.toEpochMilli(), previous?.updatedAt?.toEpochMilli()?.plus(1) ?: Long.MIN_VALUE)..
            maxOf(ended.toEpochMilli(), previous?.updatedAt?.toEpochMilli()?.plus(1) ?: Long.MIN_VALUE))
        if (previous == null) check(value.createdAt == value.updatedAt)
        else check(value.id == previous.id && value.createdAt == previous.createdAt)
    }
    private fun requireVacation(value: Vacation, previous: Vacation?, start: LocalDate, end: LocalDate,
        started: Instant, ended: Instant,
    ) {
        check(value.startDate == start && value.endDateInclusive == end)
        check(value.updatedAt.toEpochMilli() in maxOf(started.toEpochMilli(), previous?.updatedAt?.toEpochMilli()?.plus(1) ?: Long.MIN_VALUE)..
            maxOf(ended.toEpochMilli(), previous?.updatedAt?.toEpochMilli()?.plus(1) ?: Long.MIN_VALUE))
        if (previous == null) check(value.createdAt == value.updatedAt)
        else check(value.id == previous.id && value.createdAt == previous.createdAt)
    }

    private fun medicalLifecycle() {
        isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
            extendFixture(); phase = "MEDICAL_POSITIVE_LIFECYCLE"
            val first = Stage12CalendarExceptionFixture.FIRST
            val neighbour = MedicalLeave(exceptionFixture.id("medical-neighbour"), first, first.plusDays(2),
                "Nota privada vecina sintética", fixture.stamp, fixture.stamp)
            db { own.medicalLeaves.create(neighbour) }
            var expected = snapshot("medical-seeded-neighbour")
            val callsBefore = medicalCreates.size
            openDay(first); touch("day-add-medical-leave")
            fill("medical-leave-start", isoInput(first)); fill("medical-leave-end", isoInput(first.minusDays(1)))
            compose.onNodeWithTag("medical-leave-save").assertIsNotEnabled()
            exact("medical-inverted-range-no-write", expected)
            fill("medical-leave-end", isoInput(first.plusDays(2)))
            fill("medical-leave-note", "  Nota privada sintética  ")
            var started = clock.instant()
            touch("medical-leave-save")
            await { !c.stage2Busy && !has("medical-leave-dialog") && medicalCreates.size == callsBefore + 1 }
            var chosen = medicalCreates.last()
            requireMedical(chosen, null, first, first.plusDays(2), "Nota privada sintética", started, clock.instant())
            check(chosen.id != neighbour.id)
            expected = Stage12RecurringReadbackOracle.expected(expected,
                inserted = listOf(Stage12RecurringReadbackOracle.medical(chosen)))
            awaitMedicalCoverage(chosen.id, listOf(first, first.plusDays(1), first.plusDays(2)))
            exact("medical-created-complete", expected)

            var previous = chosen
            var edits = medicalUpdates.size
            medicalRowControl(chosen, "Editar")
            compose.onNodeWithTag("medical-leave-note").assertTextContains("Nota privada sintética")
            fill("medical-leave-note", "Nota privada corregida")
            started = clock.instant(); touch("medical-leave-save")
            await { !c.stage2Busy && !has("medical-leave-dialog") && medicalUpdates.size == edits + 1 }
            check(medicalUpdates.last().first == previous)
            chosen = medicalUpdates.last().second
            requireMedical(chosen, previous, first, first.plusDays(2), "Nota privada corregida", started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.medical(previous)),
                inserted = listOf(Stage12RecurringReadbackOracle.medical(chosen)))
            exact("medical-note-only-edit", expected)
            privateMedicalNoteAbsentFromCards("Nota privada corregida")

            previous = chosen; edits = medicalUpdates.size
            medicalRowControl(chosen, "Editar")
            fill("medical-leave-start", isoInput(first.plusDays(1)))
            fill("medical-leave-end", isoInput(first.plusDays(3)))
            started = clock.instant(); touch("medical-leave-save")
            await { !c.stage2Busy && !has("medical-leave-dialog") && medicalUpdates.size == edits + 1 }
            check(medicalUpdates.last().first == previous)
            chosen = medicalUpdates.last().second
            requireMedical(chosen, previous, first.plusDays(1), first.plusDays(3), "Nota privada corregida", started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.medical(previous)),
                inserted = listOf(Stage12RecurringReadbackOracle.medical(chosen)))
            awaitMedicalCoverage(chosen.id, (1..3).map { first.plusDays(it.toLong()) })
            check(c.days.single { it.date == first }.medicalLeaves.none { it.id == chosen.id })
            exact("medical-range-only-edit", expected)
            closeDay(); reopen(); exact("medical-range-reopened", expected)
            openDay(first.plusDays(1))
            medicalRowControl(chosen, "Editar")
            compose.onNodeWithTag("medical-leave-start").assertTextContains(isoInput(first.plusDays(1)))
            compose.onNodeWithTag("medical-leave-end").assertTextContains(isoInput(first.plusDays(3)))
            compose.onNodeWithTag("medical-leave-note").assertTextContains("Nota privada corregida")
            touch("medical-leave-cancel")
            privateMedicalNoteAbsentFromCards("Nota privada corregida")
            exact("medical-consult-reopened-readonly", expected)
            val deletes = medicalDeletes.size
            medicalRowControl(chosen, "Eliminar")
            touchMedicalConfirmation(chosen, "Cancelar")
            check(medicalDeletes.size == deletes)
            exact("medical-delete-cancelled", expected)
            medicalRowControl(chosen, "Eliminar")
            touchMedicalConfirmation(chosen, "Eliminar")
            await { !c.stage2Busy && medicalDeletes.size == deletes + 1 }
            check(medicalDeletes.last() == chosen)
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.medical(chosen)))
            await { c.days.none { day -> day.medicalLeaves.any { it.id == chosen.id } } }
            check(c.days.single { it.date == first.plusDays(1) }.medicalLeaves.single { it.id == neighbour.id } == neighbour)
            exact("medical-selected-only-delete", expected)
            closeDay()
            claim("C071", "note-only-and-range-only-edits-keep-UUID-createdAt-and-complete-associates")
            claim("C072", "cancel-then-confirm-delete-only-chosen-medical-UUID")
        }
        isolated(Stage12RecurringCase.KEEP_EXISTING) {
            extendFixture(); phase = "MEDICAL_INCLUSIVE_CONFLICT_AND_DRAFT"
            val first = Stage12CalendarExceptionFixture.FIRST
            val occupied = Vacation(exceptionFixture.id("vacation-conflict"), first.plusDays(2), first.plusDays(4),
                fixture.stamp, fixture.stamp)
            db { own.vacations.insert(occupied) }
            var expected = snapshot("medical-conflict-vacation-seeded")
            val creates = medicalCreates.size
            openDay(first); touch("day-add-medical-leave")
            fill("medical-leave-start", isoInput(first)); fill("medical-leave-end", isoInput(first.plusDays(2)))
            fill("medical-leave-note", "Borrador privado que debe conservarse")
            touch("medical-leave-save")
            await { !c.stage2Busy && c.stage2Message != null && medicalCreates.size == creates + 1 }
            check(c.stage2Message != "Carpeta médica guardada. Las jornadas se conservaron.")
            compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
            compose.onNodeWithTag("medical-leave-start").assertTextContains(isoInput(first))
            compose.onNodeWithTag("medical-leave-end").assertTextContains(isoInput(first.plusDays(2)))
            compose.onNodeWithTag("medical-leave-note").assertTextContains("Borrador privado que debe conservarse")
            compose.onNodeWithTag("medical-leave-save").assertIsEnabled()
            compose.onNodeWithTag("medical-leave-error").assertIsDisplayed()
            exact("medical-inclusive-conflict-retained-draft", expected)
            fill("medical-leave-end", isoInput(first.plusDays(1)))
            val started = clock.instant(); touch("medical-leave-save")
            await { !c.stage2Busy && !has("medical-leave-dialog") && medicalCreates.size == creates + 2 }
            val chosen = medicalCreates.last()
            requireMedical(chosen, null, first, first.plusDays(1), "Borrador privado que debe conservarse", started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                inserted = listOf(Stage12RecurringReadbackOracle.medical(chosen)))
            awaitMedicalCoverage(chosen.id, listOf(first, first.plusDays(1)))
            exact("medical-conscious-corrected-range", expected)
            closeDay()
            claim("C070", "inclusive-three-day-create-inverted-rejection-vacation-boundary-conflict-and-conscious-draft-correction")
        }
    }

    private fun awaitMedicalCoverage(id: UUID, dates: List<LocalDate>) {
        await { c.days.filter { day -> day.medicalLeaves.any { it.id == id } }.map { it.date } == dates }
    }

    private data class SituationExpected(
        val write: V2ShiftWrite,
        val previous: ShiftSituation?,
        val actual: ShiftActualAggregate?,
        val occurrence: RecurringOccurrence?,
        val snapshot: Stage12StoreSnapshot,
    )

    private fun situationLifecycle() {
        isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
            extendFixture(); phase = "ABSENCE_AND_CONSCIOUS_RESTORATION"
            var expected = initialSituation()
            val id = expected.write.shift.id
            openDay(Stage12CalendarExceptionFixture.FIRST)
            touch("shift-absence-$id")
            textVisible("Esta jornada tiene horario real y extras. Esta confirmación autoriza retirarlos dentro del mismo cambio.")
            compose.onNode(hasText("Confirmar") and hasAnyAncestor(isDialog())).assertIsEnabled()
            touchIn(isDialog(), "Cancelar")
            exact("absence-cancel-keeps-actual-and-neighbour", expected.snapshot)
            val calls = situationMutations.size
            touch("shift-absence-$id")
            fillLabel("Motivo (opcional)", "")
            fillLabel("Nota privada (opcional)", "")
            confirmSituation()
            awaitSituation(calls + 1, id, ShiftStatus.ABSENT)
            var mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.ABSENT, null, null, null, null)
            expected = appliedSituation(expected, mutation, "absence-empty-optionals-committed")
            check(expected.actual == null && expected.occurrence?.state == RecurringOccurrenceState.CUSTOMIZED)
            touch("shift-edit-situation-$id")
            fillLabel("Motivo (opcional)", "  Motivo sintético  ")
            fillLabel("Nota privada (opcional)", "Nota sintética de ausencia")
            confirmSituation()
            awaitSituation(calls + 2, id, ShiftStatus.ABSENT)
            mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.ABSENT, "Motivo sintético", "Nota sintética de ausencia", null, null)
            expected = appliedSituation(expected, mutation, "absence-edited-keeping-creation-and-plan")
            touch("shift-restore-$id")
            textVisible("Se retirarán: motivo, nota privada.")
            textVisible("La jornada y su plan original se conservan.")
            touchIn(isDialog(), "Cancelar")
            check(situationMutations.size == calls + 2)
            exact("absence-restoration-cancelled", expected.snapshot)
            touch("shift-restore-$id"); confirmSituation()
            awaitSituation(calls + 3, id, ShiftStatus.PLANNED)
            mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.PLANNED, null, null, null, null)
            expected = appliedSituation(expected, mutation, "absence-restored-only-metadata-removed")
            check(expected.occurrence?.state == RecurringOccurrenceState.CUSTOMIZED)
            closeDay()
            claim("C073", "empty-optionals-and-explicit-actual-removal-affect-only-selected-UUID")
            claim("C075", "absence-edit-and-cancel-confirm-restoration-with-exact-losses", "absence")
        }
        isolated(Stage12RecurringCase.KEEP_EXISTING) {
            extendFixture(); phase = "CANCELLATION_PRIVATE_SYNTHETIC_EVIDENCE"
            prepareSyntheticPickerSource()
            var expected = initialSituation()
            val id = expected.write.shift.id
            val notice = java.time.LocalDateTime.of(2026, 10, 23, 7, 30)
                .atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toInstant()
            val calls = situationMutations.size
            openDay(Stage12CalendarExceptionFixture.FIRST)
            touch("shift-cancel-$id")
            fillLabel("Motivo (obligatorio)", "")
            compose.onNode(hasText("Confirmar") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
            fillLabel("Motivo (obligatorio)", "  Cancelación sintética  ")
            fillLabel("Fecha y hora del aviso", "aviso inválido")
            compose.onNode(hasText("Confirmar") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
            fillLabel("Fecha y hora del aviso", "23/10/2026 07:30")
            fillLabel("Nota privada (opcional)", "Nota privada de cancelación")
            touchIn(isDialog(), "Cambiar")
            textVisible("Quién canceló: Empleador / organización")
            pickerInput = checkNotNull(pickerSourceUri)
            touchIn(isDialog(), "Elegir evidencia opcional")
            await { pickerSelections == 1 }
            textVisible("Imagen elegida. Se copiará al almacenamiento privado al confirmar.")
            touchIn(isDialog(), "Cancelar")
            check(situationMutations.size == calls)
            exact("cancellation-entire-form-cancelled-no-import", expected.snapshot)

            touch("shift-cancel-$id")
            fillLabel("Motivo (obligatorio)", "Cancelación sintética")
            fillLabel("Nota privada (opcional)", "Nota privada de cancelación")
            fillLabel("Fecha y hora del aviso", "23/10/2026 07:30")
            touchIn(isDialog(), "Cambiar")
            pickerInput = checkNotNull(pickerSourceUri)
            touchIn(isDialog(), "Elegir evidencia opcional")
            await { pickerSelections == 2 }
            textVisible("Imagen elegida. Se copiará al almacenamiento privado al confirmar.")
            confirmSituation()
            awaitSituation(calls + 1, id, ShiftStatus.CANCELLED)
            var mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.CANCELLED, "Cancelación sintética",
                "Nota privada de cancelación", CancellationActor.EMPLOYER, notice)
            requireSyntheticEvidence(checkNotNull(mutation.replacement?.evidence), id)
            expected = appliedSituation(expected, mutation, "cancellation-imported-original-PNG-exactly")
            val firstEvidence = checkNotNull(expected.previous?.evidence)
            check(photoStore.file(firstEvidence.storageKey).readBytes().contentEquals(syntheticPng()))
            closeDay(); reopen(); exact("cancellation-with-evidence-reopened", expected.snapshot)
            openDay(Stage12CalendarExceptionFixture.FIRST)
            touch("shift-edit-situation-$id")
            textVisible("Hay una evidencia privada guardada. Sólo se reemplaza si elegís otra imagen.")
            pickerInput = null
            touchIn(isDialog(), "Reemplazar evidencia guardada")
            await { pickerCancellations == 1 }
            textVisible("Hay una evidencia privada guardada. Sólo se reemplaza si elegís otra imagen.")
            fillLabel("Nota privada (opcional)", "Nota privada corregida")
            confirmSituation()
            awaitSituation(calls + 2, id, ShiftStatus.CANCELLED)
            mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.CANCELLED, "Cancelación sintética",
                "Nota privada corregida", CancellationActor.EMPLOYER, notice)
            check(mutation.replacement?.evidence == firstEvidence)
            expected = appliedSituation(expected, mutation, "cancellation-picker-cancel-keeps-existing-evidence")
            check(photoStore.file(firstEvidence.storageKey).readBytes().contentEquals(syntheticPng()))
            touch("shift-restore-$id")
            textVisible("Se retirarán: motivo, nota privada, quién canceló, fecha y hora del aviso, evidencia privada.")
            textVisible("La jornada y su plan original se conservan.")
            touchIn(isDialog(), "Cancelar")
            exact("cancellation-restoration-cancel-keeps-bytes", expected.snapshot)
            check(situationMutations.size == calls + 2)
            touch("shift-restore-$id"); confirmSituation()
            awaitSituation(calls + 3, id, ShiftStatus.PLANNED)
            mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.PLANNED, null, null, null, null)
            expected = appliedSituation(expected, mutation, "cancellation-restored-only-selected-evidence-removed")
            check(!photoStore.file(firstEvidence.storageKey).exists())
            check(checkNotNull(pickerSource).readBytes().contentEquals(syntheticPng()))
            closeDay()
            claim("C074", "real-cancellation-fields-controlled-private-PNG-result-import-and-complete-associates", "controlled-result-no-external-picker")
            fact("exceptions.C074.FILES_ISOLATED.controlled-result-no-external-picker", "FILES_ISOLATED",
                JSONObject().put("source_sha256", pngSha()).put("mime", "image/png").put("width", 1).put("height", 1)
                    .put("bytes", syntheticPng().size).put("mechanism", "OWN_FILE_PROVIDER_CONTROLLED_ACTIVITY_RESULT")
                    .put("external_picker_proven", false).put("selected", pickerSelections).put("cancelled", pickerCancellations))
            claim("C075", "cancellation-edit-exact-five-losses-cancel-confirm-and-private-file-removal", "cancellation")
        }
    }

    private fun initialSituation(): SituationExpected {
        val f = exceptionFixture
        return SituationExpected(f.target, null, f.actuals.first(), f.plan.occurrences.first(), snapshot("situation-initial-complete"))
    }
    private fun awaitSituation(calls: Int, id: UUID, status: ShiftStatus) {
        await { !c.stage2Busy && !has("shift-situation-dialog") && situationMutations.size == calls &&
            c.stage2Expectations[id]?.planned?.shift?.status == status }
    }
    private fun requireSituationCommand(old: SituationExpected, mutation: ShiftSituationMutation, status: ShiftStatus,
        reason: String?, note: String?, actor: CancellationActor?, notice: Instant?,
    ) {
        check(mutation.expected.planned == old.write && mutation.expected.previous == old.previous)
        check(mutation.expected.recurringOccurrence == old.occurrence)
        check(mutation.expected.actual?.previousActual == old.actual)
        check(mutation.expected.actual?.planned == old.write && mutation.expected.actual?.recurringOccurrence == old.occurrence)
        check(mutation.expected.actual?.observedClass == if (old.actual == null) null else fixture.extraClass)
        check(mutation.removeIncompatibleActual)
        val next = mutation.replacement
        if (status == ShiftStatus.PLANNED) check(next == null) else {
            checkNotNull(next)
            check(next.shiftId == old.write.shift.id && next.status == status)
            check(next.reason == reason && next.privateNote == note && next.cancellationActor == actor && next.notifiedAt == notice)
            if (old.previous != null) check(next.createdAt == old.previous.createdAt && next.updatedAt > old.previous.updatedAt)
            else check(next.createdAt == next.updatedAt)
            val submitted = checkNotNull(situationSubmissionStarted).toEpochMilli()
            val previous = old.previous?.updatedAt?.toEpochMilli()?.plus(1) ?: Long.MIN_VALUE
            check(next.updatedAt.toEpochMilli() in maxOf(submitted, previous)..maxOf(situationClockWindows.last().second.toEpochMilli(), previous))
            if (status == ShiftStatus.ABSENT) check(next.evidence == null)
        }
    }
    private fun appliedSituation(old: SituationExpected, mutation: ShiftSituationMutation, stage: String): SituationExpected {
        val found = snapshot(stage)
        val timestamp = mutation.replacement?.updatedAt ?: run {
            val table = found.tables.getValue("shifts")
            val id = Stage12RecurringReadbackOracle.write(old.write).first().values.getValue("id")
            val row = table.rows.single { it[table.columns.indexOf("id")] == id }
            val encoded = row[table.columns.indexOf("updatedAtEpochMillis")]
            check(encoded.startsWith("I:"))
            val value = encoded.removePrefix("I:").toLong()
            val range = situationClockWindows.last()
            val min = maxOf(range.first.toEpochMilli(), old.write.shift.updatedAt.toEpochMilli() + 1)
            val max = maxOf(range.second.toEpochMilli(), old.write.shift.updatedAt.toEpochMilli() + 1)
            roomClockTolerances.put(JSONObject().put("case", activeCase.wire).put("field", "SHIFT_UPDATED_AT")
                .put("policy", "REAL_DELEGATE_WINDOW_AND_PREVIOUS_PLUS_ONE_MILLISECOND")
                .put("within_declared_window", value in min..max))
            check(value in min..max)
            Instant.ofEpochMilli(value)
        }
        val write = old.write.copy(shift = old.write.shift.copy(status = mutation.replacement?.status ?: ShiftStatus.PLANNED, updatedAt = timestamp))
        val occurrence = old.occurrence?.let { o -> if (o.state != RecurringOccurrenceState.AUTOMATIC) o else o.copy(
            state = RecurringOccurrenceState.CUSTOMIZED, updatedAt = maxOf(timestamp, o.updatedAt.plusMillis(1))) }
        val removed = Stage12RecurringReadbackOracle.write(old.write) + listOfNotNull(old.previous?.let(Stage12RecurringReadbackOracle::situation)) +
            (old.actual?.let(Stage12RecurringReadbackOracle::actual) ?: emptyList()) +
            if (occurrence != old.occurrence) listOfNotNull(old.occurrence?.let(Stage12RecurringReadbackOracle::occurrence)) else emptyList()
        val inserted = Stage12RecurringReadbackOracle.write(write) + listOfNotNull(mutation.replacement?.let(Stage12RecurringReadbackOracle::situation)) +
            if (occurrence != old.occurrence) listOfNotNull(occurrence?.let(Stage12RecurringReadbackOracle::occurrence)) else emptyList()
        val files = old.snapshot.files.toMutableMap()
        val previousEvidence = old.previous?.evidence
        val nextEvidence = mutation.replacement?.evidence
        if (previousEvidence != null && nextEvidence?.storageKey != previousEvidence.storageKey)
            check(files.remove("files/schedule_photos/${previousEvidence.storageKey}") != null)
        if (nextEvidence != null && nextEvidence.storageKey != previousEvidence?.storageKey) {
            requireSyntheticEvidence(nextEvidence, write.shift.id)
            val key = "files/schedule_photos/${nextEvidence.storageKey}"
            check(key !in files)
            files[key] = Stage12FileSnapshot(syntheticPng().size.toLong(), pngSha())
        }
        val expected = Stage12RecurringReadbackOracle.expected(old.snapshot, removed, inserted, files)
        compareAlreadyCaptured(stage, found, expected)
        return SituationExpected(write, mutation.replacement, null, occurrence, expected)
    }
    private fun prepareSyntheticPickerSource() {
        check(pickerSource == null)
        val root = checkNotNull(pendingOutput).parentFile
        val file = File(root, "c074-synthetic.png")
        check(file.canonicalFile == file.absoluteFile && !Files.isSymbolicLink(file.toPath()) && file.createNewFile())
        file.writeBytes(syntheticPng())
        check(file.length() == syntheticPng().size.toLong())
        pickerSource = file
        pickerSourceUri = FileProvider.getUriForFile(context, "$QA.fileprovider", file)
        check(pickerSourceUri?.scheme == "content" && pickerSourceUri?.authority == "$QA.fileprovider")
    }
    private fun requireSyntheticEvidence(value: CancellationEvidence, id: UUID) {
        check(value.storageKey.matches(Regex("cancel_${id}_[0-9a-f]{8}\\.png")))
        check(value.mimeType == "image/png" && value.byteSize == syntheticPng().size.toLong())
        check(value.pixelWidth == (if(humanSession != null) 640 else 1) && value.pixelHeight == (if(humanSession != null) 360 else 1) && value.sha256 == pngSha())
    }
    private fun syntheticPng(): ByteArray = humanImageBytes ?: java.util.Base64.getDecoder().decode(Stage12CalendarExceptionFixture.PNG)
    private fun pngSha(): String = MessageDigest.getInstance("SHA-256").digest(syntheticPng()).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun confirmSituation() {
        situationSubmissionStarted = clock.instant()
        touchIn(isDialog(), "Confirmar")
    }

    private fun holidayLifecycle() {
        isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
            extendFixture(); phase = "HOLIDAY_CREATE_EDIT_DELETE_AND_QUERY"
            val first = Stage12CalendarExceptionFixture.FIRST
            var expected = snapshot("holidays-initial")
            openHolidays(YearMonth.of(2026, 10))
            check(h.holidays.isEmpty())
            exact("holiday-empty-october-query", expected)
            chooseHolidayDates(setOf(first))
            fillLabel("Nombre opcional", "")
            var count = holidayMutations.size
            var started = clock.instant(); text("Guardar feriado(s)")
            await { !h.isSaving && holidayMutations.size == count + 1 && h.holidayDraft.selectedDates.isEmpty() }
            var mutation = holidayMutations.last()
            check(mutation.holidayIdsToDelete.isEmpty() && mutation.conflictPolicy == HolidayConflictPolicy.KEEP_EXISTING)
            val unnamed = validatedHolidayInputs(mutation, setOf(first), "", started).single()
            expected = Stage12RecurringReadbackOracle.expected(expected, inserted = listOf(Stage12RecurringReadbackOracle.holiday(unnamed)))
            await { h.holidays == listOf(unnamed) }
            exact("holiday-single-without-name", expected)

            val dates = setOf(first.plusDays(1), first.plusDays(2))
            chooseHolidayDates(dates); fillLabel("Nombre opcional", "Feriado sintético")
            count = holidayMutations.size; started = clock.instant(); text("Guardar feriado(s)")
            await { !h.isSaving && holidayMutations.size == count + 1 && h.holidayDraft.selectedDates.isEmpty() }
            mutation = holidayMutations.last()
            val batch = validatedHolidayInputs(mutation, dates, "Feriado sintético", started)
            check(mutation.holidayIdsToDelete.isEmpty() && batch.none { it.id == unnamed.id })
            expected = Stage12RecurringReadbackOracle.expected(expected, inserted = batch.map(Stage12RecurringReadbackOracle::holiday))
            await { h.holidays.toSet() == (batch + unnamed).toSet() }
            exact("holiday-multiple-with-name", expected)

            holidayControl(unnamed, "Editar")
            await { h.holidaySelectionActive && h.holidayDraft.editingId == unnamed.id }
            chooseHolidayDates(setOf(first.plusDays(3)))
            fillLabel("Nombre opcional", "Nombre corregido")
            count = holidayMutations.size; started = clock.instant(); text("Guardar cambios")
            await { !h.isSaving && holidayMutations.size == count + 1 && h.holidayDraft.editingId == null }
            mutation = holidayMutations.last()
            val edited = validatedHolidayInputs(mutation, setOf(first.plusDays(3)), "Nombre corregido", started, unnamed).single()
            check(mutation.holidayIdsToDelete.isEmpty())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.holiday(unnamed)), inserted = listOf(Stage12RecurringReadbackOracle.holiday(edited)))
            await { h.holidays.toSet() == (batch + edited).toSet() }
            exact("holiday-edited-date-name-and-kept-identity", expected)
            val selected = batch.single { it.date == first.plusDays(1) }
            val deletes = holidayDeletes.size
            holidayControl(selected, "Eliminar"); touchIn(isDialog(), "Cancelar")
            check(holidayDeletes.size == deletes)
            exact("holiday-delete-cancelled", expected)
            holidayControl(selected, "Eliminar"); touchIn(isDialog(), "Eliminar")
            await { !h.isSaving && holidayDeletes.size == deletes + 1 && h.holidays.none { it.id == selected.id } }
            check(holidayDeletes.last() == selected.id)
            expected = Stage12RecurringReadbackOracle.expected(expected, removed = listOf(Stage12RecurringReadbackOracle.holiday(selected)))
            exact("holiday-only-selected-UUID-deleted", expected)
            closeHolidays()
            openHolidays(YearMonth.of(2026, 11)); check(h.holidays.isEmpty())
            exact("holiday-november-query-readonly", expected); closeHolidays()
            openHolidays(YearMonth.of(2026, 10))
            val retained = batch.filter { it.id != selected.id } + edited
            check(h.holidays.toSet() == retained.toSet())
            exact("holiday-october-same-entities-query", expected); closeHolidays()
            reopen(); exact("holidays-CRUD-reopened", expected)
            viewMonth(YearMonth.of(2026, 10))
            await { retained.all { holiday -> c.days.single { it.date == holiday.date }.holiday == holiday } }
            val holidayDetail = openOwnedConditionDetail(Stage12OwnedDetailSemantics.Site.HOLIDAY,
                edited.date, "Feriado · Nombre corregido")
            holidayDetail.close()
            exact("holiday-real-calendar-detail-readonly", expected)
            claim("A063", "two-month-query-without-writes-and-same-stored-entities")
            claim("A064", "one-and-multiple-dates-selected-in-the-main-calendar")
            claim("A065", "single-no-name-and-multiple-named-holiday-creation")
            claim("A066", "date-and-name-edition-preserves-UUID-createdAt")
            claim("A068", "cancel-then-confirm-delete-only-selected-holiday")
            claim("C093", "complete-main-grid-editor-persistence-and-day-detail")
        }
        val policies = listOf(Stage12RecurringCase.KEEP_EXISTING to "Conservar existentes",
            Stage12RecurringCase.REPLACE_INTACT to "Reemplazar", Stage12RecurringCase.CANCEL to "Cancelar")
        for ((case, caption) in policies) isolated(case) {
            extendFixture(); phase = "HOLIDAY_CONFLICT_${case.name}"
            val first = Stage12CalendarExceptionFixture.FIRST
            val occupied = Holiday(exceptionFixture.id("occupied-holiday"), first, "Ocupante sintético", fixture.stamp, fixture.stamp)
            val neighbour = Holiday(exceptionFixture.id("neighbour-holiday"), first.plusDays(1), "Vecino sintético", fixture.stamp, fixture.stamp)
            db { own.holidays.insert(occupied); own.holidays.insert(neighbour) }
            var expected = snapshot("holiday-policy-neighbours-seeded")
            openHolidays(YearMonth.of(2026, 10))
            chooseHolidayDates(setOf(first, first.plusDays(2))); fillLabel("Nombre opcional", "  Nombre nuevo  ")
            val count = holidayMutations.size
            text("Guardar feriado(s)")
            await { !h.isSaving && h.holidayDraft.conflictDates == setOf(first) }
            check(holidayMutations.size == count)
            exact("holiday-policy-preview-no-write", expected)
            val started = clock.instant(); touchIn(isDialog(), caption)
            if (case == Stage12RecurringCase.CANCEL) {
                await { h.holidayDraft.conflictDates.isEmpty() }
                check(holidayMutations.size == count && h.holidayDraft.selectedDates == setOf(first, first.plusDays(2)))
                check(h.holidayDraft.name == "  Nombre nuevo  ")
                exact("holiday-conflict-cancel-no-write", expected)
                closeHolidays(discard = true)
            } else {
                await { !h.isSaving && holidayMutations.size == count + 1 && h.holidayDraft.selectedDates.isEmpty() }
                val mutation = holidayMutations.last()
                val incoming = validatedHolidayInputs(mutation, setOf(first, first.plusDays(2)), "  Nombre nuevo  ", started)
                check(mutation.holidayIdsToDelete.isEmpty())
                val fresh = incoming.single { it.date == first.plusDays(2) }
                val conflict = incoming.single { it.date == first }
                check(conflict.id != occupied.id && fresh.id != neighbour.id)
                val replacement = if (case == Stage12RecurringCase.REPLACE_INTACT) occupied.copy(name = "Nombre nuevo", updatedAt = conflict.updatedAt) else occupied
                check(mutation.conflictPolicy == if (case == Stage12RecurringCase.REPLACE_INTACT) HolidayConflictPolicy.REPLACE else HolidayConflictPolicy.KEEP_EXISTING)
                expected = Stage12RecurringReadbackOracle.expected(expected,
                    removed = if (replacement == occupied) emptyList() else listOf(Stage12RecurringReadbackOracle.holiday(occupied)),
                    inserted = listOf(Stage12RecurringReadbackOracle.holiday(fresh)) +
                        if (replacement == occupied) emptyList() else listOf(Stage12RecurringReadbackOracle.holiday(replacement)))
                await { h.holidays.toSet() == setOf(replacement, neighbour, fresh) }
                exact("holiday-high-conflict-${case.wire}-exact-identities", expected)

                // Moving an existing edit has a different identity contract from an alta.
                holidayControl(fresh, "Editar")
                await { h.holidaySelectionActive && h.holidayDraft.editingId == fresh.id }
                chooseHolidayDates(setOf(first)); fillLabel("Nombre opcional", "Edición movida")
                val moveCalls = holidayMutations.size
                text("Guardar cambios"); await { !h.isSaving && h.holidayDraft.conflictDates == setOf(first) }
                exact("holiday-move-conflict-preview-no-write", expected)
                val moveStarted = clock.instant(); touchIn(isDialog(), caption)
                await { !h.isSaving && holidayMutations.size == moveCalls + 1 && h.holidayDraft.editingId == null }
                val movedMutation = holidayMutations.last()
                val edited = validatedHolidayInputs(movedMutation, setOf(first), "Edición movida", moveStarted, fresh).single()
                if (case == Stage12RecurringCase.REPLACE_INTACT) {
                    check(movedMutation.holidayIdsToDelete == setOf(replacement.id))
                    expected = Stage12RecurringReadbackOracle.expected(expected,
                        removed = listOf(fresh, replacement).map(Stage12RecurringReadbackOracle::holiday),
                        inserted = listOf(Stage12RecurringReadbackOracle.holiday(edited)))
                    await { h.holidays.toSet() == setOf(edited, neighbour) }
                } else {
                    check(movedMutation.holidayIdsToDelete.isEmpty())
                    await { h.holidays.toSet() == setOf(fresh, replacement, neighbour) }
                }
                exact("holiday-edit-move-${case.wire}-distinct-identity-oracle", expected)
                closeHolidays()
            }
            claim("A067", if (case == Stage12RecurringCase.CANCEL) "cancel-alta-conflict-without-writing"
                else "separate-policy-with-exact-alta-and-edit-identities", case.wire)
        }
        isolated(Stage12RecurringCase.LIFECYCLE) {
            extendFixture(); phase = "HOLIDAY_DISCARD_CONSCIOUS_DECISION"
            val expected = snapshot("holiday-before-discard")
            val count = holidayMutations.size
            openHolidays(YearMonth.of(2026, 10))
            chooseHolidayDates(setOf(Stage12CalendarExceptionFixture.FIRST))
            fillLabel("Nombre opcional", "Borrador que no debe guardarse")
            val draft = h.holidayDraft
            touchHeaderNavigation("Feriados", "Cerrar"); touchIn(isDialog(), "Seguir editando")
            check(h.holidayDraft == draft && h.surface == ExceptionsSurface.HOLIDAYS)
            exact("holiday-keep-editing-no-write", expected)
            closeHolidays(discard = true)
            check(holidayMutations.size == count)
            exact("holiday-explicit-discard-no-write", expected)
            claim("A069", "keep-editing-and-conscious-discard-preserve-complete-store")
        }
    }

    private fun openHolidays(month: YearMonth) {
        viewMonth(month); drawer("drawer-action-holidays")
        await { h.surface == ExceptionsSurface.HOLIDAYS && !h.isLoading && h.holidayMonth == month }
    }
    private fun closeHolidays(discard: Boolean = false) {
        check(h.surface == ExceptionsSurface.HOLIDAYS && !h.isSaving)
        touchHeaderNavigation("Feriados", "Cerrar")
        if (discard) touchIn(isDialog(), "Descartar")
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
            await { h.surface == ExceptionsSurface.NONE && !h.holidaySelectionActive &&
                c.interactionMode == CalendarInteractionMode.VIEW &&
                compose.onAllNodes(headerNavigationMatcher("Feriados", "Cerrar")).fetchSemanticsNodes().isEmpty() }
        }
    }
    private fun chooseHolidayDates(dates: Set<LocalDate>) {
        check(dates.isNotEmpty() && dates.map(YearMonth::from).distinct().size == 1)
        if (!h.holidaySelectionActive) touch("holiday-open-calendar-selection")
        await { h.holidaySelectionActive && c.selectionPurpose == CalendarSelectionPurpose.HOLIDAY }
        viewMonth(YearMonth.from(dates.first()))
        if (h.holidayDraft.editingId != null) {
            check(dates.size == 1)
            // The existing grid toggles dates; deliberately deselect the old date first.
            (c.editSelectedDates - dates).sorted().forEach { date -> touchCalendarDate(date) }
            await { c.editSelectedDates.all { it in dates } }
            (dates - c.editSelectedDates).sorted().forEach { date -> touchCalendarDate(date) }
        } else {
            val changed = (c.editSelectedDates - dates) + (dates - c.editSelectedDates)
            changed.sorted().forEach { date -> touchCalendarDate(date) }
        }
        await { c.editSelectedDates == dates && h.holidayDraft.selectedDates == dates }
        touch("holiday-confirm-calendar-selection")
        await { h.surface == ExceptionsSurface.HOLIDAYS && !h.holidaySelectionActive && !h.isLoading }
        check(h.holidayDraft.selectedDates == dates)
    }
    private fun holidayControl(holiday: Holiday, caption: String) = touchIn(hasTestTag("holiday-card-${holiday.id}"), caption)
    private fun validatedHolidayInputs(mutation: HolidayBatchMutation, dates: Set<LocalDate>, rawName: String,
        started: Instant, previous: Holiday? = null,
    ): List<Holiday> {
        check(mutation.holidaysToSave.map { it.date }.toSet() == dates && mutation.holidaysToSave.size == dates.size)
        val ended = clock.instant()
        return mutation.holidaysToSave.map { value ->
            check(value.name == rawName && !value.updatedAt.isBefore(started) && !value.updatedAt.isAfter(ended))
            if (previous == null) check(value.createdAt == value.updatedAt)
            else check(value.id == previous.id && value.createdAt == previous.createdAt)
            value.copy(name = rawName.trim().takeIf(String::isNotEmpty),
                createdAt = Instant.ofEpochMilli(value.createdAt.toEpochMilli()), updatedAt = Instant.ofEpochMilli(value.updatedAt.toEpochMilli()))
        }
    }

    private fun vacationLifecycle() {
        isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
            extendFixture(); phase = "VACATION_CREATE_QUERY_EDIT_DELETE"
            val start = LocalDate.of(2026, 10, 30)
            val end = LocalDate.of(2026, 11, 2)
            val neighbour = Vacation(exceptionFixture.id("vacation-neighbour"), LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 21), fixture.stamp, fixture.stamp)
            db { own.vacations.insert(neighbour) }
            var expected = snapshot("vacation-neighbour-seeded")
            openVacations(YearMonth.of(2026, 10))
            check(v.vacations == listOf(neighbour)); exact("vacation-october-query-readonly", expected)
            text("Agregar vacaciones"); await { v.surface == VacationSurface.EDITOR }
            chooseVacationDate("Fecha inicial", start); chooseVacationDate("Fecha final inclusiva", end)
            check(v.draft.inclusiveDayCount == 4L)
            textVisible("Vista previa: 4 días corridos")
            val count = vacationCreates.size
            val pendingDraft = v.draft
            val gate = CompletableDeferred<Unit>()
            vacationWriteGate = gate
            var started = clock.instant(); text("Guardar vacaciones")
            await { v.isSaving && vacationCreates.size == count + 1 }
            compose.onNode(hasText("Fecha inicial: ${vacationDate(start)}") and hasClickAction()).assertIsNotEnabled()
            compose.onNode(hasText("Fecha final inclusiva: ${vacationDate(end)}") and hasClickAction()).assertIsNotEnabled()
            compose.onNode(hasText("Guardar vacaciones") and hasClickAction()).assertIsNotEnabled()
            val pendingNavigationState = v
            check(pendingNavigationState.isSaving)
            touchHeaderNavigation("Agregar vacaciones", "Volver")
            check(v == pendingNavigationState)
            check(v.surface == VacationSurface.EDITOR && v.draft == pendingDraft && !v.showDiscardConfirmation)
            exact("vacation-pending-write-blocks-source-replacement", expected)
            vacationPendingGuardObserved = true
            vacationWriteGate = null
            check(gate.complete(Unit))
            await { !v.isSaving && v.surface == VacationSurface.LIST && vacationCreates.size == count + 1 }
            var chosen = vacationCreates.last()
            requireVacation(chosen, null, start, end, started, clock.instant())
            check(chosen.id != neighbour.id)
            expected = Stage12RecurringReadbackOracle.expected(expected, inserted = listOf(Stage12RecurringReadbackOracle.vacation(chosen)))
            await { v.vacations.toSet() == setOf(neighbour, chosen) }
            exact("vacation-inclusive-four-day-create", expected)
            vacationMonth(YearMonth.of(2026, 11))
            check(v.vacations == listOf(chosen))
            textVisible("${vacationDate(start)} – ${vacationDate(end)}")
            exact("vacation-same-entity-november-query", expected)
            vacationMonth(YearMonth.of(2026, 10)); check(v.vacations.toSet() == setOf(neighbour, chosen))
            exact("vacation-same-entity-october-query", expected)
            closeVacations()
            val vacationDetail = openOwnedConditionDetail(Stage12OwnedDetailSemantics.Site.VACATION,
                LocalDate.of(2026, 10, 31), "Vacaciones · ${isoInput(start)} – ${isoInput(end)}")
            check(c.days.filter { it.vacation?.id == chosen.id }.map { it.date } == listOf(start, start.plusDays(1)))
            exact("vacation-real-calendar-detail-readonly", expected); vacationDetail.close()
            viewMonth(YearMonth.of(2026, 11))
            await { c.days.filter { it.vacation?.id == chosen.id }.map { it.date } == listOf(LocalDate.of(2026, 11, 1), end) }
            exact("vacation-inclusive-november-calendar-projection", expected)
            reopen(); exact("vacation-created-reopened", expected)
            openVacations(YearMonth.of(2026, 10)); editVacation(chosen)
            val previous = chosen
            chooseVacationDate("Fecha final inclusiva", end.plusDays(1))
            val updates = vacationUpdates.size
            started = clock.instant(); text("Guardar vacaciones")
            await { !v.isSaving && v.surface == VacationSurface.LIST && vacationUpdates.size == updates + 1 }
            check(vacationUpdates.last().first == previous)
            chosen = vacationUpdates.last().second
            requireVacation(chosen, previous, start, end.plusDays(1), started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.vacation(previous)), inserted = listOf(Stage12RecurringReadbackOracle.vacation(chosen)))
            exact("vacation-edit-keeps-identity-and-creation", expected)
            editVacation(chosen); chooseVacationDate("Fecha final inclusiva", end.plusDays(2))
            val preservedDraft = v.draft
            touchHeaderNavigation("Editar vacaciones", "Volver"); await { v.showDiscardConfirmation }
            touchIn(isDialog(), "Seguir editando")
            check(v.draft == preservedDraft && !v.showDiscardConfirmation && v.surface == VacationSurface.EDITOR)
            exact("vacation-keep-editing-no-write", expected)
            touchHeaderNavigation("Editar vacaciones", "Volver"); await { v.showDiscardConfirmation }; touchIn(isDialog(), "Descartar")
            await { v.surface == VacationSurface.LIST }
            check(vacationUpdates.size == updates + 1)
            exact("vacation-discard-no-write", expected)
            val deletes = vacationDeletes.size
            requestVacationDelete(chosen); touchIn(isDialog(), "Cancelar")
            check(v.pendingDelete == null && vacationDeletes.size == deletes)
            exact("vacation-delete-cancelled", expected)
            requestVacationDelete(chosen); touchIn(isDialog(), "Eliminar")
            await { !v.isSaving && v.pendingDelete == null && vacationDeletes.size == deletes + 1 && v.vacations.none { it.id == chosen.id } }
            check(vacationDeletes.last() == chosen)
            expected = Stage12RecurringReadbackOracle.expected(expected, removed = listOf(Stage12RecurringReadbackOracle.vacation(chosen)))
            check(v.vacations == listOf(neighbour))
            exact("vacation-only-selected-period-deleted", expected); closeVacations()
            claim("A070", "same-vacation-UUID-intersects-two-months-without-writes")
            claim("A071", "literal-October30-November2-inclusive-four-days-visible-and-pending-write-exclusion")
            claim("A072", "real-date-picker-edition-keeps-UUID-and-creation")
            claim("A073", "cancel-then-confirm-delete-only-selected-period")
            claim("A075", "keep-editing-and-conscious-discard-do-not-write")
            claim("C094", "complete-vacation-management-calendar-projection-and-reopen")
        }
        isolated(Stage12RecurringCase.REPLACE_INTACT) {
            extendFixture(); phase = "VACATION_INCLUSIVE_CONFLICTS_AND_CORRECTION"
            val first = Stage12CalendarExceptionFixture.FIRST
            val occupied = Vacation(exceptionFixture.id("vacation-occupied"), first, first.plusDays(2), fixture.stamp, fixture.stamp)
            val medical = MedicalLeave(exceptionFixture.id("medical-vacation-conflict"), first.plusDays(4), first.plusDays(6),
                "Nota privada sintética vecina", fixture.stamp, fixture.stamp)
            db { own.vacations.insert(occupied); own.medicalLeaves.create(medical) }
            var expected = snapshot("vacation-two-conflicts-seeded")
            openVacations(YearMonth.of(2026, 10)); text("Agregar vacaciones")
            await { v.surface == VacationSurface.EDITOR }
            chooseVacationDate("Fecha inicial", first.plusDays(2)); chooseVacationDate("Fecha final inclusiva", first.plusDays(3))
            val count = vacationCreates.size
            text("Guardar vacaciones")
            await { !v.isSaving && vacationCreates.size == count + 1 && v.errorMessage != null }
            check(v.errorMessage == "Ese período se superpone con otras vacaciones existentes.")
            check(v.draft.startDate == first.plusDays(2) && v.draft.endDateInclusive == first.plusDays(3))
            exact("vacation-shared-boundary-overlap-rejected", expected)
            dismissVacationError("Ese período se superpone con otras vacaciones existentes.", VacationSurface.EDITOR)
            chooseVacationDate("Fecha inicial", first.plusDays(3)); chooseVacationDate("Fecha final inclusiva", first.plusDays(4))
            text("Guardar vacaciones")
            await { !v.isSaving && vacationCreates.size == count + 2 && v.errorMessage != null }
            check(v.errorMessage == "Las vacaciones no pueden superponerse con una carpeta médica.")
            check(v.draft.startDate == first.plusDays(3) && v.draft.endDateInclusive == first.plusDays(4))
            exact("vacation-medical-inclusive-boundary-rejected", expected)
            dismissVacationError("Las vacaciones no pueden superponerse con una carpeta médica.", VacationSurface.EDITOR)
            chooseVacationDate("Fecha final inclusiva", first.plusDays(3))
            textVisible("Vista previa: 1 día corrido")
            val started = clock.instant(); text("Guardar vacaciones")
            await { !v.isSaving && v.surface == VacationSurface.LIST && vacationCreates.size == count + 3 }
            val chosen = vacationCreates.last()
            requireVacation(chosen, null, first.plusDays(3), first.plusDays(3), started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected, inserted = listOf(Stage12RecurringReadbackOracle.vacation(chosen)))
            exact("vacation-conscious-single-day-correction", expected); closeVacations()
            claim("A074", "inclusive-vacation-and-medical-conflicts-retain-draft-and-entire-store")
        }
        isolated(Stage12RecurringCase.KEEP_EXISTING) {
            extendFixture(); phase = "VACATION_STALE_EDIT_AND_DELETE_CONSCIOUS_RECOVERY"
            val first = Stage12CalendarExceptionFixture.FIRST
            val original = Vacation(exceptionFixture.id("vacation-CAS"), first, first.plusDays(2), fixture.stamp, fixture.stamp)
            val neighbour = Vacation(exceptionFixture.id("vacation-CAS-neighbour"), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 2), fixture.stamp, fixture.stamp)
            db { own.vacations.insert(original); own.vacations.insert(neighbour) }
            var expected = snapshot("vacation-CAS-seeded")
            openVacations(YearMonth.of(2026, 10)); editVacation(original)
            chooseVacationDate("Fecha final inclusiva", first.plusDays(3))
            val staleDraft = v.draft
            val winner = original.copy(endDateInclusive = first.plusDays(4), updatedAt = realNext(original.updatedAt))
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.vacation(original)), inserted = listOf(Stage12RecurringReadbackOracle.vacation(winner)))
            db { own.vacations.update(original, winner) }
            await { v.vacations.any { it == winner } }
            exact("vacation-concurrent-edit-winner", expected)
            val updates = vacationUpdates.size
            text("Guardar vacaciones")
            await { !v.isSaving && vacationUpdates.size == updates + 1 && v.errorMessage != null }
            check(vacationUpdates.last().first == original)
            check(v.draft == staleDraft && v.draft.observedVacation == original && v.surface == VacationSurface.EDITOR)
            check(v.errorMessage == "Este período cambió desde que lo abriste. No se guardó nada; volvé a abrirlo para continuar.")
            exact("vacation-stale-edit-keeps-winner-and-draft", expected)
            dismissVacationError("Este período cambió desde que lo abriste. No se guardó nada; volvé a abrirlo para continuar.", VacationSurface.EDITOR)
            touchHeaderNavigation("Editar vacaciones", "Volver"); await { v.showDiscardConfirmation }; touchIn(isDialog(), "Seguir editando")
            check(v.draft == staleDraft)
            exact("vacation-stale-edit-keep-editing", expected)
            touchHeaderNavigation("Editar vacaciones", "Volver"); await { v.showDiscardConfirmation }; touchIn(isDialog(), "Descartar")
            await { v.surface == VacationSurface.LIST }
            check(vacationUpdates.size == updates + 1)
            editVacation(winner)
            check(v.draft.observedVacation == winner)
            chooseVacationDate("Fecha final inclusiva", first.plusDays(5))
            val started = clock.instant(); text("Guardar vacaciones")
            await { !v.isSaving && v.surface == VacationSurface.LIST && vacationUpdates.size == updates + 2 }
            val current = vacationUpdates.last().second
            check(vacationUpdates.last().first == winner)
            requireVacation(current, winner, first, first.plusDays(5), started, clock.instant())
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.vacation(winner)), inserted = listOf(Stage12RecurringReadbackOracle.vacation(current)))
            exact("vacation-explicit-current-version-save", expected)

            requestVacationDelete(current)
            val deleteWinner = current.copy(endDateInclusive = first.plusDays(6), updatedAt = realNext(current.updatedAt))
            expected = Stage12RecurringReadbackOracle.expected(expected,
                removed = listOf(Stage12RecurringReadbackOracle.vacation(current)), inserted = listOf(Stage12RecurringReadbackOracle.vacation(deleteWinner)))
            db { own.vacations.update(current, deleteWinner) }
            await { v.vacations.any { it == deleteWinner } }
            exact("vacation-concurrent-delete-winner", expected)
            val deletes = vacationDeletes.size
            touchIn(isDialog(), "Eliminar")
            await { !v.isSaving && vacationDeletes.size == deletes + 1 && v.errorMessage != null }
            check(vacationDeletes.last() == current && v.pendingDelete == current)
            exact("vacation-stale-delete-keeps-winner-and-confirmation", expected)
            touchIn(isDialog(), "Cancelar"); check(v.pendingDelete == null)
            dismissVacationError("Este período cambió desde que lo abriste. No se guardó nada; volvé a abrirlo para continuar.", VacationSurface.LIST)
            requestVacationDelete(deleteWinner)
            check(v.pendingDelete == deleteWinner)
            touchIn(isDialog(), "Eliminar")
            await { !v.isSaving && v.pendingDelete == null && vacationDeletes.size == deletes + 2 && v.vacations.none { it.id == deleteWinner.id } }
            check(vacationDeletes.last() == deleteWinner)
            expected = Stage12RecurringReadbackOracle.expected(expected, removed = listOf(Stage12RecurringReadbackOracle.vacation(deleteWinner)))
            check(v.vacations == listOf(neighbour))
            exact("vacation-explicit-current-version-delete", expected); closeVacations()
            claim("A076", "real-CAS-winners-retained-edit-and-delete-drafts-and-conscious-current-version-recovery")
        }
    }

    private fun openVacations(month: YearMonth) {
        viewMonth(month); drawer("drawer-action-vacations")
        await { v.surface == VacationSurface.LIST && !v.isLoading && v.visibleMonth == month }
    }
    private fun closeVacations() {
        check(v.surface == VacationSurface.LIST && !v.isSaving && v.pendingDelete == null && !v.showDiscardConfirmation)
        touchHeaderNavigation("Vacaciones", "Cerrar")
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
            await { v.surface == VacationSurface.NONE &&
                compose.onAllNodes(headerNavigationMatcher("Vacaciones", "Cerrar")).fetchSemanticsNodes().isEmpty() }
        }
    }
    private fun vacationMonth(month: YearMonth) {
        var remaining = 36
        while (v.visibleMonth != month) {
            check(remaining-- > 0); val previous = v.visibleMonth
            touchDescription(if (previous < month) "Mes siguiente de vacaciones" else "Mes anterior de vacaciones")
            await { v.visibleMonth != previous && !v.isLoading }
        }
    }
    private fun editVacation(value: Vacation) {
        touchDescription("Editar vacaciones desde ${vacationDate(value.startDate)}")
        await { v.surface == VacationSurface.EDITOR && v.draft.observedVacation == value }
    }
    private fun requestVacationDelete(value: Vacation) {
        touchDescription("Eliminar vacaciones desde ${vacationDate(value.startDate)}")
        await { v.pendingDelete == value }
    }
    private fun vacationDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR")))
    private fun chooseVacationDate(label: String, date: LocalDate) {
        val previous = if (label == "Fecha inicial") v.draft.startDate else v.draft.endDateInclusive
        text("$label: ${previous?.let(::vacationDate) ?: "Elegir"}")
        val resource = context.resources.getIdentifier("m3c_date_picker_switch_to_input_mode", "string", QA)
        check(resource != 0)
        touchDescription(context.getString(resource))
        val locale = context.resources.configuration.locales[0]
        val pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            java.time.format.FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale)
        val order = Regex("[dMy]+").findAll(pattern).map { it.value.first() }.toList()
        check(order.size == 3 && order.toSet() == setOf('d', 'M', 'y'))
        val digits = order.joinToString("") { when (it) {
            'd' -> date.dayOfMonth.toString().padStart(2, '0')
            'M' -> date.monthValue.toString().padStart(2, '0')
            else -> date.year.toString().padStart(4, '0')
        } }
        val inputLabel = context.resources.getIdentifier("m3c_date_input_label", "string", QA)
        check(inputLabel != 0)
        fillLabel(context.getString(inputLabel), digits)
        touchIn(isDialog(), "Aceptar")
        await { if (label == "Fecha inicial") v.draft.startDate == date else v.draft.endDateInclusive == date }
    }
    private fun realNext(previous: Instant): Instant = maxOf(Instant.ofEpochMilli(clock.millis()), previous.plusMillis(1))
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
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-calendar-exceptions-family-v1").put("case", slot)
            .put("calendar_clock_mode", "REAL").put("zone", zone.id).put("civil_date", today.toString())
            .put("host", "COMPONENT_ACTIVITY_REAL_MIGUARDIA_APP_BINDINGS")
            .put("main_bootstrap_proven", false).put("process_death_proven", false).put("global_settings_changed", false)
            .put("received_application_store_accessed", false).put("round_credit", 0).put("facts", facts).put("readbacks", readbacks)
        pendingReceipt = receipt; if (complementKind != ComplementKind.NONE) complementReceipt(receipt)
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
                .put("exception_preparation", JSONArray(exceptionPreparations.map { (ownCase, journal) -> JSONObject(journal.projection()).put("case", ownCase.wire) }))
                .put("controlled_picker_selections", pickerSelections).put("controlled_picker_cancellations", pickerCancellations)
                .put("external_picker_proven", humanSession?.pickerSequenceObserved() ?: false).put("synthetic_source_retained_as_evidence", pickerSource != null)
                .put("vacation_pending_guard_observed", vacationPendingGuardObserved)
                .put("last_wait_observation", lastWaitObservation ?: JSONObject.NULL)
                .put("medical_confirmation_observations", medicalConfirmationProjection())
                .put("medical_note_widget_report_observed", complementPrivacyComplete)
                .put("comparisons", comparisons).put("room_clock_tolerances", roomClockTolerances)
            if (slot in setOf("calendar-holidays", "calendar-vacations"))
                receipt.put("owned_detail_observations", ownedDetailProjection())
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
        if(humanSession != null) {
            val file = Stage12PathPolicy(privateRoot).resolve("files/datastore/weather.preferences_pb")
            check(!file.exists() && file.parentFile!!.mkdir() && file.createNewFile() && file.length()==0L)
            checkNotNull(humanSession).projection.put("weather_storage_prepared", true)
        }
        val before = journal.call(SetupStep.SETUP_READBACK) { snapshot("validated-setup") }
        val old = own; journal.call(SetupStep.SETUP_STORE_CLOSE) { old.close() }
        journal.call(SetupStep.REOPEN_PATHS) { paths.requireStored(access) }
        journal.call(SetupStep.SETUP_STORE_REOPEN) { store = createStore(paths); check(own !== old) }
        journal.call(SetupStep.REOPEN_VALIDATION) { db { fixture.validate(own, photoStore) } }
        journal.call(SetupStep.REOPEN_READBACK) { exact("setup-new-store", before) }
        opened += ownCase; setupStoreReopens++; uuids = Ids("$run/${ownCase.wire}/recurring")
        writer = Writer(own.recurringShiftWriter); editorWriter = EditorWriter(own.v2Shifts); launch(journal)
        exact("setup-owner-mounted", before)
        body()
        val end = snapshot("case-final")
        if (humanSession == null) { reopen(); exact("case-final-reopened", end) }
    }

    private fun createStore(paths: Stage12RecurringPaths): LocalDataStore =
        LocalDataStore.create(context, paths.name).also { storeConstructorReturns++ }

    private fun launch(journal: Stage12RecurringPreparation? = null) {
        check(scenario == null)
        fun start() { scenario = ActivityScenario.launch(Intent().setComponent(ComponentName(QA, if(humanSession != null) "com.blackatsystems.miguardia.stage12.Stage12HumanSessionActivity" else ComponentActivity::class.java.name))) }
        fun mounted() { mount(); await { c.loadState == CalendarLoadState.CONTENT && w.rootState is WorkSetupState.V2Ready } }
        if (journal == null) { start(); mounted() }
        else { journal.call(SetupStep.ACTIVITY_LAUNCH) { start() }; journal.call(SetupStep.ACTIVITY_MOUNT) { mounted() } }
    }
    private fun mount() {
        checkNotNull(scenario).onActivity { activity ->
            owner = activity; if (complementKind != ComplementKind.NONE) complementMountEntered()
            humanSession?.attach(activity,binding)
            val humanContext = humanSession?.let { session -> Stage12B1HumanPrivateContext(activity,context,binding,
                { error -> trace.retain(error); throw error },null,session,activity) }
            if(humanContext != null) {
                check(humanWeather == null)
                humanWeather = Stage12HumanWeatherOwner(humanContext,activity,own,clock,run+"-human-weather")
            }
            ownerObjectsObserved++
            val gate = LocalDataMutationGate()
            calendar = ViewModelProvider(activity, CalendarViewModel.Factory(own.shifts, own.explicitDayStatuses,
                MedicalRecorder(own.medicalLeaves), HolidayRecorder(own.holidays), VacationRecorder(own.vacations), clock,
                SituationRecorder(own.shiftSituations), photoStore, gate, own.calendarBatches))
                .get("$run-${activeCase.wire}-calendar", CalendarViewModel::class.java)
            work = ViewModelProvider(activity, WorkSetupViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives, clock,
                clearWeatherCacheForObjective = { id -> humanWeather?.runtime?.clearCacheForObjective(id) }))
                .get("$run-${activeCase.wire}-work", WorkSetupViewModel::class.java)
            edit = ViewModelProvider(activity, V2ShiftEditViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives,
                own.shifts, own.medicalLeaves, editorWriter, own.shiftActuals, own.recurringPlans, clock))
                .get("$run-${activeCase.wire}-edit", V2ShiftEditViewModel::class.java)
            recurring = ViewModelProvider(activity, V2RecurringPlanViewModel.Factory(own.workConfiguration, own.workCatalog, own.objectives,
                own.shifts, own.medicalLeaves, own.recurringPlans, writer, clock, zone, uuids))
                .get("$run-${activeCase.wire}-recurring", V2RecurringPlanViewModel::class.java)
            exceptions = ViewModelProvider(activity, ExceptionsViewModel.Factory(HolidayRecorder(own.holidays), own.shiftNotes,
                own.shifts, photoStore, clock, { uuids.newUuid() }, gate))
                .get("$run-${activeCase.wire}-exceptions", ExceptionsViewModel::class.java)
            vacation = ViewModelProvider(activity, VacationViewModel.Factory(VacationRecorder(own.vacations), clock,
                VacationUuidProvider { uuids.newUuid() }))
                .get("$run-${activeCase.wire}-vacation", VacationViewModel::class.java)
            val uiContext: Context = humanContext ?: object : ContextWrapper(activity) { override fun getFilesDir() = dataRoot }
            activity.setContent(parent = if (complementKind != ComplementKind.NONE) complementParent(activity) else null) {
                val cs by calendar.uiState.collectAsState(); val ws by work.uiState.collectAsState()
                val es by edit.uiState.collectAsState(); val rs by recurring.uiState.collectAsState()
                val hs by exceptions.uiState.collectAsState(); val vs by vacation.uiState.collectAsState()
                val weatherState = humanWeather?.model?.uiState?.collectAsState()?.value ?: com.blackatsystems.miguardia.ui.weather.WeatherUiState()
                val weatherActions = humanWeather?.let { resource ->
                    com.blackatsystems.miguardia.ui.weather.WeatherActions.from(resource.model).copy(enableAfterExplanation = {
                        try { checkNotNull(humanSession).weatherConsent(); resource.model.enableAfterExplanation() }
                        catch(error: Throwable) { trace.retain(error); throw error }
                    })
                } ?: com.blackatsystems.miguardia.ui.weather.WeatherActions()
                val workActions = if(humanSession == null) WorkSetupActions.from(work) else WorkSetupActions.from(work).copy(
                    saveObjectiveLocation = { id,lat,lon -> checkNotNull(humanSession).location(id,lat,lon) { work.saveObjectiveLocation(id,lat,lon) } })
                CompositionLocalProvider(LocalContext provides uiContext, LocalActivityResultRegistryOwner provides (if(humanSession != null) activity else pickerOwner)) {
                    MiGuardiaTheme {
                        val privacy = privacySurface
                        if (privacy != null) privacy.Content() else MiGuardiaApp(calendarState = cs,
                            onPreviousMonth = calendar::showPreviousMonth, onNextMonth = calendar::showNextMonth,
                            onToday = calendar::showCurrentMonth, onSelectDate = calendar::selectDate, onDismissDate = calendar::clearSelectedDate,
                            onRetry = calendar::retry, workSetupState = ws, workSetupActions = workActions,
                            onEnterCalendarEditMode = calendar::enterEditMode,
                            onEnterManualCalendarSelection = calendar::enterManualLoadSelection,
                            onEnterHolidayCalendarSelection = calendar::enterHolidaySelection,
                            onSetCalendarSelectionPurpose = calendar::setSelectionPurpose,
                            onEditSelectionChange = calendar::setEditSelectedDates,
                            onConfirmEditSelection = calendar::confirmEditSelection,
                            onResumeEditSelection = calendar::resumeEditSelection,
                            onFinishCalendarEditMode = calendar::finishEditMode,
                            stage2Actions = Stage2DayActions(clearMessage = calendar::clearStage2Message,
                                toggleUndefined = calendar::toggleUndefined, saveMedicalLeave = calendar::saveMedicalLeave,
                                deleteMedicalLeave = calendar::deleteMedicalLeave, changeSituation = complementSituationAction()),
                            v2ShiftEditState = es, v2ShiftEditActions = V2ShiftEditActions.from(edit),
                            v2RecurringState = rs, v2RecurringActions = V2RecurringActions.from(recurring),
                            exceptionsState = hs, exceptionsActions = ExceptionsActions.from(exceptions),
                            vacationState = vs, vacationActions = VacationActions.from(vacation),
                            weatherState = weatherState, weatherActions = weatherActions)
                    }
                }
            }; if (complementKind != ComplementKind.NONE) complementContentInstalled(activity)
        }
    }

    private fun closeOwner() { if (complementKind != ComplementKind.NONE) { closeComplementOwner(); return }
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
    private fun await(condition: () -> Boolean) {
        val ordinal = ++waitOrdinal
        compose.waitUntil(30_000) {
            requireReady()
            // This is a pre-predicate, non-atomic sample of already-owned UI state.
            // No original messages, dates, UUIDs, text, paths or database queries.
            val observation = JSONObject().put("schema", "stage12-calendar-exception-wait-v1")
                .put("ordinal", ordinal).put("phase", phase).put("scope", "BEFORE_CONDITION_NON_ATOMIC")
            if (::calendar.isInitialized) observation.put("calendar", JSONObject()
                .put("load", c.loadState.name).put("busy", c.stage2Busy)
                .put("message_present", c.stage2Message != null).put("message_code", stage2MessageCode(c.stage2Message))
                .put("detail_open", c.detailDate != null))
            if (::exceptions.isInitialized) observation.put("holiday", JSONObject()
                .put("surface", h.surface.name).put("loading", h.isLoading).put("saving", h.isSaving)
                .put("selection_active", h.holidaySelectionActive).put("conflict_present", h.holidayDraft.conflictDates.isNotEmpty())
                .put("error_present", h.errorMessage != null))
            if (::vacation.isInitialized) observation.put("vacation", JSONObject()
                .put("surface", v.surface.name).put("loading", v.isLoading).put("saving", v.isSaving)
                .put("error_present", v.errorMessage != null).put("discard", v.showDiscardConfirmation)
                .put("delete_present", v.pendingDelete != null))
            lastWaitObservation = observation
            val result = condition()
            observation.put("condition_returned", true).put("satisfied", result)
            result
        }
    }
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


    @Test fun medicalNotePrivacyUsesRealWidgetAndReportBoundaries() {
        complementKind = ComplementKind.C071_PRIVACY
        family("calendar-medical-privacy") {
            isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
                extendFixture(); phase = "C071_ROOM_SEED"
                val before = snapshot("privacy-before-medical")
                val first = Stage12CalendarExceptionFixture.FIRST.plusDays(1)
                val canary = "C071_PRIVATE_NOTE_" + hash(run).take(20)
                val leave = MedicalLeave(exceptionFixture.id("privacy-medical"), first, first.plusDays(2),
                    canary, fixture.stamp, fixture.stamp)
                db { own.medicalLeaves.create(leave) }
                var expected = Stage12RecurringReadbackOracle.expected(before,
                    inserted = listOf(Stage12RecurringReadbackOracle.medical(leave)))
                val persisted = db { own.medicalLeaves.observeIntersecting(first, first.plusDays(2)).first().single { it.id == leave.id } }
                checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { check(persisted == leave) }
                exact("privacy-medical-seeded", expected)
                val probe = Stage12MedicalPrivacyProbe(dataRoot, clock, zone); privacyResources = probe
                checkNotNull(scenario).onActivity { activity ->
                    probe.initialize(activity, own, "$run-c071-reports")
                    complementModels[probe.reports] = checkNotNull(probe.reports.viewModelScope.coroutineContext[kotlinx.coroutines.Job])
                }
                phase = "C071_WIDGET_SOURCE"
                val next = exceptionFixture.writes.single { it.shift.localStartDate == LocalDate.of(2026, 10, 30) }
                val projected = db { probe.projectWidgetFromRoom(own, persisted, next.shift.id, exceptionFixture.id("privacy-widget")) {
                    block -> instrumentation.runOnMainSync(block)
                } }
                complementEvents.put(projected)
                checkNotNull(scenario).onActivity { privacySurface = probe }
                compose.waitForIdle()
                phase = "C071_WIDGET_APPLIED"
                checkNotNull(scenario).onActivity {
                    complementEvents.put(probe.requireWidgetPrivate(persisted, next.shift.objectiveNameSnapshot))
                }
                exact("privacy-widget-readonly", expected)
                phase = "C071_REPORT_DEFAULT"
                checkNotNull(scenario).onActivity { probe.openReports(YearMonth.of(2026, 10)) }
                await { probe.reports.uiState.value.stage == com.blackatsystems.miguardia.reports.ReportsStage.CONTENT }
                check(probe.reports.uiState.value.privacy == com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection())
                touch("reports-format-xlsx")
                await { !probe.reports.uiState.value.isBusy && probe.reports.uiState.value.format == com.blackatsystems.miguardia.core.domain.report.ReportFormat.XLSX }
                touch("reports-generate")
                await { probe.reports.uiState.value.stage == com.blackatsystems.miguardia.reports.ReportsStage.READY }
                complementEvents.put(probe.requireReportArtifact(false, canary))
                expected = withReportArtifacts(expected, probe)
                exact("privacy-default-report", expected)
                val originalArtifact = checkNotNull(probe.reports.uiState.value.artifact)
                phase = "C071_REPORT_CANCEL"
                touch("reports-include-medical-notes")
                await { probe.reports.uiState.value.medicalConfirmationPending }
                check(!probe.reports.uiState.value.privacy.includeMedicalNotes && probe.reports.uiState.value.artifact == originalArtifact)
                textVisible("Incluir una nota médica privada")
                text("Cancelar")
                await { !probe.reports.uiState.value.medicalConfirmationPending }
                check(!probe.reports.uiState.value.privacy.includeMedicalNotes && probe.reports.uiState.value.artifact == originalArtifact)
                exact("privacy-consent-cancelled", expected)
                complementEvents.put(JSONObject().put("stage", "REPORT_FIRST_CONSENT_CANCELLED")
                    .put("pending_observed", true).put("include_before", false).put("include_after", false).put("artifact_unchanged", true))
                phase = "C071_REPORT_CONSENT"
                touch("reports-include-medical-notes")
                await { probe.reports.uiState.value.medicalConfirmationPending }
                check(!probe.reports.uiState.value.privacy.includeMedicalNotes)
                touch("reports-confirm-medical-note")
                await { !probe.reports.uiState.value.isBusy && !probe.reports.uiState.value.medicalConfirmationPending &&
                    probe.reports.uiState.value.privacy.includeMedicalNotes }
                touch("reports-generate")
                await { probe.reports.uiState.value.stage == com.blackatsystems.miguardia.reports.ReportsStage.READY }
                complementEvents.put(probe.requireReportArtifact(true, canary))
                expected = withReportArtifacts(expected, probe)
                exact("privacy-consented-report", expected)
                phase = "C071_REPORT_NEW_SESSION"
                touch("reports-back-summary")
                await { !probe.reports.uiState.value.isOpen }
                checkNotNull(scenario).onActivity { probe.openReports(YearMonth.of(2026, 10)) }
                await { probe.reports.uiState.value.stage == com.blackatsystems.miguardia.reports.ReportsStage.CONTENT }
                val reset = probe.reports.uiState.value
                check(reset.privacy == com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection() && reset.generated == null)
                check(checkNotNull(reset.preview).notes.none { it.kind == com.blackatsystems.miguardia.core.domain.report.ReportNoteKind.MEDICAL_LEAVE })
                touch("reports-back-summary"); await { !probe.reports.uiState.value.isOpen }
                exact("privacy-new-session-default", expected)
                complementEvents.put(JSONObject().put("stage", "REPORT_NEW_SESSION_RESET").put("include_medical_notes", false)
                    .put("generated_absent", true).put("medical_rows_absent", true))
                phase = "C071_REOPEN"
                reopen(); exact("privacy-reopened-complete", expected)
                val found = db { own.medicalLeaves.observeIntersecting(first, first.plusDays(2)).first().single { it.id == leave.id } }
                check(found == persisted)
                complementEvents.put(JSONObject().put("stage", "REOPENED_EXACT").put("note_unchanged", true).put("artifacts", 2))
                listOf("UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED").forEach { level ->
                    fact("exceptions.C071.$level.widget-report-private-note", level, JSONObject()
                        .put("widget_remote_views_applied", true).put("report_format", "XLSX").put("explicit_second_consent", true)
                        .put("external_export_proven", false).put("installed_widget_proven", false))
                }
                complementPrivacyComplete = true
            }
        }
    }
    private fun withReportArtifacts(before: Stage12StoreSnapshot, probe: Stage12MedicalPrivacyProbe): Stage12StoreSnapshot {
        val files = before.files.toMutableMap()
        val directory = Stage12FileSnapshot(0, hash(""), Stage12PrivateNodeKind.DIRECTORY)
        listOf("files/reports", "files/reports/artifacts").forEach { name ->
            check(files[name] == null || files[name] == directory); files[name] = directory
        }
        probe.artifacts.forEach { (key, value) -> check(files[key] == null || files[key] == value); files[key] = value }
        return before.copy(files = files.toSortedMap())
    }

    private enum class ComplementKind { NONE, C074_PENDING, C071_PRIVACY, HUMAN_SESSION }
    private var complementKind = ComplementKind.NONE
    private val complementEvents = JSONArray()
    private val complementState = JSONObject().put("schema", "stage12-calendar-complement-v1")
        .put("observations", complementEvents).put("owner_closures", 0).put("composition_closures", 0).put("model_jobs_joined", 0)
    private var complementComposition: Stage12TemporalComposition? = null
    private var complementMountUncertain = false
    private val complementModels = java.util.IdentityHashMap<androidx.lifecycle.ViewModel, kotlinx.coroutines.Job>()
    private val complementSubmittedUris = mutableListOf<Uri?>()
    private var privacyResources: Stage12MedicalPrivacyProbe? = null
    private var privacySurface by mutableStateOf<Stage12MedicalPrivacyProbe?>(null)
    private var complementPrivacyComplete = false

    @Test fun pendingCancellationEvidenceSurvivesPickerCancellation() {
        complementKind = ComplementKind.C074_PENDING
        family("calendar-cancellation-pending") {
            prepareSyntheticPickerSource()
            isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) { pendingEvidenceCase(false) }
            isolated(Stage12RecurringCase.KEEP_EXISTING) { pendingEvidenceCase(true) }
        }
    }
    private fun complementReceipt(receipt: JSONObject) {
        complementState.put("kind", complementKind.name)
        receipt.put("calendar_complement", complementState).put("runtime_api", android.os.Build.VERSION.SDK_INT)
            .put("received_state", "NOT_CERTIFIED").put("post_ui_certified", false)
    }
    private fun complementMountEntered() { check(!complementMountUncertain && complementComposition == null); complementMountUncertain = true }
    private fun complementParent(activity: ComponentActivity): androidx.compose.runtime.CompositionContext {
        listOf(calendar, work, edit, recurring, exceptions, vacation).forEach { vm ->
            complementModels[vm] = checkNotNull(vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job])
        }
        humanWeather?.model?.let { vm -> complementModels[vm] = checkNotNull(vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]) }
        val composition = Stage12TemporalComposition(context) { trace.retain(it) }
        complementComposition = composition
        return composition.initialize(activity)
    }
    private fun complementContentInstalled(activity: ComponentActivity) {
        checkNotNull(complementComposition).bindContent(activity); complementMountUncertain = false
    }
    @android.annotation.SuppressLint("RestrictedApi")
    private fun closeComplementOwner() {
        if (owner == null && scenario == null) {
            check(complementComposition == null && complementModels.isEmpty() && !complementMountUncertain); return
        }
        humanSession?.beforeClose()
        humanWeather?.cancelRequests()
        checkNotNull(scenario).close()
        val activity = checkNotNull(owner)
        check(activity.isDestroyed && activity.lifecycle.currentState == Lifecycle.State.DESTROYED)
        val storeEmpty = activity.viewModelStore.keys().isEmpty()
        val composition = checkNotNull(complementComposition)
        composition.observeDestroyed(activity)
        runBlocking { composition.closeAfterDestroyed { block -> instrumentation.runOnMainSync(block) } }
        privacyResources?.let { probe ->
            runBlocking { probe.closeAfterOwnerDestroyed() }; check(probe.closed)
            complementState.put("privacy_closures", complementState.optInt("privacy_closures") + 1)
                .put("preference_jobs_joined", complementState.optInt("preference_jobs_joined") + 1)
        }
        val jobs = complementModels.values.toSet()
        runBlocking { kotlinx.coroutines.withTimeout(30_000) { jobs.forEach { it.join() } } }
        check(storeEmpty && composition.closureProven && !complementMountUncertain && jobs.all { it.isCompleted })
        humanWeather?.let { resource ->
            runBlocking { resource.closeAfterModels() }; check(resource.closed)
            complementState.put("preference_jobs_joined", complementState.optInt("preference_jobs_joined") + 1)
        }
        humanSession?.detached(activity, true)
        humanWeather = null
        complementState.put("owner_closures", complementState.getInt("owner_closures") + 1)
            .put("composition_closures", complementState.getInt("composition_closures") + 1)
            .put("model_jobs_joined", complementState.getInt("model_jobs_joined") + jobs.size)
        complementModels.clear(); complementComposition = null; privacySurface = null; privacyResources = null; owner = null; scenario = null
    }
    private fun complementSituationAction(): (ShiftSituationExpectation, ShiftStatus, String?, String?, CancellationActor?, Instant?, Boolean, Uri?) -> Unit =
        if (complementKind !in setOf(ComplementKind.C074_PENDING, ComplementKind.HUMAN_SESSION)) calendar::changeShiftSituation else {
            { expected, target, reason, note, actor, notice, removeActual, uri ->
                if(humanSession != null) checkNotNull(humanImage).requireSelected(checkNotNull(uri))
                complementSubmittedUris += uri
                calendar.changeShiftSituation(expected, target, reason, note, actor, notice, removeActual, uri)
            }
        }
    private fun requirePendingForm(reason: String, note: String, notice: String) {
        checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) {
            val dialog = hasTestTag("shift-situation-dialog")
            compose.onAllNodes(dialog).assertCountEquals(1)
            listOf("Motivo (obligatorio)" to reason, "Nota privada (opcional)" to note, "Fecha y hora del aviso" to notice).forEach { (label, value) ->
                compose.onNode(hasText(label) and hasSetTextAction() and hasAnyAncestor(dialog)).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(value)))
            }
            compose.onNode(hasText("Quién canceló: Empleador / organización") and hasAnyAncestor(dialog)).assertIsDisplayed()
            compose.onNode(hasText("Imagen elegida. Se copiará al almacenamiento privado al confirmar.") and hasAnyAncestor(dialog)).assertIsDisplayed()
            compose.onNode(hasText("Reemplazar evidencia elegida") and hasAnyAncestor(dialog)).assertIsDisplayed()
        }
    }
    private fun pendingEvidenceCase(withStored: Boolean) {
        extendFixture()
        val variant = if (withStored) "PENDING_OVER_STORED" else "PENDING_ONLY"
        phase = "C074_" + variant + "_FORM"
        var expected = initialSituation()
        val id = expected.write.shift.id
        val originalUri = checkNotNull(pickerSourceUri)
        val noticeText = "23/10/2026 07:30"
        val notice = java.time.LocalDateTime.of(2026, 10, 23, 7, 30).atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toInstant()
        openDay(Stage12CalendarExceptionFixture.FIRST)
        if (withStored) {
            val calls = situationMutations.size
            touch("shift-cancel-$id")
            fillLabel("Motivo (obligatorio)", "Cancelación B persistida")
            fillLabel("Nota privada (opcional)", "Nota B sintética")
            fillLabel("Fecha y hora del aviso", noticeText); touchIn(isDialog(), "Cambiar")
            pickerInput = originalUri; val count = pickerSelections
            touchIn(isDialog(), "Elegir evidencia opcional"); await { pickerSelections == count + 1 }
            confirmSituation(); awaitSituation(calls + 1, id, ShiftStatus.CANCELLED)
            val mutation = situationMutations.last()
            requireSituationCommand(expected, mutation, ShiftStatus.CANCELLED, "Cancelación B persistida", "Nota B sintética", CancellationActor.EMPLOYER, notice)
            expected = appliedSituation(expected, mutation, "pending-B-baseline-committed")
            check(photoStore.file(checkNotNull(expected.previous?.evidence).storageKey).readBytes().contentEquals(syntheticPng()))
            touch("shift-edit-situation-$id")
        } else touch("shift-cancel-$id")
        val reason = "Cancelación A pendiente"
        val note = "Nota A sintética conservada"
        fillLabel("Motivo (obligatorio)", reason); fillLabel("Nota privada (opcional)", note)
        fillLabel("Fecha y hora del aviso", noticeText)
        if (!withStored) touchIn(isDialog(), "Cambiar")
        val beforeOwner = checkNotNull(owner)
        val selectedBefore = pickerSelections; val cancelledBefore = pickerCancellations
        val submissionsBefore = complementSubmittedUris.size; val writesBefore = situationMutations.size
        val oldEvidence = expected.previous?.evidence
        phase = "C074_" + variant + "_SELECT"
        pickerInput = originalUri
        touchIn(isDialog(), if (withStored) "Reemplazar evidencia guardada" else "Elegir evidencia opcional")
        await { pickerSelections == selectedBefore + 1 }
        requirePendingForm(reason, note, noticeText)
        exact("pending-A-before-picker-cancel", expected.snapshot)
        phase = "C074_" + variant + "_PICKER_CANCEL"
        pickerInput = null
        touchIn(isDialog(), "Reemplazar evidencia elegida")
        await { pickerCancellations == cancelledBefore + 1 }
        requirePendingForm(reason, note, noticeText)
        check(owner === beforeOwner && beforeOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        check(c.detailDate == Stage12CalendarExceptionFixture.FIRST && situationMutations.size == writesBefore && complementSubmittedUris.size == submissionsBefore)
        exact("pending-A-picker-cancel-no-import", expected.snapshot)
        complementEvents.put(JSONObject().put("variant", variant).put("stage", "PENDING_CANCEL_PRESERVED")
            .put("fields_exact", true).put("same_owner", true).put("same_date", true).put("writes", 0)
            .put("selection_count", 1).put("cancellation_count", 1).put("existing_evidence", withStored))
        phase = "C074_" + variant + "_CONFIRM"
        confirmSituation(); awaitSituation(writesBefore + 1, id, ShiftStatus.CANCELLED)
        check(complementSubmittedUris.size == submissionsBefore + 1 && complementSubmittedUris.last() == originalUri)
        val mutation = situationMutations.last()
        requireSituationCommand(expected, mutation, ShiftStatus.CANCELLED, reason, note, CancellationActor.EMPLOYER, notice)
        val replacement = checkNotNull(mutation.replacement?.evidence)
        requireSyntheticEvidence(replacement, id)
        check(replacement.storageKey != oldEvidence?.storageKey)
        expected = appliedSituation(expected, mutation, "pending-A-confirmed-original-URI")
        check(photoStore.file(replacement.storageKey).readBytes().contentEquals(syntheticPng()))
        if (oldEvidence != null) check(!photoStore.file(oldEvidence.storageKey).exists())
        check(checkNotNull(pickerSource).readBytes().contentEquals(syntheticPng()))
        complementEvents.put(JSONObject().put("variant", variant).put("stage", "ORIGINAL_URI_COMMITTED")
            .put("original_uri_sha256", hash(originalUri.toString())).put("source_sha256", pngSha())
            .put("bytes", syntheticPng().size).put("new_evidence_key", true).put("old_evidence_retired", oldEvidence != null))
        phase = "C074_" + variant + "_REOPEN"
        closeDay(); reopen(); exact("pending-A-confirmed-reopened", expected.snapshot)
        openDay(Stage12CalendarExceptionFixture.FIRST); touch("shift-edit-situation-$id")
        compose.onNode(hasText("Motivo (obligatorio)") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(reason)))
        compose.onNode(hasText("Nota privada (opcional)") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(note)))
        textVisible("Hay una evidencia privada guardada. Sólo se reemplaza si elegís otra imagen.")
        touchIn(isDialog(), "Cancelar"); closeDay()
        exact("pending-A-reopened-consultation-readonly", expected.snapshot)
        complementEvents.put(JSONObject().put("variant", variant).put("stage", "REOPENED_READONLY").put("exact", true))
        for (level in listOf("UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED", "FILES_ISOLATED"))
            fact("exceptions.C074." + level + ".pending-picker-cancel-" + variant.lowercase(), level,
                JSONObject().put("variant", variant).put("external_picker_proven", false).put("controlled_result", true))
    }

    private var humanSession: Stage12B1HumanSession? = null
    private var humanImage: Stage12HumanImageLease? = null
    private var humanImageBytes: ByteArray? = null
    private var humanWeather: Stage12HumanWeatherOwner? = null
    private lateinit var humanTarget: V2ShiftWrite
    private lateinit var humanAddress: Objective
    private lateinit var humanCity: Objective
    private lateinit var humanAddressPlace: WorkPlace
    private lateinit var humanCityPlace: WorkPlace
    private var humanExpected: Stage12StoreSnapshot? = null
    private val humanLocationOutcomes = mutableMapOf<UUID, Boolean>()

    @Test fun humanMapsLocationAndExternalEvidenceStayScoped() {
        complementKind = ComplementKind.HUMAN_SESSION
        family(Stage12B1HumanSession.SLOT) {
            val session = Stage12B1HumanSession(run, instrumentation as Stage12NormalQaRunner) { trace.retain(it) }
            humanSession = session
            checkNotNull(pendingReceipt).put("human_session", session.projection).put("human_acceptance_proven", false)
            humanImageBytes = instrumentation.context.assets.open("stage12-human-c074.png").use { it.readBytes() }
            check(checkNotNull(humanImageBytes).size == Stage12HumanImageLease.SIZE && pngSha() == Stage12HumanImageLease.SHA)
            humanImage = Stage12HumanImageLease(context, run, checkNotNull(humanImageBytes))
            checkNotNull(pendingReceipt).put("human_image_lease", checkNotNull(humanImage).projection)
            isolated(Stage12RecurringCase.SINGLE_OCCURRENCE) {
                extendFixture(); prepareHumanFixture(); humanMapStep(); openHumanWorkSetup()
                humanLocationStep(false); humanLocationStep(true)
                check(w.surface == WorkSetupSurface.OVERVIEW)
                checked(Stage12SelectionDiagnostics.Checkpoint.NATIVE_ASSERTION) { requireReady(); device.pressBack() }
                await { w.surface == WorkSetupSurface.NONE }
                exact("human-work-closed", checkNotNull(humanExpected))
                humanPickerSteps()
                reopen(); exact("human-persisted-before-weather", checkNotNull(humanExpected))
                humanWeatherStep()
                check(owner == null && humanWeather == null)
                exact("human-final-owned-closed", checkNotNull(humanExpected))
            }
        }
        val localCleanup = checkNotNull(cleanup)
        cleanup = { checkNotNull(humanImage).close(); localCleanup() }
    }
    private fun prepareHumanFixture() {
        phase = "HUMAN_FIXTURE"
        val before = snapshot("human-before-seed")
        val original = db { checkNotNull(own.objectives.getById(fixture.objective.id)) }
        val place = db { checkNotNull(own.workCatalog.getWorkPlace(fixture.place.id)) }
        val stamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        humanAddress = original.copy(fullName = Stage12B1HumanSession.ADDRESS_OBJECTIVE,
            address = Stage12B1HumanSession.ADDRESS, updatedAt = stamp)
        humanAddressPlace = place.copy(updatedAt = stamp)
        db { own.workCatalog.updateWorkPlace(WorkPlaceUpdate(place, humanAddressPlace, original, humanAddress)) }
        humanCity = original.copy(id = fixture.id("human-city-objective"), fullName = Stage12B1HumanSession.CITY_OBJECTIVE,
            abbreviation = "QCI", address = null, note = null, weatherLatitude = null, weatherLongitude = null,
            createdAt = stamp, updatedAt = stamp)
        humanCityPlace = place.copy(id = fixture.id("human-city-place"), objectiveId = humanCity.id, createdAt = stamp, updatedAt = stamp)
        val firstRule = db { own.workCatalog.getRuleRevisions(place.id).single() }
        val cityRule = firstRule.copy(id = fixture.id("human-city-rule"), workPlaceId = humanCityPlace.id,
            objectiveId = humanCity.id, createdAt = stamp)
        val history = db { checkNotNull(own.workConfiguration.get()) }
        db { own.workCatalog.createWorkPlace(NewWorkPlace(humanCity, humanCityPlace, cityRule,
            Stage12RecurringFirstSetContext.resolve(history, cityRule))) }
        val targetDate = today.plusDays(1)
        humanTarget = fixture.expectedWrite(fixture.id("human-maps-weather-target"), targetDate, exceptionFixture.template,
            "Puesto QA humano", stamp).let {
            it.copy(shift = it.shift.copy(objectiveNameSnapshot = humanAddress.fullName,
                objectiveAbbreviationSnapshot = humanAddress.abbreviation, objectiveAddressSnapshot = humanAddress.address))
        }
        db { own.v2Shifts.insert(humanTarget) }
        val expected = Stage12RecurringReadbackOracle.expected(before,
            removed = listOf(Stage12RecurringReadbackOracle.objective(original), Stage12RecurringReadbackOracle.place(place)),
            inserted = listOf(Stage12RecurringReadbackOracle.objective(humanAddress), Stage12RecurringReadbackOracle.place(humanAddressPlace),
                Stage12RecurringReadbackOracle.objective(humanCity), Stage12RecurringReadbackOracle.place(humanCityPlace),
                Stage12RecurringReadbackOracle.disabledWorkplaceRule(cityRule)) + Stage12RecurringReadbackOracle.write(humanTarget))
        exact("human-fixture-intended-only", expected); humanExpected = expected
        checkNotNull(humanSession).configureObjectives(humanAddress.id, humanCity.id)
        checkNotNull(pendingReceipt).put("human_fixture", JSONObject().put("scope", activeCase.wire)
            .put("map_weather_target", humanTarget.shift.id.toString()).put("address_objective", humanAddress.id.toString())
            .put("city_objective", humanCity.id.toString()).put("target_date", targetDate.toString())
            .put("picker_target", exceptionFixture.target.shift.id.toString()).put("synthetic_address", true)
            .put("received_business_used", false))
        await { w.objectivesById[humanAddress.id] == humanAddress && w.objectivesById[humanCity.id] == humanCity }
    }
    /** Only own in-process measurements; never enumerates or captures an external surface. */
    private fun awaitHuman(predicate: () -> Boolean) {
        val session = checkNotNull(humanSession)
        while (true) {
            session.requireWaitingStep(); trace.primary?.let { throw it }
            if (predicate()) return
            android.os.SystemClock.sleep(50)
        }
    }
    private fun humanVisible(text: String): Boolean {
        val nodes = compose.onAllNodes(hasText(text, substring = false)).fetchSemanticsNodes()
        return nodes.size == 1 && compose.onNode(hasText(text, substring = false)).isDisplayed()
    }
    private fun humanMapStep() {
        phase = "HUMAN_MAP_AND_RETURN"
        openDay(humanTarget.shift.localStartDate, editing = false)
        val same = checkNotNull(owner)
        val card = hasTestTag("shift-detail-card-" + humanTarget.shift.id)
        val address = hasContentDescription("Abrir en Google Maps: " + Stage12B1HumanSession.ADDRESS) and hasAnyAncestor(card) and
            !hasAnyAncestor(hasTestTag("v2-actual-" + humanTarget.shift.id + "-card"))
        // Diagnóstico local temporal (B1 humana, 8/11): árbol sintético de QA si falta el nodo.
        if (compose.onAllNodes(address).fetchSemanticsNodes().size != 1) checkNotNull(pendingReceipt).put("debug_tree",
            compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).fetchSemanticsNodes().size.toString() + "\n" +
            compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).let { roots ->
                (0 until roots.fetchSemanticsNodes().size).joinToString("\n=====\n") { roots[it].printToString(Int.MAX_VALUE) } }.take(60_000))
        compose.onAllNodes(address).assertCountEquals(1)
        compose.onNode(address).performScrollTo().assertIsDisplayed().assertHasClickAction()
        val session = checkNotNull(humanSession)
        session.begin(Stage12B1HumanSession.HumanStep.MAP_AND_RETURN)
        awaitHuman { session.mapReturnObserved() }
        requireReady(); check(owner === same && c.detailDate == humanTarget.shift.localStartDate)
        compose.onAllNodes(hasTestTag("day-detail-modal-sheet")).assertCountEquals(1)
        compose.onNode(address).assertIsDisplayed()
        exact("human-map-return-readonly", checkNotNull(humanExpected))
        session.complete("RETURNED_SAME_CONTEXT", JSONObject().put("same_owner", true).put("same_day", true)
            .put("same_shift", true).put("same_address_node", true).put("business_unchanged", true))
        fact("exceptions.C032.ACTIVITY_NATIVE.own-address-return", "ACTIVITY_NATIVE", JSONObject().put("external_return_observed", true))
        closeDay()
    }
    private fun openHumanWorkSetup() {
        touch("main-menu-button"); touch("drawer-action-work-setup")
        await { w.surface == WorkSetupSurface.OVERVIEW && w.savingLocationObjectiveId == null }
        touch("advanced-options-toggle")
        compose.onNodeWithTag("objective-address-geocode").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
    private fun humanLocationStep(city: Boolean) {
        val session = checkNotNull(humanSession)
        val objective = if (city) humanCity else humanAddress
        val place = if (city) humanCityPlace else humanAddressPlace
        val expectedBefore = checkNotNull(humanExpected)
        val previousCalls = session.locationCallCount()
        val failures = if (city) listOf(
            "El permiso fue rechazado. Podés usar una dirección, continuar sin clima o habilitarlo desde Ajustes.",
            "Este teléfono no ofrece un servicio de ubicación disponible.",
            "No hay una ubicación aproximada de tu ciudad disponible. Podés usar la dirección o intentar más tarde.",
            "No pudimos obtener la ubicación aproximada de tu ciudad. Intentá de nuevo más tarde.",
            "El teléfono devolvió una ubicación inválida. Intentá de nuevo.",
            "Falta el permiso de ubicación. Podés continuar sin clima.",
            "La ubicación del teléfono no está disponible en este momento."
        ) else listOf(
            "No encontramos esa dirección. Completala con localidad y provincia o usá tu ciudad actual para el clima.",
            "No pudimos buscar la dirección. Revisá tu conexión o usá tu ciudad actual para el clima."
        )
        phase = if (city) "HUMAN_APPROXIMATE_CITY" else "HUMAN_ADDRESS_FOR_WEATHER"
        session.begin(if (city) Stage12B1HumanSession.HumanStep.APPROXIMATE_CITY else Stage12B1HumanSession.HumanStep.ADDRESS_FOR_WEATHER)
        var failureIndex: Int? = null
        awaitHuman {
            if (session.locationCalls().getOrNull(previousCalls)?.returned != null) true
            else if (checkNotNull(owner).lifecycle.currentState == Lifecycle.State.RESUMED) {
                failureIndex = failures.indexOfFirst(::humanVisible).takeIf { it >= 0 }; failureIndex != null
            } else false
        }
        val saved = session.locationCallCount() == previousCalls + 1
        if (saved) {
            val call = session.locationCalls().last()
            check(call.objective == objective.id && call.returned == true)
            await { w.savingLocationObjectiveId == null && w.errorMessage == null && w.objectivesById[objective.id]?.weatherLatitude != null }
            val afterAt = clock.instant()
            val found = db { checkNotNull(own.objectives.getById(objective.id)) }
            val foundPlace = db { checkNotNull(own.workCatalog.getWorkPlace(place.id)) }
            check(found.updatedAt == foundPlace.updatedAt && found.updatedAt.toEpochMilli() in call.before.toEpochMilli()..afterAt.toEpochMilli())
            val next = objective.copy(weatherLatitude = call.latitude, weatherLongitude = call.longitude, updatedAt = found.updatedAt)
            val nextPlace = place.copy(updatedAt = found.updatedAt)
            check(found == next && foundPlace == nextPlace)
            val expected = Stage12RecurringReadbackOracle.expected(expectedBefore,
                listOf(Stage12RecurringReadbackOracle.objective(objective), Stage12RecurringReadbackOracle.place(place)),
                listOf(Stage12RecurringReadbackOracle.objective(next), Stage12RecurringReadbackOracle.place(nextPlace)))
            exact("human-location-" + (if(city) "city" else "address") + "-persisted", expected)
            humanExpected = expected
            if (city) { humanCity = next; humanCityPlace = nextPlace } else { humanAddress = next; humanAddressPlace = nextPlace }
            session.complete("SAVED_FOR_OBJECTIVE", JSONObject().put("callback_count", 1).put("callback_accepted", true)
                .put("objective_row_exact", true).put("place_row_exact", true).put("other_store_unchanged", true))
            fact("exceptions." + (if(city) "A042" else "A041") + ".ACTIVITY_NATIVE.own-objective-location", "ACTIVITY_NATIVE",
                JSONObject().put("persisted_for_exact_objective", true).put("permission_dialog_proven", false))
        } else {
            check(session.locationCallCount() == previousCalls && failureIndex != null)
            exact("human-location-" + (if(city) "city" else "address") + "-unavailable-no-write", expectedBefore)
            session.complete(if (!city) "NO_RESULT_OR_SERVICE_FAILURE" else if (failureIndex == 0) "PERMISSION_DENIED" else "LOCATION_UNAVAILABLE",
                JSONObject().put("failure_message_index", failureIndex).put("visible_failure", true).put("callback_count", 0).put("store_unchanged", true))
        }
        humanLocationOutcomes[objective.id] = saved
    }
    private fun humanPickerSteps() {
        phase = "HUMAN_PICKER_PREPARE"
        checkNotNull(humanImage).publish()
        checkNotNull(humanSession).bindImage(checkNotNull(humanImage)::requireSelected)
        var expected = initialSituation()
        compareAlreadyCaptured("situation-initial-complete", expected.snapshot, checkNotNull(humanExpected))
        val id = expected.write.shift.id
        openDay(Stage12CalendarExceptionFixture.FIRST); touch("shift-cancel-" + id)
        val reason = "Cancelación sintética QA humana"; val note = "Borrador propio QA conservado"
        val noticeText = "23/10/2026 07:30"
        val notice = java.time.LocalDateTime.of(2026,10,23,7,30).atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toInstant()
        fillLabel("Motivo (obligatorio)", reason); fillLabel("Nota privada (opcional)", note)
        fillLabel("Fecha y hora del aviso", noticeText); touchIn(isDialog(), "Cambiar")
        val session = checkNotNull(humanSession); val same = checkNotNull(owner)
        val writes = situationMutations.size; val submissions = complementSubmittedUris.size
        session.begin(Stage12B1HumanSession.HumanStep.PICK_OWN_IMAGE)
        awaitHuman { session.pickerReturnObserved() }
        requireReady(); requirePendingForm(reason,note,noticeText)
        check(owner === same && situationMutations.size == writes && complementSubmittedUris.size == submissions)
        exact("human-picker-selected-pending", expected.snapshot)
        session.complete("OWN_IMAGE_SELECTED", JSONObject().put("external_return", true).put("pending_prompt", true)
            .put("no_import_or_write", true).put("selected_uri_exact_before_forward", true))
        phase = "HUMAN_SECOND_PICKER_CANCEL"
        session.begin(Stage12B1HumanSession.HumanStep.CANCEL_SECOND_PICKER)
        awaitHuman { session.pickerReturnObserved() }
        requireReady(); requirePendingForm(reason,note,noticeText)
        check(owner === same && c.detailDate == Stage12CalendarExceptionFixture.FIRST && situationMutations.size == writes && complementSubmittedUris.size == submissions)
        exact("human-picker-cancel-retains-draft", expected.snapshot)
        session.complete("PENDING_IMAGE_PRESERVED", JSONObject().put("fields_exact", true).put("same_owner", true).put("same_day", true).put("no_import_or_write", true))
        phase = "HUMAN_IMAGE_CONFIRM"
        confirmSituation(); awaitSituation(writes+1,id,ShiftStatus.CANCELLED)
        check(complementSubmittedUris.size == submissions+1)
        val selected = checkNotNull(complementSubmittedUris.last())
        checkNotNull(humanImage).requireSelected(selected)
        val mutation = situationMutations.last()
        requireSituationCommand(expected,mutation,ShiftStatus.CANCELLED,reason,note,CancellationActor.EMPLOYER,notice)
        expected = appliedSituation(expected,mutation,"human-picker-exact-owned-image-committed")
        val evidence = checkNotNull(expected.previous?.evidence)
        check(photoStore.file(evidence.storageKey).readBytes().contentEquals(checkNotNull(humanImageBytes)))
        checkNotNull(humanImage).verify(); humanExpected = expected.snapshot; closeDay()
        fact("exceptions.C074.ACTIVITY_NATIVE.external-own-picker-return-cancel", "ACTIVITY_NATIVE", JSONObject()
            .put("real_activity_result_registry", true).put("pending_cancel_preserved", true).put("own_uri_proven", true))
        fact("exceptions.C074.FILES_ISOLATED.exact-own-image", "FILES_ISOLATED", JSONObject().put("bytes_exact", true))
    }
    private fun humanWeatherStep() {
        val session = checkNotNull(humanSession)
        phase = "HUMAN_WEATHER_WITH_LOCATION"
        if (humanLocationOutcomes[humanAddress.id] != true) {
            session.begin(Stage12B1HumanSession.HumanStep.WEATHER_AFTER_LOCATION)
            closeOwner(); session.complete("LOCATION_UNAVAILABLE_NOT_RUN", JSONObject().put("weather_action_not_performed", true)); return
        }
        openDay(humanTarget.shift.localStartDate, editing = false); touch("shift-weather-" + humanTarget.shift.id)
        val weather = checkNotNull(humanWeather)
        await { weather.model.uiState.value.selectedShift == humanTarget.shift && !weather.model.uiState.value.isLoading }
        val stateBefore = weather.model.uiState.value
        check(!stateBefore.preferences.enabled && !stateBefore.preferences.providerExplanationAccepted)
        compose.onNodeWithTag("weather-essential-privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("weather-enable").performScrollTo().assertIsDisplayed().assertIsEnabled()
        session.begin(Stage12B1HumanSession.HumanStep.WEATHER_AFTER_LOCATION)
        awaitHuman { weather.model.uiState.value.preferences.enabled && weather.model.uiState.value.preferences.providerExplanationAccepted }
        await { val s = weather.model.uiState.value; !s.isLoading && !s.isRefreshing && s.loadingBriefIds.isEmpty() && (s.forecast != null || s.errorMessage != null) }
        val first = weather.model.uiState.value
        check(first.selectedShift == humanTarget.shift && first.ineligibleReason == null)
        first.errorMessage?.let(::textVisible)
        first.forecast?.let { forecast ->
            check(forecast.location.id == humanAddress.id.toString() && forecast.location.latitude == humanAddress.weatherLatitude &&
                forecast.location.longitude == humanAddress.weatherLongitude)
            compose.onNodeWithTag("weather-scroll-content").assertIsDisplayed()
        }
        val observations = JSONObject().put("first_state", if(first.forecast != null) "FORECAST" else "ERROR")
            .put("exact_shift", true).put("same_objective", true).put("enabled", true).put("explanation_accepted", true)
            .put("provider_correctness_proven", false).put("coordinates_recorded", false)
        touch("weather-close"); await { weather.model.uiState.value.surface == com.blackatsystems.miguardia.ui.weather.WeatherSurface.NONE }
        check(c.detailDate == humanTarget.shift.localStartDate)
        closeDay(); openDay(humanTarget.shift.localStartDate, editing = false); touch("shift-weather-" + humanTarget.shift.id)
        try { await { val s=weather.model.uiState.value; s.selectedShift == humanTarget.shift && !s.isLoading && !s.isRefreshing && s.loadingBriefIds.isEmpty() } }
        catch (error: Throwable) {
            // Diagnóstico local temporal (B1 humana, 8/11): sólo banderas, sin coordenadas ni textos del proveedor.
            val s = weather.model.uiState.value
            checkNotNull(pendingReceipt).put("debug_weather_wait", JSONObject().put("surface", s.surface.name)
                .put("selected_present", s.selectedShift != null).put("selected_id_match", s.selectedShift?.id == humanTarget.shift.id)
                .put("selected_equal", s.selectedShift == humanTarget.shift).put("is_loading", s.isLoading).put("is_refreshing", s.isRefreshing)
                .put("loading_brief_count", s.loadingBriefIds.size).put("forecast_present", s.forecast != null)
                .put("error_present", s.errorMessage != null).put("ineligible_present", s.ineligibleReason != null)
                .put("enabled", s.preferences.enabled).put("detail_date_match", c.detailDate == humanTarget.shift.localStartDate))
            throw error
        }
        val second = weather.model.uiState.value
        check(second.preferences.enabled && second.preferences.providerExplanationAccepted && second.ineligibleReason == null &&
            (second.forecast != null || second.errorMessage != null))
        second.errorMessage?.let(::textVisible)
        observations.put("reopened_state", if(second.forecast != null) "FORECAST" else "ERROR").put("reopened_same_day", true)
        touch("weather-close"); await { weather.model.uiState.value.surface == com.blackatsystems.miguardia.ui.weather.WeatherSurface.NONE }
        closeDay(); val forecast = second.forecast; closeOwner()
        val after = snapshot("human-weather-closed-persisted"); val before = checkNotNull(humanExpected)
        check(after.tables == before.tables)
        val pref = "files/datastore/weather.preferences_pb"; val cacheDirectory = "files/weather_cache"
        val locationKey = listOf(humanAddress.id.toString(), humanAddress.fullName, checkNotNull(humanAddress.weatherLatitude).toBits().toString(),
            checkNotNull(humanAddress.weatherLongitude).toBits().toString(), humanTarget.shift.zoneId.id).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(locationKey.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
        val cache = cacheDirectory + "/weather_" + humanAddress.id + "_" + digest + "_v1.cache"
        val allowed = setOf(pref, cacheDirectory, cache)
        check(after.files.filterKeys { it !in allowed } == before.files.filterKeys { it !in allowed })
        check(after.preferences.filterKeys { !it.startsWith(pref + "#") } == before.preferences.filterKeys { !it.startsWith(pref + "#") })
        requireHumanWeatherPreferences(File(privateRoot,pref))
        if (forecast != null) {
            check(db { com.blackatsystems.miguardia.weather.WeatherCacheStore.forLocation(dataRoot,forecast.location).read() } == forecast.copy(fetchedAt = Instant.ofEpochMilli(forecast.fetchedAt.toEpochMilli())))
            check(after.files.containsKey(cache))
        } else check(after.files.keys.none { it == cache })
        observations.put("closed_preferences_decoded", true).put("only_weather_files_changed", true).put("business_unchanged", true)
            .put("runtime_and_preferences_closed", true)
        checkNotNull(pendingReceipt).put("human_weather", observations); humanExpected = after
        session.complete(if (second.forecast != null) "PROVIDER_RESPONSE" else "PROVIDER_ERROR", observations)
        fact("exceptions.A041.ACTIVITY_NATIVE.weather-enabled-with-owned-location", "ACTIVITY_NATIVE", JSONObject()
            .put("persisted_preferences", true).put("same_day_reopened", true).put("provider_success_required", false))
    }
    private fun requireHumanWeatherPreferences(file: File) {
        val type = Class.forName("androidx.datastore.preferences.PreferencesProto\$PreferenceMap",false,Stage12FullStoreReadback::class.java.classLoader)
        val message = type.getMethod("parseFrom",ByteArray::class.java).invoke(null,file.readBytes())
        val values = type.getMethod("getPreferencesMap").invoke(message) as Map<*,*>
        check(values.keys.containsAll(setOf("enabled","provider_explanation_accepted")))
        check(values.keys.all { it in setOf("enabled","provider_explanation_accepted","last_refresh_attempt_epoch_millis","retry_after_until_epoch_millis") })
        for ((key,nullable) in values) {
            val value=checkNotNull(nullable); val kind=value.javaClass.getMethod("getValueCase").invoke(value) as Enum<*>
            if(key in setOf("enabled","provider_explanation_accepted")) check(kind.name=="BOOLEAN" && value.javaClass.getMethod("getBoolean").invoke(value)==true)
            else check(kind.name=="LONG" && (value.javaClass.getMethod("getLong").invoke(value) as Long)>0L)
        }
    }
}

/** Pure policy used by the actual owned Compose selector; no Android queries here. */
internal object Stage12MedicalConfirmationSemantics {
    data class Roots(val count: Int, val identityMatches: Boolean)
    data class Sample(val step: String, val count: Int? = null, val identity: Boolean? = null, val value: Boolean? = null)
    class Observation {
        var phase = "PRECONDITIONS"
        val samples = mutableListOf<Sample>()
        var inputIntentRecorded = false
        var inputEntered = false
        var inputReturned = false
    }
    @JvmStatic fun matchesRoot(dialog: Boolean, title: Boolean, range: Boolean): Boolean = dialog && title && range
    @JvmStatic fun execute(
        observed: Observation,
        roots: () -> Roots,
        candidates: () -> Int,
        rootVisible: () -> Boolean,
        rootEnabled: () -> Boolean,
        controlVisible: () -> Boolean,
        controlEnabled: () -> Boolean,
        awaitUnique: ((() -> Boolean) -> Unit),
        checkpoint: (String, () -> Unit) -> Unit,
        publish: () -> Unit,
        input: ((() -> Unit) -> Unit),
    ) {
        fun returned(sample: Sample) { observed.samples += sample; publish() }
        awaitUnique {
            observed.phase = "ROOTS"
            val found = roots()
            returned(Sample("ROOTS", count = found.count, identity = found.identityMatches))
            check(found.count >= 0 && found.count <= 1)
            if (found.count == 0) false else {
                check(found.identityMatches)
                observed.phase = "CANDIDATES"
                val count = candidates()
                returned(Sample("CANDIDATES", count = count))
                check(count >= 0 && count <= 1)
                count == 1
            }
        }
        // The await adapter must have returned only after the same predicate succeeded.
        check(observed.samples.lastOrNull() == Sample("CANDIDATES", count = 1))
        for ((step, query) in listOf(
            "ROOT_VISIBLE" to rootVisible, "ROOT_ENABLED" to rootEnabled,
            "CONTROL_VISIBLE" to controlVisible, "CONTROL_ENABLED" to controlEnabled,
        )) {
            observed.phase = step
            checkpoint(step) {
                val value = query()
                returned(Sample(step, value = value))
                check(value)
            }
        }
        observed.phase = "INPUT"
        observed.inputIntentRecorded = true
        publish()
        input {
            check(!observed.inputEntered)
            observed.inputEntered = true
        }
        check(observed.inputEntered)
        observed.inputReturned = true
        observed.phase = "COMPLETE"
        publish()
    }
}

/** The actual header/message selector and one-input guards; no Android access here. */
internal object Stage12ExceptionTextActionCriterion {
    data class Matches(val roots: Int?, val candidates: Int?)
    @JvmStatic fun matchesAction(exactText: Boolean, clickable: Boolean, ownRelation: Boolean) =
        exactText && clickable && ownRelation
    @JvmStatic fun matchesMessageRoot(assertive: Boolean, exactMessage: Boolean) = assertive && exactMessage
    @JvmStatic fun execute(
        query: () -> Matches,
        visible: () -> Boolean,
        enabled: () -> Boolean,
        awaitUnique: ((() -> Boolean) -> Unit),
        checkpoint: (Stage12SelectionDiagnostics.Checkpoint, () -> Unit) -> Unit,
        input: () -> Unit,
    ) {
        checkpoint(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
            var unique = false
            awaitUnique {
                val result = query()
                val roots = result.roots
                if (roots != null) check(roots >= 0 && roots <= 1)
                if (roots == 0) {
                    check(result.candidates == null)
                    unique = false
                } else {
                    val count = checkNotNull(result.candidates)
                    check(count >= 0 && count <= 1)
                    unique = count == 1
                }
                unique
            }
            check(unique)
        }
        checkpoint(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { check(visible()) }
        checkpoint(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { check(enabled()) }
        checkpoint(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { input() }
    }
}

/** Closed, test-only adapter for the two normal day-condition detail clients. */
internal object Stage12OwnedDetailSemantics {
    enum class Site { HOLIDAY, VACATION }
    enum class Part { DAY, MODAL, SCROLL, CARD, TEXT }
    data class Context(val sameOwner: Boolean, val started: Boolean, val monthMatches: Boolean,
        val detailEmpty: Boolean, val dateMatches: Boolean, val busy: Boolean, val surfacesClosed: Boolean)
    data class DayModel(val count: Int, val completed: Boolean?)
    interface Adapter {
        fun nowMs(): Long
        fun awaitWithin(timeoutMs: Long, condition: () -> Boolean)
        fun context(): Context
        fun dayModel(): DayModel
        fun count(part: Part): Int
        fun visible(part: Part): Boolean
        fun dayEnabled(): Boolean
        fun openDate()
        fun scrollOwnerMatches(): Boolean
        fun scrollText()
        fun assertTextVisible()
        fun back(): Boolean
    }
    class Negative(val code: String) : IllegalStateException("OWN_DETAIL_REJECTED")
    class Event(val operation: String, val parent: Int?, val enter: Int, val point: String, val ordinal: Int) {
        var exit: Int? = null
        var status = "ENTERED"
        var contextEntered = false
        var context: Context? = null
        var queryEntered = false
        var operationEntered = false
        var operationReturned = false
        var valueReturned = false
        var value: Any? = null
        var budget: Long? = null
        var error: String? = null
        var rejection: String? = null
        var failurePoint: String? = null
        var failureOrdinal: Int? = null
        fun returned(value: Any?) { this.value = value; valueReturned = true }
        fun projection(): Map<String, Any?> = linkedMapOf(
            "operation" to operation, "parent" to parent, "enter" to enter, "exit" to exit,
            "status" to status, "checkpoint" to point, "checkpoint_ordinal" to ordinal,
            "context_entered" to contextEntered, "context_returned" to (context != null),
            "context" to context?.let { contextProjection(it) }, "query_entered" to queryEntered,
            "operation_entered" to operationEntered, "operation_returned" to operationReturned,
            "value_returned" to valueReturned, "value" to value, "budget_ms" to budget,
            "error_kind" to error, "rejection" to rejection, "failure_checkpoint" to failurePoint,
            "failure_ordinal" to failureOrdinal,
        )
    }
    @JvmStatic fun contextProjection(c: Context): Map<String, Boolean> = linkedMapOf(
        "same_owner" to c.sameOwner, "started" to c.started, "month_matches" to c.monthMatches,
        "detail_empty" to c.detailEmpty, "date_matches" to c.dateMatches, "busy" to c.busy,
        "surfaces_closed" to c.surfacesClosed,
    )
    class Session(val site: Site, val run: String, val ownCase: String, val date: String, val textHash: String,
        private val trace: Stage12SelectionDiagnostics.Trace, private val adapter: Adapter) {
        private val events = mutableListOf<Event>()
        private val stack = mutableListOf<Int>()
        private var sequence = 0
        private var firstFailure: Int? = null
        private var start: Long? = null
        private var prepared = false
        private var complete = false
        private var backObservation: Stage12OwnedBackEventSemantics.Observation? = null
        private fun reject(code: String): Nothing = throw Negative(code)
        private fun require(value: Boolean, code: String) { if (!value) reject(code) }
        private fun <T> call(op: String, point: Stage12SelectionDiagnostics.Checkpoint, body: (Event) -> T): T {
            trace.primary?.let { throw it }
            var current: Event? = null
            var id: Int? = null
            try {
                return trace.checked(point) {
                    val e = Event(op, stack.lastOrNull(), ++sequence, point.name, trace.checkpointOrdinal)
                    current = e; id = events.size; events += e; stack += checkNotNull(id)
                    val result = body(e)
                    e.status = "RETURNED"; e.exit = ++sequence
                    result
                }
            } catch (failure: Throwable) {
                current?.let { e ->
                    e.status = "FAILED"; e.exit = ++sequence
                    e.error = if (failure is Negative) "REJECTED" else "THREW"
                    e.rejection = (failure as? Negative)?.code
                    e.failurePoint = trace.firstCheckpoint.name; e.failureOrdinal = trace.firstCheckpointOrdinal
                    if (firstFailure == null) firstFailure = id
                }
                throw failure
            } finally {
                if (id != null) check(stack.removeAt(stack.lastIndex) == id)
            }
        }
        private fun context(e: Event, mode: String): Context {
            e.contextEntered = true
            val c = adapter.context(); e.context = c
            require(c.sameOwner && c.started && c.monthMatches && c.surfacesClosed, "OWN_CONTEXT")
            require(c.detailEmpty != c.dateMatches, "FOREIGN_DATE")
            when (mode) {
                "CLOSED" -> require(c.detailEmpty && !c.busy, "NOT_CLOSED_READY")
                "OPEN" -> require(c.dateMatches && !c.detailEmpty && !c.busy, "NOT_OPEN_READY")
                "OPENING", "CLOSING" -> Unit
                else -> reject("INVALID_CONTEXT_MODE")
            }
            return c
        }
        private fun remaining(): Long {
            val elapsed = adapter.nowMs() - checkNotNull(start)
            require(elapsed in 0 until 30_000L, "PREPARATION_BUDGET")
            return 30_000L - elapsed
        }
        private fun count(part: Part, op: String, mode: String = "OPEN"): Int =
            call(op, Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { e ->
                if (mode != "CLOSING") remaining()
                context(e, mode)
                if (mode != "CLOSING") remaining()
                e.queryEntered = true
                val n = adapter.count(part); e.returned(n)
                require(n in 0..1, "MULTIPLE_OR_INVALID_NODES"); n
            }
        private fun visible(part: Part, op: String, mode: String = "OPEN"): Boolean =
            call(op, Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { e ->
                if (mode != "CLOSING") remaining()
                context(e, mode)
                if (mode != "CLOSING") remaining()
                e.queryEntered = true
                adapter.visible(part).also { e.returned(it) }
            }
        fun prepare() {
            call("WAIT_OPEN", Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { e ->
                require(start == null && !prepared && !complete, "REPEATED_PREPARATION")
                start = adapter.nowMs(); e.budget = remaining()
                var ready = false
                e.operationEntered = true
                adapter.awaitWithin(checkNotNull(e.budget)) {
                    val model = call("DAY_MODEL", Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { q ->
                        context(q, "CLOSED"); remaining(); q.queryEntered = true
                        val row = adapter.dayModel()
                        q.returned(linkedMapOf("count" to row.count, "completed" to row.completed))
                        require(row.count in 0..1 && (row.completed != null) == (row.count == 1), "DAY_MODEL_IDENTITY")
                        row
                    }
                    val result = if (model.count == 0 || count(Part.DAY, "DAY_QUERY", "CLOSED") == 0) false
                    else {
                        require(visible(Part.DAY, "DAY_VISIBLE", "CLOSED"), "DAY_NOT_VISIBLE")
                        call("DAY_ENABLED", Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { q ->
                            context(q, "CLOSED"); remaining(); q.queryEntered = true
                            val enabled = adapter.dayEnabled(); q.returned(enabled)
                            require(enabled, "DAY_DISABLED")
                        }
                        true
                    }
                    ready = result; result
                }
                e.operationReturned = true; e.returned(null)
                require(ready, "OPEN_WAIT_NOT_CONFIRMED")
                remaining()
            }
            call("OPEN_INPUT", Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { e ->
                remaining(); context(e, "CLOSED"); remaining(); e.operationEntered = true
                adapter.openDate(); e.operationReturned = true; e.returned(null); remaining()
            }
            call("WAIT_DETAIL", Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { e ->
                e.budget = remaining()
                var detailReady = false
                e.operationEntered = true
                adapter.awaitWithin(checkNotNull(e.budget)) {
                    val ready = call("MODEL_QUERY", Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { q ->
                        val c = context(q, "OPENING")
                        remaining()
                        (c.dateMatches && !c.detailEmpty && !c.busy).also { q.returned(it) }
                    }
                    val result = ready && count(Part.MODAL, "MODAL_QUERY") == 1 &&
                        count(Part.SCROLL, "SCROLL_QUERY") == 1 && count(Part.CARD, "CARD_QUERY") == 1 &&
                        count(Part.TEXT, "TEXT_QUERY") == 1 && visible(Part.MODAL, "MODAL_VISIBLE") &&
                        visible(Part.SCROLL, "SCROLL_VISIBLE")
                    detailReady = result; result
                }
                e.operationReturned = true; e.returned(null)
                require(detailReady, "DETAIL_WAIT_NOT_CONFIRMED")
                remaining()
            }
            if (!visible(Part.TEXT, "TEXT_VISIBLE")) {
                call("SCROLL_OWNER_QUERY", Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { e ->
                    remaining(); context(e, "OPEN")
                    remaining()
                    e.queryEntered = true
                    val ownScroll = adapter.scrollOwnerMatches(); e.returned(ownScroll)
                    require(ownScroll, "FOREIGN_SCROLL_OWNER")
                }
                call("SCROLL_INPUT", Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { e ->
                    remaining(); context(e, "OPEN"); remaining(); e.operationEntered = true
                    adapter.scrollText(); e.operationReturned = true; e.returned(null); remaining()
                }
            }
            call("ASSERT_VISIBLE", Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { e ->
                remaining(); context(e, "OPEN")
                remaining()
                e.queryEntered = true
                val visible = adapter.visible(Part.TEXT); e.returned(visible)
                require(visible, "FINAL_TEXT_NOT_VISIBLE")
                remaining()
                e.operationEntered = true
                adapter.assertTextVisible()
                e.operationReturned = true; remaining()
            }
            prepared = true
        }
        private fun probe(operation: String): Boolean? =
            call(operation, Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { e ->
                val n = count(Part.MODAL, "CLOSE_MODAL_QUERY", "CLOSING")
                val shown = if (n == 1) visible(Part.MODAL, "CLOSE_MODAL_VISIBLE", "CLOSING") else null
                // Use the model/owner sample after these UI queries, never the older pre-query model.
                val c = context(e, "CLOSING")
                val result = when {
                    c.busy -> null
                    c.detailEmpty && n == 0 -> true
                    c.dateMatches && !c.detailEmpty && n == 1 && shown == true -> false
                    else -> null
                }
                e.returned(result); result
            }
        fun close() {
            call("CLOSE_POLICY", Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { e ->
                require(prepared && !complete && backObservation == null, "CLOSE_NOT_PREPARED")
                val observed = Stage12OwnedBackEventSemantics.Observation(); backObservation = observed
                Stage12OwnedBackEventSemantics.execute(observed,
                    formReady = { probe("CLOSE_READY") == false },
                    confirmation = { probe("CLOSE_CONFIRMATION") },
                    nativeCall = {
                        call("BACK_INPUT", Stage12SelectionDiagnostics.Checkpoint.NATIVE_ASSERTION) { q ->
                            context(q, "OPEN"); q.operationEntered = true
                            adapter.back().also { q.operationReturned = true; q.returned(it) }
                        }
                    },
                    awaitUi = { condition ->
                        call("WAIT_CLOSE", Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION) { q ->
                            q.budget = 30_000L; q.operationEntered = true
                            adapter.awaitWithin(30_000L, condition)
                            q.operationReturned = true; q.returned(null)
                        }
                    },
                )
                e.returned(null)
            }
            complete = true
        }
        fun projection(): Map<String, Any?> = linkedMapOf(
            "schema" to "stage12-owned-condition-detail-v1", "site" to site.name, "run_uuid" to run,
            "case" to ownCase, "expected_date" to date, "expected_text_sha256" to textHash,
            "status" to if (firstFailure != null) "FAILED" else if (complete) "COMPLETE" else "PREFIX",
            "prepared" to prepared, "complete" to complete, "first_failure_event" to firstFailure,
            "events" to events.map { it.projection() },
            "back_observation" to backObservation?.let { b -> linkedMapOf(
                "phase" to b.phase, "return_available" to b.returnAvailable, "api_return" to b.apiReturn,
                "confirmation_before" to b.confirmationBefore, "confirmation_ui" to b.confirmationUi,
                "error_present" to (b.escapedErrorClass != null), "failure_code" to b.failureCode,
            ) },
        )
    }
}
