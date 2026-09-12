package com.blackatsystems.miguardia.ui.worksetup

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.*
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.ui.management.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real producer + consumer + explicit confirmation. Only repository I/O is replaced. */
class ContextualWorkCreationTest {
    private val scopes = mutableListOf<CoroutineScope>()
    private var nextId = 100L
    private fun id() = UUID(10, nextId++)
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
    @After fun close() = scopes.forEach { it.cancel() }

    private fun producer(store: CreationStore, handle: SavedStateHandle = SavedStateHandle(), scope: CoroutineScope = scope()) =
        WorkSetupCoordinator(store.configurations, store, store.objectives, CLOCK, ::id, scope,
            initialPersistedState = handle.readWorkSetupState(), persist = handle::writeWorkSetupState)

    private fun consumer(store: CreationStore, handle: SavedStateHandle = SavedStateHandle(), scope: CoroutineScope = scope()) =
        V2ManualShiftLoadCoordinator(store.configurations, store, store.objectives, store.shifts,
            store.medicalLeaves, store.v2Shifts, clock = CLOCK, zoneId = ZoneOffset.UTC,
            uuidProvider = UuidProvider(::id), scope = scope,
            initialPersistedState = handle.readV2ManualShiftLoadState(), persist = handle::writeV2ManualShiftLoadState)

    private fun start(store: CreationStore, load: V2ManualShiftLoadCoordinator, dates: Set<LocalDate> = setOf(DATE)) {
        load.start(store.ready)
        load.confirmDates(dates)
        load.chooseTemplate(store.initialTemplate.id)
        load.updatePosition("Puesto ficticio 3")
    }

    private fun createRequest(load: V2ManualShiftLoadCoordinator, setup: WorkSetupCoordinator,
        kind: WorkCreationKind = WorkCreationKind.PLACE_AND_TEMPLATE): WorkCreationRequest {
        load.beginCreation(kind)
        return requireNotNull(load.uiState.value.creationRequest).also(setup::openContextualCreation)
    }

