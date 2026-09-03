package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido integral aislado; sólo se ejecuta contra el paquete QA cuando MAIN lo autorice. */
class V2ShiftActualActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var fixtureShift: Shift
    private var originalRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Before
    fun prepareFreshCompletedV2Shift() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral de horario real sólo puede ejecutarse contra el paquete QA."
        }
        markOnboardingCompletedForTest()
        val zone = AppDefaults.zoneId()
        val end = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(2, ChronoUnit.MINUTES)
        val start = end.minus(1, ChronoUnit.HOURS)
        val localStart = start.atZone(zone)
        val localEnd = end.atZone(zone)
        val timestamp = end.minus(1, ChronoUnit.DAYS)
        fixtureShift = Shift(
            id = SHIFT_ID,
            startAt = start,
            endAt = end,
            zoneId = zone,
            localStartDate = localStart.toLocalDate(),
            objectiveNameSnapshot = "Hospital ficticio horario real",
            objectiveAbbreviationSnapshot = "HFR",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = localStart.toLocalTime(),
            endTimeSnapshot = localEnd.toLocalTime(),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = "Puesto ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            val write = V2AppTestFixture.writeFor(store, fixtureShift, fixtureShift.localStartDate)
            store.v2Shifts.insert(write)
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        requireNotNull(scenario).onActivity { activity ->
            originalRequestedOrientation = activity.requestedOrientation
        }
        waitForCalendarMonthAndSelectFixtureDay()
        waitForTag("v2-actual-$SHIFT_ID-register")
    }

    @After
    fun closeActivity() {
        scenario?.onActivity { activity -> activity.requestedOrientation = originalRequestedOrientation }
        scenario?.close()
        scenario = null
    }

    @Test
    fun registerRecreatePersistReopenAndReturnToPlannedEndToEnd() {
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-register").performScrollTo().performClick()
        waitForTag("v2-actual-editor-$SHIFT_ID")
        compose.onNodeWithText("Jornada 1 de 1").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-next-save").performScrollTo().performClick()

        val correctedStart = fixtureShift.startAt.plus(5, ChronoUnit.MINUTES)
            .atZone(fixtureShift.zoneId)
            .toLocalTime()
            .toString()
        waitForTag("v2-actual-start-time")
        compose.onNodeWithTag("v2-actual-start-time").performTextClearance()
        compose.onNodeWithTag("v2-actual-start-time").performTextInput(correctedStart)
        compose.onNodeWithTag("v2-actual-reason").performTextInput(REASON)

        rotateAndVerifyDraft(
            requested = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            expected = Configuration.ORIENTATION_LANDSCAPE,
            correctedStart = correctedStart,
        )
        rotateAndVerifyDraft(
            requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            expected = Configuration.ORIENTATION_PORTRAIT,
            correctedStart = correctedStart,
        )

        requireNotNull(scenario).recreate()
        waitForTag("v2-actual-start-time")
        compose.onNodeWithTag("v2-actual-start-time").assertTextContains(correctedStart)
        compose.onNodeWithTag("v2-actual-reason").assertTextContains(REASON)
        compose.onNodeWithTag("v2-actual-next-save").performScrollTo().performClick()
        compose.onNodeWithTag("v2-actual-next-save").performScrollTo().performClick()

        val store = qaStore()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store.shiftActuals.getExpectation(SHIFT_ID)?.previousActual != null }
        }
        val stored = runBlocking {
            requireNotNull(store.shiftActuals.getExpectation(SHIFT_ID)?.previousActual)
        }
        assertEquals(REASON, stored.record.differenceReason)
        assertEquals(55L, stored.totalMinutes)
        assertEquals(55L, stored.regularMinutes)

        waitForTag("v2-actual-$SHIFT_ID-correct")
        requireNotNull(scenario).close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForCalendarMonthAndSelectFixtureDay()
        waitForTag("v2-actual-$SHIFT_ID-return-planned")
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-return-planned").performScrollTo().performClick()
        waitForTag("v2-actual-confirm-return-planned")
        compose.onNodeWithTag("v2-actual-confirm-return-planned").performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { store.shiftActuals.getExpectation(SHIFT_ID)?.previousActual == null }
        }
        assertNull(runBlocking { store.shiftActuals.getExpectation(SHIFT_ID)?.previousActual })
        waitForTag("v2-actual-$SHIFT_ID-register")
    }

    private fun waitForCalendarMonthAndSelectFixtureDay() {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag("month-grid").fetchSemanticsNodes().isNotEmpty()
        }
        val selectedMonth = YearMonth.from(fixtureShift.localStartDate)
        val currentMonth = YearMonth.now(AppDefaults.zoneId())
        if (selectedMonth.isBefore(currentMonth)) {
            val previous = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.previous_month)
            compose.onNodeWithContentDescription(previous).performClick()
        }
        val dayTag = waitForCalendarDay()
        compose.onNodeWithTag(dayTag).performScrollTo().performClick()
    }

    private fun waitForCalendarDay(): String {
        val regular = "day-${fixtureShift.localStartDate}"
        val completed = "completed-day-${fixtureShift.localStartDate}"
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(completed).fetchSemanticsNodes().isNotEmpty()
        }
        return if (compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty()) regular else completed
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun rotateAndVerifyDraft(requested: Int, expected: Int, correctedStart: String) {
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
        waitForTag("v2-actual-start-time")
        compose.onNodeWithTag("v2-actual-start-time")
            .performScrollTo()
            .assertTextContains(correctedStart)
        compose.onNodeWithTag("v2-actual-reason")
            .performScrollTo()
            .assertTextContains(REASON)
    }

    private fun qaStore() = (
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
        ).localDataStore

    private companion object {
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 15_000L
        const val REASON = "Salida anticipada ficticia"
        val SHIFT_ID: UUID = UUID.fromString("f2100000-0000-0000-0000-000000000001")
    }
}
