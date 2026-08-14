package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.toOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.model.withOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateHolidayDateException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
class NoveltyPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaDatabase
    private lateinit var store: LocalDataStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        database = Room.databaseBuilder(context, MiGuardiaDatabase::class.java, DB).build()
        store = LocalDataStore(database)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test fun holidayCrudBatchConflictAndFlow() = runBlocking {
        val date = LocalDate.of(2026, 8, 17)
        val first = Holiday(id(1), date, "Feriado inicial", NOW, NOW)
        store.holidays.insert(first)
        assertEquals(first, store.holidays.observeBetween(date, date).first().single())

        store.holidays.applyBatch(
            HolidayBatchMutation(
                holidaysToSave = listOf(Holiday(id(2), date, "Reemplazado", NOW, NOW.plusSeconds(1))),
                conflictPolicy = HolidayConflictPolicy.REPLACE,
            ),
        )
        val replaced = store.holidays.getByDate(date)!!
        assertEquals(first.id, replaced.id)
        assertEquals("Reemplazado", replaced.name)

        store.holidays.applyBatch(
            HolidayBatchMutation(
                holidaysToSave = listOf(
                    replaced.copy(name = "Nombre editado", updatedAt = NOW.plusSeconds(2)),
                ),
            ),
        )
        assertEquals("Nombre editado", store.holidays.getByDate(date)?.name)

        val editedDate = date.plusDays(1)
        store.holidays.applyBatch(
            HolidayBatchMutation(
                holidaysToSave = listOf(
                    replaced.copy(date = editedDate, name = "Editado", updatedAt = NOW.plusSeconds(3)),
                ),
            ),
        )
        assertNull(store.holidays.getByDate(date))
        assertEquals(replaced.id, store.holidays.getByDate(editedDate)?.id)
        assertEquals("Editado", store.holidays.getByDate(editedDate)?.name)

        store.holidays.delete(replaced.id)
        assertNull(store.holidays.getByDate(editedDate))
    }

    @Test fun notesAndNoveltiesCascadeWhenShiftIsDeleted() = runBlocking {
        val shift = shift(id(10))
        store.shifts.insert(shift)
        store.shiftNotes.insert(ShiftNote(id(11), shift.id, "Dato privado", NOW, NOW))
        store.shiftNovelties.applyMutation(
            ShiftNoveltyMutation.SaveInformative(
                ShiftNovelty(id(12), shift.id, ShiftNoveltyType.ADDITIONAL_TIME, "Dato manual", null, NOW, NOW),
            ),
        )
        assertEquals(1, store.shiftNotes.observeForShift(shift.id).first().size)
        assertEquals(1, store.shiftNovelties.observeForShift(shift.id).first().size)

        store.shifts.delete(shift.id)

        assertTrue(store.shiftNotes.observeForShift(shift.id).first().isEmpty())
        assertTrue(store.shiftNovelties.observeForShift(shift.id).first().isEmpty())
    }

    @Test fun holidayKeepExistingAndInvalidBatchDoNotPartiallyMutate() = runBlocking {
        val date = LocalDate.of(2026, 8, 20)
        val original = Holiday(id(70), date, "Original", NOW, NOW)
        store.holidays.insert(original)

        store.holidays.applyBatch(
            HolidayBatchMutation(
                holidaysToSave = listOf(Holiday(id(71), date, "No reemplazar", NOW, NOW.plusSeconds(1))),
                conflictPolicy = HolidayConflictPolicy.KEEP_EXISTING,
            ),
        )
        assertEquals(original, store.holidays.getByDate(date))

        assertSuspendThrows<InvalidLocalDataException> {
            store.holidays.applyBatch(
                HolidayBatchMutation(
                    holidayIdsToDelete = setOf(original.id),
                    holidaysToSave = listOf(
                        Holiday(id(72), date.plusDays(1), "Uno", NOW, NOW),
                        Holiday(id(73), date.plusDays(1), "Dos", NOW, NOW),
                    ),
                ),
            )
        }
        assertEquals(original, store.holidays.getByDate(date))

        assertSuspendThrows<DuplicateHolidayDateException> {
            store.holidays.insert(Holiday(id(74), date, "Duplicado", NOW, NOW))
        }
        assertEquals(original, store.holidays.getByDate(date))
    }

    @Test fun noteAndInformativeNoveltyCannotBeMovedToAnotherShiftByIdReuse() = runBlocking {
        val firstShift = shift(id(60))
        val secondShift = shift(id(61))
        store.shifts.insert(firstShift)
        store.shifts.insert(secondShift)

        val note = ShiftNote(id(62), firstShift.id, "Dato privado", NOW, NOW)
        store.shiftNotes.insert(note)
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftNotes.update(note.copy(shiftId = secondShift.id, updatedAt = NOW.plusSeconds(1)))
        }
        assertEquals(firstShift.id, store.shiftNotes.getById(note.id)?.shiftId)

        val novelty = ShiftNovelty(
            id(63), firstShift.id, ShiftNoveltyType.OTHER, "Dato manual", null, NOW, NOW,
        )
        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.SaveInformative(novelty))
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftNovelties.applyMutation(
                ShiftNoveltyMutation.SaveInformative(
                    novelty.copy(shiftId = secondShift.id, updatedAt = NOW.plusSeconds(1)),
                ),
            )
        }
        assertEquals(firstShift.id, store.shiftNovelties.getById(novelty.id)?.shiftId)
    }

    @Test fun statusChangeAndReturnToPlannedAreAtomic() = runBlocking {
        val shift = shift(id(20))
        store.shifts.insert(shift)
        val absent = ShiftNovelty(id(21), shift.id, ShiftNoveltyType.ABSENCE, null, null, NOW, NOW)
        store.shiftNovelties.applyMutation(
            ShiftNoveltyMutation.ChangeStatus(shift.copy(status = ShiftStatus.ABSENT), absent),
        )
        assertEquals(ShiftStatus.ABSENT, store.shifts.getById(shift.id)?.status)
        assertEquals(ShiftNoveltyType.ABSENCE, store.shiftNovelties.observeForShift(shift.id).first().single().type)

        store.shiftNovelties.applyMutation(
            ShiftNoveltyMutation.ChangeStatus(shift.copy(updatedAt = NOW.plusSeconds(1)), null),
        )
        assertEquals(ShiftStatus.PLANNED, store.shifts.getById(shift.id)?.status)
        assertTrue(store.shiftNovelties.observeForShift(shift.id).first().isEmpty())
    }

    @Test fun secondShiftAndLinkAreCreatedAndDeletedTogether() = runBlocking {
        val origin = shift(id(30))
        val second = shift(id(31)).copy(
            startAt = origin.startAt.plusSeconds(3600),
            endAt = origin.endAt.plusSeconds(3600),
        )
        store.shifts.insert(origin)
        val link = ShiftNovelty(id(32), origin.id, ShiftNoveltyType.SECOND_SHIFT, null, second.id, NOW, NOW)
        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.CreateSecondShift(link, second))
        assertEquals(second, store.shifts.getById(second.id))

        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.DeleteSecondShift(link.id, second.id))
        assertNull(store.shifts.getById(second.id))
        assertTrue(store.shiftNovelties.observeForShift(origin.id).first().isEmpty())
    }

    @Test fun formalChangeKeepsOriginalAndRestoresAtomically() = runBlocking {
        val original = shift(id(40))
        store.shifts.insert(original)
        val final = original.copy(
            endAt = original.endAt.plusSeconds(3600),
            endTimeSnapshot = LocalTime.of(8, 0),
            updatedAt = NOW.plusSeconds(1),
        )
        val change = FormalShiftChange(
            id(41), original.id, scheduleChanged = true, objectiveChanged = false, description = "Cambio ficticio",
            original = original.toOperationalSnapshot(), final = final.toOperationalSnapshot(),
            createdAt = NOW.plusSeconds(1), updatedAt = NOW.plusSeconds(1),
        )
        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.ApplyFormalChange(final, change))
        assertEquals(final, store.shifts.getById(original.id))
        assertEquals(original.toOperationalSnapshot(), store.shiftNovelties.observeFormalChange(original.id).first()?.original)

        val restored = final.withOperationalSnapshot(change.original, NOW.plusSeconds(2))
        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.RestoreOriginalPlan(restored, change.final))
        assertEquals(restored, store.shifts.getById(original.id))
        assertNull(store.shiftNovelties.observeFormalChange(original.id).first())
    }

    @Test fun restoreOriginalRejectsAConcurrentShiftChangeWithoutDeletingHistory() = runBlocking {
        val original = shift(id(80))
        store.shifts.insert(original)
        val final = original.copy(
            endAt = original.endAt.plusSeconds(3600),
            endTimeSnapshot = LocalTime.of(8, 0),
            updatedAt = NOW.plusSeconds(1),
        )
        val change = FormalShiftChange(
            id(81), original.id, scheduleChanged = true, objectiveChanged = false, description = null,
            original = original.toOperationalSnapshot(), final = final.toOperationalSnapshot(),
            createdAt = NOW.plusSeconds(1), updatedAt = NOW.plusSeconds(1),
        )
        store.shiftNovelties.applyMutation(ShiftNoveltyMutation.ApplyFormalChange(final, change))

        val concurrent = final.copy(position = "Cambio concurrente", updatedAt = NOW.plusSeconds(2))
        store.shifts.update(concurrent)
        val restored = concurrent.withOperationalSnapshot(change.original, NOW.plusSeconds(3))
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftNovelties.applyMutation(ShiftNoveltyMutation.RestoreOriginalPlan(restored, change.final))
        }

        assertEquals(concurrent, store.shifts.getById(original.id))
        assertEquals(change, store.shiftNovelties.observeFormalChange(original.id).first())
    }

    @Test fun failedSecondShiftLinkRollsBackInsertedShift() = runBlocking {
        val origin = shift(id(50))
        val second = shift(id(51))
        store.shifts.insert(origin)
        val invalid = ShiftNovelty(id(52), origin.id, ShiftNoveltyType.SECOND_SHIFT, null, id(99), NOW, NOW)

        try {
            store.shiftNovelties.applyMutation(ShiftNoveltyMutation.CreateSecondShift(invalid, second))
            throw AssertionError("Se esperaba un conflicto")
        } catch (_: Exception) {
            assertNull(store.shifts.getById(second.id))
        }
    }

    private fun shift(id: UUID): Shift {
        val date = LocalDate.of(2026, 8, 13)
        return Shift(
            id, date.atTime(19, 0).atZone(ZONE).toInstant(), date.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant(),
            ZONE, date, "Objetivo Ficticio", "OBJ", null, LocalTime.of(19, 0), LocalTime.of(7, 0),
            0xFF123456.toInt(), null, ShiftStatus.PLANNED, null, null, NOW, NOW,
        )
    }

    private fun id(number: Int) = UUID(0, number.toLong())

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T = try {
        block()
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
        error
    }

    private companion object {
        const val DB = "novelty-persistence-test.db"
        val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
    }
}
