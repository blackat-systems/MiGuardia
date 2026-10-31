package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.repository.RoomV2ShiftRepository
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftPositionOnly
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
class V2ShiftPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore

    @Test
    fun intervalOverwriteRemovesPreviousNightAndPreservesLaterSameDayShift() = runBlocking {
        val fixture = store.seedV2Catalog()
        val nightTemplate = fixture.template.copy(id = V2TestIds.uuid(951),
            startTime = LocalTime.of(19, 0), endTime = LocalTime.of(9, 0))
        val laterTemplate = fixture.template.copy(id = V2TestIds.uuid(952),
            startTime = LocalTime.of(18, 0), endTime = LocalTime.of(22, 0))
        store.workCatalog.createWorkTemplate(nightTemplate)
        store.workCatalog.createWorkTemplate(laterTemplate)
        val date = V2TestIds.SHIFT_DATE
        val night = store.buildTestV2Write(fixture.copy(template = nightTemplate), V2TestIds.uuid(953), date.minusDays(1))
        val later = store.buildTestV2Write(fixture.copy(template = laterTemplate), V2TestIds.uuid(954), date)
        val candidate = store.buildTestV2Write(fixture, V2TestIds.uuid(955), date)
        store.v2Shifts.insert(night)
        store.v2Shifts.insert(later)
        val plan = com.blackatsystems.miguardia.core.domain.shift.planV2ShiftBatch(
            setOf(date), listOf(night.shift, later.shift), listOf(candidate),
            com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy.REPLACE,
        )
        store.v2Shifts.applyV2Batch(plan.mutation.copy(deletionExpectations =
            store.v2Shifts.captureDeletionExpectations(plan.mutation.shiftIdsToDelete)),
            ShiftOccupancyExpectation.capture(date.minusDays(1), date, listOf(night.shift, later.shift)))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(night.shift.id))
        assertEquals(V2ShiftLookup.V2(later), store.v2Shifts.getShift(later.shift.id))
        assertEquals(V2ShiftLookup.V2(candidate), store.v2Shifts.getShift(candidate.shift.id))
    }

    @Test
    fun unifiedBatchInsertsIndependentNotesAndRecognizesExactRetry() = runBlocking {
        val fixture = store.seedV2Catalog()
        val first = store.buildTestV2Write(fixture, V2TestIds.uuid(901), V2TestIds.SHIFT_DATE)
        val second = store.buildTestV2Write(fixture, V2TestIds.uuid(902), V2TestIds.SHIFT_DATE.plusDays(1))
        val notes = listOf(first, second).mapIndexed { index, write ->
            val note = ShiftNote(V2TestIds.uuid(911 + index), write.shift.id,
                "Nota sintética", write.shift.createdAt, write.shift.updatedAt)
            ShiftNoteWithAttachments(note, listOf(ShiftNoteAttachment(
                id = V2TestIds.uuid(915 + index), noteId = note.id,
                storageKey = "note_${V2TestIds.uuid(915 + index)}.jpg", mimeType = "image/jpeg",
                byteSize = 100, pixelWidth = 10, pixelHeight = 10, position = 0,
                createdAt = write.shift.createdAt, updatedAt = write.shift.updatedAt,
            )))
        }
        val mutation = V2ShiftBatchMutation(shiftsToInsert = listOf(first, second), notesToInsert = notes)
        val expected = ShiftOccupancyExpectation.capture(first.shift.localStartDate, second.shift.localStartDate, emptyList())
        store.v2Shifts.applyV2Batch(mutation, expected)
        store.v2Shifts.applyV2Batch(mutation, expected)
        assertEquals(2, database.shiftNoteDao().getAll().size)
        assertEquals(notes[0].note, store.shiftNotes.getById(notes[0].note.id))
        assertEquals(notes[1].note, store.shiftNotes.getById(notes[1].note.id))
        store.shiftNotes.delete(notes[0].note.id)
        assertEquals(notes[1], store.shiftNotes.getWithAttachments(notes[1].note.id))
        assertEquals(setOf(notes[1].attachments.single().storageKey), store.shiftNotes.allAttachmentStorageKeys())
    }

    @Test
    fun changedAssociatedNoteRejectsOverwriteWithoutPartialWrites() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(921), V2TestIds.SHIFT_DATE)
        val candidate = store.buildTestV2Write(fixture, V2TestIds.uuid(922), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        val expected = store.v2Shifts.captureDeletionExpectations(setOf(original.shift.id))
        store.shiftNotes.insert(ShiftNote(V2TestIds.uuid(923), original.shift.id, "Nota agregada luego del preview",
            original.shift.createdAt, original.shift.updatedAt))
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.applyV2Batch(V2ShiftBatchMutation(shiftIdsToDelete = setOf(original.shift.id),
                shiftsToInsert = listOf(candidate), deletionExpectations = expected), occupancyOf(original))
        }
        assertEquals(V2ShiftLookup.V2(original), store.v2Shifts.getShift(original.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(candidate.shift.id))
    }

    @Test
    fun overlayPreservesFrancoAndStatusCasRejectsStaleDecision() = runBlocking {
        val fixture = store.seedV2Catalog()
        val date = V2TestIds.SHIFT_DATE
        val candidate = store.buildTestV2Write(fixture, V2TestIds.uuid(931), date)
        val occupancy = ShiftOccupancyExpectation.capture(date, date, emptyList())
        val mutation = V2ShiftBatchMutation(shiftsToInsert = listOf(candidate),
            explicitStatusExpectations = mapOf(date to null))
        store.explicitDayStatuses.set(date, ExplicitDayStatusType.DAY_OFF)
        assertSuspendThrows<ConflictingLocalWriteException> { store.v2Shifts.applyV2Batch(mutation, occupancy) }
        store.v2Shifts.applyV2Batch(mutation.copy(explicitStatusExpectations = mapOf(date to
            ExplicitDayStatus(date, ExplicitDayStatusType.DAY_OFF))), occupancy)
        assertEquals(ExplicitDayStatusType.DAY_OFF, store.explicitDayStatuses.observeBetween(date, date).first().single().type)
    }

    @Test
    fun noteConstraintFailureRollsBackEveryInsertedJourney() = runBlocking {
        val fixture = store.seedV2Catalog()
        val first = store.buildTestV2Write(fixture, V2TestIds.uuid(941), V2TestIds.SHIFT_DATE)
        val second = store.buildTestV2Write(fixture, V2TestIds.uuid(942), V2TestIds.SHIFT_DATE.plusDays(1))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_load_note BEFORE INSERT ON shift_notes BEGIN SELECT RAISE(ABORT, 'synthetic note failure'); END",
        )
        val note = ShiftNoteWithAttachments(ShiftNote(V2TestIds.uuid(943), second.shift.id, "Nota sintética",
            second.shift.createdAt, second.shift.updatedAt), emptyList())
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(V2ShiftBatchMutation(shiftsToInsert = listOf(first, second), notesToInsert = listOf(note)),
                ShiftOccupancyExpectation.capture(first.shift.localStartDate, second.shift.localStartDate, emptyList()))
        }
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(first.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(second.shift.id))
        assertTrue(database.shiftNoteDao().getAll().isEmpty())
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun pairedShiftInsertUpdateReopenAndDeleteRoundTrip() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(101), V2TestIds.SHIFT_DATE)

        store.v2Shifts.insert(original)
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(V2ShiftLookup.V2(original), store.v2Shifts.getShift(original.shift.id))

        store.close()
        openStore()
        assertEquals(V2ShiftLookup.V2(original), store.v2Shifts.getShift(original.shift.id))

        val updated = editV2ShiftPositionOnly(
            original = original,
            position = "Puesto ficticio",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )
        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(updated)),
            expectedOccupancy = occupancyOf(original),
            expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
        )
        assertEquals(V2ShiftLookup.V2(updated), store.v2Shifts.getShift(updated.shift.id))

        store.v2Shifts.deleteShift(updated)
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(updated.shift.id))
        assertNull(store.shifts.getById(updated.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(updated.shift.id))
    }

    @Test
    fun batchWritesOnlyCompletePairsAndClearsOnlyCoveredStatus() = runBlocking {
        val fixture = store.seedV2Catalog()
        val firstDate = V2TestIds.SHIFT_DATE
        val secondDate = firstDate.plusDays(1)
        store.explicitDayStatuses.set(firstDate, com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType.DAY_OFF)
        val first = store.buildTestV2Write(fixture, V2TestIds.uuid(111), firstDate)
        val second = store.buildTestV2Write(fixture, V2TestIds.uuid(112), secondDate)

        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(
                shiftsToInsert = listOf(first, second),
                explicitDayStatusDatesToClear = setOf(firstDate),
            ),
            expectedOccupancy = ShiftOccupancyExpectation.capture(firstDate, secondDate, emptyList()),
        )

        assertEquals(V2ShiftLookup.V2(first), store.v2Shifts.getShift(first.shift.id))
        assertEquals(V2ShiftLookup.V2(second), store.v2Shifts.getShift(second.shift.id))
        assertTrue(store.explicitDayStatuses.observeBetween(firstDate, secondDate).first().isEmpty())
    }

    @Test
    fun staleOccupancyRejectsTheWholeBatch() = runBlocking {
        val fixture = store.seedV2Catalog()
        val date = V2TestIds.SHIFT_DATE
        val occupied = store.buildTestV2Write(fixture, V2TestIds.uuid(121), date)
        val candidate = store.buildTestV2Write(fixture, V2TestIds.uuid(122), date.plusDays(1))
        val staleExpectation = ShiftOccupancyExpectation.capture(date, date.plusDays(1), emptyList())
        store.v2Shifts.insert(occupied)

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(shiftsToInsert = listOf(candidate)),
                expectedOccupancy = staleExpectation,
            )
        }
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(candidate.shift.id))
        assertEquals(V2ShiftLookup.V2(occupied), store.v2Shifts.getShift(occupied.shift.id))
    }

    @Test
    fun fullPairCasRejectsAConcurrentPositionChangeHiddenFromOccupancy() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(123), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        val occupancy = occupancyOf(original)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shifts SET position = 'Cambio concurrente' WHERE id = '${original.shift.id}'",
        )
        val candidate = editV2ShiftPositionOnly(
            original = original,
            position = "Cambio confirmado",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(candidate)),
                expectedOccupancy = occupancy,
                expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
            )
        }

        assertEquals("Cambio concurrente", storedWrite(original.shift.id).shift.position)
    }

    @Test
    fun fullPairCasRejectsAConcurrentSnapshotChangeHiddenFromOccupancy() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(124), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        val occupancy = occupancyOf(original)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shift_work_snapshots " +
                "SET workTypeNameSnapshot = 'Nombre histórico concurrente' " +
                "WHERE shiftId = '${original.shift.id}'",
        )
        val candidate = editV2ShiftPositionOnly(
            original = original,
            position = "Cambio confirmado",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(candidate)),
                expectedOccupancy = occupancy,
                expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
            )
        }

        assertEquals(
            "Nombre histórico concurrente",
            storedWrite(original.shift.id).snapshot.workTypeNameSnapshot,
        )
    }

    @Test
    fun exactDeleteRejectsAChangedPairAndPreservesIt() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(125), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shifts SET position = 'Cambio concurrente' WHERE id = '${original.shift.id}'",
        )

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.deleteShift(original)
        }

        assertEquals("Cambio concurrente", storedWrite(original.shift.id).shift.position)
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
    }

    @Test
    fun exactDeleteRejectsATargetThatAlreadyDisappeared() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(129), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        assertEquals(1, database.v2ShiftDao().deleteShiftAndOwnedSnapshot(original.shift.id.toString()))

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.v2Shifts.deleteShift(original)
        }

        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(original.shift.id))
    }

    @Test
    fun databaseFailureRollsBackDeletedAndInsertedPairs() = runBlocking {
        val fixture = store.seedV2Catalog()
        val date = V2TestIds.SHIFT_DATE
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(126), date)
        val replacement = store.buildTestV2Write(fixture, V2TestIds.uuid(127), date)
        store.v2Shifts.insert(original)
        val (note, notificationConfig) = seedCommonShiftDependencies(original.shift.id, 126)
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_v2_shift_rollback
                BEFORE INSERT ON shifts
                WHEN NEW.id = '${replacement.shift.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced test failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(
                    shiftIdsToDelete = setOf(original.shift.id),
                    shiftsToInsert = listOf(replacement),
                ),
                expectedOccupancy = ShiftOccupancyExpectation.capture(
                    date,
                    date,
                    listOf(original.shift),
                ),
            )
        }

        assertEquals(V2ShiftLookup.V2(original), store.v2Shifts.getShift(original.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(replacement.shift.id))
        assertEquals(note, store.shiftNotes.getById(note.id))
        assertEquals(notificationConfig, store.shiftNotificationConfigs.getForShift(original.shift.id))
    }

    @Test
    fun exactDeleteCascadesOnlyTargetDependenciesAndPreservesSharedCatalog() = runBlocking {
        val fixture = store.seedV2Catalog()
        val target = store.buildTestV2Write(fixture, V2TestIds.uuid(128), V2TestIds.SHIFT_DATE)
        val companion = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(130),
            V2TestIds.SHIFT_DATE.plusDays(1),
        )
        store.v2Shifts.insert(target)
        store.v2Shifts.insert(companion)
        val (targetNote, _) = seedCommonShiftDependencies(target.shift.id, 128)
        val (companionNote, companionConfig) = seedCommonShiftDependencies(companion.shift.id, 130)

        store.v2Shifts.deleteShift(target)

        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(target.shift.id))
        assertNull(store.shiftNotes.getById(targetNote.id))
        assertNull(store.shiftNotificationConfigs.getForShift(target.shift.id))
        assertEquals(V2ShiftLookup.V2(companion), store.v2Shifts.getShift(companion.shift.id))
        assertEquals(companionNote, store.shiftNotes.getById(companionNote.id))
        assertEquals(companionConfig, store.shiftNotificationConfigs.getForShift(companion.shift.id))
        assertEquals(fixture.objective, store.objectives.getById(fixture.objective.id))
        assertEquals(fixture.place, store.workCatalog.getWorkPlace(fixture.place.id))
        assertEquals(fixture.type, store.workCatalog.getWorkType(fixture.type.id))
        assertEquals(fixture.template, store.workCatalog.getWorkTemplate(fixture.template.id))
    }

    @Test
    fun anOrphanShiftProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(131), LocalDate.of(2026, 8, 25))
        database.v2ShiftDao().insertShift(write.shift.toEntity())

        assertSuspendThrows<InvalidLocalDataException> { store.shifts.getById(write.shift.id) }
        assertSuspendThrows<InvalidLocalDataException> { store.v2Shifts.getShift(write.shift.id) }
    }

    @Test
    fun anOrphanSnapshotProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(132), LocalDate.of(2026, 8, 26))
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys = OFF")
        try {
            database.v2ShiftDao().insertSnapshot(write.snapshot.toEntity())
        } finally {
            sqlite.execSQL("PRAGMA foreign_keys = ON")
        }

        sqlite.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(write.shift.id)
        }
    }

    @Test
    fun archivedSourcesStillAllowAnExactPositionOnlyUpdate() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(133), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        archiveSources(fixture)
        val updated = editV2ShiftPositionOnly(
            original = original,
            position = "Puesto archivado",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )

        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(updated)),
            expectedOccupancy = occupancyOf(original),
            expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
        )

        assertEquals(updated, storedWrite(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
    }

    @Test
    fun archivedPositionOnlyUpdateRollsBackWhenTheSnapshotWriteFails() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(134), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(original)
        archiveSources(fixture)
        val updated = editV2ShiftPositionOnly(
            original = original,
            position = "No debe persistir",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_position_only_rollback
                BEFORE UPDATE ON shift_work_snapshots
                WHEN OLD.shiftId = '${original.shift.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced snapshot failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(updated)),
                expectedOccupancy = occupancyOf(original),
                expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
            )
        }

        assertEquals(original, storedWrite(original.shift.id))
    }

    @Test
    fun corruptSnapshotCodeProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(135), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shift_work_snapshots SET workTypeBehaviorSnapshot = 'LEGACY' " +
                "WHERE shiftId = '${write.shift.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getShift(write.shift.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(write.shift.id)
        }
    }

    @Test
    fun corruptAuditTimestampsProduceAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(136), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shifts SET updatedAtEpochMillis = createdAtEpochMillis - 1 " +
                "WHERE id = '${write.shift.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getShift(write.shift.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.getById(write.shift.id)
        }
    }

    @Test
    fun corruptDerivedInstantsProduceAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(137), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shifts SET startEpochMillis = startEpochMillis + 60000 " +
                "WHERE id = '${write.shift.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getShift(write.shift.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.getById(write.shift.id)
        }
    }

    @Test
    fun laterIntermediateRevisionDoesNotRewriteStoredConfigurationSnapshot() = runBlocking {
        val fixture = store.seedV2Catalog()
        val date = V2TestIds.SHIFT_DATE
        val historical = store.buildTestV2Write(
            fixture = fixture,
            id = V2TestIds.uuid(141),
            date = date,
        )
        store.v2Shifts.insert(historical)

        val intermediate = EffectiveRevision(
            id = V2TestIds.uuid(142),
            effectiveFrom = date.minusDays(5),
            value = WorkConfiguration(
                sector = fixture.revision.value.sector,
                hoursReference = HoursReference.PendingSetup,
                availabilityLabel = AvailabilityLabel.AVAILABLE_FOR_CALL,
            ),
        )
        store.workConfiguration.addRevision(V2TestIds.TIMELINE, intermediate)

        assertEquals(
            fixture.revision.id,
            store.v2Shifts.getWorkSnapshot(historical.shift.id)?.configurationRevisionId,
        )
        val current = store.buildTestV2Write(
            fixture = fixture,
            id = V2TestIds.uuid(143),
            date = date,
            timestamp = V2TestIds.NOW.plusSeconds(120),
        )
        store.v2Shifts.insert(current)
        assertEquals(
            intermediate.id,
            store.v2Shifts.getWorkSnapshot(current.shift.id)?.configurationRevisionId,
        )
    }

    @Test
    fun concurrentWritersFromOneSnapshotProduceOneWinnerOneConflictAndNoPartialRows() = runBlocking {
        val fixture = store.seedV2Catalog()
        val original = store.buildTestV2Write(
            fixture = fixture,
            id = V2TestIds.uuid(151),
            date = V2TestIds.SHIFT_DATE,
        )
        store.v2Shifts.insert(original)
        val expectedOccupancy = ShiftOccupancyExpectation.capture(
            startDateInclusive = original.shift.localStartDate,
            endDateInclusive = original.shift.localStartDate.plusDays(1),
            shifts = listOf(original.shift),
        )
        val expectedUpdate = V2ShiftWriteExpectation.capture(listOf(original))
        val firstBatch = RacingBatch(
            name = "primer escritor",
            updated = editV2ShiftPositionOnly(
                original = original,
                position = "Edición concurrente A",
                updatedAt = original.shift.updatedAt.plusSeconds(1),
            ),
            companion = store.buildTestV2Write(
                fixture = fixture,
                id = V2TestIds.uuid(152),
                date = original.shift.localStartDate.plusDays(1),
                position = "Compañera A",
            ),
        )
        val secondBatch = RacingBatch(
            name = "segundo escritor",
            updated = editV2ShiftPositionOnly(
                original = original,
                position = "Edición concurrente B",
                updatedAt = original.shift.updatedAt.plusSeconds(2),
            ),
            companion = store.buildTestV2Write(
                fixture = fixture,
                id = V2TestIds.uuid(153),
                date = original.shift.localStartDate.plusDays(1),
                position = "Compañera B",
            ),
        )
        val writers = listOf(
            RoomV2ShiftRepository(database, Clock.systemUTC()),
            RoomV2ShiftRepository(database, Clock.systemUTC()),
        )
        val batches = listOf(firstBatch, secondBatch)

        val outcomes = coroutineScope {
            val ready = Channel<Unit>(capacity = writers.size)
            val release = CompletableDeferred<Unit>()
            val competingWrites = writers.zip(batches).map { (writer, batch) ->
                async(Dispatchers.IO) {
                    ready.send(Unit)
                    release.await()
                    executeRacingBatch(
                        writer = writer,
                        batch = batch,
                        expectedOccupancy = expectedOccupancy,
                        expectedUpdate = expectedUpdate,
                    )
                }
            }
            repeat(writers.size) { ready.receive() }
            release.complete(Unit)
            competingWrites.map { it.await() }
        }

        assertEquals(1, outcomes.count { it.failure == null })
        assertEquals(1, outcomes.count { it.failure is ConflictingLocalWriteException })
        assertTrue(
            outcomes.all { outcome ->
                outcome.failure == null || outcome.failure is ConflictingLocalWriteException
            },
        )
        val winner = outcomes.single { it.failure == null }.batch
        val loser = outcomes.single { it.failure is ConflictingLocalWriteException }.batch
        assertRacingBatchState(winner, loser)

        store.close()
        openStore()
        assertRacingBatchState(winner, loser)
    }

    private suspend fun executeRacingBatch(
        writer: V2ShiftRepository,
        batch: RacingBatch,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdate: V2ShiftWriteExpectation,
    ): RacingOutcome = try {
        writer.applyV2Batch(
            mutation = V2ShiftBatchMutation(
                shiftsToInsert = listOf(batch.companion),
                shiftsToUpdate = listOf(batch.updated),
            ),
            expectedOccupancy = expectedOccupancy,
            expectedUpdates = expectedUpdate,
        )
        RacingOutcome(batch, null)
    } catch (error: Throwable) {
        RacingOutcome(batch, error)
    }

    private suspend fun assertRacingBatchState(winner: RacingBatch, loser: RacingBatch) {
        assertEquals(V2ShiftLookup.V2(winner.updated), store.v2Shifts.getShift(winner.updated.shift.id))
        assertEquals(V2ShiftLookup.V2(winner.companion), store.v2Shifts.getShift(winner.companion.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(loser.companion.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(loser.companion.shift.id))
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM shifts"))
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM shift_work_snapshots"))
        assertEquals(
            0L,
            scalarLong(
                "SELECT COUNT(*) FROM shifts AS s " +
                    "LEFT JOIN shift_work_snapshots AS w ON w.shiftId = s.id " +
                    "WHERE w.shiftId IS NULL",
            ),
        )
        assertEquals(
            0L,
            scalarLong(
                "SELECT COUNT(*) FROM shift_work_snapshots AS w " +
                    "LEFT JOIN shifts AS s ON s.id = w.shiftId " +
                    "WHERE s.id IS NULL",
            ),
        )
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
            assertEquals(1, cursor.count)
        }
    }

    private fun scalarLong(sql: String): Long =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private suspend fun seedCommonShiftDependencies(
        shiftId: UUID,
        number: Int,
    ): Pair<ShiftNote, ShiftNotificationConfig> {
        val note = ShiftNote(
            id = V2TestIds.uuid(800 + number),
            shiftId = shiftId,
            body = "Nota ficticia $number",
            createdAt = V2TestIds.NOW.plusSeconds(number.toLong()),
            updatedAt = V2TestIds.NOW.plusSeconds(number.toLong()),
        )
        val config = ShiftNotificationConfig(
            shiftId = shiftId,
            reminderLeadMinutes = listOf(5L, 30L),
        )
        store.shiftNotes.insert(note)
        store.shiftNotificationConfigs.replace(config)
        return note to config
    }

    private suspend fun archiveSources(fixture: SeededV2Catalog) {
        store.workCatalog.setWorkTemplateActive(
            fixture.template.id,
            false,
            V2TestIds.NOW.plusSeconds(10),
        )
        store.workCatalog.setWorkTypeActive(
            fixture.type.id,
            false,
            V2TestIds.NOW.plusSeconds(11),
        )
        store.workCatalog.setWorkPlaceActive(
            fixture.place.id,
            false,
            V2TestIds.NOW.plusSeconds(12),
        )
    }

    private fun occupancyOf(write: V2ShiftWrite): ShiftOccupancyExpectation =
        ShiftOccupancyExpectation.capture(
            startDateInclusive = write.shift.localStartDate,
            endDateInclusive = write.shift.localStartDate,
            shifts = listOf(write.shift),
        )

    private suspend fun storedWrite(id: UUID): V2ShiftWrite =
        (store.v2Shifts.getShift(id) as V2ShiftLookup.V2).write

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database)
    }

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

    private data class RacingBatch(
        val name: String,
        val updated: V2ShiftWrite,
        val companion: V2ShiftWrite,
    )

    private data class RacingOutcome(
        val batch: RacingBatch,
        val failure: Throwable?,
    )

    private companion object {
        const val DB = "v2-shift-persistence-test.db"
    }
}
