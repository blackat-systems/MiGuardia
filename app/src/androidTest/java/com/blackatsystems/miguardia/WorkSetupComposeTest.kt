package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkPlaceDraft
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStep
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkTemplateDraft
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkSetupComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overviewOffersHoursProgressAndAvailabilityEntries() {
        var opened = 0
        var availabilityOpened = 0
        compose.setContent {
            MiGuardiaTheme {
                WorkSetupSurfaceHost(
                    state = readyState(WorkSector.NURSING).copy(surface = WorkSetupSurface.OVERVIEW),
                    actions = WorkSetupActions(
                        openHoursProgress = { opened++ },
                        openAvailability = { availabilityOpened++ },
                    ),
                )
            }
        }

        compose.onNodeWithTag("work-setup-hours-progress")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithTag("work-setup-availability")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, availabilityOpened) }
    }

    @Test
    fun loadingAndErrorNeverExposeCalendarOrFreshSelector() {
        var retried = false
        var state by mutableStateOf(
            WorkSetupUiState(
                rootState = WorkSetupState.LoadError,
                errorMessage = "No pudimos leer tu configuración laboral.",
            ),
        )
        setApp(stateProvider = { state }, actions = WorkSetupActions(retryLoad = { retried = true }))

        compose.onNodeWithTag("work-setup-load-error").assertIsDisplayed()
        compose.onNodeWithText("Calendario").assertDoesNotExist()
        compose.onNodeWithText("¿En qué rubro trabajás?").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(true, retried) }

        compose.runOnIdle { state = WorkSetupUiState(rootState = WorkSetupState.Loading) }
        compose.onNodeWithTag("work-setup-loading").assertIsDisplayed()
        compose.onNodeWithText("Calendario").assertDoesNotExist()
    }

    @Test
    fun freshInstallShowsExactlyFourIndependentSectorsAndRequiresSelection() {
        var state by mutableStateOf(WorkSetupUiState(rootState = WorkSetupState.FreshInstall))
        setApp(
            stateProvider = { state },
            actions = WorkSetupActions(
                selectSector = { selected -> state = state.copy(selectedSector = selected) },
                saveInitialSector = { state = state.copy(isSavingSector = true) },
            ),
        )

        compose.onNodeWithText("¿En qué rubro trabajás?").assertIsDisplayed()
        listOf("Vigilancia privada", "Policía", "Enfermería", "Medicina").forEach { label ->
            compose.onAllNodesWithText(label).assertCountEquals(1)
        }
        compose.onNodeWithText("Salud").assertDoesNotExist()
        compose.onNodeWithText("Otro").assertDoesNotExist()
        compose.onNodeWithTag("work-sector-continue").assertIsNotEnabled()

        compose.onNodeWithText("Enfermería").performClick()
        compose.onNodeWithTag("work-sector-nursing").assertIsSelected()
        compose.onNodeWithTag("work-sector-continue").assertIsEnabled().performClick()
        compose.onNodeWithTag("work-sector-continue").assertIsNotEnabled()
        compose.onNodeWithTag("work-sector-nursing").assertIsSelected()

        compose.runOnIdle { state = needsFirstSetState(WorkSector.NURSING) }
        compose.onNodeWithText("Próximo evento").assertIsDisplayed()
        compose.onNodeWithText("Todavía no cargaste ningún lugar de trabajo").assertIsDisplayed()
    }

    @Test
    fun v2WithoutFirstSetShowsCalendarGuideAndNeverOffersLegacyLoad() {
        var opened = false
        setApp(
            stateProvider = { needsFirstSetState(WorkSector.POLICE) },
            actions = WorkSetupActions(openFirstWorkSet = { opened = true }),
        )

        compose.onNodeWithText("Próximo evento").assertIsDisplayed()
        compose.onNodeWithText("Todavía no cargaste ningún lugar de trabajo").assertIsDisplayed()
        compose.onNodeWithText("Cargar datos").assertDoesNotExist()
        compose.onNodeWithText("Editar calendario").assertDoesNotExist()
        compose.onNodeWithText("Cargar jornadas").assertDoesNotExist()
        compose.onNodeWithText("Repetir jornadas").assertDoesNotExist()
        compose.onNodeWithText("Crear primer lugar").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun v2DayDetailDoesNotExposeTheLegacyEditAction() {
        val selectedDate = LocalDate.of(2026, 8, 22)
        setApp(
            stateProvider = { readyState(WorkSector.NURSING) },
            calendar = calendarState().copy(
                days = listOf(
                    CalendarDay(
                        date = selectedDate,
                        shifts = emptyList(),
                        explicitStatus = null,
                        hasMedicalLeave = false,
                    ),
                ),
                detailDate = selectedDate,
            ),
        )

        compose.onNodeWithTag("edit-day-action").assertDoesNotExist()
    }

    @Test
    fun v2DrawerContainsWorkSetupAndNoRemovedV1Destinations() {
        setApp(stateProvider = { readyState(WorkSector.MEDICINE) })

        compose.onNodeWithContentDescription("Abrir menú").performClick()

        compose.onNodeWithTag("drawer-action-work-setup").assertIsDisplayed()
        compose.onNodeWithText("Resumen").assertDoesNotExist()
        compose.onNodeWithText("Perfil laboral").assertDoesNotExist()
        compose.onNodeWithText("Objetivos y horarios").assertDoesNotExist()
    }

    @Test
    fun workSetupOffersRecurringPlansOnlyAfterTheFirstWorkSetIsReady() {
        var opened = 0
        var state by mutableStateOf(
            readyState(WorkSector.MEDICINE).copy(surface = WorkSetupSurface.OVERVIEW),
        )
        setApp(
            stateProvider = { state },
            recurringActions = V2RecurringActions(openPlans = { opened++ }),
        )

        compose.onNodeWithTag("work-setup-recurring-plans").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }

        compose.runOnIdle {
            state = needsFirstSetState(WorkSector.MEDICINE).copy(surface = WorkSetupSurface.OVERVIEW)
        }
        compose.onNodeWithTag("work-setup-recurring-plans").assertDoesNotExist()
        compose.onAllNodesWithTag("work-setup-calendar-guide")[0].assertIsDisplayed()
    }

    @Test
    fun firstSetKeepsTwoShortStepsAndExplainsTwentyFourHours() {
        var state by mutableStateOf(
            needsFirstSetState(WorkSector.PRIVATE_SECURITY).copy(
                surface = WorkSetupSurface.FIRST_WORK_SET,
                selectedSector = WorkSector.PRIVATE_SECURITY,
                placeDraft = WorkPlaceDraft(name = "Objetivo ficticio", abbreviation = "OBJ"),
                templateDraft = WorkTemplateDraft(typeName = "Guardia habitual"),
            ),
        )
        val actions = WorkSetupActions(
            continueToTemplate = {
                state = state.copy(
                    step = WorkSetupStep.TYPE_AND_TEMPLATE,
                    templateDraft = state.templateDraft.copy(
                        startTime = "08:00",
                        endTime = "08:00",
                        colorArgb = 0xFF123456.toInt(),
                    ),
                )
            },
            requestBack = {},
        )
        setApp(stateProvider = { state }, actions = actions)

        compose.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Continuar al tipo y horario").performScrollTo().performClick()
        compose.onNodeWithText("Paso 2 de 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardia habitual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Este horario dura 24 horas.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar lugar y horario").performScrollTo().assertIsEnabled()
    }

    @Test
    fun completionOffersExactlyTheThreeAgreedActions() {
        val opened = mutableListOf<String>()
        setApp(
            stateProvider = {
                readyState(WorkSector.NURSING).copy(
                    surface = WorkSetupSurface.COMPLETION,
                    infoMessage = "El lugar y su primer horario quedaron guardados.",
                )
            },
            actions = WorkSetupActions(
                returnToCalendar = { opened += "calendar" },
                openAdditionalTemplate = { opened += "schedule" },
                startAnotherPlace = { opened += "place" },
            ),
        )

        compose.onAllNodesWithText("Volver al Calendario").assertCountEquals(1)
        compose.onAllNodesWithText("Agregar otro horario").assertCountEquals(1)
        compose.onAllNodesWithText("Agregar otro lugar").assertCountEquals(1)
        compose.onNodeWithText("Volver al Calendario").performClick()
        compose.onNodeWithText("Agregar otro horario").performClick()
        compose.onNodeWithText("Agregar otro lugar").performClick()
        compose.runOnIdle { assertEquals(listOf("calendar", "schedule", "place"), opened) }
    }

    @Test
    fun dirtyDraftBackRequiresAConsciousDiscardDecision() {
        var state by mutableStateOf(
            needsFirstSetState(WorkSector.MEDICINE).copy(
                surface = WorkSetupSurface.FIRST_WORK_SET,
                selectedSector = WorkSector.MEDICINE,
                placeDraft = WorkPlaceDraft(name = "Clínica ficticia", abbreviation = "CLF"),
                templateDraft = WorkTemplateDraft(typeName = "Jornada habitual"),
            ),
        )
        setApp(
            stateProvider = { state },
            actions = WorkSetupActions(
                requestBack = { state = state.copy(showDiscardConfirmation = true) },
                dismissDiscard = { state = state.copy(showDiscardConfirmation = false) },
            ),
        )

        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Todavía hay datos sin guardar. ¿Querés descartarlos y volver al Calendario?")
            .assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("Clínica ficticia").assertIsDisplayed()

        compose.runOnIdle {
            state = readyState(WorkSector.MEDICINE).copy(
                surface = WorkSetupSurface.ADDITIONAL_PLACE,
                selectedSector = WorkSector.MEDICINE,
                placeDraft = WorkPlaceDraft(name = "Consultorio ficticio", abbreviation = "COF"),
            )
        }
        compose.onNodeWithText("Agregar otro lugar").assertIsDisplayed()
        compose.onNodeWithText("Guardar lugar").performScrollTo().assertIsEnabled()
    }

    @Test
    fun sectorSelectionRemainsReachableInBothThemesOrientationsAndAllZooms() {
        var dark by mutableStateOf(true)
        var zoom by mutableStateOf(AppZoom.EXTRA_LARGE)
        var width by mutableStateOf(320.dp)
        var height by mutableStateOf(480.dp)
        var workSetupState by mutableStateOf(WorkSetupUiState(rootState = WorkSetupState.FreshInstall))
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                Box(Modifier.size(width = width, height = height)) {
                    MiGuardiaApp(
                        calendarState = calendarState(),
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onToday = {},
                        onSelectDate = {},
                        onDismissDate = {},
                        onRetry = {},
                        workSetupState = workSetupState,
                    )
                }
            }
        }

        compose.onNodeWithTag("work-sector-continue").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            workSetupState = needsFirstSetState(WorkSector.NURSING).copy(
                surface = WorkSetupSurface.FIRST_WORK_SET,
                step = WorkSetupStep.TYPE_AND_TEMPLATE,
                selectedSector = WorkSector.NURSING,
                placeDraft = WorkPlaceDraft(name = "Hospital ficticio", abbreviation = "HFI"),
                templateDraft = WorkTemplateDraft(
                    typeName = "Turno habitual",
                    startTime = "20:00",
                    endTime = "08:00",
                    colorArgb = 0xFF123456.toInt(),
                ),
            )
        }
        compose.onNodeWithTag("work-time-start").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("work-time-end").performScrollTo().assertIsDisplayed()

        compose.runOnIdle {
            dark = false
            zoom = AppZoom.LARGE
            width = 480.dp
            height = 320.dp
            workSetupState = WorkSetupUiState(rootState = WorkSetupState.FreshInstall)
        }
        compose.onNodeWithText("¿En qué rubro trabajás?").assertIsDisplayed()
        compose.onNodeWithTag("work-sector-continue").performScrollTo().assertIsDisplayed()

        compose.runOnIdle {
            zoom = AppZoom.STANDARD
            width = 320.dp
            height = 480.dp
        }
        compose.onNodeWithTag("work-sector-continue").performScrollTo().assertIsDisplayed()
    }

    private fun setApp(
        stateProvider: () -> WorkSetupUiState,
        actions: WorkSetupActions = WorkSetupActions(),
        recurringActions: V2RecurringActions = V2RecurringActions(),
        calendar: CalendarUiState = calendarState(),
    ) {
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    workSetupState = stateProvider(),
                    workSetupActions = actions,
                    v2RecurringActions = recurringActions,
                )
            }
        }
    }

    private fun calendarState() = CalendarUiState(
        visibleMonth = YearMonth.of(2026, 8),
        referenceInstant = Instant.parse("2026-08-22T12:00:00Z"),
        days = emptyList(),
        hasAnyShifts = false,
        hasAnyShiftsLoaded = true,
        loadState = CalendarLoadState.CONTENT,
    )

    private fun needsFirstSetState(sector: WorkSector): WorkSetupUiState {
        val revision = configurationRevision(sector)
        val timelineId = UUID(20L, 1L)
        return WorkSetupUiState(
            rootState = WorkSetupState.V2NeedsFirstSet(
                timelineId = timelineId,
                configurationRevision = revision,
                missing = MissingWorkSetupRequirement.entries.toSet(),
            ),
            selectedSector = sector,
            catalog = emptyCatalog(timelineId, sector),
        )
    }

    private fun readyState(sector: WorkSector): WorkSetupUiState {
        val timelineId = UUID(20L, 1L)
        return WorkSetupUiState(
            rootState = WorkSetupState.V2Ready(timelineId, configurationRevision(sector)),
            selectedSector = sector,
            catalog = emptyCatalog(timelineId, sector),
        )
    }

    private fun configurationRevision(sector: WorkSector) = EffectiveRevision(
        id = UUID(20L, 2L),
        effectiveFrom = LocalDate.of(2026, 8, 22),
        value = WorkConfiguration(sector, HoursReference.PendingSetup, null),
    )

    private fun emptyCatalog(timelineId: UUID, sector: WorkSector) = WorkCatalog(
        timelineId = timelineId,
        sector = sector,
        workPlaces = emptyList(),
        workTypes = emptyList(),
        workTemplates = emptyList(),
        workplaceRuleRevisions = emptyList(),
    )
}
