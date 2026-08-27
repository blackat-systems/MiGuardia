package com.blackatsystems.miguardia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido sintético; sólo se ejecuta contra QA con autorización expresa. */
class SummaryActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun prepareSyntheticSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "El Resumen integral sólo puede probarse contra el paquete QA."
        }
        val zoneId = AppDefaults.zoneId()
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val start = YearMonth.from(now.atZone(zoneId)).atDay(1).atStartOfDay(zoneId)
        val end = start.plusHours(2)
        val shift = Shift(
            id = SHIFT_ID,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = zoneId,
            localStartDate = start.toLocalDate(),
            objectiveNameSnapshot = "Lugar ficticio Resumen",
            objectiveAbbreviationSnapshot = "LFR",
            objectiveAddressSnapshot = "Dirección privada ficticia",
            startTimeSnapshot = start.toLocalTime(),
            endTimeSnapshot = end.toLocalTime(),
            colorArgbSnapshot = 0xff336699.toInt(),
            position = "Puesto privado ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = start.minusDays(1).toInstant(),
            updatedAt = start.minusDays(1).toInstant(),
        )
        val application = context.applicationContext as MiGuardiaApplication
        application.localDataStore.clearAllDataForInstrumentation()
        runBlocking {
            application.localDataStore.v2Shifts.insert(
                V2AppTestFixture.writeFor(application.localDataStore, shift, shift.localStartDate),
            )
            application.summaryPreferences.setVisible(SummaryOptionalFamily.WORK_PLACES, true)
            application.summaryPreferences.markIntroSeen()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("main-menu-button")
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun detailMonthAndPersonalizationSurviveRecreationAndBackReturnsToCalendar() {
        openSummary()
        waitForTag("summary-metric-essential-total")
        compose.onNodeWithTag("summary-metric-essential-total").performScrollTo().performClick()
        waitForTag("summary-detail")
        compose.onNodeWithText("Dirección privada ficticia").assertDoesNotExist()
        compose.onNodeWithText("Puesto privado ficticio").assertDoesNotExist()

        requireNotNull(scenario).recreate()
        waitForTag("summary-detail")
        compose.onNodeWithTag("summary-detail").assertIsDisplayed()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForTag("summary-overview")
        compose.onNodeWithTag("summary-next-month").performScrollTo().performClick()
        waitForTag("summary-empty")
        requireNotNull(scenario).recreate()
        waitForTag("summary-empty")

        compose.onNodeWithTag("summary-menu").performClick()
        compose.onNodeWithTag("summary-menu-personalize").performClick()
        waitForTag("summary-personalization")
        compose.onNodeWithTag("summary-toggle-work_places").performScrollTo().performClick()
        requireNotNull(scenario).recreate()
        waitForTag("summary-personalization")
        compose.onNodeWithTag("summary-toggle-work_places").assertIsOff()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForTag("summary-overview")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForTag("month-grid")
    }

    private fun openSummary() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-summary").performScrollTo().performClick()
        waitForTag("summary-overview")
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 10_000L
        val SHIFT_ID: UUID = UUID.fromString("fa000000-0000-0000-0000-000000000001")
    }
}
