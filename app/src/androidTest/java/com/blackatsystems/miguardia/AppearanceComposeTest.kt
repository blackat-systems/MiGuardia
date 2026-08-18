package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun appearanceOffersStandardLargeAndExtraLargeInternalZoom() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
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
                    appZoom = zoom,
                    onAppZoomChange = { zoom = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Abrir menú").performClick()
        composeRule.onNodeWithTag("main-destination-appearance").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Zoom de MiGuardia").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Zoom de MiGuardia").assertExists()
        composeRule.onNodeWithText("150 %").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(AppZoom.LARGE, zoom) }
    }

    @Test
    fun appearanceOffersCompactLightDarkToggleAndSystemOption() {
        var mode by mutableStateOf(AppThemeMode.DARK)
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

        composeRule.runOnIdle { assertEquals(AppThemeMode.LIGHT, mode) }
        composeRule.onNodeWithText("Tema actual: Claro").assertExists()
        composeRule.onNodeWithTag("theme-mode-system").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(AppThemeMode.SYSTEM, mode) }
        composeRule.onNodeWithTag("theme-mode-system").assertIsSelected()
    }
}
