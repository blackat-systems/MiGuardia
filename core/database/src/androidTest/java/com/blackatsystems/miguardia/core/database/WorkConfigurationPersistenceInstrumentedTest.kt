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
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueMutation
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueWriteResult
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import com.blackatsystems.miguardia.core.domain.work.requiresStartedOnMarker
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
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
        val first = revision(20, DATE, WorkSector.PRIVATE_SECURITY, definition)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        store.workConfiguration.addRevision(
            TIMELINE_ID,
            EffectiveRevision(
                V2TestIds.uuid(221),
                DATE.plusDays(1),
                first.value.copy(
                    availabilityLabel = com.blackatsystems.miguardia.core.domain.work
                        .AvailabilityLabel.PASSIVE_GUARD,
                ),
            ),
        )
        val original = PerPeriodHoursEntry(
            id = ENTRY_ID,
            key = definition.keyContaining(DATE),
            requiredMinutes = PositiveMinutes(9_600),
        )
        val emptyExpected = requireNotNull(store.workConfiguration.get())
        assertTrue(
            store.workConfiguration.applyPerPeriodHoursValueMutation(
                PerPeriodHoursValueMutation(emptyExpected, original),
            ) is PerPeriodHoursValueWriteResult.Saved,
        )
        val corrected = original.copy(requiredMinutes = PositiveMinutes(9_000))
        val correctionExpected = requireNotNull(store.workConfiguration.get())
        assertTrue(
            store.workConfiguration.applyPerPeriodHoursValueMutation(
                PerPeriodHoursValueMutation(correctionExpected, corrected),
            ) is PerPeriodHoursValueWriteResult.Saved,
        )
        assertEquals(
            PerPeriodHoursValueWriteResult.Conflict,
            store.workConfiguration.applyPerPeriodHoursValueMutation(
                PerPeriodHoursValueMutation(
                    correctionExpected,
                    corrected.copy(requiredMinutes = PositiveMinutes(8_400)),
                ),
            ),
        )

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

    @Test
    fun atomicReferenceMutationPersistsResetMarkerAndDetectsStaleHistory() = runBlocking {
        val first = revision(40, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        val expected = requireNotNull(store.workConfiguration.get())
        val reference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_600))
        val start = DATE.plusDays(1)
        val fixedRevision = EffectiveRevision(
            V2TestIds.uuid(241),
            start,
            WorkConfiguration(
                WorkSector.PRIVATE_SECURITY,
                reference,
                null,
                hoursReferenceStartedOn = start,
            ),
        )
        val saved = store.workConfiguration.applyReferenceMutation(
            WorkConfigurationReferenceMutation(expected, fixedRevision),
        )
        assertTrue(saved is WorkConfigurationReferenceWriteResult.Saved)
        assertEquals(start, requireNotNull(store.workConfiguration.get()).timeline.valueAt(start)?.hoursReferenceStartedOn)

        val staleRetry = store.workConfiguration.applyReferenceMutation(
            WorkConfigurationReferenceMutation(expected, fixedRevision.copy(id = V2TestIds.uuid(242))),
        )
        assertEquals(WorkConfigurationReferenceWriteResult.Conflict, staleRetry)

        store.close()
        openStore()
        assertEquals(start, requireNotNull(store.workConfiguration.get()).timeline.valueAt(start)?.hoursReferenceStartedOn)
    }

    @Test
    fun referenceMutationRollsBackCompletelyWhenTheRevisionInsertFails() = runBlocking {
        val first = revision(45, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        val expected = requireNotNull(store.workConfiguration.get())
        val start = DATE.plusDays(1)
        val failedRevisionId = V2TestIds.uuid(246)
        val replacement = EffectiveRevision(
            failedRevisionId,
            start,
            first.value.copy(
                hoursReference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_000)),
                hoursReferenceStartedOn = start,
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_reference_mutation_rollback
                BEFORE INSERT ON work_configuration_revisions
                WHEN NEW.id = '$failedRevisionId'
                BEGIN
                    SELECT RAISE(ABORT, 'forced reference failure');
                END""",
        )

        assertEquals(
            WorkConfigurationReferenceWriteResult.Conflict,
            store.workConfiguration.applyReferenceMutation(
                WorkConfigurationReferenceMutation(expected, replacement),
            ),
        )
        val actual = requireNotNull(store.workConfiguration.get())
        assertEquals(expected.timeline.id, actual.timeline.id)
        assertEquals(expected.timeline.revisions, actual.timeline.revisions)
        assertEquals(expected.perPeriodHoursValues.entries, actual.perPeriodHoursValues.entries)
        assertEquals(1, rowCount("work_configuration_revisions"))
        assertEquals(0, rowCount("per_period_hours_definitions"))
        assertEquals(0, rowCount("per_period_hours_values"))
    }

    @Test
    fun atomicPerPeriodReferenceCreatesDefinitionRevisionAndFirstValueTogether() = runBlocking {
        val first = revision(50, DATE, WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        val expected = requireNotNull(store.workConfiguration.get())
        val start = DATE.plusDays(1)
        val definition = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        val newRevision = EffectiveRevision(
            V2TestIds.uuid(251),
            start,
            WorkConfiguration(
                WorkSector.PRIVATE_SECURITY,
                definition,
                null,
                hoursReferenceStartedOn = start,
            ),
        )
        val value = PerPeriodHoursEntry(
            ENTRY_ID,
            definition.keyContaining(start),
            PositiveMinutes(8_400),
        )

        val result = store.workConfiguration.applyReferenceMutation(
            WorkConfigurationReferenceMutation(expected, newRevision, value),
        )

        assertTrue(result is WorkConfigurationReferenceWriteResult.Saved)
        assertEquals(
            PerPeriodHoursLookup.Defined(value),
            requireNotNull(store.workConfiguration.get()).perPeriodHoursValues.valueFor(value.key),
        )
        assertEquals(1, rowCount("per_period_hours_definitions"))
        assertEquals(1, rowCount("per_period_hours_values"))
    }

    @Test
    fun sameDateReferenceReplacementIsAtomicAndRemovesObsoletePerPeriodValues() = runBlocking {
        val oldDefinition = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        val first = revision(60, DATE, WorkSector.PRIVATE_SECURITY, oldDefinition)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        val oldValue = PerPeriodHoursEntry(
            ENTRY_ID,
            oldDefinition.keyContaining(DATE),
            PositiveMinutes(8_100),
        )
        assertTrue(
            store.workConfiguration.applyPerPeriodHoursValueMutation(
                PerPeriodHoursValueMutation(
                    requireNotNull(store.workConfiguration.get()),
                    oldValue,
                ),
            ) is PerPeriodHoursValueWriteResult.Saved,
        )
        val expected = requireNotNull(store.workConfiguration.get())
        val fixed = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_000))

        val result = store.workConfiguration.applyReferenceMutation(
            WorkConfigurationReferenceMutation(
                expectedHistory = expected,
                revision = EffectiveRevision(
                    id = first.id,
                    effectiveFrom = DATE,
                    value = first.value.copy(
                        hoursReference = fixed,
                        hoursReferenceStartedOn = DATE,
                    ),
                ),
            ),
        )

        assertTrue(result is WorkConfigurationReferenceWriteResult.Saved)
        assertEquals(fixed, requireNotNull(store.workConfiguration.get()).timeline.valueAt(DATE)?.hoursReference)
        assertEquals(0, rowCount("per_period_hours_values"))
        assertEquals(0, rowCount("per_period_hours_definitions"))
        assertEquals(1, rowCount("work_configuration_revisions"))
    }

    @Test
    fun retroactiveRestartPropagatesThroughUnrelatedRevisionsButStopsAtALaterRestart() = runBlocking {
        val originalReference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(8_400))
        val first = revision(70, DATE, WorkSector.PRIVATE_SECURITY, originalReference)
        store.workConfiguration.createInitial(TIMELINE_ID, first)
        val unrelatedDate = DATE.plusDays(10)
        val laterRestartDate = DATE.plusDays(20)
        val unrelated = EffectiveRevision(
            V2TestIds.uuid(271),
            unrelatedDate,
            first.value.copy(
                availabilityLabel = com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel.PASSIVE_GUARD,
            ),
        )
        val laterRestart = EffectiveRevision(
            V2TestIds.uuid(272),
            laterRestartDate,
            first.value.copy(hoursReferenceStartedOn = laterRestartDate),
        )
        store.workConfiguration.addRevision(TIMELINE_ID, unrelated)
        store.workConfiguration.addRevision(TIMELINE_ID, laterRestart)
        val expected = requireNotNull(store.workConfiguration.get())
        val retroactiveDate = DATE.plusDays(5)
        val replacementReference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_000))
        val replacement = EffectiveRevision(
            V2TestIds.uuid(273),
            retroactiveDate,
            first.value.copy(
                hoursReference = replacementReference,
                hoursReferenceStartedOn = retroactiveDate,
            ),
        )

        assertTrue(
            store.workConfiguration.applyReferenceMutation(
                WorkConfigurationReferenceMutation(expected, replacement),
            ) is WorkConfigurationReferenceWriteResult.Saved,
        )

        val saved = requireNotNull(store.workConfiguration.get())
        assertEquals(originalReference, saved.timeline.valueAt(retroactiveDate.minusDays(1))?.hoursReference)
        assertEquals(replacementReference, saved.timeline.valueAt(retroactiveDate)?.hoursReference)
        assertEquals(retroactiveDate, saved.timeline.valueAt(unrelatedDate)?.hoursReferenceStartedOn)
        assertEquals(replacementReference, saved.timeline.valueAt(unrelatedDate)?.hoursReference)
        assertEquals(originalReference, saved.timeline.valueAt(laterRestartDate)?.hoursReference)
        assertEquals(laterRestartDate, saved.timeline.valueAt(laterRestartDate)?.hoursReferenceStartedOn)

        store.close()
        openStore()
        assertEquals(
            retroactiveDate,
            requireNotNull(store.workConfiguration.get())
                .timeline.valueAt(unrelatedDate)?.hoursReferenceStartedOn,
        )
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
        value = WorkConfiguration(
            sector,
            hours,
            availabilityLabel = null,
            hoursReferenceStartedOn = date.takeIf { hours.requiresStartedOnMarker },
        ),
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
