package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
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
import com.blackatsystems.miguardia.ui.management.ManualWorkPlaceOption
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationRequest
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
    fun oneAndThreeDatesStayCompactWithoutExpansionControl() {
        val first = LocalDate.of(2026, 10, 2)
        var state by mutableStateOf(V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(first)))
        setContent({ state }, V2ManualShiftLoadActions())
        compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains("02/10/26")
        compose.onNodeWithTag("manual-expand-dates").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(selectedDates = listOf(first.plusDays(2), first, first.plusDays(1), first).toSet()) }
        compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains("02/10/26 - 03/10/26 - 04/10/26")
        compose.onNodeWithTag("manual-expand-dates").assertDoesNotExist()
    }

    @Test
    fun positionCanBeAddedEditedCollapsedAndRemovedWithoutChangingCatalog() {
        val selected = option()
        var state by mutableStateOf(V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)),
            templateOptions = listOf(selected), selectedTemplateId = selected.template.id))
        setContent({ state }, V2ManualShiftLoadActions(updatePosition = { state = state.copy(position = it) }))
        compose.onNodeWithTag("v2-manual-position").assertDoesNotExist()
        compose.onNodeWithTag("manual-toggle-position").assertTextContains("Agregar puesto o función")
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        compose.onNodeWithTag("manual-toggle-position").assertTextContains("Contraer puesto o función")
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performTextInput("Sala sintética")
        compose.onNodeWithTag("v2-manual-position").performTextReplacement("Recepción ficticia")
        compose.onNodeWithText("Se aplicará a todas las jornadas de esta carga.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        compose.onNodeWithTag("manual-toggle-position").assertTextContains("Editar puesto o función")
        compose.onNodeWithTag("manual-position-summary").performScrollTo().assertTextContains("Puesto o función: Recepción ficticia")
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        compose.onNodeWithTag("manual-remove-position").performScrollTo().performClick()
        compose.onNodeWithTag("manual-position-summary").assertDoesNotExist()
        compose.runOnIdle { assertEquals("", state.position); assertEquals(listOf(selected), state.templateOptions) }
    }

    @Test
    fun noteCanCollapseWithoutLosingTextOrImagesAndFullDraftStillExplainsNewSelections() {
        val selected = option()
        val now = Instant.parse("2026-10-02T12:00:00Z")
        val images = (0 until 30).map { index ->
            val id = UUID(0, 9500L + index)
            ShiftNoteAttachment(id, UUID(0, 9499), "note_$id.jpg", "image/jpeg", 100, 10, 10, index, now, now)
        }
        var state by mutableStateOf(V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(LocalDate.of(2026, 10, 2)),
            templateOptions = listOf(selected), selectedTemplateId = selected.template.id,
            noteBody = "Nota sintética", noteAttachments = images))
        setContent({ state }, V2ManualShiftLoadActions(removeNoteImage = { id ->
            state = state.copy(noteAttachments = state.noteAttachments.filterNot { it.id == id })
        }))
        compose.onNodeWithTag("manual-add-note").performScrollTo().assertTextContains("Editar nota").performClick()
        compose.onNodeWithTag("manual-add-note").assertTextContains("Contraer nota")
        compose.onNodeWithTag("manual-note-images").performScrollTo().assertTextContains("Agregar imágenes").assertIsEnabled()
        compose.onAllNodesWithText("/30", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("note-draft-image-1").performScrollTo().assertExists()
        compose.onNodeWithText("Quitar imagen 1", substring = false).performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(images.drop(1), state.noteAttachments) }
        compose.onNodeWithTag("manual-add-note").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").assertDoesNotExist()
        compose.onNodeWithTag("manual-note-summary").performScrollTo().assertTextContains("Nota sintética")
        compose.onNodeWithTag("manual-add-note").performScrollTo().assertTextContains("Editar nota").performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().assertTextContains("Nota sintética")
        compose.runOnIdle { assertEquals(images.drop(1), state.noteAttachments) }
    }

    @Test
    fun inlineCatalogIsAlphabeticalAndCanAddScheduleToExistingInstitution() {
        val selected = option()
        val alphaObjective = selected.objective.copy(id = UUID(0, 333), fullName = "Álamo ficticio", abbreviation = "AL")
        val alphaPlace = selected.workPlace.copy(id = UUID(0, 334), objectiveId = alphaObjective.id)
        val state = V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(LocalDate.of(2026, 10, 2)),
            templateOptions = listOf(selected), places = listOf(
                ManualWorkPlaceOption(selected.objective, selected.workPlace), ManualWorkPlaceOption(alphaObjective, alphaPlace)))
        var target: UUID? = null
        setContent({ state }, V2ManualShiftLoadActions(beginTemplateForPlace = { target = it }))
        openCatalog()
        compose.onNodeWithTag("manual-inline-catalog").assertExists()
        val alphaTop = compose.onNodeWithTag("manual-place-${alphaPlace.id}").fetchSemanticsNode().boundsInRoot.top
        val otherTop = compose.onNodeWithTag("manual-place-${selected.workPlace.id}").fetchSemanticsNode().boundsInRoot.top
        assertTrue(alphaTop < otherTop)
        compose.onNodeWithTag("manual-place-${selected.workPlace.id}").performScrollTo().performClick()
        compose.onNodeWithTag("v2-template-${selected.template.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-create-template").performScrollTo().assertTextContains("Agregar horario").performClick()
        compose.runOnIdle { assertEquals(selected.workPlace.id, target); assertEquals(null, state.selectedTemplateId) }
    }

    @Test
    fun unifiedFormDatesExpandAndCollapseWithoutChangingSelectionOrSaving() {
        val dates = setOf(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 14))
        val state = V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = dates)
        var saves = 0
        var selections = 0
        setContent({ state }, V2ManualShiftLoadActions(requestSave = { saves++ }, confirmDates = { selections++ }))
        compose.onNodeWithText("Cargando jornada para los días:").assertIsDisplayed()
        compose.onNodeWithText("Elegí un lugar, tipo y horario").assertDoesNotExist()
        compose.onNodeWithTag("v2-manual-exact-dates").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
                listOf("Lunes 10/08/26, Miércoles 12/08/26, Jueves 13/08/26, Viernes 14/08/26")))
        compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains("10/08/26 - 12/08/26 - 13/08/26")
        compose.onNodeWithText("Mostrar todos").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-exact-dates").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Fechas contraídas"))
        compose.onNodeWithTag("manual-expand-dates").performClick()
        compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains("Lunes 10/08/26\nMiércoles 12/08/26\nJueves 13/08/26\nViernes 14/08/26")
        compose.onNodeWithText("Mostrar menos").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-exact-dates").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Fechas desplegadas"))
        compose.onNodeWithTag("manual-expand-dates").performClick()
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(dates, state.selectedDates); assertEquals(0, saves); assertEquals(0, selections) }
    }

    @Test
    fun catalogCancellationKeepsTheOptionalNoteAndDoesNotSubmit() {
        val option = option()
        var state by mutableStateOf(V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(LocalDate.of(2026, 8, 10)),
            templateOptions = listOf(option), selectedTemplateId = option.template.id))
        var saves = 0
        var choices = 0
        setContent({ state }, V2ManualShiftLoadActions(
            updateNote = { state = state.copy(noteBody = it) }, requestSave = { saves++ }, chooseTemplate = { choices++ }))
        compose.onNodeWithTag("manual-add-note").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().performTextInput("Nota ficticia sin confirmar")
        openCatalog(option.workPlace.id)
        compose.onNodeWithTag("v2-template-${option.template.id}").assertIsSelected()
        compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().assertTextContains("Nota ficticia sin confirmar")
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(option.template.id, state.selectedTemplateId)
            assertEquals("Nota ficticia sin confirmar", state.noteBody)
            assertEquals(0, saves); assertEquals(0, choices)
        }
    }

    @Test
    fun institutionWithoutSchedulesOffersItsFirstScheduleWithoutSilentSelection() {
        val option = option()
        val state = V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            timelineId = TIMELINE_ID, selectedDates = setOf(LocalDate.of(2026, 8, 10)),
            places = listOf(ManualWorkPlaceOption(option.objective, option.workPlace)))
        var target: UUID? = null
        setContent({ state }, V2ManualShiftLoadActions(beginTemplateForPlace = { target = it }))
        openCatalog(option.workPlace.id)
        compose.onNodeWithText("Agregar horario").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(option.workPlace.id, target); assertEquals(null, state.selectedTemplateId) }
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun v2ReadyKeepsCalendarActionsInTheExistingDrawer() {
        var starts = 0
        var entersEditMode = 0
        setApp(
            manualActions = V2ManualShiftLoadActions(start = { starts++ }),
            onEnterEditMode = { entersEditMode++ },
        )

        compose.onNodeWithTag("calendar-work-setup-action").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-repeat-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("main-menu-button").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("drawer-action-work-setup").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("drawer-action-calendar-repeat").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("drawer-action-calendar-load").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        awaitDrawerClosedAndCallback { starts == 1 && entersEditMode == 1 }
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, entersEditMode)
        }
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

        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("day-$first").performClick()
        compose.onNodeWithTag("day-$second").performClick()
        compose.onNodeWithContentDescription("2 días seleccionados").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("seleccionado para cargar jornadas", substring = true)
            .assertCountEquals(2)
        compose.onNodeWithContentDescription("seleccionado para editar", substring = true)
            .assertDoesNotExist()
        compose.onNodeWithTag("calendar-edit-tools").assertDoesNotExist()
        compose.onNodeWithTag("calendar-add-shift").assertDoesNotExist()

        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(setOf(first, second), calendar.editSelectedDates)
            assertEquals(setOf(first, second), confirmed)
            assertTrue(calendar.editSelectionConfirmed)
        }
    }

    @Test
    fun confirmedOrBusyLoadLocksTheGridMonthAndExit() {
        val date = LocalDate.of(2026, 8, 10)
        var previousCalls = 0
        var nextCalls = 0
        var cancels = 0
        var finishCalls = 0
        var calendar by mutableStateOf(calendarState(
            interactionMode = CalendarInteractionMode.EDIT,
            days = listOf(day(date)),
            editSelectedDates = setOf(date),
            editSelectionConfirmed = true,
        ))
        val initialMonth = calendar.visibleMonth
        setApp(
            calendarProvider = { calendar },
            onPreviousMonth = { previousCalls++; calendar = calendar.copy(visibleMonth = calendar.visibleMonth.minusMonths(1)) },
            onNextMonth = { nextCalls++; calendar = calendar.copy(visibleMonth = calendar.visibleMonth.plusMonths(1)) },
            manualStateProvider = { V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                timelineId = TIMELINE_ID,
                selectedDates = setOf(date),
                isLoading = true,
            ) },
            manualActions = V2ManualShiftLoadActions(cancel = { cancels++ }),
            onFinishEditMode = { finishCalls++ },
        )

        compose.onNodeWithTag("day-$date").assertDoesNotExist()
        assertMonthNavigationBlocked()
        compose.onNodeWithTag("manual-cancel-load").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText("¿Descartar esta carga?").assertDoesNotExist()
        compose.onNodeWithTag("calendar-edit-tools").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, previousCalls); assertEquals(0, nextCalls)
            assertEquals(initialMonth, calendar.visibleMonth)
            assertEquals(setOf(date), calendar.editSelectedDates)
            assertTrue(calendar.editSelectionConfirmed)
            assertEquals(0, cancels); assertEquals(0, finishCalls)
        }
    }

    @Test
    fun cancelLoadRequiresConfirmationBeforeDiscardingTheDraft() {
        val date = LocalDate.of(2026, 8, 10)
        var cancels = 0
        var finishCalls = 0
        setApp(
            calendarProvider = {
                calendarState(
                    interactionMode = CalendarInteractionMode.EDIT,
                    days = listOf(day(date)),
                    editSelectedDates = setOf(date),
                    editSelectionConfirmed = true,
                )
            },
            manualStateProvider = {
                V2ManualShiftLoadUiState(
                    stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                    timelineId = TIMELINE_ID,
                    selectedDates = setOf(date),
                )
            },
            manualActions = V2ManualShiftLoadActions(cancel = { cancels++ }),
            onFinishEditMode = { finishCalls++ },
        )

        compose.onNodeWithTag("manual-cancel-load").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("¿Descartar esta carga?").assertIsDisplayed()
        compose.onNodeWithText("Seguir cargando").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("¿Descartar esta carga?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, cancels)
            assertEquals(0, finishCalls)
        }

        compose.onNodeWithTag("manual-cancel-load").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("¿Descartar esta carga?").assertIsDisplayed()
        compose.onNodeWithText("Descartar carga").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("¿Descartar esta carga?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, cancels)
            assertEquals(1, finishCalls)
        }
    }

    @Test
    fun loadingOrErrorCalendarCannotConfirmRestoredDatesOrStartAnotherLoad() {
        val date = LocalDate.of(2026, 8, 10)
        var previousCalls = 0
        var nextCalls = 0
        var starts = 0
        var confirmCalls = 0
        var manualConfirmCalls = 0
        var dayOffCalls = 0
        var calendar by mutableStateOf(
            calendarState(
                interactionMode = CalendarInteractionMode.EDIT,
                days = listOf(day(date)),
                editSelectedDates = setOf(date),
                loadState = CalendarLoadState.LOADING,
            ),
        )
        val initialMonth = calendar.visibleMonth
        var manual by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.SELECT_DATES,
            timelineId = TIMELINE_ID,
        ))
        setApp(
            calendarProvider = { calendar },
            manualStateProvider = { manual },
            manualActions = V2ManualShiftLoadActions(start = { starts++ }, confirmDates = { manualConfirmCalls++ },
                requestDayOff = { _, _ -> dayOffCalls++ }),
            onConfirmSelection = { confirmCalls++ },
            onPreviousMonth = { previousCalls++; calendar = calendar.copy(visibleMonth = calendar.visibleMonth.minusMonths(1)) },
            onNextMonth = { nextCalls++; calendar = calendar.copy(visibleMonth = calendar.visibleMonth.plusMonths(1)) },
        )

        compose.onNodeWithTag("day-$date").assertDoesNotExist()
        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-remove-day-off").performScrollTo().assertIsNotEnabled()
        assertMonthNavigationBlocked()
        compose.runOnIdle {
            assertEquals(0, previousCalls); assertEquals(0, nextCalls)
            assertEquals(initialMonth, calendar.visibleMonth)
            assertEquals(setOf(date), calendar.editSelectedDates)
            assertEquals(0, confirmCalls); assertEquals(0, manualConfirmCalls); assertEquals(0, dayOffCalls)
        }

        compose.runOnIdle {
            calendar = calendar.copy(
                loadState = CalendarLoadState.ERROR,
                errorMessage = "Error ficticio recuperable",
            )
        }
        compose.onNodeWithText("Error ficticio recuperable").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-remove-day-off").performScrollTo().assertIsNotEnabled()
        assertMonthNavigationBlocked()
        compose.runOnIdle {
            assertEquals(0, previousCalls); assertEquals(0, nextCalls)
            assertEquals(initialMonth, calendar.visibleMonth)
            assertEquals(setOf(date), calendar.editSelectedDates)
            assertEquals(0, confirmCalls); assertEquals(0, manualConfirmCalls); assertEquals(0, dayOffCalls)
        }

        compose.runOnIdle {
            // End the simulated current draft before testing a NEW load from ERROR/VIEW.
            manual = V2ManualShiftLoadUiState()
            calendar = calendar.copy(
                interactionMode = CalendarInteractionMode.VIEW,
                editSelectedDates = emptySet(),
            )
        }
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("main-menu-button").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("drawer-action-calendar-load").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithTag("main-navigation-drawer").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, starts)
            assertEquals(0, previousCalls); assertEquals(0, nextCalls)
            assertEquals(initialMonth, calendar.visibleMonth)
            assertEquals(CalendarLoadState.ERROR, calendar.loadState)
        }
    }

    @Test
    fun successfulSaveFinishesTheV2CalendarModeExactlyOnce() {
        var calendar by mutableStateOf(calendarState(interactionMode = CalendarInteractionMode.EDIT))
        var manualState by mutableStateOf(
            V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.REVIEW,
                timelineId = TIMELINE_ID,
            ),
        )
        var finishCalls = 0
        var consumeCalls = 0
        setApp(
            calendarProvider = { calendar },
            manualStateProvider = { manualState },
            manualActions = V2ManualShiftLoadActions(
                consumeSuccess = { sequence ->
                    consumeCalls++
                    if (manualState.successSequence == sequence) {
                        manualState = manualState.copy(successSequence = 0)
                    }
                },
            ),
            onFinishEditMode = {
                finishCalls++
                calendar = calendar.copy(interactionMode = CalendarInteractionMode.VIEW)
            },
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
        compose.onNodeWithTag("calendar-header-notice").assertIsDisplayed().performClick()
        compose.onNodeWithText("Jornada guardada y visible.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(1, finishCalls)
            assertEquals(1, consumeCalls)
        }
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
    fun sameScheduleOptionsStayDistinctAndPrimarySaveDoesNotRequireOpeningThePreview() {
        val first = option(typeName = "Trabajo habitual", templateId = UUID(0L, 5L))
        val second = option(typeName = "Capacitación", typeId = UUID(0L, 6L), templateId = UUID(0L, 7L))
        var chosen: UUID? = null
        var saveRequests = 0
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
                requestSave = { saveRequests++ },
                requestReview = { reviewRequests++ },
            ),
        )

        openCatalog(first.workPlace.id)
        compose.onNodeWithText("Trabajo habitual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Capacitación").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Color #336699").assertCountEquals(0)
        compose.onNodeWithContentDescription(
            "FIC, Capacitación, de 08:00 a 16:00, sin seleccionar",
        ).performScrollTo().performClick()
        compose.onNodeWithTag("v2-template-${second.template.id}").assertDoesNotExist()
        openCatalog(second.workPlace.id)
        compose.onAllNodesWithTag("v2-template-${second.template.id}", useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithTag("v2-template-${second.template.id}").assertHasClickAction()
        compose.onNodeWithTag("v2-template-${second.template.id}").assertIsSelected()
        compose.onNodeWithContentDescription(
            "FIC, Capacitación, de 08:00 a 16:00, seleccionado",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        compose.onNodeWithTag("v2-manual-review").assertExists()
        compose.onNodeWithTag("v2-manual-save-direct").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(second.template.id, chosen)
            assertEquals(1, saveRequests)
            assertEquals(0, reviewRequests)
        }
        compose.onNodeWithTag("v2-manual-review").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
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

        compose.onNodeWithText("Sobrescribir").assertIsDisplayed()
        compose.onNodeWithText("Superponer").assertIsDisplayed()
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

        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        compose.onNodeWithText("10/08/26").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Puesto ficticio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No pudimos preparar la revisión.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
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
    fun directSaveActionRemainsReachableAcrossThemesAndAndroidFontScales() {
        val date = LocalDate.of(2026, 8, 10)
        val selected = option()
        var darkTheme by mutableStateOf(true)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = darkTheme, fontScale = fontScale) {
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
                    )
            }
        }

        listOf(false, true).forEach { dark ->
            TestFontScale.entries.forEach { selectedScale ->
                compose.runOnIdle {
                    darkTheme = dark
                    fontScale = selectedScale
                }
                compose.onNodeWithTag("v2-manual-save-direct")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun emptyCatalogOffersCreationAndBlocksRepeatedLaunchWithoutInventingAPlace() {
        var state by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE, timelineId = TIMELINE_ID,
            sector = WorkSector.MEDICINE, selectedDates = setOf(LocalDate.of(2026, 8, 10)),
        ))
        val launches = mutableListOf<WorkCreationKind>()
        setContent({ state }, V2ManualShiftLoadActions(beginCreation = { launches += it }))
        openCatalog()
        compose.onNodeWithTag("v2-manual-create-place").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(WorkCreationKind.PLACE_AND_TEMPLATE), launches)
            state = state.copy(creationRequest = WorkCreationRequest(UUID(0, 90), TIMELINE_ID, WorkCreationKind.TEMPLATE))
        }
        compose.onNodeWithTag("manual-institutions").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-manual-create-template").assertDoesNotExist()
        compose.onNodeWithTag("v2-manual-modify-dates").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun contextualButtonsStayReachableForEverySectorThemeAndAndroidFontScale() {
        var sector by mutableStateOf(WorkSector.PRIVATE_SECURITY)
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    V2ManualShiftLoadContent(
                        V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                            sector = sector, selectedDates = setOf(LocalDate.of(2026, 8, 10)), templateOptions = listOf(option())),
                        emptySet(), true, true, V2ManualShiftLoadActions(), {}, {},
                    )
                }
            }
        }
        WorkSector.entries.forEach { currentSector -> listOf(false, true).forEach { currentDark ->
            TestFontScale.entries.forEach { currentFontScale ->
                compose.runOnIdle { sector = currentSector; dark = currentDark; fontScale = currentFontScale }
                openCatalog(option().workPlace.id)
                compose.onNodeWithTag("v2-manual-create-place").performScrollTo().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithTag("v2-manual-create-template").performScrollTo().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
            }
        } }
    }

    @Test
    fun archivingLastTemplateKeepsSameTimelineLoadAndConfirmedCalendarSelection() {
        val date = LocalDate.of(2026, 8, 10)
        var work by mutableStateOf(readyWorkSetupState())
        var discarded = 0
        var finished = 0
        setApp(
            calendarProvider = { calendarState(interactionMode = CalendarInteractionMode.EDIT,
                days = listOf(day(date)), editSelectedDates = setOf(date), editSelectionConfirmed = true) },
            manualStateProvider = { V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                timelineId = TIMELINE_ID, sector = WorkSector.NURSING, selectedDates = setOf(date)) },
            manualActions = V2ManualShiftLoadActions(discardIncompatible = { discarded++ }),
            onFinishEditMode = { finished++ },
            workSetupStateProvider = { work },
        )
        compose.runOnIdle {
            val ready = work.rootState as WorkSetupState.V2Ready
            work = work.copy(rootState = WorkSetupState.V2NeedsFirstSet(ready.timelineId, ready.configurationRevision,
                setOf(com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE)))
        }
        openCatalog()
        compose.onNodeWithTag("v2-manual-create-place").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        compose.onNodeWithTag("work-setup-calendar-guide").assertDoesNotExist()
        compose.onNodeWithTag("day-$date").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, discarded); assertEquals(0, finished) }
    }

    @Test
    fun dayOffSelectionNeedsDatesButNeverNeedsAPlaceOrTemplate() {
        val first = LocalDate.of(2026, 8, 10)
        var state by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = TIMELINE_ID,
        ))
        val requests = mutableListOf<Pair<Set<LocalDate>, Boolean>>()
        setContent({ state }, V2ManualShiftLoadActions(requestDayOff = { dates, mark -> requests += dates to mark }))
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-remove-day-off").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(selectedDates = setOf(first)) }
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { state = state.copy(selectedDates = setOf(first, first.plusDays(2))) }
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(setOf(first) to true, setOf(first, first.plusDays(2)) to true), requests)
            assertTrue(state.templateOptions.isEmpty())
        }
    }

    @Test
    fun dayOffConfirmationAndCancellationAreExplicitForExactDates() {
        val dates = setOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
        var state by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = TIMELINE_ID, selectedDates = dates,
        ))
        var confirms = 0
        var cancels = 0
        setContent({ state }, V2ManualShiftLoadActions(
            requestDayOff = { selected, mark -> state = state.copy(selectedDates = selected, pendingDayOff = mark) },
            confirmDayOff = { confirms++ },
            cancelDayOff = { cancels++; state = state.copy(pendingDayOff = null) },
        ))
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().performClick()
        compose.onNodeWithText("10/08/2026, 12/08/2026").assertExists()
        compose.onNodeWithText("Si alguno tiene una jornada", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, confirms) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, confirms); assertEquals(dates, state.selectedDates) }
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().performClick()
        compose.onNodeWithTag("v2-confirm-day-off").performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun removingDayOffExplainsPreservationAndUsesTheRemoveAction() {
        val dates = setOf(LocalDate.of(2026, 8, 10))
        var state by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = TIMELINE_ID, selectedDates = dates,
        ))
        var requested: Pair<Set<LocalDate>, Boolean>? = null
        var confirms = 0
        setContent({ state }, V2ManualShiftLoadActions(
            requestDayOff = { selected, mark -> requested = selected to mark; state = state.copy(pendingDayOff = mark) },
            confirmDayOff = { confirms++ },
        ))
        compose.onNodeWithTag("v2-remove-day-off").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Las jornadas y otras marcas se conservan.", substring = true).assertExists()
        compose.runOnIdle { assertEquals(dates to false, requested); assertEquals(0, confirms) }
        compose.onNodeWithTag("v2-confirm-day-off").performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun dayOffErrorIsVisibleAndSavingDisablesConfirmationAndCancellation() {
        var state by mutableStateOf(V2ManualShiftLoadUiState(
            stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = TIMELINE_ID,
            selectedDates = setOf(LocalDate.of(2026, 8, 10)), pendingDayOff = true,
            errorMessage = "Hay una jornada en la selección. No se cambió ningún día.",
        ))
        var confirmations = 0
        setContent({ state }, V2ManualShiftLoadActions(confirmDayOff = { confirmations++ }))
        compose.onAllNodesWithText("Hay una jornada en la selección. No se cambió ningún día.")[0].assertExists()
        compose.onNodeWithTag("v2-confirm-day-off").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmations); state = state.copy(isSaving = true) }
        compose.onNodeWithTag("v2-confirm-day-off").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        compose.onNodeWithText("Guardando…").assertExists()
    }

    @Test
    fun dayOffActionsRemainReachableAcrossThemesAndAndroidFontScalesWithoutCatalog() {
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    V2ManualShiftLoadContent(
                        V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = TIMELINE_ID),
                        setOf(LocalDate.of(2026, 8, 10)), false, true, V2ManualShiftLoadActions(), {}, {},
                    )
                }
            }
        }
        listOf(false, true).forEach { currentDark -> TestFontScale.entries.forEach { currentFontScale ->
            compose.runOnIdle { dark = currentDark; fontScale = currentFontScale }
            compose.onNodeWithTag("v2-mark-day-off").performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("v2-remove-day-off").performScrollTo().assertIsDisplayed().assertIsEnabled()
        } }
    }

    @Test
    fun needsFirstWorkSetStillOffersDayOffLoading() {
        val work = readyWorkSetupState()
        val ready = work.rootState as WorkSetupState.V2Ready
        var starts = 0
        setApp(
            workSetupStateProvider = { work.copy(rootState = WorkSetupState.V2NeedsFirstSet(
                ready.timelineId, ready.configurationRevision,
                setOf(com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE),
            )) },
            manualActions = V2ManualShiftLoadActions(start = { starts++ }),
        )
        compose.onNodeWithTag("main-menu-button").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("drawer-action-calendar-load").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        awaitDrawerClosedAndCallback { starts == 1 }
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    fun dayDetailStartsLoadWithOnlyItsExactDate() {
        val date = LocalDate.of(2026, 8, 12)
        var starts = 0
        var dismissed = 0
        var initialDate: LocalDate? = null
        var confirmedDates: Set<LocalDate>? = null
        setApp(
            calendarProvider = { calendarState(days = listOf(day(date))).copy(detailDate = date) },
            manualActions = V2ManualShiftLoadActions(start = { starts++ }, confirmDates = { confirmedDates = it }),
            onEnterEditDate = { initialDate = it },
            onDismissDate = { dismissed++ },
        )
        compose.onNodeWithText("Editar día").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("day-detail-manage-day-off").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(date, initialDate)
            assertEquals(setOf(date), confirmedDates)
            assertEquals(1, starts)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun conflictAndClearDialogsRemainOperableAcrossThemesAndAndroidFontScales() {
        val date = LocalDate.of(2026, 8, 10)
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var clear by mutableStateOf(false)
        var decisions = 0
        val preview = com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview(
            com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction.CLEAR, setOf(date),
            results = listOf(com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateResult(
                date, com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState.APPLICABLE,
                "Se quitará la jornada ficticia 08:00–16:00, su nota y sus imágenes. ".repeat(12))),
            expectationToken = "synthetic-preview")
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val state = V2ManualShiftLoadUiState(
                        stage = if (clear) V2ManualShiftLoadStage.CHOOSE_TEMPLATE else V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                        timelineId = TIMELINE_ID, selectedDates = setOf(date), occupiedDates = setOf(date),
                        conflictDetails = List(8) { "Centro ficticio: 08:00–16:00. Nueva jornada: 10:00–18:00." },
                        batchPreview = preview.takeIf { clear })
                    V2ManualShiftLoadContent(state, setOf(date), true, true,
                        V2ManualShiftLoadActions(chooseOccupiedPolicy = { decisions++ }, confirmBatch = { decisions++ }, cancelBatch = { decisions++ }), {}, {})
                }
            }
        }
        listOf(false, true).forEach { theme -> TestFontScale.entries.forEach { value ->
            compose.runOnIdle { dark = theme; fontScale = value; clear = false }
            listOf("Sobrescribir", "Superponer", "Cancelar").forEach { label ->
                compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            }
            compose.runOnIdle { clear = true }
            compose.onNodeWithText("Confirmar").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Cancelar").assertIsDisplayed().assertIsEnabled().performClick()
        } }
        compose.runOnIdle { assertEquals(30, decisions) }
    }

    private fun setContent(
        stateProvider: () -> V2ManualShiftLoadUiState,
        actions: V2ManualShiftLoadActions,
    ) {
        compose.setContent {
            AndroidFontScaleTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
    }

    private fun openCatalog(placeId: UUID? = null) {
        compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
        if (placeId != null) compose.onNodeWithTag("manual-place-$placeId").performScrollTo().performClick()
    }

    private fun awaitDrawerClosedAndCallback(callbackObserved: () -> Boolean) {
        compose.waitUntil(5_000L) {
            !compose.onNodeWithTag("main-navigation-drawer").isDisplayed() && callbackObserved()
        }
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
    }

    private fun assertMonthNavigationBlocked() {
        for (tag in listOf("calendar-previous-month", "calendar-next-month")) {
            val nodes = compose.onAllNodesWithTag(tag)
            if (nodes.fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithTag(tag).assertDoesNotExist()
            } else {
                nodes.assertCountEquals(1)
                val node = compose.onNodeWithTag(tag).assertIsNotEnabled()
                // Exercise a disabled control only with reachable physical input, never its semantics action.
                if (node.isDisplayed()) node.performTouchInput { click() }
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
        onEnterEditDate: (LocalDate?) -> Unit = {},
        onDismissDate: () -> Unit = {},
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
    ) {
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarProvider(),
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = onDismissDate,
                    onRetry = {},
                    onEnterCalendarEditMode = { date -> onEnterEditMode(); onEnterEditDate(date) },
                    onEnterManualCalendarSelection = { date ->
                        onEnterEditMode()
                        onEnterEditDate(date)
                    },
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
        selectionPurpose = if (interactionMode == CalendarInteractionMode.EDIT) {
            com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose.MANUAL_LOAD
        } else {
            null
        },
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
