package com.blackatsystems.miguardia

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
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido Activity del plan recurrente; sólo limpia y abre el paquete QA. */
class V2RecurringPlanActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var startDate: LocalDate

    @Before
    fun prepareFreshQaFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba recurrente sólo puede ejecutarse contra el paquete QA."
        }

        startDate = LocalDate.now(AppDefaults.zoneId())
        val timestamp = Instant.now()
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            val revision = EffectiveRevision(
                id = REVISION_ID,
                effectiveFrom = startDate,
                value = WorkConfiguration(
                    sector = WorkSector.NURSING,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            )
            store.workConfiguration.createInitial(TIMELINE_ID, revision)
            val configuration = ResolvedWorkConfigurationRevision.resolve(
                history = requireNotNull(store.workConfiguration.get()),
                date = startDate,
            )
            store.workCatalog.createFirstWorkSet(firstWorkSet(configuration, timestamp))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("calendar-v2-repeat-shifts").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun draftSurvivesRecreationAndMultiMonthPlanReturnsReactivelyToCalendar() {
        compose.onNodeWithTag("calendar-v2-repeat-shifts").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-recurring-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-recurring-template-$TEMPLATE_ID").performScrollTo().performClick()
        compose.onNodeWithTag("v2-recurring-position").performScrollTo().performTextInput(POSITION)

        requireNotNull(scenario).recreate()

        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-recurring-template-$TEMPLATE_ID").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-recurring-template-$TEMPLATE_ID").assertIsSelected()
        compose.onNodeWithText(POSITION).assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-review").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("v2-recurring-save", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("v2-recurring-exact-dates").assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-save", useUnmergedTree = true).performScrollTo().performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking {
                store.recurringPlans.observePlans(TIMELINE_ID, WorkSector.NURSING).first().size == 1
            }
        }
        val aggregate = runBlocking {
            store.recurringPlans.observePlans(TIMELINE_ID, WorkSector.NURSING).first().single()
        }
        val occurrenceDates = aggregate.occurrences.map { it.localDate }
        assertTrue(occurrenceDates.any { it.month != startDate.month })
        assertTrue(aggregate.occurrences.all { it.state == RecurringOccurrenceState.AUTOMATIC })
        aggregate.occurrences.forEach { occurrence ->
            val shiftId = requireNotNull(occurrence.shiftId)
            assertNotNull(runBlocking { store.v2Shifts.getWorkSnapshot(shiftId) })
        }
        assertEquals(startDate.plusMonths(1), aggregate.latestRevision.endDateInclusive)

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
    }

    private fun firstWorkSet(
        configuration: ResolvedWorkConfigurationRevision,
        timestamp: Instant,
    ): FirstWorkSet {
        val objective = Objective(
            id = OBJECTIVE_ID,
            fullName = "Centro ficticio recurrente",
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
            rawName = "Turno recurrente",
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
                effectiveFrom = startDate,
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
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 10_000L
        const val ABBREVIATION = "REC"
        const val POSITION = "Puesto ficticio recurrente"
        val TIMELINE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000001")
        val REVISION_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000002")
        val OBJECTIVE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000004")
        val TYPE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000006")
        val RULE_ID: UUID = UUID.fromString("96000000-0000-0000-0000-000000000007")
    }
}
