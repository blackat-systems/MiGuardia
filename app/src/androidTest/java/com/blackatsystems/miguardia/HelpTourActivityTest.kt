package com.blackatsystems.miguardia

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.ui.help.HelpSessionMode
import com.blackatsystems.miguardia.ui.help.HelpSessionStage
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.help.HelpViewModel
import com.blackatsystems.miguardia.ui.help.INTRODUCTION_STEP_COUNT
import com.blackatsystems.miguardia.ui.summary.SummaryPreferences
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** MainActivity, its real HelpViewModel and real navigation; only disposable QA data. */
class HelpTourActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as MiGuardiaApplication
    private val store get() = application.localDataStore
    private var scenario: ActivityScenario<MainActivity>? = null
    private var launchIntent: Intent? = null

    @Before
    fun prepareDisposableQaWithRealHelpCoordinator() {
        check(context.packageName == QA_APPLICATION_ID)
        check(!focusLostInProcess) { "An earlier focus loss stopped this instrumentation process" }
        assertQaFocus(allowOwnLifecycleTransition = true)
        assertFalse(
            "Leave the authorized QA device unlocked before running the tour",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )
        preparePublicRootAndMarkOnboardingCompletedForTest()
        check(context.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit().putInt(MainActivity.APP_ZOOM_PERCENT, 100)
            .putString(MainActivity.APP_THEME_MODE, AppThemeMode.LIGHT.name).commit())
        runBlocking {
            application.summaryPreferences.replacePortable(SummaryPreferences(introSeen = true))
            application.weatherPreferences.replacePortable(WeatherPreferences())
        }
        store.clearAllDataForInstrumentation()
        val date = LocalDate.now(AppDefaults.zoneId()).plusDays(1)
        val shift = fixtureShift(date)
        runBlocking {
            store.v2Shifts.insert(V2AppTestFixture.writeFor(store, shift, date))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        requireNotNull(scenario).onActivity {
            launchIntent = Intent(it.intent)
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        awaitQaFocusAfterLifecycleChange()
        compose.waitUntil(WAIT_MILLIS) {
            assertQaFocus()
            var portrait = false
            requireNotNull(scenario).onActivity {
                portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            portrait
        }
        waitForTag("month-grid")
    }

    @After
    fun closeOwnActivity() {
        scenario?.let { current ->
            if (current.state != Lifecycle.State.DESTROYED) {
                current.onActivity { activity ->
                    launchIntent?.let { activity.intent = Intent(it) }
                }
            }
            current.close()
        }
        scenario = null
        launchIntent = null
    }

    @Test
    fun replayFinishesAllRealTourStepsAndReturnsToHelpWithoutChangingData() {
        val before = snapshot()
        val onboardingBefore = runBlocking { application.onboardingPreferences.state.first() }
        openReplayThroughHelp()
        finishIntroduction()

        HelpTourStep.entries.forEach { step ->
            assertRealTourStep(step)
            click("help-tour-next")
        }

        assertReturnedToHelp()
        assertEquals(before, snapshot())
        assertEquals(onboardingBefore, runBlocking { application.onboardingPreferences.state.first() })
    }

    @Test
    fun recreationKeepsReplayStepAndBackThenCloseReturnsToHelpWithoutReentryOrWrites() {
        val before = snapshot()
        val onboardingBefore = runBlocking { application.onboardingPreferences.state.first() }
        openReplayThroughHelp()
        finishIntroduction()
        while (helpState().session?.tourStep != HelpTourStep.PHOTOS) {
            assertRealTourStep(requireNotNull(helpState().session?.tourStep))
            click("help-tour-next")
        }
        assertRealTourStep(HelpTourStep.PHOTOS)
        val sessionBefore = requireNotNull(helpState().session)
        var activityBefore: MainActivity? = null
        requireNotNull(scenario).onActivity { activityBefore = it }

        requireNotNull(scenario).recreate()
        awaitQaFocusAfterLifecycleChange()
        requireNotNull(scenario).onActivity { assertNotSame(activityBefore, it) }
        assertRealTourStep(HelpTourStep.PHOTOS)
        assertEquals(sessionBefore, helpState().session)
        assertEquals(before, snapshot())

        click("help-tour-back")
        assertRealTourStep(HelpTourStep.DAY_DETAIL)
        click("help-replay-close")
        assertReturnedToHelp()
        requireNotNull(scenario).recreate()
        awaitQaFocusAfterLifecycleChange()
        assertReturnedToHelp()
        assertEquals(before, snapshot())
        assertEquals(onboardingBefore, runBlocking { application.onboardingPreferences.state.first() })
    }

    private fun openReplayThroughHelp() {
        click("main-menu-button", scroll = false)
        click("main-destination-help")
        waitForTag("help-screen")
        click("help-repeat-tour")
        waitForTag("help-introduction-1")
        val session = requireNotNull(helpState().session)
        assertEquals(HelpSessionMode.REPLAY, session.mode)
        assertEquals(HelpSessionStage.INTRODUCTION, session.stage)
        assertEquals(0, session.stepIndex)
    }

    private fun finishIntroduction() {
        repeat(INTRODUCTION_STEP_COUNT) { index ->
            waitForTag("help-introduction-${index + 1}")
            assertEquals(index, helpState().session?.stepIndex)
            click("help-introduction-next")
        }
        assertRealTourStep(HelpTourStep.MENU)
    }

    private fun assertRealTourStep(step: HelpTourStep) {
        waitForTag("help-tour-${step.name.lowercase()}")
        compose.waitForIdle()
        assertQaFocus()
        val session = requireNotNull(helpState().session)
        assertEquals(HelpSessionMode.REPLAY, session.mode)
        assertEquals(HelpSessionStage.TOUR, session.stage)
        assertEquals(step, session.tourStep)
        compose.onNodeWithTag("help-tour-anchor").assertIsDisplayed()
        compose.onNodeWithText("Este control no está disponible", substring = true).assertDoesNotExist()
        when (step) {
            HelpTourStep.MENU -> {
                compose.onNodeWithTag("main-menu-button", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag("main-navigation-drawer", useUnmergedTree = true).assertIsNotDisplayed()
            }
            HelpTourStep.TODAY_CARD ->
                compose.onNodeWithTag("next-event-card", useUnmergedTree = true).assertIsDisplayed()
            HelpTourStep.MONTH_AND_GRID, HelpTourStep.DAY_DETAIL ->
                compose.onNodeWithTag("month-grid", useUnmergedTree = true).assertIsDisplayed()
            HelpTourStep.SUMMARY ->
                compose.onNodeWithTag("summary-overview", useUnmergedTree = true).assertIsDisplayed()
            HelpTourStep.PHOTOS, HelpTourStep.LOAD_AND_REPEAT, HelpTourStep.HELP -> {
                val tag = when (step) {
                    HelpTourStep.PHOTOS -> "drawer-action-calendar-photos"
                    HelpTourStep.LOAD_AND_REPEAT -> "drawer-action-calendar-load"
                    else -> "main-destination-help"
                }
                val control = compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode()
                val viewport = compose.onNodeWithTag("main-navigation-drawer", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val bounds = control.boundsInRoot
                assertTrue("$step target is clipped", bounds.width + 1 >= control.size.width &&
                    bounds.height + 1 >= control.size.height)
                assertTrue("$step target is outside its real drawer", bounds.left >= viewport.left - 1 &&
                    bounds.top >= viewport.top - 1 && bounds.right <= viewport.right + 1 &&
                    bounds.bottom <= viewport.bottom + 1)
                val anchor = compose.onNodeWithTag("help-tour-anchor").fetchSemanticsNode().boundsInRoot
                val explanation = compose.onNodeWithTag("help-tour-explanation").fetchSemanticsNode().boundsInRoot
                assertFalse("Tour explanation covers $step", anchor.overlaps(explanation))
            }
        }
    }

    private fun assertReturnedToHelp() {
        waitForTag("help-screen")
        compose.waitForIdle()
        assertQaFocus()
        compose.onNodeWithTag("help-screen").assertIsDisplayed()
        compose.onNodeWithTag("help-repeat-tour").performScrollTo().assertIsDisplayed().assertIsEnabled()
        HelpTourStep.entries.forEach { step ->
            compose.onNodeWithTag("help-tour-${step.name.lowercase()}").assertDoesNotExist()
        }
        assertNull(helpState().session)
        assertNull(helpState().navigationEvent)
        assertTrue(helpState().canRepeat)
    }

    private fun helpState(): HelpUiState {
        assertQaFocus()
        var state: HelpUiState? = null
        requireNotNull(scenario).onActivity { state = ViewModelProvider(it)[HelpViewModel::class.java].uiState.value }
        return requireNotNull(state)
    }

    private fun click(tag: String, scroll: Boolean = true) {
        waitForTag(tag)
        assertQaFocus()
        val node = compose.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        assertQaFocus()
        node.assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertQaFocus()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(WAIT_MILLIS) {
        assertQaFocus()
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun snapshot() = runBlocking { store.backups.capture() }

    private fun assertQaFocus(allowOwnLifecycleTransition: Boolean = false) {
        check(!focusLostInProcess) { "QA focus guard already stopped this process" }
        // Package identity only; never traverse or capture another application's UI.
        val active = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
        if (active != QA_APPLICATION_ID &&
            !(allowOwnLifecycleTransition && active != null && active == launcherPackage())
        ) stopForFocusLoss()
    }

    private fun awaitQaFocusAfterLifecycleChange() = compose.waitUntil(WAIT_MILLIS) {
        check(!focusLostInProcess) { "QA focus guard already stopped this process" }
        val active = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
        if (active != null && active != QA_APPLICATION_ID && active != launcherPackage()) stopForFocusLoss()
        active == QA_APPLICATION_ID
    }

    private fun launcherPackage(): String? = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
    )?.activityInfo?.packageName

    private fun stopForFocusLoss(): Nothing {
        focusLostInProcess = true
        error("QA focus lost; tour driver stopped without reading or capturing another application")
    }

    private fun fixtureShift(date: LocalDate): Shift {
        val start = date.atTime(12, 0).atZone(AppDefaults.zoneId())
        val now = Instant.now()
        return Shift(
            id = UUID.fromString("f5000000-0000-0000-0000-000000000001"),
            startAt = start.toInstant(), endAt = start.plusHours(8).toInstant(),
            zoneId = AppDefaults.zoneId(), localStartDate = date,
            objectiveNameSnapshot = "Institución ficticia de Ayuda", objectiveAbbreviationSnapshot = "AYU",
            objectiveAddressSnapshot = null, startTimeSnapshot = LocalTime.of(12, 0),
            endTimeSnapshot = LocalTime.of(20, 0), colorArgbSnapshot = 0xFF336699.toInt(),
            position = "Función ficticia", status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = now, updatedAt = now,
        )
    }

    private companion object {
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 30_000L
        @Volatile var focusLostInProcess = false
    }
}
