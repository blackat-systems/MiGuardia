package com.blackatsystems.miguardia.ui.worksetup

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeUpdate
import com.blackatsystems.miguardia.core.domain.work.WeekendDays
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSetupCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun exactSectorCatalogContainsOnlyTheFourApprovedOptions() {
        assertEquals(
            listOf(
                "Vigilancia privada",
                "Policía",
                "Enfermería",
                "Medicina",
            ),
            WorkSetupUiState().sectorOptions.map(WorkSector::displayName),
        )
    }

    @Test
    fun loadFailureIsNotProjectedAsFreshAndRetryRecovers() {
        val configurations = FakeWorkConfigurationRepository(null).apply {
            observeFailure = IllegalStateException("fallo ficticio")
        }
        val coordinator = coordinator(configurations = configurations)

        assertEquals(WorkSetupState.LoadError, coordinator.uiState.value.rootState)
        assertFalse(coordinator.uiState.value.rootState is WorkSetupState.FreshInstall)

        configurations.observeFailure = null
        coordinator.retryLoad()

        assertEquals(WorkSetupState.FreshInstall, coordinator.uiState.value.rootState)
    }

    @Test
    fun objectiveNameFailureBlocksV2AndRetryRestoresTheCatalog() {
        val context = v2Context(WorkSector.NURSING)
        val objectives = FakeObjectiveRepository().apply {
            observeFailure = IllegalStateException("fallo ficticio")
        }
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            objectives = objectives,
        )

        assertEquals(WorkSetupState.LoadError, coordinator.uiState.value.rootState)
        assertFalse(coordinator.uiState.value.rootState is WorkSetupState.FreshInstall)

        objectives.observeFailure = null
        coordinator.retryLoad()

        assertTrue(coordinator.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet)
    }

    @Test
    fun freshInstallCannotContinueWithoutSelection() {
        val configurations = FakeWorkConfigurationRepository(null)
        val coordinator = coordinator(configurations = configurations)

        assertEquals(WorkSetupState.FreshInstall, coordinator.uiState.value.rootState)
        assertFalse(coordinator.uiState.value.canContinueSector)

        coordinator.saveInitialSector()

        assertEquals(0, configurations.createInitialCalls)
    }

    @Test
    fun doubleConfirmationKeepsOnlyOneInitialWriteUntilRoomEmits() {
        val configurations = FakeWorkConfigurationRepository(null).apply { emitCreatedHistory = false }
        val coordinator = coordinator(configurations = configurations)
        coordinator.selectSector(WorkSector.NURSING)

        coordinator.saveInitialSector()
        coordinator.saveInitialSector()

        assertEquals(1, configurations.createInitialCalls)
        assertTrue(coordinator.uiState.value.isSavingSector)
        assertEquals(WorkSector.NURSING, configurations.createdRevision?.value?.sector)
        assertEquals(HoursReference.PendingSetup, configurations.createdRevision?.value?.hoursReference)
        assertNull(configurations.createdRevision?.value?.availabilityLabel)
    }

    @Test
    fun failedInitialWriteKeepsSelectionAndAllowsRetry() {
        val configurations = FakeWorkConfigurationRepository(null).apply {
            createInitialFailure = IllegalStateException("fallo ficticio")
        }
        val coordinator = coordinator(configurations = configurations)
        coordinator.selectSector(WorkSector.MEDICINE)

        coordinator.saveInitialSector()

        assertEquals(WorkSector.MEDICINE, coordinator.uiState.value.selectedSector)
        assertFalse(coordinator.uiState.value.isSavingSector)
        assertNotNull(coordinator.uiState.value.errorMessage)
        assertTrue(coordinator.uiState.value.canContinueSector)

        configurations.createInitialFailure = null
        coordinator.saveInitialSector()

        assertEquals(2, configurations.createInitialCalls)
        assertTrue(coordinator.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet)
    }

    @Test
    fun successfulInitialWriteUsesInjectedDateAndIdsThenOpensTheEmptyV2Guide() {
        val configurations = FakeWorkConfigurationRepository(null)
        val coordinator = coordinator(configurations = configurations)
        coordinator.selectSector(WorkSector.NURSING)

        coordinator.saveInitialSector()

        assertEquals(1, configurations.createInitialCalls)
        assertEquals(UUID(0L, 1L), configurations.createdTimelineId)
        assertEquals(UUID(0L, 2L), configurations.createdRevision?.id)
        assertEquals(LocalDate.of(2026, 8, 22), configurations.createdRevision?.effectiveFrom)
        assertEquals(UUID(0L, 1L), configurations.currentHistory?.timeline?.id)
        assertTrue(coordinator.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet)
        assertFalse(coordinator.uiState.value.isSavingSector)
    }

    @Test
    fun oneLoadUsesOneReferenceDateEvenIfTheClockCrossesMidnight() {
        val current = v2Context(WorkSector.PRIVATE_SECURITY)
        val futureRevision = EffectiveRevision(
            id = UUID(9L, 3L),
            effectiveFrom = LocalDate.of(2026, 8, 23),
            value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        val history = current.history.copy(
            timeline = EffectiveDateTimeline(
                current.history.timeline.id,
                listOf(current.revision, futureRevision),
            ),
        )
        val clock = SequenceClock(
            instants = listOf(
                Instant.parse("2026-08-22T23:59:59Z"),
                Instant.parse("2026-08-23T00:00:01Z"),
            ),
            currentZone = ZoneOffset.UTC,
        )

        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(history),
            clock = clock,
        )

        val state = coordinator.uiState.value.rootState
        assertTrue(state is WorkSetupState.V2NeedsFirstSet)
        assertEquals(WorkSector.PRIVATE_SECURITY, (state as WorkSetupState.V2NeedsFirstSet).configurationRevision.value.sector)
        assertEquals(1, clock.instantCalls)
    }

    @Test
    fun v2WithoutCatalogOpensGuideWithoutWritingShiftsOrCatalog() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )

        assertTrue(coordinator.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet)
        coordinator.openFirstWorkSet()

        assertEquals(WorkSetupSurface.FIRST_WORK_SET, coordinator.uiState.value.surface)
        assertEquals("Guardia habitual", coordinator.uiState.value.templateDraft.typeName)
        assertEquals(0, catalog.createFirstWorkSetCalls)
        assertEquals(0, catalog.createTemplateCalls)
    }

    @Test
    fun openingAndClosingUntouchedSuggestedDraftDoesNotAskToDiscard() {
        val context = v2Context(WorkSector.NURSING)
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
        )
        coordinator.openFirstWorkSet()

        coordinator.requestBack()

        assertEquals(WorkSetupSurface.NONE, coordinator.uiState.value.surface)
        assertFalse(coordinator.uiState.value.showDiscardConfirmation)

        coordinator.openFirstWorkSet()
        coordinator.updatePlaceDraft { it.copy(name = "Lugar ficticio") }
        coordinator.requestBack()

        assertEquals(WorkSetupSurface.FIRST_WORK_SET, coordinator.uiState.value.surface)
        assertTrue(coordinator.uiState.value.showDiscardConfirmation)
        assertEquals("Lugar ficticio", coordinator.uiState.value.placeDraft.name)
    }

    @Test
    fun placeAndTemplateValidationCoverRequiredFieldsAndExactTimes() {
        assertFalse(validatePlaceDraft(WorkPlaceDraft()).isValid)
        assertFalse(
            validatePlaceDraft(
                WorkPlaceDraft(name = "Hospital ficticio", abbreviation = "HF"),
            ).isValid,
        )
        assertFalse(
            validatePlaceDraft(
                WorkPlaceDraft(
                    name = "Hospital ficticio",
                    abbreviation = "HFI",
                    nightHoursEnabled = true,
                    nightStart = "22:00",
                    nightEnd = "22:00",
                ),
            ).isValid,
        )
        assertTrue(
            validatePlaceDraft(
                WorkPlaceDraft(name = "Hospital ficticio", abbreviation = "HFI"),
            ).isValid,
        )
        assertFalse(
            validateTemplateDraft(
                WorkTemplateDraft(typeName = "Turno habitual", startTime = "08:00", endTime = "16:00"),
                requireTypeName = true,
            ).isValid,
        )
        assertFalse(
            validateTemplateDraft(
                WorkTemplateDraft(
                    typeName = "Turno habitual",
                    startTime = "24:00",
                    endTime = "08:00",
                    colorArgb = 0xFF123456.toInt(),
                ),
                requireTypeName = true,
            ).isValid,
        )
        assertTrue(
            validateTemplateDraft(
                WorkTemplateDraft(
                    typeName = "Turno habitual",
                    startTime = "08:00",
                    endTime = "08:00",
                    colorArgb = 0xFF123456.toInt(),
                ),
                requireTypeName = true,
            ).isValid,
        )
    }

    @Test
    fun firstWorkSetUsesSingleAtomicRepositoryCallAndCompletes() {
        val context = v2Context(WorkSector.POLICE)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        coordinator.updatePlaceDraft {
            it.copy(
                nightHoursEnabled = true,
                nightStart = "22:00",
                nightEnd = "06:00",
                classifySaturday = true,
                classifySunday = true,
                showWeekendSummary = true,
                classifyHoliday = true,
                showHolidaySummary = true,
            )
        }
        fillValidFirstSet(coordinator, "Dependencia ficticia", "DFI", "Guardia habitual")

        coordinator.saveFirstWorkSet()

        assertEquals(1, catalog.createFirstWorkSetCalls)
        assertEquals(WorkSetupSurface.COMPLETION, coordinator.uiState.value.surface)
        val saved = requireNotNull(catalog.createdFirstWorkSet)
        assertEquals(context.revision.id, saved.configurationContext.revision.id)
        assertEquals(context.revision.effectiveFrom, saved.firstRuleRevision.effectiveFrom)
        assertEquals(HoursReference.PendingSetup, context.revision.value.hoursReference)
        val night = saved.firstRuleRevision.rules.nightHours as NightHoursRule.Defined
        assertEquals(LocalTime.of(22, 0), night.startInclusive)
        assertEquals(LocalTime.of(6, 0), night.endExclusive)
        val weekend = saved.firstRuleRevision.rules.weekend as WeekendRule.Defined
        assertEquals(WeekendDays.SATURDAY_AND_SUNDAY, weekend.days)
        assertTrue(weekend.showDedicatedSummary)
        assertTrue(saved.firstRuleRevision.rules.holiday.differentTreatment)
        assertTrue(saved.firstRuleRevision.rules.holiday.showDedicatedSummary)
    }

    @Test
    fun failedAtomicFirstSetKeepsWholeDraftForRetry() {
        val context = v2Context(WorkSector.MEDICINE)
        val catalog = FakeWorkCatalogRepository().apply {
            createFirstWorkSetFailure = IllegalStateException("fallo ficticio")
        }
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Clínica ficticia", "CLF", "Jornada habitual")

        coordinator.saveFirstWorkSet()

        assertEquals(WorkSetupSurface.FIRST_WORK_SET, coordinator.uiState.value.surface)
        assertEquals("Clínica ficticia", coordinator.uiState.value.placeDraft.name)
        assertEquals("08:00", coordinator.uiState.value.templateDraft.startTime)
        assertNotNull(coordinator.uiState.value.errorMessage)
        assertFalse(coordinator.uiState.value.isSavingWorkSet)
    }

    @Test
    fun backCannotDiscardDraftWhileAtomicSaveIsRunning() {
        val context = v2Context(WorkSector.MEDICINE)
        val gate = CompletableDeferred<Unit>()
        val catalog = FakeWorkCatalogRepository().apply { createFirstWorkSetGate = gate }
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Clínica ficticia", "CLF", "Jornada habitual")

        coordinator.saveFirstWorkSet()
        coordinator.requestBack()

        assertTrue(coordinator.uiState.value.isSavingWorkSet)
        assertEquals(WorkSetupSurface.FIRST_WORK_SET, coordinator.uiState.value.surface)
        assertFalse(coordinator.uiState.value.showDiscardConfirmation)
        assertEquals("Clínica ficticia", coordinator.uiState.value.placeDraft.name)

        gate.complete(Unit)

        assertEquals(WorkSetupSurface.COMPLETION, coordinator.uiState.value.surface)
    }

    @Test
    fun completionRoutesReturnToCalendarAndOpenAdditionalTemplate() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()

        coordinator.openAdditionalTemplate()
        assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, coordinator.uiState.value.surface)

        coordinator.returnToCalendar()
        assertEquals(WorkSetupSurface.NONE, coordinator.uiState.value.surface)
    }

    @Test
    fun anotherPlaceAndItsRulesUseSingleAtomicRepositoryCall() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()

        coordinator.startAnotherPlace()
        coordinator.updatePlaceDraft {
            it.copy(name = "Segundo objetivo ficticio", abbreviation = "SEF")
        }
        coordinator.saveAdditionalPlace()

        assertEquals(WorkSetupSurface.COMPLETION, coordinator.uiState.value.surface)
        assertEquals(1, catalog.createWorkPlaceCalls)
        assertEquals(0, catalog.createTemplateCalls)
        val saved = requireNotNull(catalog.createdWorkPlace)
        assertEquals("Segundo objetivo ficticio", saved.objective.fullName)
        assertEquals(context.revision.id, saved.configurationContext.revision.id)
        assertEquals(context.revision.effectiveFrom, saved.firstRuleRevision.effectiveFrom)
        assertTrue(coordinator.uiState.value.infoMessage.orEmpty().contains("agregarle un horario"))

        val firstTypeId = requireNotNull(catalog.createdFirstWorkSet).workType.id
        coordinator.openAdditionalTemplate()
        coordinator.updateTemplateDraft {
            it.copy(startTime = "06:00", endTime = "18:00", colorArgb = 0xFF445566.toInt())
        }
        coordinator.saveAdditionalTemplate()

        assertEquals(1, catalog.createTemplateCalls)
        assertEquals(saved.workPlace.id, catalog.createdTemplate?.workPlaceId)
        assertEquals(firstTypeId, catalog.createdTemplate?.workTypeId)
    }

    @Test
    fun additionalTemplateSelectionSurvivesClosingAndReopening() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()
        val firstPlaceId = requireNotNull(catalog.createdFirstWorkSet).workPlace.id

        coordinator.startAnotherPlace()
        coordinator.updatePlaceDraft {
            it.copy(name = "Segundo objetivo ficticio", abbreviation = "SEF")
        }
        coordinator.saveAdditionalPlace()
        val secondPlaceId = requireNotNull(catalog.createdWorkPlace).workPlace.id
        coordinator.openAdditionalTemplate()
        assertEquals(secondPlaceId, coordinator.uiState.value.selectedTemplatePlaceId)

        coordinator.selectTemplatePlace(firstPlaceId)
        coordinator.requestBack()
        coordinator.openAdditionalTemplate()

        assertEquals(firstPlaceId, coordinator.uiState.value.selectedTemplatePlaceId)
    }

    @Test
    fun completionCanStartAnotherPlaceBeforeTheCatalogEmissionArrives() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository().apply { emitCreatedFirstWorkSet = false }
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()

        coordinator.startAnotherPlace()

        assertEquals(WorkSetupSurface.ADDITIONAL_PLACE, coordinator.uiState.value.surface)
        assertTrue(coordinator.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet)
    }

    @Test
    fun datedRevisionChangeBlocksAWriteBuiltFromThePreviousVisibleSector() {
        val current = v2Context(WorkSector.PRIVATE_SECURITY)
        val futureRevision = EffectiveRevision(
            id = UUID(9L, 3L),
            effectiveFrom = LocalDate.of(2026, 8, 23),
            value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        val history = current.history.copy(
            timeline = EffectiveDateTimeline(
                current.history.timeline.id,
                listOf(current.revision, futureRevision),
            ),
        )
        val clock = MutableClock(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(history),
            catalog = catalog,
            clock = clock,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()
        clock.currentInstant = Instant.parse("2026-08-23T12:00:00Z")
        coordinator.startAnotherPlace()
        coordinator.updatePlaceDraft {
            it.copy(name = "Lugar posterior ficticio", abbreviation = "LPF")
        }

        coordinator.saveAdditionalPlace()

        assertEquals(0, catalog.createWorkPlaceCalls)
        assertEquals(WorkSetupSurface.ADDITIONAL_PLACE, coordinator.uiState.value.surface)
        assertNotNull(coordinator.uiState.value.errorMessage)
    }

    @Test
    fun failedAdditionalPlaceKeepsItsDraftForRetry() {
        val context = v2Context(WorkSector.PRIVATE_SECURITY)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Objetivo ficticio", "OBJ", "Guardia habitual")
        coordinator.saveFirstWorkSet()
        coordinator.startAnotherPlace()
        coordinator.updatePlaceDraft {
            it.copy(name = "Segundo lugar ficticio", abbreviation = "SLF")
        }
        catalog.createWorkPlaceFailure = IllegalStateException("fallo ficticio")

        coordinator.saveAdditionalPlace()

        assertEquals(1, catalog.createWorkPlaceCalls)
        assertEquals(WorkSetupSurface.ADDITIONAL_PLACE, coordinator.uiState.value.surface)
        assertEquals("Segundo lugar ficticio", coordinator.uiState.value.placeDraft.name)
        assertNotNull(coordinator.uiState.value.errorMessage)
        assertFalse(coordinator.uiState.value.isSavingWorkSet)
    }

    @Test
    fun additionalTemplateReusesTheCreatedPlaceAndTypeAndKeepsDraftOnFailure() {
        val context = v2Context(WorkSector.NURSING)
        val catalog = FakeWorkCatalogRepository()
        val coordinator = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
        )
        coordinator.openFirstWorkSet()
        fillValidFirstSet(coordinator, "Hospital ficticio", "HFI", "Turno habitual")
        coordinator.saveFirstWorkSet()
        val firstSet = requireNotNull(catalog.createdFirstWorkSet)

        coordinator.openAdditionalTemplate()
        coordinator.updateTemplateDraft {
            it.copy(startTime = "20:00", endTime = "08:00", colorArgb = 0xFF654321.toInt())
        }
        coordinator.saveAdditionalTemplate()

        assertEquals(1, catalog.createTemplateCalls)
        val savedTemplate = requireNotNull(catalog.createdTemplate)
        assertEquals(firstSet.workPlace.id, savedTemplate.workPlaceId)
        assertEquals(firstSet.workType.id, savedTemplate.workTypeId)
        assertEquals(WorkSetupSurface.COMPLETION, coordinator.uiState.value.surface)

        catalog.createTemplateFailure = IllegalStateException("fallo ficticio")
        coordinator.openAdditionalTemplate()
        coordinator.updateTemplateDraft {
            it.copy(startTime = "12:00", endTime = "18:00", colorArgb = 0xFF112233.toInt())
        }
        coordinator.saveAdditionalTemplate()

        assertEquals(2, catalog.createTemplateCalls)
        assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, coordinator.uiState.value.surface)
        assertEquals("12:00", coordinator.uiState.value.templateDraft.startTime)
        assertNotNull(coordinator.uiState.value.errorMessage)
        assertFalse(coordinator.uiState.value.isSavingTemplate)
    }

    @Test
    fun persistedDraftRecreatesWithoutRepeatingAnyWrite() {
        val context = v2Context(WorkSector.NURSING)
        val catalog = FakeWorkCatalogRepository()
        var persisted = WorkSetupPersistedState()
        val original = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
            persist = { persisted = it },
        )
        original.openFirstWorkSet()
        original.updatePlaceDraft {
            it.copy(name = "Hospital ficticio", abbreviation = "HFI")
        }
        original.continueToTemplate()
        original.updateTemplateDraft {
            it.copy(
                typeName = "Turno habitual",
                startTime = "20:00",
                endTime = "08:00",
                colorArgb = 0xFF123456.toInt(),
            )
        }

        val recreated = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
            persisted = persisted,
        )

        assertEquals(WorkSetupSurface.FIRST_WORK_SET, recreated.uiState.value.surface)
        assertEquals(WorkSetupStep.TYPE_AND_TEMPLATE, recreated.uiState.value.step)
        assertEquals("Hospital ficticio", recreated.uiState.value.placeDraft.name)
        assertEquals("20:00", recreated.uiState.value.templateDraft.startTime)
        assertEquals(0, catalog.createFirstWorkSetCalls)

        val persistedBeforeCommit = persisted
        original.saveFirstWorkSet()
        val reopenedAfterCommit = coordinator(
            configurations = FakeWorkConfigurationRepository(context.history),
            catalog = catalog,
            persisted = persistedBeforeCommit,
        )

        assertTrue(reopenedAfterCommit.uiState.value.rootState is WorkSetupState.V2Ready)
        assertEquals(WorkSetupSurface.COMPLETION, reopenedAfterCommit.uiState.value.surface)
        assertEquals(1, catalog.createFirstWorkSetCalls)
    }

    @Test
    fun savedStateHandleRoundTripKeepsTheWholeUnconfirmedDraft() {
        val persisted = WorkSetupPersistedState(
            selectedSector = WorkSector.MEDICINE,
            surface = WorkSetupSurface.ADDITIONAL_TEMPLATE,
            step = WorkSetupStep.TYPE_AND_TEMPLATE,
            placeDraft = WorkPlaceDraft(
                name = "Clínica ficticia",
                abbreviation = "CLF",
                address = "Dirección ficticia",
                note = "Nota ficticia",
                nightHoursEnabled = true,
                nightStart = "21:00",
                nightEnd = "06:00",
                classifySaturday = true,
                classifySunday = true,
                showWeekendSummary = true,
                classifyHoliday = true,
                showHolidaySummary = true,
            ),
            templateDraft = WorkTemplateDraft(
                typeName = "Jornada habitual",
                startTime = "08:00",
                endTime = "20:00",
                colorArgb = 0xFF123456.toInt(),
            ),
            selectedTemplatePlaceId = UUID(5L, 1L),
            selectedTemplateTypeId = UUID(5L, 2L),
            lastCreatedPlaceId = UUID(5L, 3L),
            lastCreatedTypeId = UUID(5L, 4L),
        )
        val handle = SavedStateHandle()

        handle.writeWorkSetupState(persisted)

        assertEquals(persisted, handle.readWorkSetupState())
    }

    private fun coordinator(
        configurations: FakeWorkConfigurationRepository = FakeWorkConfigurationRepository(null),
        catalog: FakeWorkCatalogRepository = FakeWorkCatalogRepository(),
        objectives: FakeObjectiveRepository = FakeObjectiveRepository(),
        persisted: WorkSetupPersistedState = WorkSetupPersistedState(),
        persist: (WorkSetupPersistedState) -> Unit = {},
        clock: Clock = CLOCK,
    ): WorkSetupCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        val uuids = (1L..100L).map { value -> UUID(0L, value) }.iterator()
        return WorkSetupCoordinator(
            configurationRepository = configurations,
            catalogRepository = catalog,
            objectiveRepository = objectives,
            clock = clock,
            uuidProvider = { uuids.next() },
            scope = scope,
            initialPersistedState = persisted,
            persist = persist,
        )
    }

    private fun fillValidFirstSet(
        coordinator: WorkSetupCoordinator,
        placeName: String,
        abbreviation: String,
        typeName: String,
    ) {
        coordinator.updatePlaceDraft {
            it.copy(name = placeName, abbreviation = abbreviation)
        }
        coordinator.continueToTemplate()
        coordinator.updateTemplateDraft {
            it.copy(
                typeName = typeName,
                startTime = "08:00",
                endTime = "20:00",
                colorArgb = 0xFF123456.toInt(),
            )
        }
    }

    private data class V2Context(
        val history: WorkConfigurationHistory,
        val revision: EffectiveRevision<WorkConfiguration>,
    )

    private fun v2Context(sector: WorkSector): V2Context {
        val revision = EffectiveRevision(
            id = UUID(9L, 2L),
            effectiveFrom = LocalDate.of(2026, 8, 22),
            value = WorkConfiguration(sector, HoursReference.PendingSetup, null),
        )
        return V2Context(
            history = WorkConfigurationHistory(
                timeline = EffectiveDateTimeline(UUID(9L, 1L), listOf(revision)),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            ),
            revision = revision,
        )
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-08-22T12:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}

