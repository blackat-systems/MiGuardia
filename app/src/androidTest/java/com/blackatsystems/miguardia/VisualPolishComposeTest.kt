package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementSurfaceHost
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ScheduleDraft
import com.blackatsystems.miguardia.ui.management.ShiftDraft
import com.blackatsystems.miguardia.ui.summary.SummaryLoadState
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisualPolishComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appearanceAndDrawerRemainNavigableAcrossEveryInternalZoom() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(appZoom = zoom) {
                MiGuardiaApp(
                    calendarState = emptyCalendarState(),
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

        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Apariencia").performClick()
        compose.onNodeWithText("150 %").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppZoom.LARGE, zoom) }
        compose.onNodeWithText("200 %").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppZoom.EXTRA_LARGE, zoom) }
        compose.onNodeWithContentDescription("Abrir menú").assertExists()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("drawer-action-vacations")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun calendarKeepsHistoricalAbbreviationAndExactTimeAtTwoHundredPercent() {
        val month = YearMonth.of(2026, 8)
        val shift = shift(month.atDay(1))
        val now = Instant.parse("2026-08-15T12:00:00Z")
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.EXTRA_LARGE) {
                MiGuardiaApp(
                    calendarState = CalendarUiState(
                        visibleMonth = month,
                        referenceInstant = now,
                        days = projectCalendarMonth(
                            month = month,
                            shifts = listOf(shift),
                            explicitDayStatuses = emptyList(),
                            medicalLeaves = emptyList(),
                            now = now,
                        ),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    appZoom = AppZoom.EXTRA_LARGE,
                )
            }
        }

        compose.onNodeWithText("ABCDE", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("19:00–07:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("guardia ABCDE", substring = true).assertExists()
    }

    @Test
    fun visualColorPickerRemainsReachableAtTwoHundredPercent() {
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.EXTRA_LARGE) {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SCHEDULE_FORM,
                        scheduleDraft = ScheduleDraft(colorArgb = 0xFF1565C0.toInt()),
                    ),
                    actions = ManagementActions(),
                )
            }
        }

        compose.onNodeWithText("Elegir color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Área de saturación y luminosidad")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Barra arcoíris de tono")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Usar color").assertIsDisplayed()
    }

    @Test
    fun longDateSelectorAndPrimaryActionStayReachableAtTwoHundredPercent() {
        val date = LocalDate.of(2026, 8, 14)
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.EXTRA_LARGE) {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        shiftDraft = ShiftDraft(
                            month = YearMonth.from(date),
                            selectedDates = setOf(date),
                        ),
                    ),
                    actions = ManagementActions(),
                )
            }
        }

        compose.onNodeWithContentDescription("viernes 14 Agosto de 2026, seleccionado").assertExists()
        compose.onNodeWithText("Revisar y guardar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun persistentErrorsDoNotDisappearOnTheConfirmationTimer() {
        var dismissed = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MiGuardiaTheme {
                PersistentMessage(
                    message = "Error ficticio persistente",
                    onDismiss = { dismissed = true },
                    onRetry = {},
                )
            }
        }

        compose.mainClock.advanceTimeBy(3_000L)
        compose.onNodeWithText("Error ficticio persistente").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test
    fun summaryRendersTheSameInformationInLightAndDarkThemes() {
        var dark by mutableStateOf(false)
        val state = SummaryUiState(
            visibleMonth = YearMonth.of(2026, 8),
            referenceInstant = Instant.parse("2026-08-15T12:00:00Z"),
            loadState = SummaryLoadState.CONTENT,
        )
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark) {
                SummaryScreen(state, PaddingValues(), {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("Horas del mes").assertExists()
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Horas del mes").assertExists()
    }

    private fun emptyCalendarState() = CalendarUiState(
        visibleMonth = YearMonth.of(2026, 8),
        referenceInstant = Instant.parse("2026-08-15T12:00:00Z"),
        loadState = CalendarLoadState.CONTENT,
    )

    private fun shift(date: LocalDate): Shift {
        val start = ZonedDateTime.of(date, LocalTime.of(19, 0), AppDefaults.zoneId())
        val end = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), AppDefaults.zoneId())
        return Shift(
            id = UUID.fromString("81000000-0000-0000-0000-000000000001"),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "ABCDE",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
