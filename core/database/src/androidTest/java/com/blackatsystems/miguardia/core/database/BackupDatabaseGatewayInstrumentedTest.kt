package com.blackatsystems.miguardia.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupMemoryBudget
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV5
import com.blackatsystems.miguardia.core.domain.backup.estimateDecodedDatabaseBytes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDatabaseGatewayInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "backup-gateway-${UUID.randomUUID()}.db"
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        store = LocalDataStore.create(context, databaseName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun capturesAndReopensExactlyTheTwentySevenLogicalTables() = runBlocking {
        val expected = fullSnapshot()

        store.backups.validateCandidate(expected)
        store.backups.replace(expected)

        assertEquals(expected, store.backups.capture())
        store.backups.verifyLiveAfterReopen(expected)
        assertEquals(27, expected.tables.size)
        assertTrue(expected.tables.all { it.records.size == 1 })
    }

    @Test
    fun roomCaptureUsesTheSharedDecodedMemoryEstimateAtTheExactBoundary() = runBlocking {
        val expected = store.backups.capture()
        val exactLimit = estimateDecodedDatabaseBytes(expected)

        assertEquals(expected, store.backups.capture(exactLimit))
        val error = assertSuspendFails { store.backups.capture(exactLimit - 1L) }
        assertTrue(error is InvalidBackupException)
        assertEquals(exactLimit, BackupMemoryBudget.requireSnapshotFits(expected, exactLimit))
    }

    @Test
    fun failedReplacementRollsBackTheWholeRoomTransaction() = runBlocking {
        val original = emptySnapshot().withObjectives(listOf(objective(OBJECTIVE_ID, "Objetivo Norte", "NOR")))
        store.backups.replace(original)
        val conflicting = emptySnapshot().withObjectives(
            listOf(
                objective(OBJECTIVE_ID, "Objetivo Norte", "NOR"),
                objective(OTHER_OBJECTIVE_ID, "Objetivo duplicado", "NOR"),
            ),
        )

        val error = assertSuspendFails { store.backups.replace(conflicting) }

        assertTrue(error is SQLiteConstraintException)
        assertEquals(original, store.backups.capture())
    }

    @Test
    fun invalidSemanticDateIsRejectedBeforeCommit() = runBlocking {
        val tableName = "explicit_day_statuses"
        val invalid = emptySnapshot().copy(
            tables = emptySnapshot().tables.map { table ->
                if (table.name == tableName) {
                    table.copy(
                        records = listOf(
                            BackupRecord(listOf(BackupValue.Text("2026-02-30"), BackupValue.Text("DAY_OFF"))),
                        ),
                    )
                } else {
                    table
                }
            },
        )

        val error = assertSuspendFails { store.backups.replace(invalid) }

        assertTrue(error is InvalidBackupException)
        assertTrue(store.backups.capture().isEmpty)
    }

    @Test
    fun extremeIsoDatesRoundTripAndInvalidUuidOrEnumNeverCommit() = runBlocking {
        val extremes = emptySnapshot().copy(
            tables = emptySnapshot().tables.map { table ->
                if (table.name == "explicit_day_statuses") {
                    table.copy(
                        records = listOf(
                            row(text(LocalDate.MAX.toString()), text("UNDEFINED")),
                            row(text(LocalDate.MIN.toString()), text("DAY_OFF")),
                        ),
                    )
                } else {
                    table
                }
            },
        )
        store.backups.replace(extremes)
        assertEquals(extremes, store.backups.capture())
        store.backups.replace(emptySnapshot())

        val invalidUuid = emptySnapshot().withObjectives(
            listOf(objective("not-a-uuid", "Objetivo inválido", "INV")),
        )
        val invalidEnum = emptySnapshot().copy(
            tables = emptySnapshot().tables.map { table ->
                if (table.name == "explicit_day_statuses") {
                    table.copy(records = listOf(row(text("2026-08-31"), text("MAYBE"))))
                } else {
                    table
                }
            },
        )

        assertTrue(assertSuspendFails { store.backups.replace(invalidUuid) } is InvalidBackupException)
        assertTrue(assertSuspendFails { store.backups.replace(invalidEnum) } is InvalidBackupException)
        assertTrue(store.backups.capture().isEmpty)
    }

    @Test
    fun overlappingMedicalLeaveAndVacationAreRejectedInTheIsolatedCandidate() = runBlocking {
        val invalid = fullSnapshot().copy(
            tables = fullSnapshot().tables.map { table ->
                if (table.name == "vacations") {
                    table.copy(
                        records = listOf(
                            row(
                                text(VACATION_ID), text("2026-08-21"), text("2026-08-21"),
                                integer(CREATED_AT), integer(CREATED_AT),
                            ),
                        ),
                    )
                } else {
                    table
                }
            },
        )

        val error = assertSuspendFails { store.backups.validateCandidate(invalid) }

        assertTrue(error is InvalidBackupException)
        assertTrue(store.backups.capture().isEmpty)
    }

    @Test
    fun openingTheLiveGatewaySweepsOnlyOrphanedCandidateDatabasesAndSidecars() = runBlocking {
        val orphanName = "miguardia-backup-candidate-${UUID.randomUUID()}.db"
        val orphan = MiGuardiaV2Database.build(context, orphanName)
        orphan.openHelper.writableDatabase
        orphan.close()
        val orphanFile = context.getDatabasePath(orphanName)
        val orphanWal = java.io.File("${orphanFile.path}-wal").also { it.writeText("orphan") }
        val unrelated = context.getDatabasePath("miguardia-backup-candidate-not-owned.db").also {
            it.parentFile?.mkdirs()
            it.writeText("keep")
        }
        try {
            assertTrue(orphanFile.exists())
            assertTrue(orphanWal.exists())

            assertTrue(store.backups.capture().isEmpty)

            assertFalse(orphanFile.exists())
            assertFalse(orphanWal.exists())
            assertTrue(unrelated.exists())
        } finally {
            context.deleteDatabase(orphanName)
            unrelated.delete()
        }
    }

    @Test
    fun restoreWriteBarrierMakesAConcurrentRoomMutationWaitInsteadOfLosingIt() = runBlocking {
        val original = emptySnapshot().withObjectives(listOf(objective(OBJECTIVE_ID, "Original", "ORI")))
        val replacement = emptySnapshot().withObjectives(listOf(objective(OBJECTIVE_ID, "Restaurado", "RES")))
        val concurrent = emptySnapshot().withObjectives(listOf(objective(OBJECTIVE_ID, "Concurrente", "CON")))
        store.backups.replace(original)
        val barrierEntered = CompletableDeferred<Unit>()
        val releaseBarrier = CompletableDeferred<Unit>()

        val restore = async(Dispatchers.IO) {
            store.backups.replaceWithWriteBarrier(
                expectedCurrent = original,
                replacement = replacement,
                beforeReplace = {
                    barrierEntered.complete(Unit)
                    releaseBarrier.await()
                },
                afterReplace = {},
            )
        }
        barrierEntered.await()
        val concurrentWrite = async(Dispatchers.IO) { store.backups.replace(concurrent) }
        delay(100)

        assertFalse(concurrentWrite.isCompleted)
        releaseBarrier.complete(Unit)
        restore.await()
        concurrentWrite.await()

        assertEquals(concurrent, store.backups.capture())
    }

    private fun emptySnapshot(): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        timelineId = null,
        tables = MiGuardiaBackupSchemaV5.tables.map { spec ->
            BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())
        },
    )

    private fun fullSnapshot(): BackupDatabaseSnapshot {
        val plannedStart = Instant.parse("2026-08-10T11:00:00Z").toEpochMilli()
        val plannedEnd = Instant.parse("2026-08-10T19:00:00Z").toEpochMilli()
        val independentStart = Instant.parse("2026-08-12T21:00:00Z").toEpochMilli()
        val independentEnd = Instant.parse("2026-08-12T23:00:00Z").toEpochMilli()
        val availabilityStart = Instant.parse("2026-08-13T21:00:00Z").toEpochMilli()
        val availabilityEnd = Instant.parse("2026-08-13T23:00:00Z").toEpochMilli()
        val rows = mapOf(
            "objectives" to row(
                text(OBJECTIVE_ID), text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"),
                nullValue(), integer(1), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "shifts" to row(
                text(SHIFT_ID), integer(plannedStart), integer(plannedEnd), text(ZONE_ID), text("2026-08-10"),
                text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"), text("08:00"),
                text("16:00"), integer(COLOR), text("Puesto ficticio"), text("PLANNED"),
                text(OBJECTIVE_ID), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "explicit_day_statuses" to row(text("2026-08-20"), text("DAY_OFF")),
            "medical_leaves" to row(
                text(MEDICAL_ID), text("2026-08-21"), text("2026-08-21"), text("Nota médica ficticia"),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "holidays" to row(
                text(HOLIDAY_ID), text("2026-08-17"), text("Feriado ficticio"),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "vacations" to row(
                text(VACATION_ID), text("2026-08-22"), text("2026-08-22"),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "schedule_photos" to row(
                text(PHOTO_ID), text("2026-08"), text(OBJECTIVE_ID), text("Objetivo ficticio"), text("FIC"),
                text(PHOTO_STORAGE_KEY), text("image/png"), integer(67), integer(1), integer(1),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "work_configuration_roots" to row(text(TIMELINE_ID), integer(1)),
            "per_period_hours_definitions" to row(
                text(DEFINITION_ID), text(TIMELINE_ID), text("MONTHLY"), nullValue(), nullValue(), nullValue(),
            ),
            "work_configuration_revisions" to row(
                text(CONFIGURATION_ID), text(TIMELINE_ID), text("2026-01-01"), text("PRIVATE_SECURITY"),
                text("PASSIVE_GUARD"), text("PER_PERIOD"), nullValue(), nullValue(), nullValue(), nullValue(),
                nullValue(), text(DEFINITION_ID), text("2026-01-01"),
            ),
            "per_period_hours_values" to row(
                text(PERIOD_VALUE_ID), text(DEFINITION_ID), text("2026-08-01"), text("2026-09-01"),
                integer(12_000),
            ),
            "work_places" to row(
                text(PLACE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(OBJECTIVE_ID),
                integer(1), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "work_types" to row(
                text(TYPE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text("Jornada habitual"),
                text("JORNADA HABITUAL"), text("ACTIVE_WORK"), integer(1), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "work_templates" to row(
                text(TEMPLATE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(PLACE_ID), text(OBJECTIVE_ID),
                text(TYPE_ID), text("08:00"), text("16:00"), integer(COLOR), integer(1),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "workplace_rule_revisions" to row(
                text(RULE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(PLACE_ID), text(OBJECTIVE_ID),
                text("2026-01-01"), text("DISABLED"), nullValue(), nullValue(), nullValue(), nullValue(),
                text("NONE"), nullValue(), nullValue(), integer(0), integer(0), integer(CREATED_AT),
            ),
            "recurring_plans" to row(
                text(PLAN_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), integer(CREATED_AT),
            ),
            "extra_work_classes" to row(
                text(EXTRA_CLASS_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text("Extensión ficticia"),
                text("EXTENSIÓN FICTICIA"), integer(1), integer(1), integer(1),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "shift_work_snapshots" to row(
                text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text(PLACE_ID), text(OBJECTIVE_ID), text(TEMPLATE_ID), text(TYPE_ID),
                text("Jornada habitual"), text("ACTIVE_WORK"),
            ),
            "shift_notes" to row(
                text(NOTE_ID), text(SHIFT_ID), text("Nota ficticia"), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "shift_notification_configs" to row(text(SHIFT_ID)),
            "shift_notification_reminders" to row(text(SHIFT_ID), integer(60)),
            "recurring_plan_revisions" to row(
                text(PLAN_REVISION_ID), text(PLAN_ID), integer(1), text("2026-08-11"), text("ACTIVE"),
                text("2026-08-11"), text("EVERY_N_DAYS"), nullValue(), integer(1), nullValue(), nullValue(),
                text(TEMPLATE_ID), text(PLACE_ID), text(OBJECTIVE_ID), text(TYPE_ID), text("Objetivo ficticio"),
                text("FIC"), text("Dirección ficticia"), text("Jornada habitual"), text("ACTIVE_WORK"),
                text("08:00"), text("16:00"), integer(COLOR), nullValue(), text(ZONE_ID), integer(CREATED_AT),
            ),
            "recurring_occurrences" to row(
                text(PLAN_ID), text("2026-08-11"), text(PLAN_REVISION_ID), nullValue(), text("EXCLUDED"),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "shift_actual_records" to row(
                text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), integer(plannedStart),
                integer(plannedEnd + HOUR_MILLIS), text("Salida posterior ficticia"), nullValue(),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "shift_extra_intervals" to row(
                text(EXTRA_INTERVAL_ID), text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"),
                text(EXTRA_CLASS_ID), integer(plannedEnd), integer(plannedEnd + HOUR_MILLIS),
                text("Extensión ficticia"), integer(1), integer(1), integer(CREATED_AT), integer(CREATED_AT),
            ),
            "independent_extra_work_records" to row(
                text(INDEPENDENT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text(PLACE_ID), text(OBJECTIVE_ID), text(TYPE_ID), text(TEMPLATE_ID), text(EXTRA_CLASS_ID),
                text("2026-08-12"), text(ZONE_ID), integer(independentStart), integer(independentEnd),
                text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"), text("Jornada habitual"),
                text("ACTIVE_WORK"), integer(COLOR), nullValue(), text("Extensión ficticia"), integer(1), integer(1),
                integer(CREATED_AT), integer(CREATED_AT),
            ),
            "availability_windows" to row(
                text(AVAILABILITY_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text("2026-08-13"), text(ZONE_ID), integer(availabilityStart), integer(availabilityEnd),
                text("Guardia pasiva"), integer(CREATED_AT), integer(CREATED_AT),
            ),
        )
        return BackupDatabaseSnapshot(
            timelineId = TIMELINE_ID,
            tables = MiGuardiaBackupSchemaV5.tables.map { spec ->
                BackupTable(spec.name, spec.columns, spec.primaryKey, listOf(requireNotNull(rows[spec.name])))
            },
        )
    }

    private fun row(vararg values: BackupValue): BackupRecord = BackupRecord(values.toList())
    private fun text(value: String): BackupValue = BackupValue.Text(value)
    private fun integer(value: Int): BackupValue = BackupValue.Integer(value.toLong())
    private fun integer(value: Long): BackupValue = BackupValue.Integer(value)
    private fun nullValue(): BackupValue = BackupValue.Null

    private fun BackupDatabaseSnapshot.withObjectives(records: List<BackupRecord>) = copy(
        tables = tables.map { table -> if (table.name == "objectives") table.copy(records = records) else table },
    )

    private fun objective(id: String, name: String, abbreviation: String) = BackupRecord(
        listOf(
            BackupValue.Text(id),
            BackupValue.Text(name),
            BackupValue.Text(abbreviation),
            BackupValue.Null,
            BackupValue.Null,
            BackupValue.Integer(1),
            BackupValue.Integer(CREATED_AT),
            BackupValue.Integer(CREATED_AT),
        ),
    )

    private suspend fun assertSuspendFails(block: suspend () -> Unit): Throwable = try {
        block()
        throw AssertionError("Se esperaba una falla")
    } catch (error: AssertionError) {
        throw error
    } catch (error: Throwable) {
        error
    }

    private companion object {
        const val CREATED_AT = 1_788_131_400_000L
        const val HOUR_MILLIS = 3_600_000L
        const val COLOR = -13_408_615L
        const val OBJECTIVE_ID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_OBJECTIVE_ID = "22222222-2222-4222-8222-222222222222"
        const val TIMELINE_ID = "30000000-0000-4000-8000-000000000001"
        const val CONFIGURATION_ID = "30000000-0000-4000-8000-000000000002"
        const val DEFINITION_ID = "30000000-0000-4000-8000-000000000003"
        const val PERIOD_VALUE_ID = "30000000-0000-4000-8000-000000000004"
        const val PLACE_ID = "30000000-0000-4000-8000-000000000005"
        const val TYPE_ID = "30000000-0000-4000-8000-000000000006"
        const val TEMPLATE_ID = "30000000-0000-4000-8000-000000000007"
        const val RULE_ID = "30000000-0000-4000-8000-000000000008"
        const val SHIFT_ID = "30000000-0000-4000-8000-000000000009"
        const val NOTE_ID = "30000000-0000-4000-8000-000000000010"
        const val MEDICAL_ID = "30000000-0000-4000-8000-000000000011"
        const val HOLIDAY_ID = "30000000-0000-4000-8000-000000000012"
        const val VACATION_ID = "30000000-0000-4000-8000-000000000013"
        const val PHOTO_ID = "30000000-0000-4000-8000-000000000014"
        const val PHOTO_STORAGE_KEY = "30000000-0000-4000-8000-000000000014.png"
        const val PLAN_ID = "30000000-0000-4000-8000-000000000015"
        const val PLAN_REVISION_ID = "30000000-0000-4000-8000-000000000016"
        const val EXTRA_CLASS_ID = "30000000-0000-4000-8000-000000000017"
        const val EXTRA_INTERVAL_ID = "30000000-0000-4000-8000-000000000018"
        const val INDEPENDENT_ID = "30000000-0000-4000-8000-000000000019"
        const val AVAILABILITY_ID = "30000000-0000-4000-8000-000000000020"
        const val ZONE_ID = "America/Argentina/Buenos_Aires"
    }
}
