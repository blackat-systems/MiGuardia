package com.blackatsystems.miguardia

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import com.blackatsystems.miguardia.ui.photos.*
import com.blackatsystems.miguardia.ui.exceptions.*
import com.blackatsystems.miguardia.ui.vacation.*
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import com.blackatsystems.miguardia.ui.components.CONFIRMATION_DURATION_MILLIS
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.components.TransientConfirmationLayout
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class TransientConfirmationComposeTest {
    // LaunchedEffect.delay uses Android's Main dispatcher, not virtual test time.
    @get:Rule val composeRule = createComposeRule(Dispatchers.Main.immediate)

    @Test
    fun wrapContentUsesOnlyItsMeasuredBodyAndVisibleNotice() {
        var message by mutableStateOf<String?>(null)
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("confirmation-host")) {
                    TransientConfirmation(
                        message, {}, Modifier.testTag("confirmation-wrap"),
                        layout = TransientConfirmationLayout.WrapContent,
                    ) {
                        Box(Modifier.width(160.dp).height(48.dp).testTag("confirmation-body"))
                    }
                }
            }
        }
        val host = composeRule.onNodeWithTag("confirmation-host").fetchSemanticsNode().boundsInRoot
        val body = composeRule.onNodeWithTag("confirmation-body").fetchSemanticsNode().boundsInRoot
        val withoutNotice = composeRule.onNodeWithTag("confirmation-wrap").fetchSemanticsNode().boundsInRoot
        assertEquals(body.height, withoutNotice.height, density)
        assertTrue("Short content must not fill the host", withoutNotice.height < host.height)

        composeRule.runOnIdle { message = "Acción completada." }
        val notice = composeRule.onNodeWithTag("transient-confirmation").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .fetchSemanticsNode().boundsInRoot
        val withNotice = composeRule.onNodeWithTag("confirmation-wrap").fetchSemanticsNode().boundsInRoot
        val movedBody = composeRule.onNodeWithTag("confirmation-body").fetchSemanticsNode().boundsInRoot
        assertFalse("The reserved notice must not cover the body", notice.overlaps(movedBody))
        assertEquals("Only the notice's declared 8-dp top/bottom padding is reserved",
            notice.height + body.height + 16f * density, withNotice.height, density)
    }

    @Test
    fun defaultLayoutStillFillsTheHostWithAndWithoutReservedNotice() {
        var reserved by mutableStateOf(false)
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("confirmation-host")) {
                    TransientConfirmation(null, {}, Modifier.testTag("confirmation-default"), reserveSpace = reserved) {
                        Box(Modifier.width(160.dp).height(48.dp))
                    }
                }
            }
        }
        for (reserveSpace in listOf(false, true)) {
            composeRule.runOnIdle { reserved = reserveSpace }
            val host = composeRule.onNodeWithTag("confirmation-host").fetchSemanticsNode().boundsInRoot
            val container = composeRule.onNodeWithTag("confirmation-default").fetchSemanticsNode().boundsInRoot
            assertEquals("Default sizing changed: reserveSpace=$reserveSpace", host.height, container.height, density)
        }
    }

    @Test
    fun wrapContentExpiresOnTheRealClockWithoutLeavingReservedHeight() {
        var message by mutableStateOf<String?>(null)
        var started = 0L
        var dismissed = 0L
        var dismissals = 0
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    TransientConfirmation(
                        message,
                        onDismiss = {
                            dismissed = SystemClock.elapsedRealtime()
                            dismissals++
                            message = null
                        },
                        modifier = Modifier.testTag("confirmation-wrap"),
                        layout = TransientConfirmationLayout.WrapContent,
                    ) {
                        Box(Modifier.width(160.dp).height(48.dp).testTag("confirmation-body"))
                    }
                }
            }
        }
        val baseline = composeRule.onNodeWithTag("confirmation-wrap").fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { started = SystemClock.elapsedRealtime(); message = "Acción completada." }
        composeRule.onNodeWithTag("transient-confirmation").assertIsDisplayed()
        composeRule.waitUntil(CONFIRMATION_DURATION_MILLIS + 2_000L) { dismissed != 0L }
        composeRule.runOnIdle {
            assertTrue("A virtual/early timeout is not the 2500-ms Android delay", dismissed - started >= CONFIRMATION_DURATION_MILLIS)
            assertEquals(1, dismissals)
        }
        composeRule.onNodeWithTag("transient-confirmation").assertDoesNotExist()
        assertEquals(baseline, composeRule.onNodeWithTag("confirmation-wrap").fetchSemanticsNode().boundsInRoot.height, density)
    }

    @Test
    fun confirmationNeverCoversOrConsumesNavigationInPhotosNotesHolidaysAndVacations() {
        var screen by mutableStateOf(0)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var clicks=0
        composeRule.mainClock.autoAdvance=false
        val store=SchedulePhotoFileStore(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            AndroidFontScaleTheme(darkTheme=dark,fontScale =fontScale) {
                when(screen) {
                    0 -> PhotosSurfaceHost(PhotosUiState(month=java.time.YearMonth.of(2088,1),surface=PhotosSurface.LIST,infoMessage="Foto agregada.",isLoading=false),PhotosActions(close={clicks++}),store)
                    1 -> ExceptionsSurfaceHost(ExceptionsUiState(surface=ExceptionsSurface.NOTES,infoMessage="Nota guardada."),ExceptionsActions(close={clicks++}),store)
                    2 -> ExceptionsSurfaceHost(ExceptionsUiState(surface=ExceptionsSurface.HOLIDAYS,infoMessage="Feriado guardado."),ExceptionsActions(close={clicks++}),store)
                    else -> VacationSurfaceHost(VacationUiState(visibleMonth=java.time.YearMonth.of(2088,1),surface=VacationSurface.LIST,infoMessage="Vacaciones guardadas."),VacationActions(requestBack={clicks++}))
                }
            }
        }
        for (scale in TestFontScale.entries) for (theme in listOf(false,true)) for (destination in 0..3) {
            composeRule.runOnIdle { screen=destination;fontScale=scale;dark=theme }
            composeRule.mainClock.advanceTimeByFrame()
            val notice=composeRule.onNodeWithTag("transient-confirmation").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val navigation=composeRule.onNodeWithText("Cerrar").assertIsDisplayed()
            val bounds=navigation.fetchSemanticsNode().boundsInRoot
            assertFalse("Confirmation covers navigation: screen=$destination fontScale=$scale dark=$theme",notice.overlaps(bounds))
            val before=clicks
            navigation.performClick()
            composeRule.runOnIdle { assertEquals(before+1,clicks) }
        }
    }

    @Test
    fun weatherNoticeLeavesCloseVisibleAndTouchableAtEveryThemeAndFontScale() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var clicks = 0
        val failures = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                WeatherSurfaceHost(
                    WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false,
                        infoMessage = "Pronóstico actualizado."),
                    WeatherActions(close = { clicks++ }),
                )
            }
        }
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            composeRule.runOnIdle { dark = theme; fontScale = scale }
            composeRule.mainClock.advanceTimeByFrame()
            val notice = composeRule.onNodeWithTag("transient-confirmation").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val close = composeRule.onNodeWithTag("weather-close").assertIsDisplayed()
            val before = clicks
            close.performTouchInput { click() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { if (clicks != before + 1) failures += "Close missed: $scale dark=$theme" }
            if (notice.overlaps(close.fetchSemanticsNode().boundsInRoot)) failures += "Close covered: $scale dark=$theme"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun confirmationDismissUsesCurrentOwnerWhenTextIsUnchanged() {
        var secondOwner by mutableStateOf(false)
        var firstClears = 0
        var secondClears = 0
        composeRule.setContent {
            MaterialTheme {
                TransientConfirmation("Acción completada.",
                    if (secondOwner) ({ secondClears++ }) else ({ firstClears++ })) { }
            }
        }
        composeRule.runOnIdle { secondOwner = true }
        composeRule.waitUntil(4_000) { secondClears == 1 }
        composeRule.runOnIdle { assertEquals(0, firstClears); assertEquals(1, secondClears) }
    }

    @Test
    fun confirmationFloatsAndDisappearsAfterTwoAndAHalfSeconds() {
        var message by mutableStateOf<String?>("Acción completada.")
        composeRule.setContent {
            MaterialTheme {
                TransientConfirmation(message, onDismiss = { message = null }) { }
            }
        }

        composeRule.onNodeWithText("Acción completada.").assertExists()
        composeRule.waitUntil(CONFIRMATION_DURATION_MILLIS + 1_500L) { message == null }
        composeRule.onNodeWithText("Acción completada.").assertDoesNotExist()
    }
}