    private fun fillPlace(setup: WorkSetupCoordinator) = setup.updatePlaceDraft {
        it.copy(name = "Lugar ficticio nuevo", abbreviation = "NUEVO", note = "Nota local ficticia")
    }
    private fun fillTemplate(setup: WorkSetupCoordinator) = setup.updateTemplateDraft {
        it.copy(startTime = "10:15", endTime = "18:45", colorArgb = 0xFF123456.toInt())
    }
    private fun deliver(load: V2ManualShiftLoadCoordinator, setup: WorkSetupCoordinator): WorkCreationResult {
        val result = requireNotNull(setup.uiState.value.creationResult)
        load.receiveCreationResult(result)
        setup.acknowledgeCreation(result.requestId)
        return result
    }
    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun newPlaceAndTemplateReturnToOneOrManyDatesBeforeAnyShiftIsWritten() {
        for (sector in WorkSector.entries) for (count in listOf(1, 3)) {
            val store = CreationStore(sector)
            val load = consumer(store)
            val setup = producer(store)
            val dates = (0 until count).mapTo(linkedSetOf()) { DATE.plusDays(it.toLong()) }
            start(store, load, dates)
            val request = createRequest(load, setup)
            fillPlace(setup)
            setup.saveAdditionalPlace()
            assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, setup.uiState.value.surface)
            assertEquals(request.newPlaceId, setup.uiState.value.selectedTemplatePlaceId)
            assertTrue(store.writes.isEmpty())
            fillTemplate(setup)
            setup.saveAdditionalTemplate()
            val result = deliver(load, setup)
            assertEquals(request.newTemplateId, result.templateId)
            assertEquals(request.newTemplateId, load.uiState.value.selectedTemplateId)
            assertEquals(dates, load.uiState.value.selectedDates)
            assertEquals("Puesto ficticio 3", load.uiState.value.position)
            assertTrue(store.writes.isEmpty())
            load.requestSave()
            assertEquals(count, store.writes.size)
            assertEquals(dates, store.writes.map { it.shift.localStartDate }.toSet())
            store.writes.forEach {
                assertEquals(request.newTemplateId, it.snapshot.templateId)
                assertEquals(request.newPlaceId, it.snapshot.workPlaceId)
                assertEquals(sector, it.snapshot.sector)
                assertEquals("Puesto ficticio 3", it.shift.position)
                assertEquals(510, Duration.between(it.shift.startAt, it.shift.endAt).toMinutes().toInt())
            }
            load.receiveCreationResult(result)
            assertFalse(load.uiState.value.isActive)
            assertEquals(1, store.batchCalls)
        }
    }

    @Test fun existingPlaceUsesSelectedIdsAndDuplicateDeliveryDoesNotReplaceLaterChoice() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load)
        val request = createRequest(load, setup, WorkCreationKind.TEMPLATE)
        assertEquals(store.initialTemplate.workPlaceId, setup.uiState.value.selectedTemplatePlaceId)
        assertEquals(store.initialTemplate.workTypeId, setup.uiState.value.selectedTemplateTypeId)
        fillTemplate(setup)
        setup.saveAdditionalTemplate()
        val result = deliver(load, setup)
        assertEquals(request.newTemplateId, load.uiState.value.selectedTemplateId)
        load.chooseTemplate(store.initialTemplate.id)
        load.receiveCreationResult(result)
        assertEquals(store.initialTemplate.id, load.uiState.value.selectedTemplateId)
        assertEquals(0, store.placeCalls)
        assertEquals(1, store.templateCalls)
    }

    @Test fun cancellationBeforeAndAfterPlaceSavePreservesLoadAndExplainsPartialCreation() {
        for (savePlace in listOf(false, true)) {
            val store = CreationStore()
            val load = consumer(store)
            val setup = producer(store)
            start(store, load, setOf(DATE, DATE.plusDays(1)))
            val request = createRequest(load, setup)
            fillPlace(setup)
            if (savePlace) {
                setup.saveAdditionalPlace()
                fillTemplate(setup)
            }
            setup.requestBack()
            assertTrue(setup.uiState.value.showDiscardConfirmation)
            setup.confirmDiscard()
            val result = deliver(load, setup)
            assertEquals(WorkCreationOutcome.CANCELLED, result.outcome)
            assertEquals(if (savePlace) request.newPlaceId else null, result.placeId)
            assertEquals(store.initialTemplate.id, load.uiState.value.selectedTemplateId)
            assertEquals(2, load.uiState.value.selectedDates.size)
            assertEquals(if (savePlace) 1 else 0, store.placeCalls)
            assertTrue(store.writes.isEmpty())
            if (savePlace) assertTrue(load.uiState.value.infoMessage!!.contains("lugar quedó guardado"))
        }
    }

    @Test fun recreationInsideBothFormsPreservesTypedIdentityAndFieldsWithoutWriting() {
        val store = CreationStore()
        val loadHandle = SavedStateHandle()
        val setupHandle = SavedStateHandle()
        var load = consumer(store, loadHandle)
        var setup = producer(store, setupHandle)
        start(store, load, setOf(DATE, DATE.plusDays(1)))
        val request = createRequest(load, setup)
        fillPlace(setup)
        setup = producer(store, restored(setupHandle))
        assertEquals("Lugar ficticio nuevo", setup.uiState.value.placeDraft.name)
        assertEquals(request, setup.uiState.value.creationRequest)
        assertEquals(0, store.placeCalls)
        setup.saveAdditionalPlace()
        fillTemplate(setup)
        val templateState = SavedStateHandle().also { it.writeWorkSetupState(
            setup.uiState.value.let { state -> WorkSetupPersistedState(
                surface = state.surface, selectedSector = state.selectedSector,
                templateDraft = state.templateDraft, selectedTemplatePlaceId = state.selectedTemplatePlaceId,
                selectedTemplateTypeId = state.selectedTemplateTypeId, creationRequest = state.creationRequest,
                creationPlaceSaved = state.creationPlaceSaved)
            }) }
        setup = producer(store, restored(templateState))
        load = consumer(store, restored(loadHandle))
        assertEquals("10:15", setup.uiState.value.templateDraft.startTime)
        assertEquals(request.newPlaceId, setup.uiState.value.selectedTemplatePlaceId)
        assertEquals(0, store.templateCalls)
        setup.saveAdditionalTemplate()
        deliver(load, setup)
        assertEquals(request.newTemplateId, load.uiState.value.selectedTemplateId)
        assertEquals(2, load.uiState.value.selectedDates.size)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun interruptedPersistedPlaceAndTemplateAreRecoveredByIdWithoutDuplicateWrites() {
        for (phase in WorkCreationPendingWrite.entries) {
            val store = CreationStore()
            val handle = SavedStateHandle()
            val oldScope = scope()
            val load = consumer(store)
            val setup = producer(store, handle, oldScope)
            start(store, load)
            val request = createRequest(load, setup)
            fillPlace(setup)
            if (phase == WorkCreationPendingWrite.TEMPLATE) {
                setup.saveAdditionalPlace()
                fillTemplate(setup)
            }
            store.afterWriteGate = CompletableDeferred()
            if (phase == WorkCreationPendingWrite.PLACE) setup.saveAdditionalPlace() else setup.saveAdditionalTemplate()
            val snapshot = restored(handle)
            assertEquals(phase, snapshot.readWorkSetupState().creationPendingWrite)
            oldScope.cancel()
            val resumed = producer(store, snapshot)
            assertEquals(1, store.placeCalls)
            if (phase == WorkCreationPendingWrite.PLACE) {
                assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, resumed.uiState.value.surface)
                assertEquals(request.newPlaceId, resumed.uiState.value.selectedTemplatePlaceId)
                assertNull(resumed.uiState.value.creationPendingWrite)
            } else {
                assertEquals(1, store.templateCalls)
                deliver(load, resumed)
                assertEquals(request.newTemplateId, load.uiState.value.selectedTemplateId)
            }
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun failureBeforeCommitAllowsExplicitRetryAndAfterCommitRecoversWithoutDuplicate() {
        for (phase in WorkCreationPendingWrite.entries) for (afterCommit in listOf(false, true)) {
            val store = CreationStore()
            val load = consumer(store)
            val setup = producer(store)
            start(store, load)
            createRequest(load, setup)
            fillPlace(setup)
            if (phase == WorkCreationPendingWrite.TEMPLATE) { setup.saveAdditionalPlace(); fillTemplate(setup) }
            store.failBeforeWrite = !afterCommit
            store.failAfterWrite = afterCommit
            if (phase == WorkCreationPendingWrite.PLACE) setup.saveAdditionalPlace() else setup.saveAdditionalTemplate()
            store.failBeforeWrite = false
            store.failAfterWrite = false
            if (!afterCommit) {
                assertNotNull(setup.uiState.value.errorMessage)
                assertFalse(setup.uiState.value.creationBusy)
                if (phase == WorkCreationPendingWrite.PLACE) setup.saveAdditionalPlace() else setup.saveAdditionalTemplate()
            }
            assertEquals(2, store.catalog.workPlaces.size)
            if (phase == WorkCreationPendingWrite.TEMPLATE) {
                assertEquals(2, store.catalog.workTemplates.size)
                deliver(load, setup)
            }
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun uncertainReadLocksDraftAndRetryOnlyChecksPersistedIdentity() {
        val store = CreationStore()
        val handle = SavedStateHandle()
        val oldScope = scope()
        val load = consumer(store)
        val setup = producer(store, handle, oldScope)
        start(store, load)
        createRequest(load, setup)
        fillPlace(setup)
        store.afterWriteGate = CompletableDeferred()
        setup.saveAdditionalPlace()
        oldScope.cancel()
        store.failIdentityRead = true
        val resumed = producer(store, restored(handle))
        assertTrue(resumed.uiState.value.creationBusy)
        resumed.updatePlaceDraft { it.copy(name = "No aplicar") }
        resumed.saveAdditionalPlace()
        resumed.requestBack()
        assertEquals("Lugar ficticio nuevo", resumed.uiState.value.placeDraft.name)
        assertEquals(1, store.placeCalls)
        store.failIdentityRead = false
        resumed.retryCreationRecovery()
        assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, resumed.uiState.value.surface)
        assertFalse(resumed.uiState.value.creationBusy)
        assertEquals(1, store.placeCalls)
    }

    @Test fun resultBeforeOrAfterProducerCatalogEmissionUsesFreshReadAndSurvivesRefreshFailure() {
        for (emitEarly in listOf(false, true)) {
            val store = CreationStore()
            val loadHandle = SavedStateHandle()
            val load = consumer(store, loadHandle)
            val setup = producer(store)
            start(store, load)
            val request = createRequest(load, setup, WorkCreationKind.TEMPLATE)
            store.publishChanges = emitEarly
            fillTemplate(setup)
            setup.saveAdditionalTemplate()
            if (!emitEarly) assertFalse(setup.uiState.value.catalog!!.workTemplates.any { it.id == request.newTemplateId })
            store.failCatalogRead = true
            val result = deliver(load, setup)
            assertEquals(result, load.uiState.value.creationResult)
            assertEquals(request, load.uiState.value.creationRequest)
            assertEquals(1, store.templateCalls)
            val resumed = consumer(store, restored(loadHandle))
            assertNotNull(resumed.uiState.value.errorMessage)
            store.failCatalogRead = false
            resumed.retry()
            assertEquals(request.newTemplateId, resumed.uiState.value.selectedTemplateId)
            assertNull(resumed.uiState.value.creationRequest)
            assertEquals(1, store.templateCalls)
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun resultSavedBeforeAcknowledgementIsNotConsumedAgainAfterRecreation() {
        val store = CreationStore()
        val loadHandle = SavedStateHandle()
        val setupHandle = SavedStateHandle()
        val load = consumer(store, loadHandle)
        val setup = producer(store, setupHandle)
        start(store, load)
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate()
        val result = requireNotNull(setup.uiState.value.creationResult)
        load.receiveCreationResult(result)
        load.chooseTemplate(store.initialTemplate.id)
        val resumedLoad = consumer(store, restored(loadHandle))
        val resumedSetup = producer(store, restored(setupHandle))
        deliver(resumedLoad, resumedSetup)
        assertEquals(store.initialTemplate.id, resumedLoad.uiState.value.selectedTemplateId)
        assertNull(resumedSetup.uiState.value.creationRequest)
        assertEquals(1, store.templateCalls)
    }

    @Test fun staleResultFromReplacedRequestCannotChangeNewDraft() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load)
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        setup.requestBack()
        val old = deliver(load, setup)
        load.cancel()
        start(store, load, setOf(DATE.plusDays(2)))
        val next = createRequest(load, setup, WorkCreationKind.TEMPLATE)
        load.receiveCreationResult(old)
        assertEquals(next, load.uiState.value.creationRequest)
        assertEquals(setOf(DATE.plusDays(2)), load.uiState.value.selectedDates)
        setup.acknowledgeCreation(old.requestId)
        assertEquals(next, setup.uiState.value.creationRequest)
    }

    @Test fun doubleTapAndReentrantActionsDoNotReplaceInFlightCreation() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load)
        val request = createRequest(load, setup)
        load.beginCreation(WorkCreationKind.TEMPLATE)
        load.start(store.ready)
        load.confirmDates(setOf(DATE.plusDays(3)))
        assertEquals(request, load.uiState.value.creationRequest)
        fillPlace(setup)
        store.afterWriteGate = CompletableDeferred()
        setup.saveAdditionalPlace(); setup.saveAdditionalPlace()
        setup.startAnotherPlace(); setup.openAdditionalTemplate(); setup.confirmDiscard()
        assertEquals(1, store.placeCalls)
        assertEquals(request, setup.uiState.value.creationRequest)
        store.afterWriteGate!!.complete(Unit)
        assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, setup.uiState.value.surface)
    }

    @Test fun newTemplateInvalidatesAllPreviousWarningsAndRequiresCurrentOccupancyDecision() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load)
        load.requestSave()
        assertEquals(1, store.writes.size)
        start(store, load)
        load.requestSave()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, load.uiState.value.stage)
        load.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL)
        val request = createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate(); deliver(load, setup)
        assertEquals(request.newTemplateId, load.uiState.value.selectedTemplateId)
        assertNull(load.uiState.value.occupiedPolicy)
        assertNull(load.uiState.value.reviewFingerprint)
        assertTrue(load.uiState.value.acknowledgedWarnings.isEmpty())
        assertFalse(load.uiState.value.saveAfterPreparation)
        load.requestSave()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, load.uiState.value.stage)
        assertEquals(1, store.writes.size)
    }

    @Test fun incompatibleHistoryOrConcurrentArchiveNeverSelectsOrDropsDates() {
        for (archive in listOf(false, true)) {
            val store = CreationStore()
            val load = consumer(store)
            val setup = producer(store)
            val dates = setOf(DATE, DATE.plusDays(1))
            start(store, load, dates)
            val request = createRequest(load, setup)
            fillPlace(setup); setup.saveAdditionalPlace()
            fillTemplate(setup); setup.saveAdditionalTemplate()
            store.catalog = if (archive) store.catalog.copy(workTemplates = store.catalog.workTemplates.map {
                if (it.id == request.newTemplateId) it.copy(isActive = false) else it
            }) else store.catalog.copy(workplaceRuleRevisions = store.catalog.workplaceRuleRevisions.map {
                if (it.workPlaceId == request.newPlaceId) it.copy(effectiveFrom = DATE.plusDays(1)) else it
            })
            deliver(load, setup)
            assertNull(load.uiState.value.selectedTemplateId)
            assertEquals(dates, load.uiState.value.selectedDates)
            assertNotNull(load.uiState.value.errorMessage)
            load.requestSave()
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun changedSectorReleasesResultAndLetsUserChangeDatesWithoutLosingPosition() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        val dates = setOf(DATE, DATE.plusDays(1))
        start(store, load, dates)
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate()
        store.history = store.history.copy(timeline = EffectiveDateTimeline(store.history.timeline.id,
            store.history.timeline.revisions + EffectiveRevision(id(), DATE.plusDays(1),
                WorkConfiguration(WorkSector.POLICE, HoursReference.PendingSetup, null))))
        deliver(load, setup)
        assertNull(load.uiState.value.creationRequest)
        assertNull(load.uiState.value.selectedTemplateId)
        assertEquals(dates, load.uiState.value.selectedDates)
        assertEquals("Puesto ficticio 3", load.uiState.value.position)
        assertNotNull(load.uiState.value.errorMessage)
        load.backToDateSelection()
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, load.uiState.value.stage)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun historyAcrossRevisionsWritesEachDateWithItsOwnConfigurationSnapshot() {
        val store = CreationStore()
        val later = EffectiveRevision(id(), DATE.plusDays(1),
            WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null))
        store.history = store.history.copy(timeline = EffectiveDateTimeline(store.history.timeline.id,
            store.history.timeline.revisions + later))
        val load = consumer(store)
        val setup = producer(store)
        start(store, load, setOf(DATE, DATE.plusDays(1)))
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate(); deliver(load, setup)
        load.requestSave()
        assertEquals(2, store.writes.size)
        assertEquals(store.history.timeline.revisions.first().id,
            store.writes.single { it.shift.localStartDate == DATE }.snapshot.configurationRevisionId)
        assertEquals(later.id, store.writes.single { it.shift.localStartDate == DATE.plusDays(1) }.snapshot.configurationRevisionId)
    }

    @Test fun backfillRemainsAnExplicitSeparateConfirmationAfterContextualReturn() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load, setOf(DATE.minusDays(1), DATE))
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate(); deliver(load, setup)
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, load.uiState.value.stage)
        assertTrue(store.writes.isEmpty())
        load.requestSave()
        assertEquals(V2ManualShiftLoadStage.CONFIRM_BACKFILL, load.uiState.value.stage)
        assertTrue(store.writes.isEmpty())
        load.cancelBackfill()
        assertEquals(setOf(DATE.minusDays(1), DATE), load.uiState.value.selectedDates)
    }

    @Test fun explicitBackfillConfirmationCompletesContextualDirectSaveOnce() {
        val store = CreationStore()
        val load = consumer(store)
        val setup = producer(store)
        start(store, load, setOf(DATE.minusDays(1), DATE))
        val request = createRequest(load, setup)
        fillPlace(setup); setup.saveAdditionalPlace()
        fillTemplate(setup); setup.saveAdditionalTemplate(); deliver(load, setup)
        assertEquals(0, store.backfillCalls)
        load.requestSave()
        assertEquals(V2ManualShiftLoadStage.CONFIRM_BACKFILL, load.uiState.value.stage)
        load.confirmBackfill()
        assertEquals(1, store.backfillCalls)
        assertEquals(1, store.batchCalls)
        assertEquals(2, store.writes.size)
        assertTrue(store.writes.all { it.snapshot.templateId == request.newTemplateId })
        assertFalse(load.uiState.value.isActive)
    }

    @Test fun emptyCatalogCanLaunchCreationAndSameTimelineMissingSetupDoesNotInvalidateDraft() {
        val store = CreationStore()
        val load = consumer(store)
        start(store, load)
        store.catalog = store.catalog.copy(workTemplates = store.catalog.workTemplates.map { it.copy(isActive = false) })
        load.retry()
        assertTrue(load.uiState.value.templateOptions.isEmpty())
        assertTrue(load.uiState.value.canCreate)
        val missing = WorkSetupState.V2NeedsFirstSet(store.ready.timelineId, store.ready.configurationRevision,
            setOf(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE))
        assertTrue(load.uiState.value.belongsTo(missing))
        assertFalse(load.uiState.value.belongsTo(missing.copy(timelineId = id())))
        val setup = producer(store)
        createRequest(load, setup, WorkCreationKind.TEMPLATE)
        assertEquals(WorkSetupSurface.ADDITIONAL_TEMPLATE, setup.uiState.value.surface)
        fillTemplate(setup); setup.saveAdditionalTemplate(); deliver(load, setup)
        assertNotNull(load.uiState.value.selectedTemplateId)
    }

    @Test fun persistedRequestBeforeProducerLaunchRecoversAnAlreadyCommittedTemplate() {
        val store = CreationStore()
        val loadHandle = SavedStateHandle()
        val load = consumer(store, loadHandle)
        val setup = producer(store)
        start(store, load)
        val request = createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate()
        val newProducer = producer(store)
        val newConsumer = consumer(store, restored(loadHandle))
        newProducer.openContextualCreation(request)
        deliver(newConsumer, newProducer)
        assertEquals(request.newTemplateId, newConsumer.uiState.value.selectedTemplateId)
        assertEquals(1, store.templateCalls)
    }

    @Test fun resultReceivedWhileRestorationReadsCatalogIsDrainedExactlyOnce() {
        val store = CreationStore()
        val handle = SavedStateHandle()
        val load = consumer(store, handle)
        val setup = producer(store)
        start(store, load)
        val request = createRequest(load, setup, WorkCreationKind.TEMPLATE)
        fillTemplate(setup); setup.saveAdditionalTemplate()
        store.catalogReadGate = CompletableDeferred()
        val resumed = consumer(store, restored(handle))
        assertTrue(resumed.uiState.value.isLoading)
        val result = deliver(resumed, setup)
        assertEquals(result, resumed.uiState.value.creationResult)
        store.catalogReadGate!!.complete(Unit)
        assertEquals(request.newTemplateId, resumed.uiState.value.selectedTemplateId)
        assertNull(resumed.uiState.value.creationResult)
        resumed.receiveCreationResult(result)
        assertEquals(1, store.templateCalls)
    }

    @Test fun replacingTimelineAfterInterruptedAbsenceReturnsWithoutRetryLoopOrWrite() {
        val store = CreationStore()
        val setupHandle = SavedStateHandle()
        val load = consumer(store)
        val setup = producer(store, setupHandle)
        start(store, load)
        createRequest(load, setup)
        fillPlace(setup)
        val snapshot = restored(setupHandle)
        store.history = store.history.copy(timeline = EffectiveDateTimeline(id(), store.history.timeline.revisions))
        val resumed = producer(store, snapshot)
        assertEquals(WorkCreationOutcome.CANCELLED, resumed.uiState.value.creationResult?.outcome)
        assertFalse(resumed.uiState.value.creationBusy)
        deliver(load, resumed)
        assertNull(load.uiState.value.creationRequest)
        assertEquals(setOf(DATE), load.uiState.value.selectedDates)
        assertEquals(0, store.placeCalls)
        assertTrue(store.writes.isEmpty())
    }

    companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 7)
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC)
    }
}

