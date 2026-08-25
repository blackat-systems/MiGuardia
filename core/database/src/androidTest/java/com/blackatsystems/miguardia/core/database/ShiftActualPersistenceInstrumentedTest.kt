package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftActualClassSelection
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualFragmentDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftPositionOnly
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftActualPersistenceInstrumentedTest {
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
    fun aggregateSupportsExtraCorrectionRegularReturnAndReopen() = runBlocking {
        val write = seedShift(V2TestIds.uuid(201))
        val extraClass = createClass(V2TestIds.uuid(202), "Extensión de turno")
        assertTrue(store.shiftActuals.saveExtraWorkClass(null, extraClass) is ExtraWorkClassWriteResult.Saved)
        val initial = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val extraMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = initial,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Salida posterior",
                    explanation = "Cobertura ficticia",
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(extraClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(203),
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )

        assertTrue(store.shiftActuals.save(extraMutation) is ShiftActualWriteResult.Saved)
        val storedExtra = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertEquals(1, storedExtra.previousActual?.extraIntervals?.size)
        assertEquals(60L, storedExtra.previousActual?.extraMinutes)
        assertEquals("Extensión de turno", storedExtra.previousActual?.extraIntervals?.single()?.classNameSnapshot)

        store.close()
        openStore()
        val reopened = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertEquals(storedExtra.previousActual, reopened.previousActual)

        val regularMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = reopened,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt.plus(Duration.ofHours(1)),
                    actualEnd = write.shift.endAt,
                    differenceReason = "Ingreso posterior",
                    explanation = null,
                    differenceChoice = null,
                    classSelection = null,
                    fragments = emptyList(),
                ),
                clock = clockAfter(write),
                timestamp = requireNotNull(reopened.previousActual).record.updatedAt.plusMillis(1),
            ),
        )
        assertTrue(store.shiftActuals.save(regularMutation) is ShiftActualWriteResult.Saved)
        val shortened = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertEquals(0, shortened.previousActual?.extraIntervals?.size)
        assertEquals(420L, shortened.previousActual?.regularMinutes)
        assertTrue(store.shiftActuals.returnToPlanned(shortened) is ShiftActualWriteResult.ReturnedToPlanned)
        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)
    }

    @Test
    fun twoFragmentsKeepHistoricalSnapshotAfterCatalogRenameArchiveAndReactivate() = runBlocking {
        val write = seedShift(V2TestIds.uuid(211))
        val originalClass = createClass(V2TestIds.uuid(212), "Horas extras")
        store.shiftActuals.saveExtraWorkClass(null, originalClass)
        val expectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt.minus(Duration.ofMinutes(30)),
                    actualEnd = write.shift.endAt.plus(Duration.ofMinutes(30)),
                    differenceReason = "Entrada y salida extendidas",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(originalClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(213),
                            write.shift.startAt.minus(Duration.ofMinutes(30)),
                            write.shift.startAt,
                        ),
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(214),
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofMinutes(30)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )
        assertTrue(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved)

        val renamed = originalClass.updated(
            name = "Servicio extraordinario",
            isActive = false,
            timestamp = originalClass.updatedAt.plusMillis(1),
        )
        assertTrue(store.shiftActuals.saveExtraWorkClass(originalClass, renamed) is ExtraWorkClassWriteResult.Saved)
        val afterArchive = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertFalse(requireNotNull(afterArchive.observedClass).isActive)
        assertEquals(
            listOf("Horas extras", "Horas extras"),
            requireNotNull(afterArchive.previousActual).extraIntervals.map { it.classNameSnapshot },
        )

        val reactivated = renamed.updated(isActive = true, timestamp = renamed.updatedAt.plusMillis(1))
        assertTrue(store.shiftActuals.saveExtraWorkClass(renamed, reactivated) is ExtraWorkClassWriteResult.Saved)
        assertTrue(requireNotNull(store.shiftActuals.getExpectation(write.shift.id)?.observedClass).isActive)
    }

    @Test
    fun inlineDuplicateRollsBackClassAndAggregateAndClassCasRejectsStaleSelection() = runBlocking {
        val write = seedShift(V2TestIds.uuid(221))
        val existing = createClass(V2TestIds.uuid(222), "Servicio extra")
        store.shiftActuals.saveExtraWorkClass(null, existing)
        val expectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val duplicateMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Extensión ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.NewDraft(
                        V2TestIds.uuid(223),
                        "  SERVICIO   EXTRA  ",
                        helpsMeetHoursReference = false,
                        showDedicatedSummary = true,
                    ),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(224),
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )
        assertEquals(ShiftActualWriteResult.DuplicateClassName, store.shiftActuals.save(duplicateMutation))
        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)
        assertEquals(1, store.shiftActuals.observeExtraWorkClasses(V2TestIds.TIMELINE, existing.sector).take(1).toList().single().size)

        val validMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Extensión ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(existing),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(225),
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(120),
            ),
        )
        val renamed = existing.updated(name = "Clase concurrente", timestamp = existing.updatedAt.plusMillis(1))
        store.shiftActuals.saveExtraWorkClass(existing, renamed)
        assertEquals(ShiftActualWriteResult.Conflict, store.shiftActuals.save(validMutation))
        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)
    }

    @Test
    fun forgedSnapshotsAndVersionTimestampsAreRejectedWithoutPartialWrites() = runBlocking {
        val write = seedShift(V2TestIds.uuid(226))
        val originalClass = createClass(V2TestIds.uuid(227), "Horas extras")
        val reclassifiedClass = createClass(V2TestIds.uuid(228), "Servicio extraordinario")
        assertTrue(
            store.shiftActuals.saveExtraWorkClass(null, originalClass) is ExtraWorkClassWriteResult.Saved,
        )
        assertTrue(
            store.shiftActuals.saveExtraWorkClass(null, reclassifiedClass) is ExtraWorkClassWriteResult.Saved,
        )
        val initialExpectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val fragmentId = V2TestIds.uuid(229)
        val initialMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = initialExpectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Extensión ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(originalClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            fragmentId,
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )
        val forgedCreation = initialMutation.copy(
            replacement = initialMutation.replacement.copy(
                extraIntervals = initialMutation.replacement.extraIntervals.map { interval ->
                    interval.copy(classNameSnapshot = "Fotografía inventada")
                },
            ),
        )
        assertSuspendThrows<IllegalArgumentException> {
            store.shiftActuals.save(forgedCreation)
        }
        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)

        assertTrue(store.shiftActuals.save(initialMutation) is ShiftActualWriteResult.Saved)
        val afterInitial = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val reclassification = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = afterInitial,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Reclasificación ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(reclassifiedClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            fragmentId,
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(120),
            ),
        )
        val forgedReclassification = reclassification.copy(
            replacement = reclassification.replacement.copy(
                extraIntervals = reclassification.replacement.extraIntervals.map { interval ->
                    interval.copy(
                        classNameSnapshot = originalClass.name,
                        helpsMeetHoursReferenceSnapshot = originalClass.helpsMeetHoursReference,
                        showDedicatedSummarySnapshot = originalClass.showDedicatedSummary,
                    )
                },
            ),
        )
        assertSuspendThrows<IllegalArgumentException> {
            store.shiftActuals.save(forgedReclassification)
        }
        assertEquals(afterInitial, store.shiftActuals.getExpectation(write.shift.id))

        val forgedCreatedAt = reclassification.copy(
            replacement = reclassification.replacement.copy(
                record = reclassification.replacement.record.copy(
                    createdAt = requireNotNull(afterInitial.previousActual).record.createdAt.minusMillis(1),
                ),
            ),
        )
        assertSuspendThrows<IllegalArgumentException> {
            store.shiftActuals.save(forgedCreatedAt)
        }
        assertEquals(afterInitial, store.shiftActuals.getExpectation(write.shift.id))

        val previousUpdatedAt = requireNotNull(afterInitial.previousActual).record.updatedAt
        val forgedUpdatedAt = reclassification.copy(
            replacement = reclassification.replacement.copy(
                record = reclassification.replacement.record.copy(updatedAt = previousUpdatedAt),
                extraIntervals = reclassification.replacement.extraIntervals.map { interval ->
                    interval.copy(updatedAt = previousUpdatedAt)
                },
            ),
        )
        assertSuspendThrows<IllegalArgumentException> {
            store.shiftActuals.save(forgedUpdatedAt)
        }
        assertEquals(afterInitial, store.shiftActuals.getExpectation(write.shift.id))

        assertTrue(store.shiftActuals.save(reclassification) is ShiftActualWriteResult.Saved)
        assertEquals(
            reclassifiedClass.name,
            store.shiftActuals.getExpectation(write.shift.id)
                ?.previousActual
                ?.extraIntervals
                ?.single()
                ?.classNameSnapshot,
        )
    }

    @Test
    fun inlineClassCreationRollsBackWhenActualRecordInsertFails() = runBlocking {
        val write = seedShift(V2TestIds.uuid(230))
        val expectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofHours(1)),
                    differenceReason = "Extensión ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.NewDraft(
                        V2TestIds.uuid(231),
                        "Clase inline ficticia",
                        helpsMeetHoursReference = true,
                        showDedicatedSummary = false,
                    ),
                    fragments = listOf(
                        ShiftActualFragmentDraft(
                            V2TestIds.uuid(232),
                            write.shift.endAt,
                            write.shift.endAt.plus(Duration.ofHours(1)),
                        ),
                    ),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_actual_record_rollback
                BEFORE INSERT ON shift_actual_records
                WHEN NEW.shiftId = '${write.shift.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced actual record failure');
                END""",
        )

        assertEquals(ShiftActualWriteResult.Conflict, store.shiftActuals.save(mutation))

        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)
        assertEquals(
            emptyList<ExtraWorkClass>(),
            store.shiftActuals
                .observeExtraWorkClasses(V2TestIds.TIMELINE, write.snapshot.sector)
                .take(1)
                .toList()
                .single(),
        )
    }

    @Test
    fun expectationFlowReactsAndStructuralWritersRequireExactActualEvidence() = runBlocking {
        val write = seedShift(V2TestIds.uuid(231))
        val observed = async {
            withTimeout(5_000) { store.shiftActuals.observeExpectation(write.shift.id).take(2).toList() }
        }
        yield()
        val expectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofMinutes(30)),
                    differenceReason = "Extensión habitual",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.ALL_REGULAR,
                    classSelection = null,
                    fragments = emptyList(),
                ),
                clock = clockAfter(write),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )
        store.shiftActuals.save(mutation)
        val emissions = observed.await()
        assertNull(requireNotNull(emissions.first()).previousActual)
        assertEquals(510L, requireNotNull(requireNotNull(emissions.last()).previousActual).totalMinutes)

        val firstActual = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val protectionBefore = store.recurringPlans.captureProtection(
            setOf(write.shift.id),
            write.shift.localStartDate,
            write.shift.localStartDate,
        )
        val correctedMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = firstActual,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofMinutes(45)),
                    differenceReason = "Extensión habitual corregida",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.ALL_REGULAR,
                    classSelection = null,
                    fragments = emptyList(),
                ),
                clock = clockAfter(write),
                timestamp = requireNotNull(firstActual.previousActual).record.updatedAt.plusMillis(1),
            ),
        )
        store.shiftActuals.save(correctedMutation)
        val protectionAfter = store.recurringPlans.captureProtection(
            setOf(write.shift.id),
            write.shift.localStartDate,
            write.shift.localStartDate,
        )
        assertNotEquals(protectionBefore, protectionAfter)
        assertNotEquals(
            protectionBefore.versionsByShiftId.getValue(write.shift.id).actualFingerprint,
            protectionAfter.versionsByShiftId.getValue(write.shift.id).actualFingerprint,
        )

        val withActual = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertSuspendThrows<ConflictingLocalWriteException> { store.v2Shifts.deleteShift(write) }
        assertTrue(store.v2Shifts.getShift(write.shift.id) is V2ShiftLookup.V2)

        val positionOnly = editV2ShiftPositionOnly(
            original = write,
            position = "Puesto corregido",
            updatedAt = write.shift.updatedAt.plusMillis(1),
        )
        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(
                shiftsToUpdate = listOf(positionOnly),
                actualExpectations = mapOf(write.shift.id to withActual),
            ),
            expectedOccupancy = occupancyOf(write),
            expectedUpdates = V2ShiftWriteExpectation.capture(listOf(write)),
        )
        assertEquals(withActual.previousActual, store.shiftActuals.getExpectation(write.shift.id)?.previousActual)

        val current = (store.v2Shifts.getShift(write.shift.id) as V2ShiftLookup.V2).write
        val currentActual = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        val changedPlan = current.copy(
            shift = current.shift.copy(
                endAt = current.shift.endAt.plus(Duration.ofHours(1)),
                endTimeSnapshot = current.shift.endTimeSnapshot.plusHours(1),
                updatedAt = current.shift.updatedAt.plusMillis(1),
            ),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(
                    shiftsToUpdate = listOf(changedPlan),
                    actualExpectations = mapOf(current.shift.id to currentActual),
                ),
                expectedOccupancy = occupancyOf(current),
                expectedUpdates = V2ShiftWriteExpectation.capture(listOf(current)),
            )
        }
        assertEquals(current, (store.v2Shifts.getShift(write.shift.id) as V2ShiftLookup.V2).write)

        store.v2Shifts.deleteShift(current, currentActual)
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(write.shift.id))
        assertNull(database.shiftActualDao().getRecord(write.shift.id.toString()))
        assertTrue(database.shiftActualDao().getIntervals(write.shift.id.toString()).isEmpty())
    }

    @Test
    fun foreignKeysRejectOrphansAndRawInvalidAggregateIsRejectedWithoutSilentFallback() = runBlocking {
        val write = seedShift(V2TestIds.uuid(241))
        val crossedClass = ExtraWorkClass.create(
            id = V2TestIds.uuid(242),
            timelineId = V2TestIds.TIMELINE,
            sector = com.blackatsystems.miguardia.core.domain.work.WorkSector.MEDICINE,
            name = "Clase cruzada",
            helpsMeetHoursReference = false,
            showDedicatedSummary = false,
            timestamp = V2TestIds.NOW.plusSeconds(1),
        )
        assertSuspendThrows<IllegalArgumentException> {
            store.shiftActuals.saveExtraWorkClass(null, crossedClass)
        }
        val db = database.openHelper.writableDatabase
        assertThrows<android.database.sqlite.SQLiteConstraintException> {
            db.execSQL(
                "INSERT INTO shift_actual_records VALUES " +
                    "('missing-shift', '${V2TestIds.TIMELINE}', 'PRIVATE_SECURITY', 1, 2, 'Motivo', NULL, 1, 1)",
            )
        }
        db.execSQL(
            "INSERT INTO shift_actual_records VALUES " +
                "('${write.shift.id}', '${V2TestIds.TIMELINE}', 'PRIVATE_SECURITY', " +
                "${write.shift.startAt.toEpochMilli()}, ${write.shift.endAt.toEpochMilli()}, 'Motivo', NULL, 1, 1)",
        )
        assertSuspendThrows<InvalidLocalDataException> { store.shiftActuals.getExpectation(write.shift.id) }
        assertEquals(1, database.shiftActualDao().getAllRecords().size)
    }

    private suspend fun seedShift(id: UUID): V2ShiftWrite {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, id, V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        return write
    }

    private fun createClass(id: UUID, name: String): ExtraWorkClass = ExtraWorkClass.create(
        id = id,
        timelineId = V2TestIds.TIMELINE,
        sector = com.blackatsystems.miguardia.core.domain.work.WorkSector.PRIVATE_SECURITY,
        name = name,
        helpsMeetHoursReference = false,
        showDedicatedSummary = true,
        timestamp = V2TestIds.NOW.plusSeconds(1),
    )

    private fun clockAfter(write: V2ShiftWrite): Clock = Clock.fixed(
        write.shift.endAt.plus(Duration.ofHours(4)),
        ZoneOffset.UTC,
    )

    private fun occupancyOf(write: V2ShiftWrite): ShiftOccupancyExpectation =
        ShiftOccupancyExpectation.capture(
            write.shift.localStartDate,
            write.shift.localStartDate,
            listOf(write.shift),
        )

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

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val DB = "shift-actual-persistence-test.db"
    }
}
