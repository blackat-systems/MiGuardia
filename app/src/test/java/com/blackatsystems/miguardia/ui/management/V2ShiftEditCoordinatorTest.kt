package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
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
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ShiftEditCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun inspectionRejectsAnOrphanShiftAndNeverOffersEditActions() {
        val fixture = fixture()
        val orphan = neighborShift(uuid(90), DATE, LocalTime.of(18, 0), LocalTime.of(22, 0))
        val harness = harness(fixture, writes = listOf(fixture.original), otherShifts = listOf(orphan))

        harness.coordinator.inspectDay(fixture.ready, DATE)

        assertEquals(V2ShiftDayInspectionState.ERROR, harness.coordinator.uiState.value.inspectionState)
        assertTrue(harness.coordinator.uiState.value.dayRows.isEmpty())
        assertTrue(harness.coordinator.uiState.value.errorMessage.orEmpty().contains("cambiaron"))
        harness.coordinator.beginDayEditing()
        assertEquals(V2ShiftEditStage.IDLE, harness.coordinator.uiState.value.stage)
        assertEquals(0, harness.v2.updateCalls)
        assertEquals(0, harness.v2.deleteCalls)
    }

    @Test
    fun positionOnlyEditKeepsTheCompleteHistoricalPairEvenWhenSourcesAreArchived() {
        val fixture = fixture()
        val companion = fixture.write(uuid(11), DATE, fixture.alternative)
        val harness = harness(fixture, writes = listOf(fixture.original, companion))
        harness.catalog.catalog = fixture.catalog.copy(
            workPlaces = fixture.catalog.workPlaces.map { it.copy(isActive = false) },
            workTypes = fixture.catalog.workTypes.map { it.copy(isActive = false) },
            workTemplates = fixture.catalog.workTemplates.map { it.copy(isActive = false) },
        )

        harness.openEditor(fixture.original.shift.id)
        assertTrue(harness.coordinator.uiState.value.templateOptions.isEmpty())
        harness.coordinator.updatePosition("  Puesto corregido  ")
        harness.coordinator.requestReview()
        assertEquals(V2ShiftEditStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        harness.coordinator.confirmWarnings()
        harness.coordinator.save()

        val updated = harness.v2.writes.getValue(fixture.original.shift.id)
        assertEquals(fixture.original.snapshot, updated.snapshot)
        assertEquals(
            fixture.original.shift.copy(position = "Puesto corregido", updatedAt = updated.shift.updatedAt),
            updated.shift,
        )
        assertTrue(updated.shift.updatedAt.isAfter(fixture.original.shift.updatedAt))
        assertEquals(companion, harness.v2.writes.getValue(companion.shift.id))
        assertEquals(1, harness.v2.updateCalls)
    }

    @Test
    fun changingTemplateUpdatesOnlyTheEditableSnapshotsAndKeepsIdentityAndDate() {
        val fixture = fixture()
        val harness = harness(fixture)

        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.chooseTemplate(fixture.alternative.id)
        harness.coordinator.updatePosition("Función B")
        harness.coordinator.requestReview()
        assertEquals(V2ShiftEditStage.REVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()

        val updated = harness.v2.writes.getValue(fixture.original.shift.id)
        assertEquals(fixture.original.shift.id, updated.shift.id)
        assertEquals(fixture.original.shift.localStartDate, updated.shift.localStartDate)
        assertEquals(fixture.original.shift.zoneId, updated.shift.zoneId)
        assertEquals(fixture.original.shift.createdAt, updated.shift.createdAt)
        assertEquals(fixture.original.shift.status, updated.shift.status)
        assertEquals(fixture.alternative.id, updated.snapshot.templateId)
        assertEquals(fixture.alternative.startTime, updated.shift.startTimeSnapshot)
        assertEquals(fixture.alternative.endTime, updated.shift.endTimeSnapshot)
        assertEquals(fixture.alternative.colorArgb, updated.shift.colorArgbSnapshot)
        assertEquals(fixture.revision.id, updated.snapshot.configurationRevisionId)
        assertEquals("Función B", updated.shift.position)
    }

    @Test
    fun currentTemplateWithTheSameIdRemainsDistinctFromTheHistoricalSnapshot() {
        val fixture = fixture()
        val currentTemplate = fixture.template.copy(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            colorArgb = 0xFF557799.toInt(),
            updatedAt = OLD.plusSeconds(1),
        )
        val harness = harness(fixture)
        harness.catalog.catalog = fixture.catalog.copy(
            workTemplates = listOf(currentTemplate, fixture.alternative),
        )

        harness.openEditor(fixture.original.shift.id)

        val state = harness.coordinator.uiState.value
        assertTrue(state.usesHistoricalTemplate)
        assertFalse(state.templateOptions.single { it.template.id == currentTemplate.id }.matchesHistoricalSelection)
        assertFalse(state.hasUnconfirmedChanges)

        harness.coordinator.chooseTemplate(currentTemplate.id)
        assertFalse(harness.coordinator.uiState.value.usesHistoricalTemplate)
        assertTrue(harness.coordinator.uiState.value.hasUnconfirmedChanges)
        harness.coordinator.requestReview()
        harness.coordinator.save()

        val updated = harness.v2.writes.getValue(fixture.original.shift.id)
        assertEquals(currentTemplate.id, updated.snapshot.templateId)
        assertEquals(currentTemplate.startTime, updated.shift.startTimeSnapshot)
        assertEquals(currentTemplate.endTime, updated.shift.endTimeSnapshot)
        assertEquals(currentTemplate.colorArgb, updated.shift.colorArgbSnapshot)
        assertEquals(fixture.original.shift.localStartDate, updated.shift.localStartDate)
    }

    @Test
    fun reviewRequiresSameDateOverlapShortRestAndMedicalLeaveWarnings() {
        val fixture = fixture()
        val sameDate = fixture.write(uuid(12), DATE, fixture.template).copy(
            shift = fixture.write(uuid(12), DATE, fixture.template).shift.copy(
                startAt = DATE.atTime(10, 0).atZone(ZONE).toInstant(),
                endAt = DATE.atTime(14, 0).atZone(ZONE).toInstant(),
                startTimeSnapshot = LocalTime.of(10, 0),
                endTimeSnapshot = LocalTime.of(14, 0),
            ),
        )
        val previous = neighborShift(uuid(13), DATE.minusDays(1), LocalTime.of(20, 0), LocalTime.of(4, 0))
        val medical = MedicalLeave(uuid(14), DATE, DATE, "dato privado ficticio", OLD, OLD)
        val harness = harness(
            fixture,
            writes = listOf(fixture.original, sameDate),
            otherShifts = listOf(previous),
            medicalLeaves = listOf(medical),
        )

        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Cambio")
        harness.coordinator.requestReview()

        val warnings = harness.coordinator.uiState.value.warnings
        assertEquals(V2ShiftEditStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        assertTrue(warnings.any { it.contains("más de una jornada") })
        assertTrue(warnings.any { it.contains("superponen") })
        assertTrue(warnings.any { it.contains("descanso") })
        assertTrue(warnings.any { it.contains("carpeta médica") })
        harness.coordinator.confirmWarnings()
        harness.coordinator.save()
        assertEquals(DATE.minusDays(2), harness.v2.lastOccupancyPreview?.startDateInclusive)
        assertEquals(DATE.plusDays(2), harness.v2.lastOccupancyPreview?.endDateInclusive)
    }

    @Test
    fun unchangedDraftCannotReviewOrAdvanceTimestamp() {
        val fixture = fixture()
        val harness = harness(fixture)

        harness.openEditor(fixture.original.shift.id)
        assertFalse(harness.coordinator.uiState.value.hasUnconfirmedChanges)
        harness.coordinator.requestReview()

        assertEquals(V2ShiftEditStage.EDIT_FORM, harness.coordinator.uiState.value.stage)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, harness.v2.updateCalls)
    }

    @Test
    fun staleFullPairAndStaleNeighborBothRejectWithoutOverwritingTheDraft() {
        val fixture = fixture()
        val neighbor = neighborShift(uuid(15), DATE.plusDays(1), LocalTime.of(8, 0), LocalTime.of(16, 0))
        val pairHarness = harness(fixture)
        pairHarness.openEditor(fixture.original.shift.id)
        pairHarness.coordinator.updatePosition("Mi borrador")
        pairHarness.coordinator.requestReview()
        val concurrentPair = fixture.original.copy(
            snapshot = fixture.original.snapshot.copy(workTypeNameSnapshot = "Cambio concurrente"),
        )
        pairHarness.v2.replaceExternally(concurrentPair)

        pairHarness.coordinator.save()

        assertEquals(V2ShiftEditStage.EDIT_FORM, pairHarness.coordinator.uiState.value.stage)
        assertEquals("Mi borrador", pairHarness.coordinator.uiState.value.position)
        assertEquals(concurrentPair, pairHarness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, pairHarness.v2.successfulUpdates)

        val neighborHarness = harness(fixture, otherShifts = listOf(neighbor))
        neighborHarness.openEditor(fixture.original.shift.id)
        neighborHarness.coordinator.updatePosition("Otro borrador")
        neighborHarness.coordinator.requestReview()
        neighborHarness.shifts.replace(
            neighbor.copy(
                endAt = neighbor.endAt.plusSeconds(60),
                updatedAt = neighbor.updatedAt.plusMillis(1),
            ),
        )

        neighborHarness.coordinator.save()

        assertEquals(V2ShiftEditStage.EDIT_FORM, neighborHarness.coordinator.uiState.value.stage)
        assertEquals("Otro borrador", neighborHarness.coordinator.uiState.value.position)
        assertEquals(fixture.original, neighborHarness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, neighborHarness.v2.successfulUpdates)
    }

    @Test
    fun neighborConflictRequiresWarningConfirmationAgain() {
        val fixture = fixture()
        val neighbor = neighborShift(uuid(155), DATE.plusDays(1), LocalTime.of(2, 0), LocalTime.of(10, 0))
        val harness = harness(fixture, otherShifts = listOf(neighbor))
        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Borrador con descanso corto")
        harness.coordinator.requestReview()
        assertEquals(V2ShiftEditStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        harness.coordinator.confirmWarnings()
        assertEquals(V2ShiftEditStage.REVIEW, harness.coordinator.uiState.value.stage)

        harness.shifts.replace(neighbor.copy(updatedAt = neighbor.updatedAt.plusMillis(1)))
        harness.coordinator.save()

        assertEquals(V2ShiftEditStage.EDIT_FORM, harness.coordinator.uiState.value.stage)
        assertTrue(harness.coordinator.uiState.value.acknowledgedWarnings.isEmpty())
        harness.coordinator.requestReview()
        assertEquals(V2ShiftEditStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
    }

    @Test
    fun staleDeleteRejectsAndFreshDeleteRemovesOnlyTheConfirmedJourney() {
        val fixture = fixture()
        val companion = fixture.write(uuid(16), DATE, fixture.alternative)
        val harness = harness(fixture, writes = listOf(fixture.original, companion))
        harness.inspectAndOpenActions()
        harness.coordinator.requestDelete(fixture.original.shift.id)
        val concurrent = fixture.original.copy(
            snapshot = fixture.original.snapshot.copy(workTypeNameSnapshot = "Cambio concurrente"),
        )
        harness.v2.replaceExternally(concurrent)

        harness.coordinator.confirmDelete()

        assertEquals(V2ShiftEditStage.DAY_ACTIONS, harness.coordinator.uiState.value.stage)
        assertEquals(concurrent, harness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(companion, harness.v2.writes.getValue(companion.shift.id))
        assertEquals(0, harness.v2.successfulDeletes)

        harness.coordinator.requestDelete(fixture.original.shift.id)
        harness.coordinator.confirmDelete()
        assertFalse(harness.v2.writes.containsKey(fixture.original.shift.id))
        assertEquals(companion, harness.v2.writes.getValue(companion.shift.id))
        assertEquals(1, harness.v2.successfulDeletes)
    }

    @Test
    fun restoredDeleteFingerprintCannotBeConfusedBySnapshotSeparators() {
        val fixture = fixture()
        val confirmed = fixture.original.copy(
            shift = fixture.original.shift.copy(
                objectiveNameSnapshot = "A|B",
                objectiveAbbreviationSnapshot = "CDE",
            ),
        )
        val changed = confirmed.copy(
            shift = confirmed.shift.copy(
                objectiveNameSnapshot = "A",
                objectiveAbbreviationSnapshot = "B|CDE",
            ),
        )
        var persisted = V2ShiftEditPersistedState()
        val first = harness(fixture, writes = listOf(confirmed), persist = { persisted = it })
        first.inspectAndOpenActions()
        first.coordinator.requestDelete(confirmed.shift.id)
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, persisted.stage)

        val restored = harness(fixture, writes = listOf(changed), initial = persisted)

        assertEquals(V2ShiftEditStage.DAY_ACTIONS, restored.coordinator.uiState.value.stage)
        assertTrue(restored.coordinator.uiState.value.errorMessage.orEmpty().contains("cambió"))
        assertEquals(changed, restored.v2.writes.getValue(changed.shift.id))
        assertEquals(0, restored.v2.successfulDeletes)
    }

    @Test
    fun doubleTapProducesAtMostOneWrite() {
        val fixture = fixture()
        val gate = CompletableDeferred<Unit>()
        val harness = harness(fixture)
        harness.v2.writeGate = gate
        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Una vez")
        harness.coordinator.requestReview()

        harness.coordinator.save()
        harness.coordinator.save()
        assertEquals(1, harness.v2.updateCalls)

        gate.complete(Unit)
        assertEquals(1, harness.v2.successfulUpdates)
        assertEquals(1, harness.coordinator.uiState.value.successSequence)
    }

    @Test
    fun failureKeepsDraftAndRetryWritesSafely() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Borrador recuperable")
        harness.coordinator.requestReview()
        harness.v2.failure = IllegalStateException("Fallo ficticio")

        harness.coordinator.save()

        assertEquals(V2ShiftEditStage.REVIEW, harness.coordinator.uiState.value.stage)
        assertEquals("Borrador recuperable", harness.coordinator.uiState.value.position)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
        harness.v2.failure = null
        harness.coordinator.retry()
        harness.coordinator.save()
        assertEquals("Borrador recuperable", harness.v2.writes.getValue(fixture.original.shift.id).shift.position)
        assertEquals(1, harness.v2.successfulUpdates)
    }

    @Test
    fun recreationRestoresDraftButRebuildsReviewAndConsumesSuccessOnlyOnce() {
        val fixture = fixture()
        var persisted = V2ShiftEditPersistedState()
        val first = harness(fixture, persist = { persisted = it })
        first.openEditor(fixture.original.shift.id)
        first.coordinator.chooseTemplate(fixture.alternative.id)
        first.coordinator.updatePosition("Restaurado")
        first.coordinator.requestReview()
        assertEquals(V2ShiftEditStage.REVIEW, persisted.stage)

        val restored = harness(fixture, initial = persisted, persist = { persisted = it })

        assertEquals(V2ShiftEditStage.REVIEW, restored.coordinator.uiState.value.stage)
        assertEquals(fixture.alternative.id, restored.coordinator.uiState.value.selectedTemplateId)
        assertEquals("Restaurado", restored.coordinator.uiState.value.position)
        assertTrue(restored.v2.getCalls > 0)
        restored.coordinator.save()
        val sequence = restored.coordinator.uiState.value.successSequence
        assertEquals(1, sequence)
        restored.coordinator.consumeSuccess(sequence)
        assertEquals(0, restored.coordinator.uiState.value.successSequence)
        assertEquals(V2ShiftEditStage.IDLE, persisted.stage)

        val afterSuccessRecreation = harness(fixture, initial = persisted)
        assertEquals(0, afterSuccessRecreation.coordinator.uiState.value.successSequence)
        assertEquals(V2ShiftEditStage.IDLE, afterSuccessRecreation.coordinator.uiState.value.stage)
    }

    @Test
    fun recreationRehydratesDayRowsBeforeDiscardOrDeleteCancellation() {
        val fixture = fixture()
        var editPersisted = V2ShiftEditPersistedState()
        val edit = harness(fixture, persist = { editPersisted = it })
        edit.openEditor(fixture.original.shift.id)
        edit.coordinator.updatePosition("Borrador recreado")

        val restoredEdit = harness(fixture, initial = editPersisted)
        restoredEdit.coordinator.back()
        restoredEdit.coordinator.confirmDiscard()
        assertEquals(V2ShiftEditStage.DAY_ACTIONS, restoredEdit.coordinator.uiState.value.stage)
        assertEquals(listOf(fixture.original.shift.id), restoredEdit.coordinator.uiState.value.dayRows.map { it.shift.id })

        var deletePersisted = V2ShiftEditPersistedState()
        val delete = harness(fixture, persist = { deletePersisted = it })
        delete.inspectAndOpenActions()
        delete.coordinator.requestDelete(fixture.original.shift.id)

        val restoredDelete = harness(fixture, initial = deletePersisted)
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, restoredDelete.coordinator.uiState.value.stage)
        restoredDelete.coordinator.cancelDelete()
        assertEquals(V2ShiftEditStage.DAY_ACTIONS, restoredDelete.coordinator.uiState.value.stage)
        assertEquals(listOf(fixture.original.shift.id), restoredDelete.coordinator.uiState.value.dayRows.map { it.shift.id })
    }

    @Test
    fun recreationRehydratesActualExpectationBeforeConfirmingExactDeletion() {
        val fixture = fixture()
        val actualExpectation = expectationWithActual(fixture)
        var persisted = V2ShiftEditPersistedState()
        val first = harness(
            fixture,
            actualExpectation = actualExpectation,
            persist = { persisted = it },
        )
        first.inspectAndOpenActions()
        first.coordinator.requestDelete(fixture.original.shift.id)

        val restored = harness(
            fixture,
            actualExpectation = actualExpectation,
            initial = persisted,
        )
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, restored.coordinator.uiState.value.stage)
        assertEquals(actualExpectation, restored.coordinator.uiState.value.actualExpectation)

        restored.coordinator.confirmDelete()

        assertEquals(actualExpectation, restored.v2.lastExpectedActual)
        assertEquals(1, restored.v2.successfulDeletes)
    }

    @Test
    fun explicitHandoffToActualClosesStructuralDraftWithoutWritingIt() {
        val fixture = fixture()
        val actualExpectation = expectationWithActual(fixture)
        var persisted = V2ShiftEditPersistedState()
        val harness = harness(
            fixture,
            actualExpectation = actualExpectation,
            persist = { persisted = it },
        )
        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Borrador que no debe guardarse")

        harness.coordinator.handoffToActual()

        assertEquals(V2ShiftEditStage.IDLE, harness.coordinator.uiState.value.stage)
        assertEquals(V2ShiftEditPersistedState(), persisted)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, harness.v2.successfulUpdates)
    }

    @Test
    fun incompatibleRootCancelsPendingReadsWithoutReopeningTheEditor() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.inspectAndOpenActions()
        val lookupGate = CompletableDeferred<Unit>()
        harness.v2.lookupGate = lookupGate

        harness.coordinator.editShift(fixture.original.shift.id)
        assertTrue(harness.coordinator.uiState.value.isLoading)
        harness.coordinator.resume(WorkSetupState.V2Ready(uuid(404), fixture.revision))
        assertEquals(V2ShiftEditStage.IDLE, harness.coordinator.uiState.value.stage)

        lookupGate.complete(Unit)
        assertEquals(V2ShiftEditStage.IDLE, harness.coordinator.uiState.value.stage)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, harness.v2.updateCalls)
    }

    @Test
    fun incompatibleRootDuringWriteKeepsTheSurfaceUntilTheAtomicResult() {
        val fixture = fixture()
        val successful = harness(fixture)
        successful.openEditor(fixture.original.shift.id)
        successful.coordinator.updatePosition("Guardar una vez")
        successful.coordinator.requestReview()
        val successGate = CompletableDeferred<Unit>()
        successful.v2.writeGate = successGate

        successful.coordinator.save()
        assertTrue(successful.coordinator.uiState.value.isSaving)
        successful.coordinator.resume(WorkSetupState.V2Ready(uuid(405), fixture.revision))
        assertEquals(V2ShiftEditStage.REVIEW, successful.coordinator.uiState.value.stage)
        assertTrue(successful.coordinator.uiState.value.isSaving)

        successGate.complete(Unit)
        assertEquals(V2ShiftEditStage.IDLE, successful.coordinator.uiState.value.stage)
        assertEquals("Guardar una vez", successful.v2.writes.getValue(fixture.original.shift.id).shift.position)
        assertEquals(1, successful.v2.successfulUpdates)

        val failed = harness(fixture)
        failed.openEditor(fixture.original.shift.id)
        failed.coordinator.updatePosition("No guardar")
        failed.coordinator.requestReview()
        val failureGate = CompletableDeferred<Unit>()
        failed.v2.writeGate = failureGate
        failed.v2.failure = IllegalStateException("Fallo ficticio")

        failed.coordinator.save()
        failed.coordinator.resume(WorkSetupState.V2Ready(uuid(406), fixture.revision))
        assertTrue(failed.coordinator.uiState.value.isSaving)
        failureGate.complete(Unit)

        assertEquals(V2ShiftEditStage.IDLE, failed.coordinator.uiState.value.stage)
        assertTrue(failed.coordinator.uiState.value.infoMessage.orEmpty().contains("ya no coincide"))
        assertEquals(fixture.original, failed.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, failed.v2.successfulUpdates)
    }

    @Test
    fun recreationNeverReplacesAnUnavailableTemplateDraftAutomatically() {
        val fixture = fixture()
        var persisted = V2ShiftEditPersistedState()
        val first = harness(fixture, persist = { persisted = it })
        first.openEditor(fixture.original.shift.id)
        first.coordinator.chooseTemplate(fixture.alternative.id)

        val restored = harness(fixture, initial = persisted, autoResume = false)
        restored.catalog.catalog = fixture.catalog.copy(workTemplates = listOf(fixture.template))
        restored.coordinator.resume(fixture.ready)

        val state = restored.coordinator.uiState.value
        assertEquals(V2ShiftEditStage.EDIT_FORM, state.stage)
        assertEquals(fixture.alternative.id, state.selectedTemplateId)
        assertFalse(state.usesHistoricalTemplate)
        assertTrue(state.errorMessage.orEmpty().contains("ya no está activa"))
        assertEquals(fixture.original, restored.v2.writes.getValue(fixture.original.shift.id))
    }

    @Test
    fun incompatibleRootOrTimelineDiscardsRestoredSurfaceWithoutWriting() {
        val fixture = fixture()
        val initial = V2ShiftEditPersistedState(
            stage = V2ShiftEditStage.EDIT_FORM,
            timelineId = uuid(404),
            date = DATE,
            targetShiftId = fixture.original.shift.id,
            selectedTemplateId = fixture.template.id,
            position = "No escribir",
        )
        val mismatch = harness(fixture, initial = initial, autoResume = false)

        mismatch.coordinator.resume(fixture.ready)

        assertEquals(V2ShiftEditStage.IDLE, mismatch.coordinator.uiState.value.stage)
        assertEquals(0, mismatch.v2.updateCalls)

        val needsFirstSet = harness(fixture, initial = initial.copy(timelineId = fixture.timelineId), autoResume = false)
        needsFirstSet.coordinator.resume(
            WorkSetupState.V2NeedsFirstSet(fixture.timelineId, fixture.revision, emptySet()),
        )
        assertEquals(V2ShiftEditStage.IDLE, needsFirstSet.coordinator.uiState.value.stage)
        assertEquals(0, needsFirstSet.v2.updateCalls)
    }

    @Test
    fun backAndDiscardNeverWriteTheOriginalPair() {
        val fixture = fixture()
        val harness = harness(fixture)
        harness.openEditor(fixture.original.shift.id)
        harness.coordinator.updatePosition("Descartar")
        harness.coordinator.back()
        assertEquals(V2ShiftEditStage.CONFIRM_DISCARD, harness.coordinator.uiState.value.stage)
        harness.coordinator.cancelDiscard()
        assertEquals(V2ShiftEditStage.EDIT_FORM, harness.coordinator.uiState.value.stage)
        harness.coordinator.back()
        harness.coordinator.confirmDiscard()

        assertEquals(V2ShiftEditStage.DAY_ACTIONS, harness.coordinator.uiState.value.stage)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
        assertEquals(0, harness.v2.updateCalls)
    }

    @Test
    fun futureLinkedShiftRequiresExplicitEditScopeAndCanContinueOnlyThisOccurrence() {
        val fixture = fixture()
        val occurrence = fixture.occurrence()
        val harness = harness(fixture, recurringOccurrence = occurrence)

        harness.inspectAndOpenActions()
        harness.coordinator.editShift(fixture.original.shift.id)

        assertEquals(V2ShiftEditStage.CHOOSE_EDIT_SCOPE, harness.coordinator.uiState.value.stage)
        assertEquals(occurrence, harness.coordinator.uiState.value.recurringOccurrence)
        assertEquals(0, harness.v2.updateCalls)

        harness.coordinator.editOnlyThisOccurrence()
        assertEquals(V2ShiftEditStage.EDIT_FORM, harness.coordinator.uiState.value.stage)
        assertEquals(fixture.original.shift.localStartDate, harness.coordinator.uiState.value.date)
    }

    @Test
    fun futureLinkedShiftRequiresExplicitDeleteScopeAndCanContinueOnlyThisOccurrence() {
        val fixture = fixture()
        val occurrence = fixture.occurrence()
        val harness = harness(fixture, recurringOccurrence = occurrence)

        harness.inspectAndOpenActions()
        harness.coordinator.requestDelete(fixture.original.shift.id)

        assertEquals(V2ShiftEditStage.CHOOSE_DELETE_SCOPE, harness.coordinator.uiState.value.stage)
        assertEquals(occurrence, harness.coordinator.uiState.value.recurringOccurrence)
        assertEquals(0, harness.v2.deleteCalls)

        harness.coordinator.deleteOnlyThisOccurrence()
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, harness.coordinator.uiState.value.stage)
        assertEquals(0, harness.v2.deleteCalls)
    }

    @Test
    fun pastLinkedShiftAndManualFutureShiftKeepTheIndividualFlow() {
        val past = fixture(LocalDate.of(2026, 8, 22))
        val pastHarness = harness(past, recurringOccurrence = past.occurrence())
        pastHarness.inspectAndOpenActions()
        pastHarness.coordinator.editShift(past.original.shift.id)
        assertEquals(V2ShiftEditStage.EDIT_FORM, pastHarness.coordinator.uiState.value.stage)

        val manual = fixture()
        val manualHarness = harness(manual)
        manualHarness.inspectAndOpenActions()
        manualHarness.coordinator.requestDelete(manual.original.shift.id)
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, manualHarness.coordinator.uiState.value.stage)
    }

    @Test
    fun futureOccurrenceFromFinalizedPlanKeepsOnlyTheExactIndividualActions() {
        val editFixture = fixture()
        val editHarness = harness(
            editFixture,
            recurringOccurrence = editFixture.occurrence(),
            recurringPlanKind = RecurringPlanRevisionKind.FINALIZED,
        )
        editHarness.inspectAndOpenActions()
        editHarness.coordinator.editShift(editFixture.original.shift.id)
        assertEquals(V2ShiftEditStage.EDIT_FORM, editHarness.coordinator.uiState.value.stage)

        val deleteFixture = fixture()
        val deleteHarness = harness(
            deleteFixture,
            recurringOccurrence = deleteFixture.occurrence(),
            recurringPlanKind = RecurringPlanRevisionKind.FINALIZED,
        )
        deleteHarness.inspectAndOpenActions()
        deleteHarness.coordinator.requestDelete(deleteFixture.original.shift.id)
        assertEquals(V2ShiftEditStage.CONFIRM_DELETE, deleteHarness.coordinator.uiState.value.stage)
    }

    @Test
    fun futureScopeHandoffClosesIndividualEditorWithoutWriting() {
        val fixture = fixture()
        val harness = harness(fixture, recurringOccurrence = fixture.occurrence())
        harness.inspectAndOpenActions()
        harness.coordinator.editShift(fixture.original.shift.id)

        harness.coordinator.handoffToRecurring()

        assertEquals(V2ShiftEditStage.IDLE, harness.coordinator.uiState.value.stage)
        assertEquals(0, harness.v2.updateCalls)
        assertEquals(fixture.original, harness.v2.writes.getValue(fixture.original.shift.id))
    }

    @Test
    fun savedStateRoundTripsDraftAndEveryUnconfirmedBlockingStage() {
        listOf(
            V2ShiftEditStage.DAY_ACTIONS,
            V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
            V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
            V2ShiftEditStage.EDIT_FORM,
            V2ShiftEditStage.CONFIRM_WARNINGS,
            V2ShiftEditStage.REVIEW,
            V2ShiftEditStage.CONFIRM_DELETE,
            V2ShiftEditStage.CONFIRM_DISCARD,
        ).forEach { stage ->
            val expected = V2ShiftEditPersistedState(
                stage = stage,
                timelineId = uuid(1),
                date = DATE,
                targetShiftId = uuid(10),
                selectedTemplateId = uuid(7),
                usesHistoricalTemplate = false,
                position = "Borrador persistido",
                acknowledgedWarnings = listOf("Advertencia ficticia"),
                reviewFingerprint = "revision-ficticia",
                confirmedPairFingerprint = "par-ficticio",
            )
            val handle = SavedStateHandle()
            handle.writeV2ShiftEditState(expected)
            assertEquals(expected, handle.readV2ShiftEditState())
            handle.writeV2ShiftEditState(V2ShiftEditPersistedState())
            assertEquals(V2ShiftEditPersistedState(), handle.readV2ShiftEditState())
        }
    }

    private fun harness(
        fixture: Fixture,
        writes: List<V2ShiftWrite> = listOf(fixture.original),
        otherShifts: List<Shift> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        initial: V2ShiftEditPersistedState = V2ShiftEditPersistedState(),
        persist: (V2ShiftEditPersistedState) -> Unit = {},
        autoResume: Boolean = true,
        recurringOccurrence: RecurringOccurrence? = null,
        recurringPlanKind: RecurringPlanRevisionKind = RecurringPlanRevisionKind.ACTIVE,
        actualExpectation: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation? = null,
    ): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        val configurations = EditFakeConfigurations(fixture.history)
        val catalog = EditFakeCatalog(fixture.catalog)
        val shifts = EditFakeShifts(writes.map { it.shift } + otherShifts)
        val v2 = EditFakeV2Shifts(shifts, writes)
        val coordinator = V2ShiftEditCoordinator(
            configurationRepository = configurations,
            catalogRepository = catalog,
            objectiveRepository = EditFakeObjectives(listOf(fixture.objective)),
            shiftRepository = shifts,
            medicalLeaveRepository = EditFakeMedicalLeaves(medicalLeaves),
            v2ShiftRepository = v2,
            shiftActualRepository = actualExpectation?.let(::EditFakeShiftActualRepository),
            recurringPlanRepository = EditFakeRecurringPlans(
                recurringOccurrence,
                recurringOccurrence?.let { fixture.recurringPlan(it, recurringPlanKind) },
            ),
            clock = CLOCK,
            scope = scope,
            initialPersistedState = initial,
            persist = persist,
        )
        if (autoResume) coordinator.resume(fixture.ready)
        return Harness(coordinator, catalog, shifts, v2, fixture)
    }

    private fun Harness.inspectAndOpenActions() {
        coordinator.inspectDay(fixture.ready, fixture.original.shift.localStartDate)
        assertEquals(V2ShiftDayInspectionState.CONTENT, coordinator.uiState.value.inspectionState)
        coordinator.beginDayEditing()
        assertEquals(V2ShiftEditStage.DAY_ACTIONS, coordinator.uiState.value.stage)
    }

    private fun Harness.openEditor(id: UUID) {
        inspectAndOpenActions()
        coordinator.editShift(id)
        assertEquals(V2ShiftEditStage.EDIT_FORM, coordinator.uiState.value.stage)
    }

    private data class Harness(
        val coordinator: V2ShiftEditCoordinator,
        val catalog: EditFakeCatalog,
        val shifts: EditFakeShifts,
        val v2: EditFakeV2Shifts,
        val fixture: Fixture,
    )

    private data class Fixture(
        val timelineId: UUID,
        val revision: EffectiveRevision<WorkConfiguration>,
        val history: WorkConfigurationHistory,
        val objective: Objective,
        val place: WorkPlace,
        val type: WorkType,
        val template: WorkTemplate,
        val alternative: WorkTemplate,
        val catalog: WorkCatalog,
        val original: V2ShiftWrite,
    ) {
        val ready: WorkSetupState.V2Ready = WorkSetupState.V2Ready(timelineId, revision)

        fun write(id: UUID, date: LocalDate, selected: WorkTemplate): V2ShiftWrite = buildV2ShiftWrite(
            id = id,
            date = date,
            objective = objective,
            workPlace = place,
            workType = type,
            template = selected,
            configurationContext = ResolvedWorkConfigurationRevision.resolve(history, date),
            position = null,
            timestamp = OLD,
            zoneId = ZONE,
        )

        fun occurrence(): RecurringOccurrence = RecurringOccurrence(
            planId = uuid(100),
            localDate = original.shift.localStartDate,
            revisionId = uuid(101),
            shiftId = original.shift.id,
            state = RecurringOccurrenceState.AUTOMATIC,
            createdAt = OLD,
            updatedAt = OLD,
        )

        fun recurringPlan(
            occurrence: RecurringOccurrence,
            kind: RecurringPlanRevisionKind,
        ): RecurringPlanAggregate = RecurringPlanAggregate(
            plan = RecurringPlan(occurrence.planId, timelineId, WorkSector.NURSING, OLD),
            revisions = listOf(
                RecurringPlanRevision(
                    id = occurrence.revisionId,
                    planId = occurrence.planId,
                    revisionNumber = 1,
                    effectiveFrom = occurrence.localDate,
                    kind = kind,
                    endDateInclusive = occurrence.localDate.plusMonths(1),
                    pattern = RecurringPattern.Weekdays.of(setOf(occurrence.localDate.dayOfWeek)),
                    templateId = template.id,
                    workPlaceId = place.id,
                    objectiveId = objective.id,
                    workTypeId = type.id,
                    objectiveNameSnapshot = objective.fullName,
                    objectiveAbbreviationSnapshot = objective.abbreviation,
                    objectiveAddressSnapshot = objective.address,
                    workTypeNameSnapshot = type.name,
                    workTypeBehaviorSnapshot = type.behavior,
                    startTimeSnapshot = template.startTime,
                    endTimeSnapshot = template.endTime,
                    colorArgbSnapshot = template.colorArgb,
                    positionSnapshot = original.shift.position,
                    zoneId = ZONE,
                    createdAt = OLD,
                ),
            ),
            occurrences = listOf(occurrence),
        )
    }

    private fun fixture(date: LocalDate = DATE): Fixture {
        val timelineId = uuid(1)
        val revision = EffectiveRevision(
            id = uuid(2),
            effectiveFrom = date.minusMonths(2),
            value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        val history = WorkConfigurationHistory(
            timeline = EffectiveDateTimeline(timelineId, listOf(revision)),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        )
        val objective = Objective(uuid(3), "Hospital ficticio", "HFI", null, null, true, OLD, OLD)
        val place = WorkPlace(uuid(4), timelineId, WorkSector.NURSING, objective.id, true, OLD, OLD)
        val type = WorkType.create(uuid(5), timelineId, WorkSector.NURSING, "Turno asistencial", OLD)
        val template = WorkTemplate(
            uuid(6), timelineId, WorkSector.NURSING, place.id, objective.id, type.id,
            LocalTime.of(8, 0), LocalTime.of(16, 0), 0xFF336699.toInt(), true, OLD, OLD,
        )
        val alternative = WorkTemplate(
            uuid(7), timelineId, WorkSector.NURSING, place.id, objective.id, type.id,
            LocalTime.of(18, 0), LocalTime.of(23, 0), 0xFF884422.toInt(), true, OLD, OLD,
        )
        val rule = WorkplaceRuleRevision(
            uuid(8), timelineId, WorkSector.NURSING, place.id, objective.id,
            date.minusMonths(2), WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), OLD,
        )
        val catalog = WorkCatalog(timelineId, WorkSector.NURSING, listOf(place), listOf(type), listOf(template, alternative), listOf(rule))
        val original = buildV2ShiftWrite(
            uuid(10), date, objective, place, type, template,
            ResolvedWorkConfigurationRevision.resolve(history, date), "Puesto A", OLD, ZONE,
        )
        return Fixture(timelineId, revision, history, objective, place, type, template, alternative, catalog, original)
    }

    private fun expectationWithActual(fixture: Fixture):
        com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation {
        val shift = fixture.original.shift
        val record = com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord(
            shiftId = shift.id,
            timelineId = fixture.timelineId,
            sector = WorkSector.NURSING,
            actualStart = shift.startAt,
            actualEnd = shift.endAt.plusSeconds(60 * 60),
            differenceReason = "Extensión real",
            explanation = null,
            createdAt = OLD,
            updatedAt = OLD,
        )
        return com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation(
            planned = fixture.original,
            previousActual = com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate(
                record,
                emptyList(),
            ),
            observedClass = null,
            recurringOccurrence = null,
            protectionFingerprint = "actual-protection",
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 12, 31)
        val OLD: Instant = Instant.parse("2026-08-20T12:00:00Z")
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")

        fun uuid(value: Long): UUID = UUID(0L, value)

        fun neighborShift(id: UUID, date: LocalDate, start: LocalTime, end: LocalTime): Shift {
            val endDate = if (end > start) date else date.plusDays(1)
            return Shift(
                id = id,
                startAt = date.atTime(start).atZone(ZONE).toInstant(),
                endAt = endDate.atTime(end).atZone(ZONE).toInstant(),
                zoneId = ZONE,
                localStartDate = date,
                objectiveNameSnapshot = "Historia ficticia",
                objectiveAbbreviationSnapshot = "HIS",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = start,
                endTimeSnapshot = end,
                colorArgbSnapshot = 0xFF222222.toInt(),
                position = null,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = uuid(3),
                createdAt = OLD,
                updatedAt = OLD,
            )
        }
    }
}

