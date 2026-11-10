package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import java.time.Instant
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only its own disposable database, never an application's configured database. */
@RunWith(AndroidJUnit4::class)
class DayOffPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalDataStore
    private val date = V2TestIds.SHIFT_DATE

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        store = LocalDataStore(MiGuardiaV2Database.build(context, DB))
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test fun multipleDaysRoundTripIsIdempotentAndSurvivesReopen() = runBlocking {
        store.seedV2Catalog()
        val dates = setOf(date, date.plusDays(1))
        repeat(2) { store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, dates, true) }
        store.close()
        store = LocalDataStore(MiGuardiaV2Database.build(context, DB))
        assertEquals(dates.map { ExplicitDayStatus(it, ExplicitDayStatusType.DAY_OFF) }, statuses())
        repeat(2) { store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, dates, false) }
        assertTrue(statuses().isEmpty())
    }

    @Test fun removePreservesUndefinedAndUnselectedDayOff() = runBlocking {
        store.seedV2Catalog()
        store.explicitDayStatuses.set(date, ExplicitDayStatusType.UNDEFINED)
        store.explicitDayStatuses.set(date.plusDays(1), ExplicitDayStatusType.DAY_OFF)
        store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date), false)
        assertEquals(listOf(
            ExplicitDayStatus(date, ExplicitDayStatusType.UNDEFINED),
            ExplicitDayStatus(date.plusDays(1), ExplicitDayStatusType.DAY_OFF),
        ), statuses())
    }

    @Test fun changedTimelineRejectsMarkAndRemoveWithoutWrites() = runBlocking {
        store.seedV2Catalog()
        store.explicitDayStatuses.set(date, ExplicitDayStatusType.DAY_OFF)
        for (mark in listOf(true, false)) {
            assertConflict {
                store.explicitDayStatuses.updateDaysOff(UUID.randomUUID(), setOf(date), mark)
            }
        }
        assertEquals(listOf(ExplicitDayStatus(date, ExplicitDayStatusType.DAY_OFF)), statuses())
    }

    @Test fun oneOccupiedDateRejectsWholeBatchWithoutChangingShiftOrUndefined() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(700), date)
        store.v2Shifts.insert(write)
        store.explicitDayStatuses.set(date.plusDays(1), ExplicitDayStatusType.UNDEFINED)
        assertConflict {
            store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date, date.plusDays(1)), true)
        }
        assertEquals(write.shift, store.shifts.getById(write.shift.id))
        assertEquals(listOf(ExplicitDayStatus(date.plusDays(1), ExplicitDayStatusType.UNDEFINED)), statuses())
    }

    @Test fun overnightShiftEndingAtSevenAllowsFollowingFrancoWithoutChangingShift() = runBlocking {
        val fixture = buildV2CatalogFixture().let {
            it.copy(template = it.template.copy(startTime = LocalTime.of(22, 0), endTime = LocalTime.of(7, 0)))
        }
        store.workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
        store.workCatalog.createFirstWorkSet(fixture.toFirstWorkSet())
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(701), date)
        store.v2Shifts.insert(write)
        store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date.plusDays(1)), true)
        assertEquals(listOf(ExplicitDayStatus(date.plusDays(1), ExplicitDayStatusType.DAY_OFF)), statuses())
        assertEquals(write.shift, store.shifts.getById(write.shift.id))
        assertConflict {
            store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date, date.plusDays(2)), true)
        }
        assertEquals(listOf(ExplicitDayStatus(date.plusDays(1), ExplicitDayStatusType.DAY_OFF)), statuses())
        assertEquals(write.shift, store.shifts.getById(write.shift.id))
    }

    @Test fun exactMidnightEndDoesNotOccupyFollowingDate() = runBlocking {
        val fixture = buildV2CatalogFixture().let {
            it.copy(template = it.template.copy(startTime = LocalTime.of(16, 0), endTime = LocalTime.MIDNIGHT))
        }
        store.workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
        store.workCatalog.createFirstWorkSet(fixture.toFirstWorkSet())
        store.v2Shifts.insert(store.buildTestV2Write(fixture, V2TestIds.uuid(702), date))
        store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date.plusDays(1)), true)
        assertEquals(listOf(ExplicitDayStatus(date.plusDays(1), ExplicitDayStatusType.DAY_OFF)), statuses())
    }

    @Test fun noCatalogOrTemplateIsRequiredAfterConfiguration() = runBlocking {
        val fixture = buildV2CatalogFixture()
        store.workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
        store.explicitDayStatuses.updateDaysOff(V2TestIds.TIMELINE, setOf(date), true)
        assertEquals(listOf(ExplicitDayStatus(date, ExplicitDayStatusType.DAY_OFF)), statuses())
    }

    @Test fun undefinedUsesCasBlocksOnlyOwnedShiftOrFrancoAndPreservesMedicalLeave() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(703), date)
        store.v2Shifts.insert(write)
        assertConflict { store.explicitDayStatuses.updateUndefined(date, null, true) }
        store.v2Shifts.deleteShift(write)

        val leave = MedicalLeave(
            id = V2TestIds.uuid(704), startDate = date, endDateInclusive = date,
            privateNote = "Privada", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        store.medicalLeaves.create(leave)
        store.explicitDayStatuses.updateUndefined(date, null, true)
        assertEquals(listOf(ExplicitDayStatus(date, ExplicitDayStatusType.UNDEFINED)), statuses())
        assertEquals(listOf(leave), store.medicalLeaves.observeIntersecting(date, date).first())

        val expected = statuses().single()
        store.explicitDayStatuses.set(date, ExplicitDayStatusType.DAY_OFF)
        assertConflict { store.explicitDayStatuses.updateUndefined(date, expected, false) }
        assertEquals(listOf(ExplicitDayStatus(date, ExplicitDayStatusType.DAY_OFF)), statuses())
    }

    @Test fun stage3PreviewClassifiesEveryDateAndConflictBlocksTheWholeBatch() = runBlocking {
        val fixture = store.seedV2Catalog()
        val occupied = date.plusDays(1)
        store.v2Shifts.insert(store.buildTestV2Write(fixture, V2TestIds.uuid(705), occupied))
        val preview = store.calendarBatches.preview(
            CalendarBatchAction.DAY_OFF,
            setOf(date, occupied),
        )
        assertEquals(CalendarBatchDateState.APPLICABLE, preview.results.single { it.date == date }.state)
        assertEquals(CalendarBatchDateState.CONFLICT, preview.results.single { it.date == occupied }.state)
        assertConflict { store.calendarBatches.apply(preview) }
        assertTrue(statuses().isEmpty())
    }

    @Test fun stage3ConcurrentChangeInvalidatesPreviewWithoutPartialWrites() = runBlocking {
        store.seedV2Catalog()
        val untouched = date.plusDays(1)
        val changed = date.plusDays(2)
        val preview = store.calendarBatches.preview(
            CalendarBatchAction.UNDEFINED,
            setOf(untouched, changed),
        )
        store.explicitDayStatuses.set(changed, ExplicitDayStatusType.DAY_OFF)
        assertConflict { store.calendarBatches.apply(preview) }
        assertEquals(
            listOf(ExplicitDayStatus(changed, ExplicitDayStatusType.DAY_OFF)),
            statuses(),
        )
    }

    @Test fun stage3AvailabilityCreatesIndependentRecordsAndRepeatingIsUnchanged() = runBlocking {
        val fixture = store.seedV2Catalog()
        val dates = setOf(date, date.plusDays(1))
        val input = CalendarBatchAvailability(
            start = LocalTime.of(21, 0),
            end = LocalTime.of(6, 0),
            label = AvailabilityLabel.ON_CALL_RETAINER,
        )
        val preview = store.calendarBatches.preview(CalendarBatchAction.AVAILABILITY, dates, input)
        assertEquals(2, preview.applicable.size)
        store.calendarBatches.apply(preview)
        val records = dates.flatMap { selected ->
            store.availabilityWindows.observeOn(
                V2TestIds.TIMELINE,
                fixture.revision.value.sector,
                selected,
            ).first()
        }
        assertEquals(dates, records.map { it.ownerLocalDate }.toSet())
        assertEquals(2, records.map { it.id }.distinct().size)
        val repeated = store.calendarBatches.preview(CalendarBatchAction.AVAILABILITY, dates, input)
        assertEquals(2, repeated.unchanged.size)
        assertTrue(repeated.conflicts.isEmpty())
    }

    private suspend fun statuses() = store.explicitDayStatuses.observeBetween(date, date.plusDays(5)).first()

    private suspend fun assertConflict(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected a conflicting local write")
        } catch (_: ConflictingLocalWriteException) {
            // Expected: transaction refused without partial effects.
        }
    }

    private companion object { const val DB = "day-off-persistence-test.db" }
}
