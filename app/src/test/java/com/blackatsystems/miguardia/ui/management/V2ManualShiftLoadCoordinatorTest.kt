package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationOutcome
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftDeletionExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
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
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun dateReselectionAndRecreationPreserveTheEntireDraftEvenWithNoDates() {
        val source = fixture()
        val handle = SavedStateHandle()
        val first = harness(source, persist = handle::writeV2ManualShiftLoadState)
        first.startAndChoose(setOf(DATE, DATE.plusDays(1)))
        first.coordinator.updatePosition("Recepción ficticia")
        first.coordinator.updateNote("Nota ficticia para la selección final")
        val attachment = ShiftNoteAttachment(uuid(8201), uuid(8202), "note_${uuid(8201)}.jpg",
            "image/jpeg", 100, 10, 10, 0, NOW, NOW)
        handle.writeV2ManualShiftLoadState(handle.readV2ManualShiftLoadState().copy(noteAttachments = listOf(attachment)))
        val h = harness(source, initial = handle.readV2ManualShiftLoadState(), persist = handle::writeV2ManualShiftLoadState)
        val before = h.coordinator.uiState.value
        h.coordinator.backToDateSelection()
        h.coordinator.updateSelectedDates(emptySet())
        h.coordinator.confirmDates(emptySet())
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, h.coordinator.uiState.value.stage)
        assertTrue(handle.readV2ManualShiftLoadState().selectedDates.isEmpty())
        val restored = harness(source, initial = handle.readV2ManualShiftLoadState())
        val finalDates = setOf(DATE.plusDays(3), DATE.plusDays(1))
        restored.coordinator.updateSelectedDates(finalDates)
        restored.coordinator.confirmDates(finalDates)
        val after = restored.coordinator.uiState.value
        assertEquals(finalDates, after.selectedDates)
        assertEquals(before.selectedTemplateId, after.selectedTemplateId)
        assertEquals(before.selectedOption, after.selectedOption)
        assertEquals(before.position, after.position)
        assertEquals(before.noteBody, after.noteBody)
        assertEquals(before.noteAttachments, after.noteAttachments)
        assertEquals(before.operationId, after.operationId)
        assertEquals(before.operationTimestamp, after.operationTimestamp)
        assertEquals(before.selectedTemplateFingerprint, after.selectedTemplateFingerprint)
        restored.coordinator.cancel()
        assertFalse(restored.coordinator.uiState.value.isActive)
        listOf(first, h, restored).forEach {
            assertTrue(it.v2Shifts.mutations.isEmpty())
            assertTrue(it.daysOff.calls.isEmpty())
        }
    }

    @Test
    fun returningToDatesInvalidatesOldPlanAndRecalculatesConflictsForFinalDates() {
        val h = harness(fixture(), existing = listOf(existingShift(DATE.plusDays(2))))
        h.startAndChoose(setOf(DATE))
        h.coordinator.requestReview()
        assertEquals(setOf(DATE), h.coordinator.uiState.value.plannedDates)
        assertNotNull(h.coordinator.uiState.value.reviewFingerprint)
        h.coordinator.backToDateSelection()
        val returned = h.coordinator.uiState.value
        assertTrue(returned.plannedDates.isEmpty())
        assertTrue(returned.omittedDates.isEmpty())
        assertTrue(returned.conflictDetails.isEmpty())
        assertNull(returned.batchPreview)
        assertNull(returned.reviewFingerprint)
        h.coordinator.confirmDates(setOf(DATE.plusDays(2)))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, h.coordinator.uiState.value.stage)
        assertEquals(setOf(DATE.plusDays(2)), h.coordinator.uiState.value.occupiedDates)
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT)
        h.coordinator.backToDateSelection()
        assertNull(h.coordinator.uiState.value.occupiedPolicy)
        assertTrue(h.coordinator.uiState.value.conflictDetails.isEmpty())
        h.coordinator.confirmDates(setOf(DATE.plusDays(4)))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.occupiedDates.isEmpty())
        h.coordinator.save()
        assertEquals(setOf(DATE.plusDays(4)), h.v2Shifts.mutations.single().shiftsToInsert.map { it.shift.localStartDate }.toSet())
    }

    @Test
    fun changedTemplateDuringDateSelectionRequiresExplicitReselection() {
        val source = fixture()
        val h = harness(source)
        h.startAndChoose(setOf(DATE))
        h.coordinator.updateNote("Conservar nota")
        h.coordinator.updatePosition("Conservar puesto")
        h.coordinator.backToDateSelection()
        val changed = source.template.copy(endTime = LocalTime.of(18, 0), updatedAt = NOW.plusSeconds(1))
        h.catalog.catalog = source.catalog.copy(workTemplates = listOf(changed))
        h.coordinator.confirmDates(setOf(DATE.plusDays(1)))
        val after = h.coordinator.uiState.value
        assertNull(after.selectedTemplateId)
        assertTrue(after.errorMessage.orEmpty().contains("cambió"))
        assertEquals("Conservar nota", after.noteBody)
        assertEquals("Conservar puesto", after.position)
        h.coordinator.requestSave()
        assertTrue(h.v2Shifts.mutations.isEmpty())
        h.coordinator.chooseTemplate(changed.id)
        assertEquals(changed, h.coordinator.uiState.value.selectedOption?.template)
    }

    @Test
    fun changedOrRemovedInstitutionAcrossRecreationCannotSilentlyKeepItsSchedule() {
        val source = fixture()
        val handle = SavedStateHandle()
        val first = harness(source, persist = handle::writeV2ManualShiftLoadState)
        first.startAndChoose(setOf(DATE))
        first.coordinator.backToDateSelection()
        val persisted = handle.readV2ManualShiftLoadState()
        listOf(source.objective.copy(fullName = "Nombre actualizado", updatedAt = NOW.plusSeconds(1)),
            source.objective.copy(isActive = false)).forEach { objective ->
            val restored = harness(source.copy(objective = objective), initial = persisted)
            restored.coordinator.confirmDates(setOf(DATE.plusDays(1)))
            assertNull(restored.coordinator.uiState.value.selectedTemplateId)
            assertNotNull(restored.coordinator.uiState.value.errorMessage)
            assertTrue(restored.v2Shifts.mutations.isEmpty())
        }
    }

    @Test
    fun finalDatesAndPositionAreSavedWithoutChangingTheCatalog() {
        listOf("Puesto nuevo", "Puesto editado", "").forEach { position ->
            listOf(setOf(DATE.plusDays(2)), setOf(DATE.plusDays(2), DATE.plusDays(4))).forEach { finalDates ->
                val source = fixture()
                val h = harness(source)
                h.startAndChoose(setOf(DATE))
                h.coordinator.updatePosition("Puesto anterior")
                h.coordinator.updatePosition(position)
                h.coordinator.backToDateSelection()
                h.coordinator.confirmDates(finalDates)
                h.coordinator.requestSave()
                val writes = h.v2Shifts.mutations.single().shiftsToInsert
                assertEquals(finalDates, writes.map { it.shift.localStartDate }.toSet())
                assertEquals(finalDates.size, writes.size)
                assertTrue(writes.all { it.shift.position == position.takeIf(String::isNotBlank) })
                assertEquals(source.catalog, h.catalog.catalog)
            }
        }
    }

    @Test
    fun staleSaveAndReviewActionsCannotWriteWhileChoosingDates() {
        val h = harness(fixture())
        h.startAndChoose(setOf(DATE))
        h.coordinator.backToDateSelection()
        h.coordinator.requestSave()
        h.coordinator.requestReview()
        h.coordinator.save()
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, h.coordinator.uiState.value.stage)
        assertTrue(h.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun addingScheduleForAnExistingInstitutionCanCancelOrSelectNewWithoutLosingDraft() {
        val source = fixture()
        val h = harness(source)
        h.startAndChoose(setOf(DATE, DATE.plusDays(2)))
        h.coordinator.updatePosition("Puesto ficticio")
        h.coordinator.updateNote("Nota ficticia")
        val before = h.coordinator.uiState.value
        h.coordinator.beginCreation(WorkCreationKind.TEMPLATE, source.place.id)
        val cancelledRequest = requireNotNull(h.coordinator.uiState.value.creationRequest)
        assertEquals(source.place.id, cancelledRequest.preferredPlaceId)
        h.coordinator.receiveCreationResult(WorkCreationResult(cancelledRequest.id, source.timelineId, WorkCreationOutcome.CANCELLED))
        assertEquals(before.selectedOption, h.coordinator.uiState.value.selectedOption)
        h.coordinator.beginCreation(WorkCreationKind.TEMPLATE, source.place.id)
        val createdRequest = requireNotNull(h.coordinator.uiState.value.creationRequest)
        val created = source.template.copy(id = createdRequest.newTemplateId, startTime = LocalTime.of(9, 0))
        h.catalog.catalog = source.catalog.copy(workTemplates = listOf(source.template, created))
        h.coordinator.receiveCreationResult(WorkCreationResult(createdRequest.id, source.timelineId,
            WorkCreationOutcome.CREATED, source.place.id, created.id))
        val after = h.coordinator.uiState.value
        assertEquals(created, after.selectedOption?.template)
        assertEquals(before.selectedDates, after.selectedDates)
        assertEquals(before.position, after.position)
        assertEquals(before.noteBody, after.noteBody)
        assertEquals(before.operationId, after.operationId)
        h.coordinator.backToDateSelection()
        h.coordinator.confirmDates(setOf(DATE.plusDays(1)))
        assertEquals(created.id, h.coordinator.uiState.value.selectedTemplateId)
        h.coordinator.cancel()
        assertEquals(2, h.catalog.catalog.workTemplates.size)
        assertTrue(h.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun keepOccupiedOmitsFrancoAndWritesOnlyTheFreeDate() {
        val h = harness(fixture())
        val franco = ExplicitDayStatus(DATE, ExplicitDayStatusType.DAY_OFF)
        val freeDate = DATE.plusDays(1)
        h.daysOff.current = listOf(franco)
        h.startAndChoose(setOf(DATE, freeDate))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, h.coordinator.uiState.value.stage)
        assertEquals(setOf(DATE), h.coordinator.uiState.value.occupiedDates)
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED)
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        assertEquals(setOf(DATE), h.coordinator.uiState.value.omittedDates)
        assertEquals(setOf(freeDate), h.coordinator.uiState.value.plannedDates)
        h.coordinator.save()
        val mutation = h.v2Shifts.mutations.single()
        assertEquals(setOf(freeDate), mutation.shiftsToInsert.mapTo(linkedSetOf()) { it.shift.localStartDate })
        assertTrue(mutation.shiftIdsToDelete.isEmpty())
        assertTrue(mutation.explicitDayStatusDatesToClear.isEmpty())
        assertEquals(listOf(franco), h.daysOff.current)
        assertTrue(h.daysOff.calls.isEmpty())
        assertTrue(h.coordinator.uiState.value.infoMessage!!.contains("1 fechas ocupadas"))
    }

    @Test
    fun keepOccupiedWithOnlyFrancosNeverOffersOrAppliesAnEmptyBatch() {
        val h = harness(fixture())
        val dates = setOf(DATE, DATE.plusDays(1))
        h.daysOff.current = dates.map { ExplicitDayStatus(it, ExplicitDayStatusType.DAY_OFF) }
        h.startAndChoose(dates)
        h.coordinator.requestReview()
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED)
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.plannedDates.isEmpty())
        assertNotNull(h.coordinator.uiState.value.errorMessage)
        h.coordinator.save()
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertTrue(h.daysOff.calls.isEmpty())
        assertEquals(dates, h.daysOff.current.mapTo(linkedSetOf()) { it.date })
    }

    @Test
    fun lostSuccessfulBatchResponseRecoversWithoutASecondApply() {
        val h = harness(fixture())
        val selected = setOf(DATE, DATE.plusDays(2))
        h.startAndChoose(selected)
        h.v2Shifts.postCommitFailure = IllegalStateException("Respuesta perdida después del commit")
        h.coordinator.requestSave()
        assertEquals(1, h.v2Shifts.calls)
        assertEquals(2, h.v2Shifts.committed.size)
        assertEquals(selected, h.coordinator.uiState.value.attemptedDates)
        assertNotNull(h.coordinator.uiState.value.errorMessage)
        h.coordinator.requestSave()
        assertEquals(1, h.v2Shifts.calls)
        assertEquals(1, h.v2Shifts.mutations.size)
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertEquals(1, h.coordinator.uiState.value.successSequence)
        assertTrue(h.coordinator.uiState.value.infoMessage!!.contains("No se duplicó"))
    }

    @Test
    fun recreationRecognizesCommittedAttemptUsingPersistedIdentitiesAndTimestamp() {
        val handle = SavedStateHandle()
        val source = fixture()
        val first = harness(source, persist = handle::writeV2ManualShiftLoadState)
        first.startAndChoose(setOf(DATE, DATE.plusDays(2)))
        val original = first.coordinator.uiState.value
        first.v2Shifts.postCommitFailure = IllegalStateException("Respuesta perdida después del commit")
        first.coordinator.requestSave()
        val persisted = handle.readV2ManualShiftLoadState()
        assertEquals(original.operationId, persisted.operationId)
        assertEquals(original.operationTimestamp, persisted.operationTimestamp)
        assertEquals(setOf(DATE, DATE.plusDays(2)), persisted.attemptedDates)
        val restored = harness(source, initial = persisted, v2Override = first.v2Shifts)
        assertEquals(V2ManualShiftLoadStage.IDLE, restored.coordinator.uiState.value.stage)
        assertEquals(1, restored.coordinator.uiState.value.successSequence)
        assertEquals(1, restored.v2Shifts.calls)
        assertEquals(2, restored.v2Shifts.committed.size)
    }

    @Test
    fun recoveryNeverTreatsAPartialOrChangedStoredBatchAsComplete() {
        val h = harness(fixture())
        h.startAndChoose(setOf(DATE, DATE.plusDays(2)))
        h.v2Shifts.postCommitFailure = IllegalStateException("Respuesta perdida después del commit")
        h.coordinator.requestSave()
        val removed = h.v2Shifts.committed.keys.last()
        h.v2Shifts.committed.remove(removed)
        h.coordinator.requestReview()
        assertTrue(h.coordinator.uiState.value.isActive)
        assertEquals(0, h.coordinator.uiState.value.successSequence)
        assertEquals(1, h.v2Shifts.calls)
        val retained = h.v2Shifts.committed.values.single()
        h.v2Shifts.committed[retained.shift.id] = retained.copy(shift = retained.shift.copy(position = "Cambio ajeno"))
        h.coordinator.requestReview()
        assertTrue(h.coordinator.uiState.value.isActive)
        assertEquals(0, h.coordinator.uiState.value.successSequence)
        assertEquals(1, h.v2Shifts.calls)
    }

    @Test
    fun noteAndPrivateImageDraftSurviveRecreationAndCancelWithoutWriting() {
        val handle = SavedStateHandle()
        val source = fixture()
        val first = harness(source, persist = handle::writeV2ManualShiftLoadState)
        first.startAndChoose(setOf(DATE, DATE.plusDays(2)))
        first.coordinator.updateNote("Nota ficticia compartida")
        val before = handle.readV2ManualShiftLoadState()
        val attachment = ShiftNoteAttachment(uuid(8101), uuid(8102), "note_${uuid(8101)}.jpg",
            "image/jpeg", 100, 10, 10, 0, NOW, NOW)
        handle.writeV2ManualShiftLoadState(before.copy(noteAttachments = listOf(attachment)))
        val restored = harness(source, initial = handle.readV2ManualShiftLoadState())
        val state = restored.coordinator.uiState.value
        assertEquals("Nota ficticia compartida", state.noteBody)
        assertEquals(listOf(attachment), state.noteAttachments)
        assertEquals(before.operationId, state.operationId)
        assertEquals(before.operationTimestamp, state.operationTimestamp)
        assertEquals(setOf(DATE, DATE.plusDays(2)), state.selectedDates)
        restored.coordinator.cancel()
        assertEquals(V2ManualShiftLoadStage.IDLE, restored.coordinator.uiState.value.stage)
        assertTrue(restored.coordinator.uiState.value.noteBody.isEmpty())
        assertTrue(restored.coordinator.uiState.value.noteAttachments.isEmpty())
        assertTrue(restored.v2Shifts.mutations.isEmpty())
        assertTrue(restored.daysOff.calls.isEmpty())
    }

    @Test
    fun excessImageSelectionPreservesManualDraftAndRejectsBeforeReadingUris() {
        val handle = SavedStateHandle()
        val source = fixture()
        val first = harness(source, persist = handle::writeV2ManualShiftLoadState)
        first.startAndChoose(setOf(DATE))
        first.coordinator.updateNote("Nota conservada")
        val images = (0 until 29).map { index ->
            val id = uuid(8300L + index)
            ShiftNoteAttachment(id, uuid(8299), "note_$id.jpg", "image/jpeg", 100, 10, 10, index, NOW, NOW)
        }
        handle.writeV2ManualShiftLoadState(handle.readV2ManualShiftLoadState().copy(noteAttachments = images))
        val restored = harness(source, initial = handle.readV2ManualShiftLoadState())
        val selection = object : AbstractList<android.net.Uri>() {
            override val size = 2
            override fun get(index: Int): android.net.Uri = error("Must reject before reading any URI")
        }
        restored.coordinator.importNoteImages(selection)
        assertEquals(images, restored.coordinator.uiState.value.noteAttachments)
        assertEquals("Nota conservada", restored.coordinator.uiState.value.noteBody)
        assertEquals(com.blackatsystems.miguardia.ui.photos.NoteImageSelection.rejectionMessage(29, 2),
            restored.coordinator.uiState.value.errorMessage)
        assertTrue(restored.v2Shifts.mutations.isEmpty())
        restored.coordinator.importNoteImages(emptyList())
        assertEquals(images, restored.coordinator.uiState.value.noteAttachments)
    }

    @Test
    fun overlayKeepsSelectedFrancoAndCapturesItsExactStatus() {
        val h = harness(fixture())
        val franco = ExplicitDayStatus(DATE, ExplicitDayStatusType.DAY_OFF)
        h.daysOff.current = listOf(franco)
        h.startAndChoose(setOf(DATE))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.conflictDetails.any { it.contains("Franco") })
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT)
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        h.coordinator.save()
        val mutation = h.v2Shifts.mutations.single()
        assertTrue(mutation.explicitDayStatusDatesToClear.isEmpty())
        assertEquals(mapOf(DATE to franco), mutation.explicitStatusExpectations)
        assertTrue(mutation.shiftIdsToDelete.isEmpty())
    }

    @Test
    fun francoBetweenUnselectedDatesDoesNotEnterTheConflictDecision() {
        val h = harness(fixture())
        h.daysOff.current = listOf(ExplicitDayStatus(DATE.plusDays(1), ExplicitDayStatusType.DAY_OFF))
        val selected = setOf(DATE, DATE.plusDays(2))
        h.startAndChoose(selected)
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.occupiedDates.isEmpty())
        assertTrue(h.coordinator.uiState.value.conflictDetails.isEmpty())
        h.coordinator.save()
        assertEquals(selected, h.v2Shifts.mutations.single().explicitStatusExpectations.keys)
        assertTrue(h.v2Shifts.mutations.single().explicitDayStatusDatesToClear.isEmpty())
    }

    @Test
    fun overwritePreviousNightPreservesNonOverlappingSameDayAndPreviewsLosses() {
        val night = existingShiftWithTimes(DATE.minusDays(1), LocalTime.of(19, 0), LocalTime.of(9, 0))
        val later = existingShiftWithTimes(DATE, LocalTime.of(18, 0), LocalTime.of(22, 0))
        val h = harness(fixture(), existing = listOf(night, later))
        h.startAndChoose(setOf(DATE))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.conflictDetails.any { it.contains("09/08/2026") })
        assertTrue(h.coordinator.uiState.value.conflictDetails.any {
            it.contains(night.objectiveNameSnapshot) && it.contains(night.objectiveAbbreviationSnapshot)
        })
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.warnings.any { it.contains("se quitará") })
        h.coordinator.confirmWarnings()
        h.coordinator.save()
        val mutation = h.v2Shifts.mutations.single()
        assertEquals(setOf(night.id), mutation.shiftIdsToDelete)
        assertEquals(setOf(night.id), mutation.deletionExpectations.keys)
        assertFalse(later.id in mutation.shiftIdsToDelete)
    }

    @Test
    fun changedAssociatedDataInvalidatesOverwritePreviewBeforeWriting() {
        val previous = existingShift(DATE)
        val h = harness(fixture(), existing = listOf(previous))
        h.startAndChoose(setOf(DATE))
        h.coordinator.requestReview()
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)
        h.coordinator.confirmWarnings()
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        h.v2Shifts.deletionVersions[previous.id] = "new associated note"
        h.coordinator.save()
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertNotNull(h.coordinator.uiState.value.errorMessage)
    }

    @Test
    fun increasedAssociatedNoteCountRequiresNewWarningsWithoutWriting() {
        val previous = existingShift(DATE)
        val writer = FakeV2Shifts().apply {
            deletionNoteCounts[previous.id] = 1
            deletionAttachmentStorageKeys[previous.id] = setOf("note_associated_fixture.jpg")
        }
        val h = harness(fixture(), existing = listOf(previous), v2Override = writer)
        val lossPrefix = "10/08/2026 08:00–16:00: se quitará esta jornada"
        val originalWarnings = listOf("$lossPrefix, 1 notas, 1 imágenes y 0 avisos.")
        val changedWarnings = listOf("$lossPrefix, 2 notas, 1 imágenes y 0 avisos.")
        h.startAndChoose(setOf(DATE))
        h.coordinator.requestReview()
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, h.coordinator.uiState.value.stage)
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE)
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, h.coordinator.uiState.value.stage)
        assertEquals(originalWarnings, h.coordinator.uiState.value.warnings)
        h.coordinator.confirmWarnings()
        val reviewed = h.coordinator.uiState.value
        val operation = requireNotNull(reviewed.operationId)
        assertEquals(V2ManualShiftLoadStage.REVIEW, reviewed.stage)
        assertEquals(originalWarnings, reviewed.acknowledgedWarnings)
        assertNotNull(reviewed.reviewFingerprint)
        assertEquals(0, writer.calls)
        val associatedBefore = runBlocking { writer.captureDeletionExpectations(setOf(previous.id)).getValue(previous.id) }
        // The existing regression independently covers a changed opaque token.
        writer.deletionNoteCounts[previous.id] = 2
        val associatedAfter = runBlocking { writer.captureDeletionExpectations(setOf(previous.id)).getValue(previous.id) }
        assertEquals(associatedBefore.copy(noteCount = 2), associatedAfter)

        h.coordinator.save()

        val blocked = h.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, blocked.stage)
        assertTrue(blocked.isActive)
        assertFalse(blocked.isSaving)
        assertFalse(blocked.isLoading)
        assertNull(blocked.errorMessage)
        assertEquals(operation, blocked.operationId)
        assertEquals(reviewed.operationTimestamp, blocked.operationTimestamp)
        assertEquals(reviewed.selectedDates, blocked.selectedDates)
        assertEquals(reviewed.selectedTemplateId, blocked.selectedTemplateId)
        assertEquals(reviewed.position, blocked.position)
        assertEquals(reviewed.noteBody, blocked.noteBody)
        assertEquals(reviewed.noteAttachments, blocked.noteAttachments)
        assertEquals(changedWarnings, blocked.warnings)
        assertEquals(originalWarnings, blocked.acknowledgedWarnings)
        assertNotEquals(blocked.warnings, blocked.acknowledgedWarnings)
        assertEquals(0, writer.calls)
        assertTrue(writer.mutations.isEmpty())
        assertEquals(listOf(previous), h.shifts.current)

        // Explicitly dismiss the new losses before starting another review.
        h.coordinator.dismissWarnings()
        val dismissed = h.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, dismissed.stage)
        assertEquals(operation, dismissed.operationId)
        assertTrue(dismissed.warnings.isEmpty())
        assertTrue(dismissed.acknowledgedWarnings.isEmpty())
        assertNull(dismissed.reviewFingerprint)
        assertFalse(dismissed.saveAfterPreparation)
        assertNull(dismissed.errorMessage)
        h.coordinator.chooseTemplate(requireNotNull(reviewed.selectedTemplateId))
        h.coordinator.requestReview()
        val freshReview = h.coordinator.uiState.value
        assertEquals(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY, freshReview.stage)
        assertEquals(operation, freshReview.operationId)
        assertEquals(reviewed.selectedDates, freshReview.selectedDates)
        assertTrue(freshReview.acknowledgedWarnings.isEmpty())
        assertNull(freshReview.reviewFingerprint)
        h.coordinator.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL)
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, h.coordinator.uiState.value.stage)
        assertEquals(operation, h.coordinator.uiState.value.operationId)
        assertEquals(0, writer.calls)
        assertTrue(writer.mutations.isEmpty())
        assertEquals(listOf(previous), h.shifts.current)
        assertEquals(associatedAfter, runBlocking { writer.captureDeletionExpectations(setOf(previous.id)).getValue(previous.id) })
    }

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun backCancellationFromInitialSelectionClosesTheLoadWithoutWriting() {
        val source = fixture()
        val h = harness(source)
        h.coordinator.start(source.readyState)

        h.coordinator.cancel()

        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertFalse(h.coordinator.uiState.value.isActive)
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertTrue(h.daysOff.calls.isEmpty())
    }

    @Test
    fun backFromLaterLoadStageReturnsToTheSameDateSelectionWithoutWriting() {
        val dates = setOf(DATE, DATE.plusDays(1))
        val h = harness(fixture())
        h.startAndChoose(dates)

        h.coordinator.backToDateSelection()

        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, h.coordinator.uiState.value.stage)
        assertEquals(dates, h.coordinator.uiState.value.selectedDates)
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertTrue(h.daysOff.calls.isEmpty())
    }

    @Test
    fun configuredTimelineWithoutFirstPlaceCanStartToMarkDaysOff() {
        val fixture = fixture()
        val harness = harness(fixture)

        harness.coordinator.start(
            WorkSetupState.V2NeedsFirstSet(
                fixture.timelineId,
                fixture.revisions.first(),
                emptySet(),
            ),
        )
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, harness.coordinator.uiState.value.stage)

        harness.coordinator.start(fixture.readyState)
        assertEquals(V2ManualShiftLoadStage.SELECT_DATES, harness.coordinator.uiState.value.stage)
        assertTrue(harness.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun dayOffNeedsConfirmationButNoCatalogTemplateOrBackfill() {
        val source = fixture()
        val gate = CompletableDeferred<Unit>()
        val h = harness(source, configurationGate = gate)
        h.coordinator.start(source.readyState)
        val dates = setOf(DATE.minusDays(3), DATE.minusDays(2))
        h.coordinator.requestDayOff(dates, true)
        assertTrue(h.daysOff.calls.isEmpty())
        assertFalse(h.coordinator.uiState.value.isLoading)
        h.coordinator.confirmDayOff()
        assertEquals(Triple(source.timelineId, dates, true), h.daysOff.calls.single())
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertEquals(1, h.coordinator.uiState.value.successSequence)
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertNull(h.catalog.extended)
    }

    @Test
    fun cancelledDayOffConfirmationNeverWritesAndKeepsDates() {
        val h = harness(fixture())
        h.coordinator.start(fixture().readyState)
        h.coordinator.requestDayOff(setOf(DATE), false)
        h.coordinator.cancelDayOff()
        h.coordinator.confirmDayOff()
        assertNull(h.coordinator.uiState.value.pendingDayOff)
        assertEquals(setOf(DATE), h.coordinator.uiState.value.selectedDates)
        assertTrue(h.daysOff.calls.isEmpty())
    }

    @Test
    fun dayOffRemovalAndDuplicateConfirmationWriteExactlyOnce() {
        val h = harness(fixture())
        h.daysOff.gate = CompletableDeferred()
        h.coordinator.start(fixture().readyState)
        h.coordinator.requestDayOff(setOf(DATE), false)
        h.coordinator.confirmDayOff()
        h.coordinator.confirmDayOff()
        h.coordinator.cancelDayOff()
        h.coordinator.cancel()
        assertTrue(h.coordinator.uiState.value.isSaving)
        assertEquals(1, h.daysOff.calls.size)
        h.daysOff.gate!!.complete(Unit)
        assertEquals(false, h.daysOff.calls.single().third)
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertTrue(h.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun dayOffConflictStaysExplicitAndRetriesOnlyAfterConfirmation() {
        val h = harness(fixture())
        h.daysOff.failure = ConflictingLocalWriteException("Hay jornadas en las fechas elegidas.")
        h.coordinator.start(fixture().readyState)
        h.coordinator.requestDayOff(setOf(DATE), true)
        h.coordinator.confirmDayOff()
        assertEquals(true, h.coordinator.uiState.value.pendingDayOff)
        assertTrue(h.coordinator.uiState.value.errorMessage!!.contains("jornadas"))
        h.coordinator.retry()
        assertEquals(1, h.daysOff.calls.size)
        h.daysOff.failure = null
        h.coordinator.confirmDayOff()
        assertEquals(2, h.daysOff.calls.size)
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
    }

    @Test
    fun dayOffConfirmationSurvivesRecreationWithoutAutomaticWrite() {
        val handle = SavedStateHandle()
        val h = harness(fixture(), persist = handle::writeV2ManualShiftLoadState)
        h.coordinator.start(fixture().readyState)
        h.coordinator.requestDayOff(setOf(DATE), false)
        val restored = harness(fixture(), initial = handle.readV2ManualShiftLoadState())
        assertEquals(false, restored.coordinator.uiState.value.pendingDayOff)
        assertEquals(setOf(DATE), restored.coordinator.uiState.value.selectedDates)
        assertTrue(restored.daysOff.calls.isEmpty())
        assertFalse(restored.coordinator.uiState.value.isLoading)
        restored.coordinator.confirmDayOff()
        assertEquals(false, restored.daysOff.calls.single().third)
    }

    @Test
    fun dayOffRejectsEmptyAndMixedMonthSelections() {
        val h = harness(fixture())
        h.coordinator.start(fixture().readyState)
        listOf(emptySet(), setOf(DATE, DATE.plusMonths(1))).forEach { dates ->
            h.coordinator.requestDayOff(dates, true)
            assertNull(h.coordinator.uiState.value.pendingDayOff)
            assertNotNull(h.coordinator.uiState.value.errorMessage)
        }
        assertTrue(h.daysOff.calls.isEmpty())
    }

    @Test
    fun dayOffIsAvailableAfterTemplateSelectionWithoutSavingAJornada() {
        val h = harness(fixture())
        h.startAndChoose(setOf(DATE.plusDays(1)))
        h.coordinator.requestDayOff(setOf(DATE.plusDays(1)), true)
        h.coordinator.confirmDates(setOf(DATE.plusDays(2)))
        assertEquals(setOf(DATE.plusDays(1)), h.coordinator.uiState.value.selectedDates)
        h.coordinator.confirmDayOff()
        assertEquals(1, h.daysOff.calls.size)
        assertTrue(h.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun cancellingDayOffFromChooserRestoresExactDraftAndAllowsJornadaSave() {
        val h = harness(fixture())
        h.startAndChoose(setOf(DATE.plusDays(1)))
        h.coordinator.updatePosition("Puesto conservado")
        val before = h.coordinator.uiState.value
        h.coordinator.requestDayOff(before.selectedDates, true)
        h.coordinator.cancelDayOff()
        assertEquals(before, h.coordinator.uiState.value)
        h.coordinator.requestSave()
        assertEquals(1, h.v2Shifts.mutations.size)
        assertTrue(h.daysOff.calls.isEmpty())
    }

    @Test
    fun cancellingRecreatedChooserConfirmationReloadsOriginalTemplateAndDraftWithoutWriting() {
        val handle = SavedStateHandle()
        val h = harness(fixture(), persist = handle::writeV2ManualShiftLoadState)
        h.startAndChoose(setOf(DATE.plusDays(1)))
        h.coordinator.updatePosition("Puesto conservado")
        val before = h.coordinator.uiState.value
        h.coordinator.requestDayOff(before.selectedDates, false)
        val restored = harness(fixture(), initial = handle.readV2ManualShiftLoadState())
        assertEquals(V2ManualShiftLoadStage.CHOOSE_TEMPLATE, restored.coordinator.uiState.value.stage)
        assertFalse(restored.coordinator.uiState.value.isLoading)
        assertTrue(restored.coordinator.uiState.value.templateOptions.isEmpty())
        restored.coordinator.cancelDayOff()
        val after = restored.coordinator.uiState.value
        assertEquals(before.stage, after.stage)
        assertEquals(before.selectedDates, after.selectedDates)
        assertEquals(before.selectedTemplateId, after.selectedTemplateId)
        assertEquals(before.position, after.position)
        assertEquals(before.templateOptions, after.templateOptions)
        assertNull(after.pendingDayOff)
        assertTrue(restored.daysOff.calls.isEmpty())
        assertTrue(restored.v2Shifts.mutations.isEmpty())
    }

    @Test
    fun staleLoadActionsCannotWriteOrChangeDraftWhileDayOffConfirmationIsOpen() {
        val h = harness(fixture())
        h.startAndChoose(setOf(DATE.plusDays(1)))
        h.coordinator.requestDayOff(h.coordinator.uiState.value.selectedDates, true)
        val pending = h.coordinator.uiState.value
        h.coordinator.requestReview()
        h.coordinator.requestSave()
        h.coordinator.save()
        h.coordinator.backToDateSelection()
        h.coordinator.updatePosition("Cambio tardío")
        h.coordinator.beginCreation(com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind.PLACE_AND_TEMPLATE)
        assertEquals(pending, h.coordinator.uiState.value)
        assertTrue(h.v2Shifts.mutations.isEmpty())
        assertTrue(h.daysOff.calls.isEmpty())
        h.coordinator.cancel()
        h.coordinator.confirmDayOff()
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertTrue(h.daysOff.calls.isEmpty())
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
    fun mixedSectorSelectionsAreRejectedWithoutWriting() {
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
        harness.daysOff.current = listOf(ExplicitDayStatus(freeDate, ExplicitDayStatusType.UNDEFINED))

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
    fun directSaveTraversesInternalReviewAndCommitsWithoutAnotherReviewAction() = runBlocking {
        val h = harness(fixture())
        val gate = CompletableDeferred<Unit>()
        h.v2Shifts.gate = gate
        h.startAndChoose(setOf(DATE.plusDays(1)))
        val operation = h.coordinator.uiState.value.operationId
        val sequence = h.coordinator.uiState.value.successSequence
        h.coordinator.requestSave()
        assertEquals(V2ManualShiftLoadStage.REVIEW, h.coordinator.uiState.value.stage)
        assertTrue(h.coordinator.uiState.value.isSaving)
        assertEquals(operation, h.coordinator.uiState.value.operationId)
        assertEquals(1, h.v2Shifts.calls)
        assertTrue(h.v2Shifts.mutations.isEmpty())
        gate.complete(Unit)
        assertEquals(V2ManualShiftLoadStage.IDLE, h.coordinator.uiState.value.stage)
        assertFalse(h.coordinator.uiState.value.isSaving)
        assertEquals(sequence + 1, h.coordinator.uiState.value.successSequence)
        assertEquals(1, h.v2Shifts.calls)
        assertEquals(setOf(DATE.plusDays(1)), h.v2Shifts.mutations.single().shiftsToInsert.map { it.shift.localStartDate }.toSet())
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
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, harness.coordinator.uiState.value.stage)
        assertTrue(harness.coordinator.uiState.value.warnings.any { it.contains("se quitará") })
        harness.coordinator.confirmWarnings()
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
        assertEquals(V2ManualShiftLoadStage.CONFIRM_WARNINGS, initialHarness.coordinator.uiState.value.stage)
        initialHarness.coordinator.confirmWarnings()
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
    fun overnightAndTwentyFourHourTemplatesKeepExactTemporalSnapshots() {
        val overnightBase = fixture(sector = WorkSector.POLICE)
        val overnightTemplate = overnightBase.template.copy(
            startTime = LocalTime.of(21, 0),
            endTime = LocalTime.of(6, 0),
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
        assertEquals(fullDay.objective.id, fullDayWrite.shift.sourceObjectiveId)
    }

    private fun harness(
        fixture: Fixture,
        existing: List<Shift> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        initial: V2ManualShiftLoadPersistedState = V2ManualShiftLoadPersistedState(),
        persist: (V2ManualShiftLoadPersistedState) -> Unit = {},
        configurationGate: CompletableDeferred<Unit>? = null,
        v2Override: FakeV2Shifts? = null,
    ): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        val configurations = FakeConfigurations(fixture.history, configurationGate)
        val catalog = FakeCatalog(configurations, fixture.catalog)
        val objectives = FakeObjectives(listOf(fixture.objective))
        val shifts = FakeShifts(existing)
        val v2Shifts = v2Override ?: FakeV2Shifts()
        val daysOff = FakeDaysOff()
        var nextUuid = 1_000L
        val coordinator = V2ManualShiftLoadCoordinator(
            configurationRepository = configurations,
            catalogRepository = catalog,
            objectiveRepository = objectives,
            shiftRepository = shifts,
            medicalLeaveRepository = FakeMedicalLeaves(medicalLeaves),
            v2ShiftRepository = v2Shifts,
            explicitDayStatusRepository = daysOff,
            clock = CLOCK,
            zoneId = ZONE,
            uuidProvider = UuidProvider { uuid(nextUuid++) },
            scope = scope,
            initialPersistedState = initial,
            persist = persist,
        )
        return Harness(coordinator, catalog, shifts, v2Shifts, daysOff)
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
        val daysOff: FakeDaysOff,
    )

    private class FakeDaysOff : ExplicitDayStatusRepository {
        var current: List<ExplicitDayStatus> = emptyList()
        val calls = mutableListOf<Triple<UUID, Set<LocalDate>, Boolean>>()
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun updateDaysOff(expectedTimelineId: UUID, dates: Set<LocalDate>, markAsDayOff: Boolean) {
            calls += Triple(expectedTimelineId, dates, markAsDayOff)
            gate?.await()
            failure?.let { throw it }
        }
        override fun observeBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> = flowOf(current.filter { it.date in startDateInclusive..endDateInclusive })
        override fun observeFrom(startDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> = flowOf(current.filter { it.date >= startDateInclusive })
        override suspend fun set(date: LocalDate, type: ExplicitDayStatusType) = error("Unexpected legacy write")
        override suspend fun setAll(dates: Set<LocalDate>, type: ExplicitDayStatusType) = error("Unexpected legacy write")
        override suspend fun clear(date: LocalDate) = error("Unexpected legacy write")
    }

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
    ): Fixture {
        val timelineId = TIMELINE_ID
        val history = WorkConfigurationHistory(
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
            sourceObjectiveId = uuid(2),
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
}

private class FakeShifts(initial: List<Shift>) : ShiftRepository {
    var current: List<Shift> = initial
    override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(current.isNotEmpty())
    override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
        MutableStateFlow(current.filter { it.localStartDate in startDateInclusive..endDateInclusive })
    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        MutableStateFlow(current.filter { it.endAt > instantExclusive })
    override suspend fun getById(id: UUID): Shift? = current.firstOrNull { it.id == id }
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
    val committed = linkedMapOf<UUID, V2ShiftWrite>()
    var postCommitFailure: Throwable? = null
    val deletionVersions = mutableMapOf<UUID, String>()
    val deletionNoteCounts = mutableMapOf<UUID, Int>()
    val deletionAttachmentStorageKeys = mutableMapOf<UUID, Set<String>>()
    override suspend fun captureDeletionExpectations(shiftIds: Set<UUID>): Map<UUID, ShiftDeletionExpectation> =
        shiftIds.associateWith { id -> ShiftDeletionExpectation(id, deletionVersions[id] ?: "initial:$id",
            deletionNoteCounts[id] ?: 0, deletionAttachmentStorageKeys[id].orEmpty().toSet(), false, false, 0, false) }
    val mutations = mutableListOf<V2ShiftBatchMutation>()
    val occupancyExpectations = mutableListOf<ShiftOccupancyExpectation>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null
    var calls: Int = 0
    override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> =
        MutableStateFlow(emptyList())
    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> = MutableStateFlow(null)
    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? = null
    override suspend fun getShift(shiftId: UUID): V2ShiftLookup =
        committed[shiftId]?.let { V2ShiftLookup.V2(it) } ?: V2ShiftLookup.Missing
    override suspend fun insert(write: V2ShiftWrite) = error("No se usa")
    override suspend fun deleteShift(
        expected: V2ShiftWrite,
        expectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation?,
    ) = error("No se usa")
    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation,
    ) {
        calls++
        gate?.await()
        failure?.let { throw it }
        if (captureDeletionExpectations(mutation.shiftIdsToDelete) != mutation.deletionExpectations) {
            throw ConflictingLocalWriteException("Los datos asociados cambiaron")
        }
        mutations += mutation
        occupancyExpectations += expectedOccupancy
        mutation.shiftIdsToDelete.forEach(committed::remove)
        (mutation.shiftsToInsert + mutation.shiftsToUpdate).forEach { committed[it.shift.id] = it }
        postCommitFailure?.let { throw it }
    }
}
