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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ShiftDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test

class CalendarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp()
    }

    @Test
    fun controlsAndHorizontalGesturesChangeMonthButShortDragDoesNot() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("Agosto de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes anterior").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Julio de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes siguiente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("Septiembre de 2026").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Septiembre de 2026").assertExists()
        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeRight() }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("Agosto de 2026").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput {
            down(center)
            moveBy(Offset(-20f, 0f))
            up()
        }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithContentDescription("Mes anterior").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Hoy").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agosto de 2026").assertExists()
    }

    @Test
    fun gridShowsEveryStateAndDayDetailKeepsMultipleShiftsAccessible() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("ABCDE", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("23:00–03:00", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("completed-day-2026-08-02").assertExists()
        composeRule.onNodeWithText("CUR", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ahora", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("PRO", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Próx.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CAN", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Cancel.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("AUS", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Aus.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("F", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CM", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(
            "día sin definir marcado explícitamente",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "Domingo 9 de agosto de 2026, sin definir",
        ).assertExists()

        composeRule.onNodeWithContentDescription("guardia DOS", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
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
        composeRule.onNodeWithText("Reintentar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun addButtonOpensRealShiftFormForVisibleMonth() {
        var managementState by mutableStateOf(ManagementUiState())
        var requestedMonth: YearMonth? = null
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = contentState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    managementState = managementState,
                    managementActions = ManagementActions(
                        openAddShift = { month, date ->
                            requestedMonth = month
                            managementState = managementState.copy(
                                surface = ManagementSurface.SHIFT_FORM,
                                shiftDraft = ShiftDraft(
                                    month = month,
                                    selectedDates = setOf(date ?: month.atDay(1)),
                                ),
                            )
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Agregar").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardia").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardias").assertExists()
        composeRule.onNodeWithText("Revisar y guardar").assertExists()
        composeRule.runOnIdle { assertEquals(YearMonth.of(2026, 8), requestedMonth) }
    }

    @Test
    fun shiftDetailExposesEditDuplicateAndConfirmedDelete() {
        val state = contentState().copy(selectedDate = LocalDate.of(2026, 8, 3))
        var edited: UUID? = null
        var duplicated: UUID? = null
        var deleted: UUID? = null
        var openedExceptions: UUID? = null
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = { dismissed += 1 },
                    onRetry = {},
                    managementActions = ManagementActions(
                        openEditShift = { edited = it.id },
                        openDuplicateShift = { duplicated = it.id },
                        deleteShift = { deleted = it },
                    ),
                    exceptionsActions = ExceptionsActions(
                        openShift = { openedExceptions = it.id },
                    ),
                )
            }
        }

        composeRule.onAllNodesWithText("Editar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithText("Duplicar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertTrue(edited != null)
            assertTrue(duplicated != null)
        }
        composeRule.onAllNodesWithText("Informar novedad / notas")[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertTrue(openedExceptions != null)
            assertTrue(dismissed >= 3)
        }
        composeRule.onAllNodesWithText("Eliminar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Eliminar guardia").assertExists()
        composeRule.onAllNodesWithText("Eliminar")[2].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertTrue(deleted != null) }
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
            days = projectCalendarMonth(
                month = month,
                shifts = shifts,
                explicitDayStatuses = statuses,
                medicalLeaves = leaves,
                now = REFERENCE_NOW,
                vacations = if (month == YearMonth.of(2026, 8)) {
                    listOf(
                        Vacation(
                            id = UUID.fromString("10000000-0000-0000-0000-000000000099"),
                            startDate = LocalDate.of(2026, 8, 10),
                            endDateInclusive = LocalDate.of(2026, 8, 11),
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                    )
                } else {
                    emptyList()
                },
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private fun fixtureShifts(): List<Shift> = listOf(
        shift("CMP", "Objetivo completado", 1, 8, 16),
        shift("ABCDE", "Objetivo de abreviatura máxima", 2, 23, 3),
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
        val endDate = if (endTime <= startTime) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, endTime, AppDefaults.zoneId())
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
