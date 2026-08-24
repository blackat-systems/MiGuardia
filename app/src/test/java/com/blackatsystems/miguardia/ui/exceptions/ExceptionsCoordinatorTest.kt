package com.blackatsystems.miguardia.ui.exceptions

import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExceptionsCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun multiDateConflictsRequireAPolicyAndThenUseOneAtomicBatch() {
        val existing = Holiday(uuid(1), DATE, "Existente", NOW, NOW)
        val holidays = FakeHolidayRepository(listOf(existing))
        val coordinator = coordinator(holidays = holidays)
        coordinator.updateHolidayDraft {
            it.copy(
                datesText = listOf(DATE, DATE.plusDays(1)).joinToString(","),
                name = "Feriado ficticio",
            )
        }

        coordinator.saveHolidays()

        assertEquals(setOf(DATE), coordinator.uiState.value.holidayDraft.conflictDates)
        assertTrue(holidays.mutations.isEmpty())

        coordinator.saveHolidays(HolidayConflictPolicy.REPLACE)

        val mutation = holidays.mutations.single()
        assertEquals(HolidayConflictPolicy.REPLACE, mutation.conflictPolicy)
        assertEquals(setOf(DATE, DATE.plusDays(1)), mutation.holidaysToSave.mapTo(linkedSetOf(), Holiday::date))
        assertEquals(HolidayDraft(), coordinator.uiState.value.holidayDraft)
    }

    @Test
    fun suspendedBatchRejectsDoubleSaveCloseAndSurfaceReplacement() = runBlocking {
        val holidays = FakeHolidayRepository()
        val gate = CompletableDeferred<Unit>()
        holidays.gate = gate
        val coordinator = coordinator(holidays = holidays)
        coordinator.updateHolidayDraft { it.copy(datesText = DATE.toString()) }

        coordinator.saveHolidays()
        coordinator.saveHolidays()
        coordinator.close()
        coordinator.openNotes(SHIFT)

        assertTrue(coordinator.uiState.value.isSaving)
        assertEquals(ExceptionsSurface.HOLIDAYS, coordinator.uiState.value.surface)
        assertEquals(1, holidays.applyCalls)

        gate.complete(Unit)

        assertFalse(coordinator.uiState.value.isSaving)
        assertEquals(1, holidays.mutations.size)
    }

    @Test
    fun savedNotesSurfaceRestoresTheExactShiftAndCloseClearsIt() {
        val persisted = mutableListOf<ExceptionsPersistedState>()
        val coordinator = coordinator(
            shifts = FakeShiftRepository(listOf(SHIFT)),
            initial = ExceptionsPersistedState(
                surface = ExceptionsSurface.NOTES,
                holidayMonth = MONTH,
                shiftId = SHIFT.id,
            ),
            persist = persisted::add,
        )

        assertEquals(ExceptionsSurface.NOTES, coordinator.uiState.value.surface)
        assertEquals(SHIFT, coordinator.uiState.value.selectedShift)
        assertEquals(SHIFT.id, persisted.last().shiftId)

        coordinator.close()

        assertEquals(ExceptionsSurface.NONE, coordinator.uiState.value.surface)
        assertNull(persisted.last().shiftId)
    }

    private fun coordinator(
        holidays: FakeHolidayRepository = FakeHolidayRepository(),
        notes: FakeNoteRepository = FakeNoteRepository(),
        shifts: FakeShiftRepository = FakeShiftRepository(listOf(SHIFT)),
        initial: ExceptionsPersistedState = ExceptionsPersistedState(
            surface = ExceptionsSurface.HOLIDAYS,
            holidayMonth = MONTH,
        ),
        persist: (ExceptionsPersistedState) -> Unit = {},
    ): ExceptionsCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        var nextUuid = 100L
        return ExceptionsCoordinator(
            holidays = holidays,
            notes = notes,
            shifts = shifts,
            clock = CLOCK,
            uuidProvider = { uuid(nextUuid++) },
            scope = scope,
            initialState = initial,
            persist = persist,
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 17)
        val MONTH: YearMonth = YearMonth.from(DATE)
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT = Shift(
            id = uuid(10),
            startAt = DATE.atTime(8, 0).atZone(ZONE).toInstant(),
            endAt = DATE.atTime(16, 0).atZone(ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = DATE,
            objectiveNameSnapshot = "Lugar ficticio",
            objectiveAbbreviationSnapshot = "LFI",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = uuid(11),
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun uuid(value: Long): UUID = UUID(0L, value)
    }
}

private class FakeHolidayRepository(initial: List<Holiday> = emptyList()) : HolidayRepository {
    private val rows = MutableStateFlow(initial)
    val mutations = mutableListOf<HolidayBatchMutation>()
    var gate: CompletableDeferred<Unit>? = null
    var applyCalls: Int = 0

    override fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Holiday>> = rows

    override suspend fun getById(id: UUID): Holiday? = rows.value.firstOrNull { it.id == id }
    override suspend fun getByDate(date: LocalDate): Holiday? = rows.value.firstOrNull { it.date == date }

    override suspend fun insert(holiday: Holiday) {
        rows.value = rows.value + holiday
    }

    override suspend fun update(holiday: Holiday) {
        rows.value = rows.value.map { if (it.id == holiday.id) holiday else it }
    }

    override suspend fun delete(id: UUID) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun applyBatch(mutation: HolidayBatchMutation) {
        applyCalls++
        gate?.await()
        mutations += mutation
    }
}

private class FakeNoteRepository : ShiftNoteRepository {
    private val rows = MutableStateFlow<List<ShiftNote>>(emptyList())

    override fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>> = rows
    override suspend fun getById(id: UUID): ShiftNote? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(note: ShiftNote) {
        rows.value = rows.value + note
    }

    override suspend fun update(note: ShiftNote) {
        rows.value = rows.value.map { if (it.id == note.id) note else it }
    }

    override suspend fun delete(id: UUID) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

private class FakeShiftRepository(private val rows: List<Shift>) : ShiftRepository {
    override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(rows.isNotEmpty())

    override fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>> = MutableStateFlow(
        rows.filter { it.localStartDate in startDateInclusive..endDateInclusive },
    )

    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        MutableStateFlow(rows.filter { it.endAt > instantExclusive })

    override suspend fun getById(id: UUID): Shift? = rows.firstOrNull { it.id == id }
}
