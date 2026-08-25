package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualClassSelection
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualFragmentDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Duration
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ShiftActualCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun ctaUsesInjectedClockExactBoundaryStatusAndStableUuidOrder() {
        val endedB = expectation(write(uuid(12), END_BOUNDARY))
        val endedA = expectation(write(uuid(11), END_BOUNDARY))
        val future = expectation(write(uuid(13), END_BOUNDARY.plusSeconds(60)))
        val cancelled = expectation(write(uuid(14), END_BOUNDARY, ShiftStatus.CANCELLED))
        val absent = expectation(write(uuid(15), END_BOUNDARY, ShiftStatus.ABSENT))
        val harness = harness(listOf(endedB, future, cancelled, endedA, absent))

        harness.coordinator.inspectDay(
            READY,
            DATE,
            listOf(endedB, future, cancelled, endedA, absent).map { it.planned.shift },
        )

        assertEquals(listOf(uuid(11), uuid(12), uuid(14), uuid(15), uuid(13)), harness.state.rows.keys.toList())
        assertTrue(harness.content(uuid(11)).canRegister)
        assertTrue(harness.content(uuid(12)).canRegister)
        assertFalse(harness.content(uuid(13)).canRegister)
        assertTrue(harness.content(uuid(13)).unavailableMessage.orEmpty().contains("final planificado"))
        assertFalse(harness.content(uuid(14)).canRegister)
        assertTrue(harness.content(uuid(14)).unavailableMessage.orEmpty().contains("cancelada"))
        assertFalse(harness.content(uuid(15)).canRegister)
        assertTrue(harness.content(uuid(15)).unavailableMessage.orEmpty().contains("ausente"))
    }

    @Test
    fun referenceInstantReactsAtBoundaryAndOrdinalUsesStartEndThenUuid() {
        val shortB = expectation(write(uuid(72), END_BOUNDARY))
        val long = expectation(
            write(
                id = uuid(71),
                endAt = END_BOUNDARY.plus(Duration.ofHours(1)),
                duration = Duration.ofHours(9),
            ),
        )
        val shortA = expectation(write(uuid(70), END_BOUNDARY))
        val harness = harness(listOf(long, shortB, shortA))

        harness.coordinator.inspectDay(
            READY,
            DATE,
            listOf(long, shortB, shortA).map { it.planned.shift },
            END_BOUNDARY.minusMillis(1),
        )

        assertEquals(listOf(uuid(70), uuid(72), uuid(71)), harness.state.rows.keys.toList())
        assertFalse(harness.content(uuid(70)).canRegister)

        harness.coordinator.inspectDay(
            READY,
            DATE,
            listOf(long, shortB, shortA).map { it.planned.shift },
            END_BOUNDARY,
        )

        assertTrue(harness.content(uuid(70)).canRegister)
        assertFalse(harness.content(uuid(71)).canRegister)
    }

    @Test
    fun neutralLoadingErrorRetryAndMissingPairNeverOfferFalseAction() {
        val expected = expectation(write(uuid(20), END_BOUNDARY))
        val harness = harness(listOf(expected))
        harness.repository.modes[expected.planned.shift.id] = FakeReadMode.LOADING

        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        assertEquals(V2ShiftActualRowState.Loading, harness.state.rows[expected.planned.shift.id])
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)

        harness.repository.modes[expected.planned.shift.id] = FakeReadMode.ERROR
        harness.coordinator.retryInspection()
        assertTrue(harness.state.rows[expected.planned.shift.id] is V2ShiftActualRowState.Error)
        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)

        harness.repository.modes[expected.planned.shift.id] = FakeReadMode.CONTENT
        harness.repository.expectations.getValue(expected.planned.shift.id).value = null
        harness.coordinator.retryInspection()
        assertTrue(harness.state.rows[expected.planned.shift.id] is V2ShiftActualRowState.Error)
    }

    @Test
    fun regularWorkflowReviewsSavesOnceAndReturnsToSameDetailContract() = runBlocking {
        val expected = expectation(write(uuid(30), END_BOUNDARY))
        val harness = harness(listOf(expected), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        assertEquals(V2ShiftActualStage.IDENTITY, harness.editor.stage)

        harness.coordinator.next()
        harness.coordinator.updateDraft {
            it.copy(endTime = "17:00", reason = "Extensión habitual", explanation = "Detalle ficticio")
        }
        harness.coordinator.next()
        assertEquals(V2ShiftActualStage.CLASSIFICATION, harness.editor.stage)
        harness.coordinator.updateDraft { it.copy(choice = ShiftActualDifferenceChoice.ALL_REGULAR) }
        harness.coordinator.next()
        assertEquals(V2ShiftActualStage.REVIEW, harness.editor.stage)
        assertEquals(540L, requireNotNull(harness.editor.preparedMutation).replacement.regularMinutes)

        val gate = CompletableDeferred<Unit>()
        val rereadGate = CompletableDeferred<Unit>()
        harness.repository.saveGate = gate
        harness.repository.getExpectationGate = rereadGate
        harness.coordinator.save()
        harness.coordinator.save()
        assertEquals(1, harness.repository.saveCalls)
        assertTrue(harness.state.isSaving)
        gate.complete(Unit)
        yield()

        assertEquals(V2ShiftActualSurface.EDITOR, harness.state.surface)
        assertTrue(harness.state.isSaving)
        rereadGate.complete(Unit)
        yield()

        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)
        assertEquals(1, harness.state.successSequence)
        assertTrue(harness.state.infoMessage.orEmpty().contains("guardado"))
        assertEquals("Extensión habitual", harness.repository.lastMutation?.replacement?.record?.differenceReason)
        harness.coordinator.consumeSuccess(1)
        assertEquals(0, harness.state.successSequence)
        assertTrue(harness.state.infoMessage.orEmpty().contains("guardado"))
    }

    @Test
    fun conflictRefreshKeepsDraftAndUsesLatestSource() {
        val expected = expectation(write(uuid(31), END_BOUNDARY))
        val harness = harness(listOf(expected), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        harness.coordinator.next()
        harness.coordinator.updateDraft {
            it.copy(endTime = "17:00", reason = "Borrador que debe sobrevivir")
        }

        val refreshed = expected.copy(
            planned = expected.planned.copy(
                shift = expected.planned.shift.copy(updatedAt = CREATED.plusMillis(1)),
            ),
            protectionFingerprint = "protección actualizada",
        )
        harness.repository.expectations.getValue(expected.planned.shift.id).value = refreshed
        assertTrue(harness.editor.sourceConflict)

        harness.coordinator.refreshEditorSource()

        assertFalse(harness.state.isRefreshingSource)
        assertFalse(harness.editor.sourceConflict)
        assertEquals(refreshed, harness.editor.expectation)
        assertEquals("Borrador que debe sobrevivir", harness.editor.draft.reason)
        assertTrue(harness.editor.errorMessage.orEmpty().contains("actualizó"))
    }

    @Test
    fun inlineClassStartsUnansweredUsesBlankFragmentAndKeepsDraftAcrossErrorAndRecreation() {
        val expected = expectation(write(uuid(40), END_BOUNDARY))
        var persisted: PersistedActualDraft? = null
        val harness = harness(listOf(expected), clock = EDIT_CLOCK, persist = { persisted = it })
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 2, DATE)
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(endTime = "17:00", reason = "Servicio adicional") }
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(choice = ShiftActualDifferenceChoice.EXTRA_CLASS) }
        harness.coordinator.startInlineClass()
        harness.coordinator.addFragment()

        val blankFragment = harness.editor.draft.fragments.single()
        assertEquals("", blankFragment.startDate)
        assertNull(harness.editor.draft.inlineHelpsReference)
        assertNull(harness.editor.draft.inlineDedicatedSummary)
        harness.coordinator.updateDraft {
            it.copy(
                inlineClassName = "Servicio extra",
                fragments = listOf(
                    blankFragment.copy(
                        startDate = DATE.toString(),
                        startTime = "16:00",
                        endDate = DATE.toString(),
                        endTime = "17:00",
                    ),
                ),
            )
        }
        harness.coordinator.next()
        assertTrue(harness.editor.errorMessage.orEmpty().contains("Respondé"))
        assertNotNull(persisted)

        var reviewed: PersistedActualDraft? = null
        val recreated = harness(
            listOf(expected),
            restored = persisted,
            clock = EDIT_CLOCK,
            persist = { reviewed = it },
        )
        recreated.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        assertEquals(V2ShiftActualSurface.EDITOR, recreated.state.surface)
        assertEquals("Servicio extra", recreated.editor.draft.inlineClassName)
        assertNull(recreated.editor.draft.inlineHelpsReference)
        recreated.coordinator.updateDraft {
            it.copy(inlineHelpsReference = false, inlineDedicatedSummary = true)
        }
        recreated.coordinator.next()
        assertEquals(V2ShiftActualStage.REVIEW, recreated.editor.stage)
        val mutation = requireNotNull(recreated.editor.preparedMutation)
        assertEquals("Servicio extra", mutation.classToCreate?.name)
        assertEquals(mutation.classToCreate, mutation.selectedClass)
        assertEquals(60L, mutation.replacement.extraMinutes)

        val reviewRecreated = harness(listOf(expected), restored = reviewed, clock = EDIT_CLOCK)
        reviewRecreated.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        assertEquals(V2ShiftActualStage.REVIEW, reviewRecreated.editor.stage)
        assertNotNull(reviewRecreated.editor.preparedMutation)
    }

    @Test
    fun inlineDuplicateIsRecoverableWithoutInventingSourceConflict() {
        val expected = expectation(write(uuid(41), END_BOUNDARY))
        val harness = harness(listOf(expected), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(endTime = "17:00", reason = "Extensión") }
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(choice = ShiftActualDifferenceChoice.EXTRA_CLASS) }
        harness.coordinator.startInlineClass()
        harness.coordinator.addFragment()
        val fragment = harness.editor.draft.fragments.single()
        harness.coordinator.updateDraft {
            it.copy(
                inlineClassName = "Servicio extra",
                inlineHelpsReference = false,
                inlineDedicatedSummary = true,
                fragments = listOf(
                    fragment.copy(
                        startDate = DATE.toString(),
                        startTime = "16:00",
                        endDate = DATE.toString(),
                        endTime = "17:00",
                    ),
                ),
            )
        }
        harness.coordinator.next()
        assertEquals(V2ShiftActualStage.REVIEW, harness.editor.stage)

        harness.repository.saveResult = ShiftActualWriteResult.DuplicateClassName
        harness.coordinator.save()

        assertEquals(V2ShiftActualSurface.EDITOR, harness.state.surface)
        assertFalse(harness.editor.sourceConflict)
        assertFalse(harness.state.isSaving)
        assertTrue(harness.editor.errorMessage.orEmpty().contains("equivalente"))
        assertEquals("Servicio extra", harness.editor.draft.inlineClassName)

        harness.repository.saveResult = null
        harness.coordinator.save()
        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)
        assertEquals(2, harness.repository.saveCalls)
    }

    @Test
    fun selectedClassVersionMustBeReselectedAfterConcurrentCatalogChange() {
        val expected = expectation(write(uuid(42), END_BOUNDARY))
        val originalClass = extraClass(uuid(43), "Horas extras")
        val harness = harness(listOf(expected), classes = listOf(originalClass), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(endTime = "17:00", reason = "Extensión") }
        harness.coordinator.next()
        harness.coordinator.updateDraft {
            it.copy(
                choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                selectedClassId = originalClass.id,
                selectedClassUpdatedAt = originalClass.updatedAt.toString(),
                fragments = listOf(
                    V2ActualFragmentInput(
                        id = uuid(44),
                        startDate = DATE.toString(),
                        startTime = "16:00",
                        endDate = DATE.toString(),
                        endTime = "17:00",
                    ),
                ),
            )
        }

        val changedClass = originalClass.updated(
            name = "Horas extraordinarias",
            timestamp = originalClass.updatedAt.plusMillis(1),
        )
        harness.repository.classes.value = listOf(changedClass)
        harness.coordinator.next()

        assertEquals(V2ShiftActualStage.CLASSIFICATION, harness.editor.stage)
        assertTrue(harness.editor.errorMessage.orEmpty().contains("cambió"))

        harness.coordinator.updateDraft {
            it.copy(selectedClassUpdatedAt = changedClass.updatedAt.toString())
        }
        harness.coordinator.next()
        assertEquals(V2ShiftActualStage.REVIEW, harness.editor.stage)
        assertEquals(changedClass, harness.editor.preparedMutation?.selectedClass)
    }

    @Test
    fun newRecordStartsWithoutOffsetsAndDirtyCloseRequiresConfirmation() {
        val expected = expectation(write(uuid(46), END_BOUNDARY))
        val harness = harness(listOf(expected), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)

        assertNull(harness.editor.draft.startOffset)
        assertNull(harness.editor.draft.endOffset)
        harness.coordinator.close()
        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)

        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)
        harness.coordinator.updateDraft { it.copy(reason = "Cambio sin guardar") }
        harness.coordinator.close()
        assertEquals(V2ShiftActualSurface.EDITOR, harness.state.surface)
        assertTrue(harness.editor.showDiscardConfirmation)

        harness.coordinator.dismissDiscardConfirmation()
        assertFalse(harness.editor.showDiscardConfirmation)
        harness.coordinator.close()
        harness.coordinator.confirmDiscard()
        assertEquals(V2ShiftActualSurface.NONE, harness.state.surface)
    }

    @Test
    fun classReadErrorIsVisibleAndRetryRecoversCatalogForEditor() {
        val expected = expectation(write(uuid(47), END_BOUNDARY))
        val existing = extraClass(uuid(48), "Servicio extra")
        val harness = harness(listOf(expected), classes = listOf(existing), clock = EDIT_CLOCK)
        harness.repository.classReadMode = FakeReadMode.ERROR
        harness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        harness.coordinator.begin(expected.planned.shift.id, 1, 1, DATE)

        assertFalse(harness.state.isLoadingClasses)
        assertTrue(harness.state.classesLoadError.orEmpty().contains("Reintentá"))

        harness.repository.classReadMode = FakeReadMode.CONTENT
        harness.coordinator.retryClasses()

        assertFalse(harness.state.isLoadingClasses)
        assertNull(harness.state.classesLoadError)
        assertEquals(listOf(existing), harness.state.classes)
    }

    @Test
    fun unavailableRestoredDraftStaysVisibleUntilRetryOrConsciousDiscard() {
        val expected = expectation(write(uuid(49), END_BOUNDARY))
        val restored = PersistedActualDraft(
            shiftId = expected.planned.shift.id,
            expectationFingerprint = expected.toString(),
            ordinal = 1,
            count = 1,
            ownerDate = DATE,
            stage = V2ShiftActualStage.ACTUAL_TIME,
            draft = V2ActualEditorDraft(
                startDate = DATE.toString(),
                startTime = "08:00",
                endDate = DATE.toString(),
                endTime = "17:00",
                reason = "Borrador recuperable",
            ),
        )
        val retryHarness = harness(listOf(expected), restored = restored, clock = EDIT_CLOCK)
        retryHarness.repository.modes[expected.planned.shift.id] = FakeReadMode.ERROR
        retryHarness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))

        assertEquals(V2ShiftActualSurface.NONE, retryHarness.state.surface)
        assertTrue(retryHarness.state.isBlocking)
        assertTrue(retryHarness.state.restoredDraftError.orEmpty().contains(expected.planned.shift.id.toString()))

        retryHarness.repository.modes[expected.planned.shift.id] = FakeReadMode.CONTENT
        retryHarness.coordinator.retryInspection()
        assertEquals(V2ShiftActualSurface.EDITOR, retryHarness.state.surface)
        assertNull(retryHarness.state.restoredDraftError)
        assertEquals("Borrador recuperable", retryHarness.editor.draft.reason)

        var discarded: PersistedActualDraft? = restored
        val discardHarness = harness(
            listOf(expected),
            restored = restored,
            clock = EDIT_CLOCK,
            persist = { discarded = it },
        )
        discardHarness.repository.expectations.getValue(expected.planned.shift.id).value = null
        discardHarness.coordinator.inspectDay(READY, DATE, listOf(expected.planned.shift))
        assertTrue(discardHarness.state.restoredDraftError != null)

        discardHarness.coordinator.discardUnavailableRestoredDraft()
        assertNull(discardHarness.state.restoredDraftError)
        assertFalse(discardHarness.state.isBlocking)
        assertNull(discarded)
    }

    @Test
    fun plannedEqualityNeverMountsAnEmptyReviewAndHistoricalSectorOwnsItsClasses() {
        val planned = write(uuid(45), END_BOUNDARY, sector = WorkSector.POLICE)
        val expected = expectation(planned)
        val harness = harness(listOf(expected), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(planned.shift))
        harness.coordinator.begin(planned.shift.id, 1, 1, DATE)
        assertEquals(TIMELINE_ID to WorkSector.POLICE, harness.repository.lastClassContext)
        harness.coordinator.next()
        harness.coordinator.next()

        assertEquals(V2ShiftActualStage.ACTUAL_TIME, harness.editor.stage)
        assertNull(harness.editor.preparedMutation)
        assertTrue(harness.editor.errorMessage.orEmpty().contains("No hay una corrección"))
        assertEquals(0, harness.repository.saveCalls)
    }

    @Test
    fun extraToShorterClearsClassificationAndReturnConflictKeepsEditableEvidence() {
        val planned = write(uuid(50), END_BOUNDARY)
        val extraClass = extraClass(uuid(51), "Horas extras")
        val base = expectation(planned)
        val savedMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = base,
                draft = ShiftActualDraft(
                    actualStart = planned.shift.startAt,
                    actualEnd = planned.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Extensión",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(extraClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(uuid(52), planned.shift.endAt, planned.shift.endAt.plus(Duration.ofHours(1))),
                    ),
                ),
                clock = EDIT_CLOCK,
                timestamp = NOW.minusSeconds(30),
            ),
        )
        val expected = base.copy(previousActual = savedMutation.replacement, observedClass = extraClass)
        val harness = harness(listOf(expected), classes = listOf(extraClass), clock = EDIT_CLOCK)
        harness.coordinator.inspectDay(READY, DATE, listOf(planned.shift))
        harness.coordinator.begin(planned.shift.id, 1, 1, DATE)
        assertNotNull(harness.editor.draft.startOffset)
        assertNotNull(harness.editor.draft.endOffset)
        harness.coordinator.next()
        harness.coordinator.updateDraft { it.copy(endTime = "15:00", reason = "Salida anticipada") }
        harness.coordinator.next()
        assertEquals(V2ShiftActualStage.REVIEW, harness.editor.stage)
        assertNull(harness.editor.draft.choice)
        assertNull(harness.editor.draft.selectedClassId)
        assertTrue(harness.editor.draft.fragments.isEmpty())

        harness.repository.returnResult = ShiftActualWriteResult.Conflict
        harness.coordinator.requestReturnToPlanned(planned.shift.id)
        harness.coordinator.confirmReturnToPlanned()
        assertEquals(V2ShiftActualSurface.EDITOR, harness.state.surface)
        assertTrue(harness.editor.sourceConflict)
        assertTrue(harness.editor.errorMessage.orEmpty().contains("borrador"))
        assertEquals(1, harness.repository.returnCalls)
    }

    @Test
    fun classCatalogRequiresTwoAnswersReportsNormalizedDuplicateAndSupportsArchiveReactivation() {
        val existing = extraClass(uuid(61), "Horas extras")
        val harness = harness(emptyList(), classes = listOf(existing))
        harness.coordinator.openCatalog(READY)
        harness.coordinator.startNewClass()
        harness.coordinator.updateClassEditor { it.copy(name = "  HORAS   EXTRAS ") }
        harness.coordinator.saveClass()
        assertTrue(harness.state.classEditor?.errorMessage.orEmpty().contains("Respondé"))
        harness.coordinator.updateClassEditor { it.copy(helpsReference = true, dedicatedSummary = false) }
        harness.repository.classResult = ExtraWorkClassWriteResult.DuplicateName
        harness.coordinator.saveClass()
        assertTrue(harness.state.classEditor?.errorMessage.orEmpty().contains("equivalente"))

        harness.repository.classResult = null
        harness.coordinator.cancelClassEditor()
        harness.coordinator.toggleClassActive(existing.id)
        assertFalse(harness.repository.classes.value.single().isActive)
        assertTrue(harness.state.infoMessage.orEmpty().contains("archivada"))
        harness.coordinator.toggleClassActive(existing.id)
        assertTrue(harness.repository.classes.value.single().isActive)
        assertTrue(harness.state.infoMessage.orEmpty().contains("reactivada"))
    }

    private fun harness(
        values: List<ShiftActualExpectation>,
        classes: List<ExtraWorkClass> = emptyList(),
        restored: PersistedActualDraft? = null,
        clock: Clock = CLOCK,
        persist: (PersistedActualDraft?) -> Unit = {},
    ): Harness {
        val repository = FakeActualRepository(values, classes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        return Harness(
            repository,
            V2ShiftActualCoordinator(
                repository = repository,
                clock = clock,
                uuidProvider = sequentialUuidProvider(),
                scope = scope,
                restoredDraft = restored,
                persistDraft = persist,
            ),
        )
    }

    private fun expectation(write: V2ShiftWrite): ShiftActualExpectation = ShiftActualExpectation(
        planned = write,
        previousActual = null,
        observedClass = null,
        recurringOccurrence = null,
        protectionFingerprint = "protection-${write.shift.id}",
    )

    private fun write(
        id: UUID,
        endAt: Instant,
        status: ShiftStatus = ShiftStatus.PLANNED,
        sector: WorkSector = WorkSector.NURSING,
        duration: Duration = Duration.ofHours(8),
    ): V2ShiftWrite {
        val startAt = endAt.minus(duration)
        return V2ShiftWrite(
            shift = Shift(
                id = id,
                startAt = startAt,
                endAt = endAt,
                zoneId = ZONE,
                localStartDate = DATE,
                objectiveNameSnapshot = "Hospital ficticio",
                objectiveAbbreviationSnapshot = "HFI",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = startAt.atZone(ZONE).toLocalTime(),
                endTimeSnapshot = endAt.atZone(ZONE).toLocalTime(),
                colorArgbSnapshot = 0xFF336699.toInt(),
                position = null,
                status = status,
                sourceObjectiveId = OBJECTIVE_ID,
                createdAt = CREATED,
                updatedAt = CREATED,
            ),
            snapshot = ShiftWorkSnapshot(
                shiftId = id,
                timelineId = TIMELINE_ID,
                sector = sector,
                configurationRevisionId = REVISION_ID,
                workPlaceId = PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                templateId = TEMPLATE_ID,
                workTypeId = TYPE_ID,
                workTypeNameSnapshot = "Turno asistencial",
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private fun extraClass(id: UUID, name: String): ExtraWorkClass = ExtraWorkClass.create(
        id = id,
        timelineId = TIMELINE_ID,
        sector = WorkSector.NURSING,
        name = name,
        helpsMeetHoursReference = false,
        showDedicatedSummary = true,
        timestamp = CREATED,
    )

    private fun sequentialUuidProvider(): () -> UUID {
        var next = 900L
        return { uuid(next++) }
    }

    private data class Harness(
        val repository: FakeActualRepository,
        val coordinator: V2ShiftActualCoordinator,
    ) {
        val state: V2ShiftActualUiState get() = coordinator.uiState.value
        val editor: V2ShiftActualEditorState get() = requireNotNull(state.editor)
        fun content(id: UUID): V2ShiftActualRowState.Content = state.rows.getValue(id) as V2ShiftActualRowState.Content
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val END_BOUNDARY: Instant = DATE.atTime(LocalTime.of(16, 0)).atZone(ZONE).toInstant()
        val NOW: Instant = END_BOUNDARY
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val EDIT_CLOCK: Clock = Clock.fixed(END_BOUNDARY.plus(Duration.ofHours(4)), ZoneOffset.UTC)
        val CREATED: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_ID: UUID = uuid(2)
        val PLACE_ID: UUID = uuid(3)
        val OBJECTIVE_ID: UUID = uuid(4)
        val TEMPLATE_ID: UUID = uuid(5)
        val TYPE_ID: UUID = uuid(6)
        val READY: WorkSetupState = WorkSetupState.V2Ready(
            TIMELINE_ID,
            EffectiveRevision(
                REVISION_ID,
                DATE.minusDays(1),
                WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
            ),
        )

        fun uuid(value: Long): UUID = UUID(0L, value)
    }
}

private enum class FakeReadMode { CONTENT, LOADING, ERROR }

private class FakeActualRepository(
    initial: List<ShiftActualExpectation>,
    classes: List<ExtraWorkClass>,
) : ShiftActualRepository {
    val expectations = initial.associate { value ->
        value.planned.shift.id to MutableStateFlow<ShiftActualExpectation?>(value)
    }.toMutableMap()
    val modes = initial.associate { it.planned.shift.id to FakeReadMode.CONTENT }.toMutableMap()
    val classes = MutableStateFlow(classes)
    var saveGate: CompletableDeferred<Unit>? = null
    var getExpectationGate: CompletableDeferred<Unit>? = null
    var saveResult: ShiftActualWriteResult? = null
    var returnResult: ShiftActualWriteResult = ShiftActualWriteResult.ReturnedToPlanned
    var classResult: ExtraWorkClassWriteResult? = null
    var classReadMode: FakeReadMode = FakeReadMode.CONTENT
    var saveCalls = 0
    var returnCalls = 0
    var lastMutation: ShiftActualSaveMutation? = null
    var lastClassContext: Pair<UUID, WorkSector>? = null

    override fun observeExpectation(shiftId: UUID): Flow<ShiftActualExpectation?> = when (modes[shiftId]) {
        FakeReadMode.LOADING -> emptyFlow()
        FakeReadMode.ERROR -> flow { throw IllegalStateException("Fallo ficticio") }
        else -> expectations.getOrPut(shiftId) { MutableStateFlow(null) }
    }

    override suspend fun getExpectation(shiftId: UUID): ShiftActualExpectation? {
        getExpectationGate?.await()
        return expectations[shiftId]?.value
    }

    override fun observeExtraWorkClasses(timelineId: UUID, sector: WorkSector): Flow<List<ExtraWorkClass>> {
        lastClassContext = timelineId to sector
        return when (classReadMode) {
            FakeReadMode.LOADING -> emptyFlow()
            FakeReadMode.ERROR -> flow { throw IllegalStateException("Fallo ficticio de clases") }
            FakeReadMode.CONTENT -> classes
        }
    }

    override fun observeAllActuals(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<Map<UUID, com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate>> =
        MutableStateFlow(emptyMap())

    override suspend fun save(mutation: ShiftActualSaveMutation): ShiftActualWriteResult {
        saveCalls++
        lastMutation = mutation
        saveGate?.await()
        val result = saveResult ?: ShiftActualWriteResult.Saved(mutation.replacement)
        if (result is ShiftActualWriteResult.Saved) {
            expectations.getOrPut(mutation.expectation.planned.shift.id) { MutableStateFlow(null) }.value =
                mutation.expectation.copy(
                    previousActual = result.aggregate,
                    observedClass = mutation.selectedClass ?: mutation.classToCreate,
                )
        }
        return result
    }

    override suspend fun returnToPlanned(expectation: ShiftActualExpectation): ShiftActualWriteResult {
        returnCalls++
        if (returnResult == ShiftActualWriteResult.ReturnedToPlanned) {
            expectations.getOrPut(expectation.planned.shift.id) { MutableStateFlow(null) }.value =
                expectation.copy(previousActual = null, observedClass = null)
        }
        return returnResult
    }

    override suspend fun saveExtraWorkClass(
        expected: ExtraWorkClass?,
        replacement: ExtraWorkClass,
    ): ExtraWorkClassWriteResult {
        classResult?.let { return it }
        classes.value = classes.value.filterNot { it.id == replacement.id } + replacement
        return ExtraWorkClassWriteResult.Saved(replacement)
    }
}
