package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NextEventQueriesInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        store = LocalDataStore.create(context, DB)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun endingAfterIsExclusiveStableAndContainsOnlyPairedV2Shifts() = runBlocking {
        val fixture = store.seedV2Catalog()
        val first = store.buildTestV2Write(fixture, V2TestIds.uuid(401), V2TestIds.SHIFT_DATE)
        val second = store.buildTestV2Write(fixture, V2TestIds.uuid(402), V2TestIds.SHIFT_DATE.plusDays(1))
        store.v2Shifts.insert(second)
        store.v2Shifts.insert(first)

        val observed = store.shifts.observeEndingAfter(first.shift.startAt).first()

        assertEquals(listOf(first.shift.id, second.shift.id), observed.map(Shift::id))
        assertEquals(2, observed.size)
    }

    @Test
    fun explicitStatusesFromDateAreInclusiveAndOrdered() = runBlocking {
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
        val endingToday = vacation(410, today.minusDays(2), today)
        val future = vacation(411, today.plusDays(3), today.plusDays(4))
        val ended = vacation(412, today.minusDays(4), today.minusDays(3))
        store.vacations.insert(future)
        store.vacations.insert(ended)
        store.vacations.insert(endingToday)

        assertEquals(
            listOf(endingToday.id, future.id),
            store.vacations.observeEndingOnOrAfter(today).first().map(Vacation::id),
        )
    }

    @Test
    fun commonQueriesSurviveReopenInTheV2Database() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(420), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        store.close()
        store = LocalDataStore.create(context, DB)

        assertEquals(write.shift.id, store.shifts.observeEndingAfter(Instant.EPOCH).first().single().id)
    }

    private fun vacation(number: Int, start: LocalDate, end: LocalDate) = Vacation(
        id = V2TestIds.uuid(number),
        startDate = start,
        endDateInclusive = end,
        createdAt = V2TestIds.NOW,
        updatedAt = V2TestIds.NOW,
    )

    private companion object {
        const val DB = "next-event-v2-test.db"
    }
}
