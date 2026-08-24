package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRootEntity
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursLookup
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkConfigurationPersistenceInstrumentedTest {
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
    fun v2HistoryRoundTripsAndReopensWithoutAnOriginFlag() = runBlocking {
        val first = revision(1, LocalDate.of(2026, 1, 1), WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        val second = revision(2, LocalDate.of(2026, 2, 1), WorkSector.POLICE, HoursReference.NotUsed)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        store.workConfiguration.addRevision(TIMELINE_ID, second)

        assertEquals(listOf(first, second), requireNotNull(store.workConfiguration.observe().first()).timeline.revisions)
        store.close()
        openStore()
        assertEquals(listOf(first, second), requireNotNull(store.workConfiguration.get()).timeline.revisions)
    }

    @Test
    fun onlyOneNonEmptyTimelineCanExist() = runBlocking {
        val first = revision(10, DATE, WorkSector.MEDICINE, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createInitial(OTHER_TIMELINE_ID, revision(11, DATE, WorkSector.NURSING, HoursReference.NotUsed))
        }
        assertEquals(TIMELINE_ID, requireNotNull(store.workConfiguration.get()).timeline.id)
    }

    @Test
    fun multipleStoredRootsProduceAControlledInvalidDataError() = runBlocking {
        val first = revision(12, DATE, WorkSector.MEDICINE, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        database.workConfigurationDao().insertRoot(
            WorkConfigurationRootEntity(
                timelineId = OTHER_TIMELINE_ID.toString(),
                singletonSlot = 2,
            ),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.get()
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.observe().first()
        }
    }

    @Test
    fun orphanDefinitionProducesAControlledInvalidDataError() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys = OFF")
        try {
            sqlite.execSQL(
                """INSERT INTO per_period_hours_definitions(
                    id, timelineId, periodKind, weeklyFirstDayIso, cycleAnchorDate, cycleLengthDays
                ) VALUES (
                    '${V2TestIds.uuid(254)}', '${OTHER_TIMELINE_ID}', 'MONTHLY', NULL, NULL, NULL
                )""",
            )
        } finally {
            sqlite.execSQL("PRAGMA foreign_keys = ON")
        }

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.get()
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.observe().first()
        }
    }

    @Test
    fun unknownHoursReferenceCodeProducesAControlledInvalidDataError() = runBlocking {
        val first = revision(13, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE work_configuration_revisions SET hoursReferenceKind = 'UNKNOWN_REFERENCE_CODE' " +
                "WHERE id = '${first.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.get()
        }
    }

    @Test
    fun unknownSectorCodeProducesAControlledInvalidDataError() = runBlocking {
        val first = revision(14, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE work_configuration_revisions SET sector = 'HEALTH' WHERE id = '${first.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.get()
        }
    }

    @Test
    fun failedInitialCreationRollsBackRootDefinitionAndRevision() = runBlocking {
        val definition = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        val first = revision(15, DATE, WorkSector.PRIVATE_SECURITY, definition)
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_initial_configuration_rollback
                BEFORE INSERT ON work_configuration_revisions
                WHEN NEW.id = '${first.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced revision failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.createInitial(TIMELINE_ID, first)
        }

        assertNull(store.workConfiguration.get())
        assertEquals(0, rowCount("work_configuration_roots"))
        assertEquals(0, rowCount("per_period_hours_definitions"))
        assertEquals(0, rowCount("work_configuration_revisions"))
    }

    @Test
    fun perPeriodValueCreatesUpdatesAndReopensAtomically() = runBlocking {
        val definition = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        store.workConfiguration.createInitial(
            TIMELINE_ID,
            revision(20, DATE, WorkSector.PRIVATE_SECURITY, definition),
        )
        val original = PerPeriodHoursEntry(
            id = ENTRY_ID,
            key = definition.keyContaining(DATE),
            requiredMinutes = PositiveMinutes(9_600),
        )
        store.workConfiguration.createPerPeriodValue(TIMELINE_ID, original)
        val corrected = original.copy(requiredMinutes = PositiveMinutes(9_000))
        store.workConfiguration.updatePerPeriodValue(TIMELINE_ID, corrected)

        assertEquals(
            PerPeriodHoursLookup.Defined(corrected),
            requireNotNull(store.workConfiguration.get()).perPeriodHoursValues.valueFor(original.key),
        )
        store.close()
        openStore()
        assertEquals(
            PerPeriodHoursLookup.Defined(corrected),
            requireNotNull(store.workConfiguration.get()).perPeriodHoursValues.valueFor(original.key),
        )
    }

    @Test
    fun invalidRevisionLeavesTheStoredHistoryUnchanged() = runBlocking {
        val first = revision(30, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workConfiguration.addRevision(
                TIMELINE_ID,
                revision(31, DATE, WorkSector.POLICE, HoursReference.NotUsed),
            )
        }
        assertEquals(listOf(first), requireNotNull(store.workConfiguration.get()).timeline.revisions)
        assertNull(store.workConfiguration.get()?.timeline?.valueAt(DATE.minusDays(1)))
    }

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database)
    }

    private fun rowCount(table: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM `$table`")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun revision(
        number: Int,
        date: LocalDate,
        sector: WorkSector,
        hours: HoursReference,
    ) = EffectiveRevision(
        id = V2TestIds.uuid(200 + number),
        effectiveFrom = date,
        value = WorkConfiguration(sector, hours, availabilityLabel = null),
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
        const val DB = "work-configuration-v2-test.db"
        val DATE: LocalDate = LocalDate.of(2026, 8, 1)
        val TIMELINE_ID = V2TestIds.uuid(250)
        val OTHER_TIMELINE_ID = V2TestIds.uuid(251)
        val DEFINITION_ID = V2TestIds.uuid(252)
        val ENTRY_ID = V2TestIds.uuid(253)
    }
}