private class MutableClock(
    var currentInstant: Instant,
    private val currentZone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
}

private class SequenceClock(
    instants: List<Instant>,
    private val currentZone: ZoneId,
) : Clock() {
    private val remaining = instants.iterator()
    private var lastInstant = instants.last()
    var instantCalls: Int = 0
        private set

    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = SequenceClock(listOf(lastInstant), zone)

    override fun instant(): Instant {
        instantCalls++
        if (remaining.hasNext()) lastInstant = remaining.next()
        return lastInstant
    }
}

private class FakeWorkConfigurationRepository(
    initial: WorkConfigurationHistory?,
) : WorkConfigurationRepository {
    private val histories = MutableStateFlow(initial)
    var observeFailure: Throwable? = null
    var createInitialFailure: Throwable? = null
    var emitCreatedHistory: Boolean = true
    var createInitialCalls: Int = 0
    var createdTimelineId: UUID? = null
    var createdRevision: EffectiveRevision<WorkConfiguration>? = null
    val currentHistory: WorkConfigurationHistory?
        get() = histories.value

    override fun observe(): Flow<WorkConfigurationHistory?> = flow {
        observeFailure?.let { throw it }
        emitAll(histories)
    }

    override suspend fun get(): WorkConfigurationHistory? = histories.value

    override suspend fun createInitial(
        timelineId: UUID,
        firstRevision: EffectiveRevision<WorkConfiguration>,
    ) {
        createInitialCalls++
        createdTimelineId = timelineId
        createdRevision = firstRevision
        createInitialFailure?.let { throw it }
        if (emitCreatedHistory) {
            histories.value = WorkConfigurationHistory(
                timeline = EffectiveDateTimeline(timelineId, listOf(firstRevision)),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            )
        }
    }

    override suspend fun addRevision(
        timelineId: UUID,
        revision: EffectiveRevision<WorkConfiguration>,
    ) = error("No se usa en estas pruebas")

    override suspend fun createPerPeriodValue(timelineId: UUID, entry: PerPeriodHoursEntry) =
        error("No se usa en estas pruebas")

    override suspend fun updatePerPeriodValue(timelineId: UUID, entry: PerPeriodHoursEntry) =
        error("No se usa en estas pruebas")
}