private class EditFakeConfigurations(
    var history: WorkConfigurationHistory,
) : WorkConfigurationRepository {
    override fun observe(): Flow<WorkConfigurationHistory?> = MutableStateFlow(history)
    override suspend fun get(): WorkConfigurationHistory = history
    override suspend fun createInitial(timelineId: UUID, firstRevision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
    override suspend fun addRevision(timelineId: UUID, revision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
}

private class EditFakeCatalog(
    var catalog: WorkCatalog,
) : WorkCatalogRepository {
    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> = MutableStateFlow(catalog)
    override fun observeRecentlyUsed(timelineId: UUID, sector: WorkSector, limit: Int): Flow<List<RecentWorkTemplate>> =
        MutableStateFlow(emptyList())
    override suspend fun getWorkPlace(id: UUID): WorkPlace? = catalog.workPlaces.firstOrNull { it.id == id }
    override suspend fun getWorkType(id: UUID): WorkType? = catalog.workTypes.firstOrNull { it.id == id }
    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? = catalog.workTemplates.firstOrNull { it.id == id }
    override suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision? =
        catalog.ruleRevisionAt(workPlaceId, date)
    override suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision> =
        catalog.workplaceRuleRevisions.filter { it.workPlaceId == workPlaceId }
    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet) = error("No se usa")
    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace) = error("No se usa")
    override suspend fun updateWorkPlace(update: WorkPlaceUpdate) = error("No se usa")
    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkType(workType: WorkType) = error("No se usa")
    override suspend fun updateWorkType(update: WorkTypeUpdate) = error("No se usa")
    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkTemplate(workTemplate: WorkTemplate) = error("No se usa")
    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate) = error("No se usa")
    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun addWorkplaceRuleRevision(revision: WorkplaceRuleRevision, confirmationNow: Instant) = error("No se usa")
    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory = error("No se usa")
}

