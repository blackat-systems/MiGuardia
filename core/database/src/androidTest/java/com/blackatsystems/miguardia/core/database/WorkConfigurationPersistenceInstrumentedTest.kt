package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.DateWindow
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkConfigurationPersistenceInstrumentedTest {
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
    fun everySectorAvailabilityReferenceAndPeriodRoundTripsAcrossReopen() = runBlocking {
        val monthlyDefinitionId = uuid(801)
        val weeklyDefinitionId = uuid(802)
        val cycleDefinitionId = uuid(803)
        val revisions = listOf(
            revision(701, LocalDate.of(2026, 1, 1), WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup),
            revision(
                702,
                LocalDate.of(2026, 2, 1),
                WorkSector.POLICE,
                HoursReference.NotUsed,
                AvailabilityLabel.PASSIVE_GUARD,
            ),
            revision(
                703,
                LocalDate.of(2026, 3, 1),
                WorkSector.NURSING,
                HoursReference.Unknown(),
                AvailabilityLabel.AVAILABLE_FOR_CALL,
            ),
            revision(
                704,
                LocalDate.of(2026, 4, 1),
                WorkSector.MEDICINE,
                HoursReference.Unknown(HoursPeriod.Weekly(DayOfWeek.SUNDAY)),
                AvailabilityLabel.ON_CALL_RETAINER,
            ),
            revision(
                705,
                LocalDate.of(2026, 5, 1),
                WorkSector.PRIVATE_SECURITY,
                HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_000)),
            ),
            revision(
                706,
                LocalDate.of(2026, 6, 1),
                WorkSector.POLICE,
                HoursReference.Fixed(
                    HoursPeriod.Weekly(DayOfWeek.WEDNESDAY),
                    PositiveMinutes(2_400),
                ),
            ),
            revision(
                707,
                LocalDate.of(2026, 7, 1),
                WorkSector.NURSING,
                HoursReference.Fixed(
                    HoursPeriod.Cycle(LocalDate.of(2026, 6, 15), 21),
                    PositiveMinutes(7_560),
                ),
            ),
            revision(
                708,
                LocalDate.of(2026, 8, 1),
                WorkSector.MEDICINE,
                HoursReference.PerPeriod(monthlyDefinitionId, HoursPeriod.Monthly),
            ),
            revision(
                709,
                LocalDate.of(2026, 9, 1),
                WorkSector.PRIVATE_SECURITY,
                HoursReference.PerPeriod(
                    weeklyDefinitionId,
                    HoursPeriod.Weekly(DayOfWeek.SATURDAY),
                ),
            ),
            revision(
                710,
                LocalDate.of(2026, 10, 1),
                WorkSector.POLICE,
                HoursReference.PerPeriod(
                    cycleDefinitionId,
                    HoursPeriod.Cycle(LocalDate.of(2026, 9, 10), 28),
                ),
            ),
        )

        store.workConfiguration.createInitial(TIMELINE_ID, revisions.first())
        revisions.drop(1).forEach { store.workConfiguration.addRevision(TIMELINE_ID, it) }

        assertHistory(
            actual = store.workConfiguration.get(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = revisions,
        )
        assertHistory(
            actual = store.workConfiguration.observe().first(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = revisions,
        )

        store.close()
        openStore()
        val reopened = requireNotNull(store.workConfiguration.get())
        assertHistory(reopened, WorkConfigurationOrigin.NEW_V2, revisions)
        assertEquals(revisions.first().value, reopened.timeline.valueAt(LocalDate.of(2026, 1, 31)))
        assertEquals(revisions.last().value, reopened.timeline.valueAt(LocalDate.of(2026, 12, 31)))

        val persistedCycle = (reopened.timeline.revisions.last().value.hoursReference as HoursReference.PerPeriod)
            .period as HoursPeriod.Cycle
        assertEquals(
            DateWindow(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 9, 10)),
            persistedCycle.windowContaining(LocalDate.of(2026, 9, 9)),
        )
        assertEquals(
            DateWindow(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 11, 5)),
            persistedCycle.windowContaining(LocalDate.of(2026, 10, 20)),
        )
    }

    @Test
    fun freshDatabaseIsNullThenInitialCreationIsObservedAtomically() = runBlocking {
        assertNull(store.workConfiguration.get())
        assertNull(store.workConfiguration.observe().first())
        val firstRevision = revision(
            711,
            LocalDate.of(2026, 11, 1),
            WorkSector.MEDICINE,
            HoursReference.PendingSetup,
        )
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                store.workConfiguration.observe().first { history -> history != null }
            }
        }

        store.workConfiguration.createInitial(TIMELINE_ID, firstRevision)

        assertHistory(
            actual = observed.await(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = listOf(firstRevision),
        )
        assertEquals(1, rowCount("work_configuration_roots"))
        assertEquals(1, rowCount("work_configuration_revisions"))
    }

    @Test
    fun perPeriodValuesCanBeCreatedCorrectedAndReopenedWithoutChangingIdentity() = runBlocking {
        val definitionId = uuid(821)
        val reference = HoursReference.PerPeriod(definitionId, HoursPeriod.Monthly)
        val firstRevision = revision(
            721,
            LocalDate.of(2026, 1, 1),
            WorkSector.NURSING,
            reference,
        )
        val january = PerPeriodHoursEntry(
            id = uuid(831),
            key = reference.keyContaining(LocalDate.of(2026, 1, 20)),
            requiredMinutes = PositiveMinutes(8_400),
        )
        val february = PerPeriodHoursEntry(
            id = uuid(832),
            key = reference.keyContaining(LocalDate.of(2026, 2, 20)),
            requiredMinutes = PositiveMinutes(8_100),
        )

        store.workConfiguration.createInitial(TIMELINE_ID, firstRevision)
        store.workConfiguration.createPerPeriodValue(TIMELINE_ID, january)
        store.workConfiguration.createPerPeriodValue(TIMELINE_ID, february)
        val correctedJanuary = january.copy(requiredMinutes = PositiveMinutes(8_700))
        store.workConfiguration.updatePerPeriodValue(TIMELINE_ID, correctedJanuary)

        assertHistory(
            actual = store.workConfiguration.get(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = listOf(firstRevision),
            expectedValues = listOf(correctedJanuary, february),
        )

        store.close()
        openStore()
        assertHistory(
            actual = store.workConfiguration.get(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = listOf(firstRevision),
            expectedValues = listOf(correctedJanuary, february),
        )
    }

    @Test
    fun duplicateRootRevisionDefinitionAndValueAreRejectedWithoutRewritingHistory() = runBlocking {
        val initial = revision(
            731,
            LocalDate.of(2026, 1, 1),
            WorkSector.PRIVATE_SECURITY,
            HoursReference.NotUsed,
        )
        store.workConfiguration.createInitial(TIMELINE_ID, initial)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createInitial(
                uuid(999),
                revision(799, LocalDate.of(2026, 1, 2), WorkSector.POLICE, HoursReference.PendingSetup),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.addRevision(
                TIMELINE_ID,
                revision(732, initial.effectiveFrom, WorkSector.POLICE, HoursReference.NotUsed),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.addRevision(
                TIMELINE_ID,
                initial.copy(effectiveFrom = LocalDate.of(2026, 1, 2)),
            )
        }

        val definitionId = uuid(841)
        val monthlyReference = HoursReference.PerPeriod(definitionId, HoursPeriod.Monthly)
        val firstPerPeriod = revision(
            733,
            LocalDate.of(2026, 2, 1),
            WorkSector.PRIVATE_SECURITY,
            monthlyReference,
        )
        val sameDefinitionAndPattern = revision(
            734,
            LocalDate.of(2026, 3, 1),
            WorkSector.POLICE,
            monthlyReference,
        )
        store.workConfiguration.addRevision(TIMELINE_ID, firstPerPeriod)
        store.workConfiguration.addRevision(TIMELINE_ID, sameDefinitionAndPattern)
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.addRevision(
                TIMELINE_ID,
                revision(
                    735,
                    LocalDate.of(2026, 4, 1),
                    WorkSector.NURSING,
                    HoursReference.PerPeriod(
                        definitionId,
                        HoursPeriod.Weekly(DayOfWeek.MONDAY),
                    ),
                ),
            )
        }

        val storedValue = PerPeriodHoursEntry(
            id = uuid(851),
            key = monthlyReference.keyContaining(LocalDate.of(2026, 3, 15)),
            requiredMinutes = PositiveMinutes(8_000),
        )
        store.workConfiguration.createPerPeriodValue(TIMELINE_ID, storedValue)
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createPerPeriodValue(
                TIMELINE_ID,
                storedValue.copy(id = uuid(852), requiredMinutes = PositiveMinutes(8_100)),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createPerPeriodValue(
                TIMELINE_ID,
                storedValue.copy(
                    key = monthlyReference.keyContaining(LocalDate.of(2026, 4, 15)),
                ),
            )
        }
        val orphanReference = HoursReference.PerPeriod(uuid(842), HoursPeriod.Monthly)
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createPerPeriodValue(
                TIMELINE_ID,
                PerPeriodHoursEntry(
                    id = uuid(853),
                    key = orphanReference.keyContaining(LocalDate.of(2026, 5, 15)),
                    requiredMinutes = PositiveMinutes(8_200),
                ),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.updatePerPeriodValue(
                TIMELINE_ID,
                storedValue.copy(
                    key = monthlyReference.keyContaining(LocalDate.of(2026, 4, 15)),
                ),
            )
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.updatePerPeriodValue(
                TIMELINE_ID,
                storedValue.copy(id = uuid(854)),
            )
        }

        assertHistory(
            actual = store.workConfiguration.get(),
            expectedOrigin = WorkConfigurationOrigin.NEW_V2,
            expectedRevisions = listOf(initial, firstPerPeriod, sameDefinitionAndPattern),
            expectedValues = listOf(storedValue),
        )
    }

    @Test
    fun initialCreationRollsBackRootAndDefinitionWhenRevisionInsertFails() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_initial_work_revision
                BEFORE INSERT ON work_configuration_revisions
                BEGIN SELECT RAISE(ABORT, 'forced initial configuration failure'); END""".trimIndent(),
        )
        val reference = HoursReference.PerPeriod(uuid(861), HoursPeriod.Monthly)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createInitial(
                TIMELINE_ID,
                revision(
                    741,
                    LocalDate.of(2026, 1, 1),
                    WorkSector.MEDICINE,
                    reference,
                ),
            )
        }

        assertEquals(0, rowCount("work_configuration_roots"))
        assertEquals(0, rowCount("per_period_hours_definitions"))
        assertEquals(0, rowCount("work_configuration_revisions"))
        assertNull(store.workConfiguration.get())
    }

    @Test
    fun corruptCodesAndMultipleRootsAreReportedAsInvalidLocalData() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO work_configuration_roots VALUES ('$TIMELINE_ID', 1, 'NEW_V2')",
        )
        sqlite.execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                '${uuid(751)}', '$TIMELINE_ID', '2026-01-01', 'UNKNOWN_SECTOR',
                NULL, 'PENDING_SETUP', NULL, NULL, NULL, NULL, NULL, NULL
            )""".trimIndent(),
        )

        assertSuspendThrows<InvalidLocalDataException> { store.workConfiguration.get() }

        sqlite.execSQL("DELETE FROM work_configuration_revisions")
        sqlite.execSQL("DELETE FROM work_configuration_roots")
        val corruptDefinitionId = uuid(753)
        sqlite.execSQL(
            "INSERT INTO work_configuration_roots VALUES ('$TIMELINE_ID', 1, 'NEW_V2')",
        )
        sqlite.execSQL(
            """INSERT INTO per_period_hours_definitions VALUES (
                '$corruptDefinitionId', '$TIMELINE_ID', 'MONTHLY', NULL, NULL, NULL
            )""".trimIndent(),
        )
        sqlite.execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                '${uuid(754)}', '$TIMELINE_ID', '2026-01-01', 'MEDICINE',
                NULL, 'PER_PERIOD', NULL, NULL, NULL, NULL, NULL, '$corruptDefinitionId'
            )""".trimIndent(),
        )
        sqlite.execSQL(
            """INSERT INTO per_period_hours_values VALUES (
                '${uuid(755)}', '$corruptDefinitionId', '2026-01-01', '2026-01-31', 8000
            )""".trimIndent(),
        )

        assertSuspendThrows<InvalidLocalDataException> { store.workConfiguration.get() }

        sqlite.execSQL("DELETE FROM per_period_hours_values")
        sqlite.execSQL("DELETE FROM work_configuration_revisions")
        sqlite.execSQL("DELETE FROM per_period_hours_definitions")
        sqlite.execSQL("DELETE FROM work_configuration_roots")
        sqlite.execSQL(
            "INSERT INTO work_configuration_roots VALUES ('$TIMELINE_ID', 1, 'MIGRATED_V1')",
        )
        sqlite.execSQL(
            "INSERT INTO work_configuration_roots VALUES ('${uuid(752)}', 2, 'MIGRATED_V1')",
        )

        assertSuspendThrows<InvalidLocalDataException> { store.workConfiguration.get() }

        sqlite.execSQL(
            "DELETE FROM work_configuration_roots WHERE timelineId = '$TIMELINE_ID'",
        )
        assertSuspendThrows<InvalidLocalDataException> { store.workConfiguration.get() }
        Unit
    }

    @Test
    fun orphanRowsAreReportedEvenWhenNoRootCanExposeThemThroughRelations() = runBlocking {
        assertNull(store.workConfiguration.get())
        store.close()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.setForeignKeyConstraintsEnabled(false)
            sqlite.execSQL(
                """INSERT INTO per_period_hours_definitions VALUES (
                    '${uuid(871)}', '${uuid(872)}', 'MONTHLY', NULL, NULL, NULL
                )""".trimIndent(),
            )
            sqlite.execSQL(
                """INSERT INTO work_configuration_revisions VALUES (
                    '${uuid(873)}', '${uuid(874)}', '2026-01-01', 'POLICE',
                    NULL, 'PENDING_SETUP', NULL, NULL, NULL, NULL, NULL, NULL
                )""".trimIndent(),
            )
            sqlite.execSQL(
                """INSERT INTO per_period_hours_values VALUES (
                    '${uuid(875)}', '${uuid(876)}', '2026-01-01', '2026-02-01', 8000
                )""".trimIndent(),
            )
        }
        openStore()

        assertEquals(3, database.workConfigurationDao().getOrphanRowCount())
        assertSuspendThrows<InvalidLocalDataException> { store.workConfiguration.get() }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.observe().first()
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createInitial(
                TIMELINE_ID,
                revision(
                    877,
                    LocalDate.of(2026, 1, 1),
                    WorkSector.POLICE,
                    HoursReference.PendingSetup,
                ),
            )
        }
        assertEquals(0, rowCount("work_configuration_roots"))
        Unit
    }

    private fun revision(
        idSuffix: Int,
        effectiveFrom: LocalDate,
        sector: WorkSector,
        hoursReference: HoursReference,
        availabilityLabel: AvailabilityLabel? = null,
    ): EffectiveRevision<WorkConfiguration> = EffectiveRevision(
        id = uuid(idSuffix),
        effectiveFrom = effectiveFrom,
        value = WorkConfiguration(
            sector = sector,
            hoursReference = hoursReference,
            availabilityLabel = availabilityLabel,
        ),
    )

    private fun assertHistory(
        actual: WorkConfigurationHistory?,
        expectedOrigin: WorkConfigurationOrigin,
        expectedRevisions: List<EffectiveRevision<WorkConfiguration>>,
        expectedValues: List<PerPeriodHoursEntry> = emptyList(),
    ) {
        assertNotNull(actual)
        val history = requireNotNull(actual)
        assertEquals(expectedOrigin, history.origin)
        assertEquals(TIMELINE_ID, history.timeline.id)
        assertEquals(expectedRevisions, history.timeline.revisions)
        assertEquals(expectedValues, history.perPeriodHoursValues.entries)
    }

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

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

    private companion object {
        const val DATABASE_NAME: String = "work-configuration-persistence-test.db"
        val TIMELINE_ID: UUID = uuid(700)

        fun uuid(suffix: Int): UUID = UUID.fromString(
            "00000000-0000-0000-0000-${suffix.toString().padStart(12, '0')}",
        )
    }
}
