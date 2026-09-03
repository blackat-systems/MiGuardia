package com.blackatsystems.miguardia

import android.app.KeyguardManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recreación V2Ready sobre un fixture que limpia únicamente la base QA. */
class V2ReadyCalendarRecreationActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var selectedDate: LocalDate
    private lateinit var selectedMonth: YearMonth

    @Before
    fun prepareIsolatedV2ReadyFixture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La recreación V2Ready sólo puede probarse contra el paquete QA."
        }
        markOnboardingCompletedForTest()
        check(
            context.getSharedPreferences(
                MainActivity.DISPLAY_PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            ).edit()
                .putInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.STANDARD.percent)
                .commit(),
        ) {
            "La recreación debe comenzar con el zoom interno estándar."
        }
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        device.waitForIdle()
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )

        selectedMonth = YearMonth.now(AppDefaults.zoneId()).plusMonths(1)
        selectedDate = selectedMonth.atDay(10)
        val shift = fixtureShift(selectedDate)
        val todayHistory = fixtureShift(LocalDate.now(AppDefaults.zoneId())).copy(
            id = TODAY_HISTORY_SHIFT_ID,
            objectiveNameSnapshot = TODAY_HISTORY_NAME,
            objectiveAbbreviationSnapshot = "HST",
            status = ShiftStatus.CANCELLED,
        )
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            check(store.workConfiguration.get() == null) {
                "La preparación QA debe dejar una base sin configuración."
            }
            store.v2Shifts.insert(
                V2AppTestFixture.writeFor(
                    store = store,
                    shift = todayHistory,
                    effectiveFrom = todayHistory.localStartDate,
                ),
            )
            store.v2Shifts.insert(
                V2AppTestFixture.writeFor(
                    store = store,
                    shift = shift,
                    effectiveFrom = selectedDate,
                ),
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("calendar-v2-load-shifts")
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun recreationKeepsTheVisibleMonthAndOpenDayDetailInV2Ready() {
        waitForTag("today-card-toggle")
        compose.onNodeWithTag("today-card-toggle").performClick()
        waitForText("Ocultar jornadas de hoy")
        compose.onNodeWithTag("today-card-shift-$TODAY_HISTORY_SHIFT_ID").assertIsDisplayed()

        compose.onNodeWithContentDescription("Mes siguiente").performScrollTo().performClick()
        waitForText(selectedMonth.displayName())
        waitForTag("day-$selectedDate")
        compose.onNodeWithTag("day-$selectedDate").performScrollTo().performClick()
        waitForText(SHIFT_IDENTITY)

        requireNotNull(scenario).recreate()

        waitForText(SHIFT_IDENTITY)
        compose.onNodeWithText(selectedDate.fullDisplayName()).assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForText(selectedMonth.displayName())
        compose.onNodeWithText(selectedMonth.displayName()).assertIsDisplayed()
        compose.onNodeWithTag("day-$selectedDate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ocultar jornadas de hoy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("today-card-shift-$TODAY_HISTORY_SHIFT_ID").assertIsDisplayed()
    }

    @Test
    fun notificationShiftActionResolvesTheV2PairAndOpensItsOwnerDate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario?.close()
        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_VIEW_SHIFT)
                .putExtra(MainActivity.EXTRA_SHIFT_ID, SHIFT_ID.toString()),
        )

        waitForText(SHIFT_IDENTITY)
        compose.onNodeWithText(selectedDate.fullDisplayName()).assertIsDisplayed()
        compose.onNodeWithTag("shift-notifications-$SHIFT_ID")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun replayFromHelpKeepsItsStepAcrossActivityRecreationAndReturnsToHelp() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
        waitForTag("help-screen")
        compose.onNodeWithTag("help-repeat-tour").performScrollTo().performClick()
        waitForTag("help-introduction-1")
        compose.onNodeWithTag("help-introduction-next").performScrollTo().performClick()
        waitForTag("help-introduction-2")

        requireNotNull(scenario).recreate()

        waitForTag("help-introduction-2")
        compose.onNodeWithTag("help-replay-close").performScrollTo().performClick()
        waitForTag("help-screen")
    }

    @Test
    fun automaticGuideRunsOnceAfterSetupAndDoesNotReturnAfterCompletion() {
        scenario?.close()
        scenario = null
        resetOnboardingForTest()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("help-introduction-1")

        repeat(3) { index ->
            compose.onNodeWithTag("help-introduction-next").performScrollTo().performClick()
            if (index < 2) waitForTag("help-introduction-${index + 2}")
        }
        HelpTourStep.entries.forEachIndexed { index, step ->
            waitForTag("help-tour-${step.name.lowercase()}")
            compose.onNodeWithTag("help-tour-next").performScrollTo().performClick()
            if (index < HelpTourStep.entries.lastIndex) compose.waitForIdle()
        }

        waitForTag("calendar-v2-load-shifts")
        compose.onNodeWithTag("help-introduction-1").assertDoesNotExist()

        requireNotNull(scenario).recreate()

        waitForTag("calendar-v2-load-shifts")
        compose.onNodeWithTag("help-introduction-1").assertDoesNotExist()
    }

    @Test
    fun notificationDestinationWaitsForAutomaticGuideAndOpensAfterSkip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario?.close()
        scenario = null
        resetOnboardingForTest()

        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_VIEW_SHIFT)
                .putExtra(MainActivity.EXTRA_SHIFT_ID, SHIFT_ID.toString()),
        )

        waitForTag("help-introduction-1")
        compose.onNodeWithText(SHIFT_IDENTITY).assertDoesNotExist()
        compose.onNodeWithTag("help-skip").performScrollTo().performClick()
        compose.onNodeWithTag("help-confirm-skip").performClick()

        waitForText(SHIFT_IDENTITY)
        compose.onNodeWithText(selectedDate.fullDisplayName()).assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun fixtureShift(date: LocalDate): Shift {
        val zone = AppDefaults.zoneId()
        val start = date.atTime(8, 0).atZone(zone)
        return Shift(
            id = SHIFT_ID,
            startAt = start.toInstant(),
            endAt = start.plusHours(8).toInstant(),
            zoneId = zone,
            localStartDate = date,
            objectiveNameSnapshot = SHIFT_NAME,
            objectiveAbbreviationSnapshot = SHIFT_ABBREVIATION,
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = "Puesto ficticio de recreación",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }

    private fun YearMonth.displayName(): String {
        val locale = Locale.forLanguageTag("es-AR")
        val monthName = month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) }
        return "$monthName de $year"
    }

    private fun LocalDate.fullDisplayName(): String {
        val locale = Locale.forLanguageTag("es-AR")
        return format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    private companion object {
        const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS: Long = 15_000L
        const val SHIFT_NAME: String = "Lugar ficticio de recreación"
        const val SHIFT_ABBREVIATION: String = "RCV"
        const val SHIFT_IDENTITY: String = "$SHIFT_NAME ($SHIFT_ABBREVIATION)"
        const val TODAY_HISTORY_NAME: String = "Registro histórico ficticio de hoy"
        val SHIFT_ID: UUID = UUID.fromString("95000000-0000-0000-0000-000000000001")
        val TODAY_HISTORY_SHIFT_ID: UUID = UUID.fromString("95000000-0000-0000-0000-000000000002")
    }
}
