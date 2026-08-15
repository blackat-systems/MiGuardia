package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settingsOffersStandardLargeAndExtraLargeInternalZoom() {
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

        composeRule.onNodeWithText("Configuración").performClick()
        composeRule.onNodeWithText("Zoom de MiGuardia").assertExists()
        composeRule.onNodeWithText("150 %").performClick()

        composeRule.runOnIdle { assertEquals(AppZoom.LARGE, zoom) }
    }
}
