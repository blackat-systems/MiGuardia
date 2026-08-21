package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.hours.MonthlyHoursSummary
import com.blackatsystems.miguardia.ui.summary.SummaryLoadState
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SummaryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyMonthShowsHonestZerosAndEmptyExplanation() {
        composeRule.setContent { Screen(state()) }

        composeRule.onNodeWithText(
            "Todavía no hay guardias cargadas en este mes. Usá Agregar en el Calendario para cargar la primera; mientras tanto, el resumen se muestra en cero.",
        )
            .assertExists()
        composeRule.onNodeWithContentDescription("Planificadas, 0 h").assertExists()
        composeRule.onNodeWithContentDescription("Trabajadas, 0 h").assertExists()
        composeRule.onNodeWithContentDescription("Pendientes, 0 h").assertExists()
    }

    @Test
    fun realSummaryShowsAllHourAndEventCategories() {
        val summary = state().summary.copy(
            planned = Duration.ofHours(230),
            worked = Duration.ofHours(210).plusMinutes(30),
            pending = Duration.ofHours(4),
            overtime = Duration.ofHours(6).plusMinutes(30),
            nightWorked = Duration.ofHours(72),
            holidayWorked = Duration.ofHours(8),
            shiftCount = 28,
            dayOffCount = 4,
            medicalLeaveDayCount = 1,
            medicalLeaveHours = Duration.ofHours(8),
            absenceCount = 1,
            absenceHours = Duration.ofHours(8),
            cancellationCount = 1,
            cancellationHours = Duration.ofHours(8),
            vacationDayCount = 5,
        )
        composeRule.setContent { Screen(state().copy(summary = summary)) }

        composeRule.onNodeWithContentDescription("Trabajadas, 210 h 30 min").assertExists()
        composeRule.onNodeWithContentDescription("Extra después de 204 h, 6 h 30 min").assertExists()
        composeRule.onNodeWithContentDescription("Nocturnas (21:00–06:00), 72 h")
            .performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("Carpeta médica, 1 día")
            .performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("Ausencias, 8 horas")
            .performScrollTo().assertExists()
        composeRule.onNodeWithText("Cancelaciones").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Días de vacaciones, 5")
            .performScrollTo().assertExists()
        composeRule.onNodeWithText(
            "Extra, nocturnas y feriado pueden superponerse. Son clasificaciones de horas trabajadas y no se suman nuevamente al total.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun monthControlsAndRetryInvokeTheirActions() {
        var previous = 0
        var next = 0
        var today = 0
        var retry = 0
        composeRule.setContent {
            MaterialTheme {
                SummaryScreen(
                    state = state().copy(
                        loadState = SummaryLoadState.ERROR,
                        errorMessage = "Error ficticio recuperable",
                    ),
                    contentPadding = PaddingValues(),
                    onPreviousMonth = { previous += 1 },
                    onNextMonth = { next += 1 },
                    onToday = { today += 1 },
                    onRetry = { retry += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mes anterior del resumen").performClick()
        composeRule.onNodeWithContentDescription("Mes siguiente del resumen").performClick()
        composeRule.onNodeWithText("Hoy").performClick()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, today)
            assertEquals(1, retry)
        }
    }

    @Test
    fun mergedValueSemanticsReadsLabelBeforeValue() {
        composeRule.setContent { Screen(state()) }

        composeRule.onNodeWithContentDescription("Planificadas, 0 h")
            .assertContentDescriptionEquals("Planificadas, 0 h")
    }

    private fun state(): SummaryUiState {
        val month = YearMonth.of(2026, 8)
        val now = Instant.parse("2026-08-13T15:00:00Z")
        return SummaryUiState(
            visibleMonth = month,
            referenceInstant = now,
            summary = MonthlyHoursSummary(
                month = month,
                referenceInstant = now,
                planned = Duration.ZERO,
                worked = Duration.ZERO,
                pending = Duration.ZERO,
                overtime = Duration.ZERO,
                nightWorked = Duration.ZERO,
                holidayWorked = Duration.ZERO,
                shiftCount = 0,
                dayOffCount = 0,
                medicalLeaveDayCount = 0,
                medicalLeaveHours = Duration.ZERO,
                absenceCount = 0,
                absenceHours = Duration.ZERO,
                cancellationCount = 0,
                cancellationHours = Duration.ZERO,
            ),
            loadState = SummaryLoadState.CONTENT,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Screen(state: SummaryUiState) {
        MaterialTheme {
            SummaryScreen(
                state = state,
                contentPadding = PaddingValues(),
                onPreviousMonth = {},
                onNextMonth = {},
                onToday = {},
                onRetry = {},
            )
        }
    }
}