private class CreationStore(sector: WorkSector = WorkSector.NURSING) : WorkCatalogRepository {
    private val timestamp = ContextualWorkCreationTest.CLOCK.instant()
    private val date = ContextualWorkCreationTest.DATE
    private fun id(value: Long) = UUID(20, value)
    private val revision = EffectiveRevision(id(2), date, WorkConfiguration(sector, HoursReference.PendingSetup, null))
    var history = WorkConfigurationHistory(EffectiveDateTimeline(id(1), listOf(revision)), PerPeriodHoursValues(emptyList()))
    val ready = WorkSetupState.V2Ready(id(1), revision)
    private val initialObjective = Objective(id(3), "Lugar ficticio previo", "PREV", null, null, true, timestamp, timestamp)
    private val initialPlace = WorkPlace(id(4), id(1), sector, id(3), true, timestamp, timestamp)
    private val initialType = WorkType.create(id(5), id(1), sector, "Trabajo habitual", timestamp)
    val initialTemplate = WorkTemplate(id(6), id(1), sector, id(4), id(3), id(5), LocalTime.of(8, 0), LocalTime.of(16, 0), 0xFF223344.toInt(), true, timestamp, timestamp)
    private val initialRule = WorkplaceRuleRevision(id(7), id(1), sector, id(4), id(3), date,
        WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), timestamp)
    var catalog = WorkCatalog(id(1), sector, listOf(initialPlace), listOf(initialType), listOf(initialTemplate), listOf(initialRule))
    private val changes = MutableStateFlow(0)
    private val objectiveValues = MutableStateFlow(listOf(initialObjective))
    val writes = mutableListOf<V2ShiftWrite>()
    var placeCalls = 0
    var templateCalls = 0
    var batchCalls = 0
    var backfillCalls = 0
    var afterWriteGate: CompletableDeferred<Unit>? = null
    var failBeforeWrite = false
    var failAfterWrite = false
    var failIdentityRead = false
    var failCatalogRead = false
    var publishChanges = true
    var catalogReadGate: CompletableDeferred<Unit>? = null
    private suspend fun afterWrite() {
        if (publishChanges) changes.value++
        afterWriteGate?.await()
        if (failAfterWrite) error("Respuesta ficticia interrumpida")
    }
    val configurations = object : WorkConfigurationRepository {
        override fun observe() = flowOf(history)
        override suspend fun get() = history
        override suspend fun createInitial(timelineId: UUID, firstRevision: EffectiveRevision<WorkConfiguration>) = error("Fuera del recorrido")
        override suspend fun addRevision(timelineId: UUID, revision: EffectiveRevision<WorkConfiguration>) = error("Fuera del recorrido")
    }
    val objectives = object : ObjectiveRepository {
        override fun observeAll() = objectiveValues
        override fun observeActive() = objectiveValues
        override suspend fun getById(id: UUID) = objectiveValues.value.find { it.id == id }
    }
    val shifts = object : ShiftRepository {
        override fun observeHasAny() = flowOf(writes.isNotEmpty())
        override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate) =
            flowOf(writes.map { it.shift }.filter { it.localStartDate in startDateInclusive..endDateInclusive })
        override fun observeEndingAfter(instantExclusive: Instant) = flowOf(writes.map { it.shift }.filter { it.endAt > instantExclusive })
        override suspend fun getById(id: UUID) = writes.find { it.shift.id == id }?.shift
    }
    val medicalLeaves = object : MedicalLeaveRepository {
        override fun observeIntersecting(startDateInclusive: LocalDate, endDateInclusive: LocalDate) = flowOf(emptyList<MedicalLeave>())
        override suspend fun create(medicalLeave: MedicalLeave) = error("Fuera del recorrido")
        override suspend fun update(medicalLeave: MedicalLeave) = error("Fuera del recorrido")
        override suspend fun delete(id: UUID) = error("Fuera del recorrido")
    }
    val v2Shifts = object : V2ShiftRepository {
        override fun observeAll(timelineId: UUID, sector: WorkSector) = flowOf(writes.toList())
        override fun observeWorkSnapshot(shiftId: UUID) = flowOf(writes.find { it.shift.id == shiftId }?.snapshot)
        override suspend fun getWorkSnapshot(shiftId: UUID) = writes.find { it.shift.id == shiftId }?.snapshot
        override suspend fun getShift(shiftId: UUID): V2ShiftLookup = V2ShiftLookup.Missing
        override suspend fun insert(write: V2ShiftWrite) = error("Sólo se admite el lote productivo")
        override suspend fun deleteShift(expected: V2ShiftWrite, expectedActual: ShiftActualExpectation?) = error("Fuera del recorrido")
        override suspend fun applyV2Batch(mutation: V2ShiftBatchMutation, expectedOccupancy: ShiftOccupancyExpectation, expectedUpdates: V2ShiftWriteExpectation) {
            batchCalls++
            writes.removeAll { it.shift.id in mutation.shiftIdsToDelete }
            writes += mutation.shiftsToInsert
        }
    }
    // New subscriptions read durable state even when an existing subscriber's invalidation is delayed.
    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> = changes.map {
        catalogReadGate?.await()
        if (failCatalogRead) error("Lectura ficticia fallida")
        catalog
    }
    override fun observeRecentlyUsed(timelineId: UUID, sector: WorkSector, limit: Int) = flowOf(emptyList<RecentWorkTemplate>())
    override suspend fun getWorkPlace(id: UUID): WorkPlace? { if (failIdentityRead) error("Lectura ficticia fallida"); return catalog.workPlaces.find { it.id == id } }
    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? { if (failIdentityRead) error("Lectura ficticia fallida"); return catalog.workTemplates.find { it.id == id } }
    override suspend fun getWorkType(id: UUID) = catalog.workTypes.find { it.id == id }
    override suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate) = catalog.ruleRevisionAt(workPlaceId, date)
    override suspend fun getRuleRevisions(workPlaceId: UUID) = catalog.workplaceRuleRevisions.filter { it.workPlaceId == workPlaceId }
    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace) {
        placeCalls++
        if (failBeforeWrite) error("Fallo ficticio antes del commit")
        check(catalog.workPlaces.none { it.id == newWorkPlace.workPlace.id })
        catalog = catalog.copy(workPlaces = catalog.workPlaces + newWorkPlace.workPlace,
            workplaceRuleRevisions = catalog.workplaceRuleRevisions + newWorkPlace.firstRuleRevision)
        objectiveValues.value += newWorkPlace.objective
        afterWrite()
    }
    override suspend fun createWorkTemplate(workTemplate: WorkTemplate) {
        templateCalls++
        if (failBeforeWrite) error("Fallo ficticio antes del commit")
        check(catalog.workTemplates.none { it.id == workTemplate.id })
        catalog = catalog.copy(workTemplates = catalog.workTemplates + workTemplate)
        afterWrite()
    }
    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet) = error("Fuera del recorrido")
    override suspend fun updateWorkPlace(update: WorkPlaceUpdate) = error("Fuera del recorrido")
    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("Fuera del recorrido")
    override suspend fun createWorkType(workType: WorkType) = error("Fuera del recorrido")
    override suspend fun updateWorkType(update: WorkTypeUpdate) = error("Fuera del recorrido")
    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("Fuera del recorrido")
    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate) = error("Fuera del recorrido")
    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("Fuera del recorrido")
    override suspend fun addWorkplaceRuleRevision(revision: WorkplaceRuleRevision, confirmationNow: Instant) = error("Fuera del recorrido")
    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory {
        backfillCalls++
        history = history.copy(timeline = EffectiveDateTimeline(history.timeline.id,
            listOf(extension.configurationRevision) + history.timeline.revisions))
        catalog = catalog.copy(workplaceRuleRevisions = catalog.workplaceRuleRevisions +
            extension.workplaceRuleBackfills.map { it.earlierRevision })
        return history
    }
}
