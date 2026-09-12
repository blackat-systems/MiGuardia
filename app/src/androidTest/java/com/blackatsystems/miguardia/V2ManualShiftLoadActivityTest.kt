package com.blackatsystems.miguardia

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
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
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import java.time.YearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            compose.onAllNodesWithTag("calendar-v2-load-shifts").fetchSemanticsNodes().isNotEmpty()
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
    fun draftSurvivesRecreationAndShiftSnapshotSurviveReopen() {
        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().performClick()
        compose.onNodeWithTag("day-$selectedDate").performScrollTo().performClick()
        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-template-$TEMPLATE_ID").performScrollTo().performClick()
        compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performTextInput(POSITION)

        requireNotNull(scenario).recreate()

        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-template-$TEMPLATE_ID").assertIsSelected()
        compose.onNodeWithText(POSITION).assertIsDisplayed()

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
        val otherDate = if (selectedDate.dayOfMonth == 1) selectedDate.plusDays(1) else selectedDate.withDayOfMonth(1)
        val dates = setOf(selectedDate, otherDate)
        enterContextualLoad(dates)
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
        captureContextualMatrix("return", listOf("v2-template-${template.id}", "v2-manual-save-direct"))
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
        val hadZoom = preferences.contains(MainActivity.APP_ZOOM_PERCENT)
        val previousZoom = preferences.getInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.STANDARD.percent)
        val hadTheme = preferences.contains(MainActivity.APP_THEME_MODE)
        val previousTheme = preferences.getString(MainActivity.APP_THEME_MODE, null)
        val device = UiDevice.getInstance(instrumentation)
        try {
            for (theme in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)) {
                for (zoom in AppZoom.entries) {
                    check(preferences.edit()
                        .putString(MainActivity.APP_THEME_MODE, theme.name)
                        .putInt(MainActivity.APP_ZOOM_PERCENT, zoom.percent)
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
                                "QA capture $stage/${theme.name}/${zoom.percent}: focus=$hasFocus, activePackage=$activePackage",
                                error,
                            )
                        }
                        check(qaWindowIsActive()) {
                            "No se captura una aplicación ajena al fixture QA"
                        }
                        val file = File(evidence, "$stage-${theme.name}-${zoom.percent}-$tag.png")
                        check(!file.exists() && device.takeScreenshot(file)) {
                            "No se pudo guardar la captura contextual propia"
                        }
                    }
                }
            }
        } finally {
            val editor = preferences.edit()
            if (hadZoom) editor.putInt(MainActivity.APP_ZOOM_PERCENT, previousZoom)
            else editor.remove(MainActivity.APP_ZOOM_PERCENT)
            if (hadTheme) editor.putString(MainActivity.APP_THEME_MODE, previousTheme)
            else editor.remove(MainActivity.APP_THEME_MODE)
            check(editor.commit()) { "No se pudo restaurar la apariencia interna del fixture" }
            requireNotNull(scenario).recreate()
            awaitEnabledControl(controlTags.first())
        }
    }

    private fun enterContextualLoad(dates: Set<LocalDate>) {
        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().performClick()
        dates.forEach {
            awaitEnabledControl("day-$it")
            compose.onNodeWithTag("day-$it").performScrollTo().performClick()
        }
        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag("v2-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("v2-template-$TEMPLATE_ID").performScrollTo().performClick()
        compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performTextInput(POSITION)
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
        // Room/recovery may expose a disabled editor before its asynchronous check completes.
        // Await the same semantic enablement required by text input, not merely its presence.
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.let {
                !it.config.contains(SemanticsProperties.Disabled)
            } == true
        }
        compose.onNodeWithTag(tag).assertIsEnabled()
    }

    private fun awaitCreatedTemplate(): WorkTemplate {
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.size == 2 }
        }
        val template = runBlocking { store().workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first().workTemplates.single { it.id != TEMPLATE_ID } }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag("v2-template-${template.id}").fetchSemanticsNodes().isNotEmpty() }
        return template
    }

    private fun assertLoadPreserved(dates: Set<LocalDate>, templateId: UUID) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag("v2-template-$templateId").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("v2-template-$templateId").performScrollTo().assertIsSelected()
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
            compose.onAllNodesWithTag("v2-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-template-$TEMPLATE_ID").assertIsSelected()
        compose.onNodeWithTag("v2-manual-position").performScrollTo()
        compose.onNodeWithText(POSITION).assertIsDisplayed()
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
        val TIMELINE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
        val REVISION_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000002")
        val OBJECTIVE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000004")
        val TYPE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000006")
        val RULE_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000007")
    }
}