private class FakeWorkCatalogRepository : WorkCatalogRepository {
    private val catalogs = linkedMapOf<Pair<UUID, WorkSector>, MutableStateFlow<WorkCatalog>>()
    var createFirstWorkSetCalls: Int = 0
    var createWorkPlaceCalls: Int = 0
    var createTemplateCalls: Int = 0
    var createFirstWorkSetFailure: Throwable? = null
    var emitCreatedFirstWorkSet: Boolean = true
    var createWorkPlaceFailure: Throwable? = null
    var createTemplateFailure: Throwable? = null
    var createFirstWorkSetGate: CompletableDeferred<Unit>? = null
    var createdFirstWorkSet: FirstWorkSet? = null
    var createdWorkPlace: NewWorkPlace? = null
    var createdTemplate: WorkTemplate? = null

    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> =
        catalogFlow(timelineId, sector)

    override fun observeRecentlyUsed(
        timelineId: UUID,
        sector: WorkSector,
        limit: Int,
    ): Flow<List<RecentWorkTemplate>> = MutableStateFlow(emptyList())

    override suspend fun getWorkPlace(id: UUID): WorkPlace? =
        catalogs.values.asSequence().map { it.value }.flatMap { it.workPlaces.asSequence() }.firstOrNull { it.id == id }

