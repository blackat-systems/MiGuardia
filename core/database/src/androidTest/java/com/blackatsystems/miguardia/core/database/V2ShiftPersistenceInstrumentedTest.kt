package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftPositionOnly
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
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
class V2ShiftPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore

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

    private companion object {
        const val DB = "v2-shift-persistence-test.db"
    }
}
