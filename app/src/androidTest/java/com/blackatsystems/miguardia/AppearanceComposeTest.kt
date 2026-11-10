package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.androidFontDensity
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppearanceComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun appearanceKeepsThemeControlsWithoutInternalZoomOrTalkBackControls() {
        composeRule.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 8),
                        referenceInstant = Instant.parse("2026-08-13T12:00:00Z"),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Abrir menú").performClick()
        composeRule.onNodeWithTag("main-destination-appearance").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Tema de MiGuardia").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tema de MiGuardia").assertExists()
        composeRule.onNodeWithTag("theme-mode-toggle").assertHasClickAction()
        composeRule.onNodeWithText("Zoom de MiGuardia").assertDoesNotExist()
        listOf(100, 150, 200).forEach { percent ->
            composeRule.onNodeWithTag("appearance-zoom-$percent").assertDoesNotExist()
            composeRule.onNodeWithTag("appearance-zoom-radio-$percent", useUnmergedTree = true).assertDoesNotExist()
        }
        composeRule.onNodeWithText("Activar TalkBack").assertDoesNotExist()
        composeRule.onNodeWithText("Desactivar TalkBack").assertDoesNotExist()
    }

    @Test
    fun themePreservesAndroidDensityIdentityAndNativeFontConversion() {
        var scenario by mutableStateOf(TestFontScale.STANDARD)
        var supplied: Density? = null
        var inherited: Density? = null
        var geometric48 = Float.NaN
        var text14 = Float.NaN
        var nativeText14 = Float.NaN
        var originalConfigurationScale = Float.NaN
        var observedConfigurationScale = Float.NaN
        composeRule.setContent {
            val parent = LocalDensity.current
            val context = LocalContext.current
            val configurationScaleBefore = LocalConfiguration.current.fontScale
            val native = androidFontDensity(context, parent, scenario)
            SideEffect {
                originalConfigurationScale = configurationScaleBefore
                supplied = native
                nativeText14 = with(native) { 14.sp.toPx() }
            }
            CompositionLocalProvider(LocalDensity provides native) {
                MiGuardiaTheme {
                    val actual = LocalDensity.current
                    val pixels48 = with(actual) { 48.dp.toPx() }
                    val pixels14 = with(actual) { 14.sp.toPx() }
                    val configurationScaleAfter = LocalConfiguration.current.fontScale
                    SideEffect {
                        inherited = actual
                        geometric48 = pixels48
                        text14 = pixels14
                        observedConfigurationScale = configurationScaleAfter
                    }
                }
            }
        }
        var standardGeometry = Float.NaN
        var standardText = Float.NaN
        TestFontScale.entries.forEach { scale ->
            composeRule.runOnIdle { scenario = scale }
            composeRule.runOnIdle {
                assertSame("Theme must not replace Android's density/converter", supplied, inherited)
                assertEquals(scale.scale, requireNotNull(inherited).fontScale, 0f)
                assertEquals(nativeText14, text14, 0f)
                assertEquals(originalConfigurationScale, observedConfigurationScale, 0f)
                if (scale == TestFontScale.STANDARD) {
                    standardGeometry = geometric48
                    standardText = text14
                } else {
                    assertEquals("Text size must not scale touch geometry", standardGeometry, geometric48, 0f)
                    assertTrue("Android text size must actually affect SP", text14 > standardText)
                }
            }
        }
    }

    @Test
    fun appearanceOffersCompactLightDarkToggleAndSystemOption() {
        var mode by mutableStateOf(AppThemeMode.fromStorage(null))
        assertEquals(AppThemeMode.DARK, mode)
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 8),
                        referenceInstant = Instant.parse("2026-08-13T12:00:00Z"),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    appThemeMode = mode,
                    onAppThemeModeChange = { mode = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Abrir menú").performClick()
        composeRule.onNodeWithTag("main-destination-appearance").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Tema de MiGuardia").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tema de MiGuardia").assertExists()
        composeRule.onNodeWithTag("theme-mode-toggle").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(AppThemeMode.LIGHT, mode)
            mode = AppThemeMode.fromStorage(mode.name)
            assertEquals(AppThemeMode.LIGHT, mode)
        }
        composeRule.onNodeWithText("Tema actual: Claro").assertExists()
        composeRule.onNodeWithTag("theme-mode-system").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(AppThemeMode.SYSTEM, mode) }
        composeRule.onNodeWithTag("theme-mode-system").assertIsSelected()
    }
}
