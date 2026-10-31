package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftActualRecordEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.database.entity.RecurringOccurrenceEntity
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated synthetic database; no configured application data is used. */
@RunWith(AndroidJUnit4::class)
class CalendarClearInstrumentedTest {
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val date = V2TestIds.SHIFT_DATE

    @Before fun setUp() {
        context.deleteDatabase(DB)
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database)
    }
    @After fun tearDown() { store.close(); context.deleteDatabase(DB) }

    @Test fun unchangedPreviewIsStableAcrossIndependentReadsAndBothClearAndFrancoApply() = runBlocking {
        store.seedV2Catalog()
        val first = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        val second = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertEquals("An unchanged Room read must not change the CLEAR CAS token", first, second)
        store.calendarBatches.apply(first)
        val franco = store.calendarBatches.preview(CalendarBatchAction.DAY_OFF, setOf(date))
        assertEquals(franco, store.calendarBatches.preview(CalendarBatchAction.DAY_OFF, setOf(date)))
        assertTrue(franco.conflicts.isEmpty())
        store.calendarBatches.apply(franco)
        assertEquals("DAY_OFF", database.explicitDayStatusDao().getAll().single().type)
    }

    @Test fun clearSplitsMedicalAndVacationRangesAndPreservesEveryUnselectedDate() = runBlocking {
        store.seedV2Catalog()
        database.medicalLeaveDao().insert(MedicalLeaveEntity(UUID.randomUUID().toString(), date.toString(), date.plusDays(6).toString(), "Nota sintética", 0, 0))
        database.vacationDao().insert(VacationEntity(UUID.randomUUID().toString(), date.toString(), date.plusDays(6).toString(), 0, 0))
        val selected = setOf(date.plusDays(1), date.plusDays(3), date.plusDays(4))
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, selected)
        assertEquals(3, preview.applicable.size)
        assertTrue(preview.results.all { "tramos exteriores" in it.explanation })
        store.calendarBatches.apply(preview)
        val expected = listOf(date to date, date.plusDays(2) to date.plusDays(2), date.plusDays(5) to date.plusDays(6))
            .map { it.first.toString() to it.second.toString() }
        assertEquals(expected, database.medicalLeaveDao().getAll().map { it.startDate to it.endDateInclusive })
        assertEquals(expected, database.vacationDao().getAll().map { it.startDate to it.endDateInclusive })
        assertTrue(database.medicalLeaveDao().getAll().all { it.privateNote == "Nota sintética" })
        assertEquals(selected.map { it.toString() }.sorted(), database.explicitDayStatusDao().getAll().map { it.localDate }.sorted())
        assertTrue(database.explicitDayStatusDao().getAll().all { it.type == "UNDEFINED" })
    }

    @Test fun clearRemovesOwnedShiftAndNotesButPreservesPreviousOvernightAndItsNotes() = runBlocking {
        val fixture = buildV2CatalogFixture().let { it.copy(template = it.template.copy(startTime = LocalTime.of(19, 0), endTime = LocalTime.of(7, 0))) }
        store.workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
        store.workCatalog.createFirstWorkSet(fixture.toFirstWorkSet())
        val previous = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        val owned = store.buildTestV2Write(fixture, UUID.randomUUID(), date.plusDays(1))
        store.v2Shifts.insert(previous)
        store.v2Shifts.insert(owned)
        val previousNote = note(previous.shift.id)
        val ownedNote = note(owned.shift.id)
        store.shiftNotes.saveWithAttachments(previousNote, emptyList())
        val image = ShiftNoteAttachment(UUID.randomUUID(), ownedNote.id, "note_${UUID.randomUUID()}.jpg", "image/jpeg", 1, 1, 1, 0, Instant.EPOCH, Instant.EPOCH)
        store.shiftNotes.saveWithAttachments(ownedNote, listOf(image))
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date.plusDays(1)))
        assertEquals(setOf(image.storageKey), preview.removedNoteAttachmentStorageKeys)
        assertTrue(preview.results.single().explanation.contains("1 nota(s) y 1 imagen(es)"))
        store.calendarBatches.apply(preview)
        assertNull(store.shifts.getById(owned.shift.id))
        assertEquals(previous.shift, store.shifts.getById(previous.shift.id))
        assertEquals(listOf(previousNote.id.toString()), database.shiftNoteDao().getAll().map { it.id })
        assertTrue(database.shiftNoteAttachmentDao().getAll().isEmpty())
    }

    @Test fun concurrentAssociatedNoteRejectsEntireMixedBatchWithoutDeletingAnyShift() = runBlocking {
        val fixture = store.seedV2Catalog()
        val first = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        val second = store.buildTestV2Write(fixture, UUID.randomUUID(), date.plusDays(1))
        store.v2Shifts.insert(first)
        store.v2Shifts.insert(second)
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date, date.plusDays(1), date.plusDays(2)))
        store.shiftNotes.saveWithAttachments(note(second.shift.id), emptyList())
        try { store.calendarBatches.apply(preview); fail("Expected stale preview rejection") }
        catch (_: ConflictingLocalWriteException) { }
        assertEquals(first.shift, store.shifts.getById(first.shift.id))
        assertEquals(second.shift, store.shifts.getById(second.shift.id))
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
    }

    @Test fun cancelClearPreviewWritesNothingAndUndefinedDoesNotAcquireDestructiveSemantics() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        store.v2Shifts.insert(write)
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertEquals(1, preview.applicable.size)
        assertEquals(write.shift, store.shifts.getById(write.shift.id))
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
        assertEquals(1, store.calendarBatches.preview(CalendarBatchAction.UNDEFINED, setOf(date)).conflicts.size)
    }

    @Test fun concurrentRangeChangeRejectsClearWithoutPartialRangeOrStatusWrites() = runBlocking {
        store.seedV2Catalog()
        val row = MedicalLeaveEntity(UUID.randomUUID().toString(), date.toString(), date.plusDays(4).toString(), null, 0, 0)
        database.medicalLeaveDao().insert(row)
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date.plusDays(1), date.plusDays(3)))
        val changed = row.copy(privateNote = "Cambio concurrente", updatedAtEpochMillis = 1)
        database.medicalLeaveDao().update(changed)
        try { store.calendarBatches.apply(preview); fail("Expected stale preview rejection") }
        catch (_: ConflictingLocalWriteException) { }
        assertEquals(listOf(changed), database.medicalLeaveDao().getAll())
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
    }

    private fun note(shiftId: UUID) = ShiftNote(UUID.randomUUID(), shiftId, "Nota sintética", Instant.EPOCH, Instant.EPOCH)

    @Test fun clearRetiresRecurringOccurrenceAndDeletesActualWithoutResurrectingPlan() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        store.v2Shifts.insert(write)
        val planId = UUID.randomUUID()
        val revision = RecurringPlanRevision(
            id = UUID.randomUUID(), planId = planId, revisionNumber = 1, effectiveFrom = date,
            kind = RecurringPlanRevisionKind.ACTIVE, endDateInclusive = date, pattern = RecurringPattern.EveryNDays(1),
            templateId = fixture.template.id, workPlaceId = fixture.place.id, objectiveId = fixture.objective.id,
            workTypeId = fixture.type.id, objectiveNameSnapshot = fixture.objective.fullName,
            objectiveAbbreviationSnapshot = fixture.objective.abbreviation, objectiveAddressSnapshot = fixture.objective.address,
            workTypeNameSnapshot = fixture.type.name, workTypeBehaviorSnapshot = fixture.type.behavior,
            startTimeSnapshot = fixture.template.startTime, endTimeSnapshot = fixture.template.endTime,
            colorArgbSnapshot = fixture.template.colorArgb, positionSnapshot = null, zoneId = V2TestIds.ZONE,
            createdAt = V2TestIds.NOW.plusSeconds(6))
        database.recurringPlanDao().insertPlan(RecurringPlan(planId, V2TestIds.TIMELINE, fixture.revision.value.sector, V2TestIds.NOW.plusSeconds(5)).toEntity())
        database.recurringPlanDao().insertRevision(revision.toEntity())
        database.recurringPlanDao().insertOccurrences(listOf(RecurringOccurrenceEntity(planId.toString(), date.toString(), revision.id.toString(), write.shift.id.toString(), "AUTOMATIC", revision.createdAt.toEpochMilli(), revision.createdAt.toEpochMilli())))
        val snapshot = requireNotNull(database.v2ShiftDao().getSnapshot(write.shift.id.toString()))
        database.shiftActualDao().insertRecord(ShiftActualRecordEntity(write.shift.id.toString(), snapshot.timelineId, snapshot.sector,
            write.shift.startAt.toEpochMilli(), write.shift.endAt.minusSeconds(60).toEpochMilli(), "Salida ficticia", null,
            V2TestIds.NOW.toEpochMilli(), V2TestIds.NOW.toEpochMilli()))
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertTrue(preview.results.single().explanation.contains("horario(s) real(es)"))
        store.calendarBatches.apply(preview)
        val occurrence = database.recurringPlanDao().getAllOccurrences().single()
        assertEquals("EXCLUDED", occurrence.state)
        assertNull(occurrence.shiftId)
        assertNull(database.shiftActualDao().getRecord(write.shift.id.toString()))
        assertNotNull(database.recurringPlanDao().getPlan(planId.toString()))
        assertEquals(revision.toEntity(), database.recurringPlanDao().getRevisions(planId.toString()).single())
    }

    @Test fun clearRemovesSelectedAvailabilityButPreservesOtherDatesAndHolidayMetadata() = runBlocking {
        store.seedV2Catalog()
        val holiday = HolidayEntity(UUID.randomUUID().toString(), date.toString(), "Feriado sintético", 0, 0)
        database.holidayDao().insert(holiday)
        val input = CalendarBatchAvailability(LocalTime.of(19, 0), LocalTime.of(7, 0), AvailabilityLabel.ON_CALL_RETAINER)
        store.calendarBatches.apply(store.calendarBatches.preview(CalendarBatchAction.AVAILABILITY, setOf(date, date.plusDays(1)), input))
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertTrue(preview.results.single().explanation.contains("feriado se conserva"))
        store.calendarBatches.apply(preview)
        assertEquals(listOf(date.plusDays(1).toString()), database.availabilityWindowDao().getAll().map { it.ownerLocalDate })
        assertEquals(listOf(holiday), database.holidayDao().getAll())
        assertEquals("UNDEFINED", database.explicitDayStatusDao().getAll().single().type)
    }

    @Test fun failureAfterDeletionRollsBackShiftsNotesRangesAndEveryStatus() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        store.v2Shifts.insert(write)
        val note = note(write.shift.id)
        store.shiftNotes.saveWithAttachments(note, emptyList())
        val range = MedicalLeaveEntity(UUID.randomUUID().toString(), date.toString(), date.plusDays(3).toString(), "Preservada", 0, 0)
        database.medicalLeaveDao().insert(range)
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date, date.plusDays(2)))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clear_test BEFORE INSERT ON explicit_day_statuses BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try {
            store.calendarBatches.apply(preview)
            fail("Expected synthetic database failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        finally { database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clear_test") }
        assertEquals(write.shift, store.shifts.getById(write.shift.id))
        assertEquals(note, store.shiftNotes.getById(note.id))
        assertEquals(listOf(range), database.medicalLeaveDao().getAll())
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
    }
    @Test fun clearMoreThanNineHundredShiftsPreservesUnselectedPairsAndRejectsStalePreview() = runBlocking {
        val fixture = store.seedV2Catalog()
        val template = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        val retained = store.buildTestV2Write(fixture, UUID.randomUUID(), date.plusDays(1))
        store.v2Shifts.insert(retained)
        database.withTransaction {
            repeat(1_001) { index ->
                val id = UUID.nameUUIDFromBytes("stage4-clear-shift-$index".toByteArray()).toString()
                database.v2ShiftDao().insertPair(template.shift.toEntity().copy(id = id),
                    template.snapshot.toEntity().copy(shiftId = id))
            }
        }
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertEquals(preview, store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date)))
        assertEquals(1_002, database.v2ShiftDao().getAllShiftsWithSnapshots().size)
        val selectedShiftId = UUID.fromString(database.v2ShiftDao().getAllShiftsWithSnapshots()
            .first { it.shift.localStartDate == date.toString() }.shift.id)
        store.shiftNotes.saveWithAttachments(note(selectedShiftId), emptyList())
        try {
            store.calendarBatches.apply(preview)
            fail("A changed note must invalidate the complete large CLEAR preview")
        } catch (_: ConflictingLocalWriteException) { }
        assertEquals(1_002, database.v2ShiftDao().getAllShiftsWithSnapshots().size)
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
        store.calendarBatches.apply(store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date)))
        assertEquals(listOf(retained.shift.toEntity()), database.v2ShiftDao().getAllShiftsWithSnapshots().map { it.shift })
        assertTrue(database.shiftNoteDao().getAll().isEmpty())
    }

    @Test fun clearNotesBeyondMeasuredSqliteBindLimitKeepsAllReferencesAndRollsBackOnFailure() = runBlocking {
        val sqliteLimit = measuredSqliteBindLimit()
        val count = maxOf(1_001, sqliteLimit + 1)
        Log.i("Stage4SqlLimit", "measured_bind_limit=$sqliteLimit synthetic_notes=$count")
        val fixture = store.seedV2Catalog()
        val owned = store.buildTestV2Write(fixture, UUID.randomUUID(), date)
        val retained = store.buildTestV2Write(fixture, UUID.randomUUID(), date.plusDays(1))
        store.v2Shifts.insert(owned)
        store.v2Shifts.insert(retained)
        val retainedNote = note(retained.shift.id)
        store.shiftNotes.saveWithAttachments(retainedNote, emptyList())
        val imageKeys = linkedSetOf<String>()
        database.withTransaction {
            repeat(count) { index ->
                val id = UUID.nameUUIDFromBytes("stage4-clear-note-$index".toByteArray())
                database.shiftNoteDao().insert(ShiftNoteEntity(id.toString(), owned.shift.id.toString(),
                    "Nota sintética $index", 0, 0))
                if (index in setOf(0, 900, count - 1)) {
                    val attachmentId = UUID.randomUUID()
                    val attachment = ShiftNoteAttachment(attachmentId, id, "note_$attachmentId.jpg",
                        "image/jpeg", 1, 1, 1, 0, Instant.EPOCH, Instant.EPOCH)
                    database.shiftNoteAttachmentDao().insertAll(listOf(attachment.toEntity()))
                    imageKeys += attachment.storageKey
                }
            }
        }
        val preview = store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date))
        assertEquals(imageKeys, preview.removedNoteAttachmentStorageKeys)
        assertTrue(preview.results.single().explanation.contains("$count nota(s) y 3 imagen(es)"))
        assertEquals(preview, store.calendarBatches.preview(CalendarBatchAction.CLEAR, setOf(date)))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_stage4_clear BEFORE INSERT ON explicit_day_statuses BEGIN SELECT RAISE(ABORT, 'stage4 synthetic failure'); END")
        try {
            store.calendarBatches.apply(preview)
            fail("A failure after deletion must roll the complete large batch back")
        } catch (_: SQLiteException) { }
        finally { database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_stage4_clear") }
        assertEquals(count + 1, database.shiftNoteDao().getAll().size)
        assertEquals(imageKeys, database.shiftNoteAttachmentDao().getAll().map { it.storageKey }.toSet())
        assertEquals(owned.shift, store.shifts.getById(owned.shift.id))
        assertTrue(database.explicitDayStatusDao().getAll().isEmpty())
        store.calendarBatches.apply(preview)
        assertEquals(listOf(retainedNote.toEntity()), database.shiftNoteDao().getAll())
        assertTrue(database.shiftNoteAttachmentDao().getAll().isEmpty())
        assertEquals(retained.shift, store.shifts.getById(retained.shift.id))
        assertNull(store.shifts.getById(owned.shift.id))
    }

    /** Probe the actual connection limit; no assumption about the Android SQLite version. */
    private fun measuredSqliteBindLimit(): Int {
        fun accepts(count: Int): Boolean = try {
            database.openHelper.writableDatabase.compileStatement("SELECT ?$count").use { it.bindNull(count) }
            true
        } catch (_: SQLiteException) { false }
        var lower = 1
        var upper = 1_024
        while (accepts(upper)) {
            lower = upper
            check(upper < 1_048_576) { "SQLite bind limit exceeds the bounded stress fixture; investigate separately" }
            upper *= 2
        }
        while (upper - lower > 1) {
            val middle = lower + (upper - lower) / 2
            if (accepts(middle)) lower = middle else upper = middle
        }
        check(accepts(lower) && !accepts(lower + 1))
        return lower
    }

    private companion object { const val DB = "calendar-clear-stage3-test.db" }
}
