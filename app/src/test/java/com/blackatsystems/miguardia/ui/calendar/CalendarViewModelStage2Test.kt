package com.blackatsystems.miguardia.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftSituationRepository
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateResult
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchRepository
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelStage2Test {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun medicalSaveReportsSuccessOnlyAfterTheWriteAndAfterUnlocking() = runTest(dispatcher) {
        val repository = DeferredMedicalRepository()
        val viewModel = medicalViewModel(repository)
        val results = mutableListOf<Pair<Boolean, Boolean>>()
        val start = LocalDate.of(2026, 10, 23)
        viewModel.saveMedicalLeave(null, start, start.plusDays(2), "  Nota propia  ") {
            results += it to viewModel.uiState.value.stage2Busy
        }
        assertTrue(viewModel.uiState.value.stage2Busy)
        assertTrue(results.isEmpty())
        assertEquals(null, repository.persisted)
        val submitted = requireNotNull(repository.submitted)
        assertEquals(start, submitted.startDate)
        assertEquals(start.plusDays(2), submitted.endDateInclusive)
        assertEquals("Nota propia", submitted.privateNote)
        repository.release.complete(Unit)
        awaitIdle(viewModel)
        assertEquals(submitted, repository.persisted)
        assertEquals(listOf(true to false), results)
    }

    @Test
    fun rejectedMedicalSaveReturnsFailureWithoutReplacingTheExistingEntity() = runTest(dispatcher) {
        val previous = MedicalLeave(UUID.randomUUID(), LocalDate.of(2026, 10, 23),
            LocalDate.of(2026, 10, 25), "Anterior", Instant.EPOCH, Instant.EPOCH)
        val repository = DeferredMedicalRepository(previous, reject = true)
        val viewModel = medicalViewModel(repository)
        val results = mutableListOf<Pair<Boolean, Boolean>>()
        viewModel.saveMedicalLeave(previous, previous.startDate, previous.endDateInclusive, "Borrador") {
            results += it to viewModel.uiState.value.stage2Busy
        }
        assertTrue(results.isEmpty())
        assertEquals(previous, repository.expected)
        val submitted = requireNotNull(repository.submitted)
        assertEquals(previous.id, submitted.id)
        assertEquals(previous.createdAt, submitted.createdAt)
        assertEquals("Borrador", submitted.privateNote)
        assertTrue(submitted.updatedAt > previous.updatedAt)
        repository.release.complete(Unit)
        awaitIdle(viewModel)
        assertEquals(previous, repository.persisted)
        assertEquals(listOf(false to false), results)
        assertEquals("Conflicto médico controlado", viewModel.uiState.value.stage2Message)
    }

    @Test
    fun aMedicalSubmissionWhileBusyIsRejectedWithoutQueuingAnotherWrite() = runTest(dispatcher) {
        val repository = DeferredMedicalRepository()
        val viewModel = medicalViewModel(repository)
        val date = LocalDate.of(2026, 10, 23)
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        viewModel.saveMedicalLeave(null, date, date, "Primera", first::add)
        viewModel.saveMedicalLeave(null, date.plusDays(1), date.plusDays(1), "Segunda", second::add)
        assertEquals(listOf(false), second)
        assertEquals(1, repository.calls)
        assertEquals("Primera", repository.submitted?.privateNote)
        repository.release.complete(Unit)
        awaitIdle(viewModel)
        assertEquals(listOf(true), first)
        assertEquals(1, repository.calls)
    }

    @Test
    fun invertedMedicalRangeReportsFailureBeforeCallingTheRepository() = runTest(dispatcher) {
        val repository = DeferredMedicalRepository()
        val viewModel = medicalViewModel(repository)
        val date = LocalDate.of(2026, 10, 23)
        val results = mutableListOf<Boolean>()
        viewModel.saveMedicalLeave(null, date.plusDays(1), date, "Borrador", results::add)
        awaitIdle(viewModel)
        assertEquals(listOf(false), results)
        assertEquals(0, repository.calls)
        assertEquals(null, repository.persisted)
    }

    private fun medicalViewModel(repository: MedicalLeaveRepository) = CalendarViewModel(
        shiftRepository = EmptyShifts,
        explicitDayStatusRepository = EmptyStatuses,
        medicalLeaveRepository = repository,
        clock = CLOCK,
        savedStateHandle = SavedStateHandle(),
    )

    private class DeferredMedicalRepository(initial: MedicalLeave? = null, val reject: Boolean = false) :
        MedicalLeaveRepository by EmptyMedical {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var expected: MedicalLeave? = null
        var submitted: MedicalLeave? = null
        var persisted: MedicalLeave? = initial
        override suspend fun create(medicalLeave: MedicalLeave) = write(null, medicalLeave)
        override suspend fun updateExpected(expected: MedicalLeave, replacement: MedicalLeave) = write(expected, replacement)
        private suspend fun write(previous: MedicalLeave?, value: MedicalLeave) {
            calls++
            expected = previous
            submitted = value
            release.await()
            if (reject) throw ConflictingLocalWriteException("Conflicto médico controlado")
            persisted = value
        }
    }

    @Test
    fun stage3PreviewAndApplyFinishSelectionOnlyAfterTheAtomicWrite() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 9, 20)
        val repository = RecordingCalendarBatchRepository()
        val viewModel = CalendarViewModel(
            shiftRepository = EmptyShifts,
            explicitDayStatusRepository = EmptyStatuses,
            medicalLeaveRepository = EmptyMedical,
            clock = CLOCK,
            savedStateHandle = SavedStateHandle(),
            calendarBatchRepository = repository,
        )
        viewModel.enterEditMode(date)
        viewModel.previewCalendarBatch(CalendarBatchAction.DAY_OFF)
        repeat(100) {
            if (viewModel.uiState.value.batchPreview != null) return@repeat
            delay(10)
        }
        assertEquals(1, viewModel.uiState.value.batchPreview?.applicable?.size)
        assertEquals(CalendarInteractionMode.EDIT, viewModel.uiState.value.interactionMode)

        viewModel.applyCalendarBatch()
        repeat(100) {
            if (viewModel.uiState.value.batchMessage != null) return@repeat
            delay(10)
        }
        assertEquals(1, repository.applyCalls)
        assertEquals(CalendarInteractionMode.VIEW, viewModel.uiState.value.interactionMode)
        assertTrue(viewModel.uiState.value.batchMessage.orEmpty().contains("Franco aplicado"))
    }

    @Test
    fun stage3RestoresMonthPurposeSelectionFoldAndLastOpenedDate() = runTest(dispatcher) {
        val selected = LocalDate.of(2026, 9, 20)
        val lastOpened = LocalDate.of(2026, 9, 14)
        val handle = SavedStateHandle(
            mapOf(
                "calendar.visibleMonth" to "2026-09",
                "calendar.interactionMode" to "EDIT",
                "calendar.selectionPurpose" to "GENERAL",
                "calendar.editSelectedDates" to arrayListOf(selected.toString()),
                "calendar.collapsed" to true,
                "calendar.lastOpenedDate" to lastOpened.toString(),
            ),
        )
        val viewModel = CalendarViewModel(
            shiftRepository = EmptyShifts,
            explicitDayStatusRepository = EmptyStatuses,
            medicalLeaveRepository = EmptyMedical,
            clock = CLOCK,
            savedStateHandle = handle,
        )

        assertEquals(CalendarInteractionMode.EDIT, viewModel.uiState.value.interactionMode)
        assertEquals(CalendarSelectionPurpose.GENERAL, viewModel.uiState.value.selectionPurpose)
        assertEquals(setOf(selected), viewModel.uiState.value.editSelectedDates)
        assertEquals(true, viewModel.uiState.value.calendarCollapsed)
        assertEquals(lastOpened, viewModel.uiState.value.lastOpenedDate)

        viewModel.setCalendarCollapsed(false)
        viewModel.setSelectionPurpose(CalendarSelectionPurpose.MANUAL_LOAD)
        assertEquals(false, handle.get<Boolean>("calendar.collapsed"))
        assertEquals("MANUAL_LOAD", handle.get<String>("calendar.selectionPurpose"))
    }

    @Test
    fun staleDialogRefreshesAfterFailureAndTheSecondExplicitAttemptCanSucceed() = runTest(dispatcher) {
        val stale = expectation(position = "Acceso")
        val concurrent = expectation(position = "Portón")
        val repository = RecordingSituationRepository(concurrent)
        val viewModel = CalendarViewModel(
            shiftRepository = EmptyShifts,
            explicitDayStatusRepository = EmptyStatuses,
            medicalLeaveRepository = EmptyMedical,
            clock = CLOCK,
            savedStateHandle = SavedStateHandle(),
            shiftSituationRepository = repository,
        )

        viewModel.changeShiftSituation(stale, ShiftStatus.ABSENT, "Inasistencia", null, null, null, false)
        awaitIdle(viewModel)

        assertTrue(viewModel.uiState.value.stage2Message.orEmpty().contains("cambió"))
        val refreshed = requireNotNull(viewModel.uiState.value.stage2Expectations[SHIFT_ID])
        assertEquals("Portón", refreshed.planned.shift.position)
        assertEquals(0, repository.successfulWrites)

        viewModel.changeShiftSituation(refreshed, ShiftStatus.ABSENT, "Inasistencia", null, null, null, false)
        repeat(100) {
            if (repository.successfulWrites == 1 && !viewModel.uiState.value.stage2Busy) return@repeat
            delay(10)
        }

        assertEquals(1, repository.successfulWrites)
        assertEquals(ShiftStatus.ABSENT, viewModel.uiState.value.stage2Expectations[SHIFT_ID]?.planned?.shift?.status)
        assertSame(repository.current, viewModel.uiState.value.stage2Expectations[SHIFT_ID])
    }

    private suspend fun awaitIdle(viewModel: CalendarViewModel) {
        repeat(100) {
            if (!viewModel.uiState.value.stage2Busy && viewModel.uiState.value.stage2Message != null) return
            delay(10)
        }
        error("La operación Stage 2 no finalizó")
    }

    private class RecordingSituationRepository(initial: ShiftSituationExpectation) : ShiftSituationRepository {
        var current = initial
        var successfulWrites = 0
        override fun observeForShift(shiftId: UUID): Flow<ShiftSituation?> = flowOf(current.previous)
        override suspend fun inspect(shiftId: UUID): ShiftSituationExpectation = current
        override suspend fun apply(mutation: ShiftSituationMutation) {
            if (mutation.expected != current) throw ConflictingLocalWriteException(
                "La jornada cambió mientras confirmabas.",
            )
            successfulWrites++
            val replacement = mutation.replacement
            current = ShiftSituationExpectation(
                planned = current.planned.copy(
                    shift = current.planned.shift.copy(status = replacement?.status ?: ShiftStatus.PLANNED),
                ),
                previous = replacement,
                actual = current.actual,
                recurringOccurrence = current.recurringOccurrence,
            )
        }
    }

    private class RecordingCalendarBatchRepository : CalendarBatchRepository {
        var applyCalls = 0
        override suspend fun preview(
            action: CalendarBatchAction,
            dates: Set<LocalDate>,
            availability: CalendarBatchAvailability?,
        ) = CalendarBatchPreview(
            action = action,
            dates = dates,
            availability = availability,
            results = dates.sorted().map {
                CalendarBatchDateResult(it, CalendarBatchDateState.APPLICABLE, "Se aplicará.")
            },
            expectationToken = "test-token",
        )

        override suspend fun apply(expected: CalendarBatchPreview) {
            applyCalls++
        }
    }

    private object EmptyShifts : ShiftRepository {
        override fun observeHasAny(): Flow<Boolean> = flowOf(false)
        override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> = flowOf(emptyList())
        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flowOf(emptyList())
        override suspend fun getById(id: UUID): Shift? = null
    }

    private object EmptyStatuses : ExplicitDayStatusRepository {
        override fun observeBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> = flowOf(emptyList())
        override fun observeFrom(startDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> = flowOf(emptyList())
        override suspend fun set(date: LocalDate, type: ExplicitDayStatusType) = Unit
        override suspend fun setAll(dates: Set<LocalDate>, type: ExplicitDayStatusType) = Unit
        override suspend fun clear(date: LocalDate) = Unit
    }

    private object EmptyMedical : MedicalLeaveRepository {
        override fun observeIntersecting(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<MedicalLeave>> = flowOf(emptyList())
        override suspend fun create(medicalLeave: MedicalLeave) = Unit
        override suspend fun update(medicalLeave: MedicalLeave) = Unit
        override suspend fun delete(id: UUID) = Unit
    }

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000001")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-14T18:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
        fun expectation(position: String): ShiftSituationExpectation {
            val zone = ZoneId.of("America/Argentina/Buenos_Aires")
            val shift = Shift(
                id = SHIFT_ID,
                startAt = Instant.parse("2026-09-20T11:00:00Z"),
                endAt = Instant.parse("2026-09-20T19:00:00Z"),
                zoneId = zone,
                localStartDate = LocalDate.of(2026, 9, 20),
                objectiveNameSnapshot = "Objetivo ficticio",
                objectiveAbbreviationSnapshot = "FIC",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = LocalTime.of(8, 0),
                endTimeSnapshot = LocalTime.of(16, 0),
                colorArgbSnapshot = 0xff123456.toInt(),
                position = position,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = UUID.fromString("81000000-0000-0000-0000-000000000002"),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val snapshot = ShiftWorkSnapshot(
                shiftId = SHIFT_ID,
                timelineId = UUID.fromString("81000000-0000-0000-0000-000000000003"),
                sector = WorkSector.PRIVATE_SECURITY,
                configurationRevisionId = UUID.fromString("81000000-0000-0000-0000-000000000004"),
                workPlaceId = UUID.fromString("81000000-0000-0000-0000-000000000005"),
                objectiveId = requireNotNull(shift.sourceObjectiveId),
                templateId = UUID.fromString("81000000-0000-0000-0000-000000000006"),
                workTypeId = UUID.fromString("81000000-0000-0000-0000-000000000007"),
                workTypeNameSnapshot = "Jornada habitual",
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            )
            return ShiftSituationExpectation(V2ShiftWrite(shift, snapshot), null, null, null)
        }
    }
}