    override suspend fun getWorkType(id: UUID): WorkType? =
        catalogs.values.asSequence().map { it.value }.flatMap { it.workTypes.asSequence() }.firstOrNull { it.id == id }

    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? =
        catalogs.values.asSequence().map { it.value }.flatMap { it.workTemplates.asSequence() }.firstOrNull { it.id == id }

    override suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision? =
        catalogs.values.asSequence().map { it.value }.flatMap { it.workplaceRuleRevisions.asSequence() }
            .filter { it.workPlaceId == workPlaceId && !it.effectiveFrom.isAfter(date) }
            .maxByOrNull(WorkplaceRuleRevision::effectiveFrom)

    override suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision> =
        catalogs.values.flatMap { it.value.workplaceRuleRevisions }.filter { it.workPlaceId == workPlaceId }

    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet) {
        createFirstWorkSetCalls++
        createFirstWorkSetFailure?.let { throw it }
        createFirstWorkSetGate?.await()
        createdFirstWorkSet = firstWorkSet
        if (!emitCreatedFirstWorkSet) return
        val key = firstWorkSet.workPlace.timelineId to firstWorkSet.workPlace.sector
        val current = catalogFlow(key.first, key.second).value
        catalogFlow(key.first, key.second).value = current.copy(
            workPlaces = current.workPlaces + firstWorkSet.workPlace,
            workTypes = current.workTypes + firstWorkSet.workType,
            workTemplates = current.workTemplates + firstWorkSet.workTemplate,
            workplaceRuleRevisions = current.workplaceRuleRevisions + firstWorkSet.firstRuleRevision,
        )
    }

    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace) {
        createWorkPlaceCalls++
        createWorkPlaceFailure?.let { throw it }
        createdWorkPlace = newWorkPlace
        val key = newWorkPlace.workPlace.timelineId to newWorkPlace.workPlace.sector
        val current = catalogFlow(key.first, key.second).value
        catalogFlow(key.first, key.second).value = current.copy(
            workPlaces = current.workPlaces + newWorkPlace.workPlace,
            workplaceRuleRevisions = current.workplaceRuleRevisions + newWorkPlace.firstRuleRevision,
        )
    }
    override suspend fun updateWorkPlace(update: WorkPlaceUpdate) = error("No se usa")
    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkType(workType: WorkType) = error("No se usa")
    override suspend fun updateWorkType(update: WorkTypeUpdate) = error("No se usa")
    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")

    override suspend fun createWorkTemplate(workTemplate: WorkTemplate) {
        createTemplateCalls++
        createTemplateFailure?.let { throw it }
        createdTemplate = workTemplate
        val key = workTemplate.timelineId to workTemplate.sector
        val current = catalogFlow(key.first, key.second).value
        catalogFlow(key.first, key.second).value = current.copy(
            workTemplates = current.workTemplates + workTemplate,
        )
    }

    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate) = error("No se usa")
    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun addWorkplaceRuleRevision(revision: WorkplaceRuleRevision, confirmationNow: Instant) =
        error("No se usa")
    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory = error("No se usa")

    private fun catalogFlow(timelineId: UUID, sector: WorkSector): MutableStateFlow<WorkCatalog> =
        catalogs.getOrPut(timelineId to sector) {
            MutableStateFlow(
                WorkCatalog(
                    timelineId = timelineId,
                    sector = sector,
                    workPlaces = emptyList(),
                    workTypes = emptyList(),
                    workTemplates = emptyList(),
                    workplaceRuleRevisions = emptyList(),
                ),
            )
        }
}

private class FakeObjectiveRepository : ObjectiveRepository {
    private val objectives = MutableStateFlow<List<Objective>>(emptyList())
    var observeFailure: Throwable? = null

    override fun observeActive(): Flow<List<Objective>> = objectives
    override fun observeAll(): Flow<List<Objective>> = flow {
        observeFailure?.let { throw it }
        emitAll(objectives)
    }
    override suspend fun getById(id: UUID): Objective? = objectives.value.firstOrNull { it.id == id }
}
