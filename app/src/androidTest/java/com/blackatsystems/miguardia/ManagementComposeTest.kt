package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
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
import com.blackatsystems.miguardia.core.domain.model.RecentScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.DayOffDraft
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementSurfaceHost
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ObjectiveDraft
import com.blackatsystems.miguardia.ui.management.ScheduleDraft
import com.blackatsystems.miguardia.ui.management.ScheduleOption
import com.blackatsystems.miguardia.ui.management.ShiftDraft
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
        var state by mutableStateOf(
            ManagementUiState(
                surface = ManagementSurface.SHIFT_FORM,
                catalogLoaded = true,
                objectives = listOf(ACTIVE_OBJECTIVE),
                scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                shiftDraft = ShiftDraft(
                    month = month,
                    selectedDates = setOf(month.atDay(2), month.atDay(9)),
                    combinationId = SCHEDULE.id,
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = state,
                    actions = ManagementActions(
                        updatePosition = { position ->
                            state = state.copy(shiftDraft = state.shiftDraft?.copy(position = position))
                        },
                        saveShift = { policy, confirmed -> requested = policy to confirmed },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("shift-date-selector").assertDoesNotExist()
        composeRule.onNodeWithText("Una fecha").assertDoesNotExist()
        composeRule.onNodeWithText("Varias fechas").assertDoesNotExist()
        composeRule.onNodeWithTag("selected-combination-summary").assertExists()
        composeRule.onNodeWithText("Elegí objetivo y horario").assertDoesNotExist()
        composeRule.onNodeWithText("Puesto opcional").assertDoesNotExist()
        composeRule.onNodeWithText("+ Agregar puesto opcional")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Puesto opcional").performTextInput("Portería")
        composeRule.onNodeWithText("ACT · 19:00–07:00 · 2 guardias").assertExists()
        composeRule.onNodeWithText("Fechas: 02/08/2026 y 09/08/2026").assertExists()
        composeRule.onNodeWithText("Puesto: Portería").assertExists()
        composeRule.onNodeWithText("Termina al día siguiente").assertExists()
        composeRule.onNodeWithText("Revisar 2 guardias").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Confirmar 2 guardias").assertExists()
        composeRule.onAllNodesWithText("Fechas: 02/08/2026 y 09/08/2026").assertCountEquals(2)
        composeRule.onNodeWithText("Objetivo: ACT · Objetivo activo").assertExists()
        composeRule.onNodeWithText("Horario: 19:00–07:00").assertExists()
        composeRule.onAllNodesWithText("Puesto: Portería").assertCountEquals(2)
        composeRule.onNodeWithText("guardia(s)", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Guardar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(null to false, requested) }
    }

    @Test
    fun dayOffFormSupportsOneOrSeveralExplicitDates() {
        var saves = 0
        val month = YearMonth.of(2026, 8)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.DAY_OFF_FORM,
                        dayOffDraft = DayOffDraft(
                            month = month,
                            selectedDates = setOf(month.atDay(3), month.atDay(7)),
                        ),
                    ),
                    actions = ManagementActions(saveDayOffs = { saves++ }),
                )
            }
        }

        composeRule.onAllNodesWithText("Agregar francos").assertCountEquals(1)
        composeRule.onNodeWithTag("day-off-date-selector").assertDoesNotExist()
        composeRule.onNodeWithText("2 francos · 03/08/2026 y 07/08/2026").assertExists()
        composeRule.onNodeWithText("Confirmar 2 francos").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun editingShiftKeepsNotificationSettingsInsideTheForm() {
        var opened: UUID? = null
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        catalogLoaded = true,
                        scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                        shiftDraft = ShiftDraft(
                            month = YearMonth.of(2026, 8),
                            selectedDates = setOf(SHIFT.localStartDate),
                            editingShift = SHIFT,
                            combinationId = SCHEDULE.id,
                        ),
                    ),
                    actions = ManagementActions(),
                    onOpenNotifications = { opened = it.id },
                )
            }
        }

        composeRule.onNodeWithText("Avisos de esta guardia").performScrollTo().assertExists()
        composeRule.onNodeWithText("Configurar avisos").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(SHIFT.id, opened) }
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
                        catalogLoaded = true,
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
        composeRule.onNodeWithText("Usados recientemente").assertDoesNotExist()
        composeRule.onNodeWithText("Todavía no hay horarios recientes.").assertDoesNotExist()
        composeRule.onNodeWithTag("shift-preview").assertDoesNotExist()
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
    fun recentCombinationsLeadAndKeepFullObjectiveExplorerFoldedUntilRequested() {
        var state by mutableStateOf(
            ManagementUiState(
                surface = ManagementSurface.SHIFT_FORM,
                catalogLoaded = true,
                objectives = listOf(ACTIVE_OBJECTIVE, SECOND_OBJECTIVE),
                scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, SCHEDULE)),
                recent = listOf(
                    RecentScheduleCombination(
                        objective = ACTIVE_OBJECTIVE,
                        combination = SCHEDULE,
                        lastUsedAt = Instant.parse("2026-08-18T12:00:00Z"),
                    ),
                ),
                shiftDraft = ShiftDraft(
                    month = YearMonth.of(2026, 8),
                    selectedDates = setOf(LocalDate.of(2026, 8, 14)),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = state,
                    actions = ManagementActions(
                        chooseCombination = { id ->
                            state = state.copy(shiftDraft = state.shiftDraft?.copy(combinationId = id))
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Usados recientemente").assertExists()
        composeRule.onNodeWithText("Todavía no hay horarios recientes.").assertDoesNotExist()
        composeRule.onNodeWithTag("recent-combination-${SCHEDULE.id}").assertExists()
        composeRule.onNodeWithText("Segundo objetivo (SEG)").assertDoesNotExist()
        composeRule.onNodeWithText("Elegir otro objetivo u horario").assertExists()

        composeRule.onNodeWithTag("recent-combination-${SCHEDULE.id}")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("selected-combination-summary").assertExists()
        composeRule.onNodeWithText("+ Agregar puesto opcional").assertExists()
        composeRule.onNodeWithTag("shift-preview").assertExists()

        composeRule.onNodeWithText("Cambiar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("recent-combination-${SCHEDULE.id}").assertIsSelected()
        composeRule.onNodeWithText("Elegir otro objetivo u horario")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Segundo objetivo (SEG)").assertExists()
    }

    @Test
    fun shiftFormWithoutObjectivesShowsOnePrimaryStartingPointAndNoIncompletePreview() {
        var createCalls = 0
        var retryCalls = 0
        var requestedObjective: Objective? = ACTIVE_OBJECTIVE
        var state by mutableStateOf(
            ManagementUiState(
                surface = ManagementSurface.SHIFT_FORM,
                shiftDraft = ShiftDraft(
                    month = YearMonth.of(2026, 8),
                    selectedDates = setOf(LocalDate.of(2026, 8, 14)),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = state,
                    actions = ManagementActions(
                        retryCatalog = { retryCalls += 1 },
                        openObjective = { objective ->
                            createCalls += 1
                            requestedObjective = objective
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Cargando objetivos y horarios…").assertExists()
        composeRule.onNodeWithText("Creá tu primer objetivo").assertDoesNotExist()
        composeRule.onNodeWithText("Crear mi primer objetivo").assertDoesNotExist()
        state = state.copy(catalogErrorMessage = "No pudimos cargar objetivos y horarios.")
        composeRule.onNodeWithText("No pudimos cargar objetivos y horarios.").assertExists()
        composeRule.onNodeWithText("Reintentar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, retryCalls) }
        state = state.copy(catalogLoaded = true, catalogErrorMessage = null)
        composeRule.onNodeWithText("Creá tu primer objetivo").assertExists()
        composeRule.onAllNodesWithText("Crear mi primer objetivo").assertCountEquals(1)
        composeRule.onNodeWithText("Crear objetivo").assertDoesNotExist()
        composeRule.onNodeWithText("Agregá un horario").assertDoesNotExist()
        composeRule.onNodeWithText("Todavía no hay horarios recientes.").assertDoesNotExist()
        composeRule.onNodeWithTag("shift-preview").assertDoesNotExist()
        composeRule.onNodeWithText("Revisar guardia").assertDoesNotExist()
        composeRule.onNodeWithText("Puesto opcional").assertDoesNotExist()
        composeRule.onNodeWithText("Crear mi primer objetivo")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, createCalls)
            assertEquals(null, requestedObjective)
        }
    }

    @Test
    fun shiftFormWithActiveObjectivesButNoUsableSchedulesChoosesOneScheduleOwnerExactlyOnce() {
        var openScheduleCalls = 0
        var requestedObjectiveId: UUID? = null
        var requestedCombination: ScheduleCombination? = SCHEDULE
        val inactiveSchedule = SCHEDULE.copy(isActive = false)
        composeRule.setContent {
            MaterialTheme {
                ManagementSurfaceHost(
                    state = ManagementUiState(
                        surface = ManagementSurface.SHIFT_FORM,
                        catalogLoaded = true,
                        objectives = listOf(ACTIVE_OBJECTIVE, SECOND_OBJECTIVE),
                        scheduleOptions = listOf(ScheduleOption(ACTIVE_OBJECTIVE, inactiveSchedule)),
                        recent = listOf(
                            RecentScheduleCombination(
                                objective = ACTIVE_OBJECTIVE,
                                combination = inactiveSchedule,
                                lastUsedAt = Instant.parse("2026-08-18T12:00:00Z"),
                            ),
                        ),
                        shiftDraft = ShiftDraft(
                            month = YearMonth.of(2026, 8),
                            selectedDates = setOf(LocalDate.of(2026, 8, 14)),
                        ),
                    ),
                    actions = ManagementActions(
                        openSchedule = { objectiveId, combination ->
                            openScheduleCalls += 1
                            requestedObjectiveId = objectiveId
                            requestedCombination = combination
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("shift-empty-schedules").assertExists()
        composeRule.onNodeWithText("Agregá un horario").assertExists()
        composeRule.onNodeWithText("Elegí a qué objetivo querés agregarle el nuevo horario.").assertExists()
        composeRule.onNodeWithTag("add-schedule-to-${ACTIVE_OBJECTIVE.id}").assertExists()
        composeRule.onNodeWithTag("add-schedule-to-${SECOND_OBJECTIVE.id}").assertExists()
        composeRule.onNodeWithText("Crear mi primer objetivo").assertDoesNotExist()
        composeRule.onNodeWithText("Elegí objetivo y horario").assertDoesNotExist()
        composeRule.onNodeWithText("Usados recientemente").assertDoesNotExist()
        composeRule.onNodeWithText("Todavía no hay horarios recientes.").assertDoesNotExist()
        composeRule.onNodeWithText("Todavía no hay horarios para este objetivo.").assertDoesNotExist()
        composeRule.onNodeWithText("+ Agregar horario").assertDoesNotExist()
        composeRule.onNodeWithTag("shift-preview").assertDoesNotExist()
        composeRule.onNodeWithText("Puesto opcional").assertDoesNotExist()
        composeRule.onNodeWithText("Revisar guardia").assertDoesNotExist()

        composeRule.onNodeWithTag("add-schedule-to-${SECOND_OBJECTIVE.id}")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, openScheduleCalls)
            assertEquals(SECOND_OBJECTIVE.id, requestedObjectiveId)
            assertEquals(null, requestedCombination)
        }
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
                        catalogLoaded = true,
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
        composeRule.onNodeWithText("Cancelar").assertExists()
        composeRule.onNodeWithText("Agregar sólo en días libres").assertDoesNotExist()
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
                        catalogLoaded = true,
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
        composeRule.onNodeWithText("Agregar sólo en días libres").assertDoesNotExist()
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
                        catalogLoaded = true,
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
                        catalogLoaded = true,
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
