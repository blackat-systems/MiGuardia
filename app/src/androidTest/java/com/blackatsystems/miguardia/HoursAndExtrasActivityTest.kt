package com.blackatsystems.miguardia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido integral sintético; sólo se ejecuta contra QA con autorización expresa. */
class HoursAndExtrasActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var fixtureShift: Shift
    private lateinit var templateLabel: String

    @Before
    fun prepareSyntheticV2Sources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral de extras independientes sólo puede ejecutarse contra el paquete QA."
        }
        val zone = AppDefaults.zoneId()
        val end = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(2, ChronoUnit.MINUTES)
        val start = end.minus(1, ChronoUnit.HOURS)
        val timestamp = start.minus(1, ChronoUnit.DAYS)
        val localStart = start.atZone(zone)
        val localEnd = end.atZone(zone)
        fixtureShift = Shift(
            id = FIXTURE_SHIFT_ID,
            startAt = start,
            endAt = end,
            zoneId = zone,
            localStartDate = localStart.toLocalDate(),
            objectiveNameSnapshot = "Hospital ficticio extras",
            objectiveAbbreviationSnapshot = "HFE",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = localStart.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            endTimeSnapshot = localEnd.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = "Puesto inicial ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        templateLabel = "${fixtureShift.startTimeSnapshot}–${fixtureShift.endTimeSnapshot}"
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            V2AppTestFixture.writeFor(store, fixtureShift, fixtureShift.localStartDate)
            val extraClass = ExtraWorkClass.create(
                id = EXTRA_CLASS_ID,
                timelineId = V2AppTestFixture.TIMELINE_ID,
                sector = WorkSector.NURSING,
                name = "Refuerzo ficticio",
                helpsMeetHoursReference = true,
                showDedicatedSummary = true,
                timestamp = timestamp,
            )
            assertTrue(
                store.shiftActuals.saveExtraWorkClass(null, extraClass) is ExtraWorkClassWriteResult.Saved,
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        selectFixtureDay()
        waitForTag("register-independent-extra-${fixtureShift.localStartDate}")
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun createRecreateCorrectAndDeleteReturnsToTheSameCalendarDetail() {
        compose.onNodeWithTag("register-independent-extra-${fixtureShift.localStartDate}")
            .performScrollTo()
            .performClick()
        waitForTag("extra-review")
        compose.onNodeWithText(templateLabel).performScrollTo().performClick()
        compose.onNodeWithTag("extra-position").performScrollTo().performTextInput(FIRST_POSITION)

        requireNotNull(scenario).recreate()
        waitForTag("extra-position")
        compose.onNodeWithTag("extra-position").assertTextContains(FIRST_POSITION)
        compose.onNodeWithTag("extra-review").performScrollTo().performClick()
        waitForTag("extra-save")
        compose.onNodeWithText("Trabajo extra independiente").assertIsDisplayed()
        compose.onNodeWithText("Clase: Refuerzo ficticio").assertIsDisplayed()
        requireNotNull(scenario).recreate()
        waitForTag("extra-save")
        compose.onNodeWithText("Clase: Refuerzo ficticio").assertIsDisplayed()
        compose.onNodeWithTag("extra-save").performScrollTo().performClick()

        val store = qaStore()
        compose.waitUntil(WAIT_MILLIS) { runBlocking { storedExtras().size == 1 } }
        val created = runBlocking { storedExtras().single() }
        assertEquals(FIRST_POSITION, created.snapshot.position)
        waitForTag("independent-extra-${created.id}")
        compose.onNodeWithTag("correct-independent-extra-${created.id}").performScrollTo().performClick()

        waitForTag("extra-position")
        compose.onNodeWithTag("extra-position").performScrollTo().performTextClearance()
        compose.onNodeWithTag("extra-position").performTextInput(CORRECTED_POSITION)
        compose.onNodeWithTag("extra-review").performScrollTo().performClick()
        waitForTag("extra-save")
        compose.onNodeWithTag("extra-save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { storedExtras().singleOrNull()?.snapshot?.position == CORRECTED_POSITION }
        }

        waitForTag("independent-extra-${created.id}")
        compose.onNodeWithTag("delete-independent-extra-${created.id}").performScrollTo().performClick()
        waitForTag("extra-delete-confirm")
        compose.onNodeWithTag("extra-delete-confirm").performClick()
        compose.waitUntil(WAIT_MILLIS) { runBlocking { storedExtras().isEmpty() } }
        assertTrue(runBlocking { store.independentExtraWork.get(created.id) == null })
        waitForTag("register-independent-extra-${fixtureShift.localStartDate}")
    }

    @Test
    fun reviewedReferenceDraftSurvivesRecreationAndPersistsTheChosenFutureRestart() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForTag("main-menu-button")
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("drawer-action-work-setup").performClick()
        waitForTag("work-setup-hours-progress")
        compose.onNodeWithTag("work-setup-hours-progress").performScrollTo().performClick()
        waitForTag("hours-reference-configure")
        compose.onNodeWithTag("hours-reference-configure").performScrollTo().performClick()
        compose.onNodeWithTag("hours-reference-fixed").performClick()
        compose.onNodeWithText("Mes calendario").performScrollTo().performClick()
        compose.onNodeWithTag("hours-fixed-minutes").performScrollTo().performTextInput("6000")
        compose.onNodeWithTag("hours-reference-next-period")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("hours-reference-review").performScrollTo().performClick()
        waitForTag("hours-reference-save")

        requireNotNull(scenario).recreate()
        waitForTag("hours-reference-save")
        compose.onNodeWithText("Referencia fija").assertIsDisplayed()
        compose.onNodeWithTag("hours-reference-save").performScrollTo().performClick()

        val today = LocalDate.now(AppDefaults.zoneId())
        val expectedStart = today.withDayOfMonth(1).plusMonths(1)
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking {
                qaStore().workConfiguration.get()?.timeline?.valueAt(expectedStart)?.hoursReference ==
                    HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
            }
        }
        val saved = runBlocking {
            requireNotNull(qaStore().workConfiguration.get()).timeline.valueAt(expectedStart)
        }
        assertEquals(expectedStart, saved?.hoursReferenceStartedOn)
    }

    private fun selectFixtureDay() {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("month-grid").fetchSemanticsNodes().isNotEmpty()
        }
        val selectedMonth = YearMonth.from(fixtureShift.localStartDate)
        val currentMonth = YearMonth.now(AppDefaults.zoneId())
        if (selectedMonth.isBefore(currentMonth)) {
            val previous = InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.previous_month)
            compose.onNodeWithContentDescription(previous).performClick()
        }
        val regular = "day-${fixtureShift.localStartDate}"
        val completed = "completed-day-${fixtureShift.localStartDate}"
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(completed).fetchSemanticsNodes().isNotEmpty()
        }
        val tag = if (compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty()) {
            regular
        } else {
            completed
        }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private suspend fun storedExtras() = qaStore().independentExtraWork
        .observeOn(V2AppTestFixture.TIMELINE_ID, WorkSector.NURSING, fixtureShift.localStartDate)
        .first()

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun qaStore() = (
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
        ).localDataStore

    private companion object {
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 15_000L
        const val FIRST_POSITION = "Puesto extra ficticio"
        const val CORRECTED_POSITION = "Puesto corregido ficticio"
        val FIXTURE_SHIFT_ID: UUID = UUID.fromString("f2200000-0000-0000-0000-000000000001")
        val EXTRA_CLASS_ID: UUID = UUID.fromString("f2200000-0000-0000-0000-000000000002")
    }
}
