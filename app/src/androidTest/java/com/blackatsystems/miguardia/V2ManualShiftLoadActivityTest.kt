package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end QA fixture for NEW_V2. Run this class by itself after uninstalling
 * the QA package; the historical V1 activity fixtures intentionally use a
 * separate clean installation.
 */
class V2ManualShiftLoadActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var selectedDate: LocalDate
    private var originalRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Before
    fun prepareFreshV2Fixture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral V2 sólo puede ejecutarse contra el paquete QA."
        }
        UiDevice.getInstance(instrumentation).wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()

        selectedDate = LocalDate.now(AppDefaults.zoneId())
        val timestamp = Instant.now()
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        runBlocking {
            check(store.workConfiguration.get() == null) {
                "La prueba integral V2 requiere una instalación QA limpia."
            }
            val revision = EffectiveRevision(
                id = REVISION_ID,
                effectiveFrom = selectedDate,
                value = WorkConfiguration(
                    sector = WorkSector.NURSING,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            )
            store.workConfiguration.createInitial(TIMELINE_ID, revision)
            val configuration = ResolvedWorkConfigurationRevision.resolve(
                history = requireNotNull(store.workConfiguration.get()),
                date = selectedDate,
            )
            store.workCatalog.createFirstWorkSet(firstWorkSet(configuration, timestamp))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        requireNotNull(scenario).onActivity { activity ->
            originalRequestedOrientation = activity.requestedOrientation
        }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("calendar-v2-load-shifts").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun closeActivity() {
        scenario?.onActivity { activity ->
            activity.requestedOrientation = originalRequestedOrientation
        }
        scenario?.close()
        scenario = null
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
        compose.onNodeWithTag("v2-manual-review").performScrollTo().performClick()
        compose.onNodeWithTag("v2-manual-save").performScrollTo().performClick()

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
        assertNull(saved.sourceScheduleCombinationId)

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
            .assertIsDisplayed()
        compose.onNodeWithText("¿En qué rubro trabajás?").assertDoesNotExist()
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
            legacyScheduleCombinationId = null,
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
                effectiveFrom = selectedDate,
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
