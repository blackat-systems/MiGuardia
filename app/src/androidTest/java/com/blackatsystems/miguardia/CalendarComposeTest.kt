package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controlsAndHorizontalGesturesChangeMonthButShortDragDoesNot() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("Agosto de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes anterior").performClick()
        composeRule.onNodeWithText("Julio de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes siguiente").performClick()
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Septiembre de 2026").assertExists()
        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput {
            down(center)
            moveBy(Offset(-20f, 0f))
            up()
        }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithContentDescription("Mes anterior").performClick()
        composeRule.onNodeWithText("Hoy").performClick()
        composeRule.onNodeWithText("Agosto de 2026").assertExists()
    }

    @Test
    fun gridShowsEveryStateAndDayDetailKeepsMultipleShiftsAccessible() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("CMP · Hecha", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CUR · Ahora", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("PRO · Próx.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CAN · Cancel.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("AUS · Aus.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("F", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CM", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(
            "día sin definir marcado explícitamente",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "Domingo 9 de agosto de 2026, sin definir",
        ).assertExists()

        composeRule.onNodeWithContentDescription("guardia DOS", substring = true).performClick()
        composeRule.onNodeWithText("Objetivo dos (DOS)").assertExists()
        composeRule.onNodeWithText("Objetivo tres (TRE)").assertExists()
        composeRule.onAllNodesWithText("Completada").assertCountEquals(2)
    }

    @Test
    fun loadingAndRecoverableErrorHaveHonestActions() {
        var retries = 0
        composeRule.setContent {
            MiGuardiaApp(
                calendarState = CalendarUiState(
                    visibleMonth = YearMonth.of(2026, 8),
                    referenceInstant = REFERENCE_NOW,
                    loadState = CalendarLoadState.ERROR,
                    errorMessage = "Error ficticio recuperable",
                ),
                onPreviousMonth = {},
                onNextMonth = {},
                onToday = {},
                onSelectDate = {},
                onDismissDate = {},
                onRetry = { retries += 1 },
            )
        }

        composeRule.onNodeWithText("Error ficticio recuperable").assertExists()
        composeRule.onNodeWithTag("month-grid").assertDoesNotExist()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Composable
    private fun CalendarHarness(initialState: CalendarUiState) {
        var state by remember { mutableStateOf(initialState) }
        fun moveTo(month: YearMonth) {
            state = contentState(month = month).copy(selectedDate = null)
        }
        MaterialTheme {
            MiGuardiaApp(
                calendarState = state,
                onPreviousMonth = { moveTo(state.visibleMonth.minusMonths(1)) },
                onNextMonth = { moveTo(state.visibleMonth.plusMonths(1)) },
                onToday = { moveTo(YearMonth.of(2026, 8)) },
                onSelectDate = { state = state.copy(selectedDate = it) },
                onDismissDate = { state = state.copy(selectedDate = null) },
                onRetry = {},
            )
        }
    }

    private fun contentState(month: YearMonth = YearMonth.of(2026, 8)): CalendarUiState {
        val shifts = if (month == YearMonth.of(2026, 8)) fixtureShifts() else emptyList()
        val statuses = if (month == YearMonth.of(2026, 8)) {
            listOf(
                ExplicitDayStatus(LocalDate.of(2026, 8, 6), ExplicitDayStatusType.DAY_OFF),
                ExplicitDayStatus(LocalDate.of(2026, 8, 7), ExplicitDayStatusType.UNDEFINED),
            )
        } else {
            emptyList()
        }
        val leaves = if (month == YearMonth.of(2026, 8)) {
            listOf(
                MedicalLeave(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    startDate = LocalDate.of(2026, 8, 8),
                    endDateInclusive = LocalDate.of(2026, 8, 8),
                    privateNote = "Nota ficticia que no debe mostrarse",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
        } else {
            emptyList()
        }
        return CalendarUiState(
            visibleMonth = month,
            referenceInstant = REFERENCE_NOW,
            days = projectCalendarMonth(month, shifts, statuses, leaves, REFERENCE_NOW),
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private fun fixtureShifts(): List<Shift> = listOf(
        shift("CMP", "Objetivo completado", 1, 8, 16),
        shift("DOS", "Objetivo dos", 3, 6, 10),
        shift("TRE", "Objetivo tres", 3, 12, 16),
        shift("CAN", "Objetivo cancelado", 4, 8, 16, ShiftStatus.CANCELLED),
        shift("AUS", "Objetivo ausencia", 5, 8, 16, ShiftStatus.ABSENT),
        shift("CUR", "Objetivo en curso", 13, 11, 13),
        shift("PRO", "Objetivo próximo", 14, 15, 18),
    )

    private fun shift(
        abbreviation: String,
        name: String,
        day: Int,
        startHour: Int,
        endHour: Int,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): Shift {
        val date = LocalDate.of(2026, 8, day)
        val startTime = LocalTime.of(startHour, 0)
        val endTime = LocalTime.of(endHour, 0)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val end = ZonedDateTime.of(date, endTime, AppDefaults.zoneId())
        return Shift(
            id = UUID.nameUUIDFromBytes("$abbreviation-$day-$startHour".toByteArray()),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = name,
            objectiveAbbreviationSnapshot = abbreviation,
            objectiveAddressSnapshot = "Calle ficticia 123",
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF3F51B5.toInt(),
            position = "Puesto ficticio",
            status = status,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private companion object {
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 13),
            LocalTime.of(12, 0),
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
