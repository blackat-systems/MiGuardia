package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.InvalidVacationRangeException
import com.blackatsystems.miguardia.core.domain.repository.OverlappingVacationException
import com.blackatsystems.miguardia.core.domain.repository.VacationMedicalLeaveConflictException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VacationPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalDataStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test fun crudFlowIntersectionAndReopenPreserveVacation() = runBlocking {
        val original = vacation(1, LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 2))
        store.vacations.insert(original)
        assertEquals(
            original,
            store.vacations.observeOverlapping(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
            ).first().single(),
        )

        val edited = original.copy(
            endDateInclusive = LocalDate.of(2026, 9, 3),
            updatedAt = NOW.plusSeconds(1),
        )
        store.vacations.update(original, edited)
        assertEquals(edited, store.vacations.getById(original.id))

        store.close()
        openStore()
        assertEquals(edited, store.vacations.getById(original.id))

        store.vacations.delete(edited)
        assertNull(store.vacations.getById(original.id))
    }

    @Test fun overlappingRangesAreRejectedButContiguousRangesAreAllowed() = runBlocking {
        val first = vacation(10, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        store.vacations.insert(first)
        store.vacations.insert(vacation(11, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 10)))

        assertSuspendThrows<OverlappingVacationException> {
            store.vacations.insert(vacation(12, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 7)))
        }
        assertEquals(2, store.vacations.observeOverlapping(first.startDate, LocalDate.of(2026, 8, 31)).first().size)
    }

    @Test fun editingExcludesItselfButRejectsAnotherPeriodAndPreservesOriginalOnFailure() = runBlocking {
        val first = vacation(20, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        val second = vacation(21, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 15))
        store.vacations.insert(first)
        store.vacations.insert(second)

        val selfEdit = first.copy(endDateInclusive = LocalDate.of(2026, 8, 6), updatedAt = NOW.plusSeconds(1))
        store.vacations.update(first, selfEdit)
        assertEquals(selfEdit, store.vacations.getById(first.id))

        assertSuspendThrows<OverlappingVacationException> {
            store.vacations.update(
                selfEdit,
                selfEdit.copy(endDateInclusive = second.startDate, updatedAt = NOW.plusSeconds(2)),
            )
        }
        assertEquals(selfEdit, store.vacations.getById(first.id))
    }

    @Test fun vacationAndMedicalLeaveConflictIsRejectedInBothWriteDirections() = runBlocking {
        val vacation = vacation(30, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 15))
        store.vacations.insert(vacation)
        assertSuspendThrows<VacationMedicalLeaveConflictException> {
            store.medicalLeaves.create(leave(31, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20)))
        }
        assertTrue(
            store.medicalLeaves.observeIntersecting(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
            ).first().isEmpty(),
        )

        store.vacations.delete(vacation)
        val leave = leave(32, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22))
        store.medicalLeaves.create(leave)
        assertSuspendThrows<VacationMedicalLeaveConflictException> {
            store.vacations.insert(vacation(33, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 20)))
        }
        assertNull(store.vacations.getById(id(33)))
    }

    @Test fun editingEitherPeriodIntoAConflictRollsBackTheExistingData() = runBlocking {
        val vacation = vacation(34, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
        val leave = leave(35, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22))
        store.vacations.insert(vacation)
        store.medicalLeaves.create(leave)

        assertSuspendThrows<VacationMedicalLeaveConflictException> {
            store.vacations.update(
                vacation,
                vacation.copy(endDateInclusive = leave.startDate, updatedAt = NOW.plusSeconds(1)),
            )
        }
        assertEquals(vacation, store.vacations.getById(vacation.id))

        assertSuspendThrows<VacationMedicalLeaveConflictException> {
            store.medicalLeaves.update(
                leave.copy(startDate = vacation.endDateInclusive, updatedAt = NOW.plusSeconds(1)),
            )
        }
        assertEquals(
            leave,
            store.medicalLeaves.observeIntersecting(leave.startDate, leave.endDateInclusive)
                .first()
                .single(),
        )
    }

    @Test fun invalidRangeAndChangedCreatedAtAreRejectedWithoutMutation() = runBlocking {
        assertSuspendThrows<InvalidVacationRangeException> {
            store.vacations.insert(vacation(40, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)))
        }
        val missing = vacation(42, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5))
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.vacations.update(
                missing,
                missing.copy(updatedAt = NOW.plusMillis(1)),
            )
        }
        val valid = vacation(41, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3))
        store.vacations.insert(valid)
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.vacations.update(
                valid,
                valid.copy(createdAt = NOW.plusSeconds(1), updatedAt = NOW.plusSeconds(2)),
            )
        }
        assertEquals(valid, store.vacations.getById(valid.id))
    }

    @Test fun staleUpdateCannotOverwriteANewerVacationSnapshot() = runBlocking {
        val original = vacation(50, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))
        store.vacations.insert(original)
        val winner = original.copy(
            endDateInclusive = LocalDate.of(2026, 9, 4),
            updatedAt = NOW.plusMillis(1),
        )
        store.vacations.update(original, winner)

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.vacations.update(
                original,
                original.copy(
                    startDate = LocalDate.of(2026, 9, 2),
                    updatedAt = NOW.plusMillis(2),
                ),
            )
        }

        assertEquals(winner, store.vacations.getById(original.id))
    }

    @Test fun staleDeleteCannotRemoveANewerVacationSnapshot() = runBlocking {
        val original = vacation(51, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12))
        store.vacations.insert(original)
        val winner = original.copy(
            endDateInclusive = LocalDate.of(2026, 9, 13),
            updatedAt = NOW.plusMillis(1),
        )
        store.vacations.update(original, winner)

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.vacations.delete(original)
        }

        assertEquals(winner, store.vacations.getById(original.id))
        store.vacations.delete(winner)
        assertNull(store.vacations.getById(original.id))
    }

    @Test fun updateRequiresANewMillisecondVersionWithoutPartialMutation() = runBlocking {
        val original = vacation(52, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22))
        store.vacations.insert(original)

        assertSuspendThrows<InvalidLocalDataException> {
            store.vacations.update(
                original,
                original.copy(endDateInclusive = LocalDate.of(2026, 9, 23)),
            )
        }

        assertEquals(original, store.vacations.getById(original.id))
    }

    private fun openStore() {
        val database = Room.databaseBuilder(context, MiGuardiaV2Database::class.java, DB).build()
        store = LocalDataStore(database)
    }

    private fun vacation(number: Int, start: LocalDate, end: LocalDate) = Vacation(
        id = id(number),
        startDate = start,
        endDateInclusive = end,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun leave(number: Int, start: LocalDate, end: LocalDate) = MedicalLeave(
        id = id(number),
        startDate = start,
        endDateInclusive = end,
        privateNote = "Nota ficticia",
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun id(number: Int): UUID = UUID.fromString(
        "50000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
    )

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val DB = "vacation-persistence-test.db"
        val NOW: Instant = Instant.parse("2026-08-14T12:00:00Z")
    }
}
