package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementSurfaceHost
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ObjectiveDraft
import com.blackatsystems.miguardia.ui.management.ScheduleDraft
import com.blackatsystems.miguardia.ui.management.ScheduleOption
import com.blackatsystems.miguardia.ui.management.ShiftDraft
import com.blackatsystems.miguardia.ui.management.ShiftEntryMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test

class ManagementComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp()
    }

    @Test
    fun settingsShowsHiddenTemplatesAndConfirmsHistoricalPreservation() {
        var deleted: UUID? = null
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SETTINGS,
                        showHidden = true,
                        objectives = listOf(ACTIVE_OBJECTIVE, HIDDEN_OBJECTIVE),
                    ),
                    actions = ManagementActions(deleteObjective = { deleted = it }),
                )
            }
        }

        composeRule.onNodeWithText("Objetivo oculto (OCU)").assertExists()
        composeRule.onAllNodesWithText("Eliminar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Las guardias históricas se conservarán.", substring = true).assertExists()
        composeRule.onAllNodesWithText("Eliminar")[2].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(ACTIVE_OBJECTIVE.id, deleted) }
    }

    @Test
    fun objectiveFormKeepsDraftAndSubmitsOnce() {
        var state by mutableStateOf(
            ManagementUiState(surface = ManagementSurface.OBJECTIVE_FORM, objectiveDraft = ObjectiveDraft()),
        )
        var saves = 0
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = state,
                    actions = ManagementActions(
                        updateObjective = { transform ->
                            state = state.copy(objectiveDraft = transform(state.objectiveDraft))
                        },
                        saveObjective = { saves += 1 },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Nombre completo").performTextInput("Objetivo ficticio")
        composeRule.onNodeWithText("Abreviatura (2 a 5 caracteres)").performTextInput("fic")
        composeRule.onNodeWithText("Guardar objetivo").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals("FIC", state.objectiveDraft.abbreviation)
            assertEquals(1, saves)
        }
    }

    @Test
    fun similarScheduleColorWarnsButCanBeConfirmed() {
        var saves = 0
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SCHEDULE_FORM,
                        scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                        scheduleDraft = ScheduleDraft(
                            objectiveId = ACTIVE_OBJECTIVE.id,
                            colorArgb = SCHEDULE.colorArgb,
                        ),
                    ),
                    actions = ManagementActions(saveSchedule = { saves += 1 }),
                )
            }
        }

        composeRule.onNodeWithText("Guardar horario").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Colores parecidos").assertExists()
        composeRule.onNodeWithText("Usar color").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun scheduleColorPickerOffersVisualFieldHueAndRgbReadout() {
        var state by mutableStateOf(
            ManagementUiState(
                surface = ManagementSurface.SCHEDULE_FORM,
                scheduleDraft = ScheduleDraft(
                    objectiveId = ACTIVE_OBJECTIVE.id,
                    colorArgb = 0xFF1565C0.toInt(),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = state,
                    actions = ManagementActions(
                        updateSchedule = { transform ->
                            state = state.copy(scheduleDraft = transform(state.scheduleDraft))
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Elegir color").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Selector de color").assertExists()
        composeRule.onNodeWithContentDescription("Área de saturación y luminosidad").assertExists()
        composeRule.onNodeWithContentDescription("Barra arcoíris de tono").assertExists()
        composeRule.onNodeWithTag("color-saturation-brightness").performTouchInput { click() }
        composeRule.onNodeWithText("RGB:", substring = true).assertExists()
        composeRule.onNodeWithText("HEX:", substring = true).assertExists()
        composeRule.onNodeWithText("Usar color").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(state.scheduleDraft.colorArgb != 0xFF1565C0.toInt())
            assertEquals(0xFF, state.scheduleDraft.colorArgb ushr 24)
        }
    }

    @Test
    fun scheduleUsesA24HourTimeSelector() {
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SCHEDULE_FORM,
                        scheduleDraft = ScheduleDraft(
                            objectiveId = ACTIVE_OBJECTIVE.id,
                            startTime = "19:00",
                            endTime = "07:00",
                        ),
                    ),
                    actions = ManagementActions(),
                )
            }
        }

        composeRule.onNodeWithText("Inicio: 19:00").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Seleccionar hora de inicio").assertExists()
        composeRule.onNodeWithText("Cancelar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Seleccionar hora de inicio").assertDoesNotExist()
    }

    @Test
    fun multipleShiftShowsPreviewAndFinalConfirmation() {
        var requested: Pair<OccupiedDatePolicy?, Boolean>? = null
        val month = YearMonth.of(2026, 8)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                        shiftDraft = ShiftDraft(
                            mode = ShiftEntryMode.MULTIPLE,
                            month = month,
                            selectedDates = setOf(month.atDay(2), month.atDay(9)),
                            combinationId = SCHEDULE.id,
                        ),
                    ),
                    actions = ManagementActions(saveShift = { policy, confirmed -> requested = policy to confirmed }),
                )
            }
        }

        composeRule.onNodeWithText("2 fechas seleccionadas: 2, 9").assertExists()
        composeRule.onNodeWithText("Termina al día siguiente").assertExists()
        composeRule.onNodeWithText("Revisar y guardar").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Confirmar guardias").assertExists()
        composeRule.onNodeWithText("Guardar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(null to false, requested) }
    }

    @Test
    fun shiftFormGroupsSchedulesInsideSelectableObjectiveFolders() {
        var chosen: UUID? = null
        var scheduleObjective: UUID? = null
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        objectives = listOf(ACTIVE_OBJECTIVE, SECOND_OBJECTIVE),
                        scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                        shiftDraft = ShiftDraft(
                            month = YearMonth.of(2026, 8),
                            selectedDates = setOf(LocalDate.of(2026, 8, 14)),
                        ),
                    ),
                    actions = ManagementActions(
                        chooseCombination = { chosen = it },
                        openSchedule = { objectiveId, _ -> scheduleObjective = objectiveId },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Objetivo activo (ACT)").assertExists()
        composeRule.onNodeWithText("Segundo objetivo (SEG)").assertExists()
        composeRule.onNodeWithText("19:00–07:00").assertDoesNotExist()
        composeRule.onNodeWithText("Crear horario para ACT").assertDoesNotExist()

        composeRule.onNodeWithText("Objetivo activo (ACT)")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("19:00–07:00").assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("+ Agregar horario")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(SCHEDULE.id, chosen)
            assertEquals(ACTIVE_OBJECTIVE.id, scheduleObjective)
        }

        composeRule.onNodeWithText("Segundo objetivo (SEG)")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("19:00–07:00").assertDoesNotExist()
        composeRule.onNodeWithText("Todavía no hay horarios para este objetivo.").assertExists()
    }

    @Test
    fun occupiedDatesOfferAtomicPoliciesAndSeparateSecondShift() {
        var requested: OccupiedDatePolicy? = null
        val date = LocalDate.of(2026, 8, 3)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        shiftDraft = ShiftDraft(
                            month = YearMonth.from(date),
                            selectedDates = setOf(date),
                            occupiedDates = setOf(date),
                        ),
                    ),
                    actions = ManagementActions(saveShift = { policy, _ -> requested = policy }),
                )
            }
        }

        composeRule.onNodeWithText("Reemplazar").assertExists()
        composeRule.onNodeWithText("Conservar ocupadas").assertExists()
        composeRule.onNodeWithText("Agregar segunda guardia").assertExists().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agregar segunda").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(OccupiedDatePolicy.ADD_SECOND_SHIFT, requested) }
    }

    @Test
    fun editingAnOccupiedDateOnlyOffersSecondShiftOrCancel() {
        val date = LocalDate.of(2026, 8, 3)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        shiftDraft = ShiftDraft(
                            month = YearMonth.from(date),
                            selectedDates = setOf(date),
                            editingShift = SHIFT,
                            occupiedDates = setOf(date),
                        ),
                    ),
                    actions = ManagementActions(),
                )
            }
        }

        composeRule.onNodeWithText("Guardar como segunda").assertExists()
        composeRule.onNodeWithText("Reemplazar").assertDoesNotExist()
        composeRule.onNodeWithText("Conservar ocupadas").assertDoesNotExist()
    }

    @Test
    fun restWarningAllowsExplicitContinuation() {
        var confirmed = false
        val date = LocalDate.of(2026, 8, 4)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        shiftDraft = ShiftDraft(
                            month = YearMonth.from(date),
                            selectedDates = setOf(date),
                            warnings = listOf("Hay 11 h 59 min de descanso."),
                        ),
                    ),
                    actions = ManagementActions(confirmWarnings = { confirmed = true }),
                )
            }
        }

        composeRule.onNodeWithText("Hay 11 h 59 min de descanso.", substring = true).assertExists()
        composeRule.onNodeWithText("Continuar igualmente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun restWarningCanReturnToTheFormForCorrection() {
        var dismissed = false
        val date = LocalDate.of(2026, 8, 4)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        shiftDraft = ShiftDraft(
                            month = YearMonth.from(date),
                            selectedDates = setOf(date),
                            warnings = listOf("Hay 11 h 59 min de descanso."),
                        ),
                    ),
                    actions = ManagementActions(dismissWarnings = { dismissed = true }),
                )
            }
        }

        composeRule.onNodeWithText("Volver y corregir").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    companion object {
        private val ACTIVE_OBJECTIVE = Objective(
            id = UUID.fromString("20000000-0000-0000-0000-000000000001"),
            fullName = "Objetivo activo",
            abbreviation = "ACT",
            address = "Dirección ficticia",
            note = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        private val HIDDEN_OBJECTIVE = ACTIVE_OBJECTIVE.copy(
            id = UUID.fromString("20000000-0000-0000-0000-000000000002"),
            fullName = "Objetivo oculto",
            abbreviation = "OCU",
            isActive = false,
        )
        private val SECOND_OBJECTIVE = ACTIVE_OBJECTIVE.copy(
            id = UUID.fromString("20000000-0000-0000-0000-000000000003"),
            fullName = "Segundo objetivo",
            abbreviation = "SEG",
        )
        private val SCHEDULE = ScheduleCombination(
            id = UUID.fromString("30000000-0000-0000-0000-000000000001"),
            objectiveId = ACTIVE_OBJECTIVE.id,
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
            colorArgb = 0xFF1565C0.toInt(),
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        private val SHIFT = com.blackatsystems.miguardia.core.domain.model.Shift(
            id = UUID.fromString("40000000-0000-0000-0000-000000000001"),
            sourceObjectiveId = ACTIVE_OBJECTIVE.id,
            sourceScheduleCombinationId = SCHEDULE.id,
            objectiveNameSnapshot = ACTIVE_OBJECTIVE.fullName,
            objectiveAbbreviationSnapshot = ACTIVE_OBJECTIVE.abbreviation,
            objectiveAddressSnapshot = ACTIVE_OBJECTIVE.address,
            startTimeSnapshot = SCHEDULE.startTime,
            endTimeSnapshot = SCHEDULE.endTime,
            colorArgbSnapshot = SCHEDULE.colorArgb,
            position = null,
            localStartDate = LocalDate.of(2026, 8, 3),
            startAt = Instant.parse("2026-08-03T11:00:00Z"),
            endAt = Instant.parse("2026-08-03T19:00:00Z"),
            zoneId = java.time.ZoneId.of("America/Argentina/Cordoba"),
            status = com.blackatsystems.miguardia.core.domain.model.ShiftStatus.PLANNED,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
