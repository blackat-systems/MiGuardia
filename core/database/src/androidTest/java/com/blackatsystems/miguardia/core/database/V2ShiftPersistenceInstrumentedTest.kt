package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.toOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.LegacyShiftCannotBeUpdatedAsV2Exception
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
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
class V2ShiftPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaDatabase
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        openStore()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun shiftAndSnapshotInsertUpdateReopenAndCascadeAcrossCivilMonthBoundary() = runBlocking {
        val fixture = createCatalog()
        store.workCatalog.addWorkplaceRuleRevision(
            rule(
                id = uuid(101),
                place = fixture.place,
                effectiveFrom = LocalDate.of(2026, 2, 1),
                rules = rules(holidayDifferentTreatment = true),
            ),
            confirmationNow = FIXED_INSTANT,
        )
        val date = LocalDate.of(2026, 1, 31)
        val contextAtDate = resolvedConfigurationAt(date)
        val original = write(
            id = uuid(102),
            date = date,
            fixture = fixture,
            configuration = contextAtDate,
            createdAt = FIXED_INSTANT.plusSeconds(10),
        )

        store.v2Shifts.insert(original)
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))

        store.close()
        openStore()
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))

        val updated = editV2ShiftWrite(
            original = original,
            date = date,
            objective = fixture.objective,
            workPlace = fixture.place,
            workType = fixture.type,
            template = fixture.template,
            configurationContext = resolvedConfigurationAt(date),
            position = "Sector ficticio actualizado",
            updatedAt = original.shift.updatedAt.plusSeconds(30),
        )
        store.v2Shifts.update(updated)
        assertEquals(updated.shift, store.shifts.getById(updated.shift.id))
        assertEquals(updated.snapshot, store.v2Shifts.observeWorkSnapshot(updated.shift.id).first())

        store.shifts.delete(updated.shift.id)
        assertNull(store.shifts.getById(updated.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(updated.shift.id))
    }

    @Test
    fun legacyUpdateApisCannotMutateOnlyTheShiftHalfOfAV2Pair() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 6)
        val original = write(
            id = uuid(151),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val legacyToPreserve = write(
            id = uuid(152),
            date = date.plusDays(1),
            fixture = fixture,
            configuration = resolvedConfigurationAt(date.plusDays(1)),
            createdAt = FIXED_INSTANT.plusSeconds(2),
        ).shift
        val changedShiftHalf = original.shift.copy(
            position = "Cambio incompleto rechazado",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )
        store.v2Shifts.insert(original)
        store.shifts.insert(legacyToPreserve)

        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.update(changedShiftHalf)
        }
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))

        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.applyBatch(
                ShiftBatchMutation(
                    shiftIdsToDelete = setOf(legacyToPreserve.id),
                    shiftsToUpdate = listOf(changedShiftHalf),
                ),
            )
        }
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
        assertEquals(legacyToPreserve, store.shifts.getById(legacyToPreserve.id))
    }

    @Test
    fun statusNoveltyKeepsV2StructureAndRejectsForgedPayloads() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 7)
        val original = write(
            id = uuid(161),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        store.v2Shifts.insert(original)
        val absence = ShiftNovelty(
            id = uuid(162),
            shiftId = original.shift.id,
            type = ShiftNoveltyType.ABSENCE,
            description = null,
            relatedShiftId = null,
            createdAt = FIXED_INSTANT.plusSeconds(2),
            updatedAt = FIXED_INSTANT.plusSeconds(2),
        )
        val forged = original.shift.copy(
            status = ShiftStatus.ABSENT,
            position = "Cambio estructural oculto",
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftNovelties.applyMutation(
                ShiftNoveltyMutation.ChangeStatus(forged, absence),
            )
        }
        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
        assertTrue(store.shiftNovelties.observeForShift(original.shift.id).first().isEmpty())

        val absent = original.shift.copy(
            status = ShiftStatus.ABSENT,
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )
        store.shiftNovelties.applyMutation(
            ShiftNoveltyMutation.ChangeStatus(absent, absence),
        )
        assertEquals(absent, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
        assertEquals(
            ShiftNoveltyType.ABSENCE,
            store.shiftNovelties.observeForShift(original.shift.id).first().single().type,
        )
    }

    @Test
    fun structuralNoveltyWritersRejectV2SourceShifts() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 8)
        val original = write(
            id = uuid(171),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        store.v2Shifts.insert(original)
        val structurallyChanged = original.shift.copy(
            endAt = original.shift.endAt.plusSeconds(3_600),
            endTimeSnapshot = original.shift.endTimeSnapshot.plusHours(1),
            updatedAt = original.shift.updatedAt.plusSeconds(1),
        )
        val formalChange = FormalShiftChange(
            id = uuid(172),
            shiftId = original.shift.id,
            scheduleChanged = true,
            objectiveChanged = false,
            description = null,
            original = original.shift.toOperationalSnapshot(),
            final = structurallyChanged.toOperationalSnapshot(),
            createdAt = FIXED_INSTANT.plusSeconds(2),
            updatedAt = FIXED_INSTANT.plusSeconds(2),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftNovelties.applyMutation(
                ShiftNoveltyMutation.ApplyFormalChange(structurallyChanged, formalChange),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftNovelties.applyMutation(
                ShiftNoveltyMutation.RestoreOriginalPlan(
                    restoredShift = original.shift,
                    expectedFinal = original.shift.toOperationalSnapshot(),
                ),
            )
        }

        val second = original.shift.copy(
            id = uuid(173),
            createdAt = FIXED_INSTANT.plusSeconds(3),
            updatedAt = FIXED_INSTANT.plusSeconds(3),
        )
        val secondLink = ShiftNovelty(
            id = uuid(174),
            shiftId = original.shift.id,
            type = ShiftNoveltyType.SECOND_SHIFT,
            description = null,
            relatedShiftId = second.id,
            createdAt = FIXED_INSTANT.plusSeconds(3),
            updatedAt = FIXED_INSTANT.plusSeconds(3),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftNovelties.applyMutation(
                ShiftNoveltyMutation.CreateSecondShift(secondLink, second),
            )
        }

        assertEquals(original.shift, store.shifts.getById(original.shift.id))
        assertEquals(original.snapshot, store.v2Shifts.getWorkSnapshot(original.shift.id))
        assertNull(store.shifts.getById(second.id))
        assertNull(store.shiftNovelties.observeFormalChange(original.shift.id).first())
    }

    @Test
    fun legacyShiftCannotBeUpdatedAndExactConfigurationRevisionIsRequired() = runBlocking {
        val fixture = createCatalog()
        val legacyCandidate = write(
            id = uuid(201),
            date = LocalDate.of(2026, 1, 10),
            fixture = fixture,
            configuration = resolvedConfigurationAt(LocalDate.of(2026, 1, 10)),
            createdAt = FIXED_INSTANT.plusSeconds(10),
        )
        store.shifts.insert(legacyCandidate.shift)

        assertSuspendThrows<LegacyShiftCannotBeUpdatedAsV2Exception> {
            store.v2Shifts.update(
                legacyCandidate.copy(
                    shift = legacyCandidate.shift.copy(
                        position = "Edición V2 rechazada",
                        updatedAt = legacyCandidate.shift.updatedAt.plusSeconds(1),
                    ),
                ),
            )
        }
        assertNull(store.v2Shifts.getWorkSnapshot(legacyCandidate.shift.id))

        val februaryRevision = configurationRevision(
            id = uuid(202),
            effectiveFrom = LocalDate.of(2026, 2, 1),
        )
        store.workConfiguration.addRevision(TIMELINE_ID, februaryRevision)
        val februaryDate = LocalDate.of(2026, 2, 2)
        val exactWrite = write(
            id = uuid(203),
            date = februaryDate,
            fixture = fixture,
            configuration = resolvedConfigurationAt(februaryDate),
            createdAt = FIXED_INSTANT.plusSeconds(20),
        )
        val staleRevisionWrite = exactWrite.copy(
            snapshot = exactWrite.snapshot.copy(configurationRevisionId = INITIAL_REVISION_ID),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.insert(staleRevisionWrite)
        }
        assertNull(store.shifts.getById(exactWrite.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(exactWrite.shift.id))

        store.v2Shifts.insert(exactWrite)
        assertEquals(februaryRevision.id, store.v2Shifts.getWorkSnapshot(exactWrite.shift.id)?.configurationRevisionId)
    }

    @Test
    fun laterIntermediateRevisionDoesNotRewriteStoredConfigurationSnapshot() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 10)
        val historical = write(
            id = uuid(221),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        store.v2Shifts.insert(historical)

        val intermediate = EffectiveRevision(
            id = uuid(222),
            effectiveFrom = LocalDate.of(2026, 1, 5),
            value = WorkConfiguration(
                sector = SECTOR,
                hoursReference = HoursReference.PendingSetup,
                availabilityLabel = AvailabilityLabel.AVAILABLE_FOR_CALL,
            ),
        )
        store.workConfiguration.addRevision(TIMELINE_ID, intermediate)

        assertEquals(
            INITIAL_REVISION_ID,
            store.v2Shifts.getWorkSnapshot(historical.shift.id)?.configurationRevisionId,
        )
        val current = write(
            id = uuid(223),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(2),
        )
        store.v2Shifts.insert(current)
        assertEquals(
            intermediate.id,
            store.v2Shifts.getWorkSnapshot(current.shift.id)?.configurationRevisionId,
        )
    }

    @Test
    fun recentTemplatesUseCreatedAtUuidTieBreakAndBoundedLimit() = runBlocking {
        val fixture = createCatalog()
        val second = addTypeAndTemplate(
            fixture = fixture,
            typeId = uuid(302),
            templateId = uuid(303),
            name = "Consultorio",
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(14, 0),
        )
        val third = addTypeAndTemplate(
            fixture = fixture,
            typeId = uuid(304),
            templateId = uuid(305),
            name = "Internación",
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(20, 0),
        )
        val history = requireNotNull(store.workConfiguration.get())
        val firstWrite = write(
            id = uuid(306),
            date = LocalDate.of(2026, 1, 30),
            fixture = fixture,
            configuration = ResolvedWorkConfigurationRevision.resolve(history, LocalDate.of(2026, 1, 30)),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val secondWrite = write(
            id = uuid(307),
            date = LocalDate.of(2026, 1, 2),
            fixture = fixture.copy(type = second.first, template = second.second),
            configuration = ResolvedWorkConfigurationRevision.resolve(history, LocalDate.of(2026, 1, 2)),
            createdAt = FIXED_INSTANT.plusSeconds(3),
        )
        val thirdWrite = write(
            id = uuid(308),
            date = LocalDate.of(2026, 1, 3),
            fixture = fixture.copy(type = third.first, template = third.second),
            configuration = ResolvedWorkConfigurationRevision.resolve(history, LocalDate.of(2026, 1, 3)),
            createdAt = FIXED_INSTANT.plusSeconds(3),
        )
        store.v2Shifts.insert(firstWrite)
        store.v2Shifts.insert(thirdWrite)
        store.v2Shifts.insert(secondWrite)

        val recent = store.workCatalog.observeRecentlyUsed(TIMELINE_ID, SECTOR, limit = 2).first()

        assertEquals(listOf(second.second.id, third.second.id), recent.map { it.template.id })
        assertEquals(
            listOf(FIXED_INSTANT.plusSeconds(3), FIXED_INSTANT.plusSeconds(3)),
            recent.map { it.lastUsedAt },
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeRecentlyUsed(TIMELINE_ID, SECTOR, limit = 0)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeRecentlyUsed(TIMELINE_ID, SECTOR, limit = 6)
        }
        Unit
    }

    @Test
    fun batchFailureAfterDeletionRollsBackShiftAndOwnedSnapshot() = runBlocking {
        val fixture = createCatalog()
        val existing = write(
            id = uuid(401),
            date = LocalDate.of(2026, 1, 5),
            fixture = fixture,
            configuration = resolvedConfigurationAt(LocalDate.of(2026, 1, 5)),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val replacement = write(
            id = uuid(402),
            date = LocalDate.of(2026, 1, 5),
            fixture = fixture,
            configuration = resolvedConfigurationAt(LocalDate.of(2026, 1, 5)),
            createdAt = FIXED_INSTANT.plusSeconds(2),
        )
        store.v2Shifts.insert(existing)
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_replacement_snapshot
                BEFORE INSERT ON shift_work_snapshots
                WHEN NEW.shiftId = '${replacement.shift.id}'
                BEGIN SELECT RAISE(ABORT, 'forced snapshot failure'); END""".trimIndent(),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                V2ShiftBatchMutation(
                    shiftIdsToDelete = setOf(existing.shift.id),
                    shiftsToInsert = listOf(replacement),
                ),
            )
        }

        assertEquals(existing.shift, store.shifts.getById(existing.shift.id))
        assertEquals(existing.snapshot, store.v2Shifts.getWorkSnapshot(existing.shift.id))
        assertNull(store.shifts.getById(replacement.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(replacement.shift.id))
    }

    @Test
    fun batchReplacesLegacyAndV2OnlyOnDatesItWrites() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 7)
        val configuration = resolvedConfigurationAt(date)
        val legacy = write(
            id = uuid(411),
            date = date,
            fixture = fixture,
            configuration = configuration,
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val previousV2 = write(
            id = uuid(412),
            date = date,
            fixture = fixture,
            configuration = configuration,
            createdAt = FIXED_INSTANT.plusSeconds(2),
        )
        val unrelated = write(
            id = uuid(413),
            date = date.plusDays(1),
            fixture = fixture,
            configuration = resolvedConfigurationAt(date.plusDays(1)),
            createdAt = FIXED_INSTANT.plusSeconds(3),
        )
        val replacement = write(
            id = uuid(414),
            date = date,
            fixture = fixture,
            configuration = configuration,
            createdAt = FIXED_INSTANT.plusSeconds(4),
        )
        store.shifts.insert(legacy.shift)
        store.v2Shifts.insert(previousV2)
        store.v2Shifts.insert(unrelated)

        store.v2Shifts.applyV2Batch(
            V2ShiftBatchMutation(
                shiftIdsToDelete = setOf(legacy.shift.id, previousV2.shift.id),
                shiftsToInsert = listOf(replacement),
                explicitDayStatusDatesToClear = setOf(date),
            ),
        )

        assertNull(store.shifts.getById(legacy.shift.id))
        assertNull(store.shifts.getById(previousV2.shift.id))
        assertEquals(replacement.shift, store.shifts.getById(replacement.shift.id))
        assertEquals(replacement.snapshot, store.v2Shifts.getWorkSnapshot(replacement.shift.id))

        val rejected = write(
            id = uuid(415),
            date = date.plusDays(2),
            fixture = fixture,
            configuration = resolvedConfigurationAt(date.plusDays(2)),
            createdAt = FIXED_INSTANT.plusSeconds(5),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                V2ShiftBatchMutation(
                    shiftIdsToDelete = setOf(unrelated.shift.id),
                    shiftsToInsert = listOf(rejected),
                ),
            )
        }
        assertEquals(unrelated.shift, store.shifts.getById(unrelated.shift.id))
        assertNull(store.shifts.getById(rejected.shift.id))

        val clearRejected = write(
            id = uuid(416),
            date = date.plusDays(3),
            fixture = fixture,
            configuration = resolvedConfigurationAt(date.plusDays(3)),
            createdAt = FIXED_INSTANT.plusSeconds(6),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.applyV2Batch(
                V2ShiftBatchMutation(
                    shiftsToInsert = listOf(clearRejected),
                    explicitDayStatusDatesToClear = setOf(date.plusDays(4)),
                ),
            )
        }
        assertNull(store.shifts.getById(clearRejected.shift.id))
        Unit
    }

    @Test
    fun batchCanReplaceHistoricalV2ShiftWhenItsDateChangesSector() = runBlocking {
        val medicine = createCatalog()
        val transitionDate = LocalDate.of(2026, 2, 1)
        val historicalMedicine = write(
            id = uuid(431),
            date = transitionDate,
            fixture = medicine,
            configuration = resolvedConfigurationAt(transitionDate),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        store.v2Shifts.insert(historicalMedicine)

        val nursingRevision = EffectiveRevision(
            id = uuid(432),
            effectiveFrom = transitionDate,
            value = WorkConfiguration(
                sector = WorkSector.NURSING,
                hoursReference = HoursReference.PendingSetup,
                availabilityLabel = null,
            ),
        )
        store.workConfiguration.addRevision(TIMELINE_ID, nursingRevision)
        val nursingConfiguration = resolvedConfigurationAt(transitionDate)
        val nursingObjective = objective(uuid(433)).copy(
            fullName = "Hospital ficticio",
            abbreviation = "HOS",
        )
        val nursingPlace = workPlace(uuid(434), nursingObjective.id).copy(
            sector = WorkSector.NURSING,
        )
        val nursingType = workType(uuid(435), "Guardia de enfermería").copy(
            sector = WorkSector.NURSING,
        )
        val nursingTemplate = template(uuid(436), nursingPlace, nursingType).copy(
            sector = WorkSector.NURSING,
        )
        val nursing = CatalogFixture(
            objective = nursingObjective,
            place = nursingPlace,
            type = nursingType,
            template = nursingTemplate,
        )
        store.workCatalog.createFirstWorkSet(
            FirstWorkSet(
                objective = nursingObjective,
                workPlace = nursingPlace,
                firstRuleRevision = rule(uuid(437), nursingPlace, transitionDate).copy(
                    sector = WorkSector.NURSING,
                ),
                configurationContext = nursingConfiguration,
                workType = nursingType,
                workTemplate = nursingTemplate,
            ),
        )
        val replacement = write(
            id = uuid(438),
            date = transitionDate,
            fixture = nursing,
            configuration = nursingConfiguration,
            createdAt = FIXED_INSTANT.plusSeconds(2),
        )

        store.v2Shifts.applyV2Batch(
            V2ShiftBatchMutation(
                shiftIdsToDelete = setOf(historicalMedicine.shift.id),
                shiftsToInsert = listOf(replacement),
            ),
        )

        assertNull(store.shifts.getById(historicalMedicine.shift.id))
        assertNull(store.v2Shifts.getWorkSnapshot(historicalMedicine.shift.id))
        assertEquals(replacement.shift, store.shifts.getById(replacement.shift.id))
        assertEquals(replacement.snapshot, store.v2Shifts.getWorkSnapshot(replacement.shift.id))
    }

    @Test
    fun externallyCorruptedSnapshotIsReportedAsInvalidLocalData() = runBlocking {
        val fixture = createCatalog()
        val write = write(
            id = uuid(501),
            date = LocalDate.of(2026, 1, 8),
            fixture = fixture,
            configuration = resolvedConfigurationAt(LocalDate.of(2026, 1, 8)),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val rejected = write(
            id = uuid(502),
            date = LocalDate.of(2026, 1, 9),
            fixture = fixture,
            configuration = resolvedConfigurationAt(LocalDate.of(2026, 1, 9)),
            createdAt = FIXED_INSTANT.plusSeconds(2),
        )
        store.v2Shifts.insert(write)
        store.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                """UPDATE shift_work_snapshots
                    SET workTypeBehaviorSnapshot = 'UNKNOWN_BEHAVIOR'
                    WHERE shiftId = '${write.shift.id}'""".trimIndent(),
            )
        }
        openStore()

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(write.shift.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(TIMELINE_ID, SECTOR).first()
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.insert(rejected)
        }
        assertNull(store.shifts.getById(rejected.shift.id))
        Unit
    }

    @Test
    fun externallyInvalidShiftTimestampsAreRejected() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 12)
        val write = write(
            id = uuid(522),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        store.v2Shifts.insert(write)
        store.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                """UPDATE shifts
                    SET updatedAtEpochMillis = createdAtEpochMillis - 1
                    WHERE id = '${write.shift.id}'""".trimIndent(),
            )
        }
        openStore()

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(write.shift.id)
        }
        Unit
    }

    @Test
    fun realInstantsMustMatchHistoricalDateZoneAndTimes() = runBlocking {
        val fixture = createCatalog()
        val date = LocalDate.of(2026, 1, 13)
        val valid = write(
            id = uuid(511),
            date = date,
            fixture = fixture,
            configuration = resolvedConfigurationAt(date),
            createdAt = FIXED_INSTANT.plusSeconds(1),
        )
        val forged = valid.copy(
            shift = valid.shift.copy(
                id = uuid(512),
                startAt = valid.shift.startAt.plusSeconds(3_600),
                endAt = valid.shift.endAt.plusSeconds(3_600),
            ),
            snapshot = valid.snapshot.copy(shiftId = uuid(512)),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.insert(forged)
        }
        assertNull(store.shifts.getById(forged.shift.id))

        val secondPrecision = valid.copy(
            shift = valid.shift.copy(
                id = uuid(513),
                startAt = valid.shift.startAt.plusSeconds(30),
                endAt = valid.shift.endAt.plusSeconds(30),
                startTimeSnapshot = valid.shift.startTimeSnapshot.plusSeconds(30),
                endTimeSnapshot = valid.shift.endTimeSnapshot.plusSeconds(30),
            ),
            snapshot = valid.snapshot.copy(shiftId = uuid(513)),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.insert(secondPrecision)
        }
        assertNull(store.shifts.getById(secondPrecision.shift.id))

        store.v2Shifts.insert(valid)
        store.close()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                """UPDATE shifts
                    SET startEpochMillis = startEpochMillis + 3600000,
                        endEpochMillis = endEpochMillis + 3600000
                    WHERE id = '${valid.shift.id}'""".trimIndent(),
            )
        }
        openStore()

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(valid.shift.id)
        }
        Unit
    }

    private suspend fun createCatalog(): CatalogFixture {
        val initialRevision = configurationRevision(INITIAL_REVISION_ID, CONFIGURATION_DATE)
        store.workConfiguration.createInitial(TIMELINE_ID, initialRevision)
        val objective = objective(uuid(11))
        val place = workPlace(uuid(12), objective.id)
        val type = workType(uuid(13), "Guardia activa")
        val template = template(uuid(14), place, type)
        store.workCatalog.createFirstWorkSet(
            FirstWorkSet(
                objective = objective,
                workPlace = place,
                firstRuleRevision = rule(uuid(15), place, CONFIGURATION_DATE),
                configurationContext = resolvedConfigurationAt(CONFIGURATION_DATE),
                workType = type,
                workTemplate = template,
            ),
        )
        return CatalogFixture(objective, place, type, template)
    }

    private suspend fun addTypeAndTemplate(
        fixture: CatalogFixture,
        typeId: UUID,
        templateId: UUID,
        name: String,
        startTime: LocalTime,
        endTime: LocalTime,
    ): Pair<WorkType, WorkTemplate> {
        val type = workType(typeId, name)
        val template = template(
            id = templateId,
            place = fixture.place,
            type = type,
            startTime = startTime,
            endTime = endTime,
        )
        store.workCatalog.createWorkType(type)
        store.workCatalog.createWorkTemplate(template)
        return type to template
    }

    private fun write(
        id: UUID,
        date: LocalDate,
        fixture: CatalogFixture,
        configuration: ResolvedWorkConfigurationRevision,
        createdAt: Instant,
    ): V2ShiftWrite = buildV2ShiftWrite(
        id = id,
        date = date,
        objective = fixture.objective,
        workPlace = fixture.place,
        workType = fixture.type,
        template = fixture.template,
        configurationContext = configuration,
        position = "Puesto ficticio",
        timestamp = createdAt,
        zoneId = ZONE,
    )

    private suspend fun resolvedConfigurationAt(date: LocalDate): ResolvedWorkConfigurationRevision =
        ResolvedWorkConfigurationRevision.resolve(
            requireNotNull(store.workConfiguration.get()),
            date,
        )

    private fun configurationRevision(
        id: UUID,
        effectiveFrom: LocalDate,
    ): EffectiveRevision<WorkConfiguration> = EffectiveRevision(
        id = id,
        effectiveFrom = effectiveFrom,
        value = WorkConfiguration(
            sector = SECTOR,
            hoursReference = HoursReference.PendingSetup,
            availabilityLabel = null,
        ),
    )

    private fun objective(id: UUID): Objective = Objective(
        id = id,
        fullName = "Clínica ficticia",
        abbreviation = "CLF",
        address = "Calle Ficticia 200",
        note = null,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun workPlace(id: UUID, objectiveId: UUID): WorkPlace = WorkPlace(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        objectiveId = objectiveId,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun workType(id: UUID, name: String): WorkType = WorkType.create(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        rawName = name,
        timestamp = FIXED_INSTANT,
    )

    private fun template(
        id: UUID,
        place: WorkPlace,
        type: WorkType,
        startTime: LocalTime = LocalTime.of(20, 0),
        endTime: LocalTime = LocalTime.of(8, 0),
    ): WorkTemplate = WorkTemplate(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = place.id,
        objectiveId = place.objectiveId,
        workTypeId = type.id,
        startTime = startTime,
        endTime = endTime,
        colorArgb = COLOR,
        isActive = true,
        legacyScheduleCombinationId = null,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun rule(
        id: UUID,
        place: WorkPlace,
        effectiveFrom: LocalDate,
        rules: WorkplaceRules = rules(),
    ): WorkplaceRuleRevision = WorkplaceRuleRevision(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = place.id,
        objectiveId = place.objectiveId,
        effectiveFrom = effectiveFrom,
        rules = rules,
        createdAt = FIXED_INSTANT,
    )

    private fun rules(holidayDifferentTreatment: Boolean = false): WorkplaceRules = WorkplaceRules(
        nightHours = NightHoursRule.Disabled,
        weekend = WeekendRule.None,
        holiday = HolidayRule(
            differentTreatment = holidayDifferentTreatment,
            showDedicatedSummary = holidayDifferentTreatment,
        ),
    )

    private fun openStore() {
        database = MiGuardiaDatabase.build(context, DATABASE_NAME)
        store = LocalDataStore(database)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T = try {
        block()
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
        error
    }

    private data class CatalogFixture(
        val objective: Objective,
        val place: WorkPlace,
        val type: WorkType,
        val template: WorkTemplate,
    )

    private companion object {
        const val DATABASE_NAME: String = "v2-shift-persistence-test.db"
        val COLOR: Int = 0xFF345678.toInt()
        val TIMELINE_ID: UUID = uuid(1)
        val INITIAL_REVISION_ID: UUID = uuid(2)
        val CONFIGURATION_DATE: LocalDate = LocalDate.of(2026, 1, 1)
        val SECTOR: WorkSector = WorkSector.MEDICINE
        val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")

        fun uuid(suffix: Int): UUID = UUID.fromString(
            "00000000-0000-0000-0000-${suffix.toString().padStart(12, '0')}",
        )
    }
}
