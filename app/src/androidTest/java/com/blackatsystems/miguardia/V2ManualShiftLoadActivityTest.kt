package com.blackatsystems.miguardia

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import java.time.YearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido integral de carga V2 sobre un fixture que limpia únicamente la base QA. */
class V2ManualShiftLoadActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var scenarioLaunchIntent: Intent? = null
    private var contextualEvidenceDirectory: File? = null
    private var syntheticExternalRequest: PendingMainDestinationRequest? = null
    private lateinit var selectedDate: LocalDate

    @Before
    fun prepareFreshV2Fixture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral V2 sólo puede ejecutarse contra el paquete QA."
        }
        preparePublicRootAndMarkOnboardingCompletedForTest()
        UiDevice.getInstance(instrumentation).wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()

        selectedDate = LocalDate.now(AppDefaults.zoneId())
        val timestamp = Instant.now()
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            check(store.workConfiguration.get() == null) {
                "La preparación QA debe dejar una base sin configuración."
            }
            val revision = EffectiveRevision(
                id = REVISION_ID,
                effectiveFrom = selectedDate.withDayOfMonth(1),
                value = WorkConfiguration(
                    sector = WorkSector.NURSING,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            )
            store.workConfiguration.createInitial(TIMELINE_ID, revision)
            val configuration = ResolvedWorkConfigurationRevision.resolve(
                history = requireNotNull(store.workConfiguration.get()),
                date = revision.effectiveFrom,
            )
            store.workCatalog.createFirstWorkSet(firstWorkSet(configuration, timestamp))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        requireNotNull(scenario).onActivity { scenarioLaunchIntent = Intent(it.intent) }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes().isNotEmpty()
        }
        requireNotNull(scenario).onActivity {
            assertEquals(YearMonth.from(selectedDate), ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.visibleMonth)
        }
    }

    @After
    fun closeActivity() {
        try {
            // ActivityScenario 1.7 filters lifecycle events by the launch Intent. Production
            // sanitizes consumed external destinations; restore only after all test assertions.
            scenario?.onActivity { activity ->
                scenarioLaunchIntent?.let { activity.intent = Intent(it) }
            }
            // requestedOrientation belongs to this Activity; closing releases it. Changing it here
            // would start a recreation concurrently with ActivityScenario.close().
            scenario?.close()
            scenario?.let { assertEquals(Lifecycle.State.DESTROYED, it.state) }
        } finally {
            scenario = null
            scenarioLaunchIntent = null
            syntheticExternalRequest?.let { request ->
                val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
                runBlocking { app.pendingMainDestinations.consume(request) {} }
            }
            syntheticExternalRequest = null
        }
    }

    @Test
    fun backFromInitialDateSelectionCancelsCompletelyWithoutReentryOrWrites() {
        openManualLoad()
        compose.onNodeWithTag("day-$selectedDate").performClick()

        requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()

        requireNotNull(scenario).onActivity { activity ->
            val load = ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].uiState.value
            val calendar = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
            assertEquals(V2ManualShiftLoadStage.IDLE, load.stage)
            assertEquals(false, load.isActive)
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(null, calendar.selectionPurpose)
        }
        assertTrue(runBlocking {
            store().shifts.observeStartingBetween(selectedDate, selectedDate).first().isEmpty()
        })
        compose.onNodeWithTag("v2-manual-confirm-dates").assertDoesNotExist()
    }

    @Test
    fun backFromTemplateStageReturnsToTheSameDateSelectionWithoutWriting() {
        openManualLoad()
        compose.onNodeWithTag("day-$selectedDate").performClick()
        compose.onNodeWithTag("v2-manual-confirm-dates").performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("manual-institutions").fetchSemanticsNodes().isNotEmpty()
        }

        requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-manual-confirm-dates").fetchSemanticsNodes().isNotEmpty()
        }

        requireNotNull(scenario).onActivity { activity ->
            val load = ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].uiState.value
            val calendar = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
            assertEquals(V2ManualShiftLoadStage.SELECT_DATES, load.stage)
            assertEquals(setOf(selectedDate), load.selectedDates)
            assertEquals(CalendarInteractionMode.EDIT, calendar.interactionMode)
            assertEquals(CalendarSelectionPurpose.MANUAL_LOAD, calendar.selectionPurpose)
            assertEquals(setOf(selectedDate), calendar.editSelectedDates)
            assertEquals(false, calendar.editSelectionConfirmed)
        }
        assertTrue(runBlocking {
            store().shifts.observeStartingBetween(selectedDate, selectedDate).first().isEmpty()
        })
    }

    @Test
    fun draftSurvivesRecreationAndShiftSnapshotSurviveReopen() {
        openManualLoad()
        compose.onNodeWithTag("day-$selectedDate").performClick()
        compose.onNodeWithTag("v2-manual-confirm-dates").performClick()
        chooseInitialTemplate()
        compose.onNodeWithTag("manual-add-note").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().performTextInput(NOTE)
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        awaitEnabledControl("v2-manual-position")
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performTextInput(POSITION)

        requireNotNull(scenario).recreate()

        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("manual-institutions").fetchSemanticsNodes().isNotEmpty()
        }
        assertDraftRestoredWithoutWrites()
        assertSelectedTemplate(TEMPLATE_ID)
        expandNoteEditorIfNeeded()
        compose.onNodeWithTag("manual-note").performScrollTo().assertTextContains(NOTE)
        compose.onNodeWithTag("v2-manual-position")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(POSITION)

        rotateAndVerifyDraft(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            expectedConfigurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
        )
        rotateAndVerifyDraft(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            expectedConfigurationOrientation = Configuration.ORIENTATION_PORTRAIT,
        )
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking {
                store.shifts.observeStartingBetween(selectedDate, selectedDate).first().size == 1
            }
        }
        val saved = runBlocking {
            store.shifts.observeStartingBetween(selectedDate, selectedDate).first().single()
        }
        val snapshot = runBlocking { store.v2Shifts.getWorkSnapshot(saved.id) }
        assertEquals(POSITION, saved.position)
        assertEquals(OBJECTIVE_ID, saved.sourceObjectiveId)
        assertNotNull(snapshot)
        assertEquals(saved.id, snapshot?.shiftId)
        assertEquals(TEMPLATE_ID, snapshot?.templateId)
        assertEquals(WorkSector.NURSING, snapshot?.sector)
        assertEquals(NOTE, runBlocking { store.shiftNotes.observeForShift(saved.id).first().single().body })

        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText(ABBREVIATION, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        requireNotNull(scenario).close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText(ABBREVIATION, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("jornada $ABBREVIATION", substring = true)
            .assertExists()
        compose.onNodeWithText("¿En qué rubro trabajás?").assertDoesNotExist()
    }

    @Test
    fun contextualPlaceAndTemplateSurviveRecreationThenConfirmExactPairs() {
        val otherDate = if (selectedDate.dayOfWeek.value < 7 && selectedDate.plusDays(1).month == selectedDate.month) {
            selectedDate.plusDays(1)
        } else {
            selectedDate.minusDays(1)
        }
        val dates = setOf(selectedDate, otherDate)
        enterContextualLoad(dates)
        openCatalog()
        compose.onNodeWithTag("v2-manual-create-place").performScrollTo().performClick()
        awaitEnabledControl("work-place-name")
        compose.onNodeWithTag("work-place-name").performScrollTo().performTextInput("Lugar ficticio contextual")
        compose.onNodeWithTag("work-place-abbreviation").performScrollTo().performTextInput("CTX")
        requireNotNull(scenario).recreate()
        awaitEnabledControl("work-place-name")
        compose.onNodeWithText("Lugar ficticio contextual").assertExists()
        captureContextualMatrix("place", listOf("work-place-name", "work-place-continue"))
        compose.onNodeWithTag("work-place-continue").performScrollTo().performClick()
        awaitEnabledControl("work-time-start")
        val store = store()
        val place = runBlocking { store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workPlaces.single { it.id != PLACE_ID } }
        requireNotNull(scenario).onActivity {
            assertEquals(place.id, ViewModelProvider(it)[WorkSetupViewModel::class.java].uiState.value.selectedTemplatePlaceId)
        }
        fillContextualTemplate()
        requireNotNull(scenario).recreate()
        awaitEnabledControl("work-time-start")
        compose.onNodeWithText("Usar color").assertDoesNotExist()
        compose.onNodeWithText("10:15").assertExists()
        captureContextualMatrix("template", listOf("work-time-start", "work-template-save"))
        assertTrue(runBlocking { store.shifts.observeStartingBetween(dates.min(), dates.max()).first().isEmpty() })
        compose.onNodeWithTag("work-template-save").performScrollTo().performClick()
        val template = awaitCreatedTemplate()
        assertEquals(place.id, template.workPlaceId)
        assertLoadPreserved(dates, template.id)
        captureContextualMatrix("return", listOf("manual-institutions", "v2-manual-save-direct"))
        assertLoadPreserved(dates, template.id)
        assertTrue(runBlocking { store.shifts.observeStartingBetween(dates.min(), dates.max()).first().isEmpty() })
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) { runBlocking { store.shifts.observeStartingBetween(dates.min(), dates.max()).first().size == 2 } }
        val shifts = runBlocking { store.shifts.observeStartingBetween(dates.min(), dates.max()).first() }
        assertEquals(dates, shifts.map { it.localStartDate }.toSet())
        shifts.forEach { shift ->
            val snapshot = runBlocking { requireNotNull(store.v2Shifts.getWorkSnapshot(shift.id)) }
            assertEquals(template.id, snapshot.templateId)
            assertEquals(place.id, snapshot.workPlaceId)
            assertEquals(POSITION, shift.position)
        }
    }

    @Test
    fun contextualExistingPlaceDefersExternalDateUntilLoadConfirmation() {
        enterContextualLoad(setOf(selectedDate))
        openCatalog(PLACE_ID)
        compose.onNodeWithTag("v2-manual-create-template").performScrollTo().performClick()
        awaitEnabledControl("work-time-start")
        val externalDate = selectedDate.plusMonths(1)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
        requireNotNull(scenario).onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .setAction(MainActivity.ACTION_VIEW_DATE)
                .putExtra(MainActivity.EXTRA_OWNER_LOCAL_DATE, externalDate.toString())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(WAIT_MILLIS) { app.pendingMainDestinations.state.value != null }
        syntheticExternalRequest = requireNotNull(app.pendingMainDestinations.state.value)
        fillContextualTemplate()
        compose.onNodeWithTag("work-template-save").performScrollTo().performClick()
        val template = awaitCreatedTemplate()
        assertEquals(PLACE_ID, template.workPlaceId)
        assertLoadPreserved(setOf(selectedDate), template.id)
        assertNotNull(app.pendingMainDestinations.state.value)
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) { app.pendingMainDestinations.state.value == null }
        requireNotNull(scenario).onActivity {
            assertEquals(YearMonth.from(externalDate), ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.visibleMonth)
        }
    }

    @Test
    fun cancellingContextualTemplateKeepsSavedPlaceAndOriginalConfirmedLoad() {
        enterContextualLoad(setOf(selectedDate))
        openCatalog()
        compose.onNodeWithTag("v2-manual-create-place").performScrollTo().performClick()
        awaitEnabledControl("work-place-name")
        compose.onNodeWithTag("work-place-name").performScrollTo().performTextInput("Lugar ficticio parcial")
        compose.onNodeWithTag("work-place-abbreviation").performScrollTo().performTextInput("PAR")
        compose.onNodeWithTag("work-place-continue").performScrollTo().performClick()
        awaitEnabledControl("work-time-start")
        compose.onNodeWithTag("work-time-start").performScrollTo().performTextInput("1015")
        requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Descartar", useUnmergedTree = true).performClick()
        val savedPlaceMessage = compose.onNodeWithText(
            "El lugar quedó guardado, aunque cancelaste su horario. Podés crear un horario para ese lugar sin volver a crearlo.",
            useUnmergedTree = true,
        )
        // Returning from the editor refreshes Room asynchronously before publishing the confirmation.
        compose.waitUntil(WAIT_MILLIS) { savedPlaceMessage.isDisplayed() }
        savedPlaceMessage.assertIsDisplayed()
        assertLoadPreserved(setOf(selectedDate), TEMPLATE_ID)
        val catalog = runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first() }
        assertEquals(2, catalog.workPlaces.size)
        assertEquals(1, catalog.workTemplates.size)
        assertTrue(runBlocking { store().shifts.observeStartingBetween(selectedDate, selectedDate).first().isEmpty() })
    }

    @Test
    fun unifiedSingleAndMultipleLoadOverwriteOverlayClearAndCancelPreserveExactData() {
        val month = YearMonth.from(selectedDate)
        val otherDates = (1..month.lengthOfMonth()).map(month::atDay).filter { it != selectedDate }.take(2)
        val batchDates = setOf(selectedDate, otherDates[0])
        val francoDate = otherDates[1]

        // The real single-day entry goes directly from detail to the same main form.
        compose.onNodeWithTag("day-$selectedDate").performClick()
        captureClosureFrame("day-detail")
        compose.onNodeWithText("Editar día").performScrollTo().performClick()
        awaitEnabledControl("manual-institutions")
        compose.onNodeWithTag("day-detail-manage-day-off").assertDoesNotExist()
        compose.onAllNodesWithText("Cargando jornada para los días:").assertCountEquals(1)
        chooseInitialTemplate()
        addLoadNote("Nota original ficticia")
        captureContextualMatrix("load-note", listOf("manual-note", "v2-manual-save-direct"))
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
        awaitShiftCount(setOf(selectedDate), 1)
        val first = runBlocking { store().shifts.observeStartingBetween(selectedDate, selectedDate).first().single() }
        val oldNotes = runBlocking { store().shiftNotes.observeForShift(first.id).first() }
        assertEquals("Nota original ficticia", oldNotes.single().body)

        // A separate, non-overlapping row is synthetic fixture data, not another UI action.
        val untouchedId = UUID.randomUUID()
        val untouched = first.copy(id = untouchedId,
            startAt = selectedDate.atTime(20, 0).atZone(AppDefaults.zoneId()).toInstant(),
            endAt = selectedDate.atTime(23, 0).atZone(AppDefaults.zoneId()).toInstant(),
            startTimeSnapshot = LocalTime.of(20, 0), endTimeSnapshot = LocalTime.of(23, 0))
        runBlocking {
            val snapshot = requireNotNull(store().v2Shifts.getWorkSnapshot(first.id))
            val eveningTemplate = requireNotNull(store().workCatalog.getWorkTemplate(snapshot.templateId))
                .copy(id = UUID.randomUUID(), startTime = LocalTime.of(20, 0), endTime = LocalTime.of(23, 0))
            store().workCatalog.createWorkTemplate(eveningTemplate)
            store().v2Shifts.insert(V2ShiftWrite(untouched,
                snapshot.copy(shiftId = untouchedId, templateId = eveningTemplate.id)))
        }

        enterContextualLoad(batchDates)
        addLoadNote("Nota común ficticia, independiente")
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
        awaitEnabledControl("v2-occupied-dialog")
        captureClosureFrame("overwrite-preview")
        compose.onNodeWithText("Sobrescribir").performScrollTo().performClick()
        confirmWarningsIfPresented(batchDates, 3)
        val afterOverwrite = runBlocking { store().shifts.observeStartingBetween(batchDates.min(), batchDates.max()).first() }
        assertEquals(untouched, afterOverwrite.single { it.id == untouchedId })
        assertTrue(afterOverwrite.none { it.id == first.id })
        assertTrue(runBlocking { store().shiftNotes.observeForShift(first.id).first().isEmpty() })
        val newNotes = afterOverwrite.filter { it.id != untouchedId }.map { row ->
            runBlocking { store().shiftNotes.observeForShift(row.id).first().single() }
        }
        assertEquals(2, newNotes.map { it.id }.distinct().size)
        assertTrue(newNotes.all { it.body == "Nota común ficticia, independiente" })

        openLoadDates(setOf(francoDate))
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().performClick()
        awaitEnabledControl("manual-batch-preview")
        compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store().explicitDayStatuses.observeBetween(francoDate, francoDate).first().singleOrNull()?.type == ExplicitDayStatusType.DAY_OFF }
        }
        awaitLoadCompleted()
        openLoadDates(setOf(francoDate))
        chooseInitialTemplate()
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
        awaitEnabledControl("v2-occupied-dialog")
        compose.onNodeWithText("Superponer").performScrollTo().performClick()
        confirmWarningsIfPresented(setOf(francoDate), 1)
        assertEquals(ExplicitDayStatusType.DAY_OFF, runBlocking {
            store().explicitDayStatuses.observeBetween(francoDate, francoDate).first().single().type
        })
        captureClosureFrame("calendar-after-overlay")

        openLoadDates(setOf(selectedDate))
        val beforeClear = runBlocking { store().shifts.observeStartingBetween(selectedDate, selectedDate).first() }
        compose.onNodeWithTag("manual-clear-days").performScrollTo().performClick()
        awaitEnabledControl("manual-batch-preview")
        captureClosureFrame("clear-preview")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(beforeClear, runBlocking { store().shifts.observeStartingBetween(selectedDate, selectedDate).first() })
        compose.onNodeWithTag("manual-clear-days").performScrollTo().performClick()
        awaitEnabledControl("manual-batch-preview")
        compose.onNodeWithText("Confirmar").performClick()
        awaitShiftCount(setOf(selectedDate), 0)
        assertEquals(ExplicitDayStatusType.UNDEFINED, runBlocking {
            store().explicitDayStatuses.observeBetween(selectedDate, selectedDate).first().single().type
        })
        assertEquals(1, runBlocking { store().shifts.observeStartingBetween(otherDates[0], otherDates[0]).first().size })

        openLoadDates(setOf(selectedDate))
        chooseInitialTemplate()
        addLoadNote("Esta nota ficticia no se guarda")
        compose.onNodeWithTag("manual-cancel-load").performScrollTo().performClick()
        compose.onNodeWithText("Descartar carga").performClick()
        awaitLoadCompleted()
        assertTrue(runBlocking { store().shifts.observeStartingBetween(selectedDate, selectedDate).first().isEmpty() })
        assertEquals(2, runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.size })
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        captureClosureFrame("calendar-final")
    }

    @Test
    fun reselectingDatesPreservesCompleteDraftAcrossEmptySelectionAndRecreationThenSavesOnlyFinalDates() {
        val month = YearMonth.from(selectedDate)
        val initialDates = (1..4).map(month::atDay).toSet()
        val finalDates = setOf(month.atDay(2), month.atDay(4))
        enterContextualLoad(initialDates)
        addLoadNote(NOTE)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == QA_APPLICATION_ID)
        val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            try {
                bitmap.eraseColor(android.graphics.Color.CYAN)
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            } finally { bitmap.recycle() }
        }
        // A private synthetic source exercises the real importer, not the OEM picker or user media.
        val source = File(context.filesDir, "reports/artifacts/stage32-source-${UUID.randomUUID()}.png").also {
            check(it.parentFile!!.isDirectory || it.parentFile!!.mkdirs())
            it.writeBytes(bytes)
        }
        try {
            val uri = FileProvider.getUriForFile(context, "$QA_APPLICATION_ID.fileprovider", source)
            requireNotNull(scenario).onActivity { activity ->
                ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].importNoteImages(List(31) { uri })
            }
            compose.waitUntil(WAIT_MILLIS) { manualLoadState().errorMessage != null }
            assertTrue(manualLoadState().noteAttachments.isEmpty())
            requireNotNull(scenario).onActivity { activity ->
                ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].importNoteImages(listOf(uri))
            }
            compose.waitUntil(WAIT_MILLIS) {
                manualLoadState().let { !it.isLoading && it.noteAttachments.size == 1 }
            }
            val before = manualLoadState()
            assertNull(before.errorMessage)
            val draftFile = File(context.filesDir, "manual_load_drafts/${before.noteAttachments.single().storageKey}")
            assertArrayEquals(bytes, draftFile.readBytes())
            compose.onNodeWithTag("manual-expand-dates").performScrollTo().performClick()
            val locale = Locale.forLanguageTag("es-AR")
            val fullDates = initialDates.sorted().joinToString("\n") { date ->
                date.format(DateTimeFormatter.ofPattern("EEEE dd/MM/yy", locale))
                    .replaceFirstChar { it.titlecase(locale) }
            }
            compose.onNodeWithTag("v2-manual-exact-dates").performScrollTo().assertTextContains(fullDates)
            captureClosureFrame("stage32-expanded-dates")
            compose.onNodeWithTag("manual-expand-dates").performScrollTo().performClick()
            openCatalog(PLACE_ID)
            compose.onNodeWithTag("v2-manual-create-template").performScrollTo().assertIsDisplayed()
            captureClosureFrame("stage32-inline-institution-schedules")
            compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
            captureContextualMatrix("stage32-draft", listOf("manual-toggle-position", "manual-note-images"))
            assertNoDraftWrites(month)
            compose.onNodeWithTag("v2-manual-modify-dates").performScrollTo().performClick()
            awaitEnabledControl("v2-manual-confirm-dates")
            initialDates.forEach { date -> compose.onNodeWithTag("day-$date").performClick() }
            compose.waitUntil(WAIT_MILLIS) { manualLoadState().selectedDates.isEmpty() }
            compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
            requireNotNull(scenario).recreate()
            compose.waitUntil(WAIT_MILLIS) {
                compose.onAllNodesWithTag("v2-manual-confirm-dates").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
            val empty = manualLoadState()
            assertEquals(V2ManualShiftLoadStage.SELECT_DATES, empty.stage)
            assertTrue(empty.selectedDates.isEmpty())
            assertDraftValues(before, empty)
            assertArrayEquals(bytes, draftFile.readBytes())
            assertNoDraftWrites(month)
            captureClosureFrame("stage32-empty-selection-restored")
            finalDates.forEach { date -> compose.onNodeWithTag("day-$date").performClick() }
            awaitEnabledControl("v2-manual-confirm-dates")
            compose.onNodeWithTag("v2-manual-confirm-dates").performClick()
            awaitEnabledControl("manual-institutions")
            val after = manualLoadState()
            assertEquals(finalDates, after.selectedDates)
            assertEquals(before.selectedOption, after.selectedOption)
            assertDraftValues(before, after)
            assertTrue(after.occupiedDates.isEmpty())
            assertEquals(null, after.occupiedPolicy)
            assertEquals(null, after.reviewFingerprint)
            compose.onNodeWithTag("manual-expand-dates").assertDoesNotExist()
            expandNoteEditorIfNeeded()
            compose.onNodeWithTag("manual-note").performScrollTo().assertTextContains(NOTE)
            compose.onNodeWithTag("manual-note-images").assertTextContains("Agregar imágenes")
            compose.onNodeWithTag("note-draft-image-1").performScrollTo().assertIsDisplayed()
            assertNoDraftWrites(month)
            captureClosureFrame("stage32-final-draft-restored")
            compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().performClick()
            awaitShiftCount(finalDates, finalDates.size)
            val saved = runBlocking { store().shifts.observeStartingBetween(month.atDay(1), month.atEndOfMonth()).first() }
            assertEquals(finalDates, saved.map { it.localStartDate }.toSet())
            assertEquals(finalDates.size, saved.size)
            val notes = saved.map { shift ->
                assertEquals(POSITION, shift.position)
                assertEquals(OBJECTIVE_ID, shift.sourceObjectiveId)
                assertEquals(TEMPLATE_ID, runBlocking { store().v2Shifts.getWorkSnapshot(shift.id) }?.templateId)
                runBlocking { store().shiftNotes.observeWithAttachmentsForShift(shift.id).first().single() }
            }
            assertEquals(finalDates.size, notes.map { it.note.id }.distinct().size)
            assertEquals(finalDates.size, notes.map { it.attachments.single().storageKey }.distinct().size)
            notes.forEach { note ->
                assertEquals(NOTE, note.note.body)
                assertArrayEquals(bytes, File(context.filesDir, "schedule_photos/${note.attachments.single().storageKey}").readBytes())
            }
            assertEquals(false, draftFile.exists())
            assertEquals(1, runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.size })
            compose.onNodeWithTag("month-grid").assertIsDisplayed()
            compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
            captureClosureFrame("stage32-calendar-final-selection")
        } finally {
            check(!source.exists() || source.delete())
        }
    }

    private fun manualLoadState(): V2ManualShiftLoadUiState {
        var state: V2ManualShiftLoadUiState? = null
        requireNotNull(scenario).onActivity { state = ViewModelProvider(it)[V2ManualShiftLoadViewModel::class.java].uiState.value }
        return requireNotNull(state)
    }

    private fun assertDraftValues(before: V2ManualShiftLoadUiState, after: V2ManualShiftLoadUiState) {
        assertEquals(before.operationId, after.operationId)
        assertEquals(before.operationTimestamp, after.operationTimestamp)
        assertEquals(before.selectedTemplateId, after.selectedTemplateId)
        assertEquals(before.selectedTemplateFingerprint, after.selectedTemplateFingerprint)
        assertEquals(before.position, after.position)
        assertEquals(before.noteBody, after.noteBody)
        assertEquals(before.noteAttachments, after.noteAttachments)
    }

    private fun assertNoDraftWrites(month: YearMonth) {
        assertTrue(runBlocking { store().shifts.observeStartingBetween(month.atDay(1), month.atEndOfMonth()).first().isEmpty() })
        assertTrue(runBlocking { store().shiftNotes.allAttachmentStorageKeys().isEmpty() })
        assertTrue(runBlocking { store().explicitDayStatuses.observeBetween(month.atDay(1), month.atEndOfMonth()).first().isEmpty() })
    }

    private fun captureClosureFrame(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureContextualEvidence") != "true") return
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID)
        compose.waitForIdle()
        settleConfirmationForCapture()
        compose.waitUntil(WAIT_MILLIS) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == QA_APPLICATION_ID
        }
        val directory = contextualEvidenceDirectory ?: File(checkNotNull(context.getExternalFilesDir(null)),
            "contextual-qa/${UUID.randomUUID()}").also { check(it.mkdirs()); contextualEvidenceDirectory = it }
        val output = File(directory, "$name.png")
        captureQaFrame(output)
    }

    private fun captureQaFrame(output: File) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun qaFocused() = automation.rootInActiveWindow?.packageName?.toString() == QA_APPLICATION_ID
        check(qaFocused()) { "Se detuvo la captura: QA perdió el foco." }
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            // Keep pixels in memory until both checks agree; never persist a foreign window.
            check(qaFocused()) { "Se descartó la captura: QA perdió el foco." }
            check(!output.exists())
            output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun addLoadNote(text: String) {
        compose.onNodeWithTag("manual-add-note").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().performTextInput(text)
        requireNotNull(scenario).onActivity { activity ->
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }

    private fun openLoadDates(dates: Set<LocalDate>) {
        openManualLoad()
        dates.forEach { date -> awaitEnabledControl("day-$date"); compose.onNodeWithTag("day-$date").performClick() }
        compose.onNodeWithTag("v2-manual-confirm-dates").performClick()
        awaitEnabledControl("manual-institutions")
    }

    private fun awaitShiftCount(dates: Set<LocalDate>, expected: Int) {
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store().shifts.observeStartingBetween(dates.min(), dates.max()).first()
                .count { it.localStartDate in dates } == expected }
        }
        awaitLoadCompleted()
    }

    private fun awaitLoadCompleted() {
        compose.waitUntil(WAIT_MILLIS) {
            var complete = false
            requireNotNull(scenario).onActivity { activity ->
                complete = ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].uiState.value.stage == V2ManualShiftLoadStage.IDLE &&
                    ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value.interactionMode == CalendarInteractionMode.VIEW
            }
            complete
        }
        awaitEnabledControl("main-menu-button")
    }

    private fun confirmWarningsIfPresented(dates: Set<LocalDate>, expected: Int) {
        // Wait for the next state, not a retry: at most one explicit acknowledgement.
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-warning-dialog").fetchSemanticsNodes().isNotEmpty() ||
                runBlocking { store().shifts.observeStartingBetween(dates.min(), dates.max()).first()
                    .count { it.localStartDate in dates } == expected }
        }
        if (compose.onAllNodesWithTag("v2-warning-dialog").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continuar igualmente").performClick()
        }
        awaitShiftCount(dates, expected)
    }

    private fun store() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication).localDataStore

    /** Opt-in visual evidence of this disposable synthetic fixture; never changes Android display settings. */
    private fun captureContextualMatrix(stage: String, controlTags: List<String>) {
        if (InstrumentationRegistry.getArguments().getString("captureContextualEvidence") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID)
        val evidence = contextualEvidenceDirectory ?: File(
            checkNotNull(context.getExternalFilesDir(null)),
            "contextual-qa/${UUID.randomUUID()}",
        ).also {
            check(it.mkdirs()) { "No se pudo crear la carpeta propia de evidencia contextual" }
            contextualEvidenceDirectory = it
        }
        val preferences = context.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val hadTheme = preferences.contains(MainActivity.APP_THEME_MODE)
        val previousTheme = preferences.getString(MainActivity.APP_THEME_MODE, null)
        val device = UiDevice.getInstance(instrumentation)
        try {
            for (theme in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)) {
                // Real Activity evidence inherits the phone's current Android text size.
                // Simulated Android text configurations are covered by Compose matrix tests.
                run {
                    check(preferences.edit()
                        .putString(MainActivity.APP_THEME_MODE, theme.name)
                        .commit())
                    requireNotNull(scenario).recreate()
                    awaitEnabledControl(controlTags.first())
                    compose.onNodeWithText("Usar color").assertDoesNotExist()
                    requireNotNull(scenario).onActivity { activity ->
                        activity.currentFocus?.clearFocus()
                        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                    }
                    compose.waitForIdle()
                    settleConfirmationForCapture()
                    for (tag in controlTags) {
                        awaitEnabledControl(tag)
                        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled()
                        compose.waitForIdle()
                        // Hiding the IME is asynchronous; UiDevice's last package event can be stale.
                        // Check the actual active root and Activity focus, never force another window away.
                        var hasFocus = false
                        var activePackage: String? = null
                        fun qaWindowIsActive(): Boolean {
                            requireNotNull(scenario).onActivity { hasFocus = it.window.decorView.hasWindowFocus() }
                            activePackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                            return hasFocus && activePackage == QA_APPLICATION_ID
                        }
                        try {
                            compose.waitUntil(WAIT_MILLIS) { qaWindowIsActive() }
                        } catch (error: ComposeTimeoutException) {
                            throw AssertionError(
                                "QA capture $stage/${theme.name}/android-current-font: QA focus unavailable",
                                error,
                            )
                        }
                        check(qaWindowIsActive()) {
                            "No se captura una aplicación ajena al fixture QA"
                        }
                        val file = File(evidence, "$stage-${theme.name}-android-current-font-$tag.png")
                        captureQaFrame(file)
                    }
                }
            }
        } finally {
            val editor = preferences.edit()
            if (hadTheme) editor.putString(MainActivity.APP_THEME_MODE, previousTheme)
            else editor.remove(MainActivity.APP_THEME_MODE)
            check(editor.commit()) { "No se pudo restaurar la apariencia interna del fixture" }
            requireNotNull(scenario).recreate()
            awaitEnabledControl(controlTags.first())
        }
    }

    private fun settleConfirmationForCapture() {
        // Verify the normal transient dismissal using Compose's clock, so a banner
        // cannot obscure the visual evidence. No production duration or assertion changes.
        compose.mainClock.advanceTimeBy(com.blackatsystems.miguardia.ui.components.CONFIRMATION_DURATION_MILLIS + 32)
        compose.waitForIdle()
        compose.onNodeWithTag("transient-confirmation").assertDoesNotExist()
    }

    private fun enterContextualLoad(dates: Set<LocalDate>) {
        openManualLoad()
        dates.forEach {
            awaitEnabledControl("day-$it")
            compose.onNodeWithTag("day-$it").performClick()
        }
        compose.onNodeWithTag("v2-manual-confirm-dates").performClick()
        chooseInitialTemplate()
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        awaitEnabledControl("v2-manual-position")
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performTextInput(POSITION)
    }

    private fun openManualLoad() {
        awaitEnabledControl("main-menu-button")
        compose.onNodeWithTag("main-menu-button").performClick()
        awaitEnabledControl("drawer-action-calendar-load")
        compose.onNodeWithTag("drawer-action-calendar-load").performClick()
    }

    private fun openCatalog(placeId: UUID? = null) {
        awaitEnabledControl("manual-institutions")
        if (compose.onAllNodesWithTag("manual-inline-catalog").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        }
        if (placeId != null) {
            awaitEnabledControl("manual-place-$placeId")
            compose.onNodeWithTag("manual-place-$placeId").performScrollTo().performClick()
        }
    }

    private fun chooseInitialTemplate() {
        openCatalog(PLACE_ID)
        compose.onNodeWithTag("v2-template-$TEMPLATE_ID").performScrollTo().performClick()
        assertSelectedTemplate(TEMPLATE_ID)
    }

    private fun assertSelectedTemplate(templateId: UUID) {
        val template = runBlocking {
            store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
                .workTemplates.single { it.id == templateId }
        }
        openCatalog(template.workPlaceId)
        awaitSelectedControl("v2-template-$templateId")
        compose.onNodeWithTag("v2-template-$templateId").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        compose.onNodeWithTag("manual-institutions").performScrollTo().assertIsDisplayed()
    }

    private fun fillContextualTemplate() {
        awaitEnabledControl("work-time-start")
        compose.onNodeWithTag("work-time-start").performScrollTo().performTextInput("1015")
        compose.onNodeWithTag("work-time-end").performScrollTo().performTextInput("1845")
        compose.onNodeWithText("Elegir color").performScrollTo().performClick()
        compose.onNodeWithText("Usar color").performClick()
        compose.onNodeWithText("Usar color").assertDoesNotExist()
    }

    private fun awaitEnabledControl(tag: String) {
        if (tag == "manual-note" || tag == "manual-note-images") expandNoteEditorIfNeeded()
        // Room/recovery may expose a disabled editor before its asynchronous check completes.
        // Await the same semantic enablement required by text input, not merely its presence.
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.let {
                !it.config.contains(SemanticsProperties.Disabled)
            } == true
        }
        compose.onNodeWithTag(tag).assertIsEnabled()
    }

    private fun expandNoteEditorIfNeeded() {
        if (compose.onAllNodesWithTag("manual-note").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("manual-add-note").performScrollTo().performClick()
        }
    }

    private fun awaitSelectedControl(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.let {
                it.config.getOrElse(SemanticsProperties.Selected) { false }
            } == true
        }
        compose.onNodeWithTag(tag).assertIsSelected()
    }

    private fun awaitCreatedTemplate(): WorkTemplate {
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.size == 2 }
        }
        val template = runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.single { it.id != TEMPLATE_ID } }
        awaitEnabledControl("manual-institutions")
        return template
    }

    private fun assertLoadPreserved(dates: Set<LocalDate>, templateId: UUID) {
        assertSelectedTemplate(templateId)
        requireNotNull(scenario).onActivity {
            val load = ViewModelProvider(it)[V2ManualShiftLoadViewModel::class.java].uiState.value
            val calendar = ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value
            assertEquals(templateId, load.selectedTemplateId)
            assertEquals(dates, load.selectedDates)
            assertEquals(POSITION, load.position)
            assertEquals(YearMonth.from(selectedDate), calendar.visibleMonth)
            assertEquals(dates, calendar.editSelectedDates)
            assertTrue(calendar.editSelectionConfirmed)
            assertEquals(WorkSetupSurface.NONE, ViewModelProvider(it)[WorkSetupViewModel::class.java].uiState.value.surface)
        }
    }

    private fun rotateAndVerifyDraft(
        requestedOrientation: Int,
        expectedConfigurationOrientation: Int,
    ) {
        requireNotNull(scenario).onActivity { activity ->
            activity.requestedOrientation = requestedOrientation
        }
        compose.waitUntil(WAIT_MILLIS) {
            var currentOrientation = Configuration.ORIENTATION_UNDEFINED
            runCatching {
                requireNotNull(scenario).onActivity { activity ->
                    currentOrientation = activity.resources.configuration.orientation
                }
            }
            currentOrientation == expectedConfigurationOrientation
        }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("manual-institutions").fetchSemanticsNodes().isNotEmpty()
        }
        assertDraftRestoredWithoutWrites()
        assertSelectedTemplate(TEMPLATE_ID)
        expandNoteEditorIfNeeded()
        compose.onNodeWithTag("manual-note").performScrollTo().assertTextContains(NOTE)
        compose.onNodeWithTag("v2-manual-position")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(POSITION)
    }

    private fun assertDraftRestoredWithoutWrites() {
        requireNotNull(scenario).onActivity { activity ->
            val load = ViewModelProvider(activity)[V2ManualShiftLoadViewModel::class.java].uiState.value
            val calendar = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
            assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, load.stage)
            assertEquals(setOf(selectedDate), load.selectedDates)
            assertEquals(TEMPLATE_ID, load.selectedTemplateId)
            assertEquals(POSITION, load.position)
            assertEquals(NOTE, load.noteBody)
            assertEquals(CalendarInteractionMode.EDIT, calendar.interactionMode)
            assertEquals(CalendarSelectionPurpose.MANUAL_LOAD, calendar.selectionPurpose)
            assertEquals(setOf(selectedDate), calendar.editSelectedDates)
            assertTrue(calendar.editSelectionConfirmed)
        }
        assertTrue(runBlocking {
            store().shifts.observeStartingBetween(selectedDate, selectedDate).first().isEmpty()
        })
    }

    private fun firstWorkSet(
        configuration: ResolvedWorkConfigurationRevision,
        timestamp: Instant,
    ): FirstWorkSet {
        val objective = Objective(
            id = OBJECTIVE_ID,
            fullName = "Centro ficticio integral",
            abbreviation = ABBREVIATION,
            address = null,
            note = null,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val place = WorkPlace(
            id = PLACE_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            objectiveId = OBJECTIVE_ID,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val type = WorkType.create(
            id = TYPE_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            rawName = "Turno habitual",
            timestamp = timestamp,
        )
        val template = WorkTemplate(
            id = TEMPLATE_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            workPlaceId = PLACE_ID,
            objectiveId = OBJECTIVE_ID,
            workTypeId = TYPE_ID,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
            colorArgb = 0xFF336699.toInt(),
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        return FirstWorkSet(
            objective = objective,
            workPlace = place,
            firstRuleRevision = WorkplaceRuleRevision(
                id = RULE_ID,
                timelineId = TIMELINE_ID,
                sector = WorkSector.NURSING,
                workPlaceId = PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                effectiveFrom = configuration.revision.effectiveFrom,
                rules = WorkplaceRules(
                    nightHours = NightHoursRule.Disabled,
                    weekend = WeekendRule.None,
                    holiday = HolidayRule(false, false),
                ),
                createdAt = timestamp,
            ),
            configurationContext = configuration,
            workType = type,
            workTemplate = template,
        )
    }

    private companion object {
        const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS: Long = 10_000L
        const val ABBREVIATION: String = "INT"
        const val POSITION: String = "Puesto ficticio integral"
        const val NOTE: String = "Nota ficticia preservada al recrear"
        val TIMELINE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
        val REVISION_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000002")
        val OBJECTIVE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000004")
        val TYPE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000006")
        val RULE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000007")
    }
}
