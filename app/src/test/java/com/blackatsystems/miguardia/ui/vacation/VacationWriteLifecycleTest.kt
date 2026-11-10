package com.blackatsystems.miguardia.ui.vacation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Actual ViewModel on a controlled repository. This is not Room or Android evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class VacationWriteLifecycleTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    @Test fun lateCompletionMustNotEraseAnotherUserDraftAcceptedWhileSaving() = runTest(dispatcher) {
        val repository = ControlledVacations()
        val release = CompletableDeferred<Unit>(); repository.barrier = release
        val model = model(repository)
        model.openCreate(MONTH, FIRST); model.updateEndDate(FIRST.plusDays(2))
        model.save()
        assertTrue(model.uiState.value.isSaving)
        var acceptedAnotherDraft = false
        var expectedOtherDraft: VacationDraft? = null
        try {
            // These are the real callbacks still reachable from the pending editor.
            model.requestBack()
            if (model.uiState.value.showDiscardConfirmation) model.confirmDiscard()
            model.openCreate(MONTH, FIRST.plusDays(7))
            model.updateEndDate(FIRST.plusDays(9))
            acceptedAnotherDraft = model.uiState.value.draft.startDate == FIRST.plusDays(7)
            if (acceptedAnotherDraft) expectedOtherDraft = model.uiState.value.draft
        } finally { release.complete(Unit) }
        assertFalse(model.uiState.value.isSaving)
        assertEquals(1, repository.commits)
        assertEquals(FIRST, repository.values.value.single().startDate)
        assertFalse("A later accepted draft was erased by the first write's completion",
            acceptedAnotherDraft && model.uiState.value.draft != expectedOtherDraft)
    }

    @Test fun pendingVacationWriteDoesNotQueueDuplicatesOrReplaceItsSource() = runTest(dispatcher) {
        val repository = ControlledVacations()
        val release = CompletableDeferred<Unit>(); repository.barrier = release
        val model = model(repository)
        model.openCreate(MONTH, FIRST); model.updateEndDate(FIRST.plusDays(2))
        val draft = model.uiState.value.draft
        model.save(); model.save()
        try {
            model.requestBack(); model.confirmDiscard(); model.openList(MONTH)
            model.openCreate(MONTH, FIRST.plusDays(7)); model.updateStartDate(FIRST.plusDays(7))
            model.updateEndDate(FIRST.plusDays(9)); model.close()
            assertEquals(draft, model.uiState.value.draft)
            assertEquals(VacationSurface.EDITOR, model.uiState.value.surface)
            assertFalse(model.uiState.value.showDiscardConfirmation)
            assertEquals(1, repository.attempts)
        } finally { release.complete(Unit) }
        assertEquals(1, repository.attempts)
        assertEquals(1, repository.commits)
        assertEquals(VacationSurface.LIST, model.uiState.value.surface)
    }

    @Test fun staleEditAndDeleteKeepTheirWinnerUntilAnExplicitCurrentVersionAttempt() = runTest(dispatcher) {
        val original = Vacation(UUID(11, 12), FIRST, FIRST.plusDays(2), STAMP, STAMP)
        val repository = ControlledVacations(listOf(original))
        val model = model(repository)
        model.edit(original); model.updateEndDate(FIRST.plusDays(3))
        val draft = model.uiState.value.draft
        val winner = original.copy(endDateInclusive = FIRST.plusDays(4), updatedAt = STAMP.plusMillis(1))
        repository.replace(winner)
        model.save()
        assertEquals(listOf(winner), repository.values.value)
        assertEquals(draft, model.uiState.value.draft)
        assertEquals(original, model.uiState.value.draft.observedVacation)
        assertTrue(model.uiState.value.errorMessage.orEmpty().contains("cambió"))
        assertEquals(1, repository.attempts)
        model.requestBack(); model.dismissDiscard(); assertEquals(draft, model.uiState.value.draft)
        model.requestBack(); model.confirmDiscard(); model.clearMessage()
        model.edit(winner); model.updateEndDate(FIRST.plusDays(5)); model.save()
        val saved = repository.values.value.single()
        assertEquals(winner.id, saved.id); assertEquals(winner.createdAt, saved.createdAt)
        assertEquals(FIRST.plusDays(5), saved.endDateInclusive)
        assertTrue(saved.updatedAt > winner.updatedAt)
        assertEquals(2, repository.attempts)

        model.requestDelete(saved)
        val deleteWinner = saved.copy(endDateInclusive = FIRST.plusDays(6), updatedAt = saved.updatedAt.plusMillis(1))
        repository.replace(deleteWinner); model.confirmDelete()
        assertEquals(listOf(deleteWinner), repository.values.value)
        assertEquals(saved, model.uiState.value.pendingDelete)
        assertTrue(model.uiState.value.errorMessage.orEmpty().contains("cambió"))
        assertEquals(3, repository.attempts)
        model.dismissDelete(); model.clearMessage(); model.requestDelete(deleteWinner); model.confirmDelete()
        assertTrue(repository.values.value.isEmpty()); assertNull(model.uiState.value.pendingDelete)
        assertEquals(4, repository.attempts); assertEquals(2, repository.commits)
    }

    @Test fun failedWriteKeepsTheDraftUnlockedWithoutAnAutomaticRetry() = runTest(dispatcher) {
        val repository = ControlledVacations(); repository.reject = true
        val model = model(repository)
        model.openCreate(MONTH, FIRST); model.updateEndDate(FIRST.plusDays(2))
        val draft = model.uiState.value.draft
        model.save()
        assertEquals(draft, model.uiState.value.draft)
        assertFalse(model.uiState.value.isSaving)
        assertTrue(repository.values.value.isEmpty())
        assertEquals(1, repository.attempts)
        repository.reject = false
        assertEquals(1, repository.attempts)
        model.save()
        assertEquals(2, repository.attempts); assertEquals(1, repository.commits)
        assertEquals(VacationSurface.LIST, model.uiState.value.surface)
    }

    private fun model(repository: VacationRepository): VacationViewModel {
        var next = 100L
        val model = VacationViewModel(repository, Clock.fixed(STAMP, ZoneId.of("America/Argentina/Buenos_Aires")),
            VacationUuidProvider { UUID(44, next++) }, SavedStateHandle())
        stores += ViewModelStore().also { it.put("vacation", model) }
        return model
    }
    private class ControlledVacations(initial: List<Vacation> = emptyList()) : VacationRepository {
        val values = MutableStateFlow(initial)
        var barrier: CompletableDeferred<Unit>? = null
        var attempts = 0
        var commits = 0
        var reject = false
        fun replace(value: Vacation) { values.value = values.value.map { if (it.id == value.id) value else it } }
        override fun observeOverlapping(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Vacation>> =
            values.map { rows -> rows.filter { it.startDate <= endDateInclusive && it.endDateInclusive >= startDateInclusive } }
        override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> =
            values.map { rows -> rows.filter { it.endDateInclusive >= dateInclusive } }
        override suspend fun getById(id: UUID): Vacation? = values.value.singleOrNull { it.id == id }
        private suspend fun enter() {
            attempts++; barrier?.await()
            if (reject) throw ConflictingLocalWriteException("Rechazo controlado")
        }
        override suspend fun insert(vacation: Vacation) {
            enter(); check(values.value.none { it.id == vacation.id })
            values.value = values.value + vacation; commits++
        }
        override suspend fun update(expected: Vacation, replacement: Vacation) {
            enter()
            if (getById(expected.id) != expected) throw ConflictingLocalWriteException("Versión obsoleta")
            replace(replacement); commits++
        }
        override suspend fun delete(expected: Vacation) {
            enter()
            if (getById(expected.id) != expected) throw ConflictingLocalWriteException("Versión obsoleta")
            values.value = values.value.filterNot { it.id == expected.id }; commits++
        }
    }
    private companion object {
        val FIRST: LocalDate = LocalDate.of(2026, 10, 23)
        val MONTH: YearMonth = YearMonth.of(2026, 10)
        val STAMP: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
