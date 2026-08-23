package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Recorrido integral NEW_V2. Esta clase requiere una instalación QA limpia y
 * nunca debe ejecutarse contra el applicationId de producción.
 */
class V2ShiftEditActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var selectedDate: LocalDate
    private var originalRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Before
    fun prepareFreshV2FixtureWithTwoJourneys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral de edición sólo puede ejecutarse contra el paquete QA."
        }
        UiDevice.getInstance(instrumentation).wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()

        selectedDate = LocalDate.now(AppDefaults.zoneId())
        val timestamp = Instant.now().minusSeconds(10)
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        runBlocking {
            check(store.workConfiguration.get() == null) {
                "La prueba integral de edición V2 requiere una instalación QA limpia."
            }
            val revision = EffectiveRevision(
                REVISION_ID,
                selectedDate,
                WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
            )
            store.workConfiguration.createInitial(TIMELINE_ID, revision)
            val configuration = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                selectedDate,
            )
            val firstSet = firstWorkSet(configuration, timestamp)
            store.workCatalog.createFirstWorkSet(firstSet)
            val alternative = alternativeTemplate(timestamp)
            store.workCatalog.createWorkTemplate(alternative)
            store.v2Shifts.insert(
                buildV2ShiftWrite(
                    FIRST_SHIFT_ID,
                    selectedDate,
                    firstSet.objective,
                    firstSet.workPlace,
                    firstSet.workType,
                    firstSet.workTemplate,
                    configuration,
                    INITIAL_POSITION,
                    timestamp,
                    AppDefaults.zoneId(),
                ),
            )
            store.v2Shifts.insert(
                buildV2ShiftWrite(
                    SECOND_SHIFT_ID,
                    selectedDate,
                    firstSet.objective,
                    firstSet.workPlace,
                    firstSet.workType,
                    firstSet.workTemplate,
                    configuration,
                    "Compañera ficticia",
                    timestamp.plusSeconds(1),
                    AppDefaults.zoneId(),
                ),
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        requireNotNull(scenario).onActivity { activity ->
            originalRequestedOrientation = activity.requestedOrientation
        }
        waitForTag("day-$selectedDate")
    }

    @After
    fun closeActivity() {
        scenario?.onActivity { activity -> activity.requestedOrientation = originalRequestedOrientation }
        scenario?.close()
        scenario = null
    }

    @Test
    fun editRecreateChangeTemplateCancelDeleteAndDeleteOneJourneyEndToEnd() {
        compose.onNodeWithTag("day-$selectedDate").performScrollTo()
        compose.onNodeWithText("2 turnos", useUnmergedTree = true).assertExists()
        openDayActions()
        compose.onNodeWithTag("v2-edit-shift-$FIRST_SHIFT_ID").performScrollTo().performClick()
        waitForTag("v2-shift-edit-position")
        compose.onNodeWithTag("v2-shift-fixed-date").assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-edit-position").performTextClearance()
        compose.onNodeWithTag("v2-shift-edit-position").performTextInput(EDITED_POSITION)

        requireNotNull(scenario).recreate()
        waitForTag("v2-shift-edit-position")
        compose.onNodeWithTag("v2-shift-edit-position").assertTextContains(EDITED_POSITION)
        rotateAndVerifyDraft(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        rotateAndVerifyDraft(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        compose.onNodeWithTag("v2-shift-edit-back").performClick()
        compose.onNodeWithTag("v2-shift-discard-dialog").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        saveCurrentDraftWithExpectedWarning()

        val store = qaStore()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store.shifts.getById(FIRST_SHIFT_ID)?.position == EDITED_POSITION }
        }
        val firstAfterPosition = runBlocking { requireNotNull(store.shifts.getById(FIRST_SHIFT_ID)) }
        assertEquals(selectedDate, firstAfterPosition.localStartDate)
        assertNotNull(runBlocking { store.v2Shifts.getWorkSnapshot(FIRST_SHIFT_ID) })
        assertNotNull(runBlocking { store.shifts.getById(SECOND_SHIFT_ID) })

        openDayActions()
        compose.onNodeWithTag("v2-edit-shift-$FIRST_SHIFT_ID").performScrollTo().performClick()
        waitForTag("v2-shift-template-$ALTERNATIVE_TEMPLATE_ID")
        compose.onNodeWithTag("v2-shift-template-$ALTERNATIVE_TEMPLATE_ID").performScrollTo().performClick()
        saveCurrentDraftWithExpectedWarning()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store.v2Shifts.getWorkSnapshot(FIRST_SHIFT_ID)?.templateId == ALTERNATIVE_TEMPLATE_ID }
        }
        val firstAfterTemplate = runBlocking { requireNotNull(store.shifts.getById(FIRST_SHIFT_ID)) }
        assertEquals(LocalTime.of(18, 0), firstAfterTemplate.startTimeSnapshot)
        assertEquals(LocalTime.of(23, 0), firstAfterTemplate.endTimeSnapshot)
        assertEquals(selectedDate, firstAfterTemplate.localStartDate)

        openDayActions()
        compose.onNodeWithTag("v2-delete-shift-$SECOND_SHIFT_ID").performScrollTo().performClick()
        compose.onNodeWithTag("v2-shift-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("Conservar jornada").performClick()
        assertNotNull(runBlocking { store.shifts.getById(SECOND_SHIFT_ID) })
        compose.onNodeWithTag("v2-delete-shift-$SECOND_SHIFT_ID").performScrollTo().performClick()
        compose.onNodeWithTag("v2-shift-confirm-delete").performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store.shifts.getById(SECOND_SHIFT_ID) == null }
        }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("2 turnos", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(
            compose.onAllNodesWithText(ABBREVIATION, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertEquals(V2ShiftLookup.Missing, runBlocking { store.v2Shifts.getShift(SECOND_SHIFT_ID) })
        assertNotNull(runBlocking { store.shifts.getById(FIRST_SHIFT_ID) })

        requireNotNull(scenario).close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("day-$selectedDate")
        openDayActions()
        compose.onNodeWithTag("v2-shift-row-$FIRST_SHIFT_ID").assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-row-$SECOND_SHIFT_ID").assertDoesNotExist()
        assertFalse(runBlocking { store.shifts.getById(FIRST_SHIFT_ID) == null })
    }

    private fun openDayActions() {
        waitForTag("day-$selectedDate")
        compose.onNodeWithTag("day-$selectedDate").performScrollTo().performClick()
        waitForTag("v2-edit-day-action")
        compose.onNodeWithTag("v2-edit-day-action").performClick()
        waitForTag("v2-shift-edit-surface")
    }

    private fun saveCurrentDraftWithExpectedWarning() {
        compose.onNodeWithTag("v2-shift-request-review").performScrollTo().performClick()
        waitForTag("v2-shift-warning-dialog")
        compose.onNodeWithTag("v2-shift-confirm-warnings").performClick()
        waitForTag("v2-shift-save")
        compose.onNodeWithTag("v2-shift-save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-shift-edit-surface").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun rotateAndVerifyDraft(requested: Int, expected: Int) {
        requireNotNull(scenario).onActivity { activity -> activity.requestedOrientation = requested }
        compose.waitUntil(WAIT_MILLIS) {
            var orientation = Configuration.ORIENTATION_UNDEFINED
            runCatching {
                requireNotNull(scenario).onActivity { activity ->
                    orientation = activity.resources.configuration.orientation
                }
            }
            orientation == expected
        }
        waitForTag("v2-shift-edit-position")
        compose.onNodeWithTag("v2-shift-edit-position").performScrollTo().assertTextContains(EDITED_POSITION)
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun qaStore() = (
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
        ).localDataStore

    private fun firstWorkSet(
        configuration: ResolvedWorkConfigurationRevision,
        timestamp: Instant,
    ): FirstWorkSet {
        val objective = Objective(
            OBJECTIVE_ID,
            "Hospital ficticio integral",
            ABBREVIATION,
            null,
            null,
            true,
            timestamp,
            timestamp,
        )
        val place = WorkPlace(
            PLACE_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            OBJECTIVE_ID,
            true,
            timestamp,
            timestamp,
        )
        val type = WorkType.create(TYPE_ID, TIMELINE_ID, WorkSector.NURSING, "Turno asistencial", timestamp)
        val template = WorkTemplate(
            TEMPLATE_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            LocalTime.of(8, 0),
            LocalTime.of(16, 0),
            0xFF336699.toInt(),
            true,
            null,
            timestamp,
            timestamp,
        )
        return FirstWorkSet(
            objective,
            place,
            WorkplaceRuleRevision(
                RULE_ID,
                TIMELINE_ID,
                WorkSector.NURSING,
                PLACE_ID,
                OBJECTIVE_ID,
                selectedDate,
                WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)),
                timestamp,
            ),
            configuration,
            type,
            template,
        )
    }

    private fun alternativeTemplate(timestamp: Instant) = WorkTemplate(
        ALTERNATIVE_TEMPLATE_ID,
        TIMELINE_ID,
        WorkSector.NURSING,
        PLACE_ID,
        OBJECTIVE_ID,
        TYPE_ID,
        LocalTime.of(18, 0),
        LocalTime.of(23, 0),
        0xFF884422.toInt(),
        true,
        null,
        timestamp,
        timestamp,
    )

    private companion object {
        const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS: Long = 15_000L
        const val ABBREVIATION: String = "HQA"
        const val INITIAL_POSITION: String = "Puesto inicial ficticio"
        const val EDITED_POSITION: String = "Puesto corregido ficticio"
        val TIMELINE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000001")
        val REVISION_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000002")
        val OBJECTIVE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000004")
        val TYPE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000006")
        val ALTERNATIVE_TEMPLATE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000007")
        val RULE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000008")
        val FIRST_SHIFT_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000009")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000010")
    }
}