private class EditFakeObjectives(initial: List<Objective>) : ObjectiveRepository {
    private val values = initial.toMutableList()
    override fun observeActive(): Flow<List<Objective>> = MutableStateFlow(values.filter(Objective::isActive))
    override fun observeAll(): Flow<List<Objective>> = MutableStateFlow(values.toList())
    override suspend fun getById(id: UUID): Objective? = values.firstOrNull { it.id == id }
}

private class EditFakeRecurringPlans(
    private val occurrence: RecurringOccurrence?,
    private val plan: RecurringPlanAggregate?,
) : RecurringPlanRepository {
    override fun observePlans(timelineId: UUID, sector: WorkSector): Flow<List<RecurringPlanAggregate>> =
        MutableStateFlow(emptyList())

    override suspend fun getPlan(planId: UUID): RecurringPlanAggregate? = plan?.takeIf { it.plan.id == planId }

    override suspend fun getOccurrenceForShift(shiftId: UUID): RecurringOccurrence? =
        occurrence?.takeIf { it.shiftId == shiftId }

    override suspend fun captureProtection(
        shiftIds: Set<UUID>,
        startDateInclusive: LocalDate?,
        endDateInclusive: LocalDate?,
    ): RecurringProtectionExpectation = RecurringProtectionExpectation.capture(emptyList())
}

