package com.blackatsystems.miguardia

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.OverlappingVacationException
import com.blackatsystems.miguardia.core.domain.repository.VacationMedicalLeaveConflictException
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.ui.vacation.VacationSurface
import com.blackatsystems.miguardia.ui.vacation.VacationUuidProvider
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VacationViewModelInstrumentedTest {
    @Test fun savedStateRestoresSurfaceMonthAndUnsavedDraft() {
        val handle = SavedStateHandle()
        val repository = EmptyVacationRepository()
        val first = VacationViewModel(repository, CLOCK, UUIDS, handle)
        val month = YearMonth.of(2026, 9)
        val start = LocalDate.of(2026, 9, 29)
        val end = LocalDate.of(2026, 10, 2)

        first.openCreate(month, start)
        first.updateEndDate(end)

        val restored = VacationViewModel(repository, CLOCK, UUIDS, handle)
        assertEquals(VacationSurface.EDITOR, restored.uiState.value.surface)
        assertEquals(month, restored.uiState.value.visibleMonth)
        assertEquals(start, restored.uiState.value.draft.startDate)
        assertEquals(end, restored.uiState.value.draft.endDateInclusive)
        assertEquals(4L, restored.uiState.value.draft.inclusiveDayCount)
        assertTrue(restored.uiState.value.draft.isDirty)
    }

    @Test fun singleDayAndYearSpanningRangesAreSavedWithDeterministicIdentityAndInstants() {
        val repository = RecordingVacationRepository()
        val singleDay = LocalDate.of(2026, 8, 14)
        val first = VacationViewModel(repository, CLOCK, UUIDS, SavedStateHandle())

        first.openCreate(YearMonth.from(singleDay), singleDay)
        first.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(singleDay, repository.inserted.single().startDate)
        assertEquals(singleDay, repository.inserted.single().endDateInclusive)
        assertEquals(CLOCK.instant(), repository.inserted.single().createdAt)

        repository.inserted.clear()
        val second = VacationViewModel(repository, CLOCK, UUIDS, SavedStateHandle())
        val yearStart = LocalDate.of(2026, 12, 30)
        val yearEnd = LocalDate.of(2027, 1, 3)
        second.openCreate(YearMonth.from(yearStart), yearStart)
        second.updateEndDate(yearEnd)
        second.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(yearStart, repository.inserted.single().startDate)
        assertEquals(yearEnd, repository.inserted.single().endDateInclusive)
        assertEquals(5L, java.time.temporal.ChronoUnit.DAYS.between(yearStart, yearEnd) + 1)
    }

    @Test fun overlapAndMedicalConflictsRemainRecoverableInTheEditor() {
        val repository = RecordingVacationRepository()
        val viewModel = VacationViewModel(repository, CLOCK, UUIDS, SavedStateHandle())
        viewModel.openCreate(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 14))

        repository.failure = OverlappingVacationException()
        viewModel.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(
            "Ese período se superpone con otras vacaciones existentes.",
            viewModel.uiState.value.errorMessage,
        )
        assertEquals(VacationSurface.EDITOR, viewModel.uiState.value.surface)

        repository.failure = VacationMedicalLeaveConflictException()
        viewModel.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(
            "Las vacaciones no pueden superponerse con una carpeta médica.",
            viewModel.uiState.value.errorMessage,
        )
        assertEquals(VacationSurface.EDITOR, viewModel.uiState.value.surface)

        viewModel.requestBack()
        assertEquals(VacationSurface.LIST, viewModel.uiState.value.surface)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test fun restoredEditUsesTheOriginalObservedSnapshotAndAdvancesMilliseconds() {
        val repository = RecordingVacationRepository()
        val original = vacation(
            id = UUID.fromString("70000000-0000-0000-0000-000000000010"),
            start = LocalDate.of(2026, 9, 1),
            end = LocalDate.of(2026, 9, 3),
            updatedAt = CLOCK.instant(),
        )
        repository.seed(original)
        val handle = SavedStateHandle()
        val first = VacationViewModel(repository, CLOCK, UUIDS, handle)
        first.edit(original)
        first.updateEndDate(LocalDate.of(2026, 9, 4))

        val restored = VacationViewModel(repository, CLOCK, UUIDS, handle)
        assertEquals(original, restored.uiState.value.draft.observedVacation)
        restored.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val (expected, replacement) = repository.updated.single()
        assertEquals(original, expected)
        assertEquals(LocalDate.of(2026, 9, 4), replacement.endDateInclusive)
        assertEquals(CLOCK.instant().plusMillis(1), replacement.updatedAt)
    }

    @Test fun staleEditAndDeleteKeepTheirDraftOrConfirmationForAConsciousRetry() {
        val repository = RecordingVacationRepository()
        val original = vacation(
            id = UUID.fromString("70000000-0000-0000-0000-000000000011"),
            start = LocalDate.of(2026, 9, 5),
            end = LocalDate.of(2026, 9, 7),
            updatedAt = CLOCK.instant(),
        )
        repository.seed(original)
        val edit = VacationViewModel(repository, CLOCK, UUIDS, SavedStateHandle())
        edit.edit(original)
        edit.updateEndDate(original.endDateInclusive.plusDays(1))
        repository.failure = ConflictingLocalWriteException("cambio concurrente")
        edit.save()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(VacationSurface.EDITOR, edit.uiState.value.surface)
        assertEquals(original, edit.uiState.value.draft.observedVacation)
        assertTrue(edit.uiState.value.errorMessage.orEmpty().contains("cambió"))

        val deleteHandle = SavedStateHandle()
        val firstDelete = VacationViewModel(repository, CLOCK, UUIDS, deleteHandle)
        firstDelete.requestDelete(original)
        val restoredDelete = VacationViewModel(repository, CLOCK, UUIDS, deleteHandle)
        assertEquals(original, restoredDelete.uiState.value.pendingDelete)
        restoredDelete.confirmDelete()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(original, restoredDelete.uiState.value.pendingDelete)
        assertTrue(restoredDelete.uiState.value.errorMessage.orEmpty().contains("cambió"))
    }

    private class EmptyVacationRepository : VacationRepository {
        override fun observeOverlapping(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Vacation>> = flowOf(emptyList())

        override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> =
            flowOf(emptyList())

        override suspend fun getById(id: UUID): Vacation? = null
        override suspend fun insert(vacation: Vacation) = Unit
        override suspend fun update(expected: Vacation, replacement: Vacation) = Unit
        override suspend fun delete(expected: Vacation) = Unit
    }

    private class RecordingVacationRepository : VacationRepository {
        private val observed = MutableStateFlow<List<Vacation>>(emptyList())
        val inserted = mutableListOf<Vacation>()
        val updated = mutableListOf<Pair<Vacation, Vacation>>()
        var failure: Exception? = null

        fun seed(vacation: Vacation) {
            observed.value = listOf(vacation)
        }

        override fun observeOverlapping(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Vacation>> = observed

        override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> = observed

        override suspend fun getById(id: UUID): Vacation? = observed.value.firstOrNull { it.id == id }

        override suspend fun insert(vacation: Vacation) {
            failure?.let { throw it }
            inserted += vacation
            observed.value = observed.value + vacation
        }

        override suspend fun update(expected: Vacation, replacement: Vacation) {
            failure?.let { throw it }
            updated += expected to replacement
            observed.value = observed.value.map { if (it.id == replacement.id) replacement else it }
        }

        override suspend fun delete(expected: Vacation) {
            failure?.let { throw it }
            observed.value = observed.value.filterNot { it.id == expected.id }
        }
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-08-14T12:00:00Z"),
            AppDefaults.zoneId(),
        )
        val UUIDS = VacationUuidProvider {
            UUID.fromString("70000000-0000-0000-0000-000000000001")
        }

        fun vacation(
            id: UUID,
            start: LocalDate,
            end: LocalDate,
            updatedAt: Instant,
        ) = Vacation(
            id = id,
            startDate = start,
            endDateInclusive = end,
            createdAt = CLOCK.instant(),
            updatedAt = updatedAt,
        )
    }
}
