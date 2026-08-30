package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRequest
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.summary.calculateMonthlySummary
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthlyReportSnapshotInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        openStore()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun captureReadsTheMonthInsideOneTransactionWithoutChangingAnyOfTheTwentySevenTables() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(18_001), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        val beforeCounts = tableCounts()
        val beforeChanges = totalChanges()

        val snapshot = store.monthlyReportSnapshots.capture(request())

        assertEquals(YearMonth.of(2026, 8), snapshot.summaryInput.month)
        assertEquals(listOf(write.shift.id), snapshot.summaryInput.shifts.map { it.shift.id })
        assertTrue(V2TestIds.SHIFT_DATE in snapshot.captureRange)
        assertEquals(beforeCounts, tableCounts())
        assertEquals(27, beforeCounts.size)
        assertEquals(beforeChanges, totalChanges())
    }

    @Test
    fun concurrentTwoTableWriteCanOnlyProduceTheCompletePreviousOrCompleteNextState() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(18_002), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        val previous = write.shift.objectiveNameSnapshot to write.snapshot.workTypeNameSnapshot
        val next = "Objetivo posterior" to "Tipo posterior"
        val readerInsideTransaction = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val shouldBlockReader = AtomicBoolean(true)
        val readerDatabase = Room.databaseBuilder(
            context.applicationContext,
            MiGuardiaV2Database::class.java,
            DATABASE_NAME,
        ).addMigrations(
            MiGuardiaV2Database.MIGRATION_1_2,
            MiGuardiaV2Database.MIGRATION_2_3,
            MiGuardiaV2Database.MIGRATION_3_4,
            MiGuardiaV2Database.MIGRATION_4_5,
        ).setQueryCallback(
            { sql, _ ->
                if (
                    sql.startsWith("SELECT", ignoreCase = true) &&
                    sql.contains("shift_actual_records", ignoreCase = true) &&
                    shouldBlockReader.compareAndSet(true, false)
                ) {
                    readerInsideTransaction.countDown()
                    check(releaseReader.await(5, TimeUnit.SECONDS))
                }
            },
            Executor { command -> command.run() },
        ).build()
        val readerStore = LocalDataStore(readerDatabase)

        try {
            val reader = async(Dispatchers.IO) { readerStore.monthlyReportSnapshots.capture(request()) }
            assertTrue(readerInsideTransaction.await(5, TimeUnit.SECONDS))
            val writerStarted = CompletableDeferred<Unit>()
            val writer = async(Dispatchers.IO) {
                writerStarted.complete(Unit)
                database.withTransaction {
                    database.openHelper.writableDatabase.execSQL(
                        "UPDATE shifts SET objectiveNameSnapshot = ? WHERE id = ?",
                        arrayOf(next.first, write.shift.id.toString()),
                    )
                    database.openHelper.writableDatabase.execSQL(
                        "UPDATE shift_work_snapshots SET workTypeNameSnapshot = ? WHERE shiftId = ?",
                        arrayOf(next.second, write.shift.id.toString()),
                    )
                }
            }
            writerStarted.await()
            releaseReader.countDown()
            val concurrent = withTimeout(5_000) { reader.await() }.summaryInput.shifts.single()
            withTimeout(5_000) { writer.await() }

            assertEquals(
                previous,
                concurrent.shift.objectiveNameSnapshot to concurrent.snapshot.workTypeNameSnapshot,
            )

            val after = readerStore.monthlyReportSnapshots.capture(request()).summaryInput.shifts.single()
            assertEquals(next, after.shift.objectiveNameSnapshot to after.snapshot.workTypeNameSnapshot)
        } finally {
            releaseReader.countDown()
            readerStore.close()
        }
    }

    @Test
    fun missingSelectedPhotoFailsWithoutRowsOrChangesAndTheDatabaseCanBeReopened() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(18_003), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        val beforeCounts = tableCounts()
        val beforeChanges = totalChanges()

        val failure = runCatching {
            store.monthlyReportSnapshots.capture(
                request(selectedPhotoIds = setOf(V2TestIds.uuid(18_099))),
            )
        }.exceptionOrNull()

        assertTrue(failure is InvalidLocalDataException)
        assertEquals(beforeCounts, tableCounts())
        assertEquals(beforeChanges, totalChanges())

        database.close()
        openStore()
        val reopened = store.monthlyReportSnapshots.capture(request())
        assertEquals(listOf(write.shift.id), reopened.summaryInput.shifts.map { it.shift.id })
        assertEquals(beforeCounts, tableCounts())
    }

    @Test
    fun reportMonthAvailabilityKeepsActiveWorkThatStartedInThePreviousMonth() = runBlocking {
        val fixture = store.seedV2Catalog()
        enableAvailability()
        val shiftId = V2TestIds.uuid(18_004)
        val overnight = fixture.template.copy(
            id = V2TestIds.uuid(18_012),
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(2, 0),
            createdAt = V2TestIds.NOW.plusSeconds(1),
            updatedAt = V2TestIds.NOW.plusSeconds(1),
        )
        store.workCatalog.createWorkTemplate(overnight)
        val crossingShift = store.buildTestV2Write(
            fixture.copy(template = overnight),
            shiftId,
            LocalDate.of(2026, 7, 31),
        )
        store.v2Shifts.insert(crossingShift)
        val owner = LocalDate.of(2026, 8, 1)
        val configuration = ResolvedWorkConfigurationRevision.resolve(
            requireNotNull(store.workConfiguration.get()),
            owner,
        )
        val availabilityId = V2TestIds.uuid(18_005)
        val availabilityStart = Instant.parse("2026-08-01T03:00:00Z")
        val availabilityEnd = Instant.parse("2026-08-01T06:00:00Z")
        val availabilityRecord = buildAvailabilityWindowRecord(
            AvailabilityWindowDraft(
                availabilityId,
                owner,
                V2TestIds.ZONE,
                availabilityStart,
                availabilityEnd,
            ),
            configuration,
            V2TestIds.NOW.plusSeconds(120),
            previous = null,
        )
        val availabilityResult = store.availabilityWindows.applyMutation(
            AvailabilityWindowMutation(
                store.availabilityWindows.captureExpectation(
                    id = null,
                    configuration = configuration,
                    windowStart = availabilityStart,
                    windowEnd = availabilityEnd,
                ),
                availabilityRecord,
            ),
        )
        assertTrue(availabilityResult is AvailabilityWindowWriteResult.Saved)

        val snapshot = store.monthlyReportSnapshots.capture(request())
        val summary = calculateMonthlySummary(
            snapshot.summaryInput,
            Clock.fixed(Instant.parse("2026-08-02T12:00:00Z"), V2TestIds.ZONE),
            V2TestIds.ZONE,
        )

        assertTrue(crossingShift.shift.id in snapshot.summaryInput.shifts.map { it.shift.id })
        assertEquals(120L, requireNotNull(summary.availability?.replacedElapsed).value)
    }

    @Test
    fun extendedComplianceRangeNeverCapturesPrivateNotesFromNeighboringMonths() = runBlocking {
        val fixture = store.seedV2Catalog()
        enableWeeklyReference()
        val neighbor = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(18_006),
            LocalDate.of(2026, 7, 30),
        )
        val inMonth = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(18_007),
            LocalDate.of(2026, 8, 2),
        )
        store.v2Shifts.insert(neighbor)
        store.v2Shifts.insert(inMonth)
        store.shiftNotes.insert(
            ShiftNote(V2TestIds.uuid(18_008), neighbor.shift.id, "Nota vecina privada", V2TestIds.NOW, V2TestIds.NOW),
        )
        store.shiftNotes.insert(
            ShiftNote(V2TestIds.uuid(18_009), inMonth.shift.id, "Nota mensual elegida", V2TestIds.NOW, V2TestIds.NOW),
        )
        val neighboringMedicalId = V2TestIds.uuid(18_010)
        val monthlyMedicalId = V2TestIds.uuid(18_011)
        store.medicalLeaves.create(
            MedicalLeave(
                neighboringMedicalId,
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 7, 31),
                "Nota médica vecina privada",
                V2TestIds.NOW,
                V2TestIds.NOW,
            ),
        )
        store.medicalLeaves.create(
            MedicalLeave(
                monthlyMedicalId,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 3),
                "Nota médica mensual elegida",
                V2TestIds.NOW,
                V2TestIds.NOW,
            ),
        )

        val snapshot = store.monthlyReportSnapshots.capture(
            request(includeShiftNotes = true, includeMedicalNotes = true),
        )

        assertEquals(listOf(inMonth.shift.id), snapshot.shiftNotes.map { it.shiftId })
        assertEquals(
            null,
            snapshot.summaryInput.medicalLeaves.single { it.id == neighboringMedicalId }.privateNote,
        )
        assertEquals(
            "Nota médica mensual elegida",
            snapshot.summaryInput.medicalLeaves.single { it.id == monthlyMedicalId }.privateNote,
        )
    }

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DATABASE_NAME)
        store = LocalDataStore(database)
    }

    private fun request(
        selectedPhotoIds: Set<UUID> = emptySet(),
        includeShiftNotes: Boolean = false,
        includeMedicalNotes: Boolean = false,
    ): MonthlyReportSnapshotRequest =
        MonthlyReportSnapshotRequest(
            month = YearMonth.of(2026, 8),
            includeShiftNotes = includeShiftNotes,
            includeMedicalNotes = includeMedicalNotes,
            selectedPhotoIds = selectedPhotoIds,
        )

    private suspend fun enableAvailability() {
        val history = requireNotNull(store.workConfiguration.get())
        val current = history.timeline.revisions.single()
        val result = store.workConfiguration.applyAvailabilityMutation(
            WorkConfigurationAvailabilityMutation(
                expectedHistory = history,
                revision = EffectiveRevision(
                    id = current.id,
                    effectiveFrom = current.effectiveFrom,
                    value = current.value.copy(availabilityLabel = AvailabilityLabel.PASSIVE_GUARD),
                ),
            ),
        )
        assertTrue(result is WorkConfigurationAvailabilityWriteResult.Saved)
    }

    private suspend fun enableWeeklyReference() {
        val history = requireNotNull(store.workConfiguration.get())
        val current = history.timeline.revisions.single()
        val result = store.workConfiguration.applyReferenceMutation(
            WorkConfigurationReferenceMutation(
                expectedHistory = history,
                revision = EffectiveRevision(
                    id = current.id,
                    effectiveFrom = current.effectiveFrom,
                    value = current.value.copy(
                        hoursReference = HoursReference.Fixed(
                            HoursPeriod.Weekly(DayOfWeek.MONDAY),
                            PositiveMinutes(2_400),
                        ),
                        hoursReferenceStartedOn = current.effectiveFrom,
                    ),
                ),
            ),
        )
        assertTrue(result is WorkConfigurationReferenceWriteResult.Saved)
    }

    private fun tableCounts(): Map<String, Long> {
        val sqlite = database.openHelper.writableDatabase
        val tables = buildList {
            sqlite.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') " +
                    "ORDER BY name",
            ).use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.associateWith { table ->
            sqlite.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
        }
    }

    private fun totalChanges(): Long = database.openHelper.writableDatabase
        .query("SELECT total_changes()")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val DATABASE_NAME: String = "monthly-report-snapshot-instrumented.db"
    }
}