private class EditFakeMedicalLeaves(
    private val values: List<MedicalLeave>,
) : MedicalLeaveRepository {
    override fun observeIntersecting(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<MedicalLeave>> =
        MutableStateFlow(values.filter { it.startDate <= endDateInclusive && it.endDateInclusive >= startDateInclusive })
    override suspend fun create(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun update(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun delete(id: UUID) = error("No se usa")
}

private class EditFakeShifts(initial: List<Shift>) : ShiftRepository {
    private val values = MutableStateFlow(initial)
    val current: List<Shift>
        get() = values.value

    override fun observeHasAny(): Flow<Boolean> = values.map(List<Shift>::isNotEmpty)
    override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
        values.map { shifts -> shifts.filter { it.localStartDate in startDateInclusive..endDateInclusive } }
    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        values.map { shifts -> shifts.filter { it.endAt > instantExclusive } }
    override suspend fun getById(id: UUID): Shift? = current.firstOrNull { it.id == id }

    fun replace(shift: Shift) {
        values.value = current.map { if (it.id == shift.id) shift else it }
    }

    fun remove(id: UUID) {
        values.value = current.filterNot { it.id == id }
    }
}

private class EditFakeShiftActualRepository(
    private val expectation: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation,
) : com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository {
    override fun observeAllActuals(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<Map<UUID, com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate>> =
        MutableStateFlow(emptyMap())

    override fun observeExpectation(
        shiftId: UUID,
    ): Flow<com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation?> =
        MutableStateFlow(expectation.takeIf { it.planned.shift.id == shiftId })

    override suspend fun getExpectation(
        shiftId: UUID,
    ): com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation? =
        expectation.takeIf { it.planned.shift.id == shiftId }

    override fun observeExtraWorkClasses(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<List<com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass>> = MutableStateFlow(emptyList())

    override suspend fun save(
        mutation: com.blackatsystems.miguardia.core.domain.model.ShiftActualSaveMutation,
    ): com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult = error("No se usa")

    override suspend fun returnToPlanned(
        expectation: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation,
    ): com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult = error("No se usa")

    override suspend fun saveExtraWorkClass(
        expected: com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass?,
        replacement: com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass,
    ): com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult = error("No se usa")
}

private class EditFakeV2Shifts(
    private val shifts: EditFakeShifts,
    initial: List<V2ShiftWrite>,
) : V2ShiftRepository {
    val writes = initial.associateByTo(linkedMapOf()) { it.shift.id }
    var updateCalls = 0
    var deleteCalls = 0
    var successfulUpdates = 0
    var successfulDeletes = 0
    var getCalls = 0
    var failure: Throwable? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var lookupGate: CompletableDeferred<Unit>? = null
    var lastOccupancyPreview: ShiftOccupancyExpectation? = null
    var lastExpectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation? = null

    override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> =
        MutableStateFlow(
            writes.values.filter { write ->
                write.snapshot.timelineId == timelineId && write.snapshot.sector == sector
            },
        )
    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> =
        MutableStateFlow(writes[shiftId]?.snapshot)
    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? = writes[shiftId]?.snapshot
    override suspend fun getShift(shiftId: UUID): V2ShiftLookup {
        getCalls++
        lookupGate?.await()
        return writes[shiftId]?.let(V2ShiftLookup::V2)
            ?: V2ShiftLookup.Missing
    }
    override suspend fun insert(write: V2ShiftWrite) = error("No se usa")

    override suspend fun deleteShift(
        expected: V2ShiftWrite,
        expectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation?,
    ) {
        deleteCalls++
        lastExpectedActual = expectedActual
        writeGate?.await()
        failure?.let { throw it }
        if (writes[expected.shift.id] != expected) {
            throw ConflictingLocalWriteException("La jornada cambió mientras confirmabas la eliminación.")
        }
        writes.remove(expected.shift.id)
        shifts.remove(expected.shift.id)
        successfulDeletes++
    }

    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation,
    ) {
        updateCalls++
        lastOccupancyPreview = expectedOccupancy
        writeGate?.await()
        failure?.let { throw it }
        val currentOccupancy = ShiftOccupancyExpectation.capture(
            expectedOccupancy.startDateInclusive,
            expectedOccupancy.endDateInclusive,
            shifts.current.filter { it.localStartDate in expectedOccupancy.startDateInclusive..expectedOccupancy.endDateInclusive },
        )
        if (currentOccupancy != expectedOccupancy) {
            throw ConflictingLocalWriteException("Las jornadas cambiaron mientras revisabas.")
        }
        mutation.shiftsToUpdate.forEach { candidate ->
            if (writes[candidate.shift.id] != expectedUpdates.writesById[candidate.shift.id]) {
                throw ConflictingLocalWriteException("La jornada cambió mientras revisabas.")
            }
        }
        mutation.shiftsToUpdate.forEach { candidate ->
            writes[candidate.shift.id] = candidate
            shifts.replace(candidate.shift)
        }
        successfulUpdates++
    }

    fun replaceExternally(write: V2ShiftWrite) {
        writes[write.shift.id] = write
        shifts.replace(write.shift)
    }
}
