package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NextEventQueriesInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "next-event-${UUID.randomUUID()}.db"
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        store = LocalDataStore.create(context, databaseName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun endingAfterIsExclusiveStableAndReactsToInsertEditCancelAndDelete() = runBlocking {
        val earlierEnd = shift(
            "10000000-0000-0000-0000-000000000002",
            NOW.plusSeconds(60),
            NOW.plusSeconds(120),
        )
        val laterEnd = shift(
            "10000000-0000-0000-0000-000000000001",
            NOW.plusSeconds(60),
            NOW.plusSeconds(180),
        )
        val endedExactly = shift(
            "10000000-0000-0000-0000-000000000003",
            NOW.minusSeconds(60),
            NOW,
        )

        store.shifts.insert(laterEnd)
        store.shifts.insert(earlierEnd)
        store.shifts.insert(endedExactly)
        assertEquals(listOf(earlierEnd.id, laterEnd.id), store.shifts.observeEndingAfter(NOW).first().map(Shift::id))

        val edited = earlierEnd.copy(endAt = NOW.plusSeconds(240), updatedAt = NOW.plusSeconds(1))
        store.shifts.update(edited)
        assertEquals(listOf(laterEnd.id, edited.id), store.shifts.observeEndingAfter(NOW).first().map(Shift::id))

        val cancelled = laterEnd.copy(status = ShiftStatus.CANCELLED, updatedAt = NOW.plusSeconds(2))
        store.shifts.update(cancelled)
        assertEquals(ShiftStatus.CANCELLED, store.shifts.observeEndingAfter(NOW).first().first().status)

        store.shifts.delete(cancelled.id)
        assertEquals(listOf(edited.id), store.shifts.observeEndingAfter(NOW).first().map(Shift::id))
    }

    @Test
    fun explicitStatusesFromDateAreInclusiveAndNaturallyOrdered() = runBlocking {
        val today = LocalDate.of(2026, 8, 15)
        store.explicitDayStatuses.set(today.plusDays(1), ExplicitDayStatusType.UNDEFINED)
        store.explicitDayStatuses.set(today, ExplicitDayStatusType.DAY_OFF)
        store.explicitDayStatuses.set(today.minusDays(1), ExplicitDayStatusType.DAY_OFF)

        val observed = store.explicitDayStatuses.observeFrom(today).first()

        assertEquals(listOf(today, today.plusDays(1)), observed.map { it.date })
        assertEquals(ExplicitDayStatusType.DAY_OFF, observed.first().type)
    }

    @Test
    fun vacationsEndingOnOrAfterDateAreInclusiveAndOrdered() = runBlocking {
        val today = LocalDate.of(2026, 8, 15)
        val endingToday = vacation("20000000-0000-0000-0000-000000000002", today.minusDays(2), today)
        val future = vacation("20000000-0000-0000-0000-000000000003", today.plusDays(3), today.plusDays(4))
        val ended = vacation("20000000-0000-0000-0000-000000000001", today.minusDays(4), today.minusDays(3))
        store.vacations.insert(future)
        store.vacations.insert(ended)
        store.vacations.insert(endingToday)

        val observed = store.vacations.observeEndingOnOrAfter(today).first()

        assertEquals(listOf(endingToday.id, future.id), observed.map(Vacation::id))
    }

    @Test
    fun namedIsolatedDatabaseReopensWithDataAndRemainsVersionSevenWithTwentyTwoEntities() = runBlocking {
        val shift = shift(
            "30000000-0000-0000-0000-000000000001",
            NOW.plusSeconds(60),
            NOW.plusSeconds(120),
        )
        store.shifts.insert(shift)
        store.close()
        store = LocalDataStore.create(context, databaseName)

        assertEquals(shift.id, store.shifts.observeEndingAfter(NOW).first().single().id)

        val database = MiGuardiaDatabase.build(context, databaseName)
        val sqlite = database.openHelper.readableDatabase
        assertEquals(7, sqlite.version)
        val cursor = sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'android_%' AND name != 'room_master_table'",
        )
        val tables = buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
        cursor.close()
        database.close()
        assertEquals(22, tables.size)
        assertTrue("shifts" in tables)
        assertTrue("vacations" in tables)
        assertTrue("shift_notification_configs" in tables)
        assertTrue("shift_notification_reminders" in tables)
        assertFalse("next_events" in tables)
    }

    private fun shift(id: String, start: Instant, end: Instant) = Shift(
        id = UUID.fromString(id),
        startAt = start,
        endAt = end,
        zoneId = AppDefaults.zoneId(),
        localStartDate = start.atZone(AppDefaults.zoneId()).toLocalDate(),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "FIC",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = start.atZone(AppDefaults.zoneId()).toLocalTime(),
        endTimeSnapshot = end.atZone(AppDefaults.zoneId()).toLocalTime(),
        colorArgbSnapshot = 0xFF315DA8.toInt(),
        position = "Puesto ficticio",
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun vacation(id: String, start: LocalDate, end: LocalDate) = Vacation(
        id = UUID.fromString(id),
        startDate = start,
        endDateInclusive = end,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private companion object {
        val NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
