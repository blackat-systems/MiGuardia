package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoption
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoptionResult
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkTypeUpdate
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ManualShiftLoadCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun onlyV2ReadyStartsTheManualLoad() {
        val fixture = fixture()
        val harness = harness(fixture)

        harness.coordinator.start(
            WorkSetupState.V2NeedsFirstSet(
                fixture.timelineId,
                fixture.revisions.first(),
                emptySet(),
            ),
        )
        assertEquals(V2ManualShiftLoadStage.IDLE, harness.coordinator.uiState.value.stage)

        harness.coordinator.start(fixture.readyState)
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, harness.coordinator.uiState.value.stage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun selectionUsesExactConfigurationRevisionForEachDateAndWritesSnapshotPairs() {
        val fixture = fixture(
            revisions = listOf(
                revision(uuid(11), DATE, WorkSector.NURSING),
                revision(uuid(12), DATE.plusDays(10), WorkSector.NURSING),
            ),
            sector = WorkSector.NURSING,
        )
        val harness = harness(fixture)
        val selectedDates = setOf(DATE.plusDays(2), DATE.plusDays(12))

        harness.startAndChoose(selectedDates)
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()

        val writes = harness.v2Shifts.mutations.single().shiftsToInsert.sortedBy { it.shift.localStartDate }
        assertEquals(listOf(uuid(11), uuid(12)), writes.map { it.snapshot.configurationRevisionId })
        assertTrue(writes.all { it.shift.sourceObjectiveId == fixture.objective.id })
        assertTrue(writes.all { it.snapshot.workPlaceId == fixture.place.id })
        assertTrue(writes.all { it.snapshot.templateId == fixture.template.id })
        assertTrue(writes.all { it.snapshot.workTypeId == fixture.type.id })
        assertTrue(writes.all { it.shift.id == it.snapshot.shiftId })
        val expectation = harness.v2Shifts.occupancyExpectations.single()
        assertEquals(requireNotNull(selectedDates.minOrNull()).minusDays(2), expectation.startDateInclusive)
        assertEquals(requireNotNull(selectedDates.maxOrNull()).plusDays(2), expectation.endDateInclusive)
        assertTrue(expectation.observedShifts.isEmpty())
    }

    @Test
    fun futureSectorUsesItsOwnCatalogInsteadOfTheSectorVisibleToday() {
        val first = revision(uuid(21), DATE, WorkSector.PRIVATE_SECURITY)
        val future = revision(uuid(22), DATE.plusDays(8), WorkSector.POLICE)
        val fixture = fixture(
            revisions = listOf(first, future),
            sector = WorkSector.POLICE,
            ruleDate = DATE.plusDays(8),
        )
        val harness = harness(fixture)

        harness.coordinator.start(WorkSetupState.V2Ready(fixture.timelineId, first))
        harness.coordinator.confirmDates(setOf(DATE.plusDays(9)))

        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
        assertEquals(WorkSector.POLICE, harness.coordinator.uiState.value.sector)
        assertEquals(listOf(fixture.template.id), harness.coordinator.uiState.value.templateOptions.map { it.template.id })
    }

    @Test
    fun activeFilteringKeepsDifferentTypesWithTheSamePlaceAndScheduleDistinct() {
        val fixture = fixture()
        val secondType = WorkType.create(
            id = uuid(31),
            timelineId = fixture.timelineId,
            sector = fixture.sector,
            rawName = "Capacitación",
            timestamp = NOW,
        )
        val secondTemplate = fixture.template.copy(id = uuid(32), workTypeId = secondType.id)
        val archivedTemplate = fixture.template.copy(
            id = uuid(33),
            endTime = LocalTime.of(17, 0),
            isActive = false,
        )
        val catalog = fixture.catalog.copy(
            workTypes = fixture.catalog.workTypes + secondType,
            workTemplates = fixture.catalog.workTemplates + secondTemplate + archivedTemplate,
        )
        val harness = harness(fixture.copy(catalog = catalog))

        harness.coordinator.start(fixture.readyState)
        harness.coordinator.confirmDates(setOf(DATE.plusDays(1)))

        val options = harness.coordinator.uiState.value.templateOptions
        assertEquals(2, options.size)
        assertEquals(setOf("Trabajo habitual", "Capacitación"), options.mapTo(linkedSetOf()) { it.workType.name })
        assertEquals(1, options.map { it.template.startTime to it.template.endTime }.distinct().size)
    }

    @Test
    fun legacyAndMixedSectorSelectionsAreRejectedWithoutWriting() {
        val migratedFixture = fixture(origin = WorkConfigurationOrigin.MIGRATED_V1)
        val migratedHarness = harness(migratedFixture)
        migratedHarness.coordinator.start(migratedFixture.readyState)
        migratedHarness.coordinator.confirmDates(setOf(DATE.minusDays(1), DATE))
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, migratedHarness.coordinator.uiState.value.stage)
        assertNotNull(migratedHarness.coordinator.uiState.value.errorMessage)
        assertTrue(migratedHarness.v2Shifts.mutations.isEmpty())

        val mixedFixture = fixture(
            revisions = listOf(
                revision(uuid(41), DATE, WorkSector.PRIVATE_SECURITY),
                revision(uuid(42), DATE.plusDays(2), WorkSector.POLICE),
            ),
        )
        val mixedHarness = harness(mixedFixture)
        mixedHarness.coordinator.start(mixedFixture.readyState)
        mixedHarness.coordinator.confirmDates(setOf(DATE.plusDays(1), DATE.plusDays(3)))
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, mixedHarness.coordinator.uiState.value.stage)
        assertNotNull(mixedHarness.coordinator.uiState.value.errorMessage)
        assertTrue(mixedHarness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun newV2BackfillCopiesFirstConfigurationAndFirstPlaceRuleOnlyAfterConfirmation() {
        val fixture = fixture(ruleDate = DATE.plusDays(3))
        val harness = harness(fixture)
        val earlier = DATE.minusDays(4)

        harness.startAndChoose(setOf(earlier, DATE.plusDays(1)))
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CONFIRM_BACKFILL, harness.coordinator.uiState.value.stage)
        assertNull(harness.catalog.extended)
        assertTrue(harness.v2Shifts.mutations.isEmpty())

        harness.coordinator.cancelBackfill()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
        assertEquals(setOf(earlier, DATE.plusDays(1)), harness.coordinator.uiState.value.selectedDates)

        harness.coordinator.requestReview()
        harness.coordinator.confirmBackfill()

        val extension = requireNotNull(harness.catalog.extended)
        assertEquals(earlier, extension.configurationRevision.effectiveFrom)
        assertEquals(fixture.revisions.first().value, extension.configurationRevision.value)
        assertNotEquals(fixture.revisions.first().id, extension.configurationRevision.id)
        val backfill = extension.workplaceRuleBackfills.single()
        assertEquals(fixture.rule, backfill.sourceRevision)
        assertEquals(fixture.rule.rules, backfill.earlierRevision.rules)
        assertEquals(earlier, backfill.earlierRevision.effectiveFrom)
        assertNotEquals(fixture.rule.id, backfill.earlierRevision.id)
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
    }

    @Test
    fun keepOccupiedWritesOnlyFreeDatesAndClearsExplicitStatesOnlyForThoseDates() {
        val fixture = fixture()
        val occupiedDate = DATE.plusDays(1)
        val freeDate = DATE.plusDays(2)
        val existing = existingShift(occupiedDate)
        val harness = harness(fixture, existing = listOf(existing))

        harness.startAndChoose(setOf(occupiedDate, freeDate))
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, harness.coordinator.uiState.value.stage)
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED)
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        assertEquals(setOf(freeDate), harness.coordinator.uiState.value.plannedDates)
        assertEquals(setOf(occupiedDate), harness.coordinator.uiState.value.omittedDates)
        harness.coordinator.save()

        val mutation = harness.v2Shifts.mutations.single()
        assertEquals(setOf(freeDate), mutation.shiftsToInsert.mapTo(linkedSetOf()) { it.shift.localStartDate })
        assertEquals(setOf(freeDate), mutation.explicitDayStatusDatesToClear)
        assertTrue(mutation.shiftIdsToDelete.isEmpty())
    }

    @Test
    fun secondShiftWarningsRequireConfirmationAndGoingBackKeepsTheDraft() {
        val fixture = fixture()
        val selected = DATE.plusDays(1)
        val harness = harness(fixture, existing = listOf(existingShift(selected)))

        harness.startAndChoose(setOf(selected))
        harness.coordinator.updatePosition("Puerta ficticia")
        harness.coordinator.requestReview()
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT)
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())

        harness.coordinator.dismissWarnings()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
        assertEquals("Puerta ficticia", harness.coordinator.uiState.value.position)
        assertEquals(setOf(selected), harness.coordinator.uiState.value.selectedDates)

        harness.coordinator.requestReview()
        harness.coordinator.confirmWarnings()
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()
        assertEquals(1, harness.v2Shifts.mutations.size)
    }

    @Test
    fun stalePreviewRecalculatesAndNeverWritesWithTheOldConfirmation() {
        val fixture = fixture()
        val selected = DATE.plusDays(1)
        val harness = harness(fixture)
        harness.startAndChoose(setOf(selected))
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)

        harness.shifts.current = listOf(existingShift(selected))
        harness.coordinator.save()

        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, harness.coordinator.uiState.value.stage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun savedDraftRestoresItsExactStageAndDoesNotRepeatWrites() {
        val fixture = fixture()
        val persisted = mutableListOf<V2ManualShiftLoadPersistedState>()
        val first = harness(fixture, persist = persisted::add)
        first.coordinator.start(fixture.readyState)
        first.coordinator.confirmDates(setOf(DATE.plusDays(1)))
        first.coordinator.chooseTemplate(fixture.template.id)
        first.coordinator.updatePosition("Función ficticia")
        val snapshot = persisted.last()

        val restored = harness(fixture, initial = snapshot)

        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, restored.coordinator.uiState.value.stage)
        assertEquals(setOf(DATE.plusDays(1)), restored.coordinator.uiState.value.selectedDates)
        assertEquals(fixture.template.id, restored.coordinator.uiState.value.selectedTemplateId)
        assertEquals("Función ficticia", restored.coordinator.uiState.value.position)
        assertTrue(restored.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun interruptedReviewRestoreNeverPersistsTheTemporaryChooserStage() = runBlocking {
        val fixture = fixture()
        val originalPersisted = mutableListOf<V2ManualShiftLoadPersistedState>()
        val original = harness(fixture, persist = originalPersisted::add)
        original.startAndChoose(setOf(DATE.plusDays(1)))
        original.coordinator.requestReview()
        val reviewSnapshot = originalPersisted.last()
        assertEquals(V2ManualShiftLoadStage.REVIEW, reviewSnapshot.stage)

        val restoreGate = CompletableDeferred<Unit>()
        val restoredPersists = mutableListOf<V2ManualShiftLoadPersistedState>()
        val restored = harness(
            fixture = fixture,
            initial = reviewSnapshot,
            persist = restoredPersists::add,
            configurationGate = restoreGate,
        )

        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, restored.coordinator.uiState.value.stage)
        assertTrue(restoredPersists.isEmpty())

        restoreGate.complete(Unit)

        assertEquals(V2ManualShiftLoadStage.REVIEW, restored.coordinator.uiState.value.stage)
        assertEquals(V2ManualShiftLoadStage.REVIEW, restoredPersists.last().stage)
        assertTrue(restoredPersists.none { it.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE })
        assertTrue(restored.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun doubleSaveWhileRepositoryIsSuspendedProducesOneAtomicMutation() = runBlocking {
        val fixture = fixture()
        val gate = CompletableDeferred<Unit>()
        val harness = harness(fixture)
        harness.v2Shifts.gate = gate
        harness.startAndChoose(setOf(DATE.plusDays(1)))
        harness.coordinator.requestReview()

        harness.coordinator.save()
        harness.coordinator.save()
        assertTrue(harness.coordinator.uiState.value.isSaving)
        assertEquals(1, harness.v2Shifts.calls)
        gate.complete(Unit)

        assertEquals(1, harness.v2Shifts.mutations.size)
        assertFalse(harness.coordinator.uiState.value.isActive)
    }

    @Test
    fun emptyAndCrossMonthSelectionsStayInTheGridWithoutWriting() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.coordinator.start(fixture.readyState)

        harness.coordinator.confirmDates(emptySet())
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, harness.coordinator.uiState.value.stage)
        assertNotNull(harness.coordinator.uiState.value.errorMessage)

        harness.coordinator.confirmDates(setOf(DATE, DATE.plusMonths(1)))
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, harness.coordinator.uiState.value.stage)
        assertNotNull(harness.coordinator.uiState.value.errorMessage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun replaceAndCancelRemainExplicitAndClearOnlyInsertedDates() {
        val fixture = fixture()
        val occupied = DATE.plusDays(1)
        val free = DATE.plusDays(2)
        val previous = existingShift(occupied)
        val harness = harness(fixture, existing = listOf(previous))

        harness.startAndChoose(setOf(occupied, free))
        harness.coordinator.requestReview()
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL)
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())

        harness.coordinator.requestReview()
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()

        val mutation = harness.v2Shifts.mutations.single()
        assertEquals(setOf(previous.id), mutation.shiftIdsToDelete)
        assertEquals(setOf(occupied, free), mutation.shiftsToInsert.mapTo(linkedSetOf()) { it.shift.localStartDate })
        assertEquals(setOf(occupied, free), mutation.explicitDayStatusDatesToClear)
    }

    @Test
    fun occupiedDatesChangingWhileDialogIsOpenInvalidatesTheDestructiveChoice() {
        val fixture = fixture()
        val first = DATE.plusDays(1)
        val second = DATE.plusDays(2)
        val harness = harness(fixture, existing = listOf(existingShift(first)))

        harness.startAndChoose(setOf(first, second))
        harness.coordinator.requestReview()
        assertEquals(setOf(first), harness.coordinator.uiState.value.occupiedDates)

        harness.shifts.current = listOf(existingShift(first), existingShift(second))
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)

        val state = harness.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertNull(state.occupiedPolicy)
        assertEquals(setOf(first, second), state.occupiedDates)
        assertNotNull(state.errorMessage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun restoredReplacePreviewNeverAdoptsNewOccupiedDatesSilently() {
        val fixture = fixture()
        val first = DATE.plusDays(1)
        val second = DATE.plusDays(2)
        val persisted = mutableListOf<V2ManualShiftLoadPersistedState>()
        val initialHarness = harness(
            fixture,
            existing = listOf(existingShift(first)),
            persist = persisted::add,
        )
        initialHarness.startAndChoose(setOf(first, second))
        initialHarness.coordinator.requestReview()
        initialHarness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)
        assertEquals(V2ManualShiftLoadStage.REVIEW, initialHarness.coordinator.uiState.value.stage)

        val restored = harness(
            fixture,
            existing = listOf(existingShift(first), existingShift(second)),
            initial = persisted.last(),
        )

        val state = restored.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertNull(state.occupiedPolicy)
        assertNotNull(state.errorMessage)
        assertTrue(restored.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun keepWithEveryDateOccupiedNeverOffersAnEmptySave() {
        val fixture = fixture()
        val first = DATE.plusDays(1)
        val second = DATE.plusDays(2)
        val harness = harness(fixture, existing = listOf(existingShift(first), existingShift(second)))

        harness.startAndChoose(setOf(first, second))
        harness.coordinator.requestReview()
        harness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED)

        val state = harness.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertTrue(state.plannedDates.isEmpty())
        assertNotNull(state.errorMessage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun archivedSelectedTemplateReturnsToChoiceAndPreservesTheDraft() {
        val fixture = fixture()
        val persisted = mutableListOf<V2ManualShiftLoadPersistedState>()
        val initialHarness = harness(fixture, persist = persisted::add)
        initialHarness.startAndChoose(setOf(DATE.plusDays(1)))
        initialHarness.coordinator.updatePosition("Puesto ficticio")

        val archivedCatalog = fixture.catalog.copy(
            workTemplates = listOf(fixture.template.copy(isActive = false)),
        )
        val restored = harness(fixture.copy(catalog = archivedCatalog), initial = persisted.last())

        val state = restored.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertNull(state.selectedTemplateId)
        assertEquals(setOf(DATE.plusDays(1)), state.selectedDates)
        assertEquals("Puesto ficticio", state.position)
        assertNotNull(state.errorMessage)
        assertTrue(restored.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun repositoryFailureKeepsTheReviewAndAllowsOneExplicitRetry() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.startAndChoose(setOf(DATE.plusDays(1)))
        harness.coordinator.requestReview()
        harness.v2Shifts.failure = IllegalStateException("fallo ficticio")

        harness.coordinator.save()
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        assertNotNull(harness.coordinator.uiState.value.errorMessage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())

        harness.v2Shifts.failure = null
        harness.coordinator.save()
        assertEquals(1, harness.v2Shifts.mutations.size)
        assertFalse(harness.coordinator.uiState.value.isActive)
    }

    @Test
    fun concurrentShiftChangeReturnsToReviewableDraftWithoutWriting() {
        val fixture = fixture()
        val selected = DATE.plusDays(1)
        val harness = harness(fixture)
        harness.startAndChoose(setOf(selected))
        harness.coordinator.updatePosition("Puesto ficticio")
        harness.coordinator.requestReview()
        harness.v2Shifts.failure = ConflictingLocalWriteException("Cambio concurrente ficticio")

        harness.coordinator.save()

        val state = harness.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertEquals(setOf(selected), state.selectedDates)
        assertEquals(fixture.template.id, state.selectedTemplateId)
        assertEquals("Puesto ficticio", state.position)
        assertNull(state.occupiedPolicy)
        assertTrue(state.occupiedDates.isEmpty())
        assertTrue(state.plannedDates.isEmpty())
        assertTrue(state.warnings.isEmpty())
        assertNull(state.reviewFingerprint)
        assertEquals("Cambio concurrente ficticio", state.errorMessage)
        assertEquals(1, harness.v2Shifts.calls)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun medicalLeaveWarningRequiresAcknowledgementWithoutModifyingTheLeave() {
        val fixture = fixture(sector = WorkSector.NURSING)
        val selected = DATE.plusDays(1)
        val leave = MedicalLeave(uuid(70), selected, selected, "dato ficticio", NOW, NOW)
        val harness = harness(fixture, medicalLeaves = listOf(leave))

        harness.startAndChoose(setOf(selected))
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        assertTrue(harness.coordinator.uiState.value.warnings.single().contains("carpeta médica"))
        assertTrue(harness.v2Shifts.mutations.isEmpty())

        harness.coordinator.confirmWarnings()
        harness.coordinator.save()
        assertEquals(1, harness.v2Shifts.mutations.size)
    }

    @Test
    fun backfillThatPersistedBeforeAnErrorIsReReadInsteadOfDuplicated() {
        val fixture = fixture(ruleDate = DATE.plusDays(3))
        val harness = harness(fixture)
        val earlier = DATE.minusDays(3)
        harness.catalog.failureAfterPersist = IllegalStateException("fallo posterior ficticio")

        harness.startAndChoose(setOf(earlier))
        harness.coordinator.requestReview()
        harness.coordinator.confirmBackfill()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
        assertNotNull(harness.coordinator.uiState.value.errorMessage)
        assertNotNull(harness.catalog.extended)

        harness.catalog.failureAfterPersist = null
        harness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.REVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()
        assertEquals(1, harness.v2Shifts.mutations.size)
    }

    @Test
    fun incompatibleDraftIsDiscardedWithAnExplicitNoWriteMessage() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.startAndChoose(setOf(DATE.plusDays(1)))

        harness.coordinator.discardIncompatible()

        val state = harness.coordinator.uiState.value
        assertFalse(state.isActive)
        assertTrue(state.infoMessage.orEmpty().contains("no se guardó ninguna jornada"))
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun staleTemplateSnapshotRefreshesTheChooserBeforeAnotherReview() {
        val fixture = fixture()
        val selected = DATE.plusDays(1)
        val harness = harness(fixture)
        harness.startAndChoose(setOf(selected))
        harness.coordinator.requestReview()

        val changedTemplate = fixture.template.copy(
            endTime = LocalTime.of(18, 0),
            colorArgb = 0xFF884422.toInt(),
            updatedAt = NOW.plusSeconds(30),
        )
        harness.catalog.catalog = fixture.catalog.copy(workTemplates = listOf(changedTemplate))
        harness.coordinator.save()

        val state = harness.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, state.stage)
        assertEquals(LocalTime.of(18, 0), state.selectedOption?.template?.endTime)
        assertEquals(0xFF884422.toInt(), state.selectedOption?.template?.colorArgb)
        assertNotNull(state.errorMessage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun savedStateHandleRoundTripsEveryPreparedStageAndConfirmedOccupiedDates() {
        val preparedStages = listOf(
            V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            V2ManualShiftLoadStage.CONFIRM_BACKFILL,
            V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
            V2ManualShiftLoadStage.CONFIRM_WARNINGS,
            V2ManualShiftLoadStage.REVIEW,
        )
        preparedStages.forEach { stage ->
            val expected = V2ManualShiftLoadPersistedState(
                stage = stage,
                timelineId = TIMELINE_ID,
                sector = WorkSector.NURSING,
                selectedDates = setOf(DATE.plusDays(1), DATE.plusDays(2)),
                selectedTemplateId = uuid(5),
                position = "Función ficticia",
                occupiedPolicy = OccupiedDatePolicy.REPLACE,
                occupiedDates = setOf(DATE.plusDays(1)),
                acknowledgedWarnings = listOf("Advertencia ficticia"),
                reviewFingerprint = "huella-ficticia",
            )
            val handle = SavedStateHandle()

            handle.writeV2ManualShiftLoadState(expected)

            assertEquals(expected, handle.readV2ManualShiftLoadState())
        }
    }

    @Test
    fun inactivePlaceTypeTemplateAndMissingApplicableRuleAreNeverOffered() {
        val base = fixture()
        val selected = DATE.plusDays(1)
        val catalogs = listOf(
            base.catalog.copy(workPlaces = listOf(base.place.copy(isActive = false))),
            base.catalog.copy(workTypes = listOf(base.type.copy(isActive = false))),
            base.catalog.copy(workTemplates = listOf(base.template.copy(isActive = false))),
            fixture(ruleDate = DATE.plusDays(5)).catalog,
        )

        catalogs.forEach { catalog ->
            val source = if (catalog.workplaceRuleRevisions.first().effectiveFrom == DATE.plusDays(5)) {
                fixture(ruleDate = DATE.plusDays(5))
            } else {
                base
            }
            val harness = harness(source.copy(catalog = catalog))
            harness.coordinator.start(source.readyState)
            harness.coordinator.confirmDates(setOf(selected))

            assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, harness.coordinator.uiState.value.stage)
            assertTrue(harness.coordinator.uiState.value.templateOptions.isEmpty())
            assertNotNull(harness.coordinator.uiState.value.errorMessage)
            assertTrue(harness.v2Shifts.mutations.isEmpty())
        }
    }

    @Test
    fun overlapAndShortRestWarningsEachRequireConsciousConfirmation() {
        val fixture = fixture()
        val selected = DATE.plusDays(2)
        val overlapHarness = harness(
            fixture,
            existing = listOf(existingShiftWithTimes(selected, LocalTime.of(12, 0), LocalTime.of(20, 0))),
        )
        overlapHarness.startAndChoose(setOf(selected))
        overlapHarness.coordinator.requestReview()
        overlapHarness.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT)
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, overlapHarness.coordinator.uiState.value.stage)
        assertTrue(overlapHarness.coordinator.uiState.value.warnings.any { it.contains("superponen") })
        overlapHarness.coordinator.dismissWarnings()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, overlapHarness.coordinator.uiState.value.stage)

        val restHarness = harness(
            fixture,
            existing = listOf(
                existingShiftWithTimes(selected.minusDays(1), LocalTime.of(20, 0), LocalTime.of(23, 0)),
            ),
        )
        restHarness.startAndChoose(setOf(selected))
        restHarness.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, restHarness.coordinator.uiState.value.stage)
        assertTrue(restHarness.coordinator.uiState.value.warnings.any { it.contains("descanso") })
        restHarness.coordinator.confirmWarnings()
        assertEquals(V2ManualShiftLoadStage.REVIEW, restHarness.coordinator.uiState.value.stage)
    }

    @Test
    fun adoptedOvernightAndTwentyFourHourTemplatesKeepExactTemporalSnapshots() {
        val adoptedId = uuid(88)
        val overnightBase = fixture(sector = WorkSector.POLICE)
        val overnightTemplate = overnightBase.template.copy(
            startTime = LocalTime.of(21, 0),
            endTime = LocalTime.of(6, 0),
            legacyScheduleCombinationId = adoptedId,
        )
        val overnight = overnightBase.copy(
            template = overnightTemplate,
            catalog = overnightBase.catalog.copy(workTemplates = listOf(overnightTemplate)),
        )
        val overnightHarness = harness(overnight)
        val selected = DATE.plusDays(1)
        overnightHarness.startAndChoose(setOf(selected))
        overnightHarness.coordinator.requestReview()
        overnightHarness.coordinator.save()

        val overnightWrite = overnightHarness.v2Shifts.mutations.single().shiftsToInsert.single()
        assertEquals(adoptedId, overnightWrite.shift.sourceScheduleCombinationId)
        assertEquals(LocalTime.of(21, 0), overnightWrite.shift.startTimeSnapshot)
        assertEquals(LocalTime.of(6, 0), overnightWrite.shift.endTimeSnapshot)
        assertEquals(selected.plusDays(1), overnightWrite.shift.endAt.atZone(ZONE).toLocalDate())

        val fullDayBase = fixture(sector = WorkSector.MEDICINE)
        val fullDayTemplate = fullDayBase.template.copy(
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 0),
        )
        val fullDay = fullDayBase.copy(
            template = fullDayTemplate,
            catalog = fullDayBase.catalog.copy(workTemplates = listOf(fullDayTemplate)),
        )
        val fullDayHarness = harness(fullDay)
        fullDayHarness.startAndChoose(setOf(selected))
        fullDayHarness.coordinator.requestReview()
        fullDayHarness.coordinator.save()

        val fullDayWrite = fullDayHarness.v2Shifts.mutations.single().shiftsToInsert.single()
        assertEquals(24L, java.time.Duration.between(fullDayWrite.shift.startAt, fullDayWrite.shift.endAt).toHours())
        assertNull(fullDayWrite.shift.sourceScheduleCombinationId)
    }

    private fun harness(
        fixture: Fixture,
        existing: List<Shift> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        initial: V2ManualShiftLoadPersistedState = V2ManualShiftLoadPersistedState(),
        persist: (V2ManualShiftLoadPersistedState) -> Unit = {},
        configurationGate: CompletableDeferred<Unit>? = null,
    ): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        val configurations = FakeConfigurations(fixture.history, configurationGate)
        val catalog = FakeCatalog(configurations, fixture.catalog)
        val objectives = FakeObjectives(listOf(fixture.objective))
        val shifts = FakeShifts(existing)
        val v2Shifts = FakeV2Shifts()
        var nextUuid = 1_000L
        val coordinator = V2ManualShiftLoadCoordinator(
            configurationRepository = configurations,
            catalogRepository = catalog,
            objectiveRepository = objectives,
            shiftRepository = shifts,
            medicalLeaveRepository = FakeMedicalLeaves(medicalLeaves),
            v2ShiftRepository = v2Shifts,
            clock = CLOCK,
            zoneId = ZONE,
            uuidProvider = UuidProvider { uuid(nextUuid++) },
            scope = scope,
            initialPersistedState = initial,
            persist = persist,
        )
        return Harness(coordinator, catalog, shifts, v2Shifts)
    }

    private fun Harness.startAndChoose(dates: Set<LocalDate>) {
        coordinator.start(fixtureReadyState())
        coordinator.confirmDates(dates)
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, coordinator.uiState.value.stage)
        coordinator.chooseTemplate(coordinator.uiState.value.templateOptions.first().template.id)
    }

    private fun Harness.fixtureReadyState(): WorkSetupState.V2Ready {
        val state = coordinator.uiState.value
        val history = catalog.configurations.history
        val revision = requireNotNull(history.timeline.revisionAt(DATE))
        return WorkSetupState.V2Ready(state.timelineId ?: history.timeline.id, revision)
    }

    private data class Harness(
        val coordinator: V2ManualShiftLoadCoordinator,
        val catalog: FakeCatalog,
        val shifts: FakeShifts,
        val v2Shifts: FakeV2Shifts,
    )

    private data class Fixture(
        val timelineId: UUID,
        val sector: WorkSector,
        val revisions: List<EffectiveRevision<WorkConfiguration>>,
        val history: WorkConfigurationHistory,
        val objective: Objective,
        val place: WorkPlace,
        val type: WorkType,
        val template: WorkTemplate,
        val rule: WorkplaceRuleRevision,
        val catalog: WorkCatalog,
    ) {
        val readyState: WorkSetupState.V2Ready
            get() = WorkSetupState.V2Ready(timelineId, revisions.first())
    }

    private fun fixture(
        sector: WorkSector = WorkSector.PRIVATE_SECURITY,
        revisions: List<EffectiveRevision<WorkConfiguration>> = listOf(revision(uuid(1), DATE, sector)),
        ruleDate: LocalDate = revisions.first().effectiveFrom,
        origin: WorkConfigurationOrigin = WorkConfigurationOrigin.NEW_V2,
    ): Fixture {
        val timelineId = TIMELINE_ID
        val history = WorkConfigurationHistory(
            origin = origin,
            timeline = EffectiveDateTimeline(timelineId, revisions),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        )
        val objective = Objective(uuid(2), "Lugar ficticio", "FIC", null, null, true, NOW, NOW)
        val place = WorkPlace(uuid(3), timelineId, sector, objective.id, true, NOW, NOW)
        val type = WorkType.create(uuid(4), timelineId, sector, "Trabajo habitual", NOW)
        val template = WorkTemplate(
            id = uuid(5),
            timelineId = timelineId,
            sector = sector,
            workPlaceId = place.id,
            objectiveId = objective.id,
            workTypeId = type.id,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
            colorArgb = 0xFF336699.toInt(),
            isActive = true,
            legacyScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        val rule = WorkplaceRuleRevision(
            id = uuid(6),
            timelineId = timelineId,
            sector = sector,
            workPlaceId = place.id,
            objectiveId = objective.id,
            effectiveFrom = ruleDate,
            rules = WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)),
            createdAt = NOW,
        )
        val catalog = WorkCatalog(timelineId, sector, listOf(place), listOf(type), listOf(template), listOf(rule))
        return Fixture(timelineId, sector, revisions, history, objective, place, type, template, rule, catalog)
    }

    private fun existingShift(date: LocalDate): Shift {
        val start = date.atTime(8, 0).atZone(ZONE).toInstant()
        return Shift(
            id = uuid(900 + date.dayOfMonth.toLong()),
            startAt = start,
            endAt = date.atTime(16, 0).atZone(ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = date,
            objectiveNameSnapshot = "Historia ficticia",
            objectiveAbbreviationSnapshot = "HIS",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun existingShiftWithTimes(date: LocalDate, start: LocalTime, end: LocalTime): Shift {
        val startAt = date.atTime(start).atZone(ZONE).toInstant()
        val endDate = if (end > start) date else date.plusDays(1)
        return existingShift(date).copy(
            startAt = startAt,
            endAt = endDate.atTime(end).atZone(ZONE).toInstant(),
            startTimeSnapshot = start,
            endTimeSnapshot = end,
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 10)
        val NOW: Instant = Instant.parse("2026-08-22T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TIMELINE_ID: UUID = uuid(100)

        fun uuid(value: Long): UUID = UUID(0L, value)

        fun revision(id: UUID, date: LocalDate, sector: WorkSector) = EffectiveRevision(
            id = id,
            effectiveFrom = date,
            value = WorkConfiguration(sector, HoursReference.PendingSetup, null),
        )
    }
}

private class FakeConfigurations(
    initial: WorkConfigurationHistory,
    private val getGate: CompletableDeferred<Unit>? = null,
) : WorkConfigurationRepository {
    var history: WorkConfigurationHistory = initial
    override fun observe(): Flow<WorkConfigurationHistory?> = MutableStateFlow(history)
    override suspend fun get(): WorkConfigurationHistory {
        getGate?.await()
        return history
    }
    override suspend fun createInitial(timelineId: UUID, firstRevision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
    override suspend fun addRevision(timelineId: UUID, revision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
    override suspend fun createPerPeriodValue(timelineId: UUID, entry: PerPeriodHoursEntry) = error("No se usa")
    override suspend fun updatePerPeriodValue(timelineId: UUID, entry: PerPeriodHoursEntry) = error("No se usa")
}

private class FakeCatalog(
    val configurations: FakeConfigurations,
    initial: WorkCatalog,
) : WorkCatalogRepository {
    var catalog = initial
    var extended: NewV2Backfill? = null
    var failureAfterPersist: Throwable? = null

    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> {
        require(catalog.timelineId == timelineId && catalog.sector == sector)
        return MutableStateFlow(catalog)
    }
    override fun observeRecentlyUsed(timelineId: UUID, sector: WorkSector, limit: Int): Flow<List<RecentWorkTemplate>> =
        MutableStateFlow(emptyList())
    override suspend fun getWorkPlace(id: UUID): WorkPlace? = catalog.workPlaces.firstOrNull { it.id == id }
    override suspend fun getWorkType(id: UUID): WorkType? = catalog.workTypes.firstOrNull { it.id == id }
    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? = catalog.workTemplates.firstOrNull { it.id == id }
    override suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision? =
        catalog.ruleRevisionAt(workPlaceId, date)
    override suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision> =
        catalog.workplaceRuleRevisions.filter { it.workPlaceId == workPlaceId }
    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory {
        extended = extension
        val revisions = configurations.history.timeline.revisions + extension.configurationRevision
        val updated = configurations.history.copy(
            timeline = EffectiveDateTimeline(configurations.history.timeline.id, revisions),
        )
        configurations.history = updated
        catalog = catalog.copy(
            workplaceRuleRevisions = catalog.workplaceRuleRevisions + extension.workplaceRuleRevisions,
        )
        failureAfterPersist?.let { throw it }
        return updated
    }
    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet) = error("No se usa")
    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace) = error("No se usa")
    override suspend fun adoptWorkPlace(adoption: WorkPlaceAdoption): WorkPlaceAdoptionResult = error("No se usa")
    override suspend fun updateWorkPlace(update: WorkPlaceUpdate) = error("No se usa")
    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkType(workType: WorkType) = error("No se usa")
    override suspend fun updateWorkType(update: WorkTypeUpdate) = error("No se usa")
    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkTemplate(workTemplate: WorkTemplate) = error("No se usa")
    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate) = error("No se usa")
    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun addWorkplaceRuleRevision(revision: WorkplaceRuleRevision, confirmationNow: Instant) = error("No se usa")
}

private class FakeObjectives(initial: List<Objective>) : ObjectiveRepository {
    private val values = initial.toMutableList()
    override fun observeActive(): Flow<List<Objective>> = MutableStateFlow(values.filter(Objective::isActive))
    override fun observeAll(): Flow<List<Objective>> = MutableStateFlow(values.toList())
    override suspend fun getById(id: UUID): Objective? = values.firstOrNull { it.id == id }
    override suspend fun create(objective: Objective) { values += objective }
    override suspend fun update(objective: Objective) { values.replaceAll { if (it.id == objective.id) objective else it } }
    override suspend fun hide(id: UUID, updatedAt: Instant) = error("No se usa")
    override suspend fun delete(id: UUID) = error("No se usa")
}

private class FakeShifts(initial: List<Shift>) : ShiftRepository {
    var current: List<Shift> = initial
    override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(current.isNotEmpty())
    override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
        MutableStateFlow(current.filter { it.localStartDate in startDateInclusive..endDateInclusive })
    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        MutableStateFlow(current.filter { it.endAt > instantExclusive })
    override suspend fun getById(id: UUID): Shift? = current.firstOrNull { it.id == id }
    override suspend fun insert(shift: Shift) = error("No se usa")
    override suspend fun update(shift: Shift) = error("No se usa")
    override suspend fun delete(id: UUID) = error("No se usa")
    override suspend fun applyBatch(mutation: ShiftBatchMutation) = error("La ruta V1 no debe usarse")
}

private class FakeMedicalLeaves(
    private val current: List<MedicalLeave> = emptyList(),
) : MedicalLeaveRepository {
    override fun observeIntersecting(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<MedicalLeave>> =
        MutableStateFlow(current.filter { it.endDateInclusive >= startDateInclusive && it.startDate <= endDateInclusive })
    override suspend fun create(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun update(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun delete(id: UUID) = error("No se usa")
}

private class FakeV2Shifts : V2ShiftRepository {
    val mutations = mutableListOf<V2ShiftBatchMutation>()
    val occupancyExpectations = mutableListOf<ShiftOccupancyExpectation>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null
    var calls: Int = 0
    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> = MutableStateFlow(null)
    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? = null
    override suspend fun insert(write: V2ShiftWrite) = error("No se usa")
    override suspend fun update(write: V2ShiftWrite) = error("No se usa")
    override suspend fun deleteShift(shiftId: UUID) = error("No se usa")
    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
    ) {
        calls++
        gate?.await()
        failure?.let { throw it }
        mutations += mutation
        occupancyExpectations += expectedOccupancy
    }
}
