package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadContent
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftTemplateOption
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class V2ManualShiftLoadComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun v2ReadyOffersThePrimaryLoadActionAndKeepsWorkSetupSecondary() {
        var starts = 0
        var entersEditMode = 0
        setApp(
            manualActions = V2ManualShiftLoadActions(start = { starts++ }),
            onEnterEditMode = { entersEditMode++ },
        )

        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, entersEditMode)
        }
        compose.onNodeWithTag("calendar-work-setup-action").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cargar datos").assertDoesNotExist()
        compose.onNodeWithText("Editar calendario").assertDoesNotExist()
    }

    @Test
    fun v2ManualSelectionUsesTheExistingMonthGridWithoutLegacyEditTools() {
        val first = LocalDate.of(2026, 8, 10)
        val second = first.plusDays(2)
        var confirmed: Set<LocalDate>? = null
        var calendar by mutableStateOf(
            calendarState(
                interactionMode = CalendarInteractionMode.EDIT,
                days = listOf(day(first), day(second)),
            ),
        )
        setApp(
            calendarProvider = { calendar },
            manualStateProvider = { V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.SELECT_DATES,
                timelineId = TIMELINE_ID,
            ) },
            manualActions = V2ManualShiftLoadActions(confirmDates = { confirmed = it }),
            onEditSelectionChange = { selection -> calendar = calendar.copy(editSelectedDates = selection) },
            onConfirmSelection = { calendar = calendar.copy(editSelectionConfirmed = true) },
        )

        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("day-$first").performScrollTo().performClick()
        compose.onNodeWithTag("day-$second").performScrollTo().performClick()
        compose.onNodeWithText("2 días seleccionados").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("seleccionado para cargar jornadas", substring = true)
            .assertCountEquals(2)
        compose.onNodeWithContentDescription("seleccionado para editar", substring = true)
            .assertDoesNotExist()
        compose.onNodeWithTag("calendar-edit-tools").assertDoesNotExist()
        compose.onNodeWithTag("calendar-add-shift").assertDoesNotExist()

        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(setOf(first, second), confirmed)
            assertTrue(calendar.editSelectionConfirmed)
        }
    }

    @Test
    fun confirmedOrBusyLoadLocksTheGridMonthAndExit() {
        val date = LocalDate.of(2026, 8, 10)
        val calendar = calendarState(
            interactionMode = CalendarInteractionMode.EDIT,
            days = listOf(day(date)),
            editSelectedDates = setOf(date),
            editSelectionConfirmed = true,
        )
        setApp(
            calendarProvider = { calendar },
            manualStateProvider = { V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                timelineId = TIMELINE_ID,
                selectedDates = setOf(date),
                isLoading = true,
            ) },
        )

        compose.onNodeWithTag("day-$date").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mes anterior").assertIsNotEnabled()
        compose.onNodeWithText("Salir de la carga").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("calendar-edit-tools").assertDoesNotExist()
    }

    @Test
    fun loadingOrErrorCalendarCannotConfirmRestoredDatesOrStartAnotherLoad() {
        val date = LocalDate.of(2026, 8, 10)
        var calendar by mutableStateOf(
            calendarState(
                interactionMode = CalendarInteractionMode.EDIT,
                days = listOf(day(date)),
                editSelectedDates = setOf(date),
                loadState = CalendarLoadState.LOADING,
            ),
        )
        setApp(
            calendarProvider = { calendar },
            manualStateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.SELECT_DATES,
                    timelineId = TIMELINE_ID,
                )
            },
        )

        compose.onNodeWithTag("day-$date").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mes anterior").assertIsNotEnabled()

        compose.runOnIdle {
            calendar = calendar.copy(
                loadState = CalendarLoadState.ERROR,
                errorMessage = "Error ficticio recuperable",
            )
        }
        compose.onNodeWithText("Error ficticio recuperable").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-confirm-dates").performScrollTo().assertIsNotEnabled()

        compose.runOnIdle {
            calendar = calendar.copy(
                interactionMode = CalendarInteractionMode.VIEW,
                editSelectedDates = emptySet(),
            )
        }
        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun successfulSaveFinishesTheV2CalendarModeExactlyOnce() {
        var manualState by mutableStateOf(
            V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.REVIEW,
                timelineId = TIMELINE_ID,
            ),
        )
        var finishCalls = 0
        var consumeCalls = 0
        setApp(
            calendarProvider = { calendarState(interactionMode = CalendarInteractionMode.EDIT) },
            manualStateProvider = { manualState },
            manualActions = V2ManualShiftLoadActions(
                consumeSuccess = { sequence ->
                    consumeCalls++
                    if (manualState.successSequence == sequence) {
                        manualState = manualState.copy(successSequence = 0)
                    }
                },
            ),
            onFinishEditMode = { finishCalls++ },
        )
        compose.runOnIdle { assertEquals(0, finishCalls) }

        compose.runOnIdle {
            manualState = V2ManualShiftLoadUiState(
                infoMessage = "Jornada guardada.",
                successSequence = 1,
            )
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(1, finishCalls)
            assertEquals(1, consumeCalls)
            assertEquals(0, manualState.successSequence)
            manualState = manualState.copy(infoMessage = "Jornada guardada y visible.")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, finishCalls)
            assertEquals(1, consumeCalls)
        }
        compose.onNodeWithText("Jornada guardada y visible.").assertIsDisplayed()
    }

    @Test
    fun restoredCompatibleDraftReopensTheCalendarEditMode() {
        var entersEditMode = 0
        setApp(
            calendarProvider = { calendarState(interactionMode = CalendarInteractionMode.VIEW) },
            manualStateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.SELECT_DATES,
                    timelineId = TIMELINE_ID,
                )
            },
            onEnterEditMode = { entersEditMode++ },
        )
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, entersEditMode) }
    }

    @Test
    fun restoredTimelineMismatchIsDiscardedBeforeItCanBlockTheCalendar() {
        var discards = 0
        setApp(
            manualStateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.SELECT_DATES,
                    timelineId = UUID(0L, 999L),
                )
            },
            manualActions = V2ManualShiftLoadActions(discardIncompatible = { discards++ }),
        )
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, discards) }
    }

    @Test
    fun sameScheduleOptionsStayDistinctByWorkTypeAndSelectedState() {
        val first = option(typeName = "Trabajo habitual", templateId = UUID(0L, 5L))
        val second = option(typeName = "Capacitación", typeId = UUID(0L, 6L), templateId = UUID(0L, 7L))
        var chosen: UUID? = null
        var reviewRequests = 0
        var state by mutableStateOf(
            V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                timelineId = TIMELINE_ID,
                sector = WorkSector.NURSING,
                selectedDates = setOf(LocalDate.of(2026, 8, 10)),
                templateOptions = listOf(first, second),
            ),
        )
        setContent(
            stateProvider = { state },
            actions = V2ManualShiftLoadActions(
                chooseTemplate = { id ->
                    chosen = id
                    state = state.copy(selectedTemplateId = id)
                },
                requestReview = { reviewRequests++ },
            ),
        )

        compose.onNodeWithText("Trabajo habitual").assertIsDisplayed()
        compose.onNodeWithText("Capacitación").assertIsDisplayed()
        compose.onAllNodesWithText("Color #336699").assertCountEquals(2)
        compose.onNodeWithContentDescription(
            "FIC, Capacitación, de 08:00 a 16:00, color #336699, sin seleccionar",
        ).performClick()
        compose.onAllNodesWithTag("v2-template-${second.template.id}", useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithTag("v2-template-${second.template.id}").assertHasClickAction()
        compose.onNodeWithTag("v2-template-${second.template.id}").assertIsSelected()
        compose.onNodeWithContentDescription(
            "FIC, Capacitación, de 08:00 a 16:00, color #336699, seleccionado",
        ).assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-review").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(second.template.id, chosen)
            assertEquals(1, reviewRequests)
        }
    }

    @Test
    fun reviewShowsExactDatesSnapshotOvernightAndOnlyPlannedCount() {
        val option = option(
            typeName = "Guardia nocturna",
            templateId = UUID(0L, 8L),
            start = LocalTime.of(21, 0),
            end = LocalTime.of(6, 0),
        )
        val planned = setOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
        val omitted = setOf(LocalDate.of(2026, 8, 11))
        var saves = 0
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.REVIEW,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = planned + omitted,
                    templateOptions = listOf(option),
                    selectedTemplateId = option.template.id,
                    position = "Sala ficticia",
                    occupiedPolicy = OccupiedDatePolicy.KEEP_OCCUPIED,
                    plannedDates = planned,
                    omittedDates = omitted,
                    reviewFingerprint = "preview",
                )
            },
            actions = V2ManualShiftLoadActions(save = { saves++ }),
        )

        compose.onNodeWithText("2 jornadas nuevas").assertIsDisplayed()
        compose.onNodeWithText("Fechas: 10/08/2026, 12/08/2026").assertIsDisplayed()
        compose.onNodeWithText("Horario: 21:00–06:00").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-next-day").assertIsDisplayed()
        compose.onNodeWithText("Puesto o función: Sala ficticia").assertIsDisplayed()
        compose.onNodeWithText("Se conservarán sin cambios: 11/08/2026").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun consciousBackfillExplainsDatesBeforeExtending() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        var backfills = 0
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CONFIRM_BACKFILL,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = setOf(date),
                    templateOptions = listOf(selected),
                    selectedTemplateId = selected.template.id,
                    configuredFrom = date.plusDays(5),
                    backfillFrom = date,
                )
            },
            actions = V2ManualShiftLoadActions(confirmBackfill = { backfills++ }),
        )
        compose.onNodeWithTag("v2-backfill-dialog").assertIsDisplayed()
        compose.onNodeWithText("Usar desde esa fecha").performClick()
        compose.runOnIdle { assertEquals(1, backfills) }
    }

    @Test
    fun occupiedDialogOffersKeepExistingDatesWithoutImplicitReplacement() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        var occupiedChoice: OccupiedDatePolicy? = null
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = setOf(date, date.plusDays(1)),
                    occupiedDates = setOf(date),
                    templateOptions = listOf(selected),
                    selectedTemplateId = selected.template.id,
                )
            },
            actions = V2ManualShiftLoadActions(chooseOccupiedPolicy = { occupiedChoice = it }),
        )
        compose.onNodeWithTag("v2-occupied-dialog").assertIsDisplayed()
        compose.onNodeWithText("Agregar sólo en días libres").performClick()
        compose.runOnIdle { assertEquals(OccupiedDatePolicy.KEEP_OCCUPIED, occupiedChoice) }
    }

    @Test
    fun singleOccupiedDateOffersOnlyReplaceSecondShiftOrCancel() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = setOf(date),
                    occupiedDates = setOf(date),
                    templateOptions = listOf(selected),
                    selectedTemplateId = selected.template.id,
                )
            },
            actions = V2ManualShiftLoadActions(),
        )

        compose.onNodeWithText("Reemplazar").assertIsDisplayed()
        compose.onNodeWithText("Agregar segunda jornada").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Agregar sólo en días libres").assertDoesNotExist()
    }

    @Test
    fun warningDialogRequiresAnExplicitAcknowledgement() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        var acknowledged = 0
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CONFIRM_WARNINGS,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = setOf(date),
                    templateOptions = listOf(selected),
                    selectedTemplateId = selected.template.id,
                    warnings = listOf(
                        "Las jornadas ficticias se superponen.",
                        "Hay 9 h 0 min de descanso.",
                    ),
                )
            },
            actions = V2ManualShiftLoadActions(confirmWarnings = { acknowledged++ }),
        )
        compose.onNodeWithTag("v2-warning-dialog").assertIsDisplayed()
        compose.onNodeWithText("se superponen", substring = true).assertIsDisplayed()
        compose.onNodeWithText("descanso", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Continuar igualmente").performClick()
        compose.runOnIdle { assertEquals(1, acknowledged) }
    }

    @Test
    fun recoverableErrorKeepsTheDraftAndRetryIsExplicit() {
        val selected = option()
        val date = LocalDate.of(2026, 8, 10)
        var retries = 0
        setContent(
            stateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                    timelineId = TIMELINE_ID,
                    sector = WorkSector.NURSING,
                    selectedDates = setOf(date),
                    templateOptions = listOf(selected),
                    selectedTemplateId = selected.template.id,
                    position = "Puesto ficticio",
                    errorMessage = "No pudimos preparar la revisión.",
                )
            },
            actions = V2ManualShiftLoadActions(retry = { retries++ }),
        )

        compose.onNodeWithText("10/08/2026").assertIsDisplayed()
        compose.onNodeWithText("Puesto ficticio").assertIsDisplayed()
        compose.onNodeWithText("No pudimos preparar la revisión.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun v2CalendarUsesNeutralJornadaVocabulary() {
        val date = LocalDate.of(2026, 8, 10)
        setApp(
            calendarProvider = { calendarState(days = listOf(dayWithShift(date))) },
        )

        compose.onNodeWithContentDescription("jornada FIC", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("guardia FIC", substring = true).assertDoesNotExist()
    }

    @Test
    fun reviewActionRemainsReachableAcrossThemesAndInternalZooms() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        var darkTheme by mutableStateOf(true)
        var appZoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = darkTheme, appZoom = appZoom) {
                MiGuardiaApp(
                    calendarState = calendarState(
                        interactionMode = CalendarInteractionMode.EDIT,
                        days = listOf(day(date)),
                        editSelectedDates = setOf(date),
                        editSelectionConfirmed = true,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    v2ManualShiftLoadState = V2ManualShiftLoadUiState(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        timelineId = TIMELINE_ID,
                        sector = WorkSector.NURSING,
                        selectedDates = setOf(date),
                        templateOptions = listOf(selected),
                        selectedTemplateId = selected.template.id,
                    ),
                    workSetupState = readyWorkSetupState(),
                    appZoom = appZoom,
                )
            }
        }

        listOf(false, true).forEach { dark ->
            AppZoom.entries.forEach { zoom ->
                compose.runOnIdle {
                    darkTheme = dark
                    appZoom = zoom
                }
                compose.onNodeWithTag("v2-manual-review")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsEnabled()
            }
        }
    }

    private fun setContent(
        stateProvider: () -> V2ManualShiftLoadUiState,
        actions: V2ManualShiftLoadActions,
    ) {
        compose.setContent {
            MiGuardiaTheme {
                V2ManualShiftLoadContent(
                    state = stateProvider(),
                    calendarSelectedDates = stateProvider().selectedDates,
                    calendarSelectionConfirmed = stateProvider().stage != V2ManualShiftLoadStage.SELECT_DATES,
                    calendarContentReady = true,
                    actions = actions,
                    onConfirmCalendarSelection = {},
                    onModifyCalendarSelection = {},
                )
            }
        }
    }

    private fun setApp(
        calendarProvider: () -> CalendarUiState = { calendarState() },
        manualStateProvider: () -> V2ManualShiftLoadUiState = { V2ManualShiftLoadUiState() },
        manualActions: V2ManualShiftLoadActions = V2ManualShiftLoadActions(),
        onEnterEditMode: () -> Unit = {},
        onEditSelectionChange: (Set<LocalDate>) -> Unit = {},
        onConfirmSelection: () -> Unit = {},
        onFinishEditMode: () -> Unit = {},
        workSetupStateProvider: () -> WorkSetupUiState = { readyWorkSetupState() },
    ) {
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarProvider(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    onEnterCalendarEditMode = { onEnterEditMode() },
                    onEditSelectionChange = onEditSelectionChange,
                    onConfirmEditSelection = onConfirmSelection,
                    onFinishCalendarEditMode = onFinishEditMode,
                    v2ManualShiftLoadState = manualStateProvider(),
                    v2ManualShiftLoadActions = manualActions,
                    workSetupState = workSetupStateProvider(),
                )
            }
        }
    }

    private fun option(
        typeName: String = "Trabajo habitual",
        typeId: UUID = UUID(0L, 4L),
        templateId: UUID = UUID(0L, 5L),
        start: LocalTime = LocalTime.of(8, 0),
        end: LocalTime = LocalTime.of(16, 0),
    ): V2ManualShiftTemplateOption {
        val objective = Objective(
            id = UUID(0L, 2L),
            fullName = "Lugar ficticio",
            abbreviation = "FIC",
            address = null,
            note = null,
            isActive = true,
            createdAt = NOW,
            updatedAt = NOW,
        )
        val place = WorkPlace(UUID(0L, 3L), TIMELINE_ID, WorkSector.NURSING, objective.id, true, NOW, NOW)
        val type = WorkType.create(typeId, TIMELINE_ID, WorkSector.NURSING, typeName, NOW)
        val template = WorkTemplate(
            id = templateId,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            workPlaceId = place.id,
            objectiveId = objective.id,
            workTypeId = type.id,
            startTime = start,
            endTime = end,
            colorArgb = 0xFF336699.toInt(),
            isActive = true,
            legacyScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        return V2ManualShiftTemplateOption(objective, place, type, template)
    }

    private fun readyWorkSetupState(): WorkSetupUiState {
        val revision = EffectiveRevision(
            id = UUID(0L, 1L),
            effectiveFrom = LocalDate.of(2026, 8, 1),
            value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        return WorkSetupUiState(
            rootState = WorkSetupState.V2Ready(TIMELINE_ID, revision),
            selectedSector = WorkSector.NURSING,
            catalog = WorkCatalog(TIMELINE_ID, WorkSector.NURSING, emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }

    private fun calendarState(
        interactionMode: CalendarInteractionMode = CalendarInteractionMode.VIEW,
        days: List<CalendarDay> = emptyList(),
        editSelectedDates: Set<LocalDate> = emptySet(),
        editSelectionConfirmed: Boolean = false,
        loadState: CalendarLoadState = CalendarLoadState.CONTENT,
    ) = CalendarUiState(
        visibleMonth = YearMonth.of(2026, 8),
        referenceInstant = NOW,
        days = days,
        hasAnyShifts = false,
        hasAnyShiftsLoaded = true,
        loadState = loadState,
        interactionMode = interactionMode,
        editSelectedDates = editSelectedDates,
        editSelectionConfirmed = editSelectionConfirmed,
    )

    private fun day(date: LocalDate) = CalendarDay(
        date = date,
        shifts = emptyList(),
        explicitStatus = null,
        hasMedicalLeave = false,
    )

    private fun dayWithShift(date: LocalDate): CalendarDay {
        val shift = Shift(
            id = UUID(0L, 200L),
            startAt = Instant.parse("2026-08-10T11:00:00Z"),
            endAt = Instant.parse("2026-08-10T19:00:00Z"),
            zoneId = ZoneId.of("America/Argentina/Cordoba"),
            localStartDate = date,
            objectiveNameSnapshot = "Lugar ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = UUID(0L, 2L),
            sourceScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        return CalendarDay(
            date = date,
            shifts = listOf(CalendarShift(shift, ShiftTemporalStatus.UPCOMING)),
            explicitStatus = null,
            hasMedicalLeave = false,
        )
    }

    private companion object {
        val TIMELINE_ID: UUID = UUID(0L, 100L)
        val NOW: Instant = Instant.parse("2026-08-22T12:00:00Z")
    }
}
